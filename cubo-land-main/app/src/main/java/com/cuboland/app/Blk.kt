package com.cuboland.app

/**
 * Registro dos blocos novos (id >= 14). Cada bloco é uma linha em build().
 * ATENÇÃO: os ids vão pro arquivo do mundo. NUNCA reordene nem apague linhas: só adicione no FIM de build().
 * Ids 100..103 são das ferramentas (pulados). Máximo: id 1023 (o mundo guarda 4 bytes por bloco; os arrays de B têm N = 1024 — aumente N se precisar de mais).
 */
object Blk {
    const val GLOW0 = 448          // primeiro tile emissivo: o shader deixa tiles >= 448 sempre brilhando
    const val F_LIGHT = 1          // brilha (sem sombra/AO)
    const val F_GLASS = 2          // translúcido: desenhado junto com a água, vizinhos continuam visíveis
    const val F_LEAF = 4           // folhagem: malha de folha (casca + cubo interno), buracos que deixam ver o miolo, balança no vento
    const val M_STONE = 1; const val M_WOOD = 2; const val M_SOFT = 3; const val M_GLASS = 4; const val M_METAL = 5
    const val C_NAT = 2; const val C_BUILD = 3; const val C_DEC = 4

    // tipos de textura (gerados por código em Atlas.genNew)
    const val K_FLAT = 0; const val K_STONE = 1; const val K_COBBLE = 2; const val K_BRICK = 3; const val K_PLANK = 4
    const val K_LOGSIDE = 5; const val K_LOGTOP = 6; const val K_ORE = 7; const val K_WOOL = 8; const val K_GLASS = 9
    const val K_TILES = 10; const val K_CHECK = 11; const val K_SPECK = 12; const val K_STRIPE = 13; const val K_GLOW = 14
    const val K_LAVA = 15; const val K_LAMP = 16; const val K_GRAVEL = 17; const val K_SHINGLE = 18; const val K_CRYSTAL = 19
    const val K_SHELF = 20; const val K_BAND = 21; const val K_GRAD = 22; const val K_CANDY = 23; const val K_SWIRL = 24
    const val K_BORDER = 25; const val K_RING = 26; const val K_CELLS = 27; const val K_JELLY = 28; const val K_ICE = 29
    const val K_BLOCKY = 30; const val K_GLAZE = 31; const val K_CACTUS = 32; const val K_LEAFY = 33; const val K_PIX = 34; const val K_LEAF = 35
    const val K_PAL = 36; const val K_PLEAF = 37   // pixel art com paleta própria (NatArt); K_PLEAF = folhagem com buracos transparentes

    class Def(val id: Int, val name: String, val cat: Int, val top: Int, val side: Int, val bot: Int,
              val color: Int, val flags: Int, val hard: Float, val mat: Int, val desc: String)

    /** receita de cada tile novo: [tipo, c1, c2, c3, c4, semente, par | (flags shl 8)] */
    val specs = arrayOfNulls<IntArray>(512)
    private var nextTile = 24
    private var nextGlow = GLOW0
    private var nextId = 14
    /** blocos que só existem pra mundos antigos abrirem: não aparecem na mochila e viram o bloco equivalente ao carregar */
    val hidden = HashSet<Int>()
    private val aliasName = HashMap<Int, String>()
    val alias: IntArray by lazy { defs; val a = IntArray(1024); for ((id, n) in aliasName) { val t = list.firstOrNull { it.name == n }; if (t != null) a[id] = t.id }; a }
    private val list = ArrayList<Def>()
    val defs: List<Def> by lazy { build(); list }
    /** trechos de tiles seguidos de folhagem (K_LEAF): o shader usa pra balançar no vento e iluminar como folha */
    val leafRuns: List<IntArray> by lazy {
        defs; val out = ArrayList<IntArray>(); var s = -1
        for (i in 24..GLOW0) {
            val on = i < GLOW0 && (specs[i]?.get(0) == K_LEAF || specs[i]?.get(0) == K_PLEAF)
            if (on && s < 0) s = i
            if (!on && s >= 0) { out.add(intArrayOf(s, i - 1)); s = -1 }
        }
        out
    }

