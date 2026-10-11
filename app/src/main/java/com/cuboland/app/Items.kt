package com.cuboland.app

object Items {
    const val SWORD = 100; const val AXE = 101; const val STAFF = 102; const val PICK = 103

    val inventory: IntArray = run {
        val l = ArrayList<Int>()
        for (i in intArrayOf(SWORD, AXE, STAFF, PICK, B.GRASS, B.DIRT, B.STONE, B.SAND, B.WOOD, B.LEAVES,
            B.PLANK, B.BRICK, B.PINK, B.BLUE, B.YELLOW, B.LANTERN)) l.add(i)
        for (d in Blk.defs) if (d.id !in Blk.hidden) l.add(d.id)
        l.toIntArray()
    }

    /** categorias da mochila */
    val CATS = arrayOf("Tudo", "Ferramentas", "Natureza", "Construção", "Decoração")
    fun cat(id: Int) = when (id) {
        SWORD, AXE, STAFF, PICK -> 1
        B.GRASS, B.DIRT, B.SAND, B.WOOD, B.LEAVES -> 2
        B.STONE, B.PLANK, B.BRICK -> 3
        else -> if (id > 13 && id < 16384 && B.cat[id] != 0) B.cat[id] else 4
    }

    fun valid(id: Int) = inventory.contains(id)
    fun isBlock(id: Int) = id in 1..16383 && B.defined[id]

    fun name(id: Int) = when (id) {
        0 -> "Mão"
        SWORD -> "Espada de Cristal"; AXE -> "Machado Gigante"; STAFF -> "Cajado Estrelado"; PICK -> "Picareta Brilhante"
        else -> B.names.getOrElse(id) { "?" }
    }

    fun desc(id: Int) = when (id) {
        0 -> "Sem nada na mão: o personagem fica de mãos livres."
        SWORD -> "Rápida e leve. Dano 2, golpes velozes."
        AXE -> "Pesada! Dano 3, empurra longe e racha madeira bem rápido."
        STAFF -> "Dispara estrelas mágicas à distância."
        PICK -> "Quebra blocos rápido e dá uma bicada nos slimes."
        B.GRASS -> "Terra coberta de grama fofinha. Nascem matinhos e flores em cima."
        B.DIRT -> "Terra macia, boa para construir e para cavar."
        B.STONE -> "Pedra firme e resistente para paredes e castelos."
        B.SAND -> "Areia clarinha de praia. Os pés afundam nela."
        B.WOOD -> "Tronco de árvore. O machado racha rapidinho."
        B.LEAVES -> "Folhas verdinhas para copas e cercas vivas."
        B.PLANK -> "Tábuas lisinhas para casas, pontes e móveis."
        B.BRICK -> "Tijolos vermelhos para construções charmosas."
        B.PINK -> "Bloco rosa docinho, perfeito para decorar."
        B.BLUE -> "Bloco azul brilhante, lembra céu e água."
        B.YELLOW -> "Bloco amarelo alegre, cor de sol."
        B.LANTERN -> "Brilha com uma luz quentinha. Ilumina a noite."
        else -> if (id in 14..16383 && B.desc[id].isNotEmpty()) B.desc[id] else "Bloco de construção. Toque em Colocar para usar."
    }

    fun damage(id: Int) = when (id) { SWORD -> 2; AXE -> 3; STAFF -> 2; else -> 1 }
    fun cooldown(id: Int) = when (id) { SWORD -> 0.4f; AXE -> 0.65f; STAFF -> 0.55f; PICK -> 0.25f; else -> 0.3f }
    fun reach(id: Int) = when (id) { SWORD -> 3.5f; AXE -> 3.3f; else -> 3f }
    fun knock(id: Int) = when (id) { AXE -> 10f; SWORD -> 6f; else -> 4f }
    /** duração da animação de golpe (s) */
    fun swingTime(id: Int) = when (id) { SWORD -> 0.54f; AXE -> 0.66f; PICK -> 0.36f; STAFF -> 0.55f; else -> 0.3f }
    fun breaks(id: Int) = id == PICK || id == AXE || isBlock(id)
}
