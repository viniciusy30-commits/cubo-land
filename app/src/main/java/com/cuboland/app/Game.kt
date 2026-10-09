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

class BreakAnim(val x: Int, val y: Int, val z: Int, val id: Int, val pat: Int) { var t = 0f; var fx = false }

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
    var brX = 0; var brY = -1; var brZ = 0; @Volatile var brProg = 0f; private var brT = 0f; @Volatile var hitPulse = 0f; @Volatile var brPat = 0; val anims = ArrayList<BreakAnim>()
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
                if (lx != Int.MIN_VALUE) { hasHit = true; hx = cx; hy = cy; hz = cz; px = lx; py = ly; pz = lz }
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
        if (!Items.breaks(item)) return
        blockHit(item, 1f)
    }

    private fun hardness(id: Int) = when (id) { B.LEAVES -> 1.5f; B.GRASS, B.DIRT, B.SAND -> 3f; B.WOOD -> 8f; B.PLANK -> 6f; B.STONE -> 10f; B.BRICK -> 9f; else -> 4f }
    private fun efficiency(item: Int, id: Int) = when (item) {
        Items.PICK -> when (id) { B.STONE, B.BRICK -> 2f; B.DIRT, B.GRASS, B.SAND -> 1.5f; else -> 1f }
        Items.AXE -> when (id) { B.WOOD, B.PLANK -> 2f; B.LEAVES -> 1.5f; else -> 0.6f }
        else -> 0.7f
    }

    /** cada batida avança o estágio de quebra do bloco mirado; mult 2 = golpe carregado */
    private fun blockHit(item: Int, mult: Float) {
        raycast(camX, camY, camZ, dirX(), dirY(), dirZ(), camDist, camDist + 5.5f)
        if (!hasHit || hy <= 0) return
        val id = world.get(hx, hy, hz)
        if (brY != hy || brX != hx || brZ != hz || brT <= 0f) { brX = hx; brY = hy; brZ = hz; brProg = 0f; brPat = rnd.nextInt(Atlas.CK_PAT) }
        brT = 3f
        brProg += if (creative) 1f else efficiency(item, id) * mult / hardness(id)
        if (brProg >= 1f) {
            anims.add(BreakAnim(hx, hy, hz, id, brPat))
            world.set(hx, hy, hz, B.AIR); brProg = 0f; brY = -1; brT = 0f
        } else {
            breakFx(hx, hy, hz, id, false); hitPulse = 1f
            shake = max(shake, 0.06f * mult)
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
        if (!anyHit && Items.breaks(item)) { updateCamera(); blockHit(item, 2f) }   // golpe carregado: 2x de progresso na quebra
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
        brT -= dt; if (brT <= 0f) brProg = 0f; hitPulse = max(0f, hitPulse - dt * 4.5f)
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
        val ai = anims.iterator()
        while (ai.hasNext()) {
            val a = ai.next(); a.t += dt
            if (!a.fx && a.t >= 0.1f) { a.fx = true; breakFx(a.x, a.y, a.z, a.id, true) }
            if (a.t > 0.42f) ai.remove()
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
