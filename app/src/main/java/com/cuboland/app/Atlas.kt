package com.cuboland.app

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/** Atlas de texturas pintado por código: 32x32 px por tile, suave e detalhado (estilo Hytale). */
object Atlas {
    const val T = 32
    const val NT = 32          // slots no atlas (potência de 2 p/ mipmap). Usados: 0..19
    val bmp: Bitmap by lazy { build() }
    val tiles: Array<Bitmap> by lazy { Array(20) { Bitmap.createBitmap(bmp, it * T, 0, T, T) } }

    private fun h(x: Int, y: Int, s: Int): Float {
        var k = x * 73856093 xor (y * 19349663) xor (s * 83492791)
        k = (k xor (k ushr 13)) * 1274126177
        k = k xor (k ushr 16)
        return (k and 0xffff) / 65535f
    }
    private fun wrap(i: Int, p: Int) = ((i % p) + p) % p

    /** ruído suave que repete certinho nas bordas do tile */
    private fun vn(u: Float, v: Float, fx: Int, fy: Int, s: Int): Float {
        val x = u * fx; val y = v * fy
        val xi = floor(x).toInt(); val yi = floor(y).toInt()
        val ax = x - xi; val ay = y - yi
        val sx = ax * ax * (3 - 2 * ax); val sy = ay * ay * (3 - 2 * ay)
        val a = h(wrap(xi, fx), wrap(yi, fy), s); val b = h(wrap(xi + 1, fx), wrap(yi, fy), s)
        val c2 = h(wrap(xi, fx), wrap(yi + 1, fy), s); val d = h(wrap(xi + 1, fx), wrap(yi + 1, fy), s)
        val t = a + (b - a) * sx; val w = c2 + (d - c2) * sx
        return t + (w - t) * sy
    }
    private fun fbm(u: Float, v: Float, s: Int) = vn(u, v, 4, 4, s) * 0.5f + vn(u, v, 8, 8, s + 1) * 0.3f + vn(u, v, 16, 16, s + 2) * 0.2f

    private var wd1 = 0f; private var wd2 = 0f; private var wid = 0f
    private fun worley(u: Float, v: Float, g: Int, s: Int) {
        val x = u * g; val y = v * g; val xi = floor(x).toInt(); val yi = floor(y).toInt()
        var d1 = 9f; var d2 = 9f; var id = 0f
        for (j in -1..1) for (i in -1..1) {
            val cx = xi + i; val cy = yi + j; val wx = wrap(cx, g); val wy = wrap(cy, g)
            val px = cx + 0.15f + 0.7f * h(wx, wy, s); val py = cy + 0.15f + 0.7f * h(wx, wy, s + 31)
            val d = sqrt((px - x) * (px - x) + (py - y) * (py - y))
            if (d < d1) { d2 = d1; d1 = d; id = h(wx, wy, s + 57) } else if (d < d2) d2 = d
        }
        wd1 = d1; wd2 = d2; wid = id
    }

    private fun lerp(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        val r = (((a shr 16) and 255) * (1 - k) + ((b shr 16) and 255) * k).toInt()
        val g = (((a shr 8) and 255) * (1 - k) + ((b shr 8) and 255) * k).toInt()
        val bl = ((a and 255) * (1 - k) + (b and 255) * k).toInt()
        return (255 shl 24) or (r shl 16) or (g shl 8) or bl
    }
    private fun mul(c: Int, m: Float): Int {
        val r = (((c shr 16) and 255) * m).toInt().coerceIn(0, 255)
        val g = (((c shr 8) and 255) * m).toInt().coerceIn(0, 255)
        val b = ((c and 255) * m).toInt().coerceIn(0, 255)
        return (255 shl 24) or (r shl 16) or (g shl 8) or b
    }
    private const val CLEAR = 0x004F9A3A

