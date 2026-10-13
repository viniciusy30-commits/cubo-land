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
    const val N = 16384
    /** maior id definido + 1 (a malha de cubos por tipo só vai até aqui) */
    @JvmField var used = 14
    val top = IntArray(N); val side = IntArray(N); val bot = IntArray(N)
    val names = Array(N) { "" }
    val hotbar = intArrayOf(GRASS, STONE, PLANK, BRICK, PINK, BLUE, YELLOW, LANTERN)
    /** tiles do atlas por face */
    val tTop = IntArray(N); val tSide = IntArray(N); val tBot = IntArray(N)
    /** propriedades dos blocos novos (Blk): brilho/vidro, dureza, material, categoria da mochila, descrição */
    val flags = IntArray(N); val hard = FloatArray(N); val mat = IntArray(N); val cat = IntArray(N)
    val desc = Array(N) { "" }
    val defined = BooleanArray(N)
    /** troncos de qualquer árvore (carvalho + Tronco/Caule dos blocos novos): o machado/espada derruba a árvore inteira */
    val isLog = BooleanArray(N)
    /** blocos com relevo em geometria (peças saindo e entrando na face); terreno básico (ids 1..13) continua liso pra não pesar */
    val relief = BooleanArray(N)
    fun log(id: Int) = isLog[id and 16383]
    val byName = HashMap<String, Int>()
    private fun s(id: Int, t: Int, sd: Int, b: Int) { top[id] = t; side[id] = sd; bot[id] = b }
    private fun t(id: Int, a: Int, b: Int, c: Int) { tTop[id] = a; tSide[id] = b; tBot[id] = c }
    init {
        val oldNames = arrayOf("", "Grama", "Terra", "Pedra", "Areia", "Madeira", "Folhas", "Tábua", "Água",
            "Tijolo", "Rosa", "Azul", "Amarelo", "Lanterna")
        for (i in oldNames.indices) names[i] = oldNames[i]
        s(GRASS, 0x66C84C, 0x8C6039, 0x8C6039); s(DIRT, 0x8C6039, 0x8C6039, 0x8C6039)
        s(STONE, 0x9AA0A8, 0x9AA0A8, 0x9AA0A8); s(SAND, 0xF0DE9A, 0xF0DE9A, 0xF0DE9A)
        s(WOOD, 0xB98A55, 0x7A5230, 0xB98A55); s(LEAVES, 0x3FA23B, 0x3FA23B, 0x3FA23B)
        s(PLANK, 0xD9A864, 0xD9A864, 0xD9A864); s(WATER, 0x3A8DDE, 0x3A8DDE, 0x3A8DDE)
        s(BRICK, 0xC4553F, 0xC4553F, 0xC4553F); s(PINK, 0xFF8FC8, 0xFF8FC8, 0xFF8FC8)
        s(BLUE, 0x4F9BFF, 0x4F9BFF, 0x4F9BFF); s(YELLOW, 0xFFD84A, 0xFFD84A, 0xFFD84A)
        s(LANTERN, 0xFFF2A8, 0xFFF2A8, 0xFFF2A8)
        t(GRASS, 1, 2, 3); t(DIRT, 3, 3, 3); t(STONE, 4, 4, 4); t(SAND, 5, 5, 5); t(WOOD, 7, 6, 7); t(LEAVES, 8, 8, 8)
        t(PLANK, 9, 9, 9); t(WATER, 10, 10, 10); t(BRICK, 11, 11, 11); t(PINK, 12, 12, 12); t(BLUE, 13, 13, 13)
        t(YELLOW, 14, 14, 14); t(LANTERN, 15, 15, 15)
        for (id in 1..13) if (id != WATER) defined[id] = true
        isLog[WOOD] = true
        flags[LANTERN] = Blk.F_LIGHT
        for (d in Blk.defs) {
            val id = d.id
            names[id] = d.name; s(id, d.color, d.color, d.color); t(id, d.top, d.side, d.bot)
            flags[id] = d.flags; hard[id] = d.hard; mat[id] = d.mat; cat[id] = d.cat; desc[id] = d.desc
            defined[id] = true; if (id + 1 > used) used = id + 1
            byName[d.name] = id
            if (d.name.startsWith("Tronco ") || d.name.startsWith("Caule ")) isLog[id] = true
            relief[id] = (d.flags and (Blk.F_GLASS or Blk.F_LIGHT or Blk.F_LEAF)) == 0 && !d.name.startsWith("Lã ")
        }
    }
    fun solid(id: Int) = id != AIR && id != WATER
    /** bloco que brilha: sem sombra nem oclusão */
    fun light(id: Int) = (flags[id and 16383] and Blk.F_LIGHT) != 0
    /** bloco translúcido (vidro, gelo, gelatina): desenhado junto com a água */
    fun glass(id: Int) = (flags[id and 16383] and Blk.F_GLASS) != 0
    /** dá pra ver através: a face do vizinho precisa ser desenhada */
    /** folhagem: o carvalho (LEAVES) e os blocos com F_LEAF usam a malha de folha */
    fun leafy(id: Int) = id == LEAVES || (flags[id and 16383] and Blk.F_LEAF) != 0
    fun shape(id: Int): Int { val f = flags[id and 16383]; return if ((f and Blk.F_SHAPE) != 0) (f shr 5) and 7 else 0 }
    fun thin(id: Int) = (flags[id and 16383] and Blk.F_THIN) != 0
    fun see(id: Int) = id == AIR || id == WATER || leafy(id) || thin(id) || shape(id) != 0 || (flags[id and 16383] and Blk.F_GLASS) != 0
}

class MeshBuf {
    var v = FloatArray(16384); var vn = 0
    var ix = IntArray(4096); var inn = 0; var vc = 0
    fun clear() { vn = 0; inn = 0; vc = 0 }
    fun vert(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, u: Float, w: Float) {
        if (vn + 8 > v.size) v = v.copyOf(v.size * 2)
        v[vn++] = x; v[vn++] = y; v[vn++] = z; v[vn++] = r; v[vn++] = g; v[vn++] = b; v[vn++] = u; v[vn++] = w
    }
    fun tri(a: Int, b: Int, c: Int) {
        if (inn + 3 > ix.size) ix = ix.copyOf(ix.size * 2)
        ix[inn++] = vc + a; ix[inn++] = vc + b; ix[inn++] = vc + c
    }
}

class World {
    companion object {
        @JvmField var SX = 96; @JvmField var SZ = 96
        const val SY = 32; const val CH = 16
        val CX: Int get() = SX / CH
        val CZ: Int get() = SZ / CH
        const val WATER_Y = 9
        /** modos extras da galeria de blocos (além das categorias de blocos) */
        const val GAL_FURN = 1; const val GAL_HOUSE = 2; const val GAL_TREES = 3
        const val SAVE_MAGIC = 0x434C3402
        const val TV_MAGIC = 0x54563031   // trailer opcional depois dos blocos: tamanho de cada TV
        /** deve ser chamado ANTES de criar World/Game/GameRenderer: o mapa plano é bem maior que a ilha */
        fun configure(flat: Boolean, tall: Boolean = false) { SX = if (flat) 256 else 96; SZ = if (tall) 384 else SX }   // tall: a galeria de blocos precisa de mais fundo
        val CU = intArrayOf(0, 1); val CV = intArrayOf(0, 1)
        val QU = intArrayOf(0, 1, 1, 0); val QV = intArrayOf(0, 0, 1, 1)
        val AOB = floatArrayOf(0.86f, 0.91f, 0.96f, 1f)
    }

