package com.cuboland.app

import kotlin.math.sqrt

/** Mapa de relevo de cada tile do atlas: grade 8x8 (cada peça = 2x2 pixels da textura) com nível -2..+2.
 *  Mais claro que o resto da textura = sai pra fora (pepitas, pedras, nervuras); mais escuro = entra (frestas, rejunte, buracos).
 *  A borda de 1 peça fica sempre no nível 0 pra os blocos encostarem sem frestas. */
object Relief {
    /** grade do relevo automático (por brilho): peças de 2x2 pixels */
    const val G = 8
    const val STEP = 0.045f
    /** degrau de relevo por tile: a Construção (estilo Minecraft) tem relevo mais fundo, pra os detalhes 3D aparecerem bem */
    /** relevo sutil das texturas do pack Actions & Stuff na Natureza: só 1 degrau pra fora/dentro, mais raso e com luz mais fraca (a textura em si não muda) */
    const val STEP_SOFT = 0.04f
    private val packBase = arrayOf("", "", "", "dirt", "stone", "sand", "log_oak", "log_oak_top")   // tiles básicos 3..7 pintados com o pack
    fun soft(tile: Int): Boolean = (tile in 3..7) || Blk.isMc(tile)
    fun step(tile: Int): Float {
        if (soft(tile)) return STEP_SOFT
        val sp = Blk.specs.getOrNull(tile)
        return if (BuildArt.baseTile(tile) != null || (sp != null && sp[0] == Blk.K_BUILD)) 0.058f else STEP
    }
    /** g = peças por lado (8 no automático, 16 nos mapas escritos à mão da NatRelief); lv = nível -2..+2 de cada peça */
    class Map(val g: Int, val lv: ByteArray)
    private val cache = arrayOfNulls<Map>(Atlas.NT * Atlas.ROWS)

    /** mapa escrito à mão desse tile (tipo K_PAL da NatArt ou padrão Pix de K_PIX), ou null se o bloco usa o automático */
    private fun hand(tile: Int): Map? {
        if (tile in 3..7) return packMap(McTex.id(packBase[tile]))
        run { val sp = Blk.specs.getOrNull(tile); if (sp != null && sp[0] == Blk.K_MC) return packMap(sp[1]) }
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

    /** relevo sutil a partir dos pixels 16x16 da textura do pack: só níveis -1/0/+1, borda sempre 0 (blocos encostam sem fresta).
     *  Texturas lisas ficam planas; minérios levantam as pintas que destoam da pedra; o resto levanta o claro e afunda o escuro. */
    private fun packMap(tex: Int): Map {
        val px = McTex.pix(tex)
        val out = ByteArray(256)
        val nm = McTex.nameOf(tex)
        if (!(nm.contains("_ore") || nm.contains("debris"))) {
            // fora os minérios: nada de relevo por brilho (dava grãozinhos). Só os vãos de verdade (rejunte, frestas entre tábuas) entram; o resto fica liso.
            val gm = GapMaps.m[nm] ?: return Map(16, out)
            for (y in 0 until 16) for (x in 0 until 16) if (gm[y][x] == '#') out[y * 16 + x] = -1
            return Map(16, out)
        }
        val lum = FloatArray(256); val ok = BooleanArray(256)
        var sum = 0f; var n = 0
        for (i in 0 until 256) {
            val c = px[i]
            if ((c ushr 24) < 100) continue
            lum[i] = (((c shr 16) and 255) * 0.299f + ((c shr 8) and 255) * 0.587f + (c and 255) * 0.114f) / 255f
            ok[i] = true
            val x = i and 15; val y = i shr 4
            if (x in 1..14 && y in 1..14) { sum += lum[i]; n++ }
        }
        if (n == 0) return Map(16, out)
        val m = sum / n
        var vr = 0f
        for (y in 1..14) for (x in 1..14) if (ok[y * 16 + x]) { val d = lum[y * 16 + x] - m; vr += d * d }
        val sd = sqrt(vr / n)
        if (sd < 0.045f) return Map(16, out)   // textura quase lisa: fica plana
        val ore = nm.contains("_ore") || nm.contains("debris")
        // distância de cor até a cor mediana (minério: as pintas diferentes da pedra)
        var med = 0
        if (ore) {
            val rs = IntArray(n); val gs = IntArray(n); val bs = IntArray(n); var k = 0
            for (y in 1..14) for (x in 1..14) if (ok[y * 16 + x] && k < n) { val c = px[y * 16 + x]; rs[k] = (c shr 16) and 255; gs[k] = (c shr 8) and 255; bs[k] = c and 255; k++ }
            rs.sort(0, k); gs.sort(0, k); bs.sort(0, k)
            med = (rs[k / 2] shl 16) or (gs[k / 2] shl 8) or bs[k / 2]
        }
        fun dist(c: Int): Float {
            val dr = ((c shr 16) and 255) - ((med shr 16) and 255); val dg = ((c shr 8) and 255) - ((med shr 8) and 255); val db = (c and 255) - (med and 255)
            return sqrt((dr * dr + dg * dg + db * db).toFloat())
        }
        var thr = 1.0f
        repeat(3) {
            var cnt = 0
            for (y in 1..14) for (x in 1..14) {
                val i = y * 16 + x
                if (!ok[i]) { out[i] = 0; continue }
                val l: Int = if (ore) (if (dist(px[i]) > 38f) 1 else 0)
                    else { val z = (lum[i] - m) / sd; if (z > thr) 1 else if (z < -thr) -1 else 0 }
                out[i] = l.toByte(); if (l != 0) cnt++
            }
            if (cnt <= 64) return Map(16, out)   // limite de peças: não pesar a malha
            thr += 0.3f
        }
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
    fun tone(l: Int, soft: Boolean = false): Float = if (soft) (when (l) { 1 -> 1.05f; -1 -> 0.9f; else -> 1f }) else when (l) { 2 -> 1.12f; 1 -> 1.06f; 0 -> 1f; -1 -> 0.84f; else -> 0.72f }
}
