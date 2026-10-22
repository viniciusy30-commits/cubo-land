package com.cuboland.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compact review controls for the test gallery.
 * Touches outside the actual buttons are deliberately passed through to the game HUD/GL surface.
 */
class BlockReviewOverlay(ctx: Context, private val game: Game) : View(ctx) {
    private val prefs = ctx.getSharedPreferences("block_review", Context.MODE_PRIVATE)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttons = ArrayList<Pair<RectF, String>>()
    private var pressedButton: String? = null
    private var targetId = 0
    private var targetX = 0
    private var targetY = 0
    private var targetZ = 0
    private var message = "Mire em um bloco para avaliar"
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        game.updateReviewTarget()
        targetId = if (game.hasHit) game.world.get(game.hx, game.hy, game.hz) else 0
        targetX = game.hx
        targetY = game.hy
        targetZ = game.hz
        buttons.clear()

        val w = width.toFloat()
        val h = height.toFloat()
        val buttonW = dp(88f)
        val buttonH = dp(36f)
        val gap = dp(6f)
        val totalW = buttonW * 3f + gap * 2f
        val left = ((w - totalW) / 2f).coerceAtLeast(dp(8f))
        val top = h - dp(16f) - buttonH

        if (targetId > 0) {
            val status = prefs.getString("status_$targetId", null)
            val name = B.names.getOrNull(targetId).orEmpty().ifBlank { "Bloco #$targetId" }
            val label = when (status) {
                "valid" -> "Válido"
                "invalid" -> "Inválido"
                else -> "Sem avaliação"
            }
            val panel = RectF(left, top - dp(31f), left + totalW, top + buttonH)
            paint.color = Color.argb(210, 15, 20, 29)
            c.drawRoundRect(panel, dp(9f), dp(9f), paint)
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.textSize = dp(11f)
            val title = "$name (#$targetId) · $label"
            val clipped = if (paint.measureText(title) > totalW - dp(12f)) "Bloco #$targetId · $label" else title
            c.drawText(clipped, left + totalW / 2f, top - dp(11f), paint)
            addButton(c, RectF(left, top, left + buttonW, top + buttonH), "✓ Válido", Color.rgb(35, 135, 79))
            addButton(c, RectF(left + buttonW + gap, top, left + buttonW * 2f + gap, top + buttonH), "✕ Inválido", Color.rgb(178, 55, 61))
            addButton(c, RectF(left + (buttonW + gap) * 2f, top, left + (buttonW + gap) * 2f + buttonW, top + buttonH), "Copiar", Color.rgb(62, 91, 151))
        } else {
            // No target: keep a small hint above the bottom edge, but never block gameplay touches.
            paint.color = Color.argb(175, 15, 20, 29)
            val hint = RectF(dp(12f), h - dp(42f), w - dp(12f), h - dp(12f))
            c.drawRoundRect(hint, dp(8f), dp(8f), paint)
            paint.color = Color.WHITE
            paint.textSize = dp(11f)
            paint.textAlign = Paint.Align.CENTER
            c.drawText(message, w / 2f, h - dp(23f), paint)
        }
        paint.textAlign = Paint.Align.LEFT
        postInvalidateDelayed(180)
    }

    private fun addButton(c: Canvas, r: RectF, text: String, color: Int) {
        paint.color = color
        c.drawRoundRect(r, dp(7f), dp(7f), paint)
        paint.color = Color.WHITE
        paint.textSize = dp(11f)
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        c.drawText(text, r.centerX(), r.centerY() + dp(4f), paint)
        paint.textAlign = Paint.Align.LEFT
        buttons.add(r to text)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = buttons.firstOrNull { it.first.contains(e.x, e.y) }
                if (hit == null) {
                    pressedButton = null
                    return false
                }
                pressedButton = hit.second
                return true
            }
            MotionEvent.ACTION_MOVE -> return pressedButton != null
            MotionEvent.ACTION_CANCEL -> {
                pressedButton = null
                return false
            }
            MotionEvent.ACTION_UP -> {
                val pressed = pressedButton
                pressedButton = null
                if (pressed == null) return false
                val hit = buttons.firstOrNull { it.first.contains(e.x, e.y) && it.second == pressed }
                if (hit == null || targetId <= 0 || !game.hasHit) {
                    invalidate()
                    return true
                }
                when (pressed) {
                    "✓ Válido" -> saveStatus("valid")
                    "✕ Inválido" -> saveStatus("invalid")
                    "Copiar" -> {
                        game.hotbar[game.sel.coerceIn(0, 7)] = targetId
                        message = "Bloco #$targetId copiado para a mão"
                    }
                }
                invalidate()
                return true
            }
        }
        return pressedButton != null
    }

    private fun saveStatus(status: String) {
        val key = "status_$targetId"
        val data = JSONObject()
        data.put("id", targetId)
        data.put("name", B.names.getOrNull(targetId).orEmpty())
        data.put("status", status)
        data.put("x", targetX)
        data.put("y", targetY)
        data.put("z", targetZ)
        data.put("updatedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()))
        val all = JSONObject(prefs.getString("report", "{}") ?: "{}")
        all.put(targetId.toString(), data)
        prefs.edit().putString(key, status).putString("report", all.toString()).apply()
        message = if (status == "valid") "Bloco #$targetId marcado como válido" else "Bloco #$targetId marcado como inválido"
    }

    fun exportReport(): String = prefs.getString("report", "{}") ?: "{}"
}
