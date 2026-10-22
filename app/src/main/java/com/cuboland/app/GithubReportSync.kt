package com.cuboland.app

import android.content.Context
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import java.util.concurrent.Executors
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Sends block-review JSON directly to GitHub Contents API.
 * The fine-grained token is encrypted by Android Keystore and never stored in source code.
 */
class GithubReportSync(private val context: Context) {
    companion object {
        private const val PREFS = "github_report_secure"
        private const val KEY_ALIAS = "cuboland_github_token_key"
        private const val REPO = "viniciusy30-commits/cubo-land"
        private const val PATH = "reports/block-review.json"
        private const val API = "https://api.github.com"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val executor = Executors.newSingleThreadExecutor()

    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true).build())
        return generator.generateKey()
    }

    fun hasToken(): Boolean = prefs.contains("token_cipher")

    fun saveToken(token: String) {
        require(token.startsWith("github_pat_") || token.startsWith("ghp_")) {
            "Cole um token pessoal do GitHub válido (recomendado: Fine-grained)."
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val packed = cipher.iv + cipher.doFinal(token.toByteArray(StandardCharsets.UTF_8))
        prefs.edit().putString("token_cipher", Base64.getEncoder().encodeToString(packed)).apply()
    }

    private fun readToken(): String {
        val packed = Base64.getDecoder().decode(prefs.getString("token_cipher", null)
            ?: throw IllegalStateException("Configure o token do GitHub primeiro."))
        require(packed.size > 12) { "Token salvo inválido. Configure novamente." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        return String(cipher.doFinal(packed.copyOfRange(12, packed.size)), StandardCharsets.UTF_8)
    }

    fun forgetToken() { prefs.edit().remove("token_cipher").apply() }

    fun sync(reportJson: String, callback: (Boolean, String) -> Unit) {
        executor.execute {
            try {
                val token = readToken()
                val path = PATH.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }
                val get = request("GET", "$API/repos/$REPO/contents/$path", token, null)
                val sha = if (get.first == 200) JSONObject(get.second).optString("sha").takeIf { it.isNotBlank() } else null
                if (get.first != 200 && get.first != 404) {
                    throw IllegalStateException("Não foi possível consultar o relatório (HTTP ${get.first}). Confira o token e as permissões.")
                }
                val body = JSONObject().put("message", "Atualizar relatório de avaliação de blocos")
                    .put("content", Base64.getEncoder().encodeToString(reportJson.toByteArray(StandardCharsets.UTF_8)))
                    .put("branch", "main")
                if (sha != null) body.put("sha", sha)
                val put = request("PUT", "$API/repos/$REPO/contents/$path", token, body.toString())
                if (put.first !in 200..201) {
                    val detail = try { JSONObject(put.second).optString("message") } catch (_: Exception) { "" }
                    throw IllegalStateException("GitHub recusou o envio (HTTP ${put.first}) ${detail.take(120)}. Verifique Contents: Read and write.")
                }
                // Publica também uma página Markdown com as capturas incorporadas.
                try { syncMarkdownReport(reportJson, token) } catch (_: Exception) { /* JSON continua sendo a fonte principal */ }
                callback(true, "Relatório e imagens sincronizados com o GitHub.")
            } catch (e: Exception) {
                callback(false, e.message ?: "Falha ao sincronizar.")
            }
        }
    }

    /** Cria/atualiza um relatório Markdown com as fotos incorporadas. */
    private fun syncMarkdownReport(reportJson: String, token: String) {
        val all = JSONObject(reportJson)
        val markdown = StringBuilder("# Relatório de avaliação de blocos\n\n")
        markdown.append("Este relatório é atualizado automaticamente pelo CuboLand.\n\n")
        val entries = mutableListOf<JSONObject>()
        val keys = all.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            all.optJSONObject(key)?.let { entries.add(it) }
        }
        entries.sortByDescending { it.optString("updatedAt") }
        for (item in entries) {
            val id = item.optInt("id")
            val name = item.optString("name", "Bloco #$id")
            val status = if (item.optString("status") == "invalid") "❌ Inválido" else "✅ Válido"
            markdown.append("## ").append(name).append(" (#").append(id).append(")\n\n")
            markdown.append("- **Status:** ").append(status).append("\n")
            markdown.append("- **Coordenadas:** X ").append(item.optInt("x"))
                .append(", Y ").append(item.optInt("y")).append(", Z ").append(item.optInt("z")).append("\n")
            markdown.append("- **Atualizado:** ").append(item.optString("updatedAt", "—")).append("\n\n")
            val imageUrl = item.optString("screenshotUrl", "")
            if (imageUrl.isNotBlank()) {
                val rawUrl = imageUrl
                    .replace("https://github.com/$REPO/blob/main/", "https://raw.githubusercontent.com/$REPO/main/")
                markdown.append("![Captura do bloco #").append(id).append("](").append(rawUrl).append(")\n\n")
                markdown.append("[Abrir imagem em tamanho original](").append(rawUrl).append(")\n\n")
            }
            markdown.append("---\n\n")
        }
        val path = "reports/README.md"
        val encodedPath = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }
        val get = request("GET", "$API/repos/$REPO/contents/$encodedPath", token, null)
        val sha = if (get.first == 200) JSONObject(get.second).optString("sha").takeIf { it.isNotBlank() } else null
        if (get.first != 200 && get.first != 404) throw IllegalStateException("Não foi possível consultar a página de imagens.")
        val body = JSONObject().put("message", "Atualizar relatório visual dos blocos")
            .put("content", Base64.getEncoder().encodeToString(markdown.toString().toByteArray(StandardCharsets.UTF_8)))
            .put("branch", "main")
        if (sha != null) body.put("sha", sha)
        val put = request("PUT", "$API/repos/$REPO/contents/$encodedPath", token, body.toString())
        if (put.first !in 200..201) throw IllegalStateException("Não foi possível atualizar a página visual do relatório (HTTP ${put.first}).")
    }

    /** Upload an in-game screenshot and return its GitHub URL. */
    fun uploadScreenshot(bitmap: Bitmap, blockId: Int, callback: (Boolean, String) -> Unit) {
        executor.execute {
            try {
                val token = readToken()
                val bytes = ByteArrayOutputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 82, out); out.toByteArray() }
                val path = "reports/block-images/block-" + blockId + "-" + System.currentTimeMillis() + ".jpg"
                val encodedPath = path.split("/").joinToString("/") { java.net.URLEncoder.encode(it, "UTF-8") }
                val body = JSONObject().put("message", "Adicionar captura do bloco #" + blockId)
                    .put("content", Base64.getEncoder().encodeToString(bytes)).put("branch", "main")
                val put = request("PUT", "$API/repos/$REPO/contents/$encodedPath", token, body.toString())
                if (put.first !in 200..201) {
                    val detail = try { JSONObject(put.second).optString("message") } catch (_: Exception) { "" }
                    throw IllegalStateException("GitHub recusou a foto (HTTP " + put.first + ") " + detail.take(100))
                }
                callback(true, "https://github.com/$REPO/blob/main/$path")
            } catch (e: Exception) { callback(false, e.message ?: "Falha ao enviar a foto.") }
            finally { if (!bitmap.isRecycled) bitmap.recycle() }
        }
    }
    private fun request(method: String, url: String, token: String, body: String?): Pair<Int, String> {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 12000
            connection.readTimeout = 12000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("User-Agent", "CuboLand-BlockReview")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val response = stream?.use { BufferedReader(InputStreamReader(it, StandardCharsets.UTF_8)).readText() } ?: ""
            return status to response
        } finally { connection.disconnect() }
    }
}
