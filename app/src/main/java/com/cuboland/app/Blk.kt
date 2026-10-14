package com.cuboland.app

/**
 * Registro dos blocos novos (id >= 14). Cada bloco é uma linha em build().
 * ATENÇÃO: os ids vão pro arquivo do mundo. NUNCA reordene nem apague linhas: só adicione no FIM de build().
 * Ids 100..103 são das ferramentas (pulados). Máximo: id 16383 (o mundo guarda 4 bytes por bloco; os arrays de B têm N = 16384).
 */
object Blk {
    const val MAXT = 4096          // teto técnico de tiles (o atlas cresce sozinho em linhas de 32 tiles; GPU comum aguenta 8192 px)
    const val F_LIGHT = 1          // brilha (sem sombra/AO)
    const val F_GLASS = 2          // translúcido: desenhado junto com a água, vizinhos continuam visíveis
    const val F_SHAPE = 16         // formato próprio (Shapes): tipo nos bits 5-7 (1 cacto, 2 melancia, 3 abóbora, 4 cadeira, 5 mesa)
    const val F_THIN = 8           // colmo fino (bambu): desenhado como haste fina, vizinhos continuam visíveis
    const val F_LEAF = 4           // folhagem: malha de folha (casca + cubo interno), buracos que deixam ver o miolo, balança no vento
    const val M_STONE = 1; const val M_WOOD = 2; const val M_SOFT = 3; const val M_GLASS = 4; const val M_METAL = 5
    const val C_NAT = 2; const val C_BUILD = 3; const val C_DEC = 4; const val C_FURN = 5

    // tipos de textura (gerados por código em Atlas.genNew)
    const val K_FLAT = 0; const val K_STONE = 1; const val K_COBBLE = 2; const val K_BRICK = 3; const val K_PLANK = 4
    const val K_LOGSIDE = 5; const val K_LOGTOP = 6; const val K_ORE = 7; const val K_WOOL = 8; const val K_GLASS = 9
    const val K_TILES = 10; const val K_CHECK = 11; const val K_SPECK = 12; const val K_STRIPE = 13; const val K_GLOW = 14
    const val K_LAVA = 15; const val K_LAMP = 16; const val K_GRAVEL = 17; const val K_SHINGLE = 18; const val K_CRYSTAL = 19
    const val K_SHELF = 20; const val K_BAND = 21; const val K_GRAD = 22; const val K_CANDY = 23; const val K_SWIRL = 24
    const val K_BORDER = 25; const val K_RING = 26; const val K_CELLS = 27; const val K_JELLY = 28; const val K_ICE = 29
    const val K_BLOCKY = 30; const val K_GLAZE = 31; const val K_CACTUS = 32; const val K_LEAFY = 33; const val K_PIX = 34; const val K_LEAF = 35
    const val K_PALV = 39   // variante do topo com grãos em posição/quantidade diferentes (NatVar): c1 = índice da NatArt, c2 = variante
    const val K_SPRITE = 38   // folha desenhada por espécie (Leaf3D): c1 = estilo, c2 = índice da NatArt (paleta; -1 = carvalho)
    const val K_MARBLE = 40; const val K_SCREEN = 41; const val K_FABRIC = 42; const val K_WGRAIN = 43; const val K_RUG = 44; const val K_GINGHAM = 45
    const val K_BUILD = 46   // textura da Construção (BuildArt): c1 = padrão, c2 = cor base, c3 = acento (-1 = padrão), c4 = opacidade do miolo do vidro (%)
    const val K_PAL = 36; const val K_PLEAF = 37   // pixel art com paleta própria (NatArt); K_PLEAF = folhagem com buracos transparentes

    /** flags de forma da Construção (BuildShapes): sub = tipo */
    private fun shp(sub: Int, glass: Boolean = false): Int = F_SHAPE or (7 shl 5) or (sub shl 8) or (if (glass) F_GLASS else 0)
    private const val PANE = F_SHAPE or (7 shl 5) or (BuildShapes.S_PANE shl 8) or F_GLASS
    private const val PLATE = F_SHAPE or (7 shl 5) or (BuildShapes.S_PLATE shl 8) or F_GLASS
    private const val ROOF_DESC = "Telhado inclinado de verdade: o lado alto fica pro lado que você olha. Ponha duas fileiras de frente uma pra outra e feche com a cumeeira."
    /** bloco base -> [id virado pra +Z, -Z, +X, -X] (telhados e escadas) */
    val rotIds = HashMap<Int, IntArray>()
    /** laje de baixo -> laje de cima */
    val slabTop = HashMap<Int, Int>()
    /** cumeeira ao longo de Z -> cumeeira ao longo de X */
    val ridgeAlt = HashMap<Int, Int>()
    /** variante certa pra colocar: telhado/escada viram pra direção do olhar (dir 0 = +Z, 1 = -Z, 2 = +X, 3 = -X); laje sobe se estiver embaixo de um teto; cumeeira acompanha o olhar */
    fun oriented(id: Int, dir: Int, ceiling: Boolean): Int {
        defs
        val r = rotIds[id]; if (r != null) return r[dir.coerceIn(0, 3)]
        if (ceiling) { val t = slabTop[id]; if (t != null) return t }
        val a = ridgeAlt[id]; if (a != null) return if (dir >= 2) a else id
        return id
    }

    class Def(val id: Int, val name: String, val cat: Int, val top: Int, val side: Int, val bot: Int,
              val color: Int, val flags: Int, val hard: Float, val mat: Int, val desc: String)

    /** receita de cada tile novo: [tipo, c1, c2, c3, c4, semente, par | (flags shl 8)] */
    /** tiles da mobília (índices T_* de Furniture) */
    val ft = IntArray(48)
    val fts: IntArray get() { defs; return ft }
    val specs = arrayOfNulls<IntArray>(MAXT)
    private var nextTile = 24
    /** quantos tiles já foram usados (o atlas ajusta as linhas a isso) */
    val tileCount: Int get() { defs; return nextTile }
    private var nextId = 14
    /** blocos que só existem pra mundos antigos abrirem: não aparecem na mochila e viram o bloco equivalente ao carregar */
    val hidden = HashSet<Int>()
    private val aliasName = HashMap<Int, String>()
    val alias: IntArray by lazy { defs; val a = IntArray(16384); for ((id, n) in aliasName) { val t = list.firstOrNull { it.name == n }; if (t != null) a[id] = t.id }; a }
    private val list = ArrayList<Def>()
    val defs: List<Def> by lazy { build(); list }
    /** trechos de tiles seguidos de folhagem (K_LEAF): o shader usa pra balançar no vento e iluminar como folha */
    val leafRuns: List<IntArray> by lazy {
        defs; val out = ArrayList<IntArray>(); var s = -1
        for (i in 24..nextTile) {
            val on = i < nextTile && (specs[i]?.get(0) == K_LEAF || specs[i]?.get(0) == K_PLEAF || specs[i]?.get(0) == K_SPRITE)
            if (on && s < 0) s = i
            if (!on && s >= 0) { out.add(intArrayOf(s, i - 1)); s = -1 }
        }
        out
    }

    /** trechos de tiles seguidos de vidro (K_BUILD com padrão de vidro): o shader dá brilho e deixa passar luz */
    val glassRuns: List<IntArray> by lazy {
        defs; val out = ArrayList<IntArray>(); var s = -1
        for (i in 24..nextTile) {
            val sp = if (i < nextTile) specs[i] else null
            val on = sp != null && sp[0] == K_BUILD && BuildArt.isGlass(sp[1])
            if (on && s < 0) s = i
            if (!on && s >= 0) { out.add(intArrayOf(s, i - 1)); s = -1 }
        }
        out
    }

    private fun tl(kind: Int, c1: Int, c2: Int = c1, c3: Int = c1, c4: Int = 0, seed: Int = 0, par: Int = 0, fl: Int = 0, glow: Boolean = false): Int {
        val i = nextTile; nextTile++   // brilhantes e normais na mesma fila: sem região reservada
        if (i >= MAXT) return 1
        val sd = if (seed != 0) seed else i * 13 + 5
        specs[i] = intArrayOf(kind, c1, c2, c3, c4, sd, par or (fl shl 8))
        return i
    }

    /** tile da Construção (BuildArt): padrão 16x16 + cor base (+ acento opcional) */
    private fun bt(pat: Int, base: Int, acc: Int = -1, scale: Int = 0): Int = tl(K_BUILD, pat, base, acc, scale)

    /** tile pixel art da NatArt (paleta própria, alpha por cor) */
    private fun pt(art: Int, leaf: Boolean = false, glow: Boolean = false): Int = tl(if (leaf) K_PLEAF else K_PAL, art, glow = glow)
    /** tile da folhagem -> tile do sprite 3D dessa espécie (0 = sem folha 3D) */
    /** tiles dos cabinhos da abóbora e da melancia (Shapes) */
    private var tStemPump = 0; private var tStemMel = 0
    val stemPump: Int get() { defs; return tStemPump }
    val stemMel: Int get() { defs; return tStemMel }
    val spriteOf = IntArray(MAXT)
    val fruitOf = IntArray(MAXT)
    fun fruitFor(tile: Int): Int { defs; return if (tile in 0 until MAXT) fruitOf[tile] else 0 }
    /** tile base -> primeiro tile das NatVar.V variantes (0 = sem variantes) */
    val varBase = IntArray(MAXT)
    /** tile do topo com variantes de grãos: cada bloco do mundo sorteia uma (h = número 0..1 por posição) */
    /** quantas variantes tem o tile (0 = padrão da NatVar) */
    val varN = IntArray(MAXT)
    fun pickTile(tile: Int, h: Float): Int {
        val b = varBase[tile and (MAXT - 1)]; if (b == 0) return tile
        val n = varN[tile and (MAXT - 1)].let { if (it > 0) it else NatVar.V }
        return b + (h * n).toInt().coerceAtMost(n - 1)
    }
    /** tile liso da Construção com 4 variantes (detalhes em lugares diferentes, sorteadas por posição) pra não repetir quando se põem vários */
    private fun btv(pat: Int, base: Int, acc: Int = -1, scale: Int = 0): Int {
        val t = bt(pat, base, acc, scale)
        var first = 0
        for (v in 1..4) { val vt = tl(K_BUILD, pat, base, acc, scale, 0, v); if (v == 1) first = vt }
        if (t >= 24 && first >= 24) { varBase[t] = first; varN[t] = 4 }
        return t
    }
    private fun ptv(art: Int): Int {
        val t = pt(art)
        var first = 0
        for (v in 0 until NatVar.V) { val vt = tl(K_PALV, art, v); if (v == 0) first = vt }
        if (t >= 24 && first >= 24) varBase[t] = first
        return t
    }
    fun spriteFor(tile: Int): Int { defs; return if (tile in 0 until MAXT) spriteOf[tile] else 0 }
    /** bloco de folhagem com folha 3D própria: tile da casca (NatArt) + tile do sprite logo depois (ficam no mesmo trecho de leafRuns) */
    private fun leaf3(name: String, art: Int, kind: Int, color: Int, desc: String = ""): Int {
        val t = pt(art, true)
        val sp = tl(K_SPRITE, kind, art)
        if (t >= 24 && sp >= 24) spriteOf[t] = sp
        if (Leaf3D.hasFruit(kind)) { val fr = tl(K_SPRITE, 20 + kind, -2); if (t >= 24 && fr >= 24) fruitOf[t] = fr }
        return uni(name, C_NAT, t, color, M_SOFT, 2f, F_LEAF, desc)
    }
    private fun legacy(id: Int, canon: String) { hidden.add(id); aliasName[id] = canon }

    private fun blk(name: String, cat: Int, top: Int, side: Int, bot: Int, color: Int, mat: Int = 0, hard: Float = 4f, fl: Int = 0, desc: String = ""): Int {
        if (nextId == 100) nextId = 104
        if (nextId > 16383) return 0
        val id = nextId
        nextId++
        list.add(Def(id, name, cat, top, side, bot, color, fl, hard, mat, desc))
        return id
    }

    private fun uni(name: String, cat: Int, tile: Int, color: Int, mat: Int = 0, hard: Float = 4f, fl: Int = 0, desc: String = ""): Int =
        blk(name, cat, tile, tile, tile, color, mat, hard, fl, desc)

    private fun mixc(a: Int, b: Int, t: Float): Int {
        val r = (((a shr 16) and 255) * (1f - t) + ((b shr 16) and 255) * t).toInt()
        val g = (((a shr 8) and 255) * (1f - t) + ((b shr 8) and 255) * t).toInt()
        val bl = ((a and 255) * (1f - t) + (b and 255) * t).toInt()
        return (r shl 16) or (g shl 8) or bl
    }
    private fun dk(c: Int, t: Float): Int = mixc(c, 0x000000, t)
    private fun lt(c: Int, t: Float): Int = mixc(c, 0xFFFFFF, t)

    private val DYE = intArrayOf(0xF2F2EE, 0xF59B3D, 0xD96FCB, 0x6DB9EE, 0xF5D63B, 0x9AD93E, 0xF5A3C4, 0x6E747B,
        0xADB3B8, 0x3FB2B5, 0x8F5BC8, 0x4560CF, 0x8B5E3C, 0x4F8F3A, 0xD8473E, 0x2E3138)
    private val DM = arrayOf("Branco", "Laranja", "Magenta", "Azul-claro", "Amarelo", "Lima", "Rosa", "Cinza",
        "Cinza-claro", "Ciano", "Roxo", "Azul", "Marrom", "Verde", "Vermelho", "Preto")
    private val DF = arrayOf("Branca", "Laranja", "Magenta", "Azul-clara", "Amarela", "Lima", "Rosa", "Cinza",
        "Cinza-clara", "Ciano", "Roxa", "Azul", "Marrom", "Verde", "Vermelha", "Preta")

    private fun pal(pat: Int, c: Int, fl: Int = 0): Int = tl(K_PIX, dk(c, 0.5f), dk(c, 0.22f), c, lt(c, 0.2f), 0, pat, fl)

