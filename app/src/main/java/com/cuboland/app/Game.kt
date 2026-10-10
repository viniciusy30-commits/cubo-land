package com.cuboland.app

import java.util.Random
import kotlin.math.*

open class Ent(var x: Float, var y: Float, var z: Float, val hw: Float, val h: Float) {
    var vx = 0f; var vy = 0f; var vz = 0f
    var onGround = false; var inWater = false
}

class Slime(x: Float, y: Float, z: Float) : Ent(x, y, z, 0.42f, 0.85f) {
    var hp = 6; var timer = 1f; var flash = 0f; var squash = 0f; var t = 0f; var dead = false
}

class Particle(var x: Float, var y: Float, var z: Float, var vx: Float, var vy: Float, var vz: Float,
               val color: Int, val size: Float, var life: Float) { var grav = 18f; var dust = false; var age = 0f }

class LeafP(var x: Float, var y: Float, var z: Float, var vx: Float, var vz: Float, val ph: Float, val color: Int, val size: Float, var life: Float, var landed: Boolean = false, var age: Float = 0f) {
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
    val slimes = ArrayList<Slime>()
    val parts = ArrayList<Particle>()
    @Volatile var attackHeld = false; @Volatile var attackPress = false; @Volatile var attackRelease = false
    @Volatile var charging = false; @Volatile var charge = 0f
    private var pressT = 0f; private var pressing = false; var swingDur = 0.46f; @Volatile var powerSwing = false
    @Volatile var creative = false; @Volatile var flying = false; @Volatile var downHeld = false; private var lastJumpT = -9f
    var brX = 0; var brY = -1; var brZ = 0; @Volatile var hitPulse = 0f; var rT = 0f
    val dmg = HashMap<Long, Dmg>(); val debris = ArrayList<Debris>(); val trees = ArrayList<FallTree>()
    private var hitT = -1f; private var hitItem = 0; private var hitStab = false
    private var pendingT = -1f; private var pendingPower = 0f
    val leafFall = ArrayList<LeafP>(); private var leafT = 0f; var comboT = 0f
    val drops = ArrayList<Drop>(); val ripples = ArrayList<Ripple>(); val prints = ArrayList<Foot>(); val picked = IntArray(16)
    @Volatile var pickMsg = ""; @Volatile var pickT = 0f; @Volatile var swimAmt = 0f
    private var stepDist = 0f; private var stepLeft = false; private var rippleT = 0f; private var bubbleT = 0f; private var regrowT = 0f
    // entrada (escrita pela UI)
    @Volatile var stickX = 0f; @Volatile var stickY = 0f
    @Volatile var jumpHeld = false; @Volatile var wantAttack = false; @Volatile var wantPlace = false
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

    init { respawn() }

    fun respawn() {
        val x = World.SX / 2; val z = World.SZ / 2
        player.x = x + 0.5f; player.z = z + 0.5f; player.y = world.surfaceY(x, z) + 0.05f
        player.vx = 0f; player.vy = 0f; player.vz = 0f
        hp = 10; slimes.clear()
    }

    @Synchronized fun addLook(dx: Float, dy: Float) { lookDx += dx; lookDy += dy }

    private fun collides(x: Float, y: Float, z: Float, hw: Float, h: Float): Boolean {
        for (bx in floor(x - hw).toInt()..floor(x + hw).toInt())
            for (bz in floor(z - hw).toInt()..floor(z + hw).toInt())
                for (by in floor(y).toInt()..floor(y + h).toInt())
                    if (world.solid(bx, by, bz)) return true
        return false
    }

    private fun move(e: Ent, dx: Float, dy: Float, dz: Float) {
        e.x += dx
        if (collides(e.x, e.y, e.z, e.hw, e.h)) {
            e.x = if (dx > 0) floor(e.x + e.hw) - e.hw - 0.001f else floor(e.x - e.hw) + 1 + e.hw + 0.001f; e.vx = 0f
        }
        e.z += dz
        if (collides(e.x, e.y, e.z, e.hw, e.h)) {
            e.z = if (dz > 0) floor(e.z + e.hw) - e.hw - 0.001f else floor(e.z - e.hw) + 1 + e.hw + 0.001f; e.vz = 0f
        }
        e.y += dy; e.onGround = false
        if (collides(e.x, e.y, e.z, e.hw, e.h)) {
            if (dy < 0) { e.y = floor(e.y) + 1.001f; e.onGround = true } else e.y = floor(e.y + e.h) - e.h - 0.002f
            e.vy = 0f
        }
        if (!e.onGround && dy <= 0f && e.vy <= 0f && collides(e.x, e.y - 0.03f, e.z, e.hw, e.h)) { e.onGround = true; e.vy = 0f }
        e.inWater = world.get(floor(e.x).toInt(), floor(e.y + 0.4f).toInt(), floor(e.z).toInt()) == B.WATER
    }

