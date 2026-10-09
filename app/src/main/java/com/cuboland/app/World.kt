package com.cuboland.app

import java.io.File
import java.util.Random
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.floor
import kotlin.math.sqrt

object B {
    const val AIR = 0; const val GRASS = 1; const val DIRT = 2; const val STONE = 3; const val SAND = 4
    const val WOOD = 5; const val LEAVES = 6; const val PLANK = 7; const val WATER = 8; const val BRICK = 9
    const val PINK = 10; const val BLUE = 11; const val YELLOW = 12; const val LANTERN = 13
    val top = IntArray(14); val side = IntArray(14); val bot = IntArray(14)
    val names = arrayOf("", "Grama", "Terra", "Pedra", "Areia", "Madeira", "Folhas", "Tábua", "Água",
        "Tijolo", "Rosa", "Azul", "Amarelo", "Lanterna")
    val hotbar = intArrayOf(GRASS, STONE, PLANK, BRICK, PINK, BLUE, YELLOW, LANTERN)
    private fun s(id: Int, t: Int, sd: Int, b: Int) { top[id] = t; side[id] = sd; bot[id] = b }
    init {
        s(GRASS, 0x66C84C, 0x8C6039, 0x8C6039); s(DIRT, 0x8C6039, 0x8C6039, 0x8C6039)
        s(STONE, 0x9AA0A8, 0x9AA0A8, 0x9AA0A8); s(SAND, 0xF0DE9A, 0xF0DE9A, 0xF0DE9A)
        s(WOOD, 0xB98A55, 0x7A5230, 0xB98A55); s(LEAVES, 0x3FA23B, 0x3FA23B, 0x3FA23B)
        s(PLANK, 0xD9A864, 0xD9A864, 0xD9A864); s(WATER, 0x3A8DDE, 0x3A8DDE, 0x3A8DDE)
        s(BRICK, 0xC4553F, 0xC4553F, 0xC4553F); s(PINK, 0xFF8FC8, 0xFF8FC8, 0xFF8FC8)
        s(BLUE, 0x4F9BFF, 0x4F9BFF, 0x4F9BFF); s(YELLOW, 0xFFD84A, 0xFFD84A, 0xFFD84A)
        s(LANTERN, 0xFFF2A8, 0xFFF2A8, 0xFFF2A8)
    }
    fun solid(id: Int) = id != AIR && id != WATER
    val tTop = IntArray(14); val tSide = IntArray(14); val tBot = IntArray(14)
    private fun t(id: Int, a: Int, b: Int, c: Int) { tTop[id] = a; tSide[id] = b; tBot[id] = c }
    init {
        t(GRASS, 1, 2, 3); t(DIRT, 3, 3, 3); t(STONE, 4, 4, 4); t(SAND, 5, 5, 5); t(WOOD, 7, 6, 7); t(LEAVES, 8, 8, 8)
        t(PLANK, 9, 9, 9); t(WATER, 10, 10, 10); t(BRICK, 11, 11, 11); t(PINK, 12, 12, 12); t(BLUE, 13, 13, 13)
        t(YELLOW, 14, 14, 14); t(LANTERN, 15, 15, 15)
    }
}

class MeshBuf {
    var v = FloatArray(16384); var vn = 0
    var ix = ShortArray(4096); var inn = 0; var vc = 0
    fun clear() { vn = 0; inn = 0; vc = 0 }
    fun vert(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, u: Float, w: Float) {
        if (vn + 8 > v.size) v = v.copyOf(v.size * 2)
        v[vn++] = x; v[vn++] = y; v[vn++] = z; v[vn++] = r; v[vn++] = g; v[vn++] = b; v[vn++] = u; v[vn++] = w
    }
    fun tri(a: Int, b: Int, c: Int) {
        if (inn + 3 > ix.size) ix = ix.copyOf(ix.size * 2)
        ix[inn++] = (vc + a).toShort(); ix[inn++] = (vc + b).toShort(); ix[inn++] = (vc + c).toShort()
    }
}