    private fun tl(kind: Int, c1: Int, c2: Int = c1, c3: Int = c1, c4: Int = 0, seed: Int = 0, par: Int = 0, fl: Int = 0, glow: Boolean = false): Int {
        val i = if (glow) nextGlow else nextTile
        if (glow) nextGlow++ else nextTile++
        if (i >= 512 || (!glow && i >= GLOW0)) return 1
        val sd = if (seed != 0) seed else i * 13 + 5
        specs[i] = intArrayOf(kind, c1, c2, c3, c4, sd, par or (fl shl 8))
        return i
    }

    /** tile pixel art da NatArt (paleta própria, alpha por cor) */
    private fun pt(art: Int, leaf: Boolean = false, glow: Boolean = false): Int = tl(if (leaf) K_PLEAF else K_PAL, art, glow = glow)
    private fun legacy(id: Int, canon: String) { hidden.add(id); aliasName[id] = canon }

    private fun blk(name: String, cat: Int, top: Int, side: Int, bot: Int, color: Int, mat: Int = 0, hard: Float = 4f, fl: Int = 0, desc: String = ""): Int {
        if (nextId == 100) nextId = 104
        if (nextId > 1023) return 0
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
            uni("Concreto " + DM[i], C_BUILD, tl(K_FLAT, c, dk(c, 0.05f), c, 0, 0, 0, 4), c, M_STONE, 9f) }
        uni("Terracota", C_BUILD, tl(K_FLAT, 0xA8643F, 0x9A5A38, 0xA8643F, 0, 0, 0, 4), 0xA8643F, M_STONE, 8f)
        for (i in 0 until 16) { val c = mixc(DYE[i], 0x8C6A52, 0.42f)
            uni("Terracota " + DF[i], C_BUILD, tl(K_FLAT, c, dk(c, 0.07f), c, 0, 0, 0, 4), c, M_STONE, 8f) }
        for (i in 0 until 16) { val c = DYE[i]
            uni("Terracota Vitrificada " + DF[i], C_DEC, tl(K_GLAZE, dk(c, 0.2f), lt(c, 0.4f), lt(c, 0.8f), 0, 0, i and 3, 4), c, M_STONE, 8f) }
        uni("Vidro", C_BUILD, tl(K_GLASS, 0xE8F8FF, 0xB6D8E8), 0xCDEAF5, M_GLASS, 2f, F_GLASS, "Vidro transparente: dá pra ver através.")
        uni("Vidro Escuro", C_BUILD, tl(K_GLASS, 0x4A4256, 0x2A2430), 0x4A4256, M_GLASS, 2f, F_GLASS, "Vidro fumê para janelas misteriosas.")
        for (i in 0 until 16) { val c = DYE[i]
            uni("Vidro " + DM[i], C_BUILD, tl(K_GLASS, lt(c, 0.2f), dk(c, 0.25f)), c, M_GLASS, 2f, F_GLASS, "Vidro colorido e translúcido.") }
        val lampIdx = intArrayOf(0, 1, 6, 3, 4, 5, 10, 14)
        for (k in lampIdx.indices) { val i = lampIdx[k]; val c = DYE[i]
            uni("Lâmpada " + DF[i], C_DEC, tl(K_LAMP, c, dk(c, 0.45f), lt(c, 0.85f), 0, 0, 0, 0, true), c, 0, 3f, F_LIGHT, "Brilha com uma luz colorida.") }

