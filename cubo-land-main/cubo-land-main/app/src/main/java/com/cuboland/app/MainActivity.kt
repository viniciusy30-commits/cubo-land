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

class MenuBg(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val t0 = System.nanoTime()

    private fun cube(c: Canvas, cx: Float, cy: Float, s: Float, top: Int, side: Int) {
        p.style = Paint.Style.FILL
        path.reset(); path.moveTo(cx, cy - s); path.lineTo(cx + s * 0.87f, cy - s * 0.5f); path.lineTo(cx, cy); path.lineTo(cx - s * 0.87f, cy - s * 0.5f); path.close()
        p.color = top; c.drawPath(path, p)
        path.reset(); path.moveTo(cx - s * 0.87f, cy - s * 0.5f); path.lineTo(cx, cy); path.lineTo(cx, cy + s); path.lineTo(cx - s * 0.87f, cy + s * 0.5f); path.close()
        p.color = side; c.drawPath(path, p)
        path.reset(); path.moveTo(cx + s * 0.87f, cy - s * 0.5f); path.lineTo(cx, cy); path.lineTo(cx, cy + s); path.lineTo(cx + s * 0.87f, cy + s * 0.5f); path.close()
        p.color = Color.rgb((Color.red(side) * 0.8f).toInt(), (Color.green(side) * 0.8f).toInt(), (Color.blue(side) * 0.8f).toInt()); c.drawPath(path, p)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val t = (System.nanoTime() - t0) / 1e9f
        p.shader = LinearGradient(0f, 0f, 0f, h, Color.rgb(110, 200, 245), Color.rgb(200, 238, 255), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        p.color = Color.argb(200, 255, 255, 255)
        for (i in 0 until 5) {
            val x = ((i * 260f + t * 14f) % (w + 300f)) - 150f
            c.drawRoundRect(x, 40f + i * 55f, x + 170f + i * 20f, 80f + i * 55f, 20f, 20f, p)
        }
        val s = h * 0.17f
        val base = w * 0.28f
        for (layer in 0 until 3) for (k in 0..layer) {
            val bob = sin(t * 1.6f + layer + k) * 6f
            val cx = base + (k - layer / 2f) * s * 1.74f
            val cy = h * 0.42f + layer * s * 1.0f + bob
            val grass = layer == 0
            cube(c, cx, cy, s, if (grass) Color.rgb(102, 200, 76) else Color.rgb(140, 96, 57),
                if (layer == 2) Color.rgb(154, 160, 168) else Color.rgb(140, 96, 57))
        }
        cube(c, base, h * 0.42f - s * 1.4f + sin(t * 2f) * 8f, s * 0.55f, Color.rgb(255, 242, 168), Color.rgb(255, 216, 74))
        postInvalidateOnAnimation()
    }
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        root.addView(MenuBg(this))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        val title = TextView(this).apply {
            text = "CuboLand"; textSize = 44f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(12f, 0f, 6f, Color.argb(160, 20, 40, 90))
        }
        val sub = TextView(this).apply { text = "Construa. Explore. Lute."; textSize = 16f; setTextColor(Color.WHITE); setPadding(0, 0, 0, dp(18)) }
        val play = btn("▶  Jogar", Color.rgb(76, 175, 80)) { startActivity(Intent(this, GameActivity::class.java)) }
        val cfg = btn("⚙  Configurações", Color.rgb(66, 133, 244)) { startActivity(Intent(this, SettingsActivity::class.java)) }
        val ver = TextView(this).apply {
            text = "versão ${packageManager.getPackageInfo(packageName, 0).versionName}"; textSize = 12f
            setTextColor(Color.WHITE); setPadding(0, dp(12), 0, 0)
        }
        val items = listOf<View>(title, sub, play, cfg, ver)
        for ((i, v) in items.withIndex()) {
            val lp = LinearLayout.LayoutParams(if (i == 2 || i == 3) dp(260) else LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = if (i == 3) dp(10) else 0
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
