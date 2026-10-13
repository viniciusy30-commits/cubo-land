package com.cuboland.app

import com.cuboland.app.BuildKit.edgeDist
import com.cuboland.app.BuildKit.lerp
import com.cuboland.app.BuildKit.sm
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Texturas da categoria Construção, estilo "fofinho" (Hytale / KoGaMa): formas arredondadas, cores clarinhas, luz suave, nada de ruído áspero.
 *
 * Cada padrão é uma função (x, y) -> amostra, em pixels de um tile 64x64 que repete certinho nas bordas. A amostra diz:
 *   h  = altura em "degraus" de relevo (-2..+2; 0 = plano): vira o relevo 3D de verdade (Relief) e também a luz pintada na textura;
 *   m  = quanto da cor de acento (musgo, pátina, vidro, azulejo...), g = quanto de rejunte, t = variação de tom por peça,
 *   a  = opacidade (vidros), sp = brilho especular (reflexinho branco), k = linha escura (chumbo do vitral, rachadura).
 * A luz vem de cima à esquerda: as quinas viram "almofadas", a sombra esfria (roxinho) e a luz esquenta. Tudo é calculado uma vez por padrão e guardado;
 * pintar um tile com outra cor é só trocar a paleta.
 */
object BuildArt {
    const val CONCRETE = 0; const val CLAY = 1; const val BRICK = 2; const val SBRICK = 3; const val SBRICK_CRACK = 4; const val SBRICK_MOSS = 5; const val COBBLE = 6; const val COBBLE_MOSS = 7; const val ROUGH0 = 8; const val ROUGH1 = 9; const val ROUGH2 = 10; const val SMOOTH = 11; const val SPECK = 12; const val TILES = 13; const val CHISEL = 14; const val PILLAR = 15; const val SANDSIDE = 16; const val OBSIDIAN = 17; const val PLANK = 18; const val MOSAIC = 19; const val SHINGLE = 20; const val PLASTER = 21; const val METAL = 22; const val METAL_P1 = 23; const val METAL_P2 = 24; const val GLASS = 25; const val WINDOW = 26; const val AZ_PLAIN = 27; const val AZ_DECOR = 28; const val STRIP = 29; const val RINGS = 30; const val GLASS_THIN = 31; const val JAN_FRAMELESS = 32; const val JAN_SLIDE = 33; const val JAN_COLONIAL = 34; const val JAN_LOUVER = 35; const val JAN_PORTHOLE = 36; const val JAN_BARS = 37; const val VITRAL = 38
    // ---- padrões novos (ids sempre no fim) ----
    const val PAINTED = 39; const val SUBWAY = 40; const val HEXTILE = 41; const val CHECKER = 42; const val HERRING = 43; const val PANEL = 44; const val TIMBER = 45; const val LOGWALL = 46
    const val GEM = 47; const val CORRUG = 48; const val PEBBLE = 49; const val SHAKE = 50; const val GL_FACET = 51; const val GL_BUBBLE = 52; const val GL_FROST = 53; const val GL_STAR = 54
    const val GL_GRID = 55; const val GL_DROPS = 56; const val GL_RAINBOW = 57; const val JAN_ARCH = 58; const val JAN_HEART = 59; const val JAN_STAR = 60; const val DIAMOND = 61; const val STEPPING = 62
    const val STRIPES = 63; const val QUILT = 64; const val SCALES = 65; const val GL_ICE = 66
    const val BASALT = 67; const val TUFF = 68; const val NETHER = 69; const val ENDSTONE = 70; const val PRISM = 71; const val POLISHED = 72; const val QUARTZ = 73; const val LONGBRICK = 74
    // ---- padrões da v4: tijolo flamengo e as 10 madeiras (uma textura própria pra cada tábua de árvore) ----
    const val TIJOLO = 75
    const val W_ABETO = 76; const val W_BETULA = 77; const val W_SELVA = 78; const val W_ACACIA = 79; const val W_CARVALHO = 80
    const val W_MANGUE = 81; const val W_CEREJEIRA = 82; const val W_BAMBU = 83; const val W_CARMESIM = 84; const val W_DISTORCIDO = 85
    const val COUNT = 86

    /** lado do tile gerado (igual a Atlas.T) */
    const val N = 64