        // ================= MADEIRAS =================
        val WN = arrayOf("Abeto", "Bétula", "Selva", "Acácia", "Carvalho Escuro", "Mangue", "Cerejeira", "Bambu", "Carmesim", "Distorcido")
        val WP = intArrayOf(0x7B5B3A, 0xE6D79B, 0xB98563, 0xC4693B, 0x4B3321, 0x8E3F3B, 0xE9B8B2, 0xE1C75B, 0x8B3B5E, 0x2F7F7A)
        for (i in 0 until 10) { val c = WP[i]
            uni("Tábua de " + WN[i], C_BUILD, pal(Pix.PLANK, c), c, M_WOOD, 6f) }
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
        uni("Bambu Trançado", C_BUILD, pal(Pix.MOSAIC, 0xE1C75B), 0xE1C75B, M_WOOD, 5f)
        fun strip(n: String, c: Int) {
            val tp = tl(K_PIX, dk(c, 0.35f), dk(c, 0.15f), c, lt(c, 0.2f), 0, Pix.LOGTOP)
            blk(n, C_BUILD, tp, pal(Pix.STRIPSIDE, c), tp, c, M_WOOD, 7f)
        }
        strip("Carvalho Descascado", 0xC9A06B)
        strip("Abeto Descascado", 0x8A6A45)
        strip("Bétula Descascada", 0xDCCB8E)
        strip("Selva Descascada", 0xB88B62)
        strip("Acácia Descascada", 0xB8603A)
        strip("Carvalho Escuro Descascado", 0x5A4128)
        strip("Cerejeira Descascada", 0xDDA5A0)

