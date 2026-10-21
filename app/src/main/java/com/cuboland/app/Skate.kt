package com.cuboland.app

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Skate estilo Tony Hawk (PS2): empurrar, virar, OLLIE (segurar = pulo mais alto), FLIPS no ar (kickflip, heelflip,
 * shove-it, 360 flip, varial), GRABS (indy, melon, mute, nose, tail), giros de 180 em 180, GRINDS em quinas de blocos
 * (50-50, boardslide, nosegrind, tailslide) com barra de EQUILÍBRIO, combo com multiplicador e BAIL (queda) se errar.
 * Frente da prancha = (sin h, cos h); direita = (-cos h, sin h). Origem = ponto de contato das rodas com o chão.
 */
class SkateState {
    var placed = false
    var x = 0f; var y = 0f; var z = 0f
    var h = 0f
    var vx = 0f; var vy = 0f; var vz = 0f
    var onGround = false
    var inWater = false

    // ----- visual (o renderer lê) -----
    var steer = 0f            // -1 esquerda .. 1 direita (suavizado)
    var lean = 0f             // graus, + = inclina pra direita
    var pitchA = 0f           // graus, + = bico pra cima
    var wheel = 0f            // giro das rodas (graus)
    var yOff = 0f
    var crouch = 0f           // 0..1 agachado (o boneco lê)
    var stance = 1f           // 1 normal, -1 "switch" (depois de 180 ímpar)
    var spin = 0f             // giro visual do conjunto boneco+prancha (graus)
    var tumble = 0f           // prancha rodopiando depois de um tombo
    var flipRoll = 0f; var flipYaw = 0f
    var gYaw = 0f; var gPitch = 0f
    var grab = 0; var grabA = 0f
    var charge = 0f
    var bailT = 0f
    var grinding = false
    var bal = 0f              // -1..1: equilíbrio no grind (1 ou -1 = cai)
    var gKind = 0
    // ----- novos (v2) -----
    var pushPh = 0f           // fase do empurrão (0..1): perna de trás raspa no chão
    var pushW = 0f            // 0..1 quanto está empurrando
    var drift = 0f            // 0..1 derrapando (powerslide)
    var driftYaw = 0f         // graus: a prancha atravessa na derrapagem (visual)
    var lift = 0f             // a prancha se afasta dos pés durante flips (metros)
    var flipF = 0f            // 0..1 progresso do flip
    var popK = 0f             // 1 -> 0 logo depois do ollie (corpo estica)
    var landK = 0f            // 1 -> 0 logo depois do pouso (corpo amortece)
    var speedK = 0f           // 0..1 velocidade (FOV, linhas de vento)
    var manual = false        // equilibrando nas rodas de trás
    var manT = 0f
    var bannerPop = 0f        // 1 -> 0: letreiro "pula" quando aparece
    var perfectT = 0f
    var evDrift = false
    var evSlam = false
    var evPerfect = false

    // ----- eventos pro jogo (ele lê e zera) -----
    var evPop = false; var evLand = false; var evBail = false
    var impact = 0f
    var landVy = 0f

    // ----- pontuação -----
    var score = 0
    var comboPts = 0; var comboN = 0; var comboText = ""
    var banner = ""; var bannerT = 0f; var bannerGood = true
    var best = 0

    val speed: Float get() = vx * sin(h) + vz * cos(h)
    fun kmh(): Int = (abs(speed) * 3.6f * 1.5f).toInt()

    // ----- estado interno -----
    private var charging = false
    private var prevOllie = false
    private var flipT = -1f; private var flipKind = 0
    private var flipScored = false
    private var grabTime = 0f
    private var gv = 0f; private var gTime = 0f; private var gSign = 1f; private var gAcc = 0f; private var gSeed = 0f
    private var balV = 0f
    private var grindCool = 0f
    private var popT = 0f
    private var t = 0f
    private var lastVf = 0f
    private var driftT = 0f
    private var wasDrifting = false
    private var manCool = 0f
    private var groundT = 0f

