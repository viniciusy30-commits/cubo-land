package com.cuboland.app

import com.cuboland.app.BuildArt.define
import com.cuboland.app.BuildKit.S
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Desenho de cada padrão da Construção (ver BuildArt). Coordenadas em pixels (0..64), alturas em degraus de relevo. */
object BuildPats {
    private const val TAU = 6.2831853f


    /** Marcas internas que mudam por variante (longe da borda => encaixe liso entre variantes). h<0 = furinho, h>0 = calombo. */
    private fun scatter(x: Float, y: Float, s: S, seed: Int, n: Int, rmin: Float, rmax: Float, hAmp: Float, tAmp: Float) = with(BuildKit) {
        for (i in 0 until n) {
            val px = 10f + hash(i, s.v, seed) * 44f; val py = 10f + hash(i, s.v, seed + 1) * 44f
            val r = rmin + hash(i, s.v, seed + 2) * (rmax - rmin)
            val sg = if (hash(i, s.v, seed + 3) < 0.5f) -1f else 1f
            val c = cov(sdCirc(x, y, px, py, r))
            s.h += hAmp * sg * c * dome(sdCirc(x, y, px, py, r), r * 0.9f)
            s.t += tAmp * sg * c
            s.sp += 0.10f * c * (if (sg > 0) 1f else 0f)
        }
    }

    fun register() { registerCore(); registerMore() }

