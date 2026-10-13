package com.cuboland.app

import kotlin.math.sqrt

/** Mapa de relevo de cada tile do atlas: grade 8x8 (cada peça = 2x2 pixels da textura) com nível -2..+2.
 *  Mais claro que o resto da textura = sai pra fora (pepitas, pedras, nervuras); mais escuro = entra (frestas, rejunte, buracos).
 *  A borda de 1 peça fica sempre no nível 0 pra os blocos encostarem sem frestas. */
object Relief {
    /** grade do relevo automático (por brilho): peças de 2x2 pixels */
    const val G = 8
    const val STEP = 0.045f
    /** g = peças por lado (8 no automático, 16 nos mapas escritos à mão da NatRelief); lv = nível -2..+2 de cada peça */
    class Map(val g: Int, val lv: ByteArray)
    private val cache = arrayOfNulls<Map>(Atlas.NT * Atlas.ROWS)

    /** mapa escrito à mão desse tile (tipo K_PAL da NatArt ou padrão Pix de K_PIX), ou null se o bloco usa o automático */
    private fun hand(tile: Int): Map? {
        if (tile == 6 || tile == 7) {   // tronco do carvalho: mesmo relevo da casca / topo dos outros troncos
            val rows = NatRelief.pix.getOrNull(if (tile == 6) Pix.BARK else Pix.LOGTOP)
            if (rows != null && rows.size == 16) {
                val out = ByteArray(256)
                for (y in 0 until 16) for (x in 0 until 16) out[y * 16 + x] = (rows[y][x] - '0' - 2).coerceIn(-2, 2).toByte()
                return Map(16, out)
            }
        }
        BuildArt.baseTile(tile)?.let { return BuildArt.relief(it[0]) }
        val sp = Blk.specs.getOrNull(tile) ?: return null
        if (sp[0] == Blk.K_BUILD) return BuildArt.relief(sp[1], sp[6] and 255)
        if (sp[0] == Blk.K_PALV) return NatVar.relief(sp[1], sp[2])
        val rows: Array<String>? = when (sp[0]) {
            Blk.K_PAL -> NatRelief.art.getOrNull(sp[1])
            Blk.K_PIX -> NatRelief.pix.getOrNull(sp[6] and 255)
            else -> null
        }
        if (rows == null || rows.size != 16) return null
        val out = ByteArray(256)
        for (y in 0 until 16) for (x in 0 until 16) out[y * 16 + x] = (rows[y][x] - '0' - 2).coerceIn(-2, 2).toByte()
        return Map(16, out)
    }

    fun map(tile: Int): Map {
        val k = if (tile in cache.indices) tile else 0
        cache[k]?.let { return it }
        val h = hand(k)
        if (h != null) { cache[k] = h; return h }
        val out = ByteArray(G * G)
        Atlas.bmp
        val raw = Atlas.raw
        if (raw != null) {
            val w = Atlas.T * Atlas.NT
            val x0 = (k % Atlas.NT) * Atlas.T; val y0 = (k / Atlas.NT) * Atlas.T
            val cs = Atlas.T / G
            val lum = FloatArray(G * G); val ok = BooleanArray(G * G)
            var sum = 0f; var n = 0
            for (j in 0 until G) for (i in 0 until G) {
                var l = 0f; var cnt = 0
                for (y in 0 until cs) for (x in 0 until cs) {
                    val c = raw[(y0 + j * cs + y) * w + x0 + i * cs + x]
                    if ((c ushr 24) < 100) continue
                    l += (((c shr 16) and 255) * 0.299f + ((c shr 8) and 255) * 0.587f + (c and 255) * 0.114f) / 255f; cnt++
                }
                if (cnt > 0) { lum[j * G + i] = l / cnt; ok[j * G + i] = true }
                if (cnt > 0 && i in 1 until G - 1 && j in 1 until G - 1) { sum += l / cnt; n++ }
            }
            if (n > 0) {
                val m = sum / n
                var vr = 0f
                for (j in 1 until G - 1) for (i in 1 until G - 1) if (ok[j * G + i]) { val d = lum[j * G + i] - m; vr += d * d }
                val sd = sqrt(vr / n)
                if (sd > 0.03f) {
                    for (j in 1 until G - 1) for (i in 1 until G - 1) {
                        if (!ok[j * G + i]) continue
                        val z = (lum[j * G + i] - m) / sd
                        out[j * G + i] = (if (z > 1.25f) 2 else if (z > 0.5f) 1 else if (z < -1.25f) -2 else if (z < -0.5f) -1 else 0).toByte()
                    }
                }
            }
        }
        val r = Map(G, out)
        cache[k] = r
        return r
    }

    /** tom de cada nível: o fundo das frestas escurece (parece fundo mesmo), o alto pega mais luz */
    fun tone(l: Int): Float = when (l) { 2 -> 1.12f; 1 -> 1.06f; 0 -> 1f; -1 -> 0.84f; else -> 0.72f }
}