    // modos de rejunte
    const val GC = 0; const val GP = 1; const val GD = 2; const val GL = 3; const val GW = 4; const val GWOOD = 5

    private class Def(val grout: Int, val edge: Float, val grad: Float, val glass: Boolean, val fn: (Float, Float, BuildKit.S) -> Unit)
    private val defs = arrayOfNulls<Def>(COUNT)
    /** registra um padrão (chamado por BuildPats): grout = cor do rejunte, edge = quanto escurece a borda do tile, grad = degradê suave do tile, glass = vidro (luz leve, alpha) */
    fun define(id: Int, grout: Int, edge: Float, grad: Float, glass: Boolean, fn: (Float, Float, BuildKit.S) -> Unit) { defs[id] = Def(grout, edge, grad, glass, fn) }
    fun isGlass(pat: Int): Boolean { init(); return defs.getOrNull(pat.coerceIn(0, COUNT - 1))?.glass == true }

    // ---------------------------------------------------------------- cor
    private fun mixc(a: Int, b: Int, t: Float): Int {
        val r = (((a shr 16) and 255) * (1f - t) + ((b shr 16) and 255) * t)
        val g = (((a shr 8) and 255) * (1f - t) + ((b shr 8) and 255) * t)
        val bl = ((a and 255) * (1f - t) + (b and 255) * t)
        return (r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or bl.toInt().coerceIn(0, 255)
    }
    /** deixa as cores escuras mais claras e macias (visual fofinho) */
    private fun lift(c: Int): Int {
        val r = (c shr 16) and 255; val g = (c shr 8) and 255; val b = c and 255
        val lum = (r * 0.299f + g * 0.587f + b * 0.114f) / 255f
        val k = 0.20f * (1f - lum) * (1f - lum) + 0.02f
        return mixc(c, 0xFFF6EC, k)
    }
    private fun groutColor(mode: Int, base: Int): Int = when (mode) {
        GP -> mixc(base, 0xFFFFFF, 0.55f)
        GD -> mixc(base, 0x2E2438, 0.50f)
        GL -> 0x3A3040
        GW -> 0xFBF8F2
        GWOOD -> mixc(base, 0x4A2E1C, 0.52f)
        else -> mixc(base, 0xFFF4E2, 0.72f)
    }
    private fun toneC(c: Int, t: Float): Int = if (t >= 0f) mixc(c, 0xFFFFFF, t * 0.20f) else mixc(c, 0x30202C, -t * 0.20f)
    /** luz sobre a cor: acima de 1 esquenta (creme), abaixo de 1 escurece esfriando (roxinho) */
    private fun shadeC(c: Int, s: Float): Int {
        if (s >= 1f) return mixc(c, 0xFFF1D6, ((s - 1f) * 1.5f).coerceAtMost(0.55f))
        val d = (1f - s).coerceAtMost(0.7f)
        val r = ((c shr 16) and 255) * (1f - d * 1.02f); val g = ((c shr 8) and 255) * (1f - d * 1.12f); val b = (c and 255) * (1f - d * 0.80f)
        val v = mixc((r.toInt().coerceIn(0, 255) shl 16) or (g.toInt().coerceIn(0, 255) shl 8) or b.toInt().coerceIn(0, 255), 0x6A4AA8, d * 0.05f)
        return v
    }

    // ---------------------------------------------------------------- tabela pronta de cada padrão
    class Tex {
        val L = FloatArray(N * N); val sh = FloatArray(N * N); val m = FloatArray(N * N); val g = FloatArray(N * N); val t = FloatArray(N * N)
        val a = FloatArray(N * N); val sp = FloatArray(N * N); val k = FloatArray(N * N)
    }
    private val texCache = arrayOfNulls<Tex>(COUNT * 5)
    private var inited = false
    private fun init() { if (!inited) { inited = true; BuildPats.register() } }

    fun tex(pat: Int, variant: Int = 0): Tex {
        val p = pat.coerceIn(0, COUNT - 1)
        val v = variant.coerceIn(0, 4)
        val ck = p * 5 + v
        texCache[ck]?.let { return it }
        init()
        val df = defs[p] ?: defs[CONCRETE]!!
        val tx = Tex(); val s = BuildKit.S()
        // estilo Minecraft: grade de 16x16 pixels duros (cada um vira um bloco de 4x4 no tile de 64) com um nível de relevo por pixel
        val cells = Array(256) { McPix.C() }
        val nat = McPix.has(p)
        for (cy in 0 until 16) for (cx in 0 until 16) {
            val c = cells[cy * 16 + cx]
            if (nat) McPix.cell(p, v, cx, cy, c) else fromOld(df, s, v, cx, cy, c)
        }
        for (cy in 0 until 16) for (cx in 0 until 16) {
            val c = cells[cy * 16 + cx]
            val hl = cells[cy * 16 + (cx + 15) % 16].h; val hr = cells[cy * 16 + (cx + 1) % 16].h
            val hu = cells[((cy + 15) % 16) * 16 + cx].h; val hd = cells[((cy + 1) % 16) * 16 + cx].h
            // luz de cima à esquerda: quina virada pra luz clareia, quina na sombra de um vizinho mais alto escurece
            val sh = 1f + 0.08f * (max(0, c.h - hl) + max(0, c.h - hu)) - 0.08f * (max(0, hr - c.h) + max(0, hd - c.h))
            for (y in 0 until 4) for (x in 0 until 4) {
                val i = (cy * 4 + y) * N + cx * 4 + x
                tx.L[i] = c.h.toFloat(); tx.sh[i] = sh; tx.m[i] = c.m; tx.g[i] = c.g; tx.t[i] = c.t; tx.a[i] = c.a; tx.sp[i] = c.sp; tx.k[i] = c.k
            }
        }
        texCache[ck] = tx
        return tx
    }

    /** padrões sem versão própria em McPix: o desenho antigo é amostrado em 4 pontos por pixel (pega o mais forte, pra linhas finas não sumirem) e endurecido */
    private fun fromOld(df: Def, s: BuildKit.S, v: Int, cx: Int, cy: Int, c: McPix.C) {
        var hb = 0f; var tt = 0f; var g = 0f; var m = 0f; var a = 0f; var sp = 0f; var k = 0f
        for (j in 0..1) for (i in 0..1) {
            s.reset(); s.v = v
            df.fn(cx * 4 + 1f + i * 2f, cy * 4 + 1f + j * 2f, s)
            if (abs(s.h) > abs(hb)) hb = s.h
            tt += s.t * 0.25f
            g = max(g, s.g); m = max(m, s.m); a = max(a, s.a); sp = max(sp, s.sp); k = max(k, s.k)
        }
        c.h = Math.round(hb).coerceIn(-2, 2)
        c.t = Math.round(tt / 0.15f) * 0.15f
        c.g = if (g > 0.5f) 1f else 0f
        c.m = if (m > 0.5f) 1f else 0f
        c.a = a
        c.sp = if (sp > 0.2f) 0.45f else 0f
        c.k = if (k > 0.35f) 0.8f else 0f
    }

    private val palCache = HashMap<Long, IntArray>()

    /** acento padrão de cada padrão */
    fun defAccent(pat: Int): Int = when (pat) {
        SBRICK_MOSS, COBBLE_MOSS -> 0x86C25E
        SPECK -> 0xF0A898
        OBSIDIAN -> 0x9A6AE8
        METAL_P1, METAL_P2 -> 0x7FD0A8
        WINDOW, JAN_FRAMELESS, JAN_SLIDE, JAN_COLONIAL, JAN_LOUVER, JAN_PORTHOLE, JAN_BARS, JAN_ARCH, JAN_HEART, JAN_STAR -> 0xBFE6F5
        VITRAL -> 0xF2B84A
        AZ_DECOR -> 0xFBFAF4
        TIMBER -> 0x8A5E3C
        CHECKER, HERRING, STEPPING, QUILT -> 0xFFF4E4
        GEM -> 0xFFFFFF
        DIAMOND -> 0xFFF1D0
        STRIPES -> 0xFFF4E4
        else -> 0x86C25E
    }

    /** cor do rejunte / fresta no estilo Minecraft */
    private fun mcGrout(p: Int, mode: Int, b: Int): Int = when {
        p == BRICK -> mixc(0xB9B2AA, b, 0.14f)
        p == SUBWAY -> 0xCFCBC4
        p == TIJOLO -> mixc(0xB9B2AA, b, 0.14f)
        p == LONGBRICK -> mixc(b, 0x5A5560, 0.45f)
        mode == GP -> mixc(b, 0xFFFFFF, 0.55f)
        mode == GD -> mixc(b, 0x1E1822, 0.50f)
        mode == GL -> 0x3A3040
        mode == GW -> 0xD9D6CE
        mode == GWOOD -> mixc(b, 0x2E1A0E, 0.50f)
        else -> mixc(b, 0x1C1C24, 0.40f)
    }
    private fun mcTone(c: Int, t: Float): Int = if (t >= 0f) mixc(c, 0xFFFFFF, (t * 0.22f).coerceAtMost(0.5f)) else mixc(c, 0x101018, (-t * 0.24f).coerceAtMost(0.6f))
    private fun mcShade(c: Int, s: Float): Int = if (s >= 1f) mixc(c, 0xFFFFFF, ((s - 1f) * 1.6f).coerceAtMost(0.35f)) else mixc(c, 0x0C0C14, ((1f - s) * 1.7f).coerceAtMost(0.5f))

    /** pixel ARGB do padrão: x, y = 0..63. scale = % de opacidade do miolo dos vidros (0 = 100) */
    fun sample(pat: Int, base: Int, acc: Int, scale: Int, x: Int, y: Int, variant: Int = 0): Int {
        val p = pat.coerceIn(0, COUNT - 1)
        val tx = tex(p, variant); val df = defs[p] ?: defs[CONCRETE]!!
        val i = ((y and 63) shl 6) + (x and 63)
        val b = base and 0xFFFFFF
        val ac = if (acc < 0) defAccent(p) else (acc and 0xFFFFFF)
        var c = mixc(b, mcGrout(p, df.grout, b), tx.g[i])
        c = mixc(c, ac, tx.m[i])
        c = mcTone(c, tx.t[i])
        var al = tx.a[i]
        var sh = tx.sh[i]
        if (df.glass) {
            val solid = sm(0.55f, 0.95f, al)
            sh = lerp(1f, sh, solid)
            val sc = if (scale <= 0) 100 else scale
            if (al < 0.9f) al = min(1f, al * sc / 100f)
        }
        c = mcShade(c, sh)
        if (tx.sp[i] > 0f) c = mixc(c, 0xFFFFFF, tx.sp[i].coerceIn(0f, 1f))
        if (tx.k[i] > 0f) c = mixc(c, 0x30243C, (tx.k[i] * 0.78f).coerceIn(0f, 1f))
        return ((al * 255f + 0.5f).toInt().coerceIn(0, 255) shl 24) or c
    }

    /** compat: pixel do padrão em grade 16x16 */
    fun pixel(pat: Int, base: Int, acc: Int, scale: Int, px: Int, py: Int): Int = sample(pat, base, acc, scale, px * 4 + 2, py * 4 + 2)

    private val reliefCache = arrayOfNulls<Relief.Map>(COUNT * 5)
    /** relevo 3D: grade 16x16 (cada peça = 4x4 px), nível -2..+2 = média da altura da peça */
    fun relief(pat: Int, variant: Int = 0): Relief.Map {
        val p = pat.coerceIn(0, COUNT - 1)
        val ck = p * 5 + variant.coerceIn(0, 4)
        reliefCache[ck]?.let { return it }
        val tx = tex(p, variant)
        val out = ByteArray(256)
        for (cy in 0 until 16) for (cx in 0 until 16) {
            var sum = 0f
            for (y in 0 until 4) for (x in 0 until 4) sum += tx.L[(cy * 4 + y) * N + cx * 4 + x]
            out[cy * 16 + cx] = Math.round(sum / 16f).coerceIn(-2, 2).toByte()
        }
        val r = Relief.Map(16, out)
        reliefCache[ck] = r
        return r
    }

    /** tiles do terreno básico refeitos com os padrões novos: pedra (4), tábua (9), tijolo (11) -> [padrão, cor, acento] */
    fun baseTile(t: Int): IntArray? = when (t) {
        4 -> intArrayOf(ROUGH0, 0xA9B0BC, -1)
        9 -> intArrayOf(PLANK, 0xE6B873, -1)
        11 -> intArrayOf(BRICK, 0xE06A58, -1)
        else -> null
    }

}
