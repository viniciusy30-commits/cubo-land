package com.cuboland.app

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Carro esportivo vermelho dirigível. Física estilo jogo de corrida (arcade):
 * aceleração com limite de velocidade, freio/ré, esterçar só quando está andando,
 * aderência lateral (freio de mão solta a traseira e derrapa), cai com gravidade,
 * sobe degraus de 1 bloco e bate nas paredes.
 * Frente do carro = (sin h, cos h); direita = (-cos h, sin h).
 */
class CarState {
    var placed = false
    var x = 0f; var y = 0f; var z = 0f
    var h = 0f                       // direção (rad)
    var vx = 0f; var vy = 0f; var vz = 0f
    var steer = 0f                   // volante suavizado: -1 (esquerda) .. 1 (direita)
    var wheel = 0f                   // giro das rodas (graus)
    var roll = 0f; var pitchA = 0f   // inclinação visual da carroceria (graus)
    var yOff = 0f                    // suaviza o degrau (a câmera/visual sobe devagar)
    var onGround = false
    var inWater = false
    var braking = false              // luz de freio
    var reversing = false
    var slip = 0f                    // quanto está derrapando de lado
    var impact = 0f                  // força da última batida (o jogo lê e zera)
    var engine = 0f                  // 0..1 pra barulho/efeito
    private var lastVf = 0f

    val speed: Float get() = vx * sin(h) + vz * cos(h)
    fun kmh(): Int = (abs(speed) * 3.6f * 1.5f).toInt()

    // pontos de colisão (metade do comprimento 1.9, metade da largura 0.78)
    private val PX = floatArrayOf(0f, 0.78f, -0.78f, 0.78f, -0.78f, 0.78f, -0.78f)
    private val PZ = floatArrayOf(0f, 1.9f, 1.9f, -1.9f, -1.9f, 0f, 0f)

    fun blocked(px: Float, py: Float, pz: Float, coll: (Float, Float, Float) -> Boolean): Boolean {
        val sh = sin(h); val ch = cos(h)
        for (i in PX.indices) {
            // local (lx = direita, lz = frente) -> mundo
            val lx = PX[i]; val lz = PZ[i]
            val wx = px + (-ch) * lx + sh * lz
            val wz = pz + sh * lx + ch * lz
            if (coll(wx, py, wz)) return true
        }
        return false
    }