    /** 4 bytes por bloco (Int): praticamente sem limite de tipos de bloco */
    val blocks = IntArray(SX * SY * SZ)
    /** plaquinhas flutuantes (galeria de blocos) */
    class Label(val x: Float, val y: Float, val z: Float, val text: String, val big: Boolean = false, val col: Int = 0) { val lines: List<String> = text.split('\n') }
    /** CopyOnWrite: a galeria é regerada em outra thread enquanto o HUD desenha as plaquinhas */
    val labels = java.util.concurrent.CopyOnWriteArrayList<Label>()
    var spawnX = -1; var spawnZ = -1
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
        return blocks[(y * SZ + z) * SX + x]
    }

    fun solid(x: Int, y: Int, z: Int) = B.solid(get(x, y, z))

    /** caixa real (coords locais do bloco: x0,y0,z0,x1,y1,z1) dos blocos com formato próprio; null = cubo inteiro */
    fun boxOf(x: Int, y: Int, z: Int): FloatArray? = Shapes.box(this, get(x, y, z), x, z, y)
    /** o bloco (x,y,z) é sólido dentro da caixa de mundo dada? (respeita o formato real) */
    fun solidBox(x: Int, y: Int, z: Int, ax0: Float, ay0: Float, az0: Float, ax1: Float, ay1: Float, az1: Float): Boolean {
        val id = get(x, y, z); if (!B.solid(id)) return false
        val b = Shapes.box(this, id, x, z, y) ?: return true
        return ax1 > x + b[0] && ax0 < x + b[3] && ay1 > y + b[1] && ay0 < y + b[4] && az1 > z + b[2] && az0 < z + b[5]
    }
    /** o ponto de mundo está dentro do bloco real? (mira/raio) */
    fun solidPt(x: Int, y: Int, z: Int, px: Float, py: Float, pz: Float): Boolean {
        val id = get(x, y, z); if (!B.solid(id)) return false
        val b = Shapes.pickBox(this, id, x, z, y) ?: return true
        return px >= x + b[0] && px <= x + b[3] && py >= y + b[1] && py <= y + b[4] && pz >= z + b[2] && pz <= z + b[5]
    }

    /** mundo de testes: blocos da apresentação (chão, árvores, vitrines) ficam travados; só dá pra quebrar o que o jogador colocar */
    var locked: java.util.BitSet? = null
    fun isLocked(x: Int, y: Int, z: Int): Boolean {
        val l = locked ?: return false
        if (x < 0 || x >= SX || z < 0 || z >= SZ || y < 0 || y >= SY) return false
        return l.get((y * SZ + z) * SX + x)
    }

    /** tamanho de cada TV (nível 1..3 = maior; sem entrada = normal). Chave = índice do bloco */
    val tvSize = java.util.concurrent.ConcurrentHashMap<Int, Int>()
    fun tvLevel(x: Int, y: Int, z: Int): Int = tvSize[(y * SZ + z) * SX + x] ?: 0
    fun setTvLevel(x: Int, y: Int, z: Int, lv: Int) {
        val k = (y * SZ + z) * SX + x
        if (lv <= 0) tvSize.remove(k) else tvSize[k] = lv
        markDirty(x, z)
    }

    fun set(x: Int, y: Int, z: Int, id: Int) {
        if (x < 0 || x >= SX || z < 0 || z >= SZ || y < 0 || y >= SY) return
        if (!tvSize.isEmpty()) tvSize.remove((y * SZ + z) * SX + x)   // trocou/quebrou o bloco: a TV nova começa no tamanho normal
        blocks[(y * SZ + z) * SX + x] = id
        for (dx in -1..1) for (dz in -1..1) {
            val cx = (x + dx).coerceIn(0, SX - 1) / CH; val cz = (z + dz).coerceIn(0, SZ - 1) / CH
            dirty[cz * CX + cx] = true
        }
    }

    fun surfaceY(x: Int, z: Int): Int {
        for (y in SY - 1 downTo 0) if (solid(x, y, z)) return y + 1
        return 0
    }

    fun hash(x: Int, z: Int, s: Int): Float {
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
                blocks[(y * SZ + z) * SX + x] = id
            }
            for (y in hh + 1..WATER_Y) blocks[(y * SZ + z) * SX + x] = B.WATER
        }
        plantForest(Random(seed.toLong()), 420) { x, z -> h[x * SZ + z] }
        // pilares decorativos perto do spawn
        val cols = intArrayOf(B.PINK, B.BLUE, B.YELLOW, B.BRICK)
        for (i in 0 until 4) {
            val x = SX / 2 + 3 + i * 2; val z = SZ / 2 + 4
            val y = surfaceY(x, z)
            for (t in 0..2) blocks[((y + t) * SZ + z) * SX + x] = cols[i]
            blocks[((y + 3) * SZ + z) * SX + x] = B.LANTERN
        }
        scatterMinerals(seed)
        java.util.Arrays.fill(dirty, true)
    }


    /** Mapa plano: planície de grama (y=10), camadas de terra/pedra embaixo, um rio enorme serpenteando e árvores bem espaçadas. */
    class GalCat(val name: String, val ci: Int, val ids: List<Int>)
    class GalSec(val idx: Int, val title: String, val count: Int)

    /** categorias da galeria (mesma divisão da mochila) */
    private fun galleryCats(): List<GalCat> {
        // os blocos básicos (ids 1..13) não têm categoria própria: entram no começo de cada categoria, a mesma da mochila (Items.cat)
        val basic = ArrayList<Int>(); for (id in 1..13) if (id != B.WATER && B.defined[id]) basic.add(id)
        fun basicsOf(c: Int) = basic.filter { Items.cat(it) == c }
        val cats = ArrayList<GalCat>()
        cats.add(GalCat("NATUREZA", 2, basicsOf(2) + Blk.defs.filter { it.cat == Blk.C_NAT && it.id !in Blk.hidden }.map { it.id }))
        cats.add(GalCat("CONSTRUÇÃO", 3, basicsOf(3) + Blk.defs.filter { it.cat == Blk.C_BUILD && it.id !in Blk.hidden }.map { it.id }))
        cats.add(GalCat("DECORAÇÃO", 4, basicsOf(4) + Blk.defs.filter { it.cat == Blk.C_DEC }.map { it.id }))
        cats.add(GalCat("MOBÍLIA", 5, Blk.defs.filter { it.cat == Blk.C_FURN }.map { it.id }))
        val other = Blk.defs.filter { it.cat != Blk.C_NAT && it.cat != Blk.C_BUILD && it.cat != Blk.C_DEC && it.cat != Blk.C_FURN }.map { it.id }
        if (other.isNotEmpty()) cats.add(GalCat("OUTROS", 0, other))
        return cats
    }

    /** blocos de cada seção da categoria (mesmo índice de Gallery.sections) */
    private fun galleryBuckets(cat: GalCat): Array<ArrayList<Int>> {
        val matById = HashMap<Int, Int>(); for (d in Blk.defs) matById[d.id] = d.mat
        val secs = Gallery.sections(cat.ci)
        val buckets = Array(secs.size) { ArrayList<Int>() }
        for (id in cat.ids) buckets[Gallery.classify(cat.ci, B.names[id], matById[id] ?: 0)].add(id)
        return buckets
    }

    /** menu do painel de escolha: cada categoria com as suas seções que têm blocos */
    fun galleryMenu(): List<Pair<GalCat, List<GalSec>>> = galleryCats().map { c ->
        val secs = Gallery.sections(c.ci); val bk = galleryBuckets(c)
        c to secs.indices.filter { bk[it].isNotEmpty() }.map { GalSec(it, secs[it].title, bk[it].size) }
    }

    private fun setTopB(x: Int, z: Int, b: Int, top: Int) { if (x in 0 until SX && z in 0 until SZ) blocks[(top * SZ + z) * SX + x] = b }

    /** galeria de blocos. Ao entrar vem só o saguão (chão vazio); o painel de categorias chama de novo com o que o jogador escolheu:
     *  catIdx = categoria (índice de galleryMenu) e secFilter = seção dela (-1 = todas), ou extra = GAL_FURN / GAL_HOUSE / GAL_TREES.
     *  Cada tipo de bloco fica numa seção com faixa colorida e título, cada bloco numa célula de piso xadrez com plaquinha. A organização está em Gallery. */
    fun generateTest(catIdx: Int = -1, secFilter: Int = -1, extra: Int = 0) {
        locked = null
        java.util.Arrays.fill(blocks, 0); labels.clear(); tvSize.clear()
        val floor = B.byName["Concreto Cinza-claro"] ?: B.STONE
        val plate = B.byName["Concreto Branco"] ?: floor
        val top = 10
        for (z in 0 until SZ) for (x in 0 until SX) for (y in 0..top) blocks[(y * SZ + z) * SX + x] = floor
        spawnX = 10; spawnZ = 4
        val cats = if (catIdx >= 0) galleryCats() else emptyList()
        when {
            extra == GAL_FURN -> { furnitureShowcase(top); spawnX = 188; spawnZ = 1 }
            extra == GAL_HOUSE -> { modernHouse(top); spawnX = 195; spawnZ = 37 }
            extra == GAL_TREES -> { plantTestTrees(top); spawnX = 94; spawnZ = 2 }
            catIdx in cats.indices -> layoutCategory(cats[catIdx], secFilter, top, floor, plate)
            else -> {
                labels.add(Label(spawnX + 0.5f, top + 5f, spawnZ + 7f, "GALERIA DE BLOCOS", true))
                labels.add(Label(spawnX + 0.5f, top + 3.4f, spawnZ + 7f, "Toque em CATEGORIAS no alto da tela", false))
            }
        }
        val lk = java.util.BitSet(blocks.size)
        for (i in blocks.indices) if (blocks[i] != B.AIR) lk.set(i)
        locked = lk
        java.util.Arrays.fill(dirty, true)
    }

    /** uma categoria (ou uma seção dela) em faixas: células de 4x4 blocos, 30 por fileira */
    private fun layoutCategory(cat: GalCat, secFilter: Int, top: Int, floor: Int, plate: Int) {
        val colById = HashMap<Int, Int>(); for (d in Blk.defs) colById[d.id] = d.color
        fun blockNamed(n: String) = B.byName[n] ?: floor
        val cell = 4; val cols = 30; val x0 = 8; var zc = 8
        val secs = Gallery.sections(cat.ci)
        val buckets = galleryBuckets(cat)
        val total = if (secFilter in secs.indices) buckets[secFilter].size else cat.ids.size
        // faixa grande da categoria no chão + título
        val stripe = blockNamed(when (cat.ci) { 2 -> "Concreto Verde"; 3 -> "Concreto Vermelho"; 4 -> "Concreto Rosa"; 5 -> "Concreto Azul"; else -> "Concreto Preto" })
        for (z in zc until zc + 2) for (x in x0 - 2..x0 + cols * cell) setTopB(x, z, stripe, top)
        val head = if (secFilter in secs.indices) "${cat.name} - ${secs[secFilter].title}" else cat.name
        labels.add(Label(x0 + cols * cell / 2f, top + 8f, zc + 0.5f, "$head ($total)", true))
        zc += 4
        var secN = 0
        var open = false; var cur = 0   // faixa aberta: seções pequenas dividem a mesma faixa, uma ao lado da outra
        fun closeBand(rows: Int) { zc += 1 + rows * cell + 1; open = false; cur = 0 }
        for ((si, sec) in secs.withIndex()) {
            val gl = buckets[si]
            if (gl.isEmpty()) continue
            if (secFilter in secs.indices && si != secFilter) { secN++; continue }
            val n = gl.size
            val rows = (n + cols - 1) / cols
            if (open && (rows > 1 || cur + n > cols)) closeBand(1)
            val start: Int
            if (rows == 1) { start = cur; cur += n + 1; open = true } else start = 0
            val w = if (rows > 1) cols else n
            // faixa colorida da seção + título
            val accent = blockNamed(Gallery.accentName(secN++))
            for (x in x0 + start * cell until x0 + (start + w) * cell) setTopB(x, zc, accent, top)
            labels.add(Label(x0 + (start + w / 2f) * cell, top + 6.2f, zc + 0.5f, "${sec.title} (${n})", true))
            for ((j, id) in gl.withIndex()) {
                val row = j / cols; val col = start + j % cols
                val cx = x0 + col * cell + 1; val cz = zc + 1 + row * cell + 1
                if (cz + 1 >= SZ - 2) continue
                if ((row + col) % 2 == 0) for (dz in -1..1) for (dx in -1..1) setTopB(cx + dx, cz + dz, plate, top)
                blocks[((top + 1) * SZ + cz) * SX + cx] = id
                labels.add(Label(cx + 0.5f, top + (if (col % 2 == 0) 2.7f else 3.9f), cz + 0.5f, Gallery.shortName(sec, B.names[id]), false, colById[id] ?: 0))
            }
            if (rows > 1) closeBand(rows)
        }
        if (open) closeBand(1)
    }

    /** vitrine da mobília: conjuntos de mesas, sala, escritório e quartos (ficam trancados, mas dá pra sentar) */
    private fun furnitureShowcase(top: Int) {
        val y = top + 1; val bx = 176
        fun id(n: String) = B.byName[n] ?: 0
        fun put(x: Int, z: Int, n: String, yy: Int = y) { val b = id(n); if (b != 0 && x in 0 until SX && z in 0 until SZ) blocks[(yy * SZ + z) * SX + x] = b }
        fun lab(x: Float, z: Float, t: String) { labels.add(Label(x, y + 2.9f, z, t)) }
        fun grupo(x0: Int, z0: Int, w: Int, d: Int, mesa: String, cad: String) {
            for (i in 0 until w) for (j in 0 until d) put(x0 + i, z0 + j, mesa)
            for (i in 0 until w) { put(x0 + i, z0 - 1, cad); put(x0 + i, z0 + d, cad) }
            for (j in 0 until d) { put(x0 - 1, z0 + j, cad); put(x0 + w, z0 + j, cad) }
        }
        labels.add(Label(bx + 12f, top + 7f, 4f, "MOBÍLIA: mesas crescem, camas e sofás emendam, dá pra sentar!", true))
        // mesas
        lab(bx + 2f, 7f, "Mesa de Madeira"); grupo(bx + 1, 9, 2, 2, "Mesa", "Cadeira")
        lab(bx + 9f, 7f, "Mesa Escura"); grupo(bx + 7, 9, 3, 2, "Mesa Escura", "Cadeira Estofada")
        lab(bx + 16f, 7f, "Mesa de Mármore"); grupo(bx + 14, 9, 2, 2, "Mesa de Mármore", "Cadeira Estofada")
        lab(bx + 22f, 7f, "Mesa com Toalha"); grupo(bx + 20, 9, 2, 2, "Mesa com Toalha", "Cadeira")
        // redondas, banco e centro
        lab(bx + 2f, 15f, "Mesa Redonda + Bancos"); put(bx + 2, 17, "Mesa Redonda")
        put(bx + 2, 16, "Banco"); put(bx + 2, 18, "Banco"); put(bx + 1, 17, "Banco"); put(bx + 3, 17, "Banco")
        lab(bx + 9f, 15f, "Redonda de Mármore"); put(bx + 9, 17, "Mesa Redonda de Mármore")
        put(bx + 9, 16, "Cadeira"); put(bx + 9, 18, "Cadeira"); put(bx + 8, 17, "Cadeira"); put(bx + 10, 17, "Cadeira")
        lab(bx + 16f, 15f, "Mesa de Centro"); put(bx + 16, 17, "Mesa de Centro")
        lab(bx + 22f, 15f, "Tapetes"); for (i in 0 until 3) for (j in 0 until 3) { put(bx + 20 + i, 16 + j, "Tapete Vermelho"); put(bx + 24 + i, 16 + j, "Tapete Azul") }
        // sala: TV na cômoda, sofá de costas pra parede, poltronas, abajur e planta
        lab(bx + 3f, 23f, "Sala de estar")
        put(bx + 1, 25, "Cômoda"); put(bx + 2, 25, "Cômoda"); put(bx + 3, 25, "Cômoda")
        put(bx + 2, 25, "Televisão", y + 1); put(bx + 1, 25, "Abajur", y + 1); put(bx + 3, 25, "Vaso de Planta", y + 1)
        for (i in 0 until 5) for (k in 0..2) { val wb = id("Concreto Branco"); if (wb != 0) blocks[((y + k) * SZ + 32) * SX + bx + i] = wb }
        for (i in 1..3) put(bx + i, 31, "Sofá")
        put(bx + 2, 28, "Mesa de Centro"); put(bx + 0, 28, "Poltrona"); put(bx + 4, 28, "Poltrona")
        // escritório
        lab(bx + 12f, 23f, "Escritório")
        for (i in 0 until 3) put(bx + 10 + i, 25, "Escrivaninha")
        put(bx + 11, 25, "Computador", y + 1); put(bx + 12, 25, "Abajur", y + 1); put(bx + 11, 26, "Cadeira de Escritório")
        for (i in 0 until 3) put(bx + 15 + i, 25, "Estante de Livros")
        put(bx + 16, 25, "Vaso de Planta", y + 1); put(bx + 18, 25, "Armário"); put(bx + 19, 25, "Geladeira")
        // quartos: camas de 1, 2 e 3 blocos
        lab(bx + 3f, 36f, "Camas"); put(bx + 1, 38, "Cama")
        put(bx + 4, 38, "Cama"); put(bx + 4, 39, "Cama")
        for (i in 0 until 3) put(bx + 7 + i, 38, "Cama")
        put(bx + 13, 38, "Cama"); put(bx + 13, 39, "Cama"); put(bx + 15, 38, "Cama"); put(bx + 15, 39, "Cama")
        put(bx + 12, 38, "Cômoda"); put(bx + 14, 38, "Abajur"); 
        lab(bx + 22f, 36f, "Cadeiras e poltronas")
        for ((i, n) in listOf("Cadeira", "Cadeira Estofada", "Banco", "Poltrona", "Cadeira de Escritório", "Sofá").withIndex()) put(bx + 19 + i * 2, 38, n)
    }

    /** casa moderna de vitrine (trancada, dá pra entrar e subir a escada): lote murado com jardim e piscina; sala de pé-direito duplo,
     *  jantar, cozinha, cinema, adega, loft, 3 suítes e escritório no mezanino; sacada, varanda de piscina e terraço dos fundos.
     *  Coordenadas relativas: x 0..35 e z 0..22 é a casa, y 0 é o piso do térreo, y -1 o chão. */
    private fun modernHouse(top: Int) {
        val x0 = 178; val z0 = 52; val y0 = top + 1
        fun id(n: String) = B.byName[n] ?: 0
        fun any(vararg n: String): String { for (s in n) if (B.byName[s] != null) return s; return n[0] }
        fun setB(x: Int, y: Int, z: Int, b: Int) { if (x in 0 until SX && z in 0 until SZ && y in 0 until SY) blocks[(y * SZ + z) * SX + x] = b }
        fun putB(rx: Int, ry: Int, rz: Int, b: Int) = setB(x0 + rx, y0 + ry, z0 + rz, b)
        fun put(rx: Int, ry: Int, rz: Int, n: String) { val b = id(n); if (b != 0) putB(rx, ry, rz, b) }
        fun fillB(ax: Int, ay: Int, az: Int, bx: Int, by: Int, bz: Int, b: Int) {
            for (y in minOf(ay, by)..maxOf(ay, by)) for (z in minOf(az, bz)..maxOf(az, bz)) for (x in minOf(ax, bx)..maxOf(ax, bx)) putB(x, y, z, b)
        }
        fun fill(ax: Int, ay: Int, az: Int, bx: Int, by: Int, bz: Int, n: String) { val b = id(n); if (b != 0) fillB(ax, ay, az, bx, by, bz, b) }
        fun hole(ax: Int, ay: Int, az: Int, bx: Int, by: Int, bz: Int) = fillB(ax, ay, az, bx, by, bz, 0)
        val W = "Concreto Branco"; val G = "Concreto Cinza"; val K = "Concreto Preto"; val P = "Janela de Correr Preta"
        val GL = "Vidro"; val GF = "Vidro Fino"; val LJ = "Laje de Concreto Branco"
        val WOOD = "Piso de Madeira"; val SLATE = "Ladrilhos de Ardósia"
        val TILE = any("Azulejo Metrô Azul-claro", "Azulejo Metrô Ciano", "Azulejo Metrô Azul", "Concreto Azul-claro")
        val LAMP = "Lâmpada Branca"
        labels.add(Label(x0 + 17.5f, y0 + 14f, z0 - 17f, "CASA MODERNA", true))

        // ---------- lote: gramado, muro com portão, caminho, cerca-viva ----------
        fillB(-23, -1, -11, 40, -1, 29, B.GRASS)
        fill(-24, 0, -12, 41, 2, -12, W); fill(-24, 0, 30, 41, 2, 30, W); fill(-24, 0, -12, -24, 2, 30, W); fill(41, 0, -12, 41, 2, 30, W)
        for (x in -24..41 step 8) { fill(x, 0, -12, x, 3, -12, K); fill(x, 0, 30, x, 3, 30, K); put(x, 4, -12, LAMP); put(x, 4, 30, LAMP) }
        for (z in -12..30 step 6) { fill(-24, 0, z, -24, 3, z, K); fill(41, 0, z, 41, 3, z, K) }
        fill(-24, 3, -12, 41, 3, -12, LJ); fill(-24, 3, 30, 41, 3, 30, LJ); fill(-24, 3, -12, -24, 3, 30, LJ); fill(41, 3, -12, 41, 3, 30, LJ)
        hole(16, 0, -12, 19, 2, -12); fill(15, 0, -12, 15, 4, -12, K); fill(20, 0, -12, 20, 4, -12, K); fill(16, 3, -12, 19, 3, -12, K)
        put(15, 5, -12, LAMP); put(20, 5, -12, LAMP)
        fill(16, -1, -11, 19, -1, -1, SLATE)
        for (x in -22..39) { if (x in 14..21) continue; fillB(x, 0, -10, x, 1, -10, B.LEAVES) }

        // ---------- piscina com praia rasa, borda branca e deque ----------
        fill(-22, -1, -3, -1, -1, 0, WOOD); fill(-22, -1, 18, -1, -1, 21, WOOD); fill(-11, -1, 1, -1, -1, 17, WOOD)
        fill(-21, -4, 1, -12, -2, 17, TILE)
        fill(-21, -1, 1, -12, -1, 17, W)
        fillB(-20, -3, 2, -13, -1, 16, B.WATER)
        fill(-20, -3, 2, -13, -2, 5, TILE)   // parte rasa: só 1 bloco de água
        for (cx in intArrayOf(-21, -12)) for (cz in intArrayOf(1, 17)) put(cx, 0, cz, LAMP)
        put(-9, 0, 3, "Poltrona"); put(-9, 0, 5, "Poltrona"); put(-9, 0, 4, "Mesa de Centro")
        put(-9, 0, 13, "Poltrona"); put(-9, 0, 15, "Poltrona"); put(-9, 0, 14, "Mesa de Centro")
        put(-5, 0, 9, "Mesa Redonda"); put(-6, 0, 9, "Banco"); put(-4, 0, 9, "Banco"); put(-5, 0, 8, "Banco"); put(-5, 0, 10, "Banco")
        put(-22, 0, 20, "Vaso de Planta"); put(-22, 0, -2, "Vaso de Planta"); put(-2, 0, -2, "Vaso de Planta"); put(-2, 0, 20, "Vaso de Planta")

        // ---------- casa: pisos, casca e lajes ----------
        fill(0, -1, 0, 35, -1, 22, WOOD)
        fill(24, -1, 1, 34, -1, 10, SLATE); fill(16, -1, 0, 19, -1, 3, SLATE)
        fill(0, 0, 0, 35, 8, 0, W); fill(0, 0, 22, 35, 8, 22, W); fill(0, 0, 0, 0, 8, 22, W); fill(35, 0, 0, 35, 8, 22, W)
        fill(13, 4, 0, 35, 4, 22, G); fill(0, 4, 15, 12, 4, 22, G)             // laje do segundo andar (a sala fica vazada: pé-direito duplo)
        fill(14, 4, 1, 34, 4, 21, WOOD); fill(1, 4, 16, 11, 4, 21, WOOD)       // piso de madeira lá em cima
        fill(-2, 9, -5, 37, 9, 24, W)                                           // telhado plano com beiral
        fill(-2, 10, -5, 37, 10, -5, LJ); fill(-2, 10, 24, 37, 10, 24, LJ); fill(-2, 10, -5, -2, 10, 24, LJ); fill(37, 10, -5, 37, 10, 24, LJ)

        // fachada da frente: vidro de piso a teto entre colunas pretas, portão de entrada e porta da sacada
        val cols = intArrayOf(0, 6, 12, 16, 19, 24, 30, 35)
        for (x in 0..35) for (y in 0..8) {
            val b = when {
                x in cols -> id(K)
                x in 17..18 && y <= 2 -> 0
                y == 4 && x >= 13 -> id(G)
                else -> id(GL)
            }
            putB(x, y, 0, b)
        }
        hole(27, 5, 0, 28, 6, 0)
        // lateral oeste da sala: vidro com porta de correr pro deque da piscina
        for (z in 1..13) for (y in 0..8) putB(0, y, z, if (z == 7) id(K) else if (z in 5..6 && y <= 2) 0 else id(GL))
        fill(0, 0, 14, 0, 8, 14, K)
        // janelas nas paredes fechadas
        for (y0w in intArrayOf(1, 6)) {
            fill(0, y0w, 17, 0, y0w + 1, 20, P)
            fill(35, y0w, 3, 35, y0w + 1, 8, P); fill(35, y0w, 16, 35, y0w + 1, 19, P)
            fill(15, y0w, 22, 21, y0w + 1, 22, P); fill(26, y0w, 22, 32, y0w + 1, 22, P)
        }
        fill(3, 1, 22, 9, 2, 22, P); fill(2, 6, 22, 10, 7, 22, P)

        // paredes internas do térreo (portas = vãos)
        fill(13, 0, 11, 35, 3, 11, W); hole(17, 0, 11, 19, 2, 11); hole(29, 0, 11, 30, 2, 11)
        fill(13, 0, 12, 13, 3, 21, W)
        fill(24, 0, 12, 24, 3, 21, W); hole(24, 0, 16, 24, 2, 17)
        fill(0, 0, 15, 12, 3, 15, W); hole(4, 0, 15, 8, 2, 15)
        // andar de cima
        fill(24, 5, 1, 24, 8, 21, W); hole(24, 5, 3, 24, 6, 4); hole(24, 5, 17, 24, 6, 18)
        fill(13, 5, 15, 35, 8, 15, W); hole(17, 5, 15, 18, 6, 15); hole(29, 5, 15, 30, 6, 15)
        fill(13, 5, 16, 13, 8, 21, W); hole(13, 5, 18, 13, 6, 19)
        fill(13, 5, 1, 13, 6, 14, GF)         // guarda-corpo de vidro do loft sobre a sala
        fill(3, 5, 15, 12, 6, 15, GF)         // guarda-corpo do mezanino (vão x1..2 é a chegada da escada)
        // sacada da suíte
        fill(24, 4, -4, 35, 4, -1, G)
        fill(24, 5, -4, 35, 6, -4, GF); fill(24, 5, -4, 24, 6, -1, GF); fill(35, 5, -4, 35, 6, -1, GF)
        fill(24, 5, -4, 24, 8, -4, K); fill(35, 5, -4, 35, 8, -4, K)
        fill(13, 0, -5, 13, 8, -5, K); fill(22, 0, -5, 22, 8, -5, K)   // colunas da cobertura da entrada

        // escada de concreto de 5 degraus, solta na sala, até o mezanino
        val st = Blk.rotIds[id("Escada de Concreto Branco")]?.get(0) ?: 0
        for (sx in 1..2) for (s in 0..4) {
            for (k in 0 until s) put(sx, k, 10 + s, K)
            if (st != 0) putB(sx, s, 10 + s, st)
        }

        // ---------- mobília: térreo ----------
        val rug = any("Tapete Cinza", "Tapete Azul")
        // sala
        for (x in 5..9) for (z in 4..8) put(x, 0, z, rug)
        for (x in 5..9) put(x, 0, 10, "Sofá")
        put(7, 0, 6, "Mesa de Centro"); put(4, 0, 7, "Poltrona"); put(10, 0, 7, "Poltrona")
        for (x in 6..8) put(x, 0, 2, "Cômoda")
        put(7, 1, 2, "Televisão"); put(6, 1, 2, "Abajur"); put(8, 1, 2, "Vaso de Planta")
        for (x in 9..11) put(x, 0, 13, "Estante de Livros")
        put(1, 0, 1, "Vaso de Planta"); put(11, 0, 1, "Vaso de Planta"); put(1, 0, 13, "Vaso de Planta"); put(3, 0, 13, "Vaso de Planta")
        for (p in listOf(5 to 5, 8 to 5, 6 to 9)) put(p.first, 8, p.second, LAMP)
        // jantar
        for (x in 16..21) { put(x, 0, 5, "Mesa de Mármore"); put(x, 0, 6, "Mesa de Mármore"); put(x, 0, 4, "Cadeira Estofada"); put(x, 0, 7, "Cadeira Estofada") }
        put(15, 0, 5, "Cadeira Estofada"); put(22, 0, 6, "Cadeira Estofada")
        put(17, 3, 5, LAMP); put(20, 3, 6, LAMP); put(14, 0, 1, "Vaso de Planta"); put(14, 0, 10, "Vaso de Planta")
        // cozinha: armários na parede, geladeira, ilha com bancos
        for (x in 25..28) put(x, 0, 10, "Armário")
        put(29, 0, 10, "Geladeira"); put(30, 0, 10, "Geladeira"); for (x in 31..33) put(x, 0, 10, "Armário")
        for (x in 26..31) { put(x, 0, 5, "Mesa de Mármore"); put(x, 0, 6, "Mesa de Mármore"); put(x, 0, 7, "Banco") }
        put(27, 3, 5, LAMP); put(30, 3, 6, LAMP); put(34, 0, 2, "Vaso de Planta")
        // cinema: tapete, TV, sofá e poltronas
        for (x in 16..20) for (z in 13..17) put(x, 0, z, any("Tapete Vermelho", rug))
        for (x in 17..19) put(x, 0, 12, "Cômoda")
        put(18, 1, 12, "Televisão"); put(17, 1, 12, "Abajur"); put(19, 1, 12, "Abajur")
        for (x in 16..20) put(x, 0, 20, "Sofá")
        put(15, 0, 17, "Poltrona"); put(21, 0, 17, "Poltrona"); put(18, 0, 15, "Mesa de Centro"); put(18, 3, 17, LAMP)
        // quarto de hóspedes térreo
        for (x in 27..29) put(x, 0, 21, "Cama")
        put(26, 0, 21, "Cômoda"); put(30, 0, 21, "Cômoda"); put(26, 1, 21, "Abajur"); put(30, 1, 21, "Abajur")
        for (x in 27..29) for (z in 17..19) put(x, 0, z, "Tapete Azul")
        put(33, 0, 21, "Armário"); put(34, 0, 21, "Armário"); put(33, 0, 13, "Vaso de Planta"); put(28, 3, 18, LAMP)
        // adega: barris, mesa redonda e estante
        for (x in 2..8) put(x, 0, 21, "Barril")
        for (x in 9..11) put(x, 0, 21, "Estante de Livros")
        put(6, 0, 18, "Mesa Redonda"); put(5, 0, 18, "Banco"); put(7, 0, 18, "Banco"); put(6, 0, 17, "Banco"); put(6, 0, 19, "Banco"); put(6, 3, 18, LAMP)

        // ---------- mobília: andar de cima ----------
        // loft: sala de TV aberta pra sala de baixo
        for (x in 15..20) for (z in 6..10) put(x, 5, z, "Tapete Azul")
        for (x in 17..19) put(x, 5, 14, "Cômoda")
        put(18, 6, 14, "Televisão"); put(17, 6, 14, "Abajur"); put(19, 6, 14, "Vaso de Planta")
        for (x in 16..20) put(x, 5, 9, "Sofá")
        put(18, 5, 11, "Mesa de Centro"); put(15, 5, 12, "Poltrona"); put(21, 5, 12, "Poltrona")
        for (x in 21..23) put(x, 5, 1, "Estante de Livros"); put(14, 5, 1, "Vaso de Planta"); put(14, 5, 14, "Vaso de Planta")
        put(18, 8, 8, LAMP)
        // suíte master: cama grande, cômodas, escrivaninha, poltrona e tapete
        for (x in 29..31) { put(x, 5, 14, "Cama"); put(x, 5, 13, "Cama") }
        put(28, 5, 14, "Cômoda"); put(32, 5, 14, "Cômoda"); put(28, 6, 14, "Abajur"); put(32, 6, 14, "Abajur")
        for (x in 28..32) for (z in 9..12) put(x, 5, z, any("Tapete Vermelho", rug))
        for (x in 26..28) put(x, 5, 2, "Escrivaninha")
        put(27, 6, 2, "Computador"); put(27, 5, 3, "Cadeira de Escritório")
        put(25, 5, 6, "Poltrona"); put(25, 5, 8, "Poltrona"); put(25, 5, 7, "Mesa de Centro")
        for (z in 8..11) put(34, 5, z, "Armário")
        put(30, 8, 10, LAMP); put(26, 8, 4, LAMP)
        put(25, 5, -1, "Vaso de Planta"); put(34, 5, -1, "Vaso de Planta")
        put(29, 5, -2, "Mesa Redonda"); put(28, 5, -2, "Cadeira"); put(30, 5, -2, "Cadeira")
        // suíte 2
        for (x in 27..29) { put(x, 5, 21, "Cama"); put(x, 5, 20, "Cama") }
        put(26, 5, 21, "Cômoda"); put(30, 5, 21, "Cômoda"); put(26, 6, 21, "Abajur"); put(30, 6, 21, "Abajur")
        for (x in 32..33) put(x, 5, 21, "Escrivaninha")
        put(32, 6, 21, "Computador"); put(32, 5, 20, "Cadeira de Escritório"); put(28, 8, 18, LAMP)
        // quarto 3
        for (x in 15..17) { put(x, 5, 21, "Cama"); put(x, 5, 20, "Cama") }
        put(14, 5, 21, "Cômoda"); put(18, 5, 21, "Cômoda"); put(18, 6, 21, "Abajur")
        for (x in 20..22) put(x, 5, 21, "Armário"); put(20, 5, 17, "Vaso de Planta"); put(17, 8, 18, LAMP)
        // escritório no mezanino
        for (x in 3..8) put(x, 5, 21, "Escrivaninha")
        put(4, 6, 21, "Computador"); put(7, 6, 21, "Computador"); put(4, 5, 20, "Cadeira de Escritório"); put(7, 5, 20, "Cadeira de Escritório")
        for (x in 9..11) put(x, 5, 21, "Estante de Livros")
        for (z in 17..19) put(11, 5, z, "Estante de Livros")
        put(2, 5, 17, "Sofá"); put(2, 5, 18, "Sofá"); put(3, 5, 18, "Mesa de Centro"); put(5, 8, 18, LAMP)

        // ---------- fundos: terraço, mesa de jantar ao ar livre e jardim ----------
        fill(2, -1, 23, 34, -1, 27, SLATE)
        for (x in 14..17) { put(x, 0, 25, "Mesa de Madeira"); put(x, 0, 26, "Mesa de Madeira"); put(x, 0, 24, "Cadeira"); put(x, 0, 27, "Cadeira") }
        put(13, 0, 25, "Cadeira"); put(18, 0, 26, "Cadeira")
        put(26, 0, 25, "Mesa Redonda"); put(25, 0, 25, "Poltrona"); put(27, 0, 25, "Poltrona")
        for (x in intArrayOf(3, 10, 22, 33)) put(x, 0, 28, "Vaso de Planta")
        for (x in intArrayOf(2, 34)) for (zz in intArrayOf(23, 27)) put(x, 0, zz, LAMP)
        // árvores e arbustos
        val rnd = Random(99L)
        for ((tx, tz, tp) in listOf(Triple(-17, -7, 1), Triple(30, -7, 2), Triple(38, 10, 1), Triple(-18, 26, 2), Triple(38, 26, 1), Triple(10, 28, 2), Triple(-6, 25, 1)))
            plantTree(x0 + tx, top, z0 + tz, tp, rnd)
    }

    /** galeria de árvores: bem afastada dos blocos (x >= 90), uma fileira por espécie com 2 árvores, sobre um gramado */
    private fun plantTestTrees(top: Int) {
        val rows = arrayOf(
            arrayOf("CARVALHO", "", ""), arrayOf("ABETO", "Tronco de Abeto", "Folhas de Abeto"), arrayOf("BÉTULA", "Tronco de Bétula", "Folhas de Bétula"),
            arrayOf("SELVA", "Tronco de Selva", "Folhas de Selva"), arrayOf("ACÁCIA", "Tronco de Acácia", "Folhas de Acácia"),
            arrayOf("CARVALHO ESCURO", "Tronco de Carvalho Escuro", "Folhas de Carvalho Escuro"), arrayOf("MANGUE", "Tronco de Mangue", "Folhas de Mangue"),
            arrayOf("CEREJEIRA", "Tronco de Cerejeira", "Folhas de Cerejeira"), arrayOf("OUTONO", "Tronco de Abeto", "Folhas de Outono"),
            arrayOf("AMARELA", "Tronco de Bétula", "Folhas Amarelas"), arrayOf("BAMBU", "Tronco de Selva", "Folhas de Bambu"),
            arrayOf("CARMESIM", "Caule Carmesim", "Verruga Carmesim"), arrayOf("DISTORCIDO", "Caule Distorcido", "Verruga Distorcido"))
        val gx0 = 90; val gx1 = 170; val gz0 = 6; val step = 18
        val gz1 = Math.min(SZ - 4, gz0 + rows.size * step + 4)
        for (z in gz0 until gz1) for (x in gx0 until Math.min(SX - 2, gx1)) blocks[(top * SZ + z) * SX + x] = B.GRASS
        val rnd = Random(4242L)
        for ((i, r) in rows.withIndex()) {
            val z = gz0 + 9 + i * step
            if (z + 6 >= SZ) break
            labels.add(Label(gx0 + 4f, top + 6f, z + 0.5f, r[0], true))
            for (k in 0 until 2) {
                val x = gx0 + 22 + k * 24
                woodId = if (r[1].isEmpty()) B.WOOD else (B.byName[r[1]] ?: B.WOOD)
                leafId = if (r[2].isEmpty()) B.LEAVES else (B.byName[r[2]] ?: B.LEAVES)
                val type = if (r[0] == "ABETO") 4 else if (k == 0) 1 else 2
                if (r[0] == "BAMBU") plantBamboo(x, top, z, rnd) else plantTree(x, top, z, type, rnd)
            }
        }
        woodId = B.WOOD; leafId = B.LEAVES
    }

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
                    blocks[(y * SZ + z) * SX + x] = id
                }
                for (y in hh + 1..WATER_Y) blocks[(y * SZ + z) * SX + x] = B.WATER
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
            plantSpecies(x, y, z, type, rnd)
        }
        val cols = intArrayOf(B.PINK, B.BLUE, B.YELLOW, B.BRICK)
        for (i in 0 until 4) {
            val x = SX / 2 + 3 + i * 2; val z = SZ / 2 + 4
            val y = surfaceY(x, z)
            for (t in 0..2) blocks[((y + t) * SZ + z) * SX + x] = cols[i]
            blocks[((y + 3) * SZ + z) * SX + x] = B.LANTERN
        }
        scatterMinerals(seed)
        java.util.Arrays.fill(dirty, true)
    }

    /** veios de minério e manchas de granito, diorito, andesito, cascalho e argila escondidos na pedra (só em mundos novos) */
    private fun scatterMinerals(seed: Int) {
        val rnd = Random(seed.toLong() * 31L + 7L)
        val scale = (SX * SZ) / 65536f
        fun vein(name: String, count: Int, size: Int, minY: Int, maxY: Int) {
            val id = B.byName[name] ?: return
            val n = Math.max(2, (count * scale).toInt())
            for (i in 0 until n) {
                var x = rnd.nextInt(SX); var y = minY + rnd.nextInt(maxY - minY + 1); var z = rnd.nextInt(SZ)
                for (k in 0 until size) {
                    if (get(x, y, z) == B.STONE) set(x, y, z, id)
                    x += rnd.nextInt(3) - 1; y += rnd.nextInt(3) - 1; z += rnd.nextInt(3) - 1
                }
            }
        }
        vein("Minério de Carvão", 260, 10, 1, 7)
        vein("Minério de Ferro", 200, 7, 1, 7)
        vein("Minério de Cobre", 140, 8, 1, 7)
        vein("Minério de Ouro", 40, 5, 1, 4)
        vein("Minério de Redstone", 60, 6, 1, 4)
        vein("Minério de Lápis", 30, 5, 1, 5)
        vein("Minério de Diamante", 12, 4, 1, 3)
        vein("Minério de Esmeralda", 8, 2, 1, 5)
        vein("Granito", 70, 40, 1, 7)
        vein("Diorito", 70, 40, 1, 7)
        vein("Andesito", 70, 40, 1, 7)
        vein("Tufo", 40, 30, 1, 5)
        vein("Cascalho", 50, 30, 2, 7)
        vein("Argila", 40, 25, 3, 7)
        vein("Ardósia", 40, 40, 1, 3)
    }

    // ================= ÁRVORES =================
    private fun air(x: Int, y: Int, z: Int) = x in 0 until SX && z in 0 until SZ && y in 0 until SY && get(x, y, z) == B.AIR

    /** copa irregular: elipsoide de folhas com bordas ruidosas */
    private fun blob(cx: Int, cy: Int, cz: Int, rx: Float, ry: Float, rz: Float, rnd: Random) {
        val ix = Math.ceil(rx.toDouble()).toInt() + 1; val iy = Math.ceil(ry.toDouble()).toInt() + 1; val iz = Math.ceil(rz.toDouble()).toInt() + 1
        for (dx in -ix..ix) for (dy in -iy..iy) for (dz in -iz..iz) {
            val d = (dx * dx) / (rx * rx) + (dy * dy) / (ry * ry) + (dz * dz) / (rz * rz)
            val thr = 0.82f + rnd.nextFloat() * 0.34f
            if (d <= thr && air(cx + dx, cy + dy, cz + dz)) set(cx + dx, cy + dy, cz + dz, leafId)
        }
    }

    /** tronco e folha da árvore sendo plantada (cada espécie tem os seus) */
    private var woodId = B.WOOD; private var leafId = B.LEAVES
    private fun wood(x: Int, y: Int, z: Int) {
        val g = get(x, y, z)
        if (g == B.AIR || B.leafy(g)) set(x, y, z, woodId)
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
            for (yy in Math.max(1, cy - 4)..cy + h) if (B.leafy(get(x, yy, z))) { low = yy; break }
            if (low < 0) continue
            if (rnd.nextInt(4) == 0 && air(x, low - 1, z)) {
                set(x, low - 1, z, leafId)
                if (rnd.nextInt(3) == 0 && air(x, low - 2, z)) set(x, low - 2, z, leafId)
            }
        }
    }

    /** disco de folhas com borda irregular (usado no pinheiro) */
    private fun disc(cx: Int, y: Int, cz: Int, rad: Float, rnd: Random) {
        val ir = Math.ceil(rad.toDouble()).toInt() + 1
        for (dx in -ir..ir) for (dz in -ir..ir) {
            val d = sqrt((dx * dx + dz * dz).toFloat())
            if (d <= rad + (rnd.nextFloat() - 0.5f) * 0.8f && air(cx + dx, y, cz + dz)) set(cx + dx, y, cz + dz, leafId)
        }
    }

    /** touceira de bambu: várias hastes finas de alturas diferentes (sem blocos de folha; as folhinhas saem do colmo) */
    private fun plantBamboo(x: Int, y: Int, z: Int, rnd: Random) {
        val id = B.byName["Colmo de Bambu"] ?: return
        val n = 6 + rnd.nextInt(4)
        for (k in 0 until n) {
            val bx = x + rnd.nextInt(5) - 2; val bz = z + rnd.nextInt(5) - 2
            if (get(bx, y, bz) != B.GRASS) continue
            val hgt = 8 + rnd.nextInt(8)
            for (t in 1..hgt) if (air(bx, y + t, bz)) set(bx, y + t, bz, id) else break
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
                    if (rad > 0.2f) disc(x, ly, z, rad, rnd) else if (air(x, ly, z)) set(x, ly, z, leafId)
                }
                if (air(x, y + th + 2, z)) set(x, y + th + 2, z, leafId)
                if (air(x, y + th + 3, z)) set(x, y + th + 3, z, leafId)
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

    /** manchas de floresta por espécie: cada uma com o seu tronco e a sua folha */
    private val speciesNames = arrayOf(
        arrayOf("", ""), arrayOf("Tronco de Abeto", "Folhas de Abeto"), arrayOf("Tronco de Bétula", "Folhas de Bétula"),
        arrayOf("Tronco de Selva", "Folhas de Selva"), arrayOf("Tronco de Acácia", "Folhas de Acácia"),
        arrayOf("Tronco de Carvalho Escuro", "Folhas de Carvalho Escuro"), arrayOf("Tronco de Mangue", "Folhas de Mangue"),
        arrayOf("Tronco de Cerejeira", "Folhas de Cerejeira"), arrayOf("Tronco de Abeto", "Folhas de Outono"))
    private fun plantSpecies(x: Int, y: Int, z: Int, type: Int, rnd: Random) {
        val r = hash(x / 22, z / 22, 777)
        val k = if (type == 4) 1 else if (r < 0.30f) 0 else if (r < 0.42f) 1 else if (r < 0.54f) 2 else if (r < 0.64f) 3 else if (r < 0.72f) 4 else if (r < 0.80f) 5 else if (r < 0.88f) 6 else if (r < 0.95f) 7 else 8
        if (k != 0) {
            val w = B.byName[speciesNames[k][0]] ?: B.WOOD; val l = B.byName[speciesNames[k][1]] ?: B.LEAVES
            woodId = w; leafId = l
        }
        plantTree(x, y, z, type, rnd)
        woodId = B.WOOD; leafId = B.LEAVES
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
            plantSpecies(x, y, z, type, rnd)
        }
    }

    /** mundos antigos: apaga as árvores naturais (troncos em grama e folhas ligadas) e planta as novas */
    fun replantTrees(seed: Int) {
        val seen = BooleanArray(blocks.size)
        val q = java.util.ArrayDeque<IntArray>()
        for (x in 0 until SX) for (z in 0 until SZ) for (y in 1 until SY) {
            if (!B.log(get(x, y, z)) || get(x, y - 1, z) != B.GRASS) continue
            q.clear(); q.add(intArrayOf(x, y, z)); seen[(y * SZ + z) * SX + x] = true
            while (q.isNotEmpty()) {
                val c = q.poll()
                set(c[0], c[1], c[2], B.AIR)
                for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                    val nx = c[0] + dx; val ny = c[1] + dy; val nz = c[2] + dz
                    if (nx < 0 || nx >= SX || nz < 0 || nz >= SZ || ny < 0 || ny >= SY) continue
                    if (Math.abs(nx - x) > 9 || Math.abs(nz - z) > 9 || ny - y > 16) continue
                    val id = get(nx, ny, nz)
                    if (!B.log(id) && !B.leafy(id)) continue
                    val k = (ny * SZ + nz) * SX + nx
                    if (seen[k]) continue
                    seen[k] = true; q.add(intArrayOf(nx, ny, nz))
                }
            }
        }
        plantForest(Random(seed.toLong() + 99), 420) { x, z -> surfaceY(x, z) - 1 }
    }

    /** arquivo novo: cabeçalho + 4 bytes por bloco; arquivo antigo (1 byte por bloco) ainda abre e é convertido */
    fun save(f: File) {
        try {
            val tvs = HashMap(tvSize)
            val extra = if (tvs.isEmpty()) 0 else 8 + tvs.size * 8
            val bb = java.nio.ByteBuffer.allocate(4 + blocks.size * 4 + extra)
            bb.putInt(SAVE_MAGIC); bb.asIntBuffer().put(blocks)
            if (extra > 0) {
                bb.position(4 + blocks.size * 4)
                bb.putInt(TV_MAGIC); bb.putInt(tvs.size)
                for ((k, v) in tvs) { bb.putInt(k); bb.putInt(v) }
            }
            GZIPOutputStream(f.outputStream()).use { it.write(bb.array()) }
        } catch (_: Exception) {}
    }

    fun load(f: File): Boolean {
        return try {
            if (!f.exists()) return false
            val data = GZIPInputStream(f.inputStream()).use { it.readBytes() }
            if (data.size == blocks.size) {
                for (i in data.indices) blocks[i] = data[i].toInt() and 0xFF
            } else if (data.size >= 4 + blocks.size * 4) {
                val bb = java.nio.ByteBuffer.wrap(data)
                if (bb.getInt() != SAVE_MAGIC) return false
                bb.asIntBuffer().get(blocks)
                tvSize.clear()
                val tp = 4 + blocks.size * 4
                if (data.size >= tp + 8) {   // trailer opcional: tamanhos das TVs (saves antigos não têm)
                    bb.position(tp)
                    if (bb.getInt() == TV_MAGIC) {
                        val n = bb.getInt(); var i = 0
                        while (i < n && bb.remaining() >= 8) { val k = bb.getInt(); val lv = bb.getInt(); if (lv in 1..3) tvSize[k] = lv; i++ }
                    }
                }
            } else return false
            val al = Blk.alias
            for (i in blocks.indices) { val b = blocks[i]; if (b in 14..16383 && al[b] != 0) blocks[i] = al[b] }
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
                    (8 + ou + 0.01f + 0.48f * fu) / Atlas.NT, (ov + 0.01f + 0.48f * fv) / Atlas.ROWS)
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


    /** oclusão do canto (mesma conta das faces lisas) */
    private fun cornerAo(c: IntArray, a: Int, s: Int, u: Int, v: Int, cu: Int, cv: Int): Float {
        val du = if (cu == 1) 1 else -1; val dv = if (cv == 1) 1 else -1
        val bx = IntArray(3); bx[0] = c[0]; bx[1] = c[1]; bx[2] = c[2]; bx[a] += s
        val s1 = bx.clone(); s1[u] += du
        val s2 = bx.clone(); s2[v] += dv
        val cc = s1.clone(); cc[v] += dv
        val a1 = if (solid(s1[0], s1[1], s1[2])) 1 else 0
        val a2 = if (solid(s2[0], s2[1], s2[2])) 1 else 0
        val a3 = if (solid(cc[0], cc[1], cc[2])) 1 else 0
        val lvl = if (a1 == 1 && a2 == 1) 0 else 3 - (a1 + a2 + a3)
        return AOB[lvl]
    }

    /** face com relevo de verdade: grade de peças (8x8 no automático, 16x16 nos mapas escritos à mão), cada uma sai ou entra até 2 degraus (Relief.map),
     *  com paredinhas entre alturas diferentes. Peças vizinhas do mesmo nível viram um retângulo só, e as frestas que chegam na borda do bloco ganham parede
     *  (senão dava pra ver o miolo do vizinho por ela). */
    private fun reliefFace(o: MeshBuf, c: IntArray, a: Int, s: Int, tile: Int, shade: Float, ao: FloatArray) {
        val rm = Relief.map(tile)
        val G = rm.g
        val map = rm.lv
        val u = (a + 1) % 3; val v = (a + 2) % 3
        val lv = IntArray(G * G)
        for (iv in 0 until G) for (iu in 0 until G) {
            val ti: Int; val tj: Int
            when (a) { 0 -> { ti = iv; tj = G - 1 - iu }; 2 -> { ti = iu; tj = G - 1 - iv }; else -> { ti = iu; tj = iv } }
            lv[iv * G + iu] = map[tj * G + ti].toInt()
        }
        val pos = FloatArray(3)
        fun emit(fu: Float, fv: Float, h: Float, sh: Float, tu: Float, tv: Float) {
            pos[0] = c[0].toFloat(); pos[1] = c[1].toFloat(); pos[2] = c[2].toFloat()
            pos[a] += (if (s > 0) 1f else 0f) + s * h
            pos[u] += fu; pos[v] += fv
            val k = sh * ((1f - fu) * (1f - fv) * ao[0] + fu * (1f - fv) * ao[1] + fu * fv * ao[2] + (1f - fu) * fv * ao[3])
            o.vert(pos[0], pos[1], pos[2], k, k, k, Atlas.u(tile, tu), Atlas.v(tile, tv))
        }
        fun tx(fu: Float, fv: Float): Float = when (a) { 0 -> fv; else -> fu }
        fun ty(fu: Float, fv: Float): Float = when (a) { 0 -> 1f - fu; 2 -> 1f - fv; else -> fv }
        fun quadV(f0u: Float, f0v: Float, h0: Float, f1u: Float, f1v: Float, h1: Float, f2u: Float, f2v: Float, h2: Float, f3u: Float, f3v: Float, h3: Float, sh: Float, both: Boolean) {
            emit(f0u, f0v, h0, sh, tx(f0u, f0v), ty(f0u, f0v)); emit(f1u, f1v, h1, sh, tx(f1u, f1v), ty(f1u, f1v))
            emit(f2u, f2v, h2, sh, tx(f2u, f2v), ty(f2u, f2v)); emit(f3u, f3v, h3, sh, tx(f3u, f3v), ty(f3u, f3v))
            if (both) { o.tri(0, 1, 2); o.tri(0, 2, 3); o.tri(0, 2, 1); o.tri(0, 3, 2) }
            else if (s > 0) { o.tri(0, 1, 2); o.tri(0, 2, 3) } else { o.tri(0, 2, 1); o.tri(0, 3, 2) }
            o.vc += 4
        }
        val inv = 1f / G
        val step = Relief.step(tile)
        fun lvOf(iu: Int, iv: Int): Int = if (iu < 0 || iv < 0 || iu >= G || iv >= G) 0 else lv[iv * G + iu]
        // topos: retângulos de peças com o mesmo nível
        val used = BooleanArray(G * G)
        for (iv in 0 until G) for (iu in 0 until G) {
            if (used[iv * G + iu]) continue
            val l = lv[iv * G + iu]
            var w = 1
            while (iu + w < G && !used[iv * G + iu + w] && lv[iv * G + iu + w] == l) w++
            var hh = 1
            while (iv + hh < G) {
                var ok = true
                for (k in 0 until w) if (used[(iv + hh) * G + iu + k] || lv[(iv + hh) * G + iu + k] != l) { ok = false; break }
                if (!ok) break
                hh++
            }
            for (yy in iv until iv + hh) for (xx in iu until iu + w) used[yy * G + xx] = true
            val h = l * step
            val sh = shade * Relief.tone(l)
            val fu0 = iu * inv; val fu1 = (iu + w) * inv; val fv0 = iv * inv; val fv1 = (iv + hh) * inv
            quadV(fu0, fv0, h, fu1, fv0, h, fu1, fv1, h, fu0, fv1, h, sh, false)
        }
        // paredinhas: da peça mais alta pra mais baixa, mescladas ao longo da aresta. Na borda do bloco o "vizinho" é o nível 0 (frestas também fecham ali).
        fun wallTone(bot: Int): Float = if (bot < 0) 0.64f else 0.82f
        for (side in 0..1) {      // planos de u constante (esquerda/direita)
            val du = if (side == 0) -1 else 1
            for (iu in 0 until G) {
                val outside = iu + du < 0 || iu + du >= G
                var iv = 0
                while (iv < G) {
                    val l = lvOf(iu, iv); val n = lvOf(iu + du, iv)
                    if (!(n < l || (outside && n > l))) { iv++; continue }
                    var e = iv
                    while (e + 1 < G && lvOf(iu, e + 1) == l && lvOf(iu + du, e + 1) == n) e++
                    val fu = (if (side == 0) iu else iu + 1) * inv + (if (!outside) 0f else if (side == 0) 0.004f else -0.004f)   // parede na borda do bloco: recuada, senão briga (z-fighting) com a do vizinho e fica piscando
                    val top = maxOf(l, n) * step; val bot = minOf(l, n) * step
                    val wsh = shade * wallTone(minOf(l, n)) * (if (side == 1) 0.92f else 1f)
                    quadV(fu, iv * inv, top, fu, (e + 1) * inv, top, fu, (e + 1) * inv, bot, fu, iv * inv, bot, wsh, true)
                    iv = e + 1
                }
            }
        }
        for (side in 0..1) {      // planos de v constante (frente/trás)
            val dv = if (side == 0) -1 else 1
            for (iv in 0 until G) {
                val outside = iv + dv < 0 || iv + dv >= G
                var iu = 0
                while (iu < G) {
                    val l = lvOf(iu, iv); val n = lvOf(iu, iv + dv)
                    if (!(n < l || (outside && n > l))) { iu++; continue }
                    var e = iu
                    while (e + 1 < G && lvOf(e + 1, iv) == l && lvOf(e + 1, iv + dv) == n) e++
                    val fv = (if (side == 0) iv else iv + 1) * inv + (if (!outside) 0f else if (side == 0) 0.004f else -0.004f)
                    val top = maxOf(l, n) * step; val bot = minOf(l, n) * step
                    val wsh = shade * wallTone(minOf(l, n)) * (if (side == 1) 0.88f else 0.96f)
                    quadV(iu * inv, fv, top, (e + 1) * inv, fv, top, (e + 1) * inv, fv, bot, iu * inv, fv, bot, wsh, true)
                    iu = e + 1
                }
            }
        }
    }

    fun buildChunk(cx: Int, cz: Int, o: MeshBuf, w: MeshBuf) {
        o.clear(); w.clear()
        val c = IntArray(3); val p = FloatArray(3); val col = FloatArray(3); val ao = FloatArray(4); val lop = BooleanArray(6); val leafPos = ArrayList<Int>()
        for (y in 0 until SY) for (z in cz * CH until cz * CH + CH) for (x in cx * CH until cx * CH + CH) {
            val id = get(x, y, z)
            if (id == B.AIR) continue
            if (hidden.isNotEmpty() && hidden.contains((y * SZ + z) * SX + x)) continue
            val isW = id == B.WATER
            val isG = !isW && B.glass(id)
            val lowered = isW && get(x, y + 1, z) == B.AIR
            val vr = if (B.cat[id and 16383] == Blk.C_BUILD) 1f else 0.94f + 0.06f * hash(x, z, y)   // Construção: tom igual em todos os blocos, pra não aparecer a divisão
            c[0] = x; c[1] = y; c[2] = z
            if (B.thin(id)) { stalk(o, x, y, z, id, vr); continue }
            val shp = B.shape(id); if (shp != 0) { Shapes.emit(if (isG) w else o, this, x, y, z, id, shp, vr); continue }
            if (B.leafy(id)) {
                val lt = B.tSide[id]
                if (!leafOpen(x, y, z)) {
                    // segunda camada: folha escondida mas vizinha de uma folha exposta desenha só o cubo interno (aparece pelos buraquinhos)
                    var near = false
                    for (f in 0 until 6) {
                        val aa = f shr 1; val ss = if ((f and 1) == 0) 1 else -1
                        val qx = x + if (aa == 0) ss else 0; val qy = y + if (aa == 1) ss else 0; val qz = z + if (aa == 2) ss else 0
                        if (B.leafy(get(qx, qy, qz)) && leafOpen(qx, qy, qz)) { near = true; break }
                    }
                    if (near) { leafFaces(o, x, y, z, 0.16f, 0.84f, false, vr, 0.72f, lt); leafPos.add(-((y * SZ + z) * SX + x) - 1) }
                    continue
                }
                leafFaces(o, x, y, z, 0f, 1f, true, vr, 1f, lt)        // casca externa (só faces expostas)
                leafFaces(o, x, y, z, 0.16f, 0.84f, false, vr, 0.72f, lt)   // cubo interno: dá profundidade pelos buracos
                leafPos.add((y * SZ + z) * SX + x)   // folhas 3D e frutinhas saem num passe só no fim (de cima pra baixo)
                continue
            }
            if ((id == B.GRASS || id == mossId) && get(x, y + 1, z) == B.AIR) {
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
                        o.vert(ox - ax, y + 0.98f, oz - az, bot, bot, bot, u0, 0.99f / Atlas.ROWS)
                        o.vert(ox + ax, y + 0.98f, oz + az, bot, bot, bot, u1, 0.99f / Atlas.ROWS)
                        o.vert(ox + ax, y + 0.98f + hh, oz + az, top, top, top, u1, 0.01f / Atlas.ROWS)
                        o.vert(ox - ax, y + 0.98f + hh, oz - az, top, top, top, u0, 0.01f / Atlas.ROWS)
                        o.tri(0, 1, 2); o.tri(0, 2, 3); o.tri(0, 2, 1); o.tri(0, 3, 2); o.vc += 4
                    }
                }
            }
            for (face in 0 until 6) {
                val a = face shr 1; val s = if ((face and 1) == 0) 1 else -1
                val nx = x + if (a == 0) s else 0; val ny = y + if (a == 1) s else 0; val nz = z + if (a == 2) s else 0
                var nid = get(nx, ny, nz)
                if (nid != B.AIR && hidden.isNotEmpty() && hidden.contains((ny * SZ + nz) * SX + nx)) nid = B.AIR   // bloco esculpido: o vizinho desenha a face voltada pra ele
                if (isW) { if (nid != B.AIR && !B.glass(nid)) continue }
                else if (isG) { if (nid == id || !B.see(nid)) continue }
                else if (!B.see(nid)) continue   // folha e vidro deixam ver o que está atrás: a face do vizinho precisa ser desenhada
                val u = (a + 1) % 3; val v = (a + 2) % 3
                val shade = when { B.light(id) -> 1f; id == B.LEAVES -> (if (a == 1) (if (s > 0) 1f else 0.8f) else if (a == 0) 0.92f else 0.86f); a == 1 -> if (s > 0) 1f else 0.55f; a == 0 -> 0.82f; else -> 0.7f }
                val buf = if (isW || isG) w else o
                if (!isW && !isG && B.relief[id and 16383]) {
                    val rt = if (a == 1) (if (s > 0) Blk.pickTile(B.tTop[id], hash(x, z, y * 7 + 11)) else B.tBot[id]) else Blk.pickTile(B.tSide[id], hash(x + a * 5, z + s, y * 7 + 13))
                    for (q in 0 until 4) ao[q] = cornerAo(c, a, s, u, v, QU[q], QV[q])
                    reliefFace(o, c, a, s, rt, shade * vr, ao)
                    continue
                }
                for (q in 0 until 4) {
                    val cu = QU[q]; val cv = QV[q]
                    p[0] = c[0].toFloat(); p[1] = c[1].toFloat(); p[2] = c[2].toFloat()
                    p[a] += if (s > 0) 1f else 0f; p[u] += cu.toFloat(); p[v] += cv.toFloat()
                    if (lowered && p[1] > y) p[1] = y + 0.88f
                    var aob = 1f
                    if (!isW && !isG && !B.light(id) && id != B.LEAVES) {
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
                    val tile = if (a == 1) (if (s > 0) Blk.pickTile(B.tTop[id], hash(x, z, y * 7 + 11)) else B.tBot[id]) else Blk.pickTile(B.tSide[id], hash(x + a * 5, z + s, y * 7 + 13))
                    var tu = cu.toFloat(); var tv = cv.toFloat()
                    if (a == 0) { tu = cv.toFloat(); tv = 1f - cu } else if (a == 2) { tv = 1f - cv }
                    val gr = shade * vr * aob
                    var cg = gr
                    if (isW) {   // canal G da água = profundidade suave (média das 4 colunas ao redor do vértice)
                        val kx = p[0].toInt(); val kz = p[2].toInt()
                        cg = ((colDepth(kx - 1, kz - 1) + colDepth(kx, kz - 1) + colDepth(kx - 1, kz) + colDepth(kx, kz)) / 20f).coerceIn(0f, 1f)
                    }
                    buf.vert(p[0], p[1], p[2], gr, cg, gr, Atlas.u(tile, tu), Atlas.v(tile, tv))
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
        // folhas 3D da espécie + frutinhas, de cima pra baixo; quanto menos orçamento de vértices, menos cartões por bloco
        var li = leafPos.size - 1
        while (li >= 0) {
            val e = leafPos[li]; li--
            val inner = e < 0; val e2 = if (inner) -e - 1 else e
            val ex = e2 % SX; val ez = (e2 / SX) % SZ; val ey = e2 / (SX * SZ)
            var any = false
            for (f in 0 until 6) {
                val aa = f shr 1; val ss = if ((f and 1) == 0) 1 else -1
                val nid = get(ex + if (aa == 0) ss else 0, ey + if (aa == 1) ss else 0, ez + if (aa == 2) ss else 0)
                lop[f] = nid == B.AIR || nid == B.WATER
                if (lop[f]) any = true
            }
            if (inner) { for (f in 0 until 6) lop[f] = true; any = true }   // folha de segunda camada: 1 cartão preenche o volume da copa
            if (!any) continue
            val mc = if (inner) 1 else 99
            Leaf3D.emit(o, ex, ey, ez, B.tSide[get(ex, ey, ez)], lop, 0.94f + 0.06f * hash(ex, ez, ey), mc)
        }
        decorate(cx, cz, o)
    }

    // ================= BAMBU: colmo fino com nós e folhinhas =================
    private val mossId: Int by lazy { B.byName["Musgo"] ?: -1 }
    private val bambuLeaf: Int by lazy { B.tSide[B.byName["Folhas de Bambu"] ?: B.LEAVES] }
    private fun stalk(o: MeshBuf, x: Int, y: Int, z: Int, id: Int, vr: Float) {
        val cx = x + 0.5f + (hash(x, z, 31) - 0.5f) * 0.4f; val cz = z + 0.5f + (hash(x, z, 32) - 0.5f) * 0.4f
        val r = 0.105f + 0.03f * hash(x, z, 33)
        val tile = B.tSide[id]
        val y0 = y.toFloat(); val y1 = y + 1f
        val sh = floatArrayOf(0.84f * vr, 0.7f * vr, 0.96f * vr, 0.78f * vr)
        val sx = floatArrayOf(-1f, 1f, 1f, -1f); val sz = floatArrayOf(-1f, -1f, 1f, 1f)
        for (i in 0 until 4) {
            val j = (i + 1) and 3
            quad(o, tile, cx + sx[i] * r, y0, cz + sz[i] * r, cx + sx[j] * r, y0, cz + sz[j] * r, cx + sx[j] * r, y1, cz + sz[j] * r, cx + sx[i] * r, y1, cz + sz[i] * r, sh[i] * 0.9f, sh[i])
        }
        if (get(x, y + 1, z) != id) {   // ponta de cima
            val t = Blk.pickTile(B.tTop[id], 0f); val tc = (t % Atlas.NT).toFloat(); val tr = (t / Atlas.NT).toFloat()
            val u0 = (tc + 0.01f) / Atlas.NT; val u1 = (tc + 0.99f) / Atlas.NT; val vt = (tr + 0.01f) / Atlas.ROWS; val vb = (tr + 0.99f) / Atlas.ROWS
            o.vert(cx - r, y1, cz - r, vr, vr, vr, u0, vt); o.vert(cx + r, y1, cz - r, vr, vr, vr, u1, vt)
            o.vert(cx + r, y1, cz + r, vr, vr, vr, u1, vb); o.vert(cx - r, y1, cz + r, vr, vr, vr, u0, vb)
            o.tri(0, 1, 2); o.tri(0, 2, 3); o.tri(0, 2, 1); o.tri(0, 3, 2); o.vc += 4
        }
        // nó (anel um pouco mais grosso) a cada 3 blocos
        if ((y + (hash(x, z, 34) * 3f).toInt()) % 3 == 0) {
            val rn = r + 0.028f; val a0 = y + 0.42f; val a1 = y + 0.58f
            for (i in 0 until 4) {
                val j = (i + 1) and 3
                quad(o, tile, cx + sx[i] * rn, a0, cz + sz[i] * rn, cx + sx[j] * rn, a0, cz + sz[j] * rn, cx + sx[j] * rn, a1, cz + sz[j] * rn, cx + sx[i] * rn, a1, cz + sz[i] * rn, sh[i] * 0.72f, sh[i] * 0.78f)
            }
        }
        // folhinhas saindo do colmo (da altura 3 pra cima)
        var below = 0; while (below < 4 && get(x, y - 1 - below, z) == id) below++
        if (below >= 3) Leaf3D.stalkLeaves(o, cx, y + 0.5f, cz, x, y, z, bambuLeaf, vr, if (get(x, y + 1, z) != id) 5 else 3)
    }

    // ================= DECORAÇÃO DAS ÁRVORES =================
    // galhos finos com folhagem e folhinhas soltas: tudo é calculado a partir dos blocos na hora de montar a malha (nada vai pro arquivo do mundo)

    private fun quad(o: MeshBuf, tile: Int, ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float, s0: Float, s1: Float) {
        val tc = (tile % Atlas.NT).toFloat(); val tr = (tile / Atlas.NT).toFloat()   // coluna e linha do tile no atlas (antes só valia pra linha 0: o bambu saía cinza)
        val u0 = (tc + 0.01f) / Atlas.NT; val u1 = (tc + 0.99f) / Atlas.NT
        val vb = (tr + 0.99f) / Atlas.ROWS; val vt = (tr + 0.01f) / Atlas.ROWS
        o.vert(ax, ay, az, s0, s0, s0, u0, vb); o.vert(bx, by, bz, s0, s0, s0, u1, vb)
        o.vert(cx, cy, cz, s1, s1, s1, u1, vt); o.vert(dx, dy, dz, s1, s1, s1, u0, vt)
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
            o.vert(x0 + ux * su[i] * w0 + vx * sv[i] * w0, y0 + vy * sv[i] * w0, z0 + uz * su[i] * w0 + vz * sv[i] * w0, k, k, k, tu0, 0.1f / Atlas.ROWS)
            o.vert(x0 + ux * su[j] * w0 + vx * sv[j] * w0, y0 + vy * sv[j] * w0, z0 + uz * su[j] * w0 + vz * sv[j] * w0, k, k, k, tu1, 0.1f / Atlas.ROWS)
            o.vert(x1 + ux * su[j] * w1 + vx * sv[j] * w1, y1 + vy * sv[j] * w1, z1 + uz * su[j] * w1 + vz * sv[j] * w1, k, k, k, tu1, vEnd / Atlas.ROWS)
            o.vert(x1 + ux * su[i] * w1 + vx * sv[i] * w1, y1 + vy * sv[i] * w1, z1 + uz * su[i] * w1 + vz * sv[i] * w1, k, k, k, tu0, vEnd / Atlas.ROWS)
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
            if (o.vc > 3000000) return
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
            if (m.vc > 3000000) continue
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
        for (i in 0 until 4) if (B.log(get(x + DX8[i * 2], y, z + DZ8[i * 2]))) hs++
        if (hs == 0) return
        var gy = y - 1
        while (gy > 0 && (get(x, gy, z) == B.AIR || B.log(get(x, gy, z)))) gy--
        if (y - gy < 4) return   // tronco e raízes ficam lisos
        val tip = hs == 1 && !B.log(get(x, y + 1, z))
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
    private fun leafFaces(o: MeshBuf, x: Int, y: Int, z: Int, lo: Float, hi: Float, outer: Boolean, vr: Float, tone: Float, tile: Int = 8) {
        val pos = FloatArray(3)
        val tx = (tile % Atlas.NT).toFloat(); val ty = (tile / Atlas.NT).toFloat()
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
                o.vert(pos[0], pos[1], pos[2], sh, sh, sh, (tx + 0.01f + tu * 0.98f) / Atlas.NT.toFloat(), (ty + 0.01f + tv * 0.98f) / Atlas.ROWS)
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
                o.vert(pos[0], pos[1], pos[2], k, k, k, (22f + 0.01f + ur + cu * 0.23f) / Atlas.NT.toFloat(), (0.01f + vw + cv * 0.23f) / Atlas.ROWS)
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
                    k2, k2, k2, (tl + uu[q]) / Atlas.NT.toFloat(), vv[q] / Atlas.ROWS)
            }
            o.tri(0, 1, 2); o.tri(0, 2, 3); o.tri(0, 2, 1); o.tri(0, 3, 2); o.vc += 4
        }
    }
}
