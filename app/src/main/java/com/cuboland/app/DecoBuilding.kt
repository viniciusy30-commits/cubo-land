package com.cuboland.app

/**
 * Galeria "MINI PRÉDIO": prédio de 4 andares (térreo + 3) com elevador no saguão e 4 ambientes decorados por andar,
 * feito só com os móveis e decorações do addon (mais o Elevador do jogo). Medidas em "lx/lz" = coluna/fileira dentro do prédio.
 * Andar k: piso em y = top + 1 + 5k, 4 blocos de pé direito. Saguão no meio (lx 15..24) com o elevador no fundo (lx 18..21, lz 1..4).
 * Salas: fundo-esquerda, fundo-direita, frente-esquerda, frente-direita (dx/dz contados a partir do canto de cada sala).
 */
object DecoBuilding {
    @JvmField var spawnX = 40
    @JvmField var spawnZ = 80

    private var wd: World? = null
    private var top = 10
    private var bx = 24
    private var bz = 44
    private val keyMap = HashMap<String, Int>()

    private fun cn(n: String): Int { val b = B.byName[n]; return if (b == null) B.STONE else b }

    private fun kid(key: String): Int {
        val e = keyMap[key]
        if (e != null) return e
        for ((k, v) in keyMap) if (k.startsWith(key)) return v
        return 0
    }

    private fun setAbs(x: Int, y: Int, z: Int, id: Int) {
        val w = wd ?: return
        if (x < 0 || x >= World.SX || z < 0 || z >= World.SZ || y < 0 || y >= World.SY) return
        w.blocks[(y * World.SZ + z) * World.SX + x] = id
    }
    /** coluna/fileira dentro do prédio (lx, lz) */
    private fun setL(lx: Int, y: Int, lz: Int, id: Int) { setAbs(bx + lx, y, bz + lz, id) }

    /** coloca um móvel/decoração com direção da frente fixa (0 = +Z, 1 = -Z, 2 = +X, 3 = -X) */
    private fun putL(lx: Int, y: Int, lz: Int, key: String, dir: Int) {
        val id = kid(key)
        if (id == 0) return
        val w = wd ?: return
        val x = bx + lx; val z = bz + lz
        setAbs(x, y, z, id)
        if (x >= 0 && x < World.SX && z >= 0 && z < World.SZ && y >= 0 && y < World.SY) w.fState[(y * World.SZ + z) * World.SX + x] = 64 or (dir shl 4)
    }

    private class Room(val k: Int, val left: Boolean, val back: Boolean) {
        val f = 0 + k   // andar (o y real vem de floorY)
        val x0 = if (left) 1 else 26
        val z0 = if (back) 1 else 14
        val depth = if (back) 12 else 13
        val outerDir = if (left) 2 else 3
        val lobbyDir = if (left) 3 else 2
        val ox = if (left) 0 else 12     // coluna colada na parede externa
        val lo = if (left) 12 else 0     // coluna colada na parede do saguão
    }
    private fun floorY(k: Int): Int = top + 1 + 5 * k

    /** põe a peça na sala: dx/dz a partir do canto, dy = altura sobre o piso */
    private fun at(r: Room, dx: Int, dz: Int, key: String, dir: Int, dy: Int) { putL(r.x0 + dx, floorY(r.k) + dy, r.z0 + dz, key, dir) }
    private fun row(r: Room, dx0: Int, dx1: Int, dz: Int, key: String, dir: Int, dy: Int) { for (dx in dx0..dx1) at(r, dx, dz, key, dir, dy) }
    private fun col(r: Room, dx: Int, dz0: Int, dz1: Int, key: String, dir: Int, dy: Int) { for (dz in dz0..dz1) at(r, dx, dz, key, dir, dy) }

    private fun paintFloor(r: Room, name: String) {
        val b = cn(name)
        val y = if (r.k == 0) top else floorY(r.k) - 1
        for (dz in 0 until r.depth) for (dx in 0..12) setL(r.x0 + dx, y, r.z0 + dz, b)
    }

    private fun windows(r: Room, key: String) {
        val y = floorY(r.k) + 1
        val ol = if (r.left) 0 else 39
        putL(ol, y, r.z0 + 3, key, r.outerDir); putL(ol, y, r.z0 + 8, key, r.outerDir)
        if (r.back) { putL(r.x0 + 4, y, 0, key, 0); putL(r.x0 + 9, y, 0, key, 0) }
        else { putL(r.x0 + 4, y, 27, key, 1); putL(r.x0 + 9, y, 27, key, 1) }
    }

