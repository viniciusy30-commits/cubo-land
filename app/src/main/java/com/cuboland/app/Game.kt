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
               val color: Int, val size: Float, var life: Float)

class LeafP(var x: Float, var y: Float, var z: Float, var vx: Float, var vz: Float, val ph: Float, val color: Int, val size: Float, var life: Float, var landed: Boolean = false, var age: Float = 0f)

/** trecho de rachadura em coordenadas da face (0..1); aparece quando o crescimento g passa de t0 e termina em t1 */
class Seg(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val w: Float, val t0: Float, val t1: Float)

/** marca de impacto numa face do bloco. kind: 0 corte de machado, 1 furo de picareta, 2 talho de espada, 3 estocada, 4 amassado */
class Mark(val a: Int, val s: Int, val u: Float, val v: Float, val kind: Int, val ang: Float, val len: Float, val born: Float) {
    val segs = ArrayList<Seg>(); var g = 0f; var chips = FloatArray(0); var blobs = FloatArray(0); var gr = 0f
}

/** dano acumulado de um bloco: todas as marcas + progresso de quebra */
class Dmg(val x: Int, val y: Int, val z: Int, val id: Int) { var felled = false; var prog = 0f; var idle = 0f; val marks = ArrayList<Mark>() }

/** mini-bloco que voa e quica quando algo quebra */
class Debris(var x: Float, var y: Float, var z: Float, var vx: Float, var vy: Float, var vz: Float, val id: Int, val size: Float, var life: Float) {
    var rx = 0f; var ry = 0f; var rz = 0f; var vrx = 0f; var vry = 0f; var vrz = 0f; var ground = false
}

/** parte de cima de uma árvore caindo inteira. bl = (dx,dy,dz,id) por bloco, relativo ao bloco cortado */
class FallTree(val px: Float, val py: Float, val pz: Float, val dx: Float, val dz: Float, val bl: IntArray) {
    var ang = 0.03f; var vel = 0.25f; var t = 0f; val n = bl.size / 4
    val kx get() = dz; val kz get() = -dx     // eixo de rotação (horizontal, perpendicular à queda)
}

class Bolt(var x: Float, var y: Float, var z: Float, val vx: Float, val vy: Float, val vz: Float, var life: Float)

