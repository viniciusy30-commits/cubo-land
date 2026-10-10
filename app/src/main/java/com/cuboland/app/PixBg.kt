package com.cuboland.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

private fun pc(r: Int, g: Int, b: Int): Int = Color.rgb(r, g, b)

/** Cenário pixel art das telas internas: mesmo céu (muda com a hora) e campo do menu principal. */
class SceneBg(ctx: Context, var dim: Float = 0f) : View(ctx) {
    private val P = Paint()
    private val BP = Paint()
    private var sc = 4
    private var W = 0
    private var H = 0
    private var t = 0f
    private var lastNs = 0L
    private var builtAt = 0f
    private var night = 0f
    private var hour = 12f
    private var skyTop = 0
    private var skyMid = 0
    private var skyBot = 0
    private var skyBmp: Bitmap? = null
    private var landBmp: Bitmap? = null
    private var sunBmp: Bitmap? = null
    private val cloudBmp = arrayOfNulls<Bitmap>(2)

    private val bay = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    private class K(val h: Float, val top: Int, val mid: Int, val bot: Int, val night: Float)

    private val keys = arrayOf(
        K(0f, pc(14, 10, 48), pc(40, 26, 96), pc(96, 56, 148), 1f),
        K(5f, pc(16, 12, 56), pc(46, 30, 104), pc(104, 60, 150), 1f),
        K(6.8f, pc(74, 72, 176), pc(206, 124, 190), pc(255, 190, 150), 0.4f),
        K(9.2f, pc(92, 150, 252), pc(150, 200, 255), pc(236, 238, 255), 0f),
        K(16f, pc(92, 150, 252), pc(150, 200, 255), pc(236, 238, 255), 0f),
        K(18.5f, pc(86, 68, 172), pc(216, 106, 172), pc(255, 174, 112), 0.35f),
        K(21f, pc(16, 12, 56), pc(46, 30, 104), pc(104, 60, 150), 1f),
        K(24f, pc(14, 10, 48), pc(40, 26, 96), pc(96, 56, 148), 1f))

    init {
        P.isAntiAlias = false
        BP.isFilterBitmap = false
        BP.isAntiAlias = false
    }

    private fun bayf(x: Int, y: Int): Float = (bay[((y and 3) shl 2) or (x and 3)] + 0.5f) / 16f

    private fun tn(c: Int): Int {
        if (night < 0.02f) return c
        return mixC(shadeC(c, 1f - 0.5f * night), pc(40, 32, 110), 0.22f * night)
    }

