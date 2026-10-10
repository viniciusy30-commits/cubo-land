package com.cuboland.app

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.widget.Button

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun mixC(a: Int, b: Int, t: Float) = Color.rgb(
    (Color.red(a) * (1 - t) + Color.red(b) * t).toInt().coerceIn(0, 255),
    (Color.green(a) * (1 - t) + Color.green(b) * t).toInt().coerceIn(0, 255),
    (Color.blue(a) * (1 - t) + Color.blue(b) * t).toInt().coerceIn(0, 255))
fun shadeC(c: Int, f: Float) = Color.rgb((Color.red(c) * f).toInt().coerceIn(0, 255), (Color.green(c) * f).toInt().coerceIn(0, 255), (Color.blue(c) * f).toInt().coerceIn(0, 255))
fun hash2(x: Int, y: Int, s: Int = 0): Float { var n = x * 374761393 + y * 668265263 + s * 1442695041.toInt(); n = (n xor (n ushr 13)) * 1274126177; n = n xor (n ushr 16); return (n and 255) / 255f }

/** botão de pedra pixelado (estilo Minecraft): borda preta, chanfro claro em cima/esquerda, escuro embaixo/direita */
class McBtnDrawable(private val ctx: Context, private val accent: Int, private val pressed: Boolean) : Drawable() {
    private val p = Paint()
    override fun draw(c: Canvas) {
        val w = bounds.width(); val h = bounds.height(); val u = ctx.dp(2).coerceAtLeast(2); val cell = u * 2
        val base0 = mixC(Color.rgb(112, 112, 112), accent, 0.2f)
        val base = if (pressed) mixC(base0, Color.rgb(110, 130, 200), 0.3f) else base0
        p.color = if (pressed) Color.WHITE else Color.BLACK; c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        var yy = u
        while (yy < h - u) { var xx = u
            while (xx < w - u) {
                p.color = shadeC(base, 0.93f + 0.14f * hash2(xx / cell, yy / cell, accent and 7))
                c.drawRect(xx.toFloat(), yy.toFloat(), min2(xx + cell, w - u).toFloat(), min2(yy + cell, h - u).toFloat(), p); xx += cell }
            yy += cell }
        p.color = shadeC(base, 1.38f); c.drawRect(u.toFloat(), u.toFloat(), (w - u).toFloat(), (u * 2).toFloat(), p); c.drawRect(u.toFloat(), u.toFloat(), (u * 2).toFloat(), (h - u).toFloat(), p)
        p.color = shadeC(base, 0.5f); c.drawRect(u.toFloat(), (h - u * 3).toFloat(), (w - u).toFloat(), (h - u).toFloat(), p); c.drawRect((w - u * 2).toFloat(), u.toFloat(), (w - u).toFloat(), (h - u).toFloat(), p)
    }
    private fun min2(a: Int, b: Int) = if (a < b) a else b
    override fun setAlpha(a: Int) {}
    override fun setColorFilter(f: ColorFilter?) {}
    @Suppress("OVERRIDE_DEPRECATION") override fun getOpacity() = PixelFormat.OPAQUE
}

/** fundo de terra escura repetido, igual às telas de opções do Minecraft */
fun Context.dirtBackground(): Drawable {
    val s = 16; val bmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
    for (y in 0 until s) for (x in 0 until s) {
        val n = hash2(x, y, 5); val base = if (n > 0.8f) Color.rgb(96, 70, 48) else Color.rgb(78, 56, 38)
        bmp.setPixel(x, y, shadeC(base, 0.45f + 0.2f * hash2(x, y, 9)))
    }
    val big = Bitmap.createScaledBitmap(bmp, dp(64), dp(64), false)
    return BitmapDrawable(resources, big).apply { setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT); isFilterBitmap = false }
}

fun Context.mcFont(): Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

/** botão estilo Minecraft; a cor só dá um leve tom à pedra */
fun Context.btn(text: String, color: Int, onClick: () -> Unit): Button {
    val b = Button(this)
    b.text = text; b.isAllCaps = false; b.textSize = 16f; b.setTextColor(Color.WHITE)
    b.typeface = mcFont(); b.minHeight = dp(50); b.minimumHeight = dp(50)
    b.setShadowLayer(0.5f, dp(2).toFloat(), dp(2).toFloat(), Color.rgb(50, 50, 50))
    val up = McBtnDrawable(this, color, false); val down = McBtnDrawable(this, color, true)
    b.background = up; b.stateListAnimator = null
    b.setPadding(dp(16), dp(10), dp(16), dp(10))
    b.setOnTouchListener { _, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { b.background = down; b.setTextColor(Color.rgb(255, 255, 160)) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { b.background = up; b.setTextColor(Color.WHITE) }
        }
        false
    }
    b.setOnClickListener { onClick() }
    return b
}
