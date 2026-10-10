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

/** Caixa pixel art: contorno escuro com cantos recortados, moldura, degradê em faixas e "lábio" 3D embaixo. */
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

    private fun block(c: Canvas, x: Int, y: Int, w: Int, h: Int, u: Int, face: Int, rim: Int, ol: Int, big: Boolean) {
        rect(c, x + u, y, w - 2 * u, h, ol); rect(c, x, y + u, w, h - 2 * u, ol)
        rect(c, x + u, y + u, w - 2 * u, h - 2 * u, rim)
        val fx = x + 2 * u; val fy = y + 2 * u; val fw = w - 4 * u; val fh = h - 4 * u
        if (fw <= 0 || fh <= 0) return
        val n = max(1, fh / u)
        val light = shadeC(face, if (pressed) 1.0f else 1.18f)
        val dark = shadeC(face, if (pressed) 0.84f else 0.92f)
        for (i in 0 until n) {
            val ry = fy + i * u
            val rh = if (i == n - 1) fy + fh - ry else u
            rect(c, fx, ry, fw, rh, mixC(light, dark, i / max(1, n - 1).toFloat()))
        }
        rect(c, fx, fy, u, fh, Color.argb(40, 255, 255, 255))
        rect(c, fx, fy + fh - u, fw, u, Color.argb(60, 0, 0, 0))
        rect(c, fx + fw - u, fy, u, fh, Color.argb(35, 0, 0, 0))
        if (big) {
            val g = Cz.GOLD
            rect(c, x + 3 * u, y + 3 * u, u, u, g); rect(c, x + w - 4 * u, y + 3 * u, u, u, g)
            rect(c, x + 3 * u, y + h - 4 * u, u, u, g); rect(c, x + w - 4 * u, y + h - 4 * u, u, u, g)
        }
    }

    override fun draw(c: Canvas) {
        val d = ctx.resources.displayMetrics.density
        val u = max(2, (2f * d).roundToInt())
        val w = bounds.width(); val h = bounds.height()
        val lh = if (lip) 2 * u else 0
        val off = if (pressed) u else 0
        val rim = if (lip) mixC(fill, Cz.GOLD, 0.65f) else if (outline != Cz.INK) shadeC(fill, 1.25f) else mixC(shadeC(fill, 1.45f), Cz.GOLD, 0.2f)
        val round = radiusDp * d * 2f >= min(w, h) - 1f && abs(w - h) <= 4
        if (round) {
            val r = min(w, h) / 2 - lh / 2
            val cx = w / 2; val cy = r + off
            if (lip) { disc(c, cx, r + lh, r, u, outline); disc(c, cx, r + lh, r - u, u, shadeC(fill, 0.5f)) }
            disc(c, cx, cy, r, u, outline)
            disc(c, cx, cy, r - u, u, rim)
            disc(c, cx, cy, r - 2 * u, u, shadeC(fill, if (pressed) 0.95f else 1.1f))
            rect(c, cx - r / 2, cy - r / 2, u, u, Color.argb(120, 255, 255, 255))
            return
        }
        val big = w > 40 * u && h > 20 * u
        if (lip) block(c, 0, lh, w, h - lh, u, shadeC(fill, 0.5f), mixC(shadeC(fill, 0.6f), Cz.GOLD, 0.35f), outline, false)
        block(c, 0, off, w, h - lh, u, fill, rim, outline, big)
    }

    override fun setAlpha(a: Int) {}
    override fun setColorFilter(f: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun Context.cTxt(t: String, sp: Float = 14f, color: Int = Color.WHITE, shadow: Boolean = true): TextView = TextView(this).apply {
    text = t; textSize = sp; setTextColor(color); typeface = uiFont()
    paint.isAntiAlias = false
    if (shadow) setShadowLayer(0.1f, dp(1).toFloat(), dp(1).toFloat(), Color.argb(210, 20, 10, 30))
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
    val b = cTxt(t, 13f, Color.WHITE, false)
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
    private val clip = Path()
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val d = context.dp(1).toFloat()
        clip.reset(); clip.addRoundRect(RectF(0f, 0f, w, h), 2 * d, 2 * d, Path.Direction.CW); c.save(); c.clipPath(clip)
        val rows = 14; val cs = h / rows
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, h, Color.rgb(96, 160, 240), Color.rgb(206, 232, 252), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        p.color = Color.rgb(255, 246, 190); c.drawRect(w * 0.78f, cs * 1.2f, w * 0.78f + cs * 1.6f, cs * 2.8f, p)
        val cols = (w / cs).toInt() + 1
        val s = (seed and 1023) * 0.37f
        for (col in 0 until cols) {
            val x = col * cs
            val g = if (type == 1) rows - 5 else (rows - 5 - (2.2f * sin(col * 0.35f + s) + 1.4f * sin(col * 0.8f + s * 2f)).toInt())
            val river = type == 1 && abs(col - cols * (0.45f + 0.18f * sin(s))) < 2.2f
            for (r in g until rows) {
                val dd = r - g; val n = hash2(col, r, seed and 255)
                val colr = when {
                    river && dd <= 1 -> Color.rgb(64, 130, 220)
                    dd == 0 -> shadeC(Color.rgb(92, 168, 58), 0.9f + 0.2f * n)
                    dd <= 2 -> shadeC(Color.rgb(134, 96, 62), 0.82f + 0.3f * n)
                    else -> shadeC(Color.rgb(128, 128, 132), 0.78f + 0.3f * n)
                }
                p.color = colr; c.drawRect(x, r * cs, x + cs + 1f, (r + 1) * cs + 1f, p)
            }
            if (!river && col % 5 == 2 && hash2(col, 3, seed and 255) > 0.3f) {
                for (k in 1..2) { p.color = Color.rgb(110, 80, 50); c.drawRect(x, (g - k) * cs, x + cs, (g - k + 1) * cs, p) }
                p.color = Color.rgb(54, 142, 52); c.drawRect(x - cs, (g - 4) * cs, x + 2 * cs, (g - 2) * cs, p); c.drawRect(x, (g - 5) * cs, x + cs, (g - 4) * cs, p)
            }
        }
        c.restore()
        p.style = Paint.Style.STROKE; p.strokeWidth = 3 * d; p.color = Cz.INK
        c.drawRect(1.5f * d, 1.5f * d, w - 1.5f * d, h - 1.5f * d, p)
    }
}