        // ================= PEDRAS E MINERAIS =================
        fun rough(n: String, c: Int, grout: Int) { uni(n, C_BUILD, pal(Pix.STONE0 + n.length % 3, c), c, M_STONE, 10f) }
        fun smooth(n: String, c: Int) { uni(n, C_BUILD, pal(Pix.SMOOTH, c), c, M_STONE, 10f) }
        fun bricks(n: String, c: Int, grout: Int, fl: Int = 0) { uni(n, C_BUILD, pal(if ((fl and 1) != 0) Pix.CRACKBRICK else Pix.BRICK, c, fl and 2), c, M_STONE, 10f) }
        val cobC = intArrayOf(0x9AA0AA, 0xB6BCC6, 0x666C78)
        uni("Pedregulho", C_BUILD, pal(Pix.COBBLE, 0x9AA0AA), 0x9AA0AA, M_STONE, 10f)
        uni("Pedregulho Musgoso", C_BUILD, pal(Pix.COBBLE, 0x9AA0AA, 2), 0x8CA07A, M_STONE, 10f)
        smooth("Pedra Lisa", 0xC9CED8)
        bricks("Tijolos de Pedra", 0xB4BAC4, 0x7F8590)
        bricks("Tijolos de Pedra Musgosos", 0xB4BAC4, 0x7F8590, 2)
        bricks("Tijolos de Pedra Rachados", 0xB4BAC4, 0x7F8590, 1)
        uni("Tijolos Cinzelados", C_BUILD, pal(Pix.CHISEL, 0xB4BAC4), 0xB4BAC4, M_STONE, 10f)
        uni("Granito", C_BUILD, pal(Pix.SPECK, 0xB8786A), 0xB8786A, M_STONE, 10f)
        smooth("Granito Polido", 0xBE8071)
        uni("Diorito", C_BUILD, pal(Pix.SPECK, 0xDCDCD8), 0xDCDCD8, M_STONE, 10f)
        smooth("Diorito Polido", 0xE2E2DE)
        rough("Andesito", 0xA2A29E, 0x7A7A76)
        smooth("Andesito Polido", 0xA8A8A4)
        rough("Ardósia", 0x50525A, 0x33343A)
        smooth("Ardósia Polida", 0x565861)
        bricks("Tijolos de Ardósia", 0x50525A, 0x2C2D33)
        uni("Ladrilhos de Ardósia", C_BUILD, pal(Pix.TILES, 0x555760), 0x50525A, M_STONE, 10f)
        uni("Pedregulho de Ardósia", C_BUILD, pal(Pix.COBBLE, 0x50525A), 0x50525A, M_STONE, 10f)
        rough("Tufo", 0x7E8274, 0x5A5E50)
        rough("Pedra Negra", 0x3A3540, 0x221E28)
        smooth("Pedra Negra Polida", 0x413C48)
        bricks("Tijolos de Pedra Negra", 0x3A3540, 0x1E1A24)
        blk("Arenito", C_BUILD, pal(Pix.SMOOTH, 0xF0DFA8), pal(Pix.SANDSIDE, 0xF0DFA8), pal(Pix.SMOOTH, 0xE9D9A0), 0xF0DFA8, M_STONE, 7f)
        uni("Arenito Liso", C_BUILD, pal(Pix.SMOOTH, 0xF3E6B4), 0xF3E6B4, M_STONE, 7f)
        uni("Arenito Cinzelado", C_BUILD, pal(Pix.CHISEL, 0xF0DFA8), 0xF0DFA8, M_STONE, 7f)
        blk("Arenito Vermelho", C_BUILD, pal(Pix.SMOOTH, 0xD58A48), pal(Pix.SANDSIDE, 0xD58A48), pal(Pix.SMOOTH, 0xCC8444), 0xD58A48, M_STONE, 7f)
        bricks("Tijolos do Nether", 0x5A2830, 0x2E1216)
        bricks("Tijolos Vermelhos do Nether", 0x8A2A36, 0x3E1218)
        rough("Netherrack", 0x8E3C3C, 0x6A2828)
        rough("Pedra do Fim", 0xE8EAB6, 0xC4C68A)
        uni("Purpur", C_BUILD, pal(Pix.TILES, 0xB58AC0), 0xB58AC0, M_STONE, 9f)
        rough("Prismarinho", 0x6BB6A0, 0x4A8F7D)
        bricks("Tijolos de Prismarinho", 0x6FBBA6, 0x4A8F7D)
        smooth("Quartzo", 0xF0ECE4)
        blk("Pilar de Quartzo", C_BUILD, pal(Pix.SMOOTH, 0xF0ECE4), pal(Pix.PILLAR, 0xF0ECE4), pal(Pix.SMOOTH, 0xF0ECE4), 0xF0ECE4, M_STONE, 9f)
        bricks("Tijolos de Quartzo", 0xF2EEE6, 0xD2CCC0)
        uni("Obsidiana", C_BUILD, tl(K_PIX, 0x0E081C, 0x1C1030, 0x5B3A9A, 0x8A66CC, 0, Pix.OBS), 0x2E1B50, M_STONE, 24f)

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
        uni("Telhado Vermelho", C_BUILD, tl(K_SHINGLE, 0xD8584A, 0xC44A3E, 0x8E2E26), 0xD8584A, M_STONE, 8f)
        uni("Telhado Azul", C_BUILD, tl(K_SHINGLE, 0x5A86D8, 0x4C74C4, 0x2E4E8E), 0x5A86D8, M_STONE, 8f)
        uni("Telhado Verde", C_BUILD, tl(K_SHINGLE, 0x5FAE6A, 0x509C5C, 0x2F6A3A), 0x5FAE6A, M_STONE, 8f)
        uni("Palha", C_BUILD, pt(NatArt.PALHA), 0xE6C65A, M_SOFT, 3f)
        uni("Gesso Branco", C_BUILD, tl(K_FLAT, 0xFAF6EE, 0xF0EBE0, 0xFAF6EE, 0, 0, 0, 4), 0xFAF6EE, M_STONE, 6f)
        uni("Gesso Rosa", C_BUILD, tl(K_FLAT, 0xFFD6E4, 0xF8C8DA, 0xFFD6E4, 0, 0, 0, 4), 0xFFD6E4, M_STONE, 6f)
        uni("Gesso Amarelo", C_BUILD, tl(K_FLAT, 0xFFEFB0, 0xF8E6A0, 0xFFEFB0, 0, 0, 0, 4), 0xFFEFB0, M_STONE, 6f)
        uni("Gesso Azul", C_BUILD, tl(K_FLAT, 0xCFE4FA, 0xBFD8F2, 0xCFE4FA, 0, 0, 0, 4), 0xCFE4FA, M_STONE, 6f)
        uni("Piso de Madeira", C_BUILD, pal(Pix.MOSAIC, 0xC9935A), 0xC9935A, M_WOOD, 5f)
        uni("Azulejo Branco", C_BUILD, tl(K_TILES, 0xF4F4F0, 0xE8E8E4, 0xC8C8C4, 0, 0, 8), 0xF4F4F0, M_STONE, 6f)
        uni("Azulejo Azul", C_BUILD, tl(K_TILES, 0x6FA8E8, 0x5E98DA, 0x3E6EA8, 0, 0, 8), 0x6FA8E8, M_STONE, 6f)
        uni("Janela de Madeira", C_BUILD, tl(K_GLASS, 0xDDF2FF, 0x8B6B43), 0xB6A070, M_GLASS, 2f, F_GLASS, "Janela com moldura de madeira.")
        uni("Janela Branca", C_BUILD, tl(K_GLASS, 0xDDF2FF, 0xF2F2EE), 0xDDE8EE, M_GLASS, 2f, F_GLASS, "Janela com moldura branca.")
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
        blk("Abóbora Luminosa", C_DEC, pt(NatArt.ABOBORA_LUM_TOP, glow = true), pt(NatArt.ABOBORA_LUM_SIDE, glow = true), pt(NatArt.ABOBORA_LUM_TOP, glow = true), 0xF29A2A, M_WOOD, 4f, F_LIGHT, "Abóbora que brilha por dentro.")

