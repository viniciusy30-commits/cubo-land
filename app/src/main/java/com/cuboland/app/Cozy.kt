package com.cuboland.app

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Paleta "aconchegante": madeira escura, creme, dourado e cores de natureza. */
object Cz {
    val INK = Color.rgb(36, 28, 48)
    val PANEL = Color.rgb(48, 46, 84)
    val PANEL2 = Color.rgb(68, 66, 112)
    val PANEL3 = Color.rgb(34, 33, 62)
    val GOLD = Color.rgb(255, 212, 99)
    val CREAM = Color.rgb(255, 243, 219)
    val GREEN = Color.rgb(88, 196, 92)
    val SKY = Color.rgb(84, 168, 240)
    val ORANGE = Color.rgb(255, 150, 66)
    val LILAC = Color.rgb(160, 118, 232)
    val RED = Color.rgb(232, 86, 86)
    val STONE = Color.rgb(112, 120, 142)
    val TEAL = Color.rgb(60, 190, 180)
    val SOFT = Color.argb(200, 255, 255, 255)
}

fun Context.uiFont(): Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

fun Activity.goFullscreen() {
    WindowCompat.setDecorFitsSystemWindows(window, false)
    WindowInsetsControllerCompat(window, window.decorView).apply {
        hide(WindowInsetsCompat.Type.systemBars())
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}

/** Activity base: sempre em tela cheia (imersivo). */
open class CozyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        goFullscreen()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) goFullscreen()
    }
}

/** Tamanho do "pixel" das telas internas (mesmo visual do menu principal). */
fun Context.pxU(): Int = max(2, (resources.displayMetrics.density * 1.5f).roundToInt())

/**
 * Caixa pixel art no estilo do menu principal: contorno escuro com cantos recortados, moldura dourada,
 * chanfro de luz e sombra. lip = botão com "lábio" embaixo; radius >= 14 = painel; menor = chip/campo.
 */
