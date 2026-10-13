package com.cuboland.app

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Ferramentas de desenho dos padrões da Construção (formas com distância, almofadas, alvenaria, voronoi). Tudo repete a cada 64 px. */
object BuildKit {
    // ---------------------------------------------------------------- ferramentas de desenho
    /** amostra de um ponto do padrão */
    class S {
        @JvmField var h = 0f; @JvmField var m = 0f; @JvmField var g = 0f; @JvmField var t = 0f; @JvmField var a = 1f; @JvmField var sp = 0f; @JvmField var k = 0f
        // rascunho das peças (alvenaria, voronoi)
        @JvmField var v = 0; @JvmField var d = 0f; @JvmField var id = 0; @JvmField var lx = 0f; @JvmField var ly = 0f; @JvmField var ew = 0f; @JvmField var eh = 0f
        fun reset() { h = 0f; m = 0f; g = 0f; t = 0f; a = 1f; sp = 0f; k = 0f }
    }

    fun sm(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
    /** cobertura suave (1 dentro, 0 fora) a partir da distância com sinal (negativa = dentro) */
    fun cov(d: Float): Float = sm(0.85f, -0.85f, d)
    fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    fun wr(v: Float, p: Float = 64f): Float = v - floor(v / p) * p
    /** diferença a - b pelo caminho mais curto, contando que o tile repete */
    fun wd(a: Float, b: Float): Float { var d = a - b; d -= Math.round(d / 64f) * 64f; return d }
    fun sdBox(px: Float, py: Float, cx: Float, cy: Float, hw: Float, hh: Float, r: Float): Float {
        val qx = abs(px - cx) - hw + r; val qy = abs(py - cy) - hh + r
        val ax = max(qx, 0f); val ay = max(qy, 0f)
        return min(max(qx, qy), 0f) + sqrt(ax * ax + ay * ay) - r
    }
    fun sdCirc(px: Float, py: Float, cx: Float, cy: Float, r: Float): Float { val dx = px - cx; val dy = py - cy; return sqrt(dx * dx + dy * dy) - r }
    /** caixa que repete a cada 64 px */
    fun wBox(x: Float, y: Float, cx: Float, cy: Float, hw: Float, hh: Float, r: Float): Float = sdBox(wd(x, cx), wd(y, cy), 0f, 0f, hw, hh, r)
    fun wCirc(x: Float, y: Float, cx: Float, cy: Float, r: Float): Float { val dx = wd(x, cx); val dy = wd(y, cy); return sqrt(dx * dx + dy * dy) - r }
    /** perfil de almofada: 0 na borda, sobe suave até 1 a w pixels pra dentro */
    fun dome(d: Float, w: Float): Float { val t = (-d / w).coerceIn(0f, 1f); return 1f - (1f - t) * (1f - t) }
    fun gauss(d2: Float, s: Float): Float = exp(-d2 / (2f * s * s))
    fun hash(a: Int, b: Int, c: Int): Float {
        var k = a * 73856093 xor (b * 19349663) xor (c * 83492791)
        k = (k xor (k ushr 13)) * 1274126177
        k = k xor (k ushr 16)
        return (k and 0xffff) / 65535f
    }
    /** ruído suave que repete em 64 px (cells células por lado) */
    fun vn(x: Float, y: Float, cells: Int, seed: Int): Float {
        val fx = x / 64f * cells; val fy = y / 64f * cells
        val xi = floor(fx).toInt(); val yi = floor(fy).toInt()
        val ax = fx - xi; val ay = fy - yi
        val sx = ax * ax * (3 - 2 * ax); val sy = ay * ay * (3 - 2 * ay)
        fun hh(i: Int, j: Int) = hash(((i % cells) + cells) % cells, ((j % cells) + cells) % cells, seed)
        val a = hh(xi, yi); val b = hh(xi + 1, yi); val c = hh(xi, yi + 1); val d = hh(xi + 1, yi + 1)
        return lerp(lerp(a, b, sx), lerp(c, d, sx), sy)
    }
    /** faixa diagonal contínua entre blocos: 1 no centro c (em x+y, módulo 64), cai até 0 em w pixels */
    fun diag(x: Float, y: Float, c: Float, w: Float): Float { val d = abs(wd(x + y, c)) * 0.7071f; return 1f - sm(w * 0.35f, w, d) }
    fun star(x: Float, y: Float, cx: Float, cy: Float, r: Float): Float {
        val dx = x - cx; val dy = y - cy
        val glow = gauss(dx * dx + dy * dy, r * 0.22f)
        val cross = exp(-abs(dx) / (r * 0.10f)) * max(0f, 1f - abs(dy) / r) + exp(-abs(dy) / (r * 0.10f)) * max(0f, 1f - abs(dx) / r)
        return max(glow * 0.9f, cross.coerceAtMost(1f))
    }
    fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
        val vx = bx - ax; val vy = by - ay; val wx = px - ax; val wy = py - ay
        val t = ((wx * vx + wy * vy) / (vx * vx + vy * vy)).coerceIn(0f, 1f)
        val dx = wx - vx * t; val dy = wy - vy * t
        return sqrt(dx * dx + dy * dy)
    }
    /** vara de losangos/almofada geral: borda do tile (distância até a borda mais próxima, em px) */
    fun edgeDist(x: Float, y: Float): Float = min(min(x, 64f - x), min(y, 64f - y))

