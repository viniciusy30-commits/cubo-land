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

    private fun physics(e: Ent, dt: Float) {
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
        atkCd = Items.cooldown(item); swing = 0f; bodyYaw = yaw
        if (item == Items.SWORD) combo = (combo + 1) % 5   // cada ataque da espada muda o ângulo do corte
        updateCamera()
        if (item == Items.STAFF) { shoot(); return }
        val reach = Items.reach(item)
        var best: Slime? = null; var bd = 9999f
        for (s in slimes) {
            if (s.dead) continue
            val dx = s.x - player.x; val dy = s.y + 0.4f - (player.y + 1f); val dz = s.z - player.z
            val dist = sqrt(dx * dx + dy * dy + dz * dz)
            if (dist > reach) continue
            val dot = (dx * sin(yaw) + dz * cos(yaw)) / max(0.001f, sqrt(dx * dx + dz * dz))
            if (dot > 0.5f && dist < bd) { bd = dist; best = s }
        }
        if (best != null) { hitSlime(best, Items.damage(item), best.x - player.x, best.z - player.z, Items.knock(item)); return }
        if (!Items.breaks(item)) return
        raycast(camX, camY, camZ, dirX(), dirY(), dirZ(), camDist, camDist + 5.5f)
        if (hasHit && hy > 0) {
            val id = world.get(hx, hy, hz)
            burst(hx + 0.5f, hy + 0.5f, hz + 0.5f, B.top[id], 14, 4f)
            world.set(hx, hy, hz, B.AIR)
        }
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
        if (hurtCd > 0f) return
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
        atkCd -= dt; hurtCd -= dt; shake = max(0f, shake - dt * 1.5f); hurtFlash = max(0f, hurtFlash - dt * 2f)
        if (swing < 1f) swing = min(1f, swing + dt / Items.swingTime(cur()))
        if (deadTimer > 0f) { deadTimer -= dt; if (deadTimer <= 0f) respawn(); updateCamera(); return }

        val p = player
        val sp = if (p.inWater) 2.8f else 5f
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
        if (jumpHeld) {
            if (p.inWater) p.vy = 3.6f else if (p.onGround) { p.vy = 8.6f; p.onGround = false }
        }
        for (i in 0 until 2) physics(p, dt / 2f)
        p.x = p.x.coerceIn(2f, World.SX - 2f); p.z = p.z.coerceIn(2f, World.SZ - 2f)
        if (p.y < -5f) respawn()
        walkAmt += (min(1f, mag) * (if (p.onGround) 1f else 0.4f) - walkAmt) * min(1f, 10f * dt)
        walkPhase += dt * 9f * walkAmt

        if (wantAttack) { wantAttack = false; attack() }
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
        val pi = parts.iterator()
        while (pi.hasNext()) {
            val q = pi.next()
            q.life -= dt; q.vy -= 18f * dt
            val nx = q.x + q.vx * dt; val ny = q.y + q.vy * dt; val nz = q.z + q.vz * dt
            if (world.solid(floor(nx).toInt(), floor(ny).toInt(), floor(nz).toInt())) { q.vx *= 0.3f; q.vz *= 0.3f; q.vy = 0f }
            else { q.x = nx; q.y = ny; q.z = nz }
            if (q.life <= 0f) pi.remove()
        }
        updateCamera()
        raycast(camX, camY, camZ, dirX(), dirY(), dirZ(), camDist, camDist + 5.5f)
    }

    fun lookDirX() = dirX(); fun lookDirY() = dirY(); fun lookDirZ() = dirZ()
}