    companion object {
        const val MAXF = 14.5f
        const val GRAV = 24f
        val FLIP_NAMES = arrayOf("Kickflip", "Heelflip", "Pop Shove-it", "360 Flip", "Varial Flip")
        val FLIP_PTS = intArrayOf(100, 100, 120, 300, 220)
        val GRAB_NAMES = arrayOf("", "Indy", "Melon", "Mute", "Nosegrab", "Tailgrab")
        val GRIND_NAMES = arrayOf("50-50", "Boardslide", "Nosegrind", "Tailslide")
        val GRIND_RATE = floatArrayOf(120f, 200f, 160f, 160f)
    }

    // ---------------------------------------------------------------- combo
    private fun addTrick(name: String, pts: Int) {
        comboN++; comboPts += pts
        comboText = if (comboText.isEmpty() || comboText.length > 36) name else "$comboText + $name"
        banner = comboText; bannerT = 2f; bannerGood = true; bannerPop = 1f
    }

    private fun rate(tot: Int): String =
        if (tot >= 2500) "INSANO! " else if (tot >= 1200) "FODA! " else if (tot >= 600) "SICK! " else if (tot >= 250) "BOA! " else ""

    private fun bank() {
        if (comboN > 0) {
            val tot = comboPts * comboN
            score += tot
            if (tot > best) best = tot
            banner = "${rate(tot)}$comboText  +$tot"; bannerT = 3.0f; bannerGood = true; bannerPop = 1f
        }
        comboPts = 0; comboN = 0; comboText = ""; gAcc = 0f
    }

    private fun lose() {
        if (comboN > 0) { banner = "COMBO PERDIDO"; bannerT = 1.8f; bannerGood = false; bannerPop = 1f }
        comboPts = 0; comboN = 0; comboText = ""; gAcc = 0f
    }

    private fun endGrab() {
        if (grab != 0 && grabTime > 0.2f) addTrick(GRAB_NAMES[grab], 80 + (grabTime * 100f).toInt())
        grab = 0; grabTime = 0f
    }

    private fun bail() {
        if (bailT > 0f) return
        bailT = 1.5f; evBail = true; lose()
        flipT = -1f; flipRoll = 0f; flipYaw = 0f; grab = 0; grabTime = 0f
        grinding = false; charging = false; charge = 0f; spin = 0f; gYaw = 0f; gPitch = 0f
        tumble = 1f; manual = false; drift = 0f
    }

    // ---------------------------------------------------------------- grind
    private fun edgeAt(solid: (Int, Int, Int) -> Boolean, cx: Int, by: Int, cz: Int): Boolean =
        !solid(cx + 1, by, cz) || !solid(cx - 1, by, cz) || !solid(cx, by, cz + 1) || !solid(cx, by, cz - 1)

    /** topo de um bloco de quina logo abaixo da prancha (pra grindar), ou -1 */
    private fun railTop(solid: (Int, Int, Int) -> Boolean): Float {
        val cx = floor(x).toInt(); val cz = floor(z).toInt()
        var by = floor(y + 0.4f).toInt()
        val lo = floor(y - 0.6f).toInt()
        while (by >= lo) {
            if (solid(cx, by, cz)) {
                if (solid(cx, by + 1, cz) || !edgeAt(solid, cx, by, cz)) return -1f
                val top = (by + 1).toFloat()
                return if (y >= top - 0.15f && y <= top + 0.65f) top else -1f
            }
            by--
        }
        return -1f
    }

