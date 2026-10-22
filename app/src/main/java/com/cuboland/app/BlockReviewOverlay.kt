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

/** Controles de revisão no mundo 3D: a avaliação sempre usa o bloco atualmente mirado. */
class BlockReviewOverlay(ctx: Context, private val game: Game) : View(ctx) {
    private val prefs = ctx.getSharedPreferences("block_review", Context.MODE_PRIVATE)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttons = ArrayList<Pair<RectF, String>>()
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
        targetX = game.hx; targetY = game.hy; targetZ = game.hz
        buttons.clear()
        val w = width.toFloat()
        val h = height.toFloat()
        val top = h - dp(112f)
        if (targetId > 0) {
            val status = prefs.getString("status_$targetId", null)
            val name = B.names.getOrNull(targetId).orEmpty().ifBlank { "Bloco #$targetId" }
            paint.color = Color.argb(220, 15, 20, 29)
            c.drawRoundRect(RectF(dp(12f), top - dp(42f), w - dp(12f), top - dp(5f)), dp(10f), dp(10f), paint)
            paint.color = Color.WHITE; paint.textSize = dp(13f); paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            c.drawText("$name  (#$targetId) • $status".replace("null", "Sem avaliação"), dp(22f), top - dp(18f), paint)
            addButton(c, RectF(dp(12f), top, w * .34f, top + dp(42f)), "✓ Válido", Color.rgb(35, 135, 79))
            addButton(c, RectF(w * .34f + dp(3f), top, w * .67f, top + dp(42f)), "✕ Inválido", Color.rgb(178, 55, 61))
            addButton(c, RectF(w * .67f + dp(3f), top, w - dp(12f), top + dp(42f)), "Copiar", Color.rgb(62, 91, 151))
        } else {
            paint.color = Color.argb(185, 15, 20, 29)
            c.drawRoundRect(RectF(dp(12f), h - dp(62f), w - dp(12f), h - dp(12f)), dp(10f), dp(10f), paint)
            paint.color = Color.WHITE; paint.textSize = dp(13f); paint.textAlign = Paint.Align.CENTER
            c.drawText(message, w / 2f, h - dp(32f), paint)
            paint.textAlign = Paint.Align.LEFT
        }
        postInvalidateDelayed(120)
    }

    private fun addButton(c: Canvas, r: RectF, text: String, color: Int) {
        paint.color = color
        c.drawRoundRect(r, dp(8f), dp(8f), paint)
        paint.color = Color.WHITE; paint.textSize = dp(12f); paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER
        c.drawText(text, r.centerX(), r.centerY() + dp(4f), paint)
        paint.textAlign = Paint.Align.LEFT
        buttons.add(r to text)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked != MotionEvent.ACTION_UP) return true
        val b = buttons.firstOrNull { it.first.contains(e.x, e.y) } ?: return true
        if (targetId <= 0 || !game.hasHit) return true
        when {
            b.second.contains("Válido") -> saveStatus("valid")
            b.second.contains("Inválido") -> saveStatus("invalid")
            b.second == "Copiar" -> {
                game.hotbar[game.sel.coerceIn(0, 7)] = targetId
                message = "Bloco #$targetId copiado para a mão"
            }
        }
        invalidate()
        return true
    }

    private fun saveStatus(status: String) {
        val key = "status_$targetId"
        val data = JSONObject()
        data.put("id", targetId)
        data.put("name", B.names.getOrNull(targetId).orEmpty())
        data.put("status", status)
        data.put("x", targetX); data.put("y", targetY); data.put("z", targetZ)
        data.put("updatedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()))
        prefs.edit().putString(key, status).apply()
        val all = JSONObject(prefs.getString("report", "{}") ?: "{}")
        all.put(targetId.toString(), data)
        prefs.edit().putString("report", all.toString()).apply()
        message = if (status == "valid") "Bloco #$targetId marcado como válido" else "Bloco #$targetId marcado como inválido"
    }

    fun exportReport(): String = prefs.getString("report", "{}") ?: "{}"
}
