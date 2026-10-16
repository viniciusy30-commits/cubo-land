package com.cuboland.app

import kotlin.math.*

/** Blocos com formato próprio (não são cubos): cacto (kind 1), melancia (2) e abóbora (3, também a luminosa). */
object Shapes {
    private const val P = 3.14159265f

    private fun h(x: Int, z: Int, k: Int): Float {
        var n = x * 374761393 + z * 668265263 + k * 1442695041.toInt()
        n = (n xor (n ushr 13)) * 1274126177; n = n xor (n ushr 16)
        return (n and 0xFFFF) / 65535f
    }

    fun emit(o: MeshBuf, w: World, x: Int, y: Int, z: Int, id: Int, kind: Int, vr: Float) {
        when (kind) { 1 -> cactus(o, w, x, y, z, id, vr); 4, 5, 6 -> Furniture.emit(o, w, x, y, z, id, vr); 7 -> { if (Deco.isDeco(id)) Deco.emit(o, w, x, y, z, id, vr) else if (BuildShapes.isBuild(Furniture.sub(id))) BuildShapes.emit(o, w, x, y, z, id, vr) else Furniture.emit(o, w, x, y, z, id, vr) }; else -> fruit(o, x, y, z, id, kind == 3, vr) }
    }

    /** caixa de colisão/seleção (x0,y0,z0,x1,y1,z1 locais) do bloco com formato; null = cubo inteiro */
    /** caixa de mira/quebra: nas peças finas e inclinadas é mais gordinha que a de colisão */
    fun pickBox(w: World, id: Int, x: Int, z: Int, y: Int = -1): FloatArray? {
        if (B.shape(id) == 7 && BuildShapes.isBuild(Furniture.sub(id))) return BuildShapes.pick(w, id, x, y, z)
        return box(w, id, x, z, y)
    }

    fun box(w: World, id: Int, x: Int, z: Int, y: Int = -1): FloatArray? {
        if (B.shape(id) == 7 && Deco.isDeco(id)) return Deco.box(w, id, x, y, z)
        if (B.shape(id) == 7 && BuildShapes.isBuild(Furniture.sub(id))) return BuildShapes.box(w, id, x, y, z)
        if (B.thin(id)) {
            val cx = 0.5f + (w.hash(x, z, 31) - 0.5f) * 0.4f; val cz = 0.5f + (w.hash(x, z, 32) - 0.5f) * 0.4f
            val r = 0.105f + 0.03f * w.hash(x, z, 33) + 0.035f
            return floatArrayOf(cx - r, 0f, cz - r, cx + r, 1f, cz + r)
        }
        return when (B.shape(id)) {
            1 -> { val cx = 0.5f + (h(x, z, 1) - 0.5f) * 0.08f; val cz = 0.5f + (h(x, z, 2) - 0.5f) * 0.08f; floatArrayOf(cx - 0.3f, 0f, cz - 0.3f, cx + 0.3f, 1f, cz + 0.3f) }
            2 -> floatArrayOf(0.08f, 0f, 0.08f, 0.92f, 0.81f, 0.92f)
            3 -> floatArrayOf(0.04f, 0f, 0.04f, 0.96f, 0.73f, 0.96f)
            4, 5, 6, 7 -> Furniture.box(w, id, x, y, z)
            else -> null
        }
    }

    // ---------- helpers ----------
    private fun vtx(o: MeshBuf, tile: Int, x: Float, y: Float, z: Float, u: Float, v: Float, s: Float) =
        o.vert(x, y, z, s, s, s, Atlas.u(tile, u.coerceIn(0f, 1f)), Atlas.v(tile, v.coerceIn(0f, 1f)))
    private fun fin(o: MeshBuf) { o.tri(0, 1, 2); o.tri(0, 2, 3); o.tri(0, 2, 1); o.tri(0, 3, 2); o.vc += 4 }

