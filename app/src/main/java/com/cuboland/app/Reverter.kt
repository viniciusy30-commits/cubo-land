package com.cuboland.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Reverte a última "Entrega automática" do main no GitHub (a ação roda em .github/workflows/revert.yml).
 * Antes de reverter mostra exatamente o que vai voltar. Pode ser repetido: cada vez reverte a entrega anterior ainda não revertida.
 */
object Reverter {
    private const val WF = "revert.yml"
    private const val KEY = "gh_token"
    private const val API = "https://api.github.com/repos/"

    class Change(val status: String, val name: String)
    class Target(val sha: String, val date: String, val titles: List<String>, val items: List<String>, val files: List<Change>, val truncated: Boolean)

    private fun toast(a: Activity, s: String) = Toast.makeText(a, s, Toast.LENGTH_LONG).show()

    private fun http(url: String, token: String? = null, body: String? = null): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 12000; c.readTimeout = 15000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            c.requestMethod = "POST"; c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = c.responseCode
        val txt = try { (if (code < 400) c.inputStream else c.errorStream)?.bufferedReader()?.readText() ?: "" } catch (_: Exception) { "" }
        return Pair(code, txt)
    }

    fun start(a: Activity) {
        toast(a, "Buscando o que seria revertido…")
        Thread {
            try {
                val t = find()
                a.runOnUiThread {
                    if (a.isFinishing) return@runOnUiThread
                    if (t == null) toast(a, "Não achei nenhuma entrega para reverter.") else confirm(a, t)
                }
            } catch (e: Exception) {
                a.runOnUiThread { toast(a, "Sem internet ou GitHub indisponível.") }
            }
        }.start()
    }

    private fun find(): Target? {
        val r = http(API + Updater.REPO + "/commits?sha=main&per_page=100")
        if (r.first != 200) throw java.io.IOException("http ${r.first}")
        val arr = JSONArray(r.second)
        val reverted = HashSet<String>()
        for (i in 0 until arr.length()) {
            val m = arr.getJSONObject(i).getJSONObject("commit").getString("message")
            if (m.startsWith("Reverter: ") && m.length >= 17) reverted.add(m.substring(10, 17))
        }
        var sha: String? = null; var date = ""
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val cm = o.getJSONObject("commit")
            if (cm.getString("message").startsWith("Entrega autom") && !reverted.contains(o.getString("sha").substring(0, 7))) {
                sha = o.getString("sha"); date = cm.getJSONObject("committer").getString("date"); break
            }
        }
        if (sha == null) return null
        val d = http(API + Updater.REPO + "/commits/" + sha)
        if (d.first != 200) throw java.io.IOException("http ${d.first}")
        val j = JSONObject(d.second)
        val files = j.optJSONArray("files") ?: JSONArray()
        val ch = ArrayList<Change>(); val titles = ArrayList<String>(); val items = ArrayList<String>()
        for (i in 0 until files.length()) {
            val f = files.getJSONObject(i)
            val name = f.getString("filename")
            if (name.startsWith("_incoming/") || name.startsWith(".github/")) continue
            ch.add(Change(f.getString("status"), name))
            if (name.endsWith("assets/novidades.txt")) {
                for (l in f.optString("patch", "").lines()) {
                    if (l.startsWith("+#titulo ")) titles.add(l.substring(9).trim())
                    else if (l.startsWith("+- ")) items.add(l.substring(3).trim())
                }
            }
        }
        val pretty = try {
            val inF = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US); inF.timeZone = TimeZone.getTimeZone("UTC")
            SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale("pt", "BR")).format(inF.parse(date)!!)
        } catch (_: Exception) { date }
        return Target(sha, pretty, titles, items, ch, files.length() >= 300)
    }

    private fun short(n: String) = n.removePrefix("app/src/main/java/com/cuboland/app/").removePrefix("app/src/main/")

    private fun confirm(a: Activity, t: Target) {
        val added = t.files.filter { it.status == "added" }
        val removed = t.files.filter { it.status == "removed" }
        val changed = t.files.filter { it.status != "added" && it.status != "removed" }
        a.cozyDialog(440) { p, close ->
            p.addView(a.cTxt("Reverter atualização?", 20f, Cz.GOLD))
            p.addView(a.cTxt("Isso volta o main do GitHub para o estado de antes da entrega de ${t.date} (código ${t.sha.substring(0, 7)}).", 13f, Cz.CREAM, false).apply { setPadding(0, a.dp(8), 0, a.dp(8)) })
            if (t.titles.isNotEmpty()) {
                p.addView(a.cTxt("Atualização que sai:", 14f, Cz.GOLD))
                p.addView(a.cTxt(t.titles.joinToString("\n") { "•  $it" }, 12.5f, Cz.CREAM, false).apply { setPadding(0, a.dp(2), 0, a.dp(6)) })
            }
            if (t.items.isNotEmpty()) {
                p.addView(a.cTxt("O que ela trouxe:", 14f, Cz.GOLD))
                val shown = t.items.take(8).joinToString("\n") { "•  " + (if (it.length > 110) it.take(107) + "…" else it) }
                val more = if (t.items.size > 8) "\n…e mais ${t.items.size - 8} itens" else ""
                p.addView(a.cTxt(shown + more, 12f, Cz.CREAM, false).apply { setPadding(0, a.dp(2), 0, a.dp(6)) })
            }
            p.addView(a.cTxt("Arquivos que mudam:", 14f, Cz.GOLD))
            p.addView(a.cTxt("${changed.size} voltam à versão anterior, ${added.size} criados nessa entrega serão apagados, ${removed.size} apagados voltam" + (if (t.truncated) " (lista cortada pelo GitHub)" else ""), 12f, Cz.CREAM, false).apply { setPadding(0, a.dp(2), 0, a.dp(4)) })
            val lines = ArrayList<String>()
            for (f in changed) lines.add("~  ${short(f.name)}")
            for (f in added) lines.add("-  ${short(f.name)}  (será apagado)")
            for (f in removed) lines.add("+  ${short(f.name)}  (volta)")
            val cap = 16
            val txt = lines.take(cap).joinToString("\n") + (if (lines.size > cap) "\n…e mais ${lines.size - cap} arquivos" else "")
            p.addView(a.cTxt(txt, 11.5f, Cz.CREAM, false).apply { setPadding(0, 0, 0, a.dp(8)) })
            p.addView(a.cTxt("Só mexe nos arquivos do jogo. Depois o GitHub gera um APK novo já revertido, que aparece em Verificar atualização. Dá pra repetir: cada vez volta a entrega anterior.", 11.5f, Cz.GOLD, false).apply { setPadding(0, 0, 0, a.dp(12)) })
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(a.cBtn("Cancelar", Cz.STONE, 15f) { close() }, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = a.dp(6) })
            row.addView(a.cBtn("Reverter", Cz.RED, 15f) { close(); run(a) }, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = a.dp(6) })
            p.addView(row)
        }
    }

    private fun run(a: Activity) {
        val tok = a.getSharedPreferences("updater", 0).getString(KEY, null)
        if (tok.isNullOrBlank()) askToken(a) else dispatch(a, tok)
    }

    private fun dispatch(a: Activity, tok: String) {
        toast(a, "Enviando pedido ao GitHub…")
        Thread {
            try {
                val r = http(API + Updater.REPO + "/actions/workflows/$WF/dispatches", tok, "{\"ref\":\"main\"}")
                a.runOnUiThread {
                    when (r.first) {
                        204 -> done(a)
                        401, 403 -> { a.getSharedPreferences("updater", 0).edit().remove(KEY).apply(); toast(a, "Token recusado ou sem permissão de Actions. Cole um token novo."); askToken(a) }
                        else -> toast(a, "O GitHub respondeu ${r.first}. O arquivo revert.yml já está no main?")
                    }
                }
            } catch (e: Exception) {
                a.runOnUiThread { toast(a, "Sem internet ou GitHub indisponível.") }
            }
        }.start()
    }

    private fun done(a: Activity) {
        a.cozyDialog(380) { p, close ->
            p.addView(a.cTxt("Pedido enviado!", 20f, Cz.GOLD))
            p.addView(a.cTxt("O GitHub leva cerca de 1 minuto para voltar o main. Depois confira a build na aba Actions do repositório; quando terminar, toque em Verificar atualização para instalar o APK revertido.", 13f, Cz.CREAM, false).apply { setPadding(0, a.dp(8), 0, a.dp(14)) })
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(a.cBtn("Ver Actions", Cz.SKY, 15f) { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${Updater.REPO}/actions"))) }, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = a.dp(6) })
            row.addView(a.cBtn("Fechar", Cz.GREEN, 15f) { close() }, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = a.dp(6) })
            p.addView(row)
        }
    }

    private fun askToken(a: Activity) {
        a.cozyDialog(430) { p, close ->
            p.addView(a.cTxt("Autorizar o GitHub", 20f, Cz.GOLD))
            p.addView(a.cTxt("Pra reverter com um toque, o app precisa de um token do GitHub (só uma vez). Crie um token fine-grained só deste repositório com a permissão Actions: Read and write, cole aqui e ele fica guardado só neste aparelho.\n\nSem token: toque em Abrir no GitHub e, na página, em Run workflow.", 12.5f, Cz.CREAM, false).apply { setPadding(0, a.dp(8), 0, a.dp(10)) })
            val et = EditText(a).apply {
                hint = "cole o token (github_pat_…)"; setSingleLine(true); textSize = 13f
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setTextColor(Cz.INK); setHintTextColor(Cz.TXT_SOFT); setBackgroundColor(Cz.PANEL3); setPadding(a.dp(10), a.dp(8), a.dp(10), a.dp(8))
            }
            p.addView(et, lpW(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = a.dp(10) })
            val r1 = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            r1.addView(a.cBtn("Criar token", Cz.SKY, 14f) { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/personal-access-tokens/new"))) }, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = a.dp(6) })
            r1.addView(a.cBtn("Abrir no GitHub", Cz.TEAL, 14f) { a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${Updater.REPO}/actions/workflows/$WF"))) }, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = a.dp(6) })
            p.addView(r1, lpW(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = a.dp(8) })
            val r2 = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            r2.addView(a.cBtn("Cancelar", Cz.STONE, 15f) { close() }, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = a.dp(6) })
            r2.addView(a.cBtn("Salvar e reverter", Cz.RED, 15f) {
                val t = et.text.toString().trim()
                if (t.length < 20) toast(a, "Esse token parece curto demais.")
                else { a.getSharedPreferences("updater", 0).edit().putString(KEY, t).apply(); close(); dispatch(a, t) }
            }, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = a.dp(6) })
            p.addView(r2)
        }
    }
}
