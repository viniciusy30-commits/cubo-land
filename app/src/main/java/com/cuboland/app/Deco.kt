package com.cuboland.app

import android.graphics.BitmapFactory
import org.json.JSONArray
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Mobília e decoração importadas do addon monoDeco (gerado por tools/deco_import.py).
 * Dados em app/src/main/resources/deco: items.json, geo.bin (quads já assados em coordenadas de bloco), tex/N.png (64x64).
 * Cada item é um bloco de formato próprio (kind 7, sub 0); Deco.idx[id] = índice do item.
 * (Sem try/catch nem lazy de propósito: o D8 quebrava com o bytecode deles.)
 */
object Deco {
    class Item(val name: String, val cat: Int, val geo: Int, val tex: IntArray, val box: FloatArray?, val light: Int, val sit: Boolean, val group: String) {
        var tile: IntArray = IntArray(0)
    }
    class Geo(val nQuad: Int, val slot: IntArray, val shade: FloatArray, val pos: FloatArray, val uv: FloatArray)

    @JvmField val idx = IntArray(16384)
    init { java.util.Arrays.fill(idx, -1) }

    private var itemsV: ArrayList<Item>? = null
    private var geosV: ArrayList<Geo>? = null
    private var grpV: HashMap<String, String>? = null
    private val texCache = HashMap<Int, IntArray>()

    private fun res(p: String): java.io.InputStream? = Deco::class.java.getResourceAsStream(p)

    private fun loadItems(): ArrayList<Item> {
        val out = ArrayList<Item>()
        val st = res("/deco/items.json") ?: return out
        val a = JSONArray(String(st.readBytes(), Charsets.UTF_8))
        for (i in 0 until a.length()) {
            val o = a.getJSONObject(i)
            val t = o.getJSONArray("t")
            val tex = IntArray(t.length())
            for (k in 0 until t.length()) tex[k] = t.getInt(k)
            val b = o.optJSONArray("b")
            var box: FloatArray? = null
            if (b != null) { box = FloatArray(6); for (k in 0 until 6) box[k] = b.getDouble(k).toFloat() }
            out.add(Item(o.getString("n"), o.getInt("c"), o.getInt("g"), tex, box, o.optInt("l", 0), o.optInt("sit", 0) == 1, o.optString("gp", "")))
        }
        return out
    }

