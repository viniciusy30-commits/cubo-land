package com.cuboland.app

import java.io.File
import java.util.Random
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import kotlin.math.floor
import kotlin.math.cos
import kotlin.math.sin
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
        @JvmField var SX = 96; @JvmField var SZ = 96
        const val SY = 32; const val CH = 16
        val CX: Int get() = SX / CH
        val CZ: Int get() = SZ / CH
        const val WATER_Y = 9
        /** deve ser chamado ANTES de criar World/Game/GameRenderer: o mapa plano é bem maior que a ilha */
        fun configure(flat: Boolean) { SX = if (flat) 256 else 96; SZ = SX }
        val CU = intArrayOf(0, 1); val CV = intArrayOf(0, 1)
        val QU = intArrayOf(0, 1, 1, 0); val QV = intArrayOf(0, 0, 1, 1)
        val AOB = floatArrayOf(0.86f, 0.91f, 0.96f, 1f)
    }

    val blocks = ByteArray(SX * SY * SZ)
    val dirty = BooleanArray(CX * CZ) { true }
    /** blocos desenhados à parte (esculpidos pelos golpes): o mesher do chunk pula eles */
    val hidden = HashSet<Int>()
    /** matinhos/flores cortados pela espada: chave do bloco de grama -> instante do corte (voltam a crescer depois) */
    val cut = HashMap<Int, Float>()

    fun markDirty(x: Int, z: Int) {
        for (dx in -1..1) for (dz in -1..1) {
            val cx = (x + dx).coerceIn(0, SX - 1) / CH; val cz = (z + dz).coerceIn(0, SZ - 1) / CH
            dirty[cz * CX + cx] = true
        }
    }

    /** 0 = sem matinho, 1 = matinho, 2 = flor (igual ao que o mesher desenha em cima da grama) */
    fun tuftKind(x: Int, y: Int, z: Int): Int {
        if (get(x, y, z) != B.GRASS || get(x, y + 1, z) != B.AIR) return 0
        val r = hash(x, z, 3); if (r <= 0.55f) return 0
        if (cut.containsKey((y * SZ + z) * SX + x)) return 0
        return if (r > 0.94f) 2 else 1
    }

    fun cutTuft(x: Int, y: Int, z: Int, now: Float) { cut[(y * SZ + z) * SX + x] = now; markDirty(x, z) }

    fun regrow(now: Float, life: Float) {
        if (cut.isEmpty()) return
        val it = cut.entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (now - e.value > life) { val k = e.key; it.remove(); markDirty(k % SX, (k / SX) % SZ) }
        }
    }

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
        plantForest(Random(seed.toLong()), 420) { x, z -> h[x * SZ + z] }
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


    /** Mapa plano: planície de grama (y=10), camadas de terra/pedra embaixo, um rio enorme serpenteando e árvores bem espaçadas. */
    fun generateFlat(seed: Int) {
        java.util.Arrays.fill(blocks, 0)
        val h = IntArray(SX * SZ)
        for (x in 0 until SX) {
            val wob = vnoise(x * 0.012f, 0f, seed + 3)
            val zc = SZ / 2f + 58f + 22f * sin(x * 0.022f + seed * 0.1f) + (wob - 0.5f) * 30f
            val hw = 15f + 5f * sin(x * 0.047f + 1.3f) + 3f * vnoise(x * 0.03f, 4f, seed + 9)
            for (z in 0 until SZ) {
                val t = Math.abs(z - zc) / hw
                val depth = if (t < 1f) 1 + Math.round(6f * (1f - t * t)) else 0
                val hh = 10 - depth
                h[x * SZ + z] = hh
                val river = depth > 0
                for (y in 0..hh) {
                    val id = when {
                        y == hh -> if (river) (if (t > 0.55f) B.SAND else B.DIRT) else B.GRASS
                        y >= hh - 2 -> if (river) B.SAND else B.DIRT
                        else -> B.STONE
                    }
                    blocks[(y * SZ + z) * SX + x] = id.toByte()
                }
                for (y in hh + 1..WATER_Y) blocks[(y * SZ + z) * SX + x] = B.WATER.toByte()
            }
        }
        val rnd = Random(seed.toLong())
        val placed = ArrayList<IntArray>()
        for (i in 0 until 1600) {
            val x = 6 + rnd.nextInt(SX - 12); val z = 6 + rnd.nextInt(SZ - 12)
            val y = h[x * SZ + z]
            if (y != 10 || get(x, y, z) != B.GRASS || !air(x, y + 1, z)) continue
            if (Math.abs(x - SX / 2) < 8 && Math.abs(z - SZ / 2) < 8) continue
            val type = pickTree(rnd); val r = treeRadius[type]
            if (placed.any { Math.max(Math.abs(it[0] - x), Math.abs(it[1] - z)) < it[2] + r + 9 }) continue   // bem espaçadas
            placed.add(intArrayOf(x, z, r))
            plantTree(x, y, z, type, rnd)
        }
        val cols = intArrayOf(B.PINK, B.BLUE, B.YELLOW, B.BRICK)
        for (i in 0 until 4) {
            val x = SX / 2 + 3 + i * 2; val z = SZ / 2 + 4
            val y = surfaceY(x, z)
            for (t in 0..2) blocks[((y + t) * SZ + z) * SX + x] = cols[i].toByte()
            blocks[((y + 3) * SZ + z) * SX + x] = B.LANTERN.toByte()
        }
        java.util.Arrays.fill(dirty, true)
    }

    // ================= ÁRVORES =================
    private fun air(x: Int, y: Int, z: Int) = x in 0 until SX && z in 0 until SZ && y in 0 until SY && get(x, y, z) == B.AIR

    /** copa irregular: elipsoide de folhas com bordas ruidosas */
    private fun blob(cx: Int, cy: Int, cz: Int, rx: Float, ry: Float, rz: Float, rnd: Random) {
        val ix = Math.ceil(rx.toDouble()).toInt() + 1; val iy = Math.ceil(ry.toDouble()).toInt() + 1; val iz = Math.ceil(rz.toDouble()).toInt() + 1
        for (dx in -ix..ix) for (dy in -iy..iy) for (dz in -iz..iz) {
            val d = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) + (dz * dz) / (rz * rz)
            val thr = 0.82f + rnd.nextFloat() * 0.34f
            if (d <= thr && air(cx + dx, cy + dy, cz + dz)) set(cx + dx, cy + dy, cz + dz, B.LEAVES)
        }
    }

    private fun wood(x: Int, y: Int, z: Int) {
        val g = get(x, y, z)
        if (g == B.AIR || g == B.LEAVES) set(x, y, z, B.WOOD)
    }

    private val DECOR = false
    private val DX8 = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
    private val DZ8 = intArrayOf(0, 1, 1, 1, 0, -1, -1, -1)

    /** galho fino: sai do tronco na direção d (0..7), anda um eixo por vez (blocos sempre encostados) e sobe devagar.
     *  Com fork, brota um raminho no meio. Retorna a ponta [x,y,z] */
    private fun limb(x: Int, y: Int, z: Int, d: Int, len: Int, rnd: Random, fork: Boolean): IntArray {
        val dx = DX8[d and 7]; val dz = DZ8[d and 7]
        var bx = x; var by = y; var bz = z
        for (i in 1..len) {
            if (dx != 0 && dz != 0) { if (i % 2 == 1) bx += dx else bz += dz } else { bx += dx; bz += dz }
            wood(bx, by, bz)
            if (i % 2 == 0 || i == len) { by++; wood(bx, by, bz) }
            if (fork && len >= 4 && i == len / 2 + 1) {
                val t = limb(bx, by, bz, d + (if (rnd.nextBoolean()) 2 else -2) + 8, 2, rnd, false)
                blob(t[0], t[1], t[2], 1.5f, 1.2f, 1.5f, rnd)
            }
        }
        return intArrayOf(bx, by, bz)
    }

    /** galho grosso de madeira (sem folha nenhuma): anda na direção d, sobe a cada "rise" passos e brota galhos menores (depth) pelo caminho */
    private fun bough(x: Int, y: Int, z: Int, d: Int, len: Int, rise: Int, depth: Int, rnd: Random) {
        val dx = DX8[d and 7]; val dz = DZ8[d and 7]
        var bx = x; var by = y; var bz = z
        for (i in 1..len) {
            if (dx != 0 && dz != 0) { if (i % 2 == 1) bx += dx else bz += dz } else { bx += dx; bz += dz }
            wood(bx, by, bz)
            if (i % rise == 0 || i == len) { by++; wood(bx, by, bz) }
            if (depth < 2 && len >= 3 && i >= 2 && i < len && rnd.nextInt(if (depth == 0) 2 else 3) == 0) {
                val side = if (rnd.nextBoolean()) 1 + rnd.nextInt(2) else -1 - rnd.nextInt(2)
                bough(bx, by, bz, d + side + 8, Math.max(2, len * 2 / 3 - depth), Math.max(1, rise - 1), depth + 1, rnd)
            }
        }
    }

    /** copa cheia de volume: bola central + gomos em volta (não fica uma bola lisa) */
    private fun crown(cx: Int, cy: Int, cz: Int, r: Float, rnd: Random) {
        blob(cx, cy, cz, r, r * 0.8f, r, rnd)
        val n = 3 + r.toInt()
        val a0 = rnd.nextFloat() * 6.283f
        for (k in 0 until n) {
            val a = a0 + k * 6.283f / n + (rnd.nextFloat() - 0.5f) * 0.6f
            val dist = r * (0.7f + rnd.nextFloat() * 0.2f)
            blob(cx + Math.round(Math.cos(a.toDouble()) * dist).toInt(), cy + rnd.nextInt(3) - 1, cz + Math.round(Math.sin(a.toDouble()) * dist).toInt(),
                r * 0.55f, r * 0.45f, r * 0.55f, rnd)
        }
    }

    /** n galhos espalhados em volta do tronco, em alturas diferentes, cada um com uma folhagem na ponta */
    private fun limbs(tx: Int, tz: Int, y0: Int, y1: Int, n: Int, minLen: Int, maxLen: Int, leafR: Float, rnd: Random, thick: Boolean = false, bx: Int = tx, bz: Int = tz) {
        val d0 = rnd.nextInt(8)
        for (k in 0 until n) {
            val d = (d0 + k * 8 / n + rnd.nextInt(2)) and 7
            val sx = if (thick && DX8[d] > 0) bx + 1 else bx
            val sz = if (thick && DZ8[d] > 0) bz + 1 else bz
            val yy = y0 + (if (n > 1) (y1 - y0) * k / (n - 1) else 0) + rnd.nextInt(2)
            val e = limb(sx, yy, sz, d, minLen + rnd.nextInt(maxLen - minLen + 1), rnd, true)
            blob(e[0], e[1] + 1, e[2], leafR, leafR * 0.8f, leafR, rnd)
        }
    }

    /** tipo: 0 pequena, 1 média, 2 grande (tronco grosso), 3 alta e fina, 4 pinheiro, 5 arbusto, 6 tronco caído, 7 árvore de galhos (sem blocos de folha) */
    private fun pickTree(rnd: Random): Int {
        val r = rnd.nextInt(24)
        return when { r < 5 -> 0; r < 11 -> 1; r < 14 -> 2; r < 16 -> 3; r < 19 -> 4; r < 22 -> 5; else -> 6 }
    }
    private val treeRadius = intArrayOf(3, 5, 7, 3, 4, 2, 3, 8)

    /** tronco com curvas leves: a cada "kink" o tronco desvia 1 bloco pro lado. Retorna a coluna do topo [x,z] */
    private fun trunk(x: Int, y: Int, z: Int, h: Int, kinks: Int, rnd: Random): IntArray {
        var tx = x; var tz = z
        val at = HashSet<Int>()
        for (k in 0 until kinks) at.add(2 + rnd.nextInt(Math.max(1, h - 3)))
        for (t in 1..h) {
            wood(tx, y + t, tz)
            if (t in at) {
                val d = rnd.nextInt(4)
                tx += if (d == 0) 1 else if (d == 1) -1 else 0
                tz += if (d == 2) 1 else if (d == 3) -1 else 0
                wood(tx, y + t, tz)
            }
        }
        return intArrayOf(tx, tz)
    }

    /** raiz: 1 bloco encostado no base do tronco */
    private fun root(x: Int, y: Int, z: Int, dx: Int, dz: Int) {
        if (get(x + dx, y, z + dz) == B.GRASS && air(x + dx, y + 1, z + dz)) wood(x + dx, y + 1, z + dz)
    }

    /** folhas caindo pelas bordas da copa: tira o aspecto de "bola lisa" */
    private fun droop(cx: Int, cy: Int, cz: Int, r: Int, h: Int, rnd: Random) {
        for (dx in -r..r) for (dz in -r..r) {
            val x = cx + dx; val z = cz + dz
            var low = -1
            for (yy in Math.max(1, cy - 4)..cy + h) if (get(x, yy, z) == B.LEAVES) { low = yy; break }
            if (low < 0) continue
            if (rnd.nextInt(4) == 0 && air(x, low - 1, z)) {
                set(x, low - 1, z, B.LEAVES)
                if (rnd.nextInt(3) == 0 && air(x, low - 2, z)) set(x, low - 2, z, B.LEAVES)
            }
        }
    }

    /** disco de folhas com borda irregular (usado no pinheiro) */
    private fun disc(cx: Int, y: Int, cz: Int, rad: Float, rnd: Random) {
        val ir = Math.ceil(rad.toDouble()).toInt() + 1
        for (dx in -ir..ir) for (dz in -ir..ir) {
            val d = sqrt((dx * dx + dz * dz).toFloat())
            if (d <= rad + (rnd.nextFloat() - 0.5f) * 0.8f && air(cx + dx, y, cz + dz)) set(cx + dx, y, cz + dz, B.LEAVES)
        }
    }

    private fun plantTree(x: Int, y: Int, z: Int, type: Int, rnd: Random) {
        when (type) {
            0 -> {
                val th = 3 + rnd.nextInt(2)
                val tp = trunk(x, y, z, th, rnd.nextInt(2), rnd)
                limbs(tp[0], tp[1], y + th - 1, y + th - 1, 1, 2, 2, 1.7f, rnd)
                crown(tp[0], y + th + 1, tp[1], 2.4f, rnd)
                droop(tp[0], y + th, tp[1], 3, 3, rnd)
            }
            1 -> {
                val th = 5 + rnd.nextInt(3)
                val tp = trunk(x, y, z, th, 1 + rnd.nextInt(2), rnd)
                root(x, y, z, 1, 0); root(x, y, z, 0, 1)
                if (rnd.nextBoolean()) root(x, y, z, -1, 0)
                if (rnd.nextBoolean()) root(x, y, z, 0, -1)
                limbs(tp[0], tp[1], y + th - 3, y + th - 1, 3 + rnd.nextInt(2), 3, 4, 2.0f, rnd)
                crown(tp[0], y + th + 1, tp[1], 3.1f, rnd)
                droop(tp[0], y + th, tp[1], 5, 5, rnd)
            }
            2 -> {
                val th = 9 + rnd.nextInt(4)
                val thick = get(x + 1, y, z) == B.GRASS && get(x, y, z + 1) == B.GRASS && get(x + 1, y, z + 1) == B.GRASS
                var tx = x; var tz = z
                if (thick) {   // tronco grosso que afina perto do topo
                    for (t in 1..th) { wood(x, y + t, z); if (t <= th - 3) { wood(x + 1, y + t, z); wood(x, y + t, z + 1); wood(x + 1, y + t, z + 1) } }
                } else { val tp = trunk(x, y, z, th, 2, rnd); tx = tp[0]; tz = tp[1] }
                for (dx in -1..2) for (dz in -1..2) {   // raízes aparentes, com pé no chão
                    if ((dx == 0 || dx == 1) && (dz == 0 || dz == 1)) continue
                    if (rnd.nextInt(3) == 0 && get(x + dx, y, z + dz) == B.GRASS && air(x + dx, y + 1, z + dz)) {
                        wood(x + dx, y + 1, z + dz)
                        if (rnd.nextInt(3) == 0 && air(x + dx * 2, y + 1, z + dz * 2) && get(x + dx * 2, y, z + dz * 2) == B.GRASS) wood(x + dx * 2, y + 1, z + dz * 2)
                    }
                }
                limbs(tx, tz, y + th - 6, y + th - 2, 6 + rnd.nextInt(2), 3, 5, 2.7f, rnd, thick, x, z)
                crown(tx, y + th + 2, tz, 4.6f, rnd)
                droop(tx, y + th, tz, 7, 6, rnd)
            }
            3 -> {
                val th = 8 + rnd.nextInt(3)
                val tp = trunk(x, y, z, th + 1, 1, rnd)
                limbs(tp[0], tp[1], y + th - 5, y + th - 3, 2, 2, 3, 1.5f, rnd)
                for (k in 0..3) { val r = 3.1f - k * 0.7f; blob(tp[0], y + th - 4 + k * 2, tp[1], r, 1.7f, r, rnd) }
                blob(tp[0], y + th + 3, tp[1], 1.1f, 1.4f, 1.1f, rnd)
                droop(tp[0], y + th - 2, tp[1], 3, 6, rnd)
            }
            4 -> {   // pinheiro: camadas em degraus, bem fechadas embaixo e pontudas em cima
                val th = 9 + rnd.nextInt(4)
                for (t in 1..th + 1) wood(x, y + t, z)
                val base = y + 3
                for (ly in base..y + th + 1) {
                    val f = (ly - base).toFloat() / (th - 2).coerceAtLeast(1)
                    var rad = 3.7f * (1f - f) + 0.5f
                    if ((ly - base) % 3 == 2) rad -= 1.2f          // "cintura" entre os andares
                    if (rad > 0.2f) disc(x, ly, z, rad, rnd) else if (air(x, ly, z)) set(x, ly, z, B.LEAVES)
                }
                if (air(x, y + th + 2, z)) set(x, y + th + 2, z, B.LEAVES)
                if (air(x, y + th + 3, z)) set(x, y + th + 3, z, B.LEAVES)
            }
            5 -> {   // arbusto com um toquinho escondido (pra ser limpo junto com as árvores)
                wood(x, y + 1, z)
                blob(x, y + 1, z, 1.7f, 1.3f, 1.7f, rnd)
                if (rnd.nextBoolean()) blob(x + 1 - rnd.nextInt(3), y + 1, z + 1 - rnd.nextInt(3), 1.3f, 1.1f, 1.3f, rnd)
            }
            7 -> {   // árvore de galhos: tronco + galhos grossos e finos espalhados numa esfera; as folhas são cartões nos galhos (ver boughDecor), sem blocos de folha
                val th = 7 + rnd.nextInt(3)
                val tp = trunk(x, y, z, th + 2, 2, rnd)
                root(x, y, z, 1, 0); root(x, y, z, 0, 1); root(x, y, z, -1, 0); root(x, y, z, 0, -1)
                val cy = y + th; val R = 5.3f   // centro e raio da copa redonda
                val n = 10; val d0 = rnd.nextInt(8)
                for (k in 0 until n) {
                    val dy0 = -4 + k * 6 / (n - 1)   // alturas de cy-4 até cy+2
                    val d = (d0 + k * 3 + rnd.nextInt(2)) and 7
                    val dyEnd = dy0 + 1.5f
                    val len = Math.round(Math.sqrt(Math.max(4f, R * R - dyEnd * dyEnd).toDouble())).toInt() - 1 + rnd.nextInt(2)
                    bough(tp[0], cy + dy0, tp[1], d, Math.max(3, len), if (dy0 < -1) 3 else 2, 0, rnd)
                }
                for (k in 0 until 4) bough(tp[0], cy + 1 + k % 2, tp[1], d0 + k * 2 + 1, 3 + rnd.nextInt(2), 1, 1, rnd)   // topo da copa: galhos empinados
            }
            else -> {   // tronco caído, com folhas numa ponta
                val ax = if (rnd.nextBoolean()) 1 else 0; val az = 1 - ax
                val len = 3 + rnd.nextInt(2)
                for (i in 0 until len) {
                    val px = x + i * ax; val pz = z + i * az
                    if (get(px, y, pz) == B.GRASS && air(px, y + 1, pz)) wood(px, y + 1, pz)
                }
                if (rnd.nextBoolean()) blob(x + (len - 1) * ax, y + 2, z + (len - 1) * az, 1.3f, 1.0f, 1.3f, rnd)
            }
        }
    }

    private fun plantForest(rnd: Random, tries: Int, groundAt: (Int, Int) -> Int) {
        val placed = ArrayList<IntArray>()
        for (i in 0 until tries) {
            val x = 5 + rnd.nextInt(SX - 10); val z = 5 + rnd.nextInt(SZ - 10)
            val y = groundAt(x, z)
            if (get(x, y, z) != B.GRASS || y <= WATER_Y + 1 || !air(x, y + 1, z)) continue
            if (Math.abs(x - SX / 2) < 6 && Math.abs(z - SZ / 2) < 6) continue
            val type = pickTree(rnd); val r = treeRadius[type]
            if (placed.any { Math.max(Math.abs(it[0] - x), Math.abs(it[1] - z)) < it[2] + r }) continue
            placed.add(intArrayOf(x, z, r))
            plantTree(x, y, z, type, rnd)
        }
    }

    /** mundos antigos: apaga as árvores naturais (troncos em grama e folhas ligadas) e planta as novas */
    fun replantTrees(seed: Int) {
        val seen = BooleanArray(blocks.size)
        val q = java.util.ArrayDeque<IntArray>()
        for (x in 0 until SX) for (z in 0 until SZ) for (y in 1 until SY) {
            if (get(x, y, z) != B.WOOD || get(x, y - 1, z) != B.GRASS) continue
            q.clear(); q.add(intArrayOf(x, y, z)); seen[(y * SZ + z) * SX + x] = true
            while (q.isNotEmpty()) {
                val c = q.poll()
                set(c[0], c[1], c[2], B.AIR)
                for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                    val nx = c[0] + dx; val ny = c[1] + dy; val nz = c[2] + dz
                    if (nx < 0 || nx >= SX || nz < 0 || nz >= SZ || ny < 0 || ny >= SY) continue
                    if (Math.abs(nx - x) > 9 || Math.abs(nz - z) > 9 || ny - y > 16) continue
                    val id = get(nx, ny, nz)
                    if (id != B.WOOD && id != B.LEAVES) continue
                    val k = (ny * SZ + nz) * SX + nx
                    if (seen[k]) continue
                    seen[k] = true; q.add(intArrayOf(nx, ny, nz))
                }
            }
        }
        plantForest(Random(seed.toLong() + 99), 420) { x, z -> surfaceY(x, z) - 1 }
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

    /** bolota de folhas (esfera lowpoly arredondada): cada quadradinho mostra um pedaço do tile de folha, dando a copa fofa da referência */
    private fun puff(o: MeshBuf, cx: Float, cy: Float, cz: Float, r: Float, n: Int, m: Int, rot: Float, vr: Float, seed: Int) {
        for (j in 0 until m) for (i in 0 until n) {
            val ou = if (hash(seed, i * 7 + j, 311) > 0.5f) 0.5f else 0f
            val ov = if (hash(seed, i * 5 + j * 3, 312) > 0.5f) 0.5f else 0f
            for (q in 0 until 4) {
                val ii = i + (if (q == 1 || q == 2) 1 else 0); val jj = j + (if (q >= 2) 1 else 0)
                val th = 3.14159f * jj / m; val ph = rot + 6.28318f * ii / n
                val nx = sin(th) * cos(ph); val ny = cos(th); val nz = sin(th) * sin(ph)
                val sh = (0.62f + 0.38f * (0.5f + 0.5f * ny)) * vr
                val fu = if (q == 1 || q == 2) 1f else 0f; val fv = if (q >= 2) 1f else 0f
                o.vert(cx + r * nx, cy + r * 0.92f * ny, cz + r * nz, sh, sh, sh,
                    (8 + ou + 0.01f + 0.48f * fu) / Atlas.NT, ov + 0.01f + 0.48f * fv)
            }
            o.tri(0, 1, 2); o.tri(0, 2, 3); o.tri(0, 2, 1); o.tri(0, 3, 2); o.vc += 4
        }
    }

    private fun colDepth(x: Int, z: Int): Int {
        val xx = x.coerceIn(0, SX - 1); val zz = z.coerceIn(0, SZ - 1)
        var y = WATER_Y; var n = 0
        while (y >= 0 && get(xx, y, zz) == B.WATER) { n++; y-- }
        return n
    }

    fun buildChunk(cx: Int, cz: Int, o: MeshBuf, w: MeshBuf) {
        o.clear(); w.clear()
        val c = IntArray(3); val p = FloatArray(3); val col = FloatArray(3); val ao = FloatArray(4)
        for (y in 0 until SY) for (z in cz * CH until cz * CH + CH) for (x in cx * CH until cx * CH + CH) {
            val id = get(x, y, z)
            if (id == B.AIR) continue
            if (hidden.isNotEmpty() && hidden.contains((y * SZ + z) * SX + x)) continue
            val isW = id == B.WATER
            val lowered = isW && get(x, y + 1, z) == B.AIR
            val vr = 0.94f + 0.06f * hash(x, z, y)
            c[0] = x; c[1] = y; c[2] = z
            if (id == B.LEAVES) {
                if (o.vc > 55000) continue
                if (!leafOpen(x, y, z)) {
                    // segunda camada: folha escondida mas vizinha de uma folha exposta desenha só o cubo interno (aparece pelos buraquinhos)
                    var near = false
                    for (f in 0 until 6) {
                        val aa = f shr 1; val ss = if ((f and 1) == 0) 1 else -1
                        val qx = x + if (aa == 0) ss else 0; val qy = y + if (aa == 1) ss else 0; val qz = z + if (aa == 2) ss else 0
                        if (get(qx, qy, qz) == B.LEAVES && leafOpen(qx, qy, qz)) { near = true; break }
                    }
                    if (near && o.vc < 50000) leafFaces(o, x, y, z, 0.16f, 0.84f, false, vr, 0.72f)
                    continue
                }
                leafFaces(o, x, y, z, 0f, 1f, true, vr, 1f)        // casca externa (só faces expostas)
                leafFaces(o, x, y, z, 0.16f, 0.84f, false, vr, 0.72f)   // cubo interno: dá profundidade pelos buracos
                continue
            }
            if (id == B.GRASS && get(x, y + 1, z) == B.AIR) {
                val r = hash(x, z, 3)
                if (r > 0.55f && (cut.isEmpty() || !cut.containsKey((y * SZ + z) * SX + x))) {
                    val fl = r > 0.94f
                    val tile = if (fl) 17 + (hash(x, z, 4) * 3f).toInt().coerceIn(0, 2) else 16
                    val hh = if (fl) 0.62f else 0.28f + 0.3f * hash(x, z, 9)
                    val ww = if (fl) 0.55f else 0.6f + 0.25f * hash(x, z, 10)
                    val ox = x + 0.5f + (hash(x, z, 5) - 0.5f) * 0.4f; val oz = z + 0.5f + (hash(x, z, 6) - 0.5f) * 0.4f
                    val ang = hash(x, z, 11) * 3.1416f
                    val u0 = (tile + 0.01f) / Atlas.NT; val u1 = (tile + 0.99f) / Atlas.NT
                    val top = 1.0f; val bot = 0.92f
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
                var nid = get(nx, ny, nz)
                if (nid != B.AIR && hidden.isNotEmpty() && hidden.contains((ny * SZ + nz) * SX + nx)) nid = B.AIR   // bloco esculpido: o vizinho desenha a face voltada pra ele
                if (isW) { if (nid != B.AIR) continue }
                else if (id == B.LEAVES) { if (nid != B.AIR && nid != B.WATER && nid != B.LEAVES) continue }
                else if (nid != B.AIR && nid != B.WATER && nid != B.LEAVES) continue   // folha tem buracos: o que está atrás dela precisa ser desenhado
                val u = (a + 1) % 3; val v = (a + 2) % 3
                val shade = when { id == B.LANTERN -> 1f; id == B.LEAVES -> (if (a == 1) (if (s > 0) 1f else 0.8f) else if (a == 0) 0.92f else 0.86f); a == 1 -> if (s > 0) 1f else 0.55f; a == 0 -> 0.82f; else -> 0.7f }
                val buf = if (isW) w else o
                for (q in 0 until 4) {
                    val cu = QU[q]; val cv = QV[q]
                    p[0] = c[0].toFloat(); p[1] = c[1].toFloat(); p[2] = c[2].toFloat()
                    p[a] += if (s > 0) 1f else 0f; p[u] += cu.toFloat(); p[v] += cv.toFloat()
                    if (lowered && p[1] > y) p[1] = y + 0.88f
                    var aob = 1f
                    if (!isW && id != B.LANTERN && id != B.LEAVES) {
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
                    var cg = gr
                    if (isW) {   // canal G da água = profundidade suave (média das 4 colunas ao redor do vértice)
                        val kx = p[0].toInt(); val kz = p[2].toInt()
                        cg = ((colDepth(kx - 1, kz - 1) + colDepth(kx, kz - 1) + colDepth(kx - 1, kz) + colDepth(kx, kz)) / 20f).coerceIn(0f, 1f)
                    }
                    buf.vert(p[0], p[1], p[2], gr, cg, gr, (tile + 0.01f + tu * 0.98f) / Atlas.NT.toFloat(), 0.01f + tv * 0.98f)
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
        decorate(cx, cz, o)
    }

    // ================= DECORAÇÃO DAS ÁRVORES =================
    // galhos finos com folhagem e folhinhas soltas: tudo é calculado a partir dos blocos na hora de montar a malha (nada vai pro arquivo do mundo)

    private fun quad(o: MeshBuf, tile: Int, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float, s0: Float, s1: Float) {
        val u0 = (tile + 0.01f) / Atlas.NT; val u1 = (tile + 0.99f) / Atlas.NT
        o.vert(ax, ay, az, s0, s0, s0, u0, 0.99f); o.vert(bx, by, bz, s0, s0, s0, u1, 0.99f)
        o.vert(cx, cy, cz, s1, s1, s1, u1, 0.01f); o.vert(dx, dy, dz, s1, s1, s1, u0, 0.01f)
        o.tri(0, 1, 2); o.tri(0, 2, 3); o.tri(0, 2, 1); o.tri(0, 3, 2); o.vc += 4
    }

    /** tufo de folhinhas pontudas: dois cartões verticais cruzados + um deitado */
    private fun cluster(o: MeshBuf, x: Float, y: Float, z: Float, size: Float, sd: Int) {
        val r0 = hash(sd, 1, 410); val r1 = hash(sd, 2, 411); val r2 = hash(sd, 3, 412)
        val tile = if (r0 > 0.5f) 21 else 20
        val ang = r1 * 3.1416f
        val sh = 0.88f + 0.26f * r2
        val hs = size * 0.5f
        for (k in 0 until 2) {
            val a = ang + k * 1.5708f
            val ax = cos(a) * hs; val az = sin(a) * hs
            quad(o, tile, x - ax, y - hs * 0.8f, z - az, x + ax, y - hs * 0.8f, z + az, x + ax, y + hs * 0.8f, z + az, x - ax, y + hs * 0.8f, z - az, sh * 0.8f, sh)
        }
        val ux = cos(ang + 0.7f) * hs; val uz = sin(ang + 0.7f) * hs; val vx = -uz; val vz = ux
        quad(o, if (tile == 20) 21 else 20, x - ux - vx, y - 0.04f, z - uz - vz, x + ux - vx, y - 0.04f, z + uz - vz,
            x + ux + vx, y + 0.12f, z + uz + vz, x - ux + vx, y + 0.12f, z - uz + vz, sh * 0.92f, sh)
    }

    /** ripa fina de casca entre dois pontos, afinando da ponta 0 pra ponta 1 */
    private fun prism(o: MeshBuf, x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, w0: Float, w1: Float, sh: Float) {
        var dx = x1 - x0; var dy = y1 - y0; var dz = z1 - z0
        val len = sqrt(dx * dx + dy * dy + dz * dz); if (len < 0.01f) return
        dx /= len; dy /= len; dz /= len
        var ux = -dz; var uz = dx; var ul = sqrt(ux * ux + uz * uz)
        if (ul < 0.05f) { ux = 1f; uz = 0f; ul = 1f }
        ux /= ul; uz /= ul
        val vx = dy * uz; val vy = dz * ux - dx * uz; val vz = -dy * ux
        val su = floatArrayOf(-1f, 1f, 1f, -1f); val sv = floatArrayOf(-1f, -1f, 1f, 1f)
        val tu0 = 6.08f / Atlas.NT; val tu1 = 6.36f / Atlas.NT
        val vEnd = 0.1f + (len * 0.5f).coerceAtMost(0.8f)
        for (i in 0 until 4) {
            val j = (i + 1) and 3
            val k = sh * (0.72f + 0.1f * i)
            o.vert(x0 + ux * su[i] * w0 + vx * sv[i] * w0, y0 + vy * sv[i] * w0, z0 + uz * su[i] * w0 + vz * sv[i] * w0, k, k, k, tu0, 0.1f)
            o.vert(x0 + ux * su[j] * w0 + vx * sv[j] * w0, y0 + vy * sv[j] * w0, z0 + uz * su[j] * w0 + vz * sv[j] * w0, k, k, k, tu1, 0.1f)
            o.vert(x1 + ux * su[j] * w1 + vx * sv[j] * w1, y1 + vy * sv[j] * w1, z1 + uz * su[j] * w1 + vz * sv[j] * w1, k, k, k, tu1, vEnd)
            o.vert(x1 + ux * su[i] * w1 + vx * sv[i] * w1, y1 + vy * sv[i] * w1, z1 + uz * su[i] * w1 + vz * sv[i] * w1, k, k, k, tu0, vEnd)
            o.tri(0, 1, 2); o.tri(0, 2, 3); o.tri(0, 2, 1); o.tri(0, 3, 2); o.vc += 4
        }
    }

    /** um galho fino com duas curvas, tufos de folhas no meio e na ponta e, às vezes, um raminho lateral */
    private fun twig(o: MeshBuf, x: Int, y: Int, z: Int, k: Int, sc: Float = 1f) {
        val sd = x * 131 + y * 71 + z * 173 + k * 997
        val r = FloatArray(10) { hash(sd, it * 13 + y, 400 + it) }
        var a = r[0] * 6.2832f; var p = 0.25f + r[1] * 0.5f
        val sx = x + 0.5f + cos(a) * 0.3f; val sy = y + 0.5f + (r[3] - 0.3f) * 0.4f; val sz = z + 0.5f + sin(a) * 0.3f
        val l1 = (0.55f + r[2] * 0.3f) * sc
        val x1 = sx + cos(a) * cos(p) * l1; val y1 = sy + sin(p) * l1; val z1 = sz + sin(a) * cos(p) * l1
        a += (r[4] - 0.5f) * 1.4f; p += 0.1f + r[5] * 0.4f
        val l2 = (0.45f + r[6] * 0.3f) * sc
        val x2 = x1 + cos(a) * cos(p) * l2; val y2 = y1 + sin(p) * l2; val z2 = z1 + sin(a) * cos(p) * l2
        val sh = 0.8f + 0.2f * r[7]
        prism(o, sx, sy, sz, x1, y1, z1, 0.07f, 0.05f, sh)
        prism(o, x1, y1, z1, x2, y2, z2, 0.05f, 0.032f, sh)
        cluster(o, x1, y1 + 0.1f, z1, (0.75f + 0.3f * r[8]) * sc, sd + 1)
        cluster(o, x2, y2 + 0.12f, z2, (1.0f + 0.4f * r[8]) * sc, sd + 2)
        if (r[9] > 0.5f) {   // raminho lateral saindo do meio do 2º trecho
            val a3 = a + (if (r[8] > 0.5f) 1.2f else -1.2f); val p3 = p + 0.3f
            val mx = (x1 + x2) * 0.5f; val my = (y1 + y2) * 0.5f; val mz = (z1 + z2) * 0.5f
            val x3 = mx + cos(a3) * cos(p3) * 0.5f * sc; val y3 = my + sin(p3) * 0.5f * sc; val z3 = mz + sin(a3) * cos(p3) * 0.5f * sc
            prism(o, mx, my, mz, x3, y3, z3, 0.035f, 0.022f, sh)
            cluster(o, x3, y3 + 0.08f, z3, 0.7f * sc, sd + 3)
        }
    }

    private fun decorate(cx: Int, cz: Int, o: MeshBuf) {
        if (!DECOR) return   // galhos finos e folhinhas soltas desligados
        for (y in 1 until SY) for (z in cz * CH until cz * CH + CH) for (x in cx * CH until cx * CH + CH) {
            if (o.vc > 58000) return
            decorBlock(o, x, y, z)
        }
    }

    /** galhos e folhinhas de UM bloco (madeira de árvore ou folha exposta) */
    private fun decorBlock(o: MeshBuf, x: Int, y: Int, z: Int) {
        val id = get(x, y, z)
        if (id == B.WOOD) {
            if (hidden.isNotEmpty() && hidden.contains((y * SZ + z) * SX + x)) return
            var leaf = 0
            for (dy in -1..2) for (dz in -2..2) for (dx in -2..2) if (get(x + dx, y + dy, z + dz) == B.LEAVES) leaf++
            if (leaf < 3) { boughDecor(o, x, y, z); return }   // sem folhas por perto: só vale se for galho de árvore de galhos
            if (get(x, y + 1, z) != B.AIR && get(x + 1, y, z) != B.AIR && get(x - 1, y, z) != B.AIR && get(x, y, z + 1) != B.AIR && get(x, y, z - 1) != B.AIR) return
            val n = if (hash(x * 7 + 1, z, y) > 0.4f) 2 else 1
            for (k in 0 until n) twig(o, x, y, z, k)
        } else if (id == B.LEAVES && leafOpen(x, y, z)) {   // folhinhas espetando pra fora da copa: quebra o contorno quadradão
            for (f in 0 until 6) {
                val a = f shr 1; val s = if ((f and 1) == 0) 1 else -1
                if (get(x + if (a == 0) s else 0, y + if (a == 1) s else 0, z + if (a == 2) s else 0) != B.AIR) continue
                val sd = x * 37 + y * 19 + z * 29 + f * 11
                if (hash(sd, y, 420) > 0.3f) continue
                val j = hash(sd, z, 421) - 0.5f; val j2 = hash(sd, x, 422) - 0.5f
                val px = x + 0.5f + (if (a == 0) s * 0.62f else j * 0.8f)
                val py = y + 0.5f + (if (a == 1) s * 0.62f else j2 * 0.8f)
                val pz = z + 0.5f + (if (a == 2) s * 0.62f else (if (a == 0) j else j2) * 0.8f)
                cluster(o, px, py, pz, 0.65f + 0.35f * hash(sd, 5, 423), sd)
            }
        }
    }

    /** decoração de uma árvore que vai tombar (chamar ANTES de tirar os blocos do mundo). Posições relativas ao pivô;
     *  rs[i] = onde começam os índices do bloco i (rs[n] = fim), pra esconder os galhos dos blocos que já quebraram */
    fun decorTree(bl: IntArray, ox: Int, oy: Int, oz: Int, yo: Float, rs: IntArray): MeshBuf {
        val m = MeshBuf(); val n = bl.size / 4
        if (!DECOR) { java.util.Arrays.fill(rs, 0, n + 1, 0); return m }
        for (i in 0 until n) {
            rs[i] = m.inn
            if (m.vc > 58000) continue
            decorBlock(m, ox + bl[i * 4], oy + bl[i * 4 + 1], oz + bl[i * 4 + 2])
        }
        rs[n] = m.inn
        var k = 0
        while (k < m.vn) { m.v[k] -= ox + 0.5f; m.v[k + 1] -= oy + 0.5f - yo; m.v[k + 2] -= oz + 0.5f; k += 8 }
        return m
    }

    /** árvore de galhos: madeira solta no alto, ligada a outra madeira na horizontal, ganha galhinhos e uma nuvem de folhinhas em volta (a copa redonda) */
    private fun boughDecor(o: MeshBuf, x: Int, y: Int, z: Int) {
        var hs = 0
        for (i in 0 until 4) if (get(x + DX8[i * 2], y, z + DZ8[i * 2]) == B.WOOD) hs++
        if (hs == 0) return
        var gy = y - 1
        while (gy > 0 && (get(x, gy, z) == B.AIR || get(x, gy, z) == B.WOOD)) gy--
        if (y - gy < 4) return   // tronco e raízes ficam lisos
        val tip = hs == 1 && get(x, y + 1, z) != B.WOOD
        val n = if (tip) 2 else if (hash(x * 7 + 1, z, y) > 0.5f) 1 else 0
        for (k in 0 until n) twig(o, x, y, z, k, 1.4f)
        val cnt = if (tip) 7 else 5
        for (q in 0 until cnt) {   // folhagem: cartões espalhados numa esfera em volta do galho, mais pra cima que pra baixo
            val sd = x * 53 + y * 31 + z * 17 + q * 7
            cluster(o, x + 0.5f + (hash(sd, 1, 430) - 0.5f) * 3.2f, y + 0.6f + (hash(sd, 2, 431) - 0.4f) * 2.8f, z + 0.5f + (hash(sd, 3, 432) - 0.5f) * 3.2f,
                1.8f + 0.9f * hash(sd, 4, 433), sd)
        }
    }

    private fun leafOpen(x: Int, y: Int, z: Int): Boolean {
        for (f in 0 until 6) {
            val aa = f shr 1; val ss = if ((f and 1) == 0) 1 else -1
            val q = get(x + if (aa == 0) ss else 0, y + if (aa == 1) ss else 0, z + if (aa == 2) ss else 0)
            if (q == B.AIR || q == B.WATER) return true
        }
        return false
    }

    /** faces de folha recortada (tile 8). outer = só faces voltadas pra ar/água; senão todas (cubo interno menor) */
    private fun leafFaces(o: MeshBuf, x: Int, y: Int, z: Int, lo: Float, hi: Float, outer: Boolean, vr: Float, tone: Float) {
        val pos = FloatArray(3)
        for (face in 0 until 6) {
            val a = face shr 1; val s = if ((face and 1) == 0) 1 else -1
            if (outer) {
                val nid = get(x + if (a == 0) s else 0, y + if (a == 1) s else 0, z + if (a == 2) s else 0)
                if (nid != B.AIR && nid != B.WATER) continue
            }
            val u = (a + 1) % 3; val v = (a + 2) % 3
            val sh = (if (a == 1) (if (s > 0) 1f else 0.8f) else if (a == 0) 0.92f else 0.86f) * vr * tone
            for (q in 0 until 4) {
                val cu = QU[q]; val cv = QV[q]
                pos[0] = x.toFloat(); pos[1] = y.toFloat(); pos[2] = z.toFloat()
                pos[a] += if (s > 0) hi else lo
                pos[u] += if (cu == 1) hi else lo
                pos[v] += if (cv == 1) hi else lo
                var tu = cu.toFloat(); var tv = cv.toFloat()
                if (a == 0) { tu = cv.toFloat(); tv = 1f - cu } else if (a == 2) { tv = 1f - cv }
                o.vert(pos[0], pos[1], pos[2], sh, sh, sh, (8 + 0.01f + tu * 0.98f) / Atlas.NT.toFloat(), 0.01f + tv * 0.98f)
            }
            if (s > 0) { o.tri(0, 1, 2); o.tri(0, 2, 3) } else { o.tri(0, 2, 1); o.tri(0, 3, 2) }
            o.vc += 4
        }
    }

    /** caixinha 3D de folha (tile 22): hx,hy,hz = meia-largura em cada eixo; ur/vw = janela do tile (tons diferentes) */
    private fun voxBox(o: MeshBuf, cx: Float, cy: Float, cz: Float, hx: Float, hy: Float, hz: Float, sh: Float, ur: Float, vw: Float) {
        val half = floatArrayOf(hx, hy, hz); val pos = FloatArray(3)
        for (face in 0 until 6) {
            val a = face shr 1; val s = if ((face and 1) == 0) 1 else -1
            val u = (a + 1) % 3; val v = (a + 2) % 3
            val k = (if (a == 1) (if (s > 0) 1f else 0.78f) else if (a == 0) 0.9f else 0.84f) * sh
            for (q in 0 until 4) {
                val cu = QU[q]; val cv = QV[q]
                pos[0] = cx; pos[1] = cy; pos[2] = cz
                pos[a] += s * half[a]
                pos[u] += (if (cu == 1) 1f else -1f) * half[u]
                pos[v] += (if (cv == 1) 1f else -1f) * half[v]
                o.vert(pos[0], pos[1], pos[2], k, k, k, (22f + 0.01f + ur + cu * 0.23f) / Atlas.NT.toFloat(), 0.01f + vw + cv * 0.23f)
            }
            if (s > 0) { o.tri(0, 1, 2); o.tri(0, 2, 3) } else { o.tri(0, 2, 1); o.tri(0, 3, 2) }
            o.vc += 4
        }
    }

    /** placas alongadas de folha, em tamanhos e eixos variados, espetadas pra fora de cada face exposta: silhueta recortada e cheia, como na referência */
    private fun leafPlates(o: MeshBuf, x: Int, y: Int, z: Int, vr: Float) {
        for (f in 0 until 6) {
            val a = f shr 1; val s = if ((f and 1) == 0) 1 else -1
            val nid = get(x + if (a == 0) s else 0, y + if (a == 1) s else 0, z + if (a == 2) s else 0)
            if (nid != B.AIR && nid != B.WATER) continue
            val u = (a + 1) % 3; val v = (a + 2) % 3
            for (k in 0 until 3) {
                val sd = x * 37 + y * 19 + z * 29 + f * 11 + k * 53
                val r1 = hash(sd, y, 301 + k); val r2 = hash(sd, z, 302 + k); val r3 = hash(x, sd, 303 + k)
                val r4 = hash(z, sd, 304 + k); val r5 = hash(sd, x, 305 + k); val r6 = hash(y, sd, 306 + k)
                val c = floatArrayOf(x + 0.5f, y + 0.5f, z + 0.5f)
                c[a] += s * (0.40f + 0.20f * r1)
                c[u] += (r2 - 0.5f) * 0.85f; c[v] += (r3 - 0.5f) * 0.85f
                val half = floatArrayOf(0.05f + 0.03f * r4, 0.05f + 0.03f * r5, 0.05f + 0.03f * r6)
                half[(r6 * 2.99f).toInt()] = 0.16f + 0.12f * r4          // eixo comprido
                val ur = (r5 * 3.99f).toInt() * 0.25f; val vw = (r2 * 3.99f).toInt() * 0.25f
                voxBox(o, c[0], c[1], c[2], half[0], half[1], half[2], vr * (0.95f + 0.2f * r6), ur, vw)
            }
        }
    }

    /** muitas folhinhas pequenas, em ângulos variados, saindo da face exposta: dão o aspecto cheio/peludo da copa */
    private fun addCards(o: MeshBuf, x: Int, y: Int, z: Int, a: Int, s: Int, sh: Float) {
        val u = (a + 1) % 3; val v = (a + 2) % 3
        val uu = floatArrayOf(0.01f, 0.99f, 0.99f, 0.01f); val vv = floatArrayOf(0.99f, 0.99f, 0.01f, 0.01f)
        val sg1 = floatArrayOf(-1f, 1f, 1f, -1f); val sg2 = floatArrayOf(-1f, -1f, 1f, 1f)
        for (k in 0 until 2) {
            val sd = x * 31 + y * 17 + z * 13 + a * 7 + (if (s > 0) 1 else 0) * 5 + k * 101
            val r1 = hash(sd, y, 201 + k); val r2 = hash(sd, z, 202 + k); val r3 = hash(x, sd, 203 + k)
            val r4 = hash(z, sd, 204 + k); val r5 = hash(sd, x, 205 + k)
            val c = floatArrayOf(x + 0.5f, y + 0.5f, z + 0.5f)
            c[a] += s * (0.5f + 0.2f * r1)
            c[u] += (r2 - 0.5f) * 1.0f; c[v] += (r3 - 0.5f) * 1.0f
            val ang = r4 * 6.2832f; val hs = 0.13f + 0.1f * r5
            val tl = 20f + (if (r2 > 0.5f) 1f else 0f)
            val ph = (r5 - 0.5f) * 2.2f; val cph = cos(ph); val sph = sin(ph)
            val e1 = FloatArray(3); e1[u] = cos(ang) * hs; e1[v] = sin(ang) * hs
            val e2 = FloatArray(3); e2[u] = -sin(ang) * hs * cph; e2[v] = cos(ang) * hs * cph; e2[a] = s * hs * sph
            val k2 = sh * (0.88f + 0.22f * r1)
            for (q in 0 until 4) {
                o.vert(c[0] + sg1[q] * e1[0] + sg2[q] * e2[0], c[1] + sg1[q] * e1[1] + sg2[q] * e2[1], c[2] + sg1[q] * e1[2] + sg2[q] * e2[2],
                    k2, k2, k2, (tl + uu[q]) / Atlas.NT.toFloat(), vv[q])
            }
            o.tri(0, 1, 2); o.tri(0, 2, 3); o.tri(0, 2, 1); o.tri(0, 3, 2); o.vc += 4
        }
    }
}
