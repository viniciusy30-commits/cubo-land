package com.cuboland.app

import android.util.Base64
import java.util.zip.Inflater

/**
 * Texturas da Construção copiadas do pack Actions & Stuff (iguais às do Minecraft): grade 16x16 de pixels duros, sem relevo 3D.
 * Os dados ficam em McTexData (nome : RGBA deflate/base64). Cada pixel vira um bloco de 4x4 no tile de 64 do atlas.
 * tint >= 0 recolore a textura com aquela cor (mantém a luz e a sombra dos pixels) pras famílias de cores que não existem no Minecraft.
 */
object McTex {
    /** todas as texturas: as da Construção (McTexData) + as do resto do pack (McTexData2..9) */
    private val ALL: Array<String> = McTexData.D + McTexData2.D + McTexData3.D + McTexData4.D + McTexData5.D + McTexData6.D + McTexData7.D + McTexData8.D + McTexData9.D
    private val names = HashMap<String, Int>().also { m ->
        for ((i, e) in ALL.withIndex()) m[e.substring(0, e.indexOf(':'))] = i
        // Alguns nomes foram gravados com erros no pacote importado; aliases preservam os dados
        // originais, mas permitem que os blocos usem os nomes corretos.
        fun alias(correct: String, stored: String) { m[stored]?.let { m[correct] = it } }
        alias("stripped_crimson_stem_top", "stred_crimson_stem_top")
        alias("stripped_crimson_stem_side", "strd_crimson_stem_side")
        alias("stripped_warped_stem_top", "strped_warped_stem_top")
        alias("stripped_warped_stem_side", "stred_warped_stem_side")
    }
    private val missingIndex = ALL.size
    private val cache = arrayOfNulls<IntArray>(ALL.size + 1)
    private val missingNames = HashSet<String>()

    /** itens 16x16 do pack (ferramentas): ARGB ou null se não existir */
    fun itemPix(name: String): IntArray? {
        for (e in McItemData.D) {
            if (!e.startsWith("$name:")) continue
            val z = Base64.decode(e.substring(e.indexOf(':') + 1), Base64.DEFAULT)
            val inf = Inflater(); inf.setInput(z)
            val raw = ByteArray(16 * 16 * 4); var n = 0
            while (n < raw.size && !inf.finished()) { val r = inf.inflate(raw, n, raw.size - n); if (r <= 0) break; n += r }
            inf.end()
            val out = IntArray(256)
            for (p in 0 until 256) {
                val r = raw[p * 4].toInt() and 255; val g = raw[p * 4 + 1].toInt() and 255; val b = raw[p * 4 + 2].toInt() and 255; val a = raw[p * 4 + 3].toInt() and 255
                out[p] = (a shl 24) or (r shl 16) or (g shl 8) or b
            }
            return out
        }
        return null
    }
    private val tinted = HashMap<Long, IntArray>()

    /** nome do arquivo da textura i */
    fun nameOf(i: Int): String = when (i) {
        missingIndex -> "TEXTURA_AUSENTE"
        else -> if (i in ALL.indices) ALL[i].substring(0, ALL[i].indexOf(':')) else ""
    }

    /** Índice da textura. Ausências viram um padrão de diagnóstico, nunca pedra silenciosamente. */
    fun id(name: String): Int {
        names[name]?.let { return it }
        if (missingNames.add(name)) android.util.Log.e("CuboLand-TextureAudit", "Textura não encontrada: $name")
        return missingIndex
    }

    private fun missingPixels(): IntArray = IntArray(256) { p ->
        val x = p and 15; val y = p shr 4
        if (((x / 4) + (y / 4)) % 2 == 0) 0xFFFF00FF.toInt() else 0xFF202020.toInt()
    }

