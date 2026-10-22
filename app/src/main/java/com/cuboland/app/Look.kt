package com.cuboland.app

import android.content.Context

/** Aparência do personagem (tudo guardado como inteiros, um por opção). */
object Look {
    const val GENDER = 0; const val SKIN = 1; const val HAIR = 2; const val HAIRC = 3; const val EYES = 4; const val EYEC = 5
    const val BLUSH = 6; const val TOP = 7; const val TOPC = 8; const val BOTTOM = 9; const val BOTTOMC = 10
    const val SHOES = 11; const val SHOEC = 12; const val HAT = 13; const val HATC = 14; const val FACE = 15
    const val BACK = 16; const val BACKC = 17; const val NECK = 18; const val NECKC = 19; const val MOUTH = 20
    const val TOP_PATTERN = 21; const val TOP_ACCENT = 22; const val BOTTOM_PATTERN = 23; const val BOTTOM_ACCENT = 24; const val HAIR_ACCENT = 25; const val HAIR_ACCENT_COLOR = 26
    const val N = 27

    val DEF = intArrayOf(1, 1, 2, 0, 0, 3, 1, 4, 0, 0, 8, 0, 10, 7, 1, 0, 0, 4, 1, 1, 0, 0, 6, 0, 6, 0, 6)
    @Volatile var v: IntArray = DEF.copyOf()

    val SKINS = intArrayOf(0xFFF0E6, 0xFFE3CF, 0xF2C29B, 0xE7B38B, 0xD9A07A, 0xC88D68, 0xB57A55, 0x8A5A3C, 0x70452F, 0x4B2D20)
    val SKIN_NAMES = arrayOf("Marfim", "Porcelana", "Clara", "Pêssego", "Dourada", "Caramelo", "Morena", "Canela", "Cacau", "Ébano")
    val HAIRS = intArrayOf(0x6B3FA0, 0xF08CB8, 0xF2D06B, 0x7A4A2A, 0x2A2430, 0xD2562C, 0x4A7FE0, 0x6FD6B0, 0xF2F2F6, 0xB8C0D0, 0xF29A3C, 0x8E2A4A, 0x3FA66B, 0xE03030, 0x2A3A8C, 0x30C8E0, 0xC7A6FF, 0xFF6F91, 0xE8A0A8, 0x8C6A4F, 0x3B2B24, 0xBDE0FE, 0xA7C957, 0xD8B4A0)
    val HAIR_NAMES = arrayOf("Roxo", "Rosa", "Loiro", "Castanho", "Preto", "Ruivo", "Azul", "Menta", "Branco", "Prata", "Laranja", "Vinho", "Verde", "Vermelho", "Marinho", "Ciano", "Lavanda", "Rosa neon", "Rose gold", "Avelã", "Ébano", "Azul gelo", "Lima", "Areia")
    val EYE_COLORS = intArrayOf(0x4A8BE8, 0x3FA66B, 0x8B5A3C, 0x9A5BD8, 0xE0507A, 0x2C2C3A, 0xE8A030, 0x30C0C8, 0xD03030, 0xF0C030, 0x8FD3FF, 0xA7E8BD, 0xF7C8E0, 0xB7A0F7, 0xDDE6F5, 0xB9D35B)
    val EYE_COLOR_NAMES = arrayOf("Azul", "Verde", "Castanho", "Roxo", "Rosa", "Preto", "Âmbar", "Ciano", "Vermelho", "Dourado", "Azul gelo", "Menta", "Rosa claro", "Lilás", "Prata", "Lima")
    val PAL = intArrayOf(0x7FD9C8, 0xFFA3C8, 0xF4F4F4, 0x30323C, 0xE05555, 0xF2A04A, 0xFFD35C, 0x6CC070, 0x5B8DEE, 0x9A78E0, 0x9A6A3E, 0x3B4A8C, 0xE8D5B0, 0x8E2A4A, 0x8A8E9A, 0x2AA6B0, 0xC7A6FF, 0xFF6F91, 0xE8A0A8, 0xFFB4A2, 0xE76F51, 0xF4D35E, 0xB7D96C, 0x2D936C, 0x48BFE3, 0xA9DEF9, 0x4361EE, 0x3A0CA3, 0xF1C0E8, 0xD8E2DC, 0xB08968, 0xD5D9E0)
    val PAL_NAMES = arrayOf("Menta", "Rosa", "Branco", "Preto", "Vermelho", "Laranja", "Amarelo", "Verde", "Azul", "Roxo", "Marrom", "Marinho", "Bege", "Vinho", "Cinza", "Turquesa", "Lavanda", "Rosa neon", "Rose gold", "Coral", "Terracota", "Limão", "Pistache", "Esmeralda", "Ciano", "Azul gelo", "Royal", "Índigo", "Lilás", "Pérola", "Caramelo", "Prata")

