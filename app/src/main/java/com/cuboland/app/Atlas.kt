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
    const val NT = 32          // colunas do atlas (potência de 2)
    /** linhas do atlas: começa em 16 (512 tiles) e dobra sozinho quando faltar espaço (potência de 2 pros mipmaps); 0..23 = tiles originais, depois os blocos novos (Blk) */
    val ROWS: Int by lazy { var r = 16; val n = Blk.tileCount; while (r * NT < n && r < 512) r *= 2; r }
    val bmp: Bitmap by lazy { build() }
    @Volatile var raw: IntArray? = null   // preenchido por build()
    private val tileCache = arrayOfNulls<Bitmap>(NT * ROWS)
    /** recorte de um tile do atlas (ícones do HUD) */
    fun tile(i: Int): Bitmap {
        val k = if (i in 0 until NT * ROWS) i else 0
        val c = tileCache[k]
        if (c != null) return c
        val b = Bitmap.createBitmap(bmp, (k % NT) * T, (k / NT) * T, T, T)
        tileCache[k] = b
        return b
    }
    /** coordenada U/V dentro do atlas 2D: t = 0..1 dentro do tile */
    fun u(tile: Int, t: Float): Float = ((tile % NT) + 0.01f + t * 0.98f) / NT
    fun v(tile: Int, t: Float): Float = ((tile / NT) + 0.01f + t * 0.98f) / ROWS
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
        c = mul(c, 0.84f + 0.3f * vn(u, v, 24, 12, 7))                       // lâminas de grama
        if (h(floor(u * 32f).toInt(), floor(v * 32f).toInt(), 8) > 0.9f) c = mul(c, 0.8f)
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

    /** miolo da madeira: tom quente opaco com veios, sem borda */
    private fun heartwood(u: Float, v: Float): Int {
        val dx = u - 0.5f; val dy = v - 0.5f
        val r = sqrt(dx * dx + dy * dy) + (vn(u, v, 3, 3, 23) - 0.5f) * 0.05f
        return lerp(0xC99A66, 0xDDB583, 0.5f + 0.5f * sin(r * TAU * 4f))
    }

    private fun barkTop(u: Float, v: Float): Int {
        val dx = u - 0.5f; val dy = v - 0.5f
        val r = sqrt(dx * dx + dy * dy) + (vn(u, v, 3, 3, 23) - 0.5f) * 0.04f
        val c = lerp(0xE7C28F, 0xF6DDB4, 0.5f + 0.5f * sin(r * TAU * 5.5f))
        return lerp(c, 0xB07F55, sm(0.40f, 0.44f, max(abs(dx), abs(dy))))
    }

    private val OAK = intArrayOf(0x25500F, 0x3A7519, 0x56A02A, 0x7CC43F)
    /** folha do carvalho: mesmas 4 cores de sempre, agora em folhinhas sobrepostas (com nervura e sombra projetada) e buraquinhos que mostram o miolo da copa */
    private fun leaves(u: Float, v: Float): Int {
        val px = floor(u * 16f).toInt().coerceIn(0, 15); val py = floor(v * 16f).toInt().coerceIn(0, 15)
        val c = leafGrid(OAK, 91, 0)[py * 16 + px]
        return if ((c ushr 24) == 0) CLEAR else c
    }

    private val leafCache = HashMap<Int, IntArray>()
    /** grade 16x16 de folhagem: ~28 folhinhas pontudas em 3 camadas, cada uma com nervura clara, lado de baixo mais escuro e sombra caindo na de baixo */
    private fun leafGrid(pal: IntArray, seed: Int, accent: Int): IntArray {
        val key = pal.contentHashCode() * 31 + seed * 7 + accent
        leafCache[key]?.let { return it }
        val g = 16; val gf = 16f
        val tone = IntArray(g * g) { 1 }
        val cov = IntArray(g * g)
        val rnd = java.util.Random(seed * 7919L + 13L)
        val n = 28
        for (k in 0 until n) {
            val cx = rnd.nextFloat() * gf; val cy = rnd.nextFloat() * gf
            val ang = rnd.nextFloat() * 3.1416f
            val len = 2.6f + rnd.nextFloat() * 2.2f; val wd = 1.2f + rnd.nextFloat() * 0.9f
            val lay = k * 3 / n
            val ca = cos(ang); val sa = sin(ang)
            for (pass in 0..1) {
                val off = if (pass == 0) 1f else 0f
                for (y in 0 until g) for (x in 0 until g) {
                    var dx = x + 0.5f - cx - off; var dy = y + 0.5f - cy - off
                    dx -= gf * Math.round(dx / gf).toFloat(); dy -= gf * Math.round(dy / gf).toFloat()
                    val al = dx * ca + dy * sa; val b = -dx * sa + dy * ca
                    val t = al / len
                    if (abs(t) >= 1f) continue
                    val half = wd * (1f - t * t)
                    if (abs(b) >= half) continue
                    val i = y * g + x
                    if (pass == 0) { tone[i] = max(0, tone[i] - 1) }
                    else {
                        var tn = lay
                        if (b < -0.3f * half) tn += 1 else if (b > 0.45f * half) tn -= 1
                        if (abs(b) < 0.4f && t > -0.7f) tn += 1
                        tone[i] = tn.coerceIn(0, 4); cov[i]++
                    }
                }
            }
        }
        val out = IntArray(g * g)
        val pn = pal.size
        for (y in 0 until g) for (x in 0 until g) {
            val i = y * g + x
            if (cov[i] == 0 && h(x, y, seed + 5) < 0.7f) { out[i] = CLEAR; continue }
            var c = pal[(tone[i] * (pn - 1) + 2) / 4]
            if (accent != 0 && tone[i] >= 3 && h(x, y, seed + 9) > 0.92f) c = accent
            out[i] = op(c)
        }
        leafCache[key] = out
        return out
    }

    /** relevo em todos os tiles: altura = brilho suavizado + granulado por pixel; luz vinda de cima-esquerda (realce/sombra) + oclusão nos buracos e frestas */
    private fun blurWrap(src: FloatArray, r: Int): FloatArray {
        val n = T; val tmp = FloatArray(n * n); val out = FloatArray(n * n); val k = (2 * r + 1).toFloat()
        for (y in 0 until n) for (x in 0 until n) { var s = 0f; for (d in -r..r) s += src[y * n + wrap(x + d, n)]; tmp[y * n + x] = s / k }
        for (y in 0 until n) for (x in 0 until n) { var s = 0f; for (d in -r..r) s += tmp[wrap(y + d, n) * n + x]; out[y * n + x] = s / k }
        return out
    }

    private fun depth(px: IntArray, w: Int, x0: Int, y0: Int, str: Float, pix: Boolean, sd: Int) {
        val n = T * T
        val h0 = FloatArray(n); val solid = BooleanArray(n)
        var mean = 0f; var cnt = 0
        for (y in 0 until T) for (x in 0 until T) {
            val c = px[(y0 + y) * w + x0 + x]; val i = y * T + x
            if ((c ushr 24) >= 100) {
                solid[i] = true
                val l = (((c shr 16) and 255) * 0.299f + ((c shr 8) and 255) * 0.587f + (c and 255) * 0.114f) / 255f
                h0[i] = l; mean += l; cnt++
            } else h0[i] = -0.2f
        }
        if (cnt == 0) return
        mean /= cnt
        var vr = 0f
        for (i in 0 until n) if (solid[i]) { val d = h0[i] - mean; vr += d * d }
        vr /= cnt
        val amp = 0.05f + ((0.02f - vr) * 6f).coerceIn(0f, 0.12f)      // texturas chapadas ganham mais granulado de relevo
        for (y in 0 until T) for (x in 0 until T) {
            val i = y * T + x
            if (!solid[i]) continue
            if (pix) {
                h0[i] += (h(x / 4, y / 4, sd + 700) - 0.5f) * 2f * amp
                val fx = ((x % 4) + 0.5f) / 4f; val fy = ((y % 4) + 0.5f) / 4f
                h0[i] += min(min(min(fx, 1f - fx), min(fy, 1f - fy)), 0.3f) * 0.18f      // cada pixel vira um "travesseirinho"
            } else h0[i] += (vn(x / 64f, y / 64f, 32, 32, sd + 700) - 0.5f) * 2f * amp
        }
        val h1 = blurWrap(h0, 1)
        val h2 = blurWrap(h1, 3)
        for (y in 0 until T) for (x in 0 until T) {
            val i = y * T + x
            if (!solid[i]) continue
            val xp = wrap(x + 1, T); val xm = wrap(x - 1, T); val yp = wrap(y + 1, T); val ym = wrap(y - 1, T)
            val gd = h1[yp * T + xp] - h1[ym * T + xm]
            val gx = h1[y * T + xp] - h1[y * T + xm]
            val gy = h1[yp * T + x] - h1[ym * T + x]
            var s = 1f + str * 1.7f * (gd * 0.6f + gx * 0.35f + gy * 0.35f)
            val ao = h2[i] - h1[i]
            s *= 1f - str * (ao * 1.9f).coerceIn(0f, 0.34f) + str * (-ao * 1.3f).coerceIn(0f, 0.2f)
            s = s.coerceIn(0.55f, 1.4f)
            val c = px[(y0 + y) * w + x0 + x]
            val r = (((c shr 16) and 255) * s).toInt().coerceIn(0, 255)
            val gg = (((c shr 8) and 255) * s).toInt().coerceIn(0, 255)
            val bb = ((c and 255) * s).toInt().coerceIn(0, 255)
            px[(y0 + y) * w + x0 + x] = (c and (255 shl 24)) or (r shl 16) or (gg shl 8) or bb
        }
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
            var c = lerp(0x3A7519, 0x8AD04A, (lights[i] * 0.6f + (0.5f - side * 0.5f) * 0.4f).coerceIn(0f, 1f))   // mesma paleta das folhas dos blocos
            if (side > 0.5f) c = lerp(c, 0x25500F, 0.5f)
            if (abs(b) < 0.016f && t > -0.8f) c = lerp(c, 0xB4E86A, 0.5f)
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


    // ---------- texturas dos blocos novos (receitas em Blk) ----------
    private fun genNew(sp: IntArray, u: Float, v: Float): Int {
        val k = sp[0]; val c1 = sp[1]; val c2 = sp[2]; val c3 = sp[3]; val c4 = sp[4]; val s = sp[5]
        val par = sp[6] and 255; val fl = sp[6] shr 8
        var c = c1
        var a = 255
        when (k) {
            Blk.K_FLAT -> c = lerp(c1, c2, vn(u, v, 3, 3, s) * 0.65f + vn(u, v, 9, 9, s + 1) * 0.35f)
            Blk.K_STONE -> { worley(u, v, 4, s + 11); c = mul(lerp(c1, c2, wid), 1.06f - wd1 * 0.22f); c = lerp(c, c3, sm(0.16f, 0.04f, wd2 - wd1)) }
            Blk.K_COBBLE -> { worley(u, v, 4, s + 5); val st = mul(lerp(c1, c2, wid), 1.14f - wd1 * 0.5f); c = lerp(st, c3, sm(0.15f, 0.035f, wd2 - wd1)) }
            Blk.K_BRICK -> {
                val row = floor(v * 4f).toInt(); val fy = fr(v * 4f)
                val bx = u * 2f + (row % 2) * 0.5f
                val fx = fr(bx); val id = floor(bx).toInt()
                val e = min(min(fx, 1f - fx) * 0.5f, min(fy, 1f - fy) * 0.25f)
                val b = mul(lerp(c1, c2, h(row * 5 + id, row, s + 61)), 1.05f - fy * 0.14f)
                c = lerp(b, c3, 1f - sm(0.012f, 0.03f, e))
            }
            Blk.K_PLANK -> {
                val row = floor(v * 4f).toInt(); val fy = fr(v * 4f)
                var p = lerp(c1, c2, h(row, 0, s + 40))
                p = mul(p, 1f + 0.03f * sin((fy * 3f + vn(u, v, 2, 4, s + 41)) * TAU))
                p = lerp(p, c3, max(sm(0.07f, 0f, fy), sm(0.93f, 1f, fy)))
                val off = if (row % 2 == 0) 0.25f else 0.75f
                val du = abs(u - off)
                c = lerp(p, c3, sm(0.02f, 0f, min(du, 1f - du)))
            }
            Blk.K_LOGSIDE -> {
                val st = 0.5f + 0.5f * sin(u * TAU * 5f + vn(u, v, 2, 3, s + 20) * 3f)
                c = mul(lerp(c1, c2, st), 1f - 0.16f * sm(0.82f, 1f, st))
                val d = sqrt((u - 0.32f) * (u - 0.32f) + (v - 0.62f) * (v - 0.62f) * 0.6f)
                if (d < 0.08f) c = lerp(c, c3, sm(0.08f, 0.05f, d))
            }
            Blk.K_LOGTOP -> {
                val dx = u - 0.5f; val dy = v - 0.5f
                val r = sqrt(dx * dx + dy * dy) + (vn(u, v, 3, 3, s + 23) - 0.5f) * 0.04f
                c = lerp(lerp(c1, c2, 0.5f + 0.5f * sin(r * TAU * 5.5f)), c3, sm(0.40f, 0.44f, max(abs(dx), abs(dy))))
            }
            Blk.K_ORE -> {
                worley(u, v, 4, 11)
                var st = mul(lerp(c1, c2, wid), 1.06f - wd1 * 0.22f)
                st = lerp(st, mul(c2, 0.78f), sm(0.16f, 0.04f, wd2 - wd1))
                worley(u, v, 5, s + 3)
                if (wid > 0.55f) st = lerp(st, lerp(c3, c4, sm(0.1f, 0f, wd1)), sm(0.2f, 0.09f, wd1))
                c = st
            }
            Blk.K_WOOL -> {
                val wv = 0.5f + 0.5f * sin(u * TAU * 8f + floor(v * 8f) * 1.7f)
                c = mul(lerp(c1, c2, vn(u, v, 4, 4, s) * 0.7f), 0.94f + 0.08f * wv)
            }
            Blk.K_GLASS -> {
                val e = min(min(u, v), min(1f - u, 1f - v))
                if (e < 0.075f) { c = c2 }
                else {
                    c = c1; a = 150
                    if (e < 0.11f) c = lerp(c1, c2, 0.35f)
                    if (abs((u + v) - 0.62f) < 0.045f || abs((u + v) - 0.82f) < 0.018f) { c = lerp(c1, 0xFFFFFF, 0.75f); a = 215 }
                }
            }
            Blk.K_TILES -> {
                val n = if (par == 0) 4 else par
                val fu = fr(u * n); val fv = fr(v * n)
                val ti = floor(u * n).toInt(); val tj = floor(v * n).toInt()
                val e = min(min(fu, 1f - fu), min(fv, 1f - fv))
                val base = mul(lerp(c1, c2, h(ti, tj, s)), 1.04f - 0.08f * fv)
                c = lerp(base, c3, sm(0.07f, 0.02f, e))
            }
            Blk.K_CHECK -> {
                val n = if (par == 0) 4 else par
                val chk = (floor(u * n).toInt() + floor(v * n).toInt()) and 1
                c = mul(if (chk == 0) c1 else c2, 0.97f + 0.06f * vn(u, v, 6, 6, s))
            }
            Blk.K_SPECK -> {
                c = lerp(c1, c2, vn(u, v, 3, 3, s))
                worley(u, v, 9, s + 17)
                val th = if (par == 1) 0.45f else 0.7f
                if (wid > th) c = lerp(c, c3, sm(0.2f, 0.1f, wd1) * 0.9f)
                else if (c4 != 0 && wid < 0.15f) c = lerp(c, c4, sm(0.2f, 0.1f, wd1) * 0.9f)
            }
            Blk.K_STRIPE -> {
                val t = if (par == 1) u else v
                c = if ((floor(t * 4f).toInt() and 1) == 0) c1 else c2
                val fe = fr(t * 4f)
                c = lerp(c, c3, max(sm(0.1f, 0f, fe), sm(0.9f, 1f, fe)) * 0.6f)
                val fib = if (par == 1) vn(u, v, 2, 16, s) else vn(u, v, 16, 2, s)
                c = mul(c, 0.94f + 0.1f * fib)
            }
            Blk.K_GLOW -> { worley(u, v, 5, s + 9); c = mul(lerp(c1, c2, wid), 1.08f - wd1 * 0.3f); c = lerp(c, c3, sm(0.1f, 0.02f, wd2 - wd1) * 0.75f) }
            Blk.K_LAVA -> { worley(u, v, 4, s + 3); c = lerp(lerp(c1, c2, wid), c3, sm(0.2f, 0.04f, wd2 - wd1)) }
            Blk.K_LAMP -> {
                val e = min(min(u, v), min(1f - u, 1f - v))
                if (e < 0.1f || abs(u - 0.5f) < 0.025f || abs(v - 0.5f) < 0.025f) c = lerp(c2, mul(c2, 0.8f), v)
                else { val d = sqrt((u - 0.5f) * (u - 0.5f) + (v - 0.5f) * (v - 0.5f)); c = lerp(c3, c1, sm(0f, 0.5f, d)) }
            }
            Blk.K_GRAVEL -> {
                worley(u, v, 6, s)
                val pc = if (wid < 0.33f) c1 else if (wid < 0.66f) c2 else c3
                c = mul(pc, 1.12f - wd1 * 0.55f)
                c = lerp(c, mul(c2, 0.5f), sm(0.1f, 0.02f, wd2 - wd1) * 0.8f)
            }
            Blk.K_SHINGLE -> {
                val n = if (par == 0) 5 else par
                val ry = v * n; val row = floor(ry).toInt(); val fy = fr(ry)
                val xo = u * 3f + (row and 1) * 0.5f
                val fx = fr(xo); val idc = floor(xo).toInt()
                var b = mul(lerp(c1, c2, h(idc, row, s + 7)), 0.74f + 0.30f * fy)
                b = lerp(b, c3, sm(0.05f, 0f, min(fx, 1f - fx)) * 0.8f)
                c = lerp(b, c3, sm(0.06f, 0f, fy))
            }
            Blk.K_CRYSTAL -> {
                worley(u, v, 3, s + 13)
                c = mul(lerp(c1, c2, wid), 0.88f + 0.3f * wid)
                c = lerp(c, c3, sm(0.1f, 0f, wd2 - wd1) * 0.85f)
                c = lerp(c, 0xFFFFFF, 0.28f * sm(0.85f, 1f, wid))
            }
            Blk.K_SHELF -> {
                val wood = v < 0.12f || v > 0.88f || abs(v - 0.5f) < 0.06f
                if (wood) c = mul(c1, 0.96f + 0.06f * vn(u, v, 4, 8, s))
                else {
                    val row = if (v < 0.5f) 0 else 1
                    val ry = if (row == 0) (v - 0.12f) / 0.32f else (v - 0.56f) / 0.32f
                    val bi = floor(u * 8f).toInt(); val fx = fr(u * 8f)
                    val hgt = 0.6f + 0.4f * h(bi, row, s + 3)
                    if (ry < 1f - hgt) c = c2
                    else {
                        val pal = h(bi, row, s + 5)
                        val bk = if (pal < 0.2f) c3 else if (pal < 0.4f) c4 else if (pal < 0.6f) 0x5E9E5A else if (pal < 0.8f) 0xD6A441 else 0x8A5BB8
                        c = mul(bk, 0.86f + 0.2f * ry)
                        c = lerp(c, mul(bk, 0.55f), sm(0.14f, 0f, min(fx, 1f - fx)))
                    }
                }
            }
            Blk.K_BAND -> {
                val e = 0.27f + 0.05f * sin(u * TAU * 3f + 1f) + 0.035f * sin(u * TAU * 5f + 2f) + 0.025f * sin(u * TAU * 8f)
                val g = lerp(c1, mul(c1, 0.86f), sm(0f, e, v))
                val d = mul(lerp(c2, c3, vn(u, v, 3, 3, s)), 0.92f)
                c = lerp(d, g, sm(e + 0.012f, e - 0.012f, v))
            }
            Blk.K_GRAD -> {
                c = if (c3 == 0) lerp(c1, c2, v) else if (v < 0.5f) lerp(c1, c3, v * 2f) else lerp(c3, c2, (v - 0.5f) * 2f)
                c = mul(c, 0.97f + 0.06f * vn(u, v, 5, 5, s))
            }
            Blk.K_CANDY -> {
                val n = if (par == 0) 4 else par
                val st = fr((u + v) * n)
                c = lerp(c1, c2, sm(0.42f, 0.5f, st) - sm(0.92f, 1f, st))
                c = lerp(c, 0xFFFFFF, 0.1f)
            }
            Blk.K_SWIRL -> {
                val dx = u - 0.5f; val dy = v - 0.5f
                val r = sqrt(dx * dx + dy * dy)
                val ang = kotlin.math.atan2(dy, dx)
                val t = fr(r * 3.2f + ang / TAU)
                c = lerp(c1, c2, sm(0.38f, 0.5f, t) - sm(0.88f, 1f, t))
                c = lerp(c, 0xFFFFFF, 0.08f)
            }
            Blk.K_BORDER -> {
                val e = min(min(u, v), min(1f - u, 1f - v))
                c = lerp(c1, c2, vn(u, v, 4, 4, s) * 0.5f)
                c = lerp(c, c3, sm(0.1f, 0.035f, e))
                c = lerp(c, c3, sm(0.03f, 0f, abs(e - 0.15f)) * 0.7f)
            }
            Blk.K_RING -> {
                val n = if (par == 0) 3 else par
                val e = min(min(u, v), min(1f - u, 1f - v))
                val t = fr(e * n * 2f)
                c = lerp(c1, c2, sm(0.3f, 0.5f, t))
                c = lerp(c, c3, sm(0.09f, 0f, t) * 0.8f)
                c = lerp(c, c3, sm(0.04f, 0f, e))
            }
            Blk.K_CELLS -> {
                val n = if (par == 0) 4 else par
                worley(u, v, n, s)
                c = lerp(lerp(c1, c2, wid), c3, sm(0.1f, 0.03f, wd2 - wd1))
                c = lerp(c, 0xFFFFFF, 0.12f * sm(0.35f, 0f, wd1))
            }
            Blk.K_JELLY -> {
                val e = min(min(u, v), min(1f - u, 1f - v))
                if (e > 0.2f) { c = lerp(c2, c1, 0.25f); a = 240 } else { c = c1; a = 150 }
                val dd = sqrt((u - 0.25f) * (u - 0.25f) + (v - 0.22f) * (v - 0.22f))
                c = lerp(c, 0xFFFFFF, sm(0.16f, 0.04f, dd) * 0.5f)
            }
            Blk.K_ICE -> {
                c = lerp(c1, c2, vn(u, v, 3, 3, s))
                worley(u, v, 3, s + 4)
                c = lerp(c, 0xFFFFFF, sm(0.07f, 0f, wd2 - wd1) * 0.65f)
                a = if (par == 0) 190 else par
            }
            Blk.K_BLOCKY -> {
                val px = floor(u * 8f).toInt().coerceIn(0, 7); val py = floor(v * 8f).toInt().coerceIn(0, 7)
                val q = h(px, py, s) * 0.7f + h(px / 2, py, s + 1) * 0.3f
                c = if (q < 0.22f) c1 else if (q < 0.5f) c2 else if (q < 0.78f) c3 else c4
            }
            Blk.K_GLAZE -> {
                val dx = abs(u - 0.5f) * 2f; val dy = abs(v - 0.5f) * 2f
                when (par and 3) {
                    0 -> { val t = dx + dy; c = lerp(c3, c2, sm(0.45f, 0.55f, t)); c = lerp(c, c1, sm(0.95f, 1.05f, t)) }
                    1 -> { val m = min(dx, dy); c = lerp(c2, c1, sm(0.3f, 0.36f, m)); val m2 = max(dx, dy); c = lerp(c3, c, sm(0.3f, 0.36f, m2)) }
                    2 -> { val m = max(dx, dy); c = lerp(c3, c2, sm(0.28f, 0.34f, m)); c = lerp(c, c1, sm(0.62f, 0.68f, m)); c = lerp(c, c2, sm(0.86f, 0.92f, m)) }
                    else -> { val tri = abs(fr(v * 2f) - 0.5f); val z = fr(u * 2f + tri); c = lerp(c1, c2, sm(0.3f, 0.36f, z) - sm(0.8f, 0.86f, z)); c = lerp(c, c3, sm(0.09f, 0f, abs(z - 0.58f))) }
                }
            }
            Blk.K_CACTUS -> {
                val px = floor(u * 16f).toInt(); val py = floor(v * 16f).toInt()
                if (par == 1) {
                    val e = min(min(px, py), min(15 - px, 15 - py))
                    c = if (e < 2) c3 else lerp(c1, c2, vn(u, v, 4, 4, s))
                    if (e == 2) c = lerp(c, 0xFFFFFF, 0.15f)
                    if ((px == 4 || px == 11) && (py == 4 || py == 11)) c = 0xF4EBC8
                } else {
                    c = lerp(c1, c2, vn(u, v, 4, 4, s) * 0.6f)
                    if (px == 0 || px == 5 || px == 10 || px == 15) c = c3
                    else if (px == 1 || px == 6 || px == 11) c = lerp(c, 0xFFFFFF, 0.22f)
                    if ((px == 5 || px == 10) && py % 4 == 2) c = 0xF4EBC8
                    if ((px == 0 || px == 15) && py % 4 == 0) c = 0xF4EBC8
                }
            }
            Blk.K_LEAFY -> {
                worley(u, v, 5, s + 3)
                var b = if (wid < 0.25f) c1 else if (wid < 0.5f) c2 else if (wid < 0.78f) c3 else c4
                if (wd2 - wd1 < 0.05f) b = mul(c1, 0.8f)
                else if (wd1 < 0.09f) b = lerp(b, 0xFFFFFF, 0.22f)
                c = b
            }
            Blk.K_LEAF -> {
                val px = floor(u * 16f).toInt().coerceIn(0, 15); val py = floor(v * 16f).toInt().coerceIn(0, 15)
                val lg = leafGrid(intArrayOf(c1, c2, c3, c4), 91 + s, 0)[py * 16 + px]
                if ((lg ushr 24) == 0) a = 0 else c = lg and 0xFFFFFF
            }
            Blk.K_PAL -> {
                val art = NatArt.all[c1]
                val px = floor(u * 16f).toInt().coerceIn(0, 15); val py = floor(v * 16f).toInt().coerceIn(0, 15)
                val ch = art.rows[py][px]
                if (ch == '.') a = 0 else { val p = art.pal[Character.digit(ch, 16)]; c = p and 0xFFFFFF; a = p ushr 24 }
            }
            Blk.K_BUILD -> {
                val p = BuildArt.pixel(c1, c2, c3, c4, floor(u * 16f).toInt().coerceIn(0, 15), floor(v * 16f).toInt().coerceIn(0, 15))
                if ((p ushr 24) == 0) a = 0 else { c = p and 0xFFFFFF; a = p ushr 24 }
            }
            Blk.K_PALV -> {
                val p = NatVar.pixel(c1, c2, floor(u * 16f).toInt().coerceIn(0, 15), floor(v * 16f).toInt().coerceIn(0, 15))
                if ((p ushr 24) == 0) a = 0 else { c = p and 0xFFFFFF; a = p ushr 24 }
            }
            Blk.K_SPRITE -> {
                val spx = floor(u * 16f).toInt().coerceIn(0, 15); val spy = floor(v * 16f).toInt().coerceIn(0, 15)
                val p = if (c1 >= 20) Leaf3D.fruitPixel(c1 - 20, spx, spy) else Leaf3D.pixel(c1, c2, spx, spy)
                if ((p ushr 24) == 0) a = 0 else { c = p and 0xFFFFFF; a = 255 }
            }
            Blk.K_PLEAF -> {
                val art = NatArt.all[c1]
                val px = floor(u * 16f).toInt().coerceIn(0, 15); val py = floor(v * 16f).toInt().coerceIn(0, 15)
                val lg = leafGrid(intArrayOf(art.pal[0], art.pal[1], art.pal[2], art.pal[3], art.pal[4]), c1 * 13 + 1, if (art.pal.size > 5) art.pal[5] else 0)[py * 16 + px]
                if ((lg ushr 24) == 0) a = 0 else c = lg and 0xFFFFFF
            }
            Blk.K_PIX -> {
                val px = floor(u * 16f).toInt().coerceIn(0, 15); val py = floor(v * 16f).toInt().coerceIn(0, 15)
                val t = Pix.P[par][py][px] - '0'
                c = if (t <= 0) c1 else if (t == 1) c2 else if (t == 2) c3 else c4
            }
            Blk.K_MARBLE -> {
                val n1 = vn(u, v, 3, 3, s); val n2 = vn(u, v, 6, 6, s + 4)
                c = lerp(c1, c2, n1 * 0.7f + n2 * 0.3f)
                val w1 = abs(sin((u * 2f + v * 1f + n1 * 1.6f + n2 * 0.6f) * TAU))
                c = lerp(c, c3, sm(0.10f, 0f, w1) * 0.85f)
                val w2 = abs(sin((u * -1f + v * 3f + n2 * 1.3f) * TAU))
                c = lerp(c, c3, sm(0.05f, 0f, w2) * 0.45f)
            }
            Blk.K_SCREEN -> {
                if (par == 1) {   // televisão: paisagem
                    c = lerp(0x79C8F5, 0xCFEEFF, 1f - v)
                    val hill = 0.62f + 0.1f * sin(u * TAU)
                    if (v > hill) c = lerp(0x58B060, 0x3A8A3F, v)
                    val dx = u - 0.75f; val dy = v - 0.25f
                    if (dx * dx + dy * dy < 0.012f) c = 0xFFE680
                } else {   // computador: área de trabalho com janela
                    c = lerp(c1, c2, v * 0.8f)
                    if (u > 0.12f && u < 0.66f && v > 0.14f && v < 0.72f) {
                        c = 0xF2F6FA
                        if (v < 0.24f) c = c3
                        else {
                            val ln = (v - 0.28f) / 0.075f; val li = floor(ln).toInt(); val fy = fr(ln)
                            if (fy < 0.5f && li in 0..4 && u < 0.18f + 0.25f + 0.25f * h(li, 1, s)) c = lerp(0xF2F6FA, c2, 0.8f)
                        }
                    }
                    if (u > 0.74f && u < 0.9f && v > 0.14f && v < 0.74f) {
                        val ic = (v - 0.14f) / 0.2f; val fy = fr(ic)
                        if (fy < 0.6f) c = lerp(c2, 0xFFFFFF, 0.5f + 0.2f * h(floor(ic).toInt(), 2, s))
                    }
                    if (v > 0.9f) c = mul(c1, 0.5f)
                }
            }
            Blk.K_FABRIC -> {
                val wv = 0.5f + 0.5f * sin(u * TAU * 16f) * sin(v * TAU * 16f)
                c = mul(lerp(c1, c2, vn(u, v, 4, 4, s) * 0.5f), 0.92f + 0.1f * wv)
                if (par == 1) {   // capitonê: losangos com botões
                    val du = fr(u * 2f) - 0.5f; val dv = fr(v * 2f) - 0.5f
                    val dd = abs(du) + abs(dv)
                    c = mul(c, 0.82f + 0.3f * sm(0.5f, 0.12f, dd))
                    if (dd < 0.05f) c = mul(c1, 0.5f)
                }
            }
            Blk.K_WGRAIN -> {
                val pl = floor(v * 4f).toInt(); val fy = fr(v * 4f)
                var p = lerp(c1, c2, 0.2f + 0.55f * h(pl, 3, s))
                p = mul(p, 0.93f + 0.14f * (0.5f + 0.5f * sin((u * 3f + vn(u, v, 3, 12, s + 2) * 1.2f + h(pl, 0, s)) * TAU)))
                c = lerp(p, c3, max(sm(0.07f, 0f, fy), sm(0.93f, 1f, fy)) * 0.8f)
            }
            Blk.K_RUG -> {
                val e = min(min(u, v), min(1f - u, 1f - v))
                c = mul(lerp(c1, mul(c1, 0.9f), vn(u, v, 6, 6, s)), 0.94f + 0.08f * (0.5f + 0.5f * sin(u * TAU * 20f) * sin(v * TAU * 20f)))
                if (e < 0.1f) c = c2
                if (e < 0.04f || (e > 0.1f && e < 0.15f)) c = c3
                val dd = abs(u - 0.5f) + abs(v - 0.5f)
                if (dd < 0.28f && dd > 0.2f) c = c2
                if (dd < 0.1f) c = c3
            }
            Blk.K_GINGHAM -> {
                val a1 = if (fr(u * 4f) < 0.5f) 1 else 0; val b1 = if (fr(v * 4f) < 0.5f) 1 else 0
                c = if (a1 + b1 == 2) mul(c1, 0.85f) else if (a1 + b1 == 1) lerp(c1, c2, 0.5f) else c2
                c = mul(c, 0.96f + 0.06f * vn(u, v, 8, 8, s))
            }
            else -> c = c1
        }
        if (k == Blk.K_PLANK) {
            if (vn(u, v, 2, 16, s + 9) > 0.64f) c = mul(c, 0.9f)
            val nx = min(abs(u - 0.1f), abs(u - 0.9f)); val nf = fr(v * 4f)
            if (nx < 0.035f && abs(nf - 0.5f) < 0.07f) c = mul(c, 0.6f)
        }
        if (k == Blk.K_LOGSIDE) {
            val rg = fr(u * 7f + vn(u, v, 2, 4, s + 3) * 1.5f)
            if (rg < 0.14f) c = mul(c, 0.8f) else if (rg > 0.86f) c = lerp(c, 0xFFFFFF, 0.12f)
        }
        if ((fl and 8) != 0) { worley(u, v, 5, s + 201); if (wd1 < 0.1f && wid > 0.4f) c = mul(c, 0.55f) }
        if ((fl and 16) != 0) { if (h(floor(u * 16f).toInt(), floor(v * 16f).toInt(), s + 55) > 0.95f) c = lerp(c, 0xFFFFFF, 0.6f) }
        if ((fl and 1) != 0) { worley(u, v, 3, s + 101); c = lerp(c, mul(c, 0.45f), sm(0.05f, 0f, wd2 - wd1) * 0.9f) }
        if ((fl and 2) != 0) { val m = vn(u, v, 5, 5, s + 77); c = lerp(c, lerp(0x5E8F3C, 0x7FB04A, vn(u, v, 9, 9, s + 78)), sm(0.52f, 0.62f, m)) }
        if ((fl and 4) != 0) {
            val e = min(min(u, v), min(1f - u, 1f - v))
            c = mul(c, 1f - 0.16f * sm(0.07f, 0f, e))
            c = lerp(c, 0xFFFFFF, 0.14f * sm(0.05f, 0f, min(u, v)))
        }
        return (a shl 24) or (c and 0xFFFFFF)
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
        23 -> heartwood(u, v)
        else -> -1
    }


    /** rachaduras: 5 padrões x 9 estágios, em atlas próprio (2 linhas). Colunas evitam tiles especiais do shader (8, 10, 15, 22). */
    const val CK_PAT = 5; const val CK_ST = 9
    val crackCols = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 9, 11, 12, 13, 14, 16, 17, 18, 19, 20, 21, 23, 24, 25, 26, 27, 28, 29, 30, 31)
    val crackBmp: Bitmap by lazy { buildCracks() }
    private class Br(val xs: FloatArray, val ys: FloatArray, val t0: Float, val t1: Float, val w0: Float, val w1: Float)

    private fun genBranches(p: Int, rd: java.util.Random, out: ArrayList<Br>): FloatArray {
        val nMain = intArrayOf(5, 3, 5, 4, 6)[p]; val len = floatArrayOf(36f, 48f, 32f, 42f, 28f)[p]
        val jit = floatArrayOf(0.6f, 0.4f, 0.9f, 0.5f, 0.8f)[p]; val spread = floatArrayOf(5f, 10f, 7f, 16f, 4f)[p]
        val ox = 32f + (rd.nextFloat() - 0.5f) * 2f * spread; val oy = 32f + (rd.nextFloat() - 0.5f) * 2f * spread
        fun branch(x0: Float, y0: Float, ang0: Float, l: Float, t0: Float, t1: Float, w0: Float, w1: Float, depth: Int) {
            val n = (l / 2.8f).toInt().coerceAtLeast(3)
            val xs = FloatArray(n + 1); val ys = FloatArray(n + 1)
            var x = x0; var y = y0; var an = ang0
            xs[0] = x; ys[0] = y
            for (i in 1..n) {
                an += (rd.nextFloat() - 0.5f) * jit
                val st = l / n * (0.85f + rd.nextFloat() * 0.35f)
                x += cos(an) * st; y += sin(an) * st; xs[i] = x; ys[i] = y
            }
            out.add(Br(xs, ys, t0, t1, w0, w1))
            if (depth < 2) {
                val k = if (depth == 0) 2 + rd.nextInt(2) else 1
                for (q in 0 until k) {
                    val i = (n * (0.25f + rd.nextFloat() * 0.5f)).toInt().coerceIn(1, n - 1)
                    val side = if (rd.nextBoolean()) 1f else -1f
                    val ta = t0 + (t1 - t0) * (i.toFloat() / n)
                    branch(xs[i], ys[i], an + side * (0.5f + rd.nextFloat() * 0.7f), l * (0.35f + rd.nextFloat() * 0.3f),
                        (ta + 0.04f).coerceAtMost(0.9f), (ta + 0.4f).coerceAtMost(1f), w0 * 0.6f, 0.5f, depth + 1)
                }
            }
        }
        val base = rd.nextFloat() * TAU
        for (k in 0 until nMain) {
            val an = when (p) {
                1 -> if (k == 0) base else if (k == 1) base + 3.14f + (rd.nextFloat() - 0.5f) * 0.5f else base + 1.57f + (rd.nextFloat() - 0.5f)
                2 -> rd.nextInt(4) * 1.5708f + (rd.nextFloat() - 0.5f) * 0.4f
                else -> base + k * TAU / nMain + (rd.nextFloat() - 0.5f) * 0.6f
            }
            branch(ox, oy, an, len * (0.75f + rd.nextFloat() * 0.5f), 0f, 0.75f + rd.nextFloat() * 0.25f, 1.7f, 0.6f, 0)
        }
        // fraturas extras nascendo perto das bordas e crescendo pra dentro: espalham a rachadura pelo bloco todo
        val nEdge = 3 + rd.nextInt(3)
        for (i in 0 until nEdge) {
            val ea = rd.nextFloat() * TAU; val er = 24f + rd.nextFloat() * 8f
            val ex = 32f + cos(ea) * er; val ey = 32f + sin(ea) * er
            val t0 = 0.4f + rd.nextFloat() * 0.35f
            branch(ex, ey, ea + 3.14f + (rd.nextFloat() - 0.5f) * 1.2f, 12f + rd.nextFloat() * 10f, t0, (t0 + 0.3f).coerceAtMost(1f), 1.2f, 0.5f, 1)
        }
        return floatArrayOf(ox, oy)
    }

    private fun buildCracks(): Bitmap {
        val b = Bitmap.createBitmap(T * NT, T * 2, Bitmap.Config.ARGB_8888)
        val cv = android.graphics.Canvas(b)
        val core = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { strokeCap = android.graphics.Paint.Cap.ROUND; style = android.graphics.Paint.Style.STROKE }
        for (p in 0 until CK_PAT) {
            val brs = ArrayList<Br>()
            val o = genBranches(p, java.util.Random(100L + p * 37L), brs)
            for (st in 0 until CK_ST) {
                val idx = p * CK_ST + st; val row = idx / crackCols.size; val col = crackCols[idx % crackCols.size]
                val f = (st + 1) / CK_ST.toFloat()
                cv.save(); cv.translate((col * T).toFloat(), (row * T).toFloat()); cv.clipRect(0f, 0f, T.toFloat(), T.toFloat())
                core.style = android.graphics.Paint.Style.STROKE
                for (pass in 0 until 3) for (br in brs) {
                    val pr = ((f - br.t0) / (br.t1 - br.t0)).coerceIn(0f, 1f)
                    if (pr <= 0f) continue
                    val n = br.xs.size - 1; val vis = pr * n
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
                core.color = 0xF0463226.toInt(); cv.drawCircle(o[0], o[1], 1.0f + 1.2f * f, core)
                cv.restore()
            }
        }
        return b
    }

    /** estilo Minecraft fofinho: ruído por pixel, pintinhas claras/escuras, brilho no canto de cima e poucos tons de cor */
    private fun pixelArt(c: Int, px: Int, py: Int, sd: Int): Int {
        val a = (c ushr 24) and 255
        if (a == 0) return c
        val r0 = h(px, py, sd)
        var k = 1f + (r0 - 0.5f) * 0.09f + (h(px / 2, py / 2, sd + 9) - 0.5f) * 0.06f   // ruído suave: antes parecia estática de TV
        if (r0 > 0.95f) k *= 0.94f else if (r0 < 0.05f) k *= 1.05f
        if (px == 0 || py == 0) k *= 1.07f
        if (px == 15 || py == 15) k *= 0.9f
        val lv = 14f
        var out = a shl 24
        for (sh in intArrayOf(16, 8, 0)) {
            val v = (((c shr sh) and 255) * k).coerceIn(0f, 255f)
            val q = (Math.round(v / 255f * lv) / lv * 255f).toInt().coerceIn(0, 255)
            out = out or (q shl sh)
        }
        return out
    }

    private fun build(): Bitmap {
        val w = T * NT; val hh = T * ROWS
        val out = IntArray(w * hh)
        for (t in 0 until 24) for (y in 0 until T) for (x in 0 until T) {
            var a = 0; var r = 0f; var g = 0f; var bl = 0f
            for (sy in 0..1) for (sx in 0..1) {      // antialias 2x2
                val c = sample(t, (x + 0.25f + sx * 0.5f) / T, (y + 0.25f + sy * 0.5f) / T)
                val ca = (c ushr 24) and 255
                a += ca; r += ((c shr 16) and 255) * ca; g += ((c shr 8) and 255) * ca; bl += (c and 255) * ca
            }
            out[y * w + t * T + x] = if (a == 0) CLEAR
                else ((a / 4) shl 24) or ((r / a).toInt() shl 16) or ((g / a).toInt() shl 8) or (bl / a).toInt()
        }
        // terreno básico (grama, terra, pedra, areia, madeira, tábua, tijolo, cores): vira pixel art 16x16 como os blocos novos
        for (t in intArrayOf(1, 2, 3, 4, 5, 6, 7, 9, 11, 12, 13, 14)) {
            var opaque = true
            for (y in 0 until T) for (x in 0 until T) if ((out[y * w + t * T + x] ushr 24) != 255) { opaque = false; break }
            if (!opaque) continue
            for (qy in 0 until 16) for (qx in 0 until 16) {
                var r = 0; var g = 0; var bl = 0
                for (y in 0 until 4) for (x in 0 until 4) { val c = out[(qy * 4 + y) * w + t * T + qx * 4 + x]; r += (c shr 16) and 255; g += (c shr 8) and 255; bl += c and 255 }
                val avg = (255 shl 24) or ((r / 16) shl 16) or ((g / 16) shl 8) or (bl / 16)
                val pc = pixelArt(avg, qx, qy, t * 31 + 7)
                for (y in 0 until 4) for (x in 0 until 4) out[(qy * 4 + y) * w + t * T + qx * 4 + x] = pc
            }
        }
        // tronco do carvalho (tiles 6 = casca, 7 = topo): mesmos desenhos 16x16 dos outros troncos (Pix.BARK / Pix.LOGTOP), só com a paleta do carvalho
        val oakBark = intArrayOf(0x3F2B17, 0x5E4023, 0x7C5732, lerp(0x7C5732, 0xFFFFFF, 0.12f))
        val oakRings = intArrayOf(0x5E4426, 0xB08555, 0xC9A06B, lerp(0xC9A06B, 0xFFFFFF, 0.15f))
        for (t in 6..7) {
            val pat = if (t == 6) Pix.BARK else Pix.LOGTOP
            val cs = if (t == 6) oakBark else oakRings
            for (qy in 0 until 16) for (qx in 0 until 16) {
                val k = (Pix.P[pat][qy][qx] - '0').coerceIn(0, 3)
                val pc = (255 shl 24) or (cs[k] and 0xFFFFFF)
                for (y in 0 until 4) for (x in 0 until 4) out[(qy * 4 + y) * w + t * T + qx * 4 + x] = pc
            }
        }
        // pedra (4), tábua (9) e tijolo (11) do terreno básico: mesmos padrões da Construção
        for (t in intArrayOf(4, 9, 11)) {
            val bt = BuildArt.baseTile(t) ?: continue
            for (py in 0 until T) for (px in 0 until T) out[py * w + t * T + px] = BuildArt.sample(bt[0], bt[1], bt[2], 0, px * 64 / T, py * 64 / T) or (255 shl 24)
        }
        for (t in 0 until 24) bleed(out, w, t * T, 0)
        // blocos novos (receitas em Blk): 1 amostra por pixel, as formas já são suaves
        val nDefs = Blk.defs.size
        if (nDefs > 0) for (t in 24 until NT * ROWS) {
            val sp = Blk.specs[t] ?: continue
            val x0 = (t % NT) * T; val y0 = (t / NT) * T
            for (y in 0 until T) for (x in 0 until T) {
                val qx = x / 4; val qy = y / 4   // grade de 16x16 pixels grandes (pixel art)
                val g0 = if (sp[0] == Blk.K_BUILD) BuildArt.sample(sp[1], sp[2], sp[3], sp[4], x * 64 / T, y * 64 / T) else genNew(sp, (qx + 0.5f) / 16f, (qy + 0.5f) / 16f)
                val c = if (sp[0] == Blk.K_PIX || sp[0] == Blk.K_LEAF || sp[0] == Blk.K_PAL || sp[0] == Blk.K_PLEAF || sp[0] == Blk.K_SPRITE || sp[0] == Blk.K_PALV || sp[0] == Blk.K_BUILD) g0 else pixelArt(g0, qx, qy, sp[5])
                out[(y0 + y) * w + x0 + x] = if ((c ushr 24) == 0) CLEAR else c
            }
            bleed(out, w, x0, y0)
        }
        raw = out.clone()   // cópia com a cor preservada nos pixels transparentes: vai direto pra GPU (o Bitmap do Android zera essa cor)
        val b = Bitmap.createBitmap(w, hh, Bitmap.Config.ARGB_8888)
        b.setPixels(out, 0, w, 0, 0, w, hh)
        return b
    }

    /** pixels transparentes herdam a cor dos vizinhos opacos: no mipmap/filtro a borda da folha/matinho mistura verde com verde (nada de contorno escuro) */
    private fun bleed(px: IntArray, w: Int, x0: Int, y0: Int) {
        var has = false
        for (y in 0 until T) for (x in 0 until T) if ((px[(y0 + y) * w + x0 + x] ushr 24) == 0) { has = true; break }
        if (!has) return
        val filled = BooleanArray(T * T) { (px[(y0 + it / T) * w + x0 + it % T] ushr 24) != 0 }
        repeat(T / 2) {
            val add = ArrayList<IntArray>()
            for (y in 0 until T) for (x in 0 until T) {
                if (filled[y * T + x]) continue
                var r = 0; var g = 0; var b = 0; var n = 0
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= T || ny >= T || !filled[ny * T + nx]) continue
                    val c = px[(y0 + ny) * w + x0 + nx]; r += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255; n++
                }
                if (n > 0) add.add(intArrayOf(x, y, (r / n shl 16) or (g / n shl 8) or (b / n)))
            }
            if (add.isEmpty()) return
            for (a in add) { px[(y0 + a[1]) * w + x0 + a[0]] = a[2]; filled[a[1] * T + a[0]] = true }   // alpha continua 0
        }
    }
}