    /** caixa com 4 lados (tile side) + tampa de cima (tile top) */
    private fun box(o: MeshBuf, side: Int, top: Int, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, vr: Float) {
        val xs = floatArrayOf(x0, x1, x1, x0); val zs = floatArrayOf(z0, z0, z1, z1)
        val sh = floatArrayOf(0.8f, 0.7f, 0.9f, 0.76f)
        for (i in 0 until 4) {
            val j = (i + 1) and 3
            vtx(o, side, xs[i], y0, zs[i], 0f, 1f, sh[i] * vr); vtx(o, side, xs[j], y0, zs[j], 1f, 1f, sh[i] * vr)
            vtx(o, side, xs[j], y1, zs[j], 1f, 0f, sh[i] * vr); vtx(o, side, xs[i], y1, zs[i], 0f, 0f, sh[i] * vr)
            fin(o)
        }
        vtx(o, top, x0, y1, z0, 0f, 0f, vr); vtx(o, top, x1, y1, z0, 1f, 0f, vr); vtx(o, top, x1, y1, z1, 1f, 1f, vr); vtx(o, top, x0, y1, z1, 0f, 1f, vr); fin(o)
    }

    // ---------- melancia / abóbora ----------
    private fun fruit(o: MeshBuf, x: Int, y: Int, z: Int, id: Int, pump: Boolean, vr: Float) {
        val side = B.tSide[id]; val top = B.tTop[id]; val lit = B.light(id)
        val R = if (pump) 0.47f else 0.43f; val RY = if (pump) 0.36f else 0.40f
        val cx = x + 0.5f; val cz = z + 0.5f; val cy = y + RY + 0.005f
        val N = if (pump) 32 else 24   // abóbora: 8 gomos x 4 pedaços (a crista cai no centro de um pixel da textura)
        val ribs = if (pump) 8 else 0
        val phis = floatArrayOf(-55f, -27f, 0f, 27f, 55f, 80f).map { it * P / 180f }
        fun rr(phi: Float, th: Float) = R * cos(phi) * (1f + (if (ribs > 0) 0.075f * sin(ribs * th) else 0.02f * cos(3f * th)))
        fun shade(phi: Float, th: Float): Float {
            if (lit) return 1f
            val ny = sin(phi)
            return vr * (0.8f + (if (ny > 0f) 0.2f else 0.25f) * ny) * (0.94f + 0.06f * cos(th - 0.8f))
        }
        // lados
        for (j in 0 until 4) for (i in 0 until N) {
            val t0 = 2f * P * i / N; val t1 = 2f * P * (i + 1) / N
            val pa = phis[j]; val pb = phis[j + 1]
            val va = 1f - (j / 4f); val vb = 1f - ((j + 1) / 4f)
            vtx(o, side, cx + rr(pa, t0) * cos(t0), cy + RY * sin(pa), cz + rr(pa, t0) * sin(t0), i / N.toFloat(), va, shade(pa, t0))
            vtx(o, side, cx + rr(pa, t1) * cos(t1), cy + RY * sin(pa), cz + rr(pa, t1) * sin(t1), (i + 1) / N.toFloat(), va, shade(pa, t1))
            vtx(o, side, cx + rr(pb, t1) * cos(t1), cy + RY * sin(pb), cz + rr(pb, t1) * sin(t1), (i + 1) / N.toFloat(), vb, shade(pb, t1))
            vtx(o, side, cx + rr(pb, t0) * cos(t0), cy + RY * sin(pb), cz + rr(pb, t0) * sin(t0), i / N.toFloat(), vb, shade(pb, t0))
            fin(o)
        }
        // calota de cima (textura do topo, mapeada de cima) e fundo
        val pTop = phis[4]; val pTop2 = phis[5]
        for (i in 0 until N) {
            val t0 = 2f * P * i / N; val t1 = 2f * P * (i + 1) / N
            val r0 = rr(pTop, t0); val r1 = rr(pTop, t1); val q0 = rr(pTop2, t0); val q1 = rr(pTop2, t1)
            fun uu(dx: Float) = 0.5f + dx / (2f * R); 
            vtx(o, top, cx + r0 * cos(t0), cy + RY * sin(pTop), cz + r0 * sin(t0), uu(r0 * cos(t0)), uu(r0 * sin(t0)), shade(pTop, t0))
            vtx(o, top, cx + r1 * cos(t1), cy + RY * sin(pTop), cz + r1 * sin(t1), uu(r1 * cos(t1)), uu(r1 * sin(t1)), shade(pTop, t1))
            vtx(o, top, cx + q1 * cos(t1), cy + RY * sin(pTop2), cz + q1 * sin(t1), uu(q1 * cos(t1)), uu(q1 * sin(t1)), shade(pTop2, t1))
            vtx(o, top, cx + q0 * cos(t0), cy + RY * sin(pTop2), cz + q0 * sin(t0), uu(q0 * cos(t0)), uu(q0 * sin(t0)), shade(pTop2, t0))
            fin(o)
            // tampinha do polo
            val yp = cy + RY * 0.995f
            vtx(o, top, cx + q0 * cos(t0), cy + RY * sin(pTop2), cz + q0 * sin(t0), uu(q0 * cos(t0)), uu(q0 * sin(t0)), shade(pTop2, t0))
            vtx(o, top, cx + q1 * cos(t1), cy + RY * sin(pTop2), cz + q1 * sin(t1), uu(q1 * cos(t1)), uu(q1 * sin(t1)), shade(pTop2, t1))
            vtx(o, top, cx, yp, cz, 0.5f, 0.5f, shade(pTop2, t1)); vtx(o, top, cx, yp, cz, 0.5f, 0.5f, shade(pTop2, t0))
            fin(o)
            // fundo
            val b = phis[0]; val rb = rr(b, t0); val rb1 = rr(b, t1)
            vtx(o, side, cx + rb * cos(t0), cy + RY * sin(b), cz + rb * sin(t0), 0.5f, 0.9f, shade(b, t0))
            vtx(o, side, cx + rb1 * cos(t1), cy + RY * sin(b), cz + rb1 * sin(t1), 0.6f, 0.9f, shade(b, t1))
            vtx(o, side, cx, cy - RY * 0.99f, cz, 0.55f, 0.95f, shade(b, t1)); vtx(o, side, cx, cy - RY * 0.99f, cz, 0.55f, 0.95f, shade(b, t0))
            fin(o)
        }
        // cabinho (textura própria; o da abóbora é grosso e entortado na ponta)
        val sv = if (lit) 1f else vr
        if (pump) {
            val sw = 0.08f; val st = Blk.stemPump
            box(o, st, st, cx - sw, cy + RY - 0.03f, cz - sw, cx + sw, cy + RY + 0.09f, cz + sw, sv)
            box(o, st, st, cx - 0.055f + 0.045f, cy + RY + 0.09f, cz - 0.055f, cx + 0.055f + 0.045f, cy + RY + 0.17f, cz + 0.055f, sv)
        } else {
            val sw = 0.04f; val st = Blk.stemMel
            box(o, st, st, cx - sw, cy + RY - 0.03f, cz - sw, cx + sw, cy + RY + 0.07f, cz + sw, sv)
            box(o, st, st, cx - 0.03f + 0.035f, cy + RY + 0.07f, cz - 0.03f, cx + 0.03f + 0.035f, cy + RY + 0.12f, cz + 0.03f, sv)
        }
    }

