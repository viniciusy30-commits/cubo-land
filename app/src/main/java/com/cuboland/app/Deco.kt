package com.cuboland.app

import android.graphics.BitmapFactory
import org.json.JSONArray
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Mobília e decoração importadas do addon monoDeco (gerado por tools/deco_import.py).
 * Dados em app/src/main/resources/deco: items.json, geo.bin (quads já assados em coordenadas de bloco), tex/N.png (64x64).
 * Cada item é um bloco de formato próprio (kind 7, sub 0); Deco.idx[id] = índice do item.
 */
object Deco {
    class Item(val name: String, val cat: Int, val geo: Int, val tex: IntArray, val box: FloatArray?, val light: Int, val sit: Boolean, val group: String = "") {
        var tile: IntArray = IntArray(0)
    }
    class Geo(val nQuad: Int, val slot: IntArray, val shade: FloatArray, val pos: FloatArray, val uv: FloatArray)

    @JvmField val idx = IntArray(16384) { -1 }

    private fun res(p: String) = Deco::class.java.getResourceAsStream(p)

    val items: List<Item> by lazy {
        val out = ArrayList<Item>()
        try {
            val txt = res("/deco/items.json")!!.readBytes().toString(Charsets.UTF_8)
            val a = JSONArray(txt)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val t = o.getJSONArray("t"); val tex = IntArray(t.length()) { t.getInt(it) }
                val b = o.optJSONArray("b")
                val box = if (b == null) null else FloatArray(6) { b.getDouble(it).toFloat() }
                out.add(Item(o.getString("n"), o.getInt("c"), o.getInt("g"), tex, box, o.optInt("l", 0), o.optInt("sit", 0) == 1, o.optString("gp", "")))
            }
        } catch (_: Throwable) { }
        out
    }

    val geos: List<Geo> by lazy {
        val out = ArrayList<Geo>()
        try {
            val bb = ByteBuffer.wrap(res("/deco/geo.bin")!!.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
            val n = bb.int
            for (g in 0 until n) {
                bb.int  // nº de materiais
                val nq = bb.int
                val slot = IntArray(nq); val shade = FloatArray(nq); val pos = FloatArray(nq * 12); val uv = FloatArray(nq * 8)
                for (q in 0 until nq) {
                    slot[q] = bb.get().toInt() and 255; shade[q] = (bb.get().toInt() and 255) / 255f
                    for (v in 0 until 4) {
                        pos[q * 12 + v * 3] = bb.short / 2048f; pos[q * 12 + v * 3 + 1] = bb.short / 2048f; pos[q * 12 + v * 3 + 2] = bb.short / 2048f
                        uv[q * 8 + v * 2] = (bb.short.toInt() and 0xFFFF) / 65535f; uv[q * 8 + v * 2 + 1] = (bb.short.toInt() and 0xFFFF) / 65535f
                    }
                }
                out.add(Geo(nq, slot, shade, pos, uv))
            }
        } catch (_: Throwable) { }
        out
    }

    private val grp: HashMap<String, String> by lazy { val m = HashMap<String, String>(); for (d in items) m[d.name] = d.group; m }
    /** seção da galeria do item pelo nome */
    fun groupOf(name: String): String = grp[name] ?: ""
    /** títulos das seções de uma categoria (4 = decoração, 5 = mobília), na ordem do addon */
    fun groups(cat: Int): List<String> = items.filter { it.cat == cat && it.group.isNotEmpty() }.map { it.group }.distinct()

    fun isDeco(id: Int) = id in 0..16383 && idx[id] >= 0
    fun of(id: Int): Item? { val i = if (id in 0..16383) idx[id] else -1; return if (i >= 0) items[i] else null }
    fun sit(id: Int): Boolean = of(id)?.sit == true

    // ---- pixels das texturas (Atlas.build chama por pixel) ----
    private val texCache = HashMap<Int, IntArray>()
    fun pixel(tex: Int, x: Int, y: Int): Int {
        var a = texCache[tex]
        if (a == null) {
            a = IntArray(64 * 64)
            try {
                val bm = BitmapFactory.decodeStream(res("/deco/tex/$tex.png"))
                bm.getPixels(a, 0, 64, 0, 0, 64, 64); bm.recycle()
            } catch (_: Throwable) { java.util.Arrays.fill(a, 0xFFFF00FF.toInt()) }
            texCache[tex] = a
        }
        return a[(y and 63) * 64 + (x and 63)]
    }
    fun freeCache() { texCache.clear() }

    // ---- malha ----
    fun emit(o: MeshBuf, w: World, x: Int, y: Int, z: Int, id: Int, vr: Float) {
        val it = of(id) ?: return
        val g = geos.getOrNull(it.geo) ?: return
        val dir = Furniture.faceOf(w, x, y, z, 0)
        for (q in 0 until g.nQuad) {
            val s = g.slot[q]
            val tile = if (s < it.tile.size) it.tile[s] else -1
            if (tile < 0) continue
            val sh = (0.55f + 0.45f * g.shade[q]) * vr
            for (v in 0 until 4) {
                val px = g.pos[q * 12 + v * 3]; val py = g.pos[q * 12 + v * 3 + 1]; val pz = g.pos[q * 12 + v * 3 + 2]
                val rx: Float; val rz: Float
                when (dir) { 0 -> { rx = px; rz = pz }; 1 -> { rx = 1f - px; rz = 1f - pz }; 2 -> { rx = pz; rz = 1f - px }; else -> { rx = 1f - pz; rz = px } }
                o.vert(x + rx, y + py, z + rz, sh, sh, sh,
                    Atlas.u(tile, g.uv[q * 8 + v * 2].coerceIn(0f, 1f)), Atlas.v(tile, g.uv[q * 8 + v * 2 + 1].coerceIn(0f, 1f)))
            }
            o.tri(0, 1, 2); o.tri(0, 2, 3); o.tri(0, 2, 1); o.tri(0, 3, 2); o.vc += 4
        }
    }

    /** caixa de colisão/seleção girada pra direção do bloco */
    fun box(w: World, id: Int, x: Int, y: Int, z: Int): FloatArray {
        val b = of(id)?.box ?: return floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f)
        val dir = if (y >= 0) Furniture.faceOf(w, x, y, z, 0) else 0
        return when (dir) {
            0 -> floatArrayOf(b[0], b[1], b[2], b[3], b[4], b[5])
            1 -> floatArrayOf(1f - b[3], b[1], 1f - b[5], 1f - b[0], b[4], 1f - b[2])
            2 -> floatArrayOf(b[2], b[1], 1f - b[3], b[5], b[4], 1f - b[0])
            else -> floatArrayOf(1f - b[5], b[1], b[0], 1f - b[2], b[4], b[3])
        }
    }
}
