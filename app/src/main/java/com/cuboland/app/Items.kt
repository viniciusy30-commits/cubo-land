package com.cuboland.app

object Items {
    const val SWORD = 100; const val AXE = 101; const val STAFF = 102; const val PICK = 103

    val inventory = intArrayOf(SWORD, AXE, STAFF, PICK, B.GRASS, B.DIRT, B.STONE, B.SAND, B.WOOD, B.LEAVES,
        B.PLANK, B.BRICK, B.PINK, B.BLUE, B.YELLOW, B.LANTERN)

    fun valid(id: Int) = inventory.contains(id)
    fun isBlock(id: Int) = id in 1..13 && id != B.WATER

    fun name(id: Int) = when (id) {
        SWORD -> "Espada de Cristal"; AXE -> "Machado Gigante"; STAFF -> "Cajado Estrelado"; PICK -> "Picareta Brilhante"
        else -> B.names.getOrElse(id) { "?" }
    }

    fun desc(id: Int) = when (id) {
        SWORD -> "Rápida e leve. Dano 2, golpes velozes."
        AXE -> "Pesada! Dano 3 e empurra bem longe."
        STAFF -> "Dispara estrelas mágicas à distância."
        PICK -> "Quebra blocos rápido e dá uma bicada nos slimes."
        else -> "Bloco de construção. Toque em Colocar para usar."
    }

    fun damage(id: Int) = when (id) { SWORD -> 2; AXE -> 3; STAFF -> 2; else -> 1 }
    fun cooldown(id: Int) = when (id) { SWORD -> 0.4f; AXE -> 0.65f; STAFF -> 0.55f; PICK -> 0.25f; else -> 0.3f }
    fun reach(id: Int) = when (id) { SWORD -> 3.5f; AXE -> 3.3f; else -> 3f }
    fun knock(id: Int) = when (id) { AXE -> 10f; SWORD -> 6f; else -> 4f }
    /** duração da animação de golpe (s) */
    fun swingTime(id: Int) = when (id) { SWORD -> 0.54f; AXE -> 0.66f; PICK -> 0.36f; STAFF -> 0.55f; else -> 0.3f }
    fun breaks(id: Int) = id == PICK || isBlock(id)
}
