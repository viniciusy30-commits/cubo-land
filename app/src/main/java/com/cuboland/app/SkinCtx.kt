package com.cuboland.app

/** Contexto de pintura da folha de skin 128x128 (espelha core.py: Ctx). */
class SkinCtx(val L: IntArray) {
    val girl: Boolean = L[0] == 1
    val pix = IntArray(128 * 128)
    val bu = IntArray(128)
    val bv = IntArray(128)
    val bw = IntArray(128)
    val bh = IntArray(128)
    val bd = IntArray(128)
    var nb = 12
    private val rx0 = intArrayOf(64, 0)
    private val ry0 = intArrayOf(0, 57)
    private val rw = intArrayOf(64, 64)
    private val rh = intArrayOf(128, 71)
    private val cx = intArrayOf(64, 0)
    private val cy = intArrayOf(0, 57)
    private val csh = intArrayOf(0, 0)
    val spec = ArrayList<IntArray>()

    init {
        val aw = if (girl) 3 else 4
        val u = intArrayOf(0, 32, 0, 24, 0, 16, 32, 48, 0, 16, 32, 48)
        val v = intArrayOf(0, 0, 16, 16, 30, 30, 30, 30, 43, 43, 43, 43)
        val w = intArrayOf(8, 8, 8, 8, aw, aw, aw, aw, 4, 4, 4, 4)
        val h = intArrayOf(8, 8, 10, 10, 9, 9, 9, 9, 9, 9, 9, 9)
        val d = intArrayOf(8, 8, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4)
        for (i in 0 until 12) { bu[i] = u[i]; bv[i] = v[i]; bw[i] = w[i]; bh[i] = h[i]; bd[i] = d[i] }
        piece(4, 3, 6); piece(4, 3, 6); piece(8, 3, 1)   // ids fixos 12 (pe dir), 13 (pe esq), 14 (palpebras)
    }

    fun piece(w: Int, h: Int, d: Int): Int {
        if (nb >= 120) return -1
        val pw = 2 * d + 2 * w
        val ph = d + h
        for (r in 0 until 2) {
            var x = cx[r]
            var y = cy[r]
            var sh = csh[r]
            if (x + pw > rx0[r] + rw[r]) { x = rx0[r]; y = y + sh; sh = 0 }
            if (y + ph > ry0[r] + rh[r]) continue
            cx[r] = x + pw; cy[r] = y; csh[r] = maxOf(sh, ph)
            bu[nb] = x; bv[nb] = y; bw[nb] = w; bh[nb] = h; bd[nb] = d
            nb += 1
            return nb - 1
        }
        return -1
    }

    fun place(pid: Int, parent: Int, x: Int, y: Int, z: Int, rx: Int, ry: Int, rz: Int, mode: Int, anim: Int, amp: Int, mv: Int, ph: Int): Int {
        if (spec.size >= 96) return -1
        spec.add(intArrayOf(pid, parent, x, y, z, rx, ry, rz, mode, anim, amp, mv, ph))
        return spec.size - 1
    }

    fun fx(b: Int, f: Int): Int {
        val u0 = bu[b]; val w = bw[b]; val d = bd[b]
        return when (f) {
            SkinArt.TOP -> u0 + d
            SkinArt.BOTTOM -> u0 + d + w
            SkinArt.RIGHT -> u0
            SkinArt.FRONT -> u0 + d
            SkinArt.LEFT -> u0 + d + w
            else -> u0 + 2 * d + w
        }
    }
    fun fy(b: Int, f: Int): Int = if (f == SkinArt.TOP || f == SkinArt.BOTTOM) bv[b] else bv[b] + bd[b]
    fun fw(b: Int, f: Int): Int = if (f == SkinArt.RIGHT || f == SkinArt.LEFT) bd[b] else bw[b]
    fun fh(b: Int, f: Int): Int = if (f == SkinArt.TOP || f == SkinArt.BOTTOM) bd[b] else bh[b]

    fun px(b: Int, f: Int, x: Int, y: Int, col: Int, a: Int = 255) {
        if (b < 0 || x < 0 || y < 0 || x >= fw(b, f) || y >= fh(b, f)) return
        pix[(fy(b, f) + y) * 128 + fx(b, f) + x] = (a shl 24) or (col and 0xFFFFFF)
    }
    fun at(b: Int, f: Int, x: Int, y: Int): Int {
        if (b < 0 || x < 0 || y < 0 || x >= fw(b, f) || y >= fh(b, f)) return 0
        return pix[(fy(b, f) + y) * 128 + fx(b, f) + x]
    }
    fun fill(b: Int, f: Int, x: Int, y: Int, w: Int, h: Int, col: Int, a: Int = 255) {
        for (j in 0 until h) for (i in 0 until w) px(b, f, x + i, y + j, col, a)
    }
    fun art(b: Int, f: Int, x: Int, y: Int, rws: Array<String>, legend: String, cols: IntArray, a: Int = 255) {
        for (j in rws.indices) {
            val row = rws[j]
            for (i in row.indices) {
                val k = legend.indexOf(row[i])
                if (k >= 0) px(b, f, x + i, y + j, cols[k], a)
            }
        }
    }
    fun dg(s: String, i: Int): Int = s[i] - '0'
    fun pal(i: Int): Int = SkinArt.PAL[L[i] % SkinArt.PAL.size]
}