    fun update(dt: Float, gas: Float, brake: Float, steerIn: Float, hand: Boolean,
               coll: (Float, Float, Float) -> Boolean, water: (Float, Float, Float) -> Boolean) {
        val sh = sin(h); val ch = cos(h)
        var vf = vx * sh + vz * ch            // velocidade pra frente
        var vl = vx * (-ch) + vz * sh         // velocidade pro lado (direita +)
        val maxF = 32f; val maxR = 9f
        inWater = water(x, y + 0.3f, z)

        // ----- motor, freio e ré -----
        braking = false; reversing = false
        if (gas > 0f) {
            if (vf < -0.5f) { vf += 38f * dt; braking = true }      // estava de ré: freia antes de ir pra frente
            else {
                val k = 1f - (max(vf, 0f) / maxF)
                vf += 20f * gas * (if (k > 0f) k else 0f) * dt   // arranca forte e vai abrandando perto da velocidade máxima
            }
        }
        if (brake > 0f) {
            if (vf > 0.8f) { vf -= 40f * brake * dt; braking = true }
            else { vf -= 12f * brake * dt; if (vf < -maxR) vf = -maxR; reversing = true }
        }
        if (hand) {
            braking = true
            val fr = 16f * dt
            if (abs(vf) <= fr) vf = 0f else vf -= (if (vf > 0f) fr else -fr)
        }
        if (gas <= 0f && brake <= 0f) {                              // solto: freio-motor
            val fr = 4.5f * dt
            if (abs(vf) <= fr) vf = 0f else vf -= (if (vf > 0f) fr else -fr)
        }
        vf -= vf * 0.12f * dt                                         // resistência do ar
        if (inWater) { vf -= vf * 2.6f * dt; vl -= vl * 2.6f * dt }

        // ----- aderência: freio de mão solta a traseira (derrapa) -----
        val grip = if (hand) 1.5f else 9f
        vl -= vl * min(1f, grip * dt)
        slip = abs(vl)

        // ----- volante -----
        steer += (steerIn - steer) * min(1f, 8f * dt)
        val sp = abs(vf)
        val authority = (if (sp / 4f > 1f) 1f else sp / 4f) / (1f + sp / 20f)   // gira menos em velocidade alta
        val dir = if (vf >= 0f) 1f else -1f
        val turn = -steer * 2.5f * authority * dir * (if (hand) 1.7f else 1f) * dt

        // velocidade no referencial ANTES de girar: o carro "escorrega" um pouco nas curvas rápidas
        vx = vf * sh + vl * (-ch)
        vz = vf * ch + vl * sh
        h += turn
        if (blocked(x, y, z, coll)) h -= turn      // não gira pra dentro da parede
        if (blocked(x, y, z, coll)) { y += 5f * dt; vy = 0f }   // preso dentro de um bloco: sobe pra sair

        // ----- mover com colisão (eixo por eixo) -----
        var hitSpeed = 0f
        val nx = x + vx * dt
        if (!blocked(nx, y, z, coll)) x = nx
        else if (onGround && !blocked(nx, y + 1.02f, z, coll) && !blocked(x, y + 1.02f, z, coll)) { x = nx; y += 1f; yOff -= 1f }
        else { hitSpeed = max(hitSpeed, abs(vx)); vx = -vx * 0.15f }
        val nz = z + vz * dt
        if (!blocked(x, y, nz, coll)) z = nz
        else if (onGround && !blocked(x, y + 1.02f, nz, coll) && !blocked(x, y + 1.02f, z, coll)) { z = nz; y += 1f; yOff -= 1f }
        else { hitSpeed = max(hitSpeed, abs(vz)); vz = -vz * 0.15f }
        if (hitSpeed > 4f) impact = max(impact, hitSpeed)

        // ----- gravidade -----
        vy -= 26f * dt
        if (vy < -40f) vy = -40f
        val ny = y + vy * dt
        if (blocked(x, ny, z, coll)) {
            if (vy < 0f) {
                var lo = y; var hi = ny
                for (i in 0 until 8) { val mid = (lo + hi) / 2f; if (blocked(x, mid, z, coll)) hi = mid else lo = mid }
                y = lo
                if (vy < -9f) impact = max(impact, -vy * 0.6f)
                onGround = true
            }
            vy = 0f
        } else { y = ny; onGround = false }

        // ----- rodas e inclinação da carroceria -----
        wheel += vf * dt / 0.31f * 57.3f
        if (wheel > 36000f || wheel < -36000f) wheel = 0f
        val accel = if (dt > 0f) (vf - lastVf) / dt else 0f
        lastVf = vf
        val rollT = -steer * min(1f, sp / 18f) * 4f
        val pitchT = (-accel * 0.22f).coerceIn(-3.5f, 3.5f)
        roll += (rollT - roll) * min(1f, 6f * dt)
        pitchA += (pitchT - pitchA) * min(1f, 6f * dt)
        yOff += (0f - yOff) * min(1f, 9f * dt)
        engine += (min(1f, sp / maxF + gas * 0.4f) - engine) * min(1f, 5f * dt)
    }

    /** posiciona o carro à frente de (px,pz) olhando pra direção ang; devolve false se não achou lugar */
    fun spawn(px: Float, py: Float, pz: Float, ang: Float, solid: (Int, Int, Int) -> Boolean,
              coll: (Float, Float, Float) -> Boolean): Boolean {
        val dists = floatArrayOf(5.5f, 4f, 7f, 3f, 8.5f)
        h = ang
        for (d in dists) {
            val sx = px + sin(ang) * d; val sz = pz + cos(ang) * d
            var by = floor(py + 2.5f).toInt()
            val lim = floor(py - 6f).toInt()
            while (by > lim && !solid(floor(sx).toInt(), by, floor(sz).toInt())) by--
            if (by <= lim) continue
            val gy = by + 1.02f     // topo do bloco + folguinha (blocos parciais acertam na queda)
            var ok = !blocked(sx, gy, sz, coll)
            if (!ok && !blocked(sx, gy + 1f, sz, coll)) { ok = true }
            if (ok) {
                x = sx; z = sz; y = if (!blocked(sx, gy, sz, coll)) gy else gy + 1f
                vx = 0f; vy = 0f; vz = 0f; steer = 0f; roll = 0f; pitchA = 0f; yOff = 0f; lastVf = 0f
                placed = true
                return true
            }
        }
        return false
    }
}