class CozyBox(private val ctx: Context, var fill: Int, private val radiusDp: Float, private val lip: Boolean,
              private val outline: Int = Cz.INK) : Drawable() {
    var pressed = false
    private val p = Paint()

    private fun rect(c: Canvas, x: Int, y: Int, w: Int, h: Int, col: Int) {
        if (w <= 0 || h <= 0) return
        p.color = col
        c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), p)
    }

    private fun disc(c: Canvas, cx: Int, cy: Int, r: Int, u: Int, col: Int) {
        var yy = -r
        while (yy < r) {
            val my = yy + u / 2f
            val hw = sqrt(max(0f, r * r - my * my))
            val q = ((hw + u / 2f) / u).toInt() * u
            rect(c, cx - q, cy + yy, 2 * q, u, col)
            yy += u
        }
    }

    private fun box(c: Canvas, w: Int, h: Int, u: Int, face: Int, frame: Int, ft: Int, studs: Boolean) {
        rect(c, u, 0, w - 2 * u, h, Cz.INK); rect(c, 0, u, w, h - 2 * u, Cz.INK)
        val a = u + ft
        if (ft > 0) {
            rect(c, a, u, w - 2 * a, ft, shadeC(frame, 1.15f))
            rect(c, a, h - u - ft, w - 2 * a, ft, shadeC(frame, 0.7f))
            rect(c, u, a, ft, h - 2 * a, shadeC(frame, 1.15f))
            rect(c, w - u - ft, a, ft, h - 2 * a, shadeC(frame, 0.7f))
        }
        val fx = a; val fy = a; val fw = w - 2 * a; val fh = h - 2 * a
        if (fw <= 0 || fh <= 0) return
        rect(c, fx, fy, fw, fh, face)
        val hu = min(u, fh)
        val wu = min(u, fw)
        rect(c, fx, fy, fw, hu, shadeC(face, if (pressed) 1.0f else 1.22f))
        rect(c, fx, fy, wu, fh, shadeC(face, if (pressed) 1.0f else 1.12f))
        rect(c, fx, fy + fh - hu, fw, hu, shadeC(face, 0.78f))
        rect(c, fx + fw - wu, fy, wu, fh, shadeC(face, 0.86f))
        if (studs && fw > 12 * u && fh > 12 * u) {
            val g = Cz.GOLD
            rect(c, fx + 2 * u, fy + 2 * u, u, u, g); rect(c, fx + fw - 3 * u, fy + 2 * u, u, u, g)
            rect(c, fx + 2 * u, fy + fh - 3 * u, u, u, g); rect(c, fx + fw - 3 * u, fy + fh - 3 * u, u, u, g)
        }
    }

    private fun button(c: Canvas, w: Int, h: Int, u: Int) {
        val lh = 2 * u
        val top = if (pressed) u else 0
        val hh = h - lh
        val gold = Cz.GOLD
        val goldL = mixC(gold, Color.WHITE, 0.55f)
        val goldD = Color.rgb(196, 128, 48)
        rect(c, u, lh, w - 2 * u, h - lh, Cz.INK); rect(c, 0, lh + u, w, h - lh - 2 * u, Cz.INK)
        rect(c, u, lh + u, w - 2 * u, h - lh - 2 * u, goldD)
        rect(c, u, top, w - 2 * u, hh, Cz.INK); rect(c, 0, top + u, w, hh - 2 * u, Cz.INK)
        rect(c, u, top + u, w - 2 * u, hh - 2 * u, gold)
        rect(c, u, top + u, w - 2 * u, u, goldL); rect(c, u, top + u, u, hh - 2 * u, goldL)
        rect(c, u, top + hh - 2 * u, w - 2 * u, u, goldD); rect(c, w - 2 * u, top + u, u, hh - 2 * u, goldD)
        val fx = 3 * u; val fy = top + 3 * u; val fw = w - 6 * u; val fh = hh - 6 * u
        if (fw <= 0 || fh <= 0) return
        val n = max(1, fh / u)
        val light = mixC(fill, Color.WHITE, if (pressed) 0.15f else 0.35f)
        val dark = shadeC(fill, 0.62f)
        for (i in 0 until n) {
            val f = i / max(1, n - 1).toFloat()
            val col = if (f < 0.45f) mixC(light, fill, min(1f, f * 2.2f)) else mixC(fill, dark, (f - 0.45f) / 0.55f)
            val ry = fy + i * u
            rect(c, fx, ry, fw, if (i == n - 1) fy + fh - ry else u, col)
        }
        if (w > 24 * u && fh > 5 * u) {
            rect(c, fx, fy, u, u, goldL); rect(c, fx + fw - u, fy, u, u, goldL)
            rect(c, fx, fy + fh - u, u, u, goldD); rect(c, fx + fw - u, fy + fh - u, u, u, goldD)
        }
    }

    private fun round(c: Canvas, w: Int, h: Int, u: Int) {
        val lh = if (lip) 2 * u else 0
        val off = if (pressed) u else 0
        val r = min(w, h) / 2 - lh / 2
        val cx = w / 2; val cy = r + off
        if (lip) { disc(c, cx, r + lh, r, u, Cz.INK); disc(c, cx, r + lh, r - u, u, Color.rgb(196, 128, 48)) }
        disc(c, cx, cy, r, u, Cz.INK)
        disc(c, cx, cy, r - u, u, Cz.GOLD)
        disc(c, cx, cy, r - 2 * u, u, shadeC(fill, if (pressed) 0.95f else 1.05f))
        rect(c, cx - r / 2, cy - r / 2, u, u, Color.argb(160, 255, 255, 255))
    }

    override fun draw(c: Canvas) {
        val d = ctx.resources.displayMetrics.density
        val u = max(2, (d * 1.5f).roundToInt())
        val w = bounds.width(); val h = bounds.height()
        if (w <= 4 * u || h <= 4 * u) return
        val rnd = radiusDp * d * 2f >= min(w, h) - 1f && abs(w - h) <= 4
        if (rnd) { round(c, w, h, u); return }
        if (lip) { button(c, w, h, u); return }
        val hl = outline != Cz.INK
        if (radiusDp >= 14f) {
            val frame = if (hl) outline else mixC(Cz.GOLD, fill, 0.55f)
            box(c, w, h, u, fill, frame, if (hl) 2 * u else u, true)
        } else {
            box(c, w, h, u, fill, outline, if (hl) u else 0, false)
        }
    }

    override fun setAlpha(a: Int) {}
    override fun setColorFilter(f: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Texto em fonte pixelada (a mesma do menu principal). Funciona como um TextView normal. */
class PixText(ctx: Context) : TextView(ctx) {
    var shadowOn = true
    private val pp = Paint()
    private var lines: List<String> = emptyList()
    private var sc = 3

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
        requestLayout(); invalidate()
    }

    private fun wrap(src: String, maxW: Int, s: Int, maxL: Int): List<String> {
        val out = ArrayList<String>()
        for (para in src.split("\n")) {
            var cur = ""
            for (word in para.split(" ")) {
                val cand = if (cur.isEmpty()) word else "$cur $word"
                if (PixFont.width(cand, s) <= maxW) { cur = cand; continue }
                if (cur.isNotEmpty()) { out.add(cur); cur = "" }
                var w = word
                while (PixFont.width(w, s) > maxW && w.length > 1) {
                    var k = w.length - 1
                    while (k > 1 && PixFont.width(w.substring(0, k), s) > maxW) k--
                    out.add(w.substring(0, k)); w = w.substring(k)
                }
                cur = w
            }
            out.add(cur)
        }
        if (out.size > maxL && maxL > 0) {
            val keep = ArrayList<String>(out.subList(0, maxL))
            var last = keep[maxL - 1]
            while (last.isNotEmpty() && PixFont.width("$last...", s) > maxW) last = last.dropLast(1)
            keep[maxL - 1] = last.trimEnd() + "..."
            return keep
        }
        return out
    }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        sc = max(2, (textSize / 10f).roundToInt())
        val wMode = View.MeasureSpec.getMode(wSpec); val wSize = View.MeasureSpec.getSize(wSpec)
        val hMode = View.MeasureSpec.getMode(hSpec); val hSize = View.MeasureSpec.getSize(hSpec)
        val padH = paddingLeft + paddingRight; val padV = paddingTop + paddingBottom
        val avail = if (wMode == View.MeasureSpec.UNSPECIFIED) 100000 else max(1, wSize - padH)
        lines = wrap(text.toString(), avail, sc, maxLines)
        var tw = 0
        for (l in lines) tw = max(tw, PixFont.width(l, sc))
        var w = tw + padH
        var h = max(lines.size * 10 * sc + padV, minHeight)
        if (wMode == View.MeasureSpec.EXACTLY) w = wSize else if (wMode == View.MeasureSpec.AT_MOST) w = min(w, wSize)
        if (hMode == View.MeasureSpec.EXACTLY) h = hSize else if (hMode == View.MeasureSpec.AT_MOST) h = min(h, hSize)
        setMeasuredDimension(w, h)
    }

    private fun drawLine(c: Canvas, s: String, x: Int, y: Int, k: Int, col: Int) {
        pp.color = col
        var cx = x
        for (cell in PixFont.parse(s)) {
            for (ry in 0 until 7) {
                val bits = cell.rows[ry]
                var rx = 0
                while (rx < 5) {
                    if (((bits shr (4 - rx)) and 1) != 0) {
                        var e = rx
                        while (e + 1 < 5 && ((bits shr (4 - (e + 1))) and 1) != 0) e++
                        c.drawRect((cx + rx * k).toFloat(), (y + ry * k).toFloat(), (cx + (e + 1) * k).toFloat(), (y + (ry + 1) * k).toFloat(), pp)
                        rx = e + 1
                    } else {
                        rx++
                    }
                }
            }
            val mk = PixFont.marks(cell.mark)
            var i = 0
            while (i + 1 < mk.size) {
                c.drawRect((cx + mk[i] * k).toFloat(), (y + mk[i + 1] * k).toFloat(), (cx + (mk[i] + 1) * k).toFloat(), (y + (mk[i + 1] + 1) * k).toFloat(), pp)
                i += 2
            }
            cx += 6 * k
        }
    }

    override fun onDraw(c: Canvas) {
        val k = sc
        val color = currentTextColor
        val lineH = 10 * k
        val th = lines.size * lineH
        val availH = height - paddingTop - paddingBottom
        val availW = width - paddingLeft - paddingRight
        var y0 = paddingTop
        val gv = gravity and Gravity.VERTICAL_GRAVITY_MASK
        if (gv == Gravity.CENTER_VERTICAL) y0 += max(0, (availH - th) / 2)
        else if (gv == Gravity.BOTTOM) y0 += max(0, availH - th)
        val hg = gravity and Gravity.HORIZONTAL_GRAVITY_MASK
        for (i in lines.indices) {
            val l = lines[i]
            val lw = PixFont.width(l, k)
            var x0 = paddingLeft
            if (hg == Gravity.CENTER_HORIZONTAL) x0 += (availW - lw) / 2
            else if (hg == Gravity.RIGHT || hg == Gravity.END) x0 += availW - lw
            val y = y0 + i * lineH + 2 * k
            if (shadowOn) drawLine(c, l, x0 + k, y + k, k, Color.argb(175, 22, 8, 40))
            drawLine(c, l, x0, y, k, color)
        }
    }
}

