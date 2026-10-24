package com.cuboland.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Compact review controls; touches outside buttons are passed through to gameplay. */
class BlockReviewOverlay(ctx: Context, private val game: Game, private val captureScreenshot: ((Bitmap?) -> Unit) -> Unit) : View(ctx) {
    private val prefs = ctx.getSharedPreferences("block_review", Context.MODE_PRIVATE)
    private val syncer = GithubReportSync(ctx)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttons = ArrayList<Pair<RectF, String>>()
    private var pressedButton: String? = null
    private var targetId = 0
    private var targetX = 0
    private var targetY = 0
    private var targetZ = 0
    private var message = "Mire em um bloco para avaliar"
    private var syncing = false
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    private val settingsRect get() = RectF(width - dp(48f), dp(10f), width - dp(10f), dp(48f))

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        game.updateReviewTarget()
        targetId = if (game.hasHit) game.world.get(game.hx, game.hy, game.hz) else 0
        targetX = game.hx; targetY = game.hy; targetZ = game.hz
        buttons.clear()
        val w = width.toFloat()
        val h = height.toFloat()

        val settings = settingsRect
        paint.color = Color.argb(205, 15, 20, 29)
        c.drawRoundRect(settings, dp(9f), dp(9f), paint)
        paint.color = Color.WHITE; paint.textSize = dp(18f); paint.textAlign = Paint.Align.CENTER
        c.drawText("⚙", settings.centerX(), settings.centerY() + dp(6f), paint)
        buttons.add(settings to "Configurar GitHub")

        // Controles compactos, posicionados acima da hotbar.
        val buttonH = dp(28f)
        val gap = dp(4f)
        val buttonW = minOf(dp(78f), (w - dp(28f) - gap * 2f) / 3f)
        val totalW = buttonW * 3f + gap * 2f
        val left = (w - totalW) / 2f
        val top = h - dp(82f) - buttonH

        if (targetId > 0) {
            val status = prefs.getString("status_$targetId", null)
            val name = B.names.getOrNull(targetId).orEmpty().ifBlank { "Bloco #$targetId" }
            val label = when (status) { "valid" -> "Válido"; "invalid" -> "Inválido"; else -> "Sem avaliação" }
            val panel = RectF(left, top - dp(24f), left + totalW, top + buttonH)
            paint.color = Color.argb(210, 15, 20, 29); c.drawRoundRect(panel, dp(9f), dp(9f), paint)
            paint.color = Color.WHITE; paint.textAlign = Paint.Align.CENTER; paint.textSize = dp(10f)
            val title = "$name (#$targetId) · $label"
            c.drawText(if (paint.measureText(title) > totalW - dp(12f)) "Bloco #$targetId · $label" else title,
                left + totalW / 2f, top - dp(8f), paint)
            addButton(c, RectF(left, top, left + buttonW, top + buttonH), "✓ Válido", Color.rgb(35, 135, 79))
            addButton(c, RectF(left + buttonW + gap, top, left + buttonW * 2f + gap, top + buttonH), "✕ Inválido", Color.rgb(178, 55, 61))
            addButton(c, RectF(left + (buttonW + gap) * 2f, top, left + totalW, top + buttonH), "Copiar", Color.rgb(62, 91, 151))
        }

