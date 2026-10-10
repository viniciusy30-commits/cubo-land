package com.cuboland.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private fun cc(r: Int, g: Int, b: Int): Int = Color.rgb(r, g, b)
private val INK = cc(46, 22, 66)
private val WHITE = Color.WHITE

/**
 * Menu principal em pixel art. A cena inteira é desenhada numa grade lógica (cada "pixel" vira
 * um bloco inteiro na tela), com céu que muda conforme a hora, ilhas flutuantes, personagem do
 * jogador de um lado e o espaço do futuro jogador online do outro.
 */
class MenuScene(
    ctx: Context,
    private val versionName: String,
    private var continueText: String,
    private var hasNews: Boolean,
    private val onPlay: () -> Unit,
    private val onChar: () -> Unit,
    private val onSettings: () -> Unit,
    private val onNews: () -> Unit,
    private val onUpdate: () -> Unit
) : View(ctx) {

    companion object {
        const val CW = 110
        const val CH = 144
        const val CFEET = 137

        private val BAY = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

        private val ICO_STAR = arrayOf(
            "....a....",
            "....a....",
            "...aaa...",
            "aaaaaaaaa",
            ".aaaaaaa.",
            "..aaaaa..",
            "..aa.aa..",
            ".aa...aa.")
        private val ICO_REFRESH = arrayOf(
            "..aaaa...",
            ".a....a.a",
            "a......aa",
            "a.....aaa",
            "a........",
            "a.......a",
            ".a.....a.",
            "..aaaaa..")
        private val ICO_GEAR = arrayOf(
            "...aa...",
            ".a.aa.a.",
            "..aaaa..",
            "aaa..aaa",
            "aaa..aaa",
            "..aaaa..",
            ".a.aa.a.",
            "...aa...")
        private val ICO_FACE = arrayOf(
            ".aaaaaa.",
            "aaaaaaaa",
            "aaaaaaaa",
            "abaaaaba",
            "aaaaaaaa",
            ".aaaaaa.",
            "..a..a..",
            ".aaaaaa.")
        private val ICO_HEART = arrayOf(
            ".aa.aa.",
            "abaaaaa",
            "aaaaaaa",
            ".aaaaa.",
            "..aaa..",
            "...a...")
    }

    private val P = Paint()
    private val BP = Paint()
    private var cnv: Canvas = Canvas()
    private var tintOn = false
    private var night = 0f
    private var sc = 4
    private var W = 0
    private var H = 0
    private var t = 0f
    private var introT = 0f
    private var lastNs = 0L
    private var builtAt = 0f
    private var hour = 12f
    private var skyTop = 0
    private var skyMid = 0
    private var skyBot = 0
    private var needUi = false

    private var skyBmp: Bitmap? = null
    private var landBmp: Bitmap? = null
    private val cloudBmp = arrayOfNulls<Bitmap>(3)
    private var sunBmp: Bitmap? = null
    private var islL: Bitmap? = null
    private var islR: Bitmap? = null
    private var titleBmp: Bitmap? = null
    private var titleBlocks = IntArray(0)
    private var wingL: Bitmap? = null
    private var wingR: Bitmap? = null
    private var ribbonBmp: Bitmap? = null
    private var scrollBmp: Bitmap? = null
    private var plaqueMe: Bitmap? = null
    private var plaqueOther: Bitmap? = null
    private var footL: Bitmap? = null
    private var footC: Bitmap? = null
    private var bubbleQ: Bitmap? = null
    private var bubbleSoon: Bitmap? = null
    private var ghostBmp: Bitmap? = null
    private var ghostLight: PorterDuffColorFilter? = null

    private val bx = IntArray(5)
    private val by = IntArray(5)
    private val bw = IntArray(5)
    private val bh = IntArray(5)
    private val bUp = arrayOfNulls<Bitmap>(5)
    private val bDn = arrayOfNulls<Bitmap>(5)
    private var pressedBtn = -1
    private var playOffX = 6
    private var playOffY = 9

    private var midX = 160
    private var off = 96
    private var platY = 150
    private var titleX = 0
    private var titleY = 7
    private var titleW = 0
    private var blockY = 62
    private var ribbonX = 0
    private var scrollX = 0
    private var scrollY = 0

    private val me = CharView(ctx, CW, CH).apply { transparentBg = true; sway = true; shadow = false }
    private val flashF = arrayOfNulls<PorterDuffColorFilter>(17)

    private var dragging = false
    private var ghostPress = false
    private var tapMoved = false
    private var downX = 0f
    private var downY = 0f
    private var pokeT = -10f
    private var tapT = -10f

    private val motePal = intArrayOf(cc(120, 255, 235), cc(255, 160, 210), cc(255, 232, 130), cc(200, 170, 255))

    private class Key(val h: Float, val top: Int, val mid: Int, val bot: Int, val night: Float)

    private val keys = arrayOf(
        Key(0f, cc(14, 10, 48), cc(40, 26, 96), cc(96, 56, 148), 1f),
        Key(5f, cc(16, 12, 56), cc(46, 30, 104), cc(104, 60, 150), 1f),
        Key(6.8f, cc(74, 72, 176), cc(206, 124, 190), cc(255, 190, 150), 0.4f),
        Key(9.2f, cc(92, 150, 252), cc(150, 200, 255), cc(236, 238, 255), 0f),
        Key(16f, cc(92, 150, 252), cc(150, 200, 255), cc(236, 238, 255), 0f),
        Key(18.5f, cc(86, 68, 172), cc(216, 106, 172), cc(255, 174, 112), 0.35f),
        Key(21f, cc(16, 12, 56), cc(46, 30, 104), cc(104, 60, 150), 1f),
        Key(24f, cc(14, 10, 48), cc(40, 26, 96), cc(96, 56, 148), 1f))

    init {
        P.isAntiAlias = false
        BP.isAntiAlias = false
        BP.isFilterBitmap = false
        isClickable = true
    }

    // ------------------------------------------------------------------ API pública

    /** o personagem "entra no jogo" de novo (luz, faíscas, materializa) */
    fun replay() { introT = 0f }

    fun setNews(b: Boolean) { hasNews = b }

    fun setContinue(s: String) {
        if (s != continueText) { continueText = s; needUi = true }
    }

    // ------------------------------------------------------------------ primitivas

    private fun tn(c: Int): Int {
        if (night < 0.02f) return c
        return mixC(shadeC(c, 1f - 0.5f * night), cc(40, 32, 110), 0.22f * night)
    }

    private fun rc(x: Int, y: Int, w: Int, h: Int, c: Int, a: Int = 255) {
        if (w <= 0 || h <= 0) return
        P.color = if (tintOn) tn(c) else c
        if (a < 255) P.alpha = if (a < 0) 0 else a
        cnv.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), P)
    }

    private fun disc(cx: Int, cy: Int, r: Int, c: Int, a: Int = 255) {
        for (dy in -r..r) {
            val w = sqrt((r * r - dy * dy).toFloat() + 0.25f).toInt()
            rc(cx - w, cy + dy, 2 * w + 1, 1, c, a)
        }
    }

    private fun ell(cx: Int, cy: Int, rx: Int, ry: Int, c: Int, a: Int = 255) {
        for (dy in -ry..ry) {
            val k = dy.toFloat() / ry
            val w = (rx * sqrt(max(0f, 1f - k * k)) + 0.5f).toInt()
            rc(cx - w, cy + dy, 2 * w + 1, 1, c, a)
        }
    }

    private fun sparkle(x: Int, y: Int, r: Int, c: Int, a: Int = 255) {
        rc(x, y - r, 1, 2 * r + 1, c, a)
        rc(x - r, y, 2 * r + 1, 1, c, a)
        rc(x, y, 1, 1, WHITE, a)
    }

    private fun bay(x: Int, y: Int): Float = (BAY[((y and 3) shl 2) or (x and 3)] + 0.5f) / 16f

    private fun bake(w: Int, h: Int, tinted: Boolean, draw: () -> Unit): Bitmap {
        val b = Bitmap.createBitmap(max(1, w), max(1, h), Bitmap.Config.ARGB_8888)
        val oc = cnv
        val ot = tintOn
        cnv = Canvas(b)
        tintOn = tinted
        draw()
        cnv = oc
        tintOn = ot
        return b
    }

    private fun txt(s: String, x: Int, y: Int, sc: Int, col: Int) {
        val cells = PixFont.parse(s)
        var cx = x
        for (cell in cells) {
            for (ry in 0 until 7) {
                val bits = cell.rows[ry]
                var rx = 0
                while (rx < 5) {
                    if (((bits shr (4 - rx)) and 1) != 0) {
                        var e = rx
                        while (e + 1 < 5 && ((bits shr (4 - (e + 1))) and 1) != 0) e++
                        rc(cx + rx * sc, y + ry * sc, (e - rx + 1) * sc, sc, col)
                        rx = e + 1
                    } else {
                        rx++
                    }
                }
            }
            val mk = PixFont.marks(cell.mark)
            var i = 0
            while (i + 1 < mk.size) {
                rc(cx + mk[i] * sc, y + mk[i + 1] * sc, sc, sc, col)
                i += 2
            }
            cx += 6 * sc
        }
    }

    private fun icon(rows: Array<String>, x: Int, y: Int, a: Int, b: Int) {
        for (ry in rows.indices) {
            val row = rows[ry]
            for (rx in row.indices) {
                val ch = row[rx]
                if (ch == 'a') rc(x + rx, y + ry, 1, 1, a)
                else if (ch == 'b') rc(x + rx, y + ry, 1, 1, b)
            }
        }
    }

    private fun meadowY(x: Int): Int =
        (H - 34 - 2 - 3f * sin(x * 0.045f + 0.5f) - 2f * sin(x * 0.13f + 1f)).toInt()

    // ------------------------------------------------------------------ construção (cache)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        if (w <= 0 || h <= 0) return
        sc = max(2, min(h / 172, w / 320))
        W = (w + sc - 1) / sc
        H = (h + sc - 1) / sc
        rebuild(true)
    }

    private fun rebuild(first: Boolean) {
        val cal = java.util.Calendar.getInstance()
        hour = cal.get(java.util.Calendar.HOUR_OF_DAY) + cal.get(java.util.Calendar.MINUTE) / 60f
        pickSky()
        midX = W / 2
        off = if (W < 360) 96 else min(130, (W * 0.30f).toInt())
        platY = H - 30
        buildSky()
        buildLand()
        buildSprites()
        buildUi()
        if (first || ghostBmp == null) buildGhost()
        builtAt = t
    }

    private fun pickSky() {
        var found = false
        for (i in 0 until keys.size - 1) {
            val a = keys[i]
            val b = keys[i + 1]
            if (hour >= a.h && hour <= b.h) {
                val f = (hour - a.h) / (b.h - a.h)
                skyTop = mixC(a.top, b.top, f)
                skyMid = mixC(a.mid, b.mid, f)
                skyBot = mixC(a.bot, b.bot, f)
                night = a.night + (b.night - a.night) * f
                found = true
                break
            }
        }
        if (!found) {
            skyTop = keys[3].top; skyMid = keys[3].mid; skyBot = keys[3].bot; night = 0f
        }
    }

    private fun buildSky() {
        val px = IntArray(W * H)
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
            for (x in 0 until W) {
                var c = if (fr > bay(x, y)) levels[min(lv, lo + 1)] else levels[lo]
                if (y < 56 && (1f - y / 56f) * 0.9f > bay(x, y)) c = shadeC(c, 0.82f)
                px[y * W + x] = c
            }
        }
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        b.setPixels(px, 0, W, 0, 0, W, H)
        skyBmp = b
    }

    private fun ridge(px: IntArray, seed: Float, base: Int, amp: Float, cTop: Int, cBot: Int) {
        val hz = H - 30
        for (x in 0 until W) {
            val h = (base - (amp * 0.5f * sin(x * 0.021f + seed) + amp * 0.3f * sin(x * 0.057f + seed * 2.1f) + amp * 0.2f * sin(x * 0.13f + seed * 0.7f))).toInt()
            for (y in max(0, h) until H) {
                val d = (y - h).toFloat() / max(1, hz - h)
                var lvl = (min(1f, d) * 3f + (bay(x, y) - 0.5f)).toInt()
                lvl = max(0, min(3, lvl))
                px[y * W + x] = mixC(cTop, cBot, lvl / 3f)
            }
            if (h >= 0 && h < H) px[h * W + x] = mixC(cTop, WHITE, 0.18f)
        }
    }

    private fun groundPx(px: IntArray) {
        val gy = H - 34
        for (x in 0 until W) {
            val h = (gy - 10 - 5f * sin(x * 0.03f + 1f) - 3f * sin(x * 0.09f + 2f)).toInt()
            for (y in max(0, h) until H) {
                val d = (y - h) / 20f
                var c = mixC(cc(110, 196, 140), cc(80, 170, 110), min(1f, d))
                c = mixC(c, skyBot, 0.35f)
                if ((x + y) % 2 == 0 && d < 0.12f) c = shadeC(c, 1.08f)
                px[y * W + x] = c
            }
            if (h >= 0 && h < H) px[h * W + x] = mixC(cc(150, 226, 150), skyBot, 0.3f)
            val h2 = meadowY(x)
            for (y in max(0, h2) until H) {
                val d = y - h2
                val n = hash2(x, y, 5)
                var c = when {
                    d == 0 -> cc(150, 228, 100)
                    d < 3 -> cc(104, 200, 84)
                    d < 8 -> cc(84, 176, 76)
                    else -> cc(66, 146, 70)
                }
                c = shadeC(c, 0.93f + 0.14f * n)
                if (d >= 3 && (x * 3 + y) % 7 == 0) c = shadeC(c, 1.12f)
                px[y * W + x] = tn(c)
            }
        }
    }

    private fun buildLand() {
        val px = IntArray(W * H)
        ridge(px, 1.0f, H - 52, 26f, mixC(cc(120, 120, 200), skyBot, 0.55f), mixC(cc(150, 150, 210), skyBot, 0.75f))
        ridge(px, 3.0f, H - 44, 20f, mixC(cc(90, 110, 170), skyBot, 0.4f), mixC(cc(110, 150, 170), skyBot, 0.7f))
        groundPx(px)
        val b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        b.setPixels(px, 0, W, 0, 0, W, H)
        landBmp = b

        val oc = cnv
        val ot = tintOn
        cnv = Canvas(b)
        tintOn = true
        val fc = intArrayOf(cc(255, 120, 160), cc(255, 230, 110), cc(150, 200, 255), cc(255, 150, 200), cc(255, 255, 255))
        for (i in 0 until 28) {
            val x = (hash2(i, 3, 41) * (W - 8)).toInt() + 4
            if (abs(x - midX) < 62) continue
            val y = meadowY(x) + 4 + (hash2(i, 4, 42) * 12).toInt()
            flower(x, y, fc[i % 5])
        }
        for (i in 0 until 46) {
            val x = (hash2(i, 5, 43) * W).toInt()
            val y = meadowY(x) + 6 + (hash2(i, 6, 44) * 22).toInt()
            rc(x, y, 1, 3, cc(60, 140, 70)); rc(x + 1, y + 1, 1, 2, cc(90, 180, 90))
        }
        mushroom(midX - off - 50, meadowY(midX - off - 50) + 16)
        mushroom(midX + off + 48, meadowY(midX + off + 48) + 15)
        mushroom(midX - off - 56, meadowY(midX - off - 56) + 20)
        platformBody(midX - off, true)
        platformBody(midX + off, false)
        tintOn = false
        crystal(midX - off - 40, platY + 2, 18, 7, cc(255, 200, 255), cc(224, 120, 230), cc(150, 60, 180))
        crystal(midX - off - 33, platY + 3, 11, 5, cc(150, 255, 250), cc(60, 200, 220), cc(30, 120, 170))
        crystal(midX + off + 40, platY + 2, 16, 7, cc(150, 255, 250), cc(60, 200, 220), cc(30, 120, 170))
        crystal(midX + off + 33, platY + 3, 10, 5, cc(255, 200, 255), cc(224, 120, 230), cc(150, 60, 180))
        cnv = oc
        tintOn = ot
    }

    private fun flower(x: Int, y: Int, c: Int) {
        rc(x, y - 4, 1, 4, cc(60, 140, 70))
        rc(x - 1, y - 6, 3, 3, c)
        rc(x, y - 7, 1, 5, c)
        rc(x, y - 5, 1, 1, cc(255, 230, 110))
    }

    private fun mushroom(x: Int, y: Int) {
        val k = cc(60, 20, 50); val r = cc(232, 72, 96); val w = cc(255, 246, 232); val s = cc(224, 204, 190)
        rc(x - 1, y - 3, 3, 3, w); rc(x + 1, y - 3, 1, 3, s)
        rc(x - 3, y - 5, 7, 2, r); rc(x - 2, y - 6, 5, 1, r)
        rc(x - 3, y - 4, 1, 1, k); rc(x - 2, y - 5, 1, 1, w); rc(x + 1, y - 6, 1, 1, w); rc(x + 2, y - 4, 1, 1, w)
        rc(x - 4, y - 4, 1, 1, k); rc(x + 4, y - 4, 1, 1, k); rc(x - 3, y - 3, 7, 1, k)
    }

    private fun crystal(x: Int, y: Int, h: Int, w: Int, c1: Int, c2: Int, c3: Int) {
        val k = cc(40, 16, 64)
        for (i in 0 until h) {
            val f = i.toFloat() / h
            val ww = if (f < 0.5f) (w / 2f * min(1f, f * 1.8f + 0.15f)).toInt() else w / 2
            val yy = y - h + i
            rc(x - ww, yy, 2 * ww + 1, 1, c2)
            if (ww > 0) rc(x - ww, yy, max(1, ww), 1, c1)
            rc(x + ww / 2, yy, ww - ww / 2 + 1, 1, c3)
            rc(x - ww - 1, yy, 1, 1, k)
            rc(x + ww + 1, yy, 1, 1, k)
        }
        rc(x, y - h - 1, 1, 1, k)
        rc(x - 1, y - h + 2, 1, max(1, h / 3), WHITE, 200)
        rc(x - w / 2 - 1, y, w + 3, 1, k)
    }

    private fun platformBody(cx: Int, lit: Boolean) {
        val rw = 40; val rh = 9; val gy = platY
        ell(cx, gy + 6, rw + 6, rh, cc(20, 40, 40), 90)
        for (i in 0 until 7) {
            val ww = rw - (if (i > 4) 1 else 0)
            ell(cx, gy + 1 + i, ww, rh, shadeC(cc(150, 146, 176), 0.62f + 0.07f * i))
        }
        var k = -rw + 4
        while (k < rw) { rc(cx + k, gy + rh - 2, 1, 7, cc(84, 76, 110)); k += 11 }
        ell(cx, gy, rw, rh, cc(84, 76, 110))
        ell(cx, gy - 1, rw - 1, rh - 1, cc(190, 186, 214))
        ell(cx, gy - 1, rw - 4, rh - 3, cc(150, 146, 182))
        ell(cx, gy - 1, rw - 6, rh - 4, mixC(cc(70, 64, 100), if (lit) cc(110, 255, 230) else cc(150, 160, 255), if (lit) 0.25f else 0.04f))
    }

    // ------------------------------------------------------------------ sprites (nuvens, sol/lua, ilhas)

    private fun cloudDraw(ox: Int, oy: Int, s: Float) {
        val tint = if (night > 0.1f && night < 0.6f) mixC(WHITE, cc(255, 190, 200), 0.5f) else WHITE
        val base = mixC(tint, cc(90, 80, 150), night * 0.55f)
        val sh = mixC(base, cc(110, 100, 190), 0.55f)
        val hi = mixC(base, WHITE, 0.5f)
        val ax = intArrayOf(0, 7, 15, 22, -6)
        val ay = intArrayOf(0, -3, -1, 1, 2)
        val ar = intArrayOf(6, 8, 7, 5, 4)
        for (i in 0 until 5) disc(ox + (ax[i] * s).toInt(), oy + (ay[i] * s).toInt() + 2, (ar[i] * s).toInt(), sh)
        for (i in 0 until 5) disc(ox + (ax[i] * s).toInt(), oy + (ay[i] * s).toInt(), (ar[i] * s).toInt(), base)
        for (i in 0 until 3) disc(ox + (ax[i] * s).toInt() - 1, oy + (ay[i] * s).toInt() - (ar[i] * s * 0.35f).toInt(), max(1, (ar[i] * s * 0.55f).toInt()), hi)
        rc(ox - (8 * s).toInt(), oy + (5 * s).toInt(), (34 * s).toInt(), 3, base)
    }

    private fun tree(x: Int, y: Int) {
        rc(x - 1, y - 9, 3, 9, cc(112, 76, 52)); rc(x + 1, y - 9, 1, 9, cc(84, 56, 44))
        val dx = intArrayOf(-5, 5, 0, 0); val dy = intArrayOf(-14, -14, -19, -12); val rr = intArrayOf(5, 5, 6, 6)
        for (i in 0 until 4) disc(x + dx[i], y + dy[i], rr[i], shadeC(cc(70, 170, 88), 0.78f))
        val dx2 = intArrayOf(-5, 5, 0, -1); val dy2 = intArrayOf(-15, -15, -20, -13); val rr2 = intArrayOf(4, 4, 5, 5)
        for (i in 0 until 4) disc(x + dx2[i], y + dy2[i], rr2[i], if (i == 2) cc(104, 210, 112) else cc(84, 190, 98))
        val hx = intArrayOf(-3, 1, -6, 3, 5); val hy = intArrayOf(-22, -23, -17, -19, -16)
        for (i in 0 until 5) rc(x + hx[i], y + hy[i], 2, 1, cc(170, 240, 140))
        val px = intArrayOf(-4, 3, 5, -1); val py = intArrayOf(-14, -17, -12, -20)
        for (i in 0 until 4) rc(x + px[i], y + py[i], 1, 1, cc(255, 170, 200))
    }

    private fun house(x: Int, y: Int) {
        val wall = cc(255, 236, 205); val wallS = cc(222, 196, 170); val k = cc(60, 34, 70)
        rc(x - 9, y - 11, 18, 11, wall); rc(x + 4, y - 11, 5, 11, wallS)
        rc(x - 9, y - 11, 18, 1, cc(255, 250, 230))
        rc(x - 9, y - 11, 1, 11, k); rc(x + 8, y - 11, 1, 11, k); rc(x - 9, y - 1, 18, 1, k)
        for (i in 0 until 8) {
            rc(x - 12 + i, y - 12 - i, 24 - 2 * i, 1, if (i % 2 == 0) cc(214, 82, 112) else cc(232, 106, 132))
            rc(x - 12 + i, y - 12 - i, 1, 1, k); rc(x + 11 - i, y - 12 - i, 1, 1, k)
        }
        rc(x - 12, y - 12, 24, 1, cc(150, 48, 86))
        rc(x + 5, y - 24, 3, 7, cc(150, 110, 100)); rc(x + 5, y - 25, 3, 1, cc(90, 60, 70))
        rc(x - 6, y - 8, 5, 8, cc(120, 76, 56)); rc(x - 5, y - 8, 3, 1, cc(150, 100, 70)); rc(x - 3, y - 4, 1, 1, cc(255, 214, 100))
        rc(x + 1, y - 8, 5, 5, k)
        val sv = tintOn
        tintOn = false
        rc(x + 2, y - 7, 3, 3, if (night > 0.2f) cc(255, 226, 120) else cc(170, 220, 255))
        if (night > 0.2f) rc(x + 3, y - 7, 1, 3, cc(255, 250, 200))
        tintOn = sv
    }

    private fun tower(x: Int, y: Int) {
        val stone = cc(190, 184, 214); val stoneS = cc(150, 142, 184); val k = cc(60, 34, 70)
        rc(x - 7, y - 22, 14, 22, stone); rc(x + 2, y - 22, 5, 22, stoneS)
        var q = 0
        while (q < 14) { rc(x - 8 + q, y - 26, 3, 4, stone); q += 4 }
        rc(x - 8, y - 23, 16, 1, k)
        rc(x - 8, y - 22, 1, 22, k); rc(x + 7, y - 22, 1, 22, k)
        for (r in 0 until 4) for (c in 0 until 3) {
            if (hash2(c, r, 9) > 0.4f) rc(x - 6 + c * 5 + (r % 2) * 2, y - 19 + r * 5, 3, 1, mixC(stoneS, stone, 0.5f))
        }
        rc(x - 2, y - 16, 4, 6, k)
        val sv = tintOn
        tintOn = false
        rc(x - 1, y - 15, 2, 4, if (night > 0.2f) cc(255, 226, 120) else cc(150, 200, 250))
        tintOn = sv
        for (i in 0 until 10) rc(x - 6 + i / 2, y - 30 - i, 12 - i, 1, if (i % 2 == 1) cc(100, 70, 190) else cc(124, 92, 214))
        rc(x - 8, y - 27, 16, 1, cc(70, 44, 140))
        rc(x, y - 43, 1, 7, k)
    }

    private fun island(cx: Int, y: Int, w: Int) {
        val hw = w / 2
        val depth = (w * 0.55f).toInt()
        val grass = cc(104, 200, 92); val grassL = cc(160, 232, 118); val dirt = cc(150, 100, 70); val rock = cc(96, 78, 106)
        val k = cc(50, 34, 64)
        for (i in 0 until depth) {
            val f = i.toFloat() / depth
            val ww = (hw * (1f - f).pow(0.75f)).toInt()
            if (ww < 1) break
            val yy = y + 3 + i
            for (x in cx - ww..cx + ww) {
                var c = shadeC(if (i < 5) dirt else rock, 0.78f + 0.3f * hash2(x, yy, 3))
                if (x > cx + ww * 0.35f) c = shadeC(c, 0.78f)
                rc(x, yy, 1, 1, c)
            }
            rc(cx - ww, yy, 1, 1, k); rc(cx + ww, yy, 1, 1, k)
        }
        for (r in 0 until 4) {
            val ww = hw - (if (r < 3) 0 else 1)
            rc(cx - ww, y + r, 2 * ww + 1, 1, if (r > 0) grass else grassL)
        }
        rc(cx - hw - 1, y + 1, 1, 3, k); rc(cx + hw + 1, y + 1, 1, 3, k)
        var q = 0
        while (q < 2 * hw) { rc(cx - hw + q, y + 4, 2, 1 + (hash2(q, 1, 4) * 3).toInt(), grass); q += 3 }
        for (v in 0 until 3) {
            val vx = cx - hw / 2 + v * hw / 2
            for (j in 0 until 5 + v * 2) rc(vx, y + 4 + j, 1, 1, if (j % 3 != 0) cc(70, 150, 80) else cc(110, 200, 100))
            if (v == 1) rc(vx - 1, y + 9 + v * 2, 3, 1, cc(255, 150, 200))
        }
    }

    private fun buildSprites() {
        val ss = floatArrayOf(1.0f, 1.25f, 0.8f)
        for (i in 0 until 3) cloudBmp[i] = bake(64, 32, false) { cloudDraw(16, 16, ss[i]) }
        val isDay = hour >= 6f && hour <= 18f
        sunBmp = bake(56, 56, false) {
            val c = 28
            if (isDay) {
                disc(c, c, 17, cc(255, 246, 190), 28); disc(c, c, 13, cc(255, 246, 190), 60)
                disc(c, c, 9, cc(255, 244, 170)); disc(c, c, 7, cc(255, 252, 215))
                for (k in 0 until 8) {
                    val a = k * PI.toFloat() / 4f
                    rc(c + (cos(a) * 14f).toInt(), c + (sin(a) * 14f).toInt(), 2, 2, cc(255, 240, 160))
                }
            } else {
                disc(c, c, 20, cc(190, 200, 255), 22); disc(c, c, 15, cc(190, 200, 255), 40)
                disc(c, c, 10, cc(232, 238, 252))
                disc(c - 3, c - 3, 2, cc(196, 206, 232)); disc(c + 3, c + 2, 2, cc(196, 206, 232))
                disc(c - 1, c + 4, 1, cc(196, 206, 232)); disc(c + 4, c - 4, 1, cc(196, 206, 232))
            }
        }
        islL = bake(64, 84, true) { island(32, 48, 36); tree(24, 48); house(40, 48) }
        islR = bake(64, 84, true) {
            island(32, 48, 36); tower(32, 48)
            tintOn = false
            crystal(20, 48, 8, 4, cc(150, 255, 250), cc(60, 200, 220), cc(30, 120, 170))
        }
    }

    // ------------------------------------------------------------------ interface (cache)

    private fun buildTitle() {
        val s = "CUBOLAND"
        val scl = 4
        val cells = PixFont.parse(s)
        titleW = PixFont.width(s, scl)
        val lst = ArrayList<Int>()
        titleBmp = bake(titleW + 4, 7 * scl + 8, false) {
            val ox = 2; val oy = 1
            val grad = intArrayOf(cc(255, 248, 190), cc(255, 232, 140), cc(255, 208, 96), cc(255, 184, 70), cc(244, 146, 60), cc(222, 110, 60), cc(190, 80, 70))
            val xs = ArrayList<Int>(); val ys = ArrayList<Int>(); val rs = ArrayList<Int>()
            for ((i, cell) in cells.withIndex()) {
                for (ry in 0 until 7) for (rx in 0 until 5) {
                    if (((cell.rows[ry] shr (4 - rx)) and 1) != 0) {
                        xs.add(ox + i * 6 * scl + rx * scl); ys.add(oy + ry * scl); rs.add(ry)
                    }
                }
            }
            for (d in 5 downTo 1) for (k in xs.indices) rc(xs[k] - 1, ys[k] + d - 1, scl + 2, scl + 2, if (d > 2) cc(74, 30, 100) else cc(56, 20, 84))
            for (k in xs.indices) rc(xs[k] - 1, ys[k] - 1, scl + 2, scl + 2, INK)
            for (k in xs.indices) {
                val c = grad[rs[k]]
                rc(xs[k], ys[k], scl, scl, c)
                rc(xs[k], ys[k], scl, 1, mixC(c, WHITE, 0.5f))
                rc(xs[k], ys[k] + scl - 1, scl, 1, shadeC(c, 0.78f))
                rc(xs[k] + scl - 1, ys[k], 1, scl, shadeC(c, 0.88f))
                lst.add(xs[k]); lst.add(ys[k])
            }
        }
        titleBlocks = lst.toIntArray()
        titleX = midX - titleW / 2
    }

    private fun wingDraw(flip: Boolean) {
        val rootX = if (flip) 21 else 0
        val dir = if (flip) -1 else 1
        for (i in 0 until 5) {
            val l = 18 - i * 2
            val yy = 1 + i * 3
            val c = mixC(WHITE, cc(200, 170, 255), i / 6f)
            for (k in 0 until l) {
                val xx = rootX + dir * k
                rc(xx, yy, 1, 3, if (k % 5 == 4) shadeC(c, 0.85f) else c)
                rc(xx, yy + 2, 1, 1, shadeC(c, 0.78f))
            }
            rc(rootX + dir * l, yy, 1, 3, INK)
        }
    }

    private fun bakeBtn(w: Int, h: Int, base: Int, pressed: Boolean, label: String, ico: Array<String>?, play: Boolean): Bitmap {
        return bake(w, h + 3, false) {
            val off0 = if (pressed) 2 else 0
            val lip = 3
            val top = off0
            val hh = h - lip
            val light = mixC(base, WHITE, 0.35f); val dark = shadeC(base, 0.62f)
            val gold = cc(255, 208, 96); val goldL = cc(255, 240, 170); val goldD = cc(196, 128, 48)
            rc(1, top + lip, w - 2, hh, INK); rc(0, top + lip + 1, w, hh - 2, INK); rc(1, top + lip + 1, w - 2, hh - 2, goldD)
            rc(1, top, w - 2, hh, INK); rc(0, top + 1, w, hh - 2, INK)
            rc(1, top + 1, w - 2, hh - 2, gold)
            rc(1, top + 1, w - 2, 1, goldL); rc(1, top + 1, 1, hh - 2, goldL)
            rc(1, top + hh - 2, w - 2, 1, goldD); rc(w - 2, top + 1, 1, hh - 2, goldD)
            val fx = 3; val fy = top + 3; val fw = w - 6; val fh = hh - 6
            for (yy in 0 until fh) {
                val f = yy / max(1, fh - 1).toFloat()
                val c = if (f < 0.45f) mixC(light, base, min(1f, f * 2.2f)) else mixC(base, dark, (f - 0.45f) / 0.55f)
                rc(fx, fy + yy, fw, 1, c)
                if (f > 0.05f && f < 0.2f) {
                    var xx = yy % 2
                    while (xx < fw) { rc(fx + xx, fy + yy, 1, 1, shadeC(c, 1.07f)); xx += 2 }
                }
            }
            val rx0 = intArrayOf(3, w - 5, 3, w - 5); val ry0 = intArrayOf(top + 3, top + 3, top + hh - 5, top + hh - 5)
            for (i in 0 until 4) { rc(rx0[i], ry0[i], 2, 2, goldD); rc(rx0[i], ry0[i], 1, 1, goldL) }
            val tw = PixFont.width(label, 1)
            val iw = if (ico != null) 11 else if (play) 8 else 0
            val sx = (w - (tw + iw)) / 2
            val ty = top + (hh - 7) / 2
            txt(label, sx + iw, ty + 1, 1, shadeC(base, 0.42f))
            txt(label, sx + iw, ty, 1, WHITE)
            if (ico != null) icon(ico, sx, ty, WHITE, base)
            if (play && !pressed) { playOffX = sx; playOffY = ty }
        }
    }

    private fun roundBtn(col: Int, ico: Array<String>): Bitmap = bake(20, 22, false) {
        val cx = 10; val cy = 10
        disc(cx, cy + 2, 9, INK); disc(cx, cy + 2, 8, shadeC(col, 0.55f))
        disc(cx, cy, 9, INK); disc(cx, cy, 8, cc(255, 208, 96)); disc(cx, cy, 7, col)
        disc(cx - 1, cy - 2, 3, mixC(col, WHITE, 0.4f))
        icon(ico, cx - 4, cy - 4, WHITE, col)
    }

    private fun plaqueBmp(label: String, lit: Boolean): Bitmap {
        val tw = PixFont.width(label, 1)
        return bake(tw + 20, 11, false) {
            val w = tw + 20
            val wood = if (lit) cc(150, 104, 80) else cc(96, 92, 130)
            val stud = if (lit) cc(255, 220, 120) else cc(150, 160, 210)
            rc(0, 0, w, 11, INK); rc(1, 1, w - 2, 9, wood)
            rc(1, 1, w - 2, 1, mixC(wood, WHITE, 0.25f)); rc(1, 8, w - 2, 2, shadeC(wood, 0.7f))
            rc(2, 2, 1, 1, stud); rc(w - 3, 2, 1, 1, stud)
            txt(label, 13, 2, 1, if (lit) cc(255, 246, 220) else cc(190, 196, 235))
        }
    }

    private fun buildUi() {
        val oy = max(0, (H - 180) / 2)
        titleY = 7
        blockY = 62 + oy
        buildTitle()
        wingL = bake(22, 17, false) { wingDraw(true) }
        wingR = bake(22, 17, false) { wingDraw(false) }

        val lbl = "CONSTRUA E LUTE!"
        val rw0 = PixFont.width(lbl, 1) + 16
        val rcol = cc(200, 80, 150)
        ribbonBmp = bake(rw0 + 20, 14, false) {
            val ox = 10
            val dark = shadeC(rcol, 0.6f)
            rc(ox - 9, 3, 10, 9, INK); rc(ox - 8, 4, 9, 7, dark)
            rc(ox + rw0 - 1, 3, 10, 9, INK); rc(ox + rw0, 4, 9, 7, dark)
            rc(ox, 0, rw0, 12, INK); rc(ox + 1, 1, rw0 - 2, 10, rcol)
            rc(ox + 1, 1, rw0 - 2, 1, mixC(rcol, WHITE, 0.45f)); rc(ox + 1, 9, rw0 - 2, 2, shadeC(rcol, 0.82f))
            val tx = ox + (rw0 - PixFont.width(lbl, 1)) / 2
            txt(lbl, tx, 3, 1, shadeC(rcol, 0.4f)); txt(lbl, tx, 2, 1, WHITE)
        }
        ribbonX = midX - (rw0 + 20) / 2

        val bW = 112
        val hs = intArrayOf(28, 19, 19)
        val ys = intArrayOf(blockY, blockY + 46, blockY + 68)
        val bases = intArrayOf(cc(70, 196, 110), cc(150, 100, 230), cc(80, 160, 240))
        val labels = arrayOf("JOGAR", "PERSONAGEM", "AJUSTES")
        for (i in 0 until 3) {
            bx[i] = midX - bW / 2; by[i] = ys[i]; bw[i] = bW; bh[i] = hs[i] + 1
            val ico = if (i == 1) ICO_FACE else if (i == 2) ICO_GEAR else null
            bUp[i] = bakeBtn(bW, hs[i], bases[i], false, labels[i], ico, i == 0)
            bDn[i] = bakeBtn(bW, hs[i], bases[i], true, labels[i], ico, i == 0)
        }
        bx[3] = W - 22; by[3] = H - 66; bw[3] = 20; bh[3] = 22
        bx[4] = W - 22; by[4] = H - 44; bw[4] = 20; bh[4] = 22
        bUp[3] = roundBtn(cc(160, 118, 232), ICO_STAR); bDn[3] = bUp[3]
        bUp[4] = roundBtn(cc(60, 190, 180), ICO_REFRESH); bDn[4] = bUp[4]

        val st = continueText
        val stw = PixFont.width(st, 1)
        scrollBmp = bake(stw + 22, 13, false) {
            val ox = 4; val y0 = 1; val w = stw + 14
            val parch = cc(255, 236, 196); val parchD = cc(230, 200, 150); val edge = cc(196, 150, 100)
            rc(ox, y0, w, 11, INK); rc(ox + 1, y0 + 1, w - 2, 9, parch)
            rc(ox + 1, y0 + 1, w - 2, 1, cc(255, 250, 226)); rc(ox + 1, y0 + 8, w - 2, 2, parchD)
            rc(ox - 3, y0 - 1, 4, 13, INK); rc(ox - 2, y0, 2, 11, edge); rc(ox - 2, y0, 1, 11, cc(224, 180, 130))
            rc(ox + w - 1, y0 - 1, 4, 13, INK); rc(ox + w, y0, 2, 11, edge); rc(ox + w + 1, y0, 1, 11, cc(150, 104, 70))
            txt(st, ox + 7, y0 + 2, 1, cc(70, 36, 50))
        }
        scrollX = midX - (stw + 22) / 2
        scrollY = blockY + 32 - 1

        plaqueMe = plaqueBmp("JOGADOR 1", true)
        plaqueOther = plaqueBmp("AGUARDANDO", false)

        val vs = "CUBOLAND V$versionName"
        footL = bake(PixFont.width(vs, 1) + 2, 10, false) { txt(vs, 0, 1, 1, INK); txt(vs, 0, 0, 1, WHITE) }
        val a1 = "FEITO COM"; val a2 = "E CUBOS"
        val w1 = PixFont.width(a1, 1); val w2 = PixFont.width(a2, 1)
        footC = bake(w1 + w2 + 16, 10, false) {
            txt(a1, 0, 1, 1, INK); txt(a1, 0, 0, 1, WHITE)
            icon(ICO_HEART, w1 + 3, 1, INK, INK); icon(ICO_HEART, w1 + 3, 0, cc(255, 90, 120), cc(255, 190, 205))
            txt(a2, w1 + 13, 1, 1, INK); txt(a2, w1 + 13, 0, 1, WHITE)
        }
        bubbleQ = bake(13, 16, false) {
            rc(0, 0, 13, 13, INK); rc(1, 1, 11, 11, WHITE); rc(5, 13, 3, 3, INK); rc(6, 13, 1, 2, WHITE)
            txt("?", 4, 3, 1, cc(110, 100, 200))
        }
        val sw = PixFont.width("EM BREVE!", 1) + 10
        bubbleSoon = bake(sw, 16, false) {
            rc(0, 0, sw, 13, INK); rc(1, 1, sw - 2, 11, WHITE); rc(sw / 2 - 1, 13, 3, 3, INK); rc(sw / 2, 13, 1, 2, WHITE)
            txt("EM BREVE!", 5, 3, 1, cc(110, 100, 200))
        }
    }

    /** silhueta do futuro jogador 2: outro visual do próprio modelo, pintado como fantasma */
    private fun buildGhost() {
        try {
            val gv = CharView(context, CW, CH).apply { transparentBg = true; sway = false; shadow = false }
            val saved = Look.v
            try {
                Look.v = Look.PRESETS[(saved[Look.HAIR] + 3) % Look.PRESETS.size].copyOf()
                gv.setYaw(-24f)
                gv.step(0f)
            } finally {
                Look.v = saved
            }
            val src = IntArray(CW * CH)
            gv.bitmap.getPixels(src, 0, CW, 0, 0, CW, CH)
            val out = IntArray(CW * CH)
            for (y in 0 until CH) for (x in 0 until CW) {
                val c = src[y * CW + x]
                if ((c ushr 24) < 40) continue
                val lum = (Color.red(c) * 0.3f + Color.green(c) * 0.59f + Color.blue(c) * 0.11f) / 255f
                var edge = x == 0 || y == 0 || x == CW - 1 || y == CH - 1
                if (!edge) {
                    edge = (src[y * CW + x - 1] ushr 24) < 40 || (src[y * CW + x + 1] ushr 24) < 40 ||
                        (src[(y - 1) * CW + x] ushr 24) < 40 || (src[(y + 1) * CW + x] ushr 24) < 40
                }
                out[y * CW + x] = if (edge) cc(150, 230, 255) else mixC(cc(46, 34, 110), cc(150, 130, 235), min(1f, lum * 0.7f))
            }
            val b = Bitmap.createBitmap(CW, CH, Bitmap.Config.ARGB_8888)
            b.setPixels(out, 0, CW, 0, 0, CW, CH)
            ghostBmp = b
        } catch (e: Exception) {
            ghostBmp = null
        }
        ghostLight = PorterDuffColorFilter(Color.argb(130, 200, 255, 255), PorterDuff.Mode.SRC_ATOP)
    }

    // ------------------------------------------------------------------ desenho (animado)

    override fun onDraw(c: Canvas) {
        val sky = skyBmp ?: return
        val land = landBmp ?: return
        val now = System.nanoTime()
        val dt = if (lastNs == 0L) 0.016f else min(0.05f, (now - lastNs) / 1e9f)
        lastNs = now
        t += dt
        introT += dt
        if (t - builtAt > 240f) rebuild(false)
        if (needUi) { buildUi(); needUi = false }
        me.step(dt)

        cnv = c
        tintOn = false
        c.save()
        c.scale(sc.toFloat(), sc.toFloat())
        c.drawBitmap(sky, 0f, 0f, BP)
        drawStars()
        drawCelestial()
        drawClouds()
        drawShooting()
        drawIslands()
        c.drawBitmap(land, 0f, 0f, BP)
        drawTufts()
        drawBeams()
        drawRunes()
        drawGhost()
        drawMe()
        drawSlime()
        drawPlaques()
        drawMotes()
        drawFireflies()
        drawButterflies()
        drawPetals()
        drawIntroFx()
        drawUi()
        c.restore()
        postInvalidateOnAnimation()
    }

    private fun drawStars() {
        if (night < 0.05f) return
        val col = cc(255, 255, 235)
        for (i in 0 until 90) {
            val sx = (hash2(i, 1, 21) * W).toInt()
            val sy = (hash2(i, 2, 22) * H * 0.6f).toInt()
            val tw = 0.5f + 0.5f * sin(t * (1f + hash2(i, 3, 23) * 2f) + i)
            val a = (night * 255f * (0.4f + 0.6f * tw)).toInt().coerceIn(0, 255)
            rc(sx, sy, 1, 1, col, a)
            if (hash2(i, 4, 24) > 0.82f) { rc(sx - 1, sy, 3, 1, col, a / 2); rc(sx, sy - 1, 1, 3, col, a / 2) }
        }
    }

    private fun drawCelestial() {
        val b = sunBmp ?: return
        val isDay = hour >= 6f && hour <= 18f
        val frac = if (isDay) (hour - 6f) / 12f else ((hour + 24f - 18f) % 24f) / 12f
        val sx = (W * (0.1f + 0.8f * frac)).toInt()
        val sy = (H * (0.5f - 0.38f * sin(PI.toFloat() * frac))).toInt()
        cnv.drawBitmap(b, (sx - 28).toFloat(), (sy - 28).toFloat(), BP)
    }

    private fun drawClouds() {
        val cy = intArrayOf(118, 98, 124, 74, 30)
        val sp = floatArrayOf(2.2f, 1.5f, 1.0f, 1.8f, 1.2f)
        val bs = intArrayOf(40, 300, 180, 120, 340)
        for (i in 0 until 5) {
            val b = cloudBmp[i % 3] ?: continue
            val x = ((bs[i] * W / 400f + t * sp[i]) % (W + 120f)) - 60f
            cnv.drawBitmap(b, (x.toInt() - 16).toFloat(), (cy[i] - 16).toFloat(), BP)
        }
    }

    private fun drawShooting() {
        if (night < 0.4f) return
        val k = t % 12f
        if (k > 0.8f) return
        val p = k / 0.8f
        val hx = (W * 0.85f - p * W * 0.35f).toInt()
        val hy = (6f + p * 55f).toInt()
        for (j in 0 until 8) {
            val a = ((1f - j / 8f) * (1f - p * 0.6f) * 255f).toInt()
            rc(hx + j * 2, hy - j, 2, 1, WHITE, a)
        }
    }

    private fun drawIslands() {
        val bl = islL
        val br = islR
        val b0 = (sin(t * 0.8f) * 2f).roundToInt()
        val b1 = (sin(t * 0.8f + 2f) * 2f).roundToInt()
        if (bl != null) {
            val ix = 22 - 32
            cnv.drawBitmap(bl, ix.toFloat(), (50 - 48 + b0).toFloat(), BP)
            for (i in 0 until 3) {
                val ph = (t * 0.35f + i / 3f) % 1f
                val sy = 50 + b0 - 25 - (ph * 14f).toInt()
                val sx = ix + 46 + (sin(t + i) * 2f).toInt()
                rc(sx, sy, 2 + (ph * 2f).toInt(), 2, cc(240, 240, 255), ((1f - ph) * 150f).toInt())
            }
        }
        if (br != null) {
            val ix = W - 22 - 32
            cnv.drawBitmap(br, ix.toFloat(), (54 - 48 + b1).toFloat(), BP)
            val fx = W - 22 + 1
            val fy = 54 + b1 - 43
            for (k in 0 until 5) rc(fx + k, fy + (sin(t * 6f - k * 0.9f) * 1f).roundToInt(), 1, 3, cc(255, 90, 120))
        }
    }

    private fun drawTufts() {
        tintOn = true
        for (i in 0 until 34) {
            val x = (hash2(i, 9, 81) * W).toInt()
            if (abs(x - midX) < 58) continue
            val y = meadowY(x) + 3 + (hash2(i, 8, 82) * 10).toInt()
            val s = (sin(t * 2.2f + i * 0.8f) * 1.4f).roundToInt()
            rc(x, y - 2, 1, 3, cc(100, 210, 90))
            rc(x + s, y - 4, 1, 2, cc(130, 230, 110))
            rc(x - 1 - s, y - 3, 1, 2, cc(86, 190, 84))
            rc(x + 1 + s, y - 3, 1, 2, cc(120, 224, 100))
        }
        tintOn = false
    }

    private fun drawBeams() {
        val lx = midX - off
        val rx = midX + off
        val pa = (16 + 7 * sin(t * 1.5f)).toInt()
        rc(lx - 12, 0, 24, platY - 3, cc(150, 255, 240), pa)
        rc(lx - 4, 0, 8, platY - 3, WHITE, pa)
        val fa = if (sin(t * 7f) * sin(t * 2.3f) > 0.6f) 5 else 10
        rc(rx - 8, 0, 16, platY - 3, cc(130, 140, 255), fa)
    }

    private fun drawRunes() {
        runes(midX - off, true, cc(110, 255, 230))
        runes(midX + off, false, cc(150, 160, 255))
    }

    private fun runes(cx: Int, lit: Boolean, col: Int) {
        val rw = 40; val rh = 9
        val k = if (lit) min(1f, introT / 0.5f) else 1f
        val rot = if (lit) t * 0.4f else -t * 0.15f
        val two = 2f * PI.toFloat()
        for (i in 0 until 14) {
            val a = i / 14f * two + rot
            val rx = cx + (cos(a) * (rw - 9)).toInt()
            val ry = platY - 1 + (sin(a) * (rh - 5)).toInt()
            if (sin(a) > -0.9f) {
                if (lit) {
                    val fl = 0.6f + 0.4f * sin(t * 2f + i)
                    rc(rx - 1, ry - 1, 4, 4, col, (70f * fl * k).toInt())
                    rc(rx, ry, 2, 2, col, (255f * k).toInt())
                } else {
                    rc(rx, ry, 2, 2, cc(90, 100, 150), 200)
                }
            }
        }
        for (i in 0 until 48) {
            val a = i / 48f * two
            rc(cx + (cos(a) * (rw - 14)).toInt(), platY - 1 + (sin(a) * (rh - 6)).toInt(), 1, 1, if (lit) col else cc(80, 90, 140), if (lit) (160f * k).toInt() else 90)
        }
    }

    private fun flashFilter(k: Float): PorterDuffColorFilter {
        val i = (k * 16f).toInt().coerceIn(0, 16)
        var f = flashF[i]
        if (f == null) {
            f = PorterDuffColorFilter(Color.argb(i * 255 / 16, 255, 255, 255), PorterDuff.Mode.SRC_ATOP)
            flashF[i] = f
        }
        return f
    }

    private fun drawMe() {
        val x0 = midX - off - CW / 2
        val y0 = platY - 1 - CFEET
        val rev = ((introT - 0.15f) / 0.8f).coerceIn(0f, 1f)
        if (rev <= 0f) return
        val vis = (rev * CH).toInt()
        cnv.save()
        cnv.clipRect(x0.toFloat(), (y0 + CH - vis).toFloat(), (x0 + CW).toFloat(), (y0 + CH).toFloat())
        val flash = (1f - (introT - 0.5f) / 0.8f).coerceIn(0f, 1f)
        BP.colorFilter = if (flash > 0.04f) flashFilter(flash) else null
        cnv.drawBitmap(me.bitmap, x0.toFloat(), y0.toFloat(), BP)
        BP.colorFilter = null
        cnv.restore()
        if (rev < 1f) ell(midX - off, y0 + CH - vis, 30, 3, WHITE, 140)
    }

    private fun drawGhost() {
        val gb = ghostBmp
        val rx = midX + off
        ell(rx, platY - 1, 18, 4, INK, 70)
        if (gb == null) return
        val since = t - pokeT
        val shake = if (since < 0.5f) (sin(since * 50f) * 3f * (1f - since / 0.5f)).roundToInt() else 0
        val bob = (sin(t * 1.6f) * 2f).roundToInt()
        val x0 = rx - CW / 2 + shake
        val y0 = platY - 1 - CFEET + bob - 4
        BP.alpha = (120 + 40 * sin(t * 2.2f)).toInt()
        cnv.drawBitmap(gb, x0.toFloat(), y0.toFloat(), BP)
        BP.alpha = 255
        val sweep = ((t * 0.5f) % 1.6f) * 1.5f
        val by0 = y0 + CH - (sweep * CH).toInt()
        if (sweep < 1f) {
            cnv.save()
            cnv.clipRect(x0.toFloat(), by0.toFloat(), (x0 + CW).toFloat(), (by0 + 9).toFloat())
            BP.alpha = 150
            BP.colorFilter = ghostLight
            cnv.drawBitmap(gb, x0.toFloat(), y0.toFloat(), BP)
            BP.colorFilter = null
            BP.alpha = 255
            cnv.restore()
        }
        val q = bubbleQ
        if (q != null && since > 1.6f) cnv.drawBitmap(q, (rx - 36).toFloat(), (platY - 1 - 101 + 2 + (sin(t * 2.4f) * 2f).roundToInt()).toFloat(), BP)
        val sb = bubbleSoon
        if (sb != null && since < 1.6f) cnv.drawBitmap(sb, (rx - sb.width / 2).toFloat(), (platY - 1 - 112).toFloat(), BP)
    }

    private fun drawSlime() {
        val ph = t % 2.8f
        val bx0 = midX - off + 33
        var w = 9
        var h = 6
        var lift = 0
        if (ph < 0.55f) {
            lift = (sin(ph / 0.55f * PI.toFloat()) * 10f).toInt(); w = 8; h = 7
        } else if (ph < 0.7f) {
            w = 11; h = 4
        }
        val x = bx0 - w / 2
        val y = platY - 3 - h - lift
        tintOn = true
        if (lift > 0) ell(bx0, platY - 1, 4, 1, INK, 70)
        rc(x + 2, y - 1, w - 4, 1, INK)
        rc(x + 1, y + h, w - 2, 1, INK)
        for (r in 0 until h) {
            val ins = if (r == 0) 2 else if (r == 1 || r == h - 1) 1 else 0
            rc(x + ins - 1, y + r, w - 2 * ins + 2, 1, INK)
            rc(x + ins, y + r, w - 2 * ins, 1, if (r == h - 1) cc(70, 150, 220) else if (r < h / 2) cc(150, 215, 255) else cc(110, 190, 250))
        }
        rc(x + 2, y + 1, 2, 1, WHITE)
        val blink = (t % 3.3f) < 0.12f
        val ey = y + h / 2
        rc(x + w / 3, ey, 1, if (blink) 1 else 2, INK)
        rc(x + w - w / 3 - 1, ey, 1, if (blink) 1 else 2, INK)
        tintOn = false
    }

    private fun drawPlaques() {
        val pm = plaqueMe
        val po = plaqueOther
        val y = platY + 3
        if (pm != null) {
            val x = midX - off - pm.width / 2
            cnv.drawBitmap(pm, x.toFloat(), y.toFloat(), BP)
            val pulse = (150 + 105 * sin(t * 3f)).toInt()
            rc(x + 5, y + 4, 3, 3, cc(90, 230, 120), pulse)
            rc(x + 5, y + 4, 1, 1, WHITE, pulse)
        }
        if (po != null) {
            val x = midX + off - po.width / 2
            cnv.drawBitmap(po, x.toFloat(), y.toFloat(), BP)
            val pulse = (130 + 120 * sin(t * 2f)).toInt()
            rc(x + 5, y + 4, 3, 3, cc(255, 190, 80), pulse)
        }
    }

    private fun drawMotes() {
        val lx = midX - off
        val pi = PI.toFloat()
        for (i in 0 until 14) {
            val ph = (t * 0.32f + i / 14f) % 1f
            val x = lx + ((hash2(i, 1, 61) - 0.5f) * 64f).toInt() + (sin(t * 1.3f + i) * 3f).toInt()
            val y = platY - 4 - (ph * 100f).toInt()
            val a = (sin(ph * pi) * 230f).toInt()
            if (i % 4 == 0) sparkle(x, y, 2, motePal[i % 4], a) else rc(x, y, 2, 2, motePal[i % 4], a)
        }
        for (i in 0 until 26) {
            val x = ((hash2(i, 5, 62) * W + sin(t * 0.4f + i) * 6f) + W) % W
            val y = H * (0.2f + 0.6f * hash2(i, 6, 63)) - ((t * (3f + hash2(i, 7, 64) * 5f)) % (H * 0.3f))
            val tw = 0.4f + 0.6f * abs(sin(t * 1.3f + i * 1.7f))
            rc(x.toInt(), y.toInt(), 1, 1, motePal[i % 4], (150f * tw).toInt())
        }
    }

    private fun drawFireflies() {
        if (night < 0.3f) return
        for (i in 0 until 16) {
            val x = (((hash2(i, 1, 71) * W + sin(t * 0.5f + i * 1.7f) * 12f) + W) % W).toInt()
            val y = (H * (0.55f + 0.38f * hash2(i, 2, 72)) + sin(t * 0.8f + i) * 6f).toInt()
            val tw = 0.4f + 0.6f * abs(sin(t * 1.3f + i * 1.7f))
            val a = (night * 230f * tw).toInt()
            rc(x - 1, y - 1, 4, 4, cc(255, 240, 130), a / 4)
            rc(x, y, 2, 2, cc(255, 246, 170), a)
        }
    }

    private fun drawButterflies() {
        if (night > 0.6f) return
        val al = ((0.6f - night) / 0.6f * 255f).toInt().coerceIn(0, 255)
        for (k in 0 until 2) {
            val x = midX - off + (cos(t * 0.6f + k * 2.4f) * 56f).toInt()
            val y = platY - 50 + (sin(t * 1.1f + k * 2.0f) * 16f).toInt() - k * 10
            val col = if (k == 0) cc(255, 140, 200) else cc(255, 224, 110)
            if (((t * 9f).toInt() + k) % 2 == 0) {
                rc(x - 3, y - 2, 3, 3, col, al); rc(x + 1, y - 2, 3, 3, col, al)
                rc(x - 2, y + 1, 2, 2, shadeC(col, 0.8f), al); rc(x + 1, y + 1, 2, 2, shadeC(col, 0.8f), al)
            } else {
                rc(x - 1, y - 2, 3, 4, col, al)
            }
            rc(x, y - 1, 1, 3, INK, al)
        }
    }

    private fun drawPetals() {
        for (i in 0 until 7) {
            val x = ((hash2(i, 1, 91) * W + t * (6f + i)) % (W + 20f)) - 10f
            val y = ((hash2(i, 2, 92) * H + t * (9f + i * 1.5f)) % (H + 10f)) - 5f
            val col = if (night > 0.5f) cc(190, 170, 255) else cc(255, 170, 200)
            rc(x.toInt(), y.toInt(), 2, 1, col, 200)
            rc(x.toInt() + 1, y.toInt() + 1, 1, 1, col, 200)
        }
    }

    private fun burst(cx: Int, cy: Int, bt: Float, dur: Float) {
        if (bt < 0f || bt > dur) return
        for (i in 0 until 28) {
            val a = hash2(i, 1, 51) * 2f * PI.toFloat()
            val v = 18f + hash2(i, 2, 52) * 46f
            val sx = cx + (cos(a) * v * bt).toInt()
            val sy = cy + (sin(a) * v * bt * 0.8f).toInt() + (bt * bt * 26f).toInt()
            val al = (255f * (1f - bt / dur)).toInt()
            val col = motePal[i % 4]
            rc(sx, sy, 2, 2, col, al)
            if (i % 3 == 0) sparkle(sx, sy, 2, col, al)
        }
    }

    private fun drawIntroFx() {
        val cx = midX - off
        val py = platY - 1
        if (introT < 1.9f) {
            val a = (170f * (1f - introT / 1.9f)).toInt()
            rc(cx - 14, 0, 28, py, cc(150, 255, 240), a / 4)
            rc(cx - 8, 0, 16, py, cc(190, 255, 250), a / 3)
            rc(cx - 3, 0, 6, py, WHITE, a / 2)
        }
        val rt = introT - 0.2f
        if (rt >= 0f && rt <= 1.2f) {
            val rr = rt / 1.2f * 52f
            val al = (220f * (1f - rt / 1.2f)).toInt()
            for (i in 0 until 64) {
                val a = i / 64f * 2f * PI.toFloat()
                rc(cx + (cos(a) * rr).toInt(), py + (sin(a) * rr * 0.22f).toInt(), 2, 1, cc(170, 255, 240), al)
            }
        }
        burst(cx, py - 30, introT - 0.45f, 1.3f)
        burst(cx, py - 40, t - tapT, 1.0f)
    }

    private fun drawUi() {
        val cnv0 = cnv
        // fita e título
        val rb = ribbonBmp
        if (rb != null) cnv0.drawBitmap(rb, ribbonX.toFloat(), (titleY + 37).toFloat(), BP)
        val wb = (sin(t * 2f) * 1f).roundToInt()
        val wl = wingL
        val wr = wingR
        if (wl != null) cnv0.drawBitmap(wl, (titleX - 2 - 21).toFloat(), (titleY + 3 + wb).toFloat(), BP)
        if (wr != null) cnv0.drawBitmap(wr, (titleX + titleW + 2).toFloat(), (titleY + 3 + wb).toFloat(), BP)
        val tb = titleBmp
        if (tb != null) {
            val ty = titleY - 1 + (sin(t * 1.4f) * 1f).roundToInt()
            cnv0.drawBitmap(tb, (titleX - 2).toFloat(), ty.toFloat(), BP)
            val band = (t * 42f) % (titleW + 140f) - 50f
            var i = 0
            while (i + 1 < titleBlocks.size) {
                val d = (titleBlocks[i] + titleBlocks[i + 1] * 0.7f) - band
                if (d > -7f && d < 7f) rc(titleX - 2 + titleBlocks[i], ty + titleBlocks[i + 1], 4, 4, WHITE, 120)
                i += 2
            }
            for (k in 0 until 5) {
                val cyc = t * 0.7f + k * 0.31f
                val idx = cyc.toInt()
                val ph = cyc - idx
                val r = if (ph < 0.5f) (ph * 6f).toInt() else ((1f - ph) * 6f).toInt()
                if (r >= 1) sparkle(titleX + (hash2(idx, k, 1) * titleW).toInt(), titleY + (hash2(idx, k, 2) * 26f).toInt(), r, cc(255, 250, 200), 255)
            }
        }
        // botões
        for (i in 0 until 5) {
            val pr = pressedBtn == i
            val b = (if (pr) bDn[i] else bUp[i]) ?: continue
            val yy = by[i] + (if (pr && i >= 3) 2 else 0)
            cnv0.drawBitmap(b, bx[i].toFloat(), yy.toFloat(), BP)
        }
        // brilho e triângulo do JOGAR
        val dy = if (pressedBtn == 0) 2 else 0
        val fx = bx[0] + 3
        val fy = by[0] + 3 + dy
        val fw = bw[0] - 6
        val fh = bh[0] - 1 - 3 - 6
        val sweep = ((t * 36f) % 190f).toInt() - 40
        for (r in 0 until fh) {
            var x1 = fx + sweep + (fh - r) / 2
            var x2 = x1 + 6
            x1 = max(x1, fx)
            x2 = min(x2, fx + fw)
            if (x2 > x1) rc(x1, fy + r, x2 - x1, 1, WHITE, 50)
        }
        val px = bx[0] + playOffX + (sin(t * 4f) * 1f).roundToInt()
        val py = by[0] + playOffY + dy
        for (r in 0 until 7) {
            val w = 4 - abs(r - 3)
            rc(px, py + r + 1, w, 1, shadeC(cc(70, 196, 110), 0.42f))
            rc(px, py + r, w, 1, WHITE)
        }
        for (k in 0 until 4) {
            val ph = (t * 0.9f + k * 0.37f) % 1f
            val r = if (ph < 0.5f) (ph * 6f).toInt() else ((1f - ph) * 6f).toInt()
            if (r >= 1) {
                val sx = bx[0] + intArrayOf(6, bw[0] - 6, bw[0] / 3, bw[0] * 2 / 3)[k]
                val sy = by[0] + intArrayOf(3, 2, bh[0] - 3, bh[0] - 4)[k]
                sparkle(sx, sy, r, cc(255, 250, 200), 255)
            }
        }
        // pergaminho
        val sb = scrollBmp
        if (sb != null) cnv0.drawBitmap(sb, scrollX.toFloat(), scrollY.toFloat(), BP)
        // selo de novidades
        if (hasNews) {
            val ny = by[3] + (if (pressedBtn == 3) 2 else 0)
            val pu = (200 + 55 * sin(t * 5f)).toInt()
            disc(bx[3] + 17, ny + 3, 3, INK)
            disc(bx[3] + 17, ny + 3, 2, cc(240, 70, 80), pu)
            rc(bx[3] + 16, ny + 2, 1, 1, cc(255, 200, 200))
        }
        // rodapé
        val fl = footL
        val fc = footC
        if (fl != null) cnv0.drawBitmap(fl, 4f, (H - 11).toFloat(), BP)
        if (fc != null) cnv0.drawBitmap(fc, (midX - fc.width / 2).toFloat(), (H - 11).toFloat(), BP)
    }

    // ------------------------------------------------------------------ toque

    private fun hitBtn(lx: Int, ly: Int): Int {
        for (i in 0 until 5) {
            val p = if (i >= 3) 0 else 2
            if (lx >= bx[i] - p && lx < bx[i] + bw[i] + p && ly >= by[i] - p && ly < by[i] + bh[i] + p) return i
        }
        return -1
    }

    private fun inChar(lx: Int, ly: Int): Boolean {
        val cx = midX - off
        return lx >= cx - 34 && lx <= cx + 34 && ly >= platY - 110 && ly <= platY
    }

    private fun inGhost(lx: Int, ly: Int): Boolean {
        val cx = midX + off
        return lx >= cx - 34 && lx <= cx + 34 && ly >= platY - 110 && ly <= platY
    }

    private fun fire(i: Int) {
        when (i) {
            0 -> onPlay()
            1 -> onChar()
            2 -> onSettings()
            3 -> onNews()
            4 -> onUpdate()
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (W == 0) return false
        val lx = (e.x / sc).toInt()
        val ly = (e.y / sc).toInt()
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressedBtn = hitBtn(lx, ly)
                dragging = false
                ghostPress = false
                tapMoved = false
                downX = e.x
                downY = e.y
                if (pressedBtn >= 0) {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                } else if (inChar(lx, ly)) {
                    dragging = true
                    me.dragStart(e.x)
                } else if (inGhost(lx, ly)) {
                    ghostPress = true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(e.x - downX) > 14f || abs(e.y - downY) > 14f) tapMoved = true
                if (dragging) me.dragMove(e.x, 0.45f)
                if (pressedBtn >= 0 && hitBtn(lx, ly) != pressedBtn) pressedBtn = -1
            }
            MotionEvent.ACTION_UP -> {
                val b = pressedBtn
                pressedBtn = -1
                if (b >= 0 && hitBtn(lx, ly) == b) {
                    fire(b)
                } else if (dragging && !tapMoved) {
                    me.hopNow()
                    tapT = t
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                } else if (ghostPress && !tapMoved && inGhost(lx, ly)) {
                    pokeT = t
                    performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                }
                if (dragging) me.dragEnd()
                dragging = false
                ghostPress = false
            }
            MotionEvent.ACTION_CANCEL -> {
                pressedBtn = -1
                if (dragging) me.dragEnd()
                dragging = false
                ghostPress = false
            }
        }
        return true
    }
}