/** controle deslizante pixelado (0..100) */
class PixSlider(ctx: Context, var value: Int, private val onChange: (Int) -> Unit) : View(ctx) {
    private val p = Paint()

    private fun r(c: Canvas, x: Int, y: Int, w: Int, h: Int, col: Int) {
        if (w <= 0 || h <= 0) return
        p.color = col
        c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), p)
    }

    private fun knobSize(): Int = min(height, 8 * context.pxU())

    override fun onMeasure(wm: Int, hm: Int) {
        setMeasuredDimension(View.getDefaultSize(suggestedMinimumWidth, wm), context.dp(30))
    }

    override fun onDraw(c: Canvas) {
        val u = context.pxU(); val w = width; val h = height
        val ks = knobSize()
        val th = 4 * u
        val ty = (h - th) / 2
        r(c, u, ty, w - 2 * u, th, Cz.INK); r(c, 0, ty + u, w, th - 2 * u, Cz.INK)
        r(c, u, ty + u, w - 2 * u, th - 2 * u, Cz.PANEL3)
        val kx = ((w - ks) * value / 100f).toInt()
        val fillW = kx + ks / 2 - u
        r(c, u, ty + u, fillW, th - 2 * u, Cz.GOLD)
        r(c, u, ty + u, fillW, u, mixC(Cz.GOLD, Color.WHITE, 0.5f))
        val ky = (h - ks) / 2
        r(c, kx + u, ky, ks - 2 * u, ks, Cz.INK); r(c, kx, ky + u, ks, ks - 2 * u, Cz.INK)
        r(c, kx + u, ky + u, ks - 2 * u, ks - 2 * u, Cz.CREAM)
        r(c, kx + u, ky + u, ks - 2 * u, u, Color.WHITE)
        r(c, kx + u, ky + ks - 2 * u, ks - 2 * u, u, shadeC(Cz.CREAM, 0.78f))
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val a = e.actionMasked
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
            parent?.requestDisallowInterceptTouchEvent(true)
            val ks = knobSize()
            val v = ((e.x - ks / 2f) / max(1f, (width - ks).toFloat()) * 100f).roundToInt().coerceIn(0, 100)
            if (v != value) { value = v; onChange(v); invalidate() }
        }
        return true
    }
}