    /** alvenaria: fileiras (ys = início de cada uma, último = 64) com juntas (js[fila] = x onde começa cada rejunte, crescente) de largura gap.
     *  Acha a peça mais próxima: d (distância com sinal), id, posição local e tamanho. */
    fun masonry(x: Float, y: Float, ys: IntArray, js: Array<IntArray>, gap: Float, r: Float, s: S) {
        var best = 1e9f
        val nr = ys.size - 1
        for (ri in 0 until nr) {
            val y0 = ys[ri].toFloat(); val y1 = ys[ri + 1].toFloat() - gap
            val j = js[ri]
            for (i in j.indices) {
                val xa = j[i] + gap; val xb = if (i + 1 < j.size) j[i + 1].toFloat() else j[0] + 64f
                val cx = (xa + xb) * 0.5f; val hw = (xb - xa) * 0.5f; val cy = (y0 + y1) * 0.5f; val hh = (y1 - y0) * 0.5f
                for (ky in -1..1) for (kx in -1..1) {
                    val ccx = cx + 64f * kx; val ccy = cy + 64f * ky
                    if (abs(x - ccx) > hw + 6f || abs(y - ccy) > hh + 6f) continue
                    val d = sdBox(x, y, ccx, ccy, hw, hh, r)
                    if (d < best) { best = d; s.id = ri * 16 + i; s.lx = x - (ccx - hw); s.ly = y - (ccy - hh); s.ew = hw * 2; s.eh = hh * 2 }
                }
            }
        }
        s.d = best
    }

    /** voronoi que repete: cells x cells pontos com jitter; d = distância até a divisa (positivo dentro), id da célula, lx/ly = posição relativa ao centro */
    fun voronoi(x: Float, y: Float, cells: Int, jit: Float, seed: Int, s: S) {
        val cs = 64f / cells
        val ci = floor(x / cs).toInt(); val cj = floor(y / cs).toInt()
        var f1 = 1e9f; var f2 = 1e9f; var bid = 0; var bx = 0f; var by = 0f
        for (dj in -1..1) for (di in -1..1) {
            val i = ci + di; val j = cj + dj
            val wi = ((i % cells) + cells) % cells; val wj = ((j % cells) + cells) % cells
            val px = (i + 0.5f + (hash(wi, wj, seed) - 0.5f) * jit) * cs
            val py = (j + 0.5f + (hash(wi, wj, seed + 7) - 0.5f) * jit) * cs
            val dx = x - px; val dy = y - py; val dd = sqrt(dx * dx + dy * dy)
            if (dd < f1) { f2 = f1; f1 = dd; bid = wj * cells + wi; bx = dx; by = dy } else if (dd < f2) f2 = dd
        }
        s.d = (f2 - f1) * 0.5f; s.id = bid; s.lx = bx; s.ly = by
    }

}
