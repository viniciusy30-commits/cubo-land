package com.cuboland.app

import kotlin.math.*

/** Mobília detalhada: blocos com formato próprio (kinds 4..7 de Shapes). kind 7 guarda o tipo em (flags shr 8). */
object Furniture {
    const val S_CHAIR = 1; const val S_TABLE = 2; const val S_BED = 3
    const val S_CHAIR2 = 10; const val S_STOOL = 11; const val S_ARM = 12; const val S_OFFICE = 13; const val S_SOFA = 14
    const val S_TDARK = 20; const val S_TMARB = 21; const val S_TCLOTH = 22; const val S_TROUND = 23; const val S_COFFEE = 24; const val S_DESK = 25; const val S_TROUNDM = 26
    const val S_SHELF = 30; const val S_WARD = 31; const val S_DRESS = 32; const val S_FRIDGE = 33
    const val S_PC = 40; const val S_TV = 41; const val S_LAMP = 42
    const val S_ELEV = 55   // 60..99 é da BuildShapes (Painel, Placa, Laje...)
    const val S_PLANT = 50; const val S_RUGR = 51; const val S_RUGB = 52

    const val T_OAK = 0; const val T_OAKD = 1; const val T_DARK = 2; const val T_DARKD = 3; const val T_BIRCH = 4
    const val T_MARB = 5; const val T_MARBK = 6; const val T_GOLD = 7; const val T_STEEL = 8; const val T_GING = 9
    const val T_FABR = 10; const val T_FABB = 11; const val T_FABG = 12; const val T_CREAM = 13; const val T_WHITE = 14
    const val T_SCREEN = 15; const val T_BLACK = 16; const val T_KEYS = 17; const val T_POT = 18; const val T_LEAFA = 19
    const val T_LEAFB = 20; const val T_SOIL = 21; const val T_BOOK0 = 22   // 22..27
    const val T_RUGR = 28; const val T_RUGB = 29; const val T_SHADE = 30; const val T_GREY = 31; const val T_TVSCR = 32
    private const val TAU = 6.2831853f

    fun sub(id: Int): Int = when (B.shape(id)) {
        4 -> S_CHAIR; 5 -> S_TABLE; 6 -> S_BED
        7 -> (B.flags[id and 16383] shr 8) and 255
        else -> 0
    }
    fun isTv(id: Int): Boolean = sub(id) == S_TV
    fun isElev(id: Int): Boolean = sub(id) == S_ELEV
    /** escala da TV por nível de tamanho (0 = normal) */
    val TV_SCALE = floatArrayOf(1f, 1.5f, 2f, 3f)
    fun sittable(id: Int): Boolean { val s = sub(id); if (s >= 100) return FurnB.sittable(s); return s == S_CHAIR || s == S_CHAIR2 || s == S_STOOL || s == S_ARM || s == S_OFFICE || s == S_SOFA }
    fun isTable(id: Int): Boolean { val s = sub(id); if (s >= 100) return FurnB.isTable(s); return s == S_TABLE || (s in 20..26) }
    fun seatH(s: Int): Float = when (s) { S_STOOL -> 0.56f; S_ARM -> 0.5f; S_OFFICE -> 0.62f; S_SOFA -> 0.5f; S_CHAIR2 -> 0.6f; else -> if (s >= 100) FurnB.seatH(s) else 0.58f }
    fun topH(s: Int): Float = when (s) {
        S_TABLE, S_TDARK, S_TMARB, S_TROUND, S_TROUNDM -> 0.94f; S_TCLOTH -> 0.96f; S_COFFEE -> 0.48f
        S_DESK -> 0.92f; S_DRESS -> 0.9f; S_SHELF -> 1f; else -> if (s >= 100) FurnB.topH(s) else 0f
    }
    private fun hh(x: Int, z: Int, k: Int): Float {
        var n = x * 374761393 + z * 668265263 + k * 1442695041
        n = (n xor (n ushr 13)) * 1274126177; n = n xor (n ushr 16)
        return (n and 0xFFFF) / 65535f
    }
    fun baseOff(w: World, x: Int, y: Int, z: Int, s: Int): Float {
        if (s >= 100) { if (!FurnB.small(s)) return 0f } else if (s != S_PC && s != S_TV && s != S_LAMP && s != S_PLANT) return 0f
        val th = topH(sub(w.get(x, y - 1, z)))
        return if (th > 0f) th - 1f else 0f
    }

    /** direção da frente: 0 = +Z, 1 = -Z, 2 = +X, 3 = -X */
    fun facing(w: World, x: Int, y: Int, z: Int, s: Int): Int {
        val dx = intArrayOf(0, 0, 1, -1); val dz = intArrayOf(1, -1, 0, 0)
        val seat = s == S_CHAIR || s == S_CHAIR2 || s == S_STOOL || s == S_ARM || s == S_OFFICE || (s >= 100 && FurnB.faceTable(s))
        if (seat) for (d in 0..3) if (isTable(w.get(x + dx[d], y, z + dz[d]))) return d
        for (d in 0..3) { val bk = w.get(x - dx[d], y, z - dz[d]); if (B.solid(bk) && B.shape(bk) == 0) return d }
        if (s == S_SOFA || (s >= 100 && FurnB.sofaLike(s))) {
            val me = w.get(x, y, z)
            if (w.get(x + 1, y, z) == me || w.get(x - 1, y, z) == me) return 0
            if (w.get(x, y, z + 1) == me || w.get(x, y, z - 1) == me) return 2
        }
        return if (s == S_CHAIR || s == S_STOOL) (hh(x, z, 5) * 4f).toInt().coerceIn(0, 3) else 0
    }

