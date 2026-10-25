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
    private var busy = false                       // captura/diálogo de invalidação em andamento: ignora novas batidas
    private var armed: String? = null              // "valid" / "invalid" / null: ação feita ao bater num bloco com a mão vazia
    private var room: World.RoomRegion? = null     // sala (da galeria) onde a mira está
    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density
    init { game.onHandHit = { x, y, z -> post { handHit(x, y, z) } } }

    override fun onDetachedFromWindow() {
        if (game.onHandHit != null) game.onHandHit = null
        super.onDetachedFromWindow()
    }

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

        if (game.hasHit) room = game.world.roomAt(game.hx, game.hz)

        // botão pra esvaziar a barra de itens (todos os slots ficam só com a mão)
        val clear = RectF(w - dp(98f), dp(54f), w - dp(10f), dp(54f) + dp(28f))
        addButton(c, clear, "Esvaziar barra", Color.rgb(120, 80, 40), 9f)

        var rowBottom = top + buttonH
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
            rowBottom = top - dp(24f)
        }
        val half = (totalW - gap) / 2f
        // sala inteira: aparece quando a mira está dentro de uma sala da galeria
        val r = room
        if (r != null) {
            val rTop = rowBottom - gap - buttonH
            addButton(c, RectF(left, rTop, left + half, rTop + buttonH), "✓ Sala toda", Color.rgb(35, 135, 79))
            addButton(c, RectF(left + half + gap, rTop, left + totalW, rTop + buttonH), "✕ Sala toda", Color.rgb(178, 55, 61))
            rowBottom = rTop
        }
        // modo automático: com ele ligado, bater num bloco com a mão vazia aplica a ação marcada
        val aTop = rowBottom - gap - buttonH
        val onV = armed == "valid"; val onI = armed == "invalid"
        addButton(c, RectF(left, aTop, left + half, aTop + buttonH), if (onV) "✓ Auto: LIGADO" else "✓ Auto: off",
            if (onV) Color.rgb(35, 160, 90) else Color.argb(220, 45, 60, 55), 11f, onV, "Auto ✓")
        addButton(c, RectF(left + half + gap, aTop, left + totalW, aTop + buttonH), if (onI) "✕ Auto: LIGADO" else "✕ Auto: off",
            if (onI) Color.rgb(205, 60, 66) else Color.argb(220, 70, 48, 50), 11f, onI, "Auto ✕")

        // Aviso discreto no topo, sem cobrir a hotbar.
        val hint = when {
            armed != null && game.cur() != 0 -> "Auto ligado: escolha um slot vazio (mão) e bata no bloco"
            armed == "valid" -> "Auto ✓ ligado: bata no bloco com a mão vazia"
            armed == "invalid" -> "Auto ✕ ligado: bata no bloco com a mão vazia"
            else -> ""
        }
        val bannerText = if (syncing) "Sincronizando com GitHub…" else if (message.isNotBlank() && message != "Mire em um bloco para avaliar") message else hint
        if (bannerText.isNotBlank()) {
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

    private fun addButton(c: Canvas, r: RectF, text: String, color: Int, size: Float = 11f, outlined: Boolean = false, id: String = text) {
        paint.color = color; c.drawRoundRect(r, dp(7f), dp(7f), paint)
        if (outlined) {
            paint.style = Paint.Style.STROKE; paint.strokeWidth = dp(2f); paint.color = Color.WHITE
            c.drawRoundRect(r, dp(7f), dp(7f), paint); paint.style = Paint.Style.FILL
        }
        paint.color = Color.WHITE; paint.textSize = dp(size); paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        paint.textAlign = Paint.Align.CENTER; c.drawText(text, r.centerX(), r.centerY() + dp(4f), paint)
        paint.textAlign = Paint.Align.LEFT
        buttons.add(r to id)
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
                    "✕ Inválido" -> if (targetId > 0 && game.hasHit && !busy) startInvalid(targetId, targetX, targetY, targetZ)
                    "Auto ✓" -> toggleArmed("valid")
                    "Auto ✕" -> toggleArmed("invalid")
                    "✓ Sala toda" -> room?.let { confirmBulk(it, "valid") }
                    "✕ Sala toda" -> room?.let { confirmBulk(it, "invalid") }
                    "Esvaziar barra" -> {
                        java.util.Arrays.fill(game.hotbar, 0)
                        message = "Barra de itens esvaziada"
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

    private fun toggleArmed(mode: String) {
        armed = if (armed == mode) null else mode
        message = when (armed) {
            "valid" -> "Auto ✓ ligado: bata nos blocos com a mão vazia"
            "invalid" -> "Auto ✕ ligado: bata nos blocos com a mão vazia"
            else -> "Modo automático desligado"
        }
    }

    /** batida de mão vazia num bloco: aplica a ação do botão automático que estiver ligado */
    private fun handHit(x: Int, y: Int, z: Int) {
        val mode = armed ?: return
        if (busy) return
        val id = game.world.get(x, y, z)
        if (id <= 0) return
        val r = game.world.roomAt(x, z)
        if (r != null && (y != r.y || !r.ids.contains(id))) { message = "Bata num bloco exposto da sala (não no piso)"; invalidate(); return }
        if (mode == "valid") saveStatus("valid", null, id, x, y, z) else startInvalid(id, x, y, z)
        invalidate()
    }

    /** captura a foto do bloco e pergunta os motivos antes de invalidar */
    private fun startInvalid(id: Int, x: Int, y: Int, z: Int) {
        if (busy) return
        busy = true
        message = "Capturando bloco #$id…"; syncing = true; invalidate()
        captureScreenshot { bitmap ->
            post {
                syncing = false; message = ""; invalidate()
                askReasons(id, { reasons, note -> busy = false; saveStatus("invalid", bitmap, id, x, y, z, reasons, note) },
                    { busy = false; if (bitmap != null && !bitmap.isRecycled) bitmap.recycle() })
            }
        }
    }

    /** confirma e marca a sala inteira. Invalidar não mexe nos blocos que já estão inválidos (mantém motivos e foto). */
    private fun confirmBulk(r: World.RoomRegion, status: String) {
        if (busy) return
        val change = r.ids.count { prefs.getString("status_$it", null) != status }
        if (change == 0) { message = "A sala já está toda assim"; return }
        val word = if (status == "valid") "VÁLIDOS" else "INVÁLIDOS"
        busy = true
        AlertDialog.Builder(context)
            .setTitle("Marcar sala inteira")
            .setMessage("${r.title}\n\n$change de ${r.ids.size} blocos serão marcados como $word." +
                (if (status == "invalid") "\nSem foto nem motivo; depois é só ajustar os que forem diferentes." else "\nDepois é só invalidar os que tiverem problema."))
            .setNegativeButton("Cancelar") { _, _ -> busy = false }
            .setPositiveButton("Marcar") { _, _ -> busy = false; bulkMark(r, status) }
            .setOnCancelListener { busy = false }
            .show()
    }

    private fun bulkMark(r: World.RoomRegion, status: String) {
        val all = JSONObject(prefs.getString("report", "{}") ?: "{}")
        val ed = prefs.edit()
        val stamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US).format(Date())
        val oldUrls = ArrayList<String>()
        var changed = 0
        for (j in r.ids.indices) {
            val id = r.ids[j]
            if (prefs.getString("status_$id", null) == status) continue
            val oldUrl = all.optJSONObject(id.toString())?.optString("screenshotUrl", "").orEmpty()
            if (oldUrl.isNotEmpty()) oldUrls.add(oldUrl)   // só existe se estava inválido com foto e agora virou válido
            val data = JSONObject()
            data.put("id", id); data.put("name", B.names.getOrNull(id).orEmpty()); data.put("status", status)
            data.put("x", r.xs[j]); data.put("y", r.y); data.put("z", r.zs[j])
            if (status == "invalid") data.put("reasons", org.json.JSONArray())
            data.put("updatedAt", stamp)
            all.put(id.toString(), data)
            ed.putString("status_$id", status)
            changed++
        }
        ed.putString("report", all.toString(2)).apply()
        message = "$changed blocos da sala marcados; enviando relatório…"
        syncReport()
        for (u in oldUrls) syncer.deleteScreenshot(u)
    }

    private val REASONS = listOf("Item duplicado / repetido (aparece 2, 3 ou 4 vezes)", "Peças flutuando", "Textura bugada", "Incompleto / faltando partes", "Textura piscando", "Cor errada",
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