        // ================= NATUREZA =================
        uni("Terra Grossa", C_NAT, pt(NatArt.TERRA_GROSSA), 0xA37748, M_SOFT, 3f)
        val tDirt = pt(NatArt.TERRA_BASE)
        blk("Podzol", C_NAT, pt(NatArt.PODZOL_TOP), pt(NatArt.PODZOL_SIDE), tDirt, 0x7A5B38, M_SOFT, 3f)
        blk("Micélio", C_NAT, pt(NatArt.MICELIO_TOP), pt(NatArt.MICELIO_SIDE), tDirt, 0x9A8AA8, M_SOFT, 3f)
        blk("Grama Nevada", C_NAT, pt(NatArt.GRAMA_NEVADA_TOP), pt(NatArt.GRAMA_NEVADA_SIDE), tDirt, 0xF2F8FF, M_SOFT, 3f)
        uni("Lama", C_NAT, pt(NatArt.LAMA), 0x5A4A42, M_SOFT, 3f)
        uni("Argila", C_NAT, pt(NatArt.ARGILA), 0xA8B4C4, M_SOFT, 3f)
        uni("Cascalho", C_NAT, pt(NatArt.CASCALHO), 0x8E8A88, M_SOFT, 3f)
        uni("Areia Vermelha", C_NAT, pt(NatArt.AREIA_VERMELHA), 0xD9854A, M_SOFT, 3f)
        uni("Areia Rosa", C_NAT, pt(NatArt.AREIA_ROSA), 0xF8CCD8, M_SOFT, 3f)
        uni("Neve", C_NAT, pt(NatArt.NEVE), 0xFFFFFF, M_SOFT, 2f)
        uni("Gelo", C_NAT, pt(NatArt.GELO), 0xBFE3FF, M_GLASS, 3f, F_GLASS, "Gelo claro e escorregadio de olhar.")
        uni("Gelo Compactado", C_NAT, pt(NatArt.GELO_COMPACTO), 0x9CCFF5, M_GLASS, 4f)
        uni("Musgo", C_NAT, pt(NatArt.F_MUSGO, true), 0x5C9034, M_SOFT, 2f, F_LEAF)
        blk("Cacto", C_NAT, pt(NatArt.CACTO_TOP), pt(NatArt.CACTO_SIDE), pt(NatArt.CACTO_TOP), 0x4F8F3A, M_SOFT, 3f)
        blk("Bloco de Feno", C_NAT, pt(NatArt.FENO_TOP), pt(NatArt.FENO_SIDE), pt(NatArt.FENO_TOP), 0xE6C65A, M_SOFT, 3f)
        blk("Melancia", C_NAT, pt(NatArt.MELANCIA_TOP), pt(NatArt.MELANCIA_SIDE), pt(NatArt.MELANCIA_TOP), 0x5FA84A, M_SOFT, 3f)
        blk("Abóbora", C_NAT, pt(NatArt.ABOBORA_TOP), pt(NatArt.ABOBORA_SIDE), pt(NatArt.ABOBORA_TOP), 0xF29A2A, M_SOFT, 3f)
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
        fun metal(n: String, c: Int) { uni(n, C_BUILD, tl(K_BORDER, c, lt(c, 0.12f), dk(c, 0.3f), fl = 16), c, M_METAL, 14f) }
        metal("Bloco de Ferro", 0xE2E4E8)
        metal("Bloco de Ouro", 0xF6CF3E)
        metal("Bloco de Diamante", 0x5FE8DE)
        metal("Bloco de Esmeralda", 0x3FCF6A)
        metal("Bloco de Lápis", 0x3A66D2)
        metal("Bloco de Redstone", 0xD8342C)
        metal("Bloco de Cobre", 0xD98458)
        metal("Cobre Exposto", 0xB89478)
        metal("Cobre Desgastado", 0x6FAE8A)
        metal("Cobre Oxidado", 0x4FB59A)

