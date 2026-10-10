package com.cuboland.app

import android.opengl.Matrix
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** (matriz-pai, px,py,pz, rx,ry, sx,sy,sz, oy, cor, alpha, rz) -> desenha uma caixa */
typealias BoxFn = (FloatArray, Float, Float, Float, Float, Float, Float, Float, Float, Float, Int, Float, Float) -> Unit

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
    @JvmField var tilt = 0f       // graus que o corpo deita ao nadar (o jogo aplica na matriz)
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

    private fun lite(c: Int, k: Float) = mixC(c, 0xFFFFFF, k)

    companion object {
        const val HEAD_SCALE = 0.7f
        private fun lp(a: Float, b: Float, k: Float) = a + (b - a) * k
        /** graus extras do braço no golpe padrão */
        fun atkDelta(sp: Float): Float {
            if (sp >= 1f) return 0f
            return when { sp < 0.3f -> -50f * (sp / 0.3f); sp < 0.55f -> -50f + 90f * ((sp - 0.3f) / 0.25f); else -> 40f * (1f - (sp - 0.55f) / 0.45f) }
        }
    }

    /** root = origem nos pés. handOut = matriz da mão direita (pra segurar item). */
    fun draw(boxIn: BoxFn, root: FloatArray, an: Anim, handOut: FloatArray?) {
        var zf = 0
        bx = { m, px, py, pz, rx, ry, sx, sy, sz, oy, col, a, rz ->
            val k = 1f + min(zf, 260) * 0.00011f; zf++   // cada caixa nova fica um tiquinho maior: acaba o z-fighting de faces coplanares
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
        val bw = if (girl) 0.44f else 0.48f
        val gold = 0xFFD060
        val trim = lite(tc, 0.35f); val dark = shadeC(tc, 0.78f)

        // ================= ANIMAÇÃO =================
        val mv = an.move.coerceIn(0f, 1f); val rn = an.run.coerceIn(0f, 1f); val ph = an.phase
        val swimming = an.swim > 0.2f
        val fl = if (swimming) 0f else an.fly.coerceIn(0f, 1f); val fm = an.flySpd.coerceIn(0f, 1f)
        val ab = if (swimming) 0f else an.airA.coerceIn(0f, 1f) * (1f - fl)
        val chg = an.charge.coerceIn(0f, 1f)
        val vk = (an.vy / 7f).coerceIn(-1f, 1f)
        val air = ab > 0.02f
        val fs = (1f - vk) * 0.5f          // 0 subindo .. 1 caindo (contínuo, sem trancos)
        val dv = an.dive.coerceIn(0f, 1f)
        val land = an.landT.coerceIn(0f, 1f)
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
            val fk = sin(t * 7f) * 8f * fm
            hipL = lp(hipL, -6f + fk + sin(t * 2.4f) * 5f * (1f - fm), fl); hipR = lp(hipR, 4f - fk + sin(t * 2.4f + 1f) * 5f * (1f - fm), fl)
            kneeL = lp(kneeL, 10f + 8f * fm, fl); kneeR = lp(kneeR, 14f + 8f * fm, fl)
        }
        if (land > 0f) {
            hipL += (-42f - hipL) * land; hipR += (-42f - hipR) * land
            kneeL += (80f - kneeL) * land; kneeR += (80f - kneeR) * land
        }
        if (full || overalls.not() && skirtBot) {   // roupa longa/saia: pernas não atravessam o tecido
            val kk = if (full) 0.38f else 0.6f
            hipL *= kk; hipR *= kk; kneeL *= kk + 0.05f; kneeR *= kk + 0.05f
        }
        // posturas: agachar (pernas dobradas, tronco inclinado), sentar (pernas à frente, no chão), deitar (de costas)
        val cr = an.crouch.coerceIn(0f, 1f); val si = an.sit.coerceIn(0f, 1f); val li = an.lie.coerceIn(0f, 1f)
        if (cr > 0f) { hipL = lp(hipL, -55f + sw * 0.4f, cr); hipR = lp(hipR, -55f - sw * 0.4f, cr); kneeL = lp(kneeL, 95f + kneeL * 0.3f, cr); kneeR = lp(kneeR, 95f + kneeR * 0.3f, cr) }
        if (si > 0f) { val br = sin(t * 1.6f) * 2f; hipL = lp(hipL, -82f + br, si); hipR = lp(hipR, -78f - br, si); kneeL = lp(kneeL, 22f, si); kneeR = lp(kneeR, 30f, si) }
        if (li > 0f) { hipL = lp(hipL, 0f, li); hipR = lp(hipR, 0f, li); kneeL = lp(kneeL, 4f, li); kneeR = lp(kneeR, 8f, li) }
        val drop = 0.1f * land + 0.145f * cr + 0.38f * si
        val bob = sin(t * 2f) * 0.012f * (1f - stride) + abs(sin(ph)) * (0.035f + 0.04f * rn) * stride
        val move = stride * (1f + rn * 0.6f) + ab * lp(0.4f, 1.2f, fs)

        System.arraycopy(root, 0, r2, 0, 16)
        Matrix.translateM(r2, 0, 0f, bob - drop, 0f)
        if (li > 0.001f) {   // deitado de costas: gira o corpo todo em volta do centro
            Matrix.translateM(r2, 0, 0f, 0.16f * li, 0.7f * li)
            Matrix.rotateM(r2, 0, -90f * li, 1f, 0f, 0f)
        }

        val lean = 18f * cr + 6f * si + 3f * stride + 12f * rn * stride + ab * lp(-5f, 7f, fs) + 16f * land + fl * (8f + 52f * fm) - 10f * chg +
            (if (atking) 9f * sin(atk * 3.1416f) else 0f)
        val twist = sin(ph) * (5f + 9f * rn) * stride + 14f * chg + (if (atking) 18f * sin(atk * 6.2832f) else 0f)
        val roll = sin(ph) * 2.2f * stride * (1f - rn) + (if (air) 0f else sin(t * 0.9f) * 1.3f * (1f - stride))
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
        val hnod = -lean * 0.55f + sin(ph * 2f) * 1.8f * stride + sin(t * 1.3f) * 1.4f * (1f - stride) + ab * lp(5f, -8f, fs) - an.tilt * lp(0.9f, 0.5f, dv)
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
        piv(sm, bm, 0f, 0.5f, 0f, rx = sin(t * 2.2f) * 1.5f + sin(ph * 2f) * 3f * stride)
        Matrix.scaleM(sm, 0, flare, 1f, flare)
        fun sk(py: Float, sx: Float, sy: Float, sz: Float, col: Int) = b(sm, 0f, py - 0.5f, 0f, sx, sy, sz, col)

        // ================= PERNAS (coxa + joelho) =================
        var seg = when (bot) { 0 -> 0.40f; 1 -> 0.17f; 2 -> 0f; 3 -> 0.30f; 4 -> 0.40f; 5 -> 0f; 6 -> 0.44f; else -> 0.30f }
        var legCol = bc
        if (full) seg = 0f
        if (overalls) { seg = 0.40f; legCol = tc }
        val legW = when (bot) { 4 -> 0.31f; 6 -> 0.2f; else -> 0.205f }
        val legD = when (bot) { 4 -> 0.31f; 6 -> 0.215f; else -> 0.225f }
        val sh = L[Look.SHOES]; val shc = Look.pal(Look.SHOEC)
        for (sd in intArrayOf(-1, 1)) {
            val s = sd.toFloat()
            val hip = if (sd < 0) hipL else hipR; val kn = if (sd < 0) kneeL else kneeR
            piv(t1, r2, s * 0.11f, 0.44f, 0f, rx = hip + lean * fl)   // voando: as pernas acompanham a inclinação do corpo
            piv(t2, t1, 0f, -0.22f, 0f, rx = kn)
            b(t1, 0f, -0.115f, 0f, 0.19f, 0.23f, 0.21f, skin)
            b(t2, 0f, -0.11f, 0f, 0.19f, 0.22f, 0.21f, skin)
            // juntas: quadril e joelho com a cor da roupa (ou da pele) pra não abrir vão ao dobrar
            val hipC = if (seg > 0f) legCol else skin
            b(t1, 0f, 0.025f, 0f, (if (seg > 0f) legW else 0.19f) - 0.004f, 0.11f, (if (seg > 0f) legD else 0.21f) - 0.004f, hipC)
            if (seg <= 0.22f) b(t2, 0f, 0f, 0f, 0.192f, 0.07f, 0.212f, skin)
            if (seg > 0f) {
                val st = min(seg, 0.22f); val sn = seg - st
                b(t1, 0f, 0.005f - st / 2f, 0f, legW, st + 0.01f, legD, legCol)
                if (sn > 0f) {
                    b(t2, 0f, -sn / 2f, 0f, legW, sn, legD, legCol)
                    b(t2, 0f, 0f, 0f, legW - 0.012f, 0.08f, legD - 0.012f, legCol)
                    if (seg >= 0.3f) b(t2, 0f, -sn + 0.0175f, 0f, legW + 0.012f, 0.035f, legD + 0.012f, if (bot == 4) lite(bc, 0.5f) else shadeC(legCol, 0.8f))
                }
                if (bot == 7) b(t1, s * (legW / 2f + 0.006f), -0.12f, 0.02f, 0.025f, 0.12f, 0.12f, shadeC(legCol, 0.82f))
                if (bot == 4) b(t1, 0f, -0.1f, legD / 2f + 0.004f, 0.02f, 0.2f, 0.01f, shadeC(legCol, 0.75f))
            }
            fun ya(y: Float) = y + 0.22f   // converte pra o referencial da canela
            when (sh) {
                0 -> {
                    b(t2, 0f, ya(-0.34f), 0.03f, 0.225f, 0.2f, 0.28f, shc)
                    b(t2, 0f, ya(-0.235f), 0.025f, 0.245f, 0.05f, 0.26f, lite(shc, 0.3f))
                    b(t2, 0f, ya(-0.425f), 0.035f, 0.235f, 0.03f, 0.3f, 0x3A2A22)
                }
                1 -> {
                    b(t2, 0f, ya(-0.335f), 0.03f, 0.22f, 0.13f, 0.29f, shc)
                    b(t2, 0f, ya(-0.42f), 0.035f, 0.235f, 0.04f, 0.31f, white)
                    b(t2, 0f, ya(-0.35f), 0.14f, 0.2f, 0.09f, 0.1f, lite(shc, 0.6f))
                    b(t2, 0f, ya(-0.27f), 0.07f, 0.12f, 0.02f, 0.1f, 0xFFFFFF)
                }
                2 -> {
                    b(t2, 0f, ya(-0.395f), 0.04f, 0.21f, 0.09f, 0.27f, shc)
                    b(t2, 0f, ya(-0.43f), 0.04f, 0.215f, 0.02f, 0.28f, 0x3A2A22)
                    b(t2, 0f, ya(-0.35f), 0.17f, 0.07f, 0.04f, 0.05f, lite(shc, 0.4f))
                }
                3 -> {
                    b(t2, 0f, ya(-0.4f), 0.04f, 0.2f, 0.08f, 0.26f, skin)
                    b(t2, 0f, ya(-0.415f), 0.17f, 0.18f, 0.05f, 0.04f, skinD)
                }
                4 -> {
                    b(t2, 0f, ya(-0.27f), 0.02f, 0.225f, 0.34f, 0.24f, shc)
                    b(t2, 0f, ya(-0.095f), 0.015f, 0.245f, 0.06f, 0.25f, lite(shc, 0.3f))
                    b(t2, 0f, ya(-0.425f), 0.035f, 0.235f, 0.03f, 0.3f, 0x3A2A22)
                    b(t2, 0f, ya(-0.4f), 0.15f, 0.2f, 0.1f, 0.1f, shadeC(shc, 0.85f))
                }
                5 -> {   // geta com meia tabi
                    b(t2, 0f, ya(-0.35f), 0.03f, 0.205f, 0.14f, 0.23f, white)
                    b(t2, 0f, ya(-0.395f), 0.04f, 0.23f, 0.04f, 0.3f, 0x9A6A3E)
                    b(t2, 0f, ya(-0.432f), 0.1f, 0.22f, 0.03f, 0.05f, 0x6F4A29)
                    b(t2, 0f, ya(-0.432f), -0.06f, 0.22f, 0.03f, 0.05f, 0x6F4A29)
                    b(t2, 0f, ya(-0.34f), 0.1f, 0.05f, 0.05f, 0.14f, shc)
                }
                6 -> {   // mocassim
                    b(t2, 0f, ya(-0.385f), 0.04f, 0.215f, 0.11f, 0.29f, shc)
                    b(t2, 0f, ya(-0.43f), 0.04f, 0.225f, 0.02f, 0.3f, 0x2A2220)
                    b(t2, 0f, ya(-0.32f), 0.0f, 0.205f, 0.07f, 0.23f, white)
                    b(t2, 0f, ya(-0.37f), 0.17f, 0.06f, 0.04f, 0.03f, gold)
                }
                else -> {   // meias listradas + tênis baixo
                    b(t2, 0f, ya(-0.395f), 0.035f, 0.22f, 0.09f, 0.29f, shc)
                    b(t2, 0f, ya(-0.43f), 0.035f, 0.23f, 0.02f, 0.3f, white)
                    b(t2, 0f, ya(-0.28f), 0f, 0.205f, 0.13f, 0.225f, white)
                    b(t2, 0f, ya(-0.25f), 0f, 0.21f, 0.035f, 0.23f, shc)
                    b(t2, 0f, ya(-0.31f), 0f, 0.21f, 0.035f, 0.23f, shc)
                    b(t2, 0f, ya(-0.12f), 0f, 0.205f, 0.14f, 0.225f, white)
                    b(t2, 0f, ya(-0.08f), 0f, 0.21f, 0.035f, 0.23f, shc)
                    b(t2, 0f, ya(-0.15f), 0f, 0.21f, 0.035f, 0.23f, shc)
                }
            }
        }

        // ================= CORPO =================
        if (!full && !overalls) b(base, 0f, 0.46f, 0f, bw + 0.015f, 0.05f, 0.315f, shadeC(bc, 0.85f))
        when (top) {
            0 -> {
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                b(base, 0f, 0.935f, 0f, bw * 0.55f, 0.035f, 0.32f, trim)
                b(base, 0f, 0.46f, 0f, bw + 0.01f, 0.04f, 0.31f, dark)
            }
            1 -> {
                b(base, 0f, 0.69f, 0f, bw + 0.02f, 0.5f, 0.32f, tc)
                b(base, 0f, 0.47f, 0f, bw + 0.03f, 0.06f, 0.33f, dark)
                b(base, 0f, 0.58f, 0.165f, 0.26f, 0.12f, 0.03f, dark)
                b(base, -0.06f, 0.82f, 0.168f, 0.025f, 0.14f, 0.02f, 0xFFFFFF)
                b(base, 0.06f, 0.82f, 0.168f, 0.025f, 0.14f, 0.02f, 0xFFFFFF)
                b(base, 0f, 0.96f, -0.13f, 0.36f, 0.16f, 0.18f, dark)
            }
            2 -> {
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                b(base, 0f, 0.935f, 0f, bw * 0.6f, 0.035f, 0.32f, trim)
                b(base, 0f, 0.5f, 0f, bw + 0.02f, 0.05f, 0.32f, trim)
                b(base, 0f, 0.5f, -0.18f, 0.16f, 0.1f, 0.05f, trim)
                sk(0.43f, bw + 0.06f, 0.14f, 0.36f, tc)
                sk(0.36f, 0.62f, 0.12f, 0.46f, tc)
                sk(0.27f, 0.76f, 0.10f, 0.56f, shadeC(tc, 0.94f))
                sk(0.225f, 0.78f, 0.03f, 0.58f, lite(tc, 0.55f))
            }
            3 -> {
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, white)
                b(base, -bw * 0.3f, 0.69f, 0.01f, bw * 0.4f + 0.02f, 0.52f, 0.31f, tc)
                b(base, bw * 0.3f, 0.69f, 0.01f, bw * 0.4f + 0.02f, 0.52f, 0.31f, tc)
                b(base, -0.12f, 0.94f, 0f, 0.12f, 0.06f, 0.33f, dark)
                b(base, 0.12f, 0.94f, 0f, 0.12f, 0.06f, 0.33f, dark)
                b(base, 0f, 0.46f, 0f, bw + 0.03f, 0.05f, 0.32f, dark)
            }
            4 -> {
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                b(base, 0f, 0.54f, 0f, bw + 0.02f, 0.06f, 0.32f, gold)
                b(base, 0f, 0.54f, 0.165f, 0.08f, 0.07f, 0.02f, 0xFFF0A0)
                b(base, 0f, 0.455f, 0f, bw + 0.03f, 0.05f, 0.33f, trim)
                b(base, 0f, 0.935f, 0f, bw * 0.6f, 0.04f, 0.32f, trim)
            }
            5 -> {
                val plate = mixC(0xC5CEDA, tc, 0.3f)
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, 0x4A5568)
                b(base, 0f, 0.72f, 0.02f, bw + 0.02f, 0.3f, 0.32f, plate)
                b(base, 0f, 0.72f, 0.18f, 0.06f, 0.3f, 0.02f, gold)
                b(base, 0f, 0.5f, 0f, bw + 0.02f, 0.07f, 0.32f, 0x6F4A29)
                b(base, 0f, 0.5f, 0.165f, 0.08f, 0.07f, 0.02f, gold)
                b(base, 0f, 0.44f, 0.02f, bw + 0.01f, 0.1f, 0.33f, plate)
            }
            6 -> {
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, white)
                b(base, 0f, 0.66f, 0.165f, 0.3f, 0.28f, 0.03f, tc)
                b(base, 0f, 0.7f, 0.185f, 0.15f, 0.1f, 0.02f, dark)
                b(base, -0.13f, 0.88f, 0f, 0.07f, 0.14f, 0.32f, tc)
                b(base, 0.13f, 0.88f, 0f, 0.07f, 0.14f, 0.32f, tc)
                b(base, 0f, 0.62f, -0.165f, bw, 0.34f, 0.03f, tc)
                b(base, -0.13f, 0.84f, 0.17f, 0.04f, 0.04f, 0.02f, gold)
                b(base, 0.13f, 0.84f, 0.17f, 0.04f, 0.04f, 0.02f, gold)
            }
            7 -> {   // camisa social
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                b(base, -0.085f, 0.925f, 0.02f, 0.11f, 0.06f, 0.33f, lite(tc, 0.5f))
                b(base, 0.085f, 0.925f, 0.02f, 0.11f, 0.06f, 0.33f, lite(tc, 0.5f))
                b(base, 0f, 0.69f, 0.155f, 0.03f, 0.5f, 0.01f, dark)
                for (i in 0 until 4) b(base, 0f, 0.85f - i * 0.11f, 0.162f, 0.045f, 0.045f, 0.01f, white)
                b(base, 0.12f, 0.78f, 0.158f, 0.1f, 0.1f, 0.01f, dark)
            }
            8 -> {   // marinheiro (colegial anime)
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, white)
                b(base, 0f, 0.915f, -0.1f, bw + 0.04f, 0.12f, 0.12f, tc)
                b(base, -0.085f, 0.85f, 0.16f, 0.09f, 0.22f, 0.025f, tc, rz = 22f)
                b(base, 0.085f, 0.85f, 0.16f, 0.09f, 0.22f, 0.025f, tc, rz = -22f)
                b(base, 0f, 0.95f, 0.0f, 0.3f, 0.03f, 0.31f, lite(tc, 0.5f))
                b(base, 0f, 0.775f, 0.17f, 0.06f, 0.06f, 0.04f, 0xE05555)
                b(base, -0.07f, 0.77f, 0.17f, 0.09f, 0.07f, 0.035f, 0xE05555, rz = 12f)
                b(base, 0.07f, 0.77f, 0.17f, 0.09f, 0.07f, 0.035f, 0xE05555, rz = -12f)
                b(base, 0f, 0.465f, 0f, bw + 0.012f, 0.04f, 0.31f, tc)
            }
            9 -> {   // gakuran (uniforme escolar)
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                b(base, -0.09f, 0.945f, 0.0f, 0.1f, 0.07f, 0.33f, dark)
                b(base, 0.09f, 0.945f, 0.0f, 0.1f, 0.07f, 0.33f, dark)
                b(base, 0f, 0.69f, 0.155f, 0.03f, 0.5f, 0.01f, dark)
                for (i in 0 until 4) b(base, 0f, 0.86f - i * 0.12f, 0.162f, 0.045f, 0.045f, 0.01f, gold)
                b(base, 0f, 0.46f, 0f, bw + 0.02f, 0.05f, 0.315f, dark)
                b(base, -0.12f, 0.93f, 0.168f, 0.04f, 0.04f, 0.015f, gold)
                b(base, 0.12f, 0.93f, 0.168f, 0.04f, 0.04f, 0.015f, gold)
            }
            10 -> {  // kimono
                val obi = shadeC(tc, 0.55f)
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                b(base, -0.07f, 0.84f, 0.157f, 0.07f, 0.26f, 0.015f, white, rz = 22f)
                b(base, 0.07f, 0.84f, 0.157f, 0.07f, 0.26f, 0.015f, white, rz = -22f)
                b(base, 0f, 0.53f, 0f, bw + 0.03f, 0.16f, 0.33f, obi)
                b(base, 0f, 0.53f, 0.17f, 0.2f, 0.03f, 0.01f, gold)
                b(base, 0f, 0.55f, -0.2f, 0.34f, 0.24f, 0.1f, obi)
                b(base, 0f, 0.55f, -0.265f, 0.1f, 0.1f, 0.04f, gold)
                sk(0.36f, bw + 0.05f, 0.24f, 0.34f, tc)
                sk(0.16f, bw + 0.08f, 0.2f, 0.37f, tc)
                sk(0.045f, bw + 0.09f, 0.05f, 0.38f, lite(tc, 0.6f))
            }
            11 -> {  // robe de mago
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                b(base, 0f, 0.935f, 0f, bw * 0.7f, 0.04f, 0.32f, gold)
                b(base, 0f, 0.78f, 0.16f, 0.1f, 0.1f, 0.02f, gold)
                b(base, 0f, 0.78f, 0.162f, 0.04f, 0.04f, 0.02f, tc)
                b(base, 0f, 0.52f, 0f, bw + 0.03f, 0.05f, 0.33f, 0x6F4A29)
                b(base, 0.1f, 0.4f, 0.17f, 0.03f, 0.22f, 0.02f, gold)
                sk(0.4f, bw + 0.08f, 0.28f, 0.38f, tc)
                sk(0.17f, 0.74f, 0.28f, 0.54f, shadeC(tc, 0.92f))
                sk(0.045f, 0.76f, 0.05f, 0.56f, gold)
            }
            12 -> {  // terno
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                b(base, 0f, 0.74f, 0.158f, 0.16f, 0.4f, 0.01f, white)
                b(base, -0.1f, 0.84f, 0.165f, 0.1f, 0.26f, 0.02f, shadeC(tc, 0.7f), rz = 12f)
                b(base, 0.1f, 0.84f, 0.165f, 0.1f, 0.26f, 0.02f, shadeC(tc, 0.7f), rz = -12f)
                b(base, -0.0f, 0.5f, 0.17f, 0.04f, 0.04f, 0.015f, gold)
                b(base, -0.14f, 0.72f, 0.158f, 0.1f, 0.03f, 0.01f, dark)
                b(base, 0f, 0.46f, 0f, bw + 0.02f, 0.05f, 0.315f, dark)
            }
            13 -> {  // regata
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                b(base, 0f, 0.905f, 0.155f, 0.2f, 0.09f, 0.01f, skin)
                b(base, 0f, 0.935f, -0.15f, 0.26f, 0.05f, 0.01f, skin)
                b(base, 0f, 0.46f, 0f, bw + 0.012f, 0.04f, 0.31f, dark)
            }
            14 -> {  // haori samurai
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, white)
                b(base, -bw * 0.3f, 0.69f, 0.01f, bw * 0.4f + 0.02f, 0.52f, 0.31f, tc)
                b(base, bw * 0.3f, 0.69f, 0.01f, bw * 0.4f + 0.02f, 0.52f, 0.31f, tc)
                b(base, -0.1f, 0.945f, 0f, 0.1f, 0.06f, 0.33f, lite(tc, 0.5f))
                b(base, 0.1f, 0.945f, 0f, 0.1f, 0.06f, 0.33f, lite(tc, 0.5f))
                b(base, 0f, 0.78f, -0.155f, 0.15f, 0.15f, 0.02f, white)
                b(base, -bw * 0.3f, 0.33f, 0.01f, bw * 0.4f + 0.04f, 0.26f, 0.35f, tc)
                b(base, bw * 0.3f, 0.33f, 0.01f, bw * 0.4f + 0.04f, 0.26f, 0.35f, tc)
                b(base, 0f, 0.33f, -0.17f, bw + 0.04f, 0.26f, 0.03f, tc)
                b(base, -bw * 0.3f, 0.21f, 0.01f, bw * 0.4f + 0.05f, 0.03f, 0.36f, lite(tc, 0.6f))
                b(base, bw * 0.3f, 0.21f, 0.01f, bw * 0.4f + 0.05f, 0.03f, 0.36f, lite(tc, 0.6f))
                b(base, 0f, 0.5f, 0f, bw + 0.02f, 0.05f, 0.32f, 0x3A2A22)
            }
            15 -> {  // maid
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, tc)
                b(base, 0f, 0.7f, 0.16f, 0.26f, 0.4f, 0.02f, white)
                b(base, -0.1f, 0.9f, 0.0f, 0.04f, 0.1f, 0.32f, white)
                b(base, 0.1f, 0.9f, 0.0f, 0.04f, 0.1f, 0.32f, white)
                b(base, 0f, 0.945f, 0f, 0.3f, 0.04f, 0.33f, white)
                b(base, 0f, 0.5f, 0f, bw + 0.02f, 0.05f, 0.32f, white)
                b(base, 0f, 0.5f, -0.18f, 0.2f, 0.1f, 0.05f, white)
                sk(0.43f, bw + 0.06f, 0.14f, 0.36f, tc)
                sk(0.36f, 0.62f, 0.12f, 0.46f, tc)
                sk(0.27f, 0.74f, 0.1f, 0.54f, tc)
                sk(0.2f, 0.78f, 0.05f, 0.58f, white)
                sk(0.37f, 0.4f, 0.28f, 0.52f, white)
            }
            16 -> {  // colete de aventureiro
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, white)
                b(base, -bw * 0.3f, 0.69f, 0.01f, bw * 0.4f + 0.02f, 0.52f, 0.31f, tc)
                b(base, bw * 0.3f, 0.69f, 0.01f, bw * 0.4f + 0.02f, 0.52f, 0.31f, tc)
                b(base, 0f, 0.7f, 0.17f, 0.05f, 0.62f, 0.02f, 0x6F4A29, rz = 28f)
                b(base, 0f, 0.5f, 0f, bw + 0.03f, 0.06f, 0.33f, 0x6F4A29)
                b(base, 0f, 0.5f, 0.17f, 0.08f, 0.08f, 0.02f, gold)
                b(base, 0f, 0.935f, 0f, bw * 0.5f, 0.04f, 0.32f, white)
            }
            else -> {  // casacão (puffer)
                b(base, 0f, 0.69f, 0f, bw + 0.05f, 0.5f, 0.36f, tc)
                for (i in 0 until 3) b(base, 0f, 0.56f + i * 0.13f, 0f, bw + 0.06f, 0.025f, 0.37f, dark)
                b(base, 0f, 0.955f, 0f, 0.5f, 0.1f, 0.4f, dark)
                b(base, 0f, 0.7f, 0.185f, 0.02f, 0.46f, 0.01f, lite(tc, 0.5f))
                b(base, 0f, 0.465f, 0f, bw + 0.07f, 0.05f, 0.37f, dark)
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
                for (i in -2..2) b(sm, i * 0.14f, -0.2f, 0.275f, 0.025f, 0.2f, 0.02f, shadeC(bc, 0.72f))
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
        val atkRx = if (an.poseOn) -an.pP * 0.95f else aa
        var ryR = 0f
        if (toolArm) { rxR = -22f + atkRx - sw * 0.25f * (1f - rn * 0.5f); elR = -6f - 6f * rn * stride; rzR = 20f
            if (an.poseOn) { ryR = an.pY * 0.9f; elR = (elR + an.pW * 0.5f).coerceIn(-70f, 10f) } }
        else if (atking || chg > 0.01f) { rxR += atkRx; if (an.poseOn) ryR = an.pY * 0.9f }
        if (swimming) {
            // só as pernas batem. Mergulhado: braço do item aberto pra direita e parado, braço vazio esticado rente ao corpo (em direção às pernas).
            // Cabeça fora: mão do item pra baixo, normal; o outro braço aberto, remando devagar.
            val sl2 = sin(t * 2.3f)
            val lSurfX = -25f + sl2 * 14f; val lSurfZ = -(38f + sin(t * 2.3f + 1.2f) * 16f); val lSurfE = -20f + sl2 * 8f
            rxL = lp(lSurfX, 3f, dv); rzL = lp(lSurfZ, -3f, dv); elL = lp(lSurfE, -4f, dv)
            val rSurfX = (if (toolArm) -10f else -6f) + atkRx
            rxR = lp(rSurfX, -14f + atkRx, dv); rzR = lp(16f, 34f, dv); elR = lp(-6f, -8f, dv)
            ryR = if (an.poseOn && toolArm) an.pY * 0.9f else 0f
        }
        if (ab > 0.01f) {
            val flapA = sin(t * 12f) * 6f * (1f - abs(vk))
            val pxL = lp(-35f, -10f, fs) + flapA; val pzL = lp(-150f, -75f, fs); val pelL = lp(-10f, -8f, fs)
            rxL = lp(rxL, pxL, ab); rzL = lp(rzL, pzL, ab); elL = lp(elL, pelL, ab)
            if (toolArm) {   // braço da ferramenta continua vivo: balança de leve, sem subir pra cabeça
                val tx = -22f + atkRx + lp(-14f, 22f, fs) + sin(t * 9f) * 5f * (1f - abs(vk)); val tz = lp(22f, 34f, fs)
                rxR = lp(rxR, tx, ab); rzR = lp(rzR, tz, ab); elR = lp(elR, lp(-12f, -4f, fs), ab)
            } else {
                rxR = lp(rxR, lp(-35f, -10f, fs) - flapA, ab); rzR = lp(rzR, lp(150f, 75f, fs), ab); elR = lp(elR, pelL, ab)
            }
        }
        if (fl > 0.01f) {
            // os dois braços ficam pra baixo, iguais ao do item: só um balanço leve
            val fw = sin(t * 3f) * 4f * (1f - fm)
            val dn = lp(-14f, -26f, fm)
            rxL = lp(rxL, dn + sin(t * 2.6f) * 3f, fl); rzL = lp(rzL, -(16f + fw), fl); elL = lp(elL, -8f, fl)
            if (toolArm) { rxR = lp(rxR, lp(-24f, -40f, fm) + atkRx, fl); rzR = lp(rzR, 20f, fl) }
            else { rxR = lp(rxR, dn - sin(t * 2.6f) * 3f, fl); rzR = lp(rzR, 16f + fw, fl); elR = lp(elR, -8f, fl) }
        }
        if (land > 0f) { rzL -= 25f * land; if (!toolArm) rzR += 25f * land }
        for (sd in intArrayOf(-1, 1)) {
            val s = sd.toFloat()
            val rx = if (sd < 0) rxR else rxL; val rz = if (sd < 0) -rzR else -rzL; val el = if (sd < 0) elR else elL   // +x é a ESQUERDA do boneco: o item vai na direita (sd<0)
            piv(t1, base, s * (bw / 2f + 0.09f), 0.88f, 0f, rx = rx, ry = if (sd < 0) ryR else 0f, rz = rz)
            piv(t2, t1, 0f, -0.21f, 0f, rx = el)
            b(t1, 0f, -0.1f, 0f, 0.15f, 0.23f, 0.17f, skin)
            b(t2, 0f, -0.1f, 0f, 0.15f, 0.22f, 0.17f, skin)
            b(t2, 0f, -0.16f, 0.005f, 0.165f, 0.12f, 0.185f, skin)
            // juntas do braço: cotovelo e ombro na cor da manga (ou da pele, sem manga)
            if (sl >= 0.19f) b(t2, 0f, 0f, 0f, 0.181f, 0.08f, 0.201f, slCol) else b(t2, 0f, 0f, 0f, 0.152f, 0.07f, 0.172f, skin)
            if (sl > 0f) b(t1, 0f, 0.0f, 0f, 0.184f, 0.1f, 0.204f, slCol)
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
            }
            if (sd < 0 && handOut != null) {
                piv(handOut, t1, 0f, -0.21f, 0f, rx = el)
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
        val hc = Look.hair(); val hd = shadeC(hc, 0.82f); val hl = lite(hc, 0.25f)
        val tie = Look.pal(Look.HATC)
        val hs = L[Look.HAIR]
        fun bangs(h: FloatArray) {
            val n = h.size
            for (i in 0 until n) hb((i - (n - 1) / 2f) * 0.2f, 1.71f - h[i] / 2f, 0.405f, 0.205f, h[i], 0.06f, hc)
        }
        if (hs == 7) {
            hb(0f, 1.72f, 0f, 0.98f, 0.3f, 0.98f, hc)
            hb(-0.47f, 1.4f, 0f, 0.14f, 0.55f, 0.9f, hc)
            hb(0.47f, 1.4f, 0f, 0.14f, 0.55f, 0.9f, hc)
            hb(0f, 1.38f, -0.46f, 0.98f, 0.65f, 0.14f, hc)
            hb(-0.4f, 1.86f, 0.2f, 0.3f, 0.3f, 0.3f, hl, ry = 45f)
            hb(0.4f, 1.86f, 0.2f, 0.3f, 0.3f, 0.3f, hl, ry = 45f)
            hb(-0.4f, 1.86f, -0.25f, 0.3f, 0.3f, 0.3f, hl, ry = 45f)
            hb(0.4f, 1.86f, -0.25f, 0.3f, 0.3f, 0.3f, hl, ry = 45f)
            bangs(floatArrayOf(0.16f, 0.12f, 0.16f, 0.12f))
        } else if (hs == 14) {   // moicano
            hb(0f, 1.69f, 0f, 0.22f, 0.12f, 0.86f, hc)
            hb(0f, 1.5f, -0.2f, 0.22f, 0.4f, 0.4f, hc)
            for (i in 0 until 5) hb(0f, 1.82f + (if (i % 2 == 0) 0.05f else 0f), 0.3f - i * 0.15f, 0.2f, 0.22f + (if (i % 2 == 0) 0.1f else 0f), 0.14f, hl)
            hb(-0.4f, 1.62f, 0f, 0.06f, 0.16f, 0.7f, shadeC(hc, 0.6f))
            hb(0.4f, 1.62f, 0f, 0.06f, 0.16f, 0.7f, shadeC(hc, 0.6f))
        } else {
            hb(0f, 1.655f, -0.005f, 0.86f, 0.17f, 0.86f, hc)
            val backLow = when (hs) { 0 -> 1.16f; 1 -> 1.3f; 2 -> 1.0f; 3 -> 1.2f; 4 -> 1.2f; 5 -> 1.25f; 6 -> 0.7f; 8 -> 1.35f; 9 -> 1.15f; 10 -> 1.25f; 11 -> 1.35f; 12 -> 1.3f; 13 -> 1.6f; 15 -> 1.15f; 16 -> 0.5f; else -> 1.2f }
            hb(0f, (backLow + 1.7f) / 2f, -0.37f, 0.88f, 1.7f - backLow, 0.14f, hc)
            val sideLow = when (hs) { 2 -> 1.0f; 6 -> 0.85f; 0 -> 1.35f; 9 -> 1.2f; 15 -> 1.1f; 16 -> 0.6f; 8 -> 1.5f; 13 -> 1.6f; else -> 1.4f }
            hb(-0.415f, (sideLow + 1.7f) / 2f, -0.12f, 0.07f, 1.7f - sideLow, 0.52f, hc)
            hb(0.415f, (sideLow + 1.7f) / 2f, -0.12f, 0.07f, 1.7f - sideLow, 0.52f, hc)
            when (hs) {
                0 -> bangs(floatArrayOf(0.2f, 0.14f, 0.22f, 0.16f))
                1 -> {
                    bangs(floatArrayOf(0.16f, 0.1f, 0.18f, 0.1f))
                    hb(0f, 1.85f, 0.1f, 0.15f, 0.24f, 0.15f, hc)
                    hb(-0.22f, 1.84f, 0f, 0.15f, 0.22f, 0.15f, hc, rz = 18f)
                    hb(0.22f, 1.84f, 0f, 0.15f, 0.22f, 0.15f, hc, rz = -18f)
                    hb(-0.15f, 1.84f, -0.2f, 0.15f, 0.2f, 0.15f, hc, rz = 12f)
                    hb(0.15f, 1.84f, -0.2f, 0.15f, 0.2f, 0.15f, hc, rz = -12f)
                }
                2 -> {
                    bangs(floatArrayOf(0.2f, 0.17f, 0.17f, 0.2f))
                    hb(-0.4f, 1.3f, 0.24f, 0.05f, 0.6f, 0.14f, hc)
                    hb(0.4f, 1.3f, 0.24f, 0.05f, 0.6f, 0.14f, hc)
                }
                3 -> {
                    bangs(floatArrayOf(0.2f, 0.16f, 0.2f, 0.16f))
                    for (sd in intArrayOf(-1, 1)) {
                        val s = sd.toFloat()
                        val sway = sin(t * 2.4f + s) * 4f + sw * 0.18f + move * 6f
                        piv(t3, hm, s * 0.47f, 1.5f, -0.02f, rz = s * (8f + sway))
                        b(t3, 0f, 0f, 0f, 0.14f, 0.12f, 0.14f, tie)
                        b(t3, 0f, -0.2f, 0f, 0.16f, 0.34f, 0.16f, hc)
                        piv(t4, t3, 0f, -0.36f, 0f, rz = s * sin(t * 3f + s) * 5f)
                        b(t4, 0f, -0.14f, 0f, 0.12f, 0.28f, 0.12f, hd)
                        b(t4, 0f, -0.3f, 0f, 0.08f, 0.08f, 0.08f, hl)
                    }
                }
                4 -> {
                    bangs(floatArrayOf(0.22f, 0.14f, 0.14f, 0.22f))
                    hb(0f, 1.55f, -0.45f, 0.16f, 0.14f, 0.12f, tie)
                    piv(t3, hm, 0f, 1.56f, -0.46f, rx = 30f + sw * 0.3f + sin(t * 2.5f) * 3f + move * 8f)
                    b(t3, 0f, -0.17f, 0f, 0.2f, 0.34f, 0.16f, hc)
                    piv(t4, t3, 0f, -0.34f, 0f, rx = 12f + sin(t * 3f) * 5f)
                    b(t4, 0f, -0.15f, 0f, 0.16f, 0.3f, 0.13f, hd)
                    b(t4, 0f, -0.32f, 0f, 0.1f, 0.08f, 0.1f, hl)
                }
                5 -> {
                    bangs(floatArrayOf(0.2f, 0.15f, 0.15f, 0.2f))
                    hb(0f, 1.9f, -0.08f, 0.34f, 0.3f, 0.34f, hc)
                    hb(0f, 1.76f, -0.08f, 0.24f, 0.05f, 0.24f, tie)
                    hb(0f, 1.99f, -0.08f, 0.2f, 0.08f, 0.2f, hl)
                }
                6 -> {
                    bangs(floatArrayOf(0.2f, 0.2f, 0.14f, 0.2f))
                    hb(-0.38f, 1.2f, 0.12f, 0.08f, 0.75f, 0.24f, hc)
                    hb(0.38f, 1.2f, 0.12f, 0.08f, 0.75f, 0.24f, hc)
                }
                8 -> {   // undercut
                    bangs(floatArrayOf(0.24f, 0.2f, 0.16f, 0.1f))
                    hb(0.04f, 1.8f, 0.06f, 0.62f, 0.16f, 0.68f, hc)
                    hb(0.1f, 1.9f, 0.14f, 0.4f, 0.1f, 0.4f, hl)
                }
                9 -> {   // franja de lado (cobre um olho)
                    bangs(floatArrayOf(0.12f, 0.12f, 0.2f, 0.24f))
                    hb(-0.13f, 1.46f, 0.415f, 0.5f, 0.5f, 0.04f, hc)
                    hb(-0.36f, 1.2f, 0.3f, 0.08f, 0.7f, 0.14f, hd)
                }
                10 -> {  // bagunçado anime
                    bangs(floatArrayOf(0.18f, 0.12f, 0.2f, 0.1f))
                    hb(-0.3f, 1.84f, 0.1f, 0.16f, 0.22f, 0.16f, hc, rz = 25f)
                    hb(0f, 1.88f, 0.15f, 0.16f, 0.26f, 0.16f, hl, rz = -8f)
                    hb(0.3f, 1.84f, 0.05f, 0.16f, 0.22f, 0.16f, hc, rz = -28f)
                    hb(-0.2f, 1.84f, -0.25f, 0.16f, 0.2f, 0.16f, hd, rz = 20f)
                    hb(0.2f, 1.84f, -0.25f, 0.16f, 0.2f, 0.16f, hd, rz = -20f)
                    hb(-0.46f, 1.55f, 0.15f, 0.1f, 0.2f, 0.14f, hc, rz = 30f)
                    hb(0.46f, 1.55f, 0.15f, 0.1f, 0.2f, 0.14f, hc, rz = -30f)
                }
                11 -> {  // topete
                    bangs(floatArrayOf(0.1f, 0.08f, 0.1f, 0.08f))
                    hb(0f, 1.86f, 0.18f, 0.62f, 0.22f, 0.52f, hc)
                    hb(0f, 1.97f, 0.28f, 0.42f, 0.14f, 0.3f, hl)
                    hb(0f, 1.8f, 0.46f, 0.5f, 0.1f, 0.1f, hd)
                }
                12 -> {  // saiyajin
                    bangs(floatArrayOf(0.26f, 0.12f, 0.3f, 0.14f))
                    hb(0f, 2.0f, 0f, 0.2f, 0.55f, 0.2f, hc)
                    hb(-0.22f, 1.93f, 0.04f, 0.2f, 0.45f, 0.2f, hc, rz = 20f)
                    hb(0.22f, 1.93f, 0.04f, 0.2f, 0.45f, 0.2f, hc, rz = -20f)
                    hb(-0.36f, 1.8f, -0.08f, 0.18f, 0.34f, 0.18f, hd, rz = 40f)
                    hb(0.36f, 1.8f, -0.08f, 0.18f, 0.34f, 0.18f, hd, rz = -40f)
                    hb(0f, 1.9f, -0.26f, 0.2f, 0.4f, 0.18f, hd, rx = -20f)
                }
                13 -> bangs(floatArrayOf(0.1f, 0.08f, 0.1f, 0.08f))
                15 -> {  // repartido
                    bangs(floatArrayOf(0.3f, 0.2f, 0.2f, 0.3f))
                    hb(-0.41f, 1.38f, 0.1f, 0.06f, 0.3f, 0.26f, hc)
                    hb(0.41f, 1.38f, 0.1f, 0.06f, 0.3f, 0.26f, hc)
                }
                16 -> {  // hime
                    bangs(floatArrayOf(0.22f, 0.22f, 0.22f, 0.22f))
                    hb(-0.4f, 1.12f, 0.26f, 0.1f, 0.85f, 0.14f, hc)
                    hb(0.4f, 1.12f, 0.26f, 0.1f, 0.85f, 0.14f, hc)
                    hb(-0.2f, 0.65f, -0.36f, 0.2f, 0.3f, 0.12f, hd)
                    hb(0.2f, 0.65f, -0.36f, 0.2f, 0.3f, 0.12f, hd)
                }
                17 -> {  // trança
                    bangs(floatArrayOf(0.2f, 0.14f, 0.2f, 0.16f))
                    piv(t3, hm, 0f, 1.45f, -0.45f, rx = 12f + sin(t * 2.2f) * 4f + move * 10f)
                    b(t3, 0f, -0.14f, 0f, 0.17f, 0.3f, 0.15f, hc)
                    piv(t4, t3, 0f, -0.3f, 0f, rx = sin(t * 2.2f + 1f) * 6f)
                    b(t4, 0f, -0.14f, 0f, 0.15f, 0.28f, 0.13f, hd)
                    piv(t5, t4, 0f, -0.28f, 0f, rx = sin(t * 2.2f + 2f) * 8f)
                    b(t5, 0f, -0.12f, 0f, 0.13f, 0.24f, 0.11f, hc)
                    b(t5, 0f, -0.25f, 0f, 0.15f, 0.06f, 0.13f, tie)
                }
                18 -> {  // dois coques
                    bangs(floatArrayOf(0.2f, 0.15f, 0.15f, 0.2f))
                    for (sd in intArrayOf(-1, 1)) {
                        val s = sd.toFloat()
                        hb(s * 0.3f, 1.9f, -0.02f, 0.3f, 0.3f, 0.3f, hc)
                        hb(s * 0.3f, 1.77f, -0.02f, 0.2f, 0.05f, 0.2f, tie)
                        hb(s * 0.3f, 2.0f, -0.02f, 0.18f, 0.08f, 0.18f, hl)
                    }
                }
                19 -> {  // twintail alto
                    bangs(floatArrayOf(0.2f, 0.16f, 0.2f, 0.16f))
                    for (sd in intArrayOf(-1, 1)) {
                        val s = sd.toFloat()
                        piv(t3, hm, s * 0.4f, 1.72f, -0.06f, rz = s * (28f + sin(t * 2.4f + s) * 5f + move * 6f))
                        b(t3, 0f, 0f, 0f, 0.16f, 0.12f, 0.16f, tie)
                        b(t3, 0f, -0.22f, 0f, 0.22f, 0.4f, 0.2f, hc)
                        piv(t4, t3, 0f, -0.42f, 0f, rz = s * sin(t * 3f + s) * 6f)
                        b(t4, 0f, -0.2f, 0f, 0.18f, 0.4f, 0.16f, hd)
                        b(t4, 0f, -0.44f, 0f, 0.1f, 0.08f, 0.1f, hl)
                    }
                }
                20 -> {  // ahoge
                    bangs(floatArrayOf(0.2f, 0.14f, 0.22f, 0.16f))
                    val aw = sin(t * 2.6f) * 8f
                    hb(0f, 1.85f, 0.06f, 0.06f, 0.2f, 0.06f, hc, rz = aw)
                    hb(0.05f, 1.97f, 0.06f, 0.07f, 0.1f, 0.06f, hc, rz = -30f + aw)
                }
                else -> {}
            }
        }

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

        // ---------- rosto: acessórios por cima (lentes translúcidas por último) ----------
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
