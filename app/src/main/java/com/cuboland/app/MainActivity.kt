package com.cuboland.app

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.sin

/** Logo: cada letra é um cubinho colorido com grama em cima, balançando de leve. */
class LogoView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val t0 = System.nanoTime()
    private val txt = "CUBOLAND"
    private val cols = intArrayOf(Cz.GREEN, Cz.SKY, Cz.ORANGE, Cz.LILAC, Cz.RED, Cz.TEAL, Cz.GOLD, Cz.GREEN)
    private val r = RectF()
    private val clip = Path()

    override fun onMeasure(wm: Int, hm: Int) {
        val w = MeasureSpec.getSize(wm).let { if (it <= 0) context.dp(380) else minOf(it, context.dp(400)) }
        setMeasuredDimension(w, context.dp(128))
    }

    override fun onDraw(c: Canvas) {
        val t = (System.nanoTime() - t0) / 1e9f
        val d = context.dp(1).toFloat()
        val gap = 4 * d
        val ts = (width - gap * 7) / 8f
        val th = ts * 1.12f
        val baseY = height * 0.12f
        p.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD); p.textAlign = Paint.Align.CENTER; p.textSize = ts * 0.78f
        for (i in txt.indices) {
            val x = i * (ts + gap)
            val y = baseY + sin(t * 2.2f + i * 0.65f) * 3.5f * d
            val lh = 5 * d; val rad = 9 * d; val col = cols[i]
            r.set(x, y + lh, x + ts, y + th + lh)
            p.style = Paint.Style.FILL; p.color = shadeC(col, 0.55f); c.drawRoundRect(r, rad, rad, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2.2f * d; p.color = Cz.INK; c.drawRoundRect(r, rad, rad, p)
            r.set(x, y, x + ts, y + th)
            p.style = Paint.Style.FILL; p.shader = LinearGradient(0f, y, 0f, y + th, shadeC(col, 1.2f), shadeC(col, 0.95f), Shader.TileMode.CLAMP)
            c.drawRoundRect(r, rad, rad, p); p.shader = null
            clip.reset(); clip.addRoundRect(r, rad, rad, Path.Direction.CW); c.save(); c.clipPath(clip)
            p.color = Color.rgb(96, 190, 70); c.drawRect(x, y, x + ts, y + th * 0.2f, p)
            p.color = Color.rgb(132, 220, 96); c.drawRect(x, y, x + ts, y + th * 0.07f, p)
            p.color = Color.rgb(96, 190, 70)
            c.drawRect(x + ts * 0.14f, y + th * 0.2f, x + ts * 0.3f, y + th * 0.3f, p); c.drawRect(x + ts * 0.62f, y + th * 0.2f, x + ts * 0.8f, y + th * 0.34f, p)
            p.color = Color.argb(40, 255, 255, 255); c.drawRect(x, y + th * 0.4f, x + ts * 0.12f, y + th, p)
            c.restore()
            r.set(x, y, x + ts, y + th)
            p.style = Paint.Style.STROKE; p.strokeWidth = 2.2f * d; p.color = Cz.INK; c.drawRoundRect(r, rad, rad, p)
            val ty = y + th * 0.78f
            p.style = Paint.Style.STROKE; p.strokeWidth = 4f * d; p.strokeJoin = Paint.Join.ROUND; p.color = Cz.INK
            c.drawText(txt[i].toString(), x + ts / 2, ty, p)
            p.style = Paint.Style.FILL; p.color = Color.WHITE; c.drawText(txt[i].toString(), x + ts / 2, ty, p)
        }
        val cx = width * 0.5f; val cy = height * 0.9f
        c.save(); c.rotate(-3f, cx, cy)
        val sc = 1f + 0.035f * sin(t * 3.5f); c.scale(sc, sc, cx, cy)
        p.textSize = 14 * d; p.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        val tw = p.measureText("Construa e lute!")
        r.set(cx - tw / 2 - 16 * d, cy - 15 * d, cx + tw / 2 + 16 * d, cy + 8 * d)
        p.style = Paint.Style.FILL; p.color = Cz.GOLD; c.drawRoundRect(r, 12 * d, 12 * d, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2 * d; p.color = Cz.INK; c.drawRoundRect(r, 12 * d, 12 * d, p)
        p.style = Paint.Style.FILL; p.color = Cz.INK; c.drawText("Construa e lute!", cx, cy + 2 * d, p)
        c.restore()
        postInvalidateOnAnimation()
    }
}