    val GENDER_NAMES = arrayOf("Menino", "Menina")
    val HAIR_STYLES = arrayOf("Fringe Desconectado", "Espetado Premium", "Bob em Camadas", "Twin Tails Volumosos", "Ponytail Clássico", "Coque Despojado", "Longo em Camadas", "Cachos Macios", "Undercut Texturizado", "Franja Assimétrica", "Wolf Cut Selvagem", "Quiff Esculpido", "Espetado Fantasia", "Raspado Realista", "Moicano Esculpido", "Divisão Polida", "Hime em Camadas", "Tranças Duplas", "Coques Duplos", "Ponytail Alto", "Ahoge Estilizado", "Fade Texturizado", "Corte Social", "Pompadour Premium", "Cachos Definidos", "Afro Esculpido", "Dreadlocks em Camadas", "Mullet Moderno", "Coque Samurai", "Franja Cortina", "Slick Back Desestruturado", "Wolf Cut Longo", "Longo Sedoso", "Ondas em Camadas", "Cachos Longos", "Tranças Box Premium", "Franja Emo", "Corte Militar", "Crop Texturizado", "Ponytail Baixo", "Pixie Esculpido", "Bob Assimétrico", "Ponytail Atlético", "Coque de Bailarina", "Longo Real", "Trança Lateral", "Tranças Entrelaçadas", "Meio-Preso com Coque", "Cachos de Princesa", "Twin Drills Detalhados")
    /** cabelos masculinos e femininos (índices de HAIR_STYLES) */
    val HAIR_MALE = intArrayOf(0, 1, 8, 9, 10, 11, 12, 13, 14, 15, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 35, 36, 37, 38)
    val HAIR_FEM = intArrayOf(2, 3, 4, 5, 6, 7, 16, 17, 18, 19, 20, 33, 34, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49)
    fun hairList(g: Int = v[GENDER]): IntArray = if (g == 1) HAIR_FEM else HAIR_MALE
    private val HAIR_SWAP = mapOf(6 to 32, 32 to 6, 0 to 40, 40 to 0, 5 to 28, 28 to 5, 24 to 34, 34 to 24, 39 to 30, 30 to 39, 36 to 41, 41 to 36)
    /** troca o cabelo por um equivalente quando o tipo (menino/menina) muda */
    private fun hairFor(g: Int, id: Int): Int {
        if (hairList(g).contains(id)) return id
        val m = HAIR_SWAP[id]
        return if (m != null && hairList(g).contains(m)) m else if (g == 1) 2 else 0
    }
    val EYE_STYLES = arrayOf("Grandes", "Felizes", "Bolinhas", "Sonolentos", "Brilhantes", "Sérios", "Estrelas", "Gato", "Cansados")
    val ONOFF = arrayOf("Não", "Sim")
    val TOPS = arrayOf("Camiseta", "Moletom", "Vestido", "Jaqueta", "Túnica", "Armadura", "Macacão", "Camisa social", "Marinheiro", "Gakuran", "Kimono", "Robe de mago", "Terno", "Regata", "Haori samurai", "Maid", "Colete aventureiro", "Casacão")
    val BOTTOMS = arrayOf("Calça", "Shorts", "Saia", "Bermuda", "Hakama", "Saia plissada", "Leggings", "Cargo")
    val SHOE_STYLES = arrayOf("Botas", "Tênis", "Sapatilha", "Descalço", "Botas altas", "Geta", "Mocassim", "Meias listradas")
    val HATS = arrayOf("Nenhum", "Chapéu de bruxo", "Gorro de lã", "Boné", "Coroa", "Orelhas de gato", "Orelhas de coelho", "Laço grande", "Capacete", "Chapéu de palha", "Florzinha", "Faixa ninja", "Auréola", "Chifrinhos", "Orelhas de raposa", "Tiara", "Boina", "Chapéu kasa", "Fones", "Capuz", "Touca de maid", "Cartola", "Chapéu de chef", "Capacete viking", "Chifres infernais", "Tiara lunar", "Fones cyberpunk", "Capuz com orelhas", "Coroa floral", "Halo cristalino", "Visor tático", "Chifres neon")
    val FACES = arrayOf("Nada", "Óculos redondos", "Óculos escuros", "Sardas", "Tapa-olho", "Cicatriz", "Curativo", "Bigodes de gato", "Máscara", "Óculos quadrados", "Lágrima", "Monóculo", "Óculos aviador")
    val BACKS = arrayOf("Nada", "Capa", "Mochila", "Asinhas", "Rabinho", "Espada nas costas", "Asas de morcego", "Aljava", "Rabo de raposa", "Mochila escolar")
    val MOUTHS = arrayOf("Sorriso", "Neutra", "Biquinho", "Aberta", "Dentinho", "Gatinho")
    val NECKS = arrayOf("Nada", "Cachecol", "Laço", "Colar", "Gravata", "Gravata borboleta", "Coleira de sino", "Lenço", "Gargantilha", "Medalha")
    val TOP_PATTERNS = arrayOf("Sem estampa", "Brasão", "Listras", "Xadrez pixel", "Raio", "Corações", "Estrelas", "Painel tech")
    val BOTTOM_PATTERNS = arrayOf("Sem detalhe", "Listra lateral", "Patch pixel", "Joelheiras", "Painéis cargo", "Faixas", "Diamantes")
    val HAIR_ACCENTS = arrayOf("Sem mechas", "Mecha central", "Duas mechas", "Pontas coloridas", "Franja bicolor", "Reflexos", "Mecha lateral", "Neon alternado", "Duas mechas finas")

