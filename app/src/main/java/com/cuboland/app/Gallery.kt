package com.cuboland.app

/**
 * Organização da galeria de blocos: cada tipo de bloco (tijolos, vidros, telhados, tábuas...) vira uma seção própria, com faixa colorida e título.
 * Dentro de uma seção a plaquinha mostra só o que diferencia o bloco (a cor, a madeira...), porque o título já diz a família:
 * seção "TELHADOS CERÂMICOS" + plaquinha "Azul-claro" em vez de repetir "Telhado Cerâmico Azul-claro" 16 vezes.
 */
object Gallery {
    /** blocos por página na sala de exposição (6 colunas x 4 fileiras) */
    const val PER_PAGE = 24

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
        f("PEDRAS E MINERAIS") { _, m -> m == Blk.M_STONE },
        s("PEDREGULHOS", "Pedregulho ") { n, _ -> sw(n, "Pedregulho") },
        s("PEDRAS LISAS E POLIDAS", "") { n, _ -> has(n, "Polid") || n == "Pedra Lisa" || n == "Arenito Liso" || n == "Quartzo" },
        s("ARENITO", "") { n, _ -> sw(n, "Arenito") },
        s("TIJOLOS DE PEDRA", "") { n, _ -> sw(n, "Tijolos ") },
        s("TIJOLOS COLORIDOS", "Tijolo ") { n, _ -> n == "Tijolo" || sw(n, "Tijolo ") },
        s("TÁBUAS DE ÁRVORES", "Tábua de ") { n, _ -> n == "Tábua" || sw(n, "Tábua de ") },
        s("TRONCOS DESCASCADOS", "") { n, _ -> has(n, "Descascad") },
        s("BAMBU E PALHA", "") { n, _ -> sw(n, "Bambu", "Bloco de Bambu", "Palha") },
        s("TELHADOS", "Telhado ") { n, _ -> sw(n, "Telhado ") },
        s("CUMEEIRAS", "Cumeeira ") { n, _ -> sw(n, "Cumeeira") },
        s("VIDROS LISOS", "") { n, _ -> n == "Vidro" || n == "Vidro Escuro" || n == "Bloco de Vidro" },
        s("VIDROS COLORIDOS", "Vidro ") { n, _ -> colored(n, "Vidro ") },
        s("JANELAS COLONIAIS", "Janela Colonial ") { n, _ -> sw(n, "Janela Colonial") },
        s("JANELAS DE CORRER", "Janela de Correr ") { n, _ -> sw(n, "Janela de Correr ") },
        s("JANELAS", "Janela ") { n, _ -> sw(n, "Janela ") },
        s("PERSIANAS", "Persiana ") { n, _ -> sw(n, "Persiana") },
        f("METAIS") { _, m -> m == Blk.M_METAL },
        s("LAJES", "Laje de ") { n, _ -> sw(n, "Laje de ") },
        s("ESCADAS", "Escada de ") { n, _ -> sw(n, "Escada de ") },
        f("MADEIRA") { _, m -> m == Blk.M_WOOD },
        f("TECIDOS") { _, m -> m == Blk.M_SOFT },
        f("OUTROS") { _, _ -> true }
    )

    private val DECORACAO = listOf(
        s("LÃS", "Lã ") { n, _ -> sw(n, "Lã ") },
        s("TERRACOTA VITRIFICADA", "Terracota Vitrificada ") { n, _ -> sw(n, "Terracota Vitrificada") },
        s("BLOCOS LUMINOSOS", "Bloco Luminoso ") { n, _ -> sw(n, "Bloco Luminoso") },
        s("CRISTAIS", "Cristal ") { n, _ -> sw(n, "Cristal") },
        s("GELATINAS", "Gelatina ") { n, _ -> sw(n, "Gelatina") },
        s("DOCES", "") { n, _ -> has(n, "Algodão", "Bengala", "Pirulito", "Chocolate", "Biscoito") },
        s("CÉU E PAISAGEM", "") { n, _ -> has(n, "Nuvem", "Pôr do Sol", "Céu", "Oceano") },
        f("DIVERSOS") { _, _ -> true }
    )

    /** móveis da mobília v3 (FurnC): uma seção por grupo; vêm antes das outras pra não cair em "MESAS", "ASSENTOS"... */
    private fun fc(title: String) = s(title, "") { n, _ -> FurnC.groupOf(n) == title }

    private val MOBILIA = FurnC.GROUPS.map { fc(it) } + listOf(
        s("SOFÁS MODERNOS", "Sofá Moderno ") { n, _ -> sw(n, "Sofá Moderno") },
        s("SOFÁS CHESTERFIELD", "Sofá Chesterfield ") { n, _ -> sw(n, "Sofá Chesterfield") },
        s("POLTRONAS ORELHUDAS", "Poltrona Orelhuda ") { n, _ -> sw(n, "Poltrona Orelhuda") },
        s("PUFFS", "Puff ") { n, _ -> sw(n, "Puff") },
        s("CADEIRAS MODERNAS", "Cadeira Moderna ") { n, _ -> sw(n, "Cadeira Moderna") },
        s("BANQUETAS DE BAR", "Banqueta de Bar ") { n, _ -> sw(n, "Banqueta") },
        s("BANCOS DE JARDIM", "Banco de Jardim ") { n, _ -> sw(n, "Banco de Jardim") },
        s("DIVÃS", "Divã ") { n, _ -> sw(n, "Divã") },
        s("MESAS DE JANTAR", "Mesa de Jantar ") { n, _ -> sw(n, "Mesa de Jantar") },
        s("MESAS DE CABECEIRA", "Mesa de Cabeceira ") { n, _ -> sw(n, "Mesa de Cabeceira") },
        s("MESAS LATERAIS", "Mesa Lateral ") { n, _ -> sw(n, "Mesa Lateral") },
        s("BALCÕES DE COZINHA", "Balcão de Cozinha ") { n, _ -> sw(n, "Balcão") },
        s("APARADORES", "Aparador ") { n, _ -> sw(n, "Aparador") },
        s("MESAS", "") { n, _ -> sw(n, "Mesa", "Escrivaninha") },
        s("ASSENTOS", "") { n, _ -> sw(n, "Cadeira", "Banco", "Poltrona", "Sofá") },
        s("CAMAS MODERNAS", "Cama Moderna ") { n, _ -> sw(n, "Cama Moderna") },
        s("BERÇOS", "Berço ") { n, _ -> sw(n, "Berço") },
        s("QUARTO", "") { n, _ -> sw(n, "Cama", "Cômoda") },
        s("ARMÁRIOS ALTOS", "Armário Alto ") { n, _ -> sw(n, "Armário Alto") },
        s("GUARDA-ROUPAS", "Guarda-Roupa Moderno ") { n, _ -> sw(n, "Guarda-Roupa") },
        s("ESTANTES MODERNAS", "Estante Moderna ") { n, _ -> sw(n, "Estante Moderna") },
        s("CRISTALEIRAS", "Cristaleira ") { n, _ -> sw(n, "Cristaleira") },
        s("BAÚS", "Baú ") { n, _ -> sw(n, "Baú") },
        s("ARMÁRIOS SUSPENSOS", "Armário Suspenso ") { n, _ -> sw(n, "Armário Suspenso") },
        s("ARMAZENAMENTO", "") { n, _ -> sw(n, "Armário", "Estante", "Barril") },
        s("GELADEIRAS RETRÔ", "Geladeira Retrô ") { n, _ -> sw(n, "Geladeira Retrô") },
        s("GELADEIRAS", "Geladeira ") { n, _ -> sw(n, "Geladeira") },
        s("FRIGOBARES", "Frigobar ") { n, _ -> sw(n, "Frigobar") },
        s("FOGÕES", "Fogão ") { n, _ -> sw(n, "Fogão") },
        s("MICRO-ONDAS", "Micro-ondas ") { n, _ -> sw(n, "Micro-ondas") },
        s("MÁQUINAS DE LAVAR", "Máquina de Lavar ") { n, _ -> sw(n, "Máquina de Lavar") },
        s("PIAS DE COZINHA", "Pia de Cozinha ") { n, _ -> sw(n, "Pia de Cozinha") },
        s("COIFAS", "Coifa ") { n, _ -> sw(n, "Coifa") },
        s("VASOS SANITÁRIOS", "Vaso Sanitário ") { n, _ -> sw(n, "Vaso Sanitário") },
        s("PIAS DE BANHEIRO", "Pia de Banheiro ") { n, _ -> sw(n, "Pia de Banheiro") },
        s("BANHEIRAS", "Banheira ") { n, _ -> sw(n, "Banheira") },
        s("CORTINAS ABERTAS", "Cortina Aberta ") { n, _ -> sw(n, "Cortina Aberta") },
        s("CORTINAS FECHADAS", "Cortina Fechada ") { n, _ -> sw(n, "Cortina Fechada") },
        s("CORTINAS TRANSPARENTES", "Cortina Transparente ") { n, _ -> sw(n, "Cortina Transparente") },
        s("CORTINAS COM BABADO", "Cortina com Babado ") { n, _ -> sw(n, "Cortina com Babado") },
        s("CORTINAS LISTRADAS", "Cortina Listrada ") { n, _ -> sw(n, "Cortina Listrada") },
        s("PERSIANAS ROMANAS", "Persiana Romana ") { n, _ -> sw(n, "Persiana Romana") },
        s("ABAJURES", "Abajur ") { n, _ -> sw(n, "Abajur") },
        s("LUMINÁRIAS DE CHÃO", "Luminária de Chão ") { n, _ -> sw(n, "Luminária de Chão") },
        s("LUMINÁRIAS ARCO", "Luminária Arco ") { n, _ -> sw(n, "Luminária Arco") },
        s("PENDENTES", "Pendente ") { n, _ -> sw(n, "Pendente") },
        s("LUSTRES", "Lustre ") { n, _ -> sw(n, "Lustre") },
        s("ARANDELAS", "Arandela ") { n, _ -> sw(n, "Arandela") },
        s("LUMINÁRIAS DE MESA", "Luminária de Mesa ") { n, _ -> sw(n, "Luminária de Mesa") },
        s("LANTERNAS DE PAPEL", "Lanterna de Papel ") { n, _ -> sw(n, "Lanterna de Papel") },
        s("CANDELABROS", "Candelabro ") { n, _ -> sw(n, "Candelabro") },
        s("LUZES NOTURNAS", "Luz Noturna ") { n, _ -> sw(n, "Luz Noturna") },
        s("LÂMPADAS DE LAVA", "Lâmpada de Lava ") { n, _ -> sw(n, "Lâmpada de Lava") },
        s("PALMEIRAS", "Palmeira em Vaso ") { n, _ -> sw(n, "Palmeira") },
        s("SAMAMBAIAS", "Samambaia ") { n, _ -> sw(n, "Samambaia") },
        s("CACTOS EM VASO", "Cacto em Vaso ") { n, _ -> sw(n, "Cacto em Vaso") },
        s("VASOS DE FLORES", "Vaso de Flores ") { n, _ -> sw(n, "Vaso de Flores") },
        s("BONSAIS", "Bonsai ") { n, _ -> sw(n, "Bonsai") },
        s("QUADROS", "Quadro ") { n, _ -> sw(n, "Quadro") },
        s("ESPELHOS", "Espelho ") { n, _ -> sw(n, "Espelho") },
        s("RELÓGIOS DE PAREDE", "Relógio de Parede ") { n, _ -> sw(n, "Relógio de Parede") },
        s("RELÓGIOS DE PÉ", "Relógio de Pé ") { n, _ -> sw(n, "Relógio de Pé") },
        s("PILHAS DE LIVROS", "Pilha de Livros ") { n, _ -> sw(n, "Pilha de Livros") },
        s("FRUTEIRAS", "Fruteira ") { n, _ -> sw(n, "Fruteira") },
        s("GLOBOS", "Globo ") { n, _ -> sw(n, "Globo") },
        s("LAREIRAS", "Lareira ") { n, _ -> sw(n, "Lareira") },
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
