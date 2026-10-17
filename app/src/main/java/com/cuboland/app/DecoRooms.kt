package com.cuboland.app

/**
 * Galeria "APLICAÇÕES": um ambiente para cada grupo de mobília/decoração (cozinha, banheiro, quartos, loja, jardim...),
 * com piso e paredes do tema. Móveis em fileiras no chão, quadros/relógios/cortinas pendurados nas paredes,
 * janelas dentro da parede e luminárias de teto penduradas. Tudo que está em Deco.items entra, cada item com plaquinha.
 * Cada ambiente tem no máximo MAXD de fundo; o que não cabe vai pra "parte 2", "parte 3"...
 */
object DecoRooms {
    @JvmField var spawnX = 10
    @JvmField var spawnZ = 6
    @JvmField var notPlaced = 0

    private const val ROOM_W = 36
    private const val COL_W = 42
    private const val MAXD = 36
    private const val WALL_H = 7

    private class Slot(val item: Deco.Item, val w: Int, val d: Int, val ceil: Boolean) {
        var x = 0; var z = 0
    }

    private val THEME = hashMapOf(
        "ASSENTOS" to "Tábua|Concreto Branco", "MESAS E BANCADAS" to "Tábua|Concreto Branco", "CAMAS E BERÇOS" to "Tábua|Concreto Rosa",
        "ARMÁRIOS E ESTANTES" to "Tábua|Concreto Branco", "COZINHA" to "Concreto Branco|Concreto Amarelo",
        "BANHEIRO E LIMPEZA" to "Concreto Azul-claro|Concreto Ciano", "OUTROS MÓVEIS" to "Tábua|Concreto Cinza-claro",
        "QUADROS E ESPELHOS" to "Concreto Cinza-claro|Concreto Branco", "ILUMINAÇÃO" to "Concreto Preto|Concreto Cinza",
        "BONECOS E BRINQUEDOS" to "Concreto Amarelo|Concreto Laranja", "TÊXTEIS E TAPETES" to "Concreto Marrom|Concreto Branco",
        "PLANTAS" to "Grama|", "ELETRÔNICOS E MÚSICA" to "Concreto Preto|Concreto Cinza", "ELETRÔNICOS E TECNOLOGIA" to "Concreto Preto|Concreto Cinza",
        "RELÓGIOS" to "Tábua|Concreto Branco", "PLACAS E SINALIZAÇÃO" to "Concreto Cinza|Concreto Branco",
        "COMIDAS E BEBIDAS" to "Concreto Branco|Concreto Laranja", "PORTAS E JANELAS" to "Concreto Cinza-claro|Concreto Branco",
        "CERCAS E EXTERIOR" to "Grama|", "ROUPAS E ACESSÓRIOS" to "Tábua|Concreto Roxo", "CAIXAS E LOJA" to "Concreto Cinza-claro|Concreto Laranja",
        "ESPORTE E MÚSICA" to "Concreto Verde|Concreto Branco", "HOSPITAL E OFICINA" to "Concreto Branco|Concreto Ciano",
        "ESTRUTURAS E TELHADOS" to "Concreto Cinza-claro|Concreto Cinza", "SUPORTES E EXPOSITORES" to "Concreto Cinza-claro|Concreto Branco"
    )

    private fun blockOf(name: String, fb: Int): Int {
        if (name.isEmpty()) return 0
        val b = B.byName[name]
        return if (b == null) fb else b
    }

    private fun isWallItem(fd: String): Boolean {
        if (fd.contains("wall") || fd.contains("picture") || fd.contains("canvas") || fd.contains("poster") || fd.contains("modern_art")) return true
        if (fd.contains("convex") || fd.contains("curtain") || fd.contains("blind") || fd.contains("banner") || fd.contains("calendar")) return true
        if (fd.contains("photo") || fd.contains("hanging_sign") || fd.contains("sconce") || fd.contains("frame")) return true
        return false
    }
    private fun isCeilItem(fd: String): Boolean {
        return fd.contains("ceiling") || fd.contains("pendant") || fd.contains("chandelier") || fd.contains("hanging_light")
    }

