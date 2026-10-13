package com.cuboland.app

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Texturas da Construção no estilo Minecraft: pixel duro numa grade de 16x16 (cada pixel = 4x4 do tile de 64), cores chapadas com
 * variação de tom pixel a pixel, e um nível de relevo 3D (-2..+2) por pixel, que vira geometria de verdade (Relief / World.reliefFace).
 * Tudo repete certinho nas bordas (16 px). Os padrões que não estão aqui continuam nos desenhos antigos (BuildPats), só que "pixelizados" em BuildArt.
 *
 * Campos de cada pixel (C): h = nível de relevo; t = tom (+ clareia, - escurece); g = rejunte (0/1); m = cor de acento (0/1, ou parcial);
 * a = opacidade; sp = brilho branco; k = linha escura (rachadura).
 */
object McPix {
    class C {
        @JvmField var h = 0; @JvmField var t = 0f; @JvmField var g = 0f; @JvmField var m = 0f
        @JvmField var a = 1f; @JvmField var sp = 0f; @JvmField var k = 0f
        @JvmField var id = 0; @JvmField var d = 0f
        fun reset() { h = 0; t = 0f; g = 0f; m = 0f; a = 1f; sp = 0f; k = 0f }
    }

    private val isNat = BooleanArray(BuildArt.COUNT).also { arr ->
        for (p in intArrayOf(
            BuildArt.CONCRETE, BuildArt.CLAY, BuildArt.PLASTER, BuildArt.BRICK, BuildArt.SUBWAY,
            BuildArt.SBRICK, BuildArt.SBRICK_CRACK, BuildArt.SBRICK_MOSS, BuildArt.COBBLE, BuildArt.COBBLE_MOSS,
            BuildArt.ROUGH0, BuildArt.ROUGH1, BuildArt.ROUGH2, BuildArt.SMOOTH, BuildArt.SPECK, BuildArt.TILES,
            BuildArt.CHISEL, BuildArt.PILLAR, BuildArt.SANDSIDE, BuildArt.OBSIDIAN, BuildArt.PLANK, BuildArt.MOSAIC,
            BuildArt.STRIP, BuildArt.RINGS, BuildArt.SHINGLE, BuildArt.METAL, BuildArt.METAL_P1, BuildArt.METAL_P2,
            BuildArt.GLASS, BuildArt.GLASS_THIN, BuildArt.AZ_PLAIN, BuildArt.AZ_DECOR, BuildArt.LOGWALL,
            BuildArt.CHECKER, BuildArt.STRIPES, BuildArt.CORRUG, BuildArt.PEBBLE, BuildArt.SHAKE, BuildArt.PAINTED,
            BuildArt.BASALT, BuildArt.TUFF, BuildArt.NETHER, BuildArt.ENDSTONE, BuildArt.PRISM, BuildArt.POLISHED, BuildArt.QUARTZ, BuildArt.LONGBRICK
        )) arr[p] = true
    }

    fun has(p: Int): Boolean = p >= 0 && p < isNat.size && isNat[p]

    // ---------------------------------------------------------------- ferramentas
    private fun hh(x: Int, y: Int, s: Int): Float = BuildKit.hash(((x % 16) + 16) % 16, ((y % 16) + 16) % 16, s)

    /** variação de tom pixel a pixel (poucos pixels mais claros / mais escuros, como no Minecraft) */
    private fun dith(x: Int, y: Int, s: Int, amp: Float): Float {
        val r = hh(x, y, s)
        return if (r < 0.10f) -amp else if (r < 0.28f) -amp * 0.5f else if (r > 0.90f) amp else if (r > 0.72f) amp * 0.5f else 0f
    }

    /** manchas de 2x2 com quebra de pixel */
    private fun blob(x: Int, y: Int, s: Int): Float = 0.6f * hh(x shr 1, y shr 1, s) + 0.4f * hh(x, y, s + 1)