    private fun startGrind(top: Float, steerIn: Float, gas: Float, brake: Float, vf0: Float) {
        grinding = true; grindCool = 0f; charging = false; charge = 0f
        manual = false
        val q = (Math.PI / 2.0).toFloat()
        h = Math.round(h / q) * q            // alinha com o eixo da quina
        gv = if (vf0 >= 0f) max(vf0, 4f) else min(vf0, -4f)
        vy = 0f; y = top; onGround = true
        gKind = if (abs(steerIn) > 0.4f) 1 else if (gas > 0.5f) 2 else if (brake > 0.5f) 3 else 0
        gSign = if (steerIn < 0f) -1f else 1f
        bal = 0f; balV = 0f; gTime = 0f; gAcc = 0f
        gSeed = (x * 7.3f + z * 3.1f) % 6.28f
        if (flipT >= 0f) { flipT = -1f; flipRoll = 0f; flipYaw = 0f }
        endGrab()
        // gira o visual pra mais perto de múltiplo de 180 (já fica alinhado ao sair)
        spin = 0f
        addTrick(GRIND_NAMES[gKind], 60)
    }

    private fun endGrind() {
        grinding = false; grindCool = 0.3f
        val sh = sin(h); val ch = cos(h)
        vx = sh * gv; vz = ch * gv
    }

    private fun grindStep(dt: Float, steerIn: Float, ollieEdge: Boolean,
                          coll: (Float, Float, Float) -> Boolean, solid: (Int, Int, Int) -> Boolean) {
        gTime += dt
        val sh = sin(h); val ch = cos(h)
        // equilíbrio: instável sozinho; incline pro lado oposto da seta
        val wob = sin(t * 3.3f + gSeed) * 0.8f + sin(t * 1.7f + gSeed * 2f) * 0.6f
        balV += (bal * 2.6f + wob) * dt
        balV += steerIn * 7.5f * dt
        balV -= balV * min(1f, 1.8f * dt)
        bal += balV * dt
        if (abs(bal) >= 1f) { endGrind(); bail(); return }
        // pontos do grind
        gAcc += GRIND_RATE[gKind] * (1f + gTime * 0.6f) * dt
        if (gAcc >= 10f) { val k = gAcc.toInt(); comboPts += k; gAcc -= k.toFloat() }
        // sai pulando
        if (ollieEdge) {
            endGrind(); vy = 8.6f; onGround = false; y += 0.05f; evPop = true; popT = 0.18f; popK = 1f
            return
        }
        gv -= (if (gv > 0f) 1f else -1f) * 0.9f * dt
        if (abs(gv) < 1.8f) { endGrind(); vy = 0f; return }
        val nx = x + sh * gv * dt; val nz = z + ch * gv * dt
        val cx = floor(nx).toInt(); val cz = floor(nz).toInt(); val by = floor(y - 0.02f).toInt()
        val ok = solid(cx, by, cz) && !solid(cx, by + 1, cz) && edgeAt(solid, cx, by, cz)
        if (coll(nx, y, nz)) { endGrind(); vx *= 0.15f; vz *= 0.15f; return }
        if (!ok) { endGrind(); vy = 1.2f; onGround = false; return }
        x = nx; z = nz; y = (by + 1).toFloat()
        vx = sh * gv; vz = ch * gv; vy = 0f
        evGrindSpark = true
    }

    var evGrindSpark = false

    // ---------------------------------------------------------------- aterrissagem
    private fun handleLand(preVy: Float, ctl: Boolean) {
        evLand = true; landVy = -preVy; landK = (-preVy / 16f).coerceIn(0.25f, 1f)
        if (preVy < -21f && ctl) { bail(); return }
        if (!ctl) return
        val m = ((spin % 180f) + 180f) % 180f
        val rem = if (m > 90f) m - 180f else m
        if (abs(rem) > 48f) { bail(); return }
        val n = Math.round((spin - rem) / 180f)
        if (n != 0) {
            addTrick("${abs(n) * 180}", 140 * abs(n))
            if (n % 2 != 0) stance = -stance
        }
        if (abs(rem) < 14f && comboN > 0 && grab == 0) { comboPts += 50; perfectT = 1.3f; evPerfect = true }
        spin = rem
        if (grab != 0) endGrab()
        if (flipT >= 0f && !flipScored) {
            if (flipT >= 0.6f) { addTrick(FLIP_NAMES[flipKind], FLIP_PTS[flipKind]); flipScored = true }
            else flipScored = true     // não completou: sem pontos, só termina a animação
        }
        bank()
        if (preVy < -9f) impact = max(impact, -preVy * 0.45f)
        if (preVy < -13f) evSlam = true
    }

