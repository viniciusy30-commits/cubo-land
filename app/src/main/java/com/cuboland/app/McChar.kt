package com.cuboland.app

import android.opengl.Matrix
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Personagem no formato do Minecraft (Steve/Alex): cabeça 8x8x8, corpo 8x12x4, braços 4x12x4 (3 de largura nas meninas), pernas 4x12x4.
 * 1 pixel = 1,8/32 de bloco: o boneco tem 1,8 de altura e 0,6 de largura, igual ao jogador do Minecraft.
 * Cabelo, roupas, chapéus e acessórios são camadas de caixas por cima do corpo (como a "segunda camada" da skin do Minecraft).
 * Usa os mesmos estados de animação (Anim) do boneco antigo: andar, correr, pular, nadar, voar, agachar, sentar, deitar, golpe, item na mão.
 */
class McChar {
    private var bx: BoxFn = { _, _, _, _, _, _, _, _, _, _, _, _, _ -> }
    private val t1 = FloatArray(16); private val t3 = FloatArray(16); private val t4 = FloatArray(16)
    private val r2 = FloatArray(16); private val bm = FloatArray(16); private val hm = FloatArray(16)
    private val legM = arrayOf(FloatArray(16), FloatArray(16))
    private val armM = arrayOf(FloatArray(16), FloatArray(16))

    /**
     * Camada de profundidade da caixa que está sendo desenhada (0 = pele; roupas e detalhes sobem). O renderer usa isso num polygon offset:
     * assim o que vem por cima (olho sobre rosto, botão sobre camisa, bainha sobre manga) sempre ganha, em qualquer distância e em qualquer GPU,
     * sem as faces "brigarem" (z-fighting).
     */
    @JvmField var layer = 0
    private var layerTop = 0
    @JvmField var layerHook: ((Int) -> Unit)? = null
    private fun lay(from: Int, to: Int) { layer = from; layerTop = to }

    private fun piv(out: FloatArray, parent: FloatArray, px: Float, py: Float, pz: Float, rx: Float = 0f, ry: Float = 0f, rz: Float = 0f) {
        System.arraycopy(parent, 0, out, 0, 16)
        Matrix.translateM(out, 0, px, py, pz)
        if (rz != 0f) Matrix.rotateM(out, 0, rz, 0f, 0f, 1f)
        if (ry != 0f) Matrix.rotateM(out, 0, ry, 0f, 1f, 0f)
        if (rx != 0f) Matrix.rotateM(out, 0, rx, 1f, 0f, 0f)
    }

    private fun swim01(an: Anim) = if (an.swim > 0.2f) 1f else 0f
    private fun lite(c: Int, k: Float) = mixC(c, 0xFFFFFF, k)
    private fun lp(a: Float, b: Float, k: Float) = a + (b - a) * k
    private fun sm01(x: Float) = if (x <= 0f) 0f else if (x >= 1f) 1f else x * x * (3f - 2f * x)

    companion object {
        private val STROKE_KF = arrayOf(floatArrayOf(26f, -10f, -6f), floatArrayOf(52f, -18f, -26f), floatArrayOf(84f, -30f, -62f),
            floatArrayOf(64f, -42f, -72f), floatArrayOf(26f, -10f, -6f), floatArrayOf(26f, -10f, -6f))
        private val STROKE_T = floatArrayOf(0f, 0.22f, 0.46f, 0.68f, 0.92f, 1f)
        private fun armIk(yaw: Float, ev: Float, s: Float, out: FloatArray) {
            val y = Math.toRadians(yaw.toDouble()).toFloat(); val e = Math.toRadians(ev.toDouble()).toFloat()
            val dx = s * sin(y) * cos(e); val dy = cos(y) * cos(e); val dz = -sin(e)
            val h = sqrt(dx * dx + dy * dy)
            var rx = Math.toDegrees(atan2(-dz, -h).toDouble()).toFloat(); if (rx > 0f) rx -= 360f
            out[0] = rx; out[1] = Math.toDegrees(atan2(-dx, dy).toDouble()).toFloat()
        }
        const val U = 0.05625f
        private val HAIR_BK = floatArrayOf(5f, 4f, 9f, 6f, 6f, 6f, 16f, 8f, 3f, 6f, 5f, 5f, 4f, 0.8f, 1f, 5f, 14f, 6f, 5f, 5f, 5f, 3f, 4f, 4f, 6f, 6f, 8f, 11f, 5f, 5f, 5f, 10f, 16f, 12f, 13f, 14f, 7f, 1.5f, 6f, 6f, 4f, 8f, 6f, 6f, 15f, 7f, 7f, 13f, 10f, 6f)
        private val HAIR_SL = floatArrayOf(3f, 2.5f, 9f, 5f, 4f, 4f, 12f, 6f, 1.5f, 4f, 3f, 3f, 2.5f, 0.5f, 0.5f, 3.5f, 12f, 4f, 3f, 3f, 3f, 1.5f, 2f, 2f, 4f, 5f, 6f, 3f, 3f, 4f, 3f, 6f, 12f, 10f, 10f, 10f, 5f, 1f, 6f, 4f, 3f, 8f, 4f, 4f, 11f, 5f, 5f, 9f, 8f, 4f)
        private val HAIR_BG = intArrayOf(1, 5, 3, 2, 1, 2, 3, 2, 7, 4, 5, 1, 5, 0, 0, 6, 3, 2, 2, 2, 2, 1, 4, 1, 2, 0, 3, 1, 1, 6, 0, 5, 3, 2, 2, 2, 4, 0, 3, 2, 4, 4, 1, 0, 3, 4, 2, 2, 2, 2)
        private val HAIR_TP = floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 0f, 1f, 2f, 2f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0.5f, 0f, 3f, 2f, 4f, 0f, 0f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        private val HAIR_EXT = intArrayOf(0, 1, 0, 2, 3, 4, 0, 5, 0, 0, 1, 6, 23, 0, 7, 0, 0, 8, 9, 10, 11, 0, 0, 6, 12, 13, 14, 0, 15, 0, 0, 1, 0, 5, 12, 14, 0, 0, 0, 16, 0, 0, 3, 4, 12, 18, 19, 20, 12, 22)
   // 1 pixel do Minecraft em blocos (1,8 / 32)
    }