    // 26 slots persistidos; cores de roupa/acessórios usam a paleta expandida de 32 cores.
    val COUNTS = intArrayOf(2, 10, 50, 24, 9, 16, 2, 18, 32, 8, 32, 8, 32, 32, 32, 13, 10, 32, 10, 32, 6, 8, 32, 7, 32, 9, 32)


    /** looks prontos: 21 valores na ordem dos índices acima */
    val PRESET_NAMES = arrayOf("Ninja", "Colegial", "Mago", "Samurai", "Princesa", "Maid", "Príncipe", "Gatinha", "Raposa", "Colegial (menino)", "Cyberpunk", "Carmesim Infernal", "Aurora Glacial", "Streetwear", "Sakura", "Cavaleiro Solar", "Gótica Real", "Explorador", "Neon Arcade", "Floresta Mística", "Serafim Celeste", "Anime Hoodie Branco", "Guerreiro do Vazio", "Demônio de Chifres Rubros", "Techwear Monocromático", "Mago Azul e Ouro", "Streetwear Pastel", "Gatinho Urbano", "Kitsune Neon", "Princesa Lunar", "Cyber Oni", "Bruxa Carmesim", "Anjo Cristal", "Guardião Tático", "Flor de Sakura", "Hoodie Techwear")
    val PRESETS = arrayOf(
        intArrayOf(0, 1, 8, 4, 5, 5, 0, 1, 3, 0, 3, 0, 3, 11, 4, 8, 5, 3, 7, 4, 1),
        intArrayOf(1, 0, 16, 4, 4, 0, 1, 8, 11, 5, 11, 6, 3, 0, 4, 0, 9, 4, 0, 4, 0),
        intArrayOf(0, 0, 32, 8, 3, 3, 0, 11, 9, 0, 11, 0, 10, 1, 9, 0, 1, 11, 0, 6, 1),
        intArrayOf(0, 2, 28, 4, 5, 5, 0, 14, 11, 4, 3, 5, 10, 0, 4, 5, 5, 10, 0, 4, 1),
        intArrayOf(1, 0, 6, 2, 4, 0, 1, 2, 1, 0, 1, 2, 1, 15, 6, 0, 3, 2, 3, 8, 0),
        intArrayOf(1, 0, 19, 1, 0, 4, 1, 15, 3, 0, 3, 6, 3, 20, 2, 0, 0, 3, 5, 4, 5),
        intArrayOf(0, 1, 11, 2, 0, 0, 1, 12, 11, 0, 11, 6, 3, 4, 6, 0, 1, 4, 4, 4, 0),
        intArrayOf(1, 0, 41, 8, 7, 6, 1, 1, 2, 1, 3, 1, 2, 5, 2, 7, 4, 2, 6, 4, 5),
        intArrayOf(0, 1, 10, 10, 6, 6, 1, 10, 4, 0, 3, 5, 10, 14, 5, 0, 8, 5, 0, 4, 4),
        intArrayOf(0, 0, 15, 4, 0, 5, 0, 9, 11, 0, 11, 6, 3, 0, 4, 9, 9, 10, 0, 4, 1),
        intArrayOf(0, 2, 10, 6, 5, 7, 0, 3, 3, 7, 3, 1, 3, 18, 7, 2, 2, 3, 8, 7, 1, 7, 22, 1, 7, 7, 24),
        intArrayOf(0, 4, 13, 11, 5, 8, 0, 5, 13, 7, 3, 4, 3, 13, 13, 5, 6, 13, 8, 13, 1, 4, 6, 3, 6, 3, 6),
        intArrayOf(1, 1, 33, 21, 4, 10, 1, 11, 11, 6, 9, 0, 15, 15, 25, 0, 3, 9, 3, 16, 0, 6, 25, 5, 29, 5, 25),
        intArrayOf(0, 3, 27, 4, 5, 5, 0, 3, 3, 7, 3, 1, 3, 18, 7, 2, 2, 3, 8, 7, 1, 7, 22, 1, 7, 7, 24),
        intArrayOf(1, 1, 6, 17, 4, 0, 1, 0, 1, 5, 13, 2, 1, 10, 1, 3, 0, 10, 2, 1, 0, 5, 18, 2, 17, 3, 18),
        intArrayOf(0, 2, 23, 10, 6, 6, 0, 5, 6, 4, 10, 4, 6, 4, 6, 0, 5, 6, 4, 6, 0, 1, 6, 3, 10, 3, 6),
        intArrayOf(1, 0, 36, 17, 4, 3, 1, 12, 3, 0, 3, 4, 3, 21, 3, 4, 5, 3, 8, 3, 1, 1, 17, 1, 3, 6, 17),
        intArrayOf(0, 3, 31, 5, 5, 7, 0, 16, 10, 7, 10, 1, 10, 16, 10, 3, 2, 10, 3, 10, 1, 7, 7, 4, 7, 7, 7),
        intArrayOf(0, 1, 15, 9, 0, 9, 0, 7, 7, 7, 7, 1, 7, 18, 7, 11, 9, 7, 3, 7, 0, 7, 24, 1, 24, 7, 24),
        intArrayOf(0, 3, 26, 20, 2, 1, 0, 16, 8, 7, 8, 4, 3, 16, 3, 1, 7, 3, 7, 8, 0, 1, 8, 4, 8, 3, 8),
        intArrayOf(1, 2, 25, 12, 1, 1, 1, 4, 7, 6, 7, 0, 7, 14, 7, 3, 8, 7, 7, 7, 0, 5, 23, 2, 23, 2, 23),
        // Coleção inspirada em linguagens visuais recorrentes nas galerias públicas de skins:
        // anime streetwear, armadura dark-fantasy, demônio, techwear, mago azul/dourado e moda pastel.
        intArrayOf(0, 2, 0, 4, 0, 5, 0, 1, 2, 0, 3, 1, 3, 26, 2, 1, 9, 3, 1, 2, 0, 0, 29, 1, 3, 4, 29),
        intArrayOf(0, 1, 12, 4, 5, 8, 0, 5, 3, 7, 3, 0, 15, 24, 3, 8, 6, 9, 8, 6, 1, 7, 9, 4, 3, 7, 9),
        intArrayOf(0, 0, 13, 8, 5, 8, 0, 11, 3, 6, 3, 4, 3, 31, 4, 8, 6, 8, 8, 4, 1, 6, 4, 2, 3, 3, 4),
        intArrayOf(0, 2, 30, 4, 5, 5, 0, 17, 3, 7, 3, 0, 3, 30, 3, 9, 2, 3, 1, 3, 1, 7, 24, 4, 3, 0, 24),
        intArrayOf(0, 1, 32, 8, 5, 10, 0, 11, 8, 0, 3, 0, 6, 25, 6, 0, 1, 6, 9, 6, 0, 6, 6, 4, 6, 5, 6),
        intArrayOf(1, 1, 2, 17, 0, 4, 1, 1, 3, 1, 3, 1, 2, 28, 1, 1, 9, 1, 1, 1, 2, 5, 18, 1, 17, 4, 18),
        intArrayOf(1, 0, 41, 21, 7, 6, 1, 1, 8, 7, 3, 1, 3, 27, 6, 7, 9, 6, 6, 6, 5, 6, 23, 4, 23, 5, 23),
        // Presets autorais com silhuetas novas: os acessórios 3D são peças reais no modelo, não só nomes/cores.
        intArrayOf(1, 4, 10, 10, 7, 7, 1, 3, 3, 7, 3, 1, 3, 14, 4, 2, 8, 10, 8, 14, 5, 7, 24, 4, 3, 7, 24),
        intArrayOf(1, 1, 32, 9, 4, 10, 1, 2, 1, 2, 13, 4, 6, 25, 6, 0, 1, 6, 3, 6, 0, 1, 6, 1, 6, 5, 6),
        intArrayOf(0, 8, 12, 4, 5, 7, 0, 17, 3, 7, 3, 1, 3, 31, 3, 8, 2, 3, 8, 3, 1, 7, 24, 4, 3, 7, 24),
        intArrayOf(1, 3, 6, 11, 4, 8, 1, 11, 13, 6, 3, 4, 3, 24, 4, 8, 6, 13, 8, 13, 1, 6, 4, 5, 3, 4, 13),
        intArrayOf(1, 0, 32, 8, 4, 10, 1, 11, 8, 6, 9, 4, 6, 29, 6, 0, 3, 6, 3, 6, 0, 6, 6, 4, 6, 5, 6),
        intArrayOf(0, 5, 8, 4, 5, 5, 0, 5, 3, 7, 3, 4, 3, 30, 3, 8, 5, 3, 8, 3, 1, 7, 9, 3, 3, 7, 9),
        intArrayOf(1, 1, 33, 17, 4, 4, 1, 2, 1, 5, 13, 2, 1, 28, 6, 0, 1, 13, 2, 13, 0, 5, 18, 1, 13, 4, 18),
        intArrayOf(1, 2, 41, 4, 7, 7, 1, 1, 3, 7, 3, 1, 3, 27, 3, 1, 9, 3, 6, 3, 5, 7, 24, 4, 3, 7, 24))