    private fun registerCore() = with(BuildKit) {
        // ================================================================= concreto / argila / gesso: lisos, macios, só um bisel gordinho
        define(BuildArt.CONCRETE, BuildArt.GC, 0f, 0.035f, false) { x, y, s ->
            val ed = edgeDist(x, y)
            s.h = 0f
            s.t = (vn(x, y, 3, 11) - 0.5f) * 0.30f
            if (s.v == 0) for (c in 0 until 4) {   // quatro covinhas redondas (parafusos de painel)
                val cx = if (c and 1 == 0) 16f else 48f; val cy = if (c < 2) 16f else 48f
                val d = sdCirc(x, y, cx, cy, 2.6f)
                s.h -= 1.1f * (1f - sm(-1f, 2.4f, d))
                s.sp += 0.10f * gauss((x - cx + 1.2f) * (x - cx + 1.2f) + (y - cy + 1.2f) * (y - cy + 1.2f), 1.4f)
            }
            if (s.v > 0) scatter(x, y, s, 201, 5, 1.6f, 3.4f, 1.1f, 0.10f)
            s.h += 0.9f * (vn(x, y, 5, 14) - 0.5f)
            s.sp += 0.10f * diag(x, y, 34f + s.v * 7f, 9f)
        }
        define(BuildArt.CLAY, BuildArt.GC, 0f, 0.035f, false) { x, y, s ->
            val ed = edgeDist(x, y)
            s.h = 0.9f * sin(y / 64f * TAU * 4f + 0.9f * sin(x / 64f * TAU))
            scatter(x, y, s, 211, 3, 1.8f, 3.2f, 0.9f, 0.08f)
            s.t = (vn(x, y, 4, 21) - 0.5f) * 0.28f + 0.10f * sin(y / 64f * TAU * 4f + 0.9f * sin(x / 64f * TAU))
            s.sp = 0.07f * diag(x, y, 36f, 12f)
        }
        define(BuildArt.PLASTER, BuildArt.GC, 0f, 0.03f, false) { x, y, s ->
            val ed = edgeDist(x, y)
            s.h = 0.8f * (vn(x, y, 4, 33) - 0.5f)
            scatter(x, y, s, 221, 4, 2f, 4f, 0.9f, 0.08f)
            // pinceladas de desempenadeira: arcos bem suaves
            val a1 = abs(sqrt((wd(x, 16f)) * (wd(x, 16f)) + (wd(y, 50f)) * (wd(y, 50f))) - 22f)
            val a2 = abs(sqrt((wd(x, 52f)) * (wd(x, 52f)) + (wd(y, 14f)) * (wd(y, 14f))) - 18f)
            s.t = (vn(x, y, 3, 31) - 0.5f) * 0.22f + 0.14f * gauss(a1 * a1, 3.2f) - 0.12f * gauss(a2 * a2, 2.6f)
            s.sp = 0.16f * star(x, y, 46f, 20f, 3.6f) + 0.12f * star(x, y, 14f, 44f, 3f) + 0.05f * diag(x, y, 30f, 10f)
        }
        // ================================================================= tijolos
        define(BuildArt.BRICK, BuildArt.GC, 0f, 0.02f, false) { x, y, s ->
            masonry(x, y, intArrayOf(0, 16, 32, 48, 64), arrayOf(intArrayOf(28, 60), intArrayOf(12, 44), intArrayOf(28, 60), intArrayOf(12, 44)), 4f, 3.6f, s)
            s.g = 1f - cov(s.d)
            s.h = -1f + 2f * dome(s.d, 4.6f)
            s.t = (hash(s.id, 3, 11) - 0.5f) * 0.55f
            val ly = s.ly; val lx = s.lx
            s.sp = (0.30f * gauss((ly - 2.4f) * (ly - 2.4f), 1.1f) * sm(2f, 7f, lx) * (1f - sm(s.ew - 9f, s.ew - 3f, lx)) + 0.14f * star(lx, ly, 6.5f, 4.2f, 3.2f)) * cov(s.d)
        }
        stoneBricks(BuildArt.SBRICK, 0)
        stoneBricks(BuildArt.SBRICK_CRACK, 1)
        stoneBricks(BuildArt.SBRICK_MOSS, 2)
        // ================================================================= pedregulho: seixos redondos
        cobble(BuildArt.COBBLE, false)
        cobble(BuildArt.COBBLE_MOSS, true)
        // ================================================================= pedras macias (sem aspereza)
        define(BuildArt.ROUGH0, BuildArt.GC, 0f, 0.03f, false) { x, y, s ->   // pedregulhos fofos grandes
            voronoi(x, y, 3, 0.55f, 5, s)
            s.h = 1.6f * dome(-s.d, 8f) - 0.4f
            s.g = 0.30f * (1f - sm(0f, 2.2f, s.d))
            s.t = (hash(s.id, 5, 12) - 0.5f) * 0.45f + (vn(x, y, 4, 41) - 0.5f) * 0.18f
            s.sp = 0.20f * star(x, y, 20f, 40f, 3.4f) + 0.16f * star(x, y, 52f, 14f, 3f)
        }
        define(BuildArt.ROUGH1, BuildArt.GC, 0f, 0.03f, false) { x, y, s ->   // camadas onduladas
            val w = sin(y / 64f * TAU * 4f + 0.55f * sin(x / 64f * TAU * 2f))
            s.h = 1.1f * w
            s.t = 0.22f * w + (vn(x, y, 4, 42) - 0.5f) * 0.14f
            s.sp = 0.12f * star(x, y, 40f, 24f, 3.4f) + 0.10f * star(x, y, 12f, 52f, 3f)
        }
        define(BuildArt.ROUGH2, BuildArt.GC, 0f, 0.03f, false) { x, y, s ->   // pintinhas macias
            s.t = (vn(x, y, 3, 43) - 0.5f) * 0.20f
            val cs = 8f
            val ci = floor(x / cs).toInt(); val cj = floor(y / cs).toInt()
            for (dj in -1..1) for (di in -1..1) {
                val i = ((ci + di) % 8 + 8) % 8; val j = ((cj + dj) % 8 + 8) % 8
                if (hash(i, j, 77) < 0.42f) continue
                val px = (ci + di + 0.5f + (hash(i, j, 78) - 0.5f) * 0.7f) * cs; val py = (cj + dj + 0.5f + (hash(i, j, 79) - 0.5f) * 0.7f) * cs
                val r = 1.1f + hash(i, j, 80) * 1.7f
                val c = cov(sdCirc(x, y, px, py, r))
                s.t += c * (if (hash(i, j, 81) < 0.5f) 0.75f else -0.8f)
                s.h += 0.9f * c
            }
        }
        define(BuildArt.SMOOTH, BuildArt.GC, 0f, 0.035f, false) { x, y, s ->   // polida: nuvens de mármore e reflexo
            val ed = edgeDist(x, y)
            s.h = 0.6f * (vn(x, y, 5, 53) - 0.5f)
            scatter(x, y, s, 231, 2, 3f, 6f, 0.6f, 0.07f)
            s.t = (vn(x, y, 3, 51) - 0.5f) * 0.34f + (vn(x, y, 6, 52) - 0.5f) * 0.18f
            s.sp = 0.16f * diag(x, y, 36f, 7f) + 0.08f * diag(x, y, 50f, 2f) + 0.2f * star(x, y, 14f, 14f, 4.5f)
        }
        define(BuildArt.SPECK, BuildArt.GC, 0f, 0.035f, false) { x, y, s ->   // granito confete
            val ed = edgeDist(x, y)
            s.h = 0f
            s.t = (vn(x, y, 3, 61) - 0.5f) * 0.26f
            val cs = 10.6667f
            val ci = floor(x / cs).toInt(); val cj = floor(y / cs).toInt()
            for (dj in -1..1) for (di in -1..1) {
                val i = ((ci + di) % 6 + 6) % 6; val j = ((cj + dj) % 6 + 6) % 6
                if (hash(i, j, 91) < 0.30f) continue
                val px = (ci + di + 0.5f + (hash(i, j, 92) - 0.5f) * 0.8f) * cs; val py = (cj + dj + 0.5f + (hash(i, j, 93) - 0.5f) * 0.8f) * cs
                val r = 1.5f + hash(i, j, 94) * 1.8f
                val d = sdCirc(x, y, px, py, r)
                val c = cov(d)
                s.m = max(s.m, c * (0.55f + 0.45f * hash(i, j, 95)))
                s.h += 1.0f * c * dome(d, 2f)
                s.sp += 0.22f * c * gauss((x - px + r * 0.4f) * (x - px + r * 0.4f) + (y - py + r * 0.4f) * (y - py + r * 0.4f), r * 0.35f)
            }
        }
        define(BuildArt.TILES, BuildArt.GC, 0f, 0.02f, false) { x, y, s ->   // lajotas grandes e brilhantes
            masonry(x, y, intArrayOf(0, 32, 64), arrayOf(intArrayOf(28, 60), intArrayOf(28, 60)), 4f, 6f, s)
            s.g = 1f - cov(s.d)
            s.h = -1f + 2f * dome(s.d, 6.5f)
            s.t = (hash(s.id, 4, 13) - 0.5f) * 0.40f
            val u = s.lx + s.ly
            s.sp = cov(s.d) * (0.34f * (1f - sm(0f, 1.6f, abs(u - 15f))) * (1f - sm(14f, 22f, s.lx)) * (1f - sm(14f, 22f, s.ly)) + 0.20f * (1f - sm(0f, 1.0f, abs(u - 20f))) * (1f - sm(18f, 26f, s.lx)) * (1f - sm(18f, 26f, s.ly)))
        }
        define(BuildArt.CHISEL, BuildArt.GC, 0.035f, 0.02f, false) { x, y, s ->   // moldura entalhada com losango no meio
            val ed = edgeDist(x, y)
            val pr = sdBox(x, y, 32f, 32f, 23f, 23f, 6f)
            val dx = abs(x - 32f); val dy = abs(y - 32f)
            val dia = (dx + dy) * 0.7071f - 9.5f
            val panel = cov(pr)
            s.h = 0.45f * dome(-ed, 3f) - 0.95f * panel + 1.9f * dome(dia, 5f) * panel
            s.t = (vn(x, y, 3, 71) - 0.5f) * 0.22f - 0.12f * panel
            s.sp = 0.2f * star(x, y, 28f, 28f, 3f) * panel
        }
        define(BuildArt.PILLAR, BuildArt.GC, 0.035f, 0.02f, false) { x, y, s ->   // coluna canelada com capitel e base
            val fx = (wr(x, 16f) - 8f) / 8f
            val flute = 0.85f * sqrt(max(0f, 1f - fx * fx)) - 0.55f
            val capY = min(y, 64f - y)
            val cap = 1f - sm(5.4f, 7.4f, capY)
            val capH = 1.15f * dome(capY - 7f, 4f) * 0.9f + 0.35f
            s.h = lerp(flute, capH, cap)
            s.g = 0.30f * (1f - sm(0f, 1.2f, 1f - abs(fx))) * (1f - cap)
            s.t = (vn(x, y, 3, 72) - 0.5f) * 0.18f
            s.sp = 0.10f * diag(x, y, 36f, 6f)
        }
        define(BuildArt.SANDSIDE, BuildArt.GC, 0f, 0.03f, false) { x, y, s ->   // arenito: faixas de camada
            val capY = min(y, 64f - y)
            val band = 1f - sm(6.5f, 8.5f, capY)
            val g1 = gauss((y - 24f) * (y - 24f), 1.3f); val g2 = gauss((y - 40f) * (y - 40f), 1.3f)
            s.h = 0.55f * band + 0.4f * dome(-capY, 3f) - 0.7f * (g1 + g2) * (1f - band)
            s.t = (vn(x, y, 4, 73) - 0.5f) * 0.22f + 0.20f * sin(y / 64f * TAU * 2f + 0.5f * sin(x / 64f * TAU))
            s.sp = 0.10f * diag(x, y, 36f, 7f)
        }
        define(BuildArt.OBSIDIAN, BuildArt.GC, 0.035f, 0.02f, false) { x, y, s ->   // pedra-gema facetada
            val dx = x - 32f; val dy = y - 32f
            val cheb = max(abs(dx), abs(dy)) / 32f
            val top = if (abs(dy) >= abs(dx) && dy < 0) 1 else 0
            val left = if (abs(dx) > abs(dy) && dx < 0) 1 else 0
            val right = if (abs(dx) > abs(dy) && dx > 0) 1 else 0
            s.h = 2.4f * (1f - cheb) - 1.2f
            s.t = 0.50f * top + 0.30f * left - 0.30f * right - 0.55f * (if (abs(dy) >= abs(dx) && dy > 0) 1 else 0)
            s.m = 0.40f * (1f - cheb) + 0.15f * (vn(x, y, 3, 74))
            val diagLine = min(abs(abs(dx) - abs(dy)), 99f)
            s.sp = 0.38f * gauss(diagLine * diagLine, 0.9f) * (1f - sm(0.7f, 1f, cheb)) + 0.9f * star(x, y, 20f, 20f, 6f) + 0.7f * star(x, y, 46f, 42f, 4.5f) + 0.5f * star(x, y, 50f, 14f, 3.2f)
            s.sp = s.sp.coerceAtMost(1f)
            s.h = 0f
        }
        // ================================================================= madeiras
        define(BuildArt.PLANK, BuildArt.GWOOD, 0f, 0.025f, false) { x, y, s ->
            masonry(x, y, intArrayOf(0, 16, 32, 48, 64), arrayOf(intArrayOf(40), intArrayOf(8), intArrayOf(52), intArrayOf(24)), 4f, 3.4f, s)
            val c = cov(s.d)
            s.g = 1f - c
            s.h = -1f + 1.7f * dome(s.d, 4.2f)
            val cyc = s.eh * 0.5f
            val grain = sin(TAU * (s.ly * 0.085f + 0.7f * sin(x / 64f * TAU) + hash(s.id, 1, 14) * 6f))
            s.t = (hash(s.id, 6, 15) - 0.5f) * 0.55f + 0.20f * grain
            s.sp = c * (0.22f * gauss((s.ly - 2.3f) * (s.ly - 2.3f), 1.2f) * sm(2f, 6f, s.lx) * (1f - sm(s.ew - 8f, s.ew - 2f, s.lx)))
            // pregos redondinhos nas pontas
            for (e in 0..1) {
                val nx = if (e == 0) 6f else s.ew - 6f
                val dd = sdCirc(s.lx, s.ly, nx, cyc, 1.5f)
                val n = cov(dd)
                s.t -= 0.9f * n; s.h += 0.3f * n * dome(dd, 1.5f)
                s.sp += 0.35f * n * gauss((s.lx - nx + 0.7f) * (s.lx - nx + 0.7f) + (s.ly - cyc + 0.7f) * (s.ly - cyc + 0.7f), 0.6f)
            }
        }
        define(BuildArt.MOSAIC, BuildArt.GWOOD, 0f, 0.025f, false) { x, y, s ->   // tacos em xadrez (parquet)
            val sx = floor(x / 16f).toInt(); val sy = floor(y / 16f).toInt()
            val hor = ((sx + sy) and 1) == 0
            val lx = x - sx * 16f; val ly = y - sy * 16f
            val along = if (hor) lx else ly; val across = if (hor) ly else lx
            val slat = floor(across / 4f).toInt()
            val ca = across - slat * 4f - 2f
            val cb = along - 8f
            val d = sdBox(ca, cb, 0f, 0f, 1.5f, 7.5f, 1.3f)
            val c = cov(d)
            s.g = (1f - c) * 0.9f
            s.h = 0.25f * dome(d, 1.6f) - 0.1f
            val id = (sx * 4 + sy) * 4 + slat
            s.t = (hash(id, 7, 16) - 0.5f) * 0.6f + 0.12f * sin(TAU * (along * 0.06f + hash(id, 8, 17) * 5f))
            s.sp = c * 0.16f * gauss((across - slat * 4f - 1.2f) * (across - slat * 4f - 1.2f), 0.9f) * (if (hor) 1f else 0.7f)
        }
        define(BuildArt.STRIP, BuildArt.GWOOD, 0.035f, 0.03f, false) { x, y, s ->   // tronco descascado (lado): fibras lisinhas + nó
            val w = sin(TAU * (x / 64f * 4f + 0.10f * sin(y / 64f * TAU * 1f)))
            s.h = 0.9f * w
            s.t = 0.30f * w + (vn(x, y, 4, 82) - 0.5f) * 0.2f
            val kd = sqrt(((x - 40f) * 0.8f) * ((x - 40f) * 0.8f) + ((y - 22f) * 1.5f) * ((y - 22f) * 1.5f))
            s.t += 0.5f * gauss((kd - 5f) * (kd - 5f), 1.2f) - 0.5f * gauss(kd * kd, 2.4f)
            s.h += 1.3f * gauss(kd * kd, 5f) - 0.9f * gauss(kd * kd, 1.8f)
            s.sp = 0.12f * diag(x, y, 36f, 8f)
        }
        define(BuildArt.RINGS, BuildArt.GWOOD, 0f, 0.025f, false) { x, y, s ->   // topo do tronco: anéis de crescimento redondinhos e casca
            val ed = edgeDist(x, y)
            val bark = 1f - sm(4.5f, 6.5f, ed)
            val r = sqrt((x - 32f) * (x - 32f) + (y - 32f) * (y - 32f))
            val ring = sin(r * 0.62f)
            s.t = lerp(0.20f * ring - 0.18f * gauss(r * r, 5f) + (vn(x, y, 3, 83) - 0.5f) * 0.12f, -0.8f, bark)
            s.h = lerp(0.18f * ring, 0.9f * dome(-ed, 4f) - 0.2f, bark)
            s.g = 0.35f * bark
            s.sp = 0.12f * diag(x, y, 34f, 8f) * (1f - bark)
        }
        define(BuildArt.SHINGLE, BuildArt.GD, 0f, 0.02f, false) { x, y, s ->   // telhas de escama
            var cover = -1; var bestCy = 1e9f; var bestD = 1e9f
            var topD = 1e9f
            for (j in -1..8) for (i in -1..4) {
                val cx = i * 16f + (if (((j % 2) + 2) % 2 == 1) 8f else 0f) + 8f
                val cy = j * 8f + 1f
                val d = sdCirc(x, y, cx, cy, 9.2f)
                if (d < 0f && cy < bestCy) { bestCy = cy; bestD = d; cover = ((j % 8) + 8) % 8 * 8 + ((i % 4) + 4) % 4; s.lx = x - cx; s.ly = y - cy }
            }
            // sombra que a telha de cima joga na de baixo
            for (j in -2..8) for (i in -1..4) {
                val cx = i * 16f + (if (((j % 2) + 2) % 2 == 1) 8f else 0f) + 8f
                val cy = j * 8f + 1f
                if (cy < bestCy - 0.5f) topD = min(topD, sdCirc(x, y, cx, cy, 9.2f))
            }
            s.d = bestD
            val shadow = 1f - sm(0f, 3.4f, topD)
            s.h = 0.9f * dome(bestD, 5f) - 0.9f * shadow
            s.t = (hash(cover, 9, 18) - 0.5f) * 0.5f - 0.25f * shadow
            s.g = 0.0f
            s.sp = 0.22f * gauss((s.lx + 3f) * (s.lx + 3f) * 0.6f + (s.ly + 3.5f) * (s.ly + 3.5f) * 1.6f, 2.6f) * (1f - shadow)
            s.k = 0.10f * shadow
        }
        // ================================================================= metais e azulejos
        metalPlate(BuildArt.METAL, 0)
        metalPlate(BuildArt.METAL_P1, 1)
        metalPlate(BuildArt.METAL_P2, 2)
        define(BuildArt.AZ_PLAIN, BuildArt.GW, 0f, 0.02f, false) { x, y, s ->   // azulejinhos quadrados brilhantes
            masonry(x, y, intArrayOf(0, 16, 32, 48, 64), arrayOf(intArrayOf(14, 30, 46, 62), intArrayOf(14, 30, 46, 62), intArrayOf(14, 30, 46, 62), intArrayOf(14, 30, 46, 62)), 2f, 3.2f, s)
            s.g = 1f - cov(s.d)
            s.h = -0.6f + 1.4f * dome(s.d, 3.4f)
            s.t = (hash(s.id, 2, 19) - 0.5f) * 0.18f
            s.sp = cov(s.d) * (0.38f * (1f - sm(0f, 1.2f, abs(s.lx + s.ly - 9f))) * (1f - sm(6f, 11f, s.lx)) * (1f - sm(6f, 11f, s.ly)))
        }
        define(BuildArt.AZ_DECOR, BuildArt.GW, 0f, 0.02f, false) { x, y, s ->   // azulejo português: flor azul sobre fundo branco
            val lx = wr(x, 32f) - 16f; val ly = wr(y, 32f) - 16f   // centro do azulejo = 0
            val tile = sdBox(lx, ly, 0f, 0f, 14.2f, 14.2f, 3f)
            var fl = 1e9f
            for (k in 0 until 4) {   // pétalas
                val a = k * 1.5708f
                fl = min(fl, sdCirc(lx, ly, cos(a) * 6.2f, sin(a) * 6.2f, 4.6f))
            }
            val center = sdCirc(lx, ly, 0f, 0f, 2.1f)
            val corner = sdCirc(abs(lx), abs(ly), 14.2f, 14.2f, 7.8f)
            val ring = abs(sdBox(lx, ly, 0f, 0f, 11.8f, 11.8f, 2f)) - 0.55f
            val motif = max(max(cov(fl), cov(corner + 1.5f) * 0.0f), cov(ring))
            val inner = cov(center)
            val diaD = (abs(abs(lx) - 9.5f) + abs(abs(ly) - 9.5f)) - 2.2f
            s.m = (1f - max(motif * (1f - inner), cov(diaD))) * 1f   // acento = branco; base = azul
            s.m = (1f - s.m).let { 1f - it }.coerceIn(0f, 1f)
            val blue = max(cov(fl) * (1f - inner), cov(ring)); val cornerBlue = cov(corner)
            s.m = 1f - max(blue, cornerBlue)
            s.g = 1f - cov(tile)
            s.h = -0.5f + 1.2f * dome(tile, 3.4f)
            s.sp = cov(tile) * 0.30f * (1f - sm(0f, 1.2f, abs(lx + ly + 17f))) * (1f - sm(-8f, 0f, lx))
        }
        // ================================================================= vidros
        define(BuildArt.GLASS, BuildArt.GC, 0f, 0f, true) { x, y, s -> glassFilm(x, y, s, true, 0.13f) }
        define(BuildArt.GLASS_THIN, BuildArt.GC, 0f, 0f, true) { x, y, s -> glassFilm(x, y, s, false, 0.13f) }
        define(BuildArt.WINDOW, BuildArt.GC, 0f, 0.015f, true) { x, y, s -> window(x, y, s, 2, 2, 6f, 3f, 3.6f) }
        define(BuildArt.JAN_COLONIAL, BuildArt.GC, 0f, 0.015f, true) { x, y, s -> window(x, y, s, 3, 2, 5f, 2.6f, 2.6f) }
        define(BuildArt.JAN_FRAMELESS, BuildArt.GC, 0f, 0f, true) { x, y, s ->
            glassFilm(x, y, s, false, 0.13f)
            for (c in 0 until 4) {
                val cx = if (c and 1 == 0) 7f else 57f; val cy = if (c < 2) 7f else 57f
                val d = sdCirc(x, y, cx, cy, 4.2f)
                val cc = cov(d)
                if (cc > 0f) {
                    s.a = max(s.a, cc); s.m = max(s.m, cc * 0f)
                    s.h = max(s.h, 1.2f * dome(d, 3.4f) * cc)
                    val hole = cov(sdCirc(x, y, cx, cy, 1.1f))
                    s.k = max(s.k, hole * 0.5f)
                    s.sp = max(s.sp, 0.40f * gauss((x - cx + 1.6f) * (x - cx + 1.6f) + (y - cy + 1.6f) * (y - cy + 1.6f), 1.1f))
                    s.t = -0.2f
                }
            }
        }
        define(BuildArt.JAN_SLIDE, BuildArt.GC, 0f, 0.015f, true) { x, y, s ->
            val ed = edgeDist(x, y)
            val frame = max(1f - sm(4.6f, 6.2f, ed), cov(sdBox(x, y, 32f, 32f, 3.2f, 32f, 1.2f)))
            val handle = cov(sdBox(x, y, 40.2f, 32f, 1.6f, 6.5f, 1.4f))
            val inner = sdBox(x, y, 32f, 32f, 28f, 28f, 3.5f)
            glassFilm(x, y, s, false, 0.13f)
            s.a = lerp(s.a, 1f, max(frame, handle))
            s.h = 0.9f * max(frame, handle) * 1f + 0.35f * dome(-edgeDist(x, y), 3f) - 0.0f
            s.h = lerp(-0.5f, 1f, max(frame, handle))
            s.sp *= (1f - max(frame, handle))
            s.t = -0.1f * handle
        }
        define(BuildArt.JAN_LOUVER, BuildArt.GC, 0f, 0.02f, true) { x, y, s ->   // persiana de ripas
            val yy = wr(y, 8f) - 4f
            val d = sdBox(wd(x, 32f), yy, 0f, 0f, 36f, 3.1f, 1.6f)
            val c = cov(d)
            s.a = lerp(0.07f, 1f, c)
            s.h = -0.8f + 1.8f * dome(d, 3f)
            s.t = (hash(floor(y / 8f).toInt(), 3, 20) - 0.5f) * 0.18f
            s.sp = c * 0.20f * gauss((yy + 1.5f) * (yy + 1.5f), 0.8f) * (1f - sm(14f, 34f, abs(wd(x, 32f))) * 0f)
            s.g = (1f - c) * 0.3f
        }
        define(BuildArt.JAN_PORTHOLE, BuildArt.GC, 0.03f, 0.02f, true) { x, y, s ->   // escotilha redonda
            val ed = edgeDist(x, y)
            val r = sqrt((x - 32f) * (x - 32f) + (y - 32f) * (y - 32f))
            val glassA = 1f - cov(r - 14.5f).let { 1f - it } // dentro do vidro
            val inside = cov(r - 14.5f)
            val ring = cov(r - 22f)
            glassFilm(x, y, s, false, 0.13f)
            s.a = lerp(1f, s.a, inside)
            s.h = lerp(0.25f, -0.4f, inside) + 1.1f * dome(abs(r - 18.2f) - 3.6f, 3.4f) * (1f - inside)
            s.h = lerp(s.h, 0.3f + 0.4f * dome(-ed, 3f), 1f - ring)
            s.h = lerp(0.35f, s.h, ring.coerceIn(0f, 1f)) .let { it }
            s.k = (cov(abs(r - 14.5f) - 0.7f) * 0.18f)
            // rebites
            for (c in 0 until 4) {
                val cx = if (c and 1 == 0) 8f else 56f; val cy = if (c < 2) 8f else 56f
                val d = sdCirc(x, y, cx, cy, 2.8f); val n = cov(d)
                s.a = max(s.a, n); s.h += 1.0f * dome(d, 2.4f) * n
                s.sp = max(s.sp, 0.5f * n * gauss((x - cx + 1f) * (x - cx + 1f) + (y - cy + 1f) * (y - cy + 1f), 1f))
            }
            s.sp *= 1f
            s.g = 0f
            s.t = 0.05f * glassA * 0f
        }
        define(BuildArt.JAN_BARS, BuildArt.GC, 0f, 0.015f, true) { x, y, s ->
            glassFilm(x, y, s, false, 0.13f)
            var bar = 0f; var shade = 0f
            for (k in 0 until 3) {   // grades verticais redondinhas
                val cx = 8f + k * 24f
                val dx = x - cx; val d = abs(dx) - 2.6f
                val c = cov(d); if (c > bar) { bar = c; shade = (dx / 2.6f).coerceIn(-1f, 1f) }
            }
            val rail = max(cov(abs(y - 6f) - 2.4f), cov(abs(y - 58f) - 2.4f))
            val sol = max(bar, rail)
            s.a = lerp(s.a, 1f, sol)
            s.h = lerp(0f, 1.0f * sqrt(max(0f, 1f - shade * shade)) + 0.3f * rail, sol)
            s.sp *= (1f - sol)
        }
        define(BuildArt.VITRAL, BuildArt.GL, 0f, 0f, true) { x, y, s ->   // vitral: vidrinhos coloridos presos por chumbo
            voronoi(x, y, 4, 0.55f, 99, s)
            val lead = 1f - sm(0.9f, 2.3f, s.d)
            val hue = hash(s.id, 4, 21)
            s.m = if (hue < 0.34f) 0f else if (hue < 0.67f) 1f else 0.5f
            s.t = (hash(s.id, 5, 22) - 0.5f) * 0.5f + 0.25f
            s.a = lerp(0.74f, 1f, lead)
            s.g = lead
            s.h = lerp(-0.3f, 0.6f, lead)
            val glow = gauss(s.lx * s.lx + s.ly * s.ly, 6f)
            s.sp = (1f - lead) * (0.12f * glow + 0.5f * star(x, y, 17f, 15f, 3.5f) + 0.35f * star(x, y, 50f, 44f, 3f)).coerceAtMost(1f)
            s.sp = s.sp.coerceAtMost(0.9f)
        }
    }