    // ---------- cacto ----------
    private fun cactus(o: MeshBuf, w: World, x: Int, y: Int, z: Int, id: Int, vr: Float) {
        val tS = B.tSide[id]; val tT = B.tTop[id]
        val cx = x + 0.5f + (h(x, z, 1) - 0.5f) * 0.08f; val cz = z + 0.5f + (h(x, z, 2) - 0.5f) * 0.08f
        val N = 16; val R = 0.27f
        val above = w.get(x, y + 1, z) == id; val below = w.get(x, y - 1, z) == id
        val y0 = y.toFloat(); val y1 = y + 1f
        fun rad(i: Int) = R * (if ((i and 1) == 0) 1.1f else 0.9f)      // costelas: pontas e vales
        fun sh(i: Int, th: Float) = vr * (0.72f + 0.28f * (0.5f + 0.5f * cos(th - 0.9f))) * (if ((i and 1) == 0) 1f else 0.84f)
        for (i in 0 until N) {
            val t0 = 2f * P * i / N; val t1 = 2f * P * (i + 1) / N
            val r0 = rad(i); val r1 = rad(i + 1)
            vtx(o, tS, cx + r0 * cos(t0), y0, cz + r0 * sin(t0), i / N.toFloat(), 1f, sh(i, t0))
            vtx(o, tS, cx + r1 * cos(t1), y0, cz + r1 * sin(t1), (i + 1) / N.toFloat(), 1f, sh(i + 1, t1))
            vtx(o, tS, cx + r1 * cos(t1), y1, cz + r1 * sin(t1), (i + 1) / N.toFloat(), 0f, sh(i + 1, t1))
            vtx(o, tS, cx + r0 * cos(t0), y1, cz + r0 * sin(t0), i / N.toFloat(), 0f, sh(i, t0))
            fin(o)
        }
        if (!above) {   // topo arredondado
            val dome = y1 + 0.07f
            for (i in 0 until N) {
                val t0 = 2f * P * i / N; val t1 = 2f * P * (i + 1) / N
                val r0 = rad(i); val r1 = rad(i + 1)
                vtx(o, tT, cx + r0 * cos(t0), y1, cz + r0 * sin(t0), 0.5f + r0 * cos(t0) / (2f * R), 0.5f + r0 * sin(t0) / (2f * R), vr * 0.95f)
                vtx(o, tT, cx + r1 * cos(t1), y1, cz + r1 * sin(t1), 0.5f + r1 * cos(t1) / (2f * R), 0.5f + r1 * sin(t1) / (2f * R), vr * 0.95f)
                vtx(o, tT, cx, dome, cz, 0.5f, 0.5f, vr); vtx(o, tT, cx, dome, cz, 0.5f, 0.5f, vr)
                fin(o)
            }
            if (below && h(x, z, 7) < 0.55f) {   // florzinha no topo
                val ft = 17 + (h(x, z, 8) * 3f).toInt().coerceIn(0, 2); val fs = 0.17f
                for (k in 0..1) {
                    val ax = if (k == 0) fs else 0f; val az = if (k == 0) 0f else fs
                    vtx(o, ft, cx - ax, dome - 0.02f, cz - az, 0f, 1f, vr); vtx(o, ft, cx + ax, dome - 0.02f, cz + az, 1f, 1f, vr)
                    vtx(o, ft, cx + ax, dome + 0.32f, cz + az, 1f, 0f, vr); vtx(o, ft, cx - ax, dome + 0.32f, cz - az, 0f, 0f, vr)
                    fin(o)
                }
            }
        }
        // espinhos: tufinhos finos e claros nas pontas das costelas
        val sp = B.tTop[B.SAND]
        for (k in 0 until 8) for (row in 0..1) {
            if (h(x * 8 + k, z * 2 + row, y + 11) < 0.25f) continue
            val th = 2f * P * (2 * k) / N
            val yy = y + 0.22f + 0.5f * row + (h(x * 8 + k, z, y + row) - 0.5f) * 0.12f
            val bx = cx + R * 1.1f * cos(th) * 0.97f; val bz = cz + R * 1.1f * sin(th) * 0.97f
            val dx = cos(th); val dz = sin(th); val len = 0.11f
            for (c in 0..1) {
                val ex = if (c == 0) 0f else -dz * 0.022f; val ez = if (c == 0) 0f else dx * 0.022f; val ey = if (c == 0) 0.022f else 0f
                vtx(o, sp, bx + ex, yy + ey, bz + ez, 0.5f, 0.5f, 1f); vtx(o, sp, bx - ex, yy - ey, bz - ez, 0.5f, 0.5f, 1f)
                vtx(o, sp, bx + dx * len, yy - 0.035f, bz + dz * len, 0.5f, 0.5f, 1f); vtx(o, sp, bx + dx * len, yy - 0.035f, bz + dz * len, 0.5f, 0.5f, 1f)
                fin(o)
            }
        }
        // braço (às vezes): sai de lado e sobe
        if (below && h(x, z, y * 3 + 9) < 0.22f) {
            val d = (h(x, z, y + 21) * 4f).toInt().coerceIn(0, 3)
            val dx = if (d == 0) 1f else if (d == 1) -1f else 0f; val dz = if (d == 2) 1f else if (d == 3) -1f else 0f
            val a0 = y + 0.2f; val a1 = y + 0.46f
            val hx0 = min(cx + dx * 0.15f, cx + dx * 0.56f); val hx1 = max(cx + dx * 0.15f, cx + dx * 0.56f)
            val hz0 = min(cz + dz * 0.15f, cz + dz * 0.56f); val hz1 = max(cz + dz * 0.15f, cz + dz * 0.56f)
            box(o, tS, tT, if (dx != 0f) hx0 else cx - 0.1f, a0, if (dz != 0f) hz0 else cz - 0.1f, if (dx != 0f) hx1 else cx + 0.1f, a1, if (dz != 0f) hz1 else cz + 0.1f, vr)
            val ax = cx + dx * 0.46f; val az = cz + dz * 0.46f
            box(o, tS, tT, ax - 0.1f, a0, az - 0.1f, ax + 0.1f, y + 1.05f, az + 0.1f, vr)
        }
    }

