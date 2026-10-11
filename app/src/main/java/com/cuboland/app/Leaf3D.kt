package com.cuboland.app

import kotlin.math.*

/**
 * Folhas 3D por espécie.
 *  - sprite(): pixel art 16x16 de UMA folha por espécie (tipo K_SPRITE do Blk/Atlas): nervura, borda, recorte próprio.
 *  - emit(): cartões dobrados na nervura (2 quads = 8 vértices) saindo das faces expostas de cada bloco de folha,
 *    com leve queda da ponta e sombreado base -> ponta. A casca + cubo interno (World.leafFaces) continuam como base.
 */
object Leaf3D {
    const val OAK = 0; const val ABETO = 1; const val BETULA = 2; const val SELVA = 3; const val ACACIA = 4
    const val CARV_ESC = 5; const val MANGUE = 6; const val CEREJA = 7; const val OUTONO = 8; const val GINKGO = 9
    const val BAMBU = 10; const val CARMESIM = 11; const val DISTORCIDO = 12; const val MUSGO = 13

    private const val P = 3.14159265f

    // ------------------------------------------------------------ sprites
    private val OAK_PAL = intArrayOf(0x1F5A1C, 0x2E7D27, 0x3F9A32, 0x58B848, 0x7CD05E)
    private val palCache = HashMap<Int, IntArray>()

    /** 5 cores do mais escuro ao mais claro (art < 0 = verde do carvalho) */
    private fun palette(art: Int): IntArray = palCache.getOrPut(art) {
        val src = ArrayList<Int>()
        if (art < 0) for (c in OAK_PAL) src.add(c)
        else { val p = NatArt.all[art].pal; for (i in 0 until min(5, p.size)) src.add(p[i] and 0xFFFFFF) }
        if (src.isEmpty()) src.add(0x3F9A32)
        src.sortBy { ((it shr 16) and 255) * 3 + ((it shr 8) and 255) * 6 + (it and 255) }
        IntArray(5) { src[min(src.size - 1, it * src.size / 5)] }
    }

    /** pixel (px,py) do sprite da espécie `kind` (ARGB; alpha 0 = vazio) */
    fun pixel(kind: Int, art: Int, px: Int, py: Int): Int {
        val code = shape(kind, (px + 0.5f) / 16f - 0.5f, 1f - (py + 0.5f) / 16f)
        if (code == 0) return 0
        val pal = palette(art)
        val c = when (code) { 1 -> pal[1]; 2 -> pal[2]; 3 -> pal[3]; 4 -> pal[4]; else -> pal[0] }
        return (0xFF shl 24) or c
    }

    private fun fr(v: Float): Float = v - floor(v)

    /** 0 vazio, 1 borda/veio lateral, 2 meio, 3 claro, 4 nervura clara, 5 escuro. x em -0.5..0.5 (largura), t em 0..1 (base -> ponta) */
    private fun body(x: Float, t: Float, hw: Float): Int {
        val ax = abs(x)
        if (t < 0.07f && ax < 0.04f) return 4          // pecíolo
        if (hw <= 0.02f || ax >= hw) return 0
        if (ax < 0.04f) return 4                       // nervura central
        if (hw - ax < 0.06f) return 1                  // borda
        if (fr(t * 5f - ax * 2.4f) < 0.1f) return 1    // nervuras laterais
        return if (x > 0.1f && t > 0.2f) 3 else 2
    }

    private fun blob(c: FloatArray, x: Float, t: Float): Int {
        var i = 0
        while (i + 2 < c.size) {
            val dx = x - c[i]; val dy = t - c[i + 1]; val r = c[i + 2]
            val d = sqrt(dx * dx + dy * dy)
            if (d < r) return if (d > r * 0.78f) 1 else if (dx < 0f && dy > 0f) 3 else 2
            i += 3
        }
        return 0
    }

    private val CARM = floatArrayOf(-0.2f, 0.38f, 0.2f, 0.2f, 0.52f, 0.22f, 0f, 0.78f, 0.17f, -0.12f, 0.14f, 0.14f, 0.28f, 0.2f, 0.12f)
    private val MUSG = floatArrayOf(-0.3f, 0.22f, 0.17f, 0f, 0.16f, 0.19f, 0.3f, 0.24f, 0.16f, -0.16f, 0.5f, 0.17f, 0.17f, 0.55f, 0.17f, 0f, 0.8f, 0.15f)
    private val DX = floatArrayOf(-0.3f, 0f, 0.3f)
    private val DH = floatArrayOf(0.72f, 0.98f, 0.6f)