    /** retângulo de vidro com moldura: usa window() com 1x1 */
    private fun registerMore() = with(BuildKit) {
        // padrões v4 desenhados direto em McPix (a função aqui nunca é chamada, só registra o tipo de rejunte)
        define(BuildArt.TIJOLO, BuildArt.GC, 0f, 0f, false) { _, _, _ -> }
        for (wp in BuildArt.W_ABETO..BuildArt.W_DISTORCIDO) define(wp, BuildArt.GWOOD, 0f, 0f, false) { _, _, _ -> }
        // tábuas pintadas: tinta lisa, quinas gastas claras, pregos
        define(BuildArt.PAINTED, BuildArt.GWOOD, 0f, 0.025f, false) { x, y, s ->
            masonry(x, y, intArrayOf(0, 16, 32, 48, 64), arrayOf(intArrayOf(40), intArrayOf(8), intArrayOf(52), intArrayOf(24)), 3f, 3.2f, s)
            val c = cov(s.d); s.g = (1f - c) * 0.8f
            s.h = -1f + 1.7f * dome(s.d, 4f)
            s.t = (hash(s.id, 6, 41) - 0.5f) * 0.25f + 0.05f * sin(s.lx * 0.2f + s.id)
            s.sp = c * (0.30f * gauss((s.ly - 2.2f) * (s.ly - 2.2f), 1.1f) * sm(2f, 6f, s.lx) * (1f - sm(s.ew - 8f, s.ew - 2f, s.lx)))
            for (e in 0..1) { val nx = if (e == 0) 6f else s.ew - 6f; val dd = sdCirc(s.lx, s.ly, nx, s.eh * 0.5f, 1.4f); s.t -= 0.5f * cov(dd) }
        }
        define(BuildArt.SUBWAY, BuildArt.GW, 0f, 0.02f, false) { x, y, s ->
            masonry(x, y, intArrayOf(0, 16, 32, 48, 64), arrayOf(intArrayOf(30, 62), intArrayOf(14, 46), intArrayOf(30, 62), intArrayOf(14, 46)), 2f, 3.6f, s)
            s.g = 1f - cov(s.d); s.h = -0.6f + 1.4f * dome(s.d, 4f); s.t = (hash(s.id, 2, 42) - 0.5f) * 0.14f
            s.sp = cov(s.d) * (0.34f * gauss((s.ly - 2.6f) * (s.ly - 2.6f), 1.1f) * sm(2f, 6f, s.lx) * (1f - sm(s.ew - 8f, s.ew - 2f, s.lx)))
        }
        define(BuildArt.HEXTILE, BuildArt.GW, 0f, 0.02f, false) { x, y, s ->
            voronoi(x, y, 4, 0f, 43, s)   // grade regular: cada célula é um ladrilho arredondado
            val dd = 1.2f - s.d; s.g = 1f - cov(dd); s.h = -0.6f + 1.4f * dome(dd, 4.5f)
            s.t = (hash(s.id, 3, 43) - 0.5f) * 0.3f
            s.sp = cov(dd) * 0.3f * gauss((s.lx + 4f) * (s.lx + 4f) + (s.ly + 4.5f) * (s.ly + 4.5f), 2.6f)
        }
        define(BuildArt.CHECKER, BuildArt.GW, 0.035f, 0.02f, false) { x, y, s ->
            val sx = floor(x / 16f).toInt(); val sy = floor(y / 16f).toInt()
            val d = sdBox(wr(x, 16f), wr(y, 16f), 8f, 8f, 7.2f, 7.2f, 2.6f)
            s.m = if (((sx + sy) and 1) == 0) 0f else 1f
            s.g = (1f - cov(d)) * 0.7f; s.h = -0.4f + 1f * dome(d, 3.4f)
            s.sp = cov(d) * 0.28f * (1f - sm(0f, 1.2f, abs(wr(x, 16f) + wr(y, 16f) - 7f))) * (1f - sm(3f, 8f, wr(x, 16f)))
        }
        define(BuildArt.HERRING, BuildArt.GWOOD, 0f, 0.02f, false) { x, y, s ->   // espinha de peixe
            val u = (x + y) / 1.4142f; val v = (x - y) / 1.4142f
            val cu = floor(u / 8f).toInt(); val cv = floor(v / 16f).toInt()
            val shift = if ((cu and 1) == 0) 0f else 8f
            val lu = wr(u, 8f) - 4f; val lv = wr(v + shift, 16f) - 8f
            val d = sdBox(lu, lv, 0f, 0f, 3.2f, 7.4f, 1.4f)
            s.g = (1f - cov(d)) * 0.9f; s.h = -0.3f + 0.6f * dome(d, 2f)
            val id = cu * 7 + cv
            s.t = (hash(id, 9, 44) - 0.5f) * 0.55f + 0.1f * sin(lv * 0.5f + id)
            s.sp = cov(d) * 0.14f * gauss((lu + 1.6f) * (lu + 1.6f), 0.9f)
        }
        define(BuildArt.PANEL, BuildArt.GC, 0.035f, 0.02f, false) { x, y, s ->   // lambri: painéis embutidos
            val d = sdBox(wr(x, 32f), wr(y, 32f), 16f, 16f, 11f, 11f, 4f)
            val inn = cov(d)
            s.h = - 0.9f * inn + 0.8f * dome(d + 4f, 3f) * inn
            s.t = (vn(x, y, 3, 45) - 0.5f) * 0.14f; s.sp = 0.10f * diag(x, y, 36f, 7f)
            s.g = 0.25f * (1f - sm(0f, 1.5f, abs(d)))
        }
        define(BuildArt.TIMBER, BuildArt.GWOOD, 0.035f, 0.02f, false) { x, y, s ->   // enxaimel: gesso com vigas de madeira (m = madeira)
            val ed = edgeDist(x, y)
            val beam = max(1f - sm(4.5f, 6.5f, ed), 1f - sm(1.6f, 3.2f, min(abs(x - y), abs(x + y - 64f)) * 0.7071f))
            s.m = beam; s.h = 0.4f * beam - 0.05f
            s.t = (vn(x, y, 3, 46) - 0.5f) * 0.2f + 0.15f * sin(x * 0.35f + y * 0.35f) * beam
            s.sp = 0.10f * (1f - beam) * diag(x, y, 34f, 8f) + 0.15f * beam * gauss((ed - 1.2f) * (ed - 1.2f), 1f)
            s.g = 0f
        }
        define(BuildArt.LOGWALL, BuildArt.GWOOD, 0f, 0.025f, false) { x, y, s ->   // toras empilhadas
            val ly = wr(y, 16f); val t = (ly - 8f) / 8f
            val cyl = sqrt(max(0f, 1f - t * t))
            val id = floor(y / 16f).toInt()
            s.h = 0.4f + 1.1f * cyl - 1.0f; s.g = 0.9f * (1f - sm(0.25f, 0.5f, cyl))
            s.t = (hash(id, 1, 47) - 0.5f) * 0.4f + 0.18f * sin(TAU * (x / 64f * 3f + hash(id, 2, 47) * 4f) + t * 2f)
            s.sp = 0.22f * gauss((t + 0.5f) * (t + 0.5f), 0.14f) * (1f - sm(0.9f, 1f, abs(t)))
        }
        define(BuildArt.GEM, BuildArt.GC, 0.035f, 0.02f, false) { x, y, s ->   // gema lapidada
            val dx = x - 32f; val dy = y - 32f
            val oct = max(max(abs(dx), abs(dy)), (abs(dx) + abs(dy)) * 0.7071f) / 32f
            val ang = Math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
            val facet = floor((ang + 3.14159f) / 0.7854f).toInt()
            s.t = 0.55f * cos(ang + 2.4f).toFloat() * (if (oct > 0.45f) 1f else 0.4f) + (hash(facet, 5, 48) - 0.5f) * 0.25f
            s.h = 2.6f * (1f - oct) - 1.3f
            val line = min(abs(oct - 0.45f), abs(oct - 0.9f)) * 32f
            s.sp = 0.5f * gauss(line * line, 0.8f) + 0.95f * star(x, y, 22f, 20f, 7f) + 0.7f * star(x, y, 46f, 44f, 4f)
            s.sp = s.sp.coerceAtMost(1f)
            s.m = 0.5f * (1f - oct).coerceIn(0f, 1f)
        }
        define(BuildArt.CORRUG, BuildArt.GD, 0f, 0.02f, false) { x, y, s ->   // chapa ondulada
            val w = sin(TAU * x / 16f)
            s.h = 0.7f * w; s.t = 0.2f * w
            s.sp = 0.2f * gauss((wr(x, 16f) - 6f) * (wr(x, 16f) - 6f), 1.2f)
            for (c in 0 until 4) { val cx = 4f + 16f * c; for (cy in floatArrayOf(6f, 58f)) { val d = sdCirc(x, y, cx, cy, 2.3f); s.h += 0.9f * dome(d, 2f) * cov(d); s.sp += 0.4f * cov(d) * gauss((x - cx + 0.8f) * (x - cx + 0.8f) + (y - cy + 0.8f) * (y - cy + 0.8f), 0.8f) } }
        }
        define(BuildArt.PEBBLE, BuildArt.GC, 0f, 0.02f, false) { x, y, s ->   // seixos de rio miudinhos
            voronoi(x, y, 8, 0.5f, 49, s)
            val dd = 1.0f - s.d; s.g = 1f - cov(dd); s.h = -0.8f + 1.8f * dome(dd, 3.4f)
            s.t = (hash(s.id, 4, 49) - 0.5f) * 0.7f
            s.sp = cov(dd) * 0.25f * gauss((s.lx + 1.6f) * (s.lx + 1.6f) + (s.ly + 1.8f) * (s.ly + 1.8f), 1.6f)
        }
        define(BuildArt.SHAKE, BuildArt.GD, 0f, 0.02f, false) { x, y, s ->   // telhas de madeira retas
            masonry(x, y, intArrayOf(0, 16, 32, 48, 64), arrayOf(intArrayOf(12, 28, 44, 60), intArrayOf(4, 20, 36, 52), intArrayOf(12, 28, 44, 60), intArrayOf(4, 20, 36, 52)), 2f, 3.2f, s)
            val c = cov(s.d); s.g = (1f - c) * 0.8f; s.h = -0.5f + 1.3f * dome(s.d, 5f)
            s.t = (hash(s.id, 8, 50) - 0.5f) * 0.5f
            s.sp = c * 0.2f * gauss((s.ly - 2.4f) * (s.ly - 2.4f), 1.1f) * sm(1f, 4f, s.lx)
            s.k = 0.12f * (1f - dome(-(s.eh - s.ly) + 0f, 1f)) * 0f
        }
        // vidros especiais (película + enfeite)
        glassVar(BuildArt.GL_FACET) { x, y, s ->
            val d = abs(wd(x, 32f)) + abs(wd(y, 32f)); val g = min(abs(wr(x + y, 32f) - 16f), abs(wr(x - y, 32f) - 16f))
            s.a += 0.18f * (1f - sm(0f, 1.4f, g)) + 0.06f * (if (((floor((x + y) / 32f) + floor((x - y) / 32f)).toInt() and 1) == 0) 1f else 0f)
            s.sp = max(s.sp, 0.6f * (1f - sm(0f, 1.2f, g)))
        }
        glassVar(BuildArt.GL_BUBBLE) { x, y, s ->
            val bl = floatArrayOf(14f, 44f, 6f, 40f, 16f, 5f, 50f, 50f, 8f, 24f, 22f, 3.5f, 8f, 12f, 3f, 56f, 20f, 4f)
            for (i in 0 until 6) { val d = wCirc(x, y, bl[i * 3], bl[i * 3 + 1], bl[i * 3 + 2]); val ring = 1f - sm(0f, 1.3f, abs(d)); s.a = max(s.a, 0.08f + 0.5f * ring + 0.12f * cov(d)); s.sp = max(s.sp, 0.8f * ring * (if (wd(x, bl[i * 3]) + wd(y, bl[i * 3 + 1]) < 0f) 1f else 0.4f)) }
        }
        glassVar(BuildArt.GL_FROST) { x, y, s ->
            val n = vn(x, y, 8, 51) * 0.6f + vn(x, y, 16, 52) * 0.4f
            s.a = 0.42f + 0.18f * n + 0.2f * (1f - sm(0f, 8f, edgeDist(x, y))); s.sp = 0.25f + 0.3f * n
        }
        glassVar(BuildArt.GL_STAR) { x, y, s ->
            val pts = floatArrayOf(16f, 16f, 5f, 46f, 20f, 3.5f, 52f, 44f, 4.5f, 38f, 52f, 3f, 54f, 10f, 3f)
            for (i in 0 until 5) { val st = star(wd(x, pts[i * 3]) + pts[i * 3], wd(y, pts[i * 3 + 1]) + pts[i * 3 + 1], pts[i * 3], pts[i * 3 + 1], pts[i * 3 + 2] * 1.5f); s.a = max(s.a, 0.9f * st); s.sp = max(s.sp, st) }
        }
        glassVar(BuildArt.GL_GRID) { x, y, s ->
            val g = min(abs(wr(x, 16f) - 8f), abs(wr(y, 16f) - 8f)); val l = 1f - sm(5.8f, 7.6f, 8f - g * 0f - min(wr(x, 16f), min(16f - wr(x, 16f), min(wr(y, 16f), 16f - wr(y, 16f)))) + 0f)
            val e = min(min(wr(x, 16f), 16f - wr(x, 16f)), min(wr(y, 16f), 16f - wr(y, 16f)))
            val line = 1f - sm(0.3f, 1.8f, e); s.a += 0.30f * line; s.sp = max(s.sp, 0.6f * line)
        }
        glassVar(BuildArt.GL_DROPS) { x, y, s ->
            val ds = floatArrayOf(10f, 12f, 3f, 34f, 8f, 2.2f, 52f, 22f, 3.4f, 22f, 36f, 2.6f, 44f, 50f, 3f, 8f, 56f, 2.4f)
            for (i in 0 until 6) { val cx = ds[i * 3]; val cy = ds[i * 3 + 1]; val r = ds[i * 3 + 2]
                val d = min(wCirc(x, y, cx, cy, r), sdBox(wd(x, cx), wd(y, cy - 8f), 0f, 0f, r * 0.45f, 7f, r * 0.4f))
                s.a = max(s.a, 0.1f + 0.4f * cov(d)); s.sp = max(s.sp, 0.7f * cov(d) * gauss((wd(x, cx) + r * 0.4f) * (wd(x, cx) + r * 0.4f) + (wd(y, cy) + r * 0.4f) * (wd(y, cy) + r * 0.4f), r * 0.5f)) }
        }
        glassVar(BuildArt.GL_RAINBOW) { x, y, s ->
            val ph = wr(x + y, 64f) / 64f
            s.m = ph; s.a += 0.22f; s.sp = max(s.sp, 0.2f)
        }
        glassVar(BuildArt.GL_ICE) { x, y, s ->
            voronoi(x, y, 3, 0.7f, 53, s)
            val cr = 1f - sm(0f, 1.1f, s.d); s.a += 0.25f * cr + 0.12f * hash(s.id, 1, 53); s.sp = max(s.sp, 0.7f * cr)
        }
        // janelas com formas
        define(BuildArt.JAN_ARCH, BuildArt.GC, 0f, 0.015f, true) { x, y, s ->
            val d = min(sdBox(x, y, 32f, 44f, 20f, 20f, 3f), sdCirc(x, y, 32f, 28f, 20f)).let { max(it, -9999f) }
            val dd = min(sdBox(x, y, 32f, 46f, 20f, 18f, 3f), sdCirc(x, y, 32f, 30f, 20f))
            shapeWindow(x, y, s, dd, abs(x - 32f) < 1.6f || abs(y - 36f) < 1.6f)
        }
        define(BuildArt.JAN_HEART, BuildArt.GC, 0f, 0.015f, true) { x, y, s ->
            val px = (x - 32f) / 17f; val py = -(y - 34f) / 17f
            val q = px * px + py * py - 1f; val v = q * q * q - px * px * py * py * py
            shapeWindow(x, y, s, v * 9f, false)
        }
        define(BuildArt.JAN_STAR, BuildArt.GC, 0f, 0.015f, true) { x, y, s ->
            val dx = x - 32f; val dy = y - 33f; val r = sqrt(dx * dx + dy * dy); val a = Math.atan2(dy.toDouble(), dx.toDouble()).toFloat()
            val rad = 14f + 9f * (0.5f + 0.5f * cos(5f * (a + 1.5708f)).toFloat()).let { it * it }
            shapeWindow(x, y, s, r - rad, false)
        }
        define(BuildArt.DIAMOND, BuildArt.GC, 0.035f, 0.02f, false) { x, y, s ->   // losangos almofadados
            val u = wr(x + y, 32f) - 16f; val v = wr(x - y, 32f) - 16f
            val d = sdBox(u, v, 0f, 0f, 12.5f, 12.5f, 3f) * 0.7071f * 1.4142f * 0.7071f
            s.g = 1f - cov(d); s.h = -0.8f + 1.8f * dome(d, 4f); s.t = (hash(floor((x + y) / 32f).toInt(), floor((x - y) / 32f).toInt(), 61) - 0.5f) * 0.3f
            s.sp = cov(d) * 0.3f * gauss((u + 5f) * (u + 5f) * 0.3f + (v + 5f) * (v + 5f), 6f)
        }
        define(BuildArt.STEPPING, BuildArt.GC, 0f, 0.02f, false) { x, y, s ->   // pedras redondas de caminho
            var best = 1e9f; var id = 0
            val cs = floatArrayOf(16f, 16f, 12f, 46f, 14f, 11f, 48f, 48f, 13f, 50f, 12f, 10f, 14f, 52f, 9f)
            for (i in 0 until 5) { val d = wCirc(x, y, cs[i * 3], cs[i * 3 + 1], cs[i * 3 + 2]); if (d < best) { best = d; id = i } }
            s.g = 1f - cov(best); s.h = -1f + 2f * dome(best, 6f); s.t = (hash(id, 7, 62) - 0.5f) * 0.5f
            s.sp = cov(best) * 0.2f * gauss((x - cs[id * 3] + 5f) * (x - cs[id * 3] + 5f) + (y - cs[id * 3 + 1] + 5f) * (y - cs[id * 3 + 1] + 5f), 3f)
        }
        define(BuildArt.STRIPES, BuildArt.GC, 0.035f, 0.02f, false) { x, y, s ->   // listras macias
            val d = sdBox(wr(x, 16f), 32f, 8f, 32f, 5.5f, 40f, 2.5f)
            s.m = cov(d); s.h = 0.5f * dome(d, 3f) * cov(d); s.t = (vn(x, y, 3, 63) - 0.5f) * 0.14f
            s.sp = 0.2f * cov(d) * gauss((wr(x, 16f) - 6f) * (wr(x, 16f) - 6f), 1.1f)
        }
        define(BuildArt.QUILT, BuildArt.GC, 0.035f, 0.02f, false) { x, y, s ->   // acolchoado de almofadinhas
            val d = sdBox(wr(x, 16f), wr(y, 16f), 8f, 8f, 7f, 7f, 5f)
            s.g = 0.35f * (1f - cov(d)); s.h = -0.3f + 1f * dome(d, 6f)
            s.m = 0.0f; s.t = (hash(floor(x / 16f).toInt(), floor(y / 16f).toInt(), 64) - 0.5f) * 0.14f
            val bx = wr(x, 16f); val by = wr(y, 16f); s.sp = cov(d) * 0.18f * gauss((bx - 5f) * (bx - 5f) + (by - 5f) * (by - 5f), 2.4f)
            val b = gauss((bx - 8f) * (bx - 8f) + (by - 8f) * (by - 8f), 1.1f); s.k = 0.35f * b; s.h -= 0.5f * b
        }
        define(BuildArt.SCALES, BuildArt.GD, 0f, 0.02f, false) { x, y, s -> }
    }