    private fun grassTop(x: Int, y: Int, u: Float, v: Float): Int {
        val n = fbm(u, v, 1)
        var c = lerp(0x3C9438, 0x86D655, n * 1.25f - 0.1f)
        c = mul(c, 0.92f + 0.2f * vn(u, v, 16, 16, 7))
        if (h(x, y / 3, 6) > 0.62f) c = mul(c, 1.16f)      // lâminas de grama
        if (h(x + 5, y / 2, 8) > 0.86f) c = mul(c, 0.82f)
        return c
    }
    private fun dirt(x: Int, y: Int, u: Float, v: Float): Int {
        var c = lerp(0x6A4627, 0x9C6E42, fbm(u, v, 3))
        worley(u, v, 8, 12)
        if (wd1 < 0.2f && wid > 0.55f) c = mul(lerp(0xA88A68, 0xC4A582, wid), 1.08f - wd1)
        return mul(c, 0.95f + 0.1f * h(x, y, 13))
    }

    private fun px(t: Int, x: Int, y: Int): Int {
        val u = (x + 0.5f) / T; val v = (y + 0.5f) / T
        return when (t) {
            0 -> -1
            1 -> grassTop(x, y, u, v)
            2 -> {
                val depth = (6 + 7 * vn(u, 0.3f, 8, 1, 4) + 2 * h(x, 0, 5)).toInt()
                if (y < depth) mul(grassTop(x, y, u, v), 1f - 0.28f * y / depth)
                else if (y == depth) mul(grassTop(x, y, u, v), 0.62f)
                else dirt(x, y, u, v)
            }
            3 -> dirt(x, y, u, v)
            4 -> {
                worley(u, v, 4, 11)
                val base = mul(lerp(0x858B95, 0xB0B6BE, wid), 0.9f + 0.2f * fbm(u, v, 14))
                val e = wd2 - wd1
                if (e < 0.09f) mul(base, 0.6f) else if (e < 0.17f) mul(base, 1.1f) else base
            }
            5 -> {
                val rip = sin((v * 3f + fbm(u, v, 3) * 0.9f) * 6.2832f) * 0.045f
                var c = mul(lerp(0xE6D295, 0xF7EBBA, fbm(u, v, 3)), 1f + rip)
                if (h(x, y, 17) > 0.96f) c = mul(c, 0.84f)
                c
            }
            6 -> {
                val n = vn(u, v, 8, 2, 20) * 0.6f + vn(u, v, 16, 4, 21) * 0.4f
                var c = lerp(0x58391F, 0x8E6339, n)
                val gr = h(x / 2, 0, 22)
                if (gr > 0.78f) c = mul(c, 0.7f) else if (gr < 0.2f) c = mul(c, 1.12f)
                c
            }
            7 -> {
                val dx = x - 15.5f; val dy = y - 15.5f
                val r = sqrt(dx * dx + dy * dy) + (vn(u, v, 4, 4, 23) - 0.5f) * 3f
                if (maxOf(abs(dx), abs(dy)) > 14f) mul(lerp(0x58391F, 0x7A5230, vn(u, v, 8, 8, 24)), 0.95f)
                else lerp(0xB88C54, 0xD8B678, 0.5f + 0.5f * sin(r * 0.95f))
            }
            8 -> {
                worley(u, v, 6, 30)
                var m = 1.12f - wd1 * 0.85f
                if (wd2 - wd1 < 0.08f) m *= 0.55f
                if (wd1 < 0.12f && wid > 0.7f) m *= 1.18f
                mul(lerp(0x286F2B, 0x6CC63F, wid), m * (0.94f + 0.12f * h(x, y, 31)))
            }
            9 -> {
                val row = y / 8; val xs = (x + (row % 2) * 16) % 32
                val grain = vn(u, v, 2, 16, 41)
                var c = mul(lerp(0xC99555, 0xE6BA77, h(row, 0, 40)), 0.9f + 0.2f * grain)
                if (y % 8 == 7) c = mul(c, 0.6f) else if (y % 8 == 0) c = mul(c, 1.1f)
                if (xs == 0) c = mul(c, 0.65f)
                if ((x == 3 || x == 28) && y % 8 == 3) c = 0xFF6A5A4A.toInt()
                c
            }
            10 -> {
                worley(u, v, 5, 50)
                val c = lerp(0x2D7CD4, 0x56B0EE, fbm(u, v, 51))
                if (wd2 - wd1 < 0.1f) lerp(c, 0xC4E9FF, 0.5f) else c
            }
            11 -> {
                val row = y / 8; val off = (row % 2) * 8; val xs = (x + off) % 16
                if (y % 8 == 0 || xs == 0) mul(0xCFC7BC, 0.88f + 0.16f * h(x, y, 60))
                else {
                    var c = mul(lerp(0xB04632, 0xD46C4C, h(row * 5 + (x + off) / 16, row, 61)), 0.9f + 0.2f * fbm(u, v, 62))
                    if (y % 8 == 1) c = mul(c, 1.14f) else if (y % 8 == 7) c = mul(c, 0.84f)
                    c
                }
            }
            12, 13, 14 -> {
                val col = when (t) { 12 -> 0xFF8FC8; 13 -> 0x4F9BFF; else -> 0xFFD84A }
                val e = minOf(minOf(x, y), minOf(31 - x, 31 - y))
                var m = 1.08f - 0.16f * (x + y) / 62f + 0.03f * (vn(u, v, 8, 8, 70 + t) - 0.5f)
                if (e < 2) m *= 0.78f else if (e < 4) m *= 1.1f
                mul(col, m)
            }
            15 -> {
                val dx = x - 15.5f; val dy = y - 15.5f
                val frame = x < 3 || x > 28 || y < 3 || y > 28 || x == 15 || x == 16 || y == 15 || y == 16
                if (frame) mul(0x8E6A24, 0.85f + 0.3f * h(x, y, 80))
                else lerp(0xFFF8D2, 0xFFC24A, sqrt(dx * dx + dy * dy) / 22f)
            }
            16 -> tuft(x, y)
            17, 18, 19 -> flower(x, y, when (t) { 17 -> 0xFF7FB8; 18 -> 0xFFD84A; else -> 0xE8F0FF })
            else -> -1
        }
    }

