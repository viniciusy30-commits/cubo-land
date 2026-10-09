package com.cuboland.app

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/** Atlas pastel pintado por código: 64x64 por tile, formas lisas e suaves (estilo fofinho / Maple / Hytale). */
object Atlas {
    const val T = 64
    const val NT = 32          // slots (potência de 2). Usados: 0..19
    val bmp: Bitmap by lazy { build() }
    val tiles: Array<Bitmap> by lazy { Array(20) { Bitmap.createBitmap(bmp, it * T, 0, T, T) } }
    private const val TAU = 6.2831853f
    private const val CLEAR = 0x004F9A3A

    private fun h(x: Int, y: Int, s: Int): Float {
        var k = x * 73856093 xor (y * 19349663) xor (s * 83492791)
        k = (k xor (k ushr 13)) * 1274126177
        k = k xor (k ushr 16)
        return (k and 0xffff) / 65535f
    }
    private fun wrap(i: Int, p: Int) = ((i % p) + p) % p
    private fun fr(x: Float) = x - floor(x)
    private fun sm(a: Float, b: Float, x: Float): Float { val t = ((x - a) / (b - a)).coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
    private fun op(c: Int) = (255 shl 24) or (c and 0xFFFFFF)

    /** ruído suave que repete certinho nas bordas */
    private fun vn(u: Float, v: Float, fx: Int, fy: Int, s: Int): Float {
        val x = u * fx; val y = v * fy
        val xi = floor(x).toInt(); val yi = floor(y).toInt()
        val ax = x - xi; val ay = y - yi
        val sx = ax * ax * (3 - 2 * ax); val sy = ay * ay * (3 - 2 * ay)
        val a = h(wrap(xi, fx), wrap(yi, fy), s); val b = h(wrap(xi + 1, fx), wrap(yi, fy), s)
        val c = h(wrap(xi, fx), wrap(yi + 1, fy), s); val d = h(wrap(xi + 1, fx), wrap(yi + 1, fy), s)
        val t = a + (b - a) * sx; val w = c + (d - c) * sx
        return t + (w - t) * sy
    }

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

    // ---------- texturas ----------
    private fun grassTop(u: Float, v: Float): Int {
        var c = lerp(0x86D36F, 0xB5EC8C, vn(u, v, 3, 3, 1) * 0.6f + vn(u, v, 6, 6, 2) * 0.4f)
        worley(u, v, 4, 4)
        c = lerp(c, 0xCBF6A6, sm(0.7f, 0.25f, wd1) * 0.5f)                 // manchas macias de luz
        worley(u, v, 8, 9)
        if (wid > 0.86f) c = lerp(c, if (wid > 0.93f) 0xFFFFFF else 0xFFC4DD, sm(0.16f, 0.1f, wd1))  // florzinhas
        return c
    }

    private fun dirt(u: Float, v: Float): Int {
        var c = lerp(0xC9996B, 0xB98B5F, vn(u, v, 3, 3, 3))
        worley(u, v, 5, 12)
        if (wid > 0.45f) c = lerp(c, lerp(0xE6CBA4, 0xD8B98F, wid), sm(0.28f, 0.2f, wd1))
        return c
    }

    private fun grassSide(u: Float, v: Float): Int {
        val e = 0.27f + 0.05f * sin(u * TAU * 3f + 1f) + 0.035f * sin(u * TAU * 5f + 2f) + 0.025f * sin(u * TAU * 8f)
        val g = lerp(0xB5EC8C, 0x7FCB6B, sm(0f, e, v))
        val gg = lerp(g, 0x63B05B, sm(e - 0.05f, e, v))
        val d = mul(dirt(u, v), 0.86f + 0.14f * sm(e, e + 0.12f, v))
        return lerp(d, gg, sm(e + 0.012f, e - 0.012f, v))
    }

    private fun stone(u: Float, v: Float): Int {
        worley(u, v, 4, 11)
        val c = mul(lerp(0xB7BECC, 0xD6DBE6, wid), 1.06f - wd1 * 0.22f)
        return lerp(c, 0x8E96A8, sm(0.16f, 0.04f, wd2 - wd1))
    }

    private fun sand(u: Float, v: Float): Int {
        var c = lerp(0xF7E7B7, 0xFFF3D2, vn(u, v, 3, 3, 3))
        c = mul(c, 1f + 0.03f * sin((v * 3f + vn(u, v, 3, 3, 5) * 0.7f) * TAU))
        worley(u, v, 7, 17)
        if (wid > 0.85f) c = lerp(c, 0xE9D3A0, sm(0.2f, 0.12f, wd1) * 0.8f)
        return c
    }

    private fun barkSide(u: Float, v: Float): Int {
        val s = 0.5f + 0.5f * sin(u * TAU * 5f + vn(u, v, 2, 3, 20) * 3f)
        var c = mul(lerp(0xA9774F, 0xC9946A, s), 1f - 0.16f * sm(0.82f, 1f, s))
        val d = sqrt((u - 0.32f) * (u - 0.32f) + (v - 0.62f) * (v - 0.62f) * 0.6f)
        if (d < 0.08f) c = lerp(c, 0x8A5C3A, sm(0.08f, 0.05f, d))
        return c
    }

    private fun barkTop(u: Float, v: Float): Int {
        val dx = u - 0.5f; val dy = v - 0.5f
        val r = sqrt(dx * dx + dy * dy) + (vn(u, v, 3, 3, 23) - 0.5f) * 0.04f
        val c = lerp(0xE7C28F, 0xF6DDB4, 0.5f + 0.5f * sin(r * TAU * 5.5f))
        return lerp(c, 0xB07F55, sm(0.40f, 0.44f, max(abs(dx), abs(dy))))
    }

    /** folha da referência (como na foto 2): pixels em 4 tons de verde, com buraquinhos que mostram o interior da copa */
    private fun leaves(u: Float, v: Float): Int {
        val px = floor(u * 16f).toInt().coerceIn(0, 15); val py = floor(v * 16f).toInt().coerceIn(0, 15)
        if (h(px, py, 91) < 0.3f && h(px, py / 2, 92) < 0.7f) return CLEAR
        val k = h(px / 2, py, 93) * 0.6f + h(px, py, 94) * 0.4f
        val c = when { k < 0.25f -> 0x25500F; k < 0.5f -> 0x3A7519; k < 0.78f -> 0x56A02A; else -> 0x7CC43F }
        return op(c)
    }

    /** placas 3D de folha (cubinhos que saem do bloco, como na referência): pixels grandes em 4 tons de verde, sem buracos */
    private fun leafVox(u: Float, v: Float): Int {
        val px = floor(u * 8f).toInt().coerceIn(0, 7); val py = floor(v * 8f).toInt().coerceIn(0, 7)
        val k = h(px, py, 111) * 0.7f + h(px / 2, py, 112) * 0.3f
        val c = when { k < 0.22f -> 0x25500F; k < 0.5f -> 0x3A7519; k < 0.78f -> 0x56A02A; else -> 0x7CC43F }
        return op(c)
    }

    /** cartão com 5 folhinhas pontudas (fundo transparente), 2 variantes, que saem do bloco de folha */
    private fun leafCard(u: Float, v: Float, variant: Int): Int {
        val g = 32
        val qu = (floor(u * g) + 0.5f) / g; val qv = (floor(v * g) + 0.5f) / g
        val cxs = if (variant == 0) floatArrayOf(0.5f, 0.32f, 0.68f, 0.42f, 0.6f) else floatArrayOf(0.5f, 0.3f, 0.7f, 0.55f, 0.4f)
        val cys = if (variant == 0) floatArrayOf(0.55f, 0.62f, 0.6f, 0.38f, 0.35f) else floatArrayOf(0.6f, 0.5f, 0.5f, 0.32f, 0.34f)
        val angs = if (variant == 0) floatArrayOf(1.5708f, 2.3f, 0.84f, 2.0f, 1.15f) else floatArrayOf(1.4f, 2.6f, 0.55f, 0.9f, 2.2f)
        val lens = floatArrayOf(0.26f, 0.26f, 0.26f, 0.2f, 0.2f)
        val lights = floatArrayOf(0.3f, 0.55f, 0.85f, 0.45f, 0.95f)
        var out = CLEAR
        for (i in 0 until 5) {
            val len = lens[i]; val wid = len * 0.46f
            val dx = qu - cxs[i]; val dy = qv - cys[i]
            val ca = cos(angs[i]); val sa = sin(angs[i])
            val al = dx * ca + dy * sa; val b = -dx * sa + dy * ca
            val t = al / len
            if (abs(t) >= 1f) continue
            val half = wid * (1f - t * t)
            if (abs(b) >= half) continue
            val side = b / max(half, 0.001f)
            var c = lerp(0x72C47C, 0xC4F2A6, (lights[i] * 0.6f + (0.5f - side * 0.5f) * 0.4f).coerceIn(0f, 1f))
            if (side > 0.5f) c = lerp(c, 0x469A60, 0.5f)
            if (abs(b) < 0.016f && t > -0.8f) c = lerp(c, 0xE6FFC8, 0.5f)
            out = op(c)
        }
        return out
    }

    private fun plank(u: Float, v: Float): Int {
        val row = floor(v * 4f).toInt(); val fy = fr(v * 4f)
        var c = lerp(0xEFCB93, 0xF8DDB0, h(row, 0, 40))
        c = mul(c, 1f + 0.03f * sin((fy * 3f + vn(u, v, 2, 4, 41)) * TAU))
        c = lerp(c, 0xC99A62, max(sm(0.07f, 0f, fy), sm(0.93f, 1f, fy)))
        val off = if (row % 2 == 0) 0.25f else 0.75f
        val du = abs(u - off); c = lerp(c, 0xC99A62, sm(0.02f, 0f, min(du, 1f - du)))
        val ny = (fy - 0.5f) * 0.25f
        val nd = min(sqrt((u - 0.07f) * (u - 0.07f) + ny * ny), sqrt((u - 0.93f) * (u - 0.93f) + ny * ny))
        return if (nd < 0.018f) op(0xB88A58) else c
    }

    private fun brick(u: Float, v: Float): Int {
        val row = floor(v * 4f).toInt(); val fy = fr(v * 4f)
        val bx = u * 2f + (row % 2) * 0.5f
        val fx = fr(bx); val id = floor(bx).toInt()
        val e = min(min(fx, 1f - fx) * 0.5f, min(fy, 1f - fy) * 0.25f)
        val b = mul(lerp(0xE98F7A, 0xF6B49C, h(row * 5 + id, row, 61)), 1.05f - fy * 0.14f)
        return lerp(b, 0xF3EBDD, 1f - sm(0.012f, 0.03f, e))
    }

    private fun water(u: Float, v: Float): Int {
        val k = 0.5f + 0.5f * sin((u + v) * TAU * 2f + vn(u, v, 3, 3, 50) * 3f)
        return lerp(lerp(0x6FCDEB, 0x94E0F6, k), 0xFFFFFF, sm(0.93f, 1f, k) * 0.25f)
    }

    private fun candy(u: Float, v: Float, col: Int): Int {
        val e = min(min(u, v), min(1f - u, 1f - v))
        var c = mul(col, 1.05f - 0.1f * (u + v))
        c = lerp(c, mul(col, 0.86f), sm(0.07f, 0f, e))
        val d = sqrt((u - 0.3f) * (u - 0.3f) * 0.7f + (v - 0.27f) * (v - 0.27f) * 1.6f)
        return lerp(c, 0xFFFFFF, sm(0.16f, 0.07f, d) * 0.55f)
    }

    private fun lantern(u: Float, v: Float): Int {
        val e = min(min(u, v), min(1f - u, 1f - v))
        if (e < 0.09f || abs(u - 0.5f) < 0.03f || abs(v - 0.5f) < 0.03f) return lerp(0xEBB85E, 0xD39B45, v)
        val d = sqrt((u - 0.5f) * (u - 0.5f) + (v - 0.5f) * (v - 0.5f))
        return lerp(0xFFF7CF, 0xFFD27C, d * 1.6f)
    }

    /** tufinho de grama: poucas lâminas gordinhas de ponta redonda */
    private fun tuft(u: Float, v: Float): Int {
        val yb = 1f - v; var out = CLEAR
        for (k in 0 until 4) {
            val cx = 0.16f + k * 0.23f + (h(k, 0, 70) - 0.5f) * 0.06f
            val ht = if (k == 1) 0.9f else 0.42f + 0.38f * h(k, 1, 70)
            if (yb >= ht) continue
            val f = yb / ht; val lean = (h(k, 2, 70) - 0.5f) * 0.28f
            val w = 0.085f * sqrt(1f - f * f * f)
            if (abs(u - (cx + lean * f * f)) < w) out = lerp(0x88D474, 0xD9F8A8, f)
        }
        return out
    }

    /** margaridinha: pétalas redondas + miolo */
    private fun flower(u: Float, v: Float, petal: Int): Int {
        val dc = sqrt((u - 0.5f) * (u - 0.5f) + (v - 0.27f) * (v - 0.27f))
        if (dc < 0.055f) return op(0xFFD95A)
        for (k in 0 until 6) {
            val a = k * TAU / 6f
            val dx = u - (0.5f + cos(a) * 0.1f); val dy = v - (0.27f + sin(a) * 0.1f)
            if (sqrt(dx * dx + dy * dy) < 0.078f) return mul(petal, 1.02f - 0.08f * dc * 4f)
        }
        if (abs(u - 0.5f) < 0.018f && v > 0.34f) return lerp(0x7BCB6B, 0x5DB65A, v)
        val lx = u - 0.4f; val ly = v - 0.78f
        if ((lx * lx) / 0.0036f + (ly * ly) / 0.0009f < 1f) return op(0x6CC15F)
        return CLEAR
    }

    private fun sample(t: Int, u: Float, v: Float): Int = when (t) {
        0 -> -1
        1 -> grassTop(u, v)
        2 -> grassSide(u, v)
        3 -> dirt(u, v)
        4 -> stone(u, v)
        5 -> sand(u, v)
        6 -> barkSide(u, v)
        7 -> barkTop(u, v)
        8 -> leaves(u, v)
        9 -> plank(u, v)
        10 -> water(u, v)
        11 -> brick(u, v)
        12 -> candy(u, v, 0xFFB3D6)
        13 -> candy(u, v, 0x9BC9FF)
        14 -> candy(u, v, 0xFFE38F)
        15 -> lantern(u, v)
        16 -> tuft(u, v)
        17 -> flower(u, v, 0xFF9DC6)
        18 -> flower(u, v, 0xFFE170)
        19 -> flower(u, v, 0xFFFFFF)
        20 -> leafCard(u, v, 0)
        21 -> leafCard(u, v, 1)
        22 -> leafVox(u, v)
        else -> -1
    }


    /** 9 estágios de rachadura (slots 23..31): galhos pontiagudos que crescem, com sombra, brilho de borda, buracos e lascas */
    const val CRACK0 = 23
    private class Br(val xs: FloatArray, val ys: FloatArray, val t0: Float, val t1: Float, val w0: Float, val w1: Float)

    private fun paintCracks(b: Bitmap) {
        val rd = java.util.Random(11)
        val brs = ArrayList<Br>()
        val ox = 32f + (rd.nextFloat() - 0.5f) * 6f; val oy = 32f + (rd.nextFloat() - 0.5f) * 6f
        fun branch(x0: Float, y0: Float, ang0: Float, len: Float, t0: Float, t1: Float, w0: Float, w1: Float, depth: Int) {
            val n = (len / 2.6f).toInt().coerceAtLeast(3)
            val xs = FloatArray(n + 1); val ys = FloatArray(n + 1)
            var x = x0; var y = y0; var an = ang0
            xs[0] = x; ys[0] = y
            for (i in 1..n) {
                an += (rd.nextFloat() - 0.5f) * 0.65f
                val st = len / n * (0.8f + rd.nextFloat() * 0.5f)
                x += cos(an) * st; y += sin(an) * st; xs[i] = x; ys[i] = y
            }
            brs.add(Br(xs, ys, t0, t1, w0, w1))
            if (depth < 2) {
                val k = if (depth == 0) 2 else 1
                for (q in 0 until k) {
                    val i = (n * (0.3f + rd.nextFloat() * 0.45f)).toInt().coerceIn(1, n - 1)
                    val side = if (rd.nextBoolean()) 1f else -1f
                    val ta = t0 + (t1 - t0) * (i.toFloat() / n)
                    branch(xs[i], ys[i], an + side * (0.55f + rd.nextFloat() * 0.6f), len * (0.35f + rd.nextFloat() * 0.25f),
                        ta + 0.05f, (ta + 0.35f).coerceAtMost(1f), w0 * 0.55f, 0.7f, depth + 1)
                }
            }
        }
        val nMain = 5
        for (k in 0 until nMain) branch(ox, oy, k * TAU / nMain + rd.nextFloat() * 0.6f, 13f + rd.nextFloat() * 11f, 0f, 0.7f + rd.nextFloat() * 0.25f, 1.7f, 0.55f, 0)
        // lascas / buracos perto das rachaduras
        val chips = ArrayList<FloatArray>()
        for (i in 0 until 0) { val br = brs[rd.nextInt(brs.size)]; val j = rd.nextInt(br.xs.size)
            chips.add(floatArrayOf(br.xs[j] + (rd.nextFloat() - 0.5f) * 4f, br.ys[j] + (rd.nextFloat() - 0.5f) * 4f, 1.4f + rd.nextFloat() * 1.8f, br.t0 + 0.1f + rd.nextFloat() * 0.3f)) }
        val core = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { strokeCap = android.graphics.Paint.Cap.ROUND; style = android.graphics.Paint.Style.STROKE }
        val cv = android.graphics.Canvas(b)
        for (st in 0 until 9) {
            val f = (st + 1) / 9f
            cv.save(); cv.translate(((CRACK0 + st) * T).toFloat(), 0f); cv.clipRect(0f, 0f, T.toFloat(), T.toFloat())
            // 3 passadas: sombra, brilho de borda, núcleo escuro
            for (pass in 0 until 3) for (br in brs) {
                val p = ((f - br.t0) / (br.t1 - br.t0)).coerceIn(0f, 1f)
                if (p <= 0f) continue
                val n = br.xs.size - 1; val vis = p * n
                for (i in 0 until n) {
                    if (i >= vis) break
                    val fe = (vis - i).coerceAtMost(1f)
                    val wv = br.w0 + (br.w1 - br.w0) * (i.toFloat() / n)
                    val x1 = br.xs[i] + (br.xs[i + 1] - br.xs[i]) * fe; val y1 = br.ys[i] + (br.ys[i + 1] - br.ys[i]) * fe
                    when (pass) {
                        0 -> { core.color = 0x66201008; core.strokeWidth = wv + 1.3f; cv.drawLine(br.xs[i], br.ys[i], x1, y1, core) }
                        1 -> { core.color = 0x80FFF3DC.toInt(); core.strokeWidth = wv * 0.8f; cv.drawLine(br.xs[i] - 0.7f, br.ys[i] - 0.7f, x1 - 0.7f, y1 - 0.7f, core) }
                        else -> { core.color = 0xF0463226.toInt(); core.strokeWidth = wv; cv.drawLine(br.xs[i], br.ys[i], x1, y1, core) }
                    }
                }
            }
            core.style = android.graphics.Paint.Style.FILL
            core.color = 0xF0463226.toInt(); cv.drawCircle(ox, oy, 1.0f + 1.2f * f, core)
            for (c in chips) if (f >= c[3]) { core.color = 0xFF180D07.toInt(); cv.drawCircle(c[0], c[1], c[2], core)
                core.color = 0x78FFEBC8; cv.drawCircle(c[0] - 0.7f, c[1] - 0.7f, c[2] * 0.45f, core) }
            core.style = android.graphics.Paint.Style.STROKE
            cv.restore()
        }
    }

    private fun build(): Bitmap {
        val w = T * NT
        val out = IntArray(w * T)
        for (t in 0 until 23) for (y in 0 until T) for (x in 0 until T) {
            var a = 0; var r = 0f; var g = 0f; var bl = 0f
            for (sy in 0..1) for (sx in 0..1) {      // antialias 2x2
                val c = sample(t, (x + 0.25f + sx * 0.5f) / T, (y + 0.25f + sy * 0.5f) / T)
                val ca = (c ushr 24) and 255
                a += ca; r += ((c shr 16) and 255) * ca; g += ((c shr 8) and 255) * ca; bl += (c and 255) * ca
            }
            out[y * w + t * T + x] = if (a == 0) CLEAR
                else ((a / 4) shl 24) or ((r / a).toInt() shl 16) or ((g / a).toInt() shl 8) or (bl / a).toInt()
        }
        val b = Bitmap.createBitmap(w, T, Bitmap.Config.ARGB_8888)
        b.setPixels(out, 0, w, 0, 0, w, T)
        paintCracks(b)
        return b
    }
}