    fun box(w: World, id: Int, x: Int, y: Int, z: Int): FloatArray? {
        val s = sub(id)
        val b: FloatArray = when (s) {
            S_CHAIR, S_CHAIR2, S_OFFICE -> floatArrayOf(0.12f, 0f, 0.12f, 0.88f, 1f, 0.88f)
            S_STOOL -> floatArrayOf(0.16f, 0f, 0.16f, 0.84f, 0.56f, 0.84f)
            S_ARM -> floatArrayOf(0.05f, 0f, 0.05f, 0.95f, 0.88f, 0.95f)
            S_SOFA -> floatArrayOf(0f, 0f, 0f, 1f, 0.9f, 1f)
            S_TABLE, S_TDARK, S_TMARB, S_TCLOTH, S_DESK -> floatArrayOf(0f, 0f, 0f, 1f, topH(s), 1f)
            S_TROUND, S_TROUNDM -> floatArrayOf(0.04f, 0f, 0.04f, 0.96f, 0.94f, 0.96f)
            S_COFFEE -> floatArrayOf(0.04f, 0f, 0.04f, 0.96f, 0.48f, 0.96f)
            S_BED -> floatArrayOf(0f, 0f, 0f, 1f, 0.65f, 1f)
            S_SHELF, S_WARD -> floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f)
            S_DRESS -> floatArrayOf(0f, 0f, 0f, 1f, 0.9f, 1f)
            S_FRIDGE -> floatArrayOf(0.05f, 0f, 0.05f, 0.95f, 1f, 0.95f)
            S_PC -> floatArrayOf(0.08f, 0f, 0.08f, 0.92f, 0.74f, 0.92f)
            S_TV -> floatArrayOf(0.03f, 0f, 0.03f, 0.97f, 0.88f, 0.97f)
            S_LAMP -> floatArrayOf(0.18f, 0f, 0.18f, 0.82f, 0.76f, 0.82f)
            S_PLANT -> floatArrayOf(0.18f, 0f, 0.18f, 0.82f, 0.85f, 0.82f)
            S_RUGR, S_RUGB -> floatArrayOf(0f, 0f, 0f, 1f, 0.04f, 1f)
            S_ELEV -> floatArrayOf(0f, 0f, 0f, 0.3f, 1f, 0.3f)
            else -> (if (s >= 100) FurnB.box(w, x, y, z, s) else null) ?: return null
        }
        val oy = if (y >= 0) baseOff(w, x, y, z, s) else 0f
        if (oy != 0f) { b[1] += oy; b[4] += oy }
        return b
    }

    // ---------- pincel: caixas e cilindros com UV pela posição (a textura não "amassa" nas peças pequenas) ----------
    internal class C(val o: MeshBuf, val x: Int, val y: Int, val z: Int, val vr: Float) {
        var rot = 0; var oy = 0f; var lit = false; var off = false   // off = luz apagada: ignora o brilho próprio
        var sc = 1f   // escala do móvel (TV grande): cresce a partir do centro da base, pode passar da célula
        private fun rx(a: Float, c: Float) = when (rot) { 0 -> a; 1 -> 1f - a; 2 -> c; else -> 1f - c }
        private fun rz(a: Float, c: Float) = when (rot) { 0 -> c; 1 -> 1f - c; 2 -> 1f - a; else -> a }
        private fun v(t: Int, px: Float, py: Float, pz: Float, u: Float, vv: Float, s: Float) =
            if (sc == 1f) o.vert(x + px, y + py, z + pz, s, s, s, Atlas.u(t, u.coerceIn(0f, 1f)), Atlas.v(t, vv.coerceIn(0f, 1f)))
            else o.vert(x + 0.5f + (px - 0.5f) * sc, y + oy + (py - oy) * sc, z + 0.5f + (pz - 0.5f) * sc, s, s, s, Atlas.u(t, u.coerceIn(0f, 1f)), Atlas.v(t, vv.coerceIn(0f, 1f)))
        private fun fin() { o.tri(0, 1, 2); o.tri(0, 2, 3); o.tri(0, 2, 1); o.tri(0, 3, 2); o.vc += 4 }

        fun box(top: Int, side: Int, bot: Int, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, glow: Boolean = false) {
            val ax = rx(x0, z0); val bx = rx(x1, z1); val az = rz(x0, z0); val bz = rz(x1, z1)
            val a0 = min(ax, bx); val a1 = max(ax, bx); val c0 = min(az, bz); val c1 = max(az, bz)
            val ya = y0 + oy; val yb = y1 + oy
            val lt = (lit || glow) && !off
            val xs = floatArrayOf(a0, a1, a1, a0); val zs = floatArrayOf(c0, c0, c1, c1)
            val shd = floatArrayOf(0.8f, 0.7f, 0.9f, 0.76f)
            for (i in 0 until 4) {
                val j = (i + 1) and 3
                val horiz = i == 0 || i == 2
                val ui = if (horiz) xs[i] else zs[i]; val uj = if (horiz) xs[j] else zs[j]
                val sT = if (lt) 1f else shd[i] * vr; val sB = sT * 0.88f
                v(side, xs[i], ya, zs[i], ui, 1f - y0, sB); v(side, xs[j], ya, zs[j], uj, 1f - y0, sB)
                v(side, xs[j], yb, zs[j], uj, 1f - y1, sT); v(side, xs[i], yb, zs[i], ui, 1f - y1, sT)
                fin()
            }
            val st = if (lt) 1f else vr
            v(top, a0, yb, c0, a0, c0, st); v(top, a1, yb, c0, a1, c0, st); v(top, a1, yb, c1, a1, c1, st); v(top, a0, yb, c1, a0, c1, st); fin()
            val sb = if (lt) 0.8f else 0.55f * vr
            v(bot, a0, ya, c0, a0, c0, sb); v(bot, a1, ya, c0, a1, c0, sb); v(bot, a1, ya, c1, a1, c1, sb); v(bot, a0, ya, c1, a0, c1, sb); fin()
        }

        fun cyl(top: Int, side: Int, bot: Int, cx: Float, cz: Float, r0: Float, r1: Float, y0: Float, y1: Float, n: Int = 14, glow: Boolean = false) {
            val ox = rx(cx, cz); val oz = rz(cx, cz)
            val ya = y0 + oy; val yb = y1 + oy
            val lt = (lit || glow) && !off
            fun sh(th: Float) = if (lt) 1f else vr * (0.72f + 0.28f * (0.5f + 0.5f * cos(th - 0.9f)))
            for (i in 0 until n) {
                val t0 = TAU * i / n; val t1 = TAU * (i + 1) / n
                val u0 = i / n.toFloat(); val u1 = (i + 1) / n.toFloat()
                v(side, ox + r0 * cos(t0), ya, oz + r0 * sin(t0), u0, 1f - y0, sh(t0) * 0.9f)
                v(side, ox + r0 * cos(t1), ya, oz + r0 * sin(t1), u1, 1f - y0, sh(t1) * 0.9f)
                v(side, ox + r1 * cos(t1), yb, oz + r1 * sin(t1), u1, 1f - y1, sh(t1))
                v(side, ox + r1 * cos(t0), yb, oz + r1 * sin(t0), u0, 1f - y1, sh(t0))
                fin()
            }
            if (top >= 0 && r1 > 0.001f) fan(top, ox, oz, r1, yb, n, if (lt) 1f else vr)
            if (bot >= 0 && r0 > 0.001f) fan(bot, ox, oz, r0, ya, n, if (lt) 0.8f else 0.55f * vr)
        }
        private fun fan(t: Int, cx: Float, cz: Float, r: Float, yy: Float, n: Int, s: Float) {
            for (i in 0 until n) {
                val t0 = TAU * i / n; val t1 = TAU * (i + 1) / n
                v(t, cx + r * cos(t0), yy, cz + r * sin(t0), cx + r * cos(t0), cz + r * sin(t0), s)
                v(t, cx + r * cos(t1), yy, cz + r * sin(t1), cx + r * cos(t1), cz + r * sin(t1), s)
                v(t, cx, yy, cz, cx, cz, s); v(t, cx, yy, cz, cx, cz, s); fin()
            }
        }
        /** folha em losango: base, meio (esq/dir) e ponta, em coordenadas locais */
        fun leaf(t: Int, bx: Float, by: Float, bz: Float, mx: Float, my: Float, mz: Float, sx: Float, sz: Float, tx: Float, ty: Float, tz: Float, s: Float) {
            fun p(lx: Float, ly: Float, lz: Float, u: Float, vv: Float) = v(t, rx(lx, lz), ly + oy, rz(lx, lz), u, vv, if (lit && !off) 1f else s * vr)
            p(bx, by, bz, 0.5f, 1f); p(mx - sx, my, mz - sz, 0f, 0.5f); p(tx, ty, tz, 0.5f, 0f); p(mx + sx, my, mz + sz, 1f, 0.5f); fin()
        }
    }

    // ---------- interação genérica: abrir/fechar e ligar/desligar ----------
    /** 0 = sem interação extra, 1 = abre/fecha (porta, tampa), 2 = liga/desliga a luz */
    fun fKind(id: Int): Int = when (sub(id)) {
        S_WARD, S_FRIDGE -> 1
        S_LAMP -> 2
        FurnB.S_TALL, FurnB.S_WARDM, FurnB.S_CABINET, FurnB.S_TRUNK, FurnB.S_WALLCAB, FurnB.S_FRIDGE2, FurnB.S_FRIDGER,
        FurnB.S_MINI, FurnB.S_MICRO, FurnB.S_WASHER -> 1
        FurnB.S_TLAMP, FurnB.S_FLOOR, FurnB.S_ARC, FurnB.S_PEND, FurnB.S_CHAND, FurnB.S_SCONCE, FurnB.S_DESKL,
        FurnB.S_PAPER, FurnB.S_MOON, FurnB.S_LAVA -> 2
        else -> 0
    }
    const val ST_OPEN = 1; const val ST_OFF = 2
    /** rótulo do botão da mira: o que vai acontecer ao tocar */
    fun fLabel(id: Int, st: Int): String = when (fKind(id)) {
        1 -> if (st and ST_OPEN != 0) "FECHAR" else "ABRIR"
        2 -> if (st and ST_OFF != 0) "LIGAR" else "DESLIGAR"
        else -> ""
    }
    /** frente (z), retângulo da abertura [x0, y0, x1, y1] de cada móvel que abre; null = sem desenho de abertura */
    private fun openSpec(s: Int): FloatArray? = when (s) {
        S_WARD -> floatArrayOf(1f, 0.08f, 0.06f, 0.92f, 0.94f)
        S_FRIDGE -> floatArrayOf(0.95f, 0.1f, 0.05f, 0.9f, 0.95f)
        FurnB.S_TALL, FurnB.S_WARDM, FurnB.S_CABINET, FurnB.S_FRIDGE2, FurnB.S_FRIDGER -> floatArrayOf(0.98f, 0.1f, 0.06f, 0.9f, 0.94f)
        FurnB.S_WALLCAB -> floatArrayOf(0.5f, 0.1f, 0.46f, 0.9f, 0.96f)
        FurnB.S_MINI -> floatArrayOf(0.92f, 0.12f, 0.05f, 0.88f, 0.57f)
        FurnB.S_MICRO -> floatArrayOf(0.86f, 0.1f, 0.06f, 0.9f, 0.44f)
        FurnB.S_WASHER -> floatArrayOf(0.96f, 0.16f, 0.14f, 0.84f, 0.62f)
        else -> null
    }
    /** desenha por cima do móvel fechado: vão escuro, prateleira e a porta aberta (ou a tampa levantada, no baú) */
    private fun openOverlay(c: C, T: IntArray, id: Int, s: Int) {
        val bk = T[T_BLACK]
        val dt = if (s >= 100) Blk.fp(id)[0] else if (s == S_FRIDGE) T[T_WHITE] else T[T_OAK]
        if (s == FurnB.S_TRUNK) {
            c.box(bk, bk, bk, 0.1f, 0.5f, 0.14f, 0.9f, 0.565f, 0.86f)
            c.box(dt, dt, dt, 0.06f, 0.55f, 0.02f, 0.94f, 1.05f, 0.1f)
            return
        }
        val o = openSpec(s) ?: return
        val fd = o[0]; val x0 = o[1]; val y0 = o[2]; val x1 = o[3]; val y1 = o[4]
        c.box(bk, bk, bk, x0, y0, fd - 0.35f, x1, y1, fd + 0.012f)
        val ym = (y0 + y1) / 2f
        c.box(dt, dt, dt, x0 + 0.02f, ym, fd - 0.3f, x1 - 0.02f, ym + 0.035f, fd + 0.02f)
        if (y1 - y0 > 0.7f) { val yl = y0 + (y1 - y0) * 0.25f; c.box(dt, dt, dt, x0 + 0.02f, yl, fd - 0.3f, x1 - 0.02f, yl + 0.03f, fd + 0.02f) }
        c.box(dt, dt, dt, x0, y0, fd + 0.014f, x0 + 0.05f, y1, fd + 0.014f + (x1 - x0) * 0.85f)
    }

    fun emit(o: MeshBuf, w: World, x: Int, y: Int, z: Int, id: Int, vr: Float) {
        val s = sub(id)
        val T = Blk.fts
        val c = C(o, x, y, z, vr)
        c.lit = B.light(id)
        c.oy = baseOff(w, x, y, z, s)
        val fk = fKind(id)
        val fst = if (fk != 0 && y >= 0) w.fStateOf(x, y, z) else 0
        if (fk == 2 && (fst and ST_OFF) != 0) c.off = true
        when (s) {
            S_CHAIR -> { c.rot = facing(w, x, y, z, s); chair(c, T) }
            S_CHAIR2 -> { c.rot = facing(w, x, y, z, s); chair2(c, T) }
            S_STOOL -> { c.rot = facing(w, x, y, z, s); stool(c, T) }
            S_ARM -> { c.rot = facing(w, x, y, z, s); armchair(c, T) }
            S_OFFICE -> { c.rot = facing(w, x, y, z, s); office(c, T) }
            S_SOFA -> { c.rot = facing(w, x, y, z, s); sofa(c, T, w, x, y, z, id) }
            S_TABLE, S_TDARK, S_TMARB, S_TCLOTH, S_DESK -> table(c, T, w, x, y, z, id, s)
            S_TROUND, S_TROUNDM -> roundTable(c, T, s)
            S_COFFEE -> coffee(c, T)
            S_BED -> bed(c, T, w, x, y, z, id)
            S_SHELF -> { c.rot = facing(w, x, y, z, s); shelf(c, T, w, x, y, z, id) }
            S_WARD -> { c.rot = facing(w, x, y, z, s); wardrobe(c, T) }
            S_DRESS -> { c.rot = facing(w, x, y, z, s); dresser(c, T) }
            S_FRIDGE -> { c.rot = facing(w, x, y, z, s); fridge(c, T) }
            S_PC -> { c.rot = facing(w, x, y, z, s); computer(c, T) }
            S_TV -> { c.rot = facing(w, x, y, z, s); c.sc = TV_SCALE[w.tvLevel(x, y, z).coerceIn(0, TV_SCALE.size - 1)]; tv(c, T) }
            S_LAMP -> lamp(c, T)
            S_ELEV -> { }
            S_PLANT -> plant(c, T, x, z)
            S_RUGR -> c.box(T[T_RUGR], T[T_RUGR], T[T_RUGR], 0f, 0f, 0f, 1f, 0.04f, 1f)
            S_RUGB -> c.box(T[T_RUGB], T[T_RUGB], T[T_RUGB], 0f, 0f, 0f, 1f, 0.04f, 1f)
            else -> if (s >= 100) FurnB.emit(c, w, x, y, z, id, s)
        }
        if (fk == 1 && (fst and ST_OPEN) != 0) {
            openOverlay(c, T, id, s)
        }
    }

    // ---------- cadeiras ----------
    private fun chair(c: C, T: IntArray) {
        val wd = T[T_OAK]; val dk = T[T_OAKD]; val cu = T[T_FABR]
        c.box(dk, dk, dk, 0.16f, 0f, 0.72f, 0.27f, 0.42f, 0.83f); c.box(dk, dk, dk, 0.73f, 0f, 0.72f, 0.84f, 0.42f, 0.83f)
        c.box(dk, dk, dk, 0.16f, 0f, 0.16f, 0.27f, 0.97f, 0.27f); c.box(dk, dk, dk, 0.73f, 0f, 0.16f, 0.84f, 0.97f, 0.27f)
        c.box(wd, wd, wd, 0.145f, 0.97f, 0.145f, 0.285f, 1f, 0.285f); c.box(wd, wd, wd, 0.715f, 0.97f, 0.145f, 0.855f, 1f, 0.285f)
        c.box(dk, dk, dk, 0.27f, 0.16f, 0.74f, 0.73f, 0.21f, 0.81f); c.box(dk, dk, dk, 0.27f, 0.16f, 0.18f, 0.73f, 0.21f, 0.25f)
        c.box(dk, dk, dk, 0.18f, 0.16f, 0.27f, 0.25f, 0.21f, 0.73f); c.box(dk, dk, dk, 0.75f, 0.16f, 0.27f, 0.82f, 0.21f, 0.73f)
        c.box(wd, wd, dk, 0.14f, 0.42f, 0.14f, 0.86f, 0.49f, 0.86f)
        c.box(cu, cu, cu, 0.19f, 0.49f, 0.27f, 0.81f, 0.55f, 0.83f)
        c.box(cu, cu, cu, 0.17f, 0.55f, 0.25f, 0.83f, 0.59f, 0.85f)
        for (k in 0 until 3) { val x0 = 0.31f + k * 0.16f; c.box(wd, wd, wd, x0, 0.49f, 0.18f, x0 + 0.08f, 0.84f, 0.25f) }
        c.box(wd, wd, wd, 0.14f, 0.84f, 0.14f, 0.86f, 0.96f, 0.3f)
    }

    private fun chair2(c: C, T: IntArray) {
        val dk = T[T_DARKD]; val dm = T[T_DARK]; val fb = T[T_FABB]; val gd = T[T_GOLD]
        for (px in floatArrayOf(0.17f, 0.75f)) {
            c.box(dk, dk, dk, px, 0.04f, 0.73f, px + 0.08f, 0.42f, 0.81f)
            c.box(gd, gd, gd, px - 0.005f, 0f, 0.725f, px + 0.085f, 0.04f, 0.815f)
            c.box(dk, dk, dk, px, 0.04f, 0.17f, px + 0.08f, 0.96f, 0.25f)
            c.box(gd, gd, gd, px - 0.005f, 0f, 0.165f, px + 0.085f, 0.04f, 0.255f)
        }
        c.box(dm, dm, dk, 0.14f, 0.38f, 0.14f, 0.86f, 0.46f, 0.86f)
        c.box(fb, fb, fb, 0.17f, 0.46f, 0.2f, 0.83f, 0.58f, 0.86f)
        c.box(dm, dm, dm, 0.15f, 0.92f, 0.14f, 0.85f, 1f, 0.28f)
        c.box(fb, fb, fb, 0.25f, 0.58f, 0.19f, 0.75f, 0.92f, 0.27f)
        c.box(gd, gd, gd, 0.47f, 0.74f, 0.27f, 0.53f, 0.8f, 0.285f)
    }

    private fun stool(c: C, T: IntArray) {
        val dk = T[T_DARKD]; val fg = T[T_FABG]
        for (sx in 0..1) for (sz in 0..1) {
            val px = if (sx == 0) 0.24f else 0.68f; val pz = if (sz == 0) 0.24f else 0.68f
            c.box(dk, dk, dk, px, 0f, pz, px + 0.08f, 0.44f, pz + 0.08f)
        }
        c.box(dk, dk, dk, 0.32f, 0.2f, 0.26f, 0.68f, 0.25f, 0.3f); c.box(dk, dk, dk, 0.32f, 0.2f, 0.7f, 0.68f, 0.25f, 0.74f)
        c.box(dk, dk, dk, 0.26f, 0.2f, 0.32f, 0.3f, 0.25f, 0.68f); c.box(dk, dk, dk, 0.7f, 0.2f, 0.32f, 0.74f, 0.25f, 0.68f)
        c.cyl(dk, dk, dk, 0.5f, 0.5f, 0.31f, 0.31f, 0.44f, 0.5f, 12)
        c.cyl(fg, fg, fg, 0.5f, 0.5f, 0.33f, 0.29f, 0.5f, 0.58f, 12)
    }

    private fun armchair(c: C, T: IntArray) {
        val fr = T[T_FABR]; val dk = T[T_DARKD]; val cr = T[T_CREAM]
        for (sx in 0..1) for (sz in 0..1) {
            val px = if (sx == 0) 0.1f else 0.82f; val pz = if (sz == 0) 0.12f else 0.82f
            c.box(dk, dk, dk, px, 0f, pz, px + 0.08f, 0.1f, pz + 0.08f)
        }
        c.box(fr, fr, fr, 0.06f, 0.1f, 0.08f, 0.94f, 0.34f, 0.92f)
        c.box(fr, fr, fr, 0.06f, 0.34f, 0.08f, 0.94f, 0.9f, 0.3f)
        c.box(fr, fr, fr, 0.06f, 0.34f, 0.3f, 0.22f, 0.62f, 0.92f); c.box(fr, fr, fr, 0.78f, 0.34f, 0.3f, 0.94f, 0.62f, 0.92f)
        c.box(fr, fr, fr, 0.04f, 0.62f, 0.28f, 0.24f, 0.69f, 0.94f); c.box(fr, fr, fr, 0.76f, 0.62f, 0.28f, 0.96f, 0.69f, 0.94f)
        c.box(cr, cr, cr, 0.23f, 0.34f, 0.3f, 0.77f, 0.5f, 0.9f)
        c.box(cr, cr, cr, 0.24f, 0.5f, 0.3f, 0.76f, 0.84f, 0.42f)
    }

    private fun office(c: C, T: IntArray) {
        val gr = T[T_GREY]; val st = T[T_STEEL]; val bk = T[T_BLACK]
        c.box(st, st, st, 0.1f, 0.06f, 0.46f, 0.9f, 0.1f, 0.54f); c.box(st, st, st, 0.46f, 0.06f, 0.1f, 0.54f, 0.1f, 0.9f)
        for (sx in 0..1) for (sz in 0..1) { val px = if (sx == 0) 0.08f else 0.82f; val pz = if (sz == 0) 0.08f else 0.82f; c.box(bk, bk, bk, px, 0f, pz, px + 0.1f, 0.07f, pz + 0.1f) }
        c.cyl(st, st, st, 0.5f, 0.5f, 0.06f, 0.06f, 0.1f, 0.4f, 8)
        c.box(bk, bk, bk, 0.2f, 0.4f, 0.2f, 0.8f, 0.5f, 0.8f); c.box(gr, gr, gr, 0.22f, 0.5f, 0.24f, 0.78f, 0.56f, 0.8f)
        c.box(st, st, st, 0.46f, 0.5f, 0.16f, 0.54f, 0.64f, 0.24f)
        c.box(gr, gr, gr, 0.2f, 0.6f, 0.1f, 0.8f, 0.9f, 0.22f); c.box(gr, gr, gr, 0.26f, 0.9f, 0.1f, 0.74f, 1f, 0.22f)
        for (px in floatArrayOf(0.12f, 0.8f)) { c.box(st, st, st, px, 0.56f, 0.4f, px + 0.06f, 0.64f, 0.44f); c.box(bk, bk, bk, px - 0.01f, 0.64f, 0.3f, px + 0.07f, 0.68f, 0.66f) }
    }

    private fun sofa(c: C, T: IntArray, w: World, x: Int, y: Int, z: Int, id: Int) {
        val fb = T[T_FABB]; val dk = T[T_DARKD]
        val rx = intArrayOf(1, -1, 0, 0)[c.rot]; val rz = intArrayOf(0, 0, -1, 1)[c.rot]
        val nR = w.get(x + rx, y, z + rz) == id; val nL = w.get(x - rx, y, z - rz) == id
        val l0 = if (nL) 0f else 0.2f; val r1 = if (nR) 1f else 0.8f
        for (px in floatArrayOf(0.06f, 0.88f)) for (pz in floatArrayOf(0.08f, 0.86f)) c.box(dk, dk, dk, px, 0f, pz, px + 0.06f, 0.08f, pz + 0.06f)
        c.box(fb, fb, fb, 0f, 0.08f, 0.04f, 1f, 0.3f, 0.96f)
        c.box(fb, fb, fb, l0 + 0.005f, 0.3f, 0.28f, r1 - 0.005f, 0.48f, 0.96f)
        c.box(fb, fb, fb, 0f, 0.3f, 0.04f, 1f, 0.9f, 0.28f)
        c.box(fb, fb, fb, 0.02f, 0.9f, 0.04f, 0.98f, 0.96f, 0.26f)
        if (!nL) { c.box(fb, fb, fb, 0f, 0.3f, 0.04f, 0.2f, 0.62f, 0.96f); c.box(fb, fb, fb, -0.01f, 0.62f, 0.02f, 0.21f, 0.68f, 0.98f) }
        if (!nR) { c.box(fb, fb, fb, 0.8f, 0.3f, 0.04f, 1f, 0.62f, 0.96f); c.box(fb, fb, fb, 0.79f, 0.62f, 0.02f, 1.01f, 0.68f, 0.98f) }
    }

    // ---------- mesas ----------
    private fun table(c: C, T: IntArray, w: World, x: Int, y: Int, z: Int, id: Int, s: Int) {
        fun t(dx: Int, dz: Int) = w.get(x + dx, y, z + dz) == id
        val nW = t(-1, 0); val nE = t(1, 0); val nN = t(0, -1); val nS = t(0, 1)
        val m = when (s) {
            S_TDARK -> intArrayOf(T[T_DARK], T[T_DARKD], T[T_DARKD])
            S_TMARB -> intArrayOf(T[T_MARB], T[T_MARB], T[T_GOLD])
            S_TCLOTH -> intArrayOf(T[T_GING], T[T_GING], T[T_OAKD])
            else -> intArrayOf(T[T_OAK], T[T_OAKD], T[T_OAKD])
        }
        val tp = m[0]; val ed = m[1]; val lg = m[2]
        val x0 = if (nW) 0f else 0.03f; val x1 = if (nE) 1f else 0.97f
        val z0 = if (nN) 0f else 0.03f; val z1 = if (nS) 1f else 0.97f
        if (s == S_TCLOTH) {
            c.box(tp, tp, tp, 0f, 0.88f, 0f, 1f, 0.96f, 1f)
            if (!nN) c.box(tp, tp, tp, 0f, 0.6f, 0f, 1f, 0.9f, 0.025f)
            if (!nS) c.box(tp, tp, tp, 0f, 0.6f, 0.975f, 1f, 0.9f, 1f)
            if (!nW) c.box(tp, tp, tp, 0f, 0.6f, 0f, 0.025f, 0.9f, 1f)
            if (!nE) c.box(tp, tp, tp, 0.975f, 0.6f, 0f, 1f, 0.9f, 1f)
        } else {
            val ty0 = if (s == S_TMARB) 0.77f else 0.8f; val ty1 = if (s == S_DESK) 0.92f else 0.94f
            c.box(tp, ed, ed, x0, ty0, z0, x1, ty1, z1)
            val ax0 = if (nW) 0f else 0.09f; val ax1 = if (nE) 1f else 0.91f
            val az0 = if (nN) 0f else 0.09f; val az1 = if (nS) 1f else 0.91f
            val ay0 = if (s == S_TMARB) 0.72f else 0.69f
            if (s != S_DESK) {
                if (!nN) c.box(lg, lg, lg, ax0, ay0, 0.09f, ax1, ty0, 0.13f)
                if (!nS) c.box(lg, lg, lg, ax0, ay0, 0.87f, ax1, ty0, 0.91f)
                if (!nW) c.box(lg, lg, lg, 0.09f, ay0, az0, 0.13f, ty0, az1)
                if (!nE) c.box(lg, lg, lg, 0.87f, ay0, az0, 0.91f, ty0, az1)
            } else {
                if (!nW) c.box(ed, ed, ed, 0.03f, 0f, 0.06f, 0.1f, 0.8f, 0.94f)
                if (!nE) c.box(ed, ed, ed, 0.9f, 0f, 0.06f, 0.97f, 0.8f, 0.94f)
                c.box(tp, tp, tp, ax0 + 0.01f, 0.66f, 0.1f, ax1 - 0.01f, 0.8f, 0.9f)
                c.box(T[T_GOLD], T[T_GOLD], T[T_GOLD], 0.46f, 0.7f, 0.9f, 0.54f, 0.76f, 0.93f)
            }
        }
        if (s == S_DESK) return
        for (sx in 0..1) for (sz in 0..1) {
            val nx = if (sx == 0) nW else nE; val nz = if (sz == 0) nN else nS
            val dg = t(if (sx == 0) -1 else 1, if (sz == 0) -1 else 1)
            if (!((!nx && !nz) || (nx && nz && !dg))) continue
            val cx = if (sx == 0) 0.14f else 0.86f; val cz = if (sz == 0) 0.14f else 0.86f
            when (s) {
                S_TDARK -> {
                    c.box(lg, lg, lg, cx - 0.06f, 0.08f, cz - 0.06f, cx + 0.06f, 0.6f, cz + 0.06f)
                    c.box(lg, lg, lg, cx - 0.08f, 0.6f, cz - 0.08f, cx + 0.08f, 0.8f, cz + 0.08f)
                    c.box(lg, lg, lg, cx - 0.075f, 0f, cz - 0.075f, cx + 0.075f, 0.08f, cz + 0.075f)
                }
                S_TMARB -> {
                    c.box(lg, lg, lg, cx - 0.07f, 0.73f, cz - 0.07f, cx + 0.07f, 0.77f, cz + 0.07f)
                    c.box(lg, lg, lg, cx - 0.04f, 0.1f, cz - 0.04f, cx + 0.04f, 0.73f, cz + 0.04f)
                    c.box(lg, lg, lg, cx - 0.055f, 0f, cz - 0.055f, cx + 0.055f, 0.1f, cz + 0.055f)
                }
                S_TCLOTH -> c.box(lg, lg, lg, cx - 0.06f, 0f, cz - 0.06f, cx + 0.06f, 0.62f, cz + 0.06f)
                else -> {
                    c.box(lg, lg, lg, cx - 0.065f, 0.06f, cz - 0.065f, cx + 0.065f, 0.69f, cz + 0.065f)
                    c.box(lg, lg, lg, cx - 0.09f, 0f, cz - 0.09f, cx + 0.09f, 0.06f, cz + 0.09f)
                }
            }
        }
    }

    private fun roundTable(c: C, T: IntArray, s: Int) {
        val mb = s == S_TROUNDM
        val tp = if (mb) T[T_MARB] else T[T_OAK]; val ed = if (mb) T[T_MARB] else T[T_OAKD]
        val tr = if (mb) T[T_GOLD] else T[T_OAKD]; val pd = if (mb) T[T_GOLD] else T[T_OAKD]
        c.cyl(tp, ed, ed, 0.5f, 0.5f, 0.48f, 0.48f, 0.82f, 0.94f, 20)
        c.cyl(tr, tr, tr, 0.5f, 0.5f, 0.4f, 0.46f, 0.76f, 0.82f, 20)
        c.cyl(pd, pd, pd, 0.5f, 0.5f, 0.09f, 0.09f, 0.1f, 0.76f, 10)
        c.cyl(pd, pd, pd, 0.5f, 0.5f, 0.3f, 0.12f, 0f, 0.1f, 14)
    }

    private fun coffee(c: C, T: IntArray) {
        val tp = T[T_BIRCH]; val lg = T[T_OAKD]
        c.box(tp, tp, lg, 0.04f, 0.4f, 0.04f, 0.96f, 0.48f, 0.96f)
        c.box(lg, lg, lg, 0.1f, 0.14f, 0.1f, 0.9f, 0.18f, 0.9f)
        for (sx in 0..1) for (sz in 0..1) { val px = if (sx == 0) 0.07f else 0.85f; val pz = if (sz == 0) 0.07f else 0.85f; c.box(lg, lg, lg, px, 0f, pz, px + 0.08f, 0.4f, pz + 0.08f) }
    }

    // ---------- cama ----------
    private fun bed(c: C, T: IntArray, w: World, x: Int, y: Int, z: Int, id: Int) {
        val zAxis = w.get(x, y, z - 1) == id || w.get(x, y, z + 1) == id || (w.get(x - 1, y, z) != id && w.get(x + 1, y, z) != id)
        val nH = if (zAxis) w.get(x, y, z - 1) == id else w.get(x - 1, y, z) == id
        val nF = if (zAxis) w.get(x, y, z + 1) == id else w.get(x + 1, y, z) == id
        c.rot = if (zAxis) 0 else 2
        val wd = T[T_OAK]; val dk = T[T_OAKD]; val sh = T[T_CREAM]; val bl = T[T_FABB]; val ac = T[T_FABR]
        val e0 = if (nH) 0f else 0.02f; val e1 = if (nF) 1f else 0.98f
        c.box(dk, dk, dk, 0.02f, 0.18f, e0, 0.98f, 0.38f, e1)
        if (!nH) { c.box(dk, dk, dk, 0.02f, 0f, 0.02f, 0.12f, 1f, 0.12f); c.box(dk, dk, dk, 0.88f, 0f, 0.02f, 0.98f, 1f, 0.12f) }
        if (!nF) { c.box(dk, dk, dk, 0.02f, 0f, 0.88f, 0.12f, 0.56f, 0.98f); c.box(dk, dk, dk, 0.88f, 0f, 0.88f, 0.98f, 0.56f, 0.98f) }
        c.box(sh, sh, sh, 0.06f, 0.38f, if (nH) 0f else 0.1f, 0.94f, 0.58f, if (nF) 1f else 0.9f)
        c.box(bl, bl, sh, 0.03f, 0.4f, if (nH) 0f else 0.44f, 0.97f, 0.64f, if (nF) 1f else 0.9f)
        if (!nH) {
            c.box(ac, ac, ac, 0.03f, 0.4f, 0.44f, 0.97f, 0.66f, 0.52f)
            c.box(sh, sh, sh, 0.14f, 0.58f, 0.14f, 0.86f, 0.7f, 0.4f); c.box(sh, sh, sh, 0.18f, 0.7f, 0.18f, 0.82f, 0.74f, 0.36f)
            c.box(dk, dk, dk, 0.1f, 0.18f, 0.02f, 0.9f, 0.95f, 0.1f); c.box(wd, wd, wd, 0.16f, 0.4f, 0.1f, 0.84f, 0.88f, 0.12f)
            c.box(wd, wd, wd, 0.02f, 0.95f, 0f, 0.98f, 1f, 0.12f)
        }
        if (!nF) { c.box(dk, dk, dk, 0.12f, 0.18f, 0.9f, 0.88f, 0.52f, 0.98f); c.box(wd, wd, wd, 0.16f, 0.3f, 0.88f, 0.84f, 0.46f, 0.9f) }
    }

    // ---------- estante, armário, cômoda, geladeira ----------
    private fun shelf(c: C, T: IntArray, w: World, x: Int, y: Int, z: Int, id: Int) {
        val dk = T[T_OAKD]; val wd = T[T_OAK]; val pg = T[T_CREAM]
        val rx = intArrayOf(1, -1, 0, 0)[c.rot]; val rz = intArrayOf(0, 0, -1, 1)[c.rot]
        val nR = w.get(x + rx, y, z + rz) == id; val nL = w.get(x - rx, y, z - rz) == id
        val xs = if (nL) 0f else 0.07f; val xe = if (nR) 1f else 0.93f
        c.box(dk, dk, dk, 0f, 0f, 0f, 1f, 1f, 0.07f)
        if (!nL) c.box(wd, wd, dk, 0f, 0f, 0.07f, 0.07f, 1f, 0.92f)
        if (!nR) c.box(wd, wd, dk, 0.93f, 0f, 0.07f, 1f, 1f, 0.92f)
        c.box(wd, wd, dk, 0f, 0.94f, 0.07f, 1f, 1f, 0.95f)
        c.box(dk, dk, dk, xs, 0f, 0.07f, xe, 0.07f, 0.92f)
        c.box(wd, wd, wd, xs, 0.34f, 0.07f, xe, 0.4f, 0.9f); c.box(wd, wd, wd, xs, 0.67f, 0.07f, xe, 0.73f, 0.9f)
        val base = floatArrayOf(0.07f, 0.4f, 0.73f); val room = floatArrayOf(0.27f, 0.27f, 0.21f)
        for (r in 0 until 3) {
            var px = xs + 0.02f; var i = 0
            val lim = xe - 0.12f
            while (px < lim) {
                val k = hh(x * 7 + i, z + r * 31, 9)
                val bw = 0.05f + 0.06f * hh(x * 5 + i, z, 11 + r)
                val bh = (0.15f + 0.12f * hh(x * 3 + i, z + r, 13)).coerceAtMost(room[r] - 0.01f)
                val tex = T[T_BOOK0 + (k * 6f).toInt().coerceIn(0, 5)]
                if (k > 0.1f) {
                    val lean = k > 0.93f
                    if (lean) c.box(pg, tex, tex, px, base[r], 0.2f, px + bw + 0.05f, base[r] + bh * 0.9f, 0.2f + 0.56f)
                    else c.box(pg, tex, tex, px, base[r], 0.2f, px + bw, base[r] + bh, 0.2f + 0.56f)
                }
                px += bw + 0.012f + (if (k <= 0.1f) 0.08f else 0f); i++
            }
            if (r == 1 && !nR) { val k2 = T[T_BOOK0 + 3]; c.box(pg, k2, k2, xe - 0.1f, base[r], 0.2f, xe - 0.01f, base[r] + 0.05f, 0.76f); val k3 = T[T_BOOK0 + 4]; c.box(pg, k3, k3, xe - 0.09f, base[r] + 0.05f, 0.22f, xe - 0.02f, base[r] + 0.09f, 0.74f) }
        }
    }

    private fun wardrobe(c: C, T: IntArray) {
        val dk = T[T_OAKD]; val wd = T[T_OAK]; val gd = T[T_GOLD]
        c.box(dk, dk, dk, 0.02f, 0f, 0.06f, 0.98f, 0.1f, 0.94f)
        c.box(dk, dk, dk, 0.03f, 0.1f, 0.06f, 0.97f, 0.94f, 0.92f)
        c.box(wd, wd, dk, 0f, 0.94f, 0.02f, 1f, 1f, 1f)
        for (d in 0..1) {
            val x0 = if (d == 0) 0.04f else 0.51f; val x1 = x0 + 0.45f
            c.box(wd, wd, wd, x0, 0.12f, 0.92f, x1, 0.93f, 0.96f)
            c.box(dk, dk, dk, x0 + 0.06f, 0.2f, 0.96f, x1 - 0.06f, 0.52f, 0.975f)
            c.box(dk, dk, dk, x0 + 0.06f, 0.58f, 0.96f, x1 - 0.06f, 0.86f, 0.975f)
            val kx = if (d == 0) 0.41f else 0.54f
            c.box(gd, gd, gd, kx, 0.48f, 0.96f, kx + 0.04f, 0.6f, 0.99f)
        }
    }

    private fun dresser(c: C, T: IntArray) {
        val dk = T[T_OAKD]; val wd = T[T_OAK]; val gd = T[T_GOLD]
        for (sx in 0..1) for (sz in 0..1) { val px = if (sx == 0) 0.05f else 0.85f; val pz = if (sz == 0) 0.12f else 0.82f; c.box(dk, dk, dk, px, 0f, pz, px + 0.1f, 0.12f, pz + 0.06f) }
        c.box(dk, dk, dk, 0.02f, 0.12f, 0.1f, 0.98f, 0.84f, 0.92f)
        c.box(wd, wd, dk, 0f, 0.84f, 0.06f, 1f, 0.9f, 0.97f)
        for (d in 0 until 3) {
            val y0 = 0.16f + d * 0.22f
            c.box(wd, wd, wd, 0.06f, y0, 0.92f, 0.94f, y0 + 0.18f, 0.96f)
            c.box(dk, dk, dk, 0.1f, y0 + 0.03f, 0.96f, 0.9f, y0 + 0.15f, 0.97f)
            c.box(gd, gd, gd, 0.26f, y0 + 0.07f, 0.97f, 0.32f, y0 + 0.11f, 0.995f); c.box(gd, gd, gd, 0.68f, y0 + 0.07f, 0.97f, 0.74f, y0 + 0.11f, 0.995f)
        }
    }

    private fun fridge(c: C, T: IntArray) {
        val wh = T[T_WHITE]; val st = T[T_STEEL]; val bk = T[T_BLACK]
        c.box(bk, bk, bk, 0.08f, 0f, 0.1f, 0.92f, 0.04f, 0.9f)
        c.box(wh, wh, wh, 0.06f, 0.04f, 0.08f, 0.94f, 1f, 0.92f)
        c.box(wh, wh, wh, 0.07f, 0.67f, 0.92f, 0.93f, 0.98f, 0.96f)
        c.box(wh, wh, wh, 0.07f, 0.06f, 0.92f, 0.93f, 0.65f, 0.96f)
        c.box(bk, bk, bk, 0.07f, 0.65f, 0.92f, 0.93f, 0.67f, 0.955f)
        c.box(st, st, st, 0.05f, 0.98f, 0.07f, 0.95f, 1f, 0.93f)
        c.box(st, st, st, 0.8f, 0.72f, 0.96f, 0.84f, 0.92f, 0.99f); c.box(st, st, st, 0.8f, 0.28f, 0.96f, 0.84f, 0.58f, 0.99f)
        c.box(T[T_BOOK0], T[T_BOOK0], T[T_BOOK0], 0.2f, 0.82f, 0.96f, 0.27f, 0.88f, 0.97f)
        c.box(T[T_BOOK0 + 3], T[T_BOOK0 + 3], T[T_BOOK0 + 3], 0.34f, 0.78f, 0.96f, 0.4f, 0.84f, 0.97f)
        c.box(T[T_BOOK0 + 1], T[T_BOOK0 + 1], T[T_BOOK0 + 1], 0.25f, 0.7f, 0.96f, 0.31f, 0.76f, 0.97f)
    }

    // ---------- eletrônicos e decoração ----------
    private fun computer(c: C, T: IntArray) {
        val bk = T[T_BLACK]; val st = T[T_STEEL]
        c.box(st, st, st, 0.3f, 0f, 0.3f, 0.7f, 0.03f, 0.58f); c.box(bk, bk, bk, 0.46f, 0.03f, 0.4f, 0.54f, 0.2f, 0.46f)
        c.box(bk, bk, bk, 0.1f, 0.2f, 0.36f, 0.9f, 0.72f, 0.46f)
        c.box(bk, T[T_SCREEN], bk, 0.14f, 0.24f, 0.455f, 0.86f, 0.68f, 0.47f, true)
        c.box(T[T_KEYS], st, st, 0.2f, 0f, 0.62f, 0.8f, 0.04f, 0.9f)
        c.box(bk, bk, bk, 0.84f, 0f, 0.7f, 0.92f, 0.03f, 0.8f)
        c.box(bk, bk, bk, 0f, 0f, 0.36f, 0.08f, 0.18f, 0.46f); c.box(bk, bk, bk, 0.92f, 0f, 0.36f, 1f, 0.18f, 0.46f)
    }

    private fun tv(c: C, T: IntArray) {
        val bk = T[T_BLACK]; val st = T[T_STEEL]
        c.box(st, st, st, 0.28f, 0f, 0.34f, 0.72f, 0.06f, 0.6f); c.box(bk, bk, bk, 0.44f, 0.06f, 0.44f, 0.56f, 0.22f, 0.5f)
        c.box(bk, bk, bk, 0.03f, 0.22f, 0.42f, 0.97f, 0.86f, 0.52f)
        c.box(bk, bk, bk, 0.06f, 0.25f, 0.515f, 0.94f, 0.83f, 0.53f)   // desligada = preta; ligada, o renderizador cola a página por cima
    }

    private fun lamp(c: C, T: IntArray) {
        val gd = T[T_GOLD]; val sd = T[T_SHADE]
        c.cyl(gd, gd, gd, 0.5f, 0.5f, 0.18f, 0.14f, 0f, 0.06f, 12)
        c.cyl(gd, gd, gd, 0.5f, 0.5f, 0.03f, 0.03f, 0.06f, 0.42f, 8)
        c.cyl(sd, sd, sd, 0.5f, 0.5f, 0.27f, 0.16f, 0.4f, 0.74f, 14, true)
    }

    private fun plant(c: C, T: IntArray, x: Int, z: Int) {
        val pt = T[T_POT]; val so = T[T_SOIL]
        c.cyl(pt, pt, pt, 0.5f, 0.5f, 0.2f, 0.26f, 0f, 0.28f, 12)
        c.cyl(pt, pt, pt, 0.5f, 0.5f, 0.29f, 0.29f, 0.28f, 0.34f, 12)
        c.cyl(so, so, so, 0.5f, 0.5f, 0.24f, 0.24f, 0.34f, 0.36f, 12)
        for (k in 0 until 9) {
            val a = (k + hh(x, z, 3) * 0.5f) * TAU / 9f
            val len = 0.26f + 0.12f * hh(x + k, z, 4); val up = 0.2f + 0.3f * hh(x, z + k, 5)
            val ca = cos(a); val sa = sin(a)
            val bx = 0.5f + ca * 0.04f; val bz = 0.5f + sa * 0.04f
            val mx = 0.5f + ca * len * 0.55f; val mz = 0.5f + sa * len * 0.55f
            val tx = 0.5f + ca * len; val tz = 0.5f + sa * len
            val tile = if (k % 2 == 0) T[T_LEAFA] else T[T_LEAFB]
            c.leaf(tile, bx, 0.36f, bz, mx, 0.36f + up * 0.7f, mz, -sa * 0.07f, ca * 0.07f, tx, 0.36f + up * 0.85f, tz, 0.95f)
        }
        c.box(T[T_FABR], T[T_FABR], T[T_FABR], 0.46f, 0.5f, 0.46f, 0.54f, 0.58f, 0.54f)
    }
}