    private fun glassVar(id: Int, extra: (Float, Float, S) -> Unit) = with(BuildKit) {
        define(id, BuildArt.GC, 0f, 0f, true) { x, y, s ->
            glassFilm(x, y, s, false, 0.13f)
            if (id == BuildArt.GL_FROST || id == BuildArt.GL_BUBBLE || id == BuildArt.GL_STAR || id == BuildArt.GL_DROPS) { s.a = 0.12f; s.sp = 0f }
            extra(x, y, s)
            s.a = s.a.coerceIn(0f, 0.97f)
        }
    }

    /** vidraça de forma livre dentro de moldura (d < 0 dentro do vidro) */
    private fun shapeWindow(x: Float, y: Float, s: S, d: Float, mull: Boolean) = with(BuildKit) {
        val pane = cov(d) * (if (mull) 0.0f else 1f).let { 1f }
        glassFilm(x, y, s, false, 0.13f)
        val m = if (mull) 1f else 0f
        val frame = 1f - pane * (1f - m * 1f)
        s.a = lerp(1f, s.a, 1f - frame); s.m = 1f - frame
        s.h = lerp(0.9f, -0.5f, 1f - frame) * (0.7f + 0.3f * dome(-edgeDist(x, y), 3f))
        s.sp *= 1f - frame
        s.t = frame * (vn(x, y, 3, 105) - 0.5f) * 0.16f
    }