    // ---------- mobília ----------
    /** caixa com 4 lados, tampa e fundo (fundo visível por baixo da mesa) */
    private fun fbox(o: MeshBuf, side: Int, top: Int, bot: Int, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, vr: Float) {
        box(o, side, top, x0, y0, z0, x1, y1, z1, vr)
        vtx(o, bot, x0, y0, z0, 0f, 0f, 0.55f * vr); vtx(o, bot, x1, y0, z0, 1f, 0f, 0.55f * vr); vtx(o, bot, x1, y0, z1, 1f, 1f, 0.55f * vr); vtx(o, bot, x0, y0, z1, 0f, 1f, 0.55f * vr); fin(o)
    }

    /** direção da cadeira: 0 = +Z, 1 = -Z, 2 = +X, 3 = -X (vira para a mesa vizinha; sem mesa, depende da posição) */
    fun chairDir(w: World, x: Int, y: Int, z: Int): Int {
        val tbl = B.byName["Mesa"] ?: -1
        return when {
            w.get(x, y, z + 1) == tbl -> 0; w.get(x, y, z - 1) == tbl -> 1
            w.get(x + 1, y, z) == tbl -> 2; w.get(x - 1, y, z) == tbl -> 3
            else -> (h(x, z, 5) * 4f).toInt().coerceIn(0, 3)
        }
    }

