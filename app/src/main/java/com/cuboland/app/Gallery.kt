package com.cuboland.app

/**
 * Organização da galeria de blocos: cada tipo de bloco (tijolos, vidros, telhados, tábuas...) vira uma seção própria, com faixa colorida e título.
 * Dentro de uma seção a plaquinha mostra só o que diferencia o bloco (a cor, a madeira...), porque o título já diz a família:
 * seção "TELHADOS CERÂMICOS" + plaquinha "Azul-claro" em vez de repetir "Telhado Cerâmico Azul-claro" 16 vezes.
 */
object Gallery {
    /** title = título da seção; strip = parte do nome que o título já diz (cortada da plaquinha); fb = seção reserva (só recebe o que nenhuma outra quis) */
    class Sec(val title: String, val strip: String, val fb: Boolean, val rule: (String, Int) -> Boolean)

    private fun s(t: String, strip: String, r: (String, Int) -> Boolean) = Sec(t, strip, false, r)
    private fun f(t: String, r: (String, Int) -> Boolean) = Sec(t, "", true, r)

    private fun sw(n: String, vararg p: String): Boolean { for (x in p) if (n.startsWith(x)) return true; return false }
    private fun has(n: String, vararg p: String): Boolean { for (x in p) if (n.contains(x)) return true; return false }

    private val COLORS = hashSetOf("Branco", "Branca", "Laranja", "Magenta", "Azul-claro", "Azul-clara", "Amarelo", "Amarela", "Lima", "Rosa", "Cinza",
        "Cinza-claro", "Cinza-clara", "Ciano", "Roxo", "Roxa", "Azul", "Marrom", "Verde", "Vermelho", "Vermelha", "Preto", "Preta")
    /** "Vidro Azul" -> true, "Vidro Facetado" -> false */
    private fun colored(n: String, prefix: String): Boolean = n.startsWith(prefix) && COLORS.contains(n.substring(prefix.length))

    private val NATUREZA = listOf(
        s("TERRENO", "") { n, _ -> has(n, "Grama", "Terra", "Areia", "Pedra", "Neve", "Gelo", "Argila", "Cascalho", "Lama") && !sw(n, "Tronco", "Caule", "Colmo") },
        s("TRONCOS", "Tronco de ") { n, _ -> sw(n, "Tronco", "Caule", "Colmo", "Bloco de Bambu") },
        s("FOLHAS", "") { n, _ -> has(n, "Folha", "Verruga") },
        s("PLANTAS E CULTIVOS", "") { n, _ -> has(n, "Moita", "Cacto", "Abóbora", "Melancia", "Musgo", "Micélio", "Feno", "Vaso") },
        f("OUTROS") { _, _ -> true }
    )