    private fun build() {
        // ================= CORES (16 cores cada) =================
        for (i in 0 until 16) { val c = DYE[i]
            uni("Lã " + DF[i], C_DEC, tl(K_WOOL, c, dk(c, 0.14f)), c, M_SOFT, 2f) }
        for (i in 0 until 16) { val c = DYE[i]
            uni("Concreto " + DM[i], C_BUILD, btv(BuildArt.CONCRETE, c), c, M_STONE, 9f) }
        uni("Terracota", C_BUILD, btv(BuildArt.CLAY, 0xA8643F), 0xA8643F, M_STONE, 8f)
        for (i in 0 until 16) { val c = mixc(DYE[i], 0x8C6A52, 0.42f)
            uni("Terracota " + DF[i], C_BUILD, btv(BuildArt.CLAY, c), c, M_STONE, 8f) }
        for (i in 0 until 16) { val c = DYE[i]
            uni("Terracota Vitrificada " + DF[i], C_DEC, tl(K_GLAZE, dk(c, 0.2f), lt(c, 0.4f), lt(c, 0.8f), 0, 0, i and 3, 4), c, M_STONE, 8f) }
        uni("Vidro", C_BUILD, bt(BuildArt.GLASS, 0xD8F2FF), 0xCDEAF5, M_GLASS, 2f, PANE, "Vidro grosso e liso: transparente, emenda com os vizinhos sem marca.")
        uni("Vidro Escuro", C_BUILD, bt(BuildArt.GLASS, 0x4A4256, -1, 210), 0x4A4256, M_GLASS, 2f, PANE, "Vidro grosso fumê, liso, para janelas misteriosas.")
        for (i in 0 until 16) { val c = DYE[i]
            uni("Vidro " + DM[i], C_BUILD, bt(BuildArt.GLASS, c, -1, 290), c, M_GLASS, 2f, PANE, "Vidro grosso colorido, liso e translúcido.") }
        val lampIdx = intArrayOf(0, 1, 6, 3, 4, 5, 10, 14)
        for (k in lampIdx.indices) { val i = lampIdx[k]; val c = DYE[i]
            uni("Bloco Luminoso " + DM[i], C_DEC, tl(K_LAMP, c, dk(c, 0.45f), lt(c, 0.85f), 0, 0, 0, 0, true), c, 0, 3f, F_LIGHT, "Brilha com uma luz colorida.") }

        // ================= MADEIRAS =================
        val WN = arrayOf("Abeto", "Bétula", "Selva", "Acácia", "Carvalho Escuro", "Mangue", "Cerejeira", "Bambu", "Carmesim", "Distorcido")
        val WP = intArrayOf(0x7B5B3A, 0xE6D79B, 0xB98563, 0xC4693B, 0x4B3321, 0x8E3F3B, 0xE9B8B2, 0xE1C75B, 0x8B3B5E, 0x2F7F7A)
        for (i in 0 until 10) { val c = WP[i]
            uni("Tábua de " + WN[i], C_BUILD, bt(BuildArt.W_ABETO + i, c), c, M_WOOD, 6f) }
        fun log(n: String, s1: Int, s2: Int, kn: Int, t1: Int, t2: Int) {
            val tp = tl(K_PIX, s2, t2, t1, lt(t1, 0.15f), 0, Pix.LOGTOP)
            blk(n, C_NAT, tp, tl(K_PIX, kn, s2, s1, lt(s1, 0.12f), 0, Pix.BARK), tp, s2, M_WOOD, 8f)
        }
        log("Tronco de Abeto", 0x5A4128, 0x45301B, 0x33220F, 0x7B5B3A, 0x6A4C2E)
        log("Tronco de Bétula", 0xEDEDE6, 0xD3D3CB, 0x2E2E2E, 0xE6D79B, 0xD6C687)
        log("Tronco de Selva", 0x7A6232, 0x5F4B25, 0x4B3A1C, 0xB98563, 0xA87855)
        log("Tronco de Acácia", 0x7A736A, 0x5E5850, 0x484239, 0xC4693B, 0xB35E32)
        log("Tronco de Carvalho Escuro", 0x40301F, 0x2E2114, 0x1F160D, 0x4B3321, 0x3F2B1C)
        log("Tronco de Mangue", 0x6A4439, 0x523228, 0x3E241C, 0x8E3F3B, 0x7D3733)
        log("Tronco de Cerejeira", 0x4A3445, 0x382633, 0x261923, 0xE9B8B2, 0xD9A8A2)
        log("Caule Carmesim", 0x7A2A52, 0x5E1F3E, 0x4A1530, 0x8B3B5E, 0x7A3252)
        log("Caule Distorcido", 0x2A7E7A, 0x1F615E, 0x174A47, 0x2F7F7A, 0x286E6A)
        blk("Bloco de Bambu", C_BUILD, tl(K_PIX, 0x8DB03E, 0xD0B54A, 0xE1C75B, 0xF0DA80, 0, Pix.LOGTOP), tl(K_PIX, 0x6E9430, 0x8DB03E, 0xA5C94E, 0xC4E070, 0, Pix.BAMBOO), tl(K_PIX, 0x8DB03E, 0xD0B54A, 0xE1C75B, 0xF0DA80, 0, Pix.LOGTOP), 0xA5C94E, M_WOOD, 6f)
        val bamD = list[list.size - 1]   // o colmo reaproveita as texturas do Bloco de Bambu (não gasta tiles novos: o limite é 448)
        blk("Colmo de Bambu", C_NAT, bamD.top, bamD.side, bamD.bot, 0xA5C94E, M_WOOD, 2f, F_THIN, "Haste fina de bambu, com nós e folhinhas.")
        uni("Bambu Trançado", C_BUILD, bt(BuildArt.MOSAIC, 0xE1C75B), 0xE1C75B, M_WOOD, 5f)
        fun strip(n: String, c: Int) {
            val tp = bt(BuildArt.RINGS, c)
            blk(n, C_BUILD, tp, bt(BuildArt.STRIP, c), tp, c, M_WOOD, 7f)
        }
        strip("Carvalho Descascado", 0xC9A06B)
        strip("Abeto Descascado", 0x8A6A45)
        strip("Bétula Descascada", 0xDCCB8E)
        strip("Selva Descascada", 0xB88B62)
        strip("Acácia Descascada", 0xB8603A)
        strip("Carvalho Escuro Descascado", 0x5A4128)
        strip("Cerejeira Descascada", 0xDDA5A0)

        // ================= PEDRAS E MINERAIS =================
        fun rough(n: String, c: Int, grout: Int) {
            val p = when (n) { "Andesito" -> BuildArt.ROUGH2; "Ardósia" -> BuildArt.BASALT; "Tufo" -> BuildArt.TUFF; "Netherrack" -> BuildArt.NETHER; "Pedra do Fim" -> BuildArt.ENDSTONE; "Prismarinho" -> BuildArt.ROUGH1; else -> BuildArt.ROUGH0 }
            uni(n, C_BUILD, bt(p, c), c, M_STONE, 10f)
        }
        fun smooth(n: String, c: Int) { uni(n, C_BUILD, bt(if (n.startsWith("Quartzo")) BuildArt.QUARTZ else if (n.contains("Polid")) BuildArt.POLISHED else BuildArt.SMOOTH, c), c, M_STONE, 10f) }
        fun bricks(n: String, c: Int, grout: Int, fl: Int = 0) { uni(n, C_BUILD, bt(if (n.contains("Nether")) BuildArt.BRICK else if ((fl and 1) != 0) BuildArt.SBRICK_CRACK else if ((fl and 2) != 0) BuildArt.SBRICK_MOSS else if (n.contains("Ardósia") || n.contains("Quartzo")) BuildArt.LONGBRICK else if (n.contains("Prismarinho")) BuildArt.PRISM else BuildArt.SBRICK, c), c, M_STONE, 10f) }
        val cobC = intArrayOf(0x9AA0AA, 0xB6BCC6, 0x666C78)
        uni("Pedregulho", C_BUILD, bt(BuildArt.COBBLE, 0x9AA0AA), 0x9AA0AA, M_STONE, 10f)
        uni("Pedregulho Musgoso", C_BUILD, bt(BuildArt.COBBLE_MOSS, 0x9AA0AA), 0x8CA07A, M_STONE, 10f)
        smooth("Pedra Lisa", 0xC9CED8)
        bricks("Tijolos de Pedra", 0xB4BAC4, 0x7F8590)
        bricks("Tijolos de Pedra Musgosos", 0xB4BAC4, 0x7F8590, 2)
        bricks("Tijolos de Pedra Rachados", 0xB4BAC4, 0x7F8590, 1)
        uni("Tijolos Cinzelados", C_BUILD, bt(BuildArt.CHISEL, 0xB4BAC4), 0xB4BAC4, M_STONE, 10f)
        uni("Granito", C_BUILD, bt(BuildArt.SPECK, 0xB8786A, 0xE8A090), 0xB8786A, M_STONE, 10f)
        smooth("Granito Polido", 0xBE8071)
        uni("Diorito", C_BUILD, bt(BuildArt.SPECK, 0xDCDCD8, 0x6A6A70), 0xDCDCD8, M_STONE, 10f)
        smooth("Diorito Polido", 0xE2E2DE)
        rough("Andesito", 0xA2A29E, 0x7A7A76)
        smooth("Andesito Polido", 0xA8A8A4)
        rough("Ardósia", 0x50525A, 0x33343A)
        smooth("Ardósia Polida", 0x565861)
        bricks("Tijolos de Ardósia", 0x50525A, 0x2C2D33)
        uni("Ladrilhos de Ardósia", C_BUILD, bt(BuildArt.TILES, 0x555760), 0x50525A, M_STONE, 10f)
        uni("Pedregulho de Ardósia", C_BUILD, bt(BuildArt.COBBLE, 0x50525A), 0x50525A, M_STONE, 10f)
        rough("Tufo", 0x7E8274, 0x5A5E50)
        rough("Pedra Negra", 0x3A3540, 0x221E28)
        smooth("Pedra Negra Polida", 0x413C48)
        bricks("Tijolos de Pedra Negra", 0x3A3540, 0x1E1A24)
        blk("Arenito", C_BUILD, bt(BuildArt.SMOOTH, 0xF0DFA8), bt(BuildArt.SANDSIDE, 0xF0DFA8), bt(BuildArt.SMOOTH, 0xE9D9A0), 0xF0DFA8, M_STONE, 7f)
        uni("Arenito Liso", C_BUILD, bt(BuildArt.SMOOTH, 0xF3E6B4), 0xF3E6B4, M_STONE, 7f)
        uni("Arenito Cinzelado", C_BUILD, bt(BuildArt.CHISEL, 0xF0DFA8), 0xF0DFA8, M_STONE, 7f)
        blk("Arenito Vermelho", C_BUILD, bt(BuildArt.SMOOTH, 0xD58A48), bt(BuildArt.SANDSIDE, 0xD58A48), bt(BuildArt.SMOOTH, 0xCC8444), 0xD58A48, M_STONE, 7f)
        bricks("Tijolos do Nether", 0x5A2830, 0x2E1216)
        bricks("Tijolos Vermelhos do Nether", 0x8A2A36, 0x3E1218)
        rough("Netherrack", 0x8E3C3C, 0x6A2828)
        rough("Pedra do Fim", 0xE8EAB6, 0xC4C68A)
        uni("Purpur", C_BUILD, bt(BuildArt.TILES, 0xB58AC0), 0xB58AC0, M_STONE, 9f)
        rough("Prismarinho", 0x6BB6A0, 0x4A8F7D)
        bricks("Tijolos de Prismarinho", 0x6FBBA6, 0x4A8F7D)
        smooth("Quartzo", 0xF0ECE4)
        blk("Pilar de Quartzo", C_BUILD, bt(BuildArt.QUARTZ, 0xF0ECE4), bt(BuildArt.PILLAR, 0xF0ECE4), bt(BuildArt.QUARTZ, 0xF0ECE4), 0xF0ECE4, M_STONE, 9f)
        bricks("Tijolos de Quartzo", 0xF2EEE6, 0xD2CCC0)
        uni("Obsidiana", C_BUILD, bt(BuildArt.OBSIDIAN, 0x1C1030), 0x2E1B50, M_STONE, 24f)

        // ================= BLOCOS NOVOS E DOCES =================
        uni("Nuvem", C_DEC, tl(K_FLAT, 0xFFFFFF, 0xDCEBFA, 0xFFFFFF, 0, 0, 0, 4), 0xF2F8FF, M_SOFT, 2f, 0, "Fofa e branquinha, perfeita pro céu.")
        uni("Algodão-Doce Rosa", C_DEC, tl(K_FLAT, 0xFFC4DD, 0xFFE0EE), 0xFFC4DD, M_SOFT, 2f)
        uni("Algodão-Doce Azul", C_DEC, tl(K_FLAT, 0xBFE0FF, 0xE0F0FF), 0xBFE0FF, M_SOFT, 2f)
        uni("Bengala Doce", C_DEC, tl(K_CANDY, 0xE84A5F, 0xFFFFFF, 0, 0, 0, 4), 0xF08090, M_SOFT, 3f)
        uni("Pirulito", C_DEC, tl(K_SWIRL, 0xFF7FB5, 0xFFF0F7), 0xFF7FB5, M_SOFT, 3f)
        uni("Chocolate", C_DEC, tl(K_TILES, 0x6B3F2A, 0x5C3422, 0x3E2316, 0, 0, 4), 0x6B3F2A, M_SOFT, 3f)
        uni("Biscoito", C_DEC, tl(K_SPECK, 0xD9A55C, 0xE6B96E, 0x5B3A1E, 0, 0, 0), 0xD9A55C, M_SOFT, 3f)
        uni("Gelatina Rosa", C_DEC, tl(K_JELLY, 0xFF9CC8, 0xF06FA8), 0xFF9CC8, M_GLASS, 2f, F_GLASS, "Gelatina fofinha e translúcida.")
        uni("Gelatina Verde", C_DEC, tl(K_JELLY, 0x8EE07A, 0x5FBF4F), 0x8EE07A, M_GLASS, 2f, F_GLASS, "Gelatina fofinha e translúcida.")
        uni("Gelatina Roxa", C_DEC, tl(K_JELLY, 0xB896F0, 0x8F68D8), 0xB896F0, M_GLASS, 2f, F_GLASS, "Gelatina fofinha e translúcida.")
        uni("Cristal Rosa", C_DEC, tl(K_CRYSTAL, 0xF08AC4, 0xFFB8E0, 0xFFE6F4, 0, 0, 0, 16, true), 0xF08AC4, M_STONE, 8f, F_LIGHT, "Cristal mágico que brilha sozinho.")
        uni("Cristal Azul", C_DEC, tl(K_CRYSTAL, 0x5AA8F0, 0x8CCBFF, 0xD8EEFF, 0, 0, 0, 16, true), 0x5AA8F0, M_STONE, 8f, F_LIGHT, "Cristal mágico que brilha sozinho.")
        uni("Cristal Verde", C_DEC, tl(K_CRYSTAL, 0x4FCF8A, 0x86EDB4, 0xD6FFE8, 0, 0, 0, 16, true), 0x4FCF8A, M_STONE, 8f, F_LIGHT, "Cristal mágico que brilha sozinho.")
        uni("Cristal Roxo", C_DEC, tl(K_CRYSTAL, 0x9A6AE0, 0xC09CFF, 0xEBDDFF, 0, 0, 0, 16, true), 0x9A6AE0, M_STONE, 8f, F_LIGHT, "Cristal mágico que brilha sozinho.")
        blk("Pôr do Sol", C_DEC, tl(K_FLAT, 0xFFB07A, 0xFF9AA8), tl(K_GRAD, 0xFFC27A, 0xA878D8, 0xFF8FA8), tl(K_FLAT, 0xA878D8, 0x9A6AC8), 0xFF9AA8, M_SOFT, 3f)
        blk("Céu", C_DEC, tl(K_FLAT, 0x8CCBFF, 0xA6D8FF), tl(K_GRAD, 0x6DB6F5, 0xD4ECFF, 0), tl(K_FLAT, 0xD4ECFF, 0xE4F4FF), 0x8CCBFF, M_SOFT, 3f)
        blk("Oceano", C_DEC, tl(K_FLAT, 0x5FD0D8, 0x7ADCE0), tl(K_GRAD, 0x5FD0D8, 0x1F5FA8, 0), tl(K_FLAT, 0x1F5FA8, 0x2A6DB8), 0x3A9ACB, M_SOFT, 3f)
        uni("Xadrez", C_DEC, tl(K_CHECK, 0xF2F2EE, 0x2E3138, 0, 0, 0, 4), 0x8A8D90, M_STONE, 8f)
        uni("Telhado Vermelho", C_BUILD, bt(BuildArt.SHINGLE, 0xD8584A), 0xD8584A, M_STONE, 8f, shp(BuildShapes.S_ROOF), ROOF_DESC)
        uni("Telhado Azul", C_BUILD, bt(BuildArt.SHINGLE, 0x5A86D8), 0x5A86D8, M_STONE, 8f, shp(BuildShapes.S_ROOF), ROOF_DESC)
        uni("Telhado Verde", C_BUILD, bt(BuildArt.SHINGLE, 0x5FAE6A), 0x5FAE6A, M_STONE, 8f, shp(BuildShapes.S_ROOF), ROOF_DESC)
        uni("Palha", C_BUILD, pt(NatArt.PALHA), 0xE6C65A, M_SOFT, 3f)
        uni("Gesso Branco", C_BUILD, btv(BuildArt.PLASTER, 0xFAF6EE), 0xFAF6EE, M_STONE, 6f)
        uni("Gesso Rosa", C_BUILD, btv(BuildArt.PLASTER, 0xFFD6E4), 0xFFD6E4, M_STONE, 6f)
        uni("Gesso Amarelo", C_BUILD, btv(BuildArt.PLASTER, 0xFFEFB0), 0xFFEFB0, M_STONE, 6f)
        uni("Gesso Azul", C_BUILD, btv(BuildArt.PLASTER, 0xCFE4FA), 0xCFE4FA, M_STONE, 6f)
        uni("Piso de Madeira", C_BUILD, bt(BuildArt.MOSAIC, 0xC9935A), 0xC9935A, M_WOOD, 5f)
        uni("Azulejo Branco", C_BUILD, bt(BuildArt.AZ_PLAIN, 0xF4F4F0), 0xF4F4F0, M_STONE, 6f)
        uni("Azulejo Azul", C_BUILD, bt(BuildArt.AZ_DECOR, 0x2F63B5, 0xF6F6F0), 0x6FA8E8, M_STONE, 6f)
        uni("Janela de Madeira", C_BUILD, bt(BuildArt.WINDOW, 0x8B6B43, 0xBFE6F5), 0xB6A070, M_GLASS, 2f, PLATE, "Janela com moldura de madeira.")
        uni("Janela Branca", C_BUILD, bt(BuildArt.WINDOW, 0xF2F2EE, 0xBFE6F5), 0xDDE8EE, M_GLASS, 2f, PLATE, "Janela com moldura branca.")
        // Moitas eram cópias idênticas das Folhas (outro nome, mesma textura): ficam só pra mundos antigos, escondidas, e viram Folhas ao carregar
        legacy(uni("Moita Cerejeira", C_NAT, pt(NatArt.F_CEREJEIRA, true), 0xF0A6C4, M_SOFT, 2f, F_LEAF), "Folhas de Cerejeira")
        legacy(uni("Moita de Outono", C_NAT, pt(NatArt.F_OUTONO, true), 0xE07A26, M_SOFT, 2f, F_LEAF), "Folhas de Outono")
        legacy(uni("Moita Amarela", C_NAT, pt(NatArt.F_AMARELAS, true), 0xE0BE3A, M_SOFT, 2f, F_LEAF), "Folhas Amarelas")

        // ================= LUZES E MÓVEIS =================
        uni("Pedra Luminosa", C_DEC, tl(K_GLOW, 0xFFE9A0, 0xFFC96A, 0xD8963A, 0, 0, 0, 0, true), 0xFFD27C, 0, 4f, F_LIGHT, "Brilha com uma luz quentinha.")
        uni("Lanterna do Mar", C_DEC, tl(K_LAMP, 0xBFEFF0, 0x6FA8A8, 0xF4FFFF, 0, 0, 0, 0, true), 0xBFEFF0, 0, 4f, F_LIGHT, "Luz azulada de águas profundas.")
        uni("Luzcogumelo", C_DEC, tl(K_GLOW, 0xFFB26A, 0xF58A3C, 0xB85A22, 0, 0, 0, 0, true), 0xF58A3C, 0, 4f, F_LIGHT, "Brilho alaranjado e acolhedor.")
        uni("Magma", C_NAT, pt(NatArt.MAGMA, glow = true), 0xC8501E, M_STONE, 8f, F_LIGHT, "Pedra quente com rachaduras brilhantes.")
        blk("Estante", C_DEC, tl(K_PLANK, 0xD9A864, 0xC99A58, 0xA07840), tl(K_SHELF, 0xD9A864, 0x3A2A1C, 0xB5503C, 0x4F7FB8), tl(K_PLANK, 0xD9A864, 0xC99A58, 0xA07840), 0xB5503C, M_WOOD, 5f)
        blk("Barril", C_DEC, tl(K_RING, 0xC99A58, 0xD9A864, 0x8A6A3A, 0, 0, 2), tl(K_STRIPE, 0xC99A58, 0xB88A4A, 0x8A6A3A, 0, 0, 1), tl(K_RING, 0xC99A58, 0xD9A864, 0x8A6A3A, 0, 0, 2), 0xC99A58, M_WOOD, 5f)
        blk("Mesa de Trabalho", C_DEC, tl(K_TILES, 0xD9A864, 0xC99A58, 0x6B4A2A, 0, 0, 3), tl(K_PLANK, 0xC99A58, 0xB88A4A, 0x6B4A2A), tl(K_PLANK, 0xD9A864, 0xC99A58, 0x8A6A3A), 0xC99A58, M_WOOD, 5f)
        blk("Dinamite", C_DEC, tl(K_BORDER, 0xE84A4A, 0xF06A5A, 0xA02A2A), tl(K_STRIPE, 0xE84A4A, 0xF2F2EE, 0xA02A2A, 0, 0, 1), tl(K_BORDER, 0xE84A4A, 0xF06A5A, 0xA02A2A), 0xE84A4A, 0, 2f, 0, "Só de enfeite: não explode.")
        blk("Abóbora Luminosa", C_DEC, pt(NatArt.ABOBORA_LUM_TOP, glow = true), pt(NatArt.ABOBORA_LUM_SIDE, glow = true), pt(NatArt.ABOBORA_LUM_TOP, glow = true), 0xF29A2A, M_WOOD, 4f, F_LIGHT or F_SHAPE or (3 shl 5), "Abóbora que brilha por dentro.")

        // ================= NATUREZA =================
        uni("Terra Grossa", C_NAT, ptv(NatArt.TERRA_GROSSA), 0xA37748, M_SOFT, 3f)
        val tDirt = pt(NatArt.TERRA_BASE)
        blk("Podzol", C_NAT, ptv(NatArt.PODZOL_TOP), pt(NatArt.PODZOL_SIDE), tDirt, 0x7A5B38, M_SOFT, 3f)
        blk("Micélio", C_NAT, ptv(NatArt.MICELIO_TOP), pt(NatArt.MICELIO_SIDE), tDirt, 0x9A8AA8, M_SOFT, 3f)
        blk("Grama Nevada", C_NAT, ptv(NatArt.GRAMA_NEVADA_TOP), pt(NatArt.GRAMA_NEVADA_SIDE), tDirt, 0xF2F8FF, M_SOFT, 3f)
        uni("Lama", C_NAT, pt(NatArt.LAMA), 0x5A4A42, M_SOFT, 3f)
        uni("Argila", C_NAT, pt(NatArt.ARGILA), 0xA8B4C4, M_SOFT, 3f)
        uni("Cascalho", C_NAT, pt(NatArt.CASCALHO), 0x8E8A88, M_SOFT, 3f)
        uni("Areia Vermelha", C_NAT, pt(NatArt.AREIA_VERMELHA), 0xD9854A, M_SOFT, 3f)
        uni("Areia Rosa", C_NAT, pt(NatArt.AREIA_ROSA), 0xF8CCD8, M_SOFT, 3f)
        uni("Neve", C_NAT, pt(NatArt.NEVE), 0xFFFFFF, M_SOFT, 2f)
        uni("Gelo", C_NAT, pt(NatArt.GELO), 0xBFE3FF, M_GLASS, 3f, F_GLASS, "Gelo claro e escorregadio de olhar.")
        uni("Gelo Compactado", C_NAT, pt(NatArt.GELO_COMPACTO), 0x9CCFF5, M_GLASS, 4f)
        uni("Musgo", C_NAT, pal(Pix.STONE0, 0x5C9034), 0x5C9034, M_SOFT, 2f, 0, "Bloco de musgo fofinho, com tufinhos por cima.")
        blk("Cacto", C_NAT, pt(NatArt.CACTO_TOP), pt(NatArt.CACTO_SIDE), pt(NatArt.CACTO_TOP), 0x4F8F3A, M_SOFT, 3f, F_SHAPE or (1 shl 5))
        blk("Bloco de Feno", C_NAT, pt(NatArt.FENO_TOP), pt(NatArt.FENO_SIDE), pt(NatArt.FENO_TOP), 0xE6C65A, M_SOFT, 3f)
        blk("Melancia", C_NAT, pt(NatArt.MELANCIA_TOP), pt(NatArt.MELANCIA_SIDE), pt(NatArt.MELANCIA_TOP), 0x5FA84A, M_SOFT, 3f, F_SHAPE or (2 shl 5))
        blk("Abóbora", C_NAT, pt(NatArt.ABOBORA_TOP), pt(NatArt.ABOBORA_SIDE), pt(NatArt.ABOBORA_TOP), 0xF29A2A, M_SOFT, 3f, F_SHAPE or (3 shl 5))
        tStemPump = pt(NatArt.ABOBORA_CABO); tStemMel = pt(NatArt.MELANCIA_CABO)
        uni("Esponja", C_NAT, pt(NatArt.ESPONJA), 0xE8D86A, M_SOFT, 2f)
        uni("Favo de Mel", C_NAT, pt(NatArt.FAVO), 0xF2A22A, M_SOFT, 2f)
        uni("Bloco de Mel", C_NAT, pt(NatArt.MEL), 0xF8B83A, M_GLASS, 2f, F_GLASS, "Mel translúcido e docinho.")

        // ================= MINÉRIOS E METAIS =================
        fun ore(n: String, o1: Int, art: Int) { uni(n, C_NAT, pt(art), o1, M_STONE, 12f) }
        ore("Minério de Carvão", 0x2C2C30, NatArt.ORE_CARVAO)
        ore("Minério de Ferro", 0xD8A888, NatArt.ORE_FERRO)
        ore("Minério de Cobre", 0xD87A4A, NatArt.ORE_COBRE)
        ore("Minério de Ouro", 0xF2C93A, NatArt.ORE_OURO)
        ore("Minério de Redstone", 0xD8302A, NatArt.ORE_REDSTONE)
        ore("Minério de Lápis", 0x2E5AC8, NatArt.ORE_LAPIS)
        ore("Minério de Diamante", 0x4FD8D0, NatArt.ORE_DIAMANTE)
        ore("Minério de Esmeralda", 0x2FB85A, NatArt.ORE_ESMERALDA)
        fun metal(n: String, c: Int, pat: Int = BuildArt.METAL, acc: Int = -1) { uni(n, C_BUILD, bt(pat, c, acc), c, M_METAL, 14f) }
        metal("Bloco de Ferro", 0xE2E4E8)
        metal("Bloco de Ouro", 0xF6CF3E)
        metal("Bloco de Diamante", 0x5FE8DE)
        metal("Bloco de Esmeralda", 0x3FCF6A)
        metal("Bloco de Lápis", 0x3A66D2)
        metal("Bloco de Redstone", 0xD8342C)
        metal("Bloco de Cobre", 0xD98458)
        metal("Cobre Exposto", 0xC98A5E, BuildArt.METAL_P1, 0x6FAE8A)
        metal("Cobre Desgastado", 0xC98A5E, BuildArt.METAL_P2, 0x5FB08A)
        metal("Cobre Oxidado", 0x4FB59A, BuildArt.METAL_P1, 0x8FE0C4)

        // ================= FOLHAGEM DE TODAS AS ÁRVORES =================
        run { val sp = tl(K_SPRITE, Leaf3D.OAK, -1); if (sp >= 24) spriteOf[8] = sp; val fr = tl(K_SPRITE, 20 + Leaf3D.OAK, -2); if (fr >= 24) fruitOf[8] = fr }   // carvalho (Folhas): tile 8 do atlas original
        leaf3("Folhas de Abeto", NatArt.F_ABETO, Leaf3D.ABETO, 0x3C7A3A, "Folhas de abeto: verde bem escuro, de pinheiro.")
        leaf3("Folhas de Bétula", NatArt.F_BETULA, Leaf3D.BETULA, 0x8FBF4A, "Folhas de bétula: verde clarinho e fresco.")
        leaf3("Folhas de Selva", NatArt.F_SELVA, Leaf3D.SELVA, 0x45B02A, "Folhas de selva: verde vivo e cheio.")
        leaf3("Folhas de Acácia", NatArt.F_ACACIA, Leaf3D.ACACIA, 0xA2BC3C, "Folhas de acácia: verde de savana.")
        leaf3("Folhas de Carvalho Escuro", NatArt.F_CARVALHO_ESCURO, Leaf3D.CARV_ESC, 0x2F7A24, "Folhas de carvalho escuro: verde profundo.")
        leaf3("Folhas de Mangue", NatArt.F_MANGUE, Leaf3D.MANGUE, 0x62AA48, "Folhas de mangue: verde de pântano.")
        leaf3("Folhas de Cerejeira", NatArt.F_CEREJEIRA, Leaf3D.CEREJA, 0xF0A0C0, "Folhas de cerejeira: cor-de-rosa florido.")
        leaf3("Folhas de Outono", NatArt.F_OUTONO, Leaf3D.OUTONO, 0xE07A26, "Folhas de outono: laranja de fim de estação.")
        leaf3("Folhas Amarelas", NatArt.F_AMARELAS, Leaf3D.GINKGO, 0xE0BE3A, "Folhas amarelas douradas.")
        leaf3("Folhas de Bambu", NatArt.F_BAMBU, Leaf3D.BAMBU, 0x72AE34, "Folhas de bambu: finas e verdinhas.")
        leaf3("Verruga Carmesim", NatArt.F_CARMESIM, Leaf3D.CARMESIM, 0xC04068, "Verruga carmesim: copa vermelha do mundo sombrio.")
        leaf3("Verruga Distorcido", NatArt.F_DISTORCIDO, Leaf3D.DISTORCIDO, 0x2EA8A0, "Verruga distorcida: copa azul-turquesa do mundo sombrio.")

        // ================= MOBÍLIA (ids novos sempre no fim!) =================
        val F = ft
        F[Furniture.T_OAK] = tl(K_WGRAIN, 0xE0B070, 0xC8935A, 0x8C5E34)
        F[Furniture.T_OAKD] = tl(K_WGRAIN, 0xB87D45, 0x9C6535, 0x6B4524)
        F[Furniture.T_DARK] = tl(K_WGRAIN, 0x7A4E34, 0x6A422B, 0x3E2518)
        F[Furniture.T_DARKD] = tl(K_WGRAIN, 0x4E3020, 0x422818, 0x24150C)
        F[Furniture.T_BIRCH] = tl(K_WGRAIN, 0xEBDDB0, 0xDCCB98, 0xA89868)
        F[Furniture.T_MARB] = tl(K_MARBLE, 0xF4F1EC, 0xDCD8D2, 0x8E8A92)
        F[Furniture.T_MARBK] = tl(K_MARBLE, 0x2A2830, 0x3A3840, 0xC8B070)
        F[Furniture.T_GOLD] = tl(K_FLAT, 0xF2CF68, 0xD8A940)
        F[Furniture.T_STEEL] = tl(K_FLAT, 0xC8CED6, 0xA5ADB8)
        F[Furniture.T_GING] = tl(K_GINGHAM, 0xD84A4A, 0xFFF8F0)
        F[Furniture.T_FABR] = tl(K_FABRIC, 0xC8444E, 0x9A2E3A, 0, 0, 0, 1)
        F[Furniture.T_FABB] = tl(K_FABRIC, 0x4C74C4, 0x36549A, 0, 0, 0, 1)
        F[Furniture.T_FABG] = tl(K_FABRIC, 0x5FA070, 0x417A52, 0, 0, 0, 1)
        F[Furniture.T_CREAM] = tl(K_FABRIC, 0xF1E6CF, 0xD9CBAE)
        F[Furniture.T_WHITE] = tl(K_FLAT, 0xF6F6F2, 0xE4E6E8)
        F[Furniture.T_SCREEN] = tl(K_SCREEN, 0x2E5FA8, 0x7CC4F2, 0x3E7BD0)
        F[Furniture.T_BLACK] = tl(K_FLAT, 0x2A2C30, 0x1E2024)
        F[Furniture.T_KEYS] = tl(K_TILES, 0xD8DCE2, 0xC8CDD6, 0x70757E, 0, 0, 8)
        F[Furniture.T_POT] = tl(K_FLAT, 0xC4693B, 0xA8552E)
        F[Furniture.T_LEAFA] = tl(K_FLAT, 0x4F9F3C, 0x6FC24E)
        F[Furniture.T_LEAFB] = tl(K_FLAT, 0x3A8A3A, 0x56AE48)
        F[Furniture.T_SOIL] = tl(K_FLAT, 0x5A4030, 0x4A3326)
        val bookC = intArrayOf(0xC8444E, 0x4C74C4, 0x5FA070, 0xE0B040, 0x8A5BB8, 0x3FA8A8)
        for (i in 0 until 6) F[Furniture.T_BOOK0 + i] = tl(K_FLAT, bookC[i], dk(bookC[i], 0.2f))
        F[Furniture.T_RUGR] = tl(K_RUG, 0xC8444E, 0xF1E6CF, 0x8A2A36)
        F[Furniture.T_RUGB] = tl(K_RUG, 0x4C74C4, 0xF1E6CF, 0x2E4E8E)
        F[Furniture.T_SHADE] = tl(K_FABRIC, 0xFFE9B0, 0xF2D58A)
        F[Furniture.T_GREY] = tl(K_FABRIC, 0x5A5F6A, 0x45494F)
        F[Furniture.T_TVSCR] = tl(K_SCREEN, 0x2E5FA8, 0x7CC4F2, 0x3E7BD0, 0, 0, 1)
        val FU = F_SHAPE or (7 shl 5)
        blk("Cadeira", C_FURN, F[Furniture.T_FABR], F[Furniture.T_OAK], F[Furniture.T_OAKD], 0xB87D45, M_WOOD, 4f, F_SHAPE or (4 shl 5), "Cadeira de madeira com almofada e encosto de ripas. Vira sozinha para a mesa ao lado. Dá pra sentar!")
        blk("Mesa", C_FURN, F[Furniture.T_OAK], F[Furniture.T_OAKD], F[Furniture.T_OAKD], 0xD6A068, M_WOOD, 4f, F_SHAPE or (5 shl 5), "Mesa de madeira. Junte várias: o tampo cresce e as pernas ficam só nas pontas.")
        blk("Cama", C_FURN, F[Furniture.T_FABB], F[Furniture.T_CREAM], F[Furniture.T_OAKD], 0x5A86D8, M_WOOD, 4f, F_SHAPE or (6 shl 5), "Cama com cabeceira, travesseiro e coberta. Junte em fila e ela fica mais comprida.")
        blk("Cadeira Estofada", C_FURN, F[Furniture.T_FABB], F[Furniture.T_DARKD], F[Furniture.T_DARKD], 0x4C74C4, M_WOOD, 4f, FU or (Furniture.S_CHAIR2 shl 8), "Cadeira elegante de madeira escura com assento e encosto estofados. Dá pra sentar.")
        blk("Banco", C_FURN, F[Furniture.T_FABG], F[Furniture.T_DARKD], F[Furniture.T_DARKD], 0x5FA070, M_WOOD, 4f, FU or (Furniture.S_STOOL shl 8), "Banquinho redondo com almofada. Dá pra sentar.")
        blk("Poltrona", C_FURN, F[Furniture.T_FABR], F[Furniture.T_FABR], F[Furniture.T_DARKD], 0xC8444E, M_SOFT, 4f, FU or (Furniture.S_ARM shl 8), "Poltrona fofa com braços e almofada. Encosta na parede ou vira pra mesa. Dá pra sentar.")
        blk("Cadeira de Escritório", C_FURN, F[Furniture.T_GREY], F[Furniture.T_GREY], F[Furniture.T_STEEL], 0x5A5F6A, M_METAL, 4f, FU or (Furniture.S_OFFICE shl 8), "Cadeira giratória com rodinhas, braços e encosto alto. Dá pra sentar.")
        blk("Sofá", C_FURN, F[Furniture.T_FABB], F[Furniture.T_FABB], F[Furniture.T_DARKD], 0x4C74C4, M_SOFT, 4f, FU or (Furniture.S_SOFA shl 8), "Sofá estofado. Junte em fila para um sofá comprido (braços só nas pontas). Dá pra sentar.")
        blk("Mesa Escura", C_FURN, F[Furniture.T_DARK], F[Furniture.T_DARKD], F[Furniture.T_DARKD], 0x6A422B, M_WOOD, 4f, FU or (Furniture.S_TDARK shl 8), "Mesa de madeira escura com pernas torneadas. Junte várias para crescer.")
        blk("Mesa de Mármore", C_FURN, F[Furniture.T_MARB], F[Furniture.T_MARB], F[Furniture.T_GOLD], 0xE8E4DE, M_STONE, 6f, FU or (Furniture.S_TMARB shl 8), "Tampo de mármore com pés dourados. Junte várias para crescer.")
        blk("Mesa com Toalha", C_FURN, F[Furniture.T_GING], F[Furniture.T_GING], F[Furniture.T_OAKD], 0xD84A4A, M_SOFT, 4f, FU or (Furniture.S_TCLOTH shl 8), "Mesa com toalha xadrez que cai pelos lados. Junte várias para crescer.")
        blk("Mesa Redonda", C_FURN, F[Furniture.T_OAK], F[Furniture.T_OAKD], F[Furniture.T_OAKD], 0xD6A068, M_WOOD, 4f, FU or (Furniture.S_TROUND shl 8), "Mesa redonda de madeira com pé central.")
        blk("Mesa Redonda de Mármore", C_FURN, F[Furniture.T_MARB], F[Furniture.T_MARB], F[Furniture.T_GOLD], 0xE8E4DE, M_STONE, 6f, FU or (Furniture.S_TROUNDM shl 8), "Mesa redonda de mármore com pé dourado.")
        blk("Mesa de Centro", C_FURN, F[Furniture.T_BIRCH], F[Furniture.T_OAKD], F[Furniture.T_OAKD], 0xEBDDB0, M_WOOD, 4f, FU or (Furniture.S_COFFEE shl 8), "Mesinha baixa de sala com prateleira embaixo.")
        blk("Escrivaninha", C_FURN, F[Furniture.T_OAK], F[Furniture.T_OAKD], F[Furniture.T_OAKD], 0xC8935A, M_WOOD, 4f, FU or (Furniture.S_DESK shl 8), "Escrivaninha com laterais e gaveta. Junte várias para uma bancada comprida.")
        blk("Estante de Livros", C_FURN, F[Furniture.T_OAK], F[Furniture.T_OAKD], F[Furniture.T_OAKD], 0xB5503C, M_WOOD, 5f, FU or (Furniture.S_SHELF shl 8), "Estante com 3 prateleiras cheias de livros coloridos. Junte lado a lado.")
        blk("Armário", C_FURN, F[Furniture.T_OAK], F[Furniture.T_OAKD], F[Furniture.T_OAKD], 0xB87D45, M_WOOD, 5f, FU or (Furniture.S_WARD shl 8), "Guarda-roupa com duas portas, painéis e maçanetas douradas.")
        blk("Cômoda", C_FURN, F[Furniture.T_OAK], F[Furniture.T_OAKD], F[Furniture.T_OAKD], 0xC8935A, M_WOOD, 5f, FU or (Furniture.S_DRESS shl 8), "Cômoda com 3 gavetas e puxadores dourados. Dá pra pôr abajur, TV e plantas em cima.")
        blk("Geladeira", C_FURN, F[Furniture.T_WHITE], F[Furniture.T_WHITE], F[Furniture.T_BLACK], 0xF6F6F2, M_METAL, 6f, FU or (Furniture.S_FRIDGE shl 8), "Geladeira branca com freezer, puxadores e ímãs.")
        blk("Computador", C_FURN, F[Furniture.T_BLACK], F[Furniture.T_SCREEN], F[Furniture.T_BLACK], 0x2E5FA8, M_METAL, 3f, FU or (Furniture.S_PC shl 8), "Monitor com tela acesa, teclado, mouse e caixinhas de som. Fica certinho em cima de mesas.")
        blk("Televisão", C_FURN, F[Furniture.T_BLACK], F[Furniture.T_TVSCR], F[Furniture.T_BLACK], 0x2A2C30, M_METAL, 3f, FU or (Furniture.S_TV shl 8), "TV de tela plana passando uma paisagem. Fica certinha em cima de cômodas e mesas.")
        blk("Abajur", C_FURN, F[Furniture.T_SHADE], F[Furniture.T_SHADE], F[Furniture.T_GOLD], 0xFFE9B0, M_SOFT, 2f, FU or F_LIGHT or (Furniture.S_LAMP shl 8), "Abajur dourado que ilumina com luz quentinha.")
        blk("Vaso de Planta", C_FURN, F[Furniture.T_LEAFA], F[Furniture.T_POT], F[Furniture.T_POT], 0x4F9F3C, M_SOFT, 2f, FU or (Furniture.S_PLANT shl 8), "Vasinho de barro com uma plantinha de folhas grandes.")
        blk("Tapete Vermelho", C_FURN, F[Furniture.T_RUGR], F[Furniture.T_RUGR], F[Furniture.T_RUGR], 0xC8444E, M_SOFT, 2f, FU or (Furniture.S_RUGR shl 8), "Tapete macio com borda e losango.")
        blk("Tapete Azul", C_FURN, F[Furniture.T_RUGB], F[Furniture.T_RUGB], F[Furniture.T_RUGB], 0x4C74C4, M_SOFT, 2f, FU or (Furniture.S_RUGB shl 8), "Tapete macio com borda e losango.")
        // ---- vidros finos (lisos, sem moldura: blocos vizinhos se unem sem marca) ----
        uni("Vidro Fino", C_BUILD, bt(BuildArt.GLASS_THIN, 0xD8F2FF), 0xCDEAF5, M_GLASS, 2f, PANE, "Vidro fino e liso: bem transparente, emenda sem marca.")
        uni("Vidro Fino Escuro", C_BUILD, bt(BuildArt.GLASS_THIN, 0x4A4256, -1, 300), 0x4A4256, M_GLASS, 2f, PANE, "Vidro fino fumê e liso.")
        for (i in 0 until 16) { val c = DYE[i]
            uni("Vidro Fino " + DM[i], C_BUILD, bt(BuildArt.GLASS_THIN, c, -1, 320), c, M_GLASS, 2f, PANE, "Vidro fino colorido e liso.") }
        // ---- mais janelas (todas translúcidas; moldura e vidraça lisas) ----
        val GL = 0xBFE6F5
        uni("Janela Sem Moldura", C_BUILD, bt(BuildArt.JAN_FRAMELESS, 0xB8C0C8, GL), 0xCDEEFF, M_GLASS, 2f, PLATE, "Vidro liso com só quatro presilhas de metal nos cantos.")
        uni("Janela de Correr Alumínio", C_BUILD, bt(BuildArt.JAN_SLIDE, 0xB8C0C8, GL), 0xB8C0C8, M_GLASS, 2f, PLATE, "Duas folhas grandes de vidro que correm, com puxador.")
        uni("Janela de Correr Preta", C_BUILD, bt(BuildArt.JAN_SLIDE, 0x2E3138, GL), 0x2E3138, M_GLASS, 2f, PLATE, "Janela de correr com perfil preto.")
        uni("Janela de Correr Branca", C_BUILD, bt(BuildArt.JAN_SLIDE, 0xF2F2EE, GL), 0xF2F2EE, M_GLASS, 2f, PLATE, "Janela de correr com perfil branco.")
        uni("Janela de Correr Madeira", C_BUILD, bt(BuildArt.JAN_SLIDE, 0x8B6B43, GL), 0x8B6B43, M_GLASS, 2f, PLATE, "Janela de correr com perfil de madeira.")
        uni("Janela Colonial Branca", C_BUILD, bt(BuildArt.JAN_COLONIAL, 0xF2F2EE, GL), 0xF2F2EE, M_GLASS, 2f, PLATE, "Janela colonial com seis vidraças.")
        uni("Janela Colonial Madeira", C_BUILD, bt(BuildArt.JAN_COLONIAL, 0x8B6B43, GL), 0x8B6B43, M_GLASS, 2f, PLATE, "Janela colonial de madeira com seis vidraças.")
        uni("Persiana de Madeira", C_BUILD, bt(BuildArt.JAN_LOUVER, 0x9A7448, GL), 0x9A7448, M_GLASS, 2f, PLATE, "Persiana de ripas: deixa ver pelas frestas.")
        uni("Persiana Branca", C_BUILD, bt(BuildArt.JAN_LOUVER, 0xEDEBE4, GL), 0xEDEBE4, M_GLASS, 2f, PLATE, "Persiana branca de ripas.")
        uni("Escotilha de Ferro", C_BUILD, bt(BuildArt.JAN_PORTHOLE, 0xB0B6BE, GL), 0xB0B6BE, M_GLASS, 2f, PLATE, "Janela redonda de navio, com aro de ferro e rebites.")
        uni("Escotilha de Latão", C_BUILD, bt(BuildArt.JAN_PORTHOLE, 0xC8A040, GL), 0xC8A040, M_GLASS, 2f, PLATE, "Janela redonda de navio, com aro de latão.")
        uni("Janela Gradeada", C_BUILD, bt(BuildArt.JAN_BARS, 0x2E3138, GL), 0x2E3138, M_GLASS, 2f, PLATE, "Vidro com grade de ferro.")
        uni("Vitral Azul e Âmbar", C_BUILD, bt(BuildArt.VITRAL, 0x2F63D0, 0xE0A030), 0x2F63D0, M_GLASS, 2f, PLATE, "Vitral de pedaços coloridos presos por chumbo.")
        uni("Vitral Rosa e Verde", C_BUILD, bt(BuildArt.VITRAL, 0xE86AA0, 0x4FB070), 0xE86AA0, M_GLASS, 2f, PLATE, "Vitral de pedaços coloridos presos por chumbo.")
        uni("Vitral Roxo e Ciano", C_BUILD, bt(BuildArt.VITRAL, 0x8A4FD0, 0x4FD0D0), 0x8A4FD0, M_GLASS, 2f, PLATE, "Vitral de pedaços coloridos presos por chumbo.")
        uni("Vitral Vermelho e Dourado", C_BUILD, bt(BuildArt.VITRAL, 0xD8403A, 0xF2C93A), 0xD8403A, M_GLASS, 2f, PLATE, "Vitral de pedaços coloridos presos por chumbo.")
        // ---- Construção v2: bloco de vidro, mais telhados, cumeeiras, lajes e escadas (ids novos sempre no fim!) ----
        uni("Bloco de Vidro", C_BUILD, bt(BuildArt.GLASS, 0xD8F2FF), 0xCDEAF5, M_GLASS, 2f, F_GLASS, "Cubo inteiro de vidro liso, para aquários e vitrines.")
        fun last(): Def = list[list.size - 1]
        fun rot4(base: Def, sub0: Int) {
            val ids = IntArray(4); ids[0] = base.id
            for (f in 1..3) {
                val id = blk(base.name + " " + (f + 1), base.cat, base.top, base.side, base.bot, base.color, base.mat, base.hard, shp(sub0 + f), base.desc)
                hidden.add(id); ids[f] = id
            }
            rotIds[base.id] = ids
        }
        val roofs = ArrayList<Def>()
        for (n in arrayOf("Telhado Vermelho", "Telhado Azul", "Telhado Verde")) { val d = list.firstOrNull { it.name == n }; if (d != null) roofs.add(d) }
        fun newRoof(n: String, c: Int, tile: Int = -1) {
            val t = if (tile >= 0) tile else bt(BuildArt.SHINGLE, c)
            blk(n, C_BUILD, t, t, t, c, M_STONE, 8f, shp(BuildShapes.S_ROOF), ROOF_DESC); roofs.add(last())
        }
        newRoof("Telhado Preto", 0x3A3C44)
        newRoof("Telhado de Barro", 0xC4683F)
        newRoof("Telhado de Ardósia", 0x5A5E6A)
        newRoof("Telhado Marrom", 0x7A4A32)
        newRoof("Telhado Amarelo", 0xE0B040)
        newRoof("Telhado Branco", 0xE8E8E8)
        newRoof("Telhado Rosa", 0xE88AA8)
        newRoof("Telhado de Cobre Oxidado", 0x4FB59A)
        newRoof("Telhado de Palha", 0xE6C65A, pt(NatArt.PALHA))
        for (d in roofs) rot4(d, BuildShapes.S_ROOF)
        for (n in arrayOf("Telhado Vermelho", "Telhado Azul", "Telhado Verde", "Telhado Preto", "Telhado de Barro", "Telhado de Palha")) {
            val d = list.firstOrNull { it.name == n } ?: continue
            val cn = n.replace("Telhado", "Cumeeira")
            val a = blk(cn, C_BUILD, d.top, d.side, d.bot, d.color, M_STONE, 8f, shp(BuildShapes.S_RIDGE), "Telhado de duas águas num bloco só: a crista corre pra onde você olha.")
            val b = blk(cn + " 2", C_BUILD, d.top, d.side, d.bot, d.color, M_STONE, 8f, shp(BuildShapes.S_RIDGE + 1), "Telhado de duas águas num bloco só.")
            hidden.add(b); ridgeAlt[a] = b
        }
        val mats = arrayOf("Tábua de Abeto", "Tábua de Bétula", "Tábua de Selva", "Tábua de Acácia", "Tábua de Carvalho Escuro", "Tábua de Cerejeira",
            "Pedregulho", "Pedra Lisa", "Tijolos de Pedra", "Granito Polido", "Diorito Polido", "Arenito Liso", "Quartzo", "Tijolos de Quartzo",
            "Ardósia Polida", "Tijolos do Nether", "Gesso Branco", "Concreto Branco", "Terracota", "Piso de Madeira")
        for (mn in mats) {
            val m = list.firstOrNull { it.name == mn } ?: continue
            val nm = if (mn.startsWith("Tábua de ")) mn.substring(9) else mn
            val sl = blk("Laje de " + nm, C_BUILD, m.top, m.side, m.bot, m.color, m.mat, m.hard, shp(BuildShapes.S_SLAB), "Laje de meio bloco. Coloque embaixo de um teto pra ela ficar virada pra cima.")
            val st = blk("Laje de " + nm + " (cima)", C_BUILD, m.top, m.side, m.bot, m.color, m.mat, m.hard, shp(BuildShapes.S_SLABT), "Laje de meio bloco, em cima.")
            hidden.add(st); slabTop[sl] = st
        }
        for (mn in mats) {
            val m = list.firstOrNull { it.name == mn } ?: continue
            val nm = if (mn.startsWith("Tábua de ")) mn.substring(9) else mn
            blk("Escada de " + nm, C_BUILD, m.top, m.side, m.bot, m.color, m.mat, m.hard, shp(BuildShapes.S_STAIR), "Escada de degrau único: o lado alto fica pro lado que você olha.")
            rot4(last(), BuildShapes.S_STAIR)
        }

        // ================= CONSTRUÇÃO FOFINHA (v3): ids novos sempre no fim! =================
        fun pc(i: Int) = mixc(DYE[i], 0xFFFFFF, 0.30f)
        val GL2 = 0xBFE6F5
        // ---- vidros especiais (cubo + fino dividem o tile; tiles de vidro seguidos) ----
        fun gstyle(n: String, pat: Int, c: Int, acc: Int = -1, sc: Int = 0, desc: String) {
            val t = bt(pat, c, acc, sc)
            uni("Vidro $n", C_BUILD, t, c, M_GLASS, 2f, F_GLASS, "$desc (cubo inteiro)")
            uni("Vidro Fino $n", C_BUILD, t, c, M_GLASS, 2f, PANE, "$desc (fino, emenda com os vizinhos)")
        }
        gstyle("Facetado", BuildArt.GL_FACET, 0xD8F2FF, desc = "Vidro lapidado em losangos que brilham")
        gstyle("de Bolhas", BuildArt.GL_BUBBLE, 0xCFF0FF, desc = "Vidro com bolhinhas de sabão")
        gstyle("Fosco", BuildArt.GL_FROST, 0xF2F8FF, sc = 100, desc = "Vidro leitoso e fosco, deixa a luz passar")
        gstyle("Estrelado", BuildArt.GL_STAR, 0xDCE4FF, desc = "Vidro com estrelinhas cintilantes")
        gstyle("Quadriculado", BuildArt.GL_GRID, 0xD8F2FF, desc = "Vidro de quadradinhos")
        gstyle("de Gotas", BuildArt.GL_DROPS, 0xCFEFFF, desc = "Vidro com gotinhas de chuva")
        gstyle("Arco-Íris", BuildArt.GL_RAINBOW, 0xFFB8D8, 0x9EDCFF, desc = "Vidro com reflexo de arco-íris")
        gstyle("de Gelo", BuildArt.GL_ICE, 0xCFEFFF, desc = "Vidro rachadinho como gelo")
        gstyle("Rosa Cintilante", BuildArt.GL_STAR, 0xFFC4DD, sc = 260, desc = "Vidro rosa com estrelinhas")
        gstyle("Lilás Facetado", BuildArt.GL_FACET, 0xD2B8FF, sc = 260, desc = "Vidro lilás lapidado")
        gstyle("Menta de Bolhas", BuildArt.GL_BUBBLE, 0xB8F0D8, sc = 260, desc = "Vidro menta com bolhinhas")
        gstyle("Âmbar Quadriculado", BuildArt.GL_GRID, 0xFFD27A, sc = 260, desc = "Vidro âmbar de quadradinhos")
        // ---- janelas fofinhas ----
        val pn = arrayOf("Rosa", "Menta", "Lavanda", "Céu", "Amarela", "Preta", "Vermelha", "Coral")
        val pcol = intArrayOf(0xFFB6D0, 0xA8E6C8, 0xCDB8F5, 0x9FD0F5, 0xFFE08A, 0x3A3C44, 0xE5584A, 0xFF9C84)
        for (i in pn.indices) uni("Janela " + pn[i], C_BUILD, bt(BuildArt.WINDOW, pcol[i], GL2), pcol[i], M_GLASS, 2f, PLATE, "Janela com moldura gordinha e vidraças brilhantes.")
        for (i in 0 until 5) uni("Janela Colonial " + pn[i], C_BUILD, bt(BuildArt.JAN_COLONIAL, pcol[i], GL2), pcol[i], M_GLASS, 2f, PLATE, "Janela colonial com seis vidraças.")
        for (i in 0 until 6) uni("Janela em Arco " + pn[i], C_BUILD, bt(BuildArt.JAN_ARCH, pcol[i], GL2), pcol[i], M_GLASS, 2f, PLATE, "Janela com arco no topo.")
        for (i in 0 until 6) uni("Janela de Coração " + pn[i], C_BUILD, bt(BuildArt.JAN_HEART, pcol[i], GL2), pcol[i], M_GLASS, 2f, PLATE, "Vidraça em forma de coração.")
        for (i in 0 until 6) uni("Janela de Estrela " + pn[i], C_BUILD, bt(BuildArt.JAN_STAR, pcol[i], GL2), pcol[i], M_GLASS, 2f, PLATE, "Vidraça em forma de estrela.")
        uni("Vitral Verde e Turquesa", C_BUILD, bt(BuildArt.VITRAL, 0x3FBF6A, 0x3FC8D8), 0x3FBF6A, M_GLASS, 2f, PLATE, "Vitral de pedaços coloridos presos por chumbo.")
        uni("Vitral Rosa Pastel", C_BUILD, bt(BuildArt.VITRAL, 0xFFA8CC, 0xFFE08A), 0xFFA8CC, M_GLASS, 2f, PLATE, "Vitral doce em tons pastel.")
        uni("Vitral Azul Céu", C_BUILD, bt(BuildArt.VITRAL, 0x6FB4F5, 0xBFA8FF), 0x6FB4F5, M_GLASS, 2f, PLATE, "Vitral em tons de céu.")
        uni("Vitral Laranja e Lima", C_BUILD, bt(BuildArt.VITRAL, 0xFF9A3C, 0xA8E05A), 0xFF9A3C, M_GLASS, 2f, PLATE, "Vitral alegre de cítricos.")
        // ---- tijolos, tábuas pintadas, azulejos metrô e alvenaria nas 16 cores ----
        for (i in 0 until 16) { val c = pc(i); uni("Tijolo " + DM[i], C_BUILD, bt(BuildArt.TIJOLO, c), c, M_STONE, 8f, 0, "Tijolos coloridos assentados em aparelho flamengo, alternando peças compridas e curtas.") }
        for (i in 0 until 16) { val c = pc(i); uni("Tábua Pintada " + DF[i], C_BUILD, bt(BuildArt.PAINTED, c), c, M_WOOD, 6f, 0, "Tábuas verticais pintadas, com as juntas cobertas por sarrafos pregados.") }
        for (i in 0 until 16) { val c = pc(i); uni("Azulejo Metrô " + DM[i], C_BUILD, bt(BuildArt.SUBWAY, c), c, M_STONE, 6f, 0, "Azulejo retangular liso e brilhante, assentado em grade.") }
        for (i in 0 until 16) { val c = pc(i); uni("Alvenaria " + DF[i], C_BUILD, bt(BuildArt.LONGBRICK, c), c, M_STONE, 9f, 0, "Pedras retangulares de tamanhos diferentes, assentadas com argamassa.") }
        for (i in 0 until 16) { val c = pc(i); uni("Gema " + DF[i], C_BUILD, bt(BuildArt.GEM, c), c, M_STONE, 12f, 0, "Gema lapidada e brilhante.") }
        for (i in 0 until 16) { val c = pc(i); uni("Acolchoado " + DM[i], C_BUILD, bt(BuildArt.QUILT, c), c, M_SOFT, 3f, 0, "Almofadinhas macias.") }
        for (i in 0 until 16) { val c = pc(i); uni("Reboco " + DM[i], C_BUILD, btv(BuildArt.PLASTER, c), c, M_STONE, 6f) }
        for (i in 0 until 16) { val c = pc(i); uni("Lambri " + DM[i], C_BUILD, bt(BuildArt.PANEL, c), c, M_WOOD, 5f, 0, "Painéis embutidos para paredes.") }
        for (i in 0 until 8) { val c = pc(i); uni("Telhado de Ripas " + DM[i], C_BUILD, bt(BuildArt.SHAKE, c), c, M_WOOD, 6f, shp(BuildShapes.S_ROOF), ROOF_DESC); roofs.add(last()); rot4(last(), BuildShapes.S_ROOF) }
        for (i in 8 until 16) { val c = pc(i); uni("Telhado Cerâmico " + DM[i], C_BUILD, bt(BuildArt.SHINGLE, c), c, M_STONE, 6f, shp(BuildShapes.S_ROOF), ROOF_DESC); rot4(last(), BuildShapes.S_ROOF) }
        // ---- pisos e paredes especiais ----
        uni("Piso Xadrez Rosa", C_BUILD, bt(BuildArt.CHECKER, 0xFFB6D0, 0xFFF4E4), 0xFFB6D0, M_STONE, 6f)
        uni("Piso Xadrez Azul", C_BUILD, bt(BuildArt.CHECKER, 0x9FD0F5, 0xFFF4E4), 0x9FD0F5, M_STONE, 6f)
        uni("Piso Xadrez Menta", C_BUILD, bt(BuildArt.CHECKER, 0xA8E6C8, 0xFFF4E4), 0xA8E6C8, M_STONE, 6f)
        uni("Piso Xadrez Preto e Branco", C_BUILD, bt(BuildArt.CHECKER, 0x4A4658, 0xFAF8F4), 0x4A4658, M_STONE, 6f)
        uni("Piso Xadrez Vermelho", C_BUILD, bt(BuildArt.CHECKER, 0xE5584A, 0xFFF4E4), 0xE5584A, M_STONE, 6f)
        for (n in arrayOf("Carvalho" to 0xDDB57A, "Cerejeira" to 0xE0A0A0, "Escuro" to 0x8A6040, "Bétula" to 0xEFDCA8)) uni("Piso Espinha " + n.first, C_BUILD, bt(BuildArt.HERRING, n.second), n.second, M_WOOD, 5f, 0, "Piso de madeira em espinha de peixe.")
        for (n in arrayOf("Água" to 0x9CCFE8, "Rosa" to 0xFFB6D0, "Menta" to 0xA8E6C8, "Branco" to 0xF4F4F0, "Lavanda" to 0xCDB8F5)) uni("Ladrilho Hexagonal " + n.first, C_BUILD, bt(BuildArt.HEXTILE, n.second), n.second, M_STONE, 6f)
        for (n in arrayOf("Cinza" to 0xB9C2D0, "Creme" to 0xF0E4C8, "Rosa" to 0xF4C0CC, "Verde" to 0xBFE0B0)) uni("Caminho de Pedras " + n.first, C_BUILD, bt(BuildArt.STEPPING, n.second), n.second, M_STONE, 8f)
        for (n in arrayOf("Cinza" to 0xB9C2D0, "Areia" to 0xE8D4A8, "Rosa" to 0xF0B8C0, "Água" to 0xA8D0E0)) uni("Seixos " + n.first, C_BUILD, bt(BuildArt.PEBBLE, n.second), n.second, M_STONE, 8f, 0, "Pedrinhas de rio redondinhas.")
        for (n in arrayOf("Branco" to 0xFAF6EE, "Rosa" to 0xFFD6E4, "Céu" to 0xCFE4FA, "Menta" to 0xCFF0DC)) uni("Enxaimel " + n.first, C_BUILD, bt(BuildArt.TIMBER, n.second, 0x8A5E3C), n.second, M_WOOD, 6f, 0, "Parede de gesso com vigas de madeira, estilo casinha de conto de fadas.")
        for (n in arrayOf("Carvalho" to 0xC9A06B, "Abeto" to 0x9A7448, "Bétula" to 0xE8D8A0, "Cerejeira" to 0xDDA5A0, "Escuro" to 0x6A4A32)) {
            val tp = bt(BuildArt.RINGS, n.second)
            blk("Toras " + n.first, C_BUILD, tp, bt(BuildArt.LOGWALL, n.second), tp, n.second, M_WOOD, 7f, 0, "Parede de toras empilhadas.")
        }
        for (n in arrayOf("Ferro" to 0xC8CED8, "Cobre" to 0xE09A70, "Ouro" to 0xF6D860, "Rosa" to 0xF4B0C0, "Azul" to 0x8CB8E8, "Verde" to 0x8CD8A8)) uni("Chapa Ondulada " + n.first, C_BUILD, bt(BuildArt.CORRUG, n.second), n.second, M_METAL, 10f, 0, "Chapa de metal ondulada, parafusada nas cristas.")
        for (n in arrayOf("Rosa" to 0xF4B8D0, "Azul" to 0x9CCFE8, "Amarelo" to 0xFFE08A, "Menta" to 0xA8E6C8)) uni("Losangos " + n.first, C_BUILD, bt(BuildArt.DIAMOND, n.second), n.second, M_STONE, 8f, 0, "Almofadas em losango.")
        for (n in arrayOf("Rosa" to 0xFFB6D0, "Azul" to 0x9FD0F5, "Menta" to 0xA8E6C8)) uni("Listrado " + n.first, C_BUILD, bt(BuildArt.STRIPES, n.second, 0xFFF4E4), n.second, M_SOFT, 3f, 0, "Papel de parede listrado.")
        uni("Azulejo Verde", C_BUILD, bt(BuildArt.AZ_DECOR, 0x3FAF7A, 0xF6F6F0), 0x3FAF7A, M_STONE, 6f)
        uni("Azulejo Rosa", C_BUILD, bt(BuildArt.AZ_DECOR, 0xE8789F, 0xF6F6F0), 0xE8789F, M_STONE, 6f)
        uni("Azulejo Amarelo", C_BUILD, bt(BuildArt.AZ_DECOR, 0xE8B030, 0xF6F6F0), 0xE8B030, M_STONE, 6f)
        uni("Azulejo Roxo", C_BUILD, bt(BuildArt.AZ_DECOR, 0x8A5FD0, 0xF6F6F0), 0x8A5FD0, M_STONE, 6f)
        uni("Azulejo Rosa Liso", C_BUILD, bt(BuildArt.AZ_PLAIN, 0xFFC4DD), 0xFFC4DD, M_STONE, 6f)
        uni("Azulejo Céu Liso", C_BUILD, bt(BuildArt.AZ_PLAIN, 0xBFE0FF), 0xBFE0FF, M_STONE, 6f)
        uni("Azulejo Menta Liso", C_BUILD, bt(BuildArt.AZ_PLAIN, 0xBFEAD4), 0xBFEAD4, M_STONE, 6f)
        // lajes e escadas dos materiais coloridos
        for (mn in arrayOf("Tijolo Rosa", "Tijolo Azul-claro", "Tábua Pintada Branca", "Tábua Pintada Rosa", "Alvenaria Cinza-clara", "Piso Espinha Carvalho", "Reboco Rosa")) {
            val m = list.firstOrNull { it.name == mn } ?: continue
            val sl = blk("Laje de " + mn, C_BUILD, m.top, m.side, m.bot, m.color, m.mat, m.hard, shp(BuildShapes.S_SLAB), "Laje de meio bloco. Coloque embaixo de um teto pra ela ficar virada pra cima.")
            val st = blk("Laje de " + mn + " (cima)", C_BUILD, m.top, m.side, m.bot, m.color, m.mat, m.hard, shp(BuildShapes.S_SLABT), "Laje de meio bloco, em cima.")
            hidden.add(st); slabTop[sl] = st
            blk("Escada de " + mn, C_BUILD, m.top, m.side, m.bot, m.color, m.mat, m.hard, shp(BuildShapes.S_STAIR), "Escada de degrau único: o lado alto fica pro lado que você olha.")
            rot4(last(), BuildShapes.S_STAIR)
        }
        buildFurn2()
    }