    // ------------------------------------------------------------------------- peças compartilhadas
    private fun stoneBricks(id: Int, kind: Int) = with(BuildKit) {
        define(id, BuildArt.GC, 0f, 0.02f, false) { x, y, s ->
            masonry(x, y, intArrayOf(0, 20, 40, 64), arrayOf(intArrayOf(12, 40), intArrayOf(28, 56), intArrayOf(4, 36)), 4f, 4.4f, s)
            val c = cov(s.d)
            s.g = 1f - c
            s.h = -1f + 2f * dome(s.d, 5.5f)
            s.t = (hash(s.id, 3, 31) - 0.5f) * 0.55f
            s.sp = c * (0.26f * gauss((s.ly - 2.6f) * (s.ly - 2.6f), 1.1f) * sm(2f, 8f, s.lx) * (1f - sm(s.ew - 10f, s.ew - 3f, s.lx)) + 0.12f * star(s.lx, s.ly, 7f, 5f, 3.4f))
            if (kind == 1) {   // rachadura lisinha
                var d = 1e9f
                val pts = floatArrayOf(44f, 12f, 47.5f, 20f, 44f, 27f, 48.5f, 35f, 46f, 42f)
                for (i in 0 until pts.size / 2 - 1) d = min(d, segDist(x, y, pts[i * 2], pts[i * 2 + 1], pts[i * 2 + 2], pts[i * 2 + 3]))
                val pts2 = floatArrayOf(10f, 44f, 14f, 50f, 11f, 56f)
                for (i in 0 until pts2.size / 2 - 1) d = min(d, segDist(x, y, pts2[i * 2], pts2[i * 2 + 1], pts2[i * 2 + 2], pts2[i * 2 + 3]))
                s.k = max(s.k, 1f - sm(0.5f, 1.6f, d))
                s.h -= 1.0f * (1f - sm(0.4f, 2.2f, d))
            }
            if (kind == 2) mossBlobs(x, y, s, 0)
        }
    }