    private val CONSTRUCAO = listOf(
        s("CONCRETO", "Concreto ") { n, _ -> sw(n, "Concreto ") },
        s("TERRACOTA", "Terracota ") { n, _ -> n == "Terracota" || sw(n, "Terracota ") },
        s("REBOCO", "Reboco ") { n, _ -> sw(n, "Reboco ") },
        s("GESSO", "Gesso ") { n, _ -> sw(n, "Gesso ") },
        f("PEDRAS E MINERAIS") { _, m -> m == Blk.M_STONE },
        s("PEDREGULHOS", "Pedregulho ") { n, _ -> sw(n, "Pedregulho") },
        s("PEDRAS LISAS E POLIDAS", "") { n, _ -> has(n, "Polid") || n == "Pedra Lisa" || n == "Arenito Liso" || n == "Quartzo" },
        s("ARENITO", "") { n, _ -> sw(n, "Arenito") },
        s("TIJOLOS DE PEDRA", "") { n, _ -> sw(n, "Tijolos ") },
        s("TIJOLOS COLORIDOS", "Tijolo ") { n, _ -> n == "Tijolo" || sw(n, "Tijolo ") },
        s("ALVENARIA", "Alvenaria ") { n, _ -> sw(n, "Alvenaria ") },
        s("AZULEJOS", "Azulejo ") { n, _ -> sw(n, "Azulejo ") && !sw(n, "Azulejo Metrô ") },
        s("AZULEJOS METRÔ", "Azulejo Metrô ") { n, _ -> sw(n, "Azulejo Metrô ") },
        s("LADRILHOS HEXAGONAIS", "Ladrilho Hexagonal ") { n, _ -> sw(n, "Ladrilho Hexagonal ") },
        s("CAMINHOS DE PEDRAS", "Caminho de Pedras ") { n, _ -> sw(n, "Caminho de Pedras ") },
        s("SEIXOS", "Seixos ") { n, _ -> sw(n, "Seixos ") },
        s("TÁBUAS DE ÁRVORES", "Tábua de ") { n, _ -> n == "Tábua" || sw(n, "Tábua de ") },
        s("TÁBUAS PINTADAS", "Tábua Pintada ") { n, _ -> sw(n, "Tábua Pintada ") },
        s("TRONCOS DESCASCADOS", "") { n, _ -> has(n, "Descascad") },
        s("TORAS", "Toras ") { n, _ -> sw(n, "Toras ") },
        s("BAMBU E PALHA", "") { n, _ -> sw(n, "Bambu", "Bloco de Bambu", "Palha") },
        s("PISOS DE MADEIRA", "Piso ") { n, _ -> sw(n, "Piso Espinha ", "Piso de Madeira") },
        s("PISOS XADREZ", "Piso Xadrez ") { n, _ -> sw(n, "Piso Xadrez ") },
        s("LAMBRIS", "Lambri ") { n, _ -> sw(n, "Lambri ") },
        s("ENXAIMEL", "Enxaimel ") { n, _ -> sw(n, "Enxaimel ") },
        s("TELHADOS DE RIPAS", "Telhado de Ripas ") { n, _ -> sw(n, "Telhado de Ripas ") },
        s("TELHADOS CERÂMICOS", "Telhado Cerâmico ") { n, _ -> sw(n, "Telhado Cerâmico ") },
        s("TELHADOS", "Telhado ") { n, _ -> sw(n, "Telhado ") },
        s("CUMEEIRAS", "Cumeeira ") { n, _ -> sw(n, "Cumeeira") },
        s("VIDROS LISOS", "") { n, _ -> n == "Vidro" || n == "Vidro Escuro" || n == "Bloco de Vidro" },
        s("VIDROS COLORIDOS", "Vidro ") { n, _ -> colored(n, "Vidro ") },
        s("VIDROS DECORATIVOS", "Vidro ") { n, _ -> sw(n, "Vidro ") && !sw(n, "Vidro Fino") },
        s("VIDROS FINOS", "Vidro Fino ") { n, _ -> n == "Vidro Fino" || n == "Vidro Fino Escuro" || colored(n, "Vidro Fino ") },
        s("VIDROS FINOS DECORATIVOS", "Vidro Fino ") { n, _ -> sw(n, "Vidro Fino ") },
        s("JANELAS COLONIAIS", "Janela Colonial ") { n, _ -> sw(n, "Janela Colonial") },
        s("JANELAS EM ARCO", "Janela em Arco ") { n, _ -> sw(n, "Janela em Arco ") },
        s("JANELAS DE CORAÇÃO", "Janela de Coração ") { n, _ -> sw(n, "Janela de Coração ") },
        s("JANELAS DE ESTRELA", "Janela de Estrela ") { n, _ -> sw(n, "Janela de Estrela ") },
        s("JANELAS DE CORRER", "Janela de Correr ") { n, _ -> sw(n, "Janela de Correr ") },
        s("JANELAS", "Janela ") { n, _ -> sw(n, "Janela ") },
        s("PERSIANAS", "Persiana ") { n, _ -> sw(n, "Persiana") },
        s("VITRAIS", "Vitral ") { n, _ -> sw(n, "Vitral") },
        s("CHAPAS ONDULADAS", "Chapa Ondulada ") { n, _ -> sw(n, "Chapa Ondulada ") },
        f("METAIS") { _, m -> m == Blk.M_METAL },
        s("GEMAS", "Gema ") { n, _ -> sw(n, "Gema ") },
        s("LOSANGOS", "Losangos ") { n, _ -> sw(n, "Losangos ") },
        s("LISTRADOS", "Listrado ") { n, _ -> sw(n, "Listrado ") },
        s("ACOLCHOADOS", "Acolchoado ") { n, _ -> sw(n, "Acolchoado ") },
        s("LAJES", "Laje de ") { n, _ -> sw(n, "Laje de ") },
        s("ESCADAS", "Escada de ") { n, _ -> sw(n, "Escada de ") },
        f("MADEIRA") { _, m -> m == Blk.M_WOOD },
        f("TECIDOS") { _, m -> m == Blk.M_SOFT },
        f("OUTROS") { _, _ -> true }
    )

