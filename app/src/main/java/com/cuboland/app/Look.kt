package com.cuboland.app

import android.content.Context

/** Aparência do personagem (tudo guardado como inteiros, um por opção). */
object Look {
    const val GENDER = 0; const val SKIN = 1; const val HAIR = 2; const val HAIRC = 3; const val EYES = 4; const val EYEC = 5
    const val BLUSH = 6; const val TOP = 7; const val TOPC = 8; const val BOTTOM = 9; const val BOTTOMC = 10
    const val SHOES = 11; const val SHOEC = 12; const val HAT = 13; const val HATC = 14; const val FACE = 15
    const val BACK = 16; const val BACKC = 17; const val NECK = 18; const val NECKC = 19
    const val N = 20

    val DEF = intArrayOf(1, 1, 2, 0, 0, 3, 1, 4, 0, 0, 8, 0, 10, 7, 1, 0, 0, 4, 1, 1)
    @Volatile var v: IntArray = DEF.copyOf()

    val SKINS = intArrayOf(0xFFE3CF, 0xF2C29B, 0xD9A07A, 0xB57A55, 0x8A5A3C, 0x5E3B28)
    val SKIN_NAMES = arrayOf("Porcelana", "Clara", "Dourada", "Morena", "Canela", "Chocolate")
    val HAIRS = intArrayOf(0x6B3FA0, 0xF08CB8, 0xF2D06B, 0x7A4A2A, 0x2A2430, 0xD2562C, 0x4A7FE0, 0x6FD6B0, 0xF2F2F6, 0xB8C0D0, 0xF29A3C, 0x8E2A4A)
    val HAIR_NAMES = arrayOf("Roxo", "Rosa", "Loiro", "Castanho", "Preto", "Ruivo", "Azul", "Menta", "Branco", "Prata", "Laranja", "Vinho")
    val EYE_COLORS = intArrayOf(0x4A8BE8, 0x3FA66B, 0x8B5A3C, 0x9A5BD8, 0xE0507A, 0x2C2C3A, 0xE8A030, 0x30C0C8)
    val EYE_COLOR_NAMES = arrayOf("Azul", "Verde", "Castanho", "Roxo", "Rosa", "Preto", "Âmbar", "Ciano")
    val PAL = intArrayOf(0x7FD9C8, 0xFFA3C8, 0xF4F4F4, 0x30323C, 0xE05555, 0xF2A04A, 0xFFD35C, 0x6CC070, 0x5B8DEE, 0x9A78E0, 0x9A6A3E, 0x3B4A8C)
    val PAL_NAMES = arrayOf("Menta", "Rosa", "Branco", "Preto", "Vermelho", "Laranja", "Amarelo", "Verde", "Azul", "Roxo", "Marrom", "Marinho")

    val GENDER_NAMES = arrayOf("Menino", "Menina")
    val HAIR_STYLES = arrayOf("Curto", "Espetado", "Chanel", "Maria-chiquinha", "Rabo de cavalo", "Coque", "Longo", "Fofo")
    val EYE_STYLES = arrayOf("Grandes", "Felizes", "Bolinhas", "Sonolentos", "Brilhantes")
    val ONOFF = arrayOf("Não", "Sim")
    val TOPS = arrayOf("Camiseta", "Moletom", "Vestido", "Jaqueta", "Túnica", "Armadura", "Macacão")
    val BOTTOMS = arrayOf("Calça", "Shorts", "Saia", "Bermuda")
    val SHOE_STYLES = arrayOf("Botas", "Tênis", "Sapatilha", "Descalço", "Botas altas")
    val HATS = arrayOf("Nenhum", "Chapéu de bruxo", "Gorro de lã", "Boné", "Coroa", "Orelhas de gato", "Orelhas de coelho", "Laço grande", "Capacete", "Chapéu de palha", "Florzinha")
    val FACES = arrayOf("Nada", "Óculos redondos", "Óculos escuros", "Sardas")
    val BACKS = arrayOf("Nada", "Capa", "Mochila", "Asinhas", "Rabinho")
    val NECKS = arrayOf("Nada", "Cachecol", "Laço", "Colar")

    val COUNTS = intArrayOf(2, 6, 8, 12, 5, 8, 2, 7, 12, 4, 12, 5, 12, 11, 12, 4, 5, 12, 4, 12)

    fun skin() = SKINS[v[SKIN]]
    fun hair() = HAIRS[v[HAIRC]]
    fun pal(i: Int) = PAL[v[i].coerceIn(0, PAL.size - 1)]

    fun load(ctx: Context) {
        val s = ctx.getSharedPreferences("cfg", 0).getString("char", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
        val out = DEF.copyOf()
        if (s != null && s.size == N) for (i in 0 until N) out[i] = s[i].coerceIn(0, COUNTS[i] - 1)
        v = out
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences("cfg", 0).edit().putString("char", v.joinToString(",")).apply()
    }

    fun set(i: Int, value: Int) {
        val n = v.copyOf(); n[i] = ((value % COUNTS[i]) + COUNTS[i]) % COUNTS[i]; v = n
    }

    fun randomize() {
        val r = java.util.Random()
        val n = DEF.copyOf()
        for (i in 0 until N) n[i] = r.nextInt(COUNTS[i])
        n[BLUSH] = 1
        v = n
    }
}