    private fun shape(kind: Int, x: Float, t: Float): Int {
        val ax = abs(x)
        when (kind) {
            OAK -> return body(x, t, 0.47f * sin(P * t).coerceAtLeast(0f).pow(0.75f) * (1f + 0.2f * sin(t * 17f)))
            ABETO -> {
                if (ax < 0.035f && t > 0.02f) return 5
                if (ax < 0.47f && t < 0.97f) {
                    val f = fr(t * 6f + ax * 2.2f)
                    if (f < 0.4f) return if (f < 0.14f) 3 else if (f < 0.3f) 2 else 1
                }
                return 0
            }
            BETULA -> return body(x, t, 0.44f * sin(P * t.pow(0.7f)).coerceAtLeast(0f) * (0.86f + 0.14f * fr(t * 9f)))
            SELVA -> {
                var hw = 0.5f * sin(P * t).coerceAtLeast(0f).pow(0.5f) * (1f - 0.3f * t * t * t)
                hw *= min(1f, (1f - t) * 7f)
                return body(x, t, hw)
            }
            ACACIA -> {
                if (ax < 0.04f && t > 0.02f && t < 0.96f) return 1
                val i = min(5, floor(t * 6f).toInt())
                val ct = (i + 0.5f) / 6f
                val dx = (ax - 0.26f) / 0.2f; val dy = (t - ct - (ax - 0.26f) * 0.25f) / 0.085f
                val q = dx * dx + dy * dy
                if (q < 1f) return if (q > 0.7f) 1 else if (dy < -0.2f) 3 else 2
                return 0
            }
            CARV_ESC -> return body(x, t, 0.5f * sin(P * t).coerceAtLeast(0f).pow(0.6f) * (0.55f + 0.45f * abs(sin(t * 3.5f * P))))
            MANGUE -> return body(x, t, 0.42f * sin(P * t.pow(0.65f)).coerceAtLeast(0f))
            CEREJA -> {
                val dy = t - 0.5f; val r = sqrt(x * x + dy * dy); val ang = atan2(x, dy)
                val pr = 0.17f + 0.3f * abs(cos(2.5f * ang)).pow(0.55f)
                if (r < 0.07f) return 5
                if (r > pr) return 0
                return if (r > pr - 0.07f) 3 else if (abs(sin(2.5f * ang)) < 0.08f && r > 0.1f) 1 else 2
            }
            OUTONO -> {
                val dy = t - 0.42f; val r = sqrt(x * x + dy * dy); val ang = atan2(x, dy)
                if (ax < 0.035f && t < 0.2f) return 1
                val rr = 0.1f + 0.4f * abs(cos(2.5f * ang)).pow(1.6f)
                if (r > rr) return 0
                if (abs(sin(2.5f * ang)) < 0.07f && r < rr * 0.9f) return 4
                return if (r > rr - 0.05f) 1 else if (x < 0f) 3 else 2
            }
            GINKGO -> {
                val dy = t + 0.12f; val r = sqrt(x * x + dy * dy); val ang = atan2(x, dy)
                if (ax < 0.035f && t < 0.16f) return 1
                if (abs(ang) >= 0.62f || r >= 1.0f + 0.05f * sin(ang * 22f)) return 0
                if (r < 0.2f) return 0
                if (ax < 0.04f && t > 0.72f) return 0
                if (fr(ang * 9f + 10f) < 0.16f) return 1
                return if (r > 0.9f) 3 else 2
            }
            BAMBU -> {
                for (s in intArrayOf(-1, 1)) {
                    val xc = s * (0.1f + 0.25f * t)
                    val hw = 0.12f * sin(P * t.pow(0.5f)).coerceAtLeast(0f)
                    val d = abs(x - xc)
                    if (d < hw) return if (d < 0.025f) 4 else if (s > 0) 3 else 2
                }
                return 0
            }
            CARMESIM -> {
                val b = blob(CARM, x, t)
                if (b != 0) return b
                return if (abs(x - 0.4f) < 0.03f && t < 0.45f) 1 else 0
            }
            DISTORCIDO -> {
                for (i in 0 until 3) {
                    val h = DH[i]
                    if (t >= h) continue
                    val hw = 0.19f * (1f - t / h); val d = abs(x - DX[i])
                    if (d < hw) return if (d < 0.025f) 4 else if (hw - d < 0.04f) 1 else if (x < DX[i]) 3 else 2
                }
                return 0
            }
            else -> return blob(MUSG, x, t)
        }
    }