/** barra de progresso pixelada */
class PixBar(ctx: Context) : View(ctx) {
    private val p = Paint()
    private var prog = 0
    fun set(v: Int) { prog = v.coerceIn(0, 100); invalidate() }
    private fun r(c: Canvas, x: Int, y: Int, w: Int, h: Int, col: Int) {
        if (w <= 0 || h <= 0) return
        p.color = col
        c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), p)
    }
    override fun onDraw(c: Canvas) {
        val u = context.pxU(); val w = width; val h = height
        r(c, u, 0, w - 2 * u, h, Cz.INK); r(c, 0, u, w, h - 2 * u, Cz.INK)
        r(c, u, u, w - 2 * u, h - 2 * u, Cz.PANEL3)
        val fw = ((w - 2 * u) * prog / 100f).toInt()
        r(c, u, u, fw, h - 2 * u, Cz.GREEN)
        r(c, u, u, fw, u, mixC(Cz.GREEN, Color.WHITE, 0.5f))
    }
}

/** quadradinho de cor pixelado; o escolhido ganha moldura dourada */
class SwatchDrawable(private val ctx: Context, private val col: Int, private val sel: Boolean) : Drawable() {
    private val p = Paint()
    private fun r(c: Canvas, x: Int, y: Int, w: Int, h: Int, cc: Int) {
        if (w <= 0 || h <= 0) return
        p.color = cc
        c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), p)
    }
    override fun draw(c: Canvas) {
        val u = ctx.pxU(); val w = bounds.width(); val h = bounds.height()
        if (w <= 6 * u || h <= 6 * u) return
        r(c, u, 0, w - 2 * u, h, Cz.INK); r(c, 0, u, w, h - 2 * u, Cz.INK)
        val a = if (sel) 2 * u else u
        if (sel) {
            r(c, 2 * u, u, w - 4 * u, u, Cz.GOLD); r(c, 2 * u, h - 2 * u, w - 4 * u, u, Color.rgb(196, 128, 48))
            r(c, u, 2 * u, u, h - 4 * u, Cz.GOLD); r(c, w - 2 * u, 2 * u, u, h - 4 * u, Color.rgb(196, 128, 48))
        }
        r(c, a, a, w - 2 * a, h - 2 * a, col)
        r(c, a, a, w - 2 * a, u, shadeC(col, 1.25f)); r(c, a, a, u, h - 2 * a, shadeC(col, 1.12f))
        r(c, a, h - a - u, w - 2 * a, u, shadeC(col, 0.74f)); r(c, w - a - u, a, u, h - 2 * a, shadeC(col, 0.84f))
    }
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(f: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.OPAQUE
}

