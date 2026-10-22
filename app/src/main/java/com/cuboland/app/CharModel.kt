package com.cuboland.app

import android.opengl.Matrix
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** (matriz-pai, px,py,pz, rx,ry, sx,sy,sz, oy, cor, alpha, rz) -> desenha uma caixa */
typealias BoxFn = (FloatArray, Float, Float, Float, Float, Float, Float, Float, Float, Float, Int, Float, Float) -> Unit

/** pedaço de saia/vestido: vira dois painéis, um preso em cada coxa (nunca atravessa as pernas) */
class SkR(@JvmField val x: Float, @JvmField val py: Float, @JvmField val z: Float, @JvmField val sx: Float, @JvmField val sy: Float, @JvmField val sz: Float, @JvmField val col: Int, @JvmField val split: Boolean)

/** Estado de animação do boneco (preenchido pelo jogo ou pela tela de personagem). */
class Anim {
    @JvmField var t = 0f          // tempo corrido
    @JvmField var phase = 0f      // fase do passo
    @JvmField var move = 0f       // 0 parado .. 1 andando
    @JvmField var run = 0f        // 0 andando .. 1 correndo
    @JvmField var vy = 0f         // velocidade vertical
    @JvmField var air = false     // no ar
    @JvmField var landT = 0f      // 1 = acabou de pousar, cai até 0
    @JvmField var swim = 0f       // 0..1 nadando
    @JvmField var atk = 1f        // progresso do golpe (1 = sem golpe)
    @JvmField var atkArm = 0f     // graus extras do braço direito no golpe
    @JvmField var hasTool = false // segurando item na mão direita
    @JvmField var airA = 0f       // 0..1 suavizado: quanto está "no ar" (pulo fluido)
    @JvmField var dive = 0f       // 0 nadando com a cabeça fora .. 1 mergulhado
    @JvmField var poseOn = false  // usa a pose de golpe da 1ª pessoa (pP/pY/pW) no braço da ferramenta
    @JvmField var pP = 0f; @JvmField var pY = 0f; @JvmField var pR = 0f; @JvmField var pW = 0f
    @JvmField var charge = 0f     // 0..1 segurando o ataque carregado
    @JvmField var fly = 0f        // 0..1 voando (suavizado)
    @JvmField var flySpd = 0f     // 0 pairando .. 1 voando rápido
    @JvmField var crouch = 0f; @JvmField var sit = 0f; @JvmField var lie = 0f   // posturas 0..1
    @JvmField var ride = 0f; @JvmField var bent = 0f   // dirigindo: braços à frente no volante/guidão (ride) e pernas dobradas nas pedaleiras (bent)
    @JvmField var dash = 0f; @JvmField var imp = 0f; @JvmField var swimPh = 0f; @JvmField var armFwdR = 0f   // voo rápido, impulso, fase da braçada, (saída) quanto o braço do item está esticado
    @JvmField var tilt = 0f       // graus que o corpo deita ao nadar (o jogo aplica na matriz)
    @JvmField var swimFast = 0f   // 0..1 (suavizado) acelerando na água: só aí os braços batem; sem acelerar, boia/desliza
    @JvmField var toolA = 0f      // (saída) graus extras em X no item da mão, pra ele não furar o corpo/chão (nado e voo rápido)
    @JvmField var jch = 0f        // 0..1 carregando o pulo (agacha, inclina e treme); a batidinha rápida do pulo normal chega a ~0,35
    @JvmField var sj = 0f         // 0..1 (suavizado) no ar de um SUPER PULO
    @JvmField var flip = 0f       // graus da cambalhota do super pulo (0..360)
    @JvmField var launch = 0f     // 1 -> 0: acabou de decolar do super pulo pro voo (braços pra cima, relaxando)
    @JvmField var trkP = 0f   // progresso da manobra 0..1 (fases: preparação, impulso, grupado, abertura)
    @JvmField var trkF = 0f   // manobra começou no voo rápido (0/1)
    @JvmField var trk = 0f; @JvmField var trkK = 0f   // manobra no ar: peso 0..1 e tipo (1-2 cambalhota: encolhe; 3-5 parafuso/giro: braços abertos)
    @JvmField var dst = 0f        // 0..1 pisada no ar do dash (perna estica pra trás empurrando o ar); decai depois do disparo
    @JvmField var vp = 0f         // graus da direção do voo acima da horizontal (o corpo acompanha no dash)
    @JvmField var dch = 0f        // 0..1 carregando o dash aéreo (segurando o botão de correr no voo)
    // ----- pose de skatista (pés na prancha, de lado; frente da prancha = +X do boneco; perna da frente = sd>0) -----
    @JvmField var sk = 0f         // 0..1 peso da pose de skate
    @JvmField var skPush = 0f; @JvmField var skPushPh = 0f   // peso e fase do ciclo de impulsão
    @JvmField var skFace = 0f      // 1 = tronco frontal e pés escalonados durante a arrancada
    @JvmField var skGrab = 0f; @JvmField var skGrabK = 0f    // peso do grab e tipo (1 indy, 2 melon, 3 mute, 4 nose, 5 tail)
    @JvmField var skPop = 0f; @JvmField var skLand = 0f      // 1 -> 0 depois do ollie (estica) / do pouso (amortece)
    @JvmField var skMan = 0f; @JvmField var skFlip = 0f; @JvmField var skGrind = 0f   // manual, flip no ar, grind (pesos suaves)
    @JvmField var skBal = 0f      // -1..1 equilíbrio (manual/grind): os braços compensam
}

/** Modelo chibi do personagem, montado só com caixas. Usado no jogo (OpenGL) e na tela de personagem (preview). */
class CharModel {
    private var bx: BoxFn = { _, _, _, _, _, _, _, _, _, _, _, _, _ -> }
    private val t1 = FloatArray(16); private val t2 = FloatArray(16); private val t3 = FloatArray(16)
    private val t4 = FloatArray(16); private val t5 = FloatArray(16)
    private val r2 = FloatArray(16); private val bm = FloatArray(16); private val hm = FloatArray(16); private val sm = FloatArray(16)

    private fun b(m: FloatArray, px: Float, py: Float, pz: Float, sx: Float, sy: Float, sz: Float, col: Int,
                  rx: Float = 0f, ry: Float = 0f, rz: Float = 0f, a: Float = 1f) =
        bx(m, px, py, pz, rx, ry, sx, sy, sz, 0f, col, a, rz)

    /** caixa presa à cabeça */
    private fun hb(px: Float, py: Float, pz: Float, sx: Float, sy: Float, sz: Float, col: Int, rz: Float = 0f, a: Float = 1f, rx: Float = 0f, ry: Float = 0f) =
        bx(hm, px, py, pz, rx, ry, sx, sy, sz, 0f, col, a, rz)

    private fun piv(out: FloatArray, parent: FloatArray, px: Float, py: Float, pz: Float, rx: Float = 0f, ry: Float = 0f, rz: Float = 0f) {
        System.arraycopy(parent, 0, out, 0, 16)
        Matrix.translateM(out, 0, px, py, pz)
        if (rz != 0f) Matrix.rotateM(out, 0, rz, 0f, 0f, 1f)
        if (ry != 0f) Matrix.rotateM(out, 0, ry, 0f, 1f, 0f)
        if (rx != 0f) Matrix.rotateM(out, 0, rx, 1f, 0f, 0f)
    }

    private fun swim01(an: Anim) = if (an.swim > 0.2f) 1f else 0f
    private fun lite(c: Int, k: Float) = mixC(c, 0xFFFFFF, k)

    companion object {
        const val HEAD_SCALE = 0.64f
        private fun lp(a: Float, b: Float, k: Float) = a + (b - a) * k
        // braçada de peito (nado mergulhado): (yaw pra fora, elevação, cotovelo) em cada instante da volta
        private val STROKE_KF = arrayOf(floatArrayOf(26f, -10f, -6f), floatArrayOf(52f, -18f, -26f), floatArrayOf(84f, -30f, -62f),
            floatArrayOf(64f, -42f, -72f), floatArrayOf(26f, -10f, -6f), floatArrayOf(26f, -10f, -6f))
        private val STROKE_T = floatArrayOf(0f, 0.22f, 0.46f, 0.68f, 0.92f, 1f)
        /** direção do braço no corpo (x lateral, y cabeça, z frente) a partir de yaw/elevação -> (rx, rz) do pivô do ombro; s = +1 esquerda, -1 direita */
        private fun armIk(yaw: Float, ev: Float, s: Float, out: FloatArray) {
            val y = Math.toRadians(yaw.toDouble()).toFloat(); val e = Math.toRadians(ev.toDouble()).toFloat()
            val dx = s * sin(y) * cos(e); val dy = cos(y) * cos(e); val dz = -sin(e)
            val h = sqrt(dx * dx + dy * dy)
            var rx = Math.toDegrees(atan2(-dz, -h).toDouble()).toFloat(); if (rx > 0f) rx -= 360f
            out[0] = rx; out[1] = Math.toDegrees(atan2(-dx, dy).toDouble()).toFloat()
        }
        /** graus extras do braço no golpe padrão */
        fun atkDelta(sp: Float): Float {
            if (sp >= 1f) return 0f
            return when { sp < 0.3f -> -50f * (sp / 0.3f); sp < 0.55f -> -50f + 90f * ((sp - 0.3f) / 0.25f); else -> 40f * (1f - (sp - 0.55f) / 0.45f) }
        }
    }

    // ======================= CABELOS: mechas finas afiladas, camadas e tons variados =======================
    private val hsA = FloatArray(16); private val hsB = FloatArray(16)
    private var hC = 0; private var hSk = 0; private var tY = 1.715f; private var tH = 0.1f

    /** variação de tom determinística (cada mecha um tiquinho diferente: dá textura ao cabelo) */
    private fun tone(i: Int): Int {
        val k = ((i * 1103515245 + 12345) ushr 9) and 0xFF
        val f = (k % 9) / 8f
        return if (f < 0.55f) shadeC(hC, 0.74f + f * 0.4f) else lite(hC, (f - 0.55f) * 0.5f)
    }

    /** mecha que pende pra baixo, em segmentos que afinam até a ponta (rx = pra trás, rz = pro lado +x) */
    private fun strand(px: Float, py: Float, pz: Float, len: Float, w: Float, d: Float, rx: Float, rz: Float, col: Int, tip: Int = col,
                       bnx: Float = 0f, bnz: Float = 0f, segs: Int = 3, taper: Float = 0.22f, alt: Int = col, zig: Boolean = false) {
        piv(hsA, hm, px, py, pz, rx, 0f, rz)
        var cur = hsA; var nx = hsB
        val sl = len / segs
        for (i in 0 until segs) {
            val f = max(0.15f, 1f - taper * i)
            val c = if (i == segs - 1) tip else if (i % 2 == 1) alt else col
            b(cur, 0f, -sl / 2f, 0f, w * f, sl * 1.1f, d * f, c)
            if (i < segs - 1) {
                val sgn = if (zig && i % 2 == 1) -1f else 1f
                piv(nx, cur, 0f, -sl, 0f, bnx, 0f, bnz * sgn)
                val tmp = cur; cur = nx; nx = tmp
            }
        }
    }

    /** topo da cabeça: placas com altura e tom diferentes */
    private fun hTop() {
        for (ix in 0..2) for (iz in 0..2) {
            val i = ix * 3 + iz
            val jh = ((i * 5) % 3) * 0.012f
            hb((ix - 1) * 0.29f, tY + jh / 2f, (iz - 1) * 0.29f, 0.3f, tH + jh, 0.3f, tone(i))
        }
    }

    private fun hStreak(x: Float, z: Float, len: Float, wd: Float = 0.05f, k: Float = 0.3f) =
        hb(x, tY + tH / 2f + 0.022f, z, wd, 0.03f, len, lite(hC, k))

    /** nuca: colunas de tamanhos diferentes (borda irregular), com ponta afilada nos cabelos longos */
    private var fem = false
    private fun hBack(low0: Float, tip: Float = 0f, cols: Int = 6, sway: Float = 0f, shade: Float = 1f, base: Int = 20, topY: Float = 1.68f) {
        val low = if (fem) min(low0, 1.08f) else low0
        val wv = 0.88f / cols
        for (i in 0 until cols) {
            val x = (i - (cols - 1) / 2f) * wv
            val bot = low + ((i * 7) % 4) * 0.03f
            val body = max(0.03f, topY - (bot + tip))
            hb(x, topY - body / 2f, -0.41f, wv * 1.08f, body, 0.12f, shadeC(tone(base + i), shade))
            if (tip > 0f) strand(x, bot + tip, -0.41f, tip + 0.02f, wv, 0.1f, sway, 0f, shadeC(tone(base + i + 9), shade), shadeC(tone(base + i + 3), shade), segs = 2, taper = 0.35f)
        }
    }

    /** laterais (costeletas) em quatro colunas: a última cobre a têmpora até a testa (sem "entrada") */
    private fun hSide(low0: Float, tip: Float = 0f, shade: Float = 1f, base: Int = 40, topY: Float = 1.68f) {
        val low = if (fem) min(low0, 1.2f) else low0
        for (sd in intArrayOf(-1, 1)) {
            val s = sd.toFloat()
            for (j in 0..3) {
                val front = j == 3
                val z = if (front) 0.3f else -0.28f + j * 0.19f
                val bot = if (front) max(low + 0.1f, 1.5f) else low + (if (j == 1) 0.04f else 0f) + (if (j == 2) 0.1f else 0f)
                val body = topY - (bot + (if (front) 0f else tip))
                if (body <= 0.01f) continue
                hb(s * 0.425f, topY - body / 2f, z, if (front) 0.082f else 0.075f, body, if (front) 0.24f else 0.2f, shadeC(tone(base + j + (sd + 1) * 3), shade))
                if (tip > 0f && !front) strand(s * 0.425f, bot + tip, z, tip + 0.02f, 0.075f, 0.18f, 0f, 0f, shadeC(tone(base + j + 11), shade), shadeC(tone(base + j + 5), shade), segs = 2, taper = 0.35f)
            }
        }
    }

    /** franja: mechas finas na testa, abrindo em leque */
    private fun hBangs(lens: FloatArray, lean: Float = 0f, fan: Float = 14f, cx: Float = 0f, d: Float = 0.07f, curl: Float = 0f, base: Int = 60) {
        val n = lens.size; val step = 0.8f / n
        for (i in 0 until n) {
            val m = i - (n - 1) / 2f
            strand(m * step, 1.72f, 0.405f + (i % 2) * 0.012f, lens[i] + 0.03f, step * 1.12f, d, 0f, lean + (m - cx) / n * fan * 2f,
                tone(base + i), tone(base + i + 5), bnz = curl, segs = 2, taper = 0.3f)
        }
    }

    /** espeto de cabelo apontando pra cima (out = abre pro lado, rx>0 inclina pra trás) */
    private fun hSpike(x: Float, z: Float, len: Float, w: Float, out: Float, rx: Float = 0f, base: Int = 200) {
        strand(x, tY, z, len, w, w, rx, 180f - out, tone(base + (x * 50f).toInt() + (z * 30f).toInt()), lite(hC, 0.28f), segs = 3, taper = 0.3f)
    }

    private fun hCurl(x: Float, y: Float, z: Float, s: Float, i: Int) =
        hb(x, y, z, s, s, s, tone(i), rz = ((i * 37) % 50 - 25).toFloat(), rx = ((i * 53) % 60 - 30).toFloat(), ry = ((i * 29) % 90).toFloat())

    /** nuvem de cachos em volta da cabeça (cacheado, black power, fofo) */
    private fun hCurls(radius: Float, topY: Float, sz: Float, lowY: Float, rings: Int, base: Int) {
        hb(0f, (topY + lowY) / 2f + 0.03f, -0.04f, radius * 1.6f, (topY - lowY) + 0.12f, radius * 1.5f, shadeC(hC, 0.8f))
        hCurl(0f, topY + sz * 0.15f, 0f, sz * 1.25f, base)
        for (ri in 0 until rings) {
            val k = if (rings > 1) ri / (rings - 1f) else 0f
            val y = topY - (topY - lowY) * k
            val rad = radius * (if (ri == 0) 0.62f else 1f)
            val cnt = max(5, (rad * 6.2831f / (sz * 1.3f)).toInt())
            for (c in 0 until cnt) {
                val th = 6.2831f * c / cnt + ri * 0.45f
                val x = sin(th) * rad; val z = cos(th) * rad
                if (ri > 0 && z > 0.28f && y < 1.64f) continue   // nunca tapa o rosto
                hCurl(x, y, z, sz * (0.9f + ((base + ri * 7 + c * 3) % 4) * 0.07f), base + ri * 17 + c)
            }
        }
    }

    private fun hBun(x: Float, y: Float, z: Float, sz: Float, i: Int, bow: Boolean = false) {
        hb(x, y, z, sz, sz * 0.85f, sz, tone(i))
        hb(x, y, z, sz, sz * 0.85f, sz, tone(i + 1), ry = 45f)
        hb(x, y + sz * 0.18f, z, sz * 1.05f, sz * 0.1f, sz * 1.05f, lite(hC, 0.22f), ry = 22f)   // voltas do cabelo enrolado
        hb(x, y - sz * 0.1f, z, sz * 1.05f, sz * 0.09f, sz * 1.05f, shadeC(hC, 0.78f), ry = 67f)
        hb(x, y + sz * 0.45f, z, sz * 0.6f, sz * 0.15f, sz * 0.6f, lite(hC, 0.25f))
        hb(x, y - sz * 0.5f, z, sz * 0.72f, 0.05f, sz * 0.72f, Look.pal(Look.HATC))
        strand(x + sz * 0.32f, y - sz * 0.35f, z - sz * 0.4f, sz * 0.8f, 0.05f, 0.05f, 16f, 8f, tone(i + 2), tone(i + 3), segs = 2)   // fiozinho solto
        if (bow) hBow(x, y - sz * 0.42f, z - sz * 0.55f, sz * 0.8f, Look.pal(Look.HATC))
    }

    /** laço de fita (side = 0: aberto pros lados; side = 1 ou -1: virado pro lado) */
    private fun hBow(x: Float, y: Float, z: Float, s: Float, col: Int, side: Float = 0f) {
        val dk = shadeC(col, 0.75f); val lt = lite(col, 0.3f)
        if (side == 0f) {
            hb(x - s * 0.5f, y + s * 0.05f, z, s * 0.9f, s * 0.7f, s * 0.36f, col, rz = 18f)
            hb(x + s * 0.5f, y + s * 0.05f, z, s * 0.9f, s * 0.7f, s * 0.36f, col, rz = -18f)
            hb(x - s * 0.5f, y + s * 0.14f, z, s * 0.6f, s * 0.14f, s * 0.4f, lt, rz = 18f)
            hb(x + s * 0.5f, y + s * 0.14f, z, s * 0.6f, s * 0.14f, s * 0.4f, lt, rz = -18f)
            hb(x, y, z, s * 0.34f, s * 0.38f, s * 0.42f, dk)
            hb(x - s * 0.18f, y - s * 0.45f, z, s * 0.2f, s * 0.5f, s * 0.3f, dk, rz = 14f)
            hb(x + s * 0.18f, y - s * 0.45f, z, s * 0.2f, s * 0.5f, s * 0.3f, dk, rz = -14f)
        } else {
            hb(x, y + s * 0.05f, z - s * 0.5f, s * 0.36f, s * 0.7f, s * 0.9f, col, rx = -18f)
            hb(x, y + s * 0.05f, z + s * 0.5f, s * 0.36f, s * 0.7f, s * 0.9f, col, rx = 18f)
            hb(x, y + s * 0.14f, z - s * 0.5f, s * 0.4f, s * 0.14f, s * 0.6f, lt, rx = -18f)
            hb(x, y + s * 0.14f, z + s * 0.5f, s * 0.4f, s * 0.14f, s * 0.6f, lt, rx = 18f)
            hb(x, y, z, s * 0.42f, s * 0.38f, s * 0.34f, dk)
            hb(x + side * s * 0.1f, y - s * 0.45f, z - s * 0.15f, s * 0.3f, s * 0.5f, s * 0.2f, dk, rx = -10f)
            hb(x + side * s * 0.1f, y - s * 0.45f, z + s * 0.15f, s * 0.3f, s * 0.5f, s * 0.2f, dk, rx = 10f)
        }
    }

    /** mechas que emolduram o rosto (caem na frente das orelhas, com pontinha virada) */
    private fun hLocks(len: Float, w: Float, rzIn: Float, curl: Float, base: Int, z: Float = 0.27f) {
        for (sd in intArrayOf(-1, 1)) {
            val s = sd.toFloat()
            strand(s * 0.435f, 1.64f, z, len, w, 0.15f, 0f, -s * rzIn, tone(base + sd), tone(base + 3 + sd), bnz = -s * curl, segs = 4, taper = 0.14f)
            strand(s * 0.41f, 1.62f, z + 0.1f, len * 0.62f, w * 0.7f, 0.1f, 0f, -s * (rzIn + 3f), tone(base + 6 + sd), lite(hC, 0.22f), bnz = -s * curl * 1.2f, segs = 3, taper = 0.2f)
        }
    }

    /** faixa de brilho na nuca */
    private fun hShine(y: Float, w: Float = 0.84f) {
        hb(0f, y, -0.468f, w, 0.05f, 0.02f, lite(hC, 0.34f))
        hb(0f, y - 0.2f, -0.468f, w * 0.7f, 0.035f, 0.02f, lite(hC, 0.2f))
    }

    /** rabo de cavalo cheio: base volumosa, elástico e 5 camadas de mechas formando um arco que balança */
    private fun hPony(y: Float, z: Float, len: Float, high: Boolean, t: Float, move: Float, sway: Float, tie: Int, base: Int, bow: Boolean = true) {
        val pz = z - 0.08f
        hb(0f, y, z + 0.02f, 0.34f, 0.28f, 0.22f, tone(base))
        hb(0f, y + 0.03f, z + 0.03f, 0.24f, 0.22f, 0.18f, tone(base + 1), ry = 45f)
        hb(0f, y, pz, 0.26f, 0.09f, 0.24f, tie)
        val offs = floatArrayOf(0f, -0.085f, 0.085f, -0.16f, 0.16f)
        val lns = floatArrayOf(1f, 0.9f, 0.9f, 0.74f, 0.74f)
        for (k in offs.indices) {
            val o = offs[k]
            val wob = sin(t * 2.3f + k * 0.7f) * 3f
            if (high) strand(o, y, pz, len * lns[k], 0.13f - abs(o) * 0.12f, 0.15f, 140f + sway * 0.5f, 0f, tone(base + 2 + k), lite(hC, 0.25f), bnx = -28f + wob * 0.6f + move * -4f, segs = 6, taper = 0.1f)
            else strand(o, y, pz, len * lns[k], 0.13f - abs(o) * 0.1f, 0.15f, 12f + sway * 0.7f, o * 60f, tone(base + 2 + k), lite(hC, 0.25f), bnx = 7f + wob + move * 5f, segs = 5, taper = 0.13f)
        }
        if (bow) hBow(0f, y + 0.02f, pz - 0.14f, 0.3f, tie)
    }

    /** trança grossa de verdade: três gomos entrelaçados, balançando, com laço na ponta */
    private fun hBraid(x: Float, y: Float, z: Float, n: Int, th: Float, rx0: Float, rz0: Float, t: Float, move: Float, base: Int, tie: Int, bnx: Float = 0f) {
        piv(hsA, hm, x, y, z, rx = rx0 + sin(t * 2.2f) * 4f + move * 10f, rz = rz0)
        var cur = hsA; var nx = hsB
        for (k in 0 until n) {
            val f = (1f - 0.5f * k / n) * th
            b(cur, -0.045f * f, -0.1f, 0f, 0.1f * f, 0.22f, 0.13f * f, tone(base + k * 2), rz = 14f)
            b(cur, 0.045f * f, -0.1f, 0f, 0.1f * f, 0.22f, 0.13f * f, tone(base + 1 + k * 2), rz = -14f)
            piv(nx, cur, 0f, -0.17f, 0f, rx = bnx + sin(t * 2.2f + k * 0.7f) * 5f)
            val tmp = cur; cur = nx; nx = tmp
        }
        b(cur, 0f, -0.04f, 0f, 0.16f * th, 0.07f, 0.16f * th, tie)
        b(cur, -0.03f, -0.16f, 0f, 0.06f, 0.2f, 0.07f, tone(base + 40))
        b(cur, 0.03f, -0.15f, 0f, 0.06f, 0.17f, 0.07f, tone(base + 41))
    }

    /** faixas de degradê (pele -> cabelo) nas laterais e na nuca */
    private fun hFade(y0: Float, bands: Int, from: Float, to: Float) {
        for (k in 0 until bands) {
            val c = mixC(hSk, hC, from + (to - from) * k / max(1, bands - 1))
            val yb = y0 - k * 0.1f
            val zf = 0.4f - k * 0.05f; val zc = (zf - 0.41f) / 2f; val zd = zf + 0.41f
            hb(-0.425f, yb, zc, 0.075f, 0.1f, zd, c); hb(0.425f, yb, zc, 0.075f, 0.1f, zd, c)
            hb(0f, yb, -0.41f, 0.88f, 0.1f, 0.12f, c)
        }
    }

    /** vários fios caindo em volta da cabeça (dreads e tranças) */
    private fun hHang(backN: Int, sideN: Int, len: Float, w: Float, segs: Int, base: Int, sway: Float, bnz: Float, taper: Float, alt: Int, zig: Boolean, topY: Float = 1.62f) {
        for (i in 0 until backN) {
            val x = (i - (backN - 1) / 2f) * (0.76f / max(1, backN - 1))
            strand(x, topY, -0.43f, len * (1f - (i % 3) * 0.1f), w, w, sway, 0f, tone(base + i), tone(base + i + 4), bnz = bnz, segs = segs, taper = taper, alt = alt, zig = zig)
        }
        for (sd in intArrayOf(-1, 1)) {
            val s = sd.toFloat()
            for (j in 0 until sideN) {
                val z = -0.3f + j * (0.5f / max(1, sideN - 1))
                strand(s * 0.43f, topY, z, len * (0.85f - j * 0.05f), w, w, 0f, 0f, tone(base + 30 + j + (sd + 1) * 4), tone(base + 34 + j), bnz = bnz * s, segs = segs, taper = taper, alt = alt, zig = zig)
            }
        }
    }

    private fun hairLift(hs: Int): Float = when (hs) {
        1 -> 0.08f; 5 -> 0.1f; 7 -> 0.18f; 10 -> 0.08f; 11 -> 0.1f; 12 -> 0.2f; 14 -> 0.1f; 18 -> 0.08f; 21 -> 0.04f; 23 -> 0.14f
        24 -> 0.1f; 42 -> 0.06f; 43 -> 0.18f; 44 -> 0.04f; 47 -> 0.1f; 25 -> 0.22f; 26 -> 0.04f; 28 -> 0.1f; 34 -> 0.12f; 37 -> 0.05f; else -> 0f
    }