    /** 256 pixels ARGB (linha por linha) da textura original */
    fun pix(i: Int): IntArray {
        val k = if (i in cache.indices) i else missingIndex
        cache[k]?.let { return it }
        if (k == missingIndex) {
            val out = missingPixels()
            cache[k] = out
            return out
        }
        val e = ALL[k]
        val out = try {
            val z = Base64.decode(e.substring(e.indexOf(':') + 1), Base64.DEFAULT)
            val inf = Inflater()
            inf.setInput(z)
            val raw = ByteArray(16 * 16 * 4)
            var n = 0
            try {
                while (n < raw.size && !inf.finished()) {
                    val r = inf.inflate(raw, n, raw.size - n)
                    if (r <= 0) break
                    n += r
                }
                if (n != raw.size || !inf.finished()) {
                    android.util.Log.e("CuboLand-TextureAudit", "Dados incompletos para ${nameOf(k)}: $n/${raw.size} bytes")
                    missingPixels()
                } else {
                    IntArray(256) { p ->
                        val r = raw[p * 4].toInt() and 255; val g = raw[p * 4 + 1].toInt() and 255
                        val b = raw[p * 4 + 2].toInt() and 255; val a = raw[p * 4 + 3].toInt() and 255
                        (a shl 24) or (r shl 16) or (g shl 8) or b
                    }
                }
            } finally { inf.end() }
        } catch (e: Exception) {
            android.util.Log.e("CuboLand-TextureAudit", "Falha ao decodificar ${nameOf(k)}", e)
            missingPixels()
        }
        cache[k] = out
        return out
    }

    private fun lum(c: Int): Float = (((c shr 16) and 255) * 0.299f + ((c shr 8) and 255) * 0.587f + (c and 255) * 0.114f)

    /** a textura recolorida: a luz de cada pixel (relativa à média) multiplica a cor pedida */
    private fun tint(i: Int, col: Int): IntArray {
        val key = (i.toLong() shl 32) or (col.toLong() and 0xFFFFFFL)
        tinted[key]?.let { return it }
        val src = pix(i)
        var sum = 0f; var n = 0
        for (c in src) if ((c ushr 24) > 0) { sum += lum(c); n++ }
        val avg = if (n > 0 && sum > 0f) sum / n else 128f
        val tr = (col shr 16) and 255; val tg = (col shr 8) and 255; val tb = col and 255
        val out = IntArray(256)
        for (p in 0 until 256) {
            val c = src[p]; val a = c ushr 24
            if (a == 0) { out[p] = c; continue }
            val k = (lum(c) / avg).coerceIn(0.35f, 1.7f)
            var r = tr * k; var g = tg * k; var b = tb * k
            // passou de 255: o excesso clareia em direção ao branco em vez de cortar a cor
            val over = maxOf(r, g, b) - 255f
            if (over > 0f) { val t = (over / 255f).coerceAtMost(0.6f); r = r + (255f - r) * t; g = g + (255f - g) * t; b = b + (255f - b) * t }
            out[p] = (a shl 24) or (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255)
        }
        tinted[key] = out
        return out
    }

    /** vidro liso: cor única (média do miolo da textura, sem a moldura de 1 pixel) pra blocos de vidro vizinhos emendarem sem divisa nenhuma.
     *  Vidro comum tem o miolo quase transparente: vira uma película bem clara; os coloridos e o fumê mantêm a própria cor. */
    fun flatGlass(i: Int, tint: Int): Int {
        val a = if (tint >= 0) tint(i, tint and 0xFFFFFF) else pix(i)
        var sr = 0; var sg = 0; var sb = 0; var sa = 0; var n = 0
        for (y in 3..12) for (x in 3..12) {
            val c = a[(y shl 4) + x]; val al = c ushr 24
            sa += al
            if (al > 0) { sr += (c shr 16) and 255; sg += (c shr 8) and 255; sb += c and 255; n++ }
        }
        val avgA = sa / 100
        if (n == 0 || avgA < 30) return (54 shl 24) or 0xD8F2FF
        return (avgA.coerceIn(60, 200) shl 24) or ((sr / n) shl 16) or ((sg / n) shl 8) or (sb / n)
    }

    /** pixel (x, y em 0..15) da textura i, recolorida se tint >= 0 */
    fun sample(i: Int, tint: Int, x: Int, y: Int): Int {
        val a = if (tint >= 0) tint(i, tint and 0xFFFFFF) else pix(i)
        return a[((y and 15) shl 4) + (x and 15)]
    }
}