fun Context.cTxt(t: String, sp: Float = 14f, color: Int = Color.WHITE, shadow: Boolean = true): TextView = PixText(this).apply {
    text = t; textSize = sp; setTextColor(color); shadowOn = shadow
}

private fun View.pressFx(box: CozyBox) {
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { box.pressed = true; box.invalidateSelf(); v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(70).start() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { box.pressed = false; box.invalidateSelf(); v.animate().scaleX(1f).scaleY(1f).setDuration(110).start() }
        }
        false
    }
}

/** botão fofinho com lábio 3D */
fun Context.cBtn(t: String, color: Int, sp: Float = 16f, onClick: () -> Unit): TextView {
    val b = cTxt(t, sp)
    val box = CozyBox(this, color, 14f, true)
    b.background = box; b.gravity = Gravity.CENTER; b.isClickable = true; b.isFocusable = true
    b.setPadding(dp(16), dp(10), dp(16), dp(14))
    b.minHeight = dp(46)
    b.pressFx(box)
    b.setOnClickListener { onClick() }
    return b
}

/** botão redondo com um símbolo */
fun Context.cIconBtn(sym: String, color: Int, size: Int = 44, onClick: () -> Unit): TextView {
    val b = cTxt(sym, 19f)
    val box = CozyBox(this, color, size / 2f, true)
    b.background = box; b.gravity = Gravity.CENTER; b.isClickable = true
    b.setPadding(0, 0, 0, dp(3))
    b.pressFx(box)
    b.setOnClickListener { onClick() }
    b.layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    return b
}

fun Context.cPanel(fill: Int = Cz.PANEL, radius: Float = 18f, pad: Int = 14): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    background = CozyBox(this@cPanel, fill, radius, false)
    setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
}

/** cartão com título dourado */
fun Context.cCard(title: String, sub: String? = null): LinearLayout {
    val p = cPanel(Cz.PANEL, 16f, 14)
    p.addView(cTxt(title, 16f, Cz.GOLD))
    if (sub != null) p.addView(cTxt(sub, 12f, Cz.SOFT, false).apply { setPadding(0, dp(2), 0, 0) })
    return p
}

fun Context.cChip(t: String, onClick: () -> Unit): TextView {
    val b = cTxt(t, 12f, Color.WHITE, false)
    b.gravity = Gravity.CENTER; b.isClickable = true
    b.setPadding(dp(10), dp(8), dp(10), dp(8))
    b.setOnClickListener { onClick() }
    b.styleChip(false)
    return b
}