        // Aviso discreto no topo, sem cobrir a hotbar.
        val bannerText = if (syncing) "Sincronizando com GitHub…" else message
        if (bannerText.isNotBlank() && bannerText != "Mire em um bloco para avaliar") {
            paint.textSize = dp(10f); paint.textAlign = Paint.Align.CENTER
            val maxTextW = w - dp(100f)
            val shown = if (paint.measureText(bannerText) > maxTextW) {
                if (syncing) "Sincronizando com GitHub…" else bannerText.take(48) + "…"
            } else bannerText
            val bannerW = minOf(w - dp(88f), maxOf(dp(150f), paint.measureText(shown) + dp(20f)))
            val banner = RectF((w - bannerW) / 2f, dp(10f), (w + bannerW) / 2f, dp(34f))
            paint.color = Color.argb(205, 15, 20, 29); c.drawRoundRect(banner, dp(7f), dp(7f), paint)
            paint.color = Color.WHITE; c.drawText(shown, w / 2f, dp(25f), paint)
        }
        paint.textAlign = Paint.Align.LEFT
        postInvalidateDelayed(300)
    }

    private fun addButton(c: Canvas, r: RectF, text: String, color: Int) {
        paint.color = color; c.drawRoundRect(r, dp(7f), dp(7f), paint)
        paint.color = Color.WHITE; paint.textSize = dp(11f); paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER; c.drawText(text, r.centerX(), r.centerY() + dp(4f), paint)
        paint.textAlign = Paint.Align.LEFT
        buttons.add(r to text)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val hit = buttons.firstOrNull { it.first.contains(e.x, e.y) }
                if (hit == null) { pressedButton = null; return false }
                pressedButton = hit.second
                return true
            }
            MotionEvent.ACTION_MOVE -> return pressedButton != null
            MotionEvent.ACTION_CANCEL -> { pressedButton = null; return false }
            MotionEvent.ACTION_UP -> {
                val pressed = pressedButton; pressedButton = null
                if (pressed == null) return false
                if (buttons.none { it.first.contains(e.x, e.y) && it.second == pressed }) return true
                when (pressed) {
                    "Configurar GitHub" -> showGitHubDialog()
                    "✓ Válido" -> if (targetId > 0 && game.hasHit) saveStatus("valid")
                    "✕ Inválido" -> if (targetId > 0 && game.hasHit) {
                        val id = targetId; val x = targetX; val y = targetY; val z = targetZ
                        message = "Capturando bloco #$id…"; syncing = true; invalidate()
                        captureScreenshot { bitmap ->
                            post {
                                syncing = false; message = ""; invalidate()
                                askReasons(id, { reasons, note -> saveStatus("invalid", bitmap, id, x, y, z, reasons, note) },
                                    { if (bitmap != null && !bitmap.isRecycled) bitmap.recycle() })
                            }
                        }
                    }
                    "Copiar" -> if (targetId > 0 && game.hasHit) {
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

    private val REASONS = listOf("Peças flutuando", "Textura bugada", "Incompleto / faltando partes", "Textura piscando", "Cor errada",
        "Tamanho / proporção errados", "Direção / rotação errada", "Sem colisão (atravessa)", "Transparência errada", "Não abre / não funciona", "Outro")

    /** Escolha do motivo da invalidação (vários, mais uma observação opcional). Cancelar descarta a invalidação; devolve null se o diálogo não abriu. */
    private fun askReasons(id: Int, onDone: (List<String>, String) -> Unit, onCancel: () -> Unit) {
        val boxes = REASONS.map { r -> android.widget.CheckBox(context).apply { text = r; textSize = 15f; setTextColor(Color.WHITE) } }
        val note = EditText(context).apply {
            hint = "Observação (opcional)"; textSize = 14f; minLines = 2
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; for (b in boxes) addView(b) }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f).toInt(), dp(4f).toInt(), dp(20f).toInt(), 0)
            addView(android.widget.ScrollView(context).apply { addView(list) }, LinearLayout.LayoutParams(-1, dp(260f).toInt()))
            addView(note, LinearLayout.LayoutParams(-1, -2))
        }
        val name = B.names.getOrNull(id).orEmpty()
        val dialog = AlertDialog.Builder(context)
            .setTitle("Por que está inválido?\n$name (#$id)")
            .setView(content)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Invalidar", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val chosen = REASONS.filterIndexed { i, _ -> boxes[i].isChecked }
                dialog.dismiss()
                onDone(chosen, note.text.toString().trim())
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                dialog.dismiss(); message = "Invalidação cancelada"; invalidate(); onCancel()
            }
        }
        dialog.setOnCancelListener { message = "Invalidação cancelada"; invalidate(); onCancel() }
        dialog.show()
    }

    private fun showGitHubDialog() {
        val input = EditText(context).apply {
            hint = "github_pat_…"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setSingleLine(true)
            textSize = 14f
        }
        val info = TextView(context).apply {
            text = if (syncer.hasToken())
                "Token protegido pelo Android Keystore. Cole outro para substituir; ou toque em Sincronizar para enviar o relatório salvo."
            else
                "Crie um token Fine-grained para o repositório viniciusy30-commits/cubo-land com Contents: Read and write. O token será criptografado neste aparelho e nunca ficará no código."
            textSize = 13f
            setTextColor(Color.LTGRAY)
            setPadding(0, 0, 0, dp(10f).toInt())
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f).toInt(), dp(8f).toInt(), dp(20f).toInt(), 0)
            addView(info)
            addView(input, LinearLayout.LayoutParams(-1, dp(48f).toInt()))
        }
        val dialog = AlertDialog.Builder(context)
            .setTitle("Relatório no GitHub")
            .setView(content)
            .setNeutralButton("Sincronizar", null)
            .setNegativeButton("Apagar token", null)
            .setPositiveButton("Salvar token", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).isEnabled = syncer.hasToken()
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).isEnabled = syncer.hasToken()
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { dialog.dismiss(); syncReport() }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                syncer.forgetToken()
                message = "Token removido deste aparelho"
                dialog.dismiss()
                invalidate()
            }
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                try {
                    syncer.saveToken(input.text.toString().trim())
                    message = "Token protegido. Sincronizando…"
                    dialog.dismiss()
                    syncReport()
                } catch (e: Exception) {
                    info.text = e.message ?: "Não foi possível salvar o token."
                }
            }
        }
        dialog.show()
    }

    private fun syncReport() {
        if (!syncer.hasToken()) { message = "Toque em ⚙ e configure o token do GitHub"; invalidate(); return }
        syncing = true; message = "Enviando relatório ao GitHub…"; invalidate()
        syncer.sync(exportReport()) { _, result ->
            post {
                syncing = false
                message = result
                invalidate()
            }
        }
    }

    private fun saveStatus(status: String, screenshot: Bitmap? = null, reviewedId: Int = targetId,
                           reviewedX: Int = targetX, reviewedY: Int = targetY, reviewedZ: Int = targetZ,
                           reasons: List<String> = emptyList(), note: String = "") {
        val id = reviewedId
        val data = JSONObject()
        data.put("id", id)
        data.put("name", B.names.getOrNull(id).orEmpty())
        data.put("status", status)
        data.put("x", reviewedX); data.put("y", reviewedY); data.put("z", reviewedZ)
        if (status == "invalid") {
            data.put("reasons", org.json.JSONArray(reasons))
            if (note.isNotEmpty()) data.put("note", note)
        }
        data.put("updatedAt", SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date()))
        fun saveAndSync() {
            val all = JSONObject(prefs.getString("report", "{}") ?: "{}")
            // relatório antigo do bloco (foto, motivos, observação) é substituído; a foto antiga some do GitHub
            val oldUrl = all.optJSONObject(id.toString())?.optString("screenshotUrl", "").orEmpty()
            val newUrl = data.optString("screenshotUrl", "")
            all.put(id.toString(), data)
            prefs.edit().putString("status_$id", status).putString("report", all.toString(2)).apply()
            if (status == "invalid" && screenshot == null) message = "Bloco #$id invalidado; captura indisponível"
            else message = "Bloco #$id marcado; enviando relatório…"
            syncReport()
            if (oldUrl.isNotEmpty() && oldUrl != newUrl && (status == "valid" || newUrl.isNotEmpty())) syncer.deleteScreenshot(oldUrl)
        }
        if (screenshot != null && status == "invalid") {
            message = "Enviando foto do bloco #$id…"; syncing = true; invalidate()
            // Embute uma cópia reduzida da foto no próprio JSON (assim ela vem junto no zip/relatório).
            try {
                val w = 640; val h = (screenshot.height * w / screenshot.width.coerceAtLeast(1)).coerceAtLeast(1)
                val small = Bitmap.createScaledBitmap(screenshot, w, h, true)
                val bos = java.io.ByteArrayOutputStream()
                small.compress(Bitmap.CompressFormat.JPEG, 70, bos)
                if (small !== screenshot) small.recycle()
                data.put("screenshotBase64", android.util.Base64.encodeToString(bos.toByteArray(), android.util.Base64.NO_WRAP))
            } catch (_: Exception) { }
            syncer.uploadScreenshot(screenshot, id) { ok, result ->
                post {
                    syncing = false
                    if (ok) data.put("screenshotUrl", result)
                    else message = "Foto não enviada: $result"
                    saveAndSync()
                }
            }
        } else saveAndSync()
    }

    fun exportReport(): String = prefs.getString("report", "{}") ?: "{}"
}