    /** root = origem nos pés. handOut = matriz da mão direita (pra segurar item). */
    fun draw(boxIn: BoxFn, root: FloatArray, an: Anim, handOut: FloatArray?) {
        var zf = 0
        layer = 0; layerTop = 0
        bx = { m, px, py, pz, rx, ry, sx, sy, sz, oy, col, a, rz ->
            val k = 1f + min(zf, 300) * 0.00004f; zf++   // cada caixa nova fica um tiquinho maior
            layerHook?.invoke(layer)
            if (layer < layerTop) layer++   // dentro de uma seção, cada caixa nova fica por cima da anterior
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
        val landBig = (an.landT - 1f).coerceIn(0f, 0.5f) * 2f   // aterrissagem de super pulo: afunda mais e demora mais pra levantar
        val jch = an.jch.coerceIn(0f, 1f); val sj = an.sj.coerceIn(0f, 1f)   // carregando o pulo / no ar de um super pulo
        val rs = sm01(vk); val fa2 = sm01(-vk); val ap = (1f - rs - fa2).coerceIn(0f, 1f)   // pesos contínuos: subindo / topo / caindo (sem trancos)
        val leadL = if (sin(ph) < 0f) 1f else 0f   // a perna que estava na frente no passo é a que puxa o joelho no pulo
        val shiver = jch * jch * jch * sin(t * 62f) * 1.8f   // carga cheia: o corpo treme
        val legSq = 0.30f * jch + 0.22f * land + 0.12f * landBig   // pernas amassam (agachar de desenho animado)
        val dashW = max(an.dash.coerceIn(0f, 1f), an.imp.coerceIn(0f, 1f)) * (1f - swim01(an))
        val uImp = 1f - an.imp.coerceIn(0f, 1f)
        val wind = if (an.imp <= 0f) 0f else if (uImp < 0.4f) uImp / 0.4f else max(0f, 1f - (uImp - 0.4f) / 0.25f)
        // subindo / descendo no voo (suave, pela velocidade vertical): subir = corpo reto, braços em V pra cima, pernas juntas e soltas; descer = braços abertos, pernas abertas planando
        val fvy = an.vy / 5f
        val fu = if (swimming) 0f else sm01((fvy - 0.12f) / 0.7f) * fl * (1f - dashW)
        val fd = if (swimming) 0f else sm01((-fvy - 0.12f) / 0.7f) * fl * (1f - dashW)
        val atk = an.atk; val atking = atk < 1f
        val stride = if (swimming) 0f else sm01(mv * 2.2f) * (1f - ab) * (1f - fl)   // entra suave (antes: cheio em 0,05s com a fase ainda parada = travada)
        var sw = sin(ph) * (26f + 30f * rn) * stride
        var hipL = sw; var hipR = -sw
        var kneeL = max(0f, -cos(ph)) * (12f + 50f * rn) * stride
        var kneeR = max(0f, cos(ph)) * (12f + 50f * rn) * stride
        val rw = rn * stride   // peso da corrida de verdade (no chão)
        if (rw > 0.001f) {
            // ciclo de corrida: perna de balanço sobe a coxa e dobra o joelho forte (recuperação), apoia à frente absorvendo no joelho e empurra esticando atrás
            fun rHip(p: Float): Float { val s = sin(p); val c = cos(p); return 40f * s - 8f - 10f * max(0f, -c) }
            fun rKnee(p: Float): Float { val s = sin(p); val c = cos(p)
                return 100f * Math.pow(max(0f, -c).toDouble(), 1.25).toFloat() + 24f * max(0f, c) * max(0f, -s) * 1.6f + 7f * max(0f, c) * max(0f, s) }
            hipL = lp(hipL, rHip(ph), rw); hipR = lp(hipR, rHip(ph + 3.1416f), rw)
            kneeL = lp(kneeL, rKnee(ph), rw); kneeR = lp(kneeR, rKnee(ph + 3.1416f), rw)
        }
        if (swimming) {
            val sa = sin(t * 8f); val sb = sin(t * 4.6f); val cb = cos(t * 4.6f)
            // na superfície: pedalada; mergulhado: batida de perna reta (crawl)
            hipL = lp(sb * 26f, sa * 30f, dv); hipR = lp(-sb * 26f, -sa * 30f, dv)
            kneeL = lp(30f + cb * 24f, 8f + sa * 10f, dv); kneeR = lp(30f - cb * 24f, 8f - sa * 10f, dv)
            // acelerando na superfície: as pernas sobem um pouco e batem rápido (pernada de crawl), bem diferente do boiar parado
            val kf = an.swimFast.coerceIn(0f, 1f) * (1f - dv)
            if (kf > 0.001f) {
                val kk = t * 17f; val ks = sin(kk)
                hipL = lp(hipL, 6f + ks * 20f, kf); hipR = lp(hipR, 6f - ks * 20f, kf)
                kneeL = lp(kneeL, 12f + 26f * max(0f, sin(kk + 1.1f)), kf); kneeR = lp(kneeR, 12f + 26f * max(0f, -sin(kk + 1.1f)), kf)
            }
        }
        if (ab > 0.01f) {
            // 3 poses que se misturam pelo vy: pernas quase retas, só um passinho aberto na subida, juntinhas no topo e esticadas descendo (sem chutar pra frente)
            val hLd = rs * lp(-14f, 6f, sj) + ap * lp(-18f, -26f, sj) + fa2 * lp(-8f + sin(t * 9f) * 1.5f, -6f, sj)
            val kLd = rs * lp(10f, 4f, sj) + ap * lp(22f, 34f, sj) + fa2 * lp(4f, 4f, sj)
            val hTr = rs * lp(12f, 8f, sj) + ap * lp(-6f, -20f, sj) + fa2 * lp(6f, 2f, sj)
            val kTr = rs * lp(14f, 6f, sj) + ap * lp(14f, 30f, sj) + fa2 * lp(4f, 4f, sj)
            hipL = lp(hipL, lp(hTr, hLd, leadL), ab); kneeL = lp(kneeL, lp(kTr, kLd, leadL), ab)
            hipR = lp(hipR, lp(hLd, hTr, leadL), ab); kneeR = lp(kneeR, lp(kLd, kTr, leadL), ab)
        }
        if (fl > 0.01f) {
            // voando parado: as duas pernas retas, um pouco abertas (ver splay), com um balanço leve; andando, juntas e esticadas pra trás
            val hov = 1f - fm
            val base = -4f - 12f * fm + sin(t * 1.7f) * 2.5f * hov
            val fkU = sin(t * 6f) * 4f * fu; val fkD = sin(t * 4.6f) * 3f * fd   // subindo: pés batem de leve; descendo: pernas balançam soltas
            val hipT = base + 10f * fu - 12f * fd
            val kneeT = 4f + 3f * fm + 8f * fu + 16f * fd
            hipL = lp(hipL, hipT + fkU + fkD + sin(t * 7f) * 1.2f * fm, fl); hipR = lp(hipR, hipT - fkU - fkD - sin(t * 7f) * 1.2f * fm - sin(t * 1.7f) * 1.5f * hov, fl)
            kneeL = lp(kneeL, kneeT + max(0f, fkU) * 0.8f, fl); kneeR = lp(kneeR, kneeT + max(0f, -fkU) * 0.8f, fl)
        }
        if (dashW > 0.001f && fl > 0.01f) {
            val fk2 = sin(t * 14f) * 1.5f
            hipL = lp(hipL, 8f + fk2 + 20f * wind, dashW); hipR = lp(hipR, 8f + fk2 + 20f * wind, dashW)
            kneeL = lp(kneeL, 8f + 55f * wind, dashW); kneeR = lp(kneeR, 8f + 55f * wind, dashW)
        }
        val dch = an.dch.coerceIn(0f, 1f); val dst = an.dst.coerceIn(0f, 1f); val tuck = dch * (1f - dst)
        val lw0 = an.launch.coerceIn(0f, 1f); val lwE = lw0 * lw0 * (3f - 2f * lw0)
        if (tuck > 0.001f) {   // carregando o dash aéreo: as duas pernas encolhidas contra o peito (joelhos pra frente), tomando impulso
            hipL = lp(hipL, -74f, tuck); hipR = lp(hipR, -66f, tuck)
            kneeL = lp(kneeL, 108f, tuck); kneeR = lp(kneeR, 100f, tuck)
        }
        val tkw = an.trk.coerceIn(0f, 1f); val tkk = an.trkK.toInt(); val tkFast = an.trkF > 0.5f
        val tkFlip = tkk == 1 || tkk == 2 || (!tkFast && (tkk == 12 || tkk == 13))   // mortal / giro de costas: corpo encolhido
        val tkSpin = tkk == 5 && !tkFast   // pirueta: braços pro alto, bailarino
        val tkSuper = tkFast && !tkFlip   // voo rápido (tonel/espiral): esticado, braços à frente, pose de super-herói
        val tkP = an.trkP.coerceIn(0f, 1f)
        // fases da manobra: preparação (agacha, braços atrás) -> impulso (estica, braços pro alto) -> grupado/pose do giro -> abertura (prepara a queda)
        val wCr = sin(3.1416f * (tkP / 0.17f).coerceIn(0f, 1f)) * tkw * (1f - 0.7f * (if (tkFast) 1f else 0f))
        val wLa = sin(3.1416f * ((tkP - 0.09f) / 0.24f).coerceIn(0f, 1f)) * tkw
        val wTu = sm01((tkP - 0.20f) / 0.12f) * (1f - sm01((tkP - 0.68f) / 0.14f)) * tkw
        val wOp = sm01((tkP - 0.66f) / 0.16f) * (1f - sm01((tkP - 0.94f) / 0.06f)) * tkw
        val wBd = sm01((tkP - 0.06f) / 0.20f) * (1f - sm01((tkP - 0.84f) / 0.14f)) * tkw
        if (tkw > 0.001f) {
            hipL = lp(hipL, -26f, wCr); hipR = lp(hipR, -20f, wCr); kneeL = lp(kneeL, 58f, wCr); kneeR = lp(kneeR, 50f, wCr)
            hipL = lp(hipL, 4f, wLa); hipR = lp(hipR, 2f, wLa); kneeL = lp(kneeL, 3f, wLa); kneeR = lp(kneeR, 3f, wLa)
            if (tkFlip) {   // grupado de verdade (joelhos no peito) e depois abre as pernas pra baixo, uma à frente da outra, pra pousar
                hipL = lp(hipL, -76f, wTu); hipR = lp(hipR, -68f, wTu); kneeL = lp(kneeL, 116f, wTu); kneeR = lp(kneeR, 108f, wTu)
                hipL = lp(hipL, -16f, wOp); hipR = lp(hipR, 6f, wOp); kneeL = lp(kneeL, 26f, wOp); kneeR = lp(kneeR, 8f, wOp)
            } else if (tkSpin) {   // pirueta: perna de apoio esticada e a outra em "passé" (pé no joelho)
                hipL = lp(hipL, 3f, wBd); kneeL = lp(kneeL, 3f, wBd); hipR = lp(hipR, -40f, wBd); kneeR = lp(kneeR, 100f, wBd)
            } else if (tkSuper) {   // voo rápido: corpo reto como flecha, pernas juntas
                hipL = lp(hipL, 5f, wBd); hipR = lp(hipR, 5f, wBd); kneeL = lp(kneeL, 4f, wBd); kneeR = lp(kneeR, 4f, wBd)
            } else {   // parafuso/saca-rolha: corpo em arco (pernas atrás, joelhos dobrados)
                hipL = lp(hipL, 14f, wBd); hipR = lp(hipR, 10f, wBd); kneeL = lp(kneeL, 34f, wBd); kneeR = lp(kneeR, 28f, wBd)
            }
        }
        if (dst > 0.001f) {   // pisada no ar: a perna direita estica de uma vez pra trás (empurra o ar), a esquerda continua dobrada
            hipR = lp(hipR, 14f, dst); kneeR = lp(kneeR, 2f, dst)
            hipL = lp(hipL, -34f, dst * 0.85f); kneeL = lp(kneeL, 70f, dst * 0.85f)
        }
        if (lwE > 0.001f) {   // decolagem do super pulo: pernas juntinhas e esticadas, relaxando pra pose de voo
            hipL = lp(hipL, 4f, lwE * 0.7f); hipR = lp(hipR, 4f, lwE * 0.7f)
            kneeL = lp(kneeL, 8f, lwE * 0.7f); kneeR = lp(kneeR, 6f, lwE * 0.7f)
        }
        if (land > 0f) {
            hipL += (-8f - hipL) * land; hipR += (4f - hipR) * land
            kneeL += (6f - kneeL) * land; kneeR += (6f - kneeR) * land
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
        // ---- SKATISTA: perna da frente = hipR/kneeR (sd>0, +X), perna de trás = hipL/kneeL (sd<0, -X); rz abre os pés ao longo da prancha ----
        val skw = an.sk.coerceIn(0f, 1f)
        var spF = 0f; var spR = 0f
        if (skw > 0.001f) {
            val kSc = an.crouch.coerceIn(0f, 1f); val kPop = an.skPop.coerceIn(0f, 1f); val kLand = an.skLand.coerceIn(0f, 1f)
            val kGr = an.skGrab.coerceIn(0f, 1f); val kFl = an.skFlip.coerceIn(0f, 1f); val kGd = an.skGrind.coerceIn(0f, 1f)
            val kMan = an.skMan.coerceIn(0f, 1f); val kPw = an.skPush.coerceIn(0f, 1f)
            val sway = sin(an.t * 1.9f) * 2f
            var fH = -14f - 40f * kSc - 14f * kLand + sway; var fK = 24f + 46f * kSc + 24f * kLand
            var rH = -10f - 40f * kSc - 14f * kLand - sway; var rK = 28f + 46f * kSc + 26f * kLand
            var sF = 14f + 4f * kSc; var sR = 14f + 4f * kSc
            fH = lp(fH, -4f, kPop * 0.85f); fK = lp(fK, 6f, kPop * 0.85f); rH = lp(rH, -2f, kPop * 0.85f); rK = lp(rK, 4f, kPop * 0.85f)   // ollie: pernas esticam
            fH = lp(fH, -30f, kMan); fK = lp(fK, 20f, kMan); sF = lp(sF, 18f, kMan)                                                         // manual: peso atrás
            sF = lp(sF, 19f, kGd); sR = lp(sR, 19f, kGd)                                                                                    // grind: base larga
            fH = lp(fH, -34f, kFl * 0.8f); fK = lp(fK, 58f, kFl * 0.8f); rH = lp(rH, -22f, kFl * 0.8f); rK = lp(rK, 70f, kFl * 0.8f)       // flip: pernas recolhidas
            if (kPw > 0.001f) {   // empurrão: a perna de trás desce até o chão, varre pra trás e volta dobrada; a da frente agacha
                val phase = an.skPushPh.coerceIn(0f, 1f)
                val h2: Float; val k2: Float; val s2: Float
                when {
                    phase < 0.18f -> {
                        val e = phase / 0.18f
                        val smooth = e * e * (3f - 2f * e)
                        // O pé sai para o lado da prancha antes de tocar o chão.
                        s2 = 16f + 15f * smooth
                        h2 = -5f + 4f * smooth
                        k2 = 12f + 12f * smooth
                    }
                    phase < 0.68f -> {
                        val e = (phase - 0.18f) / 0.50f
                        val drive = sin(e * 3.1416f)
                        // A perna estende para trás durante o impulso, sem cruzar a prancha.
                        s2 = 31f - 5f * e
                        h2 = 2f + 30f * drive
                        k2 = 24f + 42f * drive
                    }
                    else -> {
                        val e = ((phase - 0.68f) / 0.32f).coerceIn(0f, 1f)
                        val smooth = e * e * (3f - 2f * e)
                        s2 = 26f - 10f * smooth
                        h2 = 2f - 7f * smooth
                        k2 = 24f - 12f * smooth
                    }
                }
                rH = lp(rH, h2, kPw); rK = lp(rK, k2, kPw); sR = lp(sR, s2, kPw)
                fH = lp(fH, -32f, kPw); fK = lp(fK, 56f, kPw)
            }
            fH = lp(fH, -105f, kGr); fK = lp(fK, 135f, kGr); rH = lp(rH, -95f, kGr); rK = lp(rK, 130f, kGr); sF = lp(sF, 7f, kGr); sR = lp(sR, 7f, kGr)   // grab: joelhos no peito
            hipR = lp(hipR, fH, skw); kneeR = lp(kneeR, fK, skw); hipL = lp(hipL, rH, skw); kneeL = lp(kneeL, rK, skw)
            spF = sF * skw; spR = sR * skw
        }
        if (li > 0f) { val mc = min(1f, mv * 3f); qLegL = (0.5f + 0.5f * sin(ph + 3.1416f)) * mc; qLegR = (0.5f + 0.5f * sin(ph)) * mc
            hipL = lp(hipL, 2f + 7f * qLegL, li); hipR = lp(hipR, 2f + 7f * qLegR, li)
            kneeL = lp(kneeL, 4f + 26f * qLegL, li); kneeR = lp(kneeR, 4f + 26f * qLegR, li) }
        val drop = 0.12f * cr + 0.545f * si + 0.675f * legSq + 0.14f * tuck + skw * (0.035f + 0.09f * an.skPush.coerceIn(0f, 1f) + 0.2f * an.skGrab.coerceIn(0f, 1f))
        val bob = sin(t * 2f) * 0.012f * (1f - stride) + abs(sin(ph)) * (0.035f + 0.075f * rn) * stride * (1f - 0.85f * li) + fl * (1f - fm) * sin(t * 1.6f) * 0.09f   // pairando: sobe e desce devagar
        val move = stride * (1f + rn * 0.6f) + ab * lp(0.4f, 1.2f, fs)

        System.arraycopy(root, 0, r2, 0, 16)
        Matrix.translateM(r2, 0, 0f, bob - drop, 0f)
        if (an.flip != 0f) {   // cambalhota do super pulo: gira o corpo todo em volta do centro
            Matrix.translateM(r2, 0, 0f, 0.9f, 0f); Matrix.rotateM(r2, 0, an.flip, 1f, 0f, 0f); Matrix.translateM(r2, 0, 0f, -0.9f, 0f)
        }
        if (li > 0.001f) {   // deitado de costas: gira o corpo todo em volta do centro
            Matrix.translateM(r2, 0, 0f, 0.2f * li, -0.7f * li)   // rastejando, de frente (barriga no chão)
            Matrix.rotateM(r2, 0, 90f * li, 1f, 0f, 0f)
        }

        val lean = 30f * an.dash.coerceIn(0f, 1f) * fl - 30f * wind * dashW + 18f * cr + 6f * si + 3f * stride * (1f - li) - 7f * li + 18f * rn * stride + ab * (rs * lp(-4f, 14f, sj) + ap * lp(0f, 30f, sj) + fa2 * lp(7f, 12f, sj)) + 10f * land + 30f * jch + 6f * landBig + fl * (8f + 52f * fm) * (1f - 0.78f * fu) + 14f * fd - 4f * fu - 10f * chg + 22f * tuck + 26f * dst - an.vp * an.dash.coerceIn(0f, 1f) * fl - 24f * lwE + skw * (-10f * an.skMan - 6f * an.skPop + 10f * an.skGrab) +
            (if (atking) 9f * sin(atk * 3.1416f) else 0f)
        val swimK = if (swimming) an.swimFast.coerceIn(0f, 1f) * (1f - dv) else 0f
        val twist = sin(an.swimPh) * 34f * swimK + sin(ph) * (5f + 15f * rn) * stride * (1f - li) + 14f * chg + (if (atking) 18f * sin(atk * 6.2832f) else 0f)
        val roll = sin(ph) * 2.2f * stride * (1f - rn) + 3f * sin(ph) * rn * stride + 8f * li * min(1f, mv * 3f) * sin(ph) + (if (air) 0f else sin(t * 0.9f) * 1.3f * (1f - stride)) + shiver

        // ================= ESQUELETO: quadril em 0,675 (12 px), pescoço em 1,35 (24 px), topo da cabeça em 1,8 =================
        val hipY = 12f * U
        System.arraycopy(r2, 0, bm, 0, 16)
        Matrix.translateM(bm, 0, 0f, hipY, 0f)
        if (roll != 0f) Matrix.rotateM(bm, 0, roll, 0f, 0f, 1f)
        if (twist != 0f) Matrix.rotateM(bm, 0, twist, 0f, 1f, 0f)
        if (lean != 0f) Matrix.rotateM(bm, 0, lean, 1f, 0f, 0f)
        val stretch = ab * rs * (0.025f + 0.05f * sj)   // esticadinha na subida
        val ssx = 1f + 0.06f * land + 0.05f * jch + 0.04f * landBig - stretch * 0.6f
        val ssy = 1f - 0.08f * land - 0.07f * jch - 0.05f * landBig + stretch
        if (ssx != 1f || ssy != 1f) Matrix.scaleM(bm, 0, ssx, ssy, ssx)
        Matrix.translateM(bm, 0, 0f, -hipY, 0f)

        val look = sin(t * 0.55f) * sin(t * 0.23f)
        val hy = if (swimK > 0.01f) -twist * 0.55f + 30f * swimK * sm01(sin(an.swimPh * 0.5f + 0.4f) * 1.6f - 0.3f) else if (stride > 0.1f) -twist * 0.7f else look * 16f * (1f - stride)   // nadando: gira a cabeça pra respirar a cada braçada
        val hnod = -lean * 0.55f + sin(ph * 2f) * (1.8f + 2.2f * rn) * stride + sin(t * 1.3f) * 1.4f * (1f - stride) + ab * (-6f * rs + 6f * fa2) - 10f * jch - an.tilt * lp(0.9f, 0.5f, dv) - 70f * li - 11f * fu + 7f * fd
        val htilt = sin(t * 0.8f) * 2f * (1f - stride)
        System.arraycopy(bm, 0, hm, 0, 16)
        Matrix.translateM(hm, 0, 0f, 24f * U, 0f)
        if (htilt != 0f) Matrix.rotateM(hm, 0, htilt, 0f, 0f, 1f)
        if (hy != 0f) Matrix.rotateM(hm, 0, hy, 0f, 1f, 0f)
        if (hnod != 0f) Matrix.rotateM(hm, 0, hnod, 1f, 0f, 0f)
        val blink = (t % 3.7f) > 3.58f || (t % 6.1f) > 6.0f

        // ================= POSE DOS BRAÇOS (mesma lógica do boneco antigo) =================
        val toolArm = an.hasTool
        val asw = sw * (0.95f + 0.5f * rn)
        var rxL = asw - 4f; var rxR = -asw - 4f   // braço direito (sd<0) balança contra a perna direita
        var elL = -(8f + 6f * stride + 58f * rn * stride); var elR = elL
        var rzL = -(4f + sin(t * 2f) * 1.5f * (1f - stride)); var rzR = -rzL
        val aa = an.atkArm
        val ikL = FloatArray(2); val ikR = FloatArray(2)
        val atkRx = if (an.poseOn) -an.pP * 0.95f else aa
        var ryR = 0f; var ryL = 0f
        if (rw > 0.001f) {   // braços da corrida: cotovelo bem dobrado (mais ainda quando o braço vai à frente), balanço maior à frente que atrás, cruzando de leve pro centro
            val fL = (max(0f, -asw) / 56f).coerceIn(0f, 1f); val fR = (max(0f, asw) / 56f).coerceIn(0f, 1f)
            rxL = lp(rxL, (if (asw < 0f) asw * 1.0f else asw * 0.75f) - 4f, rw); rxR = lp(rxR, (if (asw > 0f) -asw * 1.0f else -asw * 0.75f) - 4f, rw)
            elL = lp(elL, -(58f + 40f * fL), rw); elR = lp(elR, -(58f + 40f * fR), rw)
            rzL = lp(rzL, -(5f - 4.5f * fL), rw); rzR = lp(rzR, 5f - 4.5f * fR, rw)
        }
        if (toolArm) { rxR = -22f + atkRx - sw * 0.25f * (1f - rn * 0.5f); elR = -6f - 6f * rn * stride; rzR = 20f
            if (an.poseOn) { ryR = an.pY * 0.9f; elR = (elR + an.pW * 0.5f).coerceIn(-70f, 10f) } }
        else if (atking || chg > 0.01f) { rxR += atkRx; if (an.poseOn) ryR = an.pY * 0.9f }
        var swimToolA = 0f
        if (swimming) {
            // cabeça fora: braçada alternada e calma (alcança, puxa pra baixo, volta com o cotovelo dobrado). Mergulhado: braçada de peito (V à frente, abre, puxa pros lados e volta).
            val fa = an.swimFast.coerceIn(0f, 1f); val sa = an.swimPh; val pi = 3.1416f   // fa: só acelerando os braços batem
            val tOff = max(0f, an.tilt - 32f) * (1f - dv) * fa   // corpo mais deitado: os braços sobem junto pra continuarem alcançando à frente
            val sl2 = sin(t * 2.3f)
            // sem acelerar: na superfície os bracinhos batem leve pra boiar (parado ou andando); mergulhado os dois braços ficam inclinados pra trás, nas diagonais
            val lSurfX = -25f + sl2 * 14f; val lSurfZ = -(38f + sin(t * 2.3f + 1.2f) * 16f); val lSurfE = -20f + sl2 * 8f
            val diagX = 14f + sin(t * 2.5f) * 3f; val diagZ = 30f + sin(t * 2.5f + 1f) * 3f; val diagE = -6f
            val recL = max(0f, cos(sa)); val recR = max(0f, cos(sa + pi))
            val sXL = lp(lSurfX, -67f - 55f * sin(sa), fa) - tOff; val sXR = lp(lSurfX, -67f - 55f * sin(sa + pi), fa) + atkRx - tOff
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
            // braços acompanham as 3 poses: sobem à frente na subida, abrem no topo e ficam abertos pra equilibrar na queda
            val flapA = sin(t * 9f) * 5f * fa2
            val pxA = rs * lp(-62f, -14f, sj) + ap * lp(-22f, -78f, sj) + fa2 * lp(-8f, -10f, sj)
            val pzA = rs * lp(24f, 160f, sj) + ap * lp(62f, 22f, sj) + fa2 * lp(50f, 88f, sj)
            val peA = rs * lp(-12f, -4f, sj) + ap * lp(-8f, -66f, sj) + fa2 * lp(-6f, -8f, sj)
            rxL = lp(rxL, pxA + flapA, ab); rzL = lp(rzL, -pzA, ab); elL = lp(elL, peA, ab)
            // os dois braços iguais (o do item também), com o golpe somado por cima
            rxR = lp(rxR, pxA - flapA + atkRx, ab); rzR = lp(rzR, pzA, ab); elR = lp(elR, peA, ab)
        }
        if (jch > 0.01f) {   // carregando o super pulo: braços jogados pra trás, tomando impulso (o braço do item fica como está)
            val k = min(1f, jch * 2.5f)
            rxL = lp(rxL, 46f * jch + 4f, k); rzL = lp(rzL, -(14f + 10f * jch), k); elL = lp(elL, -(10f + 24f * jch), k)
            if (!toolArm) { rxR = lp(rxR, 46f * jch + 4f, k); rzR = lp(rzR, 14f + 10f * jch, k); elR = lp(elR, -(10f + 24f * jch), k) }
        }
        if (fl > 0.01f) {
            // os dois braços ficam pra baixo, iguais ao do item: só um balanço leve
            val fw = sin(t * 3f) * 4f * (1f - fm)
            val dn = lp(-14f, -26f, fm)
            rxL = lp(rxL, dn + sin(t * 2.6f) * 3f, fl); rzL = lp(rzL, -(16f + fw), fl); elL = lp(elL, -8f, fl)
            if (toolArm) { rxR = lp(rxR, lp(-24f, -40f, fm) + atkRx, fl); rzR = lp(rzR, 20f, fl) }
            else { rxR = lp(rxR, dn - sin(t * 2.6f) * 3f, fl); rzR = lp(rzR, 16f + fw, fl); elR = lp(elR, -8f, fl) }
        }
        if (fu > 0.001f || fd > 0.001f) {   // braços só reagem ao ar, bem de leve: subindo ficam levemente atrás/abertos (arrastados); descendo o ar os levanta um pouquinho
            val sw1 = sin(t * 2.6f) * 3f; val sw2 = sin(t * 2.1f + 1f) * 3f
            rxL = lp(rxL, 16f + sw1, fu * 0.8f); rzL = lp(rzL, -(24f + sw2), fu * 0.8f); elL = lp(elL, -10f, fu * 0.8f)
            rxL = lp(rxL, -10f + sw2, fd * 0.8f); rzL = lp(rzL, -(42f + sw1), fd * 0.8f); elL = lp(elL, -8f, fd * 0.8f)
            if (!toolArm) {
                rxR = lp(rxR, 16f - sw1, fu * 0.8f); rzR = lp(rzR, 24f - sw2, fu * 0.8f); elR = lp(elR, -10f, fu * 0.8f)
                rxR = lp(rxR, -10f - sw2, fd * 0.8f); rzR = lp(rzR, 42f - sw1, fd * 0.8f); elR = lp(elR, -8f, fd * 0.8f)
            }
        }
        if (tkw > 0.001f) {
            rxL = lp(rxL, 38f, wCr); rzL = lp(rzL, -12f, wCr); elL = lp(elL, -8f, wCr)
            rxL = lp(rxL, -172f, wLa); rzL = lp(rzL, -10f, wLa); elL = lp(elL, -4f, wLa)
            if (!toolArm) {
                rxR = lp(rxR, 38f, wCr); rzR = lp(rzR, 12f, wCr); elR = lp(elR, -8f, wCr)
                rxR = lp(rxR, -172f, wLa); rzR = lp(rzR, 10f, wLa); elR = lp(elR, -4f, wLa)
            }
            if (tkFlip) {   // mãos nas canelas no grupado; depois abre os braços pra equilibrar a queda
                rxL = lp(rxL, -72f, wTu); rzL = lp(rzL, -8f, wTu); elL = lp(elL, -98f, wTu)
                rxL = lp(rxL, -22f, wOp); rzL = lp(rzL, -66f, wOp); elL = lp(elL, -6f, wOp)
                if (!toolArm) {
                    rxR = lp(rxR, -72f, wTu); rzR = lp(rzR, 8f, wTu); elR = lp(elR, -98f, wTu)
                    rxR = lp(rxR, -22f, wOp); rzR = lp(rzR, 66f, wOp); elR = lp(elR, -6f, wOp)
                }
            } else if (tkSpin) {
                rxL = lp(rxL, -172f, wBd); rzL = lp(rzL, -6f, wBd); elL = lp(elL, -6f, wBd)
                if (!toolArm) { rxR = lp(rxR, -172f, wBd); rzR = lp(rzR, 6f, wBd); elR = lp(elR, -6f, wBd) }
            } else if (tkSuper) {
                rxL = lp(rxL, -166f, wBd); rzL = lp(rzL, -7f, wBd); elL = lp(elL, -3f, wBd)
                if (!toolArm) { rxR = lp(rxR, -166f, wBd); rzR = lp(rzR, 7f, wBd); elR = lp(elR, -3f, wBd) }
            } else {
                rxL = lp(rxL, 0f, wBd); rzL = lp(rzL, -80f, wBd); elL = lp(elL, -4f, wBd)
                if (!toolArm) { rxR = lp(rxR, 0f, wBd); rzR = lp(rzR, 80f, wBd); elR = lp(elR, -4f, wBd) }
            }
        }
        if (tuck > 0.001f) {   // dash aéreo carregando: braços fechados na frente do corpo, em guarda
            val k = min(1f, tuck * 3f)
            rxL = lp(rxL, -40f - 20f * tuck, k); rzL = lp(rzL, -(10f + 6f * tuck), k); elL = lp(elL, -(60f + 20f * tuck), k)
            if (!toolArm) { rxR = lp(rxR, -40f - 20f * tuck, k); rzR = lp(rzR, 10f + 6f * tuck, k); elR = lp(elR, -(60f + 20f * tuck), k) }
        }
        if (lwE > 0.001f) {   // decolagem: os dois braços sobem acima da cabeça e vão relaxando
            rxL = lp(rxL, -168f, lwE); rzL = lp(rzL, -10f, lwE); elL = lp(elL, -4f, lwE)
            if (!toolArm) { rxR = lp(rxR, -168f, lwE); rzR = lp(rzR, 10f, lwE); elR = lp(elR, -4f, lwE) }
        }
        var dashToolA = 0f
        if (dashW > 0.001f && fl > 0.01f) {   // voo rápido: braços pra trás, rentes ao corpo (puxa mais no impulso)
            val dwA = dashW * (1f - min(1f, tkw * 2.5f))   // durante a manobra a pose dela manda
            val ax = lp(14f, 36f, wind); val fl2 = sin(t * 9f) * 3f
            rxL = lp(rxL, ax + fl2, dwA); rxR = lp(rxR, ax - fl2, dwA)
            rzL = lp(rzL, -9f, dwA); rzR = lp(rzR, 9f, dwA); elL = lp(elL, -5f, dwA); elR = lp(elR, -5f, dwA)
            dashToolA = dwA * (-lean + 180f - (rxR + elR))   // item fica pra trás, longe da cabeça
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
        if (skw > 0.001f) {   // braços de skatista: abertos pro equilíbrio, balançam no empurrão, sobem no ollie, um pega a prancha no grab
            val kPop = an.skPop.coerceIn(0f, 1f); val kLand = an.skLand.coerceIn(0f, 1f); val kGr = an.skGrab.coerceIn(0f, 1f)
            val kMan = an.skMan.coerceIn(0f, 1f); val kGd = an.skGrind.coerceIn(0f, 1f); val kFl = an.skFlip.coerceIn(0f, 1f); val kPw = an.skPush.coerceIn(0f, 1f)
            val w1 = sin(an.t * 0.9f); val w2 = sin(an.t * 0.9f + 0.35f)
            var lx = -7f; var rx2 = -7f; var lz = 58f + 2f * w1; var rz2 = 58f + 2f * w2; var lel = -13f; var rel = -13f
            if (kPw > 0.001f) {
                // Contrapeso curto e controlado: tronco estável, sem braços chicoteando.
                val c = sin(an.skPushPh * 6.2832f)
                lx = lp(lx, -9f + 7f * c, kPw)
                rx2 = lp(rx2, -9f - 7f * c, kPw)
                lel = lp(lel, -12f + 3f * c, kPw)
                rel = lp(rel, -12f - 3f * c, kPw)
                lz = lp(lz, 62f + 4f * c, kPw)
                rz2 = lp(rz2, 62f - 4f * c, kPw)
            }
            lz = lp(lz, 118f, kPop); rz2 = lp(rz2, 118f, kPop); lel = lp(lel, -6f, kPop); rel = lp(rel, -6f, kPop)
            lz = lp(lz, 74f, kLand * 0.8f); rz2 = lp(rz2, 74f, kLand * 0.8f)
            lz = lp(lz, 84f, kMan); rz2 = lp(rz2, 84f, kMan)
            lz = lp(lz, 62f + 38f * an.skBal, kGd); rz2 = lp(rz2, 62f - 38f * an.skBal, kGd)
            lz = lp(lz, 80f, kFl); rz2 = lp(rz2, 80f, kFl)
            if (kGr > 0.001f) {
                val gk = an.skGrabK.toInt()
                val frontHand = gk == 2 || gk == 3 || gk == 4
                val gz = when (gk) { 4, 5 -> 40f; 2 -> 16f; 3 -> 8f; else -> 6f }
                if (frontHand) { lx = lp(lx, -20f, kGr); lz = lp(lz, gz, kGr); lel = lp(lel, -22f, kGr); rz2 = lp(rz2, 108f, kGr); rx2 = lp(rx2, 4f, kGr) }
                else { rx2 = lp(rx2, -20f, kGr); rz2 = lp(rz2, gz, kGr); rel = lp(rel, -22f, kGr); lz = lp(lz, 108f, kGr); lx = lp(lx, 4f, kGr) }
            }
            rxL = lp(rxL, lx, skw); rxR = lp(rxR, rx2, skw); rzL = lp(rzL, -lz, skw); rzR = lp(rzR, rz2, skw)
            elL = lp(elL, lel, skw); elR = lp(elR, rel, skw); ryL = lp(ryL, 0f, skw); ryR = lp(ryR, 0f, skw)
        }
        val rd = an.ride.coerceIn(0f, 1f)
        if (rd > 0.001f) { rxL = lp(rxL, -62f, rd); rxR = lp(rxR, -62f, rd); elL = lp(elL, -14f, rd); elR = lp(elR, -14f, rd); rzL = lp(rzL, -5f, rd); rzR = lp(rzR, 5f, rd) }

        // ================= ajudantes: tudo em PIXELS do Minecraft, relativo ao centro de cada parte =================
        fun hb(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, col: Int, a: Float = 1f, rx: Float = 0f, ry: Float = 0f, rz: Float = 0f) =
            bx(hm, x * U, 4f * U + y * U, z * U, rx, ry, w * U, h * U, d * U, 0f, col, a, rz)          // cabeça (centro da cabeça)
        fun tb(x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, col: Int, a: Float = 1f, rx: Float = 0f, ry: Float = 0f, rz: Float = 0f) =
            bx(bm, x * U, (18f + y) * U, z * U, rx, ry, w * U, h * U, d * U, 0f, col, a, rz)            // tronco (centro do tronco)
        var kbend = 0f; var curLeg: FloatArray? = null; val lkM = FloatArray(16)   // joelho de verdade (só no voo): caixas da perna abaixo do meio giram junto com a canela
        fun lb(m: FloatArray, x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, col: Int) {   // perna (centro da perna)
            if (kbend > 0.5f && m === curLeg) {
                val tp = y + h / 2f; val bt = y - h / 2f
                if (bt >= 0f) bx(m, x * U, (-6f + y) * U, z * U, 0f, 0f, w * U, h * U, d * U, 0f, col, 1f, 0f)
                else if (tp <= 0f) bx(lkM, x * U, y * U, z * U, 0f, 0f, w * U, h * U, d * U, 0f, col, 1f, 0f)
                else {
                    bx(m, x * U, (-6f + tp / 2f) * U, z * U, 0f, 0f, w * U, tp * U, d * U, 0f, col, 1f, 0f)
                    bx(lkM, x * U, (bt / 2f) * U, z * U, 0f, 0f, w * U, -bt * U, d * U, 0f, col, 1f, 0f)
                }
            } else bx(m, x * U, (-6f + y) * U, z * U, 0f, 0f, w * U, h * U, d * U, 0f, col, 1f, 0f)
        }
        fun ab(m: FloatArray, x: Float, y: Float, z: Float, w: Float, h: Float, d: Float, col: Int) =
            bx(m, x * U, (-4f + y) * U, z * U, 0f, 0f, w * U, h * U, d * U, 0f, col, 1f, 0f)             // braço (centro do braço)

        val hairC = Look.hair(); val hairD = shadeC(hairC, 0.8f); val hairL = lite(hairC, 0.25f)
        val eyeC = Look.EYE_COLORS[L[Look.EYEC].coerceIn(0, Look.EYE_COLORS.size - 1)]
        val shoeC = Look.pal(Look.SHOEC); val hatC = Look.pal(Look.HATC); val backC = Look.pal(Look.BACKC); val neckC = Look.pal(Look.NECKC)
        val leather = 0x6B4A2A; val steel = 0xC5CEDA
        val aw = if (girl) 3f else 4f   // Alex (menina): braços de 3 px

        // ================= PERNAS =================
        val pantPx = when { overalls -> 12f; full -> 0f; else -> when (bot) { 0 -> 12f; 1 -> 5f; 3 -> 8.5f; 4 -> 12f; 6 -> 12f; 7 -> 12f; else -> 0f } }
        val pantC = if (overalls) tc else if (bot == 6) shadeC(bc, 0.78f) else bc
        val pantW = if (bot == 4 && !full && !overalls) 6.4f else 4.6f
        var skirtLen = 0f; var skirtC = bc; var hemC = lite(bc, 0.35f)
        if (full) { skirtLen = when (top) { 2 -> 8.5f; 10 -> 11f; 11 -> 11.5f; else -> 8f }; skirtC = if (top == 15) shadeC(tc, 0.4f) else tc; hemC = if (top == 11) gold else if (top == 15) white else lite(tc, 0.4f) }
        else if (!overalls && (bot == 2 || bot == 5)) { skirtLen = if (bot == 2) 6f else 6.5f; skirtC = bc }
        else if (top == 4 && !overalls) { skirtLen = 3.5f; skirtC = tc; hemC = lite(tc, 0.4f) }
        else if (top == 17) { skirtLen = 8f; skirtC = tc; hemC = shadeC(tc, 0.7f) }
        val shoe = L[Look.SHOES]
        // a perna base já é pintada com a cor da roupa/sapato onde elas cobrem: se a perna girar e abrir uma fresta (quadril, entre as pernas, pé),
        // aparece tecido e não pele
        val covTop = if (pantPx > 0f) pantPx else skirtLen
        val covC = if (pantPx > 0f) pantC else shadeC(skirtC, 0.7f)
        val shoeH = when (shoe) { 0 -> 4f; 1 -> 2.4f; 4 -> 8f; 5 -> 2f; 6 -> 2f; 7 -> 6f; else -> 0f }
        for (sd in intArrayOf(-1, 1)) {
            val s = sd.toFloat(); val li2 = if (sd < 0) 0 else 1
            val hip = if (sd < 0) hipL else hipR; val kn = if (sd < 0) kneeL else kneeR
            val kr = max(max(max(fl, tkw), rw), skw)
            var ang = lp(hip - kn * 0.4f, hip, kr)   // perna rígida (sem joelho): o joelho só puxa um pouco o ângulo; no voo o joelho dobra de verdade (kbend)
            if (cr > 0f) ang = lp(ang, (if (sd < 0) 1f else -1f) * sw * 0.4f - 4f, cr)   // agachar do Minecraft: pernas quase retas, tronco inclinado
            if (jch > 0f) ang = lp(ang, -6f * jch, min(1f, jch * 3f))   // carregando: joelhos um pouco à frente
            if (bt > 0f) ang = lp(ang, -62f, bt)   // moto: pernas dobradas nas pedaleiras
            // pernas um pouco abertas no voo parado (abre pro lado em relação ao eixo da perna, que acompanha a inclinação do corpo)
            val splay = if (fl > 0.01f) (fl * (1f - dashW) * (9f * (1f - fm) + 2f * fm + 8f * fd - 7f * fu + sin(t * 1.7f + 1f) * 1.5f * (1f - fm)) * (1f - max(tuck, dst))).coerceAtLeast(0f) else 0f
            val legT = (ang + lean * fl) * 0.017453f
            piv(legM[li2], r2, s * 2f * U, hipY, 0f, rx = ang + lean * fl, ry = -s * splay * sin(legT), rz = s * (5f + 7f * (if (sd < 0) qLegL else qLegR)) * li + s * splay * cos(legT) + s * (if (sd < 0) spR else spF))
            if (legSq > 0f) Matrix.scaleM(legM[li2], 0, 1f + 0.4f * legSq, 1f - legSq, 1f + 0.4f * legSq)   // perna amassa e alarga
            val lm = legM[li2]
            kbend = if (kr > 0.01f) max(0f, kn) * kr else 0f; curLeg = lm
            if (kbend > 0.5f) piv(lkM, lm, 0f, -6f * U, 0f, rx = kbend)
            lay(0, 0)
            var topH = min(covTop, 12f); val botH = shoeH
            if (topH + botH > 12f) topH = 12f - botH
            val midH = 12f - topH - botH
            if (topH > 0.01f) lb(lm, 0f, 6f - topH / 2f, 0f, 4f, topH, 4f, covC)
            if (midH > 0.01f) lb(lm, 0f, 6f - topH - midH / 2f, 0f, 4f, midH, 4f, skin)
            if (botH > 0.01f) lb(lm, 0f, -6f + botH / 2f, 0f, 4f, botH, 4f, shoeC)
            lay(1, 5)
            if (pantPx > 0f) {
                lb(lm, 0f, 6f - pantPx / 2f, 0f, pantW, pantPx, pantW, pantC)
                if (pantPx >= 8f) lb(lm, 0f, 6f - pantPx + 0.5f, 0f, pantW + 0.2f, 1f, pantW + 0.2f, shadeC(pantC, 0.8f))
                if (bot == 7 && !full && !overalls) lb(lm, s * 2.45f, 1.5f, 0.3f, 0.7f, 3.4f, 2.8f, shadeC(pantC, 0.85f))
                if (bot == 1) lb(lm, 0f, 6f - 5f + 0.4f, 0f, 4.8f, 0.8f, 4.8f, lite(pantC, 0.25f))
            }
            if (skirtLen > 0f) {
                lb(lm, 0f, 6f - skirtLen / 2f, 0f, 5.1f, skirtLen, 5.3f, skirtC)
                lb(lm, 0f, 6f - skirtLen + 0.4f, 0f, 5.3f, 0.8f, 5.5f, hemC)
                if (bot == 5 && !full) lb(lm, 0.9f, 6f - skirtLen / 2f, 2.7f, 0.7f, skirtLen - 1f, 0.3f, shadeC(skirtC, 0.78f))
            }
            when (shoe) {
                0 -> { lb(lm, 0f, -4f, 0f, 4.7f, 4f, 4.7f, shoeC); lb(lm, 0f, -5f, 0.9f, 4.3f, 2f, 5.4f, shoeC); lb(lm, 0f, -2.2f, 0f, 5f, 0.8f, 5f, lite(shoeC, 0.3f)) }
                1 -> { lb(lm, 0f, -5.6f, 0.4f, 4.9f, 0.8f, 5.5f, white); lb(lm, 0f, -4.4f, 0.35f, 4.6f, 2.4f, 5.2f, shoeC); lb(lm, 0f, -3.3f, 0.9f, 3f, 0.4f, 1.4f, white) }
                2 -> { lb(lm, 0f, -5.3f, 0.3f, 4.5f, 1.4f, 5.1f, shoeC); lb(lm, 0f, -4.6f, 1.6f, 1.2f, 0.7f, 0.5f, lite(shoeC, 0.4f)) }
                4 -> { lb(lm, 0f, -2f, 0f, 4.7f, 8f, 4.7f, shoeC); lb(lm, 0f, -5f, 0.9f, 4.3f, 2f, 5.4f, shoeC); lb(lm, 0f, 1.8f, 0f, 5f, 0.8f, 5f, lite(shoeC, 0.3f)) }
                5 -> { lb(lm, 0f, -5.4f, 1.6f, 4.8f, 1.2f, 1.2f, 0x8A5A3A); lb(lm, 0f, -5.4f, -1.6f, 4.8f, 1.2f, 1.2f, 0x8A5A3A); lb(lm, 0f, -4.3f, 0.2f, 4.8f, 0.8f, 5.2f, 0xC8935A); lb(lm, 0f, -3.6f, 0.9f, 4.3f, 0.7f, 1.2f, shoeC) }
                6 -> { lb(lm, 0f, -5.4f, 0.3f, 4.7f, 1.2f, 5.3f, lite(shoeC, 0.2f)); lb(lm, 0f, -4.3f, 0.3f, 4.6f, 1.6f, 5.1f, shoeC) }
                7 -> { for (k in 0..5) lb(lm, 0f, -5.5f + k.toFloat(), 0f, 4.6f, 1f, 4.6f, if (k % 2 == 0) shoeC else white); lb(lm, 0f, -5.7f, 0.5f, 4.7f, 0.7f, 5.4f, shadeC(shoeC, 0.6f)) }
            }
            if (kbend > 2f) {   // junta do joelho: tampa a fresta que abre ao dobrar
                lay(1, 5)
                val jw = if (pantPx >= 6f) pantW + 0.05f else 4.04f
                bx(lkM, 0f, 0f, 0f, 0f, 0f, jw * U, 2.2f * U, jw * U, 0f, if (pantPx >= 6f) pantC else skin, 1f, 0f)
            }
            kbend = 0f; curLeg = null
        }

        // quadril: um bloco por baixo do tronco (cor da roupa de baixo) que tapa a fresta que se abre quando as pernas balançam
        lay(0, 0)
        tb(0f, -7f, 0f, 7.9f, 2.8f, 3.9f, covC)

        // ================= TRONCO + ROUPA =================
        lay(0, 0)
        tb(0f, 0f, 0f, 8f, 12f, 4f, skin)
        var sleeve = 4f; var sCol = tc; var tCol = tc
        when (top) {
            1, 3, 7, 14, 17 -> sleeve = 10.5f
            2 -> sleeve = 3f
            4 -> sleeve = 8f
            5 -> { sleeve = 0f; tCol = mixC(steel, tc, 0.3f) }
            8 -> { tCol = white; sCol = white }
            9 -> { sleeve = 10.5f; tCol = shadeC(tc, 0.85f); sCol = tCol }
            10, 11 -> sleeve = 10.5f
            12 -> { sleeve = 10.5f; tCol = shadeC(tc, 0.7f); sCol = tCol }
            13 -> sleeve = 0f
            15 -> { sleeve = 3.5f; tCol = shadeC(tc, 0.4f); sCol = white }
            16 -> { tCol = white; sCol = white }
            6 -> { tCol = white; sCol = white }
        }
        lay(1, 1)
        tb(0f, 0f, 0f, 8.7f, 12.2f, 4.7f, tCol)
        lay(2, 8)
        tb(0f, -5.7f, 0f, 8.8f, 0.7f, 4.8f, shadeC(tCol, 0.8f))
        when (top) {
            0 -> tb(0f, 5.6f, 0f, 8.8f, 0.8f, 4.8f, shadeC(tc, 0.78f))
            1 -> { tb(0f, 5.2f, -1.4f, 8.8f, 1.8f, 2f, shadeC(tc, 0.75f)); tb(0f, -3f, 2.4f, 5.5f, 3.2f, 0.4f, lite(tc, 0.1f)); tb(0f, -5.4f, 0f, 8.9f, 1.3f, 4.9f, shadeC(tc, 0.75f)) }
            2 -> { tb(0f, 5.4f, 0f, 8.8f, 1f, 4.8f, white); tb(0f, -2f, 0f, 8.9f, 1.2f, 4.9f, lite(tc, 0.4f)) }
            3 -> { tb(0f, 0f, 2.4f, 0.5f, 12f, 0.35f, lite(tc, 0.6f)); tb(0f, 5.2f, 0f, 8.9f, 1.5f, 4.9f, lite(tc, 0.35f)); tb(-2.6f, -3f, 2.4f, 2f, 0.4f, 0.35f, shadeC(tc, 0.7f)); tb(2.6f, -3f, 2.4f, 2f, 0.4f, 0.35f, shadeC(tc, 0.7f)) }
            4 -> { tb(0f, -3f, 0f, 8.9f, 1.5f, 4.9f, leather); tb(0f, -3f, 2.5f, 1.4f, 1.4f, 0.3f, gold); tb(0f, 5.4f, 0f, 8.8f, 1f, 4.8f, lite(tc, 0.4f)) }
            5 -> { tb(0f, 1f, 2.5f, 8.2f, 6.5f, 0.5f, lite(tCol, 0.25f)); tb(0f, 5f, 2.55f, 8.4f, 0.8f, 0.4f, gold); tb(0f, -4.5f, 0f, 8.9f, 1.3f, 4.9f, gold); tb(0f, -1.5f, 2.55f, 1.6f, 1.6f, 0.4f, gold) }
            6 -> { tb(0f, -2f, 0f, 8.9f, 8.2f, 4.9f, tc); tb(-2.6f, 4f, 0f, 1.8f, 4.2f, 4.9f, tc); tb(2.6f, 4f, 0f, 1.8f, 4.2f, 4.9f, tc); tb(-2.6f, 1.5f, 2.55f, 1.2f, 1.2f, 0.3f, gold); tb(2.6f, 1.5f, 2.55f, 1.2f, 1.2f, 0.3f, gold); tb(0f, -1f, 2.55f, 4f, 3f, 0.3f, shadeC(tc, 0.85f)) }
            7 -> { tb(0f, 5.4f, 0f, 5.4f, 1.3f, 4.9f, white); tb(0f, 0f, 2.4f, 0.5f, 12f, 0.35f, lite(tc, 0.45f)); for (k in 0..2) tb(0f, 2f - 3.2f * k.toFloat(), 2.55f, 0.7f, 0.7f, 0.3f, white) }
            8 -> { tb(0f, 3.6f, -2.5f, 8.9f, 5f, 0.45f, tc); tb(-2.6f, 4.4f, 2.5f, 3.2f, 3f, 0.4f, tc); tb(2.6f, 4.4f, 2.5f, 3.2f, 3f, 0.4f, tc); tb(0f, 3f, 2.55f, 1.8f, 2.6f, 0.4f, 0xE05555); tb(-2.6f, 3.2f, 2.58f, 2.6f, 0.4f, 0.3f, white); tb(2.6f, 3.2f, 2.58f, 2.6f, 0.4f, 0.3f, white) }
            9 -> { tb(0f, 5.6f, 0f, 8.9f, 1.4f, 4.9f, shadeC(tc, 0.8f)); for (k in 0..4) tb(0f, 3.6f - 2.4f * k.toFloat(), 2.55f, 0.8f, 0.8f, 0.3f, gold) }
            10 -> { tb(-1.3f, 3.2f, 2.5f, 1.2f, 6.5f, 0.35f, white, rz = 18f); tb(1.3f, 3.2f, 2.5f, 1.2f, 6.5f, 0.35f, white, rz = -18f); tb(0f, -2.5f, 0f, 9f, 3.6f, 5f, lite(tc, 0.35f)); tb(0f, -2.5f, -3.2f, 5.5f, 4.4f, 1.6f, lite(tc, 0.35f)) }
            11 -> { tb(0f, 5.2f, 0f, 8.9f, 1f, 4.9f, gold); tb(0f, -3f, 0f, 8.9f, 1.2f, 4.9f, gold); tb(-2f, 1.5f, 2.55f, 0.9f, 0.9f, 0.3f, gold); tb(2f, -0.5f, 2.55f, 0.9f, 0.9f, 0.3f, gold); tb(0f, 3f, 2.55f, 0.9f, 0.9f, 0.3f, gold) }
            12 -> { tb(0f, 2.2f, 2.45f, 2.6f, 6.4f, 0.4f, white); tb(-2.4f, 3f, 2.5f, 2.4f, 5f, 0.4f, shadeC(tCol, 0.8f), rz = 14f); tb(2.4f, 3f, 2.5f, 2.4f, 5f, 0.4f, shadeC(tCol, 0.8f), rz = -14f); tb(0f, -2f, 2.55f, 0.8f, 0.8f, 0.3f, gold) }
            14 -> { tb(0f, 0f, 2.4f, 0.5f, 12f, 0.35f, shadeC(tc, 0.7f)); tb(2.4f, 3f, 2.55f, 1.9f, 1.9f, 0.3f, white); tb(0f, -5.4f, 0f, 8.9f, 1.2f, 4.9f, lite(tc, 0.4f)) }
            15 -> { tb(0f, 5.4f, 0f, 8.8f, 1.2f, 4.8f, white); tb(0f, -1.5f, 2.45f, 6.2f, 9f, 0.4f, white); tb(0f, 4f, 2.5f, 3.2f, 3f, 0.4f, white) }
            16 -> { tb(-2.5f, 0f, 0f, 3.4f, 11.5f, 4.9f, tc); tb(2.5f, 0f, 0f, 3.4f, 11.5f, 4.9f, tc); tb(0f, 0f, -2.45f, 8.9f, 11.5f, 0.4f, tc); tb(0f, -4.5f, 0f, 8.95f, 1.3f, 4.95f, leather); tb(0f, -4.5f, 2.5f, 1.4f, 1.4f, 0.3f, gold) }
            17 -> { tb(-2.9f, 3f, 2.5f, 2.4f, 6f, 0.35f, lite(tc, 0.3f), rz = 8f); tb(2.9f, 3f, 2.5f, 2.4f, 6f, 0.35f, lite(tc, 0.3f), rz = -8f); for (k in 0..2) { tb(-1.6f, 1f - 3f * k.toFloat(), 2.55f, 0.8f, 0.8f, 0.3f, gold); tb(1.6f, 1f - 3f * k.toFloat(), 2.55f, 0.8f, 0.8f, 0.3f, gold) } }
        }

        // ================= BRAÇOS =================
        for (sd in intArrayOf(-1, 1)) {
            val s = sd.toFloat(); val ai = if (sd < 0) 0 else 1
            val rx = if (sd < 0) rxR else rxL; val rz = if (sd < 0) -rzR else -rzL; val el = if (sd < 0) elR else elL   // +x é a ESQUERDA do boneco: o item vai na direita (sd<0)
            val ry = if (sd < 0) ryR else ryL
            piv(armM[ai], bm, s * (4f + aw / 2f) * U, 22f * U, 0f, rx = rx + el * 0.55f, ry = ry, rz = rz)
            val am = armM[ai]
            lay(0, 0)
            val slvH = min(sleeve, 12f)
            if (slvH > 0.01f) ab(am, 0f, 6f - slvH / 2f, 0f, aw, slvH, 4f, sCol)
            if (12f - slvH > 0.01f) ab(am, 0f, -6f + (12f - slvH) / 2f, 0f, aw, 12f - slvH, 4f, skin)
            lay(1, 5)
            if (sleeve > 0f) {
                ab(am, 0f, 6f - sleeve / 2f, 0f, aw + 0.7f, sleeve, 4.7f, sCol)
                val cuff = when (top) { 1, 3 -> shadeC(tc, 0.7f); 7 -> lite(tc, 0.55f); 12 -> white; 9 -> gold; 17 -> shadeC(tc, 0.7f); else -> shadeC(sCol, 0.82f) }
                if (sleeve >= 10f) ab(am, 0f, 6f - sleeve + 0.7f, 0f, aw + 0.9f, 1.4f, 4.9f, cuff) else ab(am, 0f, 6f - sleeve + 0.35f, 0f, aw + 0.8f, 0.7f, 4.8f, cuff)
            }
            when (top) {
                2 -> ab(am, 0f, 4.2f, 0f, aw + 1.6f, 3.6f, 5.4f, tc)
                15 -> { ab(am, 0f, 4.2f, 0f, aw + 1.8f, 3.8f, 5.5f, white); ab(am, 0f, 2.2f, 0f, aw + 2f, 0.6f, 5.7f, lite(0xE8D5B0, 0.2f)) }
                10, 11, 14 -> { ab(am, s * 0.8f, 0.2f, 0f, aw + 3.4f, 8.6f, 5.2f, sCol); ab(am, s * 0.8f, -3.9f, 0f, aw + 3.6f, 1f, 5.4f, if (top == 11) gold else lite(sCol, 0.5f)) }
                5 -> { ab(am, s * 0.5f, 5.2f, 0f, aw + 2.4f, 3.4f, 5.8f, tCol); ab(am, s * 0.5f, 6.9f, 0f, aw + 2.6f, 0.6f, 6f, gold); ab(am, 0f, -4.2f, 0f, aw + 0.9f, 3.6f, 4.9f, steel); ab(am, 0f, -2.2f, 0f, aw + 1f, 0.5f, 5f, gold) }
                8 -> ab(am, 0f, 6f - 3.2f, 0f, aw + 0.85f, 0.7f, 4.85f, tc)
            }
            if (sd < 0 && handOut != null) {
                System.arraycopy(am, 0, handOut, 0, 16)
                Matrix.translateM(handOut, 0, 0f, -0.50f, 0.02f)
            }
        }

        // ================= CABEÇA: rosto =================
        lay(0, 0)
        hb(0f, 0f, 0f, 8f, 8f, 8f, skin)
        lay(1, 7)
        hb(0f, -1.5f, 4.06f, 1f, 1f, 0.2f, shadeC(skin, 0.9f))   // nariz
        val ez = 4.1f
        val eyeY = -0.5f
        val lid = shadeC(skin, 0.55f)
        val es = L[Look.EYES]
        for (sg in intArrayOf(-1, 1)) {
            val g = sg.toFloat()
            if (blink && es != 2) { hb(g * 2f, eyeY - 0.4f, ez, 2f, 0.5f, 0.25f, lid); continue }
            when (es) {
                0 -> { hb(g * 2.5f, eyeY - 0.5f, ez, 1f, 2f, 0.25f, white); hb(g * 1.5f, eyeY - 0.5f, ez + 0.02f, 1f, 2f, 0.25f, eyeC); hb(g * 1.7f, eyeY + 0.1f, ez + 0.05f, 0.5f, 0.5f, 0.25f, white) }
                1 -> { hb(g * 2f, eyeY + 0.1f, ez, 2f, 0.55f, 0.25f, 0x2C2C3A); hb(g * 2.9f, eyeY - 0.4f, ez, 0.6f, 0.6f, 0.25f, 0x2C2C3A); hb(g * 1.1f, eyeY - 0.4f, ez, 0.6f, 0.6f, 0.25f, 0x2C2C3A) }
                2 -> { hb(g * 2f, eyeY, ez, 1.2f, 1.2f, 0.25f, 0x2C2C3A) }
                3 -> { hb(g * 2.5f, eyeY - 0.5f, ez, 1f, 1f, 0.25f, white); hb(g * 1.5f, eyeY - 0.5f, ez + 0.02f, 1f, 1f, 0.25f, eyeC); hb(g * 2f, eyeY + 0.25f, ez + 0.04f, 2f, 0.6f, 0.25f, lid) }
                4 -> { hb(g * 2.5f, eyeY - 0.5f, ez, 1f, 2f, 0.25f, eyeC); hb(g * 1.5f, eyeY - 0.5f, ez, 1f, 2f, 0.25f, lite(eyeC, 0.3f)); hb(g * 2.3f, eyeY + 0.2f, ez + 0.05f, 0.55f, 0.55f, 0.25f, white); hb(g * 1.7f, eyeY - 0.9f, ez + 0.05f, 0.4f, 0.4f, 0.25f, white) }
                5 -> { hb(g * 2.5f, eyeY - 0.5f, ez, 1f, 1f, 0.25f, white); hb(g * 1.5f, eyeY - 0.5f, ez + 0.02f, 1f, 1f, 0.25f, 0x2C2C3A); hb(g * 2f, eyeY + 0.55f, ez, 2.4f, 0.5f, 0.25f, shadeC(hairC, 0.7f), rz = g * -8f) }
                6 -> { hb(g * 2.5f, eyeY - 0.5f, ez, 1f, 2f, 0.25f, lite(eyeC, 0.2f)); hb(g * 1.5f, eyeY - 0.5f, ez, 1f, 2f, 0.25f, eyeC); hb(g * 2f, eyeY - 0.5f, ez + 0.05f, 0.6f, 1.6f, 0.25f, 0xFFF1A0); hb(g * 2f, eyeY - 0.5f, ez + 0.06f, 1.6f, 0.6f, 0.25f, 0xFFF1A0) }
                7 -> { hb(g * 2.5f, eyeY - 0.5f, ez, 1f, 1.4f, 0.25f, lite(eyeC, 0.4f)); hb(g * 1.5f, eyeY - 0.5f, ez, 1f, 1.4f, 0.25f, eyeC); hb(g * 2f, eyeY - 0.5f, ez + 0.05f, 0.4f, 1.5f, 0.25f, 0x2C2C3A) }
                else -> { hb(g * 2.5f, eyeY - 0.5f, ez, 1f, 1f, 0.25f, white); hb(g * 1.5f, eyeY - 0.5f, ez + 0.02f, 1f, 1f, 0.25f, eyeC); hb(g * 2f, eyeY - 1.4f, ez, 2f, 0.5f, 0.25f, shadeC(skin, 0.75f)) }
            }
        }
        val mouthC = mixC(skin, 0x8A2A2A, 0.65f)
        when (L[Look.MOUTH]) {
            0 -> { hb(0f, -2.7f, ez, 2f, 0.7f, 0.25f, mouthC); hb(-1.4f, -2.3f, ez, 0.7f, 0.6f, 0.25f, mouthC); hb(1.4f, -2.3f, ez, 0.7f, 0.6f, 0.25f, mouthC) }
            1 -> hb(0f, -2.6f, ez, 2.4f, 0.7f, 0.25f, mouthC)
            2 -> hb(0f, -2.6f, ez, 1f, 1f, 0.25f, mixC(skin, 0xD05060, 0.7f))
            3 -> { hb(0f, -2.8f, ez, 2f, 1.5f, 0.25f, 0x7A2030); hb(0f, -3.3f, ez + 0.03f, 1f, 0.5f, 0.25f, 0xE06A80) }
            4 -> { hb(0f, -2.7f, ez, 2f, 0.7f, 0.25f, mouthC); hb(-1.4f, -2.3f, ez, 0.7f, 0.6f, 0.25f, mouthC); hb(1.4f, -2.3f, ez, 0.7f, 0.6f, 0.25f, mouthC); hb(-0.5f, -3.2f, ez, 0.8f, 0.5f, 0.25f, white) }
            else -> { hb(-0.7f, -2.8f, ez, 1.1f, 0.6f, 0.25f, mouthC); hb(0.7f, -2.8f, ez, 1.1f, 0.6f, 0.25f, mouthC); hb(0f, -2.3f, ez, 0.5f, 0.7f, 0.25f, mouthC); hb(-1.5f, -2.3f, ez, 0.6f, 0.6f, 0.25f, mouthC); hb(1.5f, -2.3f, ez, 0.6f, 0.6f, 0.25f, mouthC) }
        }
        if (L[Look.BLUSH] == 1) { val bl = mixC(skin, 0xFF6F90, 0.5f); hb(2.9f, -1.7f, ez - 0.02f, 1.6f, 0.9f, 0.25f, bl); hb(-2.9f, -1.7f, ez - 0.02f, 1.6f, 0.9f, 0.25f, bl) }

        // ================= CABELO (50 estilos do menu viram formas de bloco) =================
        val hatId = L[Look.HAT]
        val hatCovers = hatId == 2 || hatId == 3 || hatId == 8 || hatId == 16 || hatId == 19 || hatId == 21 || hatId == 22 || hatId == 23
        lay(8, 14)
        val hs = L[Look.HAIR].coerceIn(0, 49)
        val bk = HAIR_BK[hs]; val sl = HAIR_SL[hs]; val bg = HAIR_BG[hs]; val tp = HAIR_TP[hs]; val ext = HAIR_EXT[hs]
        val tie = hatC
        val sway = sin(t * 2.6f) * 2f + sin(ph) * 7f * stride
        val mohawk = ext == 7; val afro = ext == 13
        val hl = if (hatCovers) 0f else tp
        if (!mohawk) {
            if (hatCovers) hb(0f, 3.6f, 0f, 8.6f, 1.2f, 8.6f, hairC)
            else { hb(0f, 4.2f + tp / 2f, 0f, 8.8f, 1.4f + tp, 8.8f, hairC); hb(0f, 4.95f + tp, -0.6f, 6.4f, 0.3f, 5.6f, hairL) }
            val hbk = min(bk, 8f)
            hb(0f, 4.4f - hbk / 2f, -4.2f, 8.8f, hbk, 1.4f, hairC)
            val hsl = min(sl, 8f)
            hb(4.2f, 4.4f - hsl / 2f, 0f, 1.4f, hsl, 8.8f, hairC); hb(-4.2f, 4.4f - hsl / 2f, 0f, 1.4f, hsl, 8.8f, hairC)
            when (bg) {
                1 -> hb(0f, 3.5f, 4.2f, 8.8f, 1.6f, 1.2f, hairC)
                2 -> hb(0f, 3.0f, 4.2f, 8.8f, 2.6f, 1.2f, hairC)
                3 -> hb(0f, 2.6f, 4.2f, 8.8f, 3.4f, 1.2f, hairC)
                4 -> { hb(0f, 3.5f, 4.2f, 8.8f, 1.6f, 1.2f, hairC); hb(-1.2f, 2.4f, 4.2f, 6.4f, 1.2f, 1.2f, hairC); hb(-2.6f, 1.4f, 4.2f, 3.6f, 1.0f, 1.2f, hairC) }
                5 -> { for (k in 0..3) { val xk = -3.3f + 2.2f * k.toFloat(); hb(xk, 3.4f - (if (k % 2 == 0) 0.6f else 0f), 4.2f, 1.9f, 2.4f + (if (k % 2 == 0) 1f else 0f), 1.2f, hairC) } }
                6 -> { hb(-2.3f, 2.9f, 4.2f, 4.2f, 2.6f, 1.2f, hairC); hb(2.3f, 3.1f, 4.2f, 4.2f, 2.2f, 1.2f, hairC); hb(-3.9f, 1.6f, 4.2f, 1.0f, 1.2f, 1.2f, hairC); hb(3.9f, 1.6f, 4.2f, 1.0f, 1.2f, 1.2f, hairC) }
            }
            if (bk > 8f) {
                val len = bk - 8f
                tb(0f, 6f - len / 2f, -3f, 8.6f, len, 1.3f, hairC)
                tb(-1.8f, 6f - len * 0.45f, -3.7f, 2.2f, len * 0.9f, 0.5f, hairD); tb(2.4f, 6f - len * 0.45f, -3.7f, 2.6f, len * 0.9f, 0.5f, hairD)
            }
            if (sl > 8f) {
                val len2 = sl - 8f
                tb(-3.4f, 6f - len2 / 2f + 0.2f, 2.7f, 1.8f, len2, 1f, hairC); tb(3.4f, 6f - len2 / 2f + 0.2f, 2.7f, 1.8f, len2, 1f, hairC)
            }
        }
        when (ext) {
            1 -> if (!hatCovers) { val ls = if (hs == 31) 3.2f else 2.6f; for (k in 0..5) { val xk = floatArrayOf(0f, -2.4f, 2.4f, -2.4f, 2.4f, 0f)[k]; val zk = floatArrayOf(0.5f, -1.8f, -1.8f, 1.6f, 1.6f, -3f)[k]; hb(xk, 5f + ls / 2f + tp, zk, 1.7f, ls + (k % 2).toFloat(), 1.7f, hairC, rz = xk * 3f) } }
            23 -> if (!hatCovers) { for (k in 0..6) { val xk = -3.6f + 1.2f * k.toFloat(); val ls = 4f + (k % 3).toFloat() * 1.4f; hb(xk, 5f + ls / 2f, -0.5f + (k % 2).toFloat() * 1.4f, 1.5f, ls, 1.7f, hairC, rz = xk * 4f) } }
            2 -> for (g in intArrayOf(-1, 1)) { val gf = g.toFloat(); hb(gf * 5f, 0.2f, -1f, 2.1f, 1f, 2.1f, tie); hb(gf * 5f, -3.4f, -1f, 1.9f, 6.8f, 1.9f, hairC, rz = gf * sway * 0.5f) }
            3 -> { hb(0f, 3.4f, -4.6f, 2.2f, 2.2f, 1.4f, tie); hb(0f, -1.2f + 0.0f, -5.4f, 2.8f, 9f, 2f, hairC, rx = 6f + sway); hb(0f, -4.8f, -5.6f, 2.4f, 1.5f, 1.6f, hairD, rx = 6f + sway) }
            4 -> if (!hatCovers) { hb(0f, 4.6f + hl, -0.5f, 8.6f, 0.9f, 8.6f, tie); hb(0f, 6.4f + hl, -0.5f, 4.2f, 3.4f, 4.2f, hairC); hb(0f, 8.0f + hl, -0.5f, 2.4f, 0.8f, 2.4f, hairL) }
            5 -> { hb(4.9f, 0.5f, -0.5f, 1.5f, 6f, 7f, hairC); hb(-4.9f, 0.5f, -0.5f, 1.5f, 6f, 7f, hairC); hb(0f, 0f, -4.9f, 9.6f, 8f, 1.5f, hairC) }
            6 -> if (!hatCovers) { hb(0f, 5.2f + tp, 2.2f, 8.4f, 2.2f, 3.6f, hairC); hb(0f, 6.2f + tp, 2.4f, 7f, 0.6f, 2.8f, hairL) }
            7 -> { hb(0f, 4.8f, 0f, 1.8f, 1.6f, 8.8f, hairC); hb(0f, 6.2f, -0.5f, 1.8f, 1.8f, 7f, hairC); hb(0f, 3.9f, 0f, 1.8f, 0.8f, 8.6f, hairD) }
            8 -> { for (k in 0..5) tb(0f, 5.5f - 2.3f * k.toFloat(), -3.1f, 2.4f, 2.4f, 1.5f, if (k % 2 == 0) hairC else hairD); tb(0f, 5.5f - 2.3f * 6f, -3.1f, 2.6f, 1.2f, 1.6f, tie) }
            9 -> if (!hatCovers) { for (g in intArrayOf(-1, 1)) { val gf = g.toFloat(); hb(gf * 3.2f, 4.7f, -0.5f, 3.4f, 0.8f, 3.4f, tie); hb(gf * 3.2f, 6.4f, -0.5f, 3.2f, 3.2f, 3.2f, hairC) } }
            10 -> if (!hatCovers) { for (g in intArrayOf(-1, 1)) { val gf = g.toFloat(); hb(gf * 4.6f, 3.6f, -1.5f, 2.2f, 1.0f, 2.2f, tie); hb(gf * 5.4f, -3.2f, -1.5f, 2.2f, 11.5f, 2.2f, hairC, rz = gf * (3f + sway * 0.3f)) } }
            11 -> if (!hatCovers) { hb(0.4f, 5.9f + tp, 1.2f, 0.7f, 3.4f, 0.7f, hairC, rz = -18f); hb(1.6f, 7.8f + tp, 1.2f, 0.7f, 1.4f, 0.7f, hairC, rz = -42f) }
            12 -> { hb(5f, 1.5f, 0f, 1.9f, 6.4f, 7.2f, hairC); hb(-5f, 1.5f, 0f, 1.9f, 6.4f, 7.2f, hairC); hb(-2.4f, 5.5f + tp, 0f, 3.4f, 2.4f, 3.4f, hairC); hb(2.4f, 5.4f + tp, 0.4f, 3.4f, 2.2f, 3.4f, hairC); hb(0f, 4.4f, -5f, 7.8f, 3f, 1.6f, hairC) }
            13 -> { hb(0f, 6.2f, -1f, 13f, 5.5f, 12.5f, hairC); hb(5.6f, 2.2f, -1f, 2.6f, 6.5f, 11f, hairC); hb(-5.6f, 2.2f, -1f, 2.6f, 6.5f, 11f, hairC); hb(0f, 2f, -5.8f, 12.6f, 9f, 2.8f, hairC); hb(0f, 8.7f, -1f, 9.5f, 0.6f, 9.5f, hairL) }
            14 -> { for (k in -3..3) { val kf = k.toFloat(); val lg = 8f + (k and 1).toFloat() * 2f + abs(kf); hb(kf * 1.15f, 0.5f - lg / 2f + 2f, -4.9f, 1.1f, lg, 1.2f, if (k % 2 == 0) hairC else hairD) }; for (g in intArrayOf(-1, 1)) for (k in 0..2) { val gf = g.toFloat(); hb(gf * 4.9f, 0.5f - (k * 1.5f), -3f + k.toFloat() * 1.7f, 1.2f, 6f + k.toFloat(), 1.1f, hairC) } }
            15 -> if (!hatCovers) { hb(0f, 6.2f, -2.2f, 2.6f, 3f, 2.6f, hairC); hb(0f, 4.7f, -2.2f, 3.2f, 0.8f, 3.2f, tie) }
            16 -> { hb(0f, -1.6f, -4.8f, 2.2f, 1.4f, 1.4f, tie); hb(0f, -5.6f, -5.2f, 2.4f, 8f, 1.8f, hairC, rx = 4f + sway * 0.7f) }
            18 -> { for (k in 0..4) tb(-3.6f, 3.5f - 2.2f * k.toFloat(), 2.7f, 2.2f, 2.2f, 1.4f, if (k % 2 == 0) hairC else hairD); tb(-3.6f, 3.5f - 2.2f * 5f, 2.7f, 2.4f, 1.1f, 1.5f, tie) }
            19 -> { for (g in intArrayOf(-1, 1)) { val gf = g.toFloat(); for (k in 0..4) tb(gf * 3.6f, 3.5f - 2.2f * k.toFloat(), 2.7f, 2.2f, 2.2f, 1.4f, if (k % 2 == 0) hairC else hairD); tb(gf * 3.6f, 3.5f - 2.2f * 5f, 2.7f, 2.4f, 1.1f, 1.5f, tie) } }
            20 -> if (!hatCovers) { hb(0f, 5.5f, -1.4f, 3.4f, 3f, 3.4f, hairC); hb(0f, 4.4f, -1.4f, 3.8f, 0.8f, 3.8f, tie) }
            22 -> { for (g in intArrayOf(-1, 1)) { val gf = g.toFloat(); hb(gf * 4.6f, 3.6f, -1.5f, 2.2f, 1.0f, 2.2f, tie); for (k in 0..4) hb(gf * (5.2f + (k % 2).toFloat() * 0.6f), 1.8f - 2.2f * k.toFloat(), -1.5f, 2.5f, 2.4f, 2.5f, if (k % 2 == 0) hairC else hairD) } }
        }

        // ================= CHAPÉUS E ENFEITES =================
        lay(13, 19)
        when (hatId) {
            1 -> { hb(0f, 4.45f + hl, 0f, 13f, 0.9f, 13f, hatC); hb(0f, 5.0f + hl, 0f, 8.7f, 0.8f, 8.7f, gold); hb(0f, 6.2f + hl, 0f, 8.4f, 1.8f, 8.4f, hatC); hb(0.3f, 7.9f + hl, 0f, 6.4f, 1.6f, 6.4f, hatC, rz = 4f); hb(0.9f, 9.4f + hl, 0f, 4.4f, 1.6f, 4.4f, hatC, rz = 8f); hb(1.9f, 10.8f + hl, 0f, 2.4f, 1.4f, 2.4f, hatC, rz = 14f) }
            2 -> { hb(0f, 3.6f, 0f, 9.2f, 5f, 9.2f, hatC); hb(0f, 1.7f, 0f, 9.5f, 1.5f, 9.5f, lite(hatC, 0.3f)); hb(0f, 6.9f, 0f, 2.6f, 2.6f, 2.6f, white) }
            3 -> { hb(0f, 3.5f, 0f, 9f, 3.6f, 9f, hatC); hb(0f, 1.9f, 6.2f, 8f, 0.7f, 4.4f, shadeC(hatC, 0.8f)); hb(0f, 5.5f, 0f, 1.2f, 0.6f, 1.2f, lite(hatC, 0.4f)) }
            4 -> { hb(0f, 4.6f + hl, 0f, 8.8f, 1.4f, 8.8f, gold); val cx = floatArrayOf(-3.4f, 3.4f, -3.4f, 3.4f, 0f, 0f); val cz = floatArrayOf(-3.4f, -3.4f, 3.4f, 3.4f, 3.6f, -3.6f); for (k in 0..5) hb(cx[k], 6.2f + hl, cz[k], 1.6f, 2.4f, 1.6f, gold); hb(0f, 4.6f + hl, 4.5f, 1f, 1f, 0.3f, 0xE05555) }
            5 -> for (g in intArrayOf(-1, 1)) { val gf = g.toFloat(); hb(gf * 2.8f, 5.6f + hl, -0.5f, 2.6f, 2.2f, 1.2f, hairC); hb(gf * 2.8f, 7.1f + hl, -0.5f, 1.4f, 1.2f, 1f, hairC); hb(gf * 2.8f, 5.6f + hl, 0.2f, 1.4f, 1.2f, 0.3f, 0xF0A0B0) }
            6 -> for (g in intArrayOf(-1, 1)) { val gf = g.toFloat(); hb(gf * 2f, 8.6f + hl, -0.5f, 2f, 7f, 1f, hairC, rz = gf * -8f); hb(gf * 2f, 8.6f + hl, 0.1f, 1f, 5.2f, 0.3f, 0xF0A0B0, rz = gf * -8f) }
            7 -> { hb(0f, 5.3f + hl, 2f, 1.8f, 1.8f, 1.4f, hatC); hb(-2.6f, 5.6f + hl, 2f, 3.2f, 3f, 1f, hatC, rz = 14f); hb(2.6f, 5.6f + hl, 2f, 3.2f, 3f, 1f, hatC, rz = -14f) }
            8 -> { hb(0f, 3.8f, 0f, 9.4f, 3f, 9.4f, steel); hb(4.6f, 0.2f, 0f, 1.2f, 6f, 9f, steel); hb(-4.6f, 0.2f, 0f, 1.2f, 6f, 9f, steel); hb(0f, 0.2f, -4.6f, 9.4f, 6f, 1.2f, steel); hb(0f, 2.4f, 4.6f, 9.4f, 1f, 1.2f, lite(steel, 0.3f)); hb(0f, 5.8f, 0f, 1.6f, 1.6f, 8f, hatC) }
            9 -> { hb(0f, 3.6f + hl, 0f, 14f, 0.8f, 14f, 0xE8C86A); hb(0f, 5.2f + hl, 0f, 7.6f, 2.6f, 7.6f, 0xE8C86A); hb(0f, 4.4f + hl, 0f, 7.9f, 0.8f, 7.9f, hatC) }
            10 -> { hb(3.2f, 4.9f + hl, 1.5f, 1f, 1f, 1f, 0xFFD35C); hb(3.2f, 4.9f + hl, 2.6f, 1.3f, 1f, 1.3f, hatC); hb(3.2f, 4.9f + hl, 0.4f, 1.3f, 1f, 1.3f, hatC); hb(4.3f, 4.9f + hl, 1.5f, 1.3f, 1f, 1.3f, hatC); hb(2.1f, 4.9f + hl, 1.5f, 1.3f, 1f, 1.3f, hatC) }
            11 -> { hb(0f, 2.2f, 0f, 8.9f, 1.6f, 8.9f, hatC); hb(0f, 2.2f, 4.5f, 3.4f, 1.6f, 0.3f, steel); hb(1.2f, 2.2f, -5.2f, 1.2f, 3.5f, 0.8f, hatC, rz = -15f); hb(-1.2f, 1.5f, -5.4f, 1.2f, 3.5f, 0.8f, hatC, rz = 15f) }
            12 -> { hb(0f, 8f + hl, 3.2f, 7f, 0.5f, 0.8f, gold); hb(0f, 8f + hl, -3.2f, 7f, 0.5f, 0.8f, gold); hb(3.2f, 8f + hl, 0f, 0.8f, 0.5f, 7f, gold); hb(-3.2f, 8f + hl, 0f, 0.8f, 0.5f, 7f, gold) }
            13 -> for (g in intArrayOf(-1, 1)) { val gf = g.toFloat(); hb(gf * 2.6f, 5.2f + hl, 0.5f, 1.4f, 2f, 1.4f, hatC); hb(gf * 3.1f, 6.7f + hl, 0.5f, 1f, 1.4f, 1f, hatC, rz = gf * -15f) }
            14 -> for (g in intArrayOf(-1, 1)) { val gf = g.toFloat(); hb(gf * 3f, 6.2f + hl, -0.5f, 2.8f, 3.4f, 1.2f, hairC); hb(gf * 3f, 8.4f + hl, -0.5f, 1.4f, 1.4f, 1f, shadeC(hairC, 0.4f)); hb(gf * 3f, 6.2f + hl, 0.2f, 1.5f, 2f, 0.3f, 0xF4D0C0) }
            15 -> { hb(0f, 4.6f + hl, 0f, 8.7f, 0.6f, 8.7f, gold); hb(0f, 5.4f + hl, 4.1f, 2f, 1.4f, 0.5f, gold); hb(0f, 5.4f + hl, 4.4f, 0.8f, 0.8f, 0.3f, 0x6AA8F0) }
            16 -> { hb(0.6f, 4.4f, 0f, 10f, 1.8f, 9.6f, hatC); hb(0.6f, 5.6f, 0f, 1f, 1f, 1f, shadeC(hatC, 0.7f)) }
            17 -> { hb(0f, 4.6f + hl, 0f, 14f, 0.8f, 14f, 0xD8B070); hb(0f, 5.4f + hl, 0f, 10.4f, 0.8f, 10.4f, 0xD8B070); hb(0f, 6.2f + hl, 0f, 6.8f, 0.8f, 6.8f, 0xD8B070); hb(0f, 6.9f + hl, 0f, 3.2f, 0.8f, 3.2f, hatC) }
            18 -> { hb(0f, 5.0f + hl, 0f, 9.6f, 0.9f, 1.4f, hatC); for (g in intArrayOf(-1, 1)) { val gf = g.toFloat(); hb(gf * 4.8f, 3.3f + hl, 0f, 0.9f, 3.2f, 1.4f, hatC); hb(gf * 5.0f, 0.8f, 0f, 1.6f, 3.2f, 3.4f, hatC); hb(gf * 5.9f, 0.8f, 0f, 0.4f, 2.4f, 2.6f, lite(hatC, 0.4f)) } }
            19 -> { hb(0f, 4.1f, 0f, 9.8f, 2.2f, 9.8f, hatC); hb(0f, 0.4f, -4.7f, 9.8f, 7.5f, 1.8f, hatC); hb(4.7f, 1f, -0.3f, 1.8f, 6.5f, 8.4f, hatC); hb(-4.7f, 1f, -0.3f, 1.8f, 6.5f, 8.4f, hatC); hb(0f, 3.3f, 4.7f, 9.6f, 1.2f, 0.9f, shadeC(hatC, 0.8f)); tb(0f, 5.7f, -1.8f, 9.6f, 2.6f, 2.2f, hatC) }
            20 -> { hb(0f, 4.4f + hl, 0f, 9f, 1.2f, 9f, white); hb(0f, 5.1f + hl, 4.2f, 8f, 1f, 1.2f, white); hb(0f, 5.0f + hl, 0f, 7.6f, 1.6f, 7.6f, white); hb(0f, 4.2f + hl, -4.8f, 3f, 2.2f, 0.8f, white) }
            21 -> { hb(0f, 4.3f, 0f, 11f, 0.8f, 11f, hatC); hb(0f, 8.2f, 0f, 7.6f, 7.6f, 7.6f, hatC); hb(0f, 5.6f, 0f, 7.9f, 1.2f, 7.9f, shadeC(hatC, 0.5f)) }
            22 -> { hb(0f, 4.6f, 0f, 8.8f, 1.6f, 8.8f, white); hb(0f, 7.4f, 0f, 10f, 4.6f, 10f, white); hb(0f, 10f, 0f, 8.4f, 1f, 8.4f, white) }
            23 -> { hb(0f, 4.2f, 0f, 9.6f, 2.4f, 9.6f, steel); hb(4.8f, 1.6f, 0f, 0.9f, 3.2f, 8.4f, steel); hb(-4.8f, 1.6f, 0f, 0.9f, 3.2f, 8.4f, steel); hb(0f, 2.4f, 4.9f, 1f, 4f, 0.8f, steel); for (g in intArrayOf(-1, 1)) { val gf = g.toFloat(); hb(gf * 5.6f, 4.6f, 0f, 1.6f, 1.6f, 1.6f, 0xF0E8D0); hb(gf * 6.6f, 6f, 0f, 1.4f, 2.2f, 1.4f, 0xF0E8D0, rz = gf * -20f); hb(gf * 7.3f, 7.8f, 0f, 1f, 1.4f, 1f, 0xF0E8D0, rz = gf * -35f) } }
        }

        // ================= ROSTO: óculos e marcas =================
        lay(7, 12)
        val gz = 4.3f
        val blk = 0x1E1E26
        fun frame(cx: Float, w: Float, h: Float, c: Int, lens: Int, la: Float) {
            hb(cx, eyeY - 0.2f, gz, w - 0.4f, h - 0.4f, 0.2f, lens, la)
            hb(cx, eyeY - 0.2f + h / 2f, gz + 0.05f, w, 0.4f, 0.3f, c); hb(cx, eyeY - 0.2f - h / 2f, gz + 0.05f, w, 0.4f, 0.3f, c)
            hb(cx - w / 2f, eyeY - 0.2f, gz + 0.05f, 0.4f, h, 0.3f, c); hb(cx + w / 2f, eyeY - 0.2f, gz + 0.05f, 0.4f, h, 0.3f, c)
        }
        fun temples(c: Int) { for (g in intArrayOf(-1, 1)) hb(g.toFloat() * 4.1f, eyeY + 0.2f, 1.9f, 0.3f, 0.4f, 4.4f, c) }
        when (L[Look.FACE]) {
            1 -> { frame(2f, 3f, 2.8f, blk, 0x9AD8F0, 0.3f); frame(-2f, 3f, 2.8f, blk, 0x9AD8F0, 0.3f); hb(0f, eyeY + 0.3f, gz + 0.05f, 1f, 0.4f, 0.3f, blk); temples(blk) }
            2 -> { frame(2f, 3.4f, 2.2f, blk, blk, 0.92f); frame(-2f, 3.4f, 2.2f, blk, blk, 0.92f); hb(0f, eyeY + 0.3f, gz + 0.05f, 0.8f, 0.4f, 0.3f, blk); temples(blk) }
            3 -> { val fx = floatArrayOf(2.4f, 3.3f, 1.7f, -2.4f, -3.3f, -1.7f); val fy = floatArrayOf(-1.4f, -1.9f, -2.0f, -1.4f, -1.9f, -2.0f); for (k in 0..5) hb(fx[k], fy[k], 4.08f, 0.5f, 0.5f, 0.2f, shadeC(skin, 0.7f)) }
            4 -> { hb(-2f, eyeY - 0.2f, gz, 3.2f, 2.6f, 0.4f, blk); hb(0f, eyeY + 2.4f, gz - 0.1f, 8.4f, 0.5f, 0.3f, blk, rz = -8f) }
            5 -> { val sx = floatArrayOf(2.7f, 2.1f, 1.5f, 0.9f); val sy = floatArrayOf(eyeY + 2.2f, eyeY + 1.2f, eyeY + 0.2f, eyeY - 0.8f); for (k in 0..3) hb(sx[k], sy[k], 4.1f, 0.65f, 0.65f, 0.25f, 0xC45A5A) }
            6 -> { hb(2.8f, -1.9f, 4.1f, 2.6f, 1.1f, 0.3f, 0xF0D7B0); hb(2.2f, -1.9f, 4.25f, 0.3f, 0.3f, 0.2f, 0xC9A97E); hb(3.4f, -1.9f, 4.25f, 0.3f, 0.3f, 0.2f, 0xC9A97E) }
            7 -> { for (g in intArrayOf(-1, 1)) { val gf = g.toFloat(); hb(gf * 3.3f, -1.4f, 4.15f, 2f, 0.25f, 0.2f, blk, rz = gf * 10f); hb(gf * 3.4f, -2.1f, 4.15f, 2f, 0.25f, 0.2f, blk); hb(gf * 3.3f, -2.8f, 4.15f, 2f, 0.25f, 0.2f, blk, rz = gf * -10f) } }
            8 -> { hb(0f, -2.4f, 4.2f, 8.4f, 3.4f, 0.5f, 0xF2F2F2); hb(0f, -2.4f, 4.5f, 8f, 0.25f, 0.2f, 0xD0D6DE); hb(0f, -3.2f, 4.5f, 8f, 0.25f, 0.2f, 0xD0D6DE); for (g in intArrayOf(-1, 1)) hb(g.toFloat() * 4.25f, -1.8f, 1.2f, 0.3f, 0.6f, 5.2f, white) }
            9 -> { frame(2f, 3.2f, 2.4f, blk, 0x9AD8F0, 0.28f); frame(-2f, 3.2f, 2.4f, blk, 0x9AD8F0, 0.28f); hb(0f, eyeY + 0.3f, gz + 0.05f, 0.8f, 0.4f, 0.3f, blk); temples(blk) }
            10 -> hb(2.5f, eyeY - 1.6f, 4.15f, 0.7f, 1.2f, 0.3f, 0x6AB8F0)
            11 -> { frame(2f, 3.2f, 3.2f, gold, 0xFFFFFF, 0.15f); hb(4.2f, -3f, 3.8f, 0.3f, 3.5f, 0.3f, gold) }
            12 -> { frame(2f, 3.8f, 3f, gold, 0xE8A030, 0.5f); frame(-2f, 3.8f, 3f, gold, 0xE8A030, 0.5f); hb(0f, eyeY + 0.8f, gz + 0.05f, 0.8f, 0.4f, 0.3f, gold); temples(gold) }
        }

        // ================= PESCOÇO =================
        lay(9, 12)
        when (L[Look.NECK]) {
            1 -> { tb(0f, 5.2f, 0f, 9.2f, 2.2f, 5.2f, neckC); tb(2.5f, 2.2f, 2.8f, 2f, 6.5f, 0.7f, neckC); tb(2.5f, 0.5f, 2.85f, 2.05f, 0.8f, 0.5f, lite(neckC, 0.5f)) }
            2 -> { tb(0f, 5.2f, 2.55f, 1.4f, 1.4f, 0.6f, neckC); tb(-1.6f, 5.2f, 2.5f, 2f, 2.4f, 0.5f, neckC, rz = 15f); tb(1.6f, 5.2f, 2.5f, 2f, 2.4f, 0.5f, neckC, rz = -15f) }
            3 -> { tb(0f, 5.1f, 2.45f, 5.6f, 0.4f, 0.3f, gold); tb(0f, 4f, 2.55f, 1.3f, 1.3f, 0.3f, neckC) }
            4 -> { tb(0f, 5f, 2.55f, 1.8f, 1.4f, 0.5f, neckC); tb(0f, 1.4f, 2.55f, 1.8f, 6.4f, 0.4f, neckC); tb(0f, -1.9f, 2.55f, 1.8f, 0.8f, 0.4f, shadeC(neckC, 0.8f)) }
            5 -> { tb(0f, 5.2f, 2.55f, 1.2f, 1.2f, 0.5f, neckC); tb(-1.7f, 5.2f, 2.5f, 2.2f, 2f, 0.4f, neckC); tb(1.7f, 5.2f, 2.5f, 2.2f, 2f, 0.4f, neckC) }
            6 -> { tb(0f, 5.4f, 0f, 8.5f, 1f, 4.5f, neckC); tb(0f, 4.6f, 2.45f, 1f, 1f, 0.5f, gold) }
            7 -> { tb(0f, 5.3f, 0f, 8.9f, 1.4f, 4.9f, neckC); tb(0f, 3.6f, 2.5f, 3.2f, 1.2f, 0.4f, neckC); tb(0f, 2.6f, 2.52f, 1.8f, 1f, 0.4f, neckC); tb(0f, 1.8f, 2.54f, 0.8f, 0.8f, 0.4f, neckC) }
            8 -> tb(0f, 5.4f, 0f, 8.4f, 0.6f, 4.4f, neckC)
            9 -> { tb(-1.4f, 4.5f, 2.45f, 0.4f, 2.6f, 0.3f, gold, rz = -25f); tb(1.4f, 4.5f, 2.45f, 0.4f, 2.6f, 0.3f, gold, rz = 25f); tb(0f, 2.6f, 2.55f, 1.8f, 1.8f, 0.4f, gold) }
        }

        // ================= COSTAS =================
        lay(2, 6)
        val flap = sin(t * 9f) * (10f + 14f * mv)
        when (L[Look.BACK]) {
            1 -> {
                piv(t3, bm, 0f, 23f * U, -2.6f * U, rx = 6f + move * 14f + sin(t * 2f) * 2f)
                bx(t3, 0f, -7.5f * U, -0.4f * U, 0f, 0f, 9.4f * U, 15f * U, 0.7f * U, 0f, backC, 1f, 0f)
                piv(t4, t3, 0f, -15f * U, 0f, rx = 4f + move * 10f + sin(t * 3f) * 4f)
                bx(t4, 0f, -2f * U, -0.4f * U, 0f, 0f, 9.6f * U, 4f * U, 0.7f * U, 0f, shadeC(backC, 0.9f), 1f, 0f)
                bx(t4, 0f, -3.8f * U, -0.4f * U, 0f, 0f, 9.8f * U, 0.7f * U, 0.8f * U, 0f, lite(backC, 0.5f), 1f, 0f)
                tb(0f, 5.4f, -1.2f, 9.4f, 1.4f, 2.8f, backC); tb(-2.6f, 5.2f, 2.5f, 1.2f, 1.2f, 0.4f, gold); tb(2.6f, 5.2f, 2.5f, 1.2f, 1.2f, 0.4f, gold)
            }
            2 -> { tb(0f, 0.5f, -3.4f, 6f, 8f, 2.8f, backC); tb(0f, 3.2f, -3.5f, 6.2f, 2.6f, 3f, lite(backC, 0.2f)); tb(-2.6f, 1.5f, 2.45f, 1.1f, 7f, 0.4f, shadeC(backC, 0.8f)); tb(2.6f, 1.5f, 2.45f, 1.1f, 7f, 0.4f, shadeC(backC, 0.8f)) }
            3 -> for (g in intArrayOf(-1, 1)) { val gf = g.toFloat(); tb(gf * 3.4f, 3f, -3f, 0.8f, 5.5f, 3.2f, backC, rz = gf * (-(20f + flap * 0.5f))); tb(gf * 4.8f, 1.6f, -3f, 0.8f, 3.2f, 2.4f, lite(backC, 0.3f), rz = gf * (-(36f + flap * 0.6f))) }
            4 -> { for (k in 0..2) { val kf = k.toFloat(); tb(sin(t * 3f - kf) * 0.7f * (0.4f + mv), -4.2f + kf * 0.8f, -3f - kf * 1.8f, 1.7f - kf * 0.2f, 1.7f - kf * 0.2f, 1.9f, if (k == 2) lite(backC, 0.4f) else backC) } }
            5 -> { tb(0.6f, 0.5f, -3.1f, 1f, 13f, 0.6f, steel, rz = 30f); tb(0.6f, 0.5f, -3.1f, 0.4f, 12.4f, 0.7f, lite(steel, 0.4f), rz = 30f); tb(-3.9f, 6.8f, -3.1f, 3.2f, 0.9f, 1f, gold, rz = 30f); tb(-4.6f, 8.2f, -3.1f, 1f, 3.6f, 1f, leather, rz = 30f); tb(-5f, 10f, -3.1f, 1.6f, 1.6f, 1.2f, gold) }
            6 -> for (g in intArrayOf(-1, 1)) { val gf = g.toFloat(); tb(gf * 5.2f, 3.8f, -3.3f, 0.8f, 9.5f, 0.8f, shadeC(backC, 0.7f), rz = gf * (-(28f + flap * 0.6f))); tb(gf * 8.0f, 2.2f, -3.3f, 5f, 6.5f, 0.4f, backC, rz = gf * (-(28f + flap * 0.6f))); tb(gf * 5.4f, -1.2f, -3.3f, 2.6f, 2.4f, 0.4f, shadeC(backC, 0.8f), rz = gf * (-(24f + flap * 0.4f))) }
            7 -> { tb(2.2f, 2.5f, -3.4f, 2.8f, 10f, 2.8f, leather, rz = -14f); tb(2.9f, 8.2f, -3.4f, 0.8f, 2.6f, 0.8f, white, rz = -14f); tb(1.6f, 8.0f, -3.4f, 0.8f, 2.4f, 0.8f, 0xE05555, rz = -14f); tb(0f, 3.2f, 2.45f, 0.8f, 9f, 0.3f, leather, rz = -26f) }
            8 -> { for (k in 0..3) { val kf = k.toFloat(); val sz = 2.6f + (if (k == 3) 0f else kf * 0.5f); tb(sin(t * 3f - kf) * 0.9f * (0.4f + mv), -3.8f + kf * 0.9f, -3.2f - kf * 2.1f, sz, sz, 2.6f, if (k == 3) white else backC) } }
            9 -> { tb(0f, 0f, -3.7f, 7.4f, 9.5f, 3.4f, backC); tb(0f, -3f, -5.5f, 6f, 3.4f, 0.5f, lite(backC, 0.2f)); tb(0f, 3.8f, -3.8f, 7.6f, 2.4f, 3.6f, shadeC(backC, 0.85f)); tb(-2.8f, 1.5f, 2.45f, 1.1f, 7f, 0.4f, shadeC(backC, 0.8f)); tb(2.8f, 1.5f, 2.45f, 1.1f, 7f, 0.4f, shadeC(backC, 0.8f)) }
        }
    }

}
