package com.cuboland.app

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.sin
import kotlin.math.cos

class MenuBg(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val t0 = System.nanoTime()

    private fun mul(c: Int, f: Float) = Color.rgb((Color.red(c) * f).toInt(), (Color.green(c) * f).toInt(), (Color.blue(c) * f).toInt())

    private fun cube(c: Canvas, cx: Float, cy: Float, s: Float, top: Int, side: Int) {
        p.style = Paint.Style.FILL
        val faces = arrayOf(
            floatArrayOf(cx, cy - s, cx + s * 0.87f, cy - s * 0.5f, cx, cy, cx - s * 0.87f, cy - s * 0.5f),
            floatArrayOf(cx - s * 0.87f, cy - s * 0.5f, cx, cy, cx, cy + s, cx - s * 0.87f, cy + s * 0.5f),
            floatArrayOf(cx + s * 0.87f, cy - s * 0.5f, cx, cy, cx, cy + s, cx + s * 0.87f, cy + s * 0.5f))
        val cols = intArrayOf(top, mul(side, 0.92f), mul(side, 0.72f))
        for (k in 0 until 3) {
            val f = faces[k]
            path.reset(); path.moveTo(f[0], f[1]); path.lineTo(f[2], f[3]); path.lineTo(f[4], f[5]); path.lineTo(f[6], f[7]); path.close()
            p.style = Paint.Style.FILL; p.color = cols[k]; c.drawPath(path, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = s * 0.035f; p.color = Color.argb(70, 20, 30, 60); c.drawPath(path, p)
        }
        p.color = Color.argb(70, 255, 255, 255); p.strokeWidth = s * 0.05f
        c.drawLine(cx - s * 0.7f, cy - s * 0.5f, cx, cy - s * 0.88f, p)
        // pontinhos de textura na face de cima
        p.style = Paint.Style.FILL; p.color = Color.argb(45, 255, 255, 255)
        c.drawRect(cx - s * 0.25f, cy - s * 0.62f, cx - s * 0.15f, cy - s * 0.54f, p)
        c.drawRect(cx + s * 0.18f, cy - s * 0.42f, cx + s * 0.28f, cy - s * 0.34f, p)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val t = (System.nanoTime() - t0) / 1e9f
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Color.rgb(64, 140, 232), Color.rgb(130, 204, 250), Color.rgb(255, 236, 205)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        // sol com raios
        val sx = w * 0.84f; val sy = h * 0.2f; val sr = h * 0.11f
        p.shader = RadialGradient(sx, sy, sr * 3.2f, Color.argb(150, 255, 244, 190), Color.argb(0, 255, 244, 190), Shader.TileMode.CLAMP)
        c.drawCircle(sx, sy, sr * 3.2f, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = 5f; p.strokeCap = Paint.Cap.ROUND; p.color = Color.argb(70, 255, 246, 200)
        for (k in 0 until 14) { val an = k * 0.4488f + t * 0.1f; c.drawLine(sx + cos(an) * sr * 1.5f, sy + sin(an) * sr * 1.5f, sx + cos(an) * sr * (2f + 0.3f * sin(t * 2 + k)), sy + sin(an) * sr * (2f + 0.3f * sin(t * 2 + k)), p) }
        p.style = Paint.Style.FILL; p.color = Color.rgb(255, 245, 190); c.drawCircle(sx, sy, sr, p)
        p.color = Color.rgb(255, 232, 120); c.drawCircle(sx, sy, sr * 0.78f, p)
        // nuvens (em camadas, sombra embaixo)
        for (i in 0 until 6) {
            val sp = 8f + i * 4f
            val x = ((i * 290f + t * sp) % (w + 400f)) - 200f; val y = h * 0.06f + i * h * 0.075f; val cw = 150f + (i % 3) * 50f
            p.color = Color.argb(70, 120, 150, 200); c.drawRoundRect(x, y + 14f, x + cw, y + 48f, 24f, 24f, p)
            p.color = Color.argb(235, 255, 255, 255); c.drawRoundRect(x, y, x + cw, y + 38f, 24f, 24f, p)
            c.drawRoundRect(x + cw * 0.2f, y - 18f, x + cw * 0.62f, y + 24f, 22f, 22f, p)
        }
        // colinas ao fundo
        for (layer in 0 until 2) {
            path.reset(); path.moveTo(0f, h)
            var x = 0f
            while (x <= w + 20f) { path.lineTo(x, h * (0.82f - layer * 0.0f + layer * 0.05f) - sin(x * 0.006f + layer * 2f) * h * 0.05f - cos(x * 0.013f) * h * 0.02f); x += 20f }
            path.lineTo(w, h); path.close()
            p.color = if (layer == 0) Color.rgb(132, 208, 150) else Color.rgb(96, 186, 100); c.drawPath(path, p)
        }
        // ilha flutuante de cubos
        val s = h * 0.15f
        val base = w * 0.28f
        val gb = sin(t * 1.2f) * h * 0.012f
        p.color = Color.argb(45, 20, 60, 40); c.drawOval(base - s * 2.2f, h * 0.9f, base + s * 2.2f, h * 0.97f, p)
        for (layer in 0 until 3) for (k in 0..layer) {
            val bob = sin(t * 1.6f + layer + k) * 3f
            val cx = base + (k - layer / 2f) * s * 1.74f
            val cy = h * 0.46f + layer * s * 1.0f + bob + gb
            val grass = layer == 0
            cube(c, cx, cy, s, if (grass) Color.rgb(108, 208, 80) else Color.rgb(146, 100, 60),
                if (layer == 2) Color.rgb(154, 160, 168) else Color.rgb(146, 100, 60))
        }
        // arvorezinha em cima
        val ty = h * 0.46f - s * 0.5f + gb
        cube(c, base, ty - s * 0.55f, s * 0.38f, Color.rgb(160, 110, 66), Color.rgb(128, 86, 50))
        cube(c, base, ty - s * 1.25f + sin(t * 1.8f) * 3f, s * 0.7f, Color.rgb(70, 175, 80), Color.rgb(52, 140, 66))
        // cubo dourado flutuando
        cube(c, base + s * 1.6f, h * 0.24f + sin(t * 2f) * 10f, s * 0.42f, Color.rgb(255, 242, 168), Color.rgb(255, 216, 74))
        // faíscas
        p.style = Paint.Style.FILL
        for (i in 0 until 26) {
            val ph = (t * (0.05f + (i % 5) * 0.012f) + i * 0.137f) % 1f
            val fx = (i * 0.211f % 1f) * w; val fy = h * (1.02f - ph * 1.1f)
            p.color = Color.argb((150 * sin(ph * 3.14f)).toInt().coerceAtLeast(0), 255, 255, 220)
            val r = 2f + (i % 3) * 1.6f; c.drawRect(fx - r, fy - r, fx + r, fy + r, p)
        }
        postInvalidateOnAnimation()
    }
}

class TitleView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t0 = System.nanoTime()
    private val txt = "CuboLand"
    private val cols = intArrayOf(Color.rgb(255, 120, 120), Color.rgb(255, 176, 90), Color.rgb(255, 224, 90), Color.rgb(140, 224, 100),
        Color.rgb(90, 214, 230), Color.rgb(110, 160, 255), Color.rgb(190, 130, 255), Color.rgb(255, 130, 200))
    override fun onMeasure(wm: Int, hm: Int) = setMeasuredDimension(context.dp(330), context.dp(96))
    override fun onDraw(c: Canvas) {
        val t = (System.nanoTime() - t0) / 1e9f
        p.textSize = context.dp(54).toFloat(); p.typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD_ITALIC); p.strokeJoin = Paint.Join.ROUND
        var total = 0f; val ws = FloatArray(txt.length) { p.measureText(txt[it].toString()) }; for (v in ws) total += v
        var x = (width - total) / 2f
        for (i in txt.indices) {
            val y = height * 0.68f + sin(t * 2.4f + i * 0.7f) * context.dp(4)
            val ch = txt[i].toString()
            p.style = Paint.Style.FILL; p.color = Color.argb(90, 10, 20, 70); c.drawText(ch, x + context.dp(3), y + context.dp(6), p)
            p.style = Paint.Style.STROKE; p.strokeWidth = context.dp(9).toFloat(); p.color = Color.rgb(34, 44, 110); c.drawText(ch, x, y, p)
            p.style = Paint.Style.FILL; p.shader = LinearGradient(0f, y - context.dp(40), 0f, y, Color.WHITE, cols[i], Shader.TileMode.CLAMP)
            c.drawText(ch, x, y, p); p.shader = null
            x += ws[i]
        }
        postInvalidateOnAnimation()
    }
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        root.addView(MenuBg(this))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val title = TitleView(this)
        val sub = TextView(this).apply {
            text = "Construa. Explore. Lute."; textSize = 15f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(6f, 0f, 3f, Color.argb(160, 20, 40, 90)); setPadding(dp(16), dp(5), dp(16), dp(5))
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.argb(90, 20, 40, 100)) }
        }
        val gap = android.view.View(this)
        val play = btn("▶  Jogar", Color.rgb(72, 190, 84)) { startActivity(Intent(this, GameActivity::class.java)) }
        val cfg = btn("⚙  Configurações", Color.rgb(66, 133, 244)) { startActivity(Intent(this, SettingsActivity::class.java)) }
        val ver = TextView(this).apply {
            text = "versão ${packageManager.getPackageInfo(packageName, 0).versionName}"; textSize = 12f
            setTextColor(Color.WHITE); setPadding(0, dp(8), 0, 0); alpha = 0.85f
        }
        val items = listOf<View>(title, sub, gap, play, cfg, ver)
        for ((i, v) in items.withIndex()) {
            val lp = LinearLayout.LayoutParams(if (i == 3 || i == 4) dp(260) else if (i == 2) 1 else LinearLayout.LayoutParams.WRAP_CONTENT, if (i == 2) dp(16) else LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = if (i == 4) dp(4) else 0
            col.addView(v, lp)
            v.alpha = 0f; v.translationY = dp(30).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(120L * i).setDuration(450).start()
        }
        val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL)
        lp.marginEnd = dp(70)
        root.addView(col, lp)
        setContentView(root)
        Novidades.showIfNew(this)
        Updater.autoCheck(this)
    }
}