    private fun tuft(x: Int, y: Int): Int {
        val yb = 31 - y; var out = CLEAR
        for (k in 0 until 7) {
            val cx = 2.5f + k * 4.2f + (h(k, 0, 70) - 0.5f) * 2f
            val ht = 14f + 17f * h(k, 1, 70)
            if (yb >= ht) continue
            val f = yb / ht; val lean = (h(k, 2, 70) - 0.5f) * 9f
            val xc = cx + lean * f * f; val w = 1.9f * (1 - f) + 0.35f
            if (abs(x + 0.5f - xc) < w) out = mul(lerp(0x2A7326, 0xA8E366, f), 0.9f + 0.2f * h(k, 3, 70))
        }
        return out
    }

    private fun flower(x: Int, y: Int, petal: Int): Int {
        val stem = (x == 15 || x == 16) && y >= 12
        if (stem) return mul(0x3E9A34, 0.85f + 0.3f * h(x, y, 90))
        if ((y in 22..25 && x in 9..14) || (y in 19..22 && x in 17..22)) return 0xFF4FAA3F.toInt()
        val dx = x - 15.5f; val dy = y - 9f; val r = sqrt(dx * dx + dy * dy)
        if (r < 2.4f) return 0xFFFFE58A.toInt()
        if (r < 7f && (r < 5.2f || abs(sin(Math.atan2(dy.toDouble(), dx.toDouble()).toFloat() * 3f)) > 0.5f))
            return mul(petal, 1.1f - r * 0.05f)
        return CLEAR
    }

    private fun build(): Bitmap {
        val b = Bitmap.createBitmap(T * NT, T, Bitmap.Config.ARGB_8888)
        for (t in 0 until 20) for (y in 0 until T) for (x in 0 until T) b.setPixel(t * T + x, y, px(t, x, y))
        return b
    }
}
