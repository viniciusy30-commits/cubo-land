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
/** Destino das caixas texturizadas: modelo = m * T(px,py,pz) * Rz * Ry * Rx * T(0,oy,0) * S(sx,sy,sz); id = caixa na folha de skin; r0..r1 = fatia de linhas das faces laterais */
interface TexSink {
    fun box(m: FloatArray, px: Float, py: Float, pz: Float, rx: Float, ry: Float, rz: Float, sx: Float, sy: Float, sz: Float, oy: Float, id: Int, r0: Float, r1: Float)
}

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
    @JvmField var dash = 0f; @JvmField var imp = 0f; @JvmField var swimPh = 0f; @JvmField var armFwdR = 0f   // voo rápido, impulso, fase da braçada, (saída) quanto o braço do item está esticado
    @JvmField var tilt = 0f       // graus que o corpo deita ao nadar (o jogo aplica na matriz)
    @JvmField var swimFast = 0f   // 0..1 (suavizado) acelerando na água: só aí os braços batem; sem acelerar, boia/desliza
    @JvmField var toolA = 0f      // (saída) graus extras em X no item da mão, pra ele não furar o corpo/chão (nado e voo rápido)
}

/** Modelo chibi do personagem, montado só com caixas. Usado no jogo (OpenGL) e na tela de personagem (preview). */
class CharModel {
    private var snk: TexSink? = null
    private var zf = 0
    private val hP = FloatArray(16); private val bP = FloatArray(16)
    private val pEnd = Array(96) { FloatArray(16) }
    private val t1 = FloatArray(16); private val t2 = FloatArray(16)
    private val r2 = FloatArray(16); private val bm = FloatArray(16); private val hm = FloatArray(16); private val sm = FloatArray(16)

    /** caixa texturizada (id da folha). Cada caixa nova fica um tiquinho maior: acaba o z-fighting de faces coplanares */
    private fun tb(m: FloatArray, px: Float, py: Float, pz: Float, sx: Float, sy: Float, sz: Float, id: Int, r0: Float = 0f, r1: Float = 1f) {
        val k = 1f + min(zf, 400) * 0.00014f; zf++
        snk?.box(m, px, py, pz, 0f, 0f, 0f, sx * k, sy * k, sz * k, 0f, id, r0, r1)
    }

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
        const val HEAD_SCALE = 1f
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