    private fun cobble(id: Int, moss: Boolean) = with(BuildKit) {
        define(id, BuildArt.GC, 0f, 0.02f, false) { x, y, s ->
            voronoi(x, y, 5, 0.28f, 17, s)
            val dd = 1.4f - s.d
            val c = cov(dd)
            s.g = 1f - c
            s.h = -1f + 2f * dome(dd, 5.2f)
            s.t = (hash(s.id, 5, 32) - 0.5f) * 0.55f
            s.sp = c * (0.22f * gauss((s.lx + 3.2f) * (s.lx + 3.2f) * 0.9f + (s.ly + 3.6f) * (s.ly + 3.6f) * 1.6f, 2.4f))
            if (moss) mossBlobs(x, y, s, 1)
        }
    }

    /** almofadas de musgo por cima (m = cobertura) */
    private fun mossBlobs(x: Float, y: Float, s: S, v: Int) = with(BuildKit) {
        val bl = if (v == 0) floatArrayOf(8f, 6f, 7.5f, 24f, 3f, 6f, 50f, 9f, 8.5f, 36f, 24f, 5f, 59f, 33f, 7f, 13f, 46f, 8f, 31f, 58f, 6.5f, 52f, 52f, 7.5f, 3f, 62f, 5f)
        else floatArrayOf(6f, 8f, 8f, 28f, 4f, 7f, 52f, 6f, 8f, 16f, 30f, 6.5f, 44f, 34f, 7f, 8f, 52f, 8f, 34f, 58f, 7f, 58f, 56f, 6f, 24f, 46f, 5f)
        var best = 1e9f; var bi = 0
        for (i in 0 until bl.size / 3) {
            val ang = Math.atan2((wd(y, bl[i * 3 + 1])).toDouble(), (wd(x, bl[i * 3])).toDouble()).toFloat()
            val wob = 1f + 0.10f * sin(ang * 3f + i) + 0.07f * sin(ang * 5f + i * 2f)
            val d = wCirc(x, y, bl[i * 3], bl[i * 3 + 1], bl[i * 3 + 2] * wob)
            if (d < best) { best = d; bi = i }
        }
        val c = cov(best)
        s.m = max(s.m, c)
        if (c > 0f) {
            s.g *= (1f - c)
            s.h = max(s.h, 0.5f + 1.1f * dome(best, 3.6f))
            s.t = lerp(s.t, (hash(bi, 9, 33) - 0.5f) * 0.5f + 0.1f * sin(x * 0.9f + y * 0.7f), c)
            s.sp = lerp(s.sp, 0.14f * star(x, y, bl[bi * 3] - 1.5f, bl[bi * 3 + 1] - 2f, 3f), c)
            s.k = 0f
        }
    }