    private fun drawHair(hs0: Int, hatCovers: Boolean, hcol: Int, tie: Int, skin: Int, t: Float, move: Float, sw: Float) {
        hC = hcol; hSk = skin; fem = Look.v[Look.GENDER] == 1
        val hc = hcol
        val shine = lite(hc, 0.32f)
        val deep = shadeC(hc, 0.68f)
        val sway = sin(t * 2.1f) * 2.5f + move * 4f + sw * 0.08f
        val hs = if (hatCovers && hs0 in intArrayOf(1,5,7,10,11,12,14,18,23,24,25,26,28,34,35,42,43,44,47)) 0 else hs0
        tH = if (hatCovers) 0.045f else 0.12f
        tY = if (hatCovers) 1.69f else 1.715f
        fun cap() {
            // Calota facetada em três níveis, com sombras laterais e reflexos discretos.
            hb(0f, tY, 0f, 0.78f, tH, 0.78f, deep)
            for (x in -1..1) for (z in -1..1) {
                val i = (x + 1) * 3 + z + 1
                hb(x * 0.255f, tY + 0.035f, z * 0.245f, 0.27f, tH + 0.035f, 0.27f, tone(i + hs * 7))
            }
            hb(-0.16f, tY + tH * 0.55f, 0.31f, 0.12f, 0.025f, 0.12f, shine)
            hb(0.12f, tY + tH * 0.55f, -0.28f, 0.1f, 0.02f, 0.1f, lite(hc, 0.17f))
        }
        fun bangs(lengths: FloatArray, lean: Float = 0f, fan: Float = 16f) {
            hBangs(lengths, lean, fan, d = 0.09f, base = 60 + hs * 3)
            // Camadas de sobreposição tornam a franja legível em baixa resolução.
            for (i in lengths.indices step 2) {
                val x = (i - (lengths.size - 1) / 2f) * (0.8f / lengths.size)
                hb(x, 1.72f - lengths[i] * 0.32f, 0.438f, 0.045f, 0.07f, 0.018f, lite(tone(70 + i + hs), 0.16f), rz = lean * 0.25f)
            }
        }
        fun sidePart(len: Float, n: Int = 5) {
            for (i in 0 until n) {
                val x = 0.34f - i * 0.14f
                strand(x, 1.76f, 0.42f, len * (1f - i * 0.075f), 0.13f, 0.085f, -5f, -18f - i * 4f, tone(80 + hs * 4 + i), lite(hc, 0.25f), segs = 3, taper = 0.28f)
            }
        }
        fun longLayers(low: Float, front: Float = 0.88f, w: Float = 0.115f, wave: Float = 7f, count: Int = 8) {
            hBack(low, 0.12f, count, sway * 0.25f, 0.94f, 100 + hs * 3, 1.69f)
            hSide(front, 0.1f, 0.94f, 130 + hs * 3, 1.69f)
            for (sd in intArrayOf(-1, 1)) {
                val sign = sd.toFloat()
                for (i in 0..2) {
                    val len = front * (1f - i * 0.17f)
                    strand(sign * (0.42f - i * 0.035f), 1.64f, 0.27f - i * 0.055f, len, w * (1f - i * 0.13f), 0.14f, 0f, sign * (2f + i * 2f), tone(145 + hs * 3 + i + sd * 2), lite(hc, 0.24f), bnx = sign * wave, bnz = sign * 2f, segs = 5, taper = 0.16f, zig = i == 1)
                    if (i == 0) strand(sign * 0.35f, 1.61f, 0.39f, len * 0.66f, w * 0.52f, 0.07f, 0f, sign * 6f, tone(160 + hs * 2 + sd), shine, bnz = sign * 3f, segs = 4, taper = 0.22f)
                }
            }
            hShine(1.48f, 0.7f)
        }
        fun spikes(style: Int, count: Int, length: Float) {
            for (i in 0 until count) {
                val x = (i - (count - 1) / 2f) * (0.66f / max(1, count - 1))
                val z = when (i % 3) { 0 -> 0.2f; 1 -> -0.04f; else -> -0.25f }
                val ln = length * (0.72f + ((i * 7 + style) % 5) * 0.09f)
                hSpike(x, z, ln, 0.105f + (i % 2) * 0.025f, (i - count / 2) * 8f, if (i % 2 == 0) -8f else 5f, 200 + style * 5 + i)
            }
            bangs(floatArrayOf(0.12f, 0.19f, 0.1f, 0.23f, 0.14f), -2f, 18f)
        }
        fun pony(high: Boolean, len: Float, bows: Boolean) {
            cap(); hSide(1.42f, 0.04f, 0.9f, 42 + hs * 3); bangs(floatArrayOf(0.22f,0.18f,0.12f,0.18f,0.24f), 2f, 13f)
            hPony(if (high) 1.72f else 1.52f, -0.43f, len, high, t, move, sway, tie, 170 + hs * 3, bows)
            hLocks(0.48f, 0.075f, 3f, 4f, 190 + hs * 2)
        }
        fun bunPair(double: Boolean) {
            cap(); hBack(1.34f, 0.05f, 7); hSide(1.4f); bangs(floatArrayOf(0.19f,0.15f,0.12f,0.18f,0.22f))
            if (double) {
                for (sd in intArrayOf(-1,1)) { hBun(sd * 0.36f, 1.91f, -0.12f, 0.28f, 230 + sd, true); hBow(sd * 0.38f, 1.9f, 0.05f, 0.19f, tie, sd.toFloat()) }
            } else hBun(0f, 1.97f, -0.16f, 0.38f, 230 + hs, true)
            hLocks(0.46f, 0.075f, 3f, 5f, 240 + hs)
        }
        when (hs) {
            0 -> { cap(); hBack(1.28f, 0.04f, 7); hSide(1.35f); bangs(floatArrayOf(0.16f,0.23f,0.18f,0.26f,0.14f), -2f, 14f); hLocks(0.34f,0.065f,2f,3f,20); hStreak(-0.12f,0.05f,0.32f) }
            1 -> { cap(); hBack(1.39f); hSide(1.47f, shade=0.72f); spikes(hs,9,0.34f); hFade(1.57f,2,0.18f,0.52f) }
            2 -> { cap(); longLayers(1.04f,0.95f,0.105f,5f,8); bangs(floatArrayOf(0.23f,0.2f,0.17f,0.2f,0.23f),0f,5f); hLocks(0.55f,0.09f,2f,6f,22) }
            3 -> { cap(); hBack(1.25f); hSide(1.4f); bangs(floatArrayOf(0.18f,0.14f,0.2f,0.15f,0.2f)); for(sd in intArrayOf(-1,1)){hPony(1.48f,sd*0.43f,0.72f,true,t,move,sway,tie,30+sd*3,true);hBow(sd*0.45f,1.56f,-0.08f,0.23f,tie,sd.toFloat())} }
            4 -> pony(false,1.05f,true)
            5 -> bunPair(false)
            6 -> { cap(); longLayers(0.63f,0.78f,0.12f,7f,9); bangs(floatArrayOf(0.17f,0.22f,0.16f,0.2f,0.23f,0.18f),0f,13f); hLocks(0.82f,0.105f,3f,7f,35) }
            7 -> { hCurls(0.48f,1.82f,0.205f,1.28f,4,40); bangs(floatArrayOf(0.12f,0.16f,0.1f,0.14f,0.11f),0f,10f); hCurl(-0.28f,1.54f,0.35f,0.16f,53); hCurl(0.28f,1.56f,0.35f,0.17f,55) }
            8 -> { cap(); hFade(1.58f,4,0.1f,0.8f); hb(0f,1.82f,-0.02f,0.58f,0.19f,0.52f,tone(60)); for(i in 0..3) strand(-0.22f+i*0.14f,1.86f,0.3f,0.2f,0.12f,0.12f,-8f,i*5f,tone(64+i),shine,segs=2) }
            9 -> { cap(); hBack(1.17f); hSide(1.24f); sidePart(0.65f,6); hLocks(0.48f,0.07f,4f,4f,80) }
            10 -> { cap(); hBack(1.26f); hSide(1.36f); bangs(floatArrayOf(0.24f,0.12f,0.29f,0.16f,0.22f),-3f,24f); spikes(hs,6,0.25f); hLocks(0.28f,0.07f,3f,3f,90) }
            11 -> { cap(); hBack(1.43f); hSide(1.5f,shade=0.86f); bangs(floatArrayOf(0.1f,0.13f,0.08f,0.11f,0.1f)); hb(0f,1.84f,0.03f,0.24f,0.24f,0.26f,shine,rz=-12f); strand(0f,1.9f,0.06f,0.32f,0.22f,0.2f,-26f,0f,tone(100),shine,segs=3,taper=0.12f) }
            12 -> spikes(hs,12,0.42f)
            13 -> { hb(0f,1.715f,0f,0.76f,0.035f,0.76f,deep); hFade(1.61f,3,0.0f,0.28f); hb(0f,1.735f,0f,0.73f,0.025f,0.73f,shadeC(skin,0.96f)) }
            14 -> { cap(); hFade(1.59f,3,0.1f,0.6f); for(i in -2..2) hSpike(i*0.13f,0f,0.42f-abs(i)*0.025f,0.12f, i*11f,-7f,120+i) }
            15 -> { cap(); hBack(1.32f); hSide(1.42f); bangs(floatArrayOf(0.12f,0.17f,0.22f,0.17f,0.12f),0f,4f); hb(0f,1.74f,0.06f,0.055f,0.02f,0.43f,shine) }
            16 -> { cap(); longLayers(0.82f,0.88f,0.1f,3f,8); bangs(floatArrayOf(0.14f,0.18f,0.16f,0.18f,0.14f)); for(sd in intArrayOf(-1,1)) hb(sd*0.27f,1.6f,0.35f,0.1f,0.5f,0.08f,tone(120+sd),rz=-sd*12f) }
            17 -> { cap(); hBack(1.22f); hSide(1.36f); bangs(floatArrayOf(0.17f,0.14f,0.19f,0.14f,0.17f)); hBraid(-0.28f,1.56f,0.31f,5,0.92f,0f,-8f,t,move,130,tie,4f); hBraid(0.28f,1.56f,0.31f,5,0.92f,0f,8f,t,move,150,tie,-4f) }
            18 -> bunPair(true)
            19 -> { pony(true,1.15f,true); hBow(0f,1.77f,-0.44f,0.25f,tie) }
            20 -> { cap(); hBack(1.3f); hSide(1.4f); hSpike(0f,-0.1f,0.25f,0.07f,0f,0f,160) }
            21 -> { cap(); hFade(1.59f,4,0.05f,0.82f); hBack(1.47f,shade=0.68f); hSide(1.49f,shade=0.68f); bangs(floatArrayOf(0.12f,0.16f,0.1f,0.13f,0.12f)) }
            22 -> { cap(); hBack(1.39f); hSide(1.48f); bangs(floatArrayOf(0.08f,0.11f,0.08f,0.11f,0.08f)); hb(0f,1.85f,-0.02f,0.56f,0.13f,0.46f,tone(170)); hStreak(-0.08f,0.04f,0.32f) }
            23 -> { cap(); hBack(1.42f); hSide(1.5f); hb(0f,1.84f,0.02f,0.5f,0.22f,0.48f,tone(180),rz=-4f); for(i in 0..4) strand(-0.25f+i*0.125f,1.86f,0.31f,0.24f,0.11f,0.12f,-18f,-5f+i*3f,tone(184+i),shine,segs=3) }
            24 -> { hCurls(0.42f,1.79f,0.16f,1.35f,4,200); bangs(floatArrayOf(0.1f,0.14f,0.12f,0.14f,0.1f)); hLocks(0.3f,0.06f,2f,2f,210) }
            25 -> { hCurls(0.57f,1.91f,0.205f,1.2f,5,220); for(i in -2..2) hCurl(i*0.2f,1.88f,0.25f,0.18f,225+i) }
            26 -> { cap(); hBack(0.98f,0.1f,9,sway); hSide(1.18f,0.05f); bangs(floatArrayOf(0.14f,0.2f,0.16f,0.12f,0.18f)); hHang(7,3,0.72f,0.075f,4,240,sway,5f,0.16f,deep,true,1.6f) }
            27 -> { cap(); hBack(1.02f,0.23f,8,sway); hSide(1.35f,0.08f); bangs(floatArrayOf(0.1f,0.14f,0.1f,0.12f)); hShine(1.46f) }
            28 -> { bunPair(false); hb(0f,1.7f,-0.44f,0.08f,0.08f,0.05f,tie) }
            29 -> { cap(); longLayers(0.74f,0.94f,0.1f,5f,8); bangs(floatArrayOf(0.25f,0.19f,0.13f,0.19f,0.25f),0f,4f); hLocks(0.72f,0.09f,2f,4f,250) }
            30 -> { cap(); hBack(1.4f); hSide(1.45f); for(i in 0..5) strand(0.3f-i*0.12f,1.77f,0.42f,0.3f+i*0.035f,0.13f,0.08f,-8f,-22f-i*3f,tone(260+i),shine,segs=3,taper=0.28f); hLocks(0.38f,0.07f,2f,3f,270) }
            31 -> { cap(); longLayers(0.9f,0.95f,0.11f,10f,8); bangs(floatArrayOf(0.15f,0.19f,0.17f,0.2f,0.16f),-2f,17f); hBack(0.92f,0.2f,8,sway) }
            32 -> { cap(); longLayers(0.52f,0.78f,0.12f,2f,9); bangs(floatArrayOf(0.18f,0.23f,0.2f,0.23f,0.18f),0f,5f) }
            33 -> { cap(); longLayers(0.65f,0.9f,0.12f,9f,9); bangs(floatArrayOf(0.16f,0.22f,0.18f,0.21f,0.16f)); hLocks(0.7f,0.1f,3f,9f,280) }
            34 -> { hCurls(0.5f,1.84f,0.19f,1.15f,5,290); bangs(floatArrayOf(0.13f,0.16f,0.11f,0.15f,0.12f)); hLocks(0.68f,0.08f,2f,6f,300) }
            35 -> { cap(); hBack(1.35f); hSide(1.4f); bangs(floatArrayOf(0.16f,0.26f,0.32f,0.25f,0.14f),-12f,25f); sidePart(0.3f,3) }
            36 -> { cap(); hFade(1.62f,4,0.1f,0.85f); hb(0f,1.83f,0f,0.52f,0.18f,0.5f,tone(310)); for(i in -2..2) strand(i*0.12f,1.83f,0.28f,0.18f,0.11f,0.11f,-6f,i*3f,tone(315+i),shine,segs=2) }
            37 -> { cap(); hb(0f,1.82f,0f,0.8f,0.18f,0.8f,tone(320)); hBack(1.33f); hSide(1.38f); bangs(floatArrayOf(0.17f,0.18f,0.18f,0.18f,0.17f),0f,2f); hStreak(0f,0.1f,0.34f,0.04f,0.25f) }
            38 -> { cap(); hBack(1.36f); hSide(1.42f); bangs(floatArrayOf(0.12f,0.18f,0.12f,0.18f,0.12f)); for(i in -2..2) strand(i*0.12f,1.76f,0.35f,0.2f,0.1f,0.08f,-10f,i*7f,tone(330+i),shine,segs=2) }
            39 -> pony(false,0.86f,false)
            40 -> { cap(); hSide(1.25f,0.08f); hBack(1.28f,0.12f,8); bangs(floatArrayOf(0.18f,0.24f,0.17f,0.25f,0.18f),0f,10f); hLocks(0.38f,0.075f,4f,5f,340) }
            41 -> { cap(); hSide(1.26f,0.12f); hBack(1.27f,0.1f,8); bangs(floatArrayOf(0.27f,0.18f,0.13f,0.2f,0.28f),-8f,18f); hLocks(0.44f,0.08f,5f,4f,350) }
            42 -> pony(true,0.92f,true)
            43 -> bunPair(false)
            44 -> { cap(); longLayers(0.7f,0.92f,0.1f,3f,8); bangs(floatArrayOf(0.15f,0.2f,0.15f,0.2f,0.15f)); hBow(0.28f,1.61f,-0.36f,0.22f,tie,1f) }
            45 -> { cap(); hSide(1.3f); hBack(1.12f,0.08f,8); bangs(floatArrayOf(0.12f,0.16f,0.12f,0.16f,0.12f)); hBraid(0.39f,1.6f,0.25f,6,0.8f,0f,18f,t,move,360,tie,4f) }
            46 -> { cap(); hBack(0.92f,0.12f,8,sway); hSide(1.22f,0.08f); bangs(floatArrayOf(0.18f,0.13f,0.17f,0.13f,0.18f)); hBraid(-0.3f,1.58f,0.26f,6,0.82f,0f,-9f,t,move,370,tie,3f); hBraid(0.3f,1.58f,0.26f,6,0.82f,0f,9f,t,move,390,tie,-3f) }
            47 -> { cap(); hBack(1.24f); hSide(1.4f); bangs(floatArrayOf(0.18f,0.14f,0.2f,0.14f,0.18f)); hBun(0f,1.91f,-0.18f,0.29f,400,false); hBack(0.95f,0.08f,8,sway, 0.96f, 410,1.64f) }
            48 -> { hCurls(0.48f,1.86f,0.17f,1.18f,5,420); bangs(floatArrayOf(0.14f,0.17f,0.14f,0.17f,0.14f)); for(sd in intArrayOf(-1,1)) hCurl(sd*0.3f,1.55f,0.31f,0.18f,425+sd) }
            49 -> { cap(); hSide(1.42f); bangs(floatArrayOf(0.16f,0.2f,0.17f,0.2f,0.16f)); for(sd in intArrayOf(-1,1)) { hBun(sd*0.43f,1.78f,-0.08f,0.22f,440+sd,true); for(i in 0..4) strand(sd*0.43f,1.76f,-0.08f,0.48f-i*0.035f,0.075f,0.08f,i*3f,sd*8f,tone(450+i+sd),shine,segs=3,taper=0.18f) } }
            else -> { cap(); longLayers(0.8f,0.9f,0.11f,5f,8); bangs(floatArrayOf(0.17f,0.2f,0.15f,0.2f,0.17f)) }
        }
        // Micro-mechas de brilho aplicadas por cima do volume, sem depender da cor escolhida.
        if (hs != 13) {
            hb(-0.17f, 1.79f, 0.31f, 0.035f, 0.025f, 0.2f, lite(hc, 0.2f), rz = -8f)
            hb(0.11f, 1.8f, 0.29f, 0.025f, 0.02f, 0.16f, lite(hc, 0.14f), rz = 5f)
        }
    }

    /** root = origem nos pés. handOut = matriz da mão direita (pra segurar item). */
    val mc = McChar()
    /** personagem no formato do Minecraft (Steve/Alex): 1,8 de altura, cabeça 8x8x8, corpo 8x12x4, membros 4x12x4 */
    fun draw(boxIn: BoxFn, root: FloatArray, an: Anim, handOut: FloatArray?) { mc.draw(boxIn, root, an, handOut) }