        // ================= FOLHAGEM DE TODAS AS ÁRVORES =================
        uni("Folhas de Abeto", C_NAT, pt(NatArt.F_ABETO, true), 0x3C7A3A, M_SOFT, 2f, F_LEAF, "Folhas de abeto: verde bem escuro, de pinheiro.")
        uni("Folhas de Bétula", C_NAT, pt(NatArt.F_BETULA, true), 0x8FBF4A, M_SOFT, 2f, F_LEAF, "Folhas de bétula: verde clarinho e fresco.")
        uni("Folhas de Selva", C_NAT, pt(NatArt.F_SELVA, true), 0x45B02A, M_SOFT, 2f, F_LEAF, "Folhas de selva: verde vivo e cheio.")
        uni("Folhas de Acácia", C_NAT, pt(NatArt.F_ACACIA, true), 0xA2BC3C, M_SOFT, 2f, F_LEAF, "Folhas de acácia: verde de savana.")
        uni("Folhas de Carvalho Escuro", C_NAT, pt(NatArt.F_CARVALHO_ESCURO, true), 0x2F7A24, M_SOFT, 2f, F_LEAF, "Folhas de carvalho escuro: verde profundo.")
        uni("Folhas de Mangue", C_NAT, pt(NatArt.F_MANGUE, true), 0x62AA48, M_SOFT, 2f, F_LEAF, "Folhas de mangue: verde de pântano.")
        uni("Folhas de Cerejeira", C_NAT, pt(NatArt.F_CEREJEIRA, true), 0xF0A0C0, M_SOFT, 2f, F_LEAF, "Folhas de cerejeira: cor-de-rosa florido.")
        uni("Folhas de Outono", C_NAT, pt(NatArt.F_OUTONO, true), 0xE07A26, M_SOFT, 2f, F_LEAF, "Folhas de outono: laranja de fim de estação.")
        uni("Folhas Amarelas", C_NAT, pt(NatArt.F_AMARELAS, true), 0xE0BE3A, M_SOFT, 2f, F_LEAF, "Folhas amarelas douradas.")
        uni("Folhas de Bambu", C_NAT, pt(NatArt.F_BAMBU, true), 0x72AE34, M_SOFT, 2f, F_LEAF, "Folhas de bambu: finas e verdinhas.")
        uni("Verruga Carmesim", C_NAT, pt(NatArt.F_CARMESIM, true), 0xC04068, M_SOFT, 2f, F_LEAF, "Verruga carmesim: copa vermelha do mundo sombrio.")
        uni("Verruga Distorcido", C_NAT, pt(NatArt.F_DISTORCIDO, true), 0x2EA8A0, M_SOFT, 2f, F_LEAF, "Verruga distorcida: copa azul-turquesa do mundo sombrio.")
    }
}
