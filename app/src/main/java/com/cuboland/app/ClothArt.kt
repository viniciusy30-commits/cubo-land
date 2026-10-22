package com.cuboland.app

import android.graphics.Color

/**
 * Roupas em pixel-art por face (como uma skin de Minecraft): cada letra é um papel de cor e cada pixel vira
 * (por fusão gulosa) uma caixa fininha colada na superfície da roupa. Os desenhos ficam em ClothData.
 */
object ClothArt {
    private fun liteC(c: Int, k: Float) = mixC(c, 0xFFFFFF, k)
    private fun rgb(h: Int) = Color.rgb((h shr 16) and 255, (h shr 8) and 255, h and 255)

    /** papéis: m principal, l/L claros, d/D/k escuros, a/b/c destaque (claro/escuro), w/y/z branco, g/h ouro, o/p/q couro, x/X/Y aço, s/S pele, n preto, e ciano */
    fun pal(tc: Int, ac: Int, skin: Int): IntArray {
        val p = IntArray(128)
        fun s(ch: Char, c: Int) { p[ch.code] = c }
        s('m', tc); s('l', liteC(tc, 0.20f)); s('L', liteC(tc, 0.40f)); s('d', shadeC(tc, 0.80f)); s('D', shadeC(tc, 0.62f)); s('k', shadeC(tc, 0.42f))
        s('a', ac); s('b', liteC(ac, 0.35f)); s('c', shadeC(ac, 0.65f))
        s('w', rgb(0xF4F4F4)); s('y', rgb(0xD9DCE6)); s('z', rgb(0xB4B9C8))
        s('g', rgb(0xFFD060)); s('h', rgb(0xC4902A))
        s('o', rgb(0x6B4A2A)); s('p', rgb(0x8F6A3E)); s('q', rgb(0x45301A))
        s('x', rgb(0xC5CEDA)); s('X', rgb(0xEAF0F8)); s('Y', rgb(0x838FA2))
        s('s', skin); s('S', shadeC(skin, 0.84f))
        s('n', rgb(0x20222B)); s('e', rgb(0x73E7FF))
        return p
    }

    private val cache = HashMap<String, List<String>>()
    fun rows(s: String): List<String> = cache.getOrPut(s) { s.split('|') }

    private const val CLOTH = "mlLdDwyz"

    /** põe o desenho do padrão (listras, brasão...) por cima dos pixels de tecido */
    fun withPattern(src: List<String>, pat: Int): List<String> {
        if (pat <= 0 || pat >= ClothData.PAT.size) return src
        val o = rows(ClothData.PAT[pat])
        return src.mapIndexed { y, r ->
            if (y >= o.size) r else {
                val sb = StringBuilder(r)
                for (x in r.indices) { val c = o[y].getOrElse(x) { '.' }; if (c != '.' && CLOTH.indexOf(r[x]) >= 0) sb.setCharAt(x, c) }
                sb.toString()
            }
        }
    }

    /** funde pixels vizinhos de mesma cor em retângulos. emit(x0, y0, w, h, cor). Ignora '.' e a cor-base (que já é a própria caixa da roupa). */
    inline fun greedy(g: List<String>, skip: Char, emit: (Int, Int, Int, Int, Char) -> Unit) {
        val h = g.size; if (h == 0) return
        val w = g[0].length
        val used = Array(h) { BooleanArray(w) }
        for (y in 0 until h) for (x in 0 until w) {
            val c = g[y].getOrElse(x) { '.' }
            if (used[y][x]) continue
            if (c == '.' || c == ' ' || c == skip) { used[y][x] = true; continue }
            var x1 = x
            while (x1 + 1 < w && !used[y][x1 + 1] && g[y].getOrElse(x1 + 1) { '.' } == c) x1++
            var y1 = y
            while (y1 + 1 < h) {
                var ok = true
                for (xx in x..x1) if (used[y1 + 1][xx] || g[y1 + 1].getOrElse(xx) { '.' } != c) { ok = false; break }
                if (!ok) break
                y1++
            }
            for (yy in y..y1) for (xx in x..x1) used[yy][xx] = true
            emit(x, y, x1 - x + 1, y1 - y + 1, c)
        }
    }

    /** tira a coluna 2 (braço da menina, 3 px de largura) */
    fun slim(g: List<String>): List<String> = g.map { if (it.length >= 4) it.substring(0, 2) + it.substring(3) else it }
}