    // ---------------------------------------------------------------- atualização
    fun update(dt: Float, gas: Float, brake: Float, steerIn: Float, spinIn: Float, ollieHeld: Boolean,
               flipPress: Boolean, grabHeld: Boolean, grindHeld: Boolean,
               coll: (Float, Float, Float) -> Boolean, solid: (Int, Int, Int) -> Boolean,
               water: (Float, Float, Float) -> Boolean) {
        t += dt
        if (bannerT > 0f) bannerT -= dt
        grindCool -= dt; manCool -= dt
        popT = max(0f, popT - dt)
        bannerPop = max(0f, bannerPop - dt * 3.2f); popK = max(0f, popK - dt * 3.5f); landK = max(0f, landK - dt * 3.2f); perfectT = max(0f, perfectT - dt)
        if (bailT > 0f) { bailT -= dt; if (bailT <= 0f) { bailT = 0f; tumble = 0f } }
        val ctl = bailT <= 0f
        val ollieEdge = ollieHeld && !prevOllie
        prevOllie = ollieHeld
        inWater = water(x, y + 0.3f, z)

        // ----- no grind: movimento próprio -----
        if (grinding) {
            grindStep(dt, steerIn, ollieEdge, coll, solid)
            if (grinding) { visuals(dt, steerIn, gas, gv); return }
        }

        val sh = sin(h); val ch = cos(h)
        var vf = vx * sh + vz * ch
        var vl = vx * (-ch) + vz * sh
        val wasGround = onGround

        // ----- derrapagem (FREIO + direção em velocidade) e MANUAL (GRAB no chão) -----
        val drifting = ctl && onGround && !grinding && brake > 0.3f && abs(steerIn) > 0.35f && vf > 5f
        if (drifting) { driftT += dt; evDrift = true }
        else if (wasDrifting) {
            if (driftT > 0.55f && comboN >= 0) addTrick("Powerslide", 70 + (driftT * 55f).toInt())
            driftT = 0f
        }
        wasDrifting = drifting
        drift += ((if (drifting) 1f else 0f) - drift) * min(1f, 8f * dt)

        val manualOn = ctl && onGround && !grinding && grabHeld && vf > 2.8f && !drifting && manCool <= 0f
        if (manualOn) {
            if (!manual) { manual = true; manT = 0f; bal = 0f; balV = 0f; gAcc = 0f; addTrick("Manual", 40) }
            manT += dt
            val wob = sin(t * 2.6f + 0.7f) * 0.7f + sin(t * 1.3f) * 0.5f
            balV += (bal * 2.3f + wob * 0.55f) * dt
            balV += (brake - gas) * 6.5f * dt           // acelerar empurra o bico pra baixo, freio puxa pra trás
            balV -= balV * min(1f, 1.7f * dt)
            bal += balV * dt
            gAcc += 95f * (1f + manT * 0.5f) * dt
            if (gAcc >= 10f) { val k = gAcc.toInt(); comboPts += k; gAcc -= k.toFloat() }
            if (abs(bal) >= 1f) { manual = false; bal = 0f; balV = 0f; manCool = 0.9f; lose() }
        } else if (manual) { manual = false; bal = 0f; balV = 0f; gAcc = 0f }

        // combo de chão (manual/powerslide) é guardado depois de 0,7 s rodando limpo
        if (onGround && ctl && !manual && !drifting && !grinding && !charging) groundT += dt else groundT = 0f
        if (groundT > 0.7f && comboN > 0) bank()

        // ----- empurrar / frear / atrito -----
        if (onGround) {
            if (ctl && !manual) {
                if (gas > 0f) { val k = 1f - max(vf, 0f) / MAXF; vf += 11.5f * gas * (if (k > 0f) k else 0f) * dt }
                if (brake > 0f) {
                    if (drifting) vf -= 4.5f * brake * dt
                    else if (vf > 0.5f) vf -= 16f * brake * dt
                    else { vf -= 5f * brake * dt; if (vf < -3.5f) vf = -3.5f }
                }
            }
            vf -= vf * min(1f, (if (!ctl) 3.2f else if (manual) 0.2f else 0.26f) * dt)
            if (inWater) vf -= vf * min(1f, 3f * dt)
            vl -= vl * min(1f, (if (drifting) 2.0f else 16f) * dt)
        } else {
            vf -= vf * 0.02f * dt
        }

        // ----- esterçar -----
        steer += ((if (ctl && onGround) steerIn else 0f) - steer) * min(1f, 9f * dt)
        var turn = 0f
        if (ctl && onGround) {
            val sp = abs(vf)
            // Direção progressiva: fácil manobrar devagar, menos nervosa em alta velocidade.
            val auth = min(1f, 0.42f + sp / 5.5f) / (1f + sp / 28f)
            val dir = if (vf >= -0.3f) 1f else -1f
            turn = -steer * 2.35f * auth * dir * dt * (1f + 0.85f * drift)
        }
        vx = vf * sh + vl * (-ch)
        vz = vf * ch + vl * sh
        h += turn

        // ----- giro no ar (botões GIRAR) -----
        if (ctl && !onGround) {
            spin += -spinIn * 620f * dt
            if (spin > 1100f) spin = 1100f else if (spin < -1100f) spin = -1100f
        }

        // ----- flips -----
        if (ctl && !onGround && flipPress && flipT < 0f && grab == 0) {
            flipKind = if (gas > 0.5f) 3 else if (brake > 0.5f) 4 else if (steerIn < -0.4f) 1 else if (steerIn > 0.4f) 2 else 0
            flipT = 0f; flipScored = false
        }
        if (flipT >= 0f) {
            flipT += dt / 0.5f * (if (onGround) 5f else 1f)
            if (flipT >= 1f) {
                if (!flipScored && ctl) { addTrick(FLIP_NAMES[flipKind], FLIP_PTS[flipKind]); flipScored = true }
                flipT = -1f
            }
        }

        // ----- grabs -----
        if (ctl && !onGround && grabHeld && flipT < 0f) {
            if (grab == 0) {
                grab = if (gas > 0.5f) 4 else if (brake > 0.5f) 5 else if (steerIn < -0.4f) 2 else if (steerIn > 0.4f) 3 else 1
                grabTime = 0f
            }
            grabTime += dt
        } else if (grab != 0) endGrab()

        // ----- ollie: segurar carrega, soltar pula -----
        if (ctl && onGround) {
            if (ollieHeld) { charging = true; charge = min(1f, charge + dt / 0.5f) }
            else if (charging) {
                vy = 7.8f + 5.0f * charge
                onGround = false; charging = false; charge = 0f; evPop = true; popT = 0.18f; popK = 1f; manual = false; bal = 0f
            }
        } else { charging = false; charge = 0f }

        // ----- tentar grindar -----
        if (ctl && grindHeld && grindCool <= 0f && vy < 6f && abs(vf) > 2.5f) {
            val top = railTop(solid)
            if (top >= 0f) { startGrind(top, steerIn, gas, brake, vf); visuals(dt, steerIn, gas, vf); return }
        }

        // ----- mover com colisão (eixo por eixo) -----
        var hit = 0f
        val nx = x + vx * dt
        if (!coll(nx, y, z)) x = nx
        else if (onGround && !coll(nx, y + 0.52f, z) && !coll(x, y + 0.52f, z)) { x = nx; y += 0.52f; yOff -= 0.52f }
        else { hit = max(hit, abs(vx)); vx = -vx * 0.1f }
        val nz = z + vz * dt
        if (!coll(x, y, nz)) z = nz
        else if (onGround && !coll(x, y + 0.52f, nz) && !coll(x, y + 0.52f, z)) { z = nz; y += 0.52f; yOff -= 0.52f }
        else { hit = max(hit, abs(vz)); vz = -vz * 0.1f }
        if (hit > 8f && onGround && ctl) bail()
        else if (hit > 4f) impact = max(impact, hit)

        // ----- gravidade -----
        vy -= GRAV * dt
        if (vy < -40f) vy = -40f
        val ny = y + vy * dt
        var landed = false; var preVy = 0f
        if (coll(x, ny, z)) {
            if (vy < 0f) {
                var lo = y; var hi = ny
                for (i in 0 until 8) { val mid = (lo + hi) / 2f; if (coll(x, mid, z)) hi = mid else lo = mid }
                y = lo; landed = true; preVy = vy
            }
            vy = 0f
        } else { y = ny; onGround = false }
        if (landed) {
            onGround = true
            if (!wasGround) handleLand(preVy, ctl)
        }

        visuals(dt, steerIn, gas, vf)
    }

