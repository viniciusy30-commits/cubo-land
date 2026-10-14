package com.cuboland.app

import kotlin.math.*

/**
 * Mobília nova (subtipos 100..255 do kind 7). Cada bloco tem uma paleta de tiles (Blk.fp):
 * [0] corpo/tecido, [1] corpo 2 (capitonê/escuro), [2] madeira, [3] madeira escura, [4] metal, [5] detalhe, [6] luz/vidro, [7] extra.
 * Cores e materiais variam só trocando a paleta; o desenho é o mesmo.
 */
object FurnB {
    // assentos
    const val S_SOFAM = 100; const val S_CHEST = 101; const val S_WING = 102; const val S_POUF = 103
    const val S_CHAIRM = 104; const val S_BAR = 105; const val S_BENCH = 106; const val S_CHAISE = 107
    // mesas
    const val S_DINING = 110; const val S_NIGHT = 111; const val S_SIDE = 112; const val S_COUNTER = 113; const val S_BOARD = 114
    // armazenamento
    const val S_TALL = 120; const val S_WARDM = 121; const val S_SHELFM = 122; const val S_CABINET = 123; const val S_TRUNK = 124; const val S_WALLCAB = 125
    // eletros
    const val S_FRIDGE2 = 130; const val S_FRIDGER = 131; const val S_MINI = 132; const val S_STOVE = 133; const val S_MICRO = 134
    const val S_WASHER = 135; const val S_SINK = 136; const val S_HOOD = 137
    // banheiro
    const val S_TOILET = 140; const val S_BASIN = 141; const val S_TUB = 142
    // cama
    const val S_BEDM = 150; const val S_CRIB = 151
    // cortinas
    const val S_CUROPEN = 160; const val S_CURCLOSED = 161; const val S_CURSHEER = 162; const val S_CURVAL = 163; const val S_ROMAN = 164
    // lâmpadas
    const val S_TLAMP = 170; const val S_FLOOR = 171; const val S_ARC = 172; const val S_PEND = 173; const val S_CHAND = 174
    const val S_SCONCE = 175; const val S_DESKL = 176; const val S_PAPER = 177; const val S_CANDLE = 178; const val S_MOON = 179; const val S_LAVA = 180
    // decoração
    const val S_PALM = 185; const val S_FERN = 186; const val S_CACTUS = 187; const val S_FLOWERS = 188; const val S_BONSAI = 189
    const val S_PAINT = 190; const val S_MIRROR = 191; const val S_CLOCK = 192; const val S_GRAND = 193; const val S_BOOKS = 194
    const val S_FRUIT = 195; const val S_GLOBE = 196; const val S_FIRE = 197

    fun sittable(s: Int) = s == S_SOFAM || s == S_CHEST || s == S_WING || s == S_POUF || s == S_CHAIRM || s == S_BAR || s == S_BENCH || s == S_CHAISE
    fun sofaLike(s: Int) = s == S_SOFAM || s == S_CHEST || s == S_BENCH
    fun faceTable(s: Int) = s == S_WING || s == S_CHAIRM || s == S_BAR || s == S_POUF
    fun isTable(s: Int) = s == S_DINING || s == S_SIDE || s == S_COUNTER || s == S_NIGHT || s == S_BOARD
    fun seatH(s: Int): Float = when (s) {
        S_SOFAM -> 0.46f; S_CHEST -> 0.5f; S_WING -> 0.5f; S_POUF -> 0.42f; S_CHAIRM -> 0.52f; S_BAR -> 0.9f; S_BENCH -> 0.5f; S_CHAISE -> 0.38f
        else -> 0.55f
    }
    fun topH(s: Int): Float = when (s) {
        S_DINING -> 0.92f; S_NIGHT -> 0.7f; S_SIDE -> 0.6f; S_COUNTER -> 0.94f; S_BOARD -> 0.66f; S_SINK -> 0.94f
        S_MINI -> 0.62f; S_TRUNK -> 0.55f; S_WASHER -> 0.9f
        else -> 0f
    }
    /** peças pequenas que ficam em cima de mesas, balcões e cômodas */
    fun small(s: Int) = s == S_TLAMP || s == S_DESKL || s == S_CANDLE || s == S_MOON || s == S_LAVA || s == S_FLOWERS ||
        s == S_BONSAI || s == S_BOOKS || s == S_FRUIT || s == S_GLOBE || s == S_MICRO
    private fun wallItem(s: Int) = s in S_CUROPEN..S_ROMAN || s == S_SCONCE || s == S_PAINT || s == S_MIRROR || s == S_CLOCK || s == S_WALLCAB || s == S_HOOD

    // ---------- rotação ----------
    private fun rxf(r: Int, a: Float, c: Float) = when (r) { 0 -> a; 1 -> 1f - a; 2 -> c; else -> 1f - c }
    private fun rzf(r: Int, a: Float, c: Float) = when (r) { 0 -> c; 1 -> 1f - c; 2 -> 1f - a; else -> a }
    private fun rb(r: Int, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float): FloatArray {
        val ax = rxf(r, x0, z0); val bx = rxf(r, x1, z1); val az = rzf(r, x0, z0); val bz = rzf(r, x1, z1)
        return floatArrayOf(min(ax, bx), y0, min(az, bz), max(ax, bx), y1, max(az, bz))
    }
    private fun wallish(b: Int): Boolean {
        if (!B.solid(b)) return false
        if (B.leafy(b) || B.thin(b)) return false
        if (B.shape(b) == 0) return true
        return B.shape(b) == 7 && Furniture.sub(b) in 60..61
    }
    /** direção da frente: 0 = +Z, 1 = -Z, 2 = +X, 3 = -X; as costas ficam no lado da parede */
    fun wallDir(w: World, x: Int, y: Int, z: Int): Int {
        val dx = intArrayOf(0, 0, 1, -1); val dz = intArrayOf(1, -1, 0, 0)
        for (d in 0..3) if (wallish(w.get(x - dx[d], y, z - dz[d]))) return d
        return 0
    }

