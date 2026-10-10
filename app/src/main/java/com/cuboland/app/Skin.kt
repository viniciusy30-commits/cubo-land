package com.cuboland.app

/** Folha de skin pronta + descricao das caixas e das pecas extras. */
class SkinData(
    val pix: IntArray, val stamp: Int, val nb: Int,
    val bu: IntArray, val bv: IntArray, val bw: IntArray, val bh: IntArray, val bd: IntArray,
    val spec: Array<IntArray>
)

object Skin {
    private var cache: SkinData? = null
    private var key = 0
    private var stampN = 0

    @Synchronized
    fun get(): SkinData {
        val look = Look.v.copyOf()
        val k = look.contentHashCode()
        val c0 = cache
        if (c0 != null && k == key) return c0
        val c = SkinCtx(look)
        SkinArt.paint_all(c, 1)
        stampN += 1
        val d = SkinData(c.pix, stampN, c.nb, c.bu.copyOf(), c.bv.copyOf(), c.bw.copyOf(), c.bh.copyOf(), c.bd.copyOf(), c.spec.toTypedArray())
        cache = d; key = k
        return d
    }
}