    // ------------------------------------------------------------ geometria
    /** cards = cartões por bloco; len/wid = tamanho (blocos); fold = ângulo do V (rad); droop = queda da ponta; out = quanto aponta pra fora da face */
    private class Style(val cards: Int, val len: Float, val wid: Float, val fold: Float, val droop: Float, val out: Float, val fruit: Float = 0f, val fsize: Float = 0.25f)

    private val STYLES = arrayOf(
        Style(3, 0.62f, 0.50f, 0.45f, 0.25f, 0.55f, 0.3f, 0.24f),   // carvalho
        Style(3, 0.78f, 0.50f, 0.30f, 0.55f, 0.50f, 0.28f, 0.3f),   // abeto: ramos caídos
        Style(4, 0.42f, 0.36f, 0.50f, 0.45f, 0.50f, 0.2f, 0.32f),   // bétula: folhinhas pendentes
        Style(3, 0.95f, 0.75f, 0.55f, 0.35f, 0.60f, 0.28f, 0.34f),   // selva: enormes
        Style(3, 0.80f, 0.70f, 0.15f, 0.05f, 0.50f, 0.25f, 0.26f),   // acácia: achatadas em camadas
        Style(3, 0.70f, 0.55f, 0.50f, 0.30f, 0.55f, 0.25f, 0.22f),   // carvalho escuro
        Style(3, 0.60f, 0.40f, 0.50f, 0.30f, 0.50f, 0.25f, 0.34f),   // mangue
        Style(3, 0.42f, 0.42f, 0.60f, 0.05f, 0.50f, 0.35f, 0.26f),   // cerejeira: flores em concha
        Style(3, 0.50f, 0.50f, 0.40f, 0.20f, 0.50f, 0.25f, 0.26f),   // outono
        Style(3, 0.46f, 0.46f, 0.35f, 0.20f, 0.50f, 0.22f, 0.22f),   // amarelas (ginkgo)
        Style(4, 0.85f, 0.50f, 0.25f, 0.50f, 0.60f, 0.0f, 0.0f),   // bambu: lâminas longas
        Style(3, 0.55f, 0.55f, 0.50f, 0.60f, 0.50f, 0.3f, 0.3f),   // verruga carmesim
        Style(3, 0.55f, 0.55f, 0.30f, 0.00f, 0.60f, 0.3f, 0.3f),   // verruga distorcida
        Style(3, 0.38f, 0.45f, 0.60f, 0.00f, 0.40f, 0.3f, 0.2f)    // musgo
    )

    private fun h(x: Int, y: Int, z: Int, k: Int): Float {
        var n = x * 73856093 xor y * 19349663 xor z * 83492791 xor k * -1640531535
        n = n xor (n ushr 13); n *= 1274126177; n = n xor (n ushr 16)
        return (n and 0xFFFF) / 65535f
    }

