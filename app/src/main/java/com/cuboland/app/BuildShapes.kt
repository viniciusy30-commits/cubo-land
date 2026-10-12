package com.cuboland.app

/**
 * Formas da Construção (kind 7 de Shapes, tipo >= 60 em flags shr 8):
 * vidro fino que se liga nos vizinhos, janela em placa fina, lajes, escadas, telhados inclinados e cumeeiras.
 * Tipos: 60 vidro, 61 janela, 62 laje de baixo, 63 laje de cima, 64..67 telhado (lado alto pra +Z, -Z, +X, -X),
 * 68..71 escada (mesma ordem), 72 cumeeira ao longo de Z, 73 cumeeira ao longo de X.
 */
object BuildShapes {
    const val S_PANE = 60; const val S_PLATE = 61; const val S_SLAB = 62; const val S_SLABT = 63
    const val S_ROOF = 64; const val S_STAIR = 68; const val S_RIDGE = 72

    private const val T0 = 0.4375f   // espessura fina: 0.4375 .. 0.5625
    private const val T1 = 0.5625f

    fun isBuild(sub: Int) = sub >= 60

    fun sub(id: Int): Int = Furniture.sub(id)

    /** vizinho que o vidro/janela encosta: parede cheia, outro vidro ou outra janela */
    private fun wallLike(w: World, x: Int, y: Int, z: Int): Boolean {
        val n = w.get(x, y, z)
        if (!B.solid(n)) return false
        if (B.shape(n) == 7) { val s = sub(n); return s == S_PANE || s == S_PLATE }
        return B.shape(n) == 0 && !B.glass(n) && !B.thin(n) && !B.leafy(n)
    }

    /** a janela em placa corre ao longo de X? (acompanha a parede em que está) */
    private fun spanX(w: World, x: Int, y: Int, z: Int): Boolean {
        var cx = 0; var cz = 0
        if (wallLike(w, x - 1, y, z)) cx++
        if (wallLike(w, x + 1, y, z)) cx++
        if (wallLike(w, x, y, z - 1)) cz++
        if (wallLike(w, x, y, z + 1)) cz++
        return cx >= cz
    }

    /** faixas do vidro fino: [xa, xb, za, zb] do trecho ao longo de X (se houver) e ao longo de Z (se houver) */
    private fun paneArms(w: World, x: Int, y: Int, z: Int): FloatArray {
        val cW = wallLike(w, x - 1, y, z); val cE = wallLike(w, x + 1, y, z)
        val cN = wallLike(w, x, y, z - 1); val cS = wallLike(w, x, y, z + 1)
        val any = cW || cE || cN || cS
        val hasX = cW || cE || !any
        val hasZ = cN || cS
        val xa = if (cW || !any) 0f else T0; val xb = if (cE || !any) 1f else T1
        val za = if (cN) 0f else T0; val zb = if (cS) 1f else T1
        return floatArrayOf(if (hasX) 1f else 0f, xa, xb, if (hasZ) 1f else 0f, za, zb)
    }

    fun box(w: World, id: Int, x: Int, y: Int, z: Int): FloatArray? {
        return when (val s = sub(id)) {
            S_PANE -> {
                val a = paneArms(w, x, y, z)
                var x0 = 1f; var x1 = 0f; var z0 = 1f; var z1 = 0f
                if (a[0] > 0f) { x0 = minOf(x0, a[1]); x1 = maxOf(x1, a[2]); z0 = minOf(z0, T0); z1 = maxOf(z1, T1) }
                if (a[3] > 0f) { x0 = minOf(x0, T0); x1 = maxOf(x1, T1); z0 = minOf(z0, a[4]); z1 = maxOf(z1, a[5]) }
                floatArrayOf(x0, 0f, z0, x1, 1f, z1)
            }
            S_PLATE -> if (spanX(w, x, y, z)) floatArrayOf(0f, 0f, 0.43f, 1f, 1f, 0.57f) else floatArrayOf(0.43f, 0f, 0f, 0.57f, 1f, 1f)
            S_SLABT -> floatArrayOf(0f, 0.5f, 0f, 1f, 1f, 1f)
            else -> if (s >= S_SLAB) floatArrayOf(0f, 0f, 0f, 1f, 0.5f, 1f) else null
        }
    }

