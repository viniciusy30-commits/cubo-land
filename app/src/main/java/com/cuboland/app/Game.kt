package com.cuboland.app

import java.util.Random
import kotlin.math.*

open class Ent(var x: Float, var y: Float, var z: Float, val hw: Float, var h: Float) {
    var vx = 0f; var vy = 0f; var vz = 0f
    var onGround = false; var inWater = false
}

class Slime(x: Float, y: Float, z: Float) : Ent(x, y, z, 0.42f, 0.85f) {
    var hp = 6; var timer = 1f; var flash = 0f; var squash = 0f; var t = 0f; var dead = false
}

const val JUMP_TAP_T = 0.14f    // até aqui soltar = pulo normal
const val JUMP_FULL_T = 0.75f   // depois disso, tempo até a carga encher

class Particle(var x: Float, var y: Float, var z: Float, var vx: Float, var vy: Float, var vz: Float,
               val color: Int, val size: Float, var life: Float) { var grav = 18f; var dust = false; var age = 0f }

/** rastro de vento do voo rápido (parado no mundo, só envelhece). kind: 0 risco fino ao longo do voo, 1 ponto da hélice (gira em volta do eixo), 2 anel que se abre.
 *  (dx,dy,dz) = direção do voo (unitária); len = comprimento do risco; rad = raio final da hélice/anel; ph = fase da hélice */
class WindP(val kind: Int, var x: Float, var y: Float, var z: Float, val dx: Float, val dy: Float, val dz: Float,
            val color: Int, val life: Float, val len: Float, val rad: Float, val ph: Float) { var age = 0f; var amp = 1f }   // amp: intensidade (0..1) na hora que nasceu; o vento entra aos poucos

class LeafP(var x: Float, var y: Float, var z: Float, var vx: Float, var vz: Float, val ph: Float, val color: Int, val size: Float, var life: Float, var landed: Boolean = false, var age: Float = 0f) {
    var shape = 0   // espécie (Leaf3D.OAK..MUSGO): define o formato da folhinha
    var vy = 0f; var burst = 0f; var spin = 0f   // burst > 0: folhinha lançada por um corte (voa pra fora e depois flutua)
}

/** trecho de rachadura em coordenadas da face (0..1); aparece quando o crescimento g passa de t0 e termina em t1 */
class Seg(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val w: Float, val t0: Float, val t1: Float)

/** marca de impacto numa face do bloco. kind: 0 corte de machado, 1 furo de picareta, 2 talho de espada, 3 estocada, 4 amassado */
class Mark(val a: Int, val s: Int, val u: Float, val v: Float, val kind: Int, val ang: Float, val len: Float, val born: Float) {
    val segs = ArrayList<Seg>(); var g = 0f; var chips = FloatArray(0); var blobs = FloatArray(0); var gr = 0f
}

/** dano acumulado de um bloco: todas as marcas + progresso de quebra */
const val VN = 16   // resolução do bloco esculpido (VN x VN x VN mini-voxels)

class Dmg(val x: Int, val y: Int, val z: Int, val id: Int) {
    var vox: BooleanArray? = null; var carved = false; var vdirty = false; var vb = 0; var ib = 0; var icnt = 0; var dead = false
    var felled = false; var cutY = Float.NaN; var prog = 0f; var idle = 0f; val marks = ArrayList<Mark>() }

/** mini-bloco que voa e quica quando algo quebra */
class Debris(var x: Float, var y: Float, var z: Float, var vx: Float, var vy: Float, var vz: Float, val id: Int, val size: Float, var life: Float) {
    var rx = 0f; var ry = 0f; var rz = 0f; var vrx = 0f; var vry = 0f; var vrz = 0f; var ground = false
}

/** parte de cima de uma árvore caindo inteira. bl = (dx,dy,dz,id) por bloco, relativo ao bloco cortado */
class FallTree(val px: Float, val py: Float, val pz: Float, val dx: Float, val dz: Float, val bl: IntArray, val yo: Float = 0f) {
    var ang = 0.03f; var vel = 0.25f; var t = 0f; val n = bl.size / 4
    var dm: MeshBuf? = null; val rs = IntArray(bl.size / 4 + 1)   // galhos e folhinhas finas (malha própria), rs = faixa de índices de cada bloco
    val gone = BooleanArray(bl.size / 4)   // folhas e galhos que já bateram no chão e se quebraram
    /** só o tronco (perto do eixo do corte) segura a árvore; folhas e galhos finos se quebram ao tocar o chão */
    fun holds(i: Int) = B.log(bl[i * 4 + 3]) && abs(bl[i * 4]) <= 2 && abs(bl[i * 4 + 2]) <= 2
    var drop = 0f; var dvy = 0f; var dropping = false   // depois de deitar sem tocar chão, ela despenca até encostar
    var sx = 0; var sy = -99; var sz = 0; var landed = false; var lt = 0f   // sx..sz = bloco do toco (ignorado na colisão)
    val kx get() = dz; val kz get() = -dx     // eixo de rotação (horizontal, perpendicular à queda)
}

/** talho de espada: só visual, some sozinho depois de alguns minutos */
class LeafBreak(val x: Int, val y: Int, val z: Int, var t: Float) { var fx = false }

class Slash(val d: Dmg, val m: Mark, val born: Float)

/** item que cai no chão quando um bloco quebra: 1 só, flutua girando e vai pro jogador quando perto */
class Drop(var x: Float, var y: Float, var z: Float, var vx: Float, var vy: Float, var vz: Float, val id: Int) { var age = 0f }

/** onda na água: age cresce com o tempo; amp = força (queda de altura maior = onda maior) */
class Ripple(val x: Float, val z: Float, val amp: Float, var age: Float)

/** pegada no chão (areia afunda mais, terra marca, grama só amassa) */
class Foot(val x: Float, val y: Float, val z: Float, val ang: Float, val id: Int, val born: Float, val side: Float = 1f) {
    fun life() = when (id) { B.SAND -> 70f; B.DIRT -> 45f; else -> 16f }
}

class Bolt(var x: Float, var y: Float, var z: Float, val vx: Float, val vy: Float, val vz: Float, var life: Float)

class ElevShaft(val ax: Int, val az: Int) {
    @Volatile var ys = IntArray(0)
    @Volatile var boxes: Array<FloatArray> = emptyArray()
    @Volatile var carY = 0f; var fromY = 0f; var toY = 0f; var t = 0f; var dur = 1f
    @Volatile var moving = false; @Volatile var dir = 0; @Volatile var door = 1f; @Volatile var target = -1
    fun idxNear(): Int { var b = 0; for (i in ys.indices) if (abs(ys[i] - carY) < abs(ys[b] - carY)) b = i; return b }
}

class Game(val world: World) {
    val slashes = java.util.concurrent.CopyOnWriteArrayList<Slash>()
    companion object { const val SLASH_LIFE = 180f; const val LB_SHAKE = 0.12f; const val LB_FLY = 0.30f }
    val leafBreaks = ArrayList<LeafBreak>()
    val hotbar = intArrayOf(Items.SWORD, Items.STAFF, Items.PICK, B.GRASS, B.PLANK, B.BRICK, B.PINK, B.LANTERN)
    val bolts = ArrayList<Bolt>()
    @Volatile var shake = 0f
    fun cur() = hotbar[sel.coerceIn(0, 7)]

    val rnd = Random()
    val player = Ent(World.SX / 2f + 0.5f, 20f, World.SZ / 2f + 0.5f, 0.3f, 1.8f)
    // ----- veículos: carro esportivo (kind 0) e moto (kind 1); "car" é o que está sendo dirigido (ou o último) -----
    val carV = CarState()
    val bikeV = CarState().also { it.kind = 1 }
    @Volatile var curKind = 0                       // 0 carro, 1 moto, 2 skate
    // ----- skate (curKind == 2): entradas escritas pela UI -----
    val skateV = SkateState()
    val skating: Boolean get() = driving && curKind == 2
    @Volatile var skGas = 0f; @Volatile var skBrake = 0f; @Volatile var skSteer = 0f; @Volatile var skSpin = 0f
    @Volatile var skOllie = false; @Volatile var skGrab = false; @Volatile var skGrind = false
    @Volatile var skOllieLatch = false; @Volatile var skFlipLatch = false; @Volatile var skGrindLatch = false
    @Volatile var skateCrouch = 0f
    private var skSparkT = 0f
    private var skDustT = 0f
    private val collSk: (Float, Float, Float) -> Boolean = { x, y, z -> collides(x, y, z, 0.28f, 1.7f) }
    private val solidSk: (Int, Int, Int) -> Boolean = { a, b, c -> world.solid(a, b, c) }
    val car: CarState get() = if (curKind == 1) bikeV else carV
    @Volatile var driving = false
    @Volatile var wantVeh = 0                       // 1 = botão CARRO, 2 = botão MOTO
    @Volatile var carGas = 0f; @Volatile var carBrake = 0f; @Volatile var carSteer = 0f; @Volatile var carHand = false; @Volatile var carWheelie = false
    private var preDriveTP = true
    private var carDustT = 0f
    private val seatTmp = FloatArray(3)
    private val collCar: (Float, Float, Float) -> Boolean = { x, y, z -> collides(x, y, z, 0.34f, 0.95f) }
    private val collBike: (Float, Float, Float) -> Boolean = { x, y, z -> collides(x, y, z, 0.2f, 1.0f) }
    private val carWater: (Float, Float, Float) -> Boolean = { x, y, z -> waterAtPt(x, y, z) }
    private fun collOf(v: CarState) = if (v.kind == 1) collBike else collCar

    private fun carToggleNow(kind: Int) {
        val p = player
        if (driving) {
            val wasSk = skating
            driving = false; carGas = 0f; carBrake = 0f; carSteer = 0f; carHand = false; carWheelie = false
            skGas = 0f; skBrake = 0f; skSteer = 0f; skSpin = 0f; skOllie = false; skGrab = false; skGrind = false; skateCrouch = 0f
            thirdPerson = preDriveTP
            posture = 0; p.h = 1.8f
            if (wasSk) skateV.dismount()
            val ox = if (wasSk) skateV.x else car.x; val oy = if (wasSk) skateV.y else car.y
            val oz = if (wasSk) skateV.z else car.z; val oh = if (wasSk) skateV.h else car.h
            val lx = cos(oh); val lz = -sin(oh)       // esquerda do veículo
            var done = false
            if (wasSk && !collides(ox, oy, oz, p.hw, p.h)) { p.x = ox; p.z = oz; p.y = oy + 0.05f; done = true }
            if (!done) for (side in floatArrayOf(2.0f, -2.0f)) {
                val tx = ox + lx * side; val tz = oz + lz * side
                if (!collides(tx, oy, tz, p.hw, p.h)) { p.x = tx; p.z = tz; p.y = oy + 0.05f; done = true; break }
            }
            if (!done) { p.x = ox; p.z = oz; p.y = oy + 1.4f }
            p.vx = 0f; p.vy = 0f; p.vz = 0f
        } else {
            if (seated || elevRide != null || p.inWater) return
            if (kind == 2) {
                val s = skateV
                val nearS = s.placed && hypot(s.x - p.x, s.z - p.z) < 3f && abs(s.y - p.y) < 2.5f
                if (!nearS) {
                    if (!s.spawn(p.x, p.y, p.z, yaw, solidSk, collSk)) return
                    burst(s.x, s.y + 0.3f, s.z, 0xFFFFFF, 10, 2.5f)
                } else { s.vx = 0f; s.vy = 0f; s.vz = 0f; s.h = yaw; s.dismount() }
                curKind = 2
                flying = false; sprint = false; jumpHeld = false
                posture = 0; p.h = 1.8f
                preDriveTP = thirdPerson; thirdPerson = true; driving = true
                skGas = 0f; skBrake = 0f; skSteer = 0f; skSpin = 0f; skOllie = false; skGrab = false; skGrind = false
                return
            }
            val v = if (kind == 1) bikeV else carV
            val near = v.placed && hypot(v.x - p.x, v.z - p.z) < 6f && abs(v.y - p.y) < 3f
            if (!near) {
                val ok = v.spawn(p.x, p.y, p.z, yaw, { a, b, c -> world.solid(a, b, c) }, collOf(v))
                if (!ok) return
                burst(v.x, v.y + 0.6f, v.z, 0xFFFFFF, 14, 3f)
            }
            curKind = kind
            flying = false; sprint = false; jumpHeld = false
            posture = 2; p.h = postH[2]
            preDriveTP = thirdPerson; thirdPerson = true; driving = true
            carGas = 0f; carBrake = 0f; carSteer = 0f; carHand = false; carWheelie = false
        }
    }

    private fun updateCar(dt: Float) {
        if (wantVeh != 0) { val k = wantVeh; wantVeh = 0; carToggleNow(if (k == 3) 2 else if (k == 2) 1 else 0) }
        updateVeh(carV, dt); updateVeh(bikeV, dt)
        updateSkate(dt)
    }

    private fun updateSkate(dt: Float) {
        val s = skateV
        if (!s.placed) return
        val drv = skating
        if (drv) {
            s.update(dt, skGas, skBrake, skSteer, skSpin, skOllie || skOllieLatch, skFlipLatch, skGrab, skGrind || skGrindLatch, collSk, solidSk, carWater)
            skOllieLatch = false; skFlipLatch = false; skGrindLatch = false
        } else s.update(dt, 0f, 0f, 0f, 0f, false, false, false, false, collSk, solidSk, carWater)
        val maxX = World.SX - 3f; val maxZ = World.SZ - 3f
        if (s.x < 3f) { s.x = 3f; s.vx = 0f } else if (s.x > maxX) { s.x = maxX; s.vx = 0f }
        if (s.z < 3f) { s.z = 3f; s.vz = 0f } else if (s.z > maxZ) { s.z = maxZ; s.vz = 0f }
        if (s.y < -5f) {
            s.placed = false
            if (drv) { driving = false; thirdPerson = preDriveTP; skateCrouch = 0f; respawn() }
            return
        }
        if (drv && s.inWater) { wantVeh = 1; return }   // caiu na água: desce da prancha
        if (s.impact > 0f) { if (drv) shake = max(shake, min(0.6f, s.impact * 0.035f)); s.impact = 0f }
        if (s.evPop) { s.evPop = false; if (parts.size < 400) burst(s.x, s.y + 0.1f, s.z, 0xDDDDDD, 4, 1.5f) }
        if (s.evLand) { s.evLand = false; if (parts.size < 380) burst(s.x, s.y + 0.1f, s.z, 0xD9D4CC, 5, 2f) }
        if (s.evBail) { s.evBail = false; shake = max(shake, 0.55f); burst(s.x, s.y + 0.6f, s.z, 0xFFFFFF, 12, 3.5f) }
        skDustT -= dt
        if (s.evDrift) {   // derrapando: fumaça saindo das rodas
            s.evDrift = false
            if (parts.size < 380) {
                val sh = sin(s.h); val ch = cos(s.h)
                for (i in 0 until 2) {
                    val q = Particle(s.x - sh * 0.3f + (rnd.nextFloat() - 0.5f) * 0.5f, s.y + 0.08f, s.z - ch * 0.3f + (rnd.nextFloat() - 0.5f) * 0.5f,
                        (rnd.nextFloat() - 0.5f) * 1.4f, 0.6f + rnd.nextFloat() * 0.9f, (rnd.nextFloat() - 0.5f) * 1.4f, 0xE6E2DA, 0.3f, 0.6f)
                    q.grav = 0.5f; q.dust = true; parts.add(q)
                }
                shake = max(shake, 0.05f)
            }
        }
        if (drv && s.onGround && !s.grinding && s.speedK > 0.35f && skDustT <= 0f && parts.size < 360) {   // poeirinha atrás das rodas em velocidade
            skDustT = 0.07f
            val sh = sin(s.h); val ch = cos(s.h)
            val q = Particle(s.x - sh * 0.35f, s.y + 0.05f, s.z - ch * 0.35f, (rnd.nextFloat() - 0.5f) * 0.8f, 0.5f + rnd.nextFloat() * 0.6f, (rnd.nextFloat() - 0.5f) * 0.8f, 0xD9D4CC, 0.16f, 0.4f)
            q.grav = 0.3f; q.dust = true; parts.add(q)
        }
        if (s.evSlam) { s.evSlam = false; shake = max(shake, 0.3f); if (parts.size < 360) burst(s.x, s.y + 0.1f, s.z, 0xD9D4CC, 14, 4.2f) }
        if (s.evPerfect) { s.evPerfect = false; if (parts.size < 380) burst(s.x, s.y + 0.5f, s.z, 0xFFD54A, 12, 3.2f) }
        skSparkT -= dt
        if (s.evGrindSpark) {
            s.evGrindSpark = false
            if (skSparkT <= 0f && parts.size < 400) {
                skSparkT = 0.03f
                for (i in 0 until 2) {
                    val q = Particle(s.x, s.y + 0.05f, s.z, (rnd.nextFloat() - 0.5f) * 2.6f, 1.2f + rnd.nextFloat() * 1.8f, (rnd.nextFloat() - 0.5f) * 2.6f, 0xFFC850, 0.12f, 0.45f)
                    q.grav = 14f
                    parts.add(q)
                }
            }
        }
        if (drv) {
            val p = player
            p.x = s.x; p.y = s.y + s.yOff + 0.21f; p.z = s.z
            p.vx = 0f; p.vy = 0f; p.vz = 0f; p.onGround = true
            val spinRad = Math.toRadians(s.spin.toDouble()).toFloat()
            if (s.bailT > 0f) { bodyYaw = s.h; if (posture != 3) { posture = 3; p.h = postH[3] } }
            else {
                if (posture != 0) { posture = 0; p.h = 1.8f }
                // Na primeira impulsão, o tronco aponta para a frente; ao ganhar velocidade, assume a postura lateral.
                val side = 1f - s.faceForward.coerceIn(0f, 1f)
                bodyYaw = s.h - s.stance * (PI / 2.0).toFloat() * side + spinRad
            }
            skateCrouch = s.crouch
        }
    }

    private fun updateVeh(c: CarState, dt: Float) {
        if (!c.placed) return
        val drv = driving && c === car
        if (drv) c.update(dt, carGas, carBrake, carSteer, carHand, carWheelie, collOf(c), carWater)
        else c.update(dt, 0f, 0f, 0f, true, false, collOf(c), carWater)
        val maxX = World.SX - 3f; val maxZ = World.SZ - 3f
        if (c.x < 3f) { c.x = 3f; c.vx = 0f } else if (c.x > maxX) { c.x = maxX; c.vx = 0f }
        if (c.z < 3f) { c.z = 3f; c.vz = 0f } else if (c.z > maxZ) { c.z = maxZ; c.vz = 0f }
        if (c.y < -5f) {
            c.placed = false
            if (drv) { driving = false; thirdPerson = preDriveTP; respawn() }
            return
        }
        if (c.impact > 0f) { if (drv) shake = max(shake, min(0.7f, c.impact * 0.04f)); c.impact = 0f }
        // poeira: derrapando, arrancando com freio de mão ou empinando
        carDustT -= dt
        val sp = abs(c.speed)
        if (drv && carDustT <= 0f && c.onGround && ((c.slip > 2.5f && sp > 3f) || (carGas > 0f && sp < 5f && carHand))) {
            carDustT = 0.045f
            val back = if (c.kind == 1) 0.9f else 1.7f
            val bx = c.x - sin(c.h) * back; val bz = c.z - cos(c.h) * back
            for (side in (if (c.kind == 1) floatArrayOf(0f) else floatArrayOf(-0.7f, 0.7f))) {
                val ppx = bx + (-cos(c.h)) * side; val ppz = bz + sin(c.h) * side
                val q = Particle(ppx, c.y + 0.12f, ppz, (rnd.nextFloat() - 0.5f) * 0.8f, 0.5f + rnd.nextFloat() * 0.5f, (rnd.nextFloat() - 0.5f) * 0.8f, 0xD9D4CC, 0.28f, 0.9f)
                q.dust = true; q.grav = 0f
                if (parts.size < 400) parts.add(q)
            }
        }
        if (drv && sp > 5f) for (s in slimes) if (!s.dead && hypot(s.x - c.x, s.z - c.z) < 1.7f && abs(s.y - c.y) < 1.8f) hitSlime(s, 20, c.vx, c.vz, 9f)
        if (drv) {   // o boneco senta no assento e vai junto (inclinação e empinada incluídas)
            val p = player
            c.seat(seatTmp)
            p.x = seatTmp[0]; p.y = seatTmp[1]; p.z = seatTmp[2]
            p.vx = 0f; p.vy = 0f; p.vz = 0f; p.onGround = true
            bodyYaw = c.h
        }
    }

    val slimes = ArrayList<Slime>()
    /** galeria de blocos: sem monstros */
    @Volatile var noMobs = false
    /** matriz de projeção da última imagem (o HUD usa pra pôr as plaquinhas no lugar certo) */
    @Volatile var vpm: FloatArray? = null
    val parts = ArrayList<Particle>()
    val wind = ArrayList<WindP>()   // rastro de vento do voo rápido (até WIND_MAX elementos)
    private var windT = 0f; private var helixT = 0f; private var ringT = 0f
    @Volatile var attackHeld = false; @Volatile var attackPress = false; @Volatile var attackRelease = false
    @Volatile var charging = false; @Volatile var charge = 0f
    private var pressT = 0f; private var pressing = false; var swingDur = 0.46f; @Volatile var powerSwing = false
    @Volatile var creative = false; @Volatile var flying = false; @Volatile var freeFly = false; private var flyVy = 0f; @Volatile var downHeld = false; private var lastJumpT = -9f; private var lastDownT = -9f
    var brX = 0; var brY = -1; var brZ = 0; @Volatile var hitPulse = 0f; var rT = 0f
    val dmg = HashMap<Long, Dmg>(); val debris = ArrayList<Debris>(); val trees = ArrayList<FallTree>()
    private var hitT = -1f; private var hitItem = 0; private var hitStab = false
    private var pendingT = -1f; private var pendingPower = 0f
    val leafFall = ArrayList<LeafP>(); private var leafT = 0f; var comboT = 0f
    val drops = ArrayList<Drop>(); val ripples = ArrayList<Ripple>(); val prints = ArrayList<Foot>(); val picked = IntArray(16384)   // contagem por id de bloco (ids passam de 255: com 256 o jogo caía ao pegar o item de cama/verruga)
    @Volatile var pickMsg = ""; @Volatile var pickT = 0f; @Volatile var swimAmt = 0f; @Volatile var wetT = 0f; private var inWaterT = 0f   // wetT: efeito de "molhado" na tela ao sair da água (3s)
    // água: direção desejada (pro degrau), escorrer ao quebrar bloco, controle do efeito de tela molhada
    private var wishX = 0f; private var wishZ = 0f
    private val waterQ = LinkedHashSet<Int>(); private var waterT = 0f
    private var headWas = false; private var headWetT = 0f; private var climbT = 0f; private var waterInit = false
    private var stepDist = 0f; private var stepLeft = false; private var rippleT = 0f; private var bubbleT = 0f; private var regrowT = 0f
    // entrada (escrita pela UI)
    @Volatile var stickX = 0f; @Volatile var stickY = 0f
    @Volatile var jumpHeld = false; @Volatile var wantAttack = false; @Volatile var wantPlace = false
    // pulo: toque = pulo normal (sai ao soltar); segurar = agacha carregando e solta um SUPER PULO
    @Volatile var jumpLatch = false          // a UI marca no toque (pega até toque mais curto que um frame)
    @Volatile var jumpCharge = 0f            // 0..1 pra animação: batidinha do toque (~0,35) + carga
    @Volatile var superCharge = 0f           // 0..1 só da carga do super pulo (barrinha no botão)
    @Volatile var superAir = false; @Volatile var superVy0 = 0f   // no ar de um super pulo / velocidade de saída
    private var jumpArmed = false; private var jumpHoldT = 0f; private var jumpDustT = 0f; private var superTrailT = 0f; private var kickT = 0f; private var kickSide = false
    @Volatile var wantCam = false; @Volatile var sel = 0; @Volatile var sens = 1f
    private var lookDx = 0f; private var lookDy = 0f
    // estado
    @Volatile var hp = 10; @Volatile var kills = 0; @Volatile var hurtFlash = 0f
    @Volatile var deadTimer = 0f; @Volatile var thirdPerson = true
    var yaw = 0f; var pitch = -0.25f; var bodyYaw = 0f
    var time = 0f; var walkPhase = 0f; var walkAmt = 0f; var swing = 1f; var combo = 0
    var atkCd = 0f; var hurtCd = 0f; var regen = 0f; var spawnT = 2f
    var camX = 0f; var camY = 0f; var camZ = 0f; var camDist = 0f
    var hasHit = false; var hx = 0; var hy = 0; var hz = 0; var px = 0; var py = 0; var pz = 0