    /** cadeira: pernas, travessas, assento com almofada e encosto com ripas. Vira para a mesa vizinha (senão, direção fixa por posição). */
    private fun chair(o: MeshBuf, w: World, x: Int, y: Int, z: Int, id: Int, vr: Float) {
        val dir = chairDir(w, x, y, z)
        val cu = B.tTop[id]; val wd = B.tSide[id]; val dk = B.tBot[id]
        // geometria local virada pra +Z (encosto em z baixo); gira pra direção
        fun b(t1: Int, t2: Int, t3: Int, lx0: Float, ly0: Float, lz0: Float, lx1: Float, ly1: Float, lz1: Float) {
            fun mx(a: Float, c: Float) = when (dir) { 0 -> a; 1 -> 1f - a; 2 -> c; else -> 1f - c }
            fun mz(a: Float, c: Float) = when (dir) { 0 -> c; 1 -> 1f - c; 2 -> 1f - a; else -> a }
            val ax = mx(lx0, lz0); val bx = mx(lx1, lz1); val az = mz(lx0, lz0); val bz = mz(lx1, lz1)
            fbox(o, t1, t2, t3, x + min(ax, bx), y + ly0, z + min(az, bz), x + max(ax, bx), y + ly1, z + max(az, bz), vr)
        }
        // pernas da frente e postes de trás (sobem até o encosto)
        b(dk, dk, dk, 0.16f, 0f, 0.72f, 0.28f, 0.42f, 0.84f); b(dk, dk, dk, 0.72f, 0f, 0.72f, 0.84f, 0.42f, 0.84f)
        b(dk, wd, dk, 0.16f, 0f, 0.16f, 0.28f, 1f, 0.28f); b(dk, wd, dk, 0.72f, 0f, 0.16f, 0.84f, 1f, 0.28f)
        // travessas entre as pernas
        b(dk, dk, dk, 0.28f, 0.17f, 0.74f, 0.72f, 0.23f, 0.82f); b(dk, dk, dk, 0.28f, 0.17f, 0.18f, 0.72f, 0.23f, 0.26f)
        b(dk, dk, dk, 0.16f, 0.17f, 0.28f, 0.24f, 0.23f, 0.72f); b(dk, dk, dk, 0.76f, 0.17f, 0.28f, 0.84f, 0.23f, 0.72f)
        // assento + almofada
        b(wd, wd, dk, 0.14f, 0.42f, 0.14f, 0.86f, 0.50f, 0.86f)
        b(cu, cu, cu, 0.2f, 0.50f, 0.3f, 0.8f, 0.58f, 0.82f)
        // encosto: ripas e travessa de cima
        b(wd, wd, wd, 0.28f, 0.62f, 0.19f, 0.72f, 0.69f, 0.25f); b(wd, wd, wd, 0.28f, 0.73f, 0.19f, 0.72f, 0.80f, 0.25f)
        b(wd, wd, wd, 0.14f, 0.84f, 0.14f, 0.86f, 0.98f, 0.30f)
    }