class World {
    companion object {
        const val SX = 96; const val SY = 32; const val SZ = 96; const val CH = 16
        const val CX = SX / CH; const val CZ = SZ / CH; const val WATER_Y = 9
        val CU = intArrayOf(0, 1); val CV = intArrayOf(0, 1)
        val QU = intArrayOf(0, 1, 1, 0); val QV = intArrayOf(0, 0, 1, 1)
        val AOB = floatArrayOf(0.5f, 0.68f, 0.84f, 1f)
    }

    val blocks = ByteArray(SX * SY * SZ)
    val dirty = BooleanArray(CX * CZ) { true }

    fun get(x: Int, y: Int, z: Int): Int {
        if (y >= SY) return 0
        if (y < 0 || x < 0 || x >= SX || z < 0 || z >= SZ) return B.STONE
        return blocks[(y * SZ + z) * SX + x].toInt()
    }

    fun solid(x: Int, y: Int, z: Int) = B.solid(get(x, y, z))

    fun set(x: Int, y: Int, z: Int, id: Int) {
        if (x < 0 || x >= SX || z < 0 || z >= SZ || y < 0 || y >= SY) return
        blocks[(y * SZ + z) * SX + x] = id.toByte()
        for (dx in -1..1) for (dz in -1..1) {
            val cx = (x + dx).coerceIn(0, SX - 1) / CH; val cz = (z + dz).coerceIn(0, SZ - 1) / CH
            dirty[cz * CX + cx] = true
        }
    }

    fun surfaceY(x: Int, z: Int): Int {
        for (y in SY - 1 downTo 0) if (solid(x, y, z)) return y + 1
        return 0
    }