    init { world.scanElevAnchors(); respawn() }

    fun respawn() {
        world.scanElevAnchors()
        val x = if (world.spawnX >= 0) world.spawnX else World.SX / 2; val z = if (world.spawnZ >= 0) world.spawnZ else World.SZ / 2
        player.x = x + 0.5f; player.z = z + 0.5f; player.y = world.surfaceY(x, z) + 0.05f
        player.vx = 0f; player.vy = 0f; player.vz = 0f
        hp = 10; slimes.clear(); posture = 0; player.h = 1.8f
        if (driving) { driving = false; thirdPerson = preDriveTP; carGas = 0f; carBrake = 0f; carSteer = 0f; carHand = false; skGas = 0f; skBrake = 0f; skSteer = 0f; skSpin = 0f; skOllie = false; skGrab = false; skGrind = false; skateCrouch = 0f; skateV.dismount() }
        if (world.carSpawnX >= 0) {   // pista de veículos da galeria: o carro e a moto já esperam lá
            for ((v, dx) in arrayOf(Pair(carV, 0), Pair(bikeV, 7))) {
                val sx = world.carSpawnX + dx; val sz = world.carSpawnZ
                v.x = sx + 0.5f; v.z = sz + 0.5f; v.y = world.surfaceY(sx, sz) + 0.02f
                v.h = world.carSpawnYaw; v.vx = 0f; v.vy = 0f; v.vz = 0f; v.steer = 0f; v.yOff = 0f; v.wheelie = 0f
                v.placed = true
            }
        } else if (world.carSpawnX == -2) { carV.placed = false; bikeV.placed = false }
    }

    @Synchronized fun addLook(dx: Float, dy: Float) { lookDx += dx; lookDy += dy }

    private fun collides(x: Float, y: Float, z: Float, hw: Float, h: Float): Boolean {
        if (elevShafts.isNotEmpty() && elevHit(x - hw, y, z - hw, x + hw, y + h, z + hw)) return true
        for (bx in floor(x - hw).toInt()..floor(x + hw).toInt())
            for (bz in floor(z - hw).toInt()..floor(z + hw).toInt())
                for (by in floor(y).toInt()..floor(y + h).toInt())
                    if (world.solidBox(bx, by, bz, x - hw, y, z - hw, x + hw, y + h, z + hw)) return true
        return false
    }

    /** borda mais próxima (no eixo 0=x,1=y,2=z) dos blocos reais que a caixa do corpo atravessa; dir>0: face de baixo/trás, dir<0: face de cima/frente */
    private fun edgeAt(e: Ent, axis: Int, dir: Float): Float {
        var best = if (dir > 0) Float.MAX_VALUE else -Float.MAX_VALUE
        val x = e.x; val y = e.y; val z = e.z; val hw = e.hw; val h = e.h
        for (bx in floor(x - hw).toInt()..floor(x + hw).toInt())
            for (bz in floor(z - hw).toInt()..floor(z + hw).toInt())
                for (by in floor(y).toInt()..floor(y + h).toInt()) {
                    if (!world.solidBox(bx, by, bz, x - hw, y, z - hw, x + hw, y + h, z + hw)) continue
                    val b = world.boxOf(bx, by, bz)
                    val o = when (axis) { 0 -> bx; 1 -> by; else -> bz }
                    val lo = o + (b?.get(axis) ?: 0f); val hi = o + (b?.get(axis + 3) ?: 1f)
                    best = if (dir > 0) min(best, lo) else max(best, hi)
                }
        if (elevShafts.isNotEmpty()) best = elevEdge(e, axis, dir, best)
        return best
    }

    private fun move(e: Ent, dx: Float, dy: Float, dz: Float) {
        e.x += dx
        if (collides(e.x, e.y, e.z, e.hw, e.h)) {
            e.x = if (dx > 0) edgeAt(e, 0, 1f) - e.hw - 0.001f else edgeAt(e, 0, -1f) + e.hw + 0.001f; e.vx = 0f
        }
        e.z += dz
        if (collides(e.x, e.y, e.z, e.hw, e.h)) {
            e.z = if (dz > 0) edgeAt(e, 2, 1f) - e.hw - 0.001f else edgeAt(e, 2, -1f) + e.hw + 0.001f; e.vz = 0f
        }
        e.y += dy; e.onGround = false
        if (collides(e.x, e.y, e.z, e.hw, e.h)) {
            if (dy < 0) { e.y = edgeAt(e, 1, -1f) + 0.001f; e.onGround = true } else e.y = edgeAt(e, 1, 1f) - e.h - 0.002f
            e.vy = 0f
        }
        if (!e.onGround && dy <= 0f && e.vy <= 0f && collides(e.x, e.y - 0.03f, e.z, e.hw, e.h)) { e.onGround = true; e.vy = 0f }
        e.inWater = world.get(floor(e.x).toInt(), floor(e.y + 0.4f).toInt(), floor(e.z).toInt()) == B.WATER
    }

    // ---------- PULO: toque rápido = pulo normal; segurar = carrega (agacha, inclina, levanta poeira) e solta um super pulo ----------
    private fun groundId(p: Ent): Int = world.get(floor(p.x).toInt(), floor(p.y - 0.06f).toInt(), floor(p.z).toInt())

    private fun updateJump(p: Ent, dt: Float) {
        val ok = !flying && posture == 0 && !p.inWater && !driving && !seated && elevRide == null
        if (jumpLatch) { jumpLatch = false; if (ok && p.onGround && !jumpArmed) { jumpArmed = true; jumpHoldT = 0f; jumpDustT = 0f } }
        if (superAir && (p.inWater || flying || driving)) superAir = false
        if (jumpArmed) {
            if (!ok || !p.onGround) { jumpArmed = false; jumpHoldT = 0f }
            else if (jumpHeld) {
                jumpHoldT += dt
                val ch = ((jumpHoldT - JUMP_TAP_T) / JUMP_FULL_T).coerceIn(0f, 1f)
                superCharge = ch
                jumpCharge = 0.35f * min(1f, jumpHoldT / JUMP_TAP_T) + 0.65f * ch
                if (ch > 0f) chargeFx(p, ch, dt)
            } else {   // soltou: pulo normal (toque) ou super pulo (segurou)
                val ch = ((jumpHoldT - JUMP_TAP_T) / JUMP_FULL_T).coerceIn(0f, 1f)
                jumpArmed = false; jumpHoldT = 0f; superCharge = 0f; jumpCharge = 0f
                val gid = groundId(p)
                if (ch > 0.05f) {
                    p.vy = 9.2f + 8.0f * ch; p.onGround = false
                    superAir = true; superVy0 = p.vy; superTrailT = 0f
                    if (stickX != 0f || stickY != 0f) { p.vx += sin(bodyYaw) * 3.2f * ch; p.vz += cos(bodyYaw) * 3.2f * ch }
                    if (gid != B.AIR && gid != B.WATER) superLaunchDust(p.x, p.y, p.z, gid, ch)
                    shake = max(shake, 0.06f + 0.14f * ch)
                } else {
                    p.vy = 8.6f; p.onGround = false; superAir = false
                    if (gid != B.AIR && gid != B.WATER) jumpPuff(p.x, p.y, p.z, gid)
                }
            }
        } else { superCharge = 0f; jumpCharge = 0f }
    }

    private fun chargeFx(p: Ent, ch: Float, dt: Float) {
        shake = max(shake, 0.008f + 0.028f * ch)
        jumpDustT -= dt
        if (jumpDustT > 0f || parts.size > 520) return
        jumpDustT = 0.11f - 0.07f * ch
        val gid = groundId(p); if (gid == B.AIR || gid == B.WATER) return
        val col = dustColor(gid)
        for (i in 0 until 2 + (ch * 3f).toInt()) {   // anel de poeira que o corpo vai empurrando pra fora do chão
            val a = rnd.nextFloat() * 6.2832f; val r = 0.25f + rnd.nextFloat() * 0.2f; val sp = 0.9f + rnd.nextFloat() * 1.2f * (0.5f + ch)
            val pt = Particle(p.x + cos(a) * r, p.y + 0.05f, p.z + sin(a) * r, cos(a) * sp, 0.25f + rnd.nextFloat() * 0.5f, sin(a) * sp, col, 0.07f + rnd.nextFloat() * 0.06f, 0.5f + rnd.nextFloat() * 0.4f)
            pt.dust = true; pt.grav = 0.5f; parts.add(pt)
        }
    }

    private fun jumpPuff(x: Float, y: Float, z: Float, gid: Int) {   // pulinho normal: uma nuvenzinha nos pés
        if (parts.size > 520) return
        val col = dustColor(gid)
        for (i in 0 until 6) {
            val a = i * 1.0472f + rnd.nextFloat() * 0.5f; val sp = 0.9f + rnd.nextFloat() * 0.8f
            val pt = Particle(x + cos(a) * 0.15f, y + 0.05f, z + sin(a) * 0.15f, cos(a) * sp, 0.3f + rnd.nextFloat() * 0.4f, sin(a) * sp, col, 0.06f + rnd.nextFloat() * 0.05f, 0.4f + rnd.nextFloat() * 0.3f)
            pt.dust = true; pt.grav = 0.5f; parts.add(pt)
        }
    }

    private fun chip(x: Float, y: Float, z: Float, gid: Int, n: Int, sp: Float) {   // pedacinhos do bloco voando
        for (i in 0 until n) {
            val base = if (gid == B.GRASS && rnd.nextFloat() < 0.5f) B.side[gid] else B.top[gid]; val k = 0.82f + rnd.nextFloat() * 0.36f
            val r = ((base shr 16 and 255) * k).toInt().coerceIn(0, 255); val g = ((base shr 8 and 255) * k).toInt().coerceIn(0, 255); val b = ((base and 255) * k).toInt().coerceIn(0, 255)
            val a = rnd.nextFloat() * 6.2832f; val v = sp * (0.4f + rnd.nextFloat() * 0.6f)
            parts.add(Particle(x + cos(a) * 0.3f, y + 0.08f, z + sin(a) * 0.3f, cos(a) * v, 2.5f + rnd.nextFloat() * 3.5f * sp * 0.4f, sin(a) * v, (r shl 16) or (g shl 8) or b, 0.04f + rnd.nextFloat() * 0.04f, 0.7f + rnd.nextFloat() * 0.5f))
        }
    }

    /** decolagem do super pulo: onda de poeira rente ao chão, anel mais alto e uma coluna subindo junto com o boneco */
    private fun superLaunchDust(x: Float, y: Float, z: Float, gid: Int, ch: Float) {
        if (parts.size > 560) return
        val col = dustColor(gid); val pw = 0.6f + 0.6f * ch
        val n1 = 22 + (ch * 18f).toInt()
        for (i in 0 until n1) {   // onda grande, rasteira
            val a = i * 6.2832f / n1 + rnd.nextFloat() * 0.25f; val sp = (3.2f + rnd.nextFloat() * 2.4f) * pw
            val pt = Particle(x + cos(a) * 0.25f, y + 0.06f, z + sin(a) * 0.25f, cos(a) * sp, 0.25f + rnd.nextFloat() * 0.6f, sin(a) * sp, col, 0.13f + rnd.nextFloat() * 0.1f, 0.8f + rnd.nextFloat() * 0.5f)
            pt.dust = true; pt.grav = 0.3f; parts.add(pt)
        }
        for (i in 0 until 14) {   // anel interno, mais alto
            val a = i * 0.4488f + rnd.nextFloat() * 0.3f; val sp = (1.4f + rnd.nextFloat() * 1.2f) * pw
            val pt = Particle(x + cos(a) * 0.2f, y + 0.1f, z + sin(a) * 0.2f, cos(a) * sp, 1.4f + rnd.nextFloat() * 1.6f, sin(a) * sp, col, 0.1f + rnd.nextFloat() * 0.08f, 0.8f + rnd.nextFloat() * 0.4f)
            pt.dust = true; pt.grav = 0.2f; parts.add(pt)
        }
        for (i in 0 until 10) {   // coluna de poeira subindo atrás do boneco
            val a = rnd.nextFloat() * 6.2832f; val r = rnd.nextFloat() * 0.2f
            val pt = Particle(x + cos(a) * r, y + 0.1f + rnd.nextFloat() * 0.5f, z + sin(a) * r, cos(a) * 0.5f, 2.5f + rnd.nextFloat() * 3.5f * pw, sin(a) * 0.5f, col, 0.1f + rnd.nextFloat() * 0.08f, 0.8f + rnd.nextFloat() * 0.4f)
            pt.dust = true; pt.grav = 0.2f; parts.add(pt)
        }
        chip(x, y, z, gid, 8 + (ch * 6f).toInt(), 3.2f * pw)
    }

    /** aterrissagem de super pulo: choque de poeira em volta + pedaços do chão */
    private fun superLandDust(x: Float, y: Float, z: Float, gid: Int, power: Float) {
        if (parts.size > 540) return
        val col = dustColor(gid); val pw = (power / 16f).coerceIn(0.6f, 1.3f)
        for (i in 0 until 30) {
            val a = i * 0.2094f + rnd.nextFloat() * 0.2f; val sp = (3.4f + rnd.nextFloat() * 2.8f) * pw
            val pt = Particle(x + cos(a) * 0.3f, y + 0.06f, z + sin(a) * 0.3f, cos(a) * sp, 0.3f + rnd.nextFloat() * 0.7f, sin(a) * sp, col, 0.14f + rnd.nextFloat() * 0.1f, 0.8f + rnd.nextFloat() * 0.5f)
            pt.dust = true; pt.grav = 0.3f; parts.add(pt)
        }
        for (i in 0 until 14) {
            val a = rnd.nextFloat() * 6.2832f; val sp = (1.2f + rnd.nextFloat() * 1.6f) * pw
            val pt = Particle(x + cos(a) * 0.25f, y + 0.1f, z + sin(a) * 0.25f, cos(a) * sp, 1.6f + rnd.nextFloat() * 1.8f, sin(a) * sp, col, 0.1f + rnd.nextFloat() * 0.08f, 0.8f + rnd.nextFloat() * 0.4f)
            pt.dust = true; pt.grav = 0.25f; parts.add(pt)
        }
        chip(x, y, z, gid, 12, 3.6f * pw)
        shake = max(shake, 0.12f + 0.16f * pw)
    }

    fun jumpTap() {   // toque duplo no botão de subir (modo criativo) volta a voar
        if (!creative) return
        if (superAir && !player.onGround && !player.inWater && !flying) { flying = true; launchReq = true; lastJumpT = time; return }   // no ar do super pulo: um toque já voa (sem vy = 0: a subida continua fluida)
        if (time - lastJumpT < 0.32f && !flying) { flying = true; player.vy = 0f }
        lastJumpT = time
    }

    fun downTap() {   // toque duplo no botão de descer sai do voo
        if (!creative) return
        if (time - lastDownT < 0.32f && flying) flying = false
        lastDownT = time
    }

    /** o ponto está mesmo dentro d'água (a camada de cima da água é mais baixa que o bloco cheio) */
    private fun waterAtPt(x: Float, y: Float, z: Float): Boolean {
        val bx = floor(x).toInt(); val by = floor(y).toInt(); val bz = floor(z).toInt()
        if (world.get(bx, by, bz) != B.WATER) return false
        return world.get(bx, by + 1, bz) == B.WATER || y < by + 0.88f
    }

    private fun groundBelow(e: Ent): Float {
        val bx = floor(e.x).toInt(); val bz = floor(e.z).toInt()
        var y = floor(e.y + 0.05f).toInt()
        while (y >= 0) { if (world.solidCol(bx, y, bz)) return y + (world.boxOf(bx, y, bz)?.get(4) ?: 1f); y-- }
        return 0f
    }

    /** topo do degrau à frente (altura absoluta) se der pra subir nele a partir daqui (com espaço pra cabeça), senão -1 */
    private fun ledgeAhead(e: Ent, reach: Float): Float {
        val l = hypot(wishX, wishZ); if (l < 0.5f) return -1f
        val ax = e.x + wishX / l * (e.hw + 0.25f); val az = e.z + wishZ / l * (e.hw + 0.25f)
        val bx = floor(ax).toInt(); val bz = floor(az).toInt()
        var by = floor(e.y - 0.1f).toInt(); val minD = if (climbT > 0f && !e.onGround) -0.08f else 0.08f   // já subindo: continua até passar do topo
        while (by <= floor(e.y + reach).toInt()) {
            if (world.solidCol(bx, by, bz) && !world.solidCol(bx, by + 1, bz) && !world.solidCol(bx, by + 2, bz)) {
                val top = by + (world.boxOf(bx, by, bz)?.get(4) ?: 1f)
                if (top - e.y > minD && top - e.y <= reach) return top
            }
            by++
        }
        return -1f
    }

    private fun hasWaterNear(x: Int, y: Int, z: Int) =
        world.get(x + 1, y, z) == B.WATER || world.get(x - 1, y, z) == B.WATER ||
        world.get(x, y, z + 1) == B.WATER || world.get(x, y, z - 1) == B.WATER || world.get(x, y + 1, z) == B.WATER

    /** um bloco sumiu: se tem água encostada (e é abaixo do nível do mar), ela escorre pra cobrir o buraco */
    private fun waterTouch(x: Int, y: Int, z: Int) {
        if (y < 0 || y > World.WATER_Y || world.get(x, y, z) != B.AIR) return
        if (hasWaterNear(x, y, z) && waterQ.size < 3000) waterQ.add((y * World.SZ + z) * World.SX + x)
    }

    private fun waterStep(dt: Float) {
        if (!waterInit) {   // uma vez ao abrir o mundo: buracos secos abaixo do nível do mar encostados na água (de mundos salvos) também enchem
            waterInit = true
            for (y in 0..World.WATER_Y) for (z in 0 until World.SZ) for (x in 0 until World.SX)
                if (world.get(x, y, z) == B.AIR && hasWaterNear(x, y, z) && waterQ.size < 3000) waterQ.add((y * World.SZ + z) * World.SX + x)
        }
        waterT -= dt
        if (waterQ.isEmpty() || waterT > 0f) return
        waterT = 0.09f
        val cur = ArrayList(waterQ); waterQ.clear()
        var n = 0
        for (k in cur) {
            if (n >= 36) { waterQ.add(k); continue }
            val x = k % World.SX; val z = (k / World.SX) % World.SZ; val y = k / (World.SX * World.SZ)
            if (world.get(x, y, z) != B.AIR || y > World.WATER_Y || !hasWaterNear(x, y, z)) continue
            world.set(x, y, z, B.WATER); n++
            waterTouch(x + 1, y, z); waterTouch(x - 1, y, z); waterTouch(x, y, z + 1); waterTouch(x, y, z - 1); waterTouch(x, y - 1, z)
        }
    }

    private fun physics(e: Ent, dt: Float) {
        if (flying && e === player) {
            val tv = (if (jumpHeld) 7f else 0f) - (if (downHeld) 7f else 0f) + (if (freeFly) flyVy else 0f)
            e.vy += (tv - e.vy) * min(1f, 8f * dt * (1f - 0.85f * min(1f, airDashT / 0.7f)))
            val nSub = ceil(sqrt(e.vx * e.vx + e.vy * e.vy + e.vz * e.vz) * dt / 0.35f).toInt().coerceIn(1, 14)   // dash rápido: passos curtos pra não atravessar parede
            for (i in 0 until nSub) move(e, e.vx * dt / nSub, e.vy * dt / nSub, e.vz * dt / nSub)
            return
        }
        if (e === player && (e.inWater || climbT > 0f)) {   // nado (ou saindo da água num degrau): flutua com a cabeça fora, mergulha olhando pra baixo e nadando, sobe olhando pra cima
            val surf = World.WATER_Y + 0.88f
            climbT = max(0f, climbT - dt)
            val mg = hypot(stickX, stickY)
            // empuxo de mola: a água empurra pra cima (mais forte quanto mais fundo), balança com as ondas e amortece o mergulho
            val bob = sin(time * 1.5f + e.x * 0.7f + e.z * 0.5f) * 0.05f + sin(time * 2.3f + e.z * 0.9f) * 0.025f
            var acc = (((surf - 1.05f) + bob - e.y) * 10f).coerceIn(-5f, 7f)
            if (downHeld) acc = -15f   // botão de afundar
            else if (jumpHeld) acc += 17f
            else if (mg > 0.15f && pitch < -0.28f) acc = sin(pitch) * 16f
            else if (mg > 0.15f && pitch > 0.28f) acc = sin(pitch) * 12f
            // degrau: tem bloco na frente que dá pra subir (1 de altura sem pular, até a margem com o botão de pular) -> empurra pra cima
            val ledge = if (e === player) ledgeAhead(e, if (jumpHeld || e.y >= surf - 1.4f) 2.4f else 1.3f) else -1f
            if (ledge > 0f && !downHeld) { climbT = 0.3f; if (e.y < ledge + 0.01f) acc = max(acc, 3.4f * ((ledge + 0.01f - e.y) * 7f).coerceIn(1f, 4.4f)) }   // sobe até passar do topo, sem passar muito
            e.vy += (acc - 3.4f * e.vy) * dt
            e.vy = e.vy.coerceIn(-4.6f, 4.4f)
            e.vx *= 1f - 3f * dt; e.vz *= 1f - 3f * dt
            move(e, e.vx * dt, e.vy * dt, e.vz * dt)
            if (e === player) {   // teto: o máximo é a cabeça pra fora (nada de ficar "em pé" na água), a não ser que o fundo seja raso ou tenha degrau
                var cap = max(surf - 1.05f + 0.1f, groundBelow(e) + 0.9f)
                if (ledge > 0f) cap = max(cap, ledge + 0.06f)
                if (e.y > cap) e.vy = min(e.vy, -min(6f, (e.y - cap) * 8f))   // acima do teto: empurra pra baixo por velocidade (a colisão cuida do resto; mexer no y direto enterrava na borda do bloco)
            }
            return
        }
        val g = if (e.inWater) 8f else 26f
        e.vy -= g * dt
        if (e.inWater) { e.vy = max(e.vy, -3f); e.vx *= 1f - 3f * dt; e.vz *= 1f - 3f * dt }
        if (e.vy < -40f) e.vy = -40f
        move(e, e.vx * dt, e.vy * dt, e.vz * dt)
    }