    fun build(world: World, topY: Int) {
        wd = world; top = topY; bx = 24; bz = 44
        keyMap.clear()
        for (d in Deco.items) if (d.blockId > 0 && !keyMap.containsKey(d.key)) keyMap[d.key] = d.blockId
        val white = cn("Concreto Branco"); val slab = cn("Concreto Cinza-claro")
        val elev = B.byName["Elevador"]

        // ---- casca do prédio: lajes, paredes externas, parede do saguão e divisórias ----
        for (k in 0..3) {
            val f = floorY(k)
            if (k >= 1) for (lz in 0..27) for (lx in 0..39) {
                val shaft = lx in 18..21 && lz in 1..4
                if (!shaft) setL(lx, f - 1, lz, slab)
            }
            for (y in f..f + 3) {
                for (lx in 0..39) { setL(lx, y, 0, white); setL(lx, y, 27, white) }
                for (lz in 0..27) { setL(0, y, lz, white); setL(39, y, lz, white) }
                for (lz in 1..26) {
                    val door = (lz in 5..7 || lz in 19..21) && y <= f + 2
                    if (!door) { setL(14, y, lz, white); setL(25, y, lz, white) }
                }
                for (lx in 1..13) { setL(lx, y, 13, white); setL(lx + 25, y, 13, white) }
                for (lx in 18..21) for (lz in 1..4) setL(lx, y, lz, 0)   // poço do elevador livre
            }
            if (k == 0) for (y in f..f + 2) for (lx in 17..22) setL(lx, y, 27, 0)   // entrada
        }
        for (lz in 0..27) for (lx in 0..39) setL(lx, floorY(3) + 4, lz, slab)       // laje de cobertura
        // saguão: piso claro com tapete no meio
        for (k in 0..3) {
            val y = if (k == 0) top else floorY(k) - 1
            val tile = cn("Concreto Branco")
            for (lz in 1..26) for (lx in 15..24) if (!(lx in 18..21 && lz in 1..4)) setL(lx, y, lz, tile)
        }

        // ---- elevador 4x4 em cada andar (canto de trás-esquerda em lx 18, lz 1; porta pra +Z) ----
        if (elev != null) for (k in 0..3) setL(18, floorY(k), 1, elev)

        // ---- saguões ----
        for (k in 0..3) {
            val f = floorY(k)
            for (lz in 8..24 step 2) { putL(19, f, lz, "carpet_a", 0); putL(20, f, lz, "carpet_a", 0) }
            putL(15, f, 25, "pot_plant_8", 1); putL(24, f, 25, "pot_plant_8", 1)
            putL(15, f, 10, "bench_1_wood_white", 2); putL(24, f, 10, "bench_1_wood_white", 3)
            putL(15, f, 16, "bench_1_wood_white", 2); putL(24, f, 16, "bench_1_wood_white", 3)
            putL(17, f + 3, 12, "lamp_ceiling_10", 0); putL(22, f + 3, 12, "lamp_ceiling_10", 0)
            putL(17, f + 3, 20, "lamp_ceiling_10", 0); putL(22, f + 3, 20, "lamp_ceiling_10", 0)
            putL(23, f, 5, "elevator_control_panel", 0)
            wd?.labels?.add(World.Label(bx + 20f, f + 3.2f, bz + 9f, if (k == 0) "TÉRREO" else "ANDAR " + k, true))
        }

        // ---- 16 ambientes ----
        for (k in 0..3) for (back in booleanArrayOf(true, false)) for (left in booleanArrayOf(true, false)) {
            val r = Room(k, left, back)
            val id = k * 4 + (if (back) 0 else 2) + (if (left) 0 else 1)
            val title: String
            when (id) {
                0 -> { title = "SALA DE ESTAR"; sala(r) }
                1 -> { title = "COZINHA E JANTAR"; cozinha(r) }
                2 -> { title = "LOJA"; loja(r) }
                3 -> { title = "CAFÉ"; cafe(r) }
                4 -> { title = "QUARTO DO CASAL"; quarto(r) }
                5 -> { title = "QUARTO INFANTIL"; infantil(r) }
                6 -> { title = "BANHEIRO"; banheiro(r) }
                7 -> { title = "LAVANDERIA"; lavanderia(r) }
                8 -> { title = "ESCRITÓRIO"; escritorio(r) }
                9 -> { title = "SALA DE JOGOS"; jogos(r) }
                10 -> { title = "SALA DE MÚSICA"; musica(r) }
                11 -> { title = "BOUTIQUE"; boutique(r) }
                12 -> { title = "CONSULTÓRIO"; consultorio(r) }
                13 -> { title = "ACADEMIA"; academia(r) }
                14 -> { title = "GALERIA DE ARTE"; galeria(r) }
                else -> { title = "JARDIM DE INVERNO"; jardim(r) }
            }
            windows(r, "window_1_white")
            at(r, 6, if (back) 5 else 6, "lamp_ceiling_11", 0, 3)
            wd?.labels?.add(World.Label(bx + r.x0 + 6.5f, floorY(k) + 3.3f, bz + r.z0 + 1f, title, true))
        }
        // entrada
        wd?.labels?.add(World.Label(bx + 20f, top + 7f, bz + 31f, "MINI PRÉDIO", true))
        wd?.labels?.add(World.Label(bx + 20f, top + 5.3f, bz + 31f, "4 andares com elevador (mire nos botões do elevador)", false))
        spawnX = bx + 20; spawnZ = bz + 36
    }