    /** mesa: tampo que cresce ao juntar com outras mesas (L, comprida, quadrada...); pernas só nos cantos externos (e nos cantos internos do L) */
    private fun table(o: MeshBuf, w: World, x: Int, y: Int, z: Int, id: Int, vr: Float) {
        val tp = B.tTop[id]; val sd = B.tSide[id]; val lg = B.tBot[id]
        fun t(dx: Int, dz: Int) = w.get(x + dx, y, z + dz) == id
        val nW = t(-1, 0); val nE = t(1, 0); val nN = t(0, -1); val nS = t(0, 1)
        val x0 = if (nW) 0f else 0.03f; val x1 = if (nE) 1f else 0.97f
        val z0 = if (nN) 0f else 0.03f; val z1 = if (nS) 1f else 0.97f
        fbox(o, sd, tp, sd, x + x0, y + 0.80f, z + z0, x + x1, y + 0.94f, z + z1, vr)
        // saia (faixa escura logo abaixo do tampo) só nas bordas livres
        val ax0 = if (nW) 0f else 0.09f; val ax1 = if (nE) 1f else 0.91f
        val az0 = if (nN) 0f else 0.09f; val az1 = if (nS) 1f else 0.91f
        if (!nN) fbox(o, lg, lg, lg, x + ax0, y + 0.69f, z + 0.09f, x + ax1, y + 0.80f, z + 0.14f, vr)
        if (!nS) fbox(o, lg, lg, lg, x + ax0, y + 0.69f, z + 0.86f, x + ax1, y + 0.80f, z + 0.91f, vr)
        if (!nW) fbox(o, lg, lg, lg, x + 0.09f, y + 0.69f, z + az0, x + 0.14f, y + 0.80f, z + az1, vr)
        if (!nE) fbox(o, lg, lg, lg, x + 0.86f, y + 0.69f, z + az0, x + 0.91f, y + 0.80f, z + az1, vr)
        // pernas nos cantos: externos, ou internos quando os dois lados têm mesa mas a diagonal não (formato em L)
        for (sx in 0..1) for (sz in 0..1) {
            val nx = if (sx == 0) nW else nE; val nz = if (sz == 0) nN else nS
            val dg = t(if (sx == 0) -1 else 1, if (sz == 0) -1 else 1)
            if (!((!nx && !nz) || (nx && nz && !dg))) continue
            val lx = if (sx == 0) 0.07f else 0.80f; val lz = if (sz == 0) 0.07f else 0.80f
            fbox(o, lg, lg, lg, x + lx, y + 0f, z + lz, x + lx + 0.13f, y + 0.69f, z + lz + 0.13f, vr)
            fbox(o, lg, lg, lg, x + lx - 0.025f, y + 0f, z + lz - 0.025f, x + lx + 0.155f, y + 0.06f, z + lz + 0.155f, vr)   // pezinho
        }
    }

