package com.cuboland.app

import android.content.Context

/** Aparência do personagem (tudo guardado como inteiros, um por opção). */
object Look {
    const val GENDER = 0; const val SKIN = 1; const val HAIR = 2; const val HAIRC = 3; const val EYES = 4; const val EYEC = 5
    const val BLUSH = 6; const val TOP = 7; const val TOPC = 8; const val BOTTOM = 9; const val BOTTOMC = 10
    const val SHOES = 11; const val SHOEC = 12; const val HAT = 13; const val HATC = 14; const val FACE = 15
    const val BACK = 16; const val BACKC = 17; const val NECK = 18; const val NECKC = 19; const val MOUTH = 20
    const val N = 21

    val DEF = intArrayOf(1, 1, 2, 0, 0, 3, 1, 4, 0, 0, 8, 0, 10, 7, 1, 0, 0, 4, 1, 1, 0)
    @Volatile var v: IntArray = DEF.copyOf()

    val SKINS = intArrayOf(0xFFE3CF, 0xF2C29B, 0xD9A07A, 0xB57A55, 0x8A5A3C, 0x5E3B28)
    val SKIN_NAMES = arrayOf("Porcelana", "Clara", "Dourada", "Morena", "Canela", "Chocolate")
    val HAIRS = intArrayOf(0x6B3FA0, 0xF08CB8, 0xF2D06B, 0x7A4A2A, 0x2A2430, 0xD2562C, 0x4A7FE0, 0x6FD6B0, 0xF2F2F6, 0xB8C0D0, 0xF29A3C, 0x8E2A4A, 0x3FA66B, 0xE03030, 0x2A3A8C, 0x30C8E0)
    val HAIR_NAMES = arrayOf("Roxo", "Rosa", "Loiro", "Castanho", "Preto", "Ruivo", "Azul", "Menta", "Branco", "Prata", "Laranja", "Vinho", "Verde", "Vermelho", "Marinho", "Ciano")
    val EYE_COLORS = intArrayOf(0x4A8BE8, 0x3FA66B, 0x8B5A3C, 0x9A5BD8, 0xE0507A, 0x2C2C3A, 0xE8A030, 0x30C0C8, 0xD03030, 0xF0C030)
    val EYE_COLOR_NAMES = arrayOf("Azul", "Verde", "Castanho", "Roxo", "Rosa", "Preto", "Âmbar", "Ciano", "Vermelho", "Dourado")
    val PAL = intArrayOf(0x7FD9C8, 0xFFA3C8, 0xF4F4F4, 0x30323C, 0xE05555, 0xF2A04A, 0xFFD35C, 0x6CC070, 0x5B8DEE, 0x9A78E0, 0x9A6A3E, 0x3B4A8C, 0xE8D5B0, 0x8E2A4A, 0x8A8E9A, 0x2AA6B0)
    val PAL_NAMES = arrayOf("Menta", "Rosa", "Branco", "Preto", "Vermelho", "Laranja", "Amarelo", "Verde", "Azul", "Roxo", "Marrom", "Marinho", "Bege", "Vinho", "Cinza", "Turquesa")

    val GENDER_NAMES = arrayOf("Menino", "Menina")
    val HAIR_STYLES = arrayOf("Curto", "Espetado", "Chanel", "Maria-chiquinha", "Rabo de cavalo", "Coque", "Longo", "Fofo", "Undercut", "Franja de lado", "Bagunçado", "Topete", "Saiyajin", "Raspado", "Moicano", "Repartido", "Hime", "Trança", "Dois coques", "Twintail alto", "Ahoge", "Degradê", "Social", "Pompadour", "Cacheado curto", "Black power", "Dreads", "Mullet", "Coque samurai", "Cortina", "Pra trás", "Lobo", "Longo liso", "Ondulado", "Cacheado longo", "Tranças box", "Emo", "Militar", "Cogumelo", "Rabo baixo", "Pixie", "Bob assimétrico", "Rabo alto", "Coque bailarina", "Princesa", "Trança lateral", "Tranças duplas", "Meio preso", "Cachos de princesa", "Twin drills")
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
    val HATS = arrayOf("Nenhum", "Chapéu de bruxo", "Gorro de lã", "Boné", "Coroa", "Orelhas de gato", "Orelhas de coelho", "Laço grande", "Capacete", "Chapéu de palha", "Florzinha", "Faixa ninja", "Auréola", "Chifrinhos", "Orelhas de raposa", "Tiara", "Boina", "Chapéu kasa", "Fones", "Capuz", "Touca de maid", "Cartola", "Chapéu de chef", "Capacete viking")
    val FACES = arrayOf("Nada", "Óculos redondos", "Óculos escuros", "Sardas", "Tapa-olho", "Cicatriz", "Curativo", "Bigodes de gato", "Máscara", "Óculos quadrados", "Lágrima", "Monóculo", "Óculos aviador")
    val BACKS = arrayOf("Nada", "Capa", "Mochila", "Asinhas", "Rabinho", "Espada nas costas", "Asas de morcego", "Aljava", "Rabo de raposa", "Mochila escolar")
    val MOUTHS = arrayOf("Sorriso", "Neutra", "Biquinho", "Aberta", "Dentinho", "Gatinho")
    val NECKS = arrayOf("Nada", "Cachecol", "Laço", "Colar", "Gravata", "Gravata borboleta", "Coleira de sino", "Lenço", "Gargantilha", "Medalha")

    val COUNTS = intArrayOf(2, 6, 50, 16, 9, 10, 2, 18, 16, 8, 16, 8, 16, 24, 16, 13, 10, 16, 10, 16, 6)


    /** looks prontos: 21 valores na ordem dos índices acima */
    val PRESET_NAMES = arrayOf("Ninja", "Colegial", "Mago", "Samurai", "Princesa", "Maid", "Príncipe", "Gatinha", "Raposa", "Colegial (menino)")
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
        intArrayOf(0, 0, 15, 4, 0, 5, 0, 9, 11, 0, 11, 6, 3, 0, 4, 9, 9, 10, 0, 4, 1))

    fun applyPreset(i: Int) { v = PRESETS[((i % PRESETS.size) + PRESETS.size) % PRESETS.size].copyOf() }

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