    /** caixa usada pra mirar/quebrar (mais gordinha que a de colisão nas peças finas e inclinadas) */
    fun pick(w: World, id: Int, x: Int, y: Int, z: Int): FloatArray? {
        val s = sub(id)
        val b = box(w, id, x, y, z) ?: return null
        if (s == S_PANE || s == S_PLATE) {
            val c = b.copyOf()
            if (c[3] - c[0] < 0.7f) { c[0] = maxOf(0f, c[0] - 0.08f); c[3] = minOf(1f, c[3] + 0.08f) }
            if (c[5] - c[2] < 0.7f) { c[2] = maxOf(0f, c[2] - 0.08f); c[5] = minOf(1f, c[5] + 0.08f) }
            return c
        }
        if (s >= S_ROOF) return floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f)
        return b
    }

    // ---------- malha ----------
    private class Q(val o: MeshBuf, val x: Int, val y: Int, val z: Int) {
        fun v(t: Int, px: Float, py: Float, pz: Float, u: Float, vv: Float, s: Float) {
            o.vert(x + px, y + py, z + pz, s, s, s, Atlas.u(t, u.coerceIn(0f, 1f)), Atlas.v(t, vv.coerceIn(0f, 1f)))
        }
        fun fin() { o.tri(0, 1, 2); o.tri(0, 2, 3); o.tri(0, 2, 1); o.tri(0, 3, 2); o.vc += 4 }
    }

    /** placa fina: faces grandes com a textura inteira, bordas pegam a moldura da textura */
    private fun plate(q: Q, t: Int, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, alongX: Boolean, vr: Float) {
        val sf = 0.92f * vr; val sb = 0.80f * vr; val se = 0.86f * vr
        if (alongX) {
            q.v(t, x0, y0, z0, x0, 1f - y0, sf); q.v(t, x1, y0, z0, x1, 1f - y0, sf); q.v(t, x1, y1, z0, x1, 1f - y1, sf); q.v(t, x0, y1, z0, x0, 1f - y1, sf); q.fin()
            q.v(t, x0, y0, z1, x0, 1f - y0, sb); q.v(t, x1, y0, z1, x1, 1f - y0, sb); q.v(t, x1, y1, z1, x1, 1f - y1, sb); q.v(t, x0, y1, z1, x0, 1f - y1, sb); q.fin()
            q.v(t, x0, y1, z0, x0, 0.02f, vr); q.v(t, x1, y1, z0, x1, 0.02f, vr); q.v(t, x1, y1, z1, x1, 0.02f, vr); q.v(t, x0, y1, z1, x0, 0.02f, vr); q.fin()
            q.v(t, x0, y0, z0, x0, 0.98f, 0.6f * vr); q.v(t, x1, y0, z0, x1, 0.98f, 0.6f * vr); q.v(t, x1, y0, z1, x1, 0.98f, 0.6f * vr); q.v(t, x0, y0, z1, x0, 0.98f, 0.6f * vr); q.fin()
            q.v(t, x0, y0, z0, 0.02f, 1f - y0, se); q.v(t, x0, y0, z1, 0.02f, 1f - y0, se); q.v(t, x0, y1, z1, 0.02f, 1f - y1, se); q.v(t, x0, y1, z0, 0.02f, 1f - y1, se); q.fin()
            q.v(t, x1, y0, z0, 0.98f, 1f - y0, se); q.v(t, x1, y0, z1, 0.98f, 1f - y0, se); q.v(t, x1, y1, z1, 0.98f, 1f - y1, se); q.v(t, x1, y1, z0, 0.98f, 1f - y1, se); q.fin()
        } else {
            q.v(t, x0, y0, z0, z0, 1f - y0, sf); q.v(t, x0, y0, z1, z1, 1f - y0, sf); q.v(t, x0, y1, z1, z1, 1f - y1, sf); q.v(t, x0, y1, z0, z0, 1f - y1, sf); q.fin()
            q.v(t, x1, y0, z0, z0, 1f - y0, sb); q.v(t, x1, y0, z1, z1, 1f - y0, sb); q.v(t, x1, y1, z1, z1, 1f - y1, sb); q.v(t, x1, y1, z0, z0, 1f - y1, sb); q.fin()
            q.v(t, x0, y1, z0, z0, 0.02f, vr); q.v(t, x0, y1, z1, z1, 0.02f, vr); q.v(t, x1, y1, z1, z1, 0.02f, vr); q.v(t, x1, y1, z0, z0, 0.02f, vr); q.fin()
            q.v(t, x0, y0, z0, z0, 0.98f, 0.6f * vr); q.v(t, x0, y0, z1, z1, 0.98f, 0.6f * vr); q.v(t, x1, y0, z1, z1, 0.98f, 0.6f * vr); q.v(t, x1, y0, z0, z0, 0.98f, 0.6f * vr); q.fin()
            q.v(t, x0, y0, z0, 0.02f, 1f - y0, se); q.v(t, x1, y0, z0, 0.02f, 1f - y0, se); q.v(t, x1, y1, z0, 0.02f, 1f - y1, se); q.v(t, x0, y1, z0, 0.02f, 1f - y1, se); q.fin()
            q.v(t, x0, y0, z1, 0.98f, 1f - y0, se); q.v(t, x1, y0, z1, 0.98f, 1f - y0, se); q.v(t, x1, y1, z1, 0.98f, 1f - y1, se); q.v(t, x0, y1, z1, 0.98f, 1f - y1, se); q.fin()
        }
    }

    /** caixa maciça com 6 faces (textura pela posição, não amassa); noBottom pula a face de baixo */
    private fun cuboid(q: Q, top: Int, side: Int, bot: Int, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, vr: Float, noBottom: Boolean = false) {
        val xs = floatArrayOf(x0, x1, x1, x0); val zs = floatArrayOf(z0, z0, z1, z1)
        val shd = floatArrayOf(0.8f, 0.7f, 0.9f, 0.76f)
        for (i in 0 until 4) {
            val j = (i + 1) and 3
            val horiz = i == 0 || i == 2
            val ui = if (horiz) xs[i] else zs[i]; val uj = if (horiz) xs[j] else zs[j]
            val s = shd[i] * vr
            q.v(side, xs[i], y0, zs[i], ui, 1f - y0, s * 0.9f); q.v(side, xs[j], y0, zs[j], uj, 1f - y0, s * 0.9f)
            q.v(side, xs[j], y1, zs[j], uj, 1f - y1, s); q.v(side, xs[i], y1, zs[i], ui, 1f - y1, s); q.fin()
        }
        q.v(top, x0, y1, z0, x0, z0, vr); q.v(top, x1, y1, z0, x1, z0, vr); q.v(top, x1, y1, z1, x1, z1, vr); q.v(top, x0, y1, z1, x0, z1, vr); q.fin()
        if (!noBottom) { q.v(bot, x0, y0, z0, x0, z0, 0.55f * vr); q.v(bot, x1, y0, z0, x1, z0, 0.55f * vr); q.v(bot, x1, y0, z1, x1, z1, 0.55f * vr); q.v(bot, x0, y0, z1, x0, z1, 0.55f * vr); q.fin() }
    }

    // rotação local -> mundo (f: lado alto pra 0 = +Z, 1 = -Z, 2 = +X, 3 = -X); local: lx = largura, lz = rumo do lado alto
    private fun wx(f: Int, lx: Float, lz: Float) = when (f) { 0 -> lx; 1 -> 1f - lx; 2 -> lz; else -> 1f - lz }
    private fun wz(f: Int, lx: Float, lz: Float) = when (f) { 0 -> lz; 1 -> 1f - lz; 2 -> 1f - lx; else -> lx }

    /** caixa em coordenadas locais, girada pra direção f */
    private fun lbox(q: Q, f: Int, top: Int, side: Int, bot: Int, lx0: Float, y0: Float, lz0: Float, lx1: Float, y1: Float, lz1: Float, vr: Float) {
        val ax = wx(f, lx0, lz0); val bx = wx(f, lx1, lz1); val az = wz(f, lx0, lz0); val bz = wz(f, lx1, lz1)
        cuboid(q, top, side, bot, minOf(ax, bx), y0, minOf(az, bz), maxOf(ax, bx), y1, maxOf(az, bz), vr)
    }

    private val slopeShade = floatArrayOf(0.90f, 1.0f, 0.93f, 0.99f)

    /** telhado: rampa de 45 graus, lado alto atrás (parede) e dois triângulos nas laterais */
    private fun roof(q: Q, f: Int, id: Int, vr: Float) {
        val tp = B.tTop[id]; val sd = B.tSide[id]; val bt = B.tBot[id]
        val ss = slopeShade[f] * vr
        // rampa: borda baixa (lz = 0, y = 0) até borda alta (lz = 1, y = 1)
        q.v(tp, wx(f, 0f, 0f), 0f, wz(f, 0f, 0f), 0f, 1f, ss * 0.92f); q.v(tp, wx(f, 1f, 0f), 0f, wz(f, 1f, 0f), 1f, 1f, ss * 0.92f)
        q.v(tp, wx(f, 1f, 1f), 1f, wz(f, 1f, 1f), 1f, 0f, ss); q.v(tp, wx(f, 0f, 1f), 1f, wz(f, 0f, 1f), 0f, 0f, ss); q.fin()
        // parede de trás (lado alto)
        q.v(sd, wx(f, 0f, 1f), 0f, wz(f, 0f, 1f), 0f, 1f, 0.72f * vr); q.v(sd, wx(f, 1f, 1f), 0f, wz(f, 1f, 1f), 1f, 1f, 0.72f * vr)
        q.v(sd, wx(f, 1f, 1f), 1f, wz(f, 1f, 1f), 1f, 0f, 0.8f * vr); q.v(sd, wx(f, 0f, 1f), 1f, wz(f, 0f, 1f), 0f, 0f, 0.8f * vr); q.fin()
        // laterais triangulares
        for (side in 0..1) {
            val lx = side.toFloat(); val sh = (if (side == 0) 0.78f else 0.86f) * vr
            q.v(sd, wx(f, lx, 0f), 0f, wz(f, lx, 0f), 0f, 1f, sh); q.v(sd, wx(f, lx, 1f), 0f, wz(f, lx, 1f), 1f, 1f, sh)
            q.v(sd, wx(f, lx, 1f), 1f, wz(f, lx, 1f), 1f, 0f, sh); q.v(sd, wx(f, lx, 1f), 1f, wz(f, lx, 1f), 1f, 0f, sh); q.fin()
        }
        // fundo
        q.v(bt, wx(f, 0f, 0f), 0f, wz(f, 0f, 0f), 0f, 0f, 0.5f * vr); q.v(bt, wx(f, 1f, 0f), 0f, wz(f, 1f, 0f), 1f, 0f, 0.5f * vr)
        q.v(bt, wx(f, 1f, 1f), 0f, wz(f, 1f, 1f), 1f, 1f, 0.5f * vr); q.v(bt, wx(f, 0f, 1f), 0f, wz(f, 0f, 1f), 0f, 1f, 0.5f * vr); q.fin()
    }

    /** cumeeira: telhado de duas águas num bloco só (prisma triangular). alongZ = a crista corre ao longo de Z */
    private fun ridge(q: Q, alongZ: Boolean, id: Int, vr: Float) {
        val tp = B.tTop[id]; val sd = B.tSide[id]; val bt = B.tBot[id]
        // dois lados da rampa; (a, b) = coordenada de largura e de comprimento
        fun p(a: Float, h: Float, b: Float, u: Float, v: Float, s: Float) {
            if (alongZ) q.v(tp, a, h, b, u, v, s) else q.v(tp, b, h, a, u, v, s)
        }
        fun e(a: Float, h: Float, b: Float, u: Float, v: Float, s: Float) {
            if (alongZ) q.v(sd, a, h, b, u, v, s) else q.v(sd, b, h, a, u, v, s)
        }
        val sL = (if (alongZ) 0.92f else 1.0f) * vr; val sR = (if (alongZ) 0.99f else 0.9f) * vr
        // água da esquerda (a de 0 a 0.5)
        p(0f, 0f, 0f, 0f, 1f, sL * 0.92f); p(0f, 0f, 1f, 1f, 1f, sL * 0.92f); p(0.5f, 1f, 1f, 1f, 0f, sL); p(0.5f, 1f, 0f, 0f, 0f, sL); q.fin()
        // água da direita
        p(1f, 0f, 0f, 0f, 1f, sR * 0.92f); p(1f, 0f, 1f, 1f, 1f, sR * 0.92f); p(0.5f, 1f, 1f, 1f, 0f, sR); p(0.5f, 1f, 0f, 0f, 0f, sR); q.fin()
        // empenas triangulares nas pontas
        for (end in 0..1) {
            val b = end.toFloat(); val sh = (if (end == 0) 0.76f else 0.84f) * vr
            e(0f, 0f, b, 0f, 1f, sh); e(1f, 0f, b, 1f, 1f, sh); e(0.5f, 1f, b, 0.5f, 0f, sh); e(0.5f, 1f, b, 0.5f, 0f, sh); q.fin()
        }
        // fundo
        if (alongZ) { q.v(bt, 0f, 0f, 0f, 0f, 0f, 0.5f * vr); q.v(bt, 1f, 0f, 0f, 1f, 0f, 0.5f * vr); q.v(bt, 1f, 0f, 1f, 1f, 1f, 0.5f * vr); q.v(bt, 0f, 0f, 1f, 0f, 1f, 0.5f * vr) }
        else { q.v(bt, 0f, 0f, 0f, 0f, 0f, 0.5f * vr); q.v(bt, 1f, 0f, 0f, 1f, 0f, 0.5f * vr); q.v(bt, 1f, 0f, 1f, 1f, 1f, 0.5f * vr); q.v(bt, 0f, 0f, 1f, 0f, 1f, 0.5f * vr) }
        q.fin()
    }

    fun emit(o: MeshBuf, w: World, x: Int, y: Int, z: Int, id: Int, vr: Float) {
        val s = sub(id)
        val q = Q(o, x, y, z)
        val tp = B.tTop[id]; val sd = B.tSide[id]; val bt = B.tBot[id]
        when {
            s == S_PANE -> {
                val a = paneArms(w, x, y, z)
                if (a[0] > 0f) plate(q, sd, a[1], 0f, T0, a[2], 1f, T1, true, vr)
                if (a[3] > 0f) plate(q, sd, T0, 0f, a[4], T1, 1f, a[5], false, vr)
            }
            s == S_PLATE -> {
                if (spanX(w, x, y, z)) plate(q, sd, 0f, 0f, 0.43f, 1f, 1f, 0.57f, true, vr)
                else plate(q, sd, 0.43f, 0f, 0f, 0.57f, 1f, 1f, false, vr)
            }
            s == S_SLAB -> cuboid(q, tp, sd, bt, 0f, 0f, 0f, 1f, 0.5f, 1f, vr)
            s == S_SLABT -> cuboid(q, tp, sd, bt, 0f, 0.5f, 0f, 1f, 1f, 1f, vr)
            s in S_ROOF until S_ROOF + 4 -> roof(q, s - S_ROOF, id, vr)
            s in S_STAIR until S_STAIR + 4 -> {
                val f = s - S_STAIR
                lbox(q, f, tp, sd, bt, 0f, 0f, 0f, 1f, 0.5f, 0.5f, vr)    // degrau de baixo (lado baixo)
                lbox(q, f, tp, sd, bt, 0f, 0f, 0.5f, 1f, 1f, 1f, vr)      // degrau de cima (lado alto)
            }
            s == S_RIDGE -> ridge(q, true, id, vr)
            s == S_RIDGE + 1 -> ridge(q, false, id, vr)
        }
    }

    /** direção 0 = +Z, 1 = -Z, 2 = +X, 3 = -X em que o jogador olha */
    fun lookDir(yaw: Float): Int {
        val fx = kotlin.math.sin(yaw); val fz = kotlin.math.cos(yaw)
        return if (kotlin.math.abs(fz) >= kotlin.math.abs(fx)) (if (fz > 0f) 0 else 1) else (if (fx > 0f) 2 else 3)
    }
}