    /** open[f] = a face f do bloco (mesma ordem de leafFaces: eixo = f shr 1, sinal + se f par) está exposta */
    fun emit(o: MeshBuf, x: Int, y: Int, z: Int, tile: Int, open: BooleanArray, vr: Float, maxCards: Int = 99) {
        val sp = Blk.spriteFor(tile)
        if (sp == 0) return
        val pp = FloatArray(3); val dd = FloatArray(3); val sd = FloatArray(3); val up = FloatArray(3); val eo = FloatArray(3)   // locais: chunks podem montar em threads diferentes
        val spec = Blk.specs[sp] ?: return
        val st = STYLES[spec[1].coerceIn(0, STYLES.size - 1)]
        var nOpen = 0
        for (f in 0 until 6) if (open[f]) nOpen++
        if (nOpen == 0) return
        val tx = (sp % Atlas.NT).toFloat(); val ty = (sp / Atlas.NT).toFloat()
        val nt = Atlas.NT.toFloat(); val nr = Atlas.ROWS.toFloat()
        for (k in 0 until min(st.cards, maxCards)) {
            var pick = (h(x, y, z, k * 7 + 1) * nOpen).toInt().coerceAtMost(nOpen - 1)
            var face = 0
            for (f in 0 until 6) if (open[f]) { if (pick == 0) { face = f; break }; pick-- }
            val a = face shr 1; val s = if ((face and 1) == 0) 1 else -1
            val u = (a + 1) % 3; val v = (a + 2) % 3
            pp[0] = x + 0.5f; pp[1] = y + 0.5f; pp[2] = z + 0.5f
            pp[a] += s * 0.5f
            pp[u] += (h(x, y, z, k * 7 + 2) - 0.5f) * 0.8f
            pp[v] += (h(x, y, z, k * 7 + 3) - 0.5f) * 0.8f
            // direção: pra fora da face + um pouco de lado, com queda
            val ang = h(x, y, z, k * 7 + 4) * 2f * P
            dd[0] = 0f; dd[1] = 0f; dd[2] = 0f
            dd[a] = s * st.out
            dd[u] += cos(ang) * (1f - st.out); dd[v] += sin(ang) * (1f - st.out)
            dd[1] -= st.droop
            var dl = sqrt(dd[0] * dd[0] + dd[1] * dd[1] + dd[2] * dd[2]); if (dl < 0.001f) dl = 1f
            dd[0] /= dl; dd[1] /= dl; dd[2] /= dl
            // lateral = d x (0,1,0); se d é quase vertical, usa o eixo X
            up[0] = 0f; up[1] = 1f; up[2] = 0f
            if (abs(dd[1]) > 0.96f) { up[0] = 1f; up[1] = 0f }
            sd[0] = dd[1] * up[2] - dd[2] * up[1]; sd[1] = dd[2] * up[0] - dd[0] * up[2]; sd[2] = dd[0] * up[1] - dd[1] * up[0]
            var sl = sqrt(sd[0] * sd[0] + sd[1] * sd[1] + sd[2] * sd[2]); if (sl < 0.001f) sl = 1f
            sd[0] /= sl; sd[1] /= sl; sd[2] /= sl
            // "pra cima" do cartão = side x d
            var cx = sd[1] * dd[2] - sd[2] * dd[1]; var cy = sd[2] * dd[0] - sd[0] * dd[2]; var cz = sd[0] * dd[1] - sd[1] * dd[0]
            if (cy < 0f) { cx = -cx; cy = -cy; cz = -cz }
            val len = st.len * (0.8f + 0.4f * h(x, y, z, k * 7 + 5))
            val half = st.wid * 0.5f * (0.85f + 0.3f * h(x, y, z, k * 7 + 6))
            val cf = cos(st.fold) * half; val sf = sin(st.fold) * half
            val tone = (0.7f + 0.3f * cy.coerceIn(0f, 1f)) * vr
            val tipX = pp[0] + dd[0] * len; val tipY = pp[1] + dd[1] * len; val tipZ = pp[2] + dd[2] * len
            for (side in 0..1) {
                val sg = if (side == 0) -1f else 1f
                eo[0] = sd[0] * sg * cf - cx * sf; eo[1] = sd[1] * sg * cf - cy * sf; eo[2] = sd[2] * sg * cf - cz * sf
                val ue = if (side == 0) 0f else 1f
                val ub = (tx + 0.01f + 0.5f * 0.98f) / nt; val uE = (tx + 0.01f + ue * 0.98f) / nt
                val vT = (ty + 0.01f) / nr; val vB = (ty + 0.01f + 0.98f) / nr
                val sb = tone * 0.62f; val stp = tone; val se = tone * 0.94f
                o.vert(pp[0], pp[1], pp[2], sb, sb, sb, ub, vB)                                        // base na nervura
                o.vert(tipX, tipY, tipZ, stp, stp, stp, ub, vT)                                        // ponta na nervura
                o.vert(tipX + eo[0], tipY + eo[1], tipZ + eo[2], stp * se, stp * se, stp * se, uE, vT) // ponta na borda
                o.vert(pp[0] + eo[0], pp[1] + eo[1], pp[2] + eo[2], sb * se, sb * se, sb * se, uE, vB) // base na borda
                o.tri(0, 1, 2); o.tri(0, 2, 3); o.tri(0, 2, 1); o.tri(0, 3, 2); o.vc += 4
            }
        }
        // frutinhas / florzinhas penduradas (dois quads cruzados, 8 vértices)
        val ft = Blk.fruitFor(tile)
        if (ft != 0 && st.fruit > 0f && h(x, y, z, 90) < st.fruit) {
            var pick = (h(x, y, z, 91) * nOpen).toInt().coerceAtMost(nOpen - 1)
            var face = 0
            for (f in 0 until 6) if (open[f]) { if (pick == 0) { face = f; break }; pick-- }
            val a = face shr 1; val s = if ((face and 1) == 0) 1 else -1
            val u = (a + 1) % 3; val v = (a + 2) % 3
            pp[0] = x + 0.5f; pp[1] = y + 0.5f; pp[2] = z + 0.5f
            pp[a] += s * 0.5f
            pp[u] += (h(x, y, z, 92) - 0.5f) * 0.7f
            pp[v] += (h(x, y, z, 93) - 0.5f) * 0.7f
            val sz = st.fsize * (0.85f + 0.3f * h(x, y, z, 94))
            val cx = pp[0] + (if (a == 0) s * 0.03f else 0f)
            val cz = pp[2] + (if (a == 2) s * 0.03f else 0f)
            val top = pp[1] + (if (a == 1 && s > 0) 0.05f else 0f)
            val ftx = (ft % Atlas.NT).toFloat(); val fty = (ft / Atlas.NT).toFloat()
            val u0 = (ftx + 0.01f) / nt; val u1 = (ftx + 0.99f) / nt
            val v0 = (fty + 0.01f) / nr; val v1 = (fty + 0.99f) / nr
            val sh = vr * 1.0f
            val hx = sz * 0.5f
            for (q in 0..1) {
                val ax = if (q == 0) hx else 0f; val az = if (q == 0) 0f else hx
                o.vert(cx - ax, top - sz, cz - az, sh * 0.85f, sh * 0.85f, sh * 0.85f, u0, v1)
                o.vert(cx + ax, top - sz, cz + az, sh * 0.85f, sh * 0.85f, sh * 0.85f, u1, v1)
                o.vert(cx + ax, top, cz + az, sh, sh, sh, u1, v0)
                o.vert(cx - ax, top, cz - az, sh, sh, sh, u0, v0)
                o.tri(0, 1, 2); o.tri(0, 2, 3); o.tri(0, 2, 1); o.tri(0, 3, 2); o.vc += 4
            }
        }
    }