fun TextView.styleChip(sel: Boolean) {
    background = CozyBox(context, if (sel) Cz.GOLD else Cz.PANEL2, 12f, false, if (sel) Color.rgb(120, 80, 20) else Cz.INK)
    setTextColor(if (sel) Cz.INK else Color.WHITE)
}

fun lpW(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
fun View.margins(l: Int, t: Int, r: Int, b: Int): View {
    val lp = layoutParams
    if (lp is ViewGroup.MarginLayoutParams) { lp.setMargins(l, t, r, b); layoutParams = lp }
    return this
}

fun View.popIn(delay: Long = 0, dy: Int = 24) {
    alpha = 0f; translationY = context.dp(dy).toFloat()
    animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(380).start()
}

/** interruptor em forma de pílula */
class PillSwitch(ctx: Context, var on: Boolean, val onChange: (Boolean) -> Unit) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var pos = if (on) 1f else 0f
    private val r = RectF()
    init { setOnClickListener { on = !on; onChange(on); invalidate() } }
    override fun onMeasure(wm: Int, hm: Int) = setMeasuredDimension(context.dp(58), context.dp(32))
    override fun onDraw(c: Canvas) {
        val u = max(2, context.dp(2))
        val w = width; val h = height
        pos += ((if (on) 1f else 0f) - pos) * 0.3f
        fun rr(x: Int, y: Int, ww: Int, hh: Int, col: Int) { p.style = Paint.Style.FILL; p.color = col; c.drawRect(x.toFloat(), y.toFloat(), (x + ww).toFloat(), (y + hh).toFloat(), p) }
        rr(u, 0, w - 2 * u, h, Cz.INK); rr(0, u, w, h - 2 * u, Cz.INK)
        val tr = mixC(Color.rgb(90, 90, 120), Cz.GREEN, pos)
        rr(u, u, w - 2 * u, h - 2 * u, shadeC(tr, 0.7f)); rr(2 * u, 2 * u, w - 4 * u, h - 4 * u, tr)
        val ks = h - 2 * u
        val kx = u + ((w - 2 * u - ks) * pos).toInt()
        rr(kx, u, ks, ks, Cz.INK); rr(kx + u, 2 * u, ks - 2 * u, ks - 2 * u, Cz.CREAM)
        rr(kx + u, u + ks - 2 * u, ks - 2 * u, u, shadeC(Cz.CREAM, 0.78f))
        if (abs((if (on) 1f else 0f) - pos) > 0.01f) postInvalidateOnAnimation()
    }
}