    fun burst(x: Float, y: Float, z: Float, color: Int, n: Int, speed: Float) {
        if (parts.size > 520) return
        for (i in 0 until n) parts.add(Particle(x, y, z, (rnd.nextFloat() - 0.5f) * speed, rnd.nextFloat() * speed * 0.9f + 1f,
            (rnd.nextFloat() - 0.5f) * speed, color, 0.08f + rnd.nextFloat() * 0.1f, 0.6f + rnd.nextFloat() * 0.5f))
    }

    /** só partículas pequenas na cor do bloco (grama mistura verde e terra) */
    fun breakFx(bx: Int, by: Int, bz: Int, id: Int, big: Boolean) {
        val n = if (big) 30 else 6
        for (i in 0 until n) {
            val base = if (id == B.GRASS && rnd.nextFloat() < 0.5f) B.side[id] else if (id == B.WOOD && rnd.nextFloat() < 0.5f) B.side[id] else B.top[id]
            val k = 0.82f + rnd.nextFloat() * 0.36f
            val r = ((base shr 16 and 255) * k).toInt().coerceIn(0, 255); val gg = ((base shr 8 and 255) * k).toInt().coerceIn(0, 255); val b = ((base and 255) * k).toInt().coerceIn(0, 255)
            val px = bx + 0.1f + rnd.nextFloat() * 0.8f; val py = by + 0.1f + rnd.nextFloat() * 0.8f; val pz = bz + 0.1f + rnd.nextFloat() * 0.8f
            val sp = if (big) 2.6f else 1.5f
            parts.add(Particle(px, py, pz, (px - bx - 0.5f) * sp * 2f + (rnd.nextFloat() - 0.5f) * 0.6f, rnd.nextFloat() * sp * 0.8f + 0.9f,
                (pz - bz - 0.5f) * sp * 2f + (rnd.nextFloat() - 0.5f) * 0.6f, (r shl 16) or (gg shl 8) or b,
                0.035f + rnd.nextFloat() * (if (big) 0.055f else 0.04f), 0.7f + rnd.nextFloat() * 0.6f))
        }
        if (big) shake = max(shake, 0.08f)
    }

    fun raycast(ox: Float, oy: Float, oz: Float, dx: Float, dy: Float, dz: Float, t0: Float, t1: Float) {
        hasHit = false
        var t = t0
        var lx = Int.MIN_VALUE; var ly = 0; var lz = 0
        while (t < t1) {
            val cx = floor(ox + dx * t).toInt(); val cy = floor(oy + dy * t).toInt(); val cz = floor(oz + dz * t).toInt()
            if (world.solidPt(cx, cy, cz, ox + dx * t, oy + dy * t, oz + dz * t)) {
                if (lx != Int.MIN_VALUE) {
                    hasHit = true; hx = cx; hy = cy; hz = cz; px = lx; py = ly; pz = lz; rT = t
                    if (!B.solid(world.get(cx, cy, cz))) {   // parte extra de móvel grande: mira/quebra vale pro bloco principal
                        val ex = Deco.extraFind(world, cx, cy, cz)
                        if (ex != null) { hx = ex.ax; hy = ex.ay; hz = ex.az }
                    }
                }
                return
            }
            lx = cx; ly = cy; lz = cz
            t += 0.04f
        }
    }

