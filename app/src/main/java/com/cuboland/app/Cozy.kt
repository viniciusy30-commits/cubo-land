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
import kotlin.math.sin

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

fun Context.uiFont(): Typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)

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

/** Caixa arredondada com contorno escuro, brilho no topo e (opcional) "lábio" 3D embaixo. */
class CozyBox(private val ctx: Context, var fill: Int, private val radiusDp: Float, private val lip: Boolean,
              private val outline: Int = Cz.INK) : Drawable() {
    var pressed = false
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun draw(c: Canvas) {
        val d = ctx.resources.displayMetrics.density
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        val rad = radiusDp * d; val st = 2f * d
        val lh = if (lip) 4f * d else 0f
        val off = if (pressed) lh * 0.75f else 0f
        if (lip) {
            r.set(st / 2, lh + st / 2, w - st / 2, h - st / 2)
            p.style = Paint.Style.FILL; p.shader = null; p.color = shadeC(fill, 0.58f); c.drawRoundRect(r, rad, rad, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = st; p.color = outline; c.drawRoundRect(r, rad, rad, p)
        }
        val top = off + st / 2; val bot = h - lh + off - st / 2
        r.set(st / 2, top, w - st / 2, bot)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, top, 0f, bot, shadeC(fill, if (pressed) 1.0f else 1.16f), shadeC(fill, if (pressed) 0.86f else 0.96f), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, rad, rad, p); p.shader = null
        // brilho
        val gh = (bot - top) * 0.42f
        r.set(st * 2.2f, top + st * 1.6f, w - st * 2.2f, top + st * 1.6f + gh)
        p.color = Color.argb(if (pressed) 18 else 42, 255, 255, 255); c.drawRoundRect(r, rad * 0.7f, rad * 0.7f, p)
        r.set(st / 2, top, w - st / 2, bot)
        p.style = Paint.Style.STROKE; p.strokeWidth = st; p.color = outline; c.drawRoundRect(r, rad, rad, p)
        // filete interno claro
        r.set(st * 1.5f, top + st, w - st * 1.5f, bot - st)
        p.strokeWidth = 1f * d; p.color = Color.argb(46, 255, 255, 255); c.drawRoundRect(r, rad * 0.8f, rad * 0.8f, p)
    }

    override fun setAlpha(a: Int) {}
    override fun setColorFilter(f: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun Context.cTxt(t: String, sp: Float = 14f, color: Int = Color.WHITE, shadow: Boolean = true): TextView = TextView(this).apply {
    text = t; textSize = sp; setTextColor(color); typeface = uiFont()
    if (shadow) setShadowLayer(0.6f, dp(1).toFloat(), dp(1).toFloat(), Color.argb(150, 20, 14, 30))
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
        val w = width.toFloat(); val h = height.toFloat(); val d = context.dp(1).toFloat()
        pos += ((if (on) 1f else 0f) - pos) * 0.3f
        r.set(d, d, w - d, h - d)
        p.style = Paint.Style.FILL; p.color = mixC(Color.rgb(90, 90, 120), Cz.GREEN, pos); c.drawRoundRect(r, h / 2, h / 2, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.color = Cz.INK; c.drawRoundRect(r, h / 2, h / 2, p)
        val kx = h / 2 + (w - h) * pos
        p.style = Paint.Style.FILL; p.color = Color.argb(60, 0, 0, 0); c.drawCircle(kx, h / 2 + 1.5f * d, h * 0.34f, p)
        p.color = Cz.CREAM; c.drawCircle(kx, h / 2, h * 0.34f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * d; p.color = Cz.INK; c.drawCircle(kx, h / 2, h * 0.34f, p)
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

/** Cenário animado: o céu muda conforme a hora do aparelho (dia, pôr do sol, noite com lua e estrelas). */
class SceneBg(ctx: Context, var dim: Float = 0f) : View(ctx) {
    private val p = Paint()
    private val t0 = System.nanoTime()
    private val hour: Float = java.util.Calendar.getInstance().let { it.get(java.util.Calendar.HOUR_OF_DAY) + it.get(java.util.Calendar.MINUTE) / 60f }

    private class Key(val h: Float, val top: Int, val bot: Int, val night: Float)
    private val keys = arrayOf(
        Key(0f, Color.rgb(12, 16, 44), Color.rgb(48, 54, 108), 1f),
        Key(5f, Color.rgb(16, 20, 54), Color.rgb(60, 62, 120), 1f),
        Key(7f, Color.rgb(112, 110, 196), Color.rgb(255, 196, 150), 0.35f),
        Key(9.5f, Color.rgb(78, 140, 235), Color.rgb(205, 230, 252), 0f),
        Key(16f, Color.rgb(78, 140, 235), Color.rgb(205, 230, 252), 0f),
        Key(18.5f, Color.rgb(96, 84, 178), Color.rgb(255, 156, 112), 0.3f),
        Key(21f, Color.rgb(16, 20, 54), Color.rgb(60, 62, 120), 1f),
        Key(24f, Color.rgb(12, 16, 44), Color.rgb(48, 54, 108), 1f))

    private fun sky(): Triple<Int, Int, Float> {
        for (i in 0 until keys.size - 1) {
            val a = keys[i]; val b = keys[i + 1]
            if (hour >= a.h && hour <= b.h) {
                val t = (hour - a.h) / (b.h - a.h)
                return Triple(mixC(a.top, b.top, t), mixC(a.bot, b.bot, t), a.night + (b.night - a.night) * t)
            }
        }
        return Triple(keys[3].top, keys[3].bot, 0f)
    }

    private fun hh(x: Int, y: Int, s: Int = 0) = hash2(x, y, s)
    private fun ht(wx: Int, far: Boolean): Int {
        val x = wx.toFloat() + (if (far) 300f else 0f)
        return if (far) (12 + 4 * sin(x * 0.07f) + 3 * sin(x * 0.19f + 1f)).toInt() else (7 + 3 * sin(x * 0.11f) + 2 * sin(x * 0.27f + 1.3f) + 1.5f * sin(x * 0.05f + 4f)).toInt()
    }
    private fun cell(c: Canvas, x: Float, y: Float, s: Float, col: Int) { p.color = col; c.drawRect(x, y, x + s + 1f, y + s + 1f, p) }

    private fun terrain(c: Canvas, w: Float, h: Float, cs: Float, sc: Float, rows: Int, far: Boolean) {
        val off = sc.toInt(); val fr = (sc - off) * cs
        val cols = (w / cs).toInt() + 2
        for (col in 0 until cols) {
            val wx = col + off; val x = col * cs - fr
            val gy = rows - ht(wx, far)
            if (far) { for (r in gy until rows) cell(c, x, r * cs, cs, mixC(Color.rgb(112, 150, 170), Color.rgb(160, 190, 215), (hh(wx, r, 3) * 0.25f + (r - gy) * 0.02f).coerceAtMost(0.6f))); continue }
            for (r in gy until rows) {
                val d = r - gy; val n = hh(wx, r)
                val col2 = when {
                    d == 0 -> shadeC(Color.rgb(92, 168, 58), 0.88f + 0.24f * n)
                    d <= 3 -> shadeC(Color.rgb(134, 96, 62), 0.82f + 0.3f * n)
                    else -> { val st = shadeC(Color.rgb(128, 128, 132), 0.78f + 0.3f * n); if (n > 0.965f) Color.rgb(40, 40, 44) else if (n < 0.012f) Color.rgb(222, 190, 70) else st }
                }
                cell(c, x, r * cs, cs, shadeC(col2, (0.55f + 0.45f * (1f - (r.toFloat() / rows) * 0.5f))))
                if (d == 1 && hh(wx, r, 7) > 0.5f) cell(c, x, r * cs, cs * 0.5f, Color.rgb(92, 168, 58))
            }
            if (wx % 7 == 3 && hh(wx, 1, 11) > 0.35f) {
                val th = 4 + (hh(wx, 2, 12) * 2).toInt(); val base = gy
                for (k in 1..th) cell(c, x, (base - k) * cs, cs, shadeC(Color.rgb(110, 80, 50), 0.85f + 0.2f * hh(wx, k, 13)))
                for (ly in 0 until 4) for (lx in -2..2) {
                    if ((ly == 0 || ly == 3) && (lx == -2 || lx == 2)) continue
                    if (lx == 0 && ly >= 2) continue
                    cell(c, x + lx * cs, (base - th - 2 + ly) * cs, cs, shadeC(Color.rgb(54, 142, 52), 0.75f + 0.45f * hh(wx + lx, ly, 14)))
                }
            }
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val t = (System.nanoTime() - t0) / 1e9f
        val rows = 22; val cs = h / rows
        val (top, bot, night) = sky()
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(top, mixC(top, bot, 0.7f), bot), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        // estrelas
        if (night > 0.05f) for (i in 0 until 70) {
            val sx = hh(i, 1, 21) * w; val sy = hh(i, 2, 22) * h * 0.55f
            val tw = 0.5f + 0.5f * sin(t * (1f + hh(i, 3, 23) * 2f) + i)
            p.color = Color.argb((night * 230 * tw).toInt().coerceIn(0, 255), 255, 255, 235)
            val s = cs * (0.12f + 0.12f * hh(i, 4, 24)); c.drawRect(sx, sy, sx + s, sy + s, p)
        }
        // sol ou lua
        val isDay = hour in 6f..18f
        val frac = if (isDay) (hour - 6f) / 12f else ((hour + 24f - 18f) % 24f) / 12f
        val sx = w * (0.12f + 0.76f * frac); val sy = h * (0.62f - 0.5f * sin(PI.toFloat() * frac)); val sr = cs * 1.5f
        if (isDay) {
            p.color = Color.argb(40, 255, 250, 200); c.drawRect(sx - sr * 2f, sy - sr * 2f, sx + sr * 2f, sy + sr * 2f, p)
            p.color = Color.argb(70, 255, 250, 200); c.drawRect(sx - sr * 1.4f, sy - sr * 1.4f, sx + sr * 1.4f, sy + sr * 1.4f, p)
            p.color = Color.rgb(255, 246, 190); c.drawRect(sx - sr, sy - sr, sx + sr, sy + sr, p)
        } else {
            p.color = Color.argb(36, 200, 215, 255); c.drawRect(sx - sr * 1.8f, sy - sr * 1.8f, sx + sr * 1.8f, sy + sr * 1.8f, p)
            p.color = Color.rgb(232, 238, 252); c.drawRect(sx - sr * 0.9f, sy - sr * 0.9f, sx + sr * 0.9f, sy + sr * 0.9f, p)
            p.color = Color.rgb(196, 206, 232); c.drawRect(sx + sr * 0.1f, sy - sr * 0.5f, sx + sr * 0.5f, sy - sr * 0.1f, p); c.drawRect(sx - sr * 0.5f, sy + sr * 0.2f, sx - sr * 0.1f, sy + sr * 0.6f, p)
        }
        // nuvens de blocos
        val cloudA = 235 - (night * 150).toInt()
        for (i in 0 until 7) {
            val cw = (5 + (i % 3) * 2) * cs; val x = ((i * 9.1f * cs + t * cs * (0.5f + (i % 3) * 0.15f)) % (w + cw * 2f)) - cw
            val y = cs * (1.5f + (i * 2.3f) % 6f)
            p.color = Color.argb(cloudA, 255, 255, 255); c.drawRect(x, y, x + cw, y + cs, p); c.drawRect(x + cs, y - cs, x + cw - cs * 2f, y, p)
            p.color = Color.argb(cloudA, 218, 228, 242); c.drawRect(x, y + cs, x + cw - cs, y + cs * 1.45f, p)
        }
        terrain(c, w, h, cs * 1.25f, t * 0.35f, (h / (cs * 1.25f)).toInt() + 1, true)
        terrain(c, w, h, cs, t * 0.9f, rows, false)
        // escurecer à noite / entardecer
        if (night > 0.02f) { p.color = Color.argb((night * 150).toInt(), 8, 10, 40); c.drawRect(0f, 0f, w, h, p) }
        // partículas: vaga-lumes à noite, pólen de dia
        for (i in 0 until 26) {
            val px = ((hh(i, 5, 31) * w + sin(t * 0.4f + i) * cs * 2f) + w) % w
            val py = h * (0.35f + 0.5f * hh(i, 6, 32)) - ((t * cs * (0.3f + hh(i, 7, 33) * 0.5f)) % (h * 0.4f))
            val tw = 0.4f + 0.6f * abs(sin(t * 1.3f + i * 1.7f))
            val col = if (night > 0.5f) Color.argb((220 * tw).toInt(), 255, 240, 130) else Color.argb((150 * tw).toInt(), 255, 255, 230)
            val s = cs * (night.let { if (it > 0.5f) 0.2f else 0.14f })
            p.color = Color.argb(Color.alpha(col) / 4, 255, 240, 130); c.drawRect(px - s, py - s, px + s * 2f, py + s * 2f, p)
            p.color = col; c.drawRect(px, py, px + s, py + s, p)
        }
        // vinheta
        p.color = Color.argb(35, 0, 0, 0); c.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(w / 2f, h / 2f, w * 0.65f, Color.argb(0, 0, 0, 0), Color.argb(150, 0, 0, 20), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        if (dim > 0f) { p.color = Color.argb((dim * 255).toInt(), 14, 12, 34); c.drawRect(0f, 0f, w, h, p) }
        postInvalidateOnAnimation()
    }
}

/** miniatura pixelada de um mundo (gerada a partir da semente e do tipo) */
class WorldThumb(ctx: Context, private val seed: Int, private val type: Int) : View(ctx) {
    private val p = Paint()
    private val clip = Path()
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val d = context.dp(1).toFloat()
        clip.reset(); clip.addRoundRect(RectF(0f, 0f, w, h), 12 * d, 12 * d, Path.Direction.CW); c.save(); c.clipPath(clip)
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
    }
}