    // ================= andar 0 =================
    private fun sala(r: Room) {
        paintFloor(r, "Tábua")
        row(r, 3, 5, 0, "rack_1_wood_brown", 0, 0); at(r, 4, 0, "tv", 0, 1)
        at(r, 2, 0, "pot_plant_8", 0, 0); at(r, 6, 0, "lamp_ground_1", 0, 0)
        at(r, 8, 0, "canvas_art_b", 0, 2); at(r, 10, 0, "canvas_art_c", 0, 2); at(r, 7, 0, "clock_wall_black", 0, 2)
        row(r, 3, 5, 6, "sofa_blue", 1, 0); at(r, 4, 4, "coffee_table", 0, 0); row(r, 3, 5, 5, "carpet_a", 0, 0)
        at(r, 7, 4, "sofa_single_a", 3, 0)
        col(r, 0, 6, 8, "bookshelf_1", 2, 0); at(r, 0, 9, "pot_plant_12", 2, 0); at(r, 12, 9, "fireplace", 3, 0)
        at(r, 10, 6, "pillow_a", 0, 0)
    }
    private fun cozinha(r: Room) {
        paintFloor(r, "Concreto Branco")
        at(r, 1, 0, "refrigerator", 0, 0); at(r, 3, 0, "stove_2", 0, 0); at(r, 8, 0, "sink_1", 0, 0)
        for (dx in intArrayOf(2, 4, 5, 6, 7, 9, 10, 11)) at(r, dx, 0, "countertop_1_marble_white", 0, 0)
        at(r, 5, 0, "microwave_a", 0, 1); at(r, 3, 0, "cooker_hood_1", 0, 2); row(r, 4, 10, 0, "cabinet_wall", 0, 2)
        row(r, 4, 5, 6, "table_1_wood_brown", 0, 0); row(r, 4, 5, 7, "table_1_wood_brown", 0, 0)
        col(r, 3, 6, 7, "chair_1_wood_brown", 2, 0); col(r, 6, 6, 7, "chair_1_wood_brown", 3, 0)
        row(r, 4, 5, 5, "chair_1_wood_brown", 0, 0); row(r, 4, 5, 8, "chair_1_wood_brown", 1, 0)
        at(r, 4, 6, "apple_a", 0, 1); at(r, 5, 7, "bread", 0, 1); at(r, 5, 6, "cake_1", 0, 1)
        at(r, 4, 6, "hanging_light", 0, 3); at(r, 11, 9, "vending_machine_a", 3 - 0, 0); at(r, 11, 2, "pot_plant_12", 0, 0)
    }
    private fun loja(r: Room) {
        paintFloor(r, "Concreto Cinza-claro")
        col(r, r.ox, 1, 4, "rack_retail_wood_white", r.outerDir, 0); col(r, r.ox, 8, 11, "rack_retail_wood_white", r.outerDir, 0)
        for (dz in intArrayOf(1, 2, 3, 4, 8, 9, 10, 11)) at(r, r.ox, dz, "bottles_a", r.outerDir, 1)
        row(r, 3, 6, 0, "rack_retail_wood_white", 0, 0); row(r, 3, 6, 0, "foods", 0, 1)
        at(r, 6, 5, "shopping_cart_a", 0, 0); at(r, 8, 5, "shopping_cart_b", 0, 0)
        at(r, 9, 10, "cashier_table", 1, 0); at(r, 9, 10, "payment_cashier", 1, 1); at(r, r.lo, 11, "atm_1", r.lobbyDir, 0)
        row(r, 5, 7, 12, "box_1_a", 1, 0); at(r, 1, 6, "mannequin_a", 0, 0)
    }
    private fun cafe(r: Room) {
        paintFloor(r, "Tábua")
        row(r, 2, 9, 0, "countertop_wood_brown", 0, 0); at(r, 3, 0, "espresson_coffee_machine", 0, 1)
        at(r, 6, 0, "cake_1", 0, 1); at(r, 8, 0, "drink_1", 0, 1); row(r, 2, 9, 0, "cabinet_wall", 0, 2)
        for (px in intArrayOf(3, 7)) for (pz in intArrayOf(5, 9)) { at(r, px, pz, "table_1_wood_white", 0, 0); at(r, px - 1, pz, "chair_cafe", 2, 0); at(r, px + 1, pz, "chair_cafe", 3, 0) }
        at(r, r.ox, 7, "jukebox", r.outerDir, 0); at(r, r.ox, 2, "aquarium", r.outerDir, 0); at(r, 10, 11, "pot_plant_8", 1, 0)
    }
    // ================= andar 1 =================
    private fun quarto(r: Room) {
        paintFloor(r, "Tábua")
        at(r, 5, 2, "bed_1", 0, 0)
        at(r, 3, 0, "night_stand", 0, 0); at(r, 7, 0, "night_stand", 0, 0)
        at(r, 3, 0, "lamp_ground_1", 0, 1); at(r, 7, 0, "lamp_ground_1", 0, 1); at(r, 5, 0, "canvas_art_d", 0, 3)
        at(r, 10, 0, "wardrobe", 0, 0); at(r, 9, 0, "drawer_a", 0, 0)
        at(r, r.ox, 6, "mirror", r.outerDir, 2); at(r, 10, 6, "sofa_single_a", 1, 0); at(r, 4, 6, "pillow_c", 0, 0)
        row(r, 3, 6, 6, "carpet_b", 0, 0); at(r, r.ox, 10, "pot_plant_8", r.outerDir, 0)
    }
    private fun infantil(r: Room) {
        paintFloor(r, "Concreto Rosa")
        at(r, 2, 2, "cribs", 0, 0); at(r, 9, 2, "bunk_bed", 0, 0)
        at(r, 6, 0, "toy_box", 0, 0); at(r, 6, 0, "plushi_a", 0, 1); at(r, 7, 0, "plushi_b", 0, 1)
        row(r, 4, 8, 8, "carpet_c", 0, 0); at(r, 5, 8, "toys_1", 0, 0); at(r, 7, 8, "doll_a", 0, 0)
        at(r, 6, 5, "table_school", 0, 0); at(r, 5, 5, "chair_school", 2, 0); at(r, 7, 5, "chair_school", 3, 0)
        at(r, 10, 9, "ballon_a", 0, 0); at(r, 2, 0, "banner_a", 0, 2); at(r, r.ox, 6, "plushi_c", r.outerDir, 0)
    }
    private fun banheiro(r: Room) {
        paintFloor(r, "Concreto Azul-claro")
        at(r, r.ox, 1, "toilet", r.outerDir, 0); at(r, r.ox, 4, "sink_1", r.outerDir, 0); at(r, r.ox, 4, "mirror", r.outerDir, 2)
        at(r, r.ox, 11, "shower", r.outerDir, 0); at(r, 6, 12, "bathtub", 1, 0)
        at(r, 3, 12, "trash_can_a", 1, 0); at(r, 9, 12, "pot_plant_8", 1, 0); at(r, r.lo, 10, "toilet_paper", r.lobbyDir, 1)
        at(r, 4, 6, "carpet_d", 0, 0)
    }
    private fun lavanderia(r: Room) {
        paintFloor(r, "Concreto Cinza-claro")
        at(r, 2, 0, "washing_machine_a", 0, 0); at(r, 4, 0, "washing_machine_a", 0, 0); at(r, 6, 0, "basket_laundry", 0, 0)
        at(r, 8, 0, "cabinet_ground", 0, 0); row(r, 2, 4, 0, "cabinet_wall", 0, 2); at(r, 8, 0, "cabinet_wall", 0, 2)
        at(r, 8, 5, "ironing_board", 0, 0); at(r, 10, 8, "clothes_stand_1", 0, 0); at(r, r.ox, 3, "water_heater", r.outerDir, 0)
        at(r, 3, 6, "bucket", 0, 0); at(r, 11, 10, "trash_can_a", 1, 0)
    }
    // ================= andar 2 =================
    private fun escritorio(r: Room) {
        paintFloor(r, "Concreto Cinza")
        row(r, 3, 5, 0, "desk_table_a", 0, 0); at(r, 4, 0, "pc_setup_blue", 0, 1); at(r, 4, 1, "chair_gaming_a", 1, 0)
        row(r, 8, 9, 0, "desk_table_b", 0, 0); at(r, 8, 0, "laptop_a", 0, 1); at(r, 8, 1, "chair_1_wood_black", 1, 0)
        col(r, r.ox, 3, 5, "bookshelf_1", r.outerDir, 0); at(r, 11, 0, "filing_cabinet", 0, 0)
        at(r, 11, 0, "printer", 0, 1); at(r, 12, 2, "pot_plant_12", 0, 0); at(r, 6, 0, "lamp_ground_1", 0, 0)
        at(r, 7, 10, "globe", 0, 0); at(r, r.lo, 10, "whiteboard", r.lobbyDir, 1)
    }
    private fun jogos(r: Room) {
        paintFloor(r, "Concreto Preto")
        at(r, 1, 0, "arcade_machine_black", 0, 0); at(r, 3, 0, "arcade_machine_blue", 0, 0); at(r, 5, 0, "arcade_machine_green", 0, 0)
        at(r, 7, 0, "arcade_machine_red", 0, 0); at(r, 9, 0, "arcade_machine_white", 0, 0); at(r, 11, 0, "arcade_machine_yellow", 0, 0)
        at(r, r.ox, 6, "tv_table", r.outerDir, 0); at(r, r.ox, 6, "tv", r.outerDir, 1); at(r, r.ox, 7, "gaming_console_1", r.outerDir, 0)
        row(r, 4, 6, 6, "sofa_red", if (r.left) 3 else 2, 0)
        at(r, 6, 8, "dart_board", 0, 2); at(r, 9, 9, "jukebox", 1, 0); at(r, 4, 9, "ottoman_sofa", 0, 0)
    }
    private fun musica(r: Room) {
        paintFloor(r, "Concreto Marrom")
        at(r, 3, 0, "piano_upright_black", 0, 0); at(r, 3, 2, "piano_chair", 1, 0)
        at(r, 7, 0, "guitar_electric_ground_a", 0, 0); at(r, 8, 0, "guitar_electric_ground_b", 0, 0)
        at(r, 6, 6, "drum_electric", 0, 0); at(r, 10, 0, "speaker_stage", 0, 0); at(r, 6, 9, "mic", 1, 0)
        for (dx in intArrayOf(2, 5, 8, 11)) at(r, dx, 0, "acoustic_panel_black", 0, 2)
        at(r, r.ox, 4, "vinyl_player_1", r.outerDir, 0); at(r, r.lo, 11, "dj_controller", r.lobbyDir, 0); at(r, 11, 10, "radio", 1, 0)
    }
    private fun boutique(r: Room) {
        paintFloor(r, "Concreto Branco")
        at(r, 2, 2, "mannequin_a", 0, 0); at(r, 4, 2, "mannequin_b", 0, 0); at(r, 6, 2, "mannequin_c", 0, 0); at(r, 8, 2, "mannequin_d", 0, 0)
        col(r, r.ox, 1, 4, "clothes_stand_1", r.outerDir, 0); col(r, r.ox, 8, 11, "clothes_stand_1", r.outerDir, 0)
        row(r, 3, 8, 12, "rack_retail_wood_white", 1, 0); at(r, 4, 12, "shoes_a", 1, 1); at(r, 6, 12, "shoes_b", 1, 1); at(r, 8, 12, "shirt_a", 1, 1)
        at(r, 6, 6, "hat_stand_a", 0, 0); at(r, 9, 7, "mirror", 0, 0); at(r, 10, 10, "bag_a", 0, 0)
    }
    // ================= andar 3 =================
    private fun consultorio(r: Room) {
        paintFloor(r, "Concreto Branco")
        at(r, 5, 2, "patient_bed", 0, 0); at(r, 3, 0, "hospital_stuff_1", 0, 0); at(r, 8, 0, "hospital_stuff_2", 0, 0)
        at(r, 10, 0, "hospital_stuff_3", 0, 0); at(r, 11, 4, "ct_scan_hospital", 0, 0)
        at(r, r.ox, 7, "oxygen_cylinder", r.outerDir, 0); at(r, r.ox, 9, "syringes", r.outerDir, 1); at(r, r.ox, 9, "cabinet_ground", r.outerDir, 0)
        col(r, r.lo, 9, 11, "chair_wood_white", r.lobbyDir, 0); at(r, 6, 11, "fire_extinguisher", 1, 0); at(r, 9, 11, "pot_plant_8", 1, 0)
    }
    private fun academia(r: Room) {
        paintFloor(r, "Concreto Verde")
        at(r, 2, 0, "treadmill", 0, 0); at(r, 4, 0, "treadmill", 0, 0); at(r, 6, 0, "treadmill", 0, 0); at(r, 9, 0, "spin_bike", 0, 0)
        at(r, 11, 0, "spin_bike", 0, 0); at(r, 10, 6, "punching_bag", 0, 0); at(r, 4, 6, "bench_press_1", 0, 0)
        row(r, 3, 6, 9, "dumbbell", 1, 0); col(r, r.ox, 1, 4, "locker_a", r.outerDir, 0); at(r, r.ox, 8, "dispenser_3", r.outerDir, 0)
        at(r, 6, 4, "fan_ceiling", 0, 3); at(r, 8, 11, "ball_a", 0, 0)
    }
    private fun galeria(r: Room) {
        paintFloor(r, "Concreto Cinza-claro")
        val names = arrayOf("canvas_art_a", "canvas_art_b", "canvas_art_c", "canvas_art_d", "canvas_art_e", "canvas_art_f")
        for (i in names.indices) at(r, 1 + i * 2, 0, names[i], 0, 2)
        col(r, r.ox, 1, 5, "canvas_art_g", r.outerDir, 2); col(r, r.ox, 8, 11, "canvas_art_h", r.outerDir, 2)
        at(r, 5, 5, "bench_1_wood_white", 0, 0); at(r, 7, 5, "bench_1_wood_white", 0, 0); at(r, 5, 9, "bench_1_wood_white", 0, 0)
        at(r, 10, 4, "podium", 0, 0); at(r, 10, 4, "figure_1_a", 0, 1); at(r, 2, 9, "pillar", 0, 0)
        for (dz in 2..10 step 2) { at(r, 6, dz, "carpet_a", 0, 0) }
    }
    private fun jardim(r: Room) {
        paintFloor(r, "Grama")
        at(r, 1, 0, "pot_plant_8", 0, 0); at(r, 4, 0, "pot_plant_12", 0, 0); at(r, 7, 0, "pot_plant_4", 0, 0); at(r, 10, 0, "pot_plant_6", 0, 0)
        col(r, r.ox, 2, 4, "pot_plant_7", r.outerDir, 0); col(r, r.ox, 9, 11, "pot_plant_8", r.outerDir, 0)
        at(r, 6, 5, "bench_1_wood_brown", 0, 0); at(r, 6, 8, "bench_1_wood_brown", 0, 0)
        at(r, 4, 9, "watering_can_a", 1, 0); at(r, 9, 9, "garden_hose", 1, 0); at(r, 3, 4, "pot_plant_ceiling", 0, 3); at(r, 9, 4, "pot_plant_ceiling", 0, 3)
        at(r, r.lo, 11, "aquarium", r.lobbyDir, 0)
    }
}
