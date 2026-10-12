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
    fun pickTile(tile: Int, h: Float): Int { val b = varBase[tile and (MAXT - 1)]; return if (b == 0) tile else b + (h * NatVar.V).toInt().coerceAtMost(NatVar.V - 1) }
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
            uni("Concreto " + DM[i], C_BUILD, bt(BuildArt.CONCRETE, c), c, M_STONE, 9f) }
        uni("Terracota", C_BUILD, bt(BuildArt.CLAY, 0xA8643F), 0xA8643F, M_STONE, 8f)
        for (i in 0 until 16) { val c = mixc(DYE[i], 0x8C6A52, 0.42f)
            uni("Terracota " + DF[i], C_BUILD, bt(BuildArt.CLAY, c), c, M_STONE, 8f) }
        for (i in 0 until 16) { val c = DYE[i]
            uni("Terracota Vitrificada " + DF[i], C_DEC, tl(K_GLAZE, dk(c, 0.2f), lt(c, 0.4f), lt(c, 0.8f), 0, 0, i and 3, 4), c, M_STONE, 8f) }
        uni("Vidro", C_BUILD, bt(BuildArt.GLASS, 0xD8F2FF), 0xCDEAF5, M_GLASS, 2f, F_GLASS, "Vidro grosso e liso: transparente, emenda com os vizinhos sem marca.")
        uni("Vidro Escuro", C_BUILD, bt(BuildArt.GLASS, 0x4A4256, -1, 210), 0x4A4256, M_GLASS, 2f, F_GLASS, "Vidro grosso fumê, liso, para janelas misteriosas.")
        for (i in 0 until 16) { val c = DYE[i]
            uni("Vidro " + DM[i], C_BUILD, bt(BuildArt.GLASS, c, -1, 165), c, M_GLASS, 2f, F_GLASS, "Vidro grosso colorido, liso e translúcido.") }
        val lampIdx = intArrayOf(0, 1, 6, 3, 4, 5, 10, 14)
        for (k in lampIdx.indices) { val i = lampIdx[k]; val c = DYE[i]
            uni("Lâmpada " + DF[i], C_DEC, tl(K_LAMP, c, dk(c, 0.45f), lt(c, 0.85f), 0, 0, 0, 0, true), c, 0, 3f, F_LIGHT, "Brilha com uma luz colorida.") }

        // ================= MADEIRAS =================
        val WN = arrayOf("Abeto", "Bétula", "Selva", "Acácia", "Carvalho Escuro", "Mangue", "Cerejeira", "Bambu", "Carmesim", "Distorcido")
        val WP = intArrayOf(0x7B5B3A, 0xE6D79B, 0xB98563, 0xC4693B, 0x4B3321, 0x8E3F3B, 0xE9B8B2, 0xE1C75B, 0x8B3B5E, 0x2F7F7A)
        for (i in 0 until 10) { val c = WP[i]
            uni("Tábua de " + WN[i], C_BUILD, bt(BuildArt.PLANK, c), c, M_WOOD, 6f) }
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
        fun rough(n: String, c: Int, grout: Int) { uni(n, C_BUILD, bt(BuildArt.ROUGH0 + n.length % 3, c), c, M_STONE, 10f) }
        fun smooth(n: String, c: Int) { uni(n, C_BUILD, bt(BuildArt.SMOOTH, c), c, M_STONE, 10f) }
        fun bricks(n: String, c: Int, grout: Int, fl: Int = 0) { uni(n, C_BUILD, bt(if (n.contains("Nether")) BuildArt.BRICK else if ((fl and 1) != 0) BuildArt.SBRICK_CRACK else if ((fl and 2) != 0) BuildArt.SBRICK_MOSS else BuildArt.SBRICK, c), c, M_STONE, 10f) }
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
        blk("Pilar de Quartzo", C_BUILD, bt(BuildArt.SMOOTH, 0xF0ECE4), bt(BuildArt.PILLAR, 0xF0ECE4), bt(BuildArt.SMOOTH, 0xF0ECE4), 0xF0ECE4, M_STONE, 9f)
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
        uni("Telhado Vermelho", C_BUILD, bt(BuildArt.SHINGLE, 0xD8584A), 0xD8584A, M_STONE, 8f)
        uni("Telhado Azul", C_BUILD, bt(BuildArt.SHINGLE, 0x5A86D8), 0x5A86D8, M_STONE, 8f)
        uni("Telhado Verde", C_BUILD, bt(BuildArt.SHINGLE, 0x5FAE6A), 0x5FAE6A, M_STONE, 8f)
        uni("Palha", C_BUILD, pt(NatArt.PALHA), 0xE6C65A, M_SOFT, 3f)
        uni("Gesso Branco", C_BUILD, bt(BuildArt.PLASTER, 0xFAF6EE), 0xFAF6EE, M_STONE, 6f)
        uni("Gesso Rosa", C_BUILD, bt(BuildArt.PLASTER, 0xFFD6E4), 0xFFD6E4, M_STONE, 6f)
        uni("Gesso Amarelo", C_BUILD, bt(BuildArt.PLASTER, 0xFFEFB0), 0xFFEFB0, M_STONE, 6f)
        uni("Gesso Azul", C_BUILD, bt(BuildArt.PLASTER, 0xCFE4FA), 0xCFE4FA, M_STONE, 6f)
        uni("Piso de Madeira", C_BUILD, bt(BuildArt.MOSAIC, 0xC9935A), 0xC9935A, M_WOOD, 5f)
        uni("Azulejo Branco", C_BUILD, bt(BuildArt.AZ_PLAIN, 0xF4F4F0), 0xF4F4F0, M_STONE, 6f)
        uni("Azulejo Azul", C_BUILD, bt(BuildArt.AZ_DECOR, 0x2F63B5, 0xF6F6F0), 0x6FA8E8, M_STONE, 6f)
        uni("Janela de Madeira", C_BUILD, bt(BuildArt.WINDOW, 0x8B6B43, 0xBFE6F5), 0xB6A070, M_GLASS, 2f, F_GLASS, "Janela com moldura de madeira.")
        uni("Janela Branca", C_BUILD, bt(BuildArt.WINDOW, 0xF2F2EE, 0xBFE6F5), 0xDDE8EE, M_GLASS, 2f, F_GLASS, "Janela com moldura branca.")
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
        uni("Vidro Fino", C_BUILD, bt(BuildArt.GLASS_THIN, 0xD8F2FF), 0xCDEAF5, M_GLASS, 2f, F_GLASS, "Vidro fino e liso: bem transparente, emenda sem marca.")
        uni("Vidro Fino Escuro", C_BUILD, bt(BuildArt.GLASS_THIN, 0x4A4256, -1, 300), 0x4A4256, M_GLASS, 2f, F_GLASS, "Vidro fino fumê e liso.")
        for (i in 0 until 16) { val c = DYE[i]
            uni("Vidro Fino " + DM[i], C_BUILD, bt(BuildArt.GLASS_THIN, c, -1, 230), c, M_GLASS, 2f, F_GLASS, "Vidro fino colorido e liso.") }
        // ---- mais janelas (todas translúcidas; moldura e vidraça lisas) ----
        val GL = 0xBFE6F5
        uni("Janela Sem Moldura", C_BUILD, bt(BuildArt.JAN_FRAMELESS, 0xB8C0C8, GL), 0xCDEEFF, M_GLASS, 2f, F_GLASS, "Vidro liso com só quatro presilhas de metal nos cantos.")
        uni("Janela de Correr Alumínio", C_BUILD, bt(BuildArt.JAN_SLIDE, 0xB8C0C8, GL), 0xB8C0C8, M_GLASS, 2f, F_GLASS, "Duas folhas grandes de vidro que correm, com puxador.")
        uni("Janela de Correr Preta", C_BUILD, bt(BuildArt.JAN_SLIDE, 0x2E3138, GL), 0x2E3138, M_GLASS, 2f, F_GLASS, "Janela de correr com perfil preto.")
        uni("Janela de Correr Branca", C_BUILD, bt(BuildArt.JAN_SLIDE, 0xF2F2EE, GL), 0xF2F2EE, M_GLASS, 2f, F_GLASS, "Janela de correr com perfil branco.")
        uni("Janela de Correr Madeira", C_BUILD, bt(BuildArt.JAN_SLIDE, 0x8B6B43, GL), 0x8B6B43, M_GLASS, 2f, F_GLASS, "Janela de correr com perfil de madeira.")
        uni("Janela Colonial Branca", C_BUILD, bt(BuildArt.JAN_COLONIAL, 0xF2F2EE, GL), 0xF2F2EE, M_GLASS, 2f, F_GLASS, "Janela colonial com seis vidraças.")
        uni("Janela Colonial Madeira", C_BUILD, bt(BuildArt.JAN_COLONIAL, 0x8B6B43, GL), 0x8B6B43, M_GLASS, 2f, F_GLASS, "Janela colonial de madeira com seis vidraças.")
        uni("Persiana de Madeira", C_BUILD, bt(BuildArt.JAN_LOUVER, 0x9A7448, GL), 0x9A7448, M_GLASS, 2f, F_GLASS, "Persiana de ripas: deixa ver pelas frestas.")
        uni("Persiana Branca", C_BUILD, bt(BuildArt.JAN_LOUVER, 0xEDEBE4, GL), 0xEDEBE4, M_GLASS, 2f, F_GLASS, "Persiana branca de ripas.")
        uni("Escotilha de Ferro", C_BUILD, bt(BuildArt.JAN_PORTHOLE, 0xB0B6BE, GL), 0xB0B6BE, M_GLASS, 2f, F_GLASS, "Janela redonda de navio, com aro de ferro e rebites.")
        uni("Escotilha de Latão", C_BUILD, bt(BuildArt.JAN_PORTHOLE, 0xC8A040, GL), 0xC8A040, M_GLASS, 2f, F_GLASS, "Janela redonda de navio, com aro de latão.")
        uni("Janela Gradeada", C_BUILD, bt(BuildArt.JAN_BARS, 0x2E3138, GL), 0x2E3138, M_GLASS, 2f, F_GLASS, "Vidro com grade de ferro.")
        uni("Vitral Azul e Âmbar", C_BUILD, bt(BuildArt.VITRAL, 0x2F63D0, 0xE0A030), 0x2F63D0, M_GLASS, 2f, F_GLASS, "Vitral de pedaços coloridos presos por chumbo.")
        uni("Vitral Rosa e Verde", C_BUILD, bt(BuildArt.VITRAL, 0xE86AA0, 0x4FB070), 0xE86AA0, M_GLASS, 2f, F_GLASS, "Vitral de pedaços coloridos presos por chumbo.")
        uni("Vitral Roxo e Ciano", C_BUILD, bt(BuildArt.VITRAL, 0x8A4FD0, 0x4FD0D0), 0x8A4FD0, M_GLASS, 2f, F_GLASS, "Vitral de pedaços coloridos presos por chumbo.")
        uni("Vitral Vermelho e Dourado", C_BUILD, bt(BuildArt.VITRAL, 0xD8403A, 0xF2C93A), 0xD8403A, M_GLASS, 2f, F_GLASS, "Vitral de pedaços coloridos presos por chumbo.")
    }
}