    private fun pk(x: Int, y: Int): Int = x * 16 + y
    private val CRACK = intArrayOf(pk(3, 1), pk(3, 2), pk(4, 3), pk(4, 4), pk(5, 5), pk(5, 6), pk(11, 8), pk(11, 9), pk(12, 10), pk(12, 11), pk(13, 12), pk(13, 13), pk(9, 1), pk(9, 2), pk(10, 3))

    /** voronoi que repete a cada 16 px: c.d = distância até a divisa, c.id = pedra */
    private fun vor(x: Int, y: Int, cells: Int, jit: Float, seed: Int, c: C) {
        val cs = 16f / cells; val px = x + 0.5f; val py = y + 0.5f
        val ci = floor(px / cs).toInt(); val cj = floor(py / cs).toInt()
        var f1 = 1e9f; var f2 = 1e9f; var bid = 0
        for (dj in -1..1) for (di in -1..1) {
            val i = ci + di; val j = cj + dj
            val wi = ((i % cells) + cells) % cells; val wj = ((j % cells) + cells) % cells
            val qx = (i + 0.5f + (BuildKit.hash(wi, wj, seed) - 0.5f) * jit) * cs
            val qy = (j + 0.5f + (BuildKit.hash(wi, wj, seed + 7) - 0.5f) * jit) * cs
            val dx = px - qx; val dy = py - qy; val dd = sqrt(dx * dx + dy * dy)
            if (dd < f1) { f2 = f1; f1 = dd; bid = wj * cells + wi } else if (dd < f2) f2 = dd
        }
        c.d = (f2 - f1) * 0.5f; c.id = bid
    }

    /** tijolos em fileiras: rh = altura da fileira, len = comprimento; a fileira de baixo anda meio tijolo */
    private fun brickLike(c: C, x: Int, y: Int, rh: Int, len: Int, seed: Int, amp: Float, rim: Boolean) {
        val r = y / rh; val yy = y % rh
        val xs = x + (r and 1) * (len / 2)
        val xx = xs % len
        if (yy == rh - 1 || xx == len - 1) { c.g = 1f; c.h = -1; c.t = dith(x, y, seed + 5, 0.2f); return }
        c.h = 1
        if (rim && (yy == rh - 2 || xx == len - 2)) c.h = 0
        val bid = r * 5 + xs / len
        c.t = (hh(bid, r + 3, seed) - 0.5f) * amp * 2f + dith(x, y, seed + 3, 0.25f)
        if (yy == 0) c.t += 0.25f
    }

    private fun stoneBrick(c: C, x: Int, y: Int, p: Int, v: Int) {
        brickLike(c, x, y, 8, 8, 5, 0.22f, true)
        if (p == BuildArt.SBRICK_CRACK && CRACK.contains(pk(x, y))) { c.k = 0.7f; c.h = -1; c.g = 0f }
        if (p == BuildArt.SBRICK_MOSS && blob(x, y, 57 + v) < 0.33f) {
            c.m = 1f; c.g = 0f; c.h = max(c.h, 0) + 1; c.t = dith(x, y, 58, 0.5f)
        }
    }