    private fun visuals(dt: Float, steerIn: Float, gas: Float, vf: Float) {
        speedK += ((abs(vf) - 6f).coerceIn(0f, 9f) / 9f - speedK) * min(1f, 3f * dt)
        val pushing = onGround && gas > 0.05f && vf < MAXF * 0.82f && bailT <= 0f &&
            !manual && !grinding && drift < 0.25f && !inWater
        if (pushing) {
            // Um ciclo completo por passada: prepara, empurra e recolhe o pé.
            pushPh = (pushPh + dt * (0.82f + 0.16f * min(1f, abs(vf) / 8f))) % 1f
        } else {
            // Na próxima acelerada, a passada recomeça de forma previsível.
            pushPh = 0f
        }
        val pushEnvelope = if (!pushing) 0f else when {
            pushPh < 0.08f -> pushPh / 0.08f
            pushPh < 0.76f -> 1f
            else -> ((1f - pushPh) / 0.24f).coerceIn(0f, 1f)
        }
        pushW += (pushEnvelope - pushW) * min(1f, 12f * dt)
        wheel += vf * dt / 0.085f * 57.3f
        if (wheel > 36000f || wheel < -36000f) wheel = 0f
        // inclinação
        // A prancha responde à curva com atraso curto, sem tremer quando o dedo muda de lado.
        val leanT = if (grinding) bal * 15f else steer * min(1f, abs(vf) / 8f) * 18f
        lean += (leanT - lean) * min(1f, 7f * dt)
        // bico: sobe no ollie, acompanha a subida/descida
        val pT = if (popT > 0f) 20f else if (!onGround) (vy * 1.2f).coerceIn(-12f, 14f) else if (manual) 17f + bal * 9f else 0f
        pitchA += (pT - pitchA) * min(1f, 14f * dt)
        // agachar
        val cT = if (bailT > 0f) 0f else if (charging) 0.2f + 0.8f * charge else if (grinding) 0.55f
        else if (grab != 0) 0.85f else if (flipT >= 0f) 0.45f else if (onGround && gas > 0f) 0.12f else 0f
        val cT2 = max(cT, landK * 0.55f)
        crouch += (cT2 - crouch) * min(1f, 14f * dt)
        grabA += ((if (grab != 0) 1f else 0f) - grabA) * min(1f, 16f * dt)
        // flip animado
        if (flipT >= 0f) {
            val f = flipT.coerceIn(0f, 1f); val e = f * f * (3f - 2f * f)
            when (flipKind) {
                0 -> { flipRoll = 360f * e; flipYaw = 0f }
                1 -> { flipRoll = -360f * e; flipYaw = 0f }
                2 -> { flipRoll = 0f; flipYaw = 180f * e }
                3 -> { flipRoll = 360f * e; flipYaw = 360f * e }
                else -> { flipRoll = 360f * e; flipYaw = 180f * e }
            }
        } else { flipRoll = 0f; flipYaw = 0f }
        flipF = if (flipT >= 0f) flipT.coerceIn(0f, 1f) else 0f
        val liftT = if (flipT >= 0f) sin(Math.PI.toFloat() * flipF) * 0.17f else 0f
        lift += (liftT - lift) * min(1f, 18f * dt)
        // grind: boardslide gira a prancha 90°, nose/tail inclinam
        val gyT = if (grinding && gKind == 1) 90f * gSign else 0f
        val gpT = if (grinding) (if (gKind == 2) 18f else if (gKind == 3) -18f else 0f) else 0f
        gYaw += (gyT - gYaw) * min(1f, 16f * dt)
        gPitch += (gpT - gPitch) * min(1f, 12f * dt)
        // no chão o giro que sobrou volta a zero
        if (onGround && !grinding) spin -= spin * min(1f, 14f * dt)
        // tombo: prancha roda; depois assenta
        if (bailT > 0f) tumble += (abs(vf) + 6f) * 50f * dt
        else if (tumble != 0f) { tumble -= tumble * min(1f, 12f * dt); if (abs(tumble) < 1f) tumble = 0f }
        yOff += (0f - yOff) * min(1f, 9f * dt)
        lastVf = vf
        if (grinding) { vx = sin(h) * gv; vz = cos(h) * gv }
    }

