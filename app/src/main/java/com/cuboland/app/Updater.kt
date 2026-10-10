package com.cuboland.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

object Updater {
    const val REPO = "viniciusy30-commits/cubo-land"
    private const val APK = "CuboLand.apk"

    private fun toast(a: Activity, s: String) = Toast.makeText(a, s, Toast.LENGTH_SHORT).show()

    fun installed(a: Activity): Int =
        PackageInfoCompat.getLongVersionCode(a.packageManager.getPackageInfo(a.packageName, 0)).toInt()

    fun autoCheck(a: Activity) {
        val sp = a.getSharedPreferences("updater", 0)
        val now = System.currentTimeMillis()
        if (now - sp.getLong("last", 0L) < 6 * 3600 * 1000L) return
        sp.edit().putLong("last", now).apply()
        check(a, false)
    }

    fun check(a: Activity, manual: Boolean) {
        if (manual) toast(a, "Procurando atualização…")
        Thread {
            try {
                val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
                c.connectTimeout = 10000; c.readTimeout = 10000
                c.setRequestProperty("Accept", "application/vnd.github+json")
                if (c.responseCode != 200) throw java.io.IOException("http ${c.responseCode}")
                val j = JSONObject(c.inputStream.bufferedReader().readText())
                val remote = j.getString("tag_name").removePrefix("v").toIntOrNull() ?: 0
                var url: String? = null
                val assets = j.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val o = assets.getJSONObject(i)
                    if (o.getString("name").endsWith(".apk")) { url = o.getString("browser_download_url"); break }
                }
                val cur = installed(a)
                a.runOnUiThread {
                    if (a.isFinishing) return@runOnUiThread
                    if (remote > cur && url != null) offer(a, remote, cur, url)
                    else if (manual) toast(a, "Você já está na versão mais nova 🎉")
                }
            } catch (e: Exception) {
                if (manual) a.runOnUiThread { toast(a, "Sem internet ou servidor indisponível") }
            }
        }.start()
    }

    private fun offer(a: Activity, remote: Int, cur: Int, url: String) {
        a.cozyDialog(400) { p, close ->
            p.addView(a.cTxt("Nova versão disponível", 20f, Cz.GOLD))
            p.addView(a.cTxt("A versão $remote está pronta (você tem a $cur). Quer atualizar agora?", 14f, Cz.CREAM, false).apply { setPadding(0, a.dp(8), 0, a.dp(14)) })
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(a.cBtn("Depois", Cz.STONE, 15f) { close() }, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = a.dp(6) })
            row.addView(a.cBtn("Atualizar", Cz.GREEN, 15f) {
                close()
                if (Build.VERSION.SDK_INT >= 26 && !a.packageManager.canRequestPackageInstalls()) askPermission(a)
                else download(a, url)
            }, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = a.dp(6) })
            p.addView(row)
        }
    }

    private fun askPermission(a: Activity) {
        a.cozyDialog(420) { p, close ->
            p.addView(a.cTxt("Permissão necessária", 20f, Cz.GOLD))
            p.addView(a.cTxt("Para instalar a atualização, o Android precisa liberar \"Instalar apps desconhecidos\" para o CuboLand. Toque em Abrir, ative a opção, volte e toque em Verificar atualização de novo.", 13f, Cz.CREAM, false).apply { setPadding(0, a.dp(8), 0, a.dp(14)) })
            val row = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(a.cBtn("Cancelar", Cz.STONE, 15f) { close() }, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = a.dp(6) })
            row.addView(a.cBtn("Abrir", Cz.GREEN, 15f) {
                close()
                a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}")))
            }, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = a.dp(6) })
            p.addView(row)
        }
    }

    private fun download(a: Activity, url: String) {
        var tv: TextView? = null
        var bar: PixBar? = null
        val dlg = a.cozyDialog(380) { p, _ ->
            p.addView(a.cTxt("Atualizando", 20f, Cz.GOLD))
            val t = a.cTxt("Baixando… 0%", 14f, Cz.CREAM, false).apply { setPadding(0, a.dp(8), 0, a.dp(10)) }
            val b = PixBar(a)
            p.addView(t)
            p.addView(b, lpW(ViewGroup.LayoutParams.MATCH_PARENT, a.dp(26)))
            tv = t
            bar = b
        }
        dlg.setCancelable(false)
        Thread {
            try {
                val dir = File(a.cacheDir, "updates"); dir.mkdirs()
                dir.listFiles()?.forEach { it.delete() }
                val f = File(dir, APK)
                val c = URL(url).openConnection() as HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 15000; c.instanceFollowRedirects = true
                val total = c.contentLength.toLong()
                c.inputStream.use { inp ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(32768); var done = 0L; var n = 0; var lastP = -1
                        while (inp.read(buf).also { n = it } > 0) {
                            out.write(buf, 0, n); done += n
                            val p = if (total > 0) (done * 100 / total).toInt() else 0
                            if (p != lastP) { lastP = p; a.runOnUiThread { bar?.set(p); tv?.text = "Baixando… $p%" } }
                        }
                    }
                }
                a.runOnUiThread { dlg.dismiss(); install(a, f) }
            } catch (e: Exception) {
                a.runOnUiThread { dlg.dismiss(); toast(a, "Falha no download. Tente de novo.") }
            }
        }.start()
    }

    private fun install(a: Activity, f: File) {
        val uri = FileProvider.getUriForFile(a, "${a.packageName}.fileprovider", f)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        a.startActivity(i)
    }
}
