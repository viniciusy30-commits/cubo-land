package com.cuboland.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.widget.Button

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.btn(text: String, color: Int, onClick: () -> Unit): Button {
    val b = Button(this)
    b.text = text; b.isAllCaps = false; b.textSize = 17f; b.setTextColor(Color.WHITE)
    b.typeface = Typeface.DEFAULT_BOLD
    b.background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(color); setStroke(dp(3), Color.argb(90, 255, 255, 255)) }
    b.stateListAnimator = null
    b.setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
        }
        false
    }
    b.setOnClickListener { onClick() }
    return b
}