    private fun metalPlate(id: Int, patina: Int) = with(BuildKit) {
        define(id, BuildArt.GD, 0f, 0f, false) { x, y, s ->
            // chapas de meio bloco: a emenda entre blocos vira só mais uma junta da grade, então a divisão dos blocos some
            masonry(x, y, intArrayOf(0, 32, 64), arrayOf(intArrayOf(28, 60), intArrayOf(28, 60)), 4f, 4.5f, s)
            val c = cov(s.d)
            s.g = (1f - c) * 0.5f
            s.h = -1f + 1.7f * dome(s.d, 4.5f)
            s.t = (hash(s.id, 5, 101) - 0.5f) * 0.10f + (vn(x, y, 4, 101) - 0.5f) * 0.08f
            s.sp = c * (0.16f * gauss((s.ly - 3f) * (s.ly - 3f), 1.3f) * sm(3f, 9f, s.lx) * (1f - sm(s.ew - 9f, s.ew - 3f, s.lx)))
            for (cx in floatArrayOf(5.5f, s.ew - 5.5f)) for (cy in floatArrayOf(5.5f, s.eh - 5.5f)) {   // rebites redondos em relevo
                val d = sdCirc(s.lx, s.ly, cx, cy, 2.6f)
                val n = cov(d)
                s.h += 1.4f * dome(d, 2.6f) * n
                s.sp += 0.5f * n * gauss((s.lx - cx + 0.9f) * (s.lx - cx + 0.9f) + (s.ly - cy + 0.9f) * (s.ly - cy + 0.9f), 0.9f)
                s.k = max(s.k, 0.12f * cov(sdCirc(s.lx, s.ly, cx, cy, 3.6f)) * (1f - n))
            }
            if (patina > 0) {
                val bl = if (patina == 1) floatArrayOf(4f, 6f, 6f, 60f, 40f, 5f) else floatArrayOf(4f, 6f, 10f, 60f, 22f, 8f, 18f, 60f, 9f, 44f, 4f, 7f, 30f, 36f, 6f, 8f, 52f, 6f, 58f, 56f, 8f)
                var best = 1e9f
                for (i in 0 until bl.size / 3) {
                    val ang = Math.atan2((wd(y, bl[i * 3 + 1])).toDouble(), (wd(x, bl[i * 3])).toDouble()).toFloat()
                    val wob = 1f + 0.12f * sin(ang * 3f + i * 2f) + 0.08f * sin(ang * 5f + i)
                    best = min(best, wCirc(x, y, bl[i * 3], bl[i * 3 + 1], bl[i * 3 + 2] * wob))
                    // pinguinho de pátina escorrendo
                    if (hash(i, 3, 102) > 0.2f) {
                        val dx = wd(x, bl[i * 3] + (hash(i, 4, 103) - 0.5f) * 8f)
                        val dy = wd(y, bl[i * 3 + 1] + bl[i * 3 + 2] * 0.9f + 4f)
                        best = min(best, sdBox(dx, dy, 0f, 3f, 1.6f, 4.5f + hash(i, 5, 104) * 4f, 1.6f))
                    }
                }
                val c = cov(best)
                s.m = c
                s.h = max(s.h, 0.2f) + 0.9f * dome(best, 3f) * c
                s.sp *= (1f - c * 0.5f)
                s.t = lerp(s.t, 0.15f * sin(x * 0.8f + y), c)
            }
        }
    }