    private fun put(w: World, x: Int, y: Int, z: Int, id: Int) {
        if (x < 0 || x >= World.SX || z < 0 || z >= World.SZ || y < 0 || y >= World.SY) return
        w.blocks[(y * World.SZ + z) * World.SX + x] = id
    }

    fun build(w: World, top: Int) {
        notPlaced = 0
        val sx = World.SX; val sz = World.SZ
        val groups = ArrayList<String>()
        for (g in Deco.groups(5)) groups.add(g)
        for (g in Deco.groups(4)) if (!groups.contains(g)) groups.add(g)
        val cols = maxOf(1, (sx - 6) / COL_W)
        val colZ = IntArray(cols)
        for (i in 0 until cols) colZ[i] = 24
        val lamp = B.byName["Bloco Luminoso Branco"]
        spawnX = 6 + ROOM_W / 2; spawnZ = 10
        w.labels.add(World.Label(spawnX + 0.5f, top + 5f, 16f, "APLICAÇÕES", true))
        w.labels.add(World.Label(spawnX + 0.5f, top + 3.4f, 16f, "Um ambiente para cada tipo de móvel e decoração", false))

        for (g in groups) {
            val theme = THEME[g]
            var floorName = "Concreto Cinza-claro"; var wallName = "Concreto Branco"
            if (theme != null) { val p = theme.split("|"); floorName = p[0]; wallName = if (p.size > 1) p[1] else "" }
            val floorB = blockOf(floorName, B.byName["Concreto Cinza-claro"] ?: B.STONE)
            val wallB = blockOf(wallName, 0)
            val floorItems = ArrayList<Slot>(); val wallItems = ArrayList<Deco.Item>(); val ceilItems = ArrayList<Slot>()
            for (it0 in Deco.items) {
                if (it0.group != g || it0.blockId <= 0) continue
                if (isWallItem(it0.fd)) wallItems.add(it0)
                else {
                    val fp = it0.fp
                    var wd = 1; var dd = 1
                    if (fp != null) { wd = fp[1] - fp[0] + 1; dd = fp[5] - fp[4] + 1 }
                    val s = Slot(it0, maxOf(wd + 2, 4), maxOf(dd + 2, 4), isCeilItem(it0.fd))
                    if (s.ceil) ceilItems.add(s) else floorItems.add(s)
                }
            }
            val all = ArrayList<Slot>(); all.addAll(floorItems); all.addAll(ceilItems)
            var next = 0; var wnext = 0; var part = 1
            while (next < all.size || wnext < wallItems.size) {
                var c = 0
                for (i in 1 until cols) if (colZ[i] < colZ[c]) c = i
                val x0 = 4 + c * COL_W + 2
                val z0 = colZ[c] + 1
                // empacota as fileiras do chão (margem de 2 blocos pras paredes)
                var curX = 0; var rowZ = 0; var rowH = 0
                val placed = ArrayList<Slot>()
                var hasCeil = false
                while (next < all.size) {
                    val s = all[next]
                    if (curX + s.w > ROOM_W - 4) { rowZ += rowH; curX = 0; rowH = 0 }
                    if (rowZ + s.d > MAXD) break
                    var fpx = 0; var fpz = 0
                    val fp = s.item.fp
                    if (fp != null) { fpx = -fp[0]; fpz = -fp[4] }
                    s.x = x0 + 2 + curX + 1 + fpx
                    s.z = z0 + 2 + rowZ + 1 + fpz
                    curX += s.w; if (s.d > rowH) rowH = s.d
                    if (s.ceil) hasCeil = true
                    placed.add(s); next++
                }
                var depth = rowZ + rowH + 3
                if (depth < 16) depth = 16
                // peças de parede: fundo + laterais, uma a cada 3 blocos
                val wallSlots = ArrayList<IntArray>()
                var xx = x0 + 2
                while (xx < x0 + ROOM_W - 2) { wallSlots.add(intArrayOf(xx, z0)); xx += 3 }
                var zz = z0 + 3
                while (zz < z0 + depth - 2) { wallSlots.add(intArrayOf(x0, zz)); wallSlots.add(intArrayOf(x0 + ROOM_W - 1, zz)); zz += 3 }
                val wallPlaced = ArrayList<Deco.Item>(); val wallPos = ArrayList<IntArray>()
                var k = 0
                while (wnext < wallItems.size && k < wallSlots.size) { wallPlaced.add(wallItems[wnext]); wallPos.add(wallSlots[k]); wnext++; k++ }
                if (placed.isEmpty() && wallPlaced.isEmpty()) { if (next < all.size) next++ else wnext++; continue }
                if (z0 + depth + 12 > sz - 2) { notPlaced += (all.size - next) + (wallItems.size - wnext); break }

                // piso, paredes e (se tiver peça de teto) teto
                for (z in z0 - 1..z0 + depth) for (x in x0 - 1..x0 + ROOM_W) put(w, x, top, z, floorB)
                if (wallB != 0) {
                    for (y in 1..WALL_H) {
                        for (x in x0 - 1..x0 + ROOM_W) {
                            put(w, x, top + y, z0 - 1, wallB)
                            val gap = x >= x0 + ROOM_W / 2 - 5 && x <= x0 + ROOM_W / 2 + 4 && y <= 4
                            if (!gap) put(w, x, top + y, z0 + depth, wallB)
                        }
                        for (z in z0 - 1..z0 + depth) { put(w, x0 - 1, top + y, z, wallB); put(w, x0 + ROOM_W, top + y, z, wallB) }
                    }
                    if (hasCeil) for (z in z0 - 1..z0 + depth) for (x in x0 - 1..x0 + ROOM_W) put(w, x, top + WALL_H + 1, z, wallB)
                    if (lamp != null) for (px in intArrayOf(x0 - 1, x0 + ROOM_W)) for (pz in intArrayOf(z0 - 1, z0 + depth)) put(w, px, top + WALL_H + 1, pz, lamp)
                } else {
                    // ambiente aberto (jardim, exterior): só uma mureta baixa de contorno
                    for (x in x0 - 1..x0 + ROOM_W) { put(w, x, top + 1, z0 - 1, floorB); put(w, x, top + 1, z0 + depth, floorB) }
                }
                // móveis do chão e do teto
                for (s in placed) {
                    val y = if (s.ceil) top + WALL_H else top + 1
                    put(w, s.x, y, s.z, s.item.blockId)
                    w.labels.add(World.Label(s.x + 0.5f, top + 4.6f, s.z + 0.5f, Gallery.wrap(s.item.name, 12), false, 0))
                }
                // peças de parede
                for (i in wallPlaced.indices) {
                    val it1 = wallPlaced[i]; val p = wallPos[i]
                    put(w, p[0], top + 2, p[1], it1.blockId)
                    w.labels.add(World.Label(p[0] + 0.5f, top + 4.6f, p[1] + 0.5f, Gallery.wrap(it1.name, 12), false, 0))
                }
                val title = if (part > 1) g + "  -  parte " + part else g
                val cnt = placed.size + wallPlaced.size
                w.labels.add(World.Label(x0 + ROOM_W / 2f, top + 9.5f, z0 + depth + 2f, title, true))
                w.labels.add(World.Label(x0 + ROOM_W / 2f, top + 7.8f, z0 + depth + 2f, cnt.toString() + " itens", false))
                colZ[c] = z0 + depth + 10
                part++
            }
        }
        if (notPlaced > 0) w.labels.add(World.Label(spawnX + 0.5f, top + 2.4f, 16f, "Faltou espaço no mapa para " + notPlaced + " itens", false))
    }
}