    /** root = origem nos pés. handOut = matriz da mão direita (pra segurar item). */
    fun draw(sink: TexSink, root: FloatArray, an: Anim, handOut: FloatArray?) {
        snk = sink; zf = 0
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
        val dashW = max(an.dash.coerceIn(0f, 1f), an.imp.coerceIn(0f, 1f)) * (1f - swim01(an))
        val uImp = 1f - an.imp.coerceIn(0f, 1f)
        val wind = if (an.imp <= 0f) 0f else if (uImp < 0.4f) uImp / 0.4f else max(0f, 1f - (uImp - 0.4f) / 0.25f)
        val atk = an.atk; val atking = atk < 1f
        val stride = if (swimming) 0f else min(1f, mv * 3f) * (1f - ab) * (1f - fl)
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
        if (land > 0f) {
            hipL += (-42f - hipL) * land; hipR += (-42f - hipR) * land
            kneeL += (80f - kneeL) * land; kneeR += (80f - kneeR) * land
        }
        if (full || overalls.not() && skirtBot) {   // roupa longa/saia: pernas não atravessam o tecido
            val kk = if (full) 0.7f else 0.85f   // a saia agora acompanha as coxas, então pode balançar bem mais
            val cap = if (full) 20f else 26f
            hipL = (hipL * kk).coerceIn(-cap, cap); hipR = (hipR * kk).coerceIn(-cap, cap)
            kneeL = (kneeL * kk).coerceIn(0f, if (full) 36f else 60f); kneeR = (kneeR * kk).coerceIn(0f, if (full) 36f else 60f)
        }
        // posturas: agachar (pernas dobradas, tronco inclinado), sentar (pernas à frente, no chão), deitar (de costas)
        val cr = an.crouch.coerceIn(0f, 1f); val si = an.sit.coerceIn(0f, 1f)
        var qLegL = 0f; var qLegR = 0f
        if (cr > 0f) { hipL = lp(hipL, -55f + sw * 0.4f, cr); hipR = lp(hipR, -55f - sw * 0.4f, cr); kneeL = lp(kneeL, 95f + kneeL * 0.3f, cr); kneeR = lp(kneeR, 95f + kneeR * 0.3f, cr) }
        if (si > 0f) { val br = sin(t * 1.6f) * 2f; hipL = lp(hipL, -87f + br, si); hipR = lp(hipR, -87f + br, si); kneeL = lp(kneeL, 4f, si); kneeR = lp(kneeR, 4f, si) }
        if (li > 0f) { val mc = min(1f, mv * 3f); qLegL = (0.5f + 0.5f * sin(ph + 3.1416f)) * mc; qLegR = (0.5f + 0.5f * sin(ph)) * mc
            hipL = lp(hipL, 2f + 7f * qLegL, li); hipR = lp(hipR, 2f + 7f * qLegR, li)
            kneeL = lp(kneeL, 4f + 26f * qLegL, li); kneeR = lp(kneeR, 4f + 26f * qLegR, li) }
        val drop = 0.1f * land + 0.145f * cr + 0.31f * si
        val bob = sin(t * 2f) * 0.012f * (1f - stride) + abs(sin(ph)) * (0.035f + 0.04f * rn) * stride * (1f - 0.85f * li) + fl * (1f - fm) * sin(t * 1.6f) * 0.09f   // pairando: sobe e desce devagar
        val move = stride * (1f + rn * 0.6f) + ab * lp(0.4f, 1.2f, fs)

        System.arraycopy(root, 0, r2, 0, 16)
        Matrix.translateM(r2, 0, 0f, bob - drop, 0f)
        if (li > 0.001f) {   // deitado de costas: gira o corpo todo em volta do centro
            Matrix.translateM(r2, 0, 0f, 0.2f * li, -0.7f * li)   // rastejando, de frente (barriga no chão)
            Matrix.rotateM(r2, 0, 90f * li, 1f, 0f, 0f)
        }

        val lean = 30f * an.dash.coerceIn(0f, 1f) * fl - 30f * wind * dashW + 18f * cr + 6f * si + 3f * stride * (1f - li) - 7f * li + 12f * rn * stride + ab * lp(-5f, 7f, fs) + 16f * land + fl * (8f + 52f * fm) - 10f * chg +
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
        Matrix.translateM(hm, 0, 0f, 0.95f, 0f)
        if (htilt != 0f) Matrix.rotateM(hm, 0, htilt, 0f, 0f, 1f)
        if (hy != 0f) Matrix.rotateM(hm, 0, hy, 0f, 1f, 0f)
        if (hnod != 0f) Matrix.rotateM(hm, 0, hnod, 1f, 0f, 0f)
        Matrix.scaleM(hm, 0, HEAD_SCALE, HEAD_SCALE, HEAD_SCALE)
        Matrix.translateM(hm, 0, 0f, -0.95f, 0f)
        val blink = (t % 3.7f) > 3.58f || (t % 6.1f) > 6.0f

        // saia balançando (preso ao quadril)
        val flare = 1f + ab * lp(0.06f, 0.1f, fs) + 0.05f * rn * stride
        piv(sm, bm, 0f, 0.45f, 0f, rx = sin(t * 2.2f) * 1.5f + sin(ph * 2f) * 3f * stride - 62f * an.sit.coerceIn(0f, 1f) - 34f * an.crouch.coerceIn(0f, 1f))
        Matrix.scaleM(sm, 0, flare, 1f, flare)

        val sk = Skin.get()
        val aw = if (girl) 0.15f else 0.20f
        val inf = 0.0125f
        val jr = 5f / 9f

        // ================= PERNAS (texturizadas) =================
        for (sd in intArrayOf(-1, 1)) {
            val s = sd.toFloat()
            val hip = if (sd < 0) hipL else hipR; val kn = if (sd < 0) kneeL else kneeR
            piv(t1, r2, s * 0.10f, 0.45f, 0f, rx = hip + lean * fl, rz = s * (5f + 7f * (if (sd < 0) qLegL else qLegR)) * li)
            piv(t2, t1, 0f, -0.25f, 0f, rx = kn)
            val lg = if (sd < 0) 8 else 9
            tb(t1, 0f, -0.125f, 0f, 0.20f, 0.25f, 0.20f, lg, 0f, jr)
            tb(t2, 0f, -0.10f, 0f, 0.20f, 0.20f, 0.20f, lg, jr, 1f)
            tb(t2, 0f, 0f, 0f, 0.194f, 0.06f, 0.194f, lg, 0.5f, 0.61f)
            tb(t1, 0f, -0.125f, 0f, 0.20f + 2f * inf, 0.25f + inf, 0.20f + 2f * inf, lg + 2, 0f, jr)
            tb(t2, 0f, -0.10f, 0f, 0.20f + 2f * inf, 0.20f + inf, 0.20f + 2f * inf, lg + 2, jr, 1f)
            tb(t2, 0f, 0f, 0f, 0.194f + 2f * inf, 0.06f + inf, 0.194f + 2f * inf, lg + 2, 0.5f, 0.61f)
            tb(t2, 0f, -0.125f, 0.05f, 0.205f, 0.15f, 0.30f, 12 + (if (sd < 0) 0 else 1))
        }

        // ================= CORPO =================
        tb(base, 0f, 0.70f, 0f, 0.40f, 0.50f, 0.20f, 2)
        tb(base, 0f, 0.70f, 0f, 0.40f + 2f * inf, 0.50f + 2f * inf, 0.20f + 2f * inf, 3)


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

        for (sd in intArrayOf(-1, 1)) {
            val s = sd.toFloat()
            val rx = if (sd < 0) rxR else rxL; val rz = if (sd < 0) -rzR else -rzL; val el = if (sd < 0) elR else elL   // +x e a ESQUERDA do boneco: o item vai na direita (sd<0)
            piv(t1, base, s * (0.20f + aw / 2f), 0.90f, 0f, rx = rx, ry = if (sd < 0) ryR else ryL, rz = rz)
            piv(t2, t1, 0f, -0.20f, 0f, rx = el, ry = elbowSpin * s)
            val ar = if (sd < 0) 4 else 5
            tb(t1, 0f, -0.075f, 0f, aw, 0.25f, 0.20f, ar, 0f, jr)
            tb(t2, 0f, -0.10f, 0f, aw, 0.20f, 0.20f, ar, jr, 1f)
            tb(t2, 0f, 0f, 0f, aw - 0.006f, 0.06f, 0.194f, ar, 0.5f, 0.61f)
            tb(t1, 0f, -0.075f, 0f, aw + 2f * inf, 0.25f + inf, 0.20f + 2f * inf, ar + 2, 0f, jr)
            tb(t2, 0f, -0.10f, 0f, aw + 2f * inf, 0.20f + inf, 0.20f + 2f * inf, ar + 2, jr, 1f)
            tb(t2, 0f, 0f, 0f, aw - 0.006f + 2f * inf, 0.06f + inf, 0.194f + 2f * inf, ar + 2, 0.5f, 0.61f)
            if (sd < 0 && handOut != null) {
                piv(handOut, t1, 0f, -0.20f, 0f, rx = el, ry = elbowSpin * s)
                Matrix.translateM(handOut, 0, 0f, -0.16f, 0.02f)
            }
        }

        // ================= CABECA =================
        tb(hm, 0f, 1.15f, 0f, 0.40f, 0.40f, 0.40f, 0)
        tb(hm, 0f, 1.15f, 0f, 0.45f, 0.45f, 0.45f, 1)
        if (blink && L[Look.EYES] != 1) tb(hm, 0f, 1.125f, 0.201f, 0.40f, 0.15f, 0.05f, 14)

        // ================= PECAS EXTRAS (roupas, cabelo, chapeus, costas...) =================
        System.arraycopy(hm, 0, hP, 0, 16); Matrix.translateM(hP, 0, 0f, 1.15f, 0f)
        System.arraycopy(bm, 0, bP, 0, 16); Matrix.translateM(bP, 0, 0f, 0.70f, 0f)
        val np = min(sk.spec.size, 96)
        for (i in 0 until np) {
            val sp = sk.spec[i]
            val pid = sp[0]; val parent = sp[1]
            if (pid < 0 || pid >= sk.nb) { System.arraycopy(bP, 0, pEnd[i], 0, 16); continue }
            val pm: FloatArray = if (parent >= 100 && parent - 100 < i) pEnd[parent - 100] else if (parent == 0) hP else if (parent == 1) bP else sm
            val mode = sp[8]; val kind = sp[9]
            val amp = sp[10].toFloat(); val mvv = sp[11].toFloat(); val phs = sp[12].toFloat()
            var ax = sp[5].toFloat(); var ay = sp[6].toFloat(); var az = sp[7].toFloat()
            val sg = if (sp[2] < 0) -1f else 1f
            when (kind) {
                1 -> ax += sin(t * 2.4f + phs) * amp + move * mvv + sw * 0.25f
                2 -> az += sg * (sin(t * 2.4f + phs) * amp + move * mvv)
                3 -> ay += sg * (sin(t * 6f + phs) * (amp + mvv * move) + (if (air) 24f else 0f))
                4 -> { az += sin(t * 3f + phs) * amp; ax += sin(t * 2.1f + phs * 1.7f) * mvv * 0.1f }
            }
            val em = pEnd[i]
            piv(em, pm, sp[2] * 0.005f, sp[3] * 0.005f, sp[4] * 0.005f, rx = ax, ry = ay, rz = az)
            val w = sk.bw[pid] * 0.05f; val h = sk.bh[pid] * 0.05f; val d = sk.bd[pid] * 0.05f
            val offY = if (mode == 1) -h / 2f else if (mode == 2) h / 2f else 0f
            tb(em, 0f, offY, 0f, w, h, d, pid)
            if (mode == 1) Matrix.translateM(em, 0, 0f, -h, 0f) else if (mode == 2) Matrix.translateM(em, 0, 0f, h, 0f)
        }
        snk = null
    }
}