    /** vidro liso: película quase transparente, brilho na borda (só no cubo), listras diagonais que continuam de bloco em bloco e uma estrelinha */
    private fun glassFilm(x: Float, y: Float, s: S, rim: Boolean, base: Float) = with(BuildKit) {
        var a = base
        val d1 = diag(x, y, 40f, 6f); val d2 = diag(x, y, 54f, 1.8f); val d3 = diag(x, y, 26f, 1.4f)
        a += 0.24f * d1 + 0.17f * d2 + 0.10f * d3
        var sp = 0.55f * d1 + 0.40f * d2 + 0.25f * d3
        if (rim) {
            val ed = edgeDist(x, y)
            val glow = 1f - sm(0f, 9f, ed); val line = 1f - sm(0f, 1.9f, ed)
            a += 0.14f * glow + 0.22f * line
            sp += 0.20f * glow + 0.50f * line
        }
        val st = star(x, y, 15f, 15f, 6.5f)
        a = max(a, 0.95f * st)
        sp = max(sp, st)
        s.a = a.coerceIn(0f, 0.97f); s.sp = sp.coerceIn(0f, 1f)
    }

    /** janela com moldura gordinha: cols x rows vidraças, borda de espessura bw, travessas de espessura mw, cantos r */
    private fun window(x: Float, y: Float, s: S, cols: Int, rows: Int, bw: Float, mw: Float, r: Float) = with(BuildKit) {
        var dPane = 1e9f
        val pw = (64f - 2f * bw - (cols - 1) * mw) / cols; val ph = (64f - 2f * bw - (rows - 1) * mw) / rows
        for (j in 0 until rows) for (i in 0 until cols) {
            val cx = bw + pw * 0.5f + i * (pw + mw); val cy = bw + ph * 0.5f + j * (ph + mw)
            dPane = min(dPane, sdBox(x, y, cx, cy, pw * 0.5f, ph * 0.5f, r))
        }
        val pane = cov(dPane)
        glassFilm(x, y, s, false, 0.13f)
        s.a = lerp(1f, s.a, pane)
        s.m = pane
        s.h = lerp(0.9f, -0.55f, pane) * (1f) + 0.0f
        s.h *= (0.7f + 0.3f * dome(-edgeDist(x, y), 3f))
        s.sp *= pane
        s.sp += (1f - pane) * 0.12f * gauss((edgeDist(x, y) - 1.6f) * (edgeDist(x, y) - 1.6f), 0.9f)
        s.t = (1f - pane) * (vn(x, y, 3, 105) - 0.5f) * 0.16f
    }
}