    /** cama: camas em fila viram uma cama comprida (travesseiro e cabeceira só na ponta de trás, pé na ponta da frente) */
    private fun bed(o: MeshBuf, w: World, x: Int, y: Int, z: Int, id: Int, vr: Float) {
        val zAxis = w.get(x, y, z - 1) == id || w.get(x, y, z + 1) == id || (w.get(x - 1, y, z) != id && w.get(x + 1, y, z) != id)
        val nH = if (zAxis) w.get(x, y, z - 1) == id else w.get(x - 1, y, z) == id
        val nF = if (zAxis) w.get(x, y, z + 1) == id else w.get(x + 1, y, z) == id
        val bl = B.tTop[id]; val sh = B.tSide[id]; val wd = B.tBot[id]
        // local: lx = largura, lz = comprimento (cabeceira em lz baixo); no eixo X troca os dois
        fun b(t: Int, t2: Int, t3: Int, lx0: Float, ly0: Float, lz0: Float, lx1: Float, ly1: Float, lz1: Float) {
            val x0 = if (zAxis) lx0 else lz0; val x1 = if (zAxis) lx1 else lz1
            val z0 = if (zAxis) lz0 else lx0; val z1 = if (zAxis) lz1 else lx1
            fbox(o, t, t2, t3, x + x0, y + ly0, z + z0, x + x1, y + ly1, z + z1, vr)
        }
        val e0 = if (nH) 0f else 0.02f; val e1 = if (nF) 1f else 0.98f
        b(wd, wd, wd, 0.02f, 0.18f, e0, 0.98f, 0.38f, e1)                       // estrado
        if (!nH) { b(wd, wd, wd, 0.03f, 0f, 0.03f, 0.15f, 0.18f, 0.15f); b(wd, wd, wd, 0.85f, 0f, 0.03f, 0.97f, 0.18f, 0.15f) }
        if (!nF) { b(wd, wd, wd, 0.03f, 0f, 0.85f, 0.15f, 0.18f, 0.97f); b(wd, wd, wd, 0.85f, 0f, 0.85f, 0.97f, 0.18f, 0.97f) }
        b(sh, sh, sh, 0.06f, 0.38f, if (nH) 0f else 0.1f, 0.94f, 0.58f, if (nF) 1f else 0.9f)   // colchão
        b(bl, bl, sh, 0.03f, 0.40f, if (nH) 0f else 0.44f, 0.97f, 0.64f, if (nF) 1f else 0.9f) // coberta que cai pelos lados
        if (!nH) {
            b(sh, sh, sh, 0.14f, 0.58f, 0.14f, 0.86f, 0.72f, 0.40f)               // travesseiro
            b(wd, wd, wd, 0.02f, 0.18f, 0.02f, 0.98f, 0.95f, 0.10f)               // cabeceira alta
        }
        if (!nF) b(wd, wd, wd, 0.02f, 0.18f, 0.90f, 0.98f, 0.52f, 0.98f)          // peseira baixa
    }
}