    fun jumpTap() {   // toque duplo no pulo (modo criativo) liga/desliga o voo
        if (!creative) return
        if (time - lastJumpT < 0.32f) { flying = !flying; if (flying) player.vy = 0f }
        lastJumpT = time
    }

    private fun physics(e: Ent, dt: Float) {
        if (flying && e === player) {
            val tv = (if (jumpHeld) 7f else 0f) - (if (downHeld) 7f else 0f)
            e.vy += (tv - e.vy) * min(1f, 8f * dt)
            move(e, e.vx * dt, e.vy * dt, e.vz * dt)
            return
        }
        if (e === player && e.inWater) {   // nado: flutua com a cabeça fora, mergulha olhando pra baixo e nadando, sobe olhando pra cima
            val surf = World.WATER_Y + 0.88f
            val mg = hypot(stickX, stickY)
            // empuxo de mola: a água empurra pra cima (mais forte quanto mais fundo), balança com as ondas e amortece o mergulho
            val bob = sin(time * 1.5f + e.x * 0.7f + e.z * 0.5f) * 0.05f + sin(time * 2.3f + e.z * 0.9f) * 0.025f
            var acc = (((surf - 1.05f) + bob - e.y) * 10f).coerceIn(-5f, 7f)
            if (downHeld) acc = -15f   // botão de afundar
            else if (jumpHeld) acc += 17f
            else if (mg > 0.15f && pitch < -0.28f) acc = sin(pitch) * 16f
            else if (mg > 0.15f && pitch > 0.28f) acc = sin(pitch) * 12f
            e.vy += (acc - 3.4f * e.vy) * dt
            e.vy = e.vy.coerceIn(-4.6f, 4.4f)
            e.vx *= 1f - 3f * dt; e.vz *= 1f - 3f * dt
            move(e, e.vx * dt, e.vy * dt, e.vz * dt)
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
            if (world.solid(cx, cy, cz)) {
                if (lx != Int.MIN_VALUE) { hasHit = true; hx = cx; hy = cy; hz = cz; px = lx; py = ly; pz = lz; rT = t }
                return
            }
            lx = cx; ly = cy; lz = cz
            t += 0.04f
        }
    }

    private fun dirX() = sin(yaw) * cos(pitch)
    private fun dirY() = sin(pitch)
    private fun dirZ() = cos(yaw) * cos(pitch)