    fun applyPreset(i: Int) {
        val preset = PRESETS[((i % PRESETS.size) + PRESETS.size) % PRESETS.size]
        val next = DEF.copyOf()
        for (k in 0 until minOf(N, preset.size)) next[k] = preset[k].coerceIn(0, COUNTS[k] - 1)
        // Os looks antigos ganham acabamento coerente sem perder a combinação original.
        if (preset.size <= 21) { next[TOP_PATTERN] = (i + 1) % TOP_PATTERNS.size; next[TOP_ACCENT] = (i * 3 + 6) % PAL.size; next[BOTTOM_PATTERN] = i % BOTTOM_PATTERNS.size; next[BOTTOM_ACCENT] = (i * 2 + 3) % PAL.size; next[HAIR_ACCENT] = i % HAIR_ACCENTS.size }
        v = next
    }

    fun name(ctx: Context): String = ctx.getSharedPreferences("cfg", 0).getString("charname", "") ?: ""
    fun saveName(ctx: Context, s: String) { ctx.getSharedPreferences("cfg", 0).edit().putString("charname", s).apply() }

    fun skin() = SKINS[v[SKIN]]
    fun hair() = HAIRS[v[HAIRC]]
    fun pal(i: Int) = PAL[v[i].coerceIn(0, PAL.size - 1)]

    fun load(ctx: Context) {
        val s = ctx.getSharedPreferences("cfg", 0).getString("char", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
        val out = DEF.copyOf()
        if (s != null) for (i in 0 until minOf(N, s.size)) out[i] = s[i].coerceIn(0, COUNTS[i] - 1)
        v = out
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences("cfg", 0).edit().putString("char", v.joinToString(",")).apply()
    }

    fun set(i: Int, value: Int) {
        val n = v.copyOf(); n[i] = ((value % COUNTS[i]) + COUNTS[i]) % COUNTS[i]
        if (i == GENDER) n[HAIR] = hairFor(n[GENDER], n[HAIR])
        v = n
    }

    fun randomize() {
        val r = java.util.Random()
        val n = DEF.copyOf()
        for (i in 0 until N) n[i] = r.nextInt(COUNTS[i])
        n[HAIR] = hairList(n[GENDER])[r.nextInt(hairList(n[GENDER]).size)]
        n[BLUSH] = 1
        v = n
    }
}