    private fun rc(c: Canvas, x: Int, y: Int, w: Int, h: Int, col: Int, a: Int = 255) {
        if (w <= 0 || h <= 0) return
        P.color = col
        if (a < 255) P.alpha = if (a < 0) 0 else a
        c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), P)
    }

    private fun disc(c: Canvas, cx: Int, cy: Int, r: Int, col: Int, a: Int = 255) {
        for (dy in -r..r) {
            val w = sqrt((r * r - dy * dy).toFloat() + 0.25f).toInt()
            rc(c, cx - w, cy + dy, 2 * w + 1, 1, col, a)
        }
    }

    private fun meadowY(x: Int): Int =
        (H - 34 - 2 - 3f * sin(x * 0.045f + 0.5f) - 2f * sin(x * 0.13f + 1f)).toInt()

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w <= 0 || h <= 0) return
        sc = max(2, min(h / 172, w / 320))
        W = (w + sc - 1) / sc
        H = (h + sc - 1) / sc
        build()
    }

    private fun pickSky() {
        var found = false
        for (i in 0 until keys.size - 1) {
            val a = keys[i]
            val b = keys[i + 1]
            if (hour >= a.h && hour <= b.h) {
                val f = (hour - a.h) / (b.h - a.h)
                skyTop = mixC(a.top, b.top, f); skyMid = mixC(a.mid, b.mid, f); skyBot = mixC(a.bot, b.bot, f)
                night = a.night + (b.night - a.night) * f
                found = true
                break
            }
        }
        if (!found) { skyTop = keys[3].top; skyMid = keys[3].mid; skyBot = keys[3].bot; night = 0f }
    }

    private fun ridge(px: IntArray, seed: Float, base: Int, amp: Float, cTop: Int, cBot: Int) {
        val hz = H - 30
        for (x in 0 until W) {
            val h = (base - (amp * 0.5f * sin(x * 0.021f + seed) + amp * 0.3f * sin(x * 0.057f + seed * 2.1f) + amp * 0.2f * sin(x * 0.13f + seed * 0.7f))).toInt()
            for (y in max(0, h) until H) {
                val d = (y - h).toFloat() / max(1, hz - h)
                var lvl = (min(1f, d) * 3f + (bayf(x, y) - 0.5f)).toInt()
                lvl = max(0, min(3, lvl))
                px[y * W + x] = mixC(cTop, cBot, lvl / 3f)
            }
            if (h >= 0 && h < H) px[h * W + x] = mixC(cTop, Color.WHITE, 0.18f)
        }
    }

    private fun build() {
        val cal = java.util.Calendar.getInstance()
        hour = cal.get(java.util.Calendar.HOUR_OF_DAY) + cal.get(java.util.Calendar.MINUTE) / 60f
        pickSky()
        // céu em degradê pontilhado
        val sp = IntArray(W * H)
        val lv = 16
        val levels = IntArray(lv + 1)
        for (i in 0..lv) {
            val f = i / lv.toFloat()
            levels[i] = if (f < 0.55f) mixC(skyTop, skyMid, f / 0.55f) else mixC(skyMid, skyBot, (f - 0.55f) / 0.45f)
        }
        val hz = (H - 36).toFloat()
        for (y in 0 until H) {
            val f = min(1f, y / hz)
            val pos = f * lv
            val lo = pos.toInt()
            val fr = pos - lo
            for (x in 0 until W) sp[y * W + x] = if (fr > bayf(x, y)) levels[min(lv, lo + 1)] else levels[lo]
        }
        val sb = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        sb.setPixels(sp, 0, W, 0, 0, W, H)
        skyBmp = sb
        // montanhas e campo
        val px = IntArray(W * H)
        ridge(px, 1.0f, H - 52, 26f, mixC(pc(120, 120, 200), skyBot, 0.55f), mixC(pc(150, 150, 210), skyBot, 0.75f))
        ridge(px, 3.0f, H - 44, 20f, mixC(pc(90, 110, 170), skyBot, 0.4f), mixC(pc(110, 150, 170), skyBot, 0.7f))
        val gy = H - 34
        for (x in 0 until W) {
            val h = (gy - 10 - 5f * sin(x * 0.03f + 1f) - 3f * sin(x * 0.09f + 2f)).toInt()
            for (y in max(0, h) until H) {
                val d = (y - h) / 20f
                var c = mixC(pc(110, 196, 140), pc(80, 170, 110), min(1f, d))
                c = mixC(c, skyBot, 0.35f)
                if ((x + y) % 2 == 0 && d < 0.12f) c = shadeC(c, 1.08f)
                px[y * W + x] = c
            }
            if (h >= 0 && h < H) px[h * W + x] = mixC(pc(150, 226, 150), skyBot, 0.3f)
            val h2 = meadowY(x)
            for (y in max(0, h2) until H) {
                val d = y - h2
                var c = when {
                    d == 0 -> pc(150, 228, 100)
                    d < 3 -> pc(104, 200, 84)
                    d < 8 -> pc(84, 176, 76)
                    else -> pc(66, 146, 70)
                }
                c = shadeC(c, 0.93f + 0.14f * hash2(x, y, 5))
                if (d >= 3 && (x * 3 + y) % 7 == 0) c = shadeC(c, 1.12f)
                px[y * W + x] = tn(c)
            }
        }
        val lb = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        lb.setPixels(px, 0, W, 0, 0, W, H)
        landBmp = lb
        val lc = Canvas(lb)
        val fc = intArrayOf(pc(255, 120, 160), pc(255, 230, 110), pc(150, 200, 255), pc(255, 150, 200), pc(255, 255, 255))
        for (i in 0 until 40) {
            val x = (hash2(i, 3, 41) * (W - 8)).toInt() + 4
            val y = meadowY(x) + 4 + (hash2(i, 4, 42) * 22).toInt()
            rc(lc, x, y - 4, 1, 4, tn(pc(60, 140, 70)))
            rc(lc, x - 1, y - 6, 3, 3, tn(fc[i % 5]))
            rc(lc, x, y - 7, 1, 5, tn(fc[i % 5]))
            rc(lc, x, y - 5, 1, 1, tn(pc(255, 230, 110)))
        }
        // sol / lua
        val isDay = hour >= 6f && hour <= 18f
        val sn = Bitmap.createBitmap(56, 56, Bitmap.Config.ARGB_8888)
        val sc2 = Canvas(sn)
        if (isDay) {
            disc(sc2, 28, 28, 17, pc(255, 246, 190), 28); disc(sc2, 28, 28, 13, pc(255, 246, 190), 60)
            disc(sc2, 28, 28, 9, pc(255, 244, 170)); disc(sc2, 28, 28, 7, pc(255, 252, 215))
        } else {
            disc(sc2, 28, 28, 20, pc(190, 200, 255), 22); disc(sc2, 28, 28, 15, pc(190, 200, 255), 40)
            disc(sc2, 28, 28, 10, pc(232, 238, 252))
            disc(sc2, 25, 25, 2, pc(196, 206, 232)); disc(sc2, 31, 30, 2, pc(196, 206, 232))
        }
        sunBmp = sn
        // nuvens
        val tint = if (night > 0.1f && night < 0.6f) mixC(Color.WHITE, pc(255, 190, 200), 0.5f) else Color.WHITE
        val base = mixC(tint, pc(90, 80, 150), night * 0.55f)
        val sh = mixC(base, pc(110, 100, 190), 0.55f)
        val hi = mixC(base, Color.WHITE, 0.5f)
        val ax = intArrayOf(0, 7, 15, 22, -6)
        val ay = intArrayOf(0, -3, -1, 1, 2)
        val ar = intArrayOf(6, 8, 7, 5, 4)
        for (k in 0 until 2) {
            val b = Bitmap.createBitmap(64, 32, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            val s = if (k == 0) 1.0f else 0.8f
            for (i in 0 until 5) disc(c, 16 + (ax[i] * s).toInt(), 16 + (ay[i] * s).toInt() + 2, (ar[i] * s).toInt(), sh)
            for (i in 0 until 5) disc(c, 16 + (ax[i] * s).toInt(), 16 + (ay[i] * s).toInt(), (ar[i] * s).toInt(), base)
            for (i in 0 until 3) disc(c, 16 + (ax[i] * s).toInt() - 1, 16 + (ay[i] * s).toInt() - (ar[i] * s * 0.35f).toInt(), max(1, (ar[i] * s * 0.55f).toInt()), hi)
            rc(c, 16 - (8 * s).toInt(), 16 + (5 * s).toInt(), (34 * s).toInt(), 3, base)
            cloudBmp[k] = b
        }
        builtAt = t
    }

    override fun onDraw(c: Canvas) {
        val sky = skyBmp ?: return
        val land = landBmp ?: return
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 0.016f else min(0.05f, (now - lastNs) / 1e9f)
        lastNs = now
        t += dt
        if (t - builtAt > 240f) build()
        c.save()
        c.scale(sc.toFloat(), sc.toFloat())
        c.drawBitmap(sky, 0f, 0f, BP)
        if (night > 0.05f) {
            for (i in 0 until 80) {
                val sx = (hash2(i, 1, 21) * W).toInt()
                val sy = (hash2(i, 2, 22) * H * 0.6f).toInt()
                val tw = 0.5f + 0.5f * sin(t * (1f + hash2(i, 3, 23) * 2f) + i)
                rc(c, sx, sy, 1, 1, pc(255, 255, 235), (night * 255f * (0.4f + 0.6f * tw)).toInt().coerceIn(0, 255))
            }
        }
        val isDay = hour >= 6f && hour <= 18f
        val frac = if (isDay) (hour - 6f) / 12f else ((hour + 24f - 18f) % 24f) / 12f
        val sunB = sunBmp
        if (sunB != null) c.drawBitmap(sunB, (W * (0.1f + 0.8f * frac)).toInt() - 28f, (H * (0.5f - 0.38f * sin(PI.toFloat() * frac))).toInt() - 28f, BP)
        val cy = intArrayOf(40, 96, 70, 22)
        val sp2 = floatArrayOf(2.0f, 1.4f, 1.0f, 1.7f)
        val bs = intArrayOf(40, 280, 150, 340)
        for (i in 0 until 4) {
            val b = cloudBmp[i % 2] ?: continue
            val x = ((bs[i] * W / 400f + t * sp2[i]) % (W + 120f)) - 60f
            c.drawBitmap(b, (x.toInt() - 16).toFloat(), (cy[i] - 16).toFloat(), BP)
        }
        c.drawBitmap(land, 0f, 0f, BP)
        val motes = intArrayOf(pc(120, 255, 235), pc(255, 160, 210), pc(255, 232, 130), pc(200, 170, 255))
        for (i in 0 until 24) {
            val x = ((hash2(i, 5, 62) * W + sin(t * 0.4f + i) * 6f) + W) % W
            val y = H * (0.2f + 0.6f * hash2(i, 6, 63)) - ((t * (3f + hash2(i, 7, 64) * 5f)) % (H * 0.3f))
            val tw = 0.4f + 0.6f * abs(sin(t * 1.3f + i * 1.7f))
            rc(c, x.toInt(), y.toInt(), 1, 1, motes[i % 4], (170f * tw).toInt())
        }
        if (night > 0.3f) {
            for (i in 0 until 12) {
                val x = (((hash2(i, 1, 71) * W + sin(t * 0.5f + i * 1.7f) * 12f) + W) % W).toInt()
                val y = (H * (0.55f + 0.38f * hash2(i, 2, 72)) + sin(t * 0.8f + i) * 6f).toInt()
                val a = (night * 230f * (0.4f + 0.6f * abs(sin(t * 1.3f + i * 1.7f)))).toInt()
                rc(c, x - 1, y - 1, 4, 4, pc(255, 240, 130), a / 4)
                rc(c, x, y, 2, 2, pc(255, 246, 170), a)
            }
        }
        c.restore()
        if (dim > 0f) {
            P.color = Color.argb((dim * 255).toInt().coerceIn(0, 255), 14, 12, 34)
            c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), P)
        }
        postInvalidateOnAnimation()
    }
}
