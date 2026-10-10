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
    private val p = Paint()
    private val t0 = System.nanoTime()
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
            if (far) { for (r in gy until rows) cell(c, x, r * cs, cs, mixC(Color.rgb(104, 150, 150), Color.rgb(150, 190, 200), (hh(wx, r, 3) * 0.25f + (r - gy) * 0.02f).coerceAtMost(0.6f))); continue }
            for (r in gy until rows) {
                val d = r - gy; val n = hh(wx, r)
                val col2 = when {
                    d == 0 -> shadeC(Color.rgb(92, 168, 58), 0.88f + 0.24f * n)
                    d <= 3 -> shadeC(Color.rgb(134, 96, 62), 0.82f + 0.3f * n)
                    else -> { val st = shadeC(Color.rgb(128, 128, 132), 0.78f + 0.3f * n); if (n > 0.965f) Color.rgb(40, 40, 44) else if (n < 0.012f) Color.rgb(222, 190, 70) else st }
                }
                cell(c, x, r * cs, cs, shadeC(col2, (0.55f + 0.45f * (1f - (r.toFloat() / rows) * 0.5f))))
                if (d == 1 && hh(wx, r, 7) > 0.5f) cell(c, x, r * cs, cs * 0.5f, Color.rgb(92, 168, 58))   // franjinha de grama pendurada
            }
            // árvores
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
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(Color.rgb(78, 140, 235), Color.rgb(150, 200, 250), Color.rgb(205, 230, 252)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        // sol quadrado
        val sx = w * 0.8f; val sy = h * 0.16f; val sr = cs * 1.6f
        p.color = Color.argb(40, 255, 250, 200); c.drawRect(sx - sr * 2f, sy - sr * 2f, sx + sr * 2f, sy + sr * 2f, p)
        p.color = Color.argb(70, 255, 250, 200); c.drawRect(sx - sr * 1.4f, sy - sr * 1.4f, sx + sr * 1.4f, sy + sr * 1.4f, p)
        p.color = Color.rgb(255, 246, 190); c.drawRect(sx - sr, sy - sr, sx + sr, sy + sr, p)
        // nuvens de blocos
        for (i in 0 until 7) {
            val cw = (5 + (i % 3) * 2) * cs; val x = ((i * 9.1f * cs + t * cs * (0.5f + (i % 3) * 0.15f)) % (w + cw * 2f)) - cw
            val y = cs * (1.5f + (i * 2.3f) % 6f)
            p.color = Color.argb(235, 255, 255, 255); c.drawRect(x, y, x + cw, y + cs, p); c.drawRect(x + cs, y - cs, x + cw - cs * 2f, y, p)
            p.color = Color.argb(235, 218, 228, 242); c.drawRect(x, y + cs, x + cw - cs, y + cs * 1.45f, p)
        }
        terrain(c, w, h, cs * 1.25f, t * 0.35f, (h / (cs * 1.25f)).toInt() + 1, true)
        terrain(c, w, h, cs, t * 0.9f, rows, false)
        // vinheta + leve escurecida pra destacar o menu
        p.color = Color.argb(35, 0, 0, 0); c.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(w / 2f, h / 2f, w * 0.65f, Color.argb(0, 0, 0, 0), Color.argb(150, 0, 0, 20), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        postInvalidateOnAnimation()
    }
}

class TitleView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t0 = System.nanoTime()
    private val txt = "CUBOLAND"
    override fun onMeasure(wm: Int, hm: Int) = setMeasuredDimension(context.dp(380), context.dp(120))
    override fun onDraw(c: Canvas) {
        val t = (System.nanoTime() - t0) / 1e9f
        val d = context.dp(1).toFloat()
        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); p.textSize = 52 * d; p.textAlign = Paint.Align.LEFT
        p.strokeJoin = Paint.Join.MITER
        val tw = p.measureText(txt); val x = (width - tw) / 2f; val y = height * 0.62f
        // extrusão 3D em pedra escura
        p.style = Paint.Style.FILL
        for (k in 9 downTo 1) { p.color = if (k % 2 == 0) Color.rgb(54, 54, 58) else Color.rgb(66, 66, 70); c.drawText(txt, x + k * d * 0.8f, y + k * d * 0.8f, p) }
        // contorno preto
        p.style = Paint.Style.STROKE; p.strokeWidth = 7 * d; p.color = Color.BLACK; c.drawText(txt, x, y, p)
        // face da frente: pedra clara com degradê
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, y - 40 * d, 0f, y, Color.rgb(236, 236, 240), Color.rgb(142, 144, 150), Shader.TileMode.CLAMP)
        c.drawText(txt, x, y, p); p.shader = null
        // grama em cima das letras
        p.style = Paint.Style.FILL; p.color = Color.argb(0, 0, 0, 0)
        // splash amarelo pulsando
        c.save(); c.rotate(-14f, width * 0.84f, height * 0.88f)
        val sc = 1f + 0.06f * sin(t * 4f); c.scale(sc, sc, width * 0.84f, height * 0.88f)
        p.textSize = 15 * d; p.textAlign = Paint.Align.CENTER
        p.color = Color.rgb(60, 60, 0); c.drawText("Construa e lute!", width * 0.84f + 1.5f * d, height * 0.88f + 1.5f * d, p)
        p.color = Color.rgb(255, 255, 60); c.drawText("Construa e lute!", width * 0.84f, height * 0.88f, p)
        c.restore()
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
        val gap = View(this)
        val play = btn("Jogar", Color.rgb(72, 190, 84)) { startActivity(Intent(this, GameActivity::class.java)) }
        val cfg = btn("Configurações", Color.rgb(66, 133, 244)) { startActivity(Intent(this, SettingsActivity::class.java)) }
        val news = btn("O que mudou", Color.rgb(171, 71, 188)) { Novidades.showLatest(this) }
        val items = listOf<View>(title, gap, play, cfg, news)
        for ((i, v) in items.withIndex()) {
            val lp = LinearLayout.LayoutParams(if (i >= 2) dp(300) else if (i == 1) 1 else LinearLayout.LayoutParams.WRAP_CONTENT, if (i == 1) dp(10) else LinearLayout.LayoutParams.WRAP_CONTENT)
            if (i >= 3) lp.topMargin = dp(8) else if (i == 2) lp.topMargin = 0
            col.addView(v, lp)
            v.alpha = 0f; v.translationY = dp(24).toFloat()
            v.animate().alpha(1f).translationY(0f).setStartDelay(90L * i).setDuration(350).start()
        }
        root.addView(col, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        fun corner(t: String, g: Int) = TextView(this).apply {
            text = t; textSize = 12f; typeface = mcFont(); setTextColor(Color.WHITE); setShadowLayer(0.5f, dp(1).toFloat(), dp(1).toFloat(), Color.rgb(40, 40, 40)); setPadding(dp(10), dp(6), dp(10), dp(6))
            root.addView(this, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, g))
        }
        corner("CuboLand v${packageManager.getPackageInfo(packageName, 0).versionName}", Gravity.BOTTOM or Gravity.START)
        corner("Feito com ♥ e cubos", Gravity.BOTTOM or Gravity.END)
        setContentView(root)
        Novidades.showIfNew(this)
        Updater.autoCheck(this)
    }
}
