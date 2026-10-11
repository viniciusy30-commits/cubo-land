package com.cuboland.app

import kotlin.math.sqrt

/** Mapa de relevo de cada tile do atlas: grade 8x8 (cada peça = 2x2 pixels da textura) com nível -2..+2.
 *  Mais claro que o resto da textura = sai pra fora (pepitas, pedras, nervuras); mais escuro = entra (frestas, rejunte, buracos).
 *  A borda de 1 peça fica sempre no nível 0 pra os blocos encostarem sem frestas. */
object Relief {
    const val G = 8
    const val STEP = 0.045f
    private val cache = arrayOfNulls<ByteArray>(Atlas.NT * Atlas.ROWS)

    fun map(tile: Int): ByteArray {
        val k = if (tile in cache.indices) tile else 0
        cache[k]?.let { return it }
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
        cache[k] = out
        return out
    }
}