    // ---------------------------------------------------------------- um pixel de um padrão (x, y = 0..15)
    fun cell(p: Int, v: Int, x: Int, y: Int, c: C) {
        c.reset()
        when (p) {
            // ---- lisos: concreto e argila chapados como no Minecraft
            BuildArt.CONCRETE -> { if (v > 0 && hh(x, y, 11 + v) > 0.94f) c.t = 0.3f }
            BuildArt.CLAY -> { c.t = dith(x, y, 21 + v, 0.3f) }
            BuildArt.PLASTER -> {
                c.t = dith(x, y, 31 + v, 0.22f)
                if (v > 0) for (i in 0 until 2) {
                    val bx = 2 + (hh(i, v, 41) * 12f).toInt(); val by = 2 + (hh(i, v, 42) * 12f).toInt()
                    if (x == bx && y == by) { c.h = 1; c.sp = 0.3f }
                }
            }
            // ---- tijolos
            BuildArt.BRICK -> brickLike(c, x, y, 4, 8, 11, 0.28f, false)
            BuildArt.SUBWAY -> {
                brickLike(c, x, y, 4, 8, 15, 0.10f, false)
                if (c.g == 0f && y % 4 == 0 && x % 8 < 4) c.sp = 0.35f
            }
            BuildArt.SBRICK, BuildArt.SBRICK_CRACK, BuildArt.SBRICK_MOSS -> stoneBrick(c, x, y, p, v)
            // ---- pedregulho: pedras arredondadas (centro mais alto) separadas por frestas escuras
            BuildArt.COBBLE, BuildArt.COBBLE_MOSS -> {
                vor(x, y, 3, 0.75f, 17 + v, c)
                if (c.d < 0.9f) { c.g = 1f; c.h = -1; c.t = dith(x, y, 19, 0.15f) }
                else {
                    c.h = if (c.d < 2.1f) 1 else 2
                    c.t = (hh(c.id, 3, 18) - 0.5f) * 0.9f + dith(x, y, 23, 0.3f)
                    if (p == BuildArt.COBBLE_MOSS && blob(x, y, 67 + v) < 0.30f) { c.m = 1f; c.h += 1; c.t = dith(x, y, 68, 0.5f) }
                }
            }
            // ---- pedras
            BuildArt.ROUGH0 -> {
                vor(x, y, 4, 0.85f, 5 + v, c)
                c.t = (hh(c.id, 5, 12) - 0.5f) * 0.9f + dith(x, y, 43, 0.28f)
                c.h = if (hh(c.id, 9, 5) > 0.5f) 1 else 0
                if (c.d < 0.75f) { c.t -= 0.5f; c.h = -1 }
            }
            BuildArt.ROUGH1 -> {
                val w = Math.round(1.6f * sin(x * 0.785398f + 1f))
                val q = y + w + 32
                if (q % 3 == 0) { c.t = -0.6f + dith(x, y, 45, 0.1f); c.h = -1 }
                else { c.h = (q / 3) % 2; c.t = (hh((q / 3) % 16, 2, 14) - 0.5f) * 0.7f + dith(x, y, 44, 0.25f) }
            }
            BuildArt.ROUGH2 -> {
                val r = hh(x, y, 60 + v)
                if (r < 0.10f) { c.t = -0.9f; c.h = -1 } else if (r > 0.92f) { c.t = 0.8f; c.h = 1 } else c.t = dith(x, y, 61, 0.2f)
            }
            BuildArt.SMOOTH -> { c.t = dith(x, y, 52, 0.18f) }
            BuildArt.SPECK -> {
                c.t = dith(x, y, 61, 0.25f)
                if (0.55f * hh(x shr 1, y shr 1, 91) + 0.45f * hh(x, y, 92) > 0.66f) { c.m = 1f; c.h = 1; c.t = (hh(x, y, 93) - 0.5f) * 0.5f }
            }
            BuildArt.TILES -> {
                val xx = x % 8; val yy = y % 8
                if (xx == 7 || yy == 7) { c.g = 1f; c.h = -1 }
                else {
                    c.h = if (xx == 6 || yy == 6) 0 else 1
                    c.t = (hh(x / 8 + (y / 8) * 2, 4, 13) - 0.5f) * 0.5f + dith(x, y, 135, 0.15f)
                    if ((xx == 1 && yy <= 2) || (yy == 1 && xx <= 2)) c.sp = 0.3f
                }
            }
            BuildArt.CHISEL -> {
                val r = min(min(x, 15 - x), min(y, 15 - y))
                when {
                    r == 0 -> { c.t = -0.5f }
                    r == 1 -> { c.h = 1; c.t = if (x == 1 || y == 1) 0.4f else if (x == 14 || y == 14) -0.3f else 0.1f }
                    r == 2 -> { c.h = -1; c.t = -0.8f }
                    r <= 4 -> { c.h = 1; c.t = dith(x, y, 142, 0.2f) }
                    else -> { c.h = 0; c.t = -0.3f + dith(x, y, 143, 0.2f) }
                }
            }
            BuildArt.PILLAR -> {   // coluna canelada sem capitel nem base: os blocos empilhados viram uma coluna só
                when (x % 4) {
                    0 -> { c.h = -1; c.t = -0.7f }
                    1 -> { c.h = 1; c.t = 0.4f }
                    2 -> { c.h = 1; c.t = 0.1f }
                    else -> { c.h = 0; c.t = -0.2f }
                }
                c.t += dith(x, y, 151, 0.08f)
            }
            BuildArt.SANDSIDE -> {
                val g1 = 3 + (x / 4) % 2; val g2 = 11 + (x / 4) % 2
                if (y == g1 || y == g2) { c.h = -1; c.t = -0.75f }
                else { c.h = if (y < g1 || y > g2) 1 else 0; c.t = dith(x, y, 163, 0.25f) }
            }
            BuildArt.OBSIDIAN -> {
                c.t = dith(x, y, 171, 0.25f)
                val b = 0.55f * hh(x shr 1, y shr 1, 172) + 0.45f * hh(x, y, 173)
                if (b > 0.9f) c.m = 0.7f else if (b > 0.72f) c.m = 0.35f
                if ((x == 2 && y == 2) || (x == 3 && y == 2) || (x == 11 && y == 9)) c.sp = 0.18f
            }
            // ---- madeiras
            BuildArt.PLANK, BuildArt.PAINTED -> {
                val r = y / 4; val yy = y % 4
                val j1 = (r * 5 + 3) % 16; val j2 = (j1 + 8) % 16
                if (yy == 3) { c.g = 1f; c.h = -1; c.t = dith(x, y, 186, 0.15f) }
                else if (x == j1 || x == j2) { c.g = 1f; c.h = -1 }
                else {
                    val seg = if (x > min(j1, j2) && x < max(j1, j2)) 1 else 0
                    c.t = (hh(r * 2 + seg, 5, 187) - 0.5f) * 0.6f + (if (hh(x / 3, y, 188) < 0.22f) -0.28f else 0f)
                    if (yy == 0) { c.t += 0.2f; c.h = 1 }
                }
            }
            BuildArt.MOSAIC -> {
                val bx = x / 8; val by = y / 8
                val horiz = (bx + by) % 2 == 0
                val q = if (horiz) y % 4 else x % 4
                if (q == 3) { c.g = 1f; c.h = -1; c.t = dith(x, y, 205, 0.15f) }
                else {
                    c.h = if (q == 0) 1 else 0
                    c.t = (hh(bx * 2 + by, if (horiz) y / 4 else x / 4, 204) - 0.5f) * 0.6f + dith(x, y, 206, 0.22f)
                }
            }
            BuildArt.STRIP -> {   // madeira lisa de fibras verticais: 3 tons por coluna, trechinhos escuros e poucas fibras fundas
                val ct = Math.round((hh(x, 3, 221) - 0.5f) * 3f) * 0.18f
                c.t = ct + (if (hh(x, y / 4, 222) < 0.22f) -0.3f else 0f) + dith(x, y, 223, 0.08f)
                if (x % 8 == 3 && hh(x, y / 3, 224) < 0.5f) { c.t -= 0.35f; c.h = -1 }
            }
            BuildArt.RINGS -> {
                val rr = max(abs(x - 7.5f), abs(y - 7.5f)).toInt()
                when {
                    rr >= 6 -> { c.t = -0.8f + dith(x, y, 231, 0.2f); c.h = 1 }
                    rr == 5 -> { c.t = -0.2f }
                    rr % 2 == 0 -> { c.t = 0.25f + dith(x, y, 232, 0.15f) }
                    else -> { c.t = -0.3f; c.h = -1 }
                }
            }
            BuildArt.LOGWALL -> {
                val yy = y % 4; val r = y / 4
                c.t = (hh(r, 5, 281) - 0.5f) * 0.5f + (if (hh(x / 3, y, 282) < 0.2f) -0.25f else 0f)
                when (yy) { 0 -> { c.h = 0; c.t += 0.25f }; 1, 2 -> c.h = 1; else -> { c.h = -1; c.g = 1f } }
            }
            BuildArt.SHINGLE -> {
                val r = y / 4; val yy = y % 4
                val xs = x + (r and 1) * 4; val xx = xs % 8
                val tone = (hh(r * 3 + xs / 8, 4, 241) - 0.5f) * 0.7f
                when (yy) {
                    0 -> { c.h = -1; c.t = -0.8f }
                    1 -> { c.h = 0; c.t = tone - 0.1f }
                    2 -> { c.h = 1; c.t = tone + 0.05f }
                    else -> { c.h = 2; c.t = tone + 0.3f }
                }
                if (xx == 7 && yy < 3) { c.g = 1f; c.h = -1 }
                if (yy == 3 && (xx == 0 || xx == 7)) { c.h = 1; c.t -= 0.2f }
                c.t += dith(x, y, 242, 0.15f)
            }
            BuildArt.SHAKE -> {
                val r = y / 4; val yy = y % 4
                val j1 = (r * 5 + 1) % 16; val j2 = (j1 + 8) % 16
                if (yy == 3) { c.g = 1f; c.h = -1 }
                else if (x == j1 || x == j2) { c.g = 1f; c.h = -1 }
                else {
                    val seg = if (x > min(j1, j2) && x < max(j1, j2)) 1 else 0
                    c.t = (hh(r * 2 + seg, 6, 291) - 0.5f) * 0.7f + (if (hh(x, y / 2, 292) < 0.2f) -0.25f else 0f)
                    if (yy == 0) { c.h = 1; c.t += 0.2f }
                }
            }
            // ---- metais: placa levantada com moldura chanfrada e rebites
            BuildArt.METAL, BuildArt.METAL_P1, BuildArt.METAL_P2 -> {
                val r = min(min(x, 15 - x), min(y, 15 - y))
                if (r == 0) c.t = -0.5f
                else if (r == 1) c.t = if (x == 1 || y == 1) 0.45f else if (x == 14 || y == 14) -0.35f else 0f
                else { c.h = 1; c.t = dith(x, y, 251, 0.14f) }
                if ((x == 3 || x == 12) && (y == 3 || y == 12)) { c.h = 2; c.t = 0.4f }
                if (p != BuildArt.METAL && blob(x, y, 257) < (if (p == BuildArt.METAL_P1) 0.28f else 0.5f)) {
                    c.m = 1f; c.h = max(c.h - 1, 0); c.t = dith(x, y, 258, 0.4f)
                }
            }
            // ---- vidros: quase invisíveis, só moldurinha e reflexo
            BuildArt.GLASS, BuildArt.GLASS_THIN -> {
                val r = min(min(x, 15 - x), min(y, 15 - y))
                c.a = 0.13f
                if (p == BuildArt.GLASS && r == 0) { c.a = 1f; c.t = 0.5f }
                if ((x + y == 9 && x in 3..6) || (x + y == 12 && x in 3..4)) { c.a = 0.6f; c.sp = 0.9f; c.t = 0.4f }
            }
            // ---- azulejos
            BuildArt.AZ_PLAIN, BuildArt.AZ_DECOR -> {
                val xx = x % 8; val yy = y % 8
                if (xx == 7 || yy == 7) { c.g = 1f; c.h = -1 }
                else {
                    c.h = if (xx == 6 || yy == 6) 0 else 1
                    c.t = (hh(x / 8, y / 8, 271) - 0.5f) * 0.25f
                    if ((xx == 1 && yy <= 2) || (yy == 1 && xx <= 2)) c.sp = 0.4f
                    if (p == BuildArt.AZ_DECOR) {
                        val dx = abs(xx - 3); val dy = abs(yy - 3)
                        if (dx + dy <= 1 || (dx == 3 && dy == 3)) { c.m = 1f; c.h = 0 }
                    }
                }
            }
            BuildArt.CHECKER -> {
                if (((x / 4) + (y / 4)) % 2 == 0) { c.m = 1f; c.h = 1 }
                c.t = dith(x, y, 301, 0.15f)
                if (x % 4 == 0 && y % 4 == 0) c.sp = 0.25f
            }
            BuildArt.STRIPES -> {
                if ((x / 4) % 2 == 0) { c.m = 1f; c.h = 1 }
                c.t = dith(x, y, 311, 0.15f)
            }
            BuildArt.CORRUG -> {
                when (x % 4) {
                    0 -> { c.h = 0; c.t = -0.35f }
                    1 -> { c.h = 1; c.t = 0.1f }
                    2 -> { c.h = 2; c.t = 0.45f }
                    else -> { c.h = 1; c.t = 0.1f }
                }
                c.t += dith(x, y, 321, 0.1f)
            }
            BuildArt.BASALT -> {
                val ct = (hh(x, 2, 401) - 0.5f) * 0.7f
                c.t = ct + (if (hh(x, y / 4, 402) < 0.25f) -0.35f else 0f) + dith(x, y, 403, 0.12f)
                c.h = if (ct > 0.1f) 1 else 0
                if (x % 5 == 4) { c.h = -1; c.t -= 0.4f }
            }
            BuildArt.TUFF -> {
                vor(x, y, 3, 0.9f, 411 + v, c)
                c.t = (hh(c.id, 4, 412) - 0.5f) * 1.0f + dith(x, y, 413, 0.3f)
                c.h = if (hh(c.id, 6, 414) > 0.5f) 1 else 0
                if (c.d < 0.6f) c.t -= 0.4f
                if (hh(x, y, 415) < 0.07f) { c.t = -0.9f; c.h = -1 }
            }
            BuildArt.NETHER -> {
                val b = blob(x, y, 421)
                c.t = (b - 0.5f) * 1.4f + dith(x, y, 423, 0.3f)
                c.h = if (b > 0.7f) 2 else if (b > 0.52f) 1 else if (b < 0.25f) -1 else 0
            }
            BuildArt.ENDSTONE -> {
                vor(x, y, 5, 0.9f, 431, c)
                c.h = if (c.d < 0.5f) 0 else 1
                c.t = (hh(c.id, 2, 432) - 0.5f) * 0.6f + dith(x, y, 433, 0.2f)
                if (c.d < 0.5f) c.t = -0.5f
                if (hh(x, y, 434) < 0.06f) { c.h = -1; c.t = -0.7f }
            }
            BuildArt.PRISM -> {
                val dx = abs((x % 8) - 3.5f); val dy = abs((y % 8) - 3.5f); val d = dx + dy
                if (d >= 4.5f) { c.g = 1f; c.h = -1 }
                else { c.h = if (d < 2.2f) 2 else 1; c.t = (hh(x / 8 + (y / 8) * 2, 5, 441) - 0.5f) * 0.5f + dith(x, y, 442, 0.15f) }
            }
            BuildArt.POLISHED -> {
                val r = min(min(x, 15 - x), min(y, 15 - y))
                if (r == 0) c.t = -0.5f
                else if (r == 1) c.t = if (x == 1 || y == 1) 0.4f else if (x == 14 || y == 14) -0.3f else 0f
                else { c.h = 1; c.t = dith(x, y, 451, 0.15f) }
            }
            BuildArt.QUARTZ -> {
                c.t = dith(x, y, 461, 0.1f)
                if ((x - y + 32) % 16 == 5) c.t -= 0.3f
                if ((x - y + 32) % 16 == 6) c.sp = 0.2f
            }
            BuildArt.LONGBRICK -> brickLike(c, x, y, 4, 16, 471, 0.22f, false)
            BuildArt.PEBBLE -> {
                vor(x, y, 5, 0.85f, 271, c)
                if (c.d < 0.65f) { c.g = 1f; c.h = -1 }
                else { c.h = if (c.d < 1.4f) 1 else 2; c.t = (hh(c.id, 3, 272) - 0.5f) * 0.9f + dith(x, y, 273, 0.2f) }
            }
        }
    }
}