    /** põe a prancha no chão perto de (px,py,pz); false se não achou lugar */
    fun spawn(px: Float, py: Float, pz: Float, ang: Float, solid: (Int, Int, Int) -> Boolean,
              coll: (Float, Float, Float) -> Boolean): Boolean {
        h = ang
        val cands = floatArrayOf(0f, 1.6f, 2.4f, 1.0f, 3.2f)
        for (d in cands) {
            val sx = px + sin(ang) * d; val sz = pz + cos(ang) * d
            var gy = py + 0.03f
            if (d != 0f) {
                var by = floor(py + 2.5f).toInt()
                val lim = floor(py - 6f).toInt()
                while (by > lim && !solid(floor(sx).toInt(), by, floor(sz).toInt())) by--
                if (by <= lim) continue
                gy = by + 1.02f
            }
            if (!coll(sx, gy, sz)) {
                x = sx; y = gy; z = sz
                vx = 0f; vy = 0f; vz = 0f; steer = 0f; lean = 0f; pitchA = 0f; yOff = 0f; lastVf = 0f
                spin = 0f; stance = 1f; flipT = -1f; flipRoll = 0f; flipYaw = 0f; grab = 0; grabA = 0f; grinding = false
                bailT = 0f; tumble = 0f; charging = false; charge = 0f; crouch = 0f
                comboPts = 0; comboN = 0; comboText = ""
                manual = false; drift = 0f; pushW = 0f; speedK = 0f; lift = 0f; popK = 0f; landK = 0f
                placed = true
                return true
            }
        }
        return false
    }

    /** o jogador desceu: zera o que estava em andamento (a pontuação fica) */
    fun dismount() {
        flipT = -1f; flipRoll = 0f; flipYaw = 0f; grab = 0; grabA = 0f; grinding = false; spin = 0f
        bailT = 0f; tumble = 0f; charging = false; charge = 0f; crouch = 0f; gYaw = 0f; gPitch = 0f
        manual = false; drift = 0f; pushW = 0f; speedK = 0f; lift = 0f
        bank()
    }
}