    private fun fwdX() = sin(yaw) * cos(pitch)
    private fun fwdY() = sin(pitch)
    private fun fwdZ() = cos(yaw) * cos(pitch)
    // mira por toque: desvio normalizado (-1..1) a partir do centro da tela; vale só quando aimOn
    @Volatile var aimOn = false; @Volatile var aimNx = 0f; @Volatile var aimNy = 0f; @Volatile var aspect = 2f
    @Volatile var sprint = false
    @Volatile var flyImp = 0f; private var wasFast = false; private var trailT = 0f   // impulso de herói ao disparar no voo + rastro
    @Volatile var flyLaunch = 0f; @Volatile private var launchReq = false   // decolagem: super pulo + botão de pular no ar -> voa (1 -> 0 em ~0,8s, pra animação)
    @Volatile var dashHeld = false; @Volatile var dashFire = false; @Volatile var airDashCharge = 0f   // dash aéreo: segura o botão de correr no voo (carrega) e solta (dispara)
    @Volatile var airDashStomp = 0f; @Volatile var dashDirPitch = 0f; private var dashPre = 0f; private var dashCh = 0f; private var chargeFxT = 0f; private var trailFxT = 0f; private var vertFxT = 0f
    // manobras no ar: o botão de manobra é um ANALÓGICO (arrasta e solta). A direção escolhe a manobra e a velocidade do voo (normal x rápido) muda o estilo:
    // 1 mortal/looping, 2 mortal/looping de costas, 3/4 parafuso/tonel esq/dir, 5 pirueta/tonel triplo, 10/11 saca-rolha/espiral esq/dir, 12/13 giro de costas/espiral invertida esq/dir
    @Volatile var trickReq = false; @Volatile var trickKind = 0; @Volatile var trickA = 0f   // trickA: progresso 0..1
    @Volatile var trickDx = 0f; @Volatile var trickDy = 0f   // analógico ao soltar (x: lados, y: cima = pra frente)
    @Volatile var trickPitch = 0f; @Volatile var trickRoll = 0f; @Volatile var trickYaw = 0f   // total de graus em cada eixo do corpo (múltiplos de 360)
    @Volatile var trickFast = false   // a manobra começou no voo rápido
    private var trickLeft = 0f; private var trickDur = 0.65f
    fun trickOk(): Boolean = !player.onGround && !player.inWater && !driving && !seated && posture == 0 && trickLeft <= 0f
    fun trickFastNow(): Boolean = flying && (airDashT > 0.02f || hypot(player.vx, player.vz) > 11f)
    /** direção do analógico (dx: lados, dy: cima) -> tipo da manobra */
    fun trickPick(dx: Float, dy: Float): Int {
        if (hypot(dx, dy) < 0.38f) return 5
        val a = Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())).toFloat()   // 0 = cima, 90 = direita, 180 = baixo
        val sec = (((a + 360f + 22.5f) % 360f) / 45f).toInt()   // 0 cima, 1 cima-dir, 2 dir, 3 baixo-dir, 4 baixo, 5 baixo-esq, 6 esq, 7 cima-esq
        return when (sec) { 0 -> 1; 1 -> 11; 2 -> 4; 3 -> 13; 4 -> 2; 5 -> 12; 6 -> 3; else -> 10 }
    }
    fun trickName(kind: Int, fast: Boolean): String = if (!fast) when (kind) {
        1 -> "Mortal"; 2 -> "Mortal de costas"; 3 -> "Parafuso esquerda"; 4 -> "Parafuso direita"; 5 -> "Pirueta"
        10 -> "Saca-rolha esquerda"; 11 -> "Saca-rolha direita"; 12 -> "Giro de costas esquerda"; else -> "Giro de costas direita"
    } else when (kind) {
        1 -> "Looping"; 2 -> "Looping de costas"; 3 -> "Tonel duplo esquerda"; 4 -> "Tonel duplo direita"; 5 -> "Tonel triplo"
        10 -> "Espiral esquerda"; 11 -> "Espiral direita"; 12 -> "Espiral invertida esquerda"; else -> "Espiral invertida direita"
    }
    /** curva do giro: recuo de preparação (0..0,12), giro suave (acelera e freia) entre 0,10 e 0,90, passa um tiquinho do ponto e assenta no final */
    fun trickEase(a: Float): Float {
        val ant = -0.04f * sin(3.1416f * (a / 0.13f).coerceIn(0f, 1f))
        val w = ((a - 0.10f) / 0.80f).coerceIn(0f, 1f)
        val sm = w * w * w * (w * (w * 6f - 15f) + 10f)          // smootherstep: sem tranco na saída nem na chegada
        val main = 1.03f * (0.88f * sm + 0.12f * w)              // passa ~3% do giro...
        val s = ((a - 0.90f) / 0.10f).coerceIn(0f, 1f)
        val settle = main - 0.03f * (s * s * (3f - 2f * s))      // ...e assenta exatamente no ângulo final
        return settle + ant
    }
    /** subida visual do corpo durante a manobra (arco do pulo, com um agachadinho de preparação) */
    fun trickHop(a: Float): Float {
        val u = ((a - 0.05f) / 0.90f).coerceIn(0f, 1f)
        val arc = (if (trickFast) 0.24f else 0.36f) * sin(3.1416f * u)
        val dip = 0.05f * sin(3.1416f * (a / 0.11f).coerceIn(0f, 1f))
        return arc - dip
    }
    private var trickQ = false; private var trickQDx = 0f; private var trickQDy = 0f; private var trickCombo = 0   // próxima manobra emendada (aperta na 2ª metade da atual)
    fun trickCanQueue(): Boolean = trickLeft > 0f && trickA > 0.5f && !trickQ && !player.onGround && !player.inWater && !driving
    private fun trickStart(p: Ent, dx: Float, dy: Float) {
        val fast = trickFastNow()
        val k = trickPick(dx, dy)
        var pt = 0f; var rl = 0f; var yw = 0f; var du = 0.8f
        if (!fast) {
            when (k) {
                1 -> { pt = 360f; du = 0.95f }
                2 -> { pt = -360f; du = 0.95f }
                3 -> { rl = -360f; du = 0.78f }
                4 -> { rl = 360f; du = 0.78f }
                5 -> { yw = 720f; du = 0.90f }
                10 -> { pt = 360f; rl = -360f; du = 1.10f }
                11 -> { pt = 360f; rl = 360f; du = 1.10f }
                12 -> { pt = -360f; yw = 360f; du = 1.05f }
                else -> { pt = -360f; yw = -360f; du = 1.05f }
            }
        } else {
            when (k) {
                1 -> { pt = 720f; du = 1.20f }
                2 -> { pt = -720f; du = 1.20f }
                3 -> { rl = -720f; du = 0.72f }
                4 -> { rl = 720f; du = 0.72f }
                5 -> { rl = 1080f; du = 0.95f }
                10 -> { pt = 360f; rl = -720f; du = 1.25f }
                11 -> { pt = 360f; rl = 720f; du = 1.25f }
                12 -> { pt = -360f; rl = -720f; du = 1.25f }
                else -> { pt = -360f; rl = 720f; du = 1.25f }
            }
        }
        trickKind = k; trickFast = fast; trickPitch = pt; trickRoll = rl; trickYaw = yw
        trickDur = du; trickLeft = du; trickA = 0f
        shake = max(shake, if (fast) 0.035f else 0.012f)
        pickMsg = (if (trickCombo >= 2) "COMBO x" + trickCombo + "  " else "") + trickName(k, fast); pickT = 1.3f
        if (flying && !fast) p.vy = max(p.vy, 2.4f)   // voo normal: um pulinho de estilo pra cima
    }
    val dashActive: Boolean get() = airDashT > 0.02f
    private var airDashT = 0f; private var windAmt = 0f   // airDashT: tempo restante do impulso (a velocidade extra se dissipa devagar); windAmt: intensidade suave do vento (0..1)
    // postura: 0 em pé, 1 agachado, 2 sentado, 3 deitado (valores suavizados pra animação/câmera)
    @Volatile var posture = 0
    var crouchA = 0f; var sitA = 0f; var lieA = 0f
    private val postH = floatArrayOf(1.8f, 1.45f, 1.1f, 0.9f)
    /** pede uma postura; repetir a mesma volta pra em pé. Não levanta se tiver teto em cima. */
    fun requestPosture(k: Int) {
        if (seated) { standUp(); return }   // qualquer botão de postura levanta da cadeira
        if (k == 2 && posture != 2 && player.onGround) { val c = nearestChair(); if (c != null) { sitOnChair(c[0], c[1], c[2]); return } }
        val target = if (posture == k) 0 else k
        if (postH[target] > player.h && collides(player.x, player.y, player.z, player.hw, postH[target])) return
        posture = target; player.h = postH[target]
        if (target != 0) { sprint = false; jumpHeld = false }
    }
    // ---------- sentar na cadeira ----------
    @Volatile var seated = false
    private var seatX = 0; private var seatY = 0; private var seatZ = 0; private var seatDir = 0; private var seatT = 0f
    /** cadeira livre mais perto do jogador (até ~1,7 bloco), preferindo a que está na mira */
    private fun nearestChair(): IntArray? {
        val p = player; var best: IntArray? = null; var bd = 1.7f
        val fx = sin(yaw); val fz = cos(yaw)
        for (dy in -1..1) for (dz in -2..2) for (dx in -2..2) {
            val x = floor(p.x).toInt() + dx; val y = floor(p.y + 0.1f).toInt() + dy; val z = floor(p.z).toInt() + dz
            if (!Furniture.sittable(world.get(x, y, z))) continue
            val ex = x + 0.5f - p.x; val ez = z + 0.5f - p.z
            val d = hypot(ex, ez) - 0.25f * ((ex * fx + ez * fz) / max(0.001f, hypot(ex, ez)))   // pequeno bônus pra cadeira à frente
            if (d < bd) { bd = d; best = intArrayOf(x, y, z) }
        }
        return best
    }
    fun sitOnChair(x: Int, y: Int, z: Int) {
        val bid = world.get(x, y, z)
        if (!Furniture.sittable(bid) || flying || player.inWater) return
        val sb = Furniture.sub(bid)
        val dir = Furniture.facing(world, x, y, z, sb)
        seatX = x; seatY = y; seatZ = z; seatDir = dir; seated = true; seatT = 0.5f
        posture = 2; player.h = postH[2]; sprint = false; jumpHeld = false
        val bx = floatArrayOf(0f, 0f, 1f, -1f)[dir]; val bz = floatArrayOf(1f, -1f, 0f, 0f)[dir]
        player.x = x + 0.5f - bx * 0.06f; player.z = z + 0.5f - bz * 0.06f; player.y = y + Furniture.seatH(sb)
        player.vx = 0f; player.vy = 0f; player.vz = 0f; player.onGround = true
        val a = floatArrayOf(0f, 3.1416f, 1.5708f, -1.5708f)[dir]
        bodyYaw = a; yaw = a
    }
    private fun standUp() {
        seated = false; posture = 0; player.h = 1.8f
        val dxs = floatArrayOf(0f, 0f, 1f, -1f); val dzs = floatArrayOf(1f, -1f, 0f, 0f)
        var nx = seatX + 0.5f; var ny = seatY + 1.0f; var nz = seatZ + 0.5f
        val order = intArrayOf(seatDir, 2, 3, 0, 1)
        for (d in order) {
            val tx = seatX + 0.5f + dxs[d] * 0.95f; val tz = seatZ + 0.5f + dzs[d] * 0.95f
            if (!collides(tx, seatY + 0.01f, tz, player.hw, 1.8f)) { nx = tx; ny = seatY + 0.01f; nz = tz; break }
        }
        player.x = nx; player.y = ny; player.z = nz; player.vx = 0f; player.vy = 0f; player.vz = 0f
    }

    // ---------- elevador 4x4 ----------
    val elevShafts = java.util.concurrent.ConcurrentHashMap<Long, ElevShaft>()
    private var elevSeenVer = -1
    @Volatile var elevSel: ElevShaft? = null     // poço cujos botões estão na mira
    @Volatile var elevReq = 0                    // andar pedido (índice+1); a UI pede, o update executa
    @Volatile var elevRide: ElevShaft? = null

    private fun rebuildElev() {
        elevSeenVer = world.elevVer
        val cols = HashMap<Long, ArrayList<Int>>()
        for (k in world.elevAnchors) {
            val x = k % World.SX; val z = (k / World.SX) % World.SZ; val y = k / (World.SX * World.SZ)
            if (x + 4 > World.SX || z + 4 > World.SZ) continue
            cols.getOrPut((z.toLong() shl 32) or x.toLong()) { ArrayList() }.add(y)
        }
        for (key in elevShafts.keys) if (!cols.containsKey(key)) { if (elevRide === elevShafts[key]) elevRide = null; elevShafts.remove(key) }
        for ((key, l) in cols) {
            l.sort()
            val x = (key and 0xffffffffL).toInt(); val z = (key shr 32).toInt()
            val s = elevShafts.getOrPut(key) { ElevShaft(x, z).also { it.carY = l[0].toFloat() } }
            s.ys = l.toIntArray()
            if (!s.moving) { var b = l[0]; for (y in l) if (abs(y - s.carY) < abs(b - s.carY)) b = y; s.carY = b.toFloat() }
            val ax = x.toFloat(); val az = z.toFloat(); val y0 = l[0].toFloat(); val yt = l.last().toFloat(); val top = yt + 3.4f
            val bx = ArrayList<FloatArray>()
            for (y in l) bx.add(floatArrayOf(ax, y - 0.12f, az, ax + 4f, y.toFloat(), az + 4f))   // laje ABAIXO do piso: sem degrau na entrada
            for (cx in floatArrayOf(0f, 3.78f)) for (cz in floatArrayOf(0f, 3.78f)) bx.add(floatArrayOf(ax + cx, y0, az + cz, ax + cx + 0.22f, top, az + cz + 0.22f))
            bx.add(floatArrayOf(ax, y0, az, ax + 4f, top, az + 0.12f))
            bx.add(floatArrayOf(ax, y0, az, ax + 0.12f, top, az + 4f))
            bx.add(floatArrayOf(ax + 3.88f, y0, az, ax + 4f, top, az + 4f))
            for (i in 0 until l.size - 1) if (l[i + 1] > l[i] + 3) bx.add(floatArrayOf(ax, l[i] + 3f, az + 3.88f, ax + 4f, l[i + 1].toFloat(), az + 4f))
            bx.add(floatArrayOf(ax, yt + 3f, az + 3.88f, ax + 4f, top, az + 4f))
            bx.add(floatArrayOf(ax, yt + 3.2f, az, ax + 4f, top, az + 4f))
            s.boxes = bx.toTypedArray()
        }
    }
    private fun elevHit(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float): Boolean {
        for (s in elevShafts.values) {
            if (x1 < s.ax - 0.5f || x0 > s.ax + 4.5f || z1 < s.az - 0.5f || z0 > s.az + 4.5f) continue
            for (b in s.boxes) if (x1 > b[0] && x0 < b[3] && y1 > b[1] && y0 < b[4] && z1 > b[2] && z0 < b[5]) return true
        }
        return false
    }
    private fun elevEdge(e: Ent, axis: Int, dir: Float, cur: Float): Float {
        var best = cur
        val x0 = e.x - e.hw; val x1 = e.x + e.hw; val z0 = e.z - e.hw; val z1 = e.z + e.hw; val y0 = e.y; val y1 = e.y + e.h
        for (s in elevShafts.values) {
            if (x1 < s.ax - 0.5f || x0 > s.ax + 4.5f || z1 < s.az - 0.5f || z0 > s.az + 4.5f) continue
            for (b in s.boxes) if (x1 > b[0] && x0 < b[3] && y1 > b[1] && y0 < b[4] && z1 > b[2] && z0 < b[5])
                best = if (dir > 0) min(best, b[axis]) else max(best, b[axis + 3])
        }
        return best
    }
    private fun scanElev() {
        if (elevShafts.isEmpty() || seated || deadTimer > 0f) { elevSel = null; return }
        val p = player
        val dx = fwdX(); val dy = fwdY(); val dz = fwdZ()
        var best: ElevShaft? = null; var bestDot = 0.965f
        fun test(s: ElevShaft, px: Float, py: Float, pz: Float) {
            val vx = px - camX; val vy = py - camY; val vz = pz - camZ
            val d = sqrt(vx * vx + vy * vy + vz * vz); if (d > 5.5f || d < 0.2f) return
            val dot = (vx * dx + vy * dy + vz * dz) / d
            if (dot > bestDot) { bestDot = dot; best = s }
        }
        for (s in elevShafts.values) {
            if (abs(s.ax + 2f - p.x) > 9f || abs(s.az + 2f - p.z) > 9f) continue
            val inside = p.x > s.ax && p.x < s.ax + 4f && p.z > s.az && p.z < s.az + 4f && abs(p.y - s.carY) < 1.5f
            if (inside) test(s, s.ax + 3.62f, s.carY + 1.45f, s.az + 3.05f)
            for (y in s.ys) if (abs(p.y - y) < 2.5f) test(s, s.ax + 3.86f, y + 1.3f, s.az + 4.05f)
        }
        elevSel = best
    }
    private fun updateElev(dt: Float) {
        if (elevSeenVer != world.elevVer) rebuildElev()
        val rq = elevReq
        if (rq != 0) {
            elevReq = 0
            val s = elevSel; val p = player
            if (s != null && !s.moving) {
                val ti = rq - 1
                if (ti in s.ys.indices && abs(s.carY - s.ys[ti]) > 0.01f) {
                    s.fromY = s.carY; s.toY = s.ys[ti].toFloat(); s.target = ti; s.t = 0f
                    s.dur = max(2f, abs(s.toY - s.fromY) / 3f); s.dir = if (s.toY > s.fromY) 1 else -1; s.moving = true
                    val inside = p.x > s.ax && p.x < s.ax + 4f && p.z > s.az && p.z < s.az + 4f && abs(p.y - s.carY) < 0.7f
                    if (inside) { elevRide = s; flying = false; posture = 0; p.h = 1.8f }
                }
            }
        }
        for (s in elevShafts.values) {
            if (s.moving) {
                if (s.door > 0f) s.door = max(0f, s.door - dt * 2.2f)
                else {
                    s.t += dt
                    val k = (s.t / s.dur).coerceIn(0f, 1f); val e = k * k * (3f - 2f * k)
                    s.carY = s.fromY + (s.toY - s.fromY) * e
                    if (k >= 1f) { s.carY = s.toY; s.moving = false; s.dir = 0 }
                }
            } else if (s.door < 1f) s.door = min(1f, s.door + dt * 2f)
        }
        val r = elevRide
        if (r != null) {
            val p = player; val m = min(1f, 10f * dt)
            var nx = p.x + p.vx * dt; var nz = p.z + p.vz * dt
            nx += (nx.coerceIn(r.ax + 0.7f, r.ax + 3.3f) - nx) * m
            nz += (nz.coerceIn(r.az + 0.7f, r.az + 3.3f) - nz) * m
            p.x = nx; p.z = nz; p.y = r.carY + 0.08f; p.vy = 0f; p.onGround = true
            if (!r.moving && r.door > 0.9f) elevRide = null
        }
    }

    // ---------- televisão ----------
    /** TV ligada: a página do navegador é copiada pra este bitmap, que o renderizador cola na tela da TV */
    class TvLive(val x: Int, val y: Int, val z: Int) { @Volatile var bmp: android.graphics.Bitmap? = null; @Volatile var ready = false; @Volatile var seen = false }
    @Volatile var tvLive: TvLive? = null
    @Volatile var tvZoomT = 0f; @Volatile var tvZoomGoal = 0f
    @Volatile var tvAspect = 2f
    /** tela da TV no mundo: [cx, cy, cz, nx, nz, rx, rz, largura, altura]; n = pra onde a tela olha, r = direita de quem olha */
    fun tvGeom(x: Int, y: Int, z: Int): FloatArray {
        if (Deco.isTv(world.get(x, y, z))) return Deco.tvGeom(world, x, y, z)
        val rot = Furniture.facing(world, x, y, z, Furniture.S_TV)
        val sc = Furniture.TV_SCALE[world.tvLevel(x, y, z).coerceIn(0, Furniture.TV_SCALE.size - 1)]
        val ra = floatArrayOf(0.5f, 0.5f, 0.53f, 0.47f)[rot]; val rc = floatArrayOf(0.53f, 0.47f, 0.5f, 0.5f)[rot]
        val oy = Furniture.baseOff(world, x, y, z, Furniture.S_TV)   // TV em cima de cômoda/mesa desce um pouco
        return floatArrayOf(x + 0.5f + (ra - 0.5f) * sc, y + oy + 0.54f * sc, z + 0.5f + (rc - 0.5f) * sc,
            floatArrayOf(0f, 0f, 1f, -1f)[rot], floatArrayOf(1f, -1f, 0f, 0f)[rot], floatArrayOf(1f, -1f, 0f, 0f)[rot], floatArrayOf(0f, 0f, -1f, 1f)[rot],
            0.88f * sc, 0.58f * sc)
    }
    /** câmera de zoom: [camX, camY, camZ, dirX, dirZ, meia-largura (ndc), meia-altura (ndc)]; a tela fica centrada em 55% da altura */
    fun tvZoomPlan(): FloatArray? {
        val tl = tvLive ?: return null
        val g = tvGeom(tl.x, tl.y, tl.z); val a = tvAspect.coerceAtLeast(0.5f); val k = 0.70021f
        val dist = max(g[8] / 2f / (0.78f * k), g[7] / 2f / (0.92f * k * a))
        return floatArrayOf(g[0] + g[3] * dist, g[1] + 0.1f * dist * k, g[2] + g[4] * dist, -g[3], -g[4], g[7] / 2f / (dist * k * a), g[8] / 2f / (dist * k))
    }
    private fun tvBlend() {
        if (tvZoomT <= 0.001f) return
        val p = tvZoomPlan() ?: return
        val e = tvZoomT * tvZoomT * (3f - 2f * tvZoomT)
        camX += (p[0] - camX) * e; camY += (p[1] - camY) * e; camZ += (p[2] - camZ) * e
    }
    private fun tvLook(): FloatArray {
        val fx = fwdX(); val fy = fwdY(); val fz = fwdZ()
        if (tvZoomT <= 0.001f) return floatArrayOf(fx, fy, fz)
        val p = tvZoomPlan() ?: return floatArrayOf(fx, fy, fz)
        val e = tvZoomT * tvZoomT * (3f - 2f * tvZoomT)
        val x = fx + (p[3] - fx) * e; val y = fy - fy * e; val z = fz + (p[4] - fz) * e
        val l = max(0.0001f, sqrt(x * x + y * y + z * z))
        return floatArrayOf(x / l, y / l, z / l)
    }
    /** TV (x,y,z) que o jogador está olhando de perto, ou null; o HUD mostra "Usar TV" e "Tamanho" quando existe */
    @Volatile var tvAim: IntArray? = null
    private var tvScanT = 0f
    fun tvLevel(x: Int, y: Int, z: Int) = world.tvLevel(x, y, z)
    /** passa pro próximo tamanho (volta ao normal depois do maior); devolve o nível novo */
    fun cycleTvSize(x: Int, y: Int, z: Int): Int {
        if (!Furniture.isTv(world.get(x, y, z)) || Deco.isDeco(world.get(x, y, z))) return 0
        val lv = (world.tvLevel(x, y, z) + 1) % Furniture.TV_SCALE.size
        world.setTvLevel(x, y, z, lv)
        return lv
    }
    /** a linha do olho até o centro da TV está livre? (respeita o formato real dos blocos) */
    private fun tvLineClear(ex: Float, ey: Float, ez: Float, tx: Int, ty: Int, tz: Int): Boolean {
        val dx = tx + 0.5f - ex; val dy = ty + 0.5f - ey; val dz = tz + 0.5f - ez
        val n = (sqrt(dx * dx + dy * dy + dz * dz) / 0.25f).toInt()
        for (i in 1 until n) {
            val t = i / n.toFloat()
            val qx = ex + dx * t; val qy = ey + dy * t; val qz = ez + dz * t
            val cx = floor(qx).toInt(); val cy = floor(qy).toInt(); val cz = floor(qz).toInt()
            if (cx == tx && cy == ty && cz == tz) break
            if (world.solidPt(cx, cy, cz, qx, qy, qz)) return false
        }
        return true
    }
    private fun scanTv(dt: Float) {
        tvScanT -= dt; if (tvScanT > 0f) return
        tvScanT = 0.12f
        val p = player
        val ex = p.x; val ey = p.y + eyeH(); val ez = p.z
        val fx = fwdX(); val fy = fwdY(); val fz = fwdZ()
        val bx = floor(p.x).toInt(); val by = floor(p.y).toInt(); val bz = floor(p.z).toInt()
        var best: IntArray? = null; var bs = -9f
        for (dy in -3..4) for (dz in -6..6) for (dx in -6..6) {
            val x = bx + dx; val y = by + dy; val z = bz + dz
            if (!Furniture.isTv(world.get(x, y, z))) continue
            val vx = x + 0.5f - ex; val vy = y + 0.5f - ey; val vz = z + 0.5f - ez
            val dist = sqrt(vx * vx + vy * vy + vz * vz)
            if (dist > 6.5f || dist < 0.05f) continue
            val dot = (vx * fx + vy * fy + vz * fz) / dist
            if (dot < 0.8f) continue                       // só se estiver olhando (cone de ~37°)
            val sc = dot - dist * 0.03f
            if (sc <= bs) continue
            if (!tvLineClear(ex, ey, ez, x, y, z)) continue // sem parede no meio
            bs = sc; best = intArrayOf(x, y, z)
        }
        tvAim = best
    }

    // ---------- móveis interativos: abrir/fechar e ligar/desligar (estado salvo no World.fState) ----------
    /** móvel interativo (x,y,z) que o jogador está olhando de perto, ou null; o HUD mostra o botão de ação */
    @Volatile var fAim: IntArray? = null
    private var fScanT = 0f
    private fun scanFurn(dt: Float) {
        fScanT -= dt; if (fScanT > 0f) return
        fScanT = 0.1f
        if (driving) { fAim = null; return }
        val p = player
        val ex = p.x; val ey = p.y + eyeH(); val ez = p.z
        val fx = fwdX(); val fy = fwdY(); val fz = fwdZ()
        val bx = floor(p.x).toInt(); val by = floor(p.y).toInt(); val bz = floor(p.z).toInt()
        var best: IntArray? = null; var bs = -9f
        for (dy in -2..3) for (dz in -3..3) for (dx in -3..3) {
            val x = bx + dx; val y = by + dy; val z = bz + dz
            val id = world.get(x, y, z)
            if (id == 0 || Furniture.fKind(id) == 0) continue
            val vx = x + 0.5f - ex; val vy = y + 0.5f - ey; val vz = z + 0.5f - ez
            val dist = sqrt(vx * vx + vy * vy + vz * vz)
            if (dist > 3.4f || dist < 0.05f) continue
            val dot = (vx * fx + vy * fy + vz * fz) / dist
            if (dot < 0.86f) continue
            val sc = dot - dist * 0.05f
            if (sc <= bs) continue
            if (!tvLineClear(ex, ey, ez, x, y, z)) continue
            bs = sc; best = intArrayOf(x, y, z)
        }
        fAim = best
    }
    /** abre/fecha ou liga/desliga o móvel; devolve true se mudou algo */
    fun toggleFurn(x: Int, y: Int, z: Int): Boolean {
        val id = world.get(x, y, z)
        val st = world.fStateOf(x, y, z)
        when (Furniture.fKind(id)) {
            1 -> world.setFState(x, y, z, st xor Furniture.ST_OPEN)
            2 -> world.setFState(x, y, z, st xor Furniture.ST_OFF)
            3 -> world.setFState(x, y, z, (st and 3) or ((((st shr 2) + 1) and 3) shl 2))   // gira 90° por toque
            else -> return false
        }
        return true
    }

    /** prévia ao colocar móveis: 0 = nada, 1 = pode colocar (verde), 2 = bloqueado (vermelho), 3 = sem apoio embaixo (amarelo, ainda dá pra colocar) */
    fun placeCheck(): Int {
        if (!hasHit || driving) return 0
        val id = cur()
        if (!Items.isBlock(id) || (B.cat[id] != Blk.C_FURN && !Deco.isDeco(id))) return 0
        val p = player
        val ddx = px + 0.5f - p.x; val ddy = py + 0.5f - (p.y + 0.9f); val ddz = pz + 0.5f - p.z
        if (ddx * ddx + ddy * ddy + ddz * ddz > 49f) return 0
        val overlap = px + 1 > p.x - p.hw && px < p.x + p.hw && pz + 1 > p.z - p.hw && pz < p.z + p.hw && py + 1 > p.y && py < p.y + p.h
        if (overlap || py < 1 || py >= World.SY - 1 || world.get(px, py, pz) != B.AIR) return 2
        if (Deco.isDeco(id) && !Deco.fits(world, id, px, py, pz, decoDirAt(px, py, pz), p.x, p.y, p.z, p.hw, p.h)) return 2   // móvel grande (cama, armário alto...) não cabe aqui
        val below = world.get(px, py - 1, pz)
        return if (below == B.AIR || below == B.WATER) 3 else 1
    }

    /** direção da frente de um móvel importado na hora de colocar: encosta as costas na parede se tiver uma; senão fica de frente pra quem colocou */
    private fun decoDirAt(x: Int, y: Int, z: Int): Int {
        val ddx = intArrayOf(0, 0, 1, -1); val ddz = intArrayOf(1, -1, 0, 0)
        for (d in 0..3) { val bk = world.get(x - ddx[d], y, z - ddz[d]); if (B.solid(bk) && B.shape(bk) == 0) return d }
        return BuildShapes.lookDir(yaw) xor 1
    }

    /** gira um móvel 90° pra direita, sobrepondo a direção automática (móveis novos e os importados do addon) */
    fun rotateFurn(x: Int, y: Int, z: Int): Boolean {
        val id = world.get(x, y, z)
        if (Deco.isDeco(id)) {
            val cd = Furniture.faceOf(world, x, y, z, 0)
            val nd = (cd + 1) and 3
            val pl = player
            if (!Deco.fits(world, id, x, y, z, nd, pl.x, pl.y, pl.z, pl.hw, pl.h)) return false
            world.setFState(x, y, z, (world.fStateOf(x, y, z) and 15) or 64 or (nd shl 4))
            return true
        }
        val s = Furniture.sub(id)
        if (s < 256) return false
        val st = world.fStateOf(x, y, z)
        val cur = Furniture.faceOf(world, x, y, z, s)
        world.setFState(x, y, z, (st and 15) or 64 or (((cur + 1) and 3) shl 4))
        return true
    }

    /** toca num lugar de um móvel de exposição: vazio guarda o item da mão; cheio devolve o item pra mão */
    fun storeTap(x: Int, y: Int, z: Int, slot: Int): Boolean {
        val id = world.get(x, y, z)
        if (slot !in 0 until Furniture.slotsOf(id)) return false
        val inside = world.storeOf(x, y, z)?.getOrNull(slot) ?: 0
        if (inside == 0) {
            val held = cur()
            if (held == id) return false   // não guarda o próprio móvel dentro dele
            world.setStore(x, y, z, slot, held)
        } else {
            hotbar[sel.coerceIn(0, 7)] = inside
            world.setStore(x, y, z, slot, 0)
        }
        return true
    }

    fun eyeH() = 1.6f - 0.35f * crouchA - 0.65f * sitA - 1.15f * lieA
    private fun aimVec(): FloatArray {
        val fx = fwdX(); val fy = fwdY(); val fz = fwdZ()
        if (!aimOn) return floatArrayOf(fx, fy, fz)
        val tv = 0.70021f; val th = tv * aspect
        val rx = -cos(yaw); val rz = sin(yaw)
        val ux = -sin(yaw) * sin(pitch); val uy = cos(pitch); val uz = -cos(yaw) * sin(pitch)
        val a = aimNx * th; val b = -aimNy * tv
        var x = fx + rx * a + ux * b; var y = fy + uy * b; var z = fz + rz * a + uz * b
        val l = max(0.0001f, sqrt(x * x + y * y + z * z))
        return floatArrayOf(x / l, y / l, z / l)
    }
    private fun dirX() = aimVec()[0]
    private fun dirY() = aimVec()[1]
    private fun dirZ() = aimVec()[2]

    private fun updateCamera() { updateCamera0(); tvBlend() }
    private fun updateCamera0() {
        val ex = player.x; val ey = player.y + eyeH() + (if (driving) 0.55f else 0f); val ez = player.z
        if (!thirdPerson) { camX = ex; camY = ey; camZ = ez; camDist = 0f; return }
        val dx = -fwdX(); val dy = -fwdY(); val dz = -fwdZ()
        var d = 0.3f
        val maxD = if (driving) (if (curKind == 2) 5.0f + 1.4f * skateV.speedK else if (curKind == 1) 6.4f else 7.8f) else 4.6f
        while (d < maxD) {
            val nx = ex + dx * (d + 0.25f); val ny = ey + 0.3f + dy * (d + 0.25f); val nz = ez + dz * (d + 0.25f)
            if (world.solid(floor(nx).toInt(), floor(ny).toInt(), floor(nz).toInt())) break
            d += 0.1f
        }
        camDist = d
        camX = ex + dx * d; camY = ey + 0.3f + dy * d; camZ = ez + dz * d
    }

    private fun hitSlime(s: Slime, dmg: Int, dx: Float, dz: Float, knock: Float) {
        s.hp -= dmg; s.flash = 0.25f
        val l = max(0.01f, sqrt(dx * dx + dz * dz))
        s.vx = dx / l * knock; s.vz = dz / l * knock; s.vy = 5f; s.squash = 0.4f
        burst(s.x, s.y + 0.5f, s.z, 0xFFFFFF, 6, 4f); shake = 0.25f
        if (s.hp <= 0 && !s.dead) { s.dead = true; kills++; burst(s.x, s.y + 0.4f, s.z, 0x6FD65A, 26, 6f) }
    }

    private fun shoot() {
        raycast(camX, camY, camZ, dirX(), dirY(), dirZ(), camDist, camDist + 40f)
        val tx: Float; val ty: Float; val tz: Float
        if (hasHit) { tx = hx + 0.5f; ty = hy + 0.5f; tz = hz + 0.5f }
        else { tx = camX + dirX() * (camDist + 40f); ty = camY + dirY() * (camDist + 40f); tz = camZ + dirZ() * (camDist + 40f) }
        val sx = player.x + sin(yaw) * 0.7f; val sy = player.y + 1.3f; val sz = player.z + cos(yaw) * 0.7f
        var dx = tx - sx; var dy = ty - sy; var dz = tz - sz
        val l = max(0.1f, sqrt(dx * dx + dy * dy + dz * dz)); dx /= l; dy /= l; dz /= l
        bolts.add(Bolt(sx, sy, sz, dx * 18f, dy * 18f, dz * 18f, 2f))
        burst(sx, sy, sz, 0x7FE8FF, 8, 3f)
    }

    private fun attack() {
        val item = cur()
        if (atkCd > 0f) return
        atkCd = Items.cooldown(item); swing = 0f; if (!thirdPerson) bodyYaw = yaw; swingDur = Items.swingTime(item); powerSwing = false
        if (item == Items.SWORD) { combo = if (comboT > 0f) (combo + 1) % 3 else 0; comboT = 1.1f }   // combo: corte diagonal -> corte horizontal -> estocada
        updateCamera()
        if (item == Items.STAFF) { shoot(); return }
        if (hitT > 0f) { hitT = -1f; strikeHit(hitItem, hitStab) }   // golpe anterior ainda pendente: resolve antes de começar o novo
        // o dano/quebra só acontece quando a ferramenta chega no alvo (momento do impacto da animação)
        hitItem = item; hitStab = item == Items.SWORD && combo == 2
        val frac = when (item) { Items.SWORD -> when (combo) { 0 -> 0.58f; 1 -> 0.53f; else -> 0.5f }; Items.AXE -> 0.55f; Items.PICK -> 0.54f; else -> 0.5f }
        hitT = swingDur * frac
    }

    /** aplica o golpe normal (slime à frente ou bloco mirado) no instante do impacto */
    private fun strikeHit(item: Int, stab: Boolean) {
        updateCamera()
        val reach = Items.reach(item) + (if (stab) 0.8f else 0f)
        var best: Slime? = null; var bd = 9999f
        for (s in slimes) {
            if (s.dead) continue
            val dx = s.x - player.x; val dy = s.y + 0.4f - (player.y + 1f); val dz = s.z - player.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist > reach) continue
            val dot = (dx * sin(yaw) + dz * cos(yaw)) / max(0.001f, sqrt(dx * dx + dz * dz))
            if (dot > 0.5f && dist < bd) { bd = dist; best = s }
        }
        if (best != null) { hitSlime(best, Items.damage(item) + (if (stab) 1 else 0), best.x - player.x, best.z - player.z, Items.knock(item) * (if (stab) 1.4f else 1f)); return }
        if (!Items.breaks(item) && item != Items.SWORD) return
        blockHit(item, 1f)
    }

    private fun hardness(id: Int) = when (id) { B.LEAVES -> 1.5f; B.GRASS, B.DIRT, B.SAND -> 3f; B.WOOD -> 8f; B.PLANK -> 6f; B.STONE -> 10f; B.BRICK -> 9f; else -> if (id > 13 && B.hard[id] > 0f) B.hard[id] else 4f }
    private fun efficiency(item: Int, id: Int) = when (item) {
        Items.PICK -> when (id) { B.STONE, B.BRICK -> 2f; B.DIRT, B.GRASS, B.SAND -> 1.5f; else -> if (id > 13 && (B.mat[id] == Blk.M_STONE || B.mat[id] == Blk.M_METAL)) 2f else if (id > 13 && B.mat[id] == Blk.M_SOFT) 1.5f else 1f }
        Items.AXE -> when (id) { B.WOOD, B.PLANK -> 2f; B.LEAVES -> 1.5f; else -> if (id > 13 && B.leafy(id)) 1.5f else if (id > 13 && B.mat[id] == Blk.M_WOOD) 2f else 0.6f }
        else -> 0.7f
    }

    /** cada batida deixa uma marca exatamente onde a ferramenta acertou e avança a quebra; mult 2 = golpe carregado */
    private fun blockHit(item: Int, mult: Float) {
        raycast(camX, camY, camZ, dirX(), dirY(), dirZ(), camDist, camDist + 5.5f)
        if (!hasHit || hy <= 0) return
        val id = world.get(hx, hy, hz)
        if (world.isLocked(hx, hy, hz)) { hitPulse = 0.3f; return }   // bloco de apresentação do mundo de testes: não quebra
        // ponto exato do impacto: refina a entrada do raio no bloco
        val dx = dirX(); val dy = dirY(); val dz = dirZ()
        var lo = max(camDist, rT - 0.05f); var hi = rT
        for (i in 0 until 7) { val m = (lo + hi) * 0.5f; if (world.solid(floor(camX + dx * m).toInt(), floor(camY + dy * m).toInt(), floor(camZ + dz * m).toInt())) hi = m else lo = m }
        val qx = camX + dx * hi - (hx + 0.5f); val qy = camY + dy * hi - (hy + 0.5f); val qz = camZ + dz * hi - (hz + 0.5f)
        val ax = abs(qx); val ay = abs(qy); val az = abs(qz)
        val a = if (ax >= ay && ax >= az) 0 else if (ay >= az) 1 else 2
        val s = if ((if (a == 0) qx else if (a == 1) qy else qz) >= 0f) 1 else -1
        // coordenadas na face: a=0 -> (z,y); a=1 -> (x,z); a=2 -> (x,y)
        val fu = (if (a == 0) qz else qx) + 0.5f; val fv = (if (a == 1) qz else qy) + 0.5f

        if (item == Items.SWORD) {   // espada não destrói nem esculpe: deixa só uma marca de corte temporária
            if (cutFoliage(hx, hy, hz, id, hx + 0.5f + qx, hy + 0.5f + qy, hz + 0.5f + qz)) return   // folhas são cortadas de verdade
            val sd = Dmg(hx, hy, hz, id); sd.prog = 0.7f
            val sm = makeMark(a, s, fu, fv, item, false, hitStab); sm.blobs = FloatArray(0)
            slashes.add(Slash(sd, sm, time))
            while (slashes.size > 48) slashes.removeAt(0)
            hitPulse = 0.5f; shake = max(shake, 0.04f)
            val px = hx + 0.5f + qx; val py = hy + 0.5f + qy; val pz = hz + 0.5f + qz
            val nx = if (a == 0) s.toFloat() else 0f; val ny = if (a == 1) s.toFloat() else 0f; val nz = if (a == 2) s.toFloat() else 0f
            for (i in 0 until 4) parts.add(Particle(px, py, pz, nx * 2f + (rnd.nextFloat() - 0.5f) * 2.5f, ny * 2f + rnd.nextFloat() * 2f + 0.6f, nz * 2f + (rnd.nextFloat() - 0.5f) * 2.5f, 0xCFF6FF, 0.03f, 0.3f + rnd.nextFloat() * 0.2f))
            return
        }
        val key = ((hy.toLong() * World.SZ + hz) * World.SX + hx)
        val d = dmg.getOrPut(key) { Dmg(hx, hy, hz, id) }
        d.idle = 0f; brX = hx; brY = hy; brZ = hz
        d.prog += if (creative) 1f else efficiency(item, id) * mult / hardness(id)
        if (item == Items.SWORD && !creative) d.prog = min(d.prog, 0.5f)   // espada marca o bloco, mas não chega a quebrar
        hitPulse = 1f
        if (d.prog >= 1f) { dmg.remove(key); d.dead = true; breakBlock(hx, hy, hz, id); return }
        if (B.log(id) && !d.felled && d.prog >= 0.65f) {
            d.felled = true
            val cy = if (d.cutY.isNaN()) 0f else d.cutY
            if (fellTree(hx, hy, hz, cy)) trimStump(d, cy)   // a árvore tomba pela fenda: o toco fica cortado na altura do entalhe
        }   // tronco quase destruído: a parte de cima já tomba
        if (d.marks.size >= 7) d.marks.removeAt(0)
        val mk = makeMark(a, s, fu, fv, item, mult > 1.5f, hitStab)
        d.marks.add(mk)
        carve(d, a, s, qx, qy, qz, mk, item, mult > 1.5f)
        hitChips(hx + 0.5f + qx, hy + 0.5f + qy, hz + 0.5f + qz, a, s, id, item, mult > 1.5f)
        shake = max(shake, 0.06f * mult)
    }

    // ---------- marcas de impacto ----------
    private fun crack(m: Mark, x0: Float, y0: Float, ang0: Float, len: Float, t0: Float, t1: Float, w0: Float, depth: Int, jit: Float = 0.9f) {
        if (m.segs.size > 46) return
        val n = max(2, (len / 0.06f).toInt())
        var x = x0; var y = y0; var an = ang0
        for (i in 0 until n) {
            an += (rnd.nextFloat() - 0.5f) * jit
            val st = len / n * (0.8f + rnd.nextFloat() * 0.4f)
            var nx = x + cos(an) * st; var ny = y + sin(an) * st
            var stop = false
            if (nx < 0.012f || nx > 0.988f || ny < 0.012f || ny > 0.988f) { nx = nx.coerceIn(0.012f, 0.988f); ny = ny.coerceIn(0.012f, 0.988f); stop = true }
            val f0 = i.toFloat() / n; val f1 = (i + 1f) / n
            m.segs.add(Seg(x, y, nx, ny, w0 * (1f - 0.65f * f0), t0 + (t1 - t0) * f0, t0 + (t1 - t0) * f1))
            if (stop) return
            if (depth < 2 && i >= 1 && i < n - 1 && rnd.nextFloat() < 0.26f)
                crack(m, nx, ny, an + (if (rnd.nextBoolean()) 1f else -1f) * (0.55f + rnd.nextFloat() * 0.6f), len * (0.3f + rnd.nextFloat() * 0.25f),
                    t0 + (t1 - t0) * f1, min(1f, t0 + (t1 - t0) * f1 + 0.4f), w0 * 0.62f, depth + 1, jit)
            x = nx; y = ny
        }
    }

    private fun makeMark(a: Int, s: Int, u0: Float, v0: Float, item: Int, big: Boolean, stab: Boolean): Mark {
        val kind = when (item) { Items.PICK -> 1; Items.AXE -> 0; Items.SWORD -> if (stab) 3 else 2; else -> 4 }
        var ang = when (item) {
            Items.AXE -> (if (rnd.nextBoolean()) 0.8f else 2.3f) + (rnd.nextFloat() - 0.5f) * 0.5f
            Items.SWORD -> (if (combo == 0) 0.75f else 3.1f) + (rnd.nextFloat() - 0.5f) * 0.3f
            else -> rnd.nextFloat() * 6.283f
        }
        val len = when (kind) { 0 -> if (big) 0.44f else 0.32f; 2 -> if (big) 0.6f else 0.5f; 1 -> 0.2f; else -> 0.12f }
        val c = cos(ang); val sn = sin(ang)
        val mx = if (kind == 0 || kind == 2) abs(c) * len * 0.5f + 0.04f else len * 0.5f + 0.02f
        val my = if (kind == 0 || kind == 2) abs(sn) * len * 0.5f + 0.04f else len * 0.5f + 0.02f
        val u = u0.coerceIn(min(mx, 0.5f), max(0.5f, 1f - mx)); val v = v0.coerceIn(min(my, 0.5f), max(0.5f, 1f - my))
        val m = Mark(a, s, u, v, kind, ang, len, time)
        val big2 = if (big) 1.25f else 1f
        when (kind) {
            0 -> {
                for (e in 0 until 2) { val sg = if (e == 0) 1f else -1f
                    crack(m, u + c * len * 0.5f * sg, v + sn * len * 0.5f * sg, ang + (if (e == 0) 0f else 3.1416f) + (rnd.nextFloat() - 0.5f) * 0.7f,
                        (0.17f + rnd.nextFloat() * 0.13f) * big2, rnd.nextFloat() * 0.1f, 0.6f + rnd.nextFloat() * 0.4f, 0.02f, 0, 0.6f) }
                for (e in 0 until 2) crack(m, u, v, ang + (if (e == 0) 1f else -1f) * (1.2f + rnd.nextFloat() * 0.6f), (0.14f + rnd.nextFloat() * 0.14f) * big2, 0.15f + rnd.nextFloat() * 0.2f, 0.8f, 0.016f, 1)
                for (i in 0 until 5) {
                    val al = (rnd.nextFloat() - 0.5f) * len; val pp = (rnd.nextFloat() - 0.5f) * 0.14f
                    m.chips = m.chips + floatArrayOf(c * al - sn * pp, sn * al + c * pp, rnd.nextFloat() * 3.14f, 0.022f + rnd.nextFloat() * 0.028f)
                }
            }
            1 -> {
                val n = 7 + rnd.nextInt(3)
                for (i in 0 until n) { val an = i * 6.2832f / n + rnd.nextFloat() * 0.5f
                    crack(m, u + cos(an) * 0.05f, v + sin(an) * 0.05f, an, (0.2f + rnd.nextFloat() * 0.3f) * big2, rnd.nextFloat() * 0.2f, 0.55f + rnd.nextFloat() * 0.45f, 0.026f, 0, 0.55f) }
                for (i in 0 until 8) { val an = i * 0.7854f + rnd.nextFloat() * 0.4f; val r = 0.085f + rnd.nextFloat() * 0.05f
                    m.chips = m.chips + floatArrayOf(cos(an) * r, sin(an) * r, rnd.nextFloat() * 3.14f, 0.026f + rnd.nextFloat() * 0.03f) }
            }
            2 -> {
                for (e in 0 until 2) { val sg = if (e == 0) 1f else -1f
                    crack(m, u + c * len * 0.5f * sg, v + sn * len * 0.5f * sg, ang + (if (e == 0) 0f else 3.1416f) + (rnd.nextFloat() - 0.5f) * 0.4f,
                        (0.1f + rnd.nextFloat() * 0.12f) * big2, rnd.nextFloat() * 0.15f, 0.55f + rnd.nextFloat() * 0.4f, 0.012f, 0, 0.5f) }
            }
            3 -> {
                for (i in 0 until 5) { val an = i * 1.2566f + rnd.nextFloat() * 0.6f
                    crack(m, u + cos(an) * 0.03f, v + sin(an) * 0.03f, an, 0.1f + rnd.nextFloat() * 0.12f, rnd.nextFloat() * 0.15f, 0.5f + rnd.nextFloat() * 0.4f, 0.013f, 0, 0.5f) }
                for (i in 0 until 4) { val an = rnd.nextFloat() * 6.28f; m.chips = m.chips + floatArrayOf(cos(an) * 0.05f, sin(an) * 0.05f, rnd.nextFloat() * 3.14f, 0.016f + rnd.nextFloat() * 0.016f) }
            }
            else -> {
                for (i in 0 until 4) { val an = i * 1.5708f + rnd.nextFloat() * 0.8f
                    crack(m, u + cos(an) * 0.04f, v + sin(an) * 0.04f, an, 0.08f + rnd.nextFloat() * 0.08f, 0f, 0.7f, 0.012f, 0, 0.5f) }
            }
        }
        m.gr = when (kind) { 0 -> 0.09f; 1 -> 0.12f; 2 -> 0.05f; 3 -> 0.04f; else -> 0.07f } * (if (big) 1.3f else 1f)
        val nb = if (kind == 2) 3 else 6
        val bl = FloatArray(nb * 4)
        for (i in 0 until nb) {
            val al = if (kind == 0 || kind == 2) (rnd.nextFloat() - 0.5f) * len * 0.8f else 0f
            val rr = rnd.nextFloat() * m.gr * 0.7f; val an = rnd.nextFloat() * 6.283f
            bl[i * 4] = c * al + cos(an) * rr; bl[i * 4 + 1] = sn * al + sin(an) * rr; bl[i * 4 + 2] = rnd.nextFloat() * 3.14f; bl[i * 4 + 3] = m.gr * (0.55f + rnd.nextFloat() * 0.6f)
        }
        m.blobs = bl
        return m
    }


    private fun carvable(id: Int) = Items.isBlock(id) && !B.leafy(id) && id != B.WATER && !B.light(id) && !B.glass(id)

    /** arranca de verdade pedaços do bloco (mini-voxels) na área do golpe; o formato depende da ferramenta e do progresso */
    private fun carve(d: Dmg, a: Int, s: Int, qx: Float, qy: Float, qz: Float, m: Mark, item: Int, big: Boolean) {
        if (!carvable(d.id)) return
        var v = d.vox
        if (v == null) { v = BooleanArray(VN * VN * VN) { true }; d.vox = v }
        val t1 = if (a == 0) 2 else 0; val t2 = if (a == 1) 2 else 1
        val ca = cos(m.ang); val sa = sin(m.ang)
        val pr = d.prog.coerceIn(0f, 1f)
        val hw = max(0.5f, 2f - 1.6f * pr) * (VN / 8f)                       // núcleo protegido: encolhe conforme o bloco vai cedendo
        val kb = if (big) 1.3f else 1f; val kd = 1f + 0.9f * pr
        val half = (VN - 1) / 2f
        fun prot(i: Int, j: Int, k: Int) = abs(i - half) <= hw && abs(j - half) <= hw && abs(k - half) <= hw
        val trunk = B.log(d.id) && a != 1
        if (trunk && d.cutY.isNaN()) d.cutY = (if (a == 0) qy else qy).coerceIn(-0.25f, 0.25f)   // altura do entalhe de corte, fixada no 1º golpe
        val rm = BooleanArray(v.size); var any = false
        for (k in 0 until VN) for (j in 0 until VN) for (i in 0 until VN) {
            val idx = (k * VN + j) * VN + i
            if (!v[idx] || prot(i, j, k)) continue
            val rx = (i + 0.5f) / VN - 0.5f - qx; val ry = (j + 0.5f) / VN - 0.5f - qy; val rz = (k + 0.5f) / VN - 0.5f - qz
            val ra = if (a == 0) rx else if (a == 1) ry else rz
            val r1 = if (t1 == 0) rx else if (t1 == 1) ry else rz; val r2 = if (t2 == 0) rx else if (t2 == 1) ry else rz
            val dd = -s * ra
            val t = r1 * ca + r2 * sa; val w = -r1 * sa + r2 * ca
            val jit = 0.85f + rnd.nextFloat() * 0.3f
            val hit = when (item) {
                Items.AXE -> abs(t) <= m.len * 0.5f * jit && dd >= -0.05f && dd <= 0.27f * kb * kd && abs(w) <= 0.1f * kb * (1f - dd / (0.4f * kb * kd)) * jit
                Items.PICK -> { val e = dd - 0.04f; val rr = 0.17f * kb * (1f + 0.4f * pr) * jit
                    (t * t + w * w + e * e <= rr * rr) || (t * t + w * w <= 0.0049f * jit && dd <= 0.34f * kd) }
                Items.SWORD -> if (m.kind == 3) (t * t + w * w <= 0.003f && dd >= -0.05f && dd <= 0.27f * kd)
                    else (abs(t) <= m.len * 0.5f * jit && abs(w) <= 0.06f && dd >= -0.05f && dd <= 0.15f * kd)
                else -> { val rr = 0.13f * jit; t * t + w * w + dd * dd <= rr * rr }
            }
            var h2 = hit
            if (!h2 && trunk) {   // entalhe em cunha atravessando a face inteira, aprofunda a cada golpe
                val dmax = 0.16f + 0.6f * pr
                val ryy = (j + 0.5f) / VN - 0.5f - d.cutY
                val hv = (0.05f + 0.07f * pr) * (1f - dd / dmax) + (rnd.nextFloat() - 0.5f) * 0.03f
                h2 = dd >= -0.05f && dd <= dmax && abs(ryy) <= hv
            }
            if (h2) { rm[idx] = true; any = true }
        }
        val crk = carveCracks(d, v, pr, ::prot)
        if (!any && !crk) return
        // lascas extras: bordas vizinhas do buraco também se soltam
        val extra = BooleanArray(v.size)
        for (k in 0 until VN) for (j in 0 until VN) for (i in 0 until VN) {
            if (!rm[(k * VN + j) * VN + i]) continue
            for (f in 0 until 6) {
                val ni = i + (if (f == 0) 1 else if (f == 1) -1 else 0); val nj = j + (if (f == 2) 1 else if (f == 3) -1 else 0); val nk = k + (if (f == 4) 1 else if (f == 5) -1 else 0)
                if (ni !in 0 until VN || nj !in 0 until VN || nk !in 0 until VN) continue
                val ni2 = (nk * VN + nj) * VN + ni
                if (v[ni2] && !rm[ni2] && !prot(ni, nj, nk) && rnd.nextFloat() < 0.3f) extra[ni2] = true
            }
        }
        for (i in v.indices) if (rm[i] || extra[i]) v[i] = false
        d.vdirty = true
        if (!d.carved) { d.carved = true; world.hidden.add((d.y * World.SZ + d.z) * World.SX + d.x); world.set(d.x, d.y, d.z, d.id) }
    }


    /** as rachaduras viram sulcos de verdade: cada trecho visível é escavado na superfície da face onde o golpe bateu */
    private fun carveCracks(d: Dmg, v: BooleanArray, pr: Float, prot: (Int, Int, Int) -> Boolean): Boolean {
        var any = false
        val tg = (0.3f + 0.85f * pr).coerceAtMost(1f)
        for (m in d.marks) for (sg in m.segs) {
            val r = ((tg - sg.t0) / (sg.t1 - sg.t0)).coerceIn(0f, 1f); if (r <= 0f) continue
            val x2 = sg.x1 + (sg.x2 - sg.x1) * r; val y2 = sg.y1 + (sg.y2 - sg.y1) * r
            val steps = max(1, (hypot(x2 - sg.x1, y2 - sg.y1) / 0.03f).toInt())
            val layers = if (sg.w > 0.02f) 3 else 2
            val io = if (m.s > 0) VN - 1 else 0
            for (st in 0..steps) {
                val f = st / steps.toFloat()
                val i1 = ((sg.x1 + (x2 - sg.x1) * f) * VN).toInt().coerceIn(0, VN - 1); val i2 = ((sg.y1 + (y2 - sg.y1) * f) * VN).toInt().coerceIn(0, VN - 1)
                for (l in 0 until layers) {
                    val ia = io - m.s * l
                    val x: Int; val y: Int; val z: Int
                    when (m.a) { 0 -> { x = ia; z = i1; y = i2 } 1 -> { x = i1; y = ia; z = i2 } else -> { x = i1; y = i2; z = ia } }
                    if (prot(x, y, z)) continue
                    val idx = (z * VN + y) * VN + x
                    if (v[idx]) { v[idx] = false; any = true }
                }
            }
        }
        return any
    }

    /** lascas e faíscas saindo do ponto exato do impacto, de acordo com a ferramenta */
    private fun hitChips(x: Float, y: Float, z: Float, a: Int, s: Int, id: Int, item: Int, big: Boolean) {
        val nx = if (a == 0) s.toFloat() else 0f; val ny = if (a == 1) s.toFloat() else 0f; val nz = if (a == 2) s.toFloat() else 0f
        val base = B.top[id]
        val n = (if (item == Items.PICK) 6 else if (item == Items.AXE) 5 else 3) + (if (big) 4 else 0)
        for (i in 0 until n) {
            val k = 0.85f + rnd.nextFloat() * 0.4f
            val r = ((base shr 16 and 255) * k).toInt().coerceIn(0, 255); val g = ((base shr 8 and 255) * k).toInt().coerceIn(0, 255); val b = ((base and 255) * k).toInt().coerceIn(0, 255)
            val sp = 1.2f + rnd.nextFloat() * 1.6f
            parts.add(Particle(x + nx * 0.03f, y + ny * 0.03f, z + nz * 0.03f, nx * sp + (rnd.nextFloat() - 0.5f) * 1.6f, ny * sp + rnd.nextFloat() * 1.5f + 0.6f, nz * sp + (rnd.nextFloat() - 0.5f) * 1.6f,
                (r shl 16) or (g shl 8) or b, 0.03f + rnd.nextFloat() * (if (item == Items.AXE) 0.06f else 0.04f), 0.45f + rnd.nextFloat() * 0.4f))
        }
        val spark = when { item == Items.SWORD -> 0xCFF6FF; item == Items.PICK && (id == B.STONE || id == B.BRICK) -> 0xFFE7A0; else -> 0 }
        if (spark != 0) for (i in 0 until 3) parts.add(Particle(x, y, z, nx * 2.5f + (rnd.nextFloat() - 0.5f) * 2.5f, ny * 2.5f + rnd.nextFloat() * 2f + 1f, nz * 2.5f + (rnd.nextFloat() - 0.5f) * 2.5f, spark, 0.035f, 0.3f + rnd.nextFloat() * 0.2f))
    }

    // ---------- quebra: mini-blocos e árvore caindo ----------
    private fun spawnPieces(cx: Float, cy: Float, cz: Float, id: Int, n: Int, size: Float, spread: Float, vx0: Float = 0f, vy0: Float = 0f, vz0: Float = 0f, life: Float = 3.2f) {
        if (!Items.isBlock(id)) return
        for (i in 0 until n) {
            if (debris.size > 220) debris.removeAt(0)
            val ox = (rnd.nextFloat() - 0.5f) * spread; val oy = (rnd.nextFloat() - 0.5f) * spread; val oz = (rnd.nextFloat() - 0.5f) * spread
            var yy = cy + oy; var k = 0
            while (world.solid(floor(cx + ox).toInt(), floor(yy).toInt(), floor(cz + oz).toInt()) && k < 6) { yy += 0.5f; k++ }
            val q = Debris(cx + ox, yy, cz + oz, ox * 5f + vx0 + (rnd.nextFloat() - 0.5f) * 2f, 2.5f + rnd.nextFloat() * 3.5f + vy0 * 0.5f, oz * 5f + vz0 + (rnd.nextFloat() - 0.5f) * 2f,
                id, size * (0.85f + rnd.nextFloat() * 0.3f), life + rnd.nextFloat() * 1.5f)
            q.vrx = (rnd.nextFloat() - 0.5f) * 500f; q.vry = (rnd.nextFloat() - 0.5f) * 500f; q.vrz = (rnd.nextFloat() - 0.5f) * 500f
            q.rx = rnd.nextFloat() * 360f; q.ry = rnd.nextFloat() * 360f
            debris.add(q)
        }
    }

    private fun breakBlock(x: Int, y: Int, z: Int, id: Int) {
        world.hidden.remove((y * World.SZ + z) * World.SX + x)
        world.set(x, y, z, B.AIR)
        waterTouch(x, y, z)
        breakFx(x, y, z, id, true)
        val di = if (id == B.GRASS) B.DIRT else if (B.leafy(id)) (if (rnd.nextFloat() < 0.3f) id else 0) else id
        if (di != 0) dropItem(x + 0.5f, y + 0.4f, z + 0.5f, di, 0f, 0f)
        if (B.log(id)) fellTree(x, y, z)
    }

    /** o bloco que sobra no chão: só 1, pequeno, pra pegar */
    private fun dropItem(x: Float, y: Float, z: Float, id: Int, vx: Float, vz: Float) {
        if (!Items.isBlock(id)) return
        if (drops.size > 60) drops.removeAt(0)
        var yy = y; var k = 0
        while (world.solid(floor(x).toInt(), floor(yy).toInt(), floor(z).toInt()) && k < 6) { yy += 0.5f; k++ }
        drops.add(Drop(x, yy, z, vx + (rnd.nextFloat() - 0.5f) * 1.4f, 3.4f, vz + (rnd.nextFloat() - 0.5f) * 1.4f, id))
    }

    private val leafCols = intArrayOf(0x4FA52E, 0x6CBF3C, 0x8AD453, 0x3E8A25, 0xA3DF6A, 0xC4E070, 0x7FC84A)
    /** cores das folhinhas de cada tipo de folha (as 4 cores da textura); o carvalho usa leafCols */
    private fun leafPal(id: Int): IntArray {
        val sp = Blk.specs[B.tSide[id]]
        if (sp != null && sp[0] == Blk.K_LEAF) return intArrayOf(sp[1], sp[2], sp[3], sp[4])
        val pc = Leaf3D.colorsOf(B.tSide[id])   // cores da própria espécie (paleta da folha 3D)
        return if (pc != null) intArrayOf(pc[1], pc[2], pc[3], pc[4], pc[2], pc[3]) else leafCols
    }
    private fun leafShape(id: Int): Int = max(0, Leaf3D.kindOf(B.tSide[id]))
    /** a espada corta o bloco: ele vira, na hora, uma nuvem de folhinhas que escorrem no sentido do corte, girando e planando */
    private fun leafCutFx(x: Int, y: Int, z: Int, k: Int, bid: Int = B.LEAVES) {
        val pal = leafPal(bid)
        val cx = x + 0.5f; val cy = y + 0.5f; val cz = z + 0.5f
        val fx = fwdX(); val fz = fwdZ(); val hl = max(0.001f, hypot(fx, fz)); val fwx = fx / hl; val fwz = fz / hl
        val rxv = fwz; val rzv = -fwx; val sg = if (combo == 1) -1f else 1f   // direção lateral do talho
        val n = if (k == 0) 18 else 10
        if (leafFall.size < 260) for (i in 0 until n) {
            val ox = (rnd.nextFloat() - 0.5f) * 0.95f; val oy = (rnd.nextFloat() - 0.5f) * 0.95f; val oz = (rnd.nextFloat() - 0.5f) * 0.95f
            val along = (ox * rxv + oz * rzv) * sg    // posição ao longo do corte: quem está na frente voa mais alto (arco)
            val s = 1.5f + rnd.nextFloat() * 2.6f
            val l = LeafP(cx + ox, cy + oy, cz + oz,
                rxv * sg * s + fwx * (0.4f + rnd.nextFloat() * 1.5f) + (rnd.nextFloat() - 0.5f) * 0.8f,
                rzv * sg * s + fwz * (0.4f + rnd.nextFloat() * 1.5f) + (rnd.nextFloat() - 0.5f) * 0.8f,
                rnd.nextFloat() * 6.28f, pal[rnd.nextInt(pal.size)], 0.07f + rnd.nextFloat() * 0.07f, 3.4f + rnd.nextFloat() * 2.2f)
            l.shape = leafShape(bid); l.vy = 0.5f + rnd.nextFloat() * 2.0f + along * 1.6f; l.burst = 0.6f + rnd.nextFloat() * 0.5f; l.spin = 1.4f + rnd.nextFloat() * 2.4f
            leafFall.add(l)
        }
        if (parts.size < 540) {
            for (i in 0 until 8) parts.add(Particle(cx + (rnd.nextFloat() - 0.5f) * 0.9f, cy + (rnd.nextFloat() - 0.5f) * 0.9f, cz + (rnd.nextFloat() - 0.5f) * 0.9f,
                rxv * sg * (1f + rnd.nextFloat() * 2.5f) + (rnd.nextFloat() - 0.5f), 0.5f + rnd.nextFloat() * 1.8f, rzv * sg * (1f + rnd.nextFloat() * 2.5f) + (rnd.nextFloat() - 0.5f),
                pal[rnd.nextInt(pal.size)], 0.03f + rnd.nextFloat() * 0.035f, 0.5f + rnd.nextFloat() * 0.5f).also { it.grav = 5f })
            for (i in 0 until 3) parts.add(Particle(cx + (rnd.nextFloat() - 0.5f) * 0.9f, cy + (rnd.nextFloat() - 0.5f) * 0.9f, cz + (rnd.nextFloat() - 0.5f) * 0.9f,
                rxv * sg * 0.8f, 0.4f + rnd.nextFloat() * 1.0f, rzv * sg * 0.8f, if (rnd.nextBoolean()) 0xF4FFB8 else 0xFFFFFF, 0.022f, 0.6f + rnd.nextFloat() * 0.5f).also { it.grav = 0.4f })
        }
        if (k == 0 && rnd.nextFloat() < 0.12f) dropItem(cx, cy, cz, bid, 0f, 0f)
    }

    private fun tuftFx(x: Float, y: Float, z: Float, kind: Int) {
        val cols = if (kind == 2) intArrayOf(0xFFD84A, 0xFF8FC8, 0xFFFFFF, 0x66C84C) else intArrayOf(0x66C84C, 0x4FA52E, 0x8AD453, 0x3E8A25)
        for (i in 0 until 8) parts.add(Particle(x + (rnd.nextFloat() - 0.5f) * 0.4f, y + 0.1f + rnd.nextFloat() * 0.3f, z + (rnd.nextFloat() - 0.5f) * 0.4f,
            (rnd.nextFloat() - 0.5f) * 3f, 1.5f + rnd.nextFloat() * 2f, (rnd.nextFloat() - 0.5f) * 3f, cols[rnd.nextInt(cols.size)], 0.035f + rnd.nextFloat() * 0.04f, 0.6f + rnd.nextFloat() * 0.5f))
        if (leafFall.size < 140) for (i in 0 until 2) leafFall.add(LeafP(x, y + 0.3f, z, (rnd.nextFloat() - 0.5f) * 1.2f, (rnd.nextFloat() - 0.5f) * 1.2f, rnd.nextFloat() * 6.28f, cols[rnd.nextInt(cols.size)], 0.08f + rnd.nextFloat() * 0.04f, 4f))
    }

    /** a espada corta folhas (a atingida e as coladas nela) e matinhos/flores perto do golpe. true = cortou folhas */
    private fun cutFoliage(bx: Int, by: Int, bz: Int, id: Int, cx: Float, cy: Float, cz: Float): Boolean {
        var leaves = false; var tufts = 0
        if (B.leafy(id)) {
            leaves = true
            val offs = if (hitStab) arrayOf(intArrayOf(0, 0, 0)) else arrayOf(intArrayOf(0, 0, 0), intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0), intArrayOf(0, 1, 0), intArrayOf(0, -1, 0), intArrayOf(0, 0, 1), intArrayOf(0, 0, -1))
            for (o in offs) {
                val x = bx + o[0]; val y = by + o[1]; val z = bz + o[2]
                val nb = world.get(x, y, z); if (!B.leafy(nb) || world.isLocked(x, y, z)) continue
                world.set(x, y, z, B.AIR); leafCutFx(x, y, z, if (o[0] == 0 && o[1] == 0 && o[2] == 0) 0 else 1, nb)
            }
        }
        val gx0 = floor(cx - 1.4f).toInt(); val gx1 = floor(cx + 1.4f).toInt(); val gz0 = floor(cz - 1.4f).toInt(); val gz1 = floor(cz + 1.4f).toInt()
        for (gx in gx0..gx1) for (gz in gz0..gz1) for (gy in by - 1..by + 1) {
            val kind = world.tuftKind(gx, gy, gz)
            if (kind == 0) continue
            if (hypot(gx + 0.5f - cx, gz + 0.5f - cz) > 1.4f) continue
            world.cutTuft(gx, gy, gz, time); tuftFx(gx + 0.5f, gy + 1f, gz + 0.5f, kind); tufts++
        }
        if (leaves || tufts > 0) { hitPulse = 0.5f; shake = max(shake, 0.03f) }
        return leaves
    }

    /** corta o tronco: tudo de madeira/folha conectado acima do corte vira uma árvore que tomba */
    private fun fellTree(x: Int, y: Int, z: Int, cutY: Float = Float.NaN): Boolean {
        val s0 = world.get(x, y + 1, z)
        if ((!B.log(s0) && !B.leafy(s0)) || world.isLocked(x, y + 1, z)) return false
        val seen = HashSet<Int>(); val q = ArrayDeque<Int>(); val out = ArrayList<Int>()
        fun enc(a: Int, b: Int, c: Int) = ((b * World.SZ + c) * World.SX + a)
        seen.add(enc(x, y + 1, z)); q.add(enc(x, y + 1, z))
        while (q.isNotEmpty() && out.size < 4 * 800) {
            val e = q.removeFirst(); val ex = e % World.SX; val ez = (e / World.SX) % World.SZ; val ey = e / (World.SX * World.SZ)
            out.add(ex - x); out.add(ey - y); out.add(ez - z); out.add(world.get(ex, ey, ez))
            for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                val nx = ex + dx; val ny = ey + dy; val nz = ez + dz
                if (ny <= y || ny >= World.SY || abs(nx - x) > 5 || abs(nz - z) > 5 || nx < 0 || nz < 0 || nx >= World.SX || nz >= World.SZ) continue
                val k = enc(nx, ny, nz); if (seen.contains(k)) continue
                val nid = world.get(nx, ny, nz)
                if ((!B.log(nid) && !B.leafy(nid)) || world.isLocked(nx, ny, nz)) continue
                seen.add(k); q.add(k)
            }
        }
        if (out.isEmpty()) return false
        val arr = out.toIntArray()
        val ftRs = IntArray(arr.size / 4 + 1)
        val dmesh = world.decorTree(arr, x, y, z, if (cutY.isNaN()) 0f else -0.5f, ftRs)
        for (i in 0 until arr.size / 4) world.set(x + arr[i * 4], y + arr[i * 4 + 1], z + arr[i * 4 + 2], B.AIR)
        var dx = x + 0.5f - player.x; var dz = z + 0.5f - player.z
        if (hypot(dx, dz) < 0.2f) { dx = sin(yaw); dz = cos(yaw) }
        val an = atan2(dz, dx) + (rnd.nextFloat() - 0.5f) * 0.4f
        val ft = if (cutY.isNaN()) FallTree(x + 0.5f, y + 0.5f, z + 0.5f, cos(an), sin(an), arr) else FallTree(x + 0.5f, y + 0.5f + cutY, z + 0.5f, cos(an), sin(an), arr, -0.5f)   // com corte: pivô no plano do corte, base da árvore assenta nele
        ft.dm = dmesh; System.arraycopy(ftRs, 0, ft.rs, 0, ftRs.size)
        ft.sx = x; ft.sy = y; ft.sz = z; trees.add(ft)
        shake = max(shake, 0.15f)
        return true
    }

    /** o toco perde tudo acima do corte (borda irregular) e solta serragem e lascas */
    private fun trimStump(d: Dmg, cutY: Float) {
        var v = d.vox
        if (v == null) { v = BooleanArray(VN * VN * VN) { true }; d.vox = v }
        for (k in 0 until VN) for (j in 0 until VN) for (i in 0 until VN) {
            val yl = (j + 0.5f) / VN - 0.5f
            if (yl > cutY + (rnd.nextFloat() - 0.5f) * 0.07f) v[(k * VN + j) * VN + i] = false
        }
        d.vdirty = true
        if (!d.carved) { d.carved = true; world.hidden.add((d.y * World.SZ + d.z) * World.SX + d.x); world.set(d.x, d.y, d.z, d.id) }
        burst(d.x + 0.5f, d.y + 0.5f + cutY, d.z + 0.5f, 0xD9B27A, 14, 3f)
        shake = max(shake, 0.3f)
    }

    /** posição (relativa ao pivô) de um ponto da árvore depois de girar f.ang em volta do eixo horizontal */
    private fun treeRot(f: FallTree, rx: Float, ry: Float, rz: Float, o: FloatArray) {
        val kx = f.kx; val kz = f.kz; val c = cos(f.ang); val s = sin(f.ang); val kd = kx * rx + kz * rz
        o[0] = rx * c - kz * ry * s + kx * kd * (1f - c)
        o[1] = ry * c + (kz * rx - kx * rz) * s
        o[2] = rz * c + kx * ry * s + kz * kd * (1f - c)
    }

    private val tmpV = FloatArray(3)

    /** a árvore encosta no chão quando o tronco entra num bloco sólido (o toco é ignorado).
     *  Folhas e galhos que tocam o chão antes disso se quebram e a árvore continua caindo até o tronco assentar. */
    private fun treeHitsGround(f: FallTree): Boolean {
        if (f.ang < 0.2f) return false
        var hit = false
        for (i in 0 until f.n) {
            if (f.gone[i]) continue
            treeRot(f, f.bl[i * 4].toFloat(), f.bl[i * 4 + 1] + f.yo, f.bl[i * 4 + 2].toFloat(), tmpV)
            val wx = floor(f.px + tmpV[0]).toInt(); val wy = floor(f.py - f.drop + tmpV[1] - 0.42f).toInt(); val wz = floor(f.pz + tmpV[2]).toInt()
            if (f.drop <= 0f && wx == f.sx && wy == f.sy && wz == f.sz) continue
            val id = world.get(wx, wy, wz)
            if (id == B.AIR || id == B.WATER || B.leafy(id) || B.log(id)) continue
            if (f.holds(i)) hit = true else smashPiece(f, i)
        }
        return hit
    }

    /** um bloco de folha (ou galho fino) bateu no chão: quebra em pedacinhos e some, sem segurar a árvore */
    private fun smashPiece(f: FallTree, i: Int) {
        f.gone[i] = true
        val cx = f.px + tmpV[0]; val cy = f.py - f.drop + tmpV[1]; val cz = f.pz + tmpV[2]
        val bid = f.bl[i * 4 + 3]; val pal = leafPal(bid)
        if (B.log(bid)) { burst(cx, cy, cz, B.top[bid], 4, 3f); burst(cx, cy, cz, B.side[bid], 2, 3f) }
        else {
            burst(cx, cy, cz, pal[rnd.nextInt(pal.size)], 4, 3f)
            if (leafFall.size < 140) leafFall.add(LeafP(cx, cy, cz, (rnd.nextFloat() - 0.5f) * 1.6f, (rnd.nextFloat() - 0.5f) * 1.6f, rnd.nextFloat() * 6.28f,
                pal[rnd.nextInt(pal.size)], 0.08f + rnd.nextFloat() * 0.05f, 5f))
        }
        shake = max(shake, 0.12f)
    }

    /** a árvore bateu no chão: baque, poeira e folhas voando; ela fica deitada um instante antes de se desfazer */
    private fun landTree(f: FallTree) {
        f.landed = true; f.lt = 0f; f.vel = 0f
        for (i in 0 until f.n step 3) {
            if (f.gone[i]) continue
            val id = f.bl[i * 4 + 3]
            treeRot(f, f.bl[i * 4].toFloat(), f.bl[i * 4 + 1] + f.yo, f.bl[i * 4 + 2].toFloat(), tmpV)
            val cx = f.px + tmpV[0]; val cy = f.py - f.drop + tmpV[1]; val cz = f.pz + tmpV[2]
            if (B.log(id)) burst(cx, cy - 0.3f, cz, 0xC9B28A, 2, 2f)
            else if (leafFall.size < 140) leafFall.add(LeafP(cx, cy, cz, (rnd.nextFloat() - 0.5f) * 1.6f, (rnd.nextFloat() - 0.5f) * 1.6f, rnd.nextFloat() * 6.28f,
                leafPal(id)[rnd.nextInt(4)], 0.09f, 5f))
        }
        shake = max(shake, 0.5f)
        val dist = hypot(player.x - f.px, player.z - f.pz); if (dist > 18f) shake = 0.1f
    }

    /** depois de deitada, a árvore vira partículas e só alguns troncos pra pegar */
    private fun shatterTree(f: FallTree) {
        var leaves = 0; var logs = 0
        for (i in 0 until f.n) {
            if (f.gone[i]) continue
            val id = f.bl[i * 4 + 3]
            treeRot(f, f.bl[i * 4].toFloat(), f.bl[i * 4 + 1] + f.yo, f.bl[i * 4 + 2].toFloat(), tmpV)
            val cx = f.px + tmpV[0]; val cy = f.py - f.drop + tmpV[1]; val cz = f.pz + tmpV[2]
            if (B.log(id)) {
                burst(cx, cy, cz, B.top[id], 4, 3f); burst(cx, cy, cz, B.side[id], 3, 3f)
                logs++; if (logs % 5 == 1 && logs <= 21) dropItem(cx, cy + 0.2f, cz, id, 0f, 0f)
            } else {
                leaves++
                if (leaves % 2 == 0) burst(cx, cy, cz, leafPal(id)[rnd.nextInt(4)], 3, 2.5f)
                if (leaves % 4 == 0 && leafFall.size < 140) leafFall.add(LeafP(cx, cy, cz, (rnd.nextFloat() - 0.5f) * 0.8f, (rnd.nextFloat() - 0.5f) * 0.8f, rnd.nextFloat() * 6.28f,
                    leafPal(id)[rnd.nextInt(4)], 0.08f + rnd.nextFloat() * 0.05f, 7f))
            }
        }
        shake = max(shake, 0.25f)
    }

    private fun updateTrees(dt: Float) {
        val it = trees.iterator()
        while (it.hasNext()) {
            val f = it.next(); f.t += dt
            if (f.landed) { f.lt += dt; if (f.lt >= 1.2f) { shatterTree(f); it.remove() }; continue }
            if (f.t < 0.45f) {   // estalo: a árvore treme antes de tombar
                f.ang = 0.03f + sin(f.t * 45f) * 0.012f
                if (rnd.nextFloat() < 0.35f && leafFall.size < 140) leafFall.add(LeafP(f.px + (rnd.nextFloat() - 0.5f) * 3f, f.py + 3f + rnd.nextFloat() * 3f, f.pz + (rnd.nextFloat() - 0.5f) * 3f, 0f, 0f, rnd.nextFloat() * 6f, 0x6CBF3C, 0.09f, 5f))
                continue
            }
            var done = false
            for (st in 0 until 4) {
                val h = dt / 4f
                if (f.dropping) {   // deitada no ar (estava num bloco alto): cai reta até tocar o chão
                    f.dvy = min(30f, f.dvy + 22f * h); f.drop += f.dvy * h
                    if (treeHitsGround(f)) { f.drop = max(0f, f.drop - f.dvy * h * 0.6f); done = true }
                    else if (f.drop > 40f) done = true
                } else {
                    f.vel += (5.2f * sin(f.ang) + 0.45f) * h; f.ang += f.vel * h
                    if (f.ang >= 1.5708f) { f.ang = 1.5708f; if (treeHitsGround(f)) done = true else { f.dropping = true; f.dvy = 0f } }
                    else if (treeHitsGround(f)) done = true
                }
                if (done) break
            }
            if (done) landTree(f)
        }
    }

    private fun releasePower(power: Float) {
        val item = cur()
        swing = 0f; swingDur = if (item == Items.AXE) 0.58f else 0.46f; powerSwing = true; if (!thirdPerson) bodyYaw = yaw
        atkCd = Items.cooldown(item) * 1.3f; comboT = 0f; combo = 0
        pendingPower = max(0.25f, power); pendingT = swingDur * (if (item == Items.AXE) 0.4f else 0.36f)
        updateCamera()
    }

    /** impacto do golpe poderoso: acerta todos os slimes à frente, treme a tela e levanta poeira */
    private fun powerHit(pw: Float) {
        val item = cur()
        val reach = Items.reach(item) + 0.7f + pw * 0.8f
        var anyHit = false
        for (s in slimes.toList()) {
            if (s.dead) continue
            val dx = s.x - player.x; val dy = s.y + 0.4f - (player.y + 1f); val dz = s.z - player.z
            if (sqrt(dx * dx + dy * dy + dz * dz) > reach) continue
            val dot = (dx * sin(yaw) + dz * cos(yaw)) / max(0.001f, sqrt(dx * dx + dz * dz))
            if (dot < 0.15f) continue
            anyHit = true
            hitSlime(s, Items.damage(item) + 1 + (pw * 4f).toInt(), dx, dz, Items.knock(item) * (1.3f + pw))
        }
        if (!anyHit && (Items.breaks(item) || item == Items.SWORD)) { updateCamera(); blockHit(item, 2f) }   // golpe carregado: 2x de progresso na quebra
        shake = max(shake, 0.35f + 0.5f * pw)
        val fx = (player.x + sin(yaw) * 2f); val fz = (player.z + cos(yaw) * 2f)
        val fy = world.surfaceY(fx.toInt().coerceIn(1, World.SX - 2), fz.toInt().coerceIn(1, World.SZ - 2)).toFloat()
        burst(fx, fy + 0.1f, fz, 0xF4F0D8, 10 + (pw * 14).toInt(), 3f + pw * 3f)
        burst(fx, fy + 0.2f, fz, 0x9BE36A, 6, 2.5f)
    }

    private fun place() {
        if (!Items.isBlock(cur())) return
        updateCamera()
        raycast(camX, camY, camZ, dirX(), dirY(), dirZ(), camDist, camDist + 5.5f)
        if (!hasHit) return
        val p = player
        val overlap = px + 1 > p.x - p.hw && px < p.x + p.hw && pz + 1 > p.z - p.hw && pz < p.z + p.hw &&
            py + 1 > p.y && py < p.y + p.h
        if (overlap) return
        val cid = cur()
        var ddir = -1
        if (Deco.isDeco(cid)) {   // móvel importado: direção fixa na hora de colocar e confere se o móvel inteiro cabe
            ddir = decoDirAt(px, py, pz)
            if (!Deco.fits(world, cid, px, py, pz, ddir, p.x, p.y, p.z, p.hw, p.h)) return
        }
        val ceil = world.solid(px, py + 1, pz) && !world.solid(px, py - 1, pz)
        world.set(px, py, pz, Blk.oriented(cid, BuildShapes.lookDir(yaw), ceil))   // telhado/escada viram pra onde você olha; laje sobe sob um teto
        if (ddir >= 0) world.setFState(px, py, pz, 64 or (ddir shl 4))   // senão a frente mudava sozinha quando um vizinho era colocado/quebrado
        swing = 0.3f
    }

    private fun spawnSlime() {
        val a = rnd.nextFloat() * 6.283f; val r = 14f + rnd.nextFloat() * 12f
        val x = (player.x + cos(a) * r).toInt(); val z = (player.z + sin(a) * r).toInt()
        if (x < 3 || z < 3 || x >= World.SX - 3 || z >= World.SZ - 3) return
        val y = world.surfaceY(x, z)
        if (y <= World.WATER_Y + 1 || world.get(x, y, z) != B.AIR) return
        slimes.add(Slime(x + 0.5f, y + 0.1f, z + 0.5f))
        burst(x + 0.5f, y + 0.5f, z + 0.5f, 0x6FD65A, 10, 3f)
    }

    private fun hurt(dx: Float, dz: Float) {
        if (hurtCd > 0f || creative) return
        hp--; hurtCd = 1f; hurtFlash = 1f; regen = 0f; shake = 0.5f
        val l = max(0.01f, sqrt(dx * dx + dz * dz))
        player.vx = dx / l * 7f; player.vz = dz / l * 7f; player.vy = 6f
        burst(player.x, player.y + 1f, player.z, 0xFF5050, 8, 3f)
    }

    @Volatile var paused = false

    fun update(dt: Float) {
        if (tvZoomT != tvZoomGoal) { val st = dt * 2.2f; tvZoomT = if (tvZoomT < tvZoomGoal) min(tvZoomGoal, tvZoomT + st) else max(tvZoomGoal, tvZoomT - st) }
        if (paused) return
    for (sl in slashes) if (time - sl.born > SLASH_LIFE || world.get(sl.d.x, sl.d.y, sl.d.z) != sl.d.id) slashes.remove(sl)
        time += dt
        var ddx: Float; var ddy: Float
        synchronized(this) { ddx = lookDx; ddy = lookDy; lookDx = 0f; lookDy = 0f }
        yaw -= ddx * 0.0045f * sens; pitch = (pitch - ddy * 0.0045f * sens).coerceIn(-1.45f, 1.45f)
        if (wantCam) { wantCam = false; if (!driving) thirdPerson = !thirdPerson }
        if (driving) {   // câmera de perseguição: gira atrás do carro sozinha (arrastar a tela ainda deixa olhar em volta)
            if (ddx == 0f) {
                var df = (if (skating) skateV.h else car.h) - yaw
                while (df > PI) df -= (2 * PI).toFloat(); while (df < -PI) df += (2 * PI).toFloat()
                yaw += df * min(1f, 2.4f * dt) * min(1f, max(0f, if (skating) skateV.speed else car.speed) / 4f)
            }
            if (ddy == 0f) pitch += (-0.2f - pitch) * min(1f, 1.4f * dt)
        }
        atkCd -= dt; comboT -= dt; hurtCd -= dt; shake = max(0f, shake - dt * 1.5f); hurtFlash = max(0f, hurtFlash - dt * 2f)
        if (swing < 1f) swing = min(1f, swing + dt / swingDur)
        if (deadTimer > 0f) { deadTimer -= dt; if (deadTimer <= 0f) respawn(); updateCamera(); return }

        waterStep(dt)
        val p = player
        scanTv(dt)
        scanFurn(dt)
        scanElev()
        if (!creative) flying = false
        if (flying && p.onGround && downHeld) flying = false
        if (launchReq) { launchReq = false; if (flying) flyLaunchFx(p) }
        flyLaunch = max(0f, flyLaunch - dt / 0.85f)
        // dash aéreo: segurar carrega, soltar dispara
        dashDirPitch = if (freeFly) Math.toDegrees(pitch.toDouble()).toFloat() else 0f
        if (!flying) { dashHeld = false; dashFire = false; airDashCharge = 0f; dashPre = 0f; airDashStomp = 0f }
        else {
            if (dashFire) { dashFire = false; if (airDashCharge > 0.1f && dashPre <= 0f) { dashPre = 0.17f; dashCh = airDashCharge } else airDashCharge = 0f }
            if (dashPre > 0f) {   // soltou: encolhido, ele pisa no ar (perna estica pra trás) e só então sai o impulso
                dashPre -= dt; airDashStomp = min(1f, airDashStomp + dt / 0.17f)
                val dk = min(1f, 9f * dt); p.vx -= p.vx * dk; p.vy -= p.vy * dk; p.vz -= p.vz * dk   // segura no ar durante a pisada
                if (dashPre <= 0f) { dashPre = 0f; airDash(p, dashCh); airDashCharge = 0f }
            } else {
                if (dashHeld) { airDashCharge = min(1f, airDashCharge + dt / 0.6f); dashChargeFx(p, airDashCharge, dt) }
                else airDashCharge = max(0f, airDashCharge - dt / 0.15f)
                airDashStomp = max(0f, airDashStomp - dt / 0.75f)
            }
        }
        airDashT = max(0f, airDashT - dt)
        // manobras: o analógico do botão escolhe a direção; no voo rápido tudo fica mais longo, mais veloz e com mais efeito
        if (trickReq) {
            trickReq = false
            if (trickCanQueue()) { trickQ = true; trickQDx = trickDx; trickQDy = trickDy }   // na 2ª metade da manobra: emenda a próxima (combo)
            else if (trickOk()) { trickCombo = 1; trickStart(p, trickDx, trickDy) }
        }
        if (trickLeft > 0f) {
            if (p.onGround && !flying || p.inWater || driving || seated) trickLeft = min(trickLeft, 0.08f)   // pousou no meio: termina a volta rapidinho
            trickLeft = max(0f, trickLeft - dt)
            trickA = (1f - trickLeft / trickDur).coerceIn(0f, 1f)
            if (trickLeft <= 0f) {
                trickKind = 0; trickA = 0f
                if (trickQ) {
                    trickQ = false
                    if (!p.onGround || flying) { trickCombo += 1; trickStart(p, trickQDx, trickQDy) } else trickCombo = 0
                } else trickCombo = 0
            }
        } else if (trickKind != 0) { trickKind = 0; trickA = 0f; trickQ = false; trickCombo = 0 }
        if (flying && airDashT <= 0f && trickLeft <= 0f && abs(p.vy) > 3f) {   // subindo/descendo no voo: riscos de vento verticais passando pelo corpo
            vertFxT -= dt
            if (vertFxT <= 0f) {
                vertFxT = 0.06f
                val up = p.vy > 0f; val k = min(1f, (abs(p.vy) - 3f) / 3f)
                for (i in 0 until 2) {
                    val a = rnd.nextFloat() * 6.2832f; val r = 0.5f + rnd.nextFloat() * 0.8f
                    windAdd(WindP(0, p.x + cos(a) * r, p.y + (if (up) 0.1f else 1.5f) + rnd.nextFloat() * 1.0f, p.z + sin(a) * r, 0f, 1f, 0f,
                        if (rnd.nextBoolean()) 0xFFFFFF else 0xBFEFFF, 0.35f + rnd.nextFloat() * 0.2f, 0.9f + rnd.nextFloat() * 1.0f, 0f, 0f), 0.55f * k)
                }
            }
        }
        if (airDashT > 0f && trickLeft <= 0f && parts.size < 500) {   // faíscas e vento soltos atrás durante o impulso
            trailFxT -= dt
            if (trailFxT <= 0f) {
                trailFxT = 0.03f
                val l = max(0.1f, sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz)); val tbx = -p.vx / l; val tby = -p.vy / l; val tbz = -p.vz / l
                for (i in 0 until 3) {
                    val pr = Particle(p.x + (rnd.nextFloat() - 0.5f) * 0.6f, p.y + 0.8f + (rnd.nextFloat() - 0.5f) * 0.8f, p.z + (rnd.nextFloat() - 0.5f) * 0.6f,
                        tbx * (2f + rnd.nextFloat() * 4f), tby * (2f + rnd.nextFloat() * 4f), tbz * (2f + rnd.nextFloat() * 4f),
                        when (rnd.nextInt(4)) { 0 -> 0xFFF3B0; 1 -> 0x9FE6FF; else -> 0xFFFFFF }, 0.05f + rnd.nextFloat() * 0.07f, 0.35f + rnd.nextFloat() * 0.4f)
                    pr.grav = 0f; parts.add(pr)
                }
            }
        }
        if (seated) {
            seatT -= dt
            if (!Furniture.sittable(world.get(seatX, seatY, seatZ)) || flying || p.inWater || posture != 2 ||
                (seatT <= 0f && (hypot(stickX, stickY) > 0.4f || jumpHeld))) standUp()
        }
        if (flying || player.inWater) { if (posture != 0) { posture = 0; player.h = 1.8f } }
        if (posture == 2) { stickX = 0f; stickY = 0f }   // sentado não anda; rastejando anda devagar
        val pk = min(1f, 10f * dt)
        crouchA += ((if (posture == 1) 1f else if (skating) skateCrouch else 0f) - crouchA) * pk
        sitA += ((if (posture == 2) 1f else 0f) - sitA) * pk
        lieA += ((if (posture == 3) 1f else 0f) - lieA) * pk
        if (hypot(stickX, stickY) < 0.1f || posture != 0) sprint = false   // parou de andar: desliga a corrida
        val sp = (if (p.inWater && !flying) 3.6f else if (flying) 10f else 5f) * (if (charging) 0.55f else 1f) * (1f - 0.5f * airDashCharge) * (if (sprint) (if (flying) 1.9f else if (p.inWater) 1.7f else 1.55f) else 1f) * (1f - 0.45f * crouchA) * (1f - 0.72f * lieA)
        val f = sin(yaw); val c = cos(yaw)
        // voo livre: voando, o joystick segue a câmera em todas as direções (olha pra cima e sobe, olha pra baixo e desce)
        val ff = flying && freeFly && !p.inWater
        val cp = if (ff) cos(pitch) else 1f
        flyVy = if (ff) sin(pitch) * stickY * sp else 0f
        val mx = (f * stickY * cp + (-c) * stickX) * sp; val mz = (c * stickY * cp + f * stickX) * sp
        wishX = mx; wishZ = mz
        val dsh = 1f - 0.85f * min(1f, airDashT / 0.7f)   // durante o impulso do dash a velocidade extra se dissipa devagar
        val k = min(1f, (if (p.onGround) 14f else 5f) * dt * dsh)
        p.vx += (mx - p.vx) * k; p.vz += (mz - p.vz) * k
        val mag = hypot(stickX, stickY).coerceAtMost(1f)
        if (mag > 0.1f && thirdPerson && swing >= 1f) {
            val target = atan2(mx, mz)
            var diff = target - bodyYaw
            while (diff > PI) diff -= (2 * PI).toFloat(); while (diff < -PI) diff += (2 * PI).toFloat()
            bodyYaw += diff * min(1f, 12f * dt)
        } else if (!thirdPerson) bodyYaw = yaw
        if (flying && thirdPerson && (dashHeld || dashPre > 0f) && (mag <= 0.1f || freeFly)) {   // carregando/pisando: vira o corpo pra direção do dash
            val tgt = if (freeFly) atan2(fwdX(), fwdZ()) else bodyYaw
            var df = tgt - bodyYaw
            while (df > PI) df -= (2 * PI).toFloat(); while (df < -PI) df += (2 * PI).toFloat()
            bodyYaw += df * min(1f, 14f * dt)
        }
        if (jumpHeld && !flying && posture == 0 && p.inWater) { if (p.onGround) { p.vy = max(p.vy, 5.5f); p.onGround = false } }   // na água: segurar sobe/sai da margem
        updateJump(p, dt)
        val wasW = p.inWater; val vyPre = p.vy
        updateElev(dt)
        updateCar(dt)
        if (driving) { /* o carro move o jogador (updateCar) */ } else if (seated || elevRide != null) { if (elevRide == null) { p.vx = 0f; p.vy = 0f; p.vz = 0f; p.onGround = true } } else {
            for (i in 0 until 2) physics(p, dt / 2f)
            waterAndSteps(dt, wasW, vyPre)
        }
        p.x = p.x.coerceIn(2f, World.SX - 2f); p.z = p.z.coerceIn(2f, World.SZ - 2f)
        if (p.y < -5f) respawn()
        // voo rápido: impulso de herói no começo e rastro luminoso atrás (sem tremer a tela)
        val spdF = hypot(p.vx, p.vz)
        // o vento entra e sai suave (rampa pela velocidade), em vez de aparecer de uma vez ao passar de 11
        val wTgt = if (flying && airDashT > 0f) 1f else if (flying) ((spdF - 9.5f) / 5.5f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) } else 0f
        windAmt += (wTgt - windAmt) * min(1f, (if (wTgt > windAmt) 2.4f else 3.2f) * dt)
        if (windAmt > 0.5f && !wasFast) { windBurst(p, 0.4f); wasFast = true }   // sem flyImp: o pulso de impulso balançava o corpo
        else if (windAmt < 0.25f) wasFast = false
        flyImp = max(0f, flyImp - dt / 0.6f)
        updateWind(dt, p, windAmt > 0.03f)
        trailT -= dt
        if ((p.inWater && !flying && sprint && spdF > 4.5f) && trailT <= 0f && parts.size < 480) {
            trailT = 0.025f
            val l = max(0.1f, spdF); val bx = -p.vx / l; val bz = -p.vz / l
            val col = if (flying) (if (rnd.nextFloat() < 0.5f) 0xFFFFFF else 0x9FE6FF) else 0xD8F4FF
            for (i in 0 until 2) {
                val pr = Particle(p.x + bx * 0.4f + (rnd.nextFloat() - 0.5f) * 0.35f, p.y + 0.8f + (rnd.nextFloat() - 0.5f) * 0.5f, p.z + bz * 0.4f + (rnd.nextFloat() - 0.5f) * 0.35f,
                    bx * 0.6f, 0.2f, bz * 0.6f, col, 0.05f + rnd.nextFloat() * 0.05f, 0.55f + rnd.nextFloat() * 0.3f)
                pr.grav = 0f; parts.add(pr)
            }
        }
        // super pulo: rastro de poeira/vento branco atrás do boneco enquanto sobe e cai
        if (superAir && !p.onGround) {
            superTrailT -= dt
            if (superTrailT <= 0f && parts.size < 500) {
                superTrailT = 0.035f
                for (i in 0 until 2) {
                    val pt = Particle(p.x + (rnd.nextFloat() - 0.5f) * 0.5f, p.y + 0.2f + rnd.nextFloat() * 1.2f, p.z + (rnd.nextFloat() - 0.5f) * 0.5f,
                        (rnd.nextFloat() - 0.5f) * 0.6f - p.vx * 0.05f, -p.vy * 0.12f, (rnd.nextFloat() - 0.5f) * 0.6f - p.vz * 0.05f,
                        if (rnd.nextInt(3) == 0) 0xDFF1FF else 0xFFFFFF, 0.07f + rnd.nextFloat() * 0.06f, 0.5f + rnd.nextFloat() * 0.3f)
                    pt.dust = true; pt.grav = 0f; parts.add(pt)
                }
            }
        }
        // nado rápido na superfície: as pernas batem e jogam respingos atrás
        kickT -= dt
        if (p.inWater && !flying && sprint && spdF > 3f && p.y + 1.9f > World.WATER_Y + 0.88f && kickT <= 0f && parts.size < 500) {
            kickT = 0.075f; kickSide = !kickSide
            val l = max(0.1f, spdF); val dxn = p.vx / l; val dzn = p.vz / l; val sd = if (kickSide) 1f else -1f
            val fx = p.x - dxn * 0.55f - dzn * 0.12f * sd; val fz = p.z - dzn * 0.55f + dxn * 0.12f * sd
            val surfY = World.WATER_Y + 0.88f
            for (i in 0 until 3) {
                val a = rnd.nextFloat() * 6.2832f
                parts.add(Particle(fx, surfY, fz, cos(a) * 0.7f - dxn * 0.8f, 2.2f + rnd.nextFloat() * 1.8f, sin(a) * 0.7f - dzn * 0.8f, if (rnd.nextInt(3) == 0) 0xFFFFFF else 0xDFF4FF, 0.035f + rnd.nextFloat() * 0.04f, 0.5f + rnd.nextFloat() * 0.3f))
            }
            if (rnd.nextInt(3) == 0) addRipple(fx, fz, 0.35f)
        }
        walkAmt += (min(1f, mag) * (if (p.onGround) 1f else 0.4f) - walkAmt) * min(1f, 10f * dt)
        walkPhase += dt * 9f * walkAmt

        // ataque: toque = golpe normal; segurar (espada/machado) = carrega, soltar = golpe poderoso
        val chargeable = cur() == Items.SWORD || cur() == Items.AXE
        if (attackPress) { attackPress = false; if (chargeable) { pressing = true; pressT = 0f } else attack() }
        if (pressing) {
            if (!chargeable) { pressing = false; charging = false; charge = 0f }
            else if (attackHeld) {
                pressT += dt
                if (!charging && pressT > 0.18f && swing >= 0.85f) { charging = true; charge = 0f }
                if (charging) charge = min(1f, charge + dt / 0.85f)
            }
        }
        if (attackRelease) {
            attackRelease = false
            if (pressing) { pressing = false; if (charging) { val pw = charge; charging = false; charge = 0f; releasePower(pw) } else attack() }
        }
        if (!attackHeld && charging) { charging = false; charge = 0f }
        hitPulse = max(0f, hitPulse - dt * 4.5f); pickT = max(0f, pickT - dt); wetT = max(0f, wetT - dt)
        val dI = dmg.values.iterator()
        while (dI.hasNext()) {
            val d = dI.next(); d.idle += dt
            if (!world.solid(d.x, d.y, d.z)) { world.hidden.remove((d.y * World.SZ + d.z) * World.SX + d.x); d.dead = true; dI.remove(); continue }
            if (!d.carved) { if (d.idle > 4f) d.prog -= dt * 0.2f; if (d.prog <= 0f) { d.dead = true; dI.remove() } }   // bloco esculpido não se cura
        }
        if (hitT > 0f) { hitT -= dt; if (hitT <= 0f) strikeHit(hitItem, hitStab) }
        if (pendingT > 0f) { pendingT -= dt; if (pendingT <= 0f) powerHit(pendingPower) }
        if (wantPlace) { wantPlace = false; place() }

        spawnT -= dt
        if (spawnT <= 0f) { spawnT = 3f; if (!noMobs && slimes.size < 7) spawnSlime() }
        val it = slimes.iterator()
        while (it.hasNext()) {
            val s = it.next()
            s.t += dt; s.flash = max(0f, s.flash - dt); s.squash *= max(0f, 1f - 8f * dt)
            val dx = p.x - s.x; val dz = p.z - s.z; val dist = hypot(dx, dz)
            val wasGround = s.onGround
            if (s.onGround) { s.vx *= max(0f, 1f - 10f * dt); s.vz *= max(0f, 1f - 10f * dt) }
            s.timer -= dt
            if (s.onGround && s.timer <= 0f) {
                s.timer = 0.9f + rnd.nextFloat() * 1.2f
                if (dist < 13f) { s.vx = dx / max(0.1f, dist) * 3.4f; s.vz = dz / max(0.1f, dist) * 3.4f }
                else { val a = rnd.nextFloat() * 6.283f; s.vx = cos(a) * 1.6f; s.vz = sin(a) * 1.6f }
                s.vy = 7.2f; s.squash = 0.35f
            }
            physics(s, dt)
            if (s.onGround && !wasGround) s.squash = -0.35f
            if (dist < 0.85f && abs(p.y - s.y) < 1.3f && !s.dead) hurt(dx, dz)
            if (s.dead || s.y < -5f || dist > 60f) it.remove()
        }
        if (hp <= 0 && deadTimer <= 0f) deadTimer = 2.5f
        regen += dt; if (regen > 6f && hp < 10) { hp++; regen = 0f }

        val bi = bolts.iterator()
        while (bi.hasNext()) {
            val b = bi.next()
            b.life -= dt; b.x += b.vx * dt; b.y += b.vy * dt; b.z += b.vz * dt
            parts.add(Particle(b.x, b.y, b.z, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f,
                if (rnd.nextBoolean()) 0x7FE8FF else 0xFF9AE8, 0.07f, 0.35f))
            var gone = b.life <= 0f || world.solid(floor(b.x).toInt(), floor(b.y).toInt(), floor(b.z).toInt())
            if (!gone) for (s in slimes) {
                if (s.dead) continue
                val dx = s.x - b.x; val dy = s.y + 0.4f - b.y; val dz = s.z - b.z
                if (dx * dx + dy * dy + dz * dz < 0.6f) { hitSlime(s, 2, b.vx, b.vz, 7f); gone = true; break }
            }
            if (gone) { burst(b.x, b.y, b.z, 0x7FE8FF, 14, 5f); bi.remove() }
        }
        updateTrees(dt); updateDrops(dt)
        val rpi = ripples.iterator(); while (rpi.hasNext()) { val r = rpi.next(); r.age += dt; if (r.age > 3.2f) rpi.remove() }
        val fpi = prints.iterator(); while (fpi.hasNext()) { val q = fpi.next(); if (time - q.born > q.life()) fpi.remove() }
        regrowT -= dt; if (regrowT <= 0f) { regrowT = 2f; world.regrow(time, 80f) }
        val di = debris.iterator()
        while (di.hasNext()) {
            val q = di.next(); q.life -= dt
            if (q.life <= 0f) { burst(q.x, q.y, q.z, B.top[q.id], 3, 1.5f); di.remove(); continue }
            q.vy -= 22f * dt
            val h = q.size * 0.5f
            var nx = q.x + q.vx * dt; var ny = q.y + q.vy * dt; var nz = q.z + q.vz * dt
            q.ground = false
            if (q.vy <= 0f && world.solid(floor(q.x).toInt(), floor(ny - h).toInt(), floor(q.z).toInt())) {
                ny = floor(ny - h) + 1f + h
                if (q.vy < -2.5f) q.vy = -q.vy * 0.38f else q.vy = 0f
                q.vx *= 0.7f; q.vz *= 0.7f; q.ground = true
            } else if (q.vy > 0f && world.solid(floor(q.x).toInt(), floor(ny + h).toInt(), floor(q.z).toInt())) { q.vy = 0f; ny = q.y }
            if (world.solid(floor(nx + (if (q.vx > 0) h else -h)).toInt(), floor(ny).toInt(), floor(q.z).toInt())) { q.vx = -q.vx * 0.3f; nx = q.x }
            if (world.solid(floor(q.x).toInt(), floor(ny).toInt(), floor(nz + (if (q.vz > 0) h else -h)).toInt())) { q.vz = -q.vz * 0.3f; nz = q.z }
            q.x = nx; q.y = ny; q.z = nz
            val rd = if (q.ground) max(0f, 1f - 7f * dt) else 1f
            q.vrx *= rd; q.vry *= rd; q.vrz *= rd
            q.rx += q.vrx * dt; q.ry += q.vry * dt; q.rz += q.vrz * dt
        }
        val pi = parts.iterator()
        while (pi.hasNext()) {
            val q = pi.next()
            q.life -= dt; q.vy -= q.grav * dt; q.age += dt
            if (q.dust) { val dr = max(0f, 1f - 3.2f * dt); q.vx *= dr; q.vz *= dr; q.vy *= max(0f, 1f - 2f * dt) }
            if (q.grav < 0f && q.y > World.WATER_Y + 0.85f) q.life = 0f   // bolhas estouram na superfície
            val nx = q.x + q.vx * dt; val ny = q.y + q.vy * dt; val nz = q.z + q.vz * dt
            if (world.solid(floor(nx).toInt(), floor(ny).toInt(), floor(nz).toInt())) { q.vx *= 0.3f; q.vz *= 0.3f; q.vy = 0f }
            else { q.x = nx; q.y = ny; q.z = nz }
            if (q.life <= 0f) pi.remove()
        }
        // folhas caindo das árvores perto do jogador (quantidade moderada)
        leafT -= dt
        if (leafT <= 0f && leafFall.size < 120) {
            leafT = 0.06f + rnd.nextFloat() * 0.14f
            val bamb = B.byName["Colmo de Bambu"] ?: -1; val bamL = B.byName["Folhas de Bambu"] ?: B.LEAVES
            for (t in 0 until 70) {
                val x = (player.x + (rnd.nextFloat() - 0.5f) * 34f).toInt(); val z = (player.z + (rnd.nextFloat() - 0.5f) * 34f).toInt()
                val y = (player.y + (rnd.nextFloat() - 0.4f) * 20f).toInt()
                if (x < 1 || z < 1 || x >= World.SX - 1 || z >= World.SZ - 1 || y < 2 || y >= World.SY) continue
                val id = world.get(x, y, z)
                val stalk = id == bamb && world.get(x, y - 1, z) == bamb && world.get(x, y - 2, z) == bamb && world.get(x, y - 3, z) == bamb
                if (!stalk && (!B.leafy(id) || world.get(x, y - 1, z) != B.AIR)) continue
                val lid = if (stalk) bamL else id
                val cols = leafPal(lid)
                val l = LeafP(x + rnd.nextFloat(), y - 0.05f, z + rnd.nextFloat(), (rnd.nextFloat() - 0.5f) * 0.4f + 0.2f, (rnd.nextFloat() - 0.5f) * 0.5f,
                    rnd.nextFloat() * 6.28f, cols[rnd.nextInt(cols.size)], 0.12f + rnd.nextFloat() * 0.07f, 10f)
                l.shape = leafShape(lid); leafFall.add(l)
                break
            }
        }
        val lbi = leafBreaks.iterator()
        while (lbi.hasNext()) {
            val b = lbi.next(); b.t += dt
            if (!b.fx && b.t >= LB_SHAKE) { b.fx = true; leafCutFx(b.x, b.y, b.z, 0) }
            if (b.t >= LB_SHAKE + LB_FLY) lbi.remove()
        }
        val li = leafFall.iterator()
        while (li.hasNext()) {
            val l = li.next()
            l.age += dt; l.life -= dt
            if (!l.landed) {
                if (l.burst > 0f) { l.burst -= dt; val d = max(0f, 1f - 3.2f * dt); l.vx *= d; l.vz *= d; l.vy *= max(0f, 1f - 4f * dt) }
                else { l.vy = 0f; l.vx *= max(0f, 1f - 1.5f * dt); l.vz *= max(0f, 1f - 1.5f * dt) }
                l.x += (l.vx + sin(l.age * 2.2f + l.ph) * 0.5f + cos(l.age * 1.3f + l.ph) * 0.25f) * dt
                l.z += (l.vz + cos(l.age * 1.9f + l.ph) * 0.4f + sin(l.age * 1.1f + l.ph) * 0.25f) * dt
                l.y += (l.vy - (0.5f + 0.35f * abs(sin(l.age * 3.1f + l.ph)))) * dt
                if (world.solid(floor(l.x).toInt(), floor(l.y).toInt(), floor(l.z).toInt())) { l.landed = true; l.life = min(l.life, 1.4f) }
            }
            if (l.life <= 0f || l.y < 0f) li.remove()
        }
        updateCamera()
        raycast(camX, camY, camZ, dirX(), dirY(), dirZ(), camDist, camDist + 5.5f)
    }

    // ---------- água, pegadas e itens no chão ----------
    private fun addRipple(x: Float, z: Float, amp: Float, age0: Float = 0f) {
        ripples.add(Ripple(x, z, amp, age0)); if (ripples.size > 18) ripples.removeAt(0)
    }

    private fun splash(x: Float, y: Float, z: Float, v: Float) {
        val n = (8 + v * 2.2f).toInt().coerceAtMost(42)
        for (i in 0 until n) {
            val a = rnd.nextFloat() * 6.2832f; val r = rnd.nextFloat() * 0.5f; val up = 2f + rnd.nextFloat() * 2f + v * 0.28f
            val col = if (rnd.nextInt(3) == 0) 0xFFFFFF else 0xBFE6FF
            parts.add(Particle(x + cos(a) * r * 0.4f, y, z + sin(a) * r * 0.4f, cos(a) * r * 2.2f, up, sin(a) * r * 2.2f, col, 0.04f + rnd.nextFloat() * 0.05f, 0.7f + rnd.nextFloat() * 0.5f))
        }
        shake = max(shake, min(0.2f, v * 0.012f))
    }

    private var prevG = true
    private fun dustColor(gid: Int): Int {
        if (gid == B.GRASS) return 0xBDD293; if (gid == B.SAND) return 0xF3E6B6; if (gid == B.DIRT) return 0xCCA67E
        val c = B.top[gid]; val r = (c shr 16 and 255); val gg = (c shr 8 and 255); val b = (c and 255)
        return ((r + (255 - r) * 45 / 100) shl 16) or ((gg + (255 - gg) * 45 / 100) shl 8) or (b + (255 - b) * 45 / 100)
    }
    /** poeirinha fofa: bolinhas que sobem, inflam e somem; jogadas um pouco pra trás do passo */
    private fun stepDust(x: Float, y: Float, z: Float, gid: Int, vx: Float, vz: Float, sp: Float) {
        if (parts.size > 520) return
        val col = dustColor(gid)
        for (i in 0 until 5) {
            val a = rnd.nextFloat() * 6.2832f; val r = 0.3f + rnd.nextFloat() * 0.5f
            val pt = Particle(x + cos(a) * 0.12f, y + 0.04f + rnd.nextFloat() * 0.06f, z + sin(a) * 0.12f,
                -vx * 0.18f + cos(a) * r, 0.5f + rnd.nextFloat() * 0.7f, -vz * 0.18f + sin(a) * r, col, 0.07f + rnd.nextFloat() * 0.06f, 0.55f + rnd.nextFloat() * 0.35f)
            pt.dust = true; pt.grav = 0.6f; parts.add(pt)
        }
    }
    private fun landDust(x: Float, y: Float, z: Float, gid: Int, power: Float) {
        if (parts.size > 500) return
        val col = dustColor(gid); val n = (10 + power).toInt().coerceAtMost(18)
        for (i in 0 until n) {
            val a = i * 6.2832f / n + rnd.nextFloat() * 0.3f; val sp = 1.6f + rnd.nextFloat() * 1.2f
            val pt = Particle(x + cos(a) * 0.2f, y + 0.05f, z + sin(a) * 0.2f, cos(a) * sp, 0.3f + rnd.nextFloat() * 0.6f, sin(a) * sp, col, 0.09f + rnd.nextFloat() * 0.07f, 0.6f + rnd.nextFloat() * 0.4f)
            pt.dust = true; pt.grav = 0.4f; parts.add(pt)
        }
    }

    private fun waterAndSteps(dt: Float, wasW: Boolean, vyPre: Float) {
        val p = player
        if (p.onGround && !prevG && vyPre < -7f && !p.inWater && !flying) {
            val lg = world.get(floor(p.x).toInt(), floor(p.y - 0.06f).toInt(), floor(p.z).toInt())
            if (lg != B.AIR && lg != B.WATER) { if (superAir && vyPre < -10f) superLandDust(p.x, p.y, p.z, lg, -vyPre) else landDust(p.x, p.y, p.z, lg, -vyPre) }
        }
        if (p.onGround) superAir = false
        prevG = p.onGround
        val surf = World.WATER_Y + 0.88f
        val sp = hypot(p.vx, p.vz)
        swimAmt += ((if (p.inWater && !p.onGround && !flying) 1f else 0f) - swimAmt) * min(1f, 8f * dt)
        if (p.inWater) inWaterT += dt
        val hu = waterAtPt(p.x, p.y + 1.55f, p.z)   // cabeça (olhos) dentro d'água
        if (hu) { headWetT += dt; wetT = 0f }
        else { if (headWas && headWetT > 0.25f) wetT = 3f; headWetT = 0f }   // tela molhada só quando a cabeça sai da água
        headWas = hu
        if (p.inWater && !wasW) {   // entrou na água
            wetT = 0f   // onda proporcional à velocidade da queda
            val v = max(0f, -vyPre)
            val amp = (0.3f + v * 0.1f).coerceAtMost(1.8f)
            addRipple(p.x, p.z, amp)
            if (v > 7f) addRipple(p.x, p.z, amp * 0.6f, -0.18f)
            splash(p.x, surf, p.z, v)
        } else if (!p.inWater && wasW) {
            addRipple(p.x, p.z, 0.4f); splash(p.x, surf, p.z, 2.5f)
            inWaterT = 0f
        }
        if (p.inWater) {
            rippleT -= dt
            if (p.y + 1.9f > surf && rippleT <= 0f) {   // nadando na superfície: marolas contínuas
                rippleT = if (sp > 0.5f) 0.13f else 0.5f
                val bx = if (sp > 0.5f) -p.vx / sp * 0.35f else 0f; val bz = if (sp > 0.5f) -p.vz / sp * 0.35f else 0f
                addRipple(p.x + bx + (rnd.nextFloat() - 0.5f) * 0.3f, p.z + bz + (rnd.nextFloat() - 0.5f) * 0.3f, if (sp > 0.5f) 0.62f else 0.3f)
                if (sp > 0.5f) for (i in 0 until 2) {   // respingos pequenos da braçada
                    val a = rnd.nextFloat() * 6.2832f
                    parts.add(Particle(p.x + cos(a) * 0.35f, surf, p.z + sin(a) * 0.35f, cos(a) * 0.9f + p.vx * 0.2f, 1.6f + rnd.nextFloat(), sin(a) * 0.9f + p.vz * 0.2f, 0xDFF4FF, 0.035f, 0.5f))
                }
            }
            val headUnder = headWas
            bubbleT -= dt
            if (headUnder && bubbleT <= 0f) {
                bubbleT = (0.35f + rnd.nextFloat() * 0.5f) * (if (abs(p.vy) > 1.5f) 0.3f else 1f)   // subindo/afundando solta mais bolhas
                val b = Particle(p.x + (rnd.nextFloat() - 0.5f) * 0.3f, p.y + 1.5f, p.z + (rnd.nextFloat() - 0.5f) * 0.3f, 0f, 0.6f, 0f, 0xDDF4FF, 0.05f + rnd.nextFloat() * 0.04f, 2.4f)
                b.grav = -3f; parts.add(b)
            }
        }
        // pegadas: cada bloco reage de um jeito
        if (p.onGround && !flying && !p.inWater && sp > 0.8f) {
            stepDist += sp * dt
            if (stepDist > 0.58f) {
                stepDist = 0f; stepLeft = !stepLeft
                val gx = floor(p.x).toInt(); val gy = floor(p.y - 0.06f).toInt(); val gz = floor(p.z).toInt()
                val gid = world.get(gx, gy, gz)
                if (gid != B.AIR && gid != B.WATER) stepDust(p.x, gy + 1f, p.z, gid, p.vx, p.vz, sp)
                if (gid == B.SAND || gid == B.DIRT || gid == B.GRASS) {
                    val ang = atan2(p.vx, p.vz); val sd = if (stepLeft) -1f else 1f
                    prints.add(Foot(p.x + cos(ang) * 0.13f * sd, gy + 1f, p.z - sin(ang) * 0.13f * sd, ang, gid, time, sd))
                    if (prints.size > 40) prints.removeAt(0)
                    if (gid == B.SAND) burst(p.x, gy + 1.05f, p.z, 0xF0DE9A, 3, 1.2f)
                    else if (gid == B.DIRT) burst(p.x, gy + 1.05f, p.z, 0x8C6039, 2, 1f)
                }
            }
        }
    }

    // ---------- rastro de vento do voo rápido ----------
    private val WIND_MAX = 380
    private fun windAdd(w: WindP, amp: Float = 1f) { if (wind.size < WIND_MAX) { w.amp = amp; wind.add(w) } }

    /** decolagem do super pulo: aneis de vento horizontais se abrindo embaixo do boneco + riscos subindo */
    private fun flyLaunchFx(p: Ent) {
        flyLaunch = 1f
        windAdd(WindP(2, p.x, p.y + 0.3f, p.z, 0f, 1f, 0f, 0xFFFFFF, 0.7f, 0f, 3.2f, 0f), 0.8f)
        windAdd(WindP(2, p.x, p.y + 0.9f, p.z, 0f, 1f, 0f, 0x9FE6FF, 0.6f, 0f, 2.2f, 0f), 0.65f)
        for (i in 0 until 10) {
            val a = i * 0.6283f + rnd.nextFloat() * 0.3f; val r = 0.5f + rnd.nextFloat() * 0.9f
            windAdd(WindP(0, p.x + cos(a) * r, p.y + 0.2f + rnd.nextFloat() * 1.0f, p.z + sin(a) * r, 0f, 1f, 0f,
                if (i % 2 == 0) 0xFFFFFF else 0xBFEFFF, 0.5f + rnd.nextFloat() * 0.2f, 1.6f + rnd.nextFloat() * 1.2f, 0f, 0f), 0.7f)
        }
        shake = max(shake, 0.05f)
    }

    /** dash aéreo (depois da pisada): impulso grande na direção do olhar (voo livre) ou do movimento; ~2x mais longe que antes */
    private fun airDash(p: Ent, ch: Float) {
        val dx: Float; val dy: Float; val dz: Float
        if (freeFly) { dx = fwdX(); dy = fwdY(); dz = fwdZ() }
        else if (hypot(stickX, stickY) > 0.1f) { val l = max(0.001f, hypot(wishX, wishZ)); dx = wishX / l; dy = 0f; dz = wishZ / l }
        else { dx = sin(bodyYaw); dy = 0f; dz = cos(bodyYaw) }
        val spd = 36f + 26f * ch
        p.vx = dx * spd; p.vy = dy * spd; p.vz = dz * spd
        airDashT = 0.7f
        if (hypot(dx, dz) > 0.05f) bodyYaw = atan2(dx, dz)
        if (hypot(stickX, stickY) > 0.1f) sprint = true
        windAmt = 1f; wasFast = true
        windBurst(p, 1f)
        dashFx(p, dx, dy, dz, ch)
        shake = max(shake, 0.16f + 0.1f * ch)
    }

    /** o visual do impulso: onda de choque no pé que pisou o ar (anéis + raios), túnel de anéis atrás, faíscas */
    private fun dashFx(p: Ent, dx: Float, dy: Float, dz: Float, ch: Float) {
        val rx = if (abs(dy) < 0.9f) 0f else 1f; val ry = if (abs(dy) < 0.9f) 1f else 0f
        var ux = -dz * ry; var uy = dz * rx; var uz = dx * ry - dy * rx
        val ul = max(0.001f, sqrt(ux * ux + uy * uy + uz * uz)); ux /= ul; uy /= ul; uz /= ul
        val vx = dy * uz - dz * uy; val vy = dz * ux - dx * uz; val vz = dx * uy - dy * ux
        val fx = p.x - dx * 0.8f; val fy = p.y + 0.7f - dy * 0.8f; val fz = p.z - dz * 0.8f   // onde o pé empurrou o ar (atrás, no eixo do corpo)
        // 3 anéis concêntricos na pisada, voltados pra direção do dash
        windAdd(WindP(2, fx, fy, fz, dx, dy, dz, 0xFFFFFF, 0.45f, 0f, 1.5f + 0.5f * ch, 0f), 1f)
        windAdd(WindP(2, fx, fy, fz, dx, dy, dz, 0x9FE6FF, 0.6f, 0f, 2.5f + 0.7f * ch, 0f), 0.9f)
        windAdd(WindP(2, fx, fy, fz, dx, dy, dz, 0xDDF6FF, 0.75f, 0f, 3.6f + 0.9f * ch, 0f), 0.75f)
        // raios radiais saindo do pé (estrela da pisada)
        for (i in 0 until 18) {
            val a = i * 0.3491f + rnd.nextFloat() * 0.15f; val ca = cos(a); val sa = sin(a)
            val rdx = ux * ca + vx * sa; val rdy = uy * ca + vy * sa; val rdz = uz * ca + vz * sa; val r0 = 0.5f + rnd.nextFloat() * 0.5f
            windAdd(WindP(0, fx + rdx * r0, fy + rdy * r0, fz + rdz * r0, rdx, rdy, rdz, if (i % 3 == 0) 0xFFF3B0 else if (i % 2 == 0) 0xFFFFFF else 0x9FE6FF, 0.35f + rnd.nextFloat() * 0.2f, 1.4f + rnd.nextFloat() * 1.6f, 0f, 0f), 1f)
        }
        // túnel de anéis ao longo da trilha atrás do boneco
        for (i in 0 until 5) windAdd(WindP(2, p.x - dx * (1.4f + 1.5f * i), p.y + 0.8f - dy * (1.4f + 1.5f * i), p.z - dz * (1.4f + 1.5f * i), dx, dy, dz,
            if (i % 2 == 0) 0xFFFFFF else 0x9FE6FF, 0.55f + 0.08f * i, 0f, 2.3f + 0.55f * i, 0f), 0.85f - 0.1f * i)
        // faíscas e poeira de ar jogadas pra trás a partir do pé
        for (i in 0 until 40) {
            if (parts.size > 560) break
            val a = rnd.nextFloat() * 6.2832f; val sp = 1f + rnd.nextFloat() * 3.5f; val bk = 5f + rnd.nextFloat() * 11f
            val pr = Particle(fx + (ux * cos(a) + vx * sin(a)) * 0.2f, fy + (uy * cos(a) + vy * sin(a)) * 0.2f, fz + (uz * cos(a) + vz * sin(a)) * 0.2f,
                -dx * bk + (ux * cos(a) + vx * sin(a)) * sp, -dy * bk + (uy * cos(a) + vy * sin(a)) * sp, -dz * bk + (uz * cos(a) + vz * sin(a)) * sp,
                when (rnd.nextInt(4)) { 0 -> 0xFFF3B0; 1 -> 0x9FE6FF; else -> 0xFFFFFF }, 0.05f + rnd.nextFloat() * 0.09f, 0.45f + rnd.nextFloat() * 0.6f)
            pr.grav = 0f; parts.add(pr)
        }
    }

    /** carregando o dash: o ar é sugado pro boneco (partículas vindo de fora) */
    private fun dashChargeFx(p: Ent, ch: Float, dt: Float) {
        chargeFxT -= dt
        if (chargeFxT > 0f || parts.size > 500) return
        chargeFxT = 0.035f
        for (i in 0 until 2 + (ch * 3f).toInt()) {
            val a = rnd.nextFloat() * 6.2832f; val e = (rnd.nextFloat() - 0.5f) * 2.4f; val r = 2.0f + rnd.nextFloat() * 1.4f
            val ox = cos(a) * cos(e) * r; val oy = sin(e) * r; val oz = sin(a) * cos(e) * r
            val pr = Particle(p.x + ox, p.y + 0.8f + oy, p.z + oz, -ox * 3.2f, -oy * 3.2f, -oz * 3.2f, if (rnd.nextInt(3) == 0) 0x9FE6FF else 0xFFFFFF, 0.04f + rnd.nextFloat() * 0.05f, 0.3f)
            pr.grav = 0f; parts.add(pr)
        }
        shake = max(shake, 0.006f + 0.02f * ch)
    }

    /** anel grande + riscos em volta no instante em que o voo rápido dispara */
    private fun windBurst(p: Ent, amp: Float = 1f) {
        val sp = max(0.1f, sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz)); val dx = p.vx / sp; val dy = p.vy / sp; val dz = p.vz / sp
        windAdd(WindP(2, p.x + dx * 0.4f, p.y + 0.8f + dy * 0.4f, p.z + dz * 0.4f, dx, dy, dz, 0xFFFFFF, 0.75f, 0f, 4.6f, 0f), amp)
        windAdd(WindP(2, p.x - dx * 0.2f, p.y + 0.8f - dy * 0.2f, p.z - dz * 0.2f, dx, dy, dz, 0x9FE6FF, 0.6f, 0f, 3.0f, 0f), amp)
        val rx = if (abs(dy) < 0.9f) 0f else 1f; val ry = if (abs(dy) < 0.9f) 1f else 0f
        var ux = dy * 0f - dz * ry; var uy = dz * rx - dx * 0f; var uz = dx * ry - dy * rx
        val ul = max(0.001f, sqrt(ux * ux + uy * uy + uz * uz)); ux /= ul; uy /= ul; uz /= ul
        val vx = dy * uz - dz * uy; val vy = dz * ux - dx * uz; val vz = dx * uy - dy * ux
        for (i in 0 until 14) {
            val a = i * 0.4488f + rnd.nextFloat() * 0.3f; val r = 1.0f + rnd.nextFloat() * 1.6f
            windAdd(WindP(0, p.x + (ux * cos(a) + vx * sin(a)) * r - dx * rnd.nextFloat() * 1.2f, p.y + 0.8f + (uy * cos(a) + vy * sin(a)) * r - dy * rnd.nextFloat() * 1.2f,
                p.z + (uz * cos(a) + vz * sin(a)) * r - dz * rnd.nextFloat() * 1.2f, dx, dy, dz, if (i % 2 == 0) 0xFFFFFF else 0xBFEFFF, 0.5f + rnd.nextFloat() * 0.2f, 2.2f + rnd.nextFloat() * 1.6f, 0f, 0f), amp)
        }
    }

    private fun updateWind(dt: Float, p: Ent, fast: Boolean) {
        val it = wind.iterator()
        while (it.hasNext()) { val w = it.next(); w.age += dt; if (w.age >= w.life) it.remove() }
        if (!fast || trickLeft > 0f) return   // durante a manobra não nasce rastro nenhum
        val sp = max(0.1f, sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz)); val dx = p.vx / sp; val dy = p.vy / sp; val dz = p.vz / sp
        val up = abs(dy) < 0.9f   // eixo de referência: cima (ou X se o voo for quase vertical); u = d x ref
        var ux = if (up) -dz else 0f; var uy = if (up) 0f else dz; var uz = if (up) dx else -dy
        val ul = max(0.001f, sqrt(ux * ux + uy * uy + uz * uz)); ux /= ul; uy /= ul; uz /= ul
        val vx = dy * uz - dz * uy; val vy = dz * ux - dx * uz; val vz = dx * uy - dy * ux
        // riscos finos e longos, parados no mundo em volta do corpo
        val wa = windAmt.coerceIn(0.05f, 1f)   // intensidade: o vento cresce aos poucos em vez de aparecer de uma vez
        windT -= dt
        if (windT <= 0f) {
            windT = 0.03f / (0.3f + 0.7f * wa)
            for (i in 0 until 2) {
                val a = rnd.nextFloat() * 6.2832f; val r = 0.8f + rnd.nextFloat() * 1.5f; val back = rnd.nextFloat() * 1.4f - 0.9f
                windAdd(WindP(0, p.x + (ux * cos(a) + vx * sin(a)) * r + dx * back, p.y + 0.8f + (uy * cos(a) + vy * sin(a)) * r + dy * back,
                    p.z + (uz * cos(a) + vz * sin(a)) * r + dz * back, dx, dy, dz, if (rnd.nextFloat() < 0.5f) 0xFFFFFF else 0xBFEFFF, 0.35f + rnd.nextFloat() * 0.25f, 1.3f + rnd.nextFloat() * 1.5f, 0f, 0f), wa)
            }
        }
        // hélice dupla: um fio de cada mão, em fases opostas, abrindo em espiral
        helixT -= dt
        if (helixT <= 0f) {
            helixT = 0.02f / (0.3f + 0.7f * wa)
            val a0 = time * 22f
            for (sd in 0 until 2) {
                val sgn = if (sd == 0) 1f else -1f
                // mão = ao lado do corpo (perpendicular ao voo, no plano horizontal), um pouco atrás
                val hx = -dz; val hz = dx; val hl = max(0.001f, sqrt(hx * hx + hz * hz))
                val hX = p.x + hx / hl * 0.38f * sgn - dx * 0.3f; val hY = p.y + 0.72f - dy * 0.3f; val hZ = p.z + hz / hl * 0.38f * sgn - dz * 0.3f
                windAdd(WindP(1, hX, hY, hZ, dx, dy, dz, if (sd == 0) 0xFFFFFF else 0x9FE6FF, 0.5f, 0f, 0.6f, a0 + (if (sd == 0) 0f else 3.1416f)), wa)
            }
        }
        // anéis de vento se abrindo atrás do corpo
        ringT -= dt
        if (ringT <= 0f) {
            val dsh = airDashT > 0f
            ringT = if (dsh) 0.04f else 0.11f / (0.4f + 0.6f * wa)
            windAdd(WindP(2, p.x - dx * 0.5f, p.y + 0.8f - dy * 0.5f, p.z - dz * 0.5f, dx, dy, dz, if (dsh && rnd.nextBoolean()) 0x9FE6FF else 0xDDF6FF, if (dsh) 0.6f else 0.5f, 0f, if (dsh) 2.8f else 1.7f, 0f), if (dsh) 1f else wa)
        }
    }

    private fun updateDrops(dt: Float) {
        val p = player
        val di = drops.iterator()
        while (di.hasNext()) {
            val d = di.next(); d.age += dt
            val inW = world.get(floor(d.x).toInt(), floor(d.y).toInt(), floor(d.z).toInt()) == B.WATER
            d.vy -= (if (inW) 4f else 22f) * dt
            if (inW) d.vy = max(d.vy, -1.2f)
            val ny = d.y + d.vy * dt
            if (d.vy <= 0f && world.solid(floor(d.x).toInt(), floor(ny - 0.14f).toInt(), floor(d.z).toInt())) {
                d.y = floor(ny - 0.14f) + 1f + 0.14f
                d.vy = if (d.vy < -3f) -d.vy * 0.3f else 0f
                d.vx *= 0.6f; d.vz *= 0.6f
            } else d.y = ny
            val nx = d.x + d.vx * dt; if (!world.solid(floor(nx).toInt(), floor(d.y).toInt(), floor(d.z).toInt())) d.x = nx else d.vx = 0f
            val nz = d.z + d.vz * dt; if (!world.solid(floor(d.x).toInt(), floor(d.y).toInt(), floor(nz).toInt())) d.z = nz else d.vz = 0f
            val dx = p.x - d.x; val dy = p.y + 0.9f - d.y; val dz = p.z - d.z
            val dist2 = dx * dx + dy * dy + dz * dz
            if (d.age > 0.5f && dist2 < 2.8f) {
                val k = min(1f, 9f * dt); d.x += dx * k; d.y += dy * k; d.z += dz * k   // puxa pro jogador
                if (dist2 < 0.5f) {
                    if (d.id in picked.indices) picked[d.id]++; pickMsg = "+1 " + B.names[d.id]; pickT = 1.6f
                    burst(d.x, d.y, d.z, B.top[d.id], 4, 1.5f); di.remove(); continue
                }
            }
            if (d.y < -5f || d.age > 300f) di.remove()
        }
    }

    fun lookDirX() = tvLook()[0]; fun lookDirY() = tvLook()[1]; fun lookDirZ() = tvLook()[2]
}
