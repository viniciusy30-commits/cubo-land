package com.cuboland.app

/** Variações do topo de blocos com "grãos" (podzol, grama nevada, micélio, terra grossa): cada bloco no mundo sorteia uma das V versões do tile,
 *  com os grãos (aglomerados de pixels) em outras posições e em quantidade diferente: nenhum, poucos, mais, todos.
 *  O relevo da variante é derivado do mapa da NatRelief: cada grão leva consigo a sua elevação. */
object NatVar {
    const val V = 6
    private val DENS = floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 0.85f, 1f)

    /** índices da paleta que são "grão" em cada textura de topo */
    private fun grain(art: Int): Set<Int>? = when (art) {
        NatArt.PODZOL_TOP -> setOf(5, 6, 7)
        NatArt.GRAMA_NEVADA_TOP -> setOf(5, 6, 7)
        NatArt.MICELIO_TOP -> setOf(5, 6)
        NatArt.TERRA_GROSSA -> setOf(5, 6, 7, 8, 9)
        else -> null
    }
    fun has(art: Int) = grain(art) != null

    private class Data(val idx: IntArray, val lv: ByteArray)
    private val cache = HashMap<Int, Data>()

    private fun data(art: Int, v: Int): Data = cache.getOrPut(art * 16 + v) { build(art, v) }

    private fun build(art: Int, v: Int): Data {
        val a = NatArt.all[art]
        val gs = grain(art) ?: emptySet()
        val n = 16
        val base = IntArray(n * n) { Character.digit(a.rows[it / n][it % n], 16) }
        val rel = NatRelief.art.getOrNull(art)
        val bl = IntArray(n * n) { if (rel != null && rel.size == 16) (rel[it / n][it % n] - '0' - 2).coerceIn(-2, 2) else 0 }
        val isG = BooleanArray(n * n) { base[it] in gs }
        val rnd = java.util.Random(art * 7919L + v * 104729L + 17L)
        // aglomerados (8 vizinhos)
        val lab = IntArray(n * n) { -1 }
        val clusters = ArrayList<IntArray>()
        for (s in 0 until n * n) {
            if (!isG[s] || lab[s] >= 0) continue
            val cell = ArrayList<Int>(); val st = ArrayDeque<Int>(); st.add(s); lab[s] = clusters.size
            while (st.isNotEmpty()) {
                val c = st.removeLast(); cell.add(c)
                for (dy in -1..1) for (dx in -1..1) {
                    val x = c % n + dx; val y = c / n + dy
                    if (x < 0 || y < 0 || x >= n || y >= n) continue
                    val k = y * n + x
                    if (isG[k] && lab[k] < 0) { lab[k] = clusters.size; st.add(k) }
                }
            }
            clusters.add(cell.toIntArray())
        }
        // chão: os grãos somem e a elevação em volta (1 pixel) volta ao nível 0
        val idx = base.copyOf(); val lv = bl.copyOf()
        val fill = intArrayOf(1, 2, 2, 3)
        val near = BooleanArray(n * n)
        for (c in 0 until n * n) if (isG[c]) {
            idx[c] = fill[rnd.nextInt(fill.size)]
            for (dy in -1..1) for (dx in -1..1) {
                val x = c % n + dx; val y = c / n + dy
                if (x in 0 until n && y in 0 until n) near[y * n + x] = true
            }
        }
        for (c in 0 until n * n) if (near[c]) lv[c] = 0
        // nem todo grão volta pro lugar: deslocamento cíclico + espelho, e só uma parte dos aglomerados
        val sx = if (DENS[v] >= 1f) 0 else rnd.nextInt(n); val sy = if (DENS[v] >= 1f) 0 else rnd.nextInt(n)
        val flip = DENS[v] < 1f && rnd.nextBoolean()
        fun dest(c: Int): Int {
            var x = c % n; val y = c / n
            if (flip) x = n - 1 - x
            return ((y + sy) % n) * n + (x + sx) % n
        }
        fun ring(d: Int) = d % n == 0 || d / n == 0 || d % n == n - 1 || d / n == n - 1
        val used = BooleanArray(n * n)
        for (cl in clusters) {
            if (rnd.nextFloat() >= DENS[v]) continue
            var bad = false
            for (c in cl) { val d = dest(c); if (ring(d) || used[d]) { bad = true; break } }
            if (bad) continue
            for (c in cl) { val d = dest(c); idx[d] = base[c]; lv[d] = bl[c].toByte().toInt(); used[d] = true }
            // elevação do entorno do grão (chanfro) acompanha
            for (c in cl) for (dy in -1..1) for (dx in -1..1) {
                val x = c % n + dx; val y = c / n + dy
                if (x !in 0 until n || y !in 0 until n) continue
                val k = y * n + x
                if (isG[k]) continue
                val d = dest(k)
                if (ring(d) || used[d]) continue
                lv[d] = bl[k]
            }
        }
        for (c in 0 until n * n) if (ring(c)) lv[c] = bl[c].coerceAtMost(0).coerceAtLeast(0)
        return Data(idx, ByteArray(n * n) { lv[it].toByte() })
    }

    /** pixel (px,py) da variante v do topo (ARGB, igual ao K_PAL) */
    fun pixel(art: Int, v: Int, px: Int, py: Int): Int {
        val d = data(art, v.coerceIn(0, V - 1))
        return NatArt.all[art].pal[d.idx[py * 16 + px].coerceAtLeast(0)]
    }

    /** relevo 16x16 da variante (níveis -2..+2) */
    fun relief(art: Int, v: Int): Relief.Map = Relief.Map(16, data(art, v.coerceIn(0, V - 1)).lv)
}