/** ilhazinha flutuante de grama onde o personagem fica em pé */
class PedestalView(ctx: Context, private val topPad: Int, private val bottomPad: Int) : View(ctx) {
    private val p = Paint()
    private val t0 = System.nanoTime()
    override fun onDraw(c: Canvas) {
        val d = context.dp(1).toFloat(); val t = (System.nanoTime() - t0) / 1e9f
        val ch = height - (topPad + bottomPad) * d
        val feet = topPad * d + ch * 0.95f
        val cx = width / 2f
        val pw = minOf(width * 0.78f, 250 * d); val l = cx - pw / 2
        p.style = Paint.Style.FILL
        p.color = Color.argb(50, 0, 0, 0); c.drawOval(l + pw * 0.08f, feet + 58 * d, l + pw * 0.92f, feet + 74 * d, p)
        val gt = feet - 4 * d
        for (k in 0 until 9) {
            val cw = pw / 9f
            for (r in 0 until 3) {
                p.color = shadeC(Color.rgb(134, 96, 62), 0.78f + 0.3f * hash2(k, r, 5))
                c.drawRect(l + k * cw, gt + 18 * d + r * 14 * d, l + (k + 1) * cw + 1f, gt + 18 * d + (r + 1) * 14 * d + 1f, p)
            }
        }
        for (k in 0 until 5) {
            val cw = pw / 5f; val hgt = (2 - kotlin.math.abs(k - 2)) * 10 * d + 10 * d
            p.color = shadeC(Color.rgb(128, 128, 132), 0.8f + 0.2f * hash2(k, 1, 9))
            c.drawRect(l + k * cw + cw * 0.12f, gt + 60 * d, l + (k + 1) * cw - cw * 0.12f, gt + 60 * d + hgt, p)
        }
        p.color = Color.rgb(96, 190, 70); c.drawRect(l, gt, l + pw, gt + 18 * d, p)
        p.color = Color.rgb(132, 220, 96); c.drawRect(l, gt, l + pw, gt + 5 * d, p)
        p.color = Color.rgb(96, 190, 70)
        for (k in 0 until 9) if (hash2(k, 2, 4) > 0.45f) c.drawRect(l + k * pw / 9f, gt + 18 * d, l + k * pw / 9f + pw / 9f * 0.7f, gt + 25 * d, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = 2.5f * d; p.color = Cz.INK; c.drawRect(l, gt, l + pw, gt + 60 * d, p)
        p.style = Paint.Style.FILL
        val fl = intArrayOf(Color.rgb(255, 120, 150), Color.rgb(255, 230, 100), Color.rgb(250, 250, 250))
        for (k in 0 until 3) {
            val fx = l + pw * (0.12f + 0.38f * k + 0.05f * k); val fy = gt - 2 * d
            p.color = Color.rgb(60, 140, 60); c.drawRect(fx, fy - 6 * d, fx + 2 * d, fy, p)
            p.color = fl[k]; c.drawRect(fx - 2 * d, fy - 10 * d, fx + 4 * d, fy - 5 * d, p)
        }
        for (k in 0 until 7) {
            val a = (t * 0.35f + k * 0.14f) % 1f
            val px = l + pw * hash2(k, 8, 3); val py = feet - a * ch * 0.7f
            p.color = Color.argb(((1f - a) * 200).toInt(), 255, 244, 170); c.drawRect(px, py, px + 4 * d, py + 4 * d, p)
        }
        postInvalidateOnAnimation()
    }
}

class MainActivity : CozyActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        root.addView(SceneBg(this))

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(dp(24), dp(16), dp(24), dp(16)) }

        // ESQUERDA: personagem em cima da ilhazinha
        val left = FrameLayout(this)
        left.addView(PedestalView(this, 20, 80), FrameLayout.LayoutParams(-1, -1))
        val cv = CharView(this).apply { transparentBg = true; sway = true }
        left.addView(cv, FrameLayout.LayoutParams(-1, -1).apply { topMargin = dp(20); bottomMargin = dp(80) })
        left.addView(cBtn("✎  Editar", Cz.ORANGE, 13f) { startActivity(Intent(this, CharacterActivity::class.java)) },
            FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(4) })
        row.addView(left, lpW(0, ViewGroup.LayoutParams.MATCH_PARENT, 0.85f))
        left.popIn(0, 0)

        // DIREITA: logo + botões
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        val logo = LogoView(this)
        col.addView(logo, lpW(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val play = cBtn("▶   JOGAR", Cz.GREEN, 26f) { startActivity(Intent(this, WorldsActivity::class.java)) }
        play.minHeight = dp(66)
        col.addView(play, lpW(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
        val last = Worlds.all(this).firstOrNull()
        col.addView(cTxt(if (last != null) "Continuar em: ${last.name}" else "Crie seu primeiro mundo!", 12f, Cz.CREAM).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, dp(2)) })
        val r2 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r2.addView(cBtn("☺  Personagem", Cz.ORANGE, 15f) { startActivity(Intent(this, CharacterActivity::class.java)) }, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
        r2.addView(cBtn("⚙  Ajustes", Cz.SKY, 15f) { startActivity(Intent(this, SettingsActivity::class.java)) }, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
        col.addView(r2, lpW(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })
        row.addView(col, lpW(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { leftMargin = dp(12); rightMargin = dp(40) })
        for ((i, v) in listOf<View>(logo, play, r2).withIndex()) v.popIn(120L + 110L * i, 26)

        root.addView(row, FrameLayout.LayoutParams(-1, -1))

        // topo direito: novidades e atualização
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val news = FrameLayout(this)
        news.addView(cIconBtn("✦", Cz.LILAC, 44) { startActivity(Intent(this, SettingsActivity::class.java).putExtra("tab", 3)) })
        if (Novidades.hasUnseen(this)) news.addView(View(this).apply { background = CozyBox(this@MainActivity, Cz.RED, 6f, false) }, FrameLayout.LayoutParams(dp(12), dp(12), Gravity.TOP or Gravity.END))
        top.addView(news, lpW(dp(44), dp(44)).apply { rightMargin = dp(8) })
        top.addView(cIconBtn("⟳", Cz.TEAL, 44) { Updater.check(this, true) })
        root.addView(top, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { topMargin = dp(14); rightMargin = dp(22) })

        fun corner(t: String, g: Int) {
            val tv = cTxt(t, 11f, Color.WHITE)
            tv.alpha = 0.85f; tv.setPadding(dp(14), dp(6), dp(14), dp(8))
            root.addView(tv, FrameLayout.LayoutParams(-2, -2, g))
        }
        corner("CuboLand v${packageManager.getPackageInfo(packageName, 0).versionName}", Gravity.BOTTOM or Gravity.START)
        corner("Feito com ♥ e cubos", Gravity.BOTTOM or Gravity.END)

        setContentView(root)
        Novidades.showIfNew(this)
        Updater.autoCheck(this)
    }

    override fun onResume() {
        super.onResume()
        Novidades.showIfNew(this)
    }
}
