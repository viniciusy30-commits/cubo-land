package com.cuboland.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.MotionEvent
import android.widget.Button

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

private fun shade(c: Int, f: Float) = Color.rgb((Color.red(c) * f).toInt().coerceIn(0, 255), (Color.green(c) * f).toInt().coerceIn(0, 255), (Color.blue(c) * f).toInt().coerceIn(0, 255))

/** botão estilo bloco 3D: topo com degradê, borda inferior grossa que "afunda" ao tocar */
fun Context.btn(text: String, color: Int, onClick: () -> Unit): Button {
    val b = Button(this)
    b.text = text; b.isAllCaps = false; b.textSize = 17f; b.setTextColor(Color.WHITE)
    b.typeface = Typeface.DEFAULT_BOLD
    b.setShadowLayer(4f, 0f, 3f, Color.argb(140, 0, 0, 0))
    val r = dp(16).toFloat()
    fun mk(depth: Int): LayerDrawable {
        val base = GradientDrawable().apply { cornerRadius = r; setColor(shade(color, 0.55f)) }
        val top = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(shade(color, 1.18f), color, shade(color, 0.88f))).apply {
            cornerRadius = r; setStroke(dp(2), Color.argb(110, 255, 255, 255))
        }
        return LayerDrawable(arrayOf(base, top)).apply { setLayerInset(1, 0, 0, 0, dp(depth)) }
    }
    val up = mk(6); val down = mk(2)
    b.background = up
    b.setPadding(dp(14), dp(10), dp(14), dp(16))
    b.stateListAnimator = null
    b.setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { b.background = down; b.setPadding(dp(14), dp(14), dp(14), dp(12)); v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(70).start() }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { b.background = up; b.setPadding(dp(14), dp(10), dp(14), dp(16)); v.animate().scaleX(1f).scaleY(1f).setDuration(120).start() }
        }
        false
    }
    b.setOnClickListener { onClick() }
    return b
}