class Game(val world: World) {
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
        val g = if (e.inWater) 8f else 26f
        e.vy -= g * dt
        if (e.inWater) { e.vy = max(e.vy, -3f); e.vx *= 1f - 3f * dt; e.vz *= 1f - 3f * dt }
        if (e.vy < -40f) e.vy = -40f
        move(e, e.vx * dt, e.vy * dt, e.vz * dt)
    }

    fun burst(x: Float, y: Float, z: Float, color: Int, n: Int, speed: Float) {
        for (i in 0 until n) parts.add(Particle(x, y, z, (rnd.nextFloat() - 0.5f) * speed, rnd.nextFloat() * speed * 0.9f + 1f,
            (rnd.nextFloat() - 0.5f) * speed, color, 0.08f + rnd.nextFloat() * 0.1f, 0.6f + rnd.nextFloat() * 0.5f))
    }

    /** partículas fofinhas, só na cor do bloco (com leves variações de tom) */
    fun breakFx(bx: Int, by: Int, bz: Int, id: Int, big: Boolean) {
        val n = if (big) 22 else 6
        val base = B.top[id]
        for (i in 0 until n) {
            val k = 0.82f + rnd.nextFloat() * 0.36f
            val r = ((base shr 16 and 255) * k).toInt().coerceIn(0, 255); val gg = ((base shr 8 and 255) * k).toInt().coerceIn(0, 255); val b = ((base and 255) * k).toInt().coerceIn(0, 255)
            val px = bx + 0.15f + rnd.nextFloat() * 0.7f; val py = by + 0.15f + rnd.nextFloat() * 0.7f; val pz = bz + 0.15f + rnd.nextFloat() * 0.7f
            val sp = if (big) 2.6f else 1.5f
            parts.add(Particle(px, py, pz, (px - bx - 0.5f) * sp * 2f + (rnd.nextFloat() - 0.5f) * 0.6f, rnd.nextFloat() * sp * 0.8f + 0.9f,
                (pz - bz - 0.5f) * sp * 2f + (rnd.nextFloat() - 0.5f) * 0.6f, (r shl 16) or (gg shl 8) or b,
                0.045f + rnd.nextFloat() * (if (big) 0.075f else 0.045f), 0.7f + rnd.nextFloat() * 0.5f))
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
        atkCd = Items.cooldown(item); swing = 0f; bodyYaw = yaw; swingDur = Items.swingTime(item); powerSwing = false
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

        val key = ((hy.toLong() * World.SZ + hz) * World.SX + hx)
        val d = dmg.getOrPut(key) { Dmg(hx, hy, hz, id) }
        d.idle = 0f; brX = hx; brY = hy; brZ = hz
        d.prog += if (creative) 1f else efficiency(item, id) * mult / hardness(id)
        if (item == Items.SWORD && !creative) d.prog = min(d.prog, 0.5f)   // espada marca o bloco, mas não chega a quebrar
        hitPulse = 1f
        if (d.prog >= 1f) { dmg.remove(key); breakBlock(hx, hy, hz, id); return }
        if (id == B.WOOD && !d.felled && d.prog >= 0.65f) { d.felled = true; fellTree(hx, hy, hz) }   // tronco quase destruído: a parte de cima já tomba
        if (d.marks.size >= 7) d.marks.removeAt(0)
        d.marks.add(makeMark(a, s, fu, fv, item, mult > 1.5f, hitStab))
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
        spawnPieces(x + nx * 0.05f, y + ny * 0.05f, z + nz * 0.05f, id, if (big) 3 else 2, 0.11f, 0.08f, nx * 2.2f, ny * 2.2f, nz * 2.2f, 1.4f)   // pedacinhos arrancados
        val spark = when { item == Items.SWORD -> 0xCFF6FF; item == Items.PICK && (id == B.STONE || id == B.BRICK) -> 0xFFE7A0; else -> 0 }
        if (spark != 0) for (i in 0 until 3) parts.add(Particle(x, y, z, nx * 2.5f + (rnd.nextFloat() - 0.5f) * 2.5f, ny * 2.5f + rnd.nextFloat() * 2f + 1f, nz * 2.5f + (rnd.nextFloat() - 0.5f) * 2.5f, spark, 0.035f, 0.3f + rnd.nextFloat() * 0.2f))
    }

    // ---------- quebra: mini-blocos e árvore caindo ----------
    private fun spawnPieces(cx: Float, cy: Float, cz: Float, id: Int, n: Int, size: Float, spread: Float, vx0: Float = 0f, vy0: Float = 0f, vz0: Float = 0f, life: Float = 3.2f) {
        if (id <= 0 || id > 13) return
        for (i in 0 until n) {
            if (debris.size > 420) debris.removeAt(0)
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
        world.set(x, y, z, B.AIR)
        val away = player.x - (x + 0.5f); val awz = player.z - (z + 0.5f); val l = max(0.1f, hypot(away, awz))
        spawnPieces(x + 0.5f, y + 0.5f, z + 0.5f, id, if (id == B.LEAVES) 3 else 6, 0.27f, 0.6f, away / l * 0.8f, 1f, awz / l * 0.8f)
        breakFx(x, y, z, id, true)
        if (id == B.WOOD) fellTree(x, y, z)
    }

    /** corta o tronco: tudo de madeira/folha conectado acima do corte vira uma árvore que tomba */
    private fun fellTree(x: Int, y: Int, z: Int) {
        val s0 = world.get(x, y + 1, z)
        if (s0 != B.WOOD && s0 != B.LEAVES) return
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
        if (out.isEmpty()) return
        val arr = out.toIntArray()
        for (i in 0 until arr.size / 4) world.set(x + arr[i * 4], y + arr[i * 4 + 1], z + arr[i * 4 + 2], B.AIR)
        var dx = x + 0.5f - player.x; var dz = z + 0.5f - player.z
        if (hypot(dx, dz) < 0.2f) { dx = sin(yaw); dz = cos(yaw) }
        val an = atan2(dz, dx) + (rnd.nextFloat() - 0.5f) * 0.4f
        trees.add(FallTree(x + 0.5f, y + 0.5f, z + 0.5f, cos(an), sin(an), arr))
        shake = max(shake, 0.15f)
    }

    /** posição (relativa ao pivô) de um ponto da árvore depois de girar f.ang em volta do eixo horizontal */
    private fun treeRot(f: FallTree, rx: Float, ry: Float, rz: Float, o: FloatArray) {
        val kx = f.kx; val kz = f.kz; val c = cos(f.ang); val s = sin(f.ang); val kd = kx * rx + kz * rz
        o[0] = rx * c - kz * ry * s + kx * kd * (1f - c)
        o[1] = ry * c + (kz * rx - kx * rz) * s
        o[2] = rz * c + kx * ry * s + kz * kd * (1f - c)
    }

    private val tmpV = FloatArray(3)

    private fun treeHitsGround(f: FallTree): Boolean {
        if (f.ang < 0.12f) return false
        for (i in 0 until f.n) {
            treeRot(f, f.bl[i * 4].toFloat(), f.bl[i * 4 + 1].toFloat(), f.bl[i * 4 + 2].toFloat(), tmpV)
            val id = world.get(floor(f.px + tmpV[0]).toInt(), floor(f.py + tmpV[1]).toInt(), floor(f.pz + tmpV[2]).toInt())
            if (id != B.AIR && id != B.WATER && id != B.LEAVES && id != B.WOOD) return true
        }
        return false
    }

    /** a árvore bate no chão e vira uma chuva de mini-blocos, folhas e poeira */
    private fun shatterTree(f: FallTree) {
        val kx = f.kx; val kz = f.kz
        var leaves = 0
        for (i in 0 until f.n) {
            val id = f.bl[i * 4 + 3]
            treeRot(f, f.bl[i * 4].toFloat(), f.bl[i * 4 + 1].toFloat(), f.bl[i * 4 + 2].toFloat(), tmpV)
            val cx = f.px + tmpV[0]; val cy = f.py + tmpV[1]; val cz = f.pz + tmpV[2]
            val vx = f.vel * (-kz * tmpV[1]) * 0.7f; val vy = f.vel * (kz * tmpV[0] - kx * tmpV[2]) * 0.4f; val vz = f.vel * (kx * tmpV[1]) * 0.7f
            if (id == B.WOOD) spawnPieces(cx, cy, cz, id, 3, 0.36f, 0.5f, vx, vy, vz, 4f)
            else { spawnPieces(cx, cy, cz, id, 1, 0.34f, 0.5f, vx, vy, vz, 3f); leaves++
                if (leaves % 3 == 0 && leafFall.size < 140) leafFall.add(LeafP(cx, cy, cz, (rnd.nextFloat() - 0.5f) * 0.8f, (rnd.nextFloat() - 0.5f) * 0.8f, rnd.nextFloat() * 6.28f,
                    intArrayOf(0x4FA52E, 0x6CBF3C, 0x8AD453, 0x3E8A25, 0xA3DF6A)[rnd.nextInt(5)], 0.08f + rnd.nextFloat() * 0.05f, 7f)) }
            if (i % 5 == 0) burst(cx, cy, cz, if (id == B.WOOD) 0xC9A06A else 0x7FC84A, 3, 3f)
        }
        shake = max(shake, 0.55f)
        val dist = hypot(player.x - f.px, player.z - f.pz); if (dist > 18f) shake = 0.1f
    }

    private fun updateTrees(dt: Float) {
        val it = trees.iterator()
        while (it.hasNext()) {
            val f = it.next(); f.t += dt
            if (f.t < 0.45f) {   // estalo: a árvore treme antes de tombar
                f.ang = 0.03f + sin(f.t * 45f) * 0.012f
                if (rnd.nextFloat() < 0.35f && leafFall.size < 140) leafFall.add(LeafP(f.px + (rnd.nextFloat() - 0.5f) * 3f, f.py + 3f + rnd.nextFloat() * 3f, f.pz + (rnd.nextFloat() - 0.5f) * 3f, 0f, 0f, rnd.nextFloat() * 6f, 0x6CBF3C, 0.09f, 5f))
                continue
            }
            var done = false
            for (st in 0 until 3) {
                val h = dt / 3f
                f.vel += (6.5f * sin(f.ang) + 0.5f) * h; f.ang += f.vel * h
                if (f.ang >= 1.5708f) { f.ang = 1.5708f; done = true }
                else if (treeHitsGround(f)) done = true
                if (done) break
            }
            if (done) { shatterTree(f); it.remove() }
        }
    }

    private fun releasePower(power: Float) {
        val item = cur()
        swing = 0f; swingDur = if (item == Items.AXE) 0.58f else 0.46f; powerSwing = true; bodyYaw = yaw
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
            if (p.inWater) p.vy = 3.6f else if (p.onGround) { p.vy = 8.6f; p.onGround = false }
        }
        for (i in 0 until 2) physics(p, dt / 2f)
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
        hitPulse = max(0f, hitPulse - dt * 4.5f)
        val dI = dmg.values.iterator()
        while (dI.hasNext()) { val d = dI.next(); d.idle += dt; if (d.idle > 4f) d.prog -= dt * 0.2f; if (d.prog <= 0f || !world.solid(d.x, d.y, d.z)) dI.remove() }
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
        updateTrees(dt)
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
            q.life -= dt; q.vy -= 18f * dt
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
        val li = leafFall.iterator()
        while (li.hasNext()) {
            val l = li.next()
            l.age += dt; l.life -= dt
            if (!l.landed) {
                l.x += (l.vx + sin(l.age * 2.2f + l.ph) * 0.5f + cos(l.age * 1.3f + l.ph) * 0.25f) * dt
                l.z += (l.vz + cos(l.age * 1.9f + l.ph) * 0.4f + sin(l.age * 1.1f + l.ph) * 0.25f) * dt
                l.y -= (0.5f + 0.35f * abs(sin(l.age * 3.1f + l.ph))) * dt
                if (world.solid(floor(l.x).toInt(), floor(l.y).toInt(), floor(l.z).toInt())) { l.landed = true; l.life = min(l.life, 1.4f) }
            }
            if (l.life <= 0f || l.y < 0f) li.remove()
        }
        updateCamera()
        raycast(camX, camY, camZ, dirX(), dirY(), dirZ(), camDist, camDist + 5.5f)
    }

    fun lookDirX() = dirX(); fun lookDirY() = dirY(); fun lookDirZ() = dirZ()
}