    private fun loadGeos(): ArrayList<Geo> {
        val out = ArrayList<Geo>()
        val st = res("/deco/geo.bin") ?: return out
        val bb = ByteBuffer.wrap(st.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        val n = bb.getInt()
        for (g in 0 until n) {
            bb.getInt()  // nº de materiais
            val nq = bb.getInt()
            val slot = IntArray(nq); val shade = FloatArray(nq); val pos = FloatArray(nq * 12); val uv = FloatArray(nq * 8)
            for (q in 0 until nq) {
                slot[q] = bb.get().toInt() and 255
                shade[q] = (bb.get().toInt() and 255) / 255f
                for (v in 0 until 4) {
                    pos[q * 12 + v * 3] = bb.getShort().toInt() / 2048f
                    pos[q * 12 + v * 3 + 1] = bb.getShort().toInt() / 2048f
                    pos[q * 12 + v * 3 + 2] = bb.getShort().toInt() / 2048f
                    uv[q * 8 + v * 2] = (bb.getShort().toInt() and 0xFFFF) / 65535f
                    uv[q * 8 + v * 2 + 1] = (bb.getShort().toInt() and 0xFFFF) / 65535f
                }
            }
            out.add(Geo(nq, slot, shade, pos, uv))
        }
        return out
    }

    val items: List<Item>
        get() { var v = itemsV; if (v == null) { v = loadItems(); itemsV = v }; return v }
    val geos: List<Geo>
        get() { var v = geosV; if (v == null) { v = loadGeos(); geosV = v }; return v }

    /** seção da galeria do item pelo nome */
    fun groupOf(name: String): String {
        var m = grpV
        if (m == null) { m = HashMap(); for (d in items) m[d.name] = d.group; grpV = m }
        val g = m[name]
        return if (g == null) "" else g
    }
    /** títulos das seções de uma categoria (4 = decoração, 5 = mobília), na ordem do addon */
    fun groups(cat: Int): List<String> {
        val out = ArrayList<String>()
        for (d in items) if (d.cat == cat && d.group.isNotEmpty() && !out.contains(d.group)) out.add(d.group)
        return out
    }

    fun isDeco(id: Int): Boolean { if (id < 0 || id > 16383) return false; return idx[id] >= 0 }
    fun of(id: Int): Item? { if (id < 0 || id > 16383) return null; val i = idx[id]; if (i < 0) return null; return items[i] }
    fun sit(id: Int): Boolean { val d = of(id); if (d == null) return false; return d.sit }

    // ---- pixels das texturas (Atlas.build chama por pixel) ----
    private fun decode(tex: Int): IntArray {
        val a = IntArray(64 * 64)
        val st = res("/deco/tex/$tex.png")
        val bm = if (st == null) null else BitmapFactory.decodeStream(st)
        if (bm == null) { java.util.Arrays.fill(a, -65281) } else { bm.getPixels(a, 0, 64, 0, 0, 64, 64); bm.recycle() }
        return a
    }
    fun pixel(tex: Int, x: Int, y: Int): Int {
        var a = texCache[tex]
        if (a == null) { a = decode(tex); texCache[tex] = a }
        return a[(y and 63) * 64 + (x and 63)]
    }
    fun freeCache() { texCache.clear() }

    // ---- malha ----
    fun emit(o: MeshBuf, w: World, x: Int, y: Int, z: Int, id: Int, vr: Float) {
        val item = of(id)
        if (item == null) return
        if (item.geo < 0 || item.geo >= geos.size) return
        val g = geos[item.geo]
        val dir = Furniture.faceOf(w, x, y, z, 0)
        for (q in 0 until g.nQuad) {
            val s = g.slot[q]
            val tile = if (s < item.tile.size) item.tile[s] else -1
            if (tile < 0) continue
            val sh = (0.55f + 0.45f * g.shade[q]) * vr
            for (v in 0 until 4) {
                val px = g.pos[q * 12 + v * 3]; val py = g.pos[q * 12 + v * 3 + 1]; val pz = g.pos[q * 12 + v * 3 + 2]
                var rx = px; var rz = pz
                if (dir == 1) { rx = 1f - px; rz = 1f - pz } else if (dir == 2) { rx = pz; rz = 1f - px } else if (dir == 3) { rx = 1f - pz; rz = px }
                val u = Atlas.u(tile, g.uv[q * 8 + v * 2].coerceIn(0f, 1f))
                val vv = Atlas.v(tile, g.uv[q * 8 + v * 2 + 1].coerceIn(0f, 1f))
                o.vert(x + rx, y + py, z + rz, sh, sh, sh, u, vv)
            }
            o.tri(0, 1, 2); o.tri(0, 2, 3); o.tri(0, 2, 1); o.tri(0, 3, 2); o.vc += 4
        }
    }

    /** caixa de colisão/seleção girada pra direção do bloco */
    fun box(w: World, id: Int, x: Int, y: Int, z: Int): FloatArray {
        val d = of(id)
        val b = if (d == null) null else d.box
        if (b == null) return floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f)
        val dir = if (y >= 0) Furniture.faceOf(w, x, y, z, 0) else 0
        if (dir == 1) return floatArrayOf(1f - b[3], b[1], 1f - b[5], 1f - b[0], b[4], 1f - b[2])
        if (dir == 2) return floatArrayOf(b[2], b[1], 1f - b[3], b[5], b[4], 1f - b[0])
        if (dir == 3) return floatArrayOf(1f - b[5], b[1], b[0], 1f - b[2], b[4], b[3])
        return floatArrayOf(b[0], b[1], b[2], b[3], b[4], b[5])
    }
}
