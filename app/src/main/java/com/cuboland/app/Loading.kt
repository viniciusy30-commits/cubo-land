package com.cuboland.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.Gravity
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.abs
import kotlin.math.sin

/** cubinho quicando com sombra e brilho, estilo pixel */
class BounceCube(ctx: Context) : android.view.View(ctx) {
    private val p = Paint()
    private var t = 0f
    private val an = ValueAnimator.ofFloat(0f, 1f).apply { duration = 900; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator(); addUpdateListener { t = it.animatedValue as Float; invalidate() } }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); an.start() }
    override fun onDetachedFromWindow() { an.cancel(); super.onDetachedFromWindow() }
    private fun r(c: Canvas, x: Float, y: Float, w: Float, h: Float, col: Int) { p.color = col; c.drawRect(x, y, x + w, y + h, p) }
    override fun onDraw(c: Canvas) {
        val u = context.pxU().toFloat() * 2f
        val s = 12 * u
        val k = abs(sin(t * 3.1416f))                 // 0 no chão .. 1 no topo
        val y = height - s - 3 * u - k * (height - s - 4 * u) * 0.9f
        val sx = width / 2f
        val sh = s * (1.1f - 0.5f * k)
        p.color = Color.argb((90 - 50 * k).toInt(), 0, 0, 0)
        c.drawRect(sx - sh / 2, height - 2.5f * u, sx + sh / 2, height - 0.5f * u, p)
        val x = sx - s / 2
        val squash = if (k < 0.08f) (0.08f - k) * 3f else 0f
        val hh = s * (1f - squash); val yy = y + s - hh; val ww = s * (1f + squash * 0.6f); val xx = sx - ww / 2
        r(c, xx - u, yy - u, ww + 2 * u, hh + 2 * u, Cz.INK)
        r(c, xx, yy, ww, hh, Cz.GREEN)
        r(c, xx, yy, ww, hh * 0.28f, Color.rgb(140, 224, 96))       // grama
        r(c, xx, yy + hh * 0.28f, ww, hh * 0.72f, Color.rgb(150, 104, 66)) // terra
        r(c, xx + ww * 0.15f, yy + hh * 0.55f, u, u, Color.rgb(120, 82, 50)); r(c, xx + ww * 0.6f, yy + hh * 0.75f, u, u, Color.rgb(120, 82, 50))
        r(c, xx, yy, ww, u, Color.argb(120, 255, 255, 255))
        r(c, xx + ww - u, yy, u, hh, Color.argb(60, 0, 0, 0))
        // olhinhos
        r(c, xx + ww * 0.25f, yy + hh * 0.12f, u, u, Cz.INK); r(c, xx + ww * 0.65f, yy + hh * 0.12f, u, u, Cz.INK)
    }
}

/** tela de carregamento fofinha: céu animado, cubinho pulando, barra de progresso e dica */
class LoadingView(private val c0: Context, worldName: String, flat: Boolean) : FrameLayout(c0) {
    private fun dp(v: Int) = c0.dp(v)
    private val bar = PixBar(c0)
    private val msg: android.widget.TextView
    private var shown = 0f; private var target = 0.05f
    private val tips = arrayOf("Dica: toque duas vezes em pular pra voar!", "Dica: nade rápido pra fazer ondinhas na água.", "Dica: no modo criativo tudo quebra num toque.", "Dica: cada bloco deixa uma pegada diferente.", "Dica: monte sua hotbar com seus itens favoritos.", "Dica: use a semente pra recriar um mundo.")
    private val steps = arrayOf("Plantando árvores...", "Enchendo os rios...", "Acordando os passarinhos...", "Arrumando a casa...", "Quase lá...")
    private val tick = object : Runnable {
        override fun run() {
            target = (target + 0.012f).coerceAtMost(0.88f).coerceAtLeast(target)
            if (shown < target) shown += (target - shown) * 0.12f
            bar.set((shown * 100).toInt())
            msg.text = steps[((shown * steps.size).toInt()).coerceIn(0, steps.size - 1)]
            postDelayed(this, 40)
        }
    }
    fun setProgress(v: Float) { target = v }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); post(tick) }
    override fun onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow() }
    init {
        isClickable = true
        addView(SceneBg(c0, 0.15f), LayoutParams(-1, -1))
        val col = LinearLayout(c0).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(dp(30), dp(10), dp(30), dp(10)) }
        col.addView(c0.cTxtL("CUBO LAND", 30f, Cz.GOLD).apply { gravity = Gravity.CENTER })
        col.addView(BounceCube(c0), lpW(dp(150), dp(110)).apply { topMargin = dp(8) })
        val card = c0.cPanel(Cz.PANEL, 18f, 14)
        card.addView(c0.cTxt("Entrando em", 12f, Cz.SOFT, false).apply { gravity = Gravity.CENTER })
        card.addView(c0.cTxt(worldName, 22f, Color.WHITE).apply { gravity = Gravity.CENTER; maxLines = 1 })
        card.addView(c0.cTxt(if (flat) "Planície e rio" else "Ilha", 11f, if (flat) Cz.SKY else Cz.GREEN, false).apply { gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(8)) })
        card.addView(bar, lpW(-1, dp(18)))
        msg = c0.cTxt(steps[0], 12f, Cz.CREAM, false).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, 0) }
        card.addView(msg, lpW(-1, -2))
        col.addView(card, lpW(dp(340), -2).apply { topMargin = dp(6) })
        col.addView(c0.cTxtL(tips[(System.currentTimeMillis() % tips.size).toInt()], 12f, Color.WHITE, true).apply { gravity = Gravity.CENTER; setPadding(0, dp(14), 0, 0) }, lpW(dp(420), -2))
        addView(col, LayoutParams(-1, -1).apply { gravity = Gravity.CENTER })
    }
}