    // ================= MOBÍLIA v2 (FurnB): ids novos sempre no fim! =================
    /** paleta de tiles de cada móvel novo: [0] corpo, [1] corpo 2, [2] madeira, [3] madeira escura, [4] metal, [5] detalhe, [6] luz/vidro, [7] extra */
    val fpal = HashMap<Int, IntArray>()
    private val noPal = IntArray(8) { 1 }
    fun fp(id: Int): IntArray { defs; return fpal[id] ?: noPal }

    private fun buildFurn2() {
        val FUp = F_SHAPE or (7 shl 5)
        fun P(b: Int, b2: Int, w: Int, wd: Int, mt: Int, ac: Int, lg: Int, ex: Int) = intArrayOf(b, b2, w, wd, mt, ac, lg, ex)
        fun fb(name: String, sub: Int, p: IntArray, color: Int, mat: Int, hard: Float, desc: String, light: Boolean = false) {
            val id = blk(name, C_FURN, p[0], p[0], p[2], color, mat, hard, FUp or (if (light) F_LIGHT else 0) or (sub shl 8), desc)
            if (id != 0) fpal[id] = p
        }
        val F = ft
        val gold = F[Furniture.T_GOLD]; val steel = F[Furniture.T_STEEL]; val blkT = F[Furniture.T_BLACK]; val whiteT = F[Furniture.T_WHITE]
        val cream = F[Furniture.T_CREAM]; val marb = F[Furniture.T_MARB]; val marbK = F[Furniture.T_MARBK]
        val copper = tl(K_FLAT, 0xD98458, 0xB8683F); val chrome = tl(K_FLAT, 0xEEF2F6, 0xBCC8D4)
        // tecidos nas 16 cores (liso e capitonê)
        val fab = IntArray(16); val tuft = IntArray(16); val shd = IntArray(16)
        for (i in 0 until 16) {
            val c = mixc(DYE[i], 0xFFFFFF, 0.1f)
            fab[i] = tl(K_FABRIC, c, dk(c, 0.2f)); tuft[i] = tl(K_FABRIC, c, dk(c, 0.2f), 0, 0, 0, 1)
            shd[i] = tl(K_FABRIC, lt(DYE[i], 0.45f), lt(DYE[i], 0.25f))
        }
        // madeiras
        val WN = arrayOf("Carvalho", "Nogueira", "Ébano", "Bétula", "Branca", "Cerejeira", "Cinza", "Mel")
        val wl = intArrayOf(0xE0B070, 0x8A5A3A, 0x3A2C26, 0xEBDDB0, 0xF2F0EA, 0xC46A5A, 0x9AA0A4, 0xE8B86A)
        val wt = IntArray(8); val wtd = IntArray(8)
        for (i in 0 until 8) { wt[i] = tl(K_WGRAIN, wl[i], dk(wl[i], 0.14f), dk(wl[i], 0.42f)); wtd[i] = tl(K_WGRAIN, dk(wl[i], 0.28f), dk(wl[i], 0.4f), dk(wl[i], 0.6f)) }
        // esmaltes (eletros e louças coloridos)
        val EN = arrayOf("Branco", "Rosa", "Menta", "Azul", "Amarelo", "Vermelho", "Preto", "Lavanda")
        val enc = intArrayOf(0xF6F6F2, 0xFFB6D0, 0xA8E6C8, 0x9FD0F5, 0xFFE08A, 0xE5584A, 0x3A3C44, 0xCDB8F5)
        val en = IntArray(8) { tl(K_FLAT, enc[it], dk(enc[it], 0.1f)) }
        // cortinas
        val she = IntArray(8); val str = IntArray(8)
        val c8 = intArrayOf(0, 6, 3, 11, 13, 14, 4, 7)
        for (k in 0 until 8) { val i = c8[k]; she[k] = tl(K_FABRIC, lt(DYE[i], 0.72f), lt(DYE[i], 0.88f)); val c = mixc(DYE[i], 0xFFFFFF, 0.1f); str[k] = tl(K_STRIPE, c, 0xF6F2EA, dk(c, 0.25f), 0, 0, 1) }
        val lace = tl(K_FABRIC, 0xFFFFFF, 0xEFE8DC)
        // quadros, espelho, relógio, chama, água
        val artN = arrayOf("Pôr do Sol", "Mar Calmo", "Floresta", "Noite Estrelada", "Xadrez Azul e Amarelo", "Xadrez Vermelho", "Listras Vermelhas", "Listras Azuis", "Redemoinho Rosa", "Anéis Verdes")
        val art = intArrayOf(tl(K_GRAD, 0xFFC27A, 0xA878D8, 0xFF8FA8), tl(K_GRAD, 0x9FD8F5, 0x2A6DB8, 0), tl(K_GRAD, 0xCFF0A8, 0x3F8A4A, 0), tl(K_GRAD, 0x2A2A5A, 0xF2C94A, 0),
            tl(K_CHECK, 0xF4C94A, 0x3A66D2, 0, 0, 0, 2), tl(K_CHECK, 0xE5584A, 0xF6F2EA, 0, 0, 0, 3), tl(K_STRIPE, 0xE5584A, 0xF6F2EA, 0xA03A30), tl(K_STRIPE, 0x3A66D2, 0xF4C94A, 0x2A4A9A, 0, 0, 1),
            tl(K_SWIRL, 0xFF7FB5, 0xFFF0F7), tl(K_RING, 0x8FD0B8, 0xF6F2EA, 0x4A8F7A, 0, 0, 3))
        val mir = tl(K_GRAD, 0xEAF6FB, 0x9CC4D8, 0); val face = tl(K_RING, 0xFFFDF4, 0xE8E0CC, 0x8A6A3A, 0, 0, 2)
        val flame = tl(K_GLOW, 0xFFD27A, 0xFF9A3C, 0xE0601E, 0, 0, 0, 0, true)
        val water = tl(K_FLAT, 0x8ED8F0, 0x6BC4E4); val bubble = tl(K_FLAT, 0xF4FCFF, 0xDDF2FA)
        val stone = bt(BuildArt.SBRICK, 0xB4BAC4); val redBrick = bt(BuildArt.TIJOLO, 0xD8584A); val whiteBrick = bt(BuildArt.TIJOLO, 0xF4F4F0)
        // plantas, vasos, flores
        val gA = tl(K_FLAT, 0x3F9A4A, 0x5FC060); val gB = tl(K_FLAT, 0x5FA84A, 0x86D05A); val gC = tl(K_FLAT, 0x2F7A3A, 0x4FA050); val gD = tl(K_FLAT, 0x6FA84A, 0x8AC864)
        val gAut = tl(K_FLAT, 0xE07A26, 0xF0A040); val gCh = tl(K_FLAT, 0xF0A0C0, 0xFFC4DD)
        val potC = intArrayOf(0xC4693B, 0xF2F2EE, 0x4C74C4, 0xE8789F, 0x3A3C44, 0xE0B040)
        val PN = arrayOf("Barro", "Branco", "Azul", "Rosa", "Preto", "Amarelo")
        val pot = IntArray(6) { tl(K_FLAT, potC[it], dk(potC[it], 0.12f)) }
        val flC = intArrayOf(0xE5584A, 0xFFB6D0, 0xF4C94A, 0xFFFFFF, 0xA878D8, 0xFF9A3C)
        val FN = arrayOf("Rosas Vermelhas", "Rosas Rosa", "Girassóis", "Margaridas", "Lavandas", "Tulipas Laranja")
        val fl = IntArray(6) { tl(K_FLAT, flC[it], dk(flC[it], 0.12f)) }
        val globeT = tl(K_GRAD, 0x6DB6F5, 0x2A6DB8, 0)
        val c12 = intArrayOf(7, 8, 0, 15, 12, 11, 3, 9, 13, 14, 6, 10)
        val c6 = intArrayOf(0, 6, 3, 13, 4, 14)
        val warm = shd[4]

        // ---------- assentos ----------
        for (k in c12.indices) { val i = c12[k]
            fb("Sofá Moderno " + DM[i], FurnB.S_SOFAM, P(fab[i], tuft[i], wt[0], wtd[0], if (k % 2 == 0) blkT else gold, 0, 0, 0), DYE[i], M_SOFT, 4f, "Sofá baixo e moderno com almofadas e pés de metal. Junte em fila para um sofá comprido. Dá pra sentar!") }
        for (k in c8.indices) { val i = c8[k]
            fb("Sofá Chesterfield " + DM[i], FurnB.S_CHEST, P(fab[i], tuft[i], wt[1], wtd[1], gold, 0, 0, 0), DYE[i], M_SOFT, 4f, "Sofá clássico capitonê com braços enrolados e tachinhas douradas. Junte em fila. Dá pra sentar!") }
        for (k in c8.indices) { val i = c8[k]
            fb("Poltrona Orelhuda " + DF[i], FurnB.S_WING, P(fab[i], tuft[i], wt[1], wtd[1], gold, 0, 0, 0), DYE[i], M_SOFT, 4f, "Poltrona de encosto alto com abas laterais. Dá pra sentar!") }
        for (k in c12.indices) { val i = c12[k]
            fb("Puff " + DM[i], FurnB.S_POUF, P(fab[i], tuft[i], wt[0], wtd[0], gold, 0, 0, 0), DYE[i], M_SOFT, 3f, "Puff redondinho e fofo com botão no meio. Dá pra sentar!") }
        for (k in c8.indices) { val i = c8[k]
            fb("Cadeira Moderna " + DF[i], FurnB.S_CHAIRM, P(fab[i], tuft[i], wt[k % 8], wtd[k % 8], if (k % 2 == 0) steel else blkT, 0, 0, 0), DYE[i], M_WOOD, 4f, "Cadeira de design com assento e encosto estofados. Vira sozinha pra mesa. Dá pra sentar!") }
        for (k in c6.indices) { val i = c6[k]
            fb("Banqueta de Bar " + DF[i], FurnB.S_BAR, P(fab[i], tuft[i], wt[k % 8], wtd[k % 8], steel, 0, 0, 0), DYE[i], M_METAL, 4f, "Banqueta alta com apoio de pés, perfeita pra balcão e ilha de cozinha. Dá pra sentar!") }
        for (k in intArrayOf(0, 1, 6)) fb("Banco de Jardim " + WN[k], FurnB.S_BENCH, P(wt[k], wt[k], wt[k], wtd[k], blkT, 0, 0, 0), wl[k], M_WOOD, 4f, "Banco de ripas com laterais de ferro. Junte em fila pra um banco comprido. Dá pra sentar!")
        for (k in c8.indices) { val i = c8[k]
            fb("Divã " + DM[i], FurnB.S_CHAISE, P(fab[i], tuft[i], wt[1], wtd[1], gold, 0, 0, 0), DYE[i], M_SOFT, 4f, "Divã comprido com encosto elevado numa ponta. Dá pra deitar... quer dizer, sentar!") }

        // ---------- mesas ----------
        for (k in 0 until 8) fb("Mesa de Jantar " + WN[k], FurnB.S_DINING, P(wt[k], wt[k], wt[k], wtd[k], if (k % 2 == 0) blkT else steel, 0, 0, 0), wl[k], M_WOOD, 4f, "Mesa de jantar moderna com tampo grosso. Junte várias para uma mesa comprida.")
        for (k in 0 until 8) fb("Mesa de Cabeceira " + WN[k], FurnB.S_NIGHT, P(wt[k], wt[k], wt[k], wtd[k], gold, 0, 0, 0), wl[k], M_WOOD, 4f, "Criado-mudo com duas gavetas e puxadores dourados. Cabe um abajur em cima.")
        for (k in 0 until 8) fb("Mesa Lateral " + WN[k], FurnB.S_SIDE, P(wt[k], wt[k], wt[k], wtd[k], gold, 0, 0, 0), wl[k], M_WOOD, 4f, "Mesinha redonda de apoio ao lado do sofá.")
        for (k in 0 until 8) fb("Balcão de Cozinha " + EN[k], FurnB.S_COUNTER, P(en[k], en[k], en[k], wtd[1], gold, if (k % 2 == 0) marb else marbK, 0, 0), enc[k], M_STONE, 5f, "Balcão com portas, puxadores e tampo de mármore. Junte em fila pra cozinha comprida.")
        for (k in 0 until 4) fb("Aparador " + WN[k * 2], FurnB.S_BOARD, P(wt[k * 2], wt[k * 2], wt[k * 2], wtd[k * 2], gold, 0, 0, 0), wl[k * 2], M_WOOD, 4f, "Aparador baixo com três portas e pés de palito.")

        // ---------- armazenamento ----------
        for (k in 0 until 8) fb("Armário Alto " + WN[k], FurnB.S_TALL, P(wt[k], wt[k], wt[k], wtd[k], gold, 0, 0, 0), wl[k], M_WOOD, 5f, "Armário alto de duas portas.")
        for (k in 0 until 8) fb("Guarda-Roupa Moderno " + WN[k], FurnB.S_WARDM, P(wt[k], wt[k], wt[k], wtd[k], steel, 0, mir, 0), wl[k], M_WOOD, 5f, "Guarda-roupa de portas de correr, uma delas com espelho.")
        for (k in 0 until 6) fb("Estante Moderna " + WN[k], FurnB.S_SHELFM, P(fab[c12[k]], fab[c12[k]], wt[k], wtd[k], blkT, 0, 0, 0), wl[k], M_WOOD, 5f, "Estante aberta com livros e vasinhos. Junte lado a lado.")
        for (k in 0 until 4) fb("Cristaleira " + WN[k], FurnB.S_CABINET, P(wt[k], wt[k], wt[k], wtd[k], gold, 0, mir, 0), wl[k], M_WOOD, 5f, "Cristaleira com pratos e taças à mostra.")
        for (k in 0 until 4) fb("Baú " + WN[k], FurnB.S_TRUNK, P(wt[k], wt[k], wt[k], wtd[k], gold, 0, 0, 0), wl[k], M_WOOD, 5f, "Baú de madeira com cinta de metal e fecho dourado.")
        for (k in 0 until 8) fb("Armário Suspenso " + EN[k], FurnB.S_WALLCAB, P(en[k], en[k], en[k], wtd[1], gold, 0, 0, 0), enc[k], M_WOOD, 5f, "Armário de parede pra cozinha. Gruda na parede de trás.")

        // ---------- eletrodomésticos ----------
        fb("Geladeira Inox", FurnB.S_FRIDGE2, P(steel, steel, steel, steel, chrome, 0, 0, 0), 0xC8CED6, M_METAL, 6f, "Geladeira side by side de inox com dispenser de gelo.")
        fb("Geladeira Preta", FurnB.S_FRIDGE2, P(blkT, blkT, blkT, blkT, chrome, 0, 0, 0), 0x2A2C30, M_METAL, 6f, "Geladeira side by side preta com dispenser de gelo.")
        fb("Geladeira Branca", FurnB.S_FRIDGE2, P(whiteT, whiteT, whiteT, whiteT, chrome, 0, 0, 0), 0xF6F6F2, M_METAL, 6f, "Geladeira side by side branca com dispenser de gelo.")
        for (k in 0 until 8) fb("Geladeira Retrô " + EN[k], FurnB.S_FRIDGER, P(en[k], en[k], en[k], en[k], chrome, 0, 0, 0), enc[k], M_METAL, 6f, "Geladeira arredondada estilo anos 50, com puxadores cromados.")
        for (k in 0 until 8) fb("Frigobar " + EN[k], FurnB.S_MINI, P(en[k], en[k], en[k], en[k], chrome, 0, 0, 0), enc[k], M_METAL, 4f, "Frigobar pequeno. Cabe um micro-ondas em cima.")
        val st = arrayOf("Inox" to steel, "Branco" to whiteT, "Preto" to blkT, "Retrô Rosa" to en[1], "Retrô Menta" to en[2])
        for (q in st) fb("Fogão " + q.first, FurnB.S_STOVE, P(q.second, q.second, q.second, q.second, chrome, 0, 0, 0), 0xC8CED6, M_METAL, 5f, "Fogão com quatro bocas, forno com visor e botões.")
        val mc = arrayOf("Inox" to steel, "Branco" to whiteT, "Preto" to blkT, "Rosa" to en[1])
        for (q in mc) fb("Micro-ondas " + q.first, FurnB.S_MICRO, P(q.second, q.second, q.second, q.second, chrome, 0, 0, 0), 0xC8CED6, M_METAL, 3f, "Micro-ondas com porta e painel aceso. Fica em cima de balcões.")
        val wm = arrayOf("Branca" to whiteT, "Inox" to steel, "Preta" to blkT)
        for (q in wm) fb("Máquina de Lavar " + q.first, FurnB.S_WASHER, P(q.second, q.second, q.second, q.second, chrome, 0, whiteT, 0), 0xE4E6E8, M_METAL, 5f, "Lavadora de carga frontal com visor redondo.")
        for (k in 0 until 6) fb("Pia de Cozinha " + EN[k], FurnB.S_SINK, P(en[k], en[k], en[k], wtd[1], chrome, if (k % 2 == 0) marb else marbK, 0, 0), enc[k], M_STONE, 5f, "Pia com bacia de inox, torneira e armário embaixo.")
        fb("Coifa Inox", FurnB.S_HOOD, P(steel, steel, steel, steel, chrome, 0, whiteT, 0), 0xC8CED6, M_METAL, 4f, "Coifa de parede com luz embaixo.")
        fb("Coifa Preta", FurnB.S_HOOD, P(blkT, blkT, blkT, blkT, gold, 0, whiteT, 0), 0x2A2C30, M_METAL, 4f, "Coifa de parede preta com luz embaixo.")
        fb("Coifa de Cobre", FurnB.S_HOOD, P(copper, copper, copper, copper, gold, 0, whiteT, 0), 0xD98458, M_METAL, 4f, "Coifa de cobre, charmosa, com luz embaixo.")

        // ---------- banheiro ----------
        for (k in intArrayOf(0, 1, 2, 3)) fb("Vaso Sanitário " + EN[k], FurnB.S_TOILET, P(en[k], en[k], 0, 0, chrome, 0, 0, 0), enc[k], M_STONE, 4f, "Vaso com caixa acoplada e descarga cromada.")
        for (k in intArrayOf(0, 1, 2, 3)) fb("Pia de Banheiro " + EN[k], FurnB.S_BASIN, P(en[k], cream, 0, 0, chrome, 0, 0, 0), enc[k], M_STONE, 4f, "Pia com coluna e torneira.")
        for (k in intArrayOf(0, 1, 2, 3)) fb("Banheira " + EN[k], FurnB.S_TUB, P(en[k], en[k], 0, 0, if (k % 2 == 0) gold else chrome, 0, bubble, water), enc[k], M_STONE, 4f, "Banheira cheia de água com espuma, pezinhos e torneira.")

        // ---------- camas ----------
        for (k in c12.indices) { val i = c12[k]
            fb("Cama Moderna " + DF[i], FurnB.S_BEDM, P(fab[i], tuft[i], wt[k % 8], wtd[k % 8], gold, 0, 0, 0), DYE[i], M_WOOD, 4f, "Cama com cabeceira capitonê, edredom e travesseiros. Junte em fila pra uma cama mais comprida.") }
        val cribC = intArrayOf(6, 3, 4)
        for (k in 0 until 3) fb("Berço " + WN[k * 3], FurnB.S_CRIB, P(fab[cribC[k]], tuft[cribC[k]], wt[k * 3], wtd[k * 3], gold, shd[cribC[k]], shd[4], 0), wl[k * 3], M_WOOD, 4f, "Berço com grades e um móbile de bichinhos.")

        // ---------- cortinas ----------
        for (i in 0 until 16) fb("Cortina Aberta " + DF[i], FurnB.S_CUROPEN, P(fab[i], tuft[i], wt[0], wtd[0], if (i % 2 == 0) gold else blkT, gold, 0, 0), DYE[i], M_SOFT, 2f, "Cortina aberta pros lados, presa com laço. Fica na parede de trás.")
        for (i in 0 until 16) fb("Cortina Fechada " + DF[i], FurnB.S_CURCLOSED, P(fab[i], tuft[i], wt[0], wtd[0], if (i % 2 == 0) gold else blkT, gold, 0, 0), DYE[i], M_SOFT, 2f, "Cortina fechada com dobras. Junte lado a lado pra janelas largas.")
        for (k in 0 until 8) fb("Cortina Transparente " + DF[c8[k]], FurnB.S_CURSHEER, P(she[k], lace, wt[0], wtd[0], chrome, gold, 0, 0), DYE[c8[k]], M_SOFT, 2f, "Voal leve com barra de renda. Junte lado a lado.")
        for (k in 0 until 8) fb("Cortina com Babado " + DF[c8[k]], FurnB.S_CURVAL, P(fab[c8[k]], tuft[c8[k]], wt[0], wtd[0], gold, gold, 0, 0), DYE[c8[k]], M_SOFT, 2f, "Cortina aberta com bandô de babados e franjinhas douradas.")
        for (k in 0 until 8) fb("Persiana Romana " + DF[c8[k]], FurnB.S_ROMAN, P(fab[c8[k]], tuft[c8[k]], wt[k % 8], wtd[k % 8], gold, cream, 0, 0), DYE[c8[k]], M_SOFT, 2f, "Persiana de tecido em faixas, com cordinha.")
        for (k in 0 until 8) fb("Cortina Listrada " + DF[c8[k]], FurnB.S_CURCLOSED, P(str[k], fab[c8[k]], wt[0], wtd[0], gold, gold, 0, 0), DYE[c8[k]], M_SOFT, 2f, "Cortina fechada de listras verticais.")

        // ---------- lâmpadas de verdade (não são blocos!) ----------
        for (i in 0 until 12) fb("Abajur " + DM[c12[i]], FurnB.S_TLAMP, P(0, 0, if (i % 2 == 0) gold else wt[i % 8], 0, gold, 0, shd[c12[i]], 0), DYE[c12[i]], M_SOFT, 2f, "Abajur de mesa com cúpula acesa. Fica em cima de móveis.", true)
        for (k in c8.indices) fb("Luminária de Chão " + DF[c8[k]], FurnB.S_FLOOR, P(0, 0, 0, 0, if (k % 2 == 0) blkT else gold, 0, shd[c8[k]], 0), DYE[c8[k]], M_METAL, 2f, "Luminária alta de haste fina e cúpula acesa.", true)
        for (k in intArrayOf(0, 4, 6, 3)) fb("Luminária Arco " + DF[k], FurnB.S_ARC, P(0, 0, marb, 0, steel, 0, shd[k], 0), DYE[k], M_METAL, 2f, "Luminária de arco com base de mármore.", true)
        for (k in 0 until 8) fb("Pendente " + EN[k], FurnB.S_PEND, P(0, 0, en[k], 0, blkT, 0, warm, 0), enc[k], M_METAL, 2f, "Luminária pendente que pende do teto, com lâmpada acesa embaixo.", true)
        val lm = arrayOf("Dourado" to gold, "Prateado" to steel, "Preto" to blkT)
        for (q in lm) fb("Lustre " + q.first, FurnB.S_CHAND, P(0, whiteT, 0, 0, q.second, 0, flame, 0), 0xF2CF68, M_METAL, 2f, "Lustre de braços com velas acesas e pingentes de cristal.", true)
        val sc = arrayOf(Triple("Dourada", gold, 4), Triple("Preta", blkT, 4), Triple("de Cobre", copper, 4), Triple("Prateada", steel, 4), Triple("Rosa", gold, 6), Triple("Azul", chrome, 3))
        for (q in sc) fb("Arandela " + q.first, FurnB.S_SCONCE, P(0, 0, 0, 0, q.second, 0, if (q.third == 4) warm else shd[q.third], 0), 0xFFE9B0, M_METAL, 2f, "Luz de parede com cúpula acesa. Gruda na parede de trás.", true)
        val dl = arrayOf(Triple("Preta", blkT, 6), Triple("Branca", whiteT, 0), Triple("Prateada", steel, 8), Triple("Dourada", gold, 4), Triple("de Cobre", copper, 1), Triple("Vermelha", en[5], 14))
        for (q in dl) fb("Luminária de Mesa " + q.first, FurnB.S_DESKL, P(0, 0, 0, 0, q.second, q.second, warm, 0), 0xFFE9B0, M_METAL, 2f, "Luminária articulada de escritório. Fica em cima de mesas.", true)
        for (i in 0 until 10) { val j = intArrayOf(14, 1, 4, 6, 3, 13, 10, 2, 9, 0)[i]
            fb("Lanterna de Papel " + DF[j], FurnB.S_PAPER, P(0, 0, 0, wtd[1], 0, gold, shd[j], 0), DYE[j], M_SOFT, 2f, "Lanterna de papel que pende do teto, brilhando por dentro.", true) }
        for (q in lm) fb("Candelabro " + q.first, FurnB.S_CANDLE, P(0, 0, 0, 0, q.second, 0, flame, 0), 0xF2CF68, M_METAL, 2f, "Candelabro com três velinhas acesas. Fica em cima de móveis.", true)
        val mn = arrayOf("Amarela" to 4, "Azul" to 3, "Rosa" to 6, "Verde" to 13)
        for (q in mn) fb("Luz Noturna Lua " + q.first, FurnB.S_MOON, P(0, 0, 0, 0, whiteT, shd[q.second], shd[q.second], 0), DYE[q.second], M_SOFT, 2f, "Luz noturna em forma de lua com estrelinha. Fica em cima de móveis.", true)
        val lv = intArrayOf(14, 10, 3, 13, 1, 6)
        for (k in lv.indices) fb("Lâmpada de Lava " + DF[lv[k]], FurnB.S_LAVA, P(0, 0, 0, 0, if (k % 2 == 0) blkT else steel, shd[lv[k]], shd[lv[k]], 0), DYE[lv[k]], M_METAL, 2f, "Lâmpada de lava com bolhas brilhando. Fica em cima de móveis.", true)

        // ---------- decoração ----------
        for (k in 0 until 6) fb("Palmeira em Vaso " + PN[k], FurnB.S_PALM, P(gA, gB, pot[k], wtd[0], 0, 0, 0, 0), 0x3F9A4A, M_SOFT, 2f, "Palmeira alta de folhas longas num vaso.")
        for (k in 0 until 6) fb("Samambaia " + PN[k], FurnB.S_FERN, P(gC, gA, pot[k], wtd[0], 0, 0, 0, 0), 0x2F7A3A, M_SOFT, 2f, "Samambaia cheia de folhinhas num vaso.")
        for (k in 0 until 6) fb("Cacto em Vaso " + PN[k], FurnB.S_CACTUS, P(gD, 0, pot[k], 0, 0, fl[(k + 1) % 6], 0, 0), 0x6FA84A, M_SOFT, 2f, "Cacto com bracinhos e florzinha no topo.")
        for (k in 0 until 6) fb("Vaso de Flores " + FN[k], FurnB.S_FLOWERS, P(0, fl[(k + 3) % 6], pot[(k + 1) % 6], 0, 0, fl[k], fl[2], 0), flC[k], M_SOFT, 2f, "Vasinho com flores. Fica em cima de móveis.")
        fb("Bonsai Verde", FurnB.S_BONSAI, P(gA, gB, pot[4], wtd[1], 0, 0, 0, 0), 0x3F9A4A, M_SOFT, 2f, "Bonsai numa bandeja com pedrinha. Fica em cima de móveis.")
        fb("Bonsai de Outono", FurnB.S_BONSAI, P(gAut, gAut, pot[0], wtd[1], 0, 0, 0, 0), 0xE07A26, M_SOFT, 2f, "Bonsai de folhas alaranjadas. Fica em cima de móveis.")
        fb("Bonsai Cerejeira", FurnB.S_BONSAI, P(gCh, gCh, pot[1], wtd[1], 0, 0, 0, 0), 0xF0A0C0, M_SOFT, 2f, "Bonsai florido cor-de-rosa. Fica em cima de móveis.")
        for (a in 0 until 10) for (m in 0..1) fb("Quadro " + artN[a] + (if (m == 0) " Dourado" else " Escuro"), FurnB.S_PAINT, P(art[a], 0, if (m == 0) gold else wtd[1], 0, 0, 0, 0, 0), 0xE0B070, M_WOOD, 2f, "Quadro de parede com moldura. Gruda na parede de trás.")
        val mf = arrayOf("Dourado" to gold, "Preto" to blkT, "de Carvalho" to wt[0], "Branco" to whiteT)
        for (q in mf) fb("Espelho " + q.first, FurnB.S_MIRROR, P(mir, 0, q.second, 0, gold, 0, mir, 0), 0xCDEAF5, M_GLASS, 2f, "Espelho de parede com moldura. Gruda na parede de trás.")
        for (q in mf) fb("Relógio de Parede " + q.first, FurnB.S_CLOCK, P(face, 0, q.second, blkT, gold, 0, face, 0), 0xFFFDF4, M_WOOD, 2f, "Relógio de parede com ponteiros. Gruda na parede de trás.")
        for (k in intArrayOf(0, 1, 2)) fb("Relógio de Pé " + WN[k], FurnB.S_GRAND, P(wt[k], 0, wt[k], wtd[k], gold, 0, face, 0), wl[k], M_WOOD, 5f, "Relógio de pêndulo alto, com mostrador e pêndulo dourado.")
        val bk = Furniture.T_BOOK0
        for (k in 0 until 4) fb("Pilha de Livros " + (k + 1), FurnB.S_BOOKS, P(F[bk + k], F[bk + (k + 2) % 6], F[bk + (k + 4) % 6], 0, 0, F[bk + (k + 1) % 6], 0, 0), 0xB5503C, M_SOFT, 2f, "Pilha de livros com um marcador. Fica em cima de móveis.")
        for (k in intArrayOf(0, 4, 6)) fb("Fruteira " + WN[k], FurnB.S_FRUIT, P(en[5], fl[5], wt[k], wtd[0], 0, fl[2], gA, 0), 0xE5584A, M_WOOD, 2f, "Fruteira com maçã, laranja e limão. Fica em cima de móveis.")
        fb("Globo Dourado", FurnB.S_GLOBE, P(globeT, gB, 0, 0, gold, 0, 0, 0), 0x6DB6F5, M_METAL, 2f, "Globo terrestre num suporte. Fica em cima de móveis.")
        fb("Globo Preto", FurnB.S_GLOBE, P(globeT, gB, 0, 0, blkT, 0, 0, 0), 0x6DB6F5, M_METAL, 2f, "Globo terrestre num suporte preto. Fica em cima de móveis.")
        fb("Lareira de Pedra", FurnB.S_FIRE, P(stone, 0, wt[1], wtd[2], 0, 0, flame, 0), 0xB4BAC4, M_STONE, 8f, "Lareira com fogo crepitando, lenha e prateleira de madeira.", true)
        fb("Lareira de Tijolos", FurnB.S_FIRE, P(redBrick, 0, wt[0], wtd[2], 0, 0, flame, 0), 0xD8584A, M_STONE, 8f, "Lareira de tijolinhos com fogo crepitando.", true)
        fb("Lareira Branca", FurnB.S_FIRE, P(whiteBrick, 0, wt[4], wtd[2], 0, 0, flame, 0), 0xF4F4F0, M_STONE, 8f, "Lareira branca com fogo crepitando.", true)
        fb("Lareira de Mármore", FurnB.S_FIRE, P(marb, 0, gold, wtd[2], 0, 0, flame, 0), 0xE8E4DE, M_STONE, 8f, "Lareira de mármore com fogo crepitando.", true)
    }
}