    /** boneco chibi antigo (não é mais usado) */
    @Suppress("unused")
    fun drawChibi(boxIn: BoxFn, root: FloatArray, an: Anim, handOut: FloatArray?) {
        var zf = 0
        bx = { m, px, py, pz, rx, ry, sx, sy, sz, oy, col, a, rz ->
            val k = 1f + min(zf, 400) * 0.00014f; zf++   // cada caixa nova fica um tiquinho maior: acaba o z-fighting de faces coplanares
            boxIn(m, px, py, pz, rx, ry, sx * k, sy * k, sz * k, oy, col, a, rz)
        }
        val t = an.t
        val L = Look.v
        val girl = L[Look.GENDER] == 1
        val skin = Look.skin(); val skinD = shadeC(skin, 0.9f)
        val tc = Look.pal(Look.TOPC); val top = L[Look.TOP]
        val bc = Look.pal(Look.BOTTOMC); val bot = L[Look.BOTTOM]
        val white = 0xF4F4F4
        val full = top == 2 || top == 10 || top == 11 || top == 15   // roupas longas (cobrem as pernas)
        val overalls = top == 6
        val skirtBot = (bot == 2 || bot == 5) && !full && !overalls
        val wide = bot == 4 && !full && !overalls   // hakama: perna larga
        val bw = if (girl) 0.44f else 0.48f
        val gold = 0xFFD060
        val trim = lite(tc, 0.35f); val dark = shadeC(tc, 0.78f)

        // ================= ANIMAÇÃO =================
        val mv = an.move.coerceIn(0f, 1f); val li = an.lie.coerceIn(0f, 1f); val rn = an.run.coerceIn(0f, 1f) * (1f - li); val ph = an.phase   // rastejando não usa o modo corrida
        val swimming = an.swim > 0.2f
        val fl = if (swimming) 0f else an.fly.coerceIn(0f, 1f); val fm = an.flySpd.coerceIn(0f, 1f)
        val ab = if (swimming) 0f else an.airA.coerceIn(0f, 1f) * (1f - fl)
        val chg = an.charge.coerceIn(0f, 1f)
        val vk = (an.vy / 7f).coerceIn(-1f, 1f)
        val air = ab > 0.02f
        val fs = (1f - vk) * 0.5f          // 0 subindo .. 1 caindo (contínuo, sem trancos)
        val dv = an.dive.coerceIn(0f, 1f)
        val land = an.landT.coerceIn(0f, 1f)
        // manobra no ar: preparação (recuo), grupado/abertura durante o giro e abertura na saída (tudo suave, sem trancos)
        val tkW = if (swimming) 0f else an.trk.coerceIn(0f, 1f); val tkK = an.trkK.toInt(); val tkP = an.trkP
        fun sstep(x: Float): Float { val q = x.coerceIn(0f, 1f); return q * q * (3f - 2f * q) }
        val tkFlip = tkK == 1 || tkK == 2 || tkK >= 10          // mortal/looping/espirais: encolhe o corpo
        val tkAnt = tkW * sin(3.1416f * (tkP / 0.13f).coerceIn(0f, 1f))                                  // preparação: tronco recua, braços vão pra trás
        val tkHold = tkW * sstep((tkP - 0.07f) / 0.16f) * (1f - sstep((tkP - 0.70f) / 0.16f))             // corpo na posição da manobra
        val tkOpen = tkW * sin(3.1416f * ((tkP - 0.80f) / 0.20f).coerceIn(0f, 1f))                        // saída: abre braços e pernas
        val tkTuck = if (tkFlip) tkHold else 0f; val tkRoll = if (tkFlip) 0f else tkHold
        val dashW = max(an.dash.coerceIn(0f, 1f), an.imp.coerceIn(0f, 1f)) * (1f - swim01(an))
        val uImp = 1f - an.imp.coerceIn(0f, 1f)
        val wind = if (an.imp <= 0f) 0f else if (uImp < 0.4f) uImp / 0.4f else max(0f, 1f - (uImp - 0.4f) / 0.25f)
        val atk = an.atk; val atking = atk < 1f
        val stride = if (swimming) 0f else min(1f, mv * 2.2f).let { it * it * (3f - 2f * it) } * (1f - ab) * (1f - fl)
        var sw = sin(ph) * (26f + 30f * rn) * stride
        var hipL = sw; var hipR = -sw
        var kneeL = max(0f, -cos(ph)) * (12f + 50f * rn) * stride
        var kneeR = max(0f, cos(ph)) * (12f + 50f * rn) * stride
        if (swimming) {
            val sa = sin(t * 8f); val sb = sin(t * 4.6f); val cb = cos(t * 4.6f)
            // na superfície: pedalada; mergulhado: batida de perna reta (crawl)
            hipL = lp(sb * 26f, sa * 30f, dv); hipR = lp(-sb * 26f, -sa * 30f, dv)
            kneeL = lp(30f + cb * 24f, 8f + sa * 10f, dv); kneeR = lp(30f - cb * 24f, 8f - sa * 10f, dv)
        }
        if (ab > 0.01f) {
            val pL = lp(-38f, -12f + sin(t * 10f) * 3f, fs); val pKL = lp(62f, 20f, fs)
            val pR = lp(8f, 16f, fs); val pKR = lp(28f, 34f, fs)
            hipL = lp(hipL, pL, ab); kneeL = lp(kneeL, pKL, ab); hipR = lp(hipR, pR, ab); kneeR = lp(kneeR, pKR, ab)
        }
        if (fl > 0.01f) {
            val fk = sin(t * 7f) * 1.2f * fm   // sem passada: as duas pernas juntas, só um tremidinho de vento
            val fh = -5f - 16f * fm + sin(t * 1.6f) * 3f * (1f - fm) + sin(t * 5f) * 2.5f * fm   // voando: pernas esticadas pra trás, juntas   // pairando: as duas pernas iguais (nada de passo), só uma balançadinha junto com o corpo
            hipL = lp(hipL, fh + fk, fl); hipR = lp(hipR, fh + fk, fl)
            kneeL = lp(kneeL, 12f + 6f * fm, fl); kneeR = lp(kneeR, 12f + 6f * fm, fl)
        }
        if (dashW > 0.001f && fl > 0.01f) {
            val fk2 = sin(t * 14f) * 1.5f
            hipL = lp(hipL, 8f + fk2 + 20f * wind, dashW); hipR = lp(hipR, 8f + fk2 + 20f * wind, dashW)
            kneeL = lp(kneeL, 8f + 55f * wind, dashW); kneeR = lp(kneeR, 8f + 55f * wind, dashW)
        }
        if (tkW > 0.001f) {   // pernas na manobra: grupadas no mortal, juntas e retas no tonel, esticadas na abertura
            val wA = tkAnt * 0.6f
            hipL = lp(hipL, -22f, wA); hipR = lp(hipR, -22f, wA); kneeL = lp(kneeL, 34f, wA); kneeR = lp(kneeR, 34f, wA)
            hipL = lp(hipL, -92f, tkTuck); hipR = lp(hipR, -92f, tkTuck); kneeL = lp(kneeL, 108f, tkTuck); kneeR = lp(kneeR, 108f, tkTuck)
            hipL = lp(hipL, 7f, tkRoll); hipR = lp(hipR, 7f, tkRoll); kneeL = lp(kneeL, 5f, tkRoll); kneeR = lp(kneeR, 5f, tkRoll)
            hipL = lp(hipL, 10f, tkOpen); hipR = lp(hipR, 6f, tkOpen); kneeL = lp(kneeL, 8f, tkOpen); kneeR = lp(kneeR, 14f, tkOpen)
        }
        if (land > 0f) {
            hipL += (-42f - hipL) * land; hipR += (-42f - hipR) * land
            kneeL += (80f - kneeL) * land; kneeR += (80f - kneeR) * land
        }
        if (full || wide || overalls.not() && skirtBot) {   // roupa longa/saia: pernas não atravessam o tecido
            val kk = if (full) 0.5f else if (wide) 0.6f else 0.85f   // a saia agora acompanha as coxas, então pode balançar bem mais
            val cap = if (full) 13f else if (wide) 17f else 26f
            hipL = (hipL * kk).coerceIn(-cap, cap); hipR = (hipR * kk).coerceIn(-cap, cap)
            kneeL = (kneeL * kk).coerceIn(0f, if (full) 24f else if (wide) 36f else 60f); kneeR = (kneeR * kk).coerceIn(0f, if (full) 24f else if (wide) 36f else 60f)
        }
        // posturas: agachar (pernas dobradas, tronco inclinado), sentar (pernas à frente, no chão), deitar (de costas)
        val cr = an.crouch.coerceIn(0f, 1f); val si = an.sit.coerceIn(0f, 1f)
        var qLegL = 0f; var qLegR = 0f
        if (cr > 0f) { hipL = lp(hipL, -62f + sw * 0.4f, cr); hipR = lp(hipR, -62f - sw * 0.4f, cr); kneeL = lp(kneeL, 72f + kneeL * 0.3f, cr); kneeR = lp(kneeR, 72f + kneeR * 0.3f, cr) }
        if (si > 0f) { val br = sin(t * 1.6f) * 2f; hipL = lp(hipL, -87f + br, si); hipR = lp(hipR, -87f + br, si); kneeL = lp(kneeL, 4f, si); kneeR = lp(kneeR, 4f, si) }
        val bt = an.bent.coerceIn(0f, 1f)
        if (bt > 0f) { hipL = lp(hipL, -72f, bt); hipR = lp(hipR, -72f, bt); kneeL = lp(kneeL, 62f, bt); kneeR = lp(kneeR, 62f, bt) }
        if (li > 0f) { val mc = min(1f, mv * 3f); qLegL = (0.5f + 0.5f * sin(ph + 3.1416f)) * mc; qLegR = (0.5f + 0.5f * sin(ph)) * mc
            hipL = lp(hipL, 2f + 7f * qLegL, li); hipR = lp(hipR, 2f + 7f * qLegR, li)
            kneeL = lp(kneeL, 4f + 26f * qLegL, li); kneeR = lp(kneeR, 4f + 26f * qLegR, li) }
        val drop = 0.1f * land + 0.12f * cr + 0.31f * si
        val bob = sin(t * 2f) * 0.012f * (1f - stride) + abs(sin(ph)) * (0.035f + 0.04f * rn) * stride * (1f - 0.85f * li) + fl * (1f - fm) * sin(t * 1.6f) * 0.09f   // pairando: sobe e desce devagar
        val move = stride * (1f + rn * 0.6f) + ab * lp(0.4f, 1.2f, fs)

        System.arraycopy(root, 0, r2, 0, 16)
        Matrix.translateM(r2, 0, 0f, bob - drop, 0f)
        if (li > 0.001f) {   // deitado de costas: gira o corpo todo em volta do centro
            Matrix.translateM(r2, 0, 0f, 0.2f * li, -0.7f * li)   // rastejando, de frente (barriga no chão)
            Matrix.rotateM(r2, 0, 90f * li, 1f, 0f, 0f)
        }

        val lean = 30f * an.dash.coerceIn(0f, 1f) * fl - 30f * wind * dashW + 18f * cr + 6f * si + 3f * stride * (1f - li) - 7f * li + 12f * rn * stride + ab * lp(-5f, 7f, fs) + 16f * land + fl * (8f + 52f * fm) - 10f * chg + 34f * tkTuck - 12f * tkRoll - 12f * tkAnt - 8f * tkOpen +
            (if (atking) 9f * sin(atk * 3.1416f) else 0f)
        val twist = sin(ph) * (5f + 9f * rn) * stride * (1f - li) + 14f * chg + (if (atking) 18f * sin(atk * 6.2832f) else 0f)
        val roll = sin(ph) * 2.2f * stride * (1f - rn) + 8f * li * min(1f, mv * 3f) * sin(ph) + (if (air) 0f else sin(t * 0.9f) * 1.3f * (1f - stride))
        System.arraycopy(r2, 0, bm, 0, 16)
        Matrix.translateM(bm, 0, 0f, 0.46f, 0f)
        if (roll != 0f) Matrix.rotateM(bm, 0, roll, 0f, 0f, 1f)
        if (twist != 0f) Matrix.rotateM(bm, 0, twist, 0f, 1f, 0f)
        if (lean != 0f) Matrix.rotateM(bm, 0, lean, 1f, 0f, 0f)
        if (land > 0f) Matrix.scaleM(bm, 0, 1f + 0.06f * land, 1f - 0.08f * land, 1f + 0.06f * land)
        Matrix.translateM(bm, 0, 0f, -0.46f, 0f)
        val base = bm

        // cabeça: pivô no pescoço, escala menor (cabeção mais proporcional), olha em volta e balança com o passo
        val look = sin(t * 0.55f) * sin(t * 0.23f)
        val hy = if (stride > 0.1f) -twist * 0.7f else look * 16f * (1f - stride)
        val hnod = -lean * 0.55f + sin(ph * 2f) * 1.8f * stride + sin(t * 1.3f) * 1.4f * (1f - stride) + ab * lp(5f, -8f, fs) - an.tilt * lp(0.9f, 0.5f, dv) - 70f * li
        val htilt = sin(t * 0.8f) * 2f * (1f - stride)
        System.arraycopy(bm, 0, hm, 0, 16)
        Matrix.translateM(hm, 0, 0f, 0.94f, 0f)
        if (htilt != 0f) Matrix.rotateM(hm, 0, htilt, 0f, 0f, 1f)
        if (hy != 0f) Matrix.rotateM(hm, 0, hy, 0f, 1f, 0f)
        if (hnod != 0f) Matrix.rotateM(hm, 0, hnod, 1f, 0f, 0f)
        Matrix.scaleM(hm, 0, HEAD_SCALE, HEAD_SCALE, HEAD_SCALE)
        Matrix.translateM(hm, 0, 0f, -0.94f, 0f)
        val blink = (t % 3.7f) > 3.58f || (t % 6.1f) > 6.0f

        // saia balançando (preso ao quadril)
        val flare = 1f + ab * lp(0.06f, 0.1f, fs) + 0.05f * rn * stride
        piv(sm, bm, 0f, 0.5f, 0f, rx = sin(t * 2.2f) * 1.5f + sin(ph * 2f) * 3f * stride - (if (full || skirtBot) (lean * (1f - fl)).coerceIn(-20f, 20f) * 0.7f else 0f) - (if (full) 82f else 62f) * an.sit.coerceIn(0f, 1f) - (if (full) 56f else 40f) * an.crouch.coerceIn(0f, 1f))
        Matrix.scaleM(sm, 0, flare, 1f, flare)
        val skRows = ArrayList<SkR>()
        fun sk(py: Float, sx: Float, sy: Float, sz: Float, col: Int) { skRows.add(SkR(0f, py, 0f, sx, sy, sz, col, true)) }

        // ================= PERNAS (coxa + joelho) =================
        var seg = when (bot) { 0 -> 0.40f; 1 -> 0.17f; 2 -> 0f; 3 -> 0.30f; 4 -> 0.40f; 5 -> 0f; 6 -> 0.44f; else -> 0.30f }
        var legCol = bc
        if (full) seg = 0f
        if (bot == 6) seg = 0.42f
        if (overalls) { seg = 0.40f; legCol = tc }
        val legW = when (bot) { 4 -> 0.31f; 6 -> 0.2f; else -> 0.205f }
        val legD = when (bot) { 4 -> 0.31f; 6 -> 0.215f; else -> 0.225f }
        val sh = L[Look.SHOES]; val shc = Look.pal(Look.SHOEC)
        for (sd in intArrayOf(-1, 1)) {
            val s = sd.toFloat()
            val hip = if (sd < 0) hipL else hipR; val kn = if (sd < 0) kneeL else kneeR
            piv(t1, r2, s * 0.11f, 0.44f, 0f, rx = hip + lean * fl, rz = s * (5f + 7f * (if (sd < 0) qLegL else qLegR)) * li)   // rastejando: pernas abertas pro lado   // voando: as pernas acompanham a inclinação do corpo
            piv(t2, t1, 0f, -0.22f, 0f, rx = kn)
            run {   // a pele só existe onde a roupa não cobre (senão ela aparece nas dobras do joelho)
                val tTop = if (seg > 0f) 0.02f - min(seg, 0.22f) else 0.015f
                val tBot = -0.23f
                if (tTop - tBot > 0.012f && seg < 0.22f) b(t1, 0f, (tTop + tBot) / 2f, 0f, 0.19f, tTop - tBot, 0.21f, skin)   // coxa toda vestida: sem miolo de pele (ele aparecia na dobra do joelho)
                val sTop = if (seg > 0.22f) 0.02f - (seg - 0.22f) else 0.01f
                val sBot = -0.17f   // termina dentro do calçado; antes ia até o chão (-0.22) e aparecia embaixo da sola
                if (sTop - sBot > 0.012f) b(t2, 0f, (sTop + sBot) / 2f, 0f, 0.19f, sTop - sBot, 0.21f, skin)
            }
            // juntas: quadril e joelho com a cor da roupa (ou da pele) pra não abrir vão ao dobrar
            val hipC = if (seg > 0f) legCol else skin
            b(t1, 0f, 0.025f, 0f, (if (seg > 0f) legW else 0.19f) - 0.004f, 0.11f, (if (seg > 0f) legD else 0.21f) - 0.004f, hipC)
            if (seg <= 0.22f) b(t2, 0f, 0f, 0f, 0.192f, 0.07f, 0.212f, skin)
            if (seg > 0f) {
                val st = min(seg, 0.22f); val sn = seg - st
                b(t1, 0f, 0.005f - st / 2f, 0f, legW, st + 0.01f, legD, legCol)
                if (sn > 0f) {
                    b(t2, 0f, -sn / 2f, 0f, legW, sn, legD, legCol)
                    b(t2, 0f, 0f, 0f, legW - 0.006f, 0.15f, legD - 0.006f, legCol)   // joelho bem cheio: sem vão ao dobrar
                    if (seg >= 0.3f) b(t2, 0f, -sn + 0.0175f, 0f, legW + 0.012f, 0.035f, legD + 0.012f, if (bot == 4) lite(bc, 0.5f) else shadeC(legCol, 0.8f))
                }
                if (bot == 7) b(t1, s * (legW / 2f + 0.006f), -0.12f, 0.02f, 0.025f, 0.12f, 0.12f, shadeC(legCol, 0.82f))
                if (bot == 4) b(t1, 0f, -0.1f, legD / 2f + 0.004f, 0.02f, 0.2f, 0.01f, shadeC(legCol, 0.75f))
                run {   // ---- upgrade das partes de baixo ----
                    val ed = legD / 2f + 0.004f; val ew = legW / 2f + 0.004f
                    val thC = shadeC(legCol, 0.8f); val liC = lite(legCol, 0.3f); val stitch = lite(legCol, 0.5f)
                    when (bot) {
                        0 -> {  // calça: vinco, costura lateral, bolso traseiro, joelho, barra dobrada
                            b(t1, 0f, -0.11f, ed, 0.016f, 0.2f, 0.01f, thC); b(t2, 0f, -sn / 2f, ed, 0.016f, sn * 0.9f, 0.01f, thC)
                            b(t1, s * ew, -0.11f, 0f, 0.01f, 0.2f, 0.012f, liC); b(t2, s * ew, -sn / 2f, 0f, 0.01f, sn * 0.9f, 0.012f, liC)
                            b(t1, 0f, -0.075f, -ed, 0.11f, 0.08f, 0.01f, thC); b(t1, 0f, -0.04f, -ed, 0.11f, 0.012f, 0.012f, stitch)
                            b(t2, 0f, -0.02f, ed, 0.11f, 0.012f, 0.01f, thC)
                            if (sn > 0.07f) b(t2, 0f, -sn + 0.055f, 0f, legW + 0.014f, 0.012f, legD + 0.014f, liC)
                        }
                        1 -> {  // shorts: barra virada, listra lateral, costura do bolso
                            b(t1, 0f, -seg + 0.012f, 0f, legW + 0.012f, 0.03f, legD + 0.012f, liC)
                            b(t1, s * ew, -0.07f, 0f, 0.01f, 0.12f, 0.14f, lite(legCol, 0.18f)); b(t1, 0f, -0.06f, ed, 0.1f, 0.012f, 0.01f, thC)
                            b(t1, 0f, -0.06f, -ed, 0.1f, 0.06f, 0.01f, thC)
                        }
                        3 -> {  // bermuda: bolso lateral com aba e botão, barra com costura
                            b(t1, s * (ew + 0.008f), -0.1f, 0.01f, 0.012f, 0.13f, 0.13f, thC); b(t1, s * (ew + 0.012f), -0.055f, 0.01f, 0.012f, 0.014f, 0.13f, liC)
                            b(t1, s * (ew + 0.014f), -0.07f, 0.01f, 0.014f, 0.02f, 0.02f, gold)
                            b(t1, 0f, -0.09f, ed, 0.014f, 0.16f, 0.01f, thC); b(t2, 0f, -sn + 0.05f, 0f, legW + 0.014f, 0.012f, legD + 0.014f, stitch)
                            b(t1, 0f, -0.07f, -ed, 0.11f, 0.075f, 0.01f, thC)
                        }
                        4 -> {  // hakama: pregas, barra dourada
                            for (px in intArrayOf(-1, 0, 1)) { b(t1, px * 0.075f, -0.1f, ed, 0.012f, 0.2f, 0.01f, thC); b(t2, px * 0.075f, -sn / 2f, ed, 0.012f, sn * 0.9f, 0.01f, thC) }
                            b(t1, 0f, -0.02f, ed, legW * 0.9f, 0.012f, 0.01f, liC)
                            b(t2, 0f, -sn + 0.045f, 0f, legW + 0.016f, 0.014f, legD + 0.016f, gold)
                        }
                        6 -> {  // leggings: listra lateral, joelheira, bainha
                            b(t1, s * ew, -0.1f, 0f, 0.008f, 0.2f, 0.1f, liC); b(t2, s * ew, -sn / 2f, 0f, 0.008f, sn * 0.9f, 0.1f, liC)
                            b(t2, 0f, -0.02f, ed, 0.13f, 0.06f, 0.008f, thC); b(t1, 0f, -0.2f, ed, 0.13f, 0.012f, 0.008f, liC)
                            b(t2, 0f, -sn + 0.05f, 0f, legW + 0.01f, 0.012f, legD + 0.01f, liC)
                        }
                        else -> {  // cargo: bolsos com aba e botão, joelheira, punho amarrado, bolso traseiro
                            b(t1, s * (ew + 0.01f), -0.115f, 0.01f, 0.014f, 0.15f, 0.15f, thC); b(t1, s * (ew + 0.014f), -0.06f, 0.01f, 0.012f, 0.016f, 0.15f, liC)
                            b(t1, s * (ew + 0.016f), -0.075f, 0.01f, 0.014f, 0.022f, 0.022f, gold)
                            b(t2, 0f, -0.04f, ed, 0.15f, 0.1f, 0.01f, thC); b(t2, 0f, -0.04f, ed + 0.004f, 0.15f, 0.012f, 0.01f, stitch)
                            b(t1, 0f, -0.075f, -ed, 0.12f, 0.08f, 0.01f, thC)
                            b(t2, 0f, -sn + 0.045f, 0f, legW + 0.016f, 0.02f, legD + 0.016f, thC)
                        }
                    }
                    // Camada de acabamento selecionável: detalhes voxel ficam presos à perna e acompanham a animação.
                    val pat = Look.v[Look.BOTTOM_PATTERN]
                    val accent = Look.pal(Look.BOTTOM_ACCENT)
                    val frontZ = ed + 0.014f
                    when (pat) {
                        1 -> {
                            b(t1, s * (ew + 0.006f), -0.1f, 0f, 0.024f, 0.19f, legD + 0.014f, accent)
                            b(t2, s * (ew + 0.006f), -sn * 0.5f, 0f, 0.024f, max(0.02f, sn * 0.86f), legD + 0.014f, accent)
                            b(t1, s * (ew + 0.009f), -0.1f, frontZ, 0.01f, 0.18f, 0.012f, lite(accent, 0.35f))
                        }
                        2 -> {
                            b(t1, 0f, -0.075f, frontZ, 0.105f, 0.09f, 0.018f, shadeC(accent, 0.72f))
                            b(t1, 0f, -0.055f, frontZ + 0.012f, 0.075f, 0.045f, 0.012f, accent)
                            b(t1, -0.018f, -0.045f, frontZ + 0.02f, 0.018f, 0.018f, 0.01f, lite(accent, 0.4f))
                            b(t1, 0.02f, -0.068f, frontZ + 0.02f, 0.018f, 0.018f, 0.01f, shadeC(accent, 0.7f))
                        }
                        3 -> {
                            b(t2, 0f, -0.035f, frontZ, 0.145f, 0.075f, 0.02f, shadeC(accent, 0.65f))
                            b(t2, 0f, -0.035f, frontZ + 0.012f, 0.12f, 0.052f, 0.012f, accent)
                            for (sx in intArrayOf(-1, 1)) b(t2, sx * 0.047f, -0.035f, frontZ + 0.02f, 0.012f, 0.012f, 0.01f, lite(accent, 0.45f))
                        }
                        4 -> {
                            b(t1, s * (ew + 0.008f), -0.11f, 0.02f, 0.022f, 0.14f, 0.15f, shadeC(accent, 0.68f))
                            b(t1, s * (ew + 0.012f), -0.08f, 0.02f, 0.025f, 0.07f, 0.16f, accent)
                            b(t1, s * (ew + 0.016f), -0.05f, 0.02f, 0.014f, 0.012f, 0.02f, lite(accent, 0.45f))
                        }
                        5 -> {
                            for (k in 0..2) {
                                val y = -0.055f - k * 0.045f
                                b(t1, 0f, y, frontZ, legW * 0.72f, 0.012f, 0.014f, if (k == 1) shadeC(accent, 0.75f) else accent)
                                if (sn > 0.08f) b(t2, 0f, -sn * 0.45f + k * 0.025f, frontZ, legW * 0.62f, 0.01f, 0.014f, accent)
                            }
                        }
                        6 -> {
                            for (k in -1..1) {
                                b(t1, k * 0.055f, -0.075f, frontZ, 0.035f, 0.035f, 0.014f, accent, rz = 45f)
                                b(t1, k * 0.055f, -0.075f, frontZ + 0.009f, 0.012f, 0.012f, 0.01f, lite(accent, 0.45f))
                            }
                        }
                    }
                    // Segundo passe de modelagem: costuras e bolsos que diferenciam cada corte.
                    val fabricHi = lite(legCol, 0.22f)
                    val fabricLo = shadeC(legCol, 0.62f)
                    when (bot) {
                        0 -> { b(t1,0f,-0.12f,frontZ+0.003f,0.008f,0.16f,0.008f,fabricHi); b(t1,-0.055f,-0.07f,frontZ+0.006f,0.06f,0.012f,0.009f,fabricLo); b(t1,-0.075f,-0.085f,frontZ+0.01f,0.012f,0.012f,0.009f,gold); if(sn>0.08f)b(t2,0f,-sn*0.56f,frontZ,0.008f,sn*0.72f,0.009f,fabricHi) }
                        1 -> { b(t1,0f,-seg+0.024f,frontZ+0.006f,legW*0.78f,0.008f,0.009f,fabricLo); b(t1,s*0.065f,-0.11f,frontZ+0.006f,0.045f,0.04f,0.009f,accent); b(t1,s*0.065f,-0.11f,frontZ+0.012f,0.018f,0.018f,0.008f,lite(accent,0.4f)) }
                        3 -> { b(t1,s*0.065f,-0.13f,frontZ+0.004f,0.075f,0.09f,0.012f,fabricLo); b(t1,s*0.065f,-0.13f,frontZ+0.012f,0.058f,0.07f,0.012f,legCol); b(t1,s*0.065f,-0.09f,frontZ+0.02f,0.036f,0.012f,0.01f,gold) }
                        4 -> { for(px in intArrayOf(-1,1)){ b(t1,px*0.052f,-0.105f,frontZ+0.004f,0.008f,0.18f,0.008f,fabricLo); b(t2,px*0.052f,-sn*0.5f,frontZ+0.004f,0.008f,max(0.02f,sn*0.82f),0.008f,fabricHi) }; b(t1,0f,-0.03f,frontZ+0.01f,0.1f,0.018f,0.012f,gold) }
                        6 -> { b(t1,s*0.06f,-0.1f,frontZ+0.003f,0.025f,0.18f,0.01f,fabricLo); b(t1,s*0.06f,-0.1f,frontZ+0.009f,0.008f,0.16f,0.008f,0xD5D9E0); b(t2,0f,-0.04f,frontZ+0.004f,0.13f,0.012f,0.009f,fabricHi) }
                        7 -> { b(t1,s*0.065f,-0.12f,frontZ+0.006f,0.07f,0.1f,0.016f,fabricLo); b(t1,s*0.065f,-0.105f,frontZ+0.016f,0.055f,0.07f,0.012f,legCol); b(t1,s*0.065f,-0.065f,frontZ+0.025f,0.045f,0.012f,0.01f,fabricHi); b(t1,s*0.065f,-0.085f,frontZ+0.03f,0.012f,0.02f,0.01f,gold) }
                        else -> { }
                    }
                }
            }
            fun ya(y: Float) = y + 0.22f   // converte pra o referencial da canela
            // ---- calçados: tudo em coordenadas "do quadril" (chão = -0.44, joelho = -0.22). Nada passa de -0.25: antes a bota alta e as meias subiam até a coxa e furavam o joelho. ----
            fun sb(yA: Float, yB: Float, zB: Float, zF: Float, w: Float, col: Int) = b(t2, 0f, ya((yA + yB) / 2f), (zB + zF) / 2f, w, abs(yA - yB), abs(zF - zB), col)   // aceita os dois Y em qualquer ordem
            val soleD = 0x2E211B
            when (sh) {
                0 -> {  // botas curtas de couro: sola, bico, cano baixo com barra, língua, ilhós e fivela
                    val upL = lite(shc, 0.22f)
                    sb(-0.436f, -0.415f, -0.13f, 0.2f, 0.235f, soleD)
                    sb(-0.415f, -0.34f, -0.125f, 0.17f, 0.225f, shc)
                    sb(-0.415f, -0.36f, 0.11f, 0.19f, 0.232f, upL)
                    sb(-0.34f, -0.3f, -0.125f, 0.11f, 0.228f, shc)
                    sb(-0.31f, -0.285f, -0.13f, 0.115f, 0.238f, upL)
                    sb(-0.345f, -0.29f, 0.095f, 0.125f, 0.1f, lite(shc, 0.5f))
                    for (k in 0..1) b(t2, (k * 2 - 1) * 0.04f, ya(-0.32f), 0.122f, 0.014f, 0.014f, 0.012f, gold)
                    sb(-0.31f, -0.29f, -0.145f, -0.125f, 0.05f, upL)
                    for (sx in intArrayOf(-1, 1)) b(t2, sx * 0.117f, ya(-0.335f), 0f, 0.012f, 0.03f, 0.05f, gold)
                }
                1 -> {  // tênis: sola branca, biqueira, cadarço, listra lateral, língua e lingueta
                    sb(-0.436f, -0.42f, -0.135f, 0.205f, 0.24f, white)
                    sb(-0.42f, -0.345f, -0.125f, 0.175f, 0.225f, shc)
                    sb(-0.42f, -0.375f, 0.11f, 0.195f, 0.232f, white)
                    sb(-0.35f, -0.3f, -0.125f, 0.0f, 0.226f, shc)
                    sb(-0.305f, -0.295f, -0.13f, 0.005f, 0.234f, lite(shc, 0.45f))
                    sb(-0.355f, -0.315f, 0.0f, 0.14f, 0.12f, lite(shc, 0.55f))
                    for (k in 0..2) b(t2, 0f, ya(-0.348f), 0.03f + k * 0.04f, 0.11f, 0.012f, 0.014f, white)
                    for (sx in intArrayOf(-1, 1)) b(t2, sx * 0.115f, ya(-0.385f), 0.03f, 0.008f, 0.03f, 0.14f, white)
                    sb(-0.37f, -0.31f, -0.14f, -0.125f, 0.07f, white)
                }
                2 -> {  // sapatilha fechada: calcanhar fechado atrás, bico, laço e tira com fivela
                    val bw2 = lite(shc, 0.4f)
                    sb(-0.436f, -0.425f, -0.125f, 0.185f, 0.225f, 0x3A2A22)
                    sb(-0.425f, -0.385f, -0.125f, 0.18f, 0.22f, shc)
                    sb(-0.385f, -0.355f, -0.128f, -0.06f, 0.222f, shc)
                    sb(-0.385f, -0.37f, 0.1f, 0.18f, 0.214f, shadeC(shc, 0.9f))
                    sb(-0.385f, -0.368f, 0.075f, 0.105f, 0.045f, bw2)
                    for (sx in intArrayOf(-1, 1)) b(t2, sx * 0.04f, ya(-0.377f), 0.09f, 0.05f, 0.02f, 0.045f, bw2)
                    sb(-0.385f, -0.368f, -0.02f, 0.01f, 0.224f, shadeC(shc, 0.8f))
                    for (sx in intArrayOf(-1, 1)) b(t2, sx * 0.114f, ya(-0.377f), -0.005f, 0.01f, 0.022f, 0.022f, gold)
                }
                3 -> {  // descalço: pé com calcanhar, peito do pé, cinco dedinhos e unhas
                    sb(-0.436f, -0.395f, -0.11f, 0.17f, 0.2f, skin)
                    sb(-0.395f, -0.385f, -0.11f, 0.1f, 0.2f, skinD)
                    for (k in -2..2) {
                        val big = if (k == -2) 0.045f else 0.034f
                        b(t2, k * 0.038f, ya(-0.418f), 0.185f, big, 0.04f, 0.035f, skin)
                        b(t2, k * 0.038f, ya(-0.399f), 0.195f, big * 0.55f, 0.008f, 0.014f, lite(skin, 0.45f))
                    }
                }
                4 -> {  // botas altas (agora até logo abaixo do joelho): cano com barra virada, tira com fivela, cadarço e salto
                    val upL = lite(shc, 0.22f); val upD = shadeC(shc, 0.72f)
                    sb(-0.436f, -0.415f, -0.13f, 0.2f, 0.235f, soleD)
                    sb(-0.433f, -0.42f, -0.13f, -0.03f, 0.238f, 0x2A1E18)
                    sb(-0.415f, -0.34f, -0.125f, 0.17f, 0.225f, shc)
                    sb(-0.415f, -0.36f, 0.11f, 0.19f, 0.232f, upL)
                    sb(-0.34f, -0.265f, -0.125f, 0.115f, 0.23f, shc)
                    sb(-0.275f, -0.25f, -0.135f, 0.125f, 0.242f, upL)
                    sb(-0.31f, -0.295f, -0.13f, 0.12f, 0.238f, upD)
                    b(t2, 0f, ya(-0.3025f), 0.125f, 0.04f, 0.026f, 0.012f, gold)
                    sb(-0.34f, -0.265f, -0.138f, -0.125f, 0.04f, upD)
                    for (k in 0..3) b(t2, 0f, ya(-0.335f + k * 0.018f), 0.118f, 0.08f, 0.008f, 0.012f, lite(shc, 0.5f))
                }
                5 -> {  // geta com tabi: meia branca de dedo separado, base de madeira com dois dentes e tira (hanao)
                    sb(-0.436f, -0.4f, -0.09f, -0.01f, 0.23f, 0x6F4A29)
                    sb(-0.436f, -0.4f, 0.085f, 0.155f, 0.23f, 0x6F4A29)
                    sb(-0.4f, -0.375f, -0.11f, 0.175f, 0.232f, 0x9A6A3E)
                    sb(-0.375f, -0.3f, -0.11f, 0.1f, 0.205f, white)
                    sb(-0.375f, -0.34f, 0.1f, 0.17f, 0.2f, white)
                    sb(-0.375f, -0.34f, 0.165f, 0.176f, 0.012f, 0xB8B8C0)
                    b(t2, 0f, ya(-0.338f), 0.08f, 0.13f, 0.012f, 0.03f, shc)
                    for (sx in intArrayOf(-1, 1)) b(t2, sx * 0.055f, ya(-0.345f), 0.115f, 0.016f, 0.012f, 0.09f, shc)
                    sb(-0.36f, -0.34f, 0.15f, 0.18f, 0.03f, shc)
                }
                6 -> {  // mocassim: sola, cabedal, salto, tira com fivela dourada, costura no bico e meia branca
                    sb(-0.436f, -0.425f, -0.125f, 0.19f, 0.225f, 0x2A2220)
                    sb(-0.425f, -0.365f, -0.125f, 0.175f, 0.22f, shc)
                    sb(-0.365f, -0.335f, -0.125f, -0.04f, 0.22f, shc)
                    sb(-0.375f, -0.36f, 0.02f, 0.06f, 0.222f, shadeC(shc, 0.8f))
                    b(t2, 0f, ya(-0.368f), 0.04f, 0.07f, 0.014f, 0.03f, gold)
                    b(t2, 0f, ya(-0.372f), 0.14f, 0.14f, 0.008f, 0.012f, lite(shc, 0.4f))
                    sb(-0.365f, -0.3f, -0.1f, 0.0f, 0.206f, white)
                }
                else -> {  // meias listradas (param logo abaixo do joelho, sem furar a coxa) + tênis baixo
                    sb(-0.436f, -0.42f, -0.135f, 0.205f, 0.24f, white)
                    sb(-0.42f, -0.36f, -0.125f, 0.175f, 0.225f, shc)
                    sb(-0.42f, -0.385f, 0.11f, 0.195f, 0.232f, white)
                    for (k in 0..1) b(t2, 0f, ya(-0.365f), 0.04f + k * 0.04f, 0.1f, 0.012f, 0.014f, white)
                    for (sx in intArrayOf(-1, 1)) b(t2, sx * 0.115f, ya(-0.395f), 0.03f, 0.008f, 0.025f, 0.12f, white)
                    val bands = intArrayOf(white, shc, white, shc, white)
                    for (k in bands.indices) sb(-0.36f + (k + 1) * 0.0225f, -0.36f + k * 0.0225f, -0.115f, 0.105f, 0.206f, bands[k])
                }
            }
            // Acabamento próprio de cada calçado: costura, ilhós, gravação e metais em relevo.
            when (sh) {
                0 -> { for(i in 0..2) b(t2,0f,ya(-0.31f-i*0.025f),0.128f,0.11f,0.006f,0.009f,lite(shc,0.42f)); for(sx in intArrayOf(-1,1)) b(t2,sx*0.08f,ya(-0.325f),0.139f,0.014f,0.014f,0.01f,0xE9C56D) }
                1 -> { b(t2,0f,ya(-0.375f),0.236f,0.15f,0.008f,0.008f,shadeC(shc,0.72f)); for(i in 0..3) b(t2,if(i%2==0)-0.04f else 0.04f,ya(-0.345f+i*0.012f),0.134f,0.02f,0.012f,0.012f,0xD5D9E0); b(t2,0.075f,ya(-0.365f),0.238f,0.035f,0.025f,0.008f,lite(shc,0.35f)) }
                2 -> { b(t2,0f,ya(-0.371f),0.225f,0.14f,0.012f,0.009f,lite(shc,0.3f)); b(t2,0f,ya(-0.37f),0.24f,0.04f,0.024f,0.012f,0xE9C56D); b(t2,0f,ya(-0.37f),0.248f,0.018f,0.012f,0.006f,0x65452B) }
                3 -> { b(t2,0f,ya(-0.39f),0.06f,0.19f,0.018f,0.014f,0xB98B55); for(sx in intArrayOf(-1,0,1)) b(t2,sx*0.055f,ya(-0.39f),0.073f,0.018f,0.018f,0.012f,gold) }
                4 -> { for(i in 0..3) b(t2,0f,ya(-0.285f-i*0.018f),0.13f,0.13f,0.006f,0.009f,lite(shc,0.35f)); b(t2,0f,ya(-0.302f),0.145f,0.035f,0.028f,0.012f,gold); b(t2,0f,ya(-0.302f),0.154f,0.014f,0.014f,0.008f,0x48BFE3) }
                5 -> { b(t2,0f,ya(-0.414f),0.17f,0.12f,0.008f,0.009f,0xD7B17B); b(t2,-0.035f,ya(-0.414f),0.176f,0.03f,0.008f,0.008f,0x6F4A29); b(t2,0.035f,ya(-0.414f),0.176f,0.03f,0.008f,0.008f,0x6F4A29) }
                6 -> { b(t2,-0.045f,ya(-0.37f),0.148f,0.012f,0.025f,0.01f,gold,rz=-18f); b(t2,0f,ya(-0.358f),0.148f,0.09f,0.012f,0.01f,gold); b(t2,0.045f,ya(-0.37f),0.148f,0.012f,0.025f,0.01f,gold,rz=18f) }
                else -> { for(i in 0..2) b(t2,0f,ya(-0.355f+i*0.014f),0.116f,0.18f,0.006f,0.008f,if(i%2==0)white else lite(shc,0.25f)); b(t2,0.075f,ya(-0.385f),0.238f,0.035f,0.025f,0.008f,0xFFD35C) }
            }
        }

        // ================= CORPO =================
        if (!full && !overalls) b(base, 0f, 0.46f, 0f, bw + 0.015f, 0.05f, 0.315f, shadeC(bc, 0.85f))
        if (full) b(base, 0f, 0.45f, 0f, bw, 0.06f, 0.3f, tc)   // vestido/kimono: a cintura dobra mostrando a roupa, não a pele do quadril
        // pelve: uma caixa presa ao tronco + um "assento" preso ao corpo todo, na cor da roupa de baixo. Sem isso, ao agachar/sentar as coxas giram pra frente e dá pra ver o vazio por dentro.
        val pelC = if (full || overalls) tc else bc
        b(base, 0f, 0.425f, 0f, bw + 0.005f, 0.09f, 0.305f, pelC)
        b(r2, 0f, 0.4f, -0.01f, bw - 0.02f, 0.1f, 0.27f, pelC)
        if (!full && !overalls) {
            b(base, 0f, 0.447f, 0f, bw + 0.02f, 0.014f, 0.322f, if (bot == 1 || bot == 6) lite(bc, 0.4f) else 0x3A2A22)
            if (bot == 0 || bot == 3 || bot == 7 || bot == 4) b(base, 0f, 0.446f, 0.166f, 0.06f, 0.04f, 0.012f, gold)
            for (i in -2..2) if (i != 0) b(base, i * 0.085f, 0.452f, 0f, 0.022f, 0.045f, 0.326f, shadeC(bc, 0.7f))
        }
        // ---- helpers de roupa: F = detalhe colado na FRENTE, K = detalhe colado nas COSTAS, band = faixa que dá a volta no tronco ----
        val hw = bw / 2f
        fun F(x: Float, y: Float, w: Float, h: Float, col: Int, d: Float = 0.014f, rz: Float = 0f) = b(base, x, y, 0.153f + d / 2f, w, h, d, col, rz = rz)
        fun K(x: Float, y: Float, w: Float, h: Float, col: Int, d: Float = 0.014f, rz: Float = 0f) = b(base, x, y, -(0.153f + d / 2f), w, h, d, col, rz = rz)
        fun band(y: Float, h: Float, col: Int, e: Float = 0.012f) = b(base, 0f, y, 0f, bw + e, h, 0.3f + e, col)
        val steel = 0xC5CEDA; val leather = 0x6F4A29; val leatherD = 0x4A3019
        // IMPORTANTE: o tronco inteiro (frente, costas e lados) é SEMPRE da cor da roupa. Antes jaqueta/macacão/colete/haori usavam base branca e as costas apareciam brancas.
        when (top) {
            0 -> {  // camiseta: gola canelada, listras, estampa de estrela, etiqueta nas costas
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                band(0.46f, 0.045f, dark, 0.01f)
                band(0.595f, 0.022f, trim, 0.006f); band(0.55f, 0.022f, trim, 0.006f)
                b(base, 0f, 0.935f, 0f, bw * 0.55f, 0.035f, 0.32f, trim)
                F(0f, 0.91f, bw * 0.3f, 0.03f, trim); K(0f, 0.915f, bw * 0.34f, 0.02f, dark)
                val st = lite(tc, 0.75f)
                F(0f, 0.78f, 0.045f, 0.14f, st); F(0f, 0.78f, 0.14f, 0.045f, st); F(0f, 0.78f, 0.09f, 0.09f, lite(tc, 0.9f), 0.018f)
                K(0f, 0.895f, 0.06f, 0.04f, white)
                b(base, -hw + 0.004f, 0.69f, 0f, 0.012f, 0.46f, 0.296f, dark); b(base, hw - 0.004f, 0.69f, 0f, 0.012f, 0.46f, 0.296f, dark)
            }
            1 -> {  // moletom: capuz volumoso, cordões, bolso canguru, barra e punhos canelados
                b(base, 0f, 0.69f, 0f, bw + 0.02f, 0.5f, 0.32f, tc)
                band(0.47f, 0.06f, dark, 0.04f); band(0.505f, 0.015f, lite(tc, 0.2f), 0.03f)
                F(0f, 0.585f, 0.3f, 0.14f, dark, 0.03f, 0f); F(0f, 0.65f, 0.3f, 0.018f, lite(tc, 0.25f), 0.034f)
                F(-0.158f, 0.575f, 0.02f, 0.11f, shadeC(tc, 0.6f), 0.034f, 8f); F(0.158f, 0.575f, 0.02f, 0.11f, shadeC(tc, 0.6f), 0.034f, -8f)
                // capuz: arco em volta do pescoço (frente aberta) + bolsão atrás
                b(base, 0f, 0.895f, -0.2f, 0.34f, 0.13f, 0.13f, shadeC(tc, 0.85f)); b(base, 0f, 0.87f, -0.2f, 0.24f, 0.1f, 0.14f, dark)
                b(base, -0.17f, 0.915f, 0.02f, 0.1f, 0.06f, 0.3f, shadeC(tc, 0.88f)); b(base, 0.17f, 0.915f, 0.02f, 0.1f, 0.06f, 0.3f, shadeC(tc, 0.88f))
                b(base, 0f, 0.925f, 0.0f, 0.3f, 0.03f, 0.33f, dark)
                F(-0.06f, 0.82f, 0.022f, 0.15f, white, 0.02f); F(0.06f, 0.82f, 0.022f, 0.15f, white, 0.02f)
                F(-0.06f, 0.735f, 0.03f, 0.03f, 0xDDDDDD, 0.024f); F(0.06f, 0.735f, 0.03f, 0.03f, 0xDDDDDD, 0.024f)
                K(0f, 0.7f, 0.2f, 0.03f, lite(tc, 0.25f)); K(0f, 0.66f, 0.12f, 0.03f, lite(tc, 0.25f))
            }
            2 -> {  // vestido: corpete com decote quadrado, alças, faixa, laço atrás, saia em camadas com barra de renda
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                b(base, 0f, 0.935f, 0f, bw * 0.6f, 0.035f, 0.32f, trim)
                F(0f, 0.9f, bw * 0.55f, 0.04f, trim); K(0f, 0.9f, bw * 0.55f, 0.04f, trim)
                F(0f, 0.72f, 0.02f, 0.3f, trim, 0.012f); F(-0.07f, 0.72f, 0.016f, 0.3f, shadeC(tc, 0.85f), 0.012f, 6f); F(0.07f, 0.72f, 0.016f, 0.3f, shadeC(tc, 0.85f), 0.012f, -6f)
                for (i in 0 until 3) { F(-0.05f + i * 0.05f, 0.62f, 0.02f, 0.02f, lite(tc, 0.8f), 0.016f) }
                b(base, 0f, 0.5f, 0f, bw + 0.02f, 0.06f, 0.32f, trim)
                F(0f, 0.5f, 0.07f, 0.07f, lite(tc, 0.85f), 0.024f)
                b(base, 0f, 0.5f, -0.18f, 0.16f, 0.1f, 0.05f, trim); b(base, -0.1f, 0.5f, -0.19f, 0.09f, 0.07f, 0.04f, trim, rz = 20f); b(base, 0.1f, 0.5f, -0.19f, 0.09f, 0.07f, 0.04f, trim, rz = -20f)
                b(base, 0f, 0.43f, -0.2f, 0.03f, 0.12f, 0.02f, trim)
                sk(0.43f, bw + 0.06f, 0.14f, 0.36f, tc)
                sk(0.36f, 0.62f, 0.12f, 0.46f, tc)
                sk(0.27f, 0.76f, 0.10f, 0.56f, shadeC(tc, 0.94f))
                sk(0.17f, 0.78f, 0.10f, 0.58f, shadeC(tc, 0.9f))
                sk(0.125f, 0.79f, 0.03f, 0.59f, lite(tc, 0.55f))
                sk(0.095f, 0.8f, 0.025f, 0.6f, lite(tc, 0.8f))
            }
            3 -> {  // jaqueta: costas e frente 100% na cor, camisa branca na frente, zíper, bolsos com aba, barra e punhos de ribana, emblema nas costas
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                band(0.46f, 0.07f, dark, 0.03f); band(0.4975f, 0.012f, lite(tc, 0.3f), 0.034f)
                F(0f, 0.7f, 0.1f, 0.46f, white, 0.012f)               // camisa por baixo, só na frente
                F(-0.085f, 0.7f, 0.07f, 0.46f, tc, 0.02f); F(0.085f, 0.7f, 0.07f, 0.46f, tc, 0.02f)   // as duas abas da jaqueta
                F(0f, 0.68f, 0.012f, 0.44f, 0xC9CED8, 0.026f)        // zíper
                F(0f, 0.9f, 0.03f, 0.05f, 0xE8E8F0, 0.03f)           // puxador
                b(base, -0.075f, 0.935f, 0.04f, 0.1f, 0.075f, 0.2f, dark); b(base, 0.075f, 0.935f, 0.04f, 0.1f, 0.075f, 0.2f, dark)   // gola alta
                b(base, 0f, 0.935f, -0.07f, bw * 0.55f, 0.075f, 0.1f, dark)
                F(-0.14f, 0.57f, 0.1f, 0.05f, dark, 0.02f); F(0.14f, 0.57f, 0.1f, 0.05f, dark, 0.02f)  // bolsos
                F(-0.14f, 0.545f, 0.1f, 0.012f, lite(tc, 0.4f), 0.024f); F(0.14f, 0.545f, 0.1f, 0.012f, lite(tc, 0.4f), 0.024f)
                F(-0.14f, 0.79f, 0.06f, 0.05f, lite(tc, 0.3f), 0.018f)   // patch no peito
                band(0.86f, 0.018f, lite(tc, 0.3f), 0.006f)           // listra de ombro a ombro
                K(0f, 0.74f, 0.2f, 0.2f, dark, 0.016f); K(0f, 0.74f, 0.1f, 0.1f, lite(tc, 0.7f), 0.02f); K(0f, 0.74f, 0.03f, 0.2f, tc, 0.024f)   // emblema nas costas
                K(0f, 0.9f, bw - 0.02f, 0.012f, shadeC(tc, 0.7f)); K(0f, 0.5f, bw - 0.02f, 0.012f, shadeC(tc, 0.7f))
            }
            4 -> {  // túnica: gola em V bordada, tabardo central, cinto dourado, barra com bordado
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                b(base, 0f, 0.935f, 0f, bw * 0.6f, 0.04f, 0.32f, trim)
                F(0f, 0.9f, 0.12f, 0.035f, skin); F(0f, 0.875f, 0.06f, 0.03f, skin)
                F(-0.07f, 0.875f, 0.02f, 0.1f, gold, 0.018f, 25f); F(0.07f, 0.875f, 0.02f, 0.1f, gold, 0.018f, -25f)
                F(0f, 0.72f, 0.16f, 0.4f, lite(tc, 0.18f), 0.012f); F(-0.08f, 0.72f, 0.012f, 0.4f, gold, 0.016f); F(0.08f, 0.72f, 0.012f, 0.4f, gold, 0.016f)
                F(0f, 0.72f, 0.07f, 0.07f, gold, 0.02f); F(0f, 0.72f, 0.03f, 0.03f, tc, 0.024f)
                K(0f, 0.72f, 0.16f, 0.4f, lite(tc, 0.18f), 0.012f); K(-0.08f, 0.72f, 0.012f, 0.4f, gold, 0.016f); K(0.08f, 0.72f, 0.012f, 0.4f, gold, 0.016f)
                b(base, 0f, 0.54f, 0f, bw + 0.02f, 0.06f, 0.32f, 0x6F4A29)
                F(0f, 0.54f, 0.09f, 0.075f, gold, 0.02f); F(0f, 0.54f, 0.04f, 0.04f, 0x6F4A29, 0.024f)
                b(base, 0f, 0.455f, 0f, bw + 0.03f, 0.05f, 0.33f, trim); band(0.485f, 0.012f, gold, 0.034f)
            }
            5 -> {  // armadura: peitoral e dorsal de aço, ombreiras (nos braços), couro, cinto com fivela, tassetes
                val plate = mixC(steel, tc, 0.3f); val plateD = shadeC(plate, 0.8f)
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, 0x4A5568)
                b(base, 0f, 0.74f, 0.0f, bw + 0.02f, 0.4f, 0.31f, plate)       // peitoral + dorsal (volta inteira)
                F(0f, 0.74f, 0.012f, 0.4f, plateD, 0.02f)                      // nervura central
                F(-0.09f, 0.82f, 0.1f, 0.012f, lite(plate, 0.5f), 0.02f, 8f); F(0.09f, 0.82f, 0.1f, 0.012f, lite(plate, 0.5f), 0.02f, -8f)
                F(0f, 0.8f, 0.05f, 0.05f, gold, 0.026f)
                K(0f, 0.74f, 0.012f, 0.4f, plateD, 0.02f); K(0f, 0.8f, 0.2f, 0.012f, lite(plate, 0.5f), 0.02f)
                b(base, 0f, 0.935f, 0f, bw * 0.62f, 0.04f, 0.32f, gold)       // gorjal
                band(0.945f, 0.015f, lite(plate, 0.4f), 0.0f)
                b(base, 0f, 0.5f, 0f, bw + 0.02f, 0.07f, 0.32f, leather)
                F(0f, 0.5f, 0.09f, 0.08f, gold, 0.024f); F(0f, 0.5f, 0.04f, 0.04f, leatherD, 0.028f)
                b(base, 0f, 0.445f, 0f, bw + 0.02f, 0.085f, 0.32f, plateD)    // faixa de aço na cintura
                for (i in -1..1) { F(i * 0.14f, 0.455f, 0.012f, 0.07f, lite(plate, 0.4f), 0.02f) }
            }
            6 -> {  // macacão (jardineira): camisa branca só em cima, o resto na cor da roupa — costas incluídas, com alças cruzadas
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, white)                  // camisa por baixo
                b(base, 0f, 0.645f, 0f, bw + 0.012f, 0.4f, 0.308f, tc)         // frente, lados e COSTAS do macacão (da cintura ao peito)
                b(base, 0f, 0.455f, 0f, bw + 0.02f, 0.05f, 0.318f, dark)
                F(0f, 0.74f, 0.3f, 0.2f, lite(tc, 0.1f), 0.022f)               // peitilho
                F(0f, 0.74f, 0.3f, 0.014f, dark, 0.026f); F(-0.15f, 0.74f, 0.014f, 0.2f, dark, 0.026f); F(0.15f, 0.74f, 0.014f, 0.2f, dark, 0.026f)
                F(0f, 0.775f, 0.14f, 0.1f, dark, 0.03f); F(0f, 0.775f, 0.11f, 0.075f, tc, 0.034f)   // bolsão
                F(0f, 0.775f, 0.08f, 0.012f, lite(tc, 0.5f), 0.038f)
                F(-0.11f, 0.915f, 0.07f, 0.17f, tc, 0.02f); F(0.11f, 0.915f, 0.07f, 0.17f, tc, 0.02f)  // alças
                F(-0.11f, 0.86f, 0.05f, 0.05f, gold, 0.028f); F(0.11f, 0.86f, 0.05f, 0.05f, gold, 0.028f)  // botões
                K(-0.075f, 0.89f, 0.07f, 0.2f, tc, 0.02f, -18f); K(0.075f, 0.89f, 0.07f, 0.2f, tc, 0.02f, 18f)   // alças cruzando atrás
                K(0f, 0.8f, 0.1f, 0.06f, dark, 0.024f)
                K(0f, 0.55f, 0.12f, 0.1f, dark, 0.018f)                        // bolso traseiro
                F(-0.2f, 0.62f, 0.02f, 0.04f, gold, 0.02f); F(0.2f, 0.62f, 0.02f, 0.04f, gold, 0.02f)
                b(base, -hw - 0.001f, 0.6f, 0f, 0.012f, 0.03f, 0.2f, gold); b(base, hw + 0.001f, 0.6f, 0f, 0.012f, 0.03f, 0.2f, gold)
            }
            7 -> {  // camisa social: colarinho, botões, bolso, pala e costura nas costas
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                band(0.462f, 0.03f, shadeC(tc, 0.88f), 0.008f)
                b(base, -0.085f, 0.93f, 0.025f, 0.11f, 0.065f, 0.32f, lite(tc, 0.55f)); b(base, 0.085f, 0.93f, 0.025f, 0.11f, 0.065f, 0.32f, lite(tc, 0.55f))
                b(base, 0f, 0.935f, -0.07f, bw * 0.5f, 0.06f, 0.14f, lite(tc, 0.55f))
                F(-0.055f, 0.895f, 0.06f, 0.05f, lite(tc, 0.7f), 0.02f, -30f); F(0.055f, 0.895f, 0.06f, 0.05f, lite(tc, 0.7f), 0.02f, 30f)
                F(0f, 0.69f, 0.07f, 0.46f, shadeC(tc, 0.9f), 0.012f)
                for (i in 0 until 5) F(0f, 0.86f - i * 0.09f, 0.03f, 0.03f, white, 0.02f)
                F(0.12f, 0.78f, 0.1f, 0.11f, shadeC(tc, 0.85f), 0.016f); F(0.12f, 0.825f, 0.1f, 0.012f, lite(tc, 0.4f), 0.02f)
                K(0f, 0.84f, bw - 0.01f, 0.012f, shadeC(tc, 0.75f)); K(0f, 0.69f, 0.012f, 0.4f, shadeC(tc, 0.85f)); K(-0.07f, 0.85f, 0.012f, 0.06f, shadeC(tc, 0.85f)); K(0.07f, 0.85f, 0.012f, 0.06f, shadeC(tc, 0.85f))
            }
            8 -> {  // marinheiro: blusa branca, gola marinheira grande com listras, laço vermelho
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, white)
                band(0.465f, 0.045f, tc, 0.012f); band(0.5f, 0.012f, lite(tc, 0.6f), 0.008f)
                b(base, 0f, 0.915f, -0.095f, bw + 0.04f, 0.13f, 0.12f, tc)     // gola traseira (quadrada)
                b(base, 0f, 0.86f, -0.158f, bw * 0.78f, 0.09f, 0.02f, tc)
                K(0f, 0.935f, bw + 0.02f, 0.014f, white, 0.14f); K(0f, 0.9f, bw + 0.02f, 0.014f, white, 0.14f)   // listras brancas
                b(base, -0.09f, 0.945f, 0.075f, 0.11f, 0.05f, 0.18f, tc); b(base, 0.09f, 0.945f, 0.075f, 0.11f, 0.05f, 0.18f, tc)   // ombros da gola
                F(-0.085f, 0.85f, 0.09f, 0.22f, tc, 0.026f, 22f); F(0.085f, 0.85f, 0.09f, 0.22f, tc, 0.026f, -22f)
                F(-0.098f, 0.855f, 0.012f, 0.2f, white, 0.03f, 22f); F(0.098f, 0.855f, 0.012f, 0.2f, white, 0.03f, -22f)
                F(0f, 0.775f, 0.06f, 0.06f, 0xE05555, 0.04f); F(-0.07f, 0.77f, 0.09f, 0.07f, 0xE05555, 0.036f, 12f); F(0.07f, 0.77f, 0.09f, 0.07f, 0xE05555, 0.036f, -12f)
                F(-0.04f, 0.71f, 0.025f, 0.09f, 0xC03A3A, 0.034f, 8f); F(0.04f, 0.71f, 0.025f, 0.09f, 0xC03A3A, 0.034f, -8f)
            }
            9 -> {  // gakuran: gola alta, botões dourados, bolsos com aba, costura central
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                band(0.463f, 0.05f, dark, 0.02f)
                b(base, 0f, 0.945f, 0f, bw * 0.58f, 0.07f, 0.33f, dark)        // gola em pé, volta toda
                F(0f, 0.945f, 0.05f, 0.07f, dark, 0.035f); F(-0.1f, 0.94f, 0.03f, 0.03f, gold, 0.034f); F(0.1f, 0.94f, 0.03f, 0.03f, gold, 0.034f)
                F(0f, 0.69f, 0.03f, 0.5f, dark, 0.012f)
                for (i in 0 until 5) F(0f, 0.87f - i * 0.1f, 0.045f, 0.045f, gold, 0.022f)
                F(-0.13f, 0.58f, 0.1f, 0.07f, dark, 0.018f); F(0.13f, 0.58f, 0.1f, 0.07f, dark, 0.018f)
                F(-0.13f, 0.545f, 0.1f, 0.012f, gold, 0.022f); F(0.13f, 0.545f, 0.1f, 0.012f, gold, 0.022f)
                K(0f, 0.69f, 0.03f, 0.42f, dark, 0.012f); K(0f, 0.5f, bw, 0.012f, shadeC(tc, 0.7f)); K(0f, 0.88f, 0.2f, 0.012f, shadeC(tc, 0.7f))
            }
            10 -> {  // kimono: sobreposição cruzada (esquerda sobre direita), faixa obi larga, laço atrás, saia em camadas
                val obi = shadeC(tc, 0.55f)
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                b(base, 0f, 0.935f, 0f, bw * 0.6f, 0.035f, 0.32f, white); band(0.95f, 0.012f, lite(tc, 0.6f), 0.0f)
                F(-0.075f, 0.845f, 0.075f, 0.26f, white, 0.016f, 24f); F(0.075f, 0.845f, 0.075f, 0.26f, white, 0.016f, -24f)    // gola cruzada
                F(-0.04f, 0.77f, 0.1f, 0.3f, shadeC(tc, 0.9f), 0.012f, 20f)
                F(-0.09f, 0.66f, 0.012f, 0.2f, lite(tc, 0.45f), 0.014f); F(0.09f, 0.66f, 0.012f, 0.2f, lite(tc, 0.45f), 0.014f)
                F(0f, 0.7f, 0.04f, 0.04f, lite(tc, 0.6f), 0.016f); F(0.12f, 0.64f, 0.04f, 0.04f, lite(tc, 0.6f), 0.016f); F(-0.12f, 0.8f, 0.04f, 0.04f, lite(tc, 0.6f), 0.016f)   // flores
                b(base, 0f, 0.53f, 0f, bw + 0.03f, 0.16f, 0.33f, obi)
                F(0f, 0.53f, 0.2f, 0.035f, gold, 0.032f); F(0f, 0.585f, 0.3f, 0.012f, lite(obi, 0.3f), 0.032f); F(0f, 0.475f, 0.3f, 0.012f, lite(obi, 0.3f), 0.032f)
                b(base, 0f, 0.55f, -0.2f, 0.34f, 0.24f, 0.1f, obi)             // laço (musubi)
                b(base, 0f, 0.55f, -0.265f, 0.1f, 0.1f, 0.04f, gold)
                b(base, -0.14f, 0.5f, -0.24f, 0.07f, 0.2f, 0.03f, obi, rz = 8f); b(base, 0.14f, 0.5f, -0.24f, 0.07f, 0.2f, 0.03f, obi, rz = -8f)
                sk(0.36f, bw + 0.05f, 0.24f, 0.4f, tc)
                sk(0.2f, bw + 0.08f, 0.2f, 0.5f, tc)
                sk(0.115f, bw + 0.14f, 0.03f, 0.58f, lite(tc, 0.6f))
            }
            11 -> {  // robe de mago: capa de ombros, cordão, estrelas/luas bordadas, barra dourada
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                b(base, 0f, 0.935f, 0f, bw * 0.7f, 0.04f, 0.32f, gold)
                b(base, 0f, 0.915f, 0f, bw + 0.03f, 0.06f, 0.325f, shadeC(tc, 0.85f))     // mantelete nos ombros (volta toda)
                F(0f, 0.9f, bw + 0.03f, 0.014f, gold, 0.03f); K(0f, 0.9f, bw + 0.03f, 0.014f, gold, 0.03f)
                F(0f, 0.78f, 0.1f, 0.1f, gold, 0.02f); F(0f, 0.78f, 0.04f, 0.04f, tc, 0.024f)     // fecho
                val gs = lite(gold, 0.2f)
                F(-0.1f, 0.7f, 0.035f, 0.1f, gs, 0.014f); F(-0.1f, 0.7f, 0.1f, 0.035f, gs, 0.014f)        // estrelas bordadas
                F(0.1f, 0.62f, 0.03f, 0.08f, gs, 0.014f); F(0.1f, 0.62f, 0.08f, 0.03f, gs, 0.014f)
                K(0f, 0.74f, 0.04f, 0.16f, gs, 0.016f); K(0f, 0.74f, 0.16f, 0.04f, gs, 0.016f); K(0f, 0.74f, 0.1f, 0.1f, lite(tc, 0.3f), 0.02f)   // estrela grande nas costas
                b(base, 0f, 0.52f, 0f, bw + 0.03f, 0.05f, 0.33f, leather)
                F(0.1f, 0.4f, 0.03f, 0.22f, gold, 0.02f); F(0.1f, 0.27f, 0.05f, 0.05f, gold, 0.022f)   // ponta do cordão
                sk(0.4f, bw + 0.08f, 0.28f, 0.38f, tc)
                sk(0.205f, 0.74f, 0.22f, 0.54f, shadeC(tc, 0.92f))
                sk(0.115f, 0.76f, 0.04f, 0.56f, gold)
                sk(0.08f, 0.77f, 0.02f, 0.57f, lite(tc, 0.4f))
            }
            12 -> {  // terno: lapelas, camisa branca em V, gravata, lenço de bolso, botões, bolsos com aba, abertura traseira
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                band(0.462f, 0.04f, dark, 0.014f)
                F(0f, 0.74f, 0.15f, 0.4f, white, 0.01f)
                F(0f, 0.74f, 0.04f, 0.3f, 0xC03A3A, 0.02f); F(0f, 0.9f, 0.06f, 0.04f, 0xA82E2E, 0.024f)     // gravata + nó
                F(0f, 0.7f, 0.014f, 0.3f, 0x8A2222, 0.026f)
                F(-0.105f, 0.84f, 0.1f, 0.27f, shadeC(tc, 0.72f), 0.022f, 12f); F(0.105f, 0.84f, 0.1f, 0.27f, shadeC(tc, 0.72f), 0.022f, -12f)   // lapelas
                F(-0.15f, 0.88f, 0.05f, 0.06f, shadeC(tc, 0.62f), 0.028f, 40f); F(0.15f, 0.88f, 0.05f, 0.06f, shadeC(tc, 0.62f), 0.028f, -40f)
                b(base, -0.07f, 0.94f, 0.03f, 0.1f, 0.05f, 0.3f, shadeC(tc, 0.7f)); b(base, 0.07f, 0.94f, 0.03f, 0.1f, 0.05f, 0.3f, shadeC(tc, 0.7f))
                F(0.0f, 0.5f, 0.04f, 0.04f, gold, 0.022f); F(0f, 0.6f, 0.04f, 0.04f, gold, 0.022f)
                F(-0.15f, 0.76f, 0.07f, 0.04f, white, 0.02f); F(-0.15f, 0.77f, 0.04f, 0.02f, 0xC03A3A, 0.024f)   // lenço
                F(-0.14f, 0.55f, 0.1f, 0.03f, dark, 0.022f); F(0.14f, 0.55f, 0.1f, 0.03f, dark, 0.022f)
                K(0f, 0.69f, 0.014f, 0.46f, dark, 0.012f); K(0f, 0.52f, 0.05f, 0.1f, shadeC(tc, 0.6f), 0.014f)
            }
            13 -> {  // regata: alças finas, decote redondo, recorte nas costas, barra e estampa
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                F(0f, 0.905f, 0.2f, 0.09f, skin, 0.016f); F(0f, 0.865f, 0.14f, 0.03f, skin, 0.016f)
                F(0f, 0.887f, 0.24f, 0.014f, trim, 0.022f)
                K(0f, 0.935f, 0.26f, 0.05f, skin, 0.016f); K(0f, 0.9f, 0.1f, 0.03f, skin, 0.016f)
                band(0.462f, 0.05f, dark, 0.012f)
                F(0f, 0.7f, 0.12f, 0.02f, lite(tc, 0.6f)); F(0f, 0.665f, 0.07f, 0.02f, lite(tc, 0.6f)); F(0f, 0.63f, 0.16f, 0.02f, lite(tc, 0.35f))
                K(0f, 0.6f, 0.2f, 0.02f, lite(tc, 0.4f))
                b(base, -hw + 0.005f, 0.87f, 0f, 0.012f, 0.1f, 0.3f, shadeC(tc, 0.8f)); b(base, hw - 0.005f, 0.87f, 0f, 0.012f, 0.1f, 0.3f, shadeC(tc, 0.8f))
            }
            14 -> {  // haori: casaco aberto em cima do kimono branco; costas, frente e lados na cor; aba curta que não atravessa as pernas
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                F(0f, 0.7f, 0.12f, 0.46f, white, 0.012f)                        // peito do kimono por baixo
                F(-0.07f, 0.7f, 0.05f, 0.46f, lite(tc, 0.6f), 0.02f); F(0.07f, 0.7f, 0.05f, 0.46f, lite(tc, 0.6f), 0.02f)   // bordas da abertura
                b(base, -0.1f, 0.945f, 0f, 0.1f, 0.06f, 0.33f, lite(tc, 0.5f)); b(base, 0.1f, 0.945f, 0f, 0.1f, 0.06f, 0.33f, lite(tc, 0.5f))
                F(0f, 0.9f, 0.1f, 0.03f, white, 0.02f)
                F(-0.07f, 0.775f, 0.016f, 0.03f, white, 0.026f); F(0.07f, 0.775f, 0.016f, 0.03f, white, 0.026f); F(0f, 0.775f, 0.14f, 0.012f, 0xE8E8F0, 0.022f)   // cordão himo
                K(0f, 0.76f, 0.15f, 0.15f, lite(tc, 0.45f), 0.016f); K(0f, 0.76f, 0.1f, 0.1f, tc, 0.02f); K(0f, 0.76f, 0.04f, 0.04f, lite(tc, 0.7f), 0.024f)   // brasão nas costas
                b(base, 0f, 0.5f, 0f, bw + 0.02f, 0.05f, 0.32f, 0x3A2A22)       // faixa
                F(0f, 0.5f, 0.08f, 0.06f, gold, 0.026f)
                band(0.462f, 0.05f, tc, 0.03f); band(0.49f, 0.012f, lite(tc, 0.5f), 0.034f)   // barra do haori rente à cintura (sem abas soltas: as pernas não atravessam mais)
                F(-0.17f, 0.46f, 0.1f, 0.012f, gold, 0.034f); F(0.17f, 0.46f, 0.1f, 0.012f, gold, 0.034f)
                b(base, 0f, 0.87f, 0f, bw + 0.012f, 0.03f, 0.31f, shadeC(tc, 0.85f))   // pala dos ombros
            }
            15 -> {  // maid: corpete com avental, babados nos ombros, laço, saia com avental só na frente
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                F(0f, 0.7f, 0.26f, 0.4f, white, 0.016f); F(0f, 0.7f, 0.2f, 0.34f, lite(tc, 0.05f), 0.02f)
                F(0f, 0.745f, 0.12f, 0.12f, white, 0.024f)
                F(-0.115f, 0.9f, 0.05f, 0.16f, white, 0.02f); F(0.115f, 0.9f, 0.05f, 0.16f, white, 0.02f)   // alças do avental
                b(base, -0.1f, 0.9f, 0f, 0.05f, 0.1f, 0.32f, white); b(base, 0.1f, 0.9f, 0f, 0.05f, 0.1f, 0.32f, white)
                b(base, 0f, 0.945f, 0f, 0.3f, 0.04f, 0.33f, white)
                F(0f, 0.915f, 0.06f, 0.05f, 0xE05555, 0.03f); F(-0.04f, 0.915f, 0.045f, 0.04f, 0xE05555, 0.026f); F(0.04f, 0.915f, 0.045f, 0.04f, 0xE05555, 0.026f)
                b(base, 0f, 0.5f, 0f, bw + 0.02f, 0.05f, 0.32f, white)
                b(base, 0f, 0.5f, -0.18f, 0.2f, 0.1f, 0.05f, white); b(base, -0.12f, 0.49f, -0.19f, 0.09f, 0.08f, 0.04f, white, rz = 18f); b(base, 0.12f, 0.49f, -0.19f, 0.09f, 0.08f, 0.04f, white, rz = -18f)
                K(0f, 0.77f, 0.2f, 0.014f, white, 0.012f)
                sk(0.43f, bw + 0.06f, 0.14f, 0.36f, tc)
                sk(0.36f, 0.62f, 0.12f, 0.46f, tc)
                sk(0.27f, 0.74f, 0.1f, 0.54f, tc)
                sk(0.17f, 0.76f, 0.1f, 0.56f, tc)
                sk(0.125f, 0.78f, 0.04f, 0.58f, white)
                sk(0.1f, 0.79f, 0.02f, 0.59f, lite(tc, 0.4f))
                skRows.add(SkR(0f, 0.36f, 0.255f, 0.34f, 0.12f, 0.04f, white, false))   // avental: só na frente (antes dava a volta e as costas ficavam brancas)
                skRows.add(SkR(0f, 0.27f, 0.30f, 0.38f, 0.1f, 0.04f, white, false))
                skRows.add(SkR(0f, 0.17f, 0.315f, 0.4f, 0.1f, 0.04f, white, false))
                skRows.add(SkR(0f, 0.125f, 0.325f, 0.42f, 0.03f, 0.04f, lite(tc, 0.5f), false))
            }
            16 -> {  // colete de aventureiro: camisa branca só aparece na frente/mangas; colete na cor completo (costas incluídas), cordões de couro, cinto e bolsos
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                F(0f, 0.7f, 0.14f, 0.46f, white, 0.012f)
                F(-0.115f, 0.7f, 0.1f, 0.48f, tc, 0.026f); F(0.115f, 0.7f, 0.1f, 0.48f, tc, 0.026f)
                F(-0.115f, 0.7f, 0.012f, 0.48f, shadeC(tc, 0.7f), 0.03f); F(0.115f, 0.7f, 0.012f, 0.48f, shadeC(tc, 0.7f), 0.03f)
                b(base, -0.1f, 0.935f, 0f, 0.1f, 0.06f, 0.325f, shadeC(tc, 0.85f)); b(base, 0.1f, 0.935f, 0f, 0.1f, 0.06f, 0.325f, shadeC(tc, 0.85f))
                for (i in 0 until 4) { F(0f, 0.86f - i * 0.1f, 0.15f, 0.014f, leather, 0.034f, if (i % 2 == 0) 14f else -14f) }   // cordões cruzados
                for (i in 0 until 4) F(-0.075f, 0.86f - i * 0.1f, 0.025f, 0.025f, gold, 0.038f).also { F(0.075f, 0.86f - i * 0.1f, 0.025f, 0.025f, gold, 0.038f) }
                F(-0.14f, 0.63f, 0.09f, 0.08f, shadeC(tc, 0.75f), 0.034f); F(0.14f, 0.63f, 0.09f, 0.08f, shadeC(tc, 0.75f), 0.034f)   // bolsos do colete
                F(-0.14f, 0.665f, 0.09f, 0.012f, leather, 0.038f); F(0.14f, 0.665f, 0.09f, 0.012f, leather, 0.038f)
                K(0f, 0.9f, bw - 0.01f, 0.012f, shadeC(tc, 0.7f)); K(0f, 0.74f, 0.012f, 0.4f, shadeC(tc, 0.75f)); K(0f, 0.74f, 0.2f, 0.2f, shadeC(tc, 0.88f), 0.012f)
                K(0f, 0.78f, 0.1f, 0.012f, leather, 0.02f); K(0f, 0.74f, 0.1f, 0.012f, leather, 0.02f); K(0f, 0.70f, 0.1f, 0.012f, leather, 0.02f)
                b(base, 0f, 0.5f, 0f, bw + 0.03f, 0.06f, 0.33f, leather)
                F(0f, 0.5f, 0.08f, 0.08f, gold, 0.026f); F(0f, 0.5f, 0.04f, 0.04f, leatherD, 0.03f)
                F(-0.17f, 0.5f, 0.06f, 0.09f, leatherD, 0.03f); F(0.17f, 0.5f, 0.06f, 0.09f, leatherD, 0.03f)   // bolsinhas no cinto
                band(0.462f, 0.04f, shadeC(tc, 0.7f), 0.012f)
            }
            else -> {  // casacão (puffer): gomos acolchoados, gola alta forrada, zíper, bolsos, barra elástica
                b(base, 0f, 0.69f, 0f, bw + 0.05f, 0.5f, 0.36f, tc)
                for (i in 0 until 4) band(0.555f + i * 0.115f, 0.022f, dark, 0.06f)
                for (i in 0 until 4) { F(0f, 0.5f + i * 0.115f + 0.058f, bw * 0.9f, 0.01f, lite(tc, 0.15f), 0.022f) }   // brilho dos gomos
                b(base, 0f, 0.912f, 0f, bw + 0.04f, 0.07f, 0.37f, dark)          // gola acolchoada baixa (a cabeça fica por cima, sem atravessar)
                b(base, 0f, 0.915f, -0.17f, 0.34f, 0.09f, 0.08f, dark)           // gola mais alta só atrás
                b(base, 0f, 0.944f, 0f, bw * 0.7f, 0.014f, 0.32f, lite(tc, 0.3f))  // forro claro
                F(0f, 0.7f, 0.022f, 0.5f, lite(tc, 0.55f), 0.02f); F(0f, 0.9f, 0.04f, 0.05f, 0xE8E8F0, 0.026f)
                F(-0.145f, 0.57f, 0.1f, 0.016f, dark, 0.026f, -12f); F(0.145f, 0.57f, 0.1f, 0.016f, dark, 0.026f, 12f)   // bolsos
                K(0f, 0.78f, 0.14f, 0.1f, dark, 0.024f)                          // etiqueta nas costas
                b(base, 0f, 0.465f, 0f, bw + 0.07f, 0.05f, 0.37f, dark)
            }
        }
        // ================= ALTA COSTURA VOXEL: REFORMA INDIVIDUAL DAS ROUPAS =================
        // Cada tipo tem recortes, reforços e motivos próprios construídos em relevo 3D.
        val garmentA = Look.pal(Look.TOP_ACCENT)
        val garmentHi = lite(garmentA, 0.42f)
        val garmentLo = shadeC(garmentA, 0.62f)
        val stitch = lite(tc, 0.34f)
        when (top) {
            0 -> { // camiseta streetwear: painéis de ombro, etiqueta e patch bordado
                F(-0.12f,0.86f,0.13f,0.022f,shadeC(tc,0.65f),0.022f,-12f); F(0.12f,0.86f,0.13f,0.022f,shadeC(tc,0.65f),0.022f,12f)
                F(-0.145f,0.72f,0.06f,0.07f,garmentLo,0.026f); F(-0.145f,0.72f,0.04f,0.05f,garmentA,0.036f)
                F(-0.145f,0.74f,0.02f,0.02f,garmentHi,0.042f)
                for(i in 0..3) F(0.12f+i*0.016f,0.535f,0.008f,0.016f,stitch,0.022f)
                K(0f,0.63f,0.18f,0.012f,shadeC(tc,0.62f),0.02f)
            }
            1 -> { // moletom: painéis diagonais, ilhós de metal, bolso reforçado
                F(-0.14f,0.81f,0.05f,0.14f,shadeC(tc,0.65f),0.024f,-25f); F(0.14f,0.81f,0.05f,0.14f,shadeC(tc,0.65f),0.024f,25f)
                F(-0.13f,0.81f,0.018f,0.12f,stitch,0.034f,-25f); F(0.13f,0.81f,0.018f,0.12f,stitch,0.034f,25f)
                F(-0.135f,0.58f,0.025f,0.02f,0xD5D9E0,0.043f); F(0.135f,0.58f,0.025f,0.02f,0xD5D9E0,0.043f)
                F(0.14f,0.75f,0.055f,0.07f,garmentLo,0.025f); F(0.14f,0.75f,0.035f,0.045f,garmentA,0.036f)
                K(-0.14f,0.71f,0.025f,0.12f,stitch,0.022f,-8f); K(0.14f,0.71f,0.025f,0.12f,stitch,0.022f,8f)
            }
            2 -> { // vestido de gala: barbatanas de corset, pérolas e bordado floral
                F(-0.105f,0.78f,0.014f,0.24f,garmentLo,0.026f); F(0.105f,0.78f,0.014f,0.24f,garmentLo,0.026f)
                for(i in 0..4) { F(-0.025f,0.84f-i*0.045f,0.02f,0.02f,0xF0D9A1,0.036f); F(0.025f,0.82f-i*0.045f,0.02f,0.02f,garmentHi,0.036f) }
                F(-0.13f,0.69f,0.05f,0.05f,garmentLo,0.026f,45f); F(-0.13f,0.69f,0.03f,0.03f,garmentHi,0.04f,45f)
                F(0.13f,0.69f,0.05f,0.05f,garmentLo,0.026f,45f); F(0.13f,0.69f,0.03f,0.03f,garmentHi,0.04f,45f)
                sk(0.31f,0.66f,0.018f,0.49f,garmentLo); sk(0.19f,0.7f,0.018f,0.55f,garmentHi)
            }
            3 -> { // jaqueta técnica: pala em camadas, bolsos 3D, badge e filetes
                band(0.86f,0.024f,shadeC(tc,0.62f),0.018f); band(0.842f,0.008f,stitch,0.02f)
                for(s in intArrayOf(-1,1)) { val x=s*0.15f; F(x,0.68f,0.08f,0.11f,shadeC(tc,0.62f),0.027f); F(x,0.70f,0.06f,0.065f,tc,0.038f); F(x,0.635f,0.045f,0.01f,stitch,0.042f) }
                F(0.14f,0.79f,0.075f,0.075f,garmentLo,0.03f,45f); F(0.14f,0.79f,0.045f,0.045f,garmentA,0.04f,45f)
                for(i in 0..3) F(-0.16f+i*0.02f,0.56f,0.008f,0.012f,stitch,0.04f)
            }
            4 -> { // túnica: galão bordado, medalhão e barra geométrica
                F(-0.14f,0.69f,0.022f,0.42f,garmentLo,0.023f); F(0.14f,0.69f,0.022f,0.42f,garmentLo,0.023f)
                F(0f,0.82f,0.14f,0.14f,garmentLo,0.028f,45f); F(0f,0.82f,0.085f,0.085f,garmentA,0.04f,45f)
                F(0f,0.82f,0.027f,0.06f,0xF0D9A1,0.048f)
                for(i in 0..5) F(-0.12f+i*0.048f,0.49f,0.022f,0.022f,if(i%2==0) garmentHi else 0xF0D9A1,0.038f,45f)
            }
            5 -> { // armadura: placas sobrepostas, rebites, nervuras e brasão
                for(s in intArrayOf(-1,1)) { val x=s*0.145f; F(x,0.79f,0.08f,0.18f,shadeC(steel,0.62f),0.027f,s*-8f); F(x,0.80f,0.06f,0.14f,steel,0.039f,s*-8f); F(x,0.85f,0.018f,0.06f,lite(steel,0.45f),0.05f); F(x,0.73f,0.018f,0.06f,gold,0.05f) }
                F(0f,0.71f,0.16f,0.17f,shadeC(steel,0.62f),0.032f,45f); F(0f,0.71f,0.11f,0.12f,gold,0.043f,45f); F(0f,0.71f,0.04f,0.07f,tc,0.05f)
                for(i in -1..1) F(i*0.13f,0.45f,0.014f,0.06f,lite(steel,0.4f),0.035f)
            }
            6 -> { // jardineira: pesponto, ferragens, etiqueta e bolso-ferramenta
                F(-0.11f,0.83f,0.024f,0.16f,shadeC(tc,0.62f),0.03f,-18f); F(0.11f,0.83f,0.024f,0.16f,shadeC(tc,0.62f),0.03f,18f)
                for(s in intArrayOf(-1,1)) { F(s*0.12f,0.87f,0.045f,0.045f,gold,0.045f); F(s*0.12f,0.87f,0.018f,0.018f,leatherD,0.055f) }
                F(0.12f,0.63f,0.075f,0.085f,shadeC(tc,0.62f),0.032f); F(0.12f,0.63f,0.052f,0.06f,garmentA,0.043f)
                K(-0.12f,0.65f,0.08f,0.08f,shadeC(tc,0.62f),0.026f); K(0.12f,0.65f,0.08f,0.08f,shadeC(tc,0.62f),0.026f)
            }
            7 -> { // camisa social: risca de giz, monograma e casas de botão
                for(x in floatArrayOf(-0.16f,-0.12f,0.12f,0.16f)) F(x,0.69f,0.006f,0.39f,stitch,0.02f)
                F(0.12f,0.78f,0.09f,0.10f,shadeC(tc,0.68f),0.024f); F(0.12f,0.79f,0.064f,0.065f,tc,0.035f); F(0.12f,0.79f,0.023f,0.034f,garmentHi,0.044f)
                for(i in 0..3) F(0.06f,0.65f-i*0.045f,0.012f,0.012f,gold,0.035f)
            }
            8 -> { // marinheiro: âncora voxel, galão de gola e insígnia
                F(0f,0.68f,0.018f,0.14f,0x263B62,0.034f); F(0f,0.62f,0.1f,0.018f,0x263B62,0.036f)
                F(-0.05f,0.65f,0.018f,0.065f,0x263B62,0.036f,-25f); F(0.05f,0.65f,0.018f,0.065f,0x263B62,0.036f,25f)
                F(-0.13f,0.85f,0.018f,0.11f,garmentHi,0.034f,24f); F(0.13f,0.85f,0.018f,0.11f,garmentHi,0.034f,-24f)
                K(0f,0.72f,0.18f,0.018f,garmentHi,0.024f); K(0f,0.68f,0.13f,0.008f,white,0.03f)
            }
            9 -> { // gakuran: galões militares, filete e botões metálicos
                F(-0.17f,0.83f,0.018f,0.15f,gold,0.03f,-12f); F(0.17f,0.83f,0.018f,0.15f,gold,0.03f,12f)
                for(i in 0..3) { F(-0.035f,0.84f-i*0.085f,0.018f,0.018f,gold,0.04f); F(0.035f,0.84f-i*0.085f,0.018f,0.018f,gold,0.04f) }
                F(-0.14f,0.65f,0.065f,0.06f,garmentLo,0.03f,45f); F(-0.14f,0.65f,0.04f,0.04f,gold,0.04f,45f)
            }
            10 -> { // kimono: flores sakura, faixa texturizada e bainha de seda
                for(s in intArrayOf(-1,1)) { val x=s*0.14f; F(x,0.79f,0.06f,0.06f,garmentLo,0.03f,45f); F(x-s*0.025f,0.79f,0.022f,0.045f,garmentHi,0.04f,s*25f); F(x+s*0.025f,0.79f,0.022f,0.045f,garmentHi,0.04f,-s*25f); F(x,0.75f,0.018f,0.035f,0xF0D9A1,0.045f) }
                for(i in 0..5) F(-0.13f+i*0.052f,0.53f,0.018f,0.06f,if(i%2==0) gold else garmentHi,0.04f)
                sk(0.29f,0.72f,0.014f,0.5f,garmentLo); sk(0.18f,0.76f,0.014f,0.53f,garmentHi)
            }
            11 -> { // robe arcana: runas, estrelas em camadas e borda ritual
                F(-0.15f,0.68f,0.02f,0.42f,gold,0.026f); F(0.15f,0.68f,0.02f,0.42f,gold,0.026f)
                for(i in 0..4) { val yy=0.78f-i*0.06f; F(-0.11f,yy,0.035f,0.035f,garmentLo,0.028f,45f); F(-0.11f,yy,0.017f,0.017f,garmentHi,0.04f) }
                F(0.1f,0.72f,0.05f,0.05f,garmentLo,0.028f,45f); F(0.1f,0.72f,0.022f,0.032f,0xF0D9A1,0.042f)
                for(i in 0..4) F(-0.12f+i*0.06f,0.47f,0.018f,0.024f,gold,0.04f)
            }
            12 -> { // terno: alfaiataria pinstripe, lapela contrastante e alfinete
                for(x in floatArrayOf(-0.17f,-0.13f,0.13f,0.17f)) F(x,0.7f,0.006f,0.39f,stitch,0.02f)
                F(-0.13f,0.84f,0.012f,0.18f,stitch,0.032f,30f); F(0.13f,0.84f,0.012f,0.18f,stitch,0.032f,-30f)
                F(0.14f,0.84f,0.05f,0.022f,white,0.04f,16f); F(0.14f,0.86f,0.02f,0.016f,garmentA,0.048f)
                F(0.06f,0.63f,0.012f,0.012f,gold,0.04f); F(0.06f,0.58f,0.012f,0.012f,gold,0.04f)
            }
            13 -> { // regata: recorte esportivo, ribana lateral e logo 3D
                F(-0.17f,0.68f,0.025f,0.42f,shadeC(tc,0.62f),0.025f); F(0.17f,0.68f,0.025f,0.42f,shadeC(tc,0.62f),0.025f)
                F(-0.17f,0.68f,0.01f,0.39f,garmentHi,0.035f); F(0.17f,0.68f,0.01f,0.39f,garmentHi,0.035f)
                F(0f,0.77f,0.13f,0.13f,garmentLo,0.028f,45f); F(0f,0.77f,0.09f,0.09f,garmentA,0.04f,45f)
                F(0f,0.77f,0.025f,0.05f,garmentHi,0.05f)
            }
            14 -> { // haori: bordado botânico, galão de barra e forro
                for(s in intArrayOf(-1,1)) { val x=s*0.13f; F(x,0.74f,0.05f,0.10f,garmentLo,0.028f,s*30f); F(x-s*0.02f,0.76f,0.02f,0.055f,garmentHi,0.04f,s*30f); F(x+s*0.02f,0.72f,0.02f,0.05f,0xF0D9A1,0.044f,-s*20f) }
                for(i in -3..3) F(i*0.04f,0.485f,0.022f,0.022f,if(i%2==0) gold else garmentHi,0.042f,45f)
                K(0f,0.67f,0.18f,0.018f,garmentLo,0.025f); K(0f,0.645f,0.13f,0.008f,garmentHi,0.032f)
                sk(0.29f,0.72f,0.016f,0.49f,garmentLo); sk(0.19f,0.76f,0.016f,0.52f,garmentHi)
            }
            15 -> { // maid: renda em degraus, bolso com bordado e friso de avental
                for(i in -4..4) { F(i*0.035f,0.49f,0.023f,0.023f,white,0.036f,if(i%2==0)45f else -45f); F(i*0.035f,0.91f,0.02f,0.018f,white,0.037f) }
                F(-0.13f,0.64f,0.075f,0.08f,white,0.03f); F(-0.13f,0.64f,0.052f,0.057f,lite(tc,0.1f),0.04f); F(-0.13f,0.665f,0.018f,0.018f,garmentHi,0.048f)
                F(0.13f,0.64f,0.075f,0.08f,white,0.03f); F(0.13f,0.64f,0.052f,0.057f,lite(tc,0.1f),0.04f)
            }
            16 -> { // colete de aventureiro: tiras de couro, bolsos em camadas e mosquetões
                for(s in intArrayOf(-1,1)) { val x=s*0.15f; F(x,0.7f,0.045f,0.36f,leatherD,0.032f); F(x,0.7f,0.018f,0.33f,leather,0.043f); F(x,0.59f,0.09f,0.10f,shadeC(tc,0.62f),0.047f); F(x,0.615f,0.06f,0.06f,tc,0.056f); F(x,0.65f,0.03f,0.016f,gold,0.06f) }
                F(0f,0.79f,0.055f,0.055f,garmentLo,0.03f,45f); F(0f,0.79f,0.035f,0.035f,garmentHi,0.044f,45f)
                K(-0.15f,0.66f,0.018f,0.25f,leather,0.028f); K(0.15f,0.66f,0.018f,0.25f,leather,0.028f)
            }
            else -> { // puffer: gomos acolchoados, quilting e puxadores
                for(y in floatArrayOf(0.84f,0.73f,0.62f,0.51f)) { F(-0.17f,y,0.022f,0.034f,shadeC(tc,0.62f),0.038f); F(0.17f,y,0.022f,0.034f,shadeC(tc,0.62f),0.038f); F(0f,y+0.035f,0.22f,0.008f,stitch,0.032f) }
                F(-0.13f,0.58f,0.075f,0.07f,shadeC(tc,0.62f),0.037f,-12f); F(0.13f,0.58f,0.075f,0.07f,shadeC(tc,0.62f),0.037f,12f)
                for(i in 0..4) F(0f,0.84f-i*0.075f,0.018f,0.018f,if(i%2==0)gold else 0xD5D9E0,0.047f)
                K(0f,0.8f,0.2f,0.018f,stitch,0.026f)
            }
        }

        if (skirtBot) {
            if (bot == 2) {
                sk(0.45f, bw + 0.06f, 0.1f, 0.36f, bc)
                sk(0.36f, 0.6f, 0.1f, 0.44f, bc)
                sk(0.295f, 0.68f, 0.05f, 0.5f, lite(bc, 0.35f))
            } else {   // plissada
                sk(0.45f, bw + 0.06f, 0.1f, 0.36f, bc)
                sk(0.36f, 0.62f, 0.1f, 0.46f, bc)
                sk(0.29f, 0.72f, 0.06f, 0.52f, shadeC(bc, 0.9f))
                sk(0.255f, 0.73f, 0.015f, 0.53f, lite(bc, 0.5f))
                for (i in -2..2) skRows.add(SkR(i * 0.14f + (if (i == 0) 0.001f else 0f), 0.3f, 0.275f, 0.025f, 0.2f, 0.02f, shadeC(bc, 0.72f), false))
            }
        }

        // Acabamento individual das saias: pregas profundas, vivo duplo e aplicações frontais.
        if (skirtBot) {
            val skirtHi = lite(bc, 0.34f); val skirtLo = shadeC(bc, 0.64f); val skirtAccent = Look.pal(Look.BOTTOM_ACCENT)
            val countPleats = if (bot == 2) 5 else 7
            for (i in 0 until countPleats) {
                val x = (i - (countPleats - 1) / 2f) * 0.09f
                skRows.add(SkR(x,0.29f,0.286f,0.012f,0.18f,0.012f,skirtLo,false))
                skRows.add(SkR(x+0.012f,0.29f,0.294f,0.006f,0.16f,0.008f,skirtHi,false))
            }
            if(bot==2) { skRows.add(SkR(0f,0.34f,0.303f,0.11f,0.045f,0.016f,skirtAccent,false)); skRows.add(SkR(0f,0.34f,0.314f,0.045f,0.025f,0.009f,gold,false)) }
            else for(i in -2..2) { val x=i*0.1f; skRows.add(SkR(x,0.29f,0.304f,0.025f,0.025f,0.01f,skirtAccent,false)); skRows.add(SkR(x,0.29f,0.312f,0.01f,0.01f,0.008f,skirtHi,false)) }
        }
        if (full || skirtBot) skRows.add(SkR(0f, 0.415f, 0f, bw + 0.05f, 0.1f, 0.34f, if (full) tc else bc, true))   // tampa da saia: acompanha o giro e fecha o vão com a cintura
        if (skirtBot) {
            if (bot == 2) { sk(0.265f, 0.7f, 0.03f, 0.52f, lite(bc, 0.7f)); b(base, 0f, 0.468f, 0.168f, 0.09f, 0.05f, 0.02f, lite(bc, 0.5f)); b(base, 0f, 0.468f, 0.175f, 0.03f, 0.03f, 0.02f, gold) }
            else { sk(0.235f, 0.74f, 0.02f, 0.54f, lite(bc, 0.6f)); b(base, 0f, 0.468f, 0.168f, 0.05f, 0.05f, 0.02f, gold); b(base, 0f, 0.468f, -0.168f, 0.2f, 0.04f, 0.02f, shadeC(bc, 0.7f)) }
        }
        // ---- saias e vestidos: cintura fixa no corpo; o resto é UMA peça só presa ao quadril (não divide com as pernas) ----
        for (r in skRows) {
            if (r.py > 0.42f) b(base, r.x, r.py, r.z, r.sx, r.sy, r.sz, r.col)
            else b(sm, r.x, r.py - 0.5f, r.z, r.sx, r.sy, r.sz + (if (full) 0.05f else 0f), r.col)   // vestido longo: um pouco mais fundo, o pé não fura na frente
        }

        // ================= MICROTEXTURA E RELEVO DE MATERIAIS =================
        // Camadas geométricas reais no modelo: pespontos, nervuras, painéis e ferragens.
        // O padrão muda por peça para que a roupa não pareça apenas um cubo pintado.
        val fabricHi = lite(tc, 0.22f)
        val fabricMid = mixC(tc, 0xFFFFFF, 0.10f)
        val fabricShadow = shadeC(tc, 0.72f)
        val seamCol = mixC(tc, 0xFFFFFF, 0.30f)
        // Costura dupla nos ombros e laterais, com pequenas marcas de ponto.
        for (side in intArrayOf(-1, 1)) {
            val x = side * (bw * 0.37f)
            F(x, 0.825f, 0.177f, 0.21f, fabricShadow, 0.012f, side * 8f)
            F(x + side * 0.012f, 0.825f, 0.184f, 0.18f, seamCol, 0.008f, side * 8f)
            for (j in 0..5) {
                F(side * (0.12f + j * 0.009f), 0.55f + (j % 2) * 0.014f, 0.177f, 0.009f, seamCol, 0.009f)
            }
        }
        // Textura tecida em pequenos relevos alternados; deixa áreas amplas menos chapadas.
        if (top == 0 || top == 1 || top == 3 || top == 7 || top == 12 || top == 17) {
            for (row in 0..4) for (col in 0..4) {
                val x = -0.16f + col * 0.08f
                val y = 0.58f + row * 0.065f
                if ((row + col) % 2 == 0) F(x, y, 0.176f, 0.018f, fabricMid, 0.008f)
            }
        }
        // Painel dorsal e costuras em X nas peças estruturadas, visíveis ao girar o personagem.
        K(0f, 0.82f, -0.176f, 0.012f, fabricShadow, 0.014f)
        if (top == 3 || top == 5 || top == 6 || top == 16 || top == 17) {
            K(-0.12f, 0.7f, -0.178f, 0.17f, fabricShadow, 0.014f, -18f)
            K(0.12f, 0.7f, -0.178f, 0.17f, fabricShadow, 0.014f, 18f)
            K(-0.12f, 0.7f, -0.186f, 0.13f, seamCol, 0.009f, -18f)
            K(0.12f, 0.7f, -0.186f, 0.13f, seamCol, 0.009f, 18f)
        }
        // Zíper/fecho tridimensional individual para casacos, moletons e macacões.
        if (top == 1 || top == 3 || top == 6 || top == 17) {
            F(0f, 0.69f, 0.184f, 0.31f, fabricShadow, 0.025f)
            F(0f, 0.69f, 0.199f, 0.27f, seamCol, 0.008f)
            for (j in 0..5) F(0.012f, 0.81f - j * 0.045f, 0.204f, 0.012f, 0xD5D9E0, 0.018f)
            F(-0.025f, 0.86f, 0.2f, 0.045f, 0xD5D9E0, 0.022f)
        }
        // Etiqueta de marca, rebites e presilhas em relevo; cor acompanha o acabamento escolhido.
        F(-0.14f, 0.53f, 0.19f, 0.052f, garmentLo, 0.024f)
        F(-0.14f, 0.53f, 0.205f, 0.032f, garmentA, 0.018f)
        F(-0.14f, 0.53f, 0.217f, 0.012f, garmentHi, 0.012f)
        if (top == 5 || top == 16 || top == 17 || top == 18) {
            for (side in intArrayOf(-1, 1)) {
                F(side * 0.17f, 0.77f, 0.198f, 0.026f, 0xD5D9E0, 0.025f)
                F(side * 0.17f, 0.77f, 0.214f, 0.012f, 0xFFFFFF, 0.012f)
            }
        }

        // ================= CAMADAS DE ESTAMPA PERSONALIZÁVEIS =================
        // Cada motivo é construído com pequenos volumes, como uma skin voxel, não como um adesivo plano.
        val printCol = Look.pal(Look.TOP_ACCENT)
        val printDark = shadeC(printCol, 0.68f)
        val printLight = lite(printCol, 0.36f)
        when (Look.v[Look.TOP_PATTERN]) {
            1 -> { // brasão bordado em camadas
                F(0f, 0.73f, 0.17f, 0.17f, printDark, 0.028f, 45f)
                F(0f, 0.73f, 0.125f, 0.125f, printCol, 0.034f, 45f)
                F(0f, 0.73f, 0.045f, 0.075f, printLight, 0.04f)
                F(-0.045f, 0.72f, 0.025f, 0.06f, printLight, 0.04f, -22f)
                F(0.045f, 0.72f, 0.025f, 0.06f, printDark, 0.04f, 22f)
                F(0f, 0.63f, 0.11f, 0.018f, printCol, 0.026f)
                K(0f, 0.73f, 0.14f, 0.14f, printDark, 0.02f, 45f)
                K(0f, 0.73f, 0.08f, 0.08f, printCol, 0.026f, 45f)
            }
            2 -> { // faixas esportivas duplas
                for (y in floatArrayOf(0.77f, 0.72f, 0.57f)) {
                    F(0f, y, bw * 0.86f, 0.025f, printDark, 0.022f)
                    F(0f, y + 0.014f, bw * 0.86f, 0.009f, printLight, 0.028f)
                }
                K(0f, 0.76f, bw * 0.76f, 0.022f, printCol, 0.02f)
            }
            3 -> { // mosaico xadrez 3x3 com contorno
                F(0f, 0.72f, 0.19f, 0.19f, printDark, 0.02f)
                for (yy in 0..2) for (xx in 0..2) {
                    val col = if ((xx + yy) % 2 == 0) printCol else printLight
                    F(-0.055f + xx * 0.055f, 0.775f - yy * 0.055f, 0.048f, 0.048f, col, 0.03f)
                }
                F(0f, 0.61f, 0.11f, 0.012f, printCol, 0.02f)
            }
            4 -> { // raio pixelado
                F(-0.025f, 0.81f, 0.075f, 0.035f, printDark, 0.024f, -12f)
                F(0.025f, 0.765f, 0.07f, 0.04f, printCol, 0.03f, -30f)
                F(-0.005f, 0.72f, 0.055f, 0.045f, printLight, 0.032f, -30f)
                F(-0.045f, 0.675f, 0.055f, 0.035f, printCol, 0.03f, -12f)
                F(0.015f, 0.635f, 0.045f, 0.035f, printDark, 0.026f, -12f)
            }
            5 -> { // corações em pixel art
                for (x in floatArrayOf(-0.105f, 0.105f)) {
                    F(x - 0.025f, 0.76f, 0.05f, 0.05f, printCol, 0.024f)
                    F(x + 0.025f, 0.76f, 0.05f, 0.05f, printCol, 0.024f)
                    F(x, 0.72f, 0.085f, 0.045f, printCol, 0.026f)
                    F(x, 0.685f, 0.045f, 0.03f, printDark, 0.024f)
                    F(x - 0.018f, 0.77f, 0.018f, 0.018f, printLight, 0.03f)
                }
            }
            6 -> { // constelação de estrelas e pontos
                val pts = arrayOf(floatArrayOf(-0.13f, 0.79f), floatArrayOf(0.02f, 0.82f), floatArrayOf(0.13f, 0.73f), floatArrayOf(-0.055f, 0.67f))
                for ((i, pt) in pts.withIndex()) {
                    val size = if (i % 2 == 0) 0.055f else 0.035f
                    F(pt[0], pt[1], size, size, printCol, 0.024f, 45f)
                    F(pt[0], pt[1], size * 0.35f, size * 0.35f, printLight, 0.032f)
                }
                F(0.075f, 0.64f, 0.025f, 0.025f, printLight, 0.022f)
                F(-0.15f, 0.72f, 0.018f, 0.018f, printLight, 0.022f)
            }
            7 -> { // painel tecnológico com linhas, placas e pixels de status
                F(0.095f, 0.72f, 0.07f, 0.24f, printDark, 0.026f)
                F(0.095f, 0.72f, 0.045f, 0.19f, printCol, 0.034f)
                for (k in 0..3) F(0.095f, 0.65f + k * 0.045f, 0.03f, 0.012f, printLight, 0.04f)
                F(-0.095f, 0.77f, 0.07f, 0.07f, printDark, 0.026f)
                F(-0.095f, 0.77f, 0.04f, 0.04f, printCol, 0.034f)
                F(-0.095f, 0.67f, 0.06f, 0.012f, printLight, 0.026f)
                K(0f, 0.7f, 0.16f, 0.12f, printDark, 0.02f)
                K(0f, 0.7f, 0.09f, 0.05f, printCol, 0.026f)
            }
        }

        // ================= BRAÇOS (ombro + cotovelo) =================
        val toolArm = an.hasTool
        val sl = when (top) { 0 -> 0.16f; 1 -> 0.36f; 2 -> 0.13f; 3 -> 0.36f; 4 -> 0.30f; 5 -> 0.2f; 6 -> 0.16f; 7 -> 0.32f; 8 -> 0.18f; 9 -> 0.36f; 10 -> 0.34f; 11 -> 0.36f; 12 -> 0.36f; 13 -> 0f; 14 -> 0.30f; 15 -> 0.14f; 16 -> 0.16f; else -> 0.36f }
        val slCol = when (top) { 5 -> 0x4A5568; 6, 8, 15, 16 -> white; else -> tc }
        val asw = sw * (0.95f + 0.5f * rn)
        var rxL = asw - 4f; var rxR = -asw - 4f   // braço direito (sd<0) balança contra a perna direita
        var elL = -(8f + 6f * stride + 58f * rn * stride); var elR = elL
        var rzL = -(4f + sin(t * 2f) * 1.5f * (1f - stride)); var rzR = -rzL
        val aa = an.atkArm
        val ikL = FloatArray(2); val ikR = FloatArray(2)
        val atkRx = if (an.poseOn) -an.pP * 0.95f else aa
        var ryR = 0f; var ryL = 0f
        if (toolArm) { rxR = -22f + atkRx - sw * 0.25f * (1f - rn * 0.5f); elR = -6f - 6f * rn * stride; rzR = 20f
            if (an.poseOn) { ryR = an.pY * 0.9f; elR = (elR + an.pW * 0.5f).coerceIn(-70f, 10f) } }
        else if (atking || chg > 0.01f) { rxR += atkRx; if (an.poseOn) ryR = an.pY * 0.9f }
        var swimToolA = 0f
        if (swimming) {
            // cabeça fora: braçada alternada e calma (alcança, puxa pra baixo, volta com o cotovelo dobrado). Mergulhado: braçada de peito (V à frente, abre, puxa pros lados e volta).
            val fa = an.swimFast.coerceIn(0f, 1f); val sa = an.swimPh; val pi = 3.1416f   // fa: só acelerando os braços batem
            val sl2 = sin(t * 2.3f)
            // sem acelerar: na superfície os bracinhos batem leve pra boiar (parado ou andando); mergulhado os dois braços ficam inclinados pra trás, nas diagonais
            val lSurfX = -25f + sl2 * 14f; val lSurfZ = -(38f + sin(t * 2.3f + 1.2f) * 16f); val lSurfE = -20f + sl2 * 8f
            val diagX = 14f + sin(t * 2.5f) * 3f; val diagZ = 30f + sin(t * 2.5f + 1f) * 3f; val diagE = -6f
            val recL = max(0f, cos(sa)); val recR = max(0f, cos(sa + pi))
            val sXL = lp(lSurfX, -67f - 55f * sin(sa), fa); val sXR = lp(lSurfX, -67f - 55f * sin(sa + pi), fa) + atkRx
            val sZL = lp(lSurfZ, -8f, fa); val sZR = lp(-lSurfZ, 8f, fa)
            val sEL = lp(lSurfE, -(7f + 30f * recL), fa); val sER = lp(lSurfE, -(7f + 30f * recR), fa)
            val sYL = (16f + 10f * recL) * fa; val sYR = -(16f + 10f * recR) * fa
            var e = (sa / 6.2832f) % 1f; if (e < 0f) e += 1f
            var ki = 0; while (ki < 4 && e >= STROKE_T[ki + 1]) ki++
            val ue = (e - STROKE_T[ki]) / (STROKE_T[ki + 1] - STROKE_T[ki]); val u = ue * ue * (3f - 2f * ue)
            val ka = STROKE_KF[ki]; val kb = STROKE_KF[ki + 1]
            val yw = lp(ka[0], kb[0], u); val ev = lp(ka[1], kb[1], u); val dE0 = lp(ka[2], kb[2], u)
            armIk(yw, ev, 1f, ikL); armIk(yw, ev, -1f, ikR)
            val dXL = lp(diagX, ikL[0], fa); val dZL = lp(-diagZ, -ikL[1], fa); val dEL = lp(diagE, dE0, fa)
            val dXR = lp(diagX, ikR[0], fa) + atkRx; val dZR = lp(diagZ, -ikR[1], fa)
            rxL = lp(sXL, dXL, dv); rzL = lp(sZL, dZL, dv); elL = lp(sEL, dEL, dv); ryL = lp(sYL, 0f, dv)
            rxR = lp(sXR, dXR, dv); rzR = lp(sZR, dZR, dv); elR = lp(sER, dEL, dv); ryR = lp(sYR, 0f, dv)
            if (an.poseOn && toolArm) ryR += an.pY * 0.9f
            swimToolA = 0.5f * (-(an.tilt - 14f) - (rxR - atkRx + elR))   // o item segue a mão (metade da compensação): sem atravessar cabeça nem tronco
        }
        if (ab > 0.01f) {
            val flapA = sin(t * 12f) * 6f * (1f - abs(vk))
            val pxL = lp(-35f, -10f, fs) + flapA; val pzL = lp(-150f, -75f, fs); val pelL = lp(-10f, -8f, fs)
            rxL = lp(rxL, pxL, ab); rzL = lp(rzL, pzL, ab); elL = lp(elL, pelL, ab)
            // no pulo os dois braços sobem igual (o do item também), com o golpe somado por cima
            rxR = lp(rxR, lp(-35f, -10f, fs) - flapA + atkRx, ab); rzR = lp(rzR, lp(150f, 75f, fs), ab); elR = lp(elR, pelL, ab)
        }
        if (fl > 0.01f) {
            // os dois braços ficam pra baixo, iguais ao do item: só um balanço leve
            val fw = sin(t * 3f) * 4f * (1f - fm)
            val dn = lp(-14f, -26f, fm)
            rxL = lp(rxL, dn + sin(t * 2.6f) * 3f, fl); rzL = lp(rzL, -(16f + fw), fl); elL = lp(elL, -8f, fl)
            if (toolArm) { rxR = lp(rxR, lp(-24f, -40f, fm) + atkRx, fl); rzR = lp(rzR, 20f, fl) }
            else { rxR = lp(rxR, dn - sin(t * 2.6f) * 3f, fl); rzR = lp(rzR, 16f + fw, fl); elR = lp(elR, -8f, fl) }
        }
        var dashToolA = 0f
        if (dashW > 0.001f && fl > 0.01f) {   // voo rápido: braços pra trás, rentes ao corpo (puxa mais no impulso)
            val ax = lp(14f, 36f, wind); val fl2 = sin(t * 9f) * 3f
            rxL = lp(rxL, ax + fl2, dashW); rxR = lp(rxR, ax - fl2, dashW)
            rzL = lp(rzL, -9f, dashW); rzR = lp(rzR, 9f, dashW); elL = lp(elL, -5f, dashW); elR = lp(elR, -5f, dashW)
            dashToolA = dashW * (-lean + 180f - (rxR + elR))   // item fica pra trás, longe da cabeça
        }
        if (tkW > 0.001f) {   // braços na manobra
            rxL = lp(rxL, 30f, tkAnt); rxR = lp(rxR, 30f, tkAnt); rzL = lp(rzL, -12f, tkAnt); rzR = lp(rzR, 12f, tkAnt)   // balanço pra trás antes de sair
            rxL = lp(rxL, -82f, tkTuck); rxR = lp(rxR, -82f, tkTuck); rzL = lp(rzL, -9f, tkTuck); rzR = lp(rzR, 9f, tkTuck)   // abraça os joelhos
            elL = lp(elL, -62f, tkTuck); elR = lp(elR, -62f, tkTuck)
            rxL = lp(rxL, -8f, tkRoll); rxR = lp(rxR, -8f, tkRoll); rzL = lp(rzL, -74f, tkRoll); rzR = lp(rzR, 74f, tkRoll)   // braços abertos, tipo avião
            elL = lp(elL, -4f, tkRoll); elR = lp(elR, -4f, tkRoll)
            rxL = lp(rxL, -18f, tkOpen); rxR = lp(rxR, -18f, tkOpen); rzL = lp(rzL, -62f, tkOpen); rzR = lp(rzR, 62f, tkOpen)   // saída: abre os braços
            elL = lp(elL, -6f, tkOpen); elR = lp(elR, -6f, tkOpen)
        }
        an.armFwdR = 0f
        an.toolA = if (swimming) swimToolA else dashToolA
        if (land > 0f) { rzL -= 25f * land; if (!toolArm) rzR += 25f * land }
        var elbowSpin = 0f
        if (li > 0.001f) {   // rastejar: cada braço alcança pra frente no plano do chão (levantado), apoia e puxa com o cotovelo dobrado no plano
            val mc = min(1f, mv * 3f); val pl = (0.5f + 0.5f * sin(ph)) * mc; val pr = (0.5f + 0.5f * sin(ph + 3.1416f)) * mc
            val fwdL = max(0f, cos(ph)) * mc; val fwdR = max(0f, cos(ph + 3.1416f)) * mc
            rxL = lp(rxL, 3f + 17f * fwdL, li); rxR = lp(rxR, 3f + 17f * fwdR, li)
            rzL = lp(rzL, -(32f + 68f * pl), li); rzR = lp(rzR, 32f + 68f * pr, li)
            elL = lp(elL, -(14f + 36f * (1f - pl)), li); elR = lp(elR, -(14f + 36f * (1f - pr)), li)
            ryR = lp(ryR, 0f, li); ryL = lp(ryL, 0f, li); elbowSpin = 90f * li
        }
        val rd = an.ride.coerceIn(0f, 1f)
        if (rd > 0.001f) { rxL = lp(rxL, -62f, rd); rxR = lp(rxR, -62f, rd); elL = lp(elL, -14f, rd); elR = lp(elR, -14f, rd); rzL = lp(rzL, -5f, rd); rzR = lp(rzR, 5f, rd) }
        for (sd in intArrayOf(-1, 1)) {
            val s = sd.toFloat()
            val rx = if (sd < 0) rxR else rxL; val rz = if (sd < 0) -rzR else -rzL; val el = if (sd < 0) elR else elL   // +x é a ESQUERDA do boneco: o item vai na direita (sd<0)
            piv(t1, base, s * (bw / 2f + 0.09f + (if (top == 1 || top == 17) 0.03f else if (top == 5 || top == 11) 0.015f else 0f)), 0.88f, 0f, rx = rx, ry = if (sd < 0) ryR else ryL, rz = rz)
            piv(t2, t1, 0f, -0.21f, 0f, rx = el, ry = elbowSpin * s)
            run {   // pele só onde não há manga
                val remS = sl - 0.215f
                val uTop = if (sl > 0f) 0.02f - min(sl, 0.215f) else 0.015f; val uBot = -0.215f
                if (uTop - uBot > 0.012f && sl < 0.215f) b(t1, 0f, (uTop + uBot) / 2f, 0f, 0.15f, uTop - uBot, 0.17f, skin)   // braço todo de manga: sem miolo de pele (aparecia na dobra do cotovelo)
                val fTop = if (remS > 0f) 0.02f - remS else 0.01f; val fBot = -0.21f
                if (fTop - fBot > 0.012f) b(t2, 0f, (fTop + fBot) / 2f, 0f, 0.15f, fTop - fBot, 0.17f, skin)
                val hTop = min(-0.1f, fTop + 0.01f); val hBot = -0.22f
                if (hTop - hBot > 0.012f) b(t2, 0f, (hTop + hBot) / 2f, 0.005f, 0.165f, hTop - hBot, 0.185f, skin)
            }
            // juntas do braço: cotovelo e ombro na cor da manga (ou da pele, sem manga)
            if (sl >= 0.19f) b(t2, 0f, 0f, 0f, 0.183f, 0.14f, 0.203f, slCol) else b(t2, 0f, 0f, 0f, 0.152f, 0.07f, 0.172f, skin)
            if (sl > 0f) b(t1, 0f, 0.0f, 0f, 0.186f, 0.12f, 0.206f, slCol)
            val su = min(sl, 0.215f)
            if (sl > 0f) {
                b(t1, 0f, (0.03f - su) / 2f, 0f, 0.18f, su + 0.03f, 0.2f, slCol)
                if (sl > 0.215f) {
                    val rem = sl - 0.215f
                    b(t2, 0f, 0.005f - rem / 2f, 0f, 0.182f, rem + 0.01f, 0.202f, slCol)
                    if (sl >= 0.28f) b(t2, 0f, -rem + 0.02f, 0f, 0.19f, 0.04f, 0.21f, shadeC(slCol, 0.82f))
                } else if (sl >= 0.12f && top != 13) b(t1, 0f, -su + 0.02f, 0f, 0.19f, 0.035f, 0.21f, shadeC(slCol, 0.85f))
            } else b(t1, 0f, 0.03f, 0f, 0.17f, 0.06f, 0.19f, skinD)
            when (top) {
                5 -> {
                    b(t1, s * 0.01f, 0.05f, 0f, 0.22f, 0.1f, 0.24f, mixC(0xC5CEDA, tc, 0.3f))
                    b(t1, s * 0.01f, 0.0f, 0f, 0.225f, 0.025f, 0.245f, gold)
                }
                8 -> b(t1, 0f, -su + 0.05f, 0f, 0.188f, 0.025f, 0.208f, tc)
                10, 11, 14 -> {   // manga larga
                    b(t2, s * 0.03f, -0.08f, 0f, 0.2f, 0.26f, 0.24f, slCol)
                    b(t2, s * 0.03f, -0.2f, 0f, 0.205f, 0.03f, 0.245f, if (top == 11) gold else lite(slCol, 0.55f))
                }
                15 -> b(t1, 0f, -0.1f, 0f, 0.22f, 0.1f, 0.22f, white)
                17 -> { b(t1, 0f, -0.1f, 0f, 0.21f, 0.04f, 0.23f, dark); b(t2, 0f, -0.05f, 0f, 0.21f, 0.04f, 0.23f, dark) }
                9 -> b(t2, 0f, -rnd(sl), 0f, 0.194f, 0.025f, 0.214f, gold)
                0 -> b(t1, 0f, -su + 0.03f, 0f, 0.186f, 0.03f, 0.206f, dark)
                1 -> { b(t2, 0f, -0.185f, 0f, 0.195f, 0.06f, 0.215f, dark); b(t2, 0f, -0.14f, 0f, 0.192f, 0.012f, 0.212f, lite(tc, 0.2f)) }
                3 -> { b(t2, 0f, -0.185f, 0f, 0.196f, 0.07f, 0.216f, dark); b(t1, 0f, -0.04f, 0f, 0.186f, 0.02f, 0.206f, lite(tc, 0.35f)) }
                4 -> b(t2, 0f, -rnd(sl), 0f, 0.194f, 0.025f, 0.214f, gold)
                7 -> { b(t2, 0f, -0.18f, 0f, 0.192f, 0.07f, 0.212f, lite(tc, 0.55f)); b(t2, s * 0.0f, -0.16f, 0.108f, 0.025f, 0.025f, 0.012f, white) }
                12 -> { b(t2, 0f, -0.19f, 0f, 0.192f, 0.04f, 0.212f, white); b(t2, 0f, -0.15f, 0.108f, 0.02f, 0.02f, 0.012f, gold) }
                13 -> {}
                16 -> { b(t2, 0f, -0.1f, 0f, 0.17f, 0.12f, 0.19f, leather); b(t2, 0f, -0.04f, 0f, 0.174f, 0.014f, 0.194f, gold) }
            }
            // Acabamento exclusivo de mangas: reforços e emblemas presos aos membros animados.
            val sleeveSigil = Look.pal(Look.TOP_ACCENT)
            val sleeveLite = lite(slCol, 0.35f)
            when (top) {
                0 -> { if(sl>0.06f) { b(t1,0f,-su+0.025f,0.108f,0.17f,0.012f,0.012f,sleeveLite); b(t1,0f,-su+0.044f,0.109f,0.17f,0.006f,0.01f,shadeC(slCol,0.6f)) }; b(t1,0f,0.045f,0.12f,0.05f,0.05f,0.018f,sleeveSigil,rz=45f) }
                1 -> { b(t1,0f,-0.08f,0.11f,0.14f,0.05f,0.018f,shadeC(slCol,0.6f),rz=8f); for(i in -1..1) b(t2,i*0.045f,-0.17f,0.12f,0.014f,0.014f,0.012f,0xD5D9E0) }
                2 -> { b(t1,0f,-0.015f,0.11f,0.19f,0.055f,0.02f,lite(tc,0.18f)); for(i in -2..2) b(t1,i*0.035f,-0.055f,0.125f,0.016f,0.016f,0.012f,0xF0D9A1) }
                3 -> { b(t1,0f,0.045f,0.12f,0.19f,0.03f,0.02f,shadeC(tc,0.6f)); b(t2,0f,-0.08f,0.11f,0.145f,0.085f,0.02f,shadeC(tc,0.62f)); for(i in -2..2) b(t2,i*0.025f,-0.08f,0.125f,0.008f,0.07f,0.009f,sleeveLite) }
                4 -> { if(sl>0.1f) { b(t2,0f,-rnd(sl),0.11f,0.19f,0.024f,0.016f,gold); b(t2,0f,-rnd(sl)+0.02f,0.12f,0.15f,0.008f,0.01f,0xF0D9A1) }; b(t1,0f,0.035f,0.12f,0.075f,0.05f,0.018f,sleeveSigil,rz=45f) }
                5 -> { b(t1,0f,0.025f,0.125f,0.19f,0.06f,0.03f,steel); b(t1,0f,0.005f,0.145f,0.16f,0.018f,0.012f,lite(steel,0.4f)); for(i in -1..1) b(t1,i*0.06f,0.018f,0.15f,0.018f,0.018f,0.012f,gold) }
                6 -> { b(t1,0f,0.035f,0.115f,0.17f,0.018f,0.014f,sleeveLite); b(t1,0.04f,-0.045f,0.125f,0.038f,0.038f,0.012f,sleeveSigil) }
                7 -> { b(t2,0f,-0.18f,0.11f,0.19f,0.045f,0.018f,lite(tc,0.55f)); b(t2,0.04f,-0.18f,0.13f,0.02f,0.02f,0.012f,gold) }
                8 -> { b(t1,0f,-0.03f,0.112f,0.18f,0.018f,0.014f,tc); b(t1,0f,-0.055f,0.115f,0.18f,0.01f,0.01f,white); b(t2,0f,-0.16f,0.11f,0.18f,0.018f,0.014f,tc) }
                9 -> { b(t2,0f,-rnd(sl),0.113f,0.19f,0.026f,0.016f,gold); for(i in -1..1) b(t1,i*0.045f,0.035f,0.124f,0.018f,0.028f,0.012f,gold) }
                10 -> { b(t2,0f,-0.1f,0.115f,0.19f,0.22f,0.018f,shadeC(tc,0.7f)); b(t2,0f,-0.19f,0.128f,0.19f,0.025f,0.014f,sleeveLite); b(t1,0f,-0.04f,0.136f,0.045f,0.045f,0.012f,sleeveSigil,rz=45f) }
                11 -> { b(t2,0f,-0.18f,0.116f,0.2f,0.035f,0.02f,gold); b(t1,0f,0.03f,0.13f,0.06f,0.06f,0.014f,gold,rz=45f); b(t1,0f,0.03f,0.14f,0.03f,0.03f,0.012f,sleeveSigil,rz=45f) }
                12 -> { b(t2,0f,-0.18f,0.11f,0.19f,0.05f,0.02f,white); b(t2,0.045f,-0.175f,0.13f,0.02f,0.02f,0.012f,gold) }
                13 -> { /* braços expostos por desenho */ }
                14 -> { b(t2,0f,-0.18f,0.114f,0.2f,0.03f,0.018f,sleeveSigil); b(t1,0f,-0.04f,0.126f,0.04f,0.07f,0.014f,sleeveSigil,rz=28f); b(t1,0.03f,-0.04f,0.136f,0.018f,0.045f,0.01f,0xF0D9A1,rz=-28f) }
                15 -> { b(t1,0f,-0.03f,0.114f,0.2f,0.04f,0.024f,white); for(i in -2..2) b(t1,i*0.035f,-0.055f,0.13f,0.016f,0.016f,0.012f,sleeveLite); b(t2,0f,-0.18f,0.113f,0.19f,0.035f,0.022f,white) }
                16 -> { b(t1,s*0.06f,-0.03f,0.118f,0.03f,0.12f,0.02f,leather); b(t1,s*0.06f,0.015f,0.132f,0.04f,0.024f,0.012f,gold); b(t2,0f,-0.08f,0.111f,0.15f,0.08f,0.018f,shadeC(slCol,0.62f)) }
                else -> { for(i in 0..2) { b(t1,0f,0.015f-i*0.045f,0.114f,0.17f,0.008f,0.012f,shadeC(slCol,0.62f)); b(t2,0f,-0.06f-i*0.04f,0.114f,0.17f,0.008f,0.012f,shadeC(slCol,0.62f)) }; b(t2,0f,-0.17f,0.125f,0.17f,0.025f,0.016f,0xD5D9E0) }
            }
            // A estampa da camisa também aparece nas mangas; o relevo acompanha ombro e cotovelo.
            if (sl > 0.12f) {
                val ac = Look.pal(Look.TOP_ACCENT)
                when (L[Look.TOP_PATTERN]) {
                    1 -> {
                        b(t1, 0f, -0.04f, 0.111f, 0.075f, 0.075f, 0.018f, shadeC(ac, 0.7f), rz = 45f)
                        b(t1, 0f, -0.04f, 0.123f, 0.045f, 0.045f, 0.012f, ac, rz = 45f)
                    }
                    2 -> {
                        b(t1, 0f, -0.1f, 0.108f, 0.17f, 0.018f, 0.018f, ac)
                        b(t2, 0f, -0.15f, 0.108f, 0.17f, 0.018f, 0.018f, ac)
                    }
                    3 -> for (k in 0..2) b(t1, -0.045f + k * 0.045f, -0.08f, 0.111f, 0.038f, 0.038f, 0.018f, if (k % 2 == 0) ac else lite(ac, 0.35f))
                    4 -> {
                        b(t1, 0f, -0.04f, 0.111f, 0.035f, 0.07f, 0.018f, ac, rz = -25f)
                        b(t1, 0.025f, -0.09f, 0.123f, 0.03f, 0.06f, 0.012f, lite(ac, 0.35f), rz = -25f)
                    }
                    5 -> {
                        b(t1, -0.035f, -0.045f, 0.111f, 0.04f, 0.04f, 0.018f, ac)
                        b(t1, 0.035f, -0.045f, 0.111f, 0.04f, 0.04f, 0.018f, ac)
                        b(t1, 0f, -0.08f, 0.123f, 0.035f, 0.035f, 0.012f, shadeC(ac, 0.72f))
                    }
                    6 -> {
                        b(t1, 0f, -0.045f, 0.111f, 0.045f, 0.045f, 0.018f, ac, rz = 45f)
                        b(t1, 0f, -0.045f, 0.123f, 0.018f, 0.018f, 0.012f, lite(ac, 0.4f))
                    }
                    7 -> {
                        b(t1, 0f, -0.04f, 0.111f, 0.08f, 0.085f, 0.018f, shadeC(ac, 0.7f))
                        b(t1, 0f, -0.04f, 0.123f, 0.055f, 0.06f, 0.012f, ac)
                        b(t1, 0f, -0.04f, 0.133f, 0.018f, 0.045f, 0.01f, lite(ac, 0.35f))
                    }
                }
            }
            if (sd < 0 && handOut != null) {
                piv(handOut, t1, 0f, -0.21f, 0f, rx = el, ry = elbowSpin * s)
                Matrix.translateM(handOut, 0, 0f, -0.16f, 0.02f)
            }
        }

        // ================= COSTAS =================
        val bcol = Look.pal(Look.BACKC)
        when (L[Look.BACK]) {
            1 -> {
                piv(t3, base, 0f, 0.9f, -0.17f, rx = 6f + move * 14f + sin(t * 2f) * 2f)
                b(t3, 0f, -0.28f, -0.025f, 0.46f, 0.56f, 0.05f, bcol)
                piv(t4, t3, 0f, -0.56f, 0f, rx = 4f + move * 10f + sin(t * 3f) * 4f)
                b(t4, 0f, -0.12f, -0.02f, 0.52f, 0.24f, 0.05f, shadeC(bcol, 0.9f))
                b(t4, 0f, -0.235f, -0.02f, 0.54f, 0.035f, 0.055f, lite(bcol, 0.5f))
                b(base, 0f, 0.93f, -0.1f, 0.5f, 0.07f, 0.22f, bcol)
                b(base, -0.12f, 0.9f, 0.17f, 0.05f, 0.05f, 0.03f, gold)
                b(base, 0.12f, 0.9f, 0.17f, 0.05f, 0.05f, 0.03f, gold)
            }
            2 -> {
                b(base, 0f, 0.7f, -0.25f, 0.34f, 0.4f, 0.2f, bcol)
                b(base, 0f, 0.62f, -0.36f, 0.26f, 0.18f, 0.06f, shadeC(bcol, 0.85f))
                b(base, 0f, 0.86f, -0.26f, 0.35f, 0.1f, 0.22f, lite(bcol, 0.25f))
                b(base, -0.12f, 0.72f, 0.155f, 0.05f, 0.42f, 0.025f, shadeC(bcol, 0.8f))
                b(base, 0.12f, 0.72f, 0.155f, 0.05f, 0.42f, 0.025f, shadeC(bcol, 0.8f))
                b(base, -0.12f, 0.93f, 0f, 0.05f, 0.04f, 0.32f, shadeC(bcol, 0.8f))
                b(base, 0.12f, 0.93f, 0f, 0.05f, 0.04f, 0.32f, shadeC(bcol, 0.8f))
                b(base, 0f, 0.82f, -0.372f, 0.06f, 0.06f, 0.02f, gold)
            }
            3 -> {
                val flap = sin(t * 5f) * 8f + (if (air) 24f else 0f) + move * 8f
                for (sd in intArrayOf(-1, 1)) {
                    val s = sd.toFloat()
                    piv(t3, base, s * 0.09f, 0.82f, -0.19f, ry = s * (10f + flap), rz = -s * 35f)
                    b(t3, 0f, 0.22f, 0f, 0.16f, 0.44f, 0.04f, bcol)
                    b(t3, s * 0.1f, 0.14f, 0f, 0.14f, 0.3f, 0.035f, lite(bcol, 0.3f))
                    b(t3, s * 0.18f, 0.1f, 0f, 0.1f, 0.2f, 0.03f, lite(bcol, 0.55f))
                }
            }
            4 -> {
                piv(t3, base, 0f, 0.52f, -0.17f, rx = 35f + sin(t * 3f) * 8f + sw * 0.2f)
                b(t3, 0f, -0.1f, 0f, 0.09f, 0.22f, 0.09f, bcol)
                piv(t4, t3, 0f, -0.2f, 0f, rx = -25f + sin(t * 3f + 1f) * 10f)
                b(t4, 0f, -0.1f, 0f, 0.08f, 0.2f, 0.08f, bcol)
                piv(t5, t4, 0f, -0.2f, 0f, rx = -35f + sin(t * 3f + 2f) * 12f)
                b(t5, 0f, -0.07f, 0f, 0.08f, 0.14f, 0.08f, lite(bcol, 0.55f))
            }
            5 -> {   // espada nas costas
                piv(t3, base, 0f, 0.68f, -0.2f, rz = -38f)
                b(t3, 0f, 0.0f, 0f, 0.08f, 0.82f, 0.06f, 0x3A2A22)
                b(t3, 0f, -0.3f, 0.0f, 0.095f, 0.05f, 0.07f, gold)
                b(t3, 0f, 0.43f, 0f, 0.24f, 0.045f, 0.08f, gold)
                b(t3, 0f, 0.56f, 0f, 0.055f, 0.2f, 0.055f, bcol)
                b(t3, 0f, 0.67f, 0f, 0.08f, 0.05f, 0.08f, gold)
                b(base, 0f, 0.7f, -0.17f, 0.06f, 0.06f, 0.025f, gold)
            }
            6 -> {   // asas de morcego
                val fl = sin(t * 4f) * 8f + (if (air) 30f else 0f) + move * 10f
                for (sd in intArrayOf(-1, 1)) {
                    val s = sd.toFloat()
                    piv(t3, base, s * 0.08f, 0.8f, -0.19f, ry = s * (28f + fl), rz = -s * 22f)
                    b(t3, s * 0.18f, 0.12f, 0f, 0.36f, 0.05f, 0.04f, shadeC(bcol, 0.6f))
                    b(t3, s * 0.15f, 0.0f, 0f, 0.3f, 0.2f, 0.02f, bcol, a = 0.95f)
                    b(t3, s * 0.3f, 0.06f, 0f, 0.2f, 0.28f, 0.02f, lite(bcol, 0.2f), a = 0.95f)
                    b(t3, s * 0.44f, 0.15f, 0f, 0.06f, 0.1f, 0.04f, shadeC(bcol, 0.6f))
                }
            }
            7 -> {   // aljava com flechas
                piv(t3, base, 0f, 0.7f, -0.2f, rz = 30f)
                b(t3, 0f, 0f, 0f, 0.14f, 0.5f, 0.12f, 0x6F4A29)
                b(t3, 0f, 0.26f, 0f, 0.16f, 0.04f, 0.14f, shadeC(0x6F4A29, 0.7f))
                for (i in -1..1) {
                    b(t3, i * 0.04f, 0.34f, 0f, 0.02f, 0.2f, 0.02f, 0xB08A5A)
                    b(t3, i * 0.04f, 0.45f, 0f, 0.06f, 0.07f, 0.02f, bcol)
                }
                b(base, 0f, 0.7f, 0.158f, 0.04f, 0.58f, 0.01f, 0x6F4A29, rz = -30f)
            }
            8 -> {   // rabo de raposa
                piv(t3, base, 0f, 0.5f, -0.17f, rx = 45f + sin(t * 2.4f) * 8f + sw * 0.25f)
                b(t3, 0f, -0.14f, 0f, 0.2f, 0.3f, 0.2f, bcol)
                piv(t4, t3, 0f, -0.28f, 0f, rx = -20f + sin(t * 2.4f + 1f) * 10f)
                b(t4, 0f, -0.14f, 0f, 0.26f, 0.3f, 0.26f, lite(bcol, 0.1f))
                piv(t5, t4, 0f, -0.28f, 0f, rx = -25f + sin(t * 2.4f + 2f) * 12f)
                b(t5, 0f, -0.08f, 0f, 0.2f, 0.18f, 0.2f, 0xF8F8F8)
            }
            9 -> {   // mochila escolar (randoseru)
                b(base, 0f, 0.66f, -0.25f, 0.4f, 0.5f, 0.17f, bcol)
                b(base, 0f, 0.8f, -0.255f, 0.42f, 0.2f, 0.18f, shadeC(bcol, 0.85f))
                b(base, 0f, 0.78f, -0.35f, 0.07f, 0.07f, 0.02f, gold)
                b(base, 0f, 0.5f, -0.32f, 0.3f, 0.12f, 0.04f, lite(bcol, 0.2f))
                b(base, -0.12f, 0.72f, 0.155f, 0.05f, 0.42f, 0.025f, shadeC(bcol, 0.8f))
                b(base, 0.12f, 0.72f, 0.155f, 0.05f, 0.42f, 0.025f, shadeC(bcol, 0.8f))
                b(base, -0.12f, 0.935f, 0f, 0.05f, 0.04f, 0.32f, shadeC(bcol, 0.8f))
                b(base, 0.12f, 0.935f, 0f, 0.05f, 0.04f, 0.32f, shadeC(bcol, 0.8f))
            }
        }

        // ===== CAMADA DE IDENTIDADE VISUAL PARA ACESSÓRIOS DAS COSTAS =====
        // Mochilas, capas, asas, armas e caudas recebem acabamento próprio em relevo.
        val backHi = lite(bcol, 0.32f)
        val backLo = shadeC(bcol, 0.65f)
        when (L[Look.BACK]) {
            1 -> { // capa: borda dupla, fechos e costura em losangos
                b(base, -0.19f, 0.49f, -0.206f, 0.025f, 0.68f, 0.025f, backLo)
                b(base, 0.19f, 0.49f, -0.206f, 0.025f, 0.68f, 0.025f, backLo)
                b(base, 0f, 0.2f, -0.207f, 0.34f, 0.025f, 0.025f, backHi)
                for (i in -2..2) {
                    b(base, i * 0.055f, 0.43f + (i % 2) * 0.055f, -0.211f, 0.025f, 0.025f, 0.018f, backHi)
                }
                for (sd in intArrayOf(-1, 1)) {
                    b(base, sd * 0.12f, 0.91f, -0.205f, 0.055f, 0.07f, 0.025f, gold)
                    b(base, sd * 0.12f, 0.91f, -0.222f, 0.025f, 0.035f, 0.012f, 0xFFF0A0)
                }
            }
            2, 9 -> { // mochila: bolsos, costura, alças e fivelas
                b(base, 0f, 0.62f, -0.37f, 0.27f, 0.19f, 0.035f, backHi)
                b(base, 0f, 0.61f, -0.394f, 0.2f, 0.12f, 0.018f, backLo)
                b(base, 0f, 0.67f, -0.409f, 0.09f, 0.035f, 0.012f, gold)
                for (sd in intArrayOf(-1, 1)) {
                    b(base, sd * 0.13f, 0.73f, -0.365f, 0.025f, 0.27f, 0.018f, backHi)
                    b(base, sd * 0.13f, 0.83f, -0.38f, 0.065f, 0.035f, 0.02f, 0xD9DDE5)
                    b(base, sd * 0.13f, 0.83f, -0.395f, 0.025f, 0.012f, 0.01f, gold)
                }
            }
            3, 6 -> { // asas: nervuras e contorno reforçado
                for (sd in intArrayOf(-1, 1)) {
                    val s = sd.toFloat()
                    b(base, s * 0.18f, 0.86f, -0.22f, 0.04f, 0.15f, 0.035f, backHi, rz = -s * 18f)
                    b(base, s * 0.3f, 0.76f, -0.22f, 0.035f, 0.17f, 0.025f, backLo, rz = -s * 30f)
                    b(base, s * 0.38f, 0.7f, -0.215f, 0.025f, 0.1f, 0.02f, backHi, rz = -s * 38f)
                }
            }
            5 -> { // espada: bainha, runa e rebites do punho
                b(base, -0.045f, 0.7f, -0.24f, 0.018f, 0.55f, 0.018f, backHi, rz = -38f)
                b(base, 0.04f, 0.7f, -0.24f, 0.018f, 0.55f, 0.018f, backLo, rz = -38f)
                b(base, 0f, 0.91f, -0.245f, 0.05f, 0.06f, 0.025f, 0xE8F2FF)
                b(base, 0f, 0.91f, -0.26f, 0.022f, 0.035f, 0.012f, 0x79CFFF)
            }
        }

        // ================= PESCOÇO =================
        val ncol = Look.pal(Look.NECKC)
        when (L[Look.NECK]) {
            9 -> {   // medalha dourada com fita
                b(base, 0f, 0.86f, 0.158f, 0.07f, 0.2f, 0.012f, 0xE05555)
                b(base, 0f, 0.72f, 0.165f, 0.12f, 0.12f, 0.02f, gold)
                b(base, 0f, 0.72f, 0.176f, 0.06f, 0.06f, 0.012f, 0xFFF0A0)
            }
            1 -> {
                b(base, 0f, 0.93f, 0f, 0.52f, 0.1f, 0.35f, ncol)
                b(base, 0f, 0.945f, 0f, 0.53f, 0.025f, 0.36f, lite(ncol, 0.45f))
                b(base, 0.12f, 0.78f, 0.185f, 0.13f, 0.3f, 0.05f, ncol)
                b(base, 0.12f, 0.64f, 0.185f, 0.13f, 0.03f, 0.055f, shadeC(ncol, 0.8f))
                piv(t3, base, -0.12f, 0.9f, -0.19f, rx = 4f + move * 18f + sin(t * 2.5f) * 3f)
                b(t3, 0f, -0.16f, -0.02f, 0.13f, 0.32f, 0.05f, ncol)
            }
            2 -> {
                b(base, 0f, 0.925f, 0f, 0.3f, 0.03f, 0.32f, shadeC(ncol, 0.85f))
                b(base, 0f, 0.9f, 0.165f, 0.07f, 0.07f, 0.04f, ncol)
                b(base, -0.075f, 0.905f, 0.165f, 0.11f, 0.09f, 0.04f, ncol, rz = 14f)
                b(base, 0.075f, 0.905f, 0.165f, 0.11f, 0.09f, 0.04f, ncol, rz = -14f)
                b(base, -0.03f, 0.83f, 0.165f, 0.04f, 0.12f, 0.03f, shadeC(ncol, 0.85f), rz = -10f)
                b(base, 0.03f, 0.83f, 0.165f, 0.04f, 0.12f, 0.03f, shadeC(ncol, 0.85f), rz = 10f)
            }
            3 -> {   // colar: corrente + pingente
                b(base, 0f, 0.94f, 0f, 0.31f, 0.025f, 0.34f, gold)
                b(base, 0f, 0.885f, 0.172f, 0.025f, 0.1f, 0.02f, gold)
                b(base, 0f, 0.8f, 0.176f, 0.075f, 0.085f, 0.03f, ncol)
                b(base, -0.012f, 0.815f, 0.194f, 0.02f, 0.025f, 0.01f, 0xFFFFFF)
            }
            4 -> {   // gravata
                b(base, -0.085f, 0.925f, 0.168f, 0.11f, 0.05f, 0.03f, white, rz = 22f)
                b(base, 0.085f, 0.925f, 0.168f, 0.11f, 0.05f, 0.03f, white, rz = -22f)
                b(base, 0f, 0.9f, 0.176f, 0.075f, 0.07f, 0.04f, ncol)
                b(base, 0f, 0.77f, 0.178f, 0.07f, 0.2f, 0.025f, ncol)
                b(base, 0f, 0.64f, 0.178f, 0.1f, 0.08f, 0.025f, shadeC(ncol, 0.85f))
            }
            5 -> {   // gravata borboleta
                b(base, 0f, 0.905f, 0.172f, 0.05f, 0.06f, 0.04f, shadeC(ncol, 0.8f))
                b(base, -0.07f, 0.905f, 0.172f, 0.1f, 0.09f, 0.035f, ncol, rz = 10f)
                b(base, 0.07f, 0.905f, 0.172f, 0.1f, 0.09f, 0.035f, ncol, rz = -10f)
            }
            6 -> {   // coleira com sino
                b(base, 0f, 0.935f, 0f, 0.32f, 0.055f, 0.345f, ncol)
                b(base, 0f, 0.895f, 0.18f, 0.08f, 0.08f, 0.05f, gold)
                b(base, 0f, 0.885f, 0.206f, 0.03f, 0.04f, 0.01f, 0x8A6A20)
            }
            7 -> {   // lenço
                b(base, 0f, 0.935f, 0f, 0.5f, 0.09f, 0.35f, ncol)
                b(base, 0f, 0.86f, 0.178f, 0.22f, 0.07f, 0.03f, ncol)
                b(base, 0f, 0.8f, 0.178f, 0.12f, 0.06f, 0.03f, shadeC(ncol, 0.88f))
                b(base, 0f, 0.755f, 0.178f, 0.05f, 0.04f, 0.03f, shadeC(ncol, 0.8f))
            }
            8 -> {   // gargantilha
                b(base, 0f, 0.94f, 0f, 0.3f, 0.025f, 0.335f, 0x1E1E28)
                b(base, 0f, 0.9f, 0.172f, 0.045f, 0.055f, 0.025f, ncol)
                b(base, -0.01f, 0.915f, 0.186f, 0.015f, 0.02f, 0.01f, 0xFFFFFF)
            }
        }

        // ================= CABEÇA E ROSTO (tudo em hm) =================
        val ec = Look.EYE_COLORS[L[Look.EYEC]]
        val dk = 0x2A2230
        val fc = L[Look.FACE]
        val mask = fc == 8
        hb(0f, 1.31f, 0f, 0.78f, 0.78f, 0.78f, skin)
        hb(-0.4f, 1.27f, 0f, 0.05f, 0.12f, 0.1f, skinD)
        hb(0.4f, 1.27f, 0f, 0.05f, 0.12f, 0.1f, skinD)
        for (sd in intArrayOf(-1, 1)) {
            val s = sd.toFloat(); val ex = s * 0.2f; val ey = 1.25f
            val hidden = fc == 4 && sd < 0
            if (!hidden) {
                if (blink && L[Look.EYES] != 1) hb(ex, ey - 0.03f, 0.4f, 0.15f, 0.028f, 0.02f, dk)
                else when (L[Look.EYES]) {
                    0 -> {
                        hb(ex, ey, 0.398f, 0.17f, 0.23f, 0.02f, dk)
                        hb(ex, ey - 0.005f, 0.402f, 0.135f, 0.19f, 0.02f, ec)
                        hb(ex, ey - 0.065f, 0.404f, 0.125f, 0.06f, 0.02f, lite(ec, 0.45f))
                        hb(ex, ey, 0.406f, 0.07f, 0.12f, 0.02f, 0x1A1620)
                        hb(ex - 0.035f, ey + 0.06f, 0.41f, 0.055f, 0.055f, 0.02f, 0xFFFFFF)
                        hb(ex + 0.03f, ey - 0.05f, 0.41f, 0.025f, 0.025f, 0.02f, 0xFFFFFF)
                        hb(ex, ey + 0.115f, 0.4f, 0.19f, 0.03f, 0.02f, dk)
                    }
                    1 -> {
                        hb(ex, ey + 0.015f, 0.4f, 0.09f, 0.035f, 0.02f, dk)
                        hb(ex - 0.055f, ey - 0.01f, 0.4f, 0.06f, 0.035f, 0.02f, dk, rz = 25f)
                        hb(ex + 0.055f, ey - 0.01f, 0.4f, 0.06f, 0.035f, 0.02f, dk, rz = -25f)
                    }
                    2 -> {
                        hb(ex, ey, 0.4f, 0.11f, 0.14f, 0.02f, shadeC(ec, 0.35f))
                        hb(ex - 0.02f, ey + 0.035f, 0.406f, 0.045f, 0.045f, 0.02f, 0xFFFFFF)
                        hb(ex + 0.02f, ey - 0.035f, 0.406f, 0.02f, 0.02f, 0.02f, 0xFFFFFF)
                    }
                    3 -> {
                        hb(ex, ey - 0.02f, 0.398f, 0.17f, 0.1f, 0.02f, dk)
                        hb(ex, ey - 0.03f, 0.402f, 0.12f, 0.07f, 0.02f, ec)
                        hb(ex - 0.03f, ey - 0.01f, 0.408f, 0.035f, 0.035f, 0.02f, 0xFFFFFF)
                        hb(ex, ey + 0.04f, 0.404f, 0.2f, 0.03f, 0.02f, dk)
                    }
                    5 -> {   // sérios
                        hb(ex, ey - 0.02f, 0.398f, 0.18f, 0.13f, 0.02f, dk)
                        hb(ex, ey - 0.025f, 0.402f, 0.14f, 0.1f, 0.02f, ec)
                        hb(ex, ey - 0.025f, 0.406f, 0.06f, 0.09f, 0.02f, 0x1A1620)
                        hb(ex - 0.035f, ey + 0.005f, 0.41f, 0.035f, 0.035f, 0.02f, 0xFFFFFF)
                        hb(ex, ey + 0.065f, 0.408f, 0.21f, 0.045f, 0.02f, dk, rz = s * 18f)
                    }
                    6 -> {   // estrelas
                        hb(ex, ey, 0.398f, 0.19f, 0.25f, 0.02f, dk)
                        hb(ex, ey - 0.005f, 0.402f, 0.16f, 0.21f, 0.02f, ec)
                        hb(ex, ey + 0.02f, 0.41f, 0.1f, 0.03f, 0.02f, 0xFFFFFF)
                        hb(ex, ey + 0.02f, 0.41f, 0.03f, 0.1f, 0.02f, 0xFFFFFF)
                        hb(ex + 0.05f, ey - 0.06f, 0.41f, 0.045f, 0.045f, 0.02f, 0xFFFFFF)
                        hb(ex, ey + 0.125f, 0.4f, 0.21f, 0.03f, 0.02f, dk)
                    }
                    7 -> {   // gato
                        hb(ex, ey, 0.398f, 0.17f, 0.2f, 0.02f, dk)
                        hb(ex, ey - 0.005f, 0.402f, 0.14f, 0.17f, 0.02f, ec)
                        hb(ex, ey, 0.406f, 0.03f, 0.15f, 0.02f, 0x1A1620)
                        hb(ex - 0.04f, ey + 0.05f, 0.41f, 0.04f, 0.04f, 0.02f, 0xFFFFFF)
                        hb(ex, ey + 0.105f, 0.4f, 0.19f, 0.03f, 0.02f, dk)
                    }
                    8 -> {   // cansados
                        hb(ex, ey - 0.03f, 0.398f, 0.17f, 0.1f, 0.02f, dk)
                        hb(ex, ey - 0.04f, 0.402f, 0.12f, 0.07f, 0.02f, ec)
                        hb(ex, ey + 0.02f, 0.406f, 0.2f, 0.035f, 0.02f, dk)
                        hb(ex, ey - 0.11f, 0.4f, 0.14f, 0.02f, 0.02f, mixC(skin, 0x6A4A7A, 0.35f))
                    }
                    else -> {
                        hb(ex, ey, 0.398f, 0.19f, 0.25f, 0.02f, dk)
                        hb(ex, ey - 0.005f, 0.402f, 0.16f, 0.21f, 0.02f, ec)
                        hb(ex, ey - 0.06f, 0.404f, 0.15f, 0.09f, 0.02f, lite(ec, 0.5f))
                        hb(ex, ey, 0.406f, 0.08f, 0.13f, 0.02f, 0x1A1620)
                        hb(ex - 0.04f, ey + 0.07f, 0.41f, 0.07f, 0.07f, 0.02f, 0xFFFFFF)
                        hb(ex + 0.04f, ey - 0.06f, 0.41f, 0.035f, 0.035f, 0.02f, 0xFFFFFF)
                        hb(ex + 0.05f, ey + 0.07f, 0.41f, 0.02f, 0.02f, 0.02f, 0xFFFFFF)
                        hb(ex, ey + 0.125f, 0.4f, 0.21f, 0.03f, 0.02f, dk)
                    }
                }
                if (girl && L[Look.EYES] != 1 && L[Look.EYES] != 5) hb(ex + s * 0.105f, ey + 0.125f, 0.4f, 0.04f, 0.04f, 0.02f, dk)
            }
            if (L[Look.BLUSH] == 1) hb(s * 0.29f, 1.13f, 0.398f, 0.12f, 0.06f, 0.02f, 0xFF8CA0, a = 0.75f)
            if (!hidden) hb(s * 0.2f, 1.42f, 0.398f, if (girl) 0.1f else 0.12f, if (girl) 0.025f else 0.035f, 0.02f, shadeC(Look.hair(), 0.5f), rz = s * -7f)
            if (fc == 3) {
                val fr = mixC(skin, 0x8A4A2A, 0.5f)
                hb(s * 0.2f, 1.18f, 0.4f, 0.025f, 0.025f, 0.02f, fr)
                hb(s * 0.26f, 1.2f, 0.4f, 0.025f, 0.025f, 0.02f, fr)
                hb(s * 0.3f, 1.17f, 0.4f, 0.025f, 0.025f, 0.02f, fr)
            }
            if (fc == 7) {   // bigodes de gato
                for (i in 0 until 3) hb(s * 0.31f, 1.2f - i * 0.05f, 0.4f, 0.13f, 0.014f, 0.02f, 0x5A4A4A, rz = s * (-12f + i * 12f))
            }
        }
        if (!mask) {
            val mc = 0x8A3A3A
            when (L[Look.MOUTH]) {
                1 -> hb(0f, 1.09f, 0.398f, 0.06f, 0.022f, 0.02f, mc)
                2 -> { hb(-0.03f, 1.09f, 0.398f, 0.04f, 0.022f, 0.02f, mc); hb(0.03f, 1.09f, 0.398f, 0.04f, 0.022f, 0.02f, mc); hb(0f, 1.105f, 0.398f, 0.022f, 0.022f, 0.02f, mc) }
                3 -> { hb(0f, 1.07f, 0.398f, 0.1f, 0.07f, 0.02f, 0x7A2A3A); hb(0f, 1.045f, 0.402f, 0.06f, 0.025f, 0.02f, 0xFF8CA0); hb(0f, 1.1f, 0.402f, 0.08f, 0.02f, 0.02f, white) }
                4 -> {
                    hb(0f, 1.09f, 0.398f, 0.06f, 0.022f, 0.02f, mc); hb(-0.045f, 1.11f, 0.398f, 0.022f, 0.022f, 0.02f, mc); hb(0.045f, 1.11f, 0.398f, 0.022f, 0.022f, 0.02f, mc)
                    hb(-0.03f, 1.065f, 0.402f, 0.025f, 0.035f, 0.02f, white)
                }
                5 -> { hb(-0.03f, 1.085f, 0.398f, 0.045f, 0.022f, 0.02f, mc, rz = -20f); hb(0.03f, 1.085f, 0.398f, 0.045f, 0.022f, 0.02f, mc, rz = 20f); hb(0f, 1.1f, 0.398f, 0.02f, 0.03f, 0.02f, mc) }
                else -> {
                    hb(0f, 1.09f, 0.398f, 0.06f, 0.022f, 0.02f, mc)
                    hb(-0.045f, 1.11f, 0.398f, 0.022f, 0.022f, 0.02f, mc)
                    hb(0.045f, 1.11f, 0.398f, 0.022f, 0.022f, 0.02f, mc)
                }
            }
        }

        // ---------- cabelo ----------
        val hatId = L[Look.HAT]
        val hatCovers = hatId == 1 || hatId == 2 || hatId == 3 || hatId == 8 || hatId == 9 || hatId == 16 || hatId == 17 || hatId == 19 || hatId == 20 || hatId == 21 || hatId == 22 || hatId == 23
        drawHair(L[Look.HAIR], hatCovers, Look.hair(), Look.pal(Look.HATC), skin, t, move, sw)
        // Mechas selecionáveis em camadas voxel, mantendo a cor base do penteado.
        val hairAccent = Look.pal(Look.HAIR_ACCENT_COLOR)
        val hairAccentDark = shadeC(hairAccent, 0.72f)
        when (L[Look.HAIR_ACCENT]) {
            1 -> { hb(0f, 1.78f, 0.43f, 0.075f, 0.2f, 0.018f, hairAccent); hb(0f, 1.69f, 0.432f, 0.045f, 0.11f, 0.018f, lite(hairAccent, 0.3f)) }
            2 -> for (sd in intArrayOf(-1, 1)) { val x = sd * 0.17f; hb(x, 1.76f, 0.43f, 0.055f, 0.18f, 0.018f, hairAccent, rz = -sd * 8f); hb(x, 1.68f, 0.432f, 0.028f, 0.1f, 0.018f, lite(hairAccent, 0.25f)) }
            3 -> for (sd in intArrayOf(-1, 1)) { val x = sd * 0.31f; hb(x, 1.49f, 0.405f, 0.07f, 0.09f, 0.018f, hairAccent); hb(x * 0.92f, 1.44f, 0.405f, 0.05f, 0.035f, 0.018f, hairAccentDark) }
            4 -> { for (k in 0..4) hb(-0.22f + k * 0.11f, 1.72f - (k % 2) * 0.035f, 0.432f, 0.035f, 0.13f, 0.018f, if (k % 2 == 0) hairAccent else hairAccentDark, rz = (k - 2) * 7f) }
            5 -> { hb(-0.1f, 1.83f, 0.19f, 0.3f, 0.045f, 0.23f, hairAccent, rz = -8f); hb(0.1f, 1.84f, 0.19f, 0.25f, 0.035f, 0.19f, lite(hairAccent, 0.28f), rz = 8f) }
            6 -> { hb(0.34f, 1.61f, 0.36f, 0.05f, 0.22f, 0.02f, hairAccent, rz = -12f); hb(0.35f, 1.52f, 0.36f, 0.03f, 0.1f, 0.02f, lite(hairAccent, 0.3f), rz = -12f) }
            7 -> for (k in 0..5) hb(-0.25f + k * 0.1f, 1.78f - (k % 2) * 0.035f, 0.435f, 0.028f, 0.16f, 0.02f, if (k % 2 == 0) hairAccent else hairAccentDark, rz = (k - 2) * 5f)
            8 -> { hb(-0.09f, 1.79f, 0.435f, 0.025f, 0.13f, 0.018f, hairAccent); hb(0.09f, 1.79f, 0.435f, 0.025f, 0.13f, 0.018f, hairAccent) }
        }
        // Presilhas e micromechas em relevo: detalhes extra acompanham a cabeça e giram com ela.
        if (L[Look.HAIR] != 13) {
            val clip = Look.pal(Look.HATC)
            for (sd in intArrayOf(-1, 1)) {
                val x = sd * 0.34f
                hb(x, 1.66f, 0.414f, 0.075f, 0.035f, 0.025f, shadeC(clip, 0.68f), rz = -sd * 18f)
                hb(x, 1.67f, 0.43f, 0.055f, 0.018f, 0.018f, lite(clip, 0.38f), rz = -sd * 18f)
                hb(sd * 0.39f, 1.55f, 0.405f, 0.025f, 0.11f, 0.014f, lite(Look.hair(), 0.32f), rz = sd * 8f)
            }
        }
        // Mechas metalizadas finas sobre o topo, com brilho e sombra para volume de material.
        val crownAccent = Look.pal(Look.HAIR_ACCENT_COLOR)
        for (i in 0..3) {
            val x = -0.21f + i * 0.14f
            hb(x, 1.81f + (i % 2) * 0.025f, 0.29f, 0.028f, 0.012f, 0.16f,
                if (i % 2 == 0) lite(crownAccent, 0.24f) else shadeC(crownAccent, 0.78f), rz = (i - 1) * 4f)
        }
        // cabelo volumoso empurra o chapéu pra cima (sem atravessar)
        val hatLift = if (hatId == 0 || hatCovers) 0f else hairLift(L[Look.HAIR])
        if (hatLift != 0f) Matrix.translateM(hm, 0, 0f, hatLift, 0f)
        // ---------- chapéus e enfeites ----------
        val hcl = Look.pal(Look.HATC)
        when (L[Look.HAT]) {
            1 -> {
                hb(0f, 1.745f, 0f, 1.25f, 0.05f, 1.25f, shadeC(hcl, 0.85f))
                hb(0f, 1.85f, 0f, 0.66f, 0.2f, 0.66f, hcl)
                hb(0f, 2.03f, 0f, 0.5f, 0.2f, 0.5f, hcl)
                hb(0f, 2.2f, 0f, 0.34f, 0.2f, 0.34f, hcl)
                piv(t3, hm, 0f, 2.3f, 0f, rx = -20f + sin(t * 2f) * 4f + move * 8f)
                b(t3, 0f, 0.1f, 0f, 0.2f, 0.22f, 0.2f, hcl)
                hb(0f, 1.79f, 0f, 0.68f, 0.07f, 0.68f, gold)
                hb(0f, 1.79f, 0.345f, 0.1f, 0.1f, 0.02f, 0xFFF0A0)
            }
            2 -> {
                hb(0f, 1.76f, 0f, 0.9f, 0.12f, 0.9f, lite(hcl, 0.35f))
                hb(0f, 1.9f, 0f, 0.84f, 0.18f, 0.84f, hcl)
                hb(0f, 1.9f, 0f, 0.855f, 0.04f, 0.855f, lite(hcl, 0.5f))
                hb(0f, 2.03f, 0f, 0.66f, 0.12f, 0.66f, hcl)
                hb(0f, 2.15f, 0f, 0.2f, 0.2f, 0.2f, white)
            }
            3 -> {
                hb(0f, 1.8f, 0f, 0.88f, 0.18f, 0.88f, hcl)
                hb(0f, 1.9f, 0f, 0.6f, 0.04f, 0.6f, shadeC(hcl, 0.9f))
                hb(0f, 1.93f, 0f, 0.07f, 0.04f, 0.07f, lite(hcl, 0.3f))
                hb(0f, 1.725f, 0.52f, 0.62f, 0.045f, 0.34f, shadeC(hcl, 0.8f), rx = 10f)
                hb(0f, 1.82f, 0.445f, 0.12f, 0.08f, 0.02f, 0xFFFFFF)
            }
            4 -> {   // coroa (assenta no topo da cabeça/cabelo)
                val R = 0.47f; val cy = 1.755f
                hb(0f, cy, R, 0.96f, 0.11f, 0.06f, gold)
                hb(0f, cy, -R, 0.96f, 0.11f, 0.06f, gold)
                hb(R, cy, 0f, 0.06f, 0.11f, 0.9f, gold)
                hb(-R, cy, 0f, 0.06f, 0.11f, 0.9f, gold)
                for (i in 0 until 5) {
                    val x = -0.36f + i * 0.18f
                    val hh = if (i % 2 == 0) 0.2f else 0.12f
                    hb(x, cy + 0.055f + hh / 2f, R, 0.1f, hh, 0.06f, gold)
                    hb(x, cy + 0.055f + 0.07f, -R, 0.1f, 0.14f, 0.06f, gold)
                }
                for (i in 0 until 3) {
                    val z = -0.24f + i * 0.24f
                    hb(R, cy + 0.12f, z, 0.06f, 0.14f, 0.1f, gold)
                    hb(-R, cy + 0.12f, z, 0.06f, 0.14f, 0.1f, gold)
                }
                hb(0f, cy, R + 0.035f, 0.09f, 0.08f, 0.02f, hcl)
                hb(-0.25f, cy, R + 0.035f, 0.055f, 0.055f, 0.02f, lite(hcl, 0.3f))
                hb(0.25f, cy, R + 0.035f, 0.055f, 0.055f, 0.02f, lite(hcl, 0.3f))
                hb(0f, cy + 0.3f, R, 0.05f, 0.05f, 0.05f, hcl)
            }
            5 -> {
                for (sd in intArrayOf(-1, 1)) {
                    val s = sd.toFloat()
                    hb(s * 0.26f, 1.86f, -0.02f, 0.22f, 0.22f, 0.1f, hcl, rz = -s * 14f)
                    hb(s * 0.3f, 1.99f, -0.02f, 0.12f, 0.12f, 0.1f, hcl, rz = -s * 20f)
                    hb(s * 0.26f, 1.85f, 0.035f, 0.12f, 0.14f, 0.02f, 0xFFB0C8, rz = -s * 14f)
                }
            }
            6 -> {
                for (sd in intArrayOf(-1, 1)) {
                    val s = sd.toFloat()
                    hb(s * 0.2f, 2.0f, -0.03f, 0.15f, 0.5f, 0.1f, hcl, rz = -s * 10f)
                    hb(s * 0.235f, 2.3f, -0.03f, 0.13f, 0.14f, 0.1f, hcl, rz = -s * 20f)
                    hb(s * 0.2f, 2.0f, 0.025f, 0.08f, 0.4f, 0.02f, 0xFFB0C8, rz = -s * 10f)
                }
            }
            7 -> {
                piv(t3, hm, 0.28f, 1.78f, 0.1f, rz = -12f)
                b(t3, 0f, 0f, 0f, 0.12f, 0.12f, 0.12f, hcl)
                b(t3, 0.15f, 0.02f, 0f, 0.22f, 0.18f, 0.1f, hcl, rz = 12f)
                b(t3, -0.15f, 0.02f, 0f, 0.22f, 0.18f, 0.1f, hcl, rz = -12f)
                b(t3, 0.15f, 0.06f, 0.055f, 0.14f, 0.04f, 0.02f, lite(hcl, 0.5f))
                b(t3, -0.15f, 0.06f, 0.055f, 0.14f, 0.04f, 0.02f, lite(hcl, 0.5f))
                b(t3, 0.05f, -0.14f, 0f, 0.07f, 0.18f, 0.07f, shadeC(hcl, 0.9f), rz = 10f)
                b(t3, -0.05f, -0.14f, 0f, 0.07f, 0.18f, 0.07f, shadeC(hcl, 0.9f), rz = -10f)
            }
            8 -> {
                val iron = mixC(0xC5CEDA, hcl, 0.3f)
                hb(0f, 1.82f, 0f, 0.9f, 0.28f, 0.9f, iron)
                hb(0f, 1.69f, 0f, 0.92f, 0.05f, 0.92f, gold)
                hb(0f, 2.08f, -0.05f, 0.1f, 0.22f, 0.34f, hcl)
                hb(0f, 1.98f, 0f, 0.12f, 0.06f, 0.2f, gold)
                hb(-0.455f, 1.6f, 0f, 0.05f, 0.18f, 0.4f, iron)
                hb(0.455f, 1.6f, 0f, 0.05f, 0.18f, 0.4f, iron)
            }
            9 -> {
                hb(0f, 1.745f, 0f, 1.34f, 0.04f, 1.34f, 0xE8C877)
                hb(0f, 1.86f, 0f, 0.68f, 0.2f, 0.68f, 0xEFD488)
                hb(0f, 1.79f, 0f, 0.7f, 0.06f, 0.7f, hcl)
            }
            10 -> {
                piv(t3, hm, 0.3f, 1.74f, 0.2f)
                b(t3, 0f, 0f, 0f, 0.09f, 0.09f, 0.06f, 0xFFD35C)
                b(t3, 0f, 0.1f, 0f, 0.1f, 0.1f, 0.05f, hcl)
                b(t3, 0f, -0.1f, 0f, 0.1f, 0.1f, 0.05f, hcl)
                b(t3, 0.1f, 0f, 0f, 0.1f, 0.1f, 0.05f, hcl)
                b(t3, -0.1f, 0f, 0f, 0.1f, 0.1f, 0.05f, hcl)
                b(t3, 0.075f, 0.075f, 0f, 0.09f, 0.09f, 0.045f, lite(hcl, 0.25f), rz = 45f)
                b(t3, -0.075f, 0.075f, 0f, 0.09f, 0.09f, 0.045f, lite(hcl, 0.25f), rz = 45f)
                b(t3, 0.075f, -0.075f, 0f, 0.09f, 0.09f, 0.045f, lite(hcl, 0.25f), rz = 45f)
                b(t3, -0.075f, -0.075f, 0f, 0.09f, 0.09f, 0.045f, lite(hcl, 0.25f), rz = 45f)
                b(t3, 0.13f, -0.12f, -0.01f, 0.1f, 0.06f, 0.04f, 0x6CC070, rz = -30f)
            }
            11 -> {  // faixa ninja
                hb(0f, 1.5f, 0.42f, 0.82f, 0.1f, 0.05f, hcl)
                hb(-0.43f, 1.5f, 0f, 0.05f, 0.1f, 0.82f, hcl)
                hb(0.43f, 1.5f, 0f, 0.05f, 0.1f, 0.82f, hcl)
                hb(0f, 1.5f, -0.43f, 0.82f, 0.1f, 0.05f, hcl)
                hb(0f, 1.5f, 0.45f, 0.34f, 0.09f, 0.02f, 0xC5CEDA)
                hb(0f, 1.5f, 0.462f, 0.12f, 0.05f, 0.01f, 0x8A94A4)
                piv(t3, hm, 0f, 1.5f, -0.45f, rx = 20f + sin(t * 4f) * 6f + move * 20f)
                b(t3, -0.06f, -0.16f, 0f, 0.1f, 0.34f, 0.03f, hcl)
                b(t3, 0.06f, -0.2f, 0f, 0.1f, 0.42f, 0.03f, shadeC(hcl, 0.85f))
            }
            12 -> {  // auréola
                val fy = 2.02f + sin(t * 2.2f) * 0.03f
                val hl2 = lite(0xFFE070, 0.2f)
                hb(0f, fy, 0.24f, 0.56f, 0.045f, 0.07f, hl2)
                hb(0f, fy, -0.24f, 0.56f, 0.045f, 0.07f, hl2)
                hb(0.24f, fy, 0f, 0.07f, 0.045f, 0.4f, hl2)
                hb(-0.24f, fy, 0f, 0.07f, 0.045f, 0.4f, hl2)
            }
            13 -> {  // chifrinhos
                for (sd in intArrayOf(-1, 1)) {
                    val s = sd.toFloat()
                    hb(s * 0.22f, 1.82f, 0.02f, 0.11f, 0.16f, 0.11f, hcl, rz = -s * 14f)
                    hb(s * 0.255f, 1.93f, 0.02f, 0.07f, 0.1f, 0.07f, lite(hcl, 0.3f), rz = -s * 26f)
                }
            }
            14 -> {  // orelhas de raposa
                for (sd in intArrayOf(-1, 1)) {
                    val s = sd.toFloat()
                    hb(s * 0.27f, 1.88f, -0.02f, 0.2f, 0.26f, 0.1f, hcl, rz = -s * 10f)
                    hb(s * 0.285f, 2.03f, -0.02f, 0.1f, 0.12f, 0.1f, white, rz = -s * 14f)
                    hb(s * 0.27f, 1.87f, 0.035f, 0.1f, 0.16f, 0.02f, 0xFFB0C8, rz = -s * 10f)
                }
            }
            15 -> {  // tiara
                hb(0f, 1.74f, 0.4f, 0.62f, 0.05f, 0.05f, 0xE8E8F4)
                for (i in 0 until 3) hb((i - 1) * 0.16f, 1.8f + (if (i == 1) 0.04f else 0f), 0.4f, 0.06f, 0.1f + (if (i == 1) 0.08f else 0f), 0.04f, 0xE8E8F4)
                hb(0f, 1.76f, 0.43f, 0.07f, 0.07f, 0.02f, hcl)
                hb(0f, 1.9f, 0.43f, 0.04f, 0.04f, 0.02f, lite(hcl, 0.4f))
            }
            16 -> {  // boina
                hb(0.04f, 1.77f, 0f, 0.92f, 0.1f, 0.92f, hcl)
                hb(0.08f, 1.85f, 0f, 0.8f, 0.1f, 0.8f, hcl)
                hb(0.1f, 1.91f, 0f, 0.5f, 0.05f, 0.5f, shadeC(hcl, 0.9f))
                hb(0.1f, 1.96f, 0f, 0.07f, 0.07f, 0.07f, shadeC(hcl, 0.7f))
            }
            17 -> {  // kasa (chapéu cônico)
                val st = 0xE8C877
                hb(0f, 1.78f, 0f, 1.34f, 0.05f, 1.34f, st)
                hb(0f, 1.84f, 0f, 1.02f, 0.06f, 1.02f, shadeC(st, 0.95f))
                hb(0f, 1.9f, 0f, 0.72f, 0.06f, 0.72f, st)
                hb(0f, 1.96f, 0f, 0.44f, 0.06f, 0.44f, shadeC(st, 0.95f))
                hb(0f, 2.02f, 0f, 0.16f, 0.07f, 0.16f, st)
                hb(0f, 1.74f, 0.2f, 0.5f, 0.03f, 0.03f, hcl)
            }
            18 -> {  // fones de ouvido
                hb(0f, 1.78f, 0f, 0.9f, 0.05f, 0.1f, hcl)
                hb(-0.45f, 1.52f, 0f, 0.05f, 0.5f, 0.1f, hcl)
                hb(0.45f, 1.52f, 0f, 0.05f, 0.5f, 0.1f, hcl)
                hb(-0.49f, 1.27f, 0f, 0.1f, 0.26f, 0.26f, shadeC(hcl, 0.8f))
                hb(0.49f, 1.27f, 0f, 0.1f, 0.26f, 0.26f, shadeC(hcl, 0.8f))
                hb(-0.54f, 1.27f, 0f, 0.03f, 0.16f, 0.16f, lite(hcl, 0.4f))
                hb(0.54f, 1.27f, 0f, 0.03f, 0.16f, 0.16f, lite(hcl, 0.4f))
            }
            19 -> {  // capuz
                hb(0f, 1.62f, -0.02f, 0.94f, 0.3f, 0.9f, hcl)
                hb(0f, 1.3f, -0.44f, 0.94f, 0.88f, 0.1f, hcl)
                hb(-0.46f, 1.34f, -0.1f, 0.1f, 0.78f, 0.7f, hcl)
                hb(0.46f, 1.34f, -0.1f, 0.1f, 0.78f, 0.7f, hcl)
                hb(0f, 1.46f, 0.455f, 0.94f, 0.04f, 0.04f, lite(hcl, 0.35f))
                hb(0f, 1.0f, -0.4f, 0.94f, 0.1f, 0.28f, shadeC(hcl, 0.85f))
            }
            21 -> {  // cartola
                hb(0f, 1.745f, 0f, 1.0f, 0.06f, 1.0f, shadeC(hcl, 0.8f))
                hb(0f, 1.99f, 0f, 0.58f, 0.46f, 0.58f, hcl)
                hb(0f, 1.8f, 0f, 0.6f, 0.09f, 0.6f, lite(hcl, 0.5f))
                hb(0f, 2.225f, 0f, 0.6f, 0.03f, 0.6f, shadeC(hcl, 0.85f))
                hb(0.2f, 1.8f, 0.3f, 0.1f, 0.1f, 0.02f, gold)
            }
            22 -> {  // chapéu de chef
                hb(0f, 1.78f, 0f, 0.84f, 0.12f, 0.84f, lite(hcl, 0.7f))
                hb(0f, 1.98f, 0f, 0.98f, 0.32f, 0.98f, lite(hcl, 0.85f))
                hb(0f, 2.18f, 0f, 0.8f, 0.14f, 0.8f, lite(hcl, 0.85f))
                hb(0f, 1.84f, 0.43f, 0.84f, 0.025f, 0.02f, shadeC(hcl, 0.8f))
            }
            23 -> {  // capacete viking
                val iron = mixC(0xC5CEDA, hcl, 0.3f); val bone = 0xF2EAD0
                hb(0f, 1.78f, 0f, 0.9f, 0.2f, 0.9f, iron)
                hb(0f, 1.9f, 0f, 0.7f, 0.06f, 0.7f, shadeC(iron, 0.9f))
                hb(0f, 1.74f, 0.46f, 0.14f, 0.18f, 0.03f, shadeC(iron, 0.85f))
                for (sd in intArrayOf(-1, 1)) {
                    val s = sd.toFloat()
                    hb(s * 0.5f, 1.8f, 0f, 0.14f, 0.14f, 0.14f, bone)
                    hb(s * 0.6f, 1.93f, 0f, 0.13f, 0.18f, 0.13f, bone, rz = -s * 14f)
                    hb(s * 0.66f, 2.1f, 0f, 0.1f, 0.18f, 0.1f, bone, rz = -s * 28f)
                }
            }
            20 -> {  // touca de maid
                hb(0f, 1.74f, 0.02f, 0.82f, 0.06f, 0.5f, white)
                for (i in -2..2) hb(i * 0.15f, 1.78f, 0.27f, 0.1f, 0.05f, 0.05f, white)
                hb(0f, 1.75f, 0.28f, 0.76f, 0.03f, 0.04f, lite(hcl, 0.6f))
                hb(-0.3f, 1.74f, -0.1f, 0.1f, 0.04f, 0.5f, shadeC(white, 0.92f))
                hb(0.3f, 1.74f, -0.1f, 0.1f, 0.04f, 0.5f, shadeC(white, 0.92f))
            }
        }


            24 -> { // chifres infernais: base óssea, ponta afiada e brilho interno
                for (sd in intArrayOf(-1, 1)) {
                    val s = sd.toFloat()
                    hb(s * 0.25f, 1.86f, -0.01f, 0.16f, 0.22f, 0.15f, shadeC(hcl, 0.68f), rz = -s * 22f)
                    hb(s * 0.32f, 2.02f, -0.015f, 0.12f, 0.2f, 0.12f, hcl, rz = -s * 34f)
                    hb(s * 0.39f, 2.16f, 0f, 0.065f, 0.15f, 0.065f, lite(hcl, 0.42f), rz = -s * 43f)
                    hb(s * 0.25f, 1.79f, 0.09f, 0.09f, 0.08f, 0.06f, 0xD83D58)
                }
            }
            25 -> { // tiara lunar com crescente e gemas
                hb(0f, 1.77f, 0.405f, 0.7f, 0.045f, 0.04f, hcl)
                hb(0f, 1.94f, 0.405f, 0.05f, 0.25f, 0.045f, lite(hcl, 0.35f))
                hb(-0.1f, 1.91f, 0.405f, 0.05f, 0.15f, 0.045f, hcl, rz = -25f)
                hb(0.1f, 1.91f, 0.405f, 0.05f, 0.15f, 0.045f, hcl, rz = 25f)
                hb(0f, 2.08f, 0.41f, 0.09f, 0.09f, 0.045f, 0xA9DEF9)
                hb(-0.27f, 1.81f, 0.41f, 0.07f, 0.07f, 0.04f, 0xF1C0E8)
                hb(0.27f, 1.81f, 0.41f, 0.07f, 0.07f, 0.04f, 0xF1C0E8)
            }
            26 -> { // fones cyberpunk com antenas e módulos luminosos
                hb(0f, 1.79f, 0f, 0.82f, 0.07f, 0.09f, shadeC(hcl, 0.62f))
                hb(-0.45f, 1.52f, 0f, 0.1f, 0.48f, 0.17f, hcl)
                hb(0.45f, 1.52f, 0f, 0.1f, 0.48f, 0.17f, hcl)
                hb(-0.5f, 1.27f, 0f, 0.16f, 0.25f, 0.27f, shadeC(hcl, 0.7f))
                hb(0.5f, 1.27f, 0f, 0.16f, 0.25f, 0.27f, shadeC(hcl, 0.7f))
                hb(-0.5f, 1.27f, 0.145f, 0.09f, 0.12f, 0.025f, 0x48BFE3)
                hb(0.5f, 1.27f, 0.145f, 0.09f, 0.12f, 0.025f, 0xFF6F91)
                hb(-0.32f, 1.92f, -0.02f, 0.04f, 0.18f, 0.04f, hcl, rz = -18f)
                hb(0.32f, 1.92f, -0.02f, 0.04f, 0.18f, 0.04f, hcl, rz = 18f)
            }
            27 -> { // capuz volumoso com orelhas e forro
                hb(0f, 1.62f, -0.04f, 0.96f, 0.3f, 0.9f, hcl)
                hb(0f, 1.36f, -0.39f, 0.94f, 0.7f, 0.16f, hcl)
                hb(-0.45f, 1.36f, -0.04f, 0.12f, 0.7f, 0.78f, hcl)
                hb(0.45f, 1.36f, -0.04f, 0.12f, 0.7f, 0.78f, hcl)
                for (sd in intArrayOf(-1, 1)) {
                    val s = sd.toFloat()
                    hb(s * 0.28f, 1.91f, -0.02f, 0.18f, 0.25f, 0.12f, hcl, rz = -s * 12f)
                    hb(s * 0.28f, 1.92f, 0.05f, 0.09f, 0.13f, 0.025f, 0xF1C0E8, rz = -s * 12f)
                }
                hb(0f, 1.44f, 0.43f, 0.58f, 0.035f, 0.025f, lite(hcl, 0.35f))
            }
            28 -> { // coroa floral: pétalas, folhas e gema central
                for (i in 0 until 9) {
                    val x = (i - 4) * 0.095f
                    hb(x, 1.78f + (i % 2) * 0.025f, 0.4f, 0.13f, 0.09f, 0.07f, if (i % 2 == 0) 0xF1C0E8 else 0xFFB4A2, rz = (i - 4) * 9f)
                    if (i % 2 == 0) hb(x, 1.73f, 0.39f, 0.05f, 0.08f, 0.04f, 0x6CC070, rz = (i - 4) * 8f)
                }
                hb(0f, 1.82f, 0.45f, 0.09f, 0.09f, 0.03f, 0xFFD35C)
            }
            29 -> { // halo cristalino flutuante com quatro fragmentos
                val fy = 2.03f + sin(t * 2.5f) * 0.035f
                hb(0f, fy, 0.22f, 0.52f, 0.035f, 0.06f, hcl)
                hb(0f, fy, -0.22f, 0.52f, 0.035f, 0.06f, hcl)
                hb(0.22f, fy, 0f, 0.06f, 0.035f, 0.38f, hcl)
                hb(-0.22f, fy, 0f, 0.06f, 0.035f, 0.38f, hcl)
                for (sd in intArrayOf(-1, 1)) hb(sd * 0.34f, fy + 0.04f, 0.02f, 0.07f, 0.11f, 0.07f, lite(hcl, 0.48f), rz = sd * 24f)
                hb(0f, fy + 0.04f, 0.24f, 0.08f, 0.08f, 0.035f, 0xFFFFFF)
            }
            30 -> { // visor tático, lentes duplas e faixa lateral
                hb(0f, 1.48f, 0.445f, 0.78f, 0.18f, 0.06f, shadeC(hcl, 0.55f))
                hb(-0.18f, 1.49f, 0.482f, 0.29f, 0.105f, 0.025f, 0x48BFE3)
                hb(0.18f, 1.49f, 0.482f, 0.29f, 0.105f, 0.025f, 0x48BFE3)
                hb(0f, 1.49f, 0.498f, 0.06f, 0.13f, 0.018f, lite(hcl, 0.4f))
                hb(-0.42f, 1.47f, 0.38f, 0.07f, 0.16f, 0.05f, hcl)
                hb(0.42f, 1.47f, 0.38f, 0.07f, 0.16f, 0.05f, hcl)
            }
            31 -> { // chifres neon com anéis e ponta luminosa
                for (sd in intArrayOf(-1, 1)) {
                    val s = sd.toFloat()
                    hb(s * 0.23f, 1.84f, 0f, 0.13f, 0.2f, 0.12f, shadeC(hcl, 0.7f), rz = -s * 18f)
                    hb(s * 0.29f, 1.98f, 0f, 0.1f, 0.2f, 0.1f, hcl, rz = -s * 30f)
                    hb(s * 0.35f, 2.13f, 0f, 0.06f, 0.13f, 0.06f, 0x48BFE3, rz = -s * 42f)
                    hb(s * 0.27f, 1.92f, 0.07f, 0.14f, 0.035f, 0.13f, 0xFF6F91)
                }
            }
        }

        // ===== ACABAMENTO INDIVIDUAL DE CHAPÉUS E ACESSÓRIOS DE CABEÇA =====
        // Filetes, rebites, emblemas e painéis seguem o acessório escolhido.
        val hatHi = lite(hcl, 0.38f)
        val hatLo = shadeC(hcl, 0.68f)
        when (hatId) {
            1, 2, 3, 16, 17, 21, 22 -> {
                // Banda em camadas + fivela com sombra e brilho para leitura 3D.
                hb(0f, 1.82f, 0.365f, 0.52f, 0.055f, 0.025f, hatLo)
                hb(0f, 1.83f, 0.382f, 0.46f, 0.025f, 0.018f, hatHi)
                hb(0f, 1.83f, 0.397f, 0.095f, 0.07f, 0.018f, gold)
                hb(0f, 1.83f, 0.41f, 0.045f, 0.035f, 0.012f, hcl)
                for (sd in intArrayOf(-1, 1)) {
                    hb(sd * 0.29f, 1.83f, 0.378f, 0.025f, 0.025f, 0.016f, 0xE7D9B4)
                }
            }
            4, 8, 23 -> {
                // Armadura/coroa: placas segmentadas, rebites e crista central.
                for (sd in intArrayOf(-1, 1)) {
                    hb(sd * 0.29f, 1.82f, 0.43f, 0.12f, 0.1f, 0.035f, hatLo, rz = -sd * 12f)
                    hb(sd * 0.29f, 1.83f, 0.452f, 0.065f, 0.045f, 0.018f, 0xF5D77B)
                    hb(sd * 0.42f, 1.72f, 0.39f, 0.045f, 0.045f, 0.025f, 0xD8DEE8)
                }
                hb(0f, 1.98f, 0.39f, 0.08f, 0.19f, 0.04f, hatHi)
                hb(0f, 2.08f, 0.4f, 0.035f, 0.08f, 0.025f, 0xFFFFFF)
            }
            24, 31 -> {
                for (sd in intArrayOf(-1, 1)) {
                    hb(sd * 0.29f, 1.96f, 0.08f, 0.07f, 0.04f, 0.06f, 0xFF6F91)
                    hb(sd * 0.32f, 2.06f, 0.02f, 0.035f, 0.07f, 0.035f, 0xFFFFFF)
                }
            }
            25, 28, 29 -> {
                hb(-0.18f, 1.82f, 0.44f, 0.045f, 0.045f, 0.025f, 0xFFFFFF)
                hb(0.18f, 1.82f, 0.44f, 0.045f, 0.045f, 0.025f, 0xFFFFFF)
            }
            26, 30 -> {
                hb(-0.5f, 1.27f, 0.16f, 0.035f, 0.1f, 0.025f, 0xFFFFFF)
                hb(0.5f, 1.27f, 0.16f, 0.035f, 0.1f, 0.025f, 0xFFFFFF)
            }
            27 -> {
                hb(-0.3f, 1.42f, 0.44f, 0.025f, 0.42f, 0.025f, hatHi)
                hb(0.3f, 1.42f, 0.44f, 0.025f, 0.42f, 0.025f, hatHi)
            }
            5, 6, 14, 15, 20 -> {
                // Acessórios fofos: costura central, pequenos pingentes e borda contrastante.
                hb(0f, 1.77f, 0.36f, 0.52f, 0.025f, 0.025f, hatHi)
                hb(0f, 1.78f, 0.38f, 0.08f, 0.07f, 0.025f, 0xFFF0F5)
                hb(0f, 1.78f, 0.4f, 0.035f, 0.035f, 0.015f, 0xFF8FB5)
                for (sd in intArrayOf(-1, 1)) {
                    hb(sd * 0.36f, 1.76f, 0.34f, 0.035f, 0.035f, 0.025f, 0xFFFFFF)
                }
            }
            10, 12, 13 -> {
                // Cristal, halo ou chifres: núcleo luminoso e facetas com contraste.
                hb(0f, 2.02f, 0.13f, 0.045f, 0.15f, 0.04f, hatHi)
                hb(0f, 2.08f, 0.16f, 0.025f, 0.06f, 0.025f, 0xFFFFFF)
                for (sd in intArrayOf(-1, 1)) {
                    hb(sd * 0.25f, 1.88f, 0.12f, 0.04f, 0.09f, 0.035f, hatLo, rz = -sd * 18f)
                }
            }
            18 -> {
                // Fones: anéis exteriores e núcleo luminoso nos dois earcups.
                for (sd in intArrayOf(-1, 1)) {
                    hb(sd * 0.54f, 1.27f, 0.09f, 0.045f, 0.19f, 0.19f, hatHi)
                    hb(sd * 0.55f, 1.27f, 0.195f, 0.025f, 0.1f, 0.1f, 0x73E7FF)
                    hb(sd * 0.55f, 1.27f, 0.211f, 0.012f, 0.045f, 0.045f, 0xFFFFFF)
                }
            }
        }
        if (hatLift != 0f) Matrix.translateM(hm, 0, 0f, -hatLift, 0f)
        // ---------- rosto: acessórios por cima (lentes translúcidas por último) ----------
        // ===== ACABAMENTO DE ÓCULOS, MÁSCARAS E DETALHES FACIAIS =====
        // Camadas de aro, lentes e costuras em volumes separados para dar profundidade.
        val faceAccent = Look.pal(Look.HATC)
        if (fc == 1 || fc == 2 || fc == 3 || fc == 4 || fc == 7 || fc == 9) {
            for (sd in intArrayOf(-1, 1)) {
                hb(sd * 0.19f, 1.29f, 0.43f, 0.025f, 0.035f, 0.02f, lite(faceAccent, 0.28f))
                hb(sd * 0.19f, 1.31f, 0.444f, 0.012f, 0.012f, 0.01f, 0xFFFFFF)
            }
            hb(0f, 1.3f, 0.43f, 0.09f, 0.018f, 0.018f, shadeC(faceAccent, 0.7f))
        }
        if (fc == 8) { // máscara: painéis laterais, costura central e respiradores
            hb(0f, 1.04f, 0.452f, 0.045f, 0.22f, 0.018f, shadeC(faceAccent, 0.72f))
            for (sd in intArrayOf(-1, 1)) {
                hb(sd * 0.22f, 1.04f, 0.455f, 0.08f, 0.09f, 0.018f, lite(faceAccent, 0.24f))
                hb(sd * 0.29f, 1.04f, 0.47f, 0.035f, 0.05f, 0.012f, 0xDDE4EE)
            }
        }
        if (fc == 11 || fc == 12) { // marcas faciais e pequenos brilhos
            hb(-0.24f, 1.27f, 0.425f, 0.06f, 0.025f, 0.018f, lite(faceAccent, 0.32f), rz = -16f)
            hb(-0.2f, 1.24f, 0.43f, 0.025f, 0.012f, 0.01f, 0xFFFFFF)
        }
        if (fc == 4) {
            hb(-0.2f, 1.25f, 0.415f, 0.26f, 0.22f, 0.03f, 0x1E1E28)
            hb(-0.2f, 1.25f, 0.432f, 0.18f, 0.02f, 0.01f, 0x3A3A48)
            hb(0f, 1.37f, 0.41f, 0.82f, 0.03f, 0.03f, 0x1E1E28, rz = 14f)
        }
        if (fc == 5) {   // cicatriz sobre o olho direito
            hb(0.22f, 1.33f, 0.41f, 0.035f, 0.38f, 0.02f, 0xC06070, rz = -14f)
            hb(0.19f, 1.4f, 0.412f, 0.1f, 0.02f, 0.01f, 0xE8A0A8, rz = -14f)
            hb(0.25f, 1.27f, 0.412f, 0.1f, 0.02f, 0.01f, 0xE8A0A8, rz = -14f)
        }
        if (fc == 6) {   // curativo no nariz
            hb(0f, 1.17f, 0.41f, 0.2f, 0.07f, 0.02f, 0xF2D0A0, rz = 18f)
            hb(0f, 1.17f, 0.422f, 0.07f, 0.06f, 0.01f, 0xE0B080, rz = 18f)
        }
        if (fc == 8) {   // máscara
            hb(0f, 1.04f, 0.41f, 0.72f, 0.3f, 0.03f, white)
            hb(0f, 1.1f, 0.43f, 0.72f, 0.02f, 0.01f, 0xDDE4EE)
            hb(0f, 1.04f, 0.43f, 0.72f, 0.02f, 0.01f, 0xDDE4EE)
            hb(0f, 0.98f, 0.43f, 0.72f, 0.02f, 0.01f, 0xDDE4EE)
            hb(-0.405f, 1.1f, 0.15f, 0.025f, 0.05f, 0.5f, 0xE6E6EE)
            hb(0.405f, 1.1f, 0.15f, 0.025f, 0.05f, 0.5f, 0xE6E6EE)
        }
        if (fc == 10) {  // lágrima
            hb(-0.285f, 1.1f, 0.41f, 0.035f, 0.1f, 0.02f, 0x8AD0FF, a = 0.9f)
            hb(-0.285f, 1.03f, 0.41f, 0.05f, 0.05f, 0.02f, 0x8AD0FF, a = 0.9f)
        }
        if (fc == 11) {   // monóculo (olho direito do boneco)
            val gx = -0.2f; val gy = 1.25f; val fr = gold
            hb(gx, gy + 0.14f, 0.41f, 0.3f, 0.03f, 0.025f, fr); hb(gx, gy - 0.14f, 0.41f, 0.3f, 0.03f, 0.025f, fr)
            hb(gx - 0.15f, gy, 0.41f, 0.03f, 0.3f, 0.025f, fr); hb(gx + 0.15f, gy, 0.41f, 0.03f, 0.3f, 0.025f, fr)
            hb(gx, gy, 0.405f, 0.27f, 0.27f, 0.015f, 0xCFE8FF, a = 0.25f)
            hb(gx - 0.15f, gy - 0.3f, 0.4f, 0.02f, 0.3f, 0.02f, fr)
            hb(gx - 0.15f, gy - 0.47f, 0.38f, 0.04f, 0.04f, 0.04f, fr)
        }
        if (fc == 12) {   // óculos aviador
            val fr = gold
            for (sd in intArrayOf(-1, 1)) {
                val s = sd.toFloat(); val cx = s * 0.2f
                hb(cx, 1.25f, 0.41f, 0.3f, 0.26f, 0.025f, 0x3A3A44, a = 0.8f)
                hb(cx, 1.15f, 0.41f, 0.22f, 0.07f, 0.025f, 0x3A3A44, a = 0.8f)
                hb(cx, 1.385f, 0.412f, 0.32f, 0.03f, 0.025f, fr); hb(cx, 1.115f, 0.412f, 0.2f, 0.03f, 0.025f, fr)
                hb(cx - 0.16f, 1.25f, 0.412f, 0.03f, 0.26f, 0.025f, fr); hb(cx + 0.16f, 1.25f, 0.412f, 0.03f, 0.26f, 0.025f, fr)
                hb(s * 0.37f, 1.32f, 0.41f, 0.11f, 0.03f, 0.025f, fr)
                hb(s * 0.4f, 1.32f, 0.2f, 0.03f, 0.03f, 0.43f, fr)
                hb(s * 0.4f, 1.295f, -0.005f, 0.03f, 0.05f, 0.06f, fr)
            }
            hb(0f, 1.34f, 0.412f, 0.1f, 0.03f, 0.025f, fr)
        }
        if (fc == 1 || fc == 2 || fc == 9) {
            val frame = if (fc == 1) 0x3A2E24 else if (fc == 9) 0x2A3A5A else 0x1E1E28
            val cy = 1.25f
            for (sd in intArrayOf(-1, 1)) {
                val s = sd.toFloat(); val cx = s * 0.2f
                if (fc == 1) {
                    hb(cx, cy + 0.13f, 0.41f, 0.27f, 0.03f, 0.025f, frame)
                    hb(cx, cy - 0.13f, 0.41f, 0.27f, 0.03f, 0.025f, frame)
                    hb(cx - 0.135f, cy, 0.41f, 0.03f, 0.26f, 0.025f, frame)
                    hb(cx + 0.135f, cy, 0.41f, 0.03f, 0.26f, 0.025f, frame)
                } else if (fc == 9) {
                    hb(cx, cy + 0.12f, 0.41f, 0.3f, 0.06f, 0.025f, frame)
                    hb(cx, cy - 0.12f, 0.41f, 0.26f, 0.02f, 0.025f, frame)
                    hb(cx - 0.14f, cy, 0.41f, 0.025f, 0.24f, 0.025f, frame)
                    hb(cx + 0.14f, cy, 0.41f, 0.025f, 0.24f, 0.025f, frame)
                } else {
                    hb(cx, cy, 0.41f, 0.25f, 0.2f, 0.03f, frame)
                    hb(cx - 0.05f, cy + 0.04f, 0.428f, 0.07f, 0.03f, 0.01f, 0x6A6A88)
                }
                hb(s * 0.37f, cy + 0.07f, 0.41f, 0.11f, 0.03f, 0.025f, frame)        // dobradiça: liga a frente à haste
                hb(s * 0.4f, cy + 0.07f, 0.2f, 0.03f, 0.03f, 0.43f, frame)           // haste encostada na lateral
                hb(s * 0.4f, cy + 0.045f, -0.005f, 0.03f, 0.05f, 0.06f, frame)       // ponta que desce atrás da orelha
            }
            hb(0f, cy + 0.04f, 0.41f, 0.1f, 0.03f, 0.025f, frame)
            if (fc == 2) hb(0f, cy + 0.1f, 0.41f, 0.56f, 0.035f, 0.03f, frame)
            if (fc == 1 || fc == 9) {
                hb(-0.2f, cy, 0.405f, 0.25f, 0.25f, 0.015f, 0xCFE8FF, a = 0.28f)
                hb(0.2f, cy, 0.405f, 0.25f, 0.25f, 0.015f, 0xCFE8FF, a = 0.28f)
            }
        }
    }

    private fun rnd(sl: Float) = max(0f, sl - 0.215f) - 0.02f
}