    fun box(w: World, x: Int, y: Int, z: Int, s: Int): FloatArray? {
        val r = if (wallItem(s)) wallDir(w, x, y, z) else 0
        return when (s) {
            S_SOFAM, S_CHEST -> floatArrayOf(0f, 0f, 0f, 1f, 0.88f, 1f)
            S_WING -> floatArrayOf(0.05f, 0f, 0.05f, 0.95f, 0.95f, 0.95f)
            S_POUF -> floatArrayOf(0.12f, 0f, 0.12f, 0.88f, 0.42f, 0.88f)
            S_CHAIRM -> floatArrayOf(0.12f, 0f, 0.12f, 0.88f, 0.95f, 0.88f)
            S_BAR -> floatArrayOf(0.18f, 0f, 0.18f, 0.82f, 0.9f, 0.82f)
            S_BENCH -> floatArrayOf(0f, 0f, 0f, 1f, 0.9f, 1f)
            S_CHAISE -> floatArrayOf(0f, 0f, 0f, 1f, 0.55f, 1f)
            S_DINING -> floatArrayOf(0f, 0f, 0f, 1f, 0.92f, 1f)
            S_NIGHT -> floatArrayOf(0.06f, 0f, 0.06f, 0.94f, 0.7f, 0.94f)
            S_SIDE -> floatArrayOf(0.14f, 0f, 0.14f, 0.86f, 0.6f, 0.86f)
            S_COUNTER, S_SINK -> floatArrayOf(0f, 0f, 0f, 1f, 0.94f, 1f)
            S_BOARD -> floatArrayOf(0f, 0f, 0.1f, 1f, 0.66f, 0.9f)
            S_TALL, S_WARDM, S_SHELFM, S_CABINET, S_FRIDGE2, S_FRIDGER -> floatArrayOf(0.02f, 0f, 0.02f, 0.98f, 1f, 0.98f)
            S_TRUNK -> floatArrayOf(0.06f, 0f, 0.1f, 0.94f, 0.55f, 0.9f)
            S_WALLCAB -> rb(r, 0f, 0.4f, 0f, 1f, 1f, 0.5f)
            S_MINI -> floatArrayOf(0.08f, 0f, 0.08f, 0.92f, 0.62f, 0.92f)
            S_STOVE -> floatArrayOf(0.02f, 0f, 0.02f, 0.98f, 0.9f, 0.98f)
            S_MICRO -> floatArrayOf(0.06f, 0f, 0.14f, 0.94f, 0.5f, 0.86f)
            S_WASHER -> floatArrayOf(0.04f, 0f, 0.04f, 0.96f, 0.9f, 0.96f)
            S_HOOD -> rb(r, 0f, 0.7f, 0f, 1f, 1f, 0.7f)
            S_TOILET -> floatArrayOf(0.15f, 0f, 0.05f, 0.85f, 0.75f, 0.95f)
            S_BASIN -> floatArrayOf(0.1f, 0f, 0.1f, 0.9f, 0.9f, 0.9f)
            S_TUB -> floatArrayOf(0f, 0f, 0f, 1f, 0.5f, 1f)
            S_BEDM -> floatArrayOf(0f, 0f, 0f, 1f, 0.65f, 1f)
            S_CRIB -> floatArrayOf(0.04f, 0f, 0.04f, 0.96f, 0.85f, 0.96f)
            S_CUROPEN, S_CURCLOSED, S_CURSHEER, S_CURVAL, S_ROMAN -> rb(r, 0f, 0f, 0f, 1f, 1f, 0.2f)
            S_SCONCE -> rb(r, 0.3f, 0.4f, 0f, 0.7f, 0.9f, 0.3f)
            S_PAINT, S_MIRROR, S_CLOCK -> rb(r, 0.08f, 0.1f, 0f, 0.92f, 0.92f, 0.1f)
            S_TLAMP -> floatArrayOf(0.22f, 0f, 0.22f, 0.78f, 0.7f, 0.78f)
            S_FLOOR -> floatArrayOf(0.22f, 0f, 0.22f, 0.78f, 1f, 0.78f)
            S_ARC -> floatArrayOf(0.1f, 0f, 0.1f, 0.9f, 1f, 0.9f)
            S_PEND, S_PAPER -> floatArrayOf(0.2f, 0.3f, 0.2f, 0.8f, 1f, 0.8f)
            S_CHAND -> floatArrayOf(0.1f, 0.45f, 0.1f, 0.9f, 1f, 0.9f)
            S_DESKL -> floatArrayOf(0.15f, 0f, 0.15f, 0.85f, 0.7f, 0.85f)
            S_CANDLE -> floatArrayOf(0.25f, 0f, 0.25f, 0.75f, 0.6f, 0.75f)
            S_MOON -> floatArrayOf(0.2f, 0f, 0.3f, 0.8f, 0.62f, 0.7f)
            S_LAVA -> floatArrayOf(0.28f, 0f, 0.28f, 0.72f, 0.78f, 0.72f)
            S_PALM -> floatArrayOf(0.2f, 0f, 0.2f, 0.8f, 1f, 0.8f)
            S_FERN, S_CACTUS -> floatArrayOf(0.18f, 0f, 0.18f, 0.82f, 0.8f, 0.82f)
            S_FLOWERS -> floatArrayOf(0.25f, 0f, 0.25f, 0.75f, 0.6f, 0.75f)
            S_BONSAI -> floatArrayOf(0.15f, 0f, 0.15f, 0.85f, 0.5f, 0.85f)
            S_GRAND -> floatArrayOf(0.1f, 0f, 0.1f, 0.9f, 1f, 0.9f)
            S_BOOKS -> floatArrayOf(0.15f, 0f, 0.2f, 0.85f, 0.25f, 0.8f)
            S_FRUIT -> floatArrayOf(0.15f, 0f, 0.15f, 0.85f, 0.3f, 0.85f)
            S_GLOBE -> floatArrayOf(0.2f, 0f, 0.2f, 0.8f, 0.6f, 0.8f)
            S_FIRE -> floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f)
            else -> null
        }
    }

    /** direção usada pelo desenho (e pela caixa) */
    fun rot(w: World, x: Int, y: Int, z: Int, s: Int): Int = when {
        wallItem(s) -> wallDir(w, x, y, z)
        else -> Furniture.facing(w, x, y, z, s)
    }

    // ---------- pincel curto ----------
    private fun bx(c: Furniture.C, t: Int, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) = c.box(t, t, t, x0, y0, z0, x1, y1, z1)
    private fun gx(c: Furniture.C, t: Int, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float) = c.box(t, t, t, x0, y0, z0, x1, y1, z1, true)
    private fun cy(c: Furniture.C, t: Int, cx: Float, cz: Float, r0: Float, r1: Float, y0: Float, y1: Float, n: Int = 12) = c.cyl(t, t, t, cx, cz, r0, r1, y0, y1, n)
    private fun gy(c: Furniture.C, t: Int, cx: Float, cz: Float, r0: Float, r1: Float, y0: Float, y1: Float, n: Int = 12) = c.cyl(t, t, t, cx, cz, r0, r1, y0, y1, n, true)
    private fun hh(x: Int, z: Int, k: Int): Float {
        var n = x * 374761393 + z * 668265263 + k * 1442695041
        n = (n xor (n ushr 13)) * 1274126177; n = n xor (n ushr 16)
        return (n and 0xFFFF) / 65535f
    }

    internal fun emit(c: Furniture.C, w: World, x: Int, y: Int, z: Int, id: Int, s: Int) {
        val p = Blk.fp(id)
        val T = Blk.fts
        c.lit = false
        c.rot = rot(w, x, y, z, s)
        when (s) {
            S_SOFAM -> sofaM(c, p, w, x, y, z, id)
            S_CHEST -> chesterfield(c, p, w, x, y, z, id)
            S_WING -> wing(c, p)
            S_POUF -> pouf(c, p)
            S_CHAIRM -> chairM(c, p)
            S_BAR -> barStool(c, p)
            S_BENCH -> bench(c, p, w, x, y, z, id)
            S_CHAISE -> chaise(c, p)
            S_DINING -> dining(c, p, w, x, y, z, id)
            S_NIGHT -> nightstand(c, p, T)
            S_SIDE -> sideTable(c, p)
            S_COUNTER -> counter(c, p, T, w, x, y, z, id)
            S_BOARD -> sideboard(c, p, T)
            S_TALL -> tallCabinet(c, p, T)
            S_WARDM -> wardrobeM(c, p, T)
            S_SHELFM -> shelfM(c, p, T, w, x, y, z, id)
            S_CABINET -> cabinet(c, p, T)
            S_TRUNK -> trunk(c, p, T)
            S_WALLCAB -> wallCab(c, p, T)
            else -> emit2(c, p, T, w, x, y, z, id, s)
        }
    }

    // =============== ASSENTOS ===============
    private fun sofaM(c: Furniture.C, p: IntArray, w: World, x: Int, y: Int, z: Int, id: Int) {
        val b = p[0]; val b2 = p[1]; val mt = p[4]
        val rx = intArrayOf(1, -1, 0, 0)[c.rot]; val rz = intArrayOf(0, 0, -1, 1)[c.rot]
        val nR = w.get(x + rx, y, z + rz) == id; val nL = w.get(x - rx, y, z - rz) == id
        val l0 = if (nL) 0f else 0.15f; val r1 = if (nR) 1f else 0.85f
        for (px in floatArrayOf(0.05f, 0.89f)) for (pz in floatArrayOf(0.08f, 0.86f)) bx(c, mt, px, 0f, pz, px + 0.06f, 0.1f, pz + 0.06f)
        bx(c, b2, 0f, 0.1f, 0.04f, 1f, 0.24f, 0.96f)
        bx(c, b, l0 + 0.005f, 0.24f, 0.28f, r1 - 0.005f, 0.4f, 0.96f)
        bx(c, b, 0f, 0.24f, 0.04f, 1f, 0.72f, 0.26f)
        val mid = (l0 + r1) / 2f
        bx(c, b2, l0 + 0.02f, 0.4f, 0.14f, mid - 0.01f, 0.66f, 0.28f); bx(c, b2, mid + 0.01f, 0.4f, 0.14f, r1 - 0.02f, 0.66f, 0.28f)
        if (!nL) bx(c, b, 0f, 0.24f, 0.04f, 0.15f, 0.55f, 0.96f)
        if (!nR) bx(c, b, 0.85f, 0.24f, 0.04f, 1f, 0.55f, 0.96f)
    }

    private fun chesterfield(c: Furniture.C, p: IntArray, w: World, x: Int, y: Int, z: Int, id: Int) {
        val b = p[0]; val t = p[1]; val wk = p[3]; val gd = p[4]
        val rx = intArrayOf(1, -1, 0, 0)[c.rot]; val rz = intArrayOf(0, 0, -1, 1)[c.rot]
        val nR = w.get(x + rx, y, z + rz) == id; val nL = w.get(x - rx, y, z - rz) == id
        val l0 = if (nL) 0f else 0.17f; val r1 = if (nR) 1f else 0.83f
        for (px in floatArrayOf(0.06f, 0.88f)) for (pz in floatArrayOf(0.08f, 0.86f)) { bx(c, wk, px, 0f, pz, px + 0.06f, 0.08f, pz + 0.06f); bx(c, gd, px - 0.005f, 0.06f, pz - 0.005f, px + 0.065f, 0.08f, pz + 0.065f) }
        bx(c, b, 0f, 0.08f, 0.04f, 1f, 0.3f, 0.96f)
        bx(c, b, l0 + 0.005f, 0.3f, 0.28f, r1 - 0.005f, 0.46f, 0.96f)
        bx(c, t, 0f, 0.3f, 0.04f, 1f, 0.8f, 0.26f)
        bx(c, b, 0f, 0.8f, 0.03f, 1f, 0.88f, 0.3f)
        if (!nL) { bx(c, b, 0f, 0.3f, 0.04f, 0.17f, 0.6f, 0.96f); bx(c, b, -0.01f, 0.6f, 0.02f, 0.18f, 0.7f, 0.98f); bx(c, gd, 0.05f, 0.3f, 0.965f, 0.12f, 0.58f, 0.99f) }
        if (!nR) { bx(c, b, 0.83f, 0.3f, 0.04f, 1f, 0.6f, 0.96f); bx(c, b, 0.82f, 0.6f, 0.02f, 1.01f, 0.7f, 0.98f); bx(c, gd, 0.88f, 0.3f, 0.965f, 0.95f, 0.58f, 0.99f) }
    }

    private fun wing(c: Furniture.C, p: IntArray) {
        val b = p[0]; val t = p[1]; val wk = p[3]
        for (sx in 0..1) for (sz in 0..1) { val px = if (sx == 0) 0.1f else 0.82f; val pz = if (sz == 0) 0.12f else 0.82f; bx(c, wk, px, 0f, pz, px + 0.08f, 0.1f, pz + 0.08f) }
        bx(c, b, 0.06f, 0.1f, 0.08f, 0.94f, 0.32f, 0.92f)
        bx(c, t, 0.06f, 0.32f, 0.08f, 0.94f, 0.95f, 0.28f)
        bx(c, b, 0.04f, 0.32f, 0.1f, 0.2f, 0.62f, 0.9f); bx(c, b, 0.8f, 0.32f, 0.1f, 0.96f, 0.62f, 0.9f)
        bx(c, b, 0.04f, 0.62f, 0.08f, 0.22f, 0.9f, 0.42f); bx(c, b, 0.78f, 0.62f, 0.08f, 0.96f, 0.9f, 0.42f)
        bx(c, t, 0.2f, 0.32f, 0.28f, 0.8f, 0.46f, 0.9f)
    }

    private fun pouf(c: Furniture.C, p: IntArray) {
        val b = p[0]; val t = p[1]
        cy(c, b, 0.5f, 0.5f, 0.34f, 0.38f, 0f, 0.3f, 14)
        c.cyl(t, t, t, 0.5f, 0.5f, 0.36f, 0.3f, 0.3f, 0.42f, 14)
        bx(c, p[4], 0.47f, 0.42f, 0.47f, 0.53f, 0.44f, 0.53f)
    }

    private fun chairM(c: Furniture.C, p: IntArray) {
        val b = p[0]; val mt = p[4]; val wd = p[2]
        for (sx in 0..1) for (sz in 0..1) { val px = if (sx == 0) 0.16f else 0.8f; val pz = if (sz == 0) 0.16f else 0.8f; bx(c, mt, px, 0f, pz, px + 0.04f, 0.42f, pz + 0.04f) }
        bx(c, mt, 0.16f, 0.14f, 0.18f, 0.84f, 0.17f, 0.2f); bx(c, mt, 0.16f, 0.14f, 0.8f, 0.84f, 0.17f, 0.82f)
        bx(c, b, 0.12f, 0.42f, 0.14f, 0.88f, 0.52f, 0.86f)
        bx(c, wd, 0.14f, 0.52f, 0.14f, 0.86f, 0.55f, 0.84f)
        bx(c, b, 0.14f, 0.55f, 0.12f, 0.86f, 0.92f, 0.22f)
        bx(c, mt, 0.16f, 0.52f, 0.14f, 0.2f, 0.7f, 0.18f); bx(c, mt, 0.8f, 0.52f, 0.14f, 0.84f, 0.7f, 0.18f)
    }

    private fun barStool(c: Furniture.C, p: IntArray) {
        val b = p[0]; val mt = p[4]
        for (sx in 0..1) for (sz in 0..1) { val px = if (sx == 0) 0.24f else 0.7f; val pz = if (sz == 0) 0.24f else 0.7f; bx(c, mt, px, 0f, pz, px + 0.06f, 0.78f, pz + 0.06f) }
        bx(c, mt, 0.24f, 0.32f, 0.24f, 0.76f, 0.35f, 0.27f); bx(c, mt, 0.24f, 0.32f, 0.73f, 0.76f, 0.35f, 0.76f)
        bx(c, mt, 0.24f, 0.32f, 0.27f, 0.27f, 0.35f, 0.73f); bx(c, mt, 0.73f, 0.32f, 0.27f, 0.76f, 0.35f, 0.73f)
        cy(c, p[2], 0.5f, 0.5f, 0.3f, 0.3f, 0.78f, 0.82f, 14)
        c.cyl(b, b, b, 0.5f, 0.5f, 0.3f, 0.26f, 0.82f, 0.9f, 14)
    }

    private fun bench(c: Furniture.C, p: IntArray, w: World, x: Int, y: Int, z: Int, id: Int) {
        val wd = p[2]; val wk = p[3]; val mt = p[4]
        val rx = intArrayOf(1, -1, 0, 0)[c.rot]; val rz = intArrayOf(0, 0, -1, 1)[c.rot]
        val nR = w.get(x + rx, y, z + rz) == id; val nL = w.get(x - rx, y, z - rz) == id
        if (!nL) { bx(c, mt, 0.02f, 0f, 0.1f, 0.08f, 0.9f, 0.14f); bx(c, mt, 0.02f, 0f, 0.86f, 0.08f, 0.42f, 0.9f); bx(c, mt, 0.02f, 0.4f, 0.1f, 0.08f, 0.44f, 0.9f) }
        if (!nR) { bx(c, mt, 0.92f, 0f, 0.1f, 0.98f, 0.9f, 0.14f); bx(c, mt, 0.92f, 0f, 0.86f, 0.98f, 0.42f, 0.9f); bx(c, mt, 0.92f, 0.4f, 0.1f, 0.98f, 0.44f, 0.9f) }
        for (k in 0 until 4) { val z0 = 0.16f + k * 0.2f; bx(c, wd, 0f, 0.44f, z0, 1f, 0.5f, z0 + 0.16f) }
        for (k in 0 until 3) { val y0 = 0.56f + k * 0.12f; bx(c, wd, 0f, y0, 0.08f, 1f, y0 + 0.09f, 0.13f) }
        bx(c, wk, 0f, 0.5f, 0.06f, 1f, 0.9f, 0.08f)
    }

    private fun chaise(c: Furniture.C, p: IntArray) {
        val b = p[0]; val t = p[1]; val wk = p[3]
        for (px in floatArrayOf(0.05f, 0.89f)) for (pz in floatArrayOf(0.05f, 0.89f)) bx(c, wk, px, 0f, pz, px + 0.06f, 0.12f, pz + 0.06f)
        bx(c, b, 0.02f, 0.12f, 0.02f, 0.98f, 0.3f, 0.98f)
        bx(c, t, 0.04f, 0.3f, 0.2f, 0.96f, 0.38f, 0.98f)
        bx(c, b, 0.02f, 0.3f, 0.02f, 0.98f, 0.62f, 0.2f)
        bx(c, t, 0.12f, 0.62f, 0.04f, 0.88f, 0.7f, 0.2f)
        bx(c, b, 0.02f, 0.3f, 0.02f, 0.14f, 0.5f, 0.98f)
    }

    // =============== MESAS ===============
    private fun dining(c: Furniture.C, p: IntArray, w: World, x: Int, y: Int, z: Int, id: Int) {
        val tp = p[2]; val ed = p[3]; val lg = p[4]
        fun t(dx: Int, dz: Int) = w.get(x + dx, y, z + dz) == id
        val nW = t(-1, 0); val nE = t(1, 0); val nN = t(0, -1); val nS = t(0, 1)
        val x0 = if (nW) 0f else 0.02f; val x1 = if (nE) 1f else 0.98f
        val z0 = if (nN) 0f else 0.02f; val z1 = if (nS) 1f else 0.98f
        c.box(tp, ed, ed, x0, 0.84f, z0, x1, 0.92f, z1)
        c.box(ed, ed, ed, if (nW) 0f else 0.08f, 0.8f, if (nN) 0f else 0.08f, if (nE) 1f else 0.92f, 0.84f, if (nS) 1f else 0.92f)
        for (sx in 0..1) for (sz in 0..1) {
            val nx = if (sx == 0) nW else nE; val nz = if (sz == 0) nN else nS
            val dg = t(if (sx == 0) -1 else 1, if (sz == 0) -1 else 1)
            if (!((!nx && !nz) || (nx && nz && !dg))) continue
            val cx = if (sx == 0) 0.12f else 0.88f; val cz = if (sz == 0) 0.12f else 0.88f
            bx(c, lg, cx - 0.045f, 0f, cz - 0.045f, cx + 0.045f, 0.84f, cz + 0.045f)
        }
    }

    private fun nightstand(c: Furniture.C, p: IntArray, T: IntArray) {
        val wd = p[2]; val wk = p[3]; val mt = p[4]
        for (px in floatArrayOf(0.1f, 0.84f)) for (pz in floatArrayOf(0.12f, 0.8f)) bx(c, wk, px, 0f, pz, px + 0.06f, 0.14f, pz + 0.08f)
        bx(c, wd, 0.06f, 0.14f, 0.1f, 0.94f, 0.66f, 0.92f)
        bx(c, wk, 0.04f, 0.66f, 0.08f, 0.96f, 0.7f, 0.94f)
        bx(c, wk, 0.1f, 0.2f, 0.915f, 0.9f, 0.4f, 0.935f); bx(c, wk, 0.1f, 0.43f, 0.915f, 0.9f, 0.62f, 0.935f)
        bx(c, mt, 0.42f, 0.28f, 0.935f, 0.58f, 0.31f, 0.965f); bx(c, mt, 0.42f, 0.51f, 0.935f, 0.58f, 0.54f, 0.965f)
    }

    private fun sideTable(c: Furniture.C, p: IntArray) {
        val tp = p[2]; val mt = p[4]
        c.cyl(tp, tp, tp, 0.5f, 0.5f, 0.34f, 0.34f, 0.54f, 0.6f, 18)
        cy(c, mt, 0.5f, 0.5f, 0.04f, 0.04f, 0.06f, 0.54f, 8)
        cy(c, mt, 0.5f, 0.5f, 0.24f, 0.1f, 0f, 0.06f, 14)
    }

    private fun counter(c: Furniture.C, p: IntArray, T: IntArray, w: World, x: Int, y: Int, z: Int, id: Int) {
        val wd = p[2]; val wk = p[3]; val mt = p[4]; val top = p[5]
        val rx = intArrayOf(1, -1, 0, 0)[c.rot]; val rz = intArrayOf(0, 0, -1, 1)[c.rot]
        val nR = w.get(x + rx, y, z + rz) == id; val nL = w.get(x - rx, y, z - rz) == id
        val a = if (nL) 0f else 0.01f; val e = if (nR) 1f else 0.99f
        bx(c, wk, a, 0f, 0.06f, e, 0.1f, 0.94f)
        bx(c, wd, a, 0.1f, 0.08f, e, 0.84f, 0.92f)
        c.box(top, top, top, if (nL) 0f else -0.01f, 0.84f, 0.02f, if (nR) 1f else 1.01f, 0.94f, 1f)
        bx(c, wd, a + 0.03f, 0.14f, 0.92f, e - 0.03f, 0.8f, 0.95f)
        bx(c, wk, 0.1f, 0.18f, 0.95f, 0.47f, 0.78f, 0.96f); bx(c, wk, 0.53f, 0.18f, 0.95f, 0.9f, 0.78f, 0.96f)
        bx(c, mt, 0.4f, 0.5f, 0.96f, 0.44f, 0.7f, 0.985f); bx(c, mt, 0.56f, 0.5f, 0.96f, 0.6f, 0.7f, 0.985f)
    }

    private fun sideboard(c: Furniture.C, p: IntArray, T: IntArray) {
        val wd = p[2]; val wk = p[3]; val mt = p[4]
        for (px in floatArrayOf(0.06f, 0.9f)) for (pz in floatArrayOf(0.16f, 0.78f)) bx(c, wk, px, 0f, pz, px + 0.04f, 0.16f, pz + 0.06f)
        bx(c, wd, 0.02f, 0.16f, 0.12f, 0.98f, 0.62f, 0.88f)
        bx(c, wk, 0f, 0.62f, 0.1f, 1f, 0.66f, 0.9f)
        for (d in 0..2) { val x0 = 0.05f + d * 0.31f; bx(c, wk, x0, 0.2f, 0.88f, x0 + 0.29f, 0.58f, 0.9f); bx(c, mt, x0 + 0.12f, 0.36f, 0.9f, x0 + 0.17f, 0.46f, 0.925f) }
    }

    // =============== ARMAZENAMENTO ===============
    private fun tallCabinet(c: Furniture.C, p: IntArray, T: IntArray) {
        val wd = p[2]; val wk = p[3]; val mt = p[4]
        bx(c, wk, 0.03f, 0f, 0.06f, 0.97f, 0.06f, 0.94f)
        bx(c, wd, 0.04f, 0.06f, 0.08f, 0.96f, 0.96f, 0.92f)
        bx(c, wk, 0.02f, 0.96f, 0.05f, 0.98f, 1f, 0.96f)
        bx(c, wk, 0.08f, 0.1f, 0.92f, 0.92f, 0.42f, 0.945f); bx(c, wk, 0.08f, 0.46f, 0.92f, 0.92f, 0.92f, 0.945f)
        bx(c, mt, 0.45f, 0.4f, 0.945f, 0.55f, 0.5f, 0.975f)
    }

    private fun wardrobeM(c: Furniture.C, p: IntArray, T: IntArray) {
        val wd = p[2]; val wk = p[3]; val mt = p[4]; val gl = p[6]
        bx(c, wk, 0.02f, 0f, 0.06f, 0.98f, 0.04f, 0.94f)
        bx(c, wd, 0.03f, 0.04f, 0.08f, 0.97f, 0.97f, 0.92f)
        bx(c, wk, 0.02f, 0.97f, 0.05f, 0.98f, 1f, 0.96f)
        bx(c, wd, 0.05f, 0.06f, 0.92f, 0.52f, 0.95f, 0.945f)
        bx(c, gl, 0.48f, 0.06f, 0.945f, 0.95f, 0.95f, 0.96f)
        bx(c, mt, 0.46f, 0.4f, 0.96f, 0.5f, 0.64f, 0.985f); bx(c, mt, 0.5f, 0.4f, 0.945f, 0.54f, 0.64f, 0.97f)
    }

    private fun shelfM(c: Furniture.C, p: IntArray, T: IntArray, w: World, x: Int, y: Int, z: Int, id: Int) {
        val wd = p[2]; val mt = p[4]
        val rx = intArrayOf(1, -1, 0, 0)[c.rot]; val rz = intArrayOf(0, 0, -1, 1)[c.rot]
        val nR = w.get(x + rx, y, z + rz) == id; val nL = w.get(x - rx, y, z - rz) == id
        val a = if (nL) 0f else 0.03f; val e = if (nR) 1f else 0.97f
        if (!nL) bx(c, mt, 0.02f, 0f, 0.1f, 0.06f, 1f, 0.9f)
        if (!nR) bx(c, mt, 0.94f, 0f, 0.1f, 0.98f, 1f, 0.9f)
        bx(c, mt, a, 0f, 0.86f, e, 1f, 0.89f)
        for (k in 0..3) { val y0 = k * 0.3f; bx(c, wd, a, y0, 0.1f, e, y0 + 0.05f, 0.9f) }
        bx(c, wd, a, 0.95f, 0.1f, e, 1f, 0.9f)
        for (r in 0 until 3) {
            val y0 = r * 0.3f + 0.05f
            var px = a + 0.06f; var i = 0
            while (px < e - 0.12f) {
                val k = hh(x * 7 + i, z + r * 31, 21)
                if (k < 0.5f) { val bw = 0.05f + 0.05f * hh(x + i, z, 22 + r); val tex = T[Furniture.T_BOOK0 + (k * 12f).toInt().coerceIn(0, 5)]
                    c.box(T[Furniture.T_CREAM], tex, tex, px, y0, 0.2f, px + bw, y0 + 0.16f + 0.06f * hh(x, z + i, 23), 0.76f); px += bw + 0.015f }
                else if (k < 0.65f) { cy(c, p[0], px + 0.07f, 0.45f, 0.06f, 0.05f, y0, y0 + 0.14f, 8); px += 0.2f }
                else px += 0.1f
                i++
            }
        }
    }

    private fun cabinet(c: Furniture.C, p: IntArray, T: IntArray) {
        val wd = p[2]; val wk = p[3]; val gl = p[6]
        bx(c, wk, 0.03f, 0f, 0.08f, 0.97f, 0.12f, 0.94f)
        bx(c, wd, 0.04f, 0.12f, 0.08f, 0.96f, 0.96f, 0.14f)
        bx(c, wd, 0.04f, 0.12f, 0.08f, 0.1f, 0.96f, 0.92f); bx(c, wd, 0.9f, 0.12f, 0.08f, 0.96f, 0.96f, 0.92f)
        bx(c, wk, 0.02f, 0.96f, 0.06f, 0.98f, 1f, 0.96f)
        bx(c, wd, 0.06f, 0.12f, 0.88f, 0.94f, 0.96f, 0.915f)
        for (k in 0..2) bx(c, wk, 0.1f, 0.3f + k * 0.22f, 0.14f, 0.9f, 0.33f + k * 0.22f, 0.9f)
        for (k in 0..3) { val x0 = 0.14f + k * 0.2f; bx(c, T[Furniture.T_WHITE], x0, 0.33f, 0.4f, x0 + 0.14f, 0.5f, 0.55f) }
        for (k in 0..1) { val x0 = 0.2f + k * 0.4f; cy(c, T[Furniture.T_GOLD], x0 + 0.1f, 0.5f, 0.07f, 0.05f, 0.55f, 0.7f, 8) }
        bx(c, T[Furniture.T_GOLD], 0.46f, 0.4f, 0.915f, 0.54f, 0.5f, 0.935f)
    }

    private fun trunk(c: Furniture.C, p: IntArray, T: IntArray) {
        val wd = p[2]; val wk = p[3]; val mt = p[4]
        bx(c, wd, 0.06f, 0f, 0.1f, 0.94f, 0.38f, 0.9f)
        bx(c, wk, 0.06f, 0.12f, 0.1f, 0.94f, 0.16f, 0.9f)
        bx(c, wk, 0.04f, 0.38f, 0.08f, 0.96f, 0.46f, 0.92f)
        bx(c, wd, 0.08f, 0.46f, 0.12f, 0.92f, 0.52f, 0.88f); bx(c, wd, 0.12f, 0.52f, 0.16f, 0.88f, 0.55f, 0.84f)
        for (px in floatArrayOf(0.18f, 0.76f)) bx(c, mt, px, 0f, 0.085f, px + 0.06f, 0.55f, 0.915f)
        bx(c, T[Furniture.T_GOLD], 0.46f, 0.3f, 0.9f, 0.54f, 0.44f, 0.94f)
    }

    private fun wallCab(c: Furniture.C, p: IntArray, T: IntArray) {
        val wd = p[2]; val wk = p[3]; val mt = p[4]
        bx(c, wd, 0f, 0.42f, 0f, 1f, 1f, 0.48f)
        bx(c, wk, 0f, 0.4f, 0f, 1f, 0.44f, 0.5f); bx(c, wk, 0f, 0.98f, 0f, 1f, 1f, 0.5f)
        bx(c, wd, 0.03f, 0.46f, 0.46f, 0.49f, 0.96f, 0.5f); bx(c, wd, 0.51f, 0.46f, 0.46f, 0.97f, 0.96f, 0.5f)
        bx(c, mt, 0.44f, 0.5f, 0.5f, 0.47f, 0.74f, 0.53f); bx(c, mt, 0.53f, 0.5f, 0.5f, 0.56f, 0.74f, 0.53f)
    }

    private fun emit2(c: Furniture.C, p: IntArray, T: IntArray, w: World, x: Int, y: Int, z: Int, id: Int, s: Int) {
        when (s) {
            S_FRIDGE2 -> fridge2(c, p, T)
            S_FRIDGER -> fridgeR(c, p, T)
            S_MINI -> mini(c, p, T)
            S_STOVE -> stove(c, p, T)
            S_MICRO -> micro(c, p, T)
            S_WASHER -> washer(c, p, T)
            S_SINK -> sink(c, p, T)
            S_HOOD -> hood(c, p, T)
            S_TOILET -> toilet(c, p)
            S_BASIN -> basin(c, p, T)
            S_TUB -> tub(c, p, T)
            S_BEDM -> bedM(c, p, T, w, x, y, z, id)
            S_CRIB -> crib(c, p, T)
            S_CUROPEN, S_CURVAL -> curtainOpen(c, p, w, x, y, z, id, s == S_CURVAL)
            S_CURCLOSED -> curtainClosed(c, p, w, x, y, z, id, false)
            S_CURSHEER -> curtainClosed(c, p, w, x, y, z, id, true)
            S_ROMAN -> roman(c, p, w, x, y, z, id)
            else -> emit3(c, p, T, s)
        }
    }

    private fun emit3(c: Furniture.C, p: IntArray, T: IntArray, s: Int) {
        when (s) {
            S_TLAMP -> tableLamp(c, p)
            S_FLOOR -> floorLamp(c, p)
            S_ARC -> arcLamp(c, p)
            S_PEND -> pendant(c, p)
            S_CHAND -> chandelier(c, p, T)
            S_SCONCE -> sconce(c, p)
            S_DESKL -> deskLamp(c, p)
            S_PAPER -> paperLamp(c, p)
            S_CANDLE -> candelabra(c, p, T)
            S_MOON -> moonLight(c, p)
            S_LAVA -> lavaLamp(c, p)
            S_PALM -> palm(c, p, T)
            S_FERN -> fern(c, p, T)
            S_CACTUS -> cactus(c, p, T)
            S_FLOWERS -> flowers(c, p, T)
            S_BONSAI -> bonsai(c, p, T)
            S_PAINT -> painting(c, p)
            S_MIRROR -> mirror(c, p)
            S_CLOCK -> wallClock(c, p)
            S_GRAND -> grandClock(c, p)
            S_BOOKS -> books(c, p, T)
            S_FRUIT -> fruitBowl(c, p)
            S_GLOBE -> globe(c, p)
            S_FIRE -> fireplace(c, p)
        }
    }

    // =============== ELETRODOMÉSTICOS ===============
    private fun fridge2(c: Furniture.C, p: IntArray, T: IntArray) {
        val b = p[0]; val mt = p[4]; val bk = T[Furniture.T_BLACK]
        bx(c, bk, 0.06f, 0f, 0.1f, 0.94f, 0.04f, 0.9f)
        bx(c, b, 0.04f, 0.04f, 0.06f, 0.96f, 1f, 0.92f)
        bx(c, b, 0.06f, 0.06f, 0.92f, 0.49f, 0.98f, 0.96f); bx(c, b, 0.51f, 0.06f, 0.92f, 0.94f, 0.98f, 0.96f)
        bx(c, bk, 0.49f, 0.06f, 0.92f, 0.51f, 0.98f, 0.955f)
        bx(c, mt, 0.43f, 0.3f, 0.96f, 0.46f, 0.72f, 0.99f); bx(c, mt, 0.54f, 0.3f, 0.96f, 0.57f, 0.72f, 0.99f)
        bx(c, bk, 0.12f, 0.5f, 0.96f, 0.34f, 0.84f, 0.975f)
        gx(c, T[Furniture.T_SCREEN], 0.16f, 0.72f, 0.975f, 0.3f, 0.8f, 0.985f)
        bx(c, mt, 0.18f, 0.56f, 0.975f, 0.28f, 0.6f, 0.99f)
    }

    private fun fridgeR(c: Furniture.C, p: IntArray, T: IntArray) {
        val b = p[0]; val mt = p[4]; val bk = T[Furniture.T_BLACK]
        for (px in floatArrayOf(0.1f, 0.8f)) bx(c, mt, px, 0f, 0.14f, px + 0.1f, 0.05f, 0.24f)
        bx(c, b, 0.08f, 0.05f, 0.1f, 0.92f, 0.86f, 0.9f)
        bx(c, b, 0.12f, 0.86f, 0.12f, 0.88f, 0.93f, 0.88f); bx(c, b, 0.18f, 0.93f, 0.16f, 0.82f, 0.98f, 0.84f)
        bx(c, b, 0.1f, 0.08f, 0.9f, 0.9f, 0.64f, 0.94f); bx(c, b, 0.1f, 0.7f, 0.9f, 0.9f, 0.88f, 0.94f)
        bx(c, bk, 0.1f, 0.64f, 0.9f, 0.9f, 0.7f, 0.935f)
        bx(c, mt, 0.78f, 0.74f, 0.94f, 0.82f, 0.86f, 0.975f); bx(c, mt, 0.78f, 0.38f, 0.94f, 0.82f, 0.58f, 0.975f)
        bx(c, mt, 0.4f, 0.8f, 0.94f, 0.6f, 0.83f, 0.955f)
    }

    private fun mini(c: Furniture.C, p: IntArray, T: IntArray) {
        val b = p[0]; val mt = p[4]; val bk = T[Furniture.T_BLACK]
        bx(c, bk, 0.1f, 0f, 0.1f, 0.9f, 0.04f, 0.9f)
        bx(c, b, 0.08f, 0.04f, 0.08f, 0.92f, 0.62f, 0.92f)
        bx(c, b, 0.1f, 0.06f, 0.92f, 0.9f, 0.6f, 0.95f)
        bx(c, mt, 0.78f, 0.2f, 0.95f, 0.82f, 0.5f, 0.975f)
        bx(c, bk, 0.1f, 0.4f, 0.92f, 0.9f, 0.43f, 0.945f)
    }

    private fun stove(c: Furniture.C, p: IntArray, T: IntArray) {
        val b = p[0]; val mt = p[4]; val bk = T[Furniture.T_BLACK]
        bx(c, b, 0.02f, 0f, 0.02f, 0.98f, 0.84f, 0.98f)
        bx(c, bk, 0.02f, 0.84f, 0.02f, 0.98f, 0.9f, 0.98f)
        for (cx in floatArrayOf(0.28f, 0.72f)) for (cz in floatArrayOf(0.3f, 0.7f)) { cy(c, mt, cx, cz, 0.16f, 0.16f, 0.9f, 0.915f, 12); cy(c, bk, cx, cz, 0.11f, 0.11f, 0.915f, 0.93f, 12) }
        bx(c, b, 0.04f, 0.9f, 0.02f, 0.96f, 1f, 0.1f)
        bx(c, b, 0.04f, 0.62f, 0.98f, 0.96f, 0.84f, 1f)
        for (k in 0..3) bx(c, mt, 0.14f + k * 0.2f, 0.69f, 1f, 0.2f + k * 0.2f, 0.77f, 1.02f)
        bx(c, bk, 0.1f, 0.12f, 0.98f, 0.9f, 0.58f, 1f)
        bx(c, T[Furniture.T_SCREEN], 0.16f, 0.2f, 1f, 0.84f, 0.5f, 1.01f)
        bx(c, mt, 0.12f, 0.6f, 1.01f, 0.88f, 0.64f, 1.04f)
    }

    private fun micro(c: Furniture.C, p: IntArray, T: IntArray) {
        val b = p[0]; val mt = p[4]; val bk = T[Furniture.T_BLACK]
        bx(c, b, 0.06f, 0f, 0.14f, 0.94f, 0.5f, 0.86f)
        bx(c, bk, 0.1f, 0.06f, 0.86f, 0.64f, 0.44f, 0.875f)
        bx(c, mt, 0.66f, 0.08f, 0.875f, 0.69f, 0.42f, 0.9f)
        gx(c, T[Furniture.T_SCREEN], 0.74f, 0.34f, 0.86f, 0.9f, 0.42f, 0.875f)
        for (k in 0..2) bx(c, bk, 0.74f, 0.1f + k * 0.08f, 0.86f, 0.9f, 0.15f + k * 0.08f, 0.875f)
    }

    private fun washer(c: Furniture.C, p: IntArray, T: IntArray) {
        val b = p[0]; val mt = p[4]; val bk = T[Furniture.T_BLACK]
        bx(c, b, 0.04f, 0f, 0.04f, 0.96f, 0.9f, 0.96f)
        bx(c, mt, 0.1f, 0.78f, 0.96f, 0.9f, 0.88f, 0.975f)
        bx(c, bk, 0.14f, 0.82f, 0.975f, 0.34f, 0.86f, 0.985f); gx(c, T[Furniture.T_SCREEN], 0.7f, 0.8f, 0.975f, 0.86f, 0.86f, 0.985f)
        bx(c, mt, 0.24f, 0.14f, 0.96f, 0.76f, 0.7f, 0.975f); bx(c, mt, 0.2f, 0.2f, 0.96f, 0.8f, 0.64f, 0.975f)
        bx(c, bk, 0.28f, 0.2f, 0.975f, 0.72f, 0.64f, 0.985f); bx(c, bk, 0.24f, 0.24f, 0.975f, 0.76f, 0.6f, 0.985f)
        bx(c, p[6], 0.3f, 0.52f, 0.985f, 0.4f, 0.6f, 0.99f)
    }

    private fun sink(c: Furniture.C, p: IntArray, T: IntArray) {
        val wd = p[2]; val wk = p[3]; val mt = p[4]; val top = p[5]
        bx(c, wk, 0f, 0f, 0.06f, 1f, 0.1f, 0.94f)
        bx(c, wd, 0f, 0.1f, 0.08f, 1f, 0.84f, 0.92f)
        bx(c, wk, 0.04f, 0.14f, 0.92f, 0.48f, 0.8f, 0.945f); bx(c, wk, 0.52f, 0.14f, 0.92f, 0.96f, 0.8f, 0.945f)
        bx(c, mt, 0.43f, 0.5f, 0.945f, 0.46f, 0.7f, 0.975f); bx(c, mt, 0.54f, 0.5f, 0.945f, 0.57f, 0.7f, 0.975f)
        c.box(top, top, top, 0f, 0.84f, 0f, 0.26f, 0.94f, 1f); c.box(top, top, top, 0.74f, 0.84f, 0f, 1f, 0.94f, 1f)
        c.box(top, top, top, 0.26f, 0.84f, 0f, 0.74f, 0.94f, 0.26f); c.box(top, top, top, 0.26f, 0.84f, 0.74f, 0.74f, 0.94f, 1f)
        bx(c, mt, 0.26f, 0.78f, 0.26f, 0.74f, 0.84f, 0.74f)
        bx(c, T[Furniture.T_BLACK], 0.47f, 0.84f, 0.47f, 0.53f, 0.85f, 0.53f)
        cy(c, mt, 0.5f, 0.12f, 0.03f, 0.03f, 0.94f, 0.99f, 6)
        bx(c, mt, 0.47f, 0.97f, 0.1f, 0.53f, 0.99f, 0.36f); bx(c, mt, 0.47f, 0.93f, 0.33f, 0.53f, 0.99f, 0.36f)
    }

    private fun hood(c: Furniture.C, p: IntArray, T: IntArray) {
        val b = p[0]; val mt = p[4]
        bx(c, b, 0.02f, 0.7f, 0f, 0.98f, 0.8f, 0.7f)
        bx(c, b, 0.1f, 0.8f, 0f, 0.9f, 0.9f, 0.5f)
        bx(c, b, 0.3f, 0.9f, 0f, 0.7f, 1f, 0.34f)
        bx(c, mt, 0.02f, 0.68f, 0.02f, 0.98f, 0.7f, 0.68f)
        gx(c, p[6], 0.2f, 0.67f, 0.3f, 0.8f, 0.69f, 0.5f)
        bx(c, mt, 0.4f, 0.74f, 0.7f, 0.6f, 0.78f, 0.72f)
    }

    // =============== BANHEIRO ===============
    private fun toilet(c: Furniture.C, p: IntArray) {
        val b = p[0]; val l = p[1]; val mt = p[4]
        cy(c, b, 0.5f, 0.6f, 0.14f, 0.2f, 0f, 0.22f, 12)
        cy(c, b, 0.5f, 0.6f, 0.27f, 0.22f, 0.22f, 0.4f, 14)
        cy(c, l, 0.5f, 0.6f, 0.3f, 0.3f, 0.4f, 0.44f, 14)
        bx(c, l, 0.22f, 0.44f, 0.22f, 0.78f, 0.48f, 0.4f)
        bx(c, b, 0.2f, 0.36f, 0.05f, 0.8f, 0.76f, 0.3f)
        bx(c, l, 0.18f, 0.76f, 0.04f, 0.82f, 0.8f, 0.32f)
        cy(c, mt, 0.5f, 0.17f, 0.04f, 0.04f, 0.8f, 0.83f, 6)
    }

    private fun basin(c: Furniture.C, p: IntArray, T: IntArray) {
        val b = p[0]; val d = p[1]; val mt = p[4]
        cy(c, b, 0.5f, 0.5f, 0.15f, 0.12f, 0f, 0.62f, 10)
        bx(c, b, 0.1f, 0.62f, 0.12f, 0.2f, 0.8f, 0.88f); bx(c, b, 0.8f, 0.62f, 0.12f, 0.9f, 0.8f, 0.88f)
        bx(c, b, 0.2f, 0.62f, 0.12f, 0.8f, 0.8f, 0.24f); bx(c, b, 0.2f, 0.62f, 0.76f, 0.8f, 0.8f, 0.88f)
        bx(c, d, 0.2f, 0.62f, 0.24f, 0.8f, 0.7f, 0.76f)
        cy(c, mt, 0.5f, 0.17f, 0.03f, 0.03f, 0.8f, 0.9f, 6)
        bx(c, mt, 0.47f, 0.88f, 0.17f, 0.53f, 0.91f, 0.34f)
    }

    private fun tub(c: Furniture.C, p: IntArray, T: IntArray) {
        val b = p[0]; val mt = p[4]; val wt = p[7]
        for (px in floatArrayOf(0.04f, 0.84f)) for (pz in floatArrayOf(0.04f, 0.84f)) cy(c, mt, px + 0.06f, pz + 0.06f, 0.06f, 0.05f, 0f, 0.1f, 8)
        bx(c, b, 0f, 0.1f, 0f, 1f, 0.2f, 1f)
        bx(c, b, 0f, 0.2f, 0f, 0.14f, 0.5f, 1f); bx(c, b, 0.86f, 0.2f, 0f, 1f, 0.5f, 1f)
        bx(c, b, 0.14f, 0.2f, 0f, 0.86f, 0.5f, 0.14f); bx(c, b, 0.14f, 0.2f, 0.86f, 0.86f, 0.5f, 1f)
        bx(c, wt, 0.14f, 0.2f, 0.14f, 0.86f, 0.4f, 0.86f)
        for (k in 0..3) bx(c, p[6], 0.2f + 0.14f * k, 0.4f, 0.3f + 0.1f * (k and 1) + 0.15f * (k / 2), 0.28f + 0.14f * k, 0.42f, 0.38f + 0.1f * (k and 1) + 0.15f * (k / 2))
        cy(c, mt, 0.5f, 0.07f, 0.03f, 0.03f, 0.5f, 0.78f, 6)
        bx(c, mt, 0.47f, 0.75f, 0.07f, 0.53f, 0.78f, 0.3f)
    }

    // =============== CAMA ===============
    private fun bedM(c: Furniture.C, p: IntArray, T: IntArray, w: World, x: Int, y: Int, z: Int, id: Int) {
        val zAxis = w.get(x, y, z - 1) == id || w.get(x, y, z + 1) == id || (w.get(x - 1, y, z) != id && w.get(x + 1, y, z) != id)
        val nH = if (zAxis) w.get(x, y, z - 1) == id else w.get(x - 1, y, z) == id
        val nF = if (zAxis) w.get(x, y, z + 1) == id else w.get(x + 1, y, z) == id
        c.rot = if (zAxis) 0 else 2
        val duv = p[0]; val head = p[1]; val wd = p[2]; val wk = p[3]; val gd = p[4]
        val sheet = T[Furniture.T_WHITE]; val pil = T[Furniture.T_CREAM]
        val e0 = if (nH) 0f else 0.02f; val e1 = if (nF) 1f else 0.98f
        bx(c, wk, 0.02f, 0.14f, e0, 0.98f, 0.34f, e1)
        if (!nH) { bx(c, wk, 0.02f, 0f, 0.02f, 0.1f, 0.14f, 0.1f); bx(c, wk, 0.9f, 0f, 0.02f, 0.98f, 0.14f, 0.1f) }
        if (!nF) { bx(c, wk, 0.02f, 0f, 0.9f, 0.1f, 0.14f, 0.98f); bx(c, wk, 0.9f, 0f, 0.9f, 0.98f, 0.14f, 0.98f) }
        bx(c, sheet, 0.05f, 0.34f, if (nH) 0f else 0.08f, 0.95f, 0.46f, if (nF) 1f else 0.92f)
        bx(c, duv, 0.02f, 0.4f, if (nH) 0f else 0.36f, 0.98f, 0.56f, if (nF) 1f else 0.94f)
        bx(c, head, 0.02f, 0.5f, if (nH) 0f else 0.36f, 0.98f, 0.58f, if (nH) 0.1f else 0.46f)
        if (!nF) bx(c, head, 0.02f, 0.4f, 0.9f, 0.98f, 0.44f, 0.96f)
        if (!nH) {
            bx(c, pil, 0.1f, 0.46f, 0.12f, 0.46f, 0.62f, 0.34f); bx(c, pil, 0.54f, 0.46f, 0.12f, 0.9f, 0.62f, 0.34f)
            bx(c, head, 0.16f, 0.52f, 0.2f, 0.4f, 0.66f, 0.3f)
            bx(c, wk, 0f, 0.14f, 0f, 1f, 1f, 0.07f)
            bx(c, head, 0.05f, 0.34f, 0.07f, 0.95f, 0.94f, 0.11f)
            bx(c, gd, 0f, 0.94f, 0f, 1f, 1f, 0.12f)
            for (k in 0..3) bx(c, gd, 0.2f + k * 0.2f, 0.52f, 0.11f, 0.24f + k * 0.2f, 0.56f, 0.125f)
        }
        if (!nF) bx(c, wd, 0.02f, 0.14f, 0.94f, 0.98f, 0.45f, 1f)
    }

    private fun crib(c: Furniture.C, p: IntArray, T: IntArray) {
        val wd = p[2]; val wk = p[3]; val b = p[0]
        for (px in floatArrayOf(0.04f, 0.9f)) for (pz in floatArrayOf(0.04f, 0.9f)) bx(c, wk, px, 0f, pz, px + 0.06f, 0.85f, pz + 0.06f)
        bx(c, wd, 0.06f, 0.24f, 0.06f, 0.94f, 0.3f, 0.94f)
        bx(c, T[Furniture.T_WHITE], 0.08f, 0.3f, 0.08f, 0.92f, 0.38f, 0.92f)
        bx(c, b, 0.1f, 0.38f, 0.3f, 0.9f, 0.43f, 0.92f)
        bx(c, T[Furniture.T_CREAM], 0.14f, 0.38f, 0.1f, 0.5f, 0.46f, 0.28f)
        for (k in 0 until 6) { val u = 0.14f + k * 0.14f; bx(c, wd, u, 0.3f, 0.045f, u + 0.03f, 0.8f, 0.075f); bx(c, wd, u, 0.3f, 0.925f, u + 0.03f, 0.8f, 0.955f) }
        for (k in 0 until 6) { val u = 0.14f + k * 0.14f; bx(c, wd, 0.045f, 0.3f, u, 0.075f, 0.8f, u + 0.03f); bx(c, wd, 0.925f, 0.3f, u, 0.955f, 0.8f, u + 0.03f) }
        bx(c, wk, 0.04f, 0.8f, 0.04f, 0.96f, 0.85f, 0.1f); bx(c, wk, 0.04f, 0.8f, 0.9f, 0.96f, 0.85f, 0.96f)
        bx(c, wk, 0.04f, 0.8f, 0.04f, 0.1f, 0.85f, 0.96f); bx(c, wk, 0.9f, 0.8f, 0.04f, 0.96f, 0.85f, 0.96f)
        bx(c, p[4], 0.495f, 0.55f, 0.495f, 0.505f, 0.99f, 0.505f)
        bx(c, p[5], 0.38f, 0.6f, 0.4f, 0.46f, 0.68f, 0.48f); bx(c, p[1], 0.54f, 0.58f, 0.52f, 0.62f, 0.66f, 0.6f); bx(c, p[6], 0.46f, 0.64f, 0.58f, 0.54f, 0.72f, 0.66f)
    }

    // =============== CORTINAS ===============
    private fun neighbors(c: Furniture.C, w: World, x: Int, y: Int, z: Int, id: Int): BooleanArray {
        val rx = intArrayOf(1, -1, 0, 0)[c.rot]; val rz = intArrayOf(0, 0, -1, 1)[c.rot]
        return booleanArrayOf(w.get(x - rx, y, z - rz) == id, w.get(x + rx, y, z + rz) == id, w.get(x, y + 1, z) == id, w.get(x, y - 1, z) == id)
    }

    private fun panel(c: Furniture.C, t: Int, hem: Int, x0: Float, x1: Float, n: Int, y0: Float, y1: Float) {
        for (i in 0 until n) {
            val xa = x0 + (x1 - x0) * i / n; val xb = x0 + (x1 - x0) * (i + 1) / n
            val d = if ((i and 1) == 0) 0.17f else 0.07f
            bx(c, t, xa, y0, 0.06f, xb, y1, 0.06f + d)
        }
        if (y0 > 0.01f) bx(c, hem, x0, y0, 0.06f, x1, y0 + 0.05f, 0.25f)
    }

    /** cortinas empilhadas na vertical se emendam: só o bloco de cima tem varão e só o de baixo tem barra */
    private fun rod(c: Furniture.C, p: IntArray, nb: BooleanArray) {
        if (nb[2]) return
        val mt = p[4]
        bx(c, mt, 0f, 0.93f, 0.03f, 1f, 0.97f, 0.08f)
        if (!nb[0]) bx(c, p[5], 0f, 0.9f, 0.02f, 0.06f, 1f, 0.1f)
        if (!nb[1]) bx(c, p[5], 0.94f, 0.9f, 0.02f, 1f, 1f, 0.1f)
    }

    private fun curtainOpen(c: Furniture.C, p: IntArray, w: World, x: Int, y: Int, z: Int, id: Int, valance: Boolean) {
        val nb = neighbors(c, w, x, y, z, id); val nL = nb[0]; val nR = nb[1]; val up = nb[2]; val dn = nb[3]
        val b = p[0]; val h = p[1]
        val y0 = if (dn) 0f else 0.04f; val y1 = if (up) 1f else if (valance) 0.76f else 0.92f
        rod(c, p, nb)
        if (!nL) { panel(c, b, h, 0f, 0.3f, 4, y0, y1); if (!dn) bx(c, p[5], 0f, 0.44f, 0.05f, 0.3f, 0.5f, 0.26f) }
        if (!nR) { panel(c, b, h, 0.7f, 1f, 4, y0, y1); if (!dn) bx(c, p[5], 0.7f, 0.44f, 0.05f, 1f, 0.5f, 0.26f) }
        if (valance && !up) {
            bx(c, h, 0f, 0.8f, 0.02f, 1f, 0.97f, 0.26f)
            for (i in 0 until 4) bx(c, h, i * 0.25f + 0.015f, 0.72f, 0.03f, i * 0.25f + 0.235f, 0.8f, 0.24f)
            for (i in 0 until 4) bx(c, p[5], i * 0.25f + 0.1f, 0.66f, 0.1f, i * 0.25f + 0.15f, 0.72f, 0.15f)
        }
    }

    private fun curtainClosed(c: Furniture.C, p: IntArray, w: World, x: Int, y: Int, z: Int, id: Int, sheer: Boolean) {
        val nb = neighbors(c, w, x, y, z, id); val up = nb[2]; val dn = nb[3]
        val b = p[0]; val h = p[1]
        val y0 = if (dn) 0f else 0.1f; val y1 = if (up) 1f else 0.92f
        rod(c, p, nb)
        if (sheer) {
            for (i in 0 until 8) { val xa = i / 8f; bx(c, b, xa, y0, 0.05f, xa + 0.125f, y1, 0.05f + (if ((i and 1) == 0) 0.09f else 0.05f)) }
            if (!dn) {
                bx(c, h, 0f, 0.06f, 0.05f, 1f, 0.14f, 0.15f)
                for (i in 0 until 8) bx(c, h, i / 8f + 0.03f, 0.03f, 0.06f, i / 8f + 0.095f, 0.06f, 0.14f)
            }
        } else panel(c, b, h, 0f, 1f, 8, if (dn) 0f else 0.05f, y1)
    }

    private fun roman(c: Furniture.C, p: IntArray, w: World, x: Int, y: Int, z: Int, id: Int) {
        val nb = neighbors(c, w, x, y, z, id); val up = nb[2]; val dn = nb[3]
        val b = p[0]; val wd = p[2]
        if (!up) bx(c, wd, 0f, 0.92f, 0.02f, 1f, 1f, 0.14f)
        val yTop = if (up) 1f else 0.92f; val yBot = if (dn) 0f else 0.1f
        var k = 0
        var y0 = yBot
        while (y0 < yTop - 0.01f) {
            val y1 = min(y0 + 0.13f, yTop)
            bx(c, b, 0.03f, y0, 0.04f, 0.97f, y1, if ((k and 1) == 0) 0.09f else 0.14f)
            y0 += 0.16f; k++
        }
        if (!dn) bx(c, wd, 0.02f, 0.06f, 0.04f, 0.98f, 0.1f, 0.14f)
        bx(c, p[5], 0.88f, yBot, 0.14f, 0.9f, yTop, 0.16f)
        if (!dn) bx(c, p[5], 0.86f, 0.06f, 0.14f, 0.92f, 0.12f, 0.2f)
    }

    // =============== LÂMPADAS ===============
    private fun tableLamp(c: Furniture.C, p: IntArray) {
        val bs = p[2]; val mt = p[4]
        cy(c, bs, 0.5f, 0.5f, 0.2f, 0.14f, 0f, 0.06f, 12)
        cy(c, mt, 0.5f, 0.5f, 0.03f, 0.03f, 0.06f, 0.4f, 8)
        cy(c, bs, 0.5f, 0.5f, 0.07f, 0.05f, 0.18f, 0.28f, 8)
        gy(c, p[6], 0.5f, 0.5f, 0.3f, 0.18f, 0.38f, 0.7f, 14)
        bx(c, mt, 0.45f, 0.7f, 0.45f, 0.55f, 0.72f, 0.55f)
    }

    private fun floorLamp(c: Furniture.C, p: IntArray) {
        val mt = p[4]
        cy(c, mt, 0.5f, 0.5f, 0.24f, 0.22f, 0f, 0.04f, 14)
        cy(c, mt, 0.5f, 0.5f, 0.025f, 0.025f, 0.04f, 0.78f, 8)
        gy(c, p[6], 0.5f, 0.5f, 0.27f, 0.17f, 0.62f, 0.96f, 14)
        bx(c, mt, 0.46f, 0.96f, 0.46f, 0.54f, 0.99f, 0.54f)
    }

    private fun arcLamp(c: Furniture.C, p: IntArray) {
        val mt = p[4]; val bs = p[2]
        bx(c, bs, 0.06f, 0f, 0.36f, 0.34f, 0.07f, 0.64f)
        bx(c, mt, 0.18f, 0.07f, 0.47f, 0.22f, 0.92f, 0.53f)
        bx(c, mt, 0.18f, 0.9f, 0.47f, 0.72f, 0.95f, 0.53f)
        bx(c, mt, 0.7f, 0.74f, 0.495f, 0.73f, 0.92f, 0.505f)
        gy(c, p[6], 0.72f, 0.5f, 0.2f, 0.1f, 0.6f, 0.78f, 12)
    }

    private fun pendant(c: Furniture.C, p: IntArray) {
        val mt = p[4]
        cy(c, mt, 0.5f, 0.5f, 0.015f, 0.015f, 0.7f, 1f, 6)
        cy(c, mt, 0.5f, 0.5f, 0.05f, 0.05f, 0.7f, 0.76f, 8)
        cy(c, p[2], 0.5f, 0.5f, 0.33f, 0.14f, 0.44f, 0.72f, 14)
        gy(c, p[6], 0.5f, 0.5f, 0.1f, 0.1f, 0.38f, 0.45f, 10)
    }

    private fun chandelier(c: Furniture.C, p: IntArray, T: IntArray) {
        val mt = p[4]; val cr = T[Furniture.T_CREAM]
        cy(c, mt, 0.5f, 0.5f, 0.015f, 0.015f, 0.8f, 1f, 6)
        cy(c, mt, 0.5f, 0.5f, 0.09f, 0.07f, 0.62f, 0.8f, 10)
        bx(c, mt, 0.14f, 0.62f, 0.47f, 0.86f, 0.66f, 0.53f); bx(c, mt, 0.47f, 0.62f, 0.14f, 0.53f, 0.66f, 0.86f)
        val pts = arrayOf(floatArrayOf(0.14f, 0.5f), floatArrayOf(0.86f, 0.5f), floatArrayOf(0.5f, 0.14f), floatArrayOf(0.5f, 0.86f), floatArrayOf(0.5f, 0.5f))
        for (q in pts) {
            val tip = q[0] != 0.5f || q[1] != 0.5f
            val y0 = if (tip) 0.66f else 0.8f
            cy(c, cr, q[0], q[1], 0.03f, 0.03f, y0, y0 + 0.12f, 6)
            gx(c, p[6], q[0] - 0.02f, y0 + 0.12f, q[1] - 0.02f, q[0] + 0.02f, y0 + 0.18f, q[1] + 0.02f)
            if (tip) { bx(c, p[1], q[0] - 0.015f, 0.5f, q[1] - 0.015f, q[0] + 0.015f, 0.62f, q[1] + 0.015f); bx(c, p[1], q[0] - 0.03f, 0.46f, q[1] - 0.03f, q[0] + 0.03f, 0.5f, q[1] + 0.03f) }
        }
    }

    private fun sconce(c: Furniture.C, p: IntArray) {
        val mt = p[4]
        bx(c, mt, 0.38f, 0.5f, 0f, 0.62f, 0.9f, 0.04f)
        bx(c, mt, 0.46f, 0.6f, 0.04f, 0.54f, 0.66f, 0.2f)
        gy(c, p[6], 0.5f, 0.22f, 0.14f, 0.09f, 0.58f, 0.86f, 10)
        bx(c, mt, 0.46f, 0.86f, 0.18f, 0.54f, 0.88f, 0.26f)
    }

    private fun deskLamp(c: Furniture.C, p: IntArray) {
        val mt = p[4]
        bx(c, mt, 0.3f, 0f, 0.3f, 0.7f, 0.04f, 0.7f)
        bx(c, mt, 0.46f, 0.04f, 0.46f, 0.5f, 0.4f, 0.5f)
        bx(c, mt, 0.46f, 0.38f, 0.4f, 0.5f, 0.42f, 0.62f)
        bx(c, mt, 0.46f, 0.42f, 0.58f, 0.5f, 0.6f, 0.62f)
        bx(c, p[5], 0.4f, 0.54f, 0.6f, 0.56f, 0.62f, 0.84f)
        gx(c, p[6], 0.41f, 0.52f, 0.62f, 0.55f, 0.54f, 0.82f)
    }

    private fun paperLamp(c: Furniture.C, p: IntArray) {
        val dk = p[3]
        cy(c, dk, 0.5f, 0.5f, 0.012f, 0.012f, 0.82f, 1f, 6)
        cy(c, dk, 0.5f, 0.5f, 0.12f, 0.12f, 0.8f, 0.83f, 10)
        gy(c, p[6], 0.5f, 0.5f, 0.16f, 0.26f, 0.36f, 0.46f, 12)
        gy(c, p[6], 0.5f, 0.5f, 0.26f, 0.3f, 0.46f, 0.58f, 12)
        gy(c, p[6], 0.5f, 0.5f, 0.3f, 0.26f, 0.58f, 0.7f, 12)
        gy(c, p[6], 0.5f, 0.5f, 0.26f, 0.14f, 0.7f, 0.8f, 12)
        cy(c, dk, 0.5f, 0.5f, 0.14f, 0.14f, 0.34f, 0.37f, 10)
        cy(c, dk, 0.5f, 0.5f, 0.305f, 0.305f, 0.575f, 0.585f, 12)
        bx(c, p[5], 0.485f, 0.2f, 0.485f, 0.515f, 0.34f, 0.515f)
    }

    private fun candelabra(c: Furniture.C, p: IntArray, T: IntArray) {
        val mt = p[4]; val cr = T[Furniture.T_CREAM]
        cy(c, mt, 0.5f, 0.5f, 0.2f, 0.14f, 0f, 0.05f, 12)
        cy(c, mt, 0.5f, 0.5f, 0.03f, 0.03f, 0.05f, 0.28f, 8)
        bx(c, mt, 0.2f, 0.26f, 0.47f, 0.8f, 0.29f, 0.53f); bx(c, mt, 0.2f, 0.29f, 0.47f, 0.24f, 0.34f, 0.53f); bx(c, mt, 0.76f, 0.29f, 0.47f, 0.8f, 0.34f, 0.53f)
        for (k in 0..2) {
            val cx = 0.2f + k * 0.3f; val top = if (k == 1) 0.56f else 0.5f; val y0 = if (k == 1) 0.29f else 0.29f
            cy(c, cr, cx, 0.5f, 0.04f, 0.04f, y0, top, 8)
            gx(c, p[6], cx - 0.02f, top, 0.48f, cx + 0.02f, top + 0.07f, 0.52f)
        }
    }

    private fun moonLight(c: Furniture.C, p: IntArray) {
        val mt = p[4]; val l = p[6]
        bx(c, mt, 0.3f, 0f, 0.4f, 0.7f, 0.05f, 0.6f)
        gx(c, l, 0.3f, 0.12f, 0.46f, 0.42f, 0.6f, 0.54f); gx(c, l, 0.3f, 0.5f, 0.46f, 0.62f, 0.6f, 0.54f); gx(c, l, 0.3f, 0.12f, 0.46f, 0.62f, 0.22f, 0.54f)
        gx(c, l, 0.52f, 0.4f, 0.46f, 0.62f, 0.6f, 0.54f); gx(c, l, 0.52f, 0.12f, 0.46f, 0.62f, 0.32f, 0.54f)
        bx(c, mt, 0.44f, 0.05f, 0.47f, 0.48f, 0.12f, 0.53f); bx(c, mt, 0.52f, 0.05f, 0.47f, 0.56f, 0.12f, 0.53f)
        gx(c, p[5], 0.66f, 0.46f, 0.47f, 0.74f, 0.54f, 0.53f)
    }

    private fun lavaLamp(c: Furniture.C, p: IntArray) {
        val mt = p[4]
        cy(c, mt, 0.5f, 0.5f, 0.22f, 0.14f, 0f, 0.14f, 12)
        gy(c, p[6], 0.5f, 0.5f, 0.14f, 0.08f, 0.14f, 0.7f, 10)
        gx(c, p[5], 0.44f, 0.2f, 0.44f, 0.56f, 0.3f, 0.56f); gx(c, p[5], 0.46f, 0.38f, 0.46f, 0.54f, 0.46f, 0.54f); gx(c, p[5], 0.45f, 0.54f, 0.45f, 0.55f, 0.62f, 0.55f)
        cy(c, mt, 0.5f, 0.5f, 0.09f, 0.05f, 0.7f, 0.78f, 10)
    }

    // =============== DECORAÇÃO ===============
    private fun leaves(c: Furniture.C, t1: Int, t2: Int, n: Int, by: Float, rMid: Float, yMid: Float, rTip: Float, yTip: Float, wid: Float, off: Float) {
        for (k in 0 until n) {
            val a = (k + off) * 6.2831853f / n
            val ca = cos(a); val sa = sin(a)
            c.leaf(if (k % 2 == 0) t1 else t2, 0.5f, by, 0.5f, 0.5f + ca * rMid, yMid, 0.5f + sa * rMid, -sa * wid, ca * wid, 0.5f + ca * rTip, yTip, 0.5f + sa * rTip, 0.95f)
        }
    }

    private fun palm(c: Furniture.C, p: IntArray, T: IntArray) {
        val pot = p[2]
        cy(c, pot, 0.5f, 0.5f, 0.2f, 0.28f, 0f, 0.28f, 12); cy(c, pot, 0.5f, 0.5f, 0.3f, 0.3f, 0.28f, 0.33f, 12)
        cy(c, T[Furniture.T_SOIL], 0.5f, 0.5f, 0.25f, 0.25f, 0.33f, 0.35f, 12)
        cy(c, p[3], 0.5f, 0.5f, 0.05f, 0.035f, 0.35f, 0.7f, 6)
        leaves(c, p[0], p[1], 8, 0.7f, 0.2f, 0.86f, 0.42f, 0.66f, 0.09f, 0.3f)
        leaves(c, p[1], p[0], 4, 0.7f, 0.08f, 0.9f, 0.12f, 1f, 0.07f, 0.5f)
    }

    private fun fern(c: Furniture.C, p: IntArray, T: IntArray) {
        val pot = p[2]
        cy(c, pot, 0.5f, 0.5f, 0.17f, 0.25f, 0f, 0.22f, 12); cy(c, pot, 0.5f, 0.5f, 0.27f, 0.27f, 0.22f, 0.27f, 12)
        cy(c, T[Furniture.T_SOIL], 0.5f, 0.5f, 0.22f, 0.22f, 0.27f, 0.29f, 12)
        leaves(c, p[0], p[1], 12, 0.29f, 0.2f, 0.52f, 0.4f, 0.4f, 0.07f, 0.2f)
        leaves(c, p[1], p[0], 6, 0.29f, 0.1f, 0.58f, 0.2f, 0.66f, 0.06f, 0.6f)
    }

    private fun cactus(c: Furniture.C, p: IntArray, T: IntArray) {
        val pot = p[2]; val g = p[0]
        cy(c, pot, 0.5f, 0.5f, 0.17f, 0.25f, 0f, 0.22f, 12); cy(c, pot, 0.5f, 0.5f, 0.27f, 0.27f, 0.22f, 0.27f, 12)
        cy(c, T[Furniture.T_SOIL], 0.5f, 0.5f, 0.22f, 0.22f, 0.27f, 0.29f, 12)
        cy(c, g, 0.5f, 0.5f, 0.14f, 0.12f, 0.29f, 0.7f, 10); cy(c, g, 0.5f, 0.5f, 0.12f, 0.05f, 0.7f, 0.78f, 10)
        bx(c, g, 0.3f, 0.42f, 0.44f, 0.4f, 0.5f, 0.56f); bx(c, g, 0.3f, 0.42f, 0.44f, 0.38f, 0.62f, 0.56f)
        bx(c, g, 0.6f, 0.5f, 0.44f, 0.7f, 0.58f, 0.56f); bx(c, g, 0.62f, 0.5f, 0.44f, 0.7f, 0.7f, 0.56f)
        bx(c, p[5], 0.46f, 0.78f, 0.46f, 0.54f, 0.84f, 0.54f)
    }

    private fun flowers(c: Furniture.C, p: IntArray, T: IntArray) {
        cy(c, p[2], 0.5f, 0.5f, 0.12f, 0.18f, 0f, 0.28f, 10)
        val xs = floatArrayOf(0.5f, 0.38f, 0.62f, 0.44f, 0.58f); val zs = floatArrayOf(0.5f, 0.46f, 0.46f, 0.58f, 0.58f); val hs = floatArrayOf(0.56f, 0.5f, 0.5f, 0.46f, 0.46f)
        for (i in 0 until 5) {
            cy(c, T[Furniture.T_LEAFA], xs[i], zs[i], 0.012f, 0.012f, 0.28f, hs[i], 5)
            bx(c, if (i % 2 == 0) p[5] else p[1], xs[i] - 0.05f, hs[i], zs[i] - 0.05f, xs[i] + 0.05f, hs[i] + 0.07f, zs[i] + 0.05f)
            bx(c, p[6], xs[i] - 0.015f, hs[i] + 0.07f, zs[i] - 0.015f, xs[i] + 0.015f, hs[i] + 0.085f, zs[i] + 0.015f)
        }
    }

    private fun bonsai(c: Furniture.C, p: IntArray, T: IntArray) {
        bx(c, p[2], 0.1f, 0f, 0.2f, 0.9f, 0.06f, 0.8f)
        bx(c, T[Furniture.T_SOIL], 0.14f, 0.06f, 0.24f, 0.86f, 0.09f, 0.76f)
        bx(c, T[Furniture.T_STEEL], 0.6f, 0.09f, 0.4f, 0.78f, 0.15f, 0.58f)
        bx(c, p[3], 0.44f, 0.09f, 0.46f, 0.54f, 0.28f, 0.54f); bx(c, p[3], 0.4f, 0.26f, 0.44f, 0.5f, 0.38f, 0.52f)
        bx(c, p[0], 0.2f, 0.34f, 0.3f, 0.5f, 0.44f, 0.6f); bx(c, p[1], 0.38f, 0.42f, 0.38f, 0.72f, 0.5f, 0.7f); bx(c, p[0], 0.3f, 0.46f, 0.34f, 0.6f, 0.52f, 0.62f)
    }

    private fun painting(c: Furniture.C, p: IntArray) {
        val fr = p[2]; val art = p[0]
        bx(c, fr, 0.08f, 0.1f, 0f, 0.92f, 0.17f, 0.07f); bx(c, fr, 0.08f, 0.83f, 0f, 0.92f, 0.9f, 0.07f)
        bx(c, fr, 0.08f, 0.17f, 0f, 0.15f, 0.83f, 0.07f); bx(c, fr, 0.85f, 0.17f, 0f, 0.92f, 0.83f, 0.07f)
        bx(c, art, 0.15f, 0.17f, 0.01f, 0.85f, 0.83f, 0.05f)
    }

    private fun mirror(c: Furniture.C, p: IntArray) {
        val fr = p[2]; val gl = p[6]
        bx(c, fr, 0.1f, 0.04f, 0f, 0.9f, 0.12f, 0.07f); bx(c, fr, 0.1f, 0.88f, 0f, 0.9f, 0.96f, 0.07f)
        bx(c, fr, 0.1f, 0.12f, 0f, 0.18f, 0.88f, 0.07f); bx(c, fr, 0.82f, 0.12f, 0f, 0.9f, 0.88f, 0.07f)
        bx(c, gl, 0.18f, 0.12f, 0.01f, 0.82f, 0.88f, 0.05f)
        bx(c, p[4], 0.44f, 0.96f, 0f, 0.56f, 1f, 0.07f)
    }

    private fun wallClock(c: Furniture.C, p: IntArray) {
        val fr = p[2]; val dk = p[3]
        bx(c, fr, 0.12f, 0.14f, 0f, 0.88f, 0.9f, 0.06f)
        bx(c, p[6], 0.17f, 0.19f, 0.05f, 0.83f, 0.85f, 0.07f)
        bx(c, dk, 0.48f, 0.22f, 0.07f, 0.52f, 0.28f, 0.08f); bx(c, dk, 0.48f, 0.76f, 0.07f, 0.52f, 0.82f, 0.08f)
        bx(c, dk, 0.2f, 0.5f, 0.07f, 0.26f, 0.54f, 0.08f); bx(c, dk, 0.74f, 0.5f, 0.07f, 0.8f, 0.54f, 0.08f)
        bx(c, dk, 0.49f, 0.52f, 0.075f, 0.51f, 0.74f, 0.09f); bx(c, dk, 0.5f, 0.5f, 0.075f, 0.7f, 0.52f, 0.095f)
        bx(c, p[4], 0.47f, 0.49f, 0.09f, 0.53f, 0.55f, 0.1f)
    }

    private fun grandClock(c: Furniture.C, p: IntArray) {
        val wd = p[2]; val dk = p[3]; val mt = p[4]
        bx(c, wd, 0.1f, 0f, 0.1f, 0.9f, 0.14f, 0.9f)
        bx(c, wd, 0.18f, 0.14f, 0.18f, 0.24f, 0.62f, 0.82f); bx(c, wd, 0.76f, 0.14f, 0.18f, 0.82f, 0.62f, 0.82f)
        bx(c, dk, 0.24f, 0.14f, 0.18f, 0.76f, 0.62f, 0.24f)
        bx(c, mt, 0.485f, 0.22f, 0.4f, 0.515f, 0.58f, 0.43f); cy(c, mt, 0.5f, 0.415f, 0.08f, 0.08f, 0.16f, 0.24f, 10)
        bx(c, wd, 0.14f, 0.62f, 0.14f, 0.86f, 0.88f, 0.86f)
        bx(c, p[6], 0.24f, 0.66f, 0.86f, 0.76f, 0.84f, 0.875f)
        bx(c, dk, 0.495f, 0.74f, 0.875f, 0.505f, 0.82f, 0.885f); bx(c, dk, 0.5f, 0.74f, 0.875f, 0.68f, 0.76f, 0.885f)
        bx(c, wd, 0.2f, 0.88f, 0.2f, 0.8f, 0.94f, 0.8f); bx(c, wd, 0.32f, 0.94f, 0.32f, 0.68f, 1f, 0.68f)
        bx(c, mt, 0.46f, 1f, 0.46f, 0.54f, 1.02f, 0.54f)
    }

    private fun books(c: Furniture.C, p: IntArray, T: IntArray) {
        val cr = T[Furniture.T_CREAM]
        c.box(p[0], cr, cr, 0.15f, 0f, 0.2f, 0.85f, 0.08f, 0.8f)
        c.box(p[1], cr, cr, 0.2f, 0.08f, 0.24f, 0.8f, 0.15f, 0.76f)
        c.box(p[5], cr, cr, 0.26f, 0.15f, 0.28f, 0.74f, 0.21f, 0.72f)
        bx(c, p[2], 0.34f, 0.21f, 0.36f, 0.5f, 0.24f, 0.46f)
    }

    private fun fruitBowl(c: Furniture.C, p: IntArray) {
        c.cyl(p[2], p[2], p[2], 0.5f, 0.5f, 0.18f, 0.32f, 0f, 0.14f, 14)
        bx(c, p[0], 0.3f, 0.12f, 0.38f, 0.46f, 0.26f, 0.54f); bx(c, p[1], 0.5f, 0.12f, 0.4f, 0.66f, 0.26f, 0.56f)
        bx(c, p[5], 0.4f, 0.12f, 0.54f, 0.58f, 0.25f, 0.7f); bx(c, p[6], 0.38f, 0.24f, 0.44f, 0.54f, 0.3f, 0.58f)
        bx(c, p[3], 0.45f, 0.29f, 0.48f, 0.47f, 0.33f, 0.5f)
    }

    private fun globe(c: Furniture.C, p: IntArray) {
        val mt = p[4]
        cy(c, mt, 0.5f, 0.5f, 0.2f, 0.16f, 0f, 0.05f, 10)
        bx(c, mt, 0.485f, 0.05f, 0.485f, 0.515f, 0.18f, 0.515f)
        cy(c, p[0], 0.5f, 0.5f, 0.12f, 0.2f, 0.18f, 0.28f, 12); cy(c, p[0], 0.5f, 0.5f, 0.2f, 0.2f, 0.28f, 0.42f, 12); cy(c, p[0], 0.5f, 0.5f, 0.2f, 0.12f, 0.42f, 0.52f, 12)
        bx(c, mt, 0.26f, 0.2f, 0.485f, 0.29f, 0.52f, 0.515f); bx(c, mt, 0.71f, 0.2f, 0.485f, 0.74f, 0.52f, 0.515f)
        bx(c, p[1], 0.34f, 0.3f, 0.3f, 0.5f, 0.4f, 0.31f); bx(c, p[1], 0.56f, 0.34f, 0.3f, 0.66f, 0.46f, 0.31f)
    }

    private fun fireplace(c: Furniture.C, p: IntArray) {
        val st = p[0]; val wd = p[2]; val dk = p[3]; val fl = p[6]
        bx(c, st, 0f, 0f, 0f, 1f, 0.1f, 0.9f)
        bx(c, st, 0f, 0.1f, 0f, 0.18f, 0.82f, 0.9f); bx(c, st, 0.82f, 0.1f, 0f, 1f, 0.82f, 0.9f)
        bx(c, dk, 0.18f, 0.1f, 0.02f, 0.82f, 0.62f, 0.1f)
        bx(c, st, 0.18f, 0.62f, 0f, 0.82f, 0.82f, 0.9f)
        bx(c, st, 0f, 0.82f, 0f, 1f, 0.9f, 0.9f)
        bx(c, wd, -0.02f, 0.9f, -0.01f, 1.02f, 0.97f, 0.96f)
        bx(c, st, 0.1f, 0.97f, 0f, 0.9f, 1f, 0.6f)
        bx(c, dk, 0.24f, 0.1f, 0.3f, 0.76f, 0.2f, 0.42f); bx(c, dk, 0.28f, 0.1f, 0.5f, 0.72f, 0.2f, 0.62f)
        gx(c, fl, 0.28f, 0.1f, 0.28f, 0.72f, 0.14f, 0.66f)
        gx(c, fl, 0.32f, 0.2f, 0.34f, 0.46f, 0.44f, 0.46f); gx(c, fl, 0.54f, 0.2f, 0.4f, 0.68f, 0.4f, 0.54f); gx(c, fl, 0.42f, 0.2f, 0.38f, 0.58f, 0.56f, 0.52f)
    }
}
