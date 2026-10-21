package com.cuboland.app

import android.graphics.Bitmap
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
    class Item(val name: String, val cat: Int, val geo: Int, val tex: IntArray, val box: FloatArray?, val light: Int, val sit: Boolean, val group: String, val fp: IntArray?, val kd: Int, val ob: FloatArray?, val sc: FloatArray?, val fd: String, val key: String) {
        var tile: IntArray = IntArray(0)
        var blockId: Int = 0
        var nc: Boolean = false   // sem colisão no addon (cortina, quadro, bandeira): atravessa, mas a caixa ainda serve pra mirar/quebrar
    }
    class Geo(val nQuad: Int, val slot: IntArray, val shade: FloatArray, val vis: IntArray, val pos: FloatArray, val uv: FloatArray)

    @JvmField val idx = IntArray(16384)
    init { java.util.Arrays.fill(idx, -1) }

    private var itemsV: ArrayList<Item>? = null
    private var geosV: ArrayList<Geo>? = null
    private var grpV: HashMap<String, String>? = null
    private val texCache = HashMap<Int, IntArray>()

    private fun res(p: String): java.io.InputStream? = Deco::class.java.getResourceAsStream(p)

    private fun fpOf(a: JSONArray?): IntArray? {
        if (a == null) return null
        val r = IntArray(6)
        for (k in 0 until 6) r[k] = a.getInt(k)
        return r
    }

    private fun floatsOf(a: JSONArray?): FloatArray? {
        if (a == null) return null
        val r = FloatArray(a.length())
        for (k in 0 until a.length()) r[k] = a.getDouble(k).toFloat()
        return r
    }

    /** TV / computador com tela que abre o navegador (o conversor acha a tela pelo material "light") */
    fun isTv(id: Int): Boolean { val d = of(id); if (d == null) return false; return d.sc != null }
    /** tela no mundo: [cx, cy, cz, nx, nz, rx, rz, largura, altura] (mesmo formato de Game.tvGeom) */
    fun tvGeom(w: World, x: Int, y: Int, z: Int): FloatArray {
        val d = of(w.get(x, y, z))
        val s = if (d == null) null else d.sc
        if (s == null) return floatArrayOf(x + 0.5f, y + 0.5f, z + 0.5f, 0f, 1f, 1f, 0f, 0.8f, 0.5f)
        val dir = Furniture.faceOf(w, x, y, z, 0)
        var px = s[0]; var pz = s[2]; var nx = s[3]; var nz = s[4]
        if (dir == 1) { px = 1f - s[0]; pz = 1f - s[2]; nx = -s[3]; nz = -s[4] }
        else if (dir == 2) { px = s[2]; pz = 1f - s[0]; nx = s[4]; nz = -s[3] }
        else if (dir == 3) { px = 1f - s[2]; pz = s[0]; nx = -s[4]; nz = s[3] }
        return floatArrayOf(x + px, y + s[1], z + pz, nx, nz, nz, -nx, s[7], s[8])
    }

    private fun boxOf6(a: JSONArray?): FloatArray? {
        if (a == null) return null
        val r = FloatArray(6)
        for (k in 0 until 6) r[k] = a.getDouble(k).toFloat()
        return r
    }

    /** 1 = abre/fecha, 2 = liga/desliga, 0 = nada (usado por Furniture.fKind) */
    fun kindOf(id: Int): Int { val d = of(id); if (d == null) return 0; return d.kd }
    private fun toggled(w: World, x: Int, y: Int, z: Int, kd: Int): Boolean {
        if (kd == 0) return false
        val st = w.fStateOf(x, y, z)
        return if (kd == 1) (st and Furniture.ST_OPEN) != 0 else (st and Furniture.ST_OFF) != 0
    }

    private fun rotBox(b: FloatArray, dir: Int): FloatArray {
        if (dir == 1) return floatArrayOf(1f - b[3], b[1], 1f - b[5], 1f - b[0], b[4], 1f - b[2])
        if (dir == 2) return floatArrayOf(b[2], b[1], 1f - b[3], b[5], b[4], 1f - b[0])
        if (dir == 3) return floatArrayOf(1f - b[5], b[1], b[0], 1f - b[2], b[4], b[3])
        return floatArrayOf(b[0], b[1], b[2], b[3], b[4], b[5])
    }

    /** parte extra de um móvel de vários blocos: âncora (ax,ay,az) + caixa local da célula pedida */
    class Extra(val ax: Int, val ay: Int, val az: Int, val box: FloatArray)

    fun extraFind(w: World, x: Int, y: Int, z: Int): Extra? {
        for (ay in y - 2..y) for (ax in x - 2..x + 2) for (az in z - 2..z + 2) {
            if (ax == x && ay == y && az == z) continue
            val d = of(w.get(ax, ay, az))
            if (d == null) continue
            val fp = d.fp
            if (fp == null) continue
            val dir = Furniture.faceOf(w, ax, ay, az, 0)
            for (i in fp[0]..fp[1]) for (j in fp[4]..fp[5]) {
                var dx = i; var dz = j
                if (dir == 1) { dx = -i; dz = -j } else if (dir == 2) { dx = j; dz = -i } else if (dir == 3) { dx = -j; dz = i }
                if (ax + dx != x || az + dz != z) continue
                for (k in fp[2]..fp[3]) if (ay + k == y) {
                    val base = d.box
                    val b = if (base == null) floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f) else floatArrayOf(base[0], base[1], base[2], base[3], if (k < fp[3]) 1f else base[4], base[5])
                    return Extra(ax, ay, az, rotBox(b, dir))
                }
            }
        }
        return null
    }

    /** colisão das células "extras" de móveis de vários blocos (cama, armário alto...): caixa local da célula (x,y,z), ou null */
    fun extraBox(w: World, x: Int, y: Int, z: Int): FloatArray? {
        val e = extraFind(w, x, y, z)
        if (e == null) return null
        return e.box
    }

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
            val itn = Item(o.getString("n"), o.getInt("c"), o.getInt("g"), tex, box, o.optInt("l", 0), o.optInt("sit", 0) == 1, o.optString("gp", ""), fpOf(o.optJSONArray("fp")), o.optInt("kd", 0), boxOf6(o.optJSONArray("ob")), floatsOf(o.optJSONArray("sc")), o.optString("f", ""), o.optString("k", "").replace("_drz", ""))
            itn.nc = o.optInt("nc", 0) == 1
            out.add(itn)
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
            val slot = IntArray(nq); val shade = FloatArray(nq); val vis = IntArray(nq); val pos = FloatArray(nq * 12); val uv = FloatArray(nq * 8)
            for (q in 0 until nq) {
                slot[q] = bb.get().toInt() and 255
                shade[q] = (bb.get().toInt() and 255) / 255f
                vis[q] = bb.get().toInt() and 255
                for (v in 0 until 4) {
                    pos[q * 12 + v * 3] = bb.getShort().toInt() / 2048f
                    pos[q * 12 + v * 3 + 1] = bb.getShort().toInt() / 2048f
                    pos[q * 12 + v * 3 + 2] = bb.getShort().toInt() / 2048f
                    uv[q * 8 + v * 2] = (bb.getShort().toInt() and 0xFFFF) / 65535f
                    uv[q * 8 + v * 2 + 1] = (bb.getShort().toInt() and 0xFFFF) / 65535f
                }
            }
            out.add(Geo(nq, slot, shade, vis, pos, uv))
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
    fun noCol(id: Int): Boolean { val d = of(id); if (d == null) return false; return d.nc }
    fun sit(id: Int): Boolean { val d = of(id); if (d == null) return false; return d.sit }

    // ---- pixels das texturas (Atlas.build chama por pixel) ----
    private fun decode(tex: Int): IntArray {
        val a = IntArray(64 * 64)
        try {
            val bm = res("/deco/tex/$tex.png")?.use { BitmapFactory.decodeStream(it) }
            if (bm == null) {
                java.util.Arrays.fill(a, -65281)
            } else {
                // O atlas espera 64x64; normaliza dimensões inesperadas sem suavizar o pixel art.
                val sized = if (bm.width == 64 && bm.height == 64) bm
                    else Bitmap.createScaledBitmap(bm, 64, 64, false)
                try {
                    sized.getPixels(a, 0, 64, 0, 0, 64, 64)
                } finally {
                    if (sized !== bm) sized.recycle()
                    bm.recycle()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("CuboLand-TextureAudit", "Falha ao carregar textura de decoração $tex", e)
            java.util.Arrays.fill(a, -65281)
        }
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
        val tg = if (toggled(w, x, y, z, item.kd)) 2 else 1
        for (q in 0 until g.nQuad) {
            if ((g.vis[q] and tg) == 0) continue
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


    /** malha do item sozinho (sem mundo), na direção padrão, só para gerar o ícone; devolve a caixa [minx,miny,minz,maxx,maxy,maxz] ou null se vazio */
    fun emitIcon(o: MeshBuf, id: Int, vr: Float): FloatArray? {
        val item = of(id)
        if (item == null) return null
        if (item.geo < 0 || item.geo >= geos.size) return null
        val g = geos[item.geo]
        val bb = floatArrayOf(1e9f, 1e9f, 1e9f, -1e9f, -1e9f, -1e9f)
        var n = 0
        for (q in 0 until g.nQuad) {
            if ((g.vis[q] and 1) == 0) continue
            val sl = g.slot[q]
            val tile = if (sl < item.tile.size) item.tile[sl] else -1
            if (tile < 0) continue
            val sh = (0.55f + 0.45f * g.shade[q]) * vr
            for (v in 0 until 4) {
                val px = g.pos[q * 12 + v * 3]; val py = g.pos[q * 12 + v * 3 + 1]; val pz = g.pos[q * 12 + v * 3 + 2]
                val u = Atlas.u(tile, g.uv[q * 8 + v * 2].coerceIn(0f, 1f))
                val vv = Atlas.v(tile, g.uv[q * 8 + v * 2 + 1].coerceIn(0f, 1f))
                o.vert(px, py, pz, sh, sh, sh, u, vv)
                if (px < bb[0]) bb[0] = px; if (py < bb[1]) bb[1] = py; if (pz < bb[2]) bb[2] = pz
                if (px > bb[3]) bb[3] = px; if (py > bb[4]) bb[4] = py; if (pz > bb[5]) bb[5] = pz
            }
            o.tri(0, 1, 2); o.tri(0, 2, 3); o.tri(0, 2, 1); o.tri(0, 3, 2); o.vc += 4
            n++
        }
        return if (n == 0) null else bb
    }

    /**
     * cabe aqui? confere o móvel inteiro (âncora + blocos extras do footprint) girado pra dir:
     * tudo dentro do mundo, só ar, sem o jogador dentro e sem pegar o espaço de outro móvel grande.
     * (a própria âncora (ax,ay,az) é ignorada ao procurar partes extras: serve também pra girar um móvel já colocado)
     */
    fun fits(w: World, id: Int, ax: Int, ay: Int, az: Int, dir: Int, plx: Float, ply: Float, plz: Float, phw: Float, ph: Float): Boolean {
        val d = of(id)
        if (d == null) return true
        val fp = d.fp
        val x0 = if (fp == null) 0 else fp[0]; val x1 = if (fp == null) 0 else fp[1]
        val y0 = if (fp == null) 0 else fp[2]; val y1 = if (fp == null) 0 else fp[3]
        val z0 = if (fp == null) 0 else fp[4]; val z1 = if (fp == null) 0 else fp[5]
        for (i in x0..x1) for (j in z0..z1) for (k in y0..y1) {
            var dx = i; var dz = j
            if (dir == 1) { dx = -i; dz = -j } else if (dir == 2) { dx = j; dz = -i } else if (dir == 3) { dx = -j; dz = i }
            val tx = ax + dx; val ty = ay + k; val tz = az + dz
            if (tx < 0 || tz < 0 || tx >= World.SX || tz >= World.SZ || ty < 1 || ty >= World.SY - 1) return false
            if (i != 0 || j != 0 || k != 0) {
                if (w.get(tx, ty, tz) != B.AIR) return false
                val pb = tx + 1 > plx - phw && tx < plx + phw && tz + 1 > plz - phw && tz < plz + phw && ty + 1 > ply && ty < ply + ph
                if (pb) return false
            }
            val e = extraFind(w, tx, ty, tz)
            if (e != null && !(e.ax == ax && e.ay == ay && e.az == az)) return false
        }
        return true
    }

    /** caixa de colisão/seleção girada pra direção do bloco */
    fun box(w: World, id: Int, x: Int, y: Int, z: Int): FloatArray {
        val d = of(id)
        var b = if (d == null) null else d.box
        if (d != null && d.ob != null && y >= 0 && toggled(w, x, y, z, d.kd)) b = d.ob
        if (b == null) return floatArrayOf(0f, 0f, 0f, 1f, 1f, 1f)
        val dir = if (y >= 0) Furniture.faceOf(w, x, y, z, 0) else 0
        if (dir == 1) return floatArrayOf(1f - b[3], b[1], 1f - b[5], 1f - b[0], b[4], 1f - b[2])
        if (dir == 2) return floatArrayOf(b[2], b[1], 1f - b[3], b[5], b[4], 1f - b[0])
        if (dir == 3) return floatArrayOf(1f - b[5], b[1], b[0], 1f - b[2], b[4], b[3])
        return floatArrayOf(b[0], b[1], b[2], b[3], b[4], b[5])
    }
}