    private val DECORACAO = listOf(
        s("LÃS", "Lã ") { n, _ -> sw(n, "Lã ") },
        s("TERRACOTA VITRIFICADA", "Terracota Vitrificada ") { n, _ -> sw(n, "Terracota Vitrificada") },
        s("LÂMPADAS", "Lâmpada ") { n, _ -> sw(n, "Lâmpada") },
        s("CRISTAIS", "Cristal ") { n, _ -> sw(n, "Cristal") },
        s("GELATINAS", "Gelatina ") { n, _ -> sw(n, "Gelatina") },
        s("DOCES", "") { n, _ -> has(n, "Algodão", "Bengala", "Pirulito", "Chocolate", "Biscoito") },
        s("CÉU E PAISAGEM", "") { n, _ -> has(n, "Nuvem", "Pôr do Sol", "Céu", "Oceano") },
        f("DIVERSOS") { _, _ -> true }
    )

    private val MOBILIA = listOf(
        s("MESAS", "") { n, _ -> sw(n, "Mesa", "Escrivaninha") },
        s("ASSENTOS", "") { n, _ -> sw(n, "Cadeira", "Banco", "Poltrona", "Sofá") },
        s("QUARTO", "") { n, _ -> sw(n, "Cama", "Cômoda") },
        s("ARMAZENAMENTO", "") { n, _ -> sw(n, "Armário", "Estante", "Barril") },
        s("TAPETES", "Tapete ") { n, _ -> sw(n, "Tapete") },
        s("ELETRÔNICOS", "") { n, _ -> sw(n, "Computador", "Televisão", "Geladeira") },
        f("DIVERSOS") { _, _ -> true }
    )

    private val OUTROS = listOf(f("OUTROS") { _, _ -> true })

    /** seções de cada categoria da galeria, na ordem em que aparecem */
    fun sections(cat: Int): List<Sec> = when (cat) {
        2 -> NATUREZA
        3 -> CONSTRUCAO
        4 -> DECORACAO
        5 -> MOBILIA
        else -> OUTROS
    }

    /** índice da seção do bloco: primeiro as seções normais (na ordem), depois as reservas */
    fun classify(cat: Int, name: String, mat: Int): Int {
        val secs = sections(cat)
        for (i in secs.indices) if (!secs[i].fb && secs[i].rule(name, mat)) return i
        for (i in secs.indices) if (secs[i].fb && secs[i].rule(name, mat)) return i
        return secs.size - 1
    }

    /** texto da plaquinha: o nome sem a parte que o título da seção já diz; nomes compridos quebram em duas linhas */
    fun shortName(sec: Sec, name: String): String {
        var t = name
        if (sec.strip.isNotEmpty() && name.startsWith(sec.strip) && name.length > sec.strip.length) t = name.substring(sec.strip.length)
        if (t.isNotEmpty()) t = t.substring(0, 1).uppercase() + t.substring(1)
        return wrap(t, 12)
    }

    /** quebra no espaço mais perto do meio quando passa de max letras */
    fun wrap(t: String, max: Int): String {
        if (t.length <= max) return t
        var best = -1
        for (i in t.indices) if (t[i] == ' ' && (best < 0 || Math.abs(i - t.length / 2) < Math.abs(best - t.length / 2))) best = i
        if (best < 0) return t
        return t.substring(0, best) + "\n" + t.substring(best + 1)
    }

    private val ACCENTS = arrayOf("Azul", "Vermelho", "Verde", "Laranja", "Roxo", "Ciano", "Amarelo", "Rosa", "Marrom", "Magenta", "Lima", "Azul-claro")
    /** concreto da faixa colorida da seção número i (as cores se alternam pra seções vizinhas se distinguirem) */
    fun accentName(i: Int): String = "Concreto " + ACCENTS[i % ACCENTS.size]
}