    private fun updateCamera() {
        val ex = player.x; val ey = player.y + 1.6f; val ez = player.z
        if (!thirdPerson) { camX = ex; camY = ey; camZ = ez; camDist = 0f; return }
        val dx = -dirX(); val dy = -dirY(); val dz = -dirZ()
        var d = 0.3f
        while (d < 4.6f) {
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

    private fun hardness(id: Int) = when (id) { B.LEAVES -> 1.5f; B.GRASS, B.DIRT, B.SAND -> 3f; B.WOOD -> 8f; B.PLANK -> 6f; B.STONE -> 10f; B.BRICK -> 9f; else -> 4f }
    private fun efficiency(item: Int, id: Int) = when (item) {
        Items.PICK -> when (id) { B.STONE, B.BRICK -> 2f; B.DIRT, B.GRASS, B.SAND -> 1.5f; else -> 1f }
        Items.AXE -> when (id) { B.WOOD, B.PLANK -> 2f; B.LEAVES -> 1.5f; else -> 0.6f }
        else -> 0.7f
    }

    /** cada batida deixa uma marca exatamente onde a ferramenta acertou e avança a quebra; mult 2 = golpe carregado */
    private fun blockHit(item: Int, mult: Float) {
        raycast(camX, camY, camZ, dirX(), dirY(), dirZ(), camDist, camDist + 5.5f)
        if (!hasHit || hy <= 0) return
        val id = world.get(hx, hy, hz)
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
        if (id == B.WOOD && !d.felled && d.prog >= 0.65f) {
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


    private fun carvable(id: Int) = id in 1..13 && id != B.LEAVES && id != B.WATER && id != B.LANTERN

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
        val trunk = d.id == B.WOOD && a != 1
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
        if (id <= 0 || id > 13) return
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
        breakFx(x, y, z, id, true)
        val di = when (id) { B.GRASS -> B.DIRT; B.LEAVES -> if (rnd.nextFloat() < 0.3f) B.LEAVES else 0; else -> id }
        if (di != 0) dropItem(x + 0.5f, y + 0.4f, z + 0.5f, di, 0f, 0f)
        if (id == B.WOOD) fellTree(x, y, z)
    }

    /** o bloco que sobra no chão: só 1, pequeno, pra pegar */
    private fun dropItem(x: Float, y: Float, z: Float, id: Int, vx: Float, vz: Float) {
        if (id <= 0 || id > 13 || id == B.WATER) return
        if (drops.size > 60) drops.removeAt(0)
        var yy = y; var k = 0
        while (world.solid(floor(x).toInt(), floor(yy).toInt(), floor(z).toInt()) && k < 6) { yy += 0.5f; k++ }
        drops.add(Drop(x, yy, z, vx + (rnd.nextFloat() - 0.5f) * 1.4f, 3.4f, vz + (rnd.nextFloat() - 0.5f) * 1.4f, id))
    }

    private val leafCols = intArrayOf(0x4FA52E, 0x6CBF3C, 0x8AD453, 0x3E8A25, 0xA3DF6A, 0xC4E070, 0x7FC84A)
    /** a espada corta o bloco: ele vira, na hora, uma nuvem de folhinhas que escorrem no sentido do corte, girando e planando */
    private fun leafCutFx(x: Int, y: Int, z: Int, k: Int) {
        val cx = x + 0.5f; val cy = y + 0.5f; val cz = z + 0.5f
        val fx = dirX(); val fz = dirZ(); val hl = max(0.001f, hypot(fx, fz)); val fwx = fx / hl; val fwz = fz / hl
        val rxv = fwz; val rzv = -fwx; val sg = if (combo == 1) -1f else 1f   // direção lateral do talho
        val n = if (k == 0) 18 else 10
        if (leafFall.size < 260) for (i in 0 until n) {
            val ox = (rnd.nextFloat() - 0.5f) * 0.95f; val oy = (rnd.nextFloat() - 0.5f) * 0.95f; val oz = (rnd.nextFloat() - 0.5f) * 0.95f
            val along = (ox * rxv + oz * rzv) * sg    // posição ao longo do corte: quem está na frente voa mais alto (arco)
            val s = 1.5f + rnd.nextFloat() * 2.6f
            val l = LeafP(cx + ox, cy + oy, cz + oz,
                rxv * sg * s + fwx * (0.4f + rnd.nextFloat() * 1.5f) + (rnd.nextFloat() - 0.5f) * 0.8f,
                rzv * sg * s + fwz * (0.4f + rnd.nextFloat() * 1.5f) + (rnd.nextFloat() - 0.5f) * 0.8f,
                rnd.nextFloat() * 6.28f, leafCols[rnd.nextInt(leafCols.size)], 0.07f + rnd.nextFloat() * 0.07f, 3.4f + rnd.nextFloat() * 2.2f)
            l.vy = 0.5f + rnd.nextFloat() * 2.0f + along * 1.6f; l.burst = 0.6f + rnd.nextFloat() * 0.5f; l.spin = 1.4f + rnd.nextFloat() * 2.4f
            leafFall.add(l)
        }
        if (parts.size < 540) {
            for (i in 0 until 8) parts.add(Particle(cx + (rnd.nextFloat() - 0.5f) * 0.9f, cy + (rnd.nextFloat() - 0.5f) * 0.9f, cz + (rnd.nextFloat() - 0.5f) * 0.9f,
                rxv * sg * (1f + rnd.nextFloat() * 2.5f) + (rnd.nextFloat() - 0.5f), 0.5f + rnd.nextFloat() * 1.8f, rzv * sg * (1f + rnd.nextFloat() * 2.5f) + (rnd.nextFloat() - 0.5f),
                leafCols[rnd.nextInt(leafCols.size)], 0.03f + rnd.nextFloat() * 0.035f, 0.5f + rnd.nextFloat() * 0.5f).also { it.grav = 5f })
            for (i in 0 until 3) parts.add(Particle(cx + (rnd.nextFloat() - 0.5f) * 0.9f, cy + (rnd.nextFloat() - 0.5f) * 0.9f, cz + (rnd.nextFloat() - 0.5f) * 0.9f,
                rxv * sg * 0.8f, 0.4f + rnd.nextFloat() * 1.0f, rzv * sg * 0.8f, if (rnd.nextBoolean()) 0xF4FFB8 else 0xFFFFFF, 0.022f, 0.6f + rnd.nextFloat() * 0.5f).also { it.grav = 0.4f })
        }
        if (k == 0 && rnd.nextFloat() < 0.12f) dropItem(cx, cy, cz, B.LEAVES, 0f, 0f)
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
        if (id == B.LEAVES) {
            leaves = true
            val offs = if (hitStab) arrayOf(intArrayOf(0, 0, 0)) else arrayOf(intArrayOf(0, 0, 0), intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0), intArrayOf(0, 1, 0), intArrayOf(0, -1, 0), intArrayOf(0, 0, 1), intArrayOf(0, 0, -1))
            for (o in offs) {
                val x = bx + o[0]; val y = by + o[1]; val z = bz + o[2]
                if (world.get(x, y, z) != B.LEAVES) continue
                world.set(x, y, z, B.AIR); leafCutFx(x, y, z, if (o[0] == 0 && o[1] == 0 && o[2] == 0) 0 else 1)
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
        if (s0 != B.WOOD && s0 != B.LEAVES) return false
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
                if (nid != B.WOOD && nid != B.LEAVES) continue
                seen.add(k); q.add(k)
            }
        }
        if (out.isEmpty()) return false
        val arr = out.toIntArray()
        for (i in 0 until arr.size / 4) world.set(x + arr[i * 4], y + arr[i * 4 + 1], z + arr[i * 4 + 2], B.AIR)
        var dx = x + 0.5f - player.x; var dz = z + 0.5f - player.z
        if (hypot(dx, dz) < 0.2f) { dx = sin(yaw); dz = cos(yaw) }
        val an = atan2(dz, dx) + (rnd.nextFloat() - 0.5f) * 0.4f
        val ft = if (cutY.isNaN()) FallTree(x + 0.5f, y + 0.5f, z + 0.5f, cos(an), sin(an), arr) else FallTree(x + 0.5f, y + 0.5f + cutY, z + 0.5f, cos(an), sin(an), arr, -0.5f)   // com corte: pivô no plano do corte, base da árvore assenta nele
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

    /** a árvore encosta no chão quando a parte de baixo de algum bloco dela entra num bloco sólido (o toco é ignorado) */
    private fun treeHitsGround(f: FallTree): Boolean {
        if (f.ang < 0.2f) return false
        for (i in 0 until f.n) {
            treeRot(f, f.bl[i * 4].toFloat(), f.bl[i * 4 + 1] + f.yo, f.bl[i * 4 + 2].toFloat(), tmpV)
            val wx = floor(f.px + tmpV[0]).toInt(); val wy = floor(f.py - f.drop + tmpV[1] - 0.42f).toInt(); val wz = floor(f.pz + tmpV[2]).toInt()
            if (f.drop <= 0f && wx == f.sx && wy == f.sy && wz == f.sz) continue
            val id = world.get(wx, wy, wz)
            if (id != B.AIR && id != B.WATER && id != B.LEAVES && id != B.WOOD) return true
        }
        return false
    }

    /** a árvore bateu no chão: baque, poeira e folhas voando; ela fica deitada um instante antes de se desfazer */
    private fun landTree(f: FallTree) {
        f.landed = true; f.lt = 0f; f.vel = 0f
        for (i in 0 until f.n step 3) {
            val id = f.bl[i * 4 + 3]
            treeRot(f, f.bl[i * 4].toFloat(), f.bl[i * 4 + 1] + f.yo, f.bl[i * 4 + 2].toFloat(), tmpV)
            val cx = f.px + tmpV[0]; val cy = f.py - f.drop + tmpV[1]; val cz = f.pz + tmpV[2]
            if (id == B.WOOD) burst(cx, cy - 0.3f, cz, 0xC9B28A, 2, 2f)
            else if (leafFall.size < 140) leafFall.add(LeafP(cx, cy, cz, (rnd.nextFloat() - 0.5f) * 1.6f, (rnd.nextFloat() - 0.5f) * 1.6f, rnd.nextFloat() * 6.28f,
                intArrayOf(0x4FA52E, 0x6CBF3C, 0x8AD453, 0x3E8A25)[rnd.nextInt(4)], 0.09f, 5f))
        }
        shake = max(shake, 0.5f)
        val dist = hypot(player.x - f.px, player.z - f.pz); if (dist > 18f) shake = 0.1f
    }

    /** depois de deitada, a árvore vira partículas e só alguns troncos pra pegar */
    private fun shatterTree(f: FallTree) {
        var leaves = 0; var logs = 0
        for (i in 0 until f.n) {
            val id = f.bl[i * 4 + 3]
            treeRot(f, f.bl[i * 4].toFloat(), f.bl[i * 4 + 1] + f.yo, f.bl[i * 4 + 2].toFloat(), tmpV)
            val cx = f.px + tmpV[0]; val cy = f.py - f.drop + tmpV[1]; val cz = f.pz + tmpV[2]
            if (id == B.WOOD) {
                burst(cx, cy, cz, 0xB98A55, 4, 3f); burst(cx, cy, cz, 0x7A5230, 3, 3f)
                logs++; if (logs % 5 == 1 && logs <= 21) dropItem(cx, cy + 0.2f, cz, B.WOOD, 0f, 0f)
            } else {
                leaves++
                if (leaves % 2 == 0) burst(cx, cy, cz, intArrayOf(0x4FA52E, 0x6CBF3C, 0x8AD453, 0x3E8A25)[rnd.nextInt(4)], 3, 2.5f)
                if (leaves % 4 == 0 && leafFall.size < 140) leafFall.add(LeafP(cx, cy, cz, (rnd.nextFloat() - 0.5f) * 0.8f, (rnd.nextFloat() - 0.5f) * 0.8f, rnd.nextFloat() * 6.28f,
                    intArrayOf(0x4FA52E, 0x6CBF3C, 0x8AD453, 0x3E8A25, 0xA3DF6A)[rnd.nextInt(5)], 0.08f + rnd.nextFloat() * 0.05f, 7f))
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
        world.set(px, py, pz, cur())
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

    fun update(dt: Float) {
    for (sl in slashes) if (time - sl.born > SLASH_LIFE || world.get(sl.d.x, sl.d.y, sl.d.z) != sl.d.id) slashes.remove(sl)
        time += dt
        var ddx: Float; var ddy: Float
        synchronized(this) { ddx = lookDx; ddy = lookDy; lookDx = 0f; lookDy = 0f }
        yaw -= ddx * 0.0045f * sens; pitch = (pitch - ddy * 0.0045f * sens).coerceIn(-1.45f, 1.45f)
        if (wantCam) { wantCam = false; thirdPerson = !thirdPerson }
        atkCd -= dt; comboT -= dt; hurtCd -= dt; shake = max(0f, shake - dt * 1.5f); hurtFlash = max(0f, hurtFlash - dt * 2f)
        if (swing < 1f) swing = min(1f, swing + dt / swingDur)
        if (deadTimer > 0f) { deadTimer -= dt; if (deadTimer <= 0f) respawn(); updateCamera(); return }

        val p = player
        if (!creative) flying = false
        if (flying && p.onGround && downHeld) flying = false
        val sp = (if (p.inWater && !flying) 2.8f else if (flying) 9f else 5f) * (if (charging) 0.55f else 1f)
        val f = sin(yaw); val c = cos(yaw)
        val mx = (f * stickY + (-c) * stickX) * sp; val mz = (c * stickY + f * stickX) * sp
        val k = min(1f, (if (p.onGround) 14f else 5f) * dt)
        p.vx += (mx - p.vx) * k; p.vz += (mz - p.vz) * k
        val mag = hypot(stickX, stickY).coerceAtMost(1f)
        if (mag > 0.1f && thirdPerson && swing >= 1f) {
            val target = atan2(mx, mz)
            var diff = target - bodyYaw
            while (diff > PI) diff -= (2 * PI).toFloat(); while (diff < -PI) diff += (2 * PI).toFloat()
            bodyYaw += diff * min(1f, 12f * dt)
        } else if (!thirdPerson) bodyYaw = yaw
        if (jumpHeld && !flying) {
            if (p.inWater) { if (p.y > World.WATER_Y + 0.88f - 1.4f) p.vy = max(p.vy, 4.2f) } else if (p.onGround) { p.vy = 8.6f; p.onGround = false }
        }
        val wasW = p.inWater; val vyPre = p.vy
        for (i in 0 until 2) physics(p, dt / 2f)
        waterAndSteps(dt, wasW, vyPre)
        p.x = p.x.coerceIn(2f, World.SX - 2f); p.z = p.z.coerceIn(2f, World.SZ - 2f)
        if (p.y < -5f) respawn()
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
        hitPulse = max(0f, hitPulse - dt * 4.5f); pickT = max(0f, pickT - dt)
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
        if (spawnT <= 0f) { spawnT = 3f; if (slimes.size < 7) spawnSlime() }
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
        if (leafT <= 0f && leafFall.size < 30) {
            leafT = 0.2f + rnd.nextFloat() * 0.3f
            for (t in 0 until 28) {
                val x = (player.x + (rnd.nextFloat() - 0.5f) * 30f).toInt(); val z = (player.z + (rnd.nextFloat() - 0.5f) * 30f).toInt()
                val y = (player.y + (rnd.nextFloat() - 0.4f) * 18f).toInt()
                if (x < 1 || z < 1 || x >= World.SX - 1 || z >= World.SZ - 1 || y < 2 || y >= World.SY) continue
                if (world.get(x, y, z) != B.LEAVES || world.get(x, y - 1, z) != B.AIR) continue
                val cols = intArrayOf(0x4FA52E, 0x6CBF3C, 0x8AD453, 0x3E8A25, 0xA3DF6A, 0xC4E070, 0x7FC84A)
                leafFall.add(LeafP(x + rnd.nextFloat(), y - 0.05f, z + rnd.nextFloat(), (rnd.nextFloat() - 0.5f) * 0.4f + 0.2f, (rnd.nextFloat() - 0.5f) * 0.5f,
                    rnd.nextFloat() * 6.28f, cols[rnd.nextInt(cols.size)], 0.07f + rnd.nextFloat() * 0.05f, 9f))
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
        ripples.add(Ripple(x, z, amp, age0)); if (ripples.size > 7) ripples.removeAt(0)
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
            if (lg != B.AIR && lg != B.WATER) landDust(p.x, p.y, p.z, lg, -vyPre)
        }
        prevG = p.onGround
        val surf = World.WATER_Y + 0.88f
        val sp = hypot(p.vx, p.vz)
        swimAmt += ((if (p.inWater && !p.onGround && !flying) 1f else 0f) - swimAmt) * min(1f, 8f * dt)
        if (p.inWater && !wasW) {   // entrou na água: onda proporcional à velocidade da queda
            val v = max(0f, -vyPre)
            val amp = (0.3f + v * 0.1f).coerceAtMost(1.8f)
            addRipple(p.x, p.z, amp)
            if (v > 7f) addRipple(p.x, p.z, amp * 0.6f, -0.18f)
            splash(p.x, surf, p.z, v)
        } else if (!p.inWater && wasW) { addRipple(p.x, p.z, 0.4f); splash(p.x, surf, p.z, 2.5f) }
        if (p.inWater) {
            rippleT -= dt
            if (p.y + 1.9f > surf && rippleT <= 0f) {   // nadando na superfície: marolas contínuas
                rippleT = if (sp > 0.5f) 0.32f else 0.9f
                addRipple(p.x + (rnd.nextFloat() - 0.5f) * 0.3f, p.z + (rnd.nextFloat() - 0.5f) * 0.3f, if (sp > 0.5f) 0.42f else 0.18f)
            }
            val headUnder = world.get(floor(p.x).toInt(), floor(p.y + 1.55f).toInt(), floor(p.z).toInt()) == B.WATER
            bubbleT -= dt
            if (headUnder && bubbleT <= 0f) {
                bubbleT = 0.35f + rnd.nextFloat() * 0.5f
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
                    picked[d.id]++; pickMsg = "+1 " + B.names[d.id]; pickT = 1.6f
                    burst(d.x, d.y, d.z, B.top[d.id], 4, 1.5f); di.remove(); continue
                }
            }
            if (d.y < -5f || d.age > 300f) di.remove()
        }
    }

    fun lookDirX() = dirX(); fun lookDirY() = dirY(); fun lookDirZ() = dirZ()
}