    private fun hash(x: Int, z: Int, s: Int): Float {
        var h = x * 374761393 + z * 668265263 + s * 1442695041.toInt()
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xffff) / 65535f
    }

    private fun vnoise(x: Float, z: Float, s: Int): Float {
        val xi = floor(x).toInt(); val zi = floor(z).toInt()
        val fx = x - xi; val fz = z - zi
        val sx = fx * fx * (3 - 2 * fx); val sz = fz * fz * (3 - 2 * fz)
        val a = hash(xi, zi, s); val b = hash(xi + 1, zi, s)
        val c = hash(xi, zi + 1, s); val d = hash(xi + 1, zi + 1, s)
        val t = a + (b - a) * sx; val u = c + (d - c) * sx
        return t + (u - t) * sz
    }

    fun generate(seed: Int) {
        java.util.Arrays.fill(blocks, 0)
        val h = IntArray(SX * SZ)
        for (x in 0 until SX) for (z in 0 until SZ) {
            val dx = (x - SX / 2f) / (SX / 2f); val dz = (z - SZ / 2f) / (SZ / 2f)
            val d = sqrt(dx * dx + dz * dz)
            val f = (1f - d * d).coerceAtLeast(0f)
            val n = vnoise(x * 0.05f, z * 0.05f, seed) * 0.65f + vnoise(x * 0.11f, z * 0.11f, seed + 7) * 0.35f
            val hh = (3f + f * 8f + n * 9f * f).toInt().coerceIn(1, SY - 8)
            h[x * SZ + z] = hh
            val beach = hh <= WATER_Y + 1
            for (y in 0..hh) {
                val id = when {
                    y == hh -> if (beach) B.SAND else B.GRASS
                    y >= hh - 3 -> if (beach) B.SAND else B.DIRT
                    else -> B.STONE
                }
                blocks[(y * SZ + z) * SX + x] = id.toByte()
            }
            for (y in hh + 1..WATER_Y) blocks[(y * SZ + z) * SX + x] = B.WATER.toByte()
        }
        val rnd = Random(seed.toLong())
        val trees = ArrayList<IntArray>()
        for (i in 0 until 220) {
            val x = 4 + rnd.nextInt(SX - 8); val z = 4 + rnd.nextInt(SZ - 8)
            val y = h[x * SZ + z]
            if (get(x, y, z) != B.GRASS || y <= WATER_Y + 1) continue
            if (Math.abs(x - SX / 2) < 5 && Math.abs(z - SZ / 2) < 5) continue
            if (trees.any { Math.abs(it[0] - x) < 4 && Math.abs(it[1] - z) < 4 }) continue
            trees.add(intArrayOf(x, z))
            val th = 4 + rnd.nextInt(2)
            for (t in 1..th) blocks[((y + t) * SZ + z) * SX + x] = B.WOOD.toByte()
            for (dy in th - 2..th + 1) {
                val r = if (dy >= th) 1 else 2
                for (dx in -r..r) for (dz in -r..r) {
                    if (Math.abs(dx) == r && Math.abs(dz) == r && rnd.nextBoolean()) continue
                    val bx = x + dx; val by = y + dy; val bz = z + dz
                    if (bx in 0 until SX && bz in 0 until SZ && by < SY && get(bx, by, bz) == B.AIR)
                        blocks[(by * SZ + bz) * SX + bx] = B.LEAVES.toByte()
                }
            }
        }
        // pilares decorativos perto do spawn
        val cols = intArrayOf(B.PINK, B.BLUE, B.YELLOW, B.BRICK)
        for (i in 0 until 4) {
            val x = SX / 2 + 3 + i * 2; val z = SZ / 2 + 4
            val y = surfaceY(x, z)
            for (t in 0..2) blocks[((y + t) * SZ + z) * SX + x] = cols[i].toByte()
            blocks[((y + 3) * SZ + z) * SX + x] = B.LANTERN.toByte()
        }
        java.util.Arrays.fill(dirty, true)
    }

    fun save(f: File) {
        try { GZIPOutputStream(f.outputStream()).use { it.write(blocks.clone()) } } catch (_: Exception) {}
    }

    fun load(f: File): Boolean {
        return try {
            if (!f.exists()) return false
            val data = GZIPInputStream(f.inputStream()).use { it.readBytes() }
            if (data.size != blocks.size) return false
            System.arraycopy(data, 0, blocks, 0, data.size)
            java.util.Arrays.fill(dirty, true); true
        } catch (_: Exception) { false }
    }

    private fun hcol(c: Int, sh: Float, out: FloatArray) {
        out[0] = ((c shr 16) and 255) / 255f * sh; out[1] = ((c shr 8) and 255) / 255f * sh; out[2] = (c and 255) / 255f * sh
    }

    fun buildChunk(cx: Int, cz: Int, o: MeshBuf, w: MeshBuf) {
        o.clear(); w.clear()
        val c = IntArray(3); val p = FloatArray(3); val col = FloatArray(3); val ao = FloatArray(4)
        for (y in 0 until SY) for (z in cz * CH until cz * CH + CH) for (x in cx * CH until cx * CH + CH) {
            val id = get(x, y, z)
            if (id == B.AIR) continue
            val isW = id == B.WATER
            val lowered = isW && get(x, y + 1, z) == B.AIR
            val vr = 0.94f + 0.06f * hash(x, z, y)
            c[0] = x; c[1] = y; c[2] = z
            if (id == B.GRASS && get(x, y + 1, z) == B.AIR) {
                val r = hash(x, z, 3)
                if (r > 0.38f) {
                    val fl = r > 0.965f
                    val tile = if (fl) 17 + (hash(x, z, 4) * 3f).toInt().coerceIn(0, 2) else 16
                    val hh = if (fl) 0.62f else 0.4f + 0.4f * hash(x, z, 9)
                    val ww = if (fl) 0.55f else 0.75f + 0.25f * hash(x, z, 10)
                    val ox = x + 0.5f + (hash(x, z, 5) - 0.5f) * 0.4f; val oz = z + 0.5f + (hash(x, z, 6) - 0.5f) * 0.4f
                    val ang = hash(x, z, 11) * 3.1416f
                    val u0 = (tile + 0.01f) / Atlas.NT; val u1 = (tile + 0.99f) / Atlas.NT
                    val top = 0.98f + 0.1f * vr; val bot = 0.7f
                    for (d in 0 until 2) {
                        val a2 = ang + d * 1.5708f
                        val ax = kotlin.math.cos(a2) * 0.5f * ww; val az = kotlin.math.sin(a2) * 0.5f * ww
                        o.vert(ox - ax, y + 0.98f, oz - az, bot, bot, bot, u0, 0.99f)
                        o.vert(ox + ax, y + 0.98f, oz + az, bot, bot, bot, u1, 0.99f)
                        o.vert(ox + ax, y + 0.98f + hh, oz + az, top, top, top, u1, 0.01f)
                        o.vert(ox - ax, y + 0.98f + hh, oz - az, top, top, top, u0, 0.01f)
                        o.tri(0, 1, 2); o.tri(0, 2, 3); o.tri(0, 2, 1); o.tri(0, 3, 2); o.vc += 4
                    }
                }
            }
            for (face in 0 until 6) {
                val a = face shr 1; val s = if ((face and 1) == 0) 1 else -1
                val nx = x + if (a == 0) s else 0; val ny = y + if (a == 1) s else 0; val nz = z + if (a == 2) s else 0
                val nid = get(nx, ny, nz)
                if (isW) { if (nid != B.AIR) continue } else if (nid != B.AIR && nid != B.WATER) continue
                val u = (a + 1) % 3; val v = (a + 2) % 3
                val shade = when { id == B.LANTERN -> 1f; a == 1 -> if (s > 0) 1f else 0.55f; a == 0 -> 0.82f; else -> 0.7f }
                val buf = if (isW) w else o
                for (q in 0 until 4) {
                    val cu = QU[q]; val cv = QV[q]
                    p[0] = c[0].toFloat(); p[1] = c[1].toFloat(); p[2] = c[2].toFloat()
                    p[a] += if (s > 0) 1f else 0f; p[u] += cu.toFloat(); p[v] += cv.toFloat()
                    if (lowered && p[1] > y) p[1] = y + 0.88f
                    var aob = 1f
                    if (!isW && id != B.LANTERN) {
                        val du = if (cu == 1) 1 else -1; val dv = if (cv == 1) 1 else -1
                        val bx = IntArray(3); bx[0] = c[0]; bx[1] = c[1]; bx[2] = c[2]; bx[a] += s
                        val s1 = bx.clone(); s1[u] += du
                        val s2 = bx.clone(); s2[v] += dv
                        val cc = s1.clone(); cc[v] += dv
                        val a1 = if (solid(s1[0], s1[1], s1[2])) 1 else 0
                        val a2 = if (solid(s2[0], s2[1], s2[2])) 1 else 0
                        val a3 = if (solid(cc[0], cc[1], cc[2])) 1 else 0
                        val lvl = if (a1 == 1 && a2 == 1) 0 else 3 - (a1 + a2 + a3)
                        ao[q] = AOB[lvl]; aob = ao[q]
                    } else ao[q] = 1f
                    val tile = if (a == 1) (if (s > 0) B.tTop[id] else B.tBot[id]) else B.tSide[id]
                    var tu = cu.toFloat(); var tv = cv.toFloat()
                    if (a == 0) { tu = cv.toFloat(); tv = 1f - cu } else if (a == 2) { tv = 1f - cv }
                    val gr = shade * vr * aob
                    buf.vert(p[0], p[1], p[2], gr, gr, gr, (tile + 0.01f + tu * 0.98f) / Atlas.NT.toFloat(), 0.01f + tv * 0.98f)
                }
                val flip = ao[0] + ao[2] < ao[1] + ao[3]
                if (s > 0) {
                    if (!flip) { buf.tri(0, 1, 2); buf.tri(0, 2, 3) } else { buf.tri(1, 2, 3); buf.tri(1, 3, 0) }
                } else {
                    if (!flip) { buf.tri(0, 2, 1); buf.tri(0, 3, 2) } else { buf.tri(1, 3, 2); buf.tri(1, 0, 3) }
                }
                buf.vc += 4
            }
        }
    }
}