    // ------------------------------------------------------------ sprites das frutas (16x16, cores próprias)
    private fun q2(x: Float, t: Float, cx: Float, cy: Float, rx: Float, ry: Float): Float { val a = (x - cx) / rx; val b = (t - cy) / ry; return a * a + b * b }

    private fun flower(x: Float, t: Float, petal: Int, edge: Int, mid: Int, edgeIn: Int): Int {
        val dy = t - 0.5f; val r = sqrt(x * x + dy * dy); val ang = atan2(x, dy)
        val pr = 0.15f + 0.33f * abs(cos(2.5f * ang)).pow(0.6f)
        if (r < 0.065f) return mid
        if (r > pr) return 0
        return if (r > pr - 0.07f) edge else if (abs(sin(2.5f * ang)) < 0.07f && r > 0.1f) edgeIn else petal
    }

    /** pixel da fruta/florzinha da espécie (ARGB; 0 = vazio) */
    fun fruitPixel(kind: Int, px: Int, py: Int): Int {
        val x = (px + 0.5f) / 16f - 0.5f; val t = 1f - (py + 0.5f) / 16f
        val ax = abs(x)
        val c: Int = when (kind) {
            OAK -> {   // maçã
                val q = q2(x, t, 0f, 0.4f, 0.3f, 0.29f)
                if (t > 0.66f && t < 0.9f && ax < 0.04f) 0x6B4423
                else if (t > 0.72f && t < 0.88f && x > 0.04f && x < 0.22f && (t - 0.72f) < (0.22f - x) * 0.9f + 0.02f) 0x4CA03A
                else if (q < 1f) { if (x < -0.07f && x > -0.17f && t > 0.42f && t < 0.56f) 0xFF9A86 else if (q > 0.62f) 0x9E2222 else 0xD8372D } else 0
            }
            ABETO -> {   // pinha
                val q = q2(x, t, 0f, 0.45f, 0.17f, 0.37f)
                if (ax < 0.03f && t > 0.8f && t < 0.97f) 0x4A2F18
                else if (q < 1f) { if (fr(t * 7f + ax * 3f) < 0.25f) 0x5A3A1D else if (x < 0f) 0xA06A3C else 0x86582F } else 0
            }
            BETULA -> {   // amentilho
                if (ax < 0.025f && t > 0.9f) 0x6B5A3A
                else if (ax < 0.075f && t > 0.08f && t < 0.92f) { if (fr(t * 9f) < 0.3f) 0xA3AE4F else 0xD1D86F } else 0
            }
            SELVA -> flower(x, t, 0xE8432E, 0xFF8A5A, 0xFFD43B, 0xB82A22)
            ACACIA -> {   // pompom amarelo
                val q = q2(x, t, 0f, 0.45f, 0.3f, 0.3f)
                if (ax < 0.025f && t > 0.76f) 0x5E7A2A
                else if (q < 1f) { if (q > 0.7f) 0xC9A62A else if (fr(x * 13f + t * 7f) < 0.25f) 0xFFF28A else 0xF4DB3E } else 0
            }
            CARV_ESC -> {   // bolota
                val q = q2(x, t, 0f, 0.38f, 0.21f, 0.27f)
                if (ax < 0.03f && t > 0.78f && t < 0.95f) 0x4A3520
                else if (q < 1f) { if (t > 0.5f) { if (fr((x + t) * 8f) < 0.3f) 0x3E2A18 else 0x5B4026 } else if (t < 0.17f && ax < 0.05f) 0x6B4423 else if (q > 0.7f) 0x9A7240 else 0xC89A5B } else 0
            }
            MANGUE -> {   // vagem comprida
                val hw = (if (t < 0.18f) 0.03f + 0.18f * t else 0.065f).coerceAtMost(0.07f)
                if (t < 0.04f || t > 0.95f || ax >= hw) 0
                else if (t > 0.85f) 0x5E4A2B else if (t < 0.18f) 0x4F6E27 else if (hw - ax < 0.025f) 0x5F8A2C else 0x7FA83F
            }
            CEREJA -> flower(x, t, 0xFFE3EC, 0xFFF6F9, 0xF2C94C, 0xF7A8C4)
            OUTONO -> {   // frutinhas vermelhas
                var r = 0
                val cs = floatArrayOf(-0.18f, 0.35f, 0.17f, 0.18f, 0.38f, 0.17f, 0f, 0.62f, 0.17f)
                var i = 0
                while (i < 9) { val dx = x - cs[i]; val dy = t - cs[i + 1]; val d = sqrt(dx * dx + dy * dy)
                    if (d < cs[i + 2]) { r = if (dx < -0.04f && dy > 0.04f && d < 0.1f) 0xFF9A86 else if (d > 0.13f) 0x8F1F1F else 0xC8352B; break }
                    i += 3 }
                if (r == 0 && ax < 0.02f && t > 0.7f) 0x5E4A2B else r
            }
            GINKGO -> {   // castanhas douradas
                var r = 0
                val cs = floatArrayOf(-0.14f, 0.4f, 0.19f, 0.16f, 0.46f, 0.19f)
                var i = 0
                while (i < 6) { val dx = x - cs[i]; val dy = t - cs[i + 1]; val d = sqrt(dx * dx + dy * dy)
                    if (d < cs[i + 2]) { r = if (d > 0.14f) 0xA8842A else if (dx < 0f && dy > 0f) 0xFFE27A else 0xE8C443; break }
                    i += 3 }
                if (r == 0 && ax < 0.03f && t > 0.68f) 0x6B5A2A else r
            }
            CARMESIM, DISTORCIDO -> {   // verruga brilhante
                val q = q2(x, t, 0f, 0.45f, 0.3f, 0.3f)
                val crim = kind == CARMESIM
                if (q >= 1f) 0
                else if (q > 0.72f) (if (crim) 0x9E3C16 else 0x178A82)
                else if (fr(x * 9f) + fr(t * 11f) > 1.55f) (if (crim) 0xFFC56B else 0xA8FFF5)
                else (if (crim) 0xE8742A else 0x35D6C8)
            }
            MUSGO -> flower(x, t, 0xFFFFFF, 0xF6E6EE, 0xFFD84A, 0xE8D4DE)
            else -> 0
        }
        return if (c == 0) 0 else (0xFF shl 24) or c
    }

    fun hasFruit(kind: Int): Boolean = kind in STYLES.indices && STYLES[kind].fruit > 0f
}
