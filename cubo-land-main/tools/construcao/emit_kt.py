from gen import *
names,P,R,A=emit()
consts=' '.join(f'const val {n} = {i};' for n,i in names)
alpha_cases=''
for (n,i),a in zip(names,A):
    if a: alpha_cases+=f'        {i} -> intArrayOf({",".join(map(str,a))})\n'
kt='''package com.cuboland.app

/** Texturas 16x16 da categoria Construção (gerado por gen.py): cada padrão é um mapa de índices de paleta (0-6 = rampa da cor base, do escuro ao claro;
 *  7 = rejunte; 8-14 = rampa da cor de acento: musgo, pátina, vidro, azulejo) + um mapa de relevo (digitos 0..4 = nível -2..+2, 2 = plano).
 *  A luz vem de cima à esquerda: tijolos, tábuas e telhas têm quina clara em cima e sombra embaixo. A rampa esfria nas sombras e esquenta nas luzes.
 *  Os vidros têm alpha por índice: moldura quase opaca, miolo bem transparente e reflexos diagonais. */
object BuildArt {
    %s
    val P: Array<Array<String>> = arrayOf(
%s
    )
    val R: Array<Array<String>> = arrayOf(
%s
    )
    private const val SH = 0x241838
    private const val LT = 0xFFF4D8
    private fun mix(a: Int, b: Int, t: Float): Int {
        val r = (((a shr 16) and 255) * (1f - t) + ((b shr 16) and 255) * t).toInt()
        val g = (((a shr 8) and 255) * (1f - t) + ((b shr 8) and 255) * t).toInt()
        val bl = ((a and 255) * (1f - t) + (b and 255) * t).toInt()
        return (r shl 16) or (g shl 8) or bl
    }
    private fun ramp(c: Int, out: IntArray, o: Int) {
        out[o] = mix(c, SH, 0.62f); out[o + 1] = mix(c, SH, 0.42f); out[o + 2] = mix(c, SH, 0.20f); out[o + 3] = c
        out[o + 4] = mix(c, LT, 0.14f); out[o + 5] = mix(c, LT, 0.30f); out[o + 6] = mix(c, LT, 0.52f)
    }
    private fun defAccent(pat: Int): Int = when (pat) {
        SBRICK_MOSS, COBBLE_MOSS -> 0x5E8F3C
        SPECK -> 0xE0A090
        OBSIDIAN -> 0x7A4AD0
        METAL_P1, METAL_P2 -> 0x5FB08A
        WINDOW, JAN_FRAMELESS, JAN_SLIDE, JAN_COLONIAL, JAN_LOUVER, JAN_PORTHOLE, JAN_BARS -> 0xBFE6F5
        VITRAL -> 0xE0A030
        AZ_DECOR -> 0xF6F6F0
        else -> 0x5E8F3C
    }
    private fun alphaTab(pat: Int): IntArray? = when (pat) {
%s        else -> null
    }
    private val cache = HashMap<Long, IntArray>()
    /** paleta ARGB de 16 entradas: base = cor principal, acc = acento (-1 = padrão do tipo), scale = %% de opacidade do miolo do vidro (0 = 100) */
    fun palette(pat: Int, base: Int, acc: Int, scale: Int): IntArray {
        val key = (pat.toLong() shl 56) xor (base.toLong() and 0xFFFFFF shl 32) xor ((if (acc < 0) 0x1000000L else acc.toLong() and 0xFFFFFF) shl 8) xor scale.toLong()
        return cache.getOrPut(key) {
            val p = IntArray(15)
            ramp(base and 0xFFFFFF, p, 0)
            p[7] = if (pat == VITRAL) 0x2A2630 else if (pat == BRICK) mix(0xD2C9B8, base and 0xFFFFFF, 0.12f) else if (pat == AZ_PLAIN) mix(base and 0xFFFFFF, SH, 0.3f) else mix(base and 0xFFFFFF, SH, 0.5f)
            ramp(if (acc < 0) defAccent(pat) else acc and 0xFFFFFF, p, 8)
            val al = alphaTab(pat)
            val sc = if (scale <= 0) 100 else scale
            IntArray(16) { i ->
                val c = if (i < 15) p[i] else p[14]
                val a0 = al?.get(i) ?: 255
                val a = if (a0 >= 230) a0 else minOf(255, a0 * sc / 100)
                (a shl 24) or (c and 0xFFFFFF)
            }
        }
    }
    /** pixel ARGB (px,py) do padrão */
    fun pixel(pat: Int, base: Int, acc: Int, scale: Int, px: Int, py: Int): Int {
        val p = P[pat.coerceIn(0, P.size - 1)]
        return palette(pat, base, acc, scale)[Character.digit(p[py][px], 16)]
    }
    fun relief(pat: Int): Relief.Map {
        val rows = R[pat.coerceIn(0, R.size - 1)]
        val out = ByteArray(256)
        for (y in 0 until 16) for (x in 0 until 16) out[y * 16 + x] = (rows[y][x] - '0' - 2).coerceIn(-2, 2).toByte()
        return Relief.Map(16, out)
    }
    /** tiles do terreno básico refeitos com os padrões novos: pedra (4), tábua (9), tijolo (11) -> [padrão, cor, acento] */
    fun baseTile(t: Int): IntArray? = when (t) {
        4 -> intArrayOf(ROUGH0, 0x9AA0A8, -1)
        9 -> intArrayOf(PLANK, 0xD9A864, -1)
        11 -> intArrayOf(BRICK, 0xC4553F, -1)
        else -> null
    }
}
''' % (consts, ',\n'.join('        arrayOf('+p+')' for p in P), ',\n'.join('        arrayOf('+r+')' for r in R), alpha_cases)
open('../../app/src/main/java/com/cuboland/app/BuildArt.kt','w').write(kt)
print(len(kt))