fun Context.cToggleRow(title: String, desc: String, on: Boolean, onChange: (Boolean) -> Unit): View {
    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(0, dp(8), 0, dp(8)) }
    val tx = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    tx.addView(cTxt(title, 15f, Cz.CREAM))
    tx.addView(cTxt(desc, 12f, Cz.SOFT, false))
    row.addView(tx, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    row.addView(PillSwitch(this, on, onChange), lpW(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    return row
}

/** diálogo fofinho (painel centralizado); build recebe o painel e uma função pra fechar */
fun Activity.cozyDialog(widthDp: Int = 440, build: (LinearLayout, () -> Unit) -> Unit): Dialog {
    val dlg = Dialog(this)
    dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
    val panel = cPanel(Cz.PANEL, 22f, 18)
    build(panel) { dlg.dismiss() }
    val sv = ScrollView(this); sv.isFillViewport = false; sv.addView(panel)
    dlg.setContentView(sv)
    dlg.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setLayout(this@cozyDialog.dp(widthDp), WindowManager.LayoutParams.WRAP_CONTENT)
        setDimAmount(0.62f)
        setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    dlg.show()
    dlg.window?.let { w -> WindowInsetsControllerCompat(w, w.decorView).hide(WindowInsetsCompat.Type.systemBars()) }
    return dlg
}

fun Activity.cozyConfirm(title: String, msg: String, okText: String, okColor: Int, onOk: () -> Unit) {
    cozyDialog(380) { p, close ->
        p.addView(cTxt(title, 20f, Cz.GOLD))
        p.addView(cTxt(msg, 14f, Cz.CREAM, false).apply { setPadding(0, dp(8), 0, dp(14)) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(cBtn("Cancelar", Cz.STONE, 15f) { close() }, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        row.addView(cBtn(okText, okColor, 15f) { close(); onOk() }, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
        p.addView(row)
    }
}

/** cabeçalho padrão das telas: botão voltar redondo + título */
fun Context.cHeader(title: String, sub: String?, onBack: () -> Unit): LinearLayout {
    val h = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    h.addView(cIconBtn("‹", Cz.ORANGE, 46, onBack).apply { textSize = 28f })
    val tx = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
    tx.addView(cTxt(title, 24f, Color.WHITE))
    if (sub != null) tx.addView(cTxt(sub, 12f, Cz.SOFT, false))
    h.addView(tx, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    return h
}

/** miniatura pixelada de um mundo (gerada a partir da semente e do tipo) */
class WorldThumb(ctx: Context, private val seed: Int, private val type: Int) : View(ctx) {
    private val p = Paint()
    private fun r(c: Canvas, x: Float, y: Float, x2: Float, y2: Float, col: Int) { p.color = col; c.drawRect(x, y, x2, y2, p) }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val u = context.pxU().toFloat()
        val rows = 14; val cs = h / rows
        p.style = Paint.Style.FILL
        val skyC = intArrayOf(Color.rgb(96, 160, 240), Color.rgb(122, 178, 244), Color.rgb(148, 196, 248), Color.rgb(172, 212, 250), Color.rgb(192, 224, 252), Color.rgb(206, 232, 252))
        for (i in 0 until 6) r(c, 0f, h * i / 6f, w, h * (i + 1) / 6f + 1f, skyC[i])
        r(c, w * 0.78f, cs * 1.2f, w * 0.78f + cs * 1.6f, cs * 2.8f, Color.rgb(255, 246, 190))
        val cols = (w / cs).toInt() + 1
        val s = (seed and 1023) * 0.37f
        for (col in 0 until cols) {
            val x = col * cs
            val g = if (type == 1) rows - 5 else (rows - 5 - (2.2f * sin(col * 0.35f + s) + 1.4f * sin(col * 0.8f + s * 2f)).toInt())
            val river = type == 1 && abs(col - cols * (0.45f + 0.18f * sin(s))) < 2.2f
            for (rr in g until rows) {
                val dd = rr - g; val n = hash2(col, rr, seed and 255)
                val colr = when {
                    river && dd <= 1 -> Color.rgb(64, 130, 220)
                    dd == 0 -> shadeC(Color.rgb(92, 168, 58), 0.9f + 0.2f * n)
                    dd <= 2 -> shadeC(Color.rgb(134, 96, 62), 0.82f + 0.3f * n)
                    else -> shadeC(Color.rgb(128, 128, 132), 0.78f + 0.3f * n)
                }
                r(c, x, rr * cs, x + cs + 1f, (rr + 1) * cs + 1f, colr)
            }
            if (!river && col % 5 == 2 && hash2(col, 3, seed and 255) > 0.3f) {
                for (k in 1..2) r(c, x, (g - k) * cs, x + cs, (g - k + 1) * cs, Color.rgb(110, 80, 50))
                r(c, x - cs, (g - 4) * cs, x + 2 * cs, (g - 2) * cs, Color.rgb(54, 142, 52))
                r(c, x, (g - 5) * cs, x + cs, (g - 4) * cs, Color.rgb(54, 142, 52))
            }
        }
        // moldura: contorno escuro + filete dourado
        r(c, 0f, 0f, w, u, Cz.INK); r(c, 0f, h - u, w, h, Cz.INK); r(c, 0f, 0f, u, h, Cz.INK); r(c, w - u, 0f, w, h, Cz.INK)
        r(c, u, u, w - u, 2 * u, Cz.GOLD); r(c, u, h - 2 * u, w - u, h - u, Color.rgb(196, 128, 48))
        r(c, u, 2 * u, 2 * u, h - 2 * u, Cz.GOLD); r(c, w - 2 * u, 2 * u, w - u, h - 2 * u, Color.rgb(196, 128, 48))
    }
}
