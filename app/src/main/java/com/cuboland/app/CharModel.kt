package com.cuboland.app

import android.opengl.Matrix
import kotlin.math.abs
import kotlin.math.sin

/** (matriz-pai, px,py,pz, rx,ry, sx,sy,sz, oy, cor, alpha, rz) -> desenha uma caixa */
typealias BoxFn = (FloatArray, Float, Float, Float, Float, Float, Float, Float, Float, Float, Int, Float, Float) -> Unit

/** Modelo chibi do personagem, montado só com caixas. Usado no jogo (OpenGL) e na tela de personagem (preview). */
class CharModel {
    private var bx: BoxFn = { _, _, _, _, _, _, _, _, _, _, _, _, _ -> }
    private val t1 = FloatArray(16); private val t2 = FloatArray(16); private val t3 = FloatArray(16)
    private val t4 = FloatArray(16); private val t5 = FloatArray(16)

    private fun b(m: FloatArray, px: Float, py: Float, pz: Float, sx: Float, sy: Float, sz: Float, col: Int,
                  rx: Float = 0f, ry: Float = 0f, rz: Float = 0f, a: Float = 1f) =
        bx(m, px, py, pz, rx, ry, sx, sy, sz, 0f, col, a, rz)

    private fun piv(out: FloatArray, parent: FloatArray, px: Float, py: Float, pz: Float, rx: Float = 0f, ry: Float = 0f, rz: Float = 0f) {
        System.arraycopy(parent, 0, out, 0, 16)
        Matrix.translateM(out, 0, px, py, pz)
        if (rz != 0f) Matrix.rotateM(out, 0, rz, 0f, 0f, 1f)
        if (ry != 0f) Matrix.rotateM(out, 0, ry, 0f, 1f, 0f)
        if (rx != 0f) Matrix.rotateM(out, 0, rx, 1f, 0f, 0f)
    }

    private fun lite(c: Int, k: Float) = mixC(c, 0xFFFFFF, k)

    /** sw = giro das pernas (graus), angR/angL = ângulo dos braços, handOut = matriz da mão direita (pra segurar item) */
    fun draw(box: BoxFn, base: FloatArray, t: Float, sw: Float, angR: Float, angL: Float, handOut: FloatArray?) {
        bx = box
        val L = Look.v
        val girl = L[Look.GENDER] == 1
        val skin = Look.skin(); val skinD = shadeC(skin, 0.9f)
        val tc = Look.pal(Look.TOPC); val top = L[Look.TOP]
        val bc = Look.pal(Look.BOTTOMC); val bot = L[Look.BOTTOM]
        val move = abs(sw) / 38f
        val dress = top == 2; val overalls = top == 6
        val bw = if (girl) 0.44f else 0.48f
        val gold = 0xFFD060
        val trim = lite(tc, 0.35f); val dark = shadeC(tc, 0.78f)

        // ---------- pernas, calça/shorts e sapatos ----------
        var seg = when (bot) { 0 -> 0.40f; 1 -> 0.17f; 2 -> 0f; else -> 0.30f }
        var legCol = bc
        if (dress) seg = 0f
        if (overalls) { seg = 0.40f; legCol = tc }
        val sh = L[Look.SHOES]; val shc = Look.pal(Look.SHOEC)
        for (sd in intArrayOf(-1, 1)) {
            val s = sd.toFloat()
            piv(t1, base, s * 0.11f, 0.44f, 0f, rx = if (sd < 0) sw else -sw)
            b(t1, 0f, -0.22f, 0f, 0.19f, 0.44f, 0.21f, skin)
            if (seg > 0f) {
                b(t1, 0f, -seg / 2f, 0f, 0.205f, seg, 0.225f, legCol)
                if (seg >= 0.3f) b(t1, 0f, -seg + 0.0175f, 0f, 0.215f, 0.035f, 0.235f, shadeC(legCol, 0.8f))
            }
            when (sh) {
                0 -> {
                    b(t1, 0f, -0.34f, 0.03f, 0.225f, 0.2f, 0.28f, shc)
                    b(t1, 0f, -0.235f, 0.025f, 0.245f, 0.05f, 0.26f, lite(shc, 0.3f))
                    b(t1, 0f, -0.425f, 0.035f, 0.235f, 0.03f, 0.3f, 0x3A2A22)
                }
                1 -> {
                    b(t1, 0f, -0.335f, 0.03f, 0.22f, 0.13f, 0.29f, shc)
                    b(t1, 0f, -0.42f, 0.035f, 0.235f, 0.04f, 0.31f, 0xF8F8F8)
                    b(t1, 0f, -0.35f, 0.14f, 0.2f, 0.09f, 0.1f, lite(shc, 0.6f))
                    b(t1, 0f, -0.27f, 0.07f, 0.12f, 0.02f, 0.1f, 0xFFFFFF)
                }
                2 -> {
                    b(t1, 0f, -0.395f, 0.04f, 0.21f, 0.09f, 0.27f, shc)
                    b(t1, 0f, -0.43f, 0.04f, 0.215f, 0.02f, 0.28f, 0x3A2A22)
                    b(t1, 0f, -0.35f, 0.17f, 0.07f, 0.04f, 0.05f, lite(shc, 0.4f))
                }
                3 -> {
                    b(t1, 0f, -0.4f, 0.04f, 0.2f, 0.08f, 0.26f, skin)
                    b(t1, 0f, -0.415f, 0.17f, 0.18f, 0.05f, 0.04f, skinD)
                }
                else -> {
                    b(t1, 0f, -0.27f, 0.02f, 0.225f, 0.34f, 0.24f, shc)
                    b(t1, 0f, -0.095f, 0.015f, 0.245f, 0.06f, 0.25f, lite(shc, 0.3f))
                    b(t1, 0f, -0.425f, 0.035f, 0.235f, 0.03f, 0.3f, 0x3A2A22)
                    b(t1, 0f, -0.4f, 0.15f, 0.2f, 0.1f, 0.1f, shadeC(shc, 0.85f))
                }
            }
        }

        // ---------- corpo ----------
        if (!dress && !overalls) b(base, 0f, 0.46f, 0f, bw + 0.015f, 0.05f, 0.315f, shadeC(bc, 0.85f))
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
                b(base, 0f, 0.47f, 0f, bw + 0.06f, 0.14f, 0.36f, tc)
                b(base, 0f, 0.36f, 0f, 0.62f, 0.12f, 0.46f, tc)
                b(base, 0f, 0.27f, 0f, 0.76f, 0.10f, 0.56f, shadeC(tc, 0.94f))
                b(base, 0f, 0.225f, 0f, 0.78f, 0.03f, 0.58f, lite(tc, 0.55f))
                b(base, 0f, 0.5f, 0f, bw + 0.02f, 0.05f, 0.32f, trim)
                b(base, 0f, 0.5f, -0.18f, 0.16f, 0.1f, 0.05f, trim)
            }
            3 -> {
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, 0xF4F4F4)
                b(base, -bw * 0.3f, 0.69f, 0.01f, bw * 0.4f, 0.5f, 0.31f, tc)
                b(base, bw * 0.3f, 0.69f, 0.01f, bw * 0.4f, 0.5f, 0.31f, tc)
                b(base, -0.12f, 0.94f, 0f, 0.12f, 0.06f, 0.33f, dark)
                b(base, 0.12f, 0.94f, 0f, 0.12f, 0.06f, 0.33f, dark)
                b(base, 0f, 0.46f, 0f, bw + 0.02f, 0.05f, 0.32f, dark)
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
                b(base, 0f, 0.44f, 0.02f, bw, 0.1f, 0.32f, plate)
            }
            else -> {
                b(base, 0f, 0.69f, 0f, bw, 0.5f, 0.3f, 0xF4F4F4)
                b(base, 0f, 0.66f, 0.165f, 0.3f, 0.28f, 0.03f, tc)
                b(base, 0f, 0.7f, 0.185f, 0.15f, 0.1f, 0.02f, dark)
                b(base, -0.13f, 0.88f, 0f, 0.07f, 0.14f, 0.32f, tc)
                b(base, 0.13f, 0.88f, 0f, 0.07f, 0.14f, 0.32f, tc)
                b(base, 0f, 0.62f, -0.165f, bw, 0.34f, 0.03f, tc)
                b(base, -0.13f, 0.84f, 0.17f, 0.04f, 0.04f, 0.02f, gold)
                b(base, 0.13f, 0.84f, 0.17f, 0.04f, 0.04f, 0.02f, gold)
            }
        }
        if (bot == 2 && !dress && !overalls) {
            b(base, 0f, 0.45f, 0f, bw + 0.06f, 0.1f, 0.36f, bc)
            b(base, 0f, 0.36f, 0f, 0.6f, 0.1f, 0.44f, bc)
            b(base, 0f, 0.295f, 0f, 0.68f, 0.05f, 0.5f, lite(bc, 0.35f))
        }

        // ---------- braços ----------
        val sl = when (top) { 0 -> 0.16f; 1 -> 0.36f; 2 -> 0.13f; 3 -> 0.36f; 4 -> 0.30f; 5 -> 0.2f; else -> 0.16f }
        val slCol = when (top) { 5 -> 0x4A5568; 6 -> 0xF4F4F4; else -> tc }
        for (sd in intArrayOf(-1, 1)) {
            val s = sd.toFloat()
            val ang = if (sd > 0) angR else angL
            piv(t2, base, s * (bw / 2f + 0.09f), 0.88f, 0f, rx = ang)
            b(t2, 0f, -0.21f, 0f, 0.15f, 0.42f, 0.17f, skin)
            b(t2, 0f, -0.37f, 0.005f, 0.165f, 0.12f, 0.185f, skin)
            b(t2, 0f, (0.03f - sl) / 2f, 0f, 0.18f, sl + 0.03f, 0.2f, slCol)
            if (sl >= 0.28f) b(t2, 0f, -sl + 0.02f, 0f, 0.19f, 0.04f, 0.21f, shadeC(slCol, 0.82f))
            if (top == 5) {
                b(t2, s * 0.01f, 0.05f, 0f, 0.22f, 0.1f, 0.24f, mixC(0xC5CEDA, tc, 0.3f))
                b(t2, s * 0.01f, 0.0f, 0f, 0.225f, 0.025f, 0.245f, gold)
            }
            if (sd > 0 && handOut != null) {
                piv(handOut, base, s * (bw / 2f + 0.09f), 0.88f, 0f, rx = ang)
                Matrix.translateM(handOut, 0, 0f, -0.37f, 0.02f)
            }
        }

        // ---------- costas (capa, mochila, asas, rabinho) ----------
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
                for (sd in intArrayOf(-1, 1)) {
                    val s = sd.toFloat()
                    piv(t3, base, s * 0.09f, 0.82f, -0.19f, ry = s * (10f + sin(t * 5f) * 8f), rz = -s * 35f)
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
        }

        // ---------- pescoço ----------
        val ncol = Look.pal(Look.NECKC)
        when (L[Look.NECK]) {
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
            3 -> {
                b(base, 0f, 0.9f, 0.152f, 0.26f, 0.02f, 0.03f, gold)
                b(base, -0.13f, 0.9f, 0.07f, 0.02f, 0.02f, 0.16f, gold)
                b(base, 0.13f, 0.9f, 0.07f, 0.02f, 0.02f, 0.16f, gold)
                b(base, 0f, 0.89f, 0.16f, 0.05f, 0.03f, 0.04f, gold)
                b(base, 0f, 0.84f, 0.16f, 0.07f, 0.09f, 0.04f, ncol)
                b(base, -0.015f, 0.86f, 0.182f, 0.02f, 0.03f, 0.01f, 0xFFFFFF)
            }
        }

        // ---------- cabeça e rosto ----------
        val ec = Look.EYE_COLORS[L[Look.EYEC]]
        val dk = 0x2A2230
        b(base, 0f, 1.31f, 0f, 0.78f, 0.78f, 0.78f, skin)
        b(base, -0.4f, 1.27f, 0f, 0.05f, 0.12f, 0.1f, skinD)
        b(base, 0.4f, 1.27f, 0f, 0.05f, 0.12f, 0.1f, skinD)
        for (sd in intArrayOf(-1, 1)) {
            val s = sd.toFloat(); val ex = s * 0.2f; val ey = 1.25f
            when (L[Look.EYES]) {
                0 -> {
                    b(base, ex, ey, 0.398f, 0.17f, 0.23f, 0.02f, dk)
                    b(base, ex, ey - 0.005f, 0.402f, 0.135f, 0.19f, 0.02f, ec)
                    b(base, ex, ey - 0.065f, 0.404f, 0.125f, 0.06f, 0.02f, lite(ec, 0.45f))
                    b(base, ex, ey, 0.406f, 0.07f, 0.12f, 0.02f, 0x1A1620)
                    b(base, ex - 0.035f, ey + 0.06f, 0.41f, 0.055f, 0.055f, 0.02f, 0xFFFFFF)
                    b(base, ex + 0.03f, ey - 0.05f, 0.41f, 0.025f, 0.025f, 0.02f, 0xFFFFFF)
                    b(base, ex, ey + 0.115f, 0.4f, 0.19f, 0.03f, 0.02f, dk)
                }
                1 -> {
                    b(base, ex, ey + 0.015f, 0.4f, 0.09f, 0.035f, 0.02f, dk)
                    b(base, ex - 0.055f, ey - 0.01f, 0.4f, 0.06f, 0.035f, 0.02f, dk, rz = 25f)
                    b(base, ex + 0.055f, ey - 0.01f, 0.4f, 0.06f, 0.035f, 0.02f, dk, rz = -25f)
                }
                2 -> {
                    b(base, ex, ey, 0.4f, 0.11f, 0.14f, 0.02f, shadeC(ec, 0.35f))
                    b(base, ex - 0.02f, ey + 0.035f, 0.406f, 0.045f, 0.045f, 0.02f, 0xFFFFFF)
                    b(base, ex + 0.02f, ey - 0.035f, 0.406f, 0.02f, 0.02f, 0.02f, 0xFFFFFF)
                }
                3 -> {
                    b(base, ex, ey - 0.02f, 0.398f, 0.17f, 0.1f, 0.02f, dk)
                    b(base, ex, ey - 0.03f, 0.402f, 0.12f, 0.07f, 0.02f, ec)
                    b(base, ex - 0.03f, ey - 0.01f, 0.408f, 0.035f, 0.035f, 0.02f, 0xFFFFFF)
                    b(base, ex, ey + 0.04f, 0.404f, 0.2f, 0.03f, 0.02f, dk)
                }
                else -> {
                    b(base, ex, ey, 0.398f, 0.19f, 0.25f, 0.02f, dk)
                    b(base, ex, ey - 0.005f, 0.402f, 0.16f, 0.21f, 0.02f, ec)
                    b(base, ex, ey - 0.06f, 0.404f, 0.15f, 0.09f, 0.02f, lite(ec, 0.5f))
                    b(base, ex, ey, 0.406f, 0.08f, 0.13f, 0.02f, 0x1A1620)
                    b(base, ex - 0.04f, ey + 0.07f, 0.41f, 0.07f, 0.07f, 0.02f, 0xFFFFFF)
                    b(base, ex + 0.04f, ey - 0.06f, 0.41f, 0.035f, 0.035f, 0.02f, 0xFFFFFF)
                    b(base, ex + 0.05f, ey + 0.07f, 0.41f, 0.02f, 0.02f, 0.02f, 0xFFFFFF)
                    b(base, ex, ey + 0.125f, 0.4f, 0.21f, 0.03f, 0.02f, dk)
                }
            }
            if (girl && L[Look.EYES] != 1) b(base, ex + s * 0.105f, ey + 0.125f, 0.4f, 0.04f, 0.04f, 0.02f, dk)
            if (L[Look.BLUSH] == 1) b(base, s * 0.29f, 1.13f, 0.398f, 0.12f, 0.06f, 0.02f, 0xFF8CA0, a = 0.75f)
            b(base, s * 0.2f, 1.42f, 0.398f, if (girl) 0.1f else 0.12f, if (girl) 0.025f else 0.035f, 0.02f, shadeC(Look.hair(), 0.5f), rz = s * -7f)
            if (L[Look.FACE] == 3) {
                val fr = mixC(skin, 0x8A4A2A, 0.5f)
                b(base, s * 0.2f, 1.18f, 0.4f, 0.025f, 0.025f, 0.02f, fr)
                b(base, s * 0.26f, 1.2f, 0.4f, 0.025f, 0.025f, 0.02f, fr)
                b(base, s * 0.3f, 1.17f, 0.4f, 0.025f, 0.025f, 0.02f, fr)
            }
        }
        b(base, 0f, 1.09f, 0.398f, 0.06f, 0.022f, 0.02f, 0x8A3A3A)
        b(base, -0.045f, 1.11f, 0.398f, 0.022f, 0.022f, 0.02f, 0x8A3A3A)
        b(base, 0.045f, 1.11f, 0.398f, 0.022f, 0.022f, 0.02f, 0x8A3A3A)

        // ---------- cabelo ----------
        val hc = Look.hair(); val hd = shadeC(hc, 0.82f); val hl = lite(hc, 0.25f)
        val tie = Look.pal(Look.HATC)
        val hs = L[Look.HAIR]
        fun bangs(h: FloatArray) {
            val n = h.size
            for (i in 0 until n) b(base, (i - (n - 1) / 2f) * 0.2f, 1.71f - h[i] / 2f, 0.405f, 0.205f, h[i], 0.06f, hc)
        }
        if (hs == 7) {
            b(base, 0f, 1.72f, 0f, 0.98f, 0.3f, 0.98f, hc)
            b(base, -0.47f, 1.4f, 0f, 0.14f, 0.55f, 0.9f, hc)
            b(base, 0.47f, 1.4f, 0f, 0.14f, 0.55f, 0.9f, hc)
            b(base, 0f, 1.38f, -0.46f, 0.98f, 0.65f, 0.14f, hc)
            b(base, -0.4f, 1.86f, 0.2f, 0.3f, 0.3f, 0.3f, hl, ry = 45f)
            b(base, 0.4f, 1.86f, 0.2f, 0.3f, 0.3f, 0.3f, hl, ry = 45f)
            b(base, -0.4f, 1.86f, -0.25f, 0.3f, 0.3f, 0.3f, hl, ry = 45f)
            b(base, 0.4f, 1.86f, -0.25f, 0.3f, 0.3f, 0.3f, hl, ry = 45f)
            bangs(floatArrayOf(0.16f, 0.12f, 0.16f, 0.12f))
        } else {
            b(base, 0f, 1.655f, -0.005f, 0.86f, 0.17f, 0.86f, hc)
            val backLow = when (hs) { 0 -> 1.16f; 1 -> 1.3f; 2 -> 1.0f; 3 -> 1.2f; 4 -> 1.2f; 5 -> 1.25f; else -> 0.7f }
            b(base, 0f, (backLow + 1.7f) / 2f, -0.37f, 0.88f, 1.7f - backLow, 0.14f, hc)
            val sideLow = when (hs) { 2 -> 1.0f; 6 -> 0.85f; 0 -> 1.35f; else -> 1.4f }
            b(base, -0.415f, (sideLow + 1.7f) / 2f, -0.12f, 0.07f, 1.7f - sideLow, 0.52f, hc)
            b(base, 0.415f, (sideLow + 1.7f) / 2f, -0.12f, 0.07f, 1.7f - sideLow, 0.52f, hc)
            when (hs) {
                0 -> bangs(floatArrayOf(0.2f, 0.14f, 0.22f, 0.16f))
                1 -> {
                    bangs(floatArrayOf(0.16f, 0.1f, 0.18f, 0.1f))
                    b(base, 0f, 1.85f, 0.1f, 0.15f, 0.24f, 0.15f, hc)
                    b(base, -0.22f, 1.84f, 0f, 0.15f, 0.22f, 0.15f, hc, rz = 18f)
                    b(base, 0.22f, 1.84f, 0f, 0.15f, 0.22f, 0.15f, hc, rz = -18f)
                    b(base, -0.15f, 1.84f, -0.2f, 0.15f, 0.2f, 0.15f, hc, rz = 12f)
                    b(base, 0.15f, 1.84f, -0.2f, 0.15f, 0.2f, 0.15f, hc, rz = -12f)
                }
                2 -> {
                    bangs(floatArrayOf(0.2f, 0.17f, 0.17f, 0.2f))
                    b(base, -0.4f, 1.3f, 0.24f, 0.05f, 0.6f, 0.14f, hc)
                    b(base, 0.4f, 1.3f, 0.24f, 0.05f, 0.6f, 0.14f, hc)
                }
                3 -> {
                    bangs(floatArrayOf(0.2f, 0.16f, 0.2f, 0.16f))
                    for (sd in intArrayOf(-1, 1)) {
                        val s = sd.toFloat()
                        val sway = sin(t * 2.4f + s) * 4f + sw * 0.18f
                        piv(t3, base, s * 0.47f, 1.5f, -0.02f, rz = s * (8f + sway))
                        b(t3, 0f, 0f, 0f, 0.14f, 0.12f, 0.14f, tie)
                        b(t3, 0f, -0.2f, 0f, 0.16f, 0.34f, 0.16f, hc)
                        piv(t4, t3, 0f, -0.36f, 0f, rz = s * sin(t * 3f + s) * 5f)
                        b(t4, 0f, -0.14f, 0f, 0.12f, 0.28f, 0.12f, hd)
                        b(t4, 0f, -0.3f, 0f, 0.08f, 0.08f, 0.08f, hl)
                    }
                }
                4 -> {
                    bangs(floatArrayOf(0.22f, 0.14f, 0.14f, 0.22f))
                    b(base, 0f, 1.55f, -0.45f, 0.16f, 0.14f, 0.12f, tie)
                    piv(t3, base, 0f, 1.56f, -0.46f, rx = 30f + sw * 0.3f + sin(t * 2.5f) * 3f)
                    b(t3, 0f, -0.17f, 0f, 0.2f, 0.34f, 0.16f, hc)
                    piv(t4, t3, 0f, -0.34f, 0f, rx = 12f + sin(t * 3f) * 5f)
                    b(t4, 0f, -0.15f, 0f, 0.16f, 0.3f, 0.13f, hd)
                    b(t4, 0f, -0.32f, 0f, 0.1f, 0.08f, 0.1f, hl)
                }
                5 -> {
                    bangs(floatArrayOf(0.2f, 0.15f, 0.15f, 0.2f))
                    b(base, 0f, 1.9f, -0.08f, 0.34f, 0.3f, 0.34f, hc)
                    b(base, 0f, 1.76f, -0.08f, 0.24f, 0.05f, 0.24f, tie)
                    b(base, 0f, 1.99f, -0.08f, 0.2f, 0.08f, 0.2f, hl)
                }
                else -> {
                    bangs(floatArrayOf(0.2f, 0.2f, 0.14f, 0.2f))
                    b(base, -0.38f, 1.2f, 0.12f, 0.08f, 0.75f, 0.24f, hc)
                    b(base, 0.38f, 1.2f, 0.12f, 0.08f, 0.75f, 0.24f, hc)
                }
            }
        }

        // ---------- chapéus e enfeites ----------
        val hcl = Look.pal(Look.HATC)
        when (L[Look.HAT]) {
            1 -> {
                b(base, 0f, 1.745f, 0f, 1.25f, 0.05f, 1.25f, shadeC(hcl, 0.85f))
                b(base, 0f, 1.85f, 0f, 0.66f, 0.2f, 0.66f, hcl)
                b(base, 0f, 2.03f, 0f, 0.5f, 0.2f, 0.5f, hcl)
                b(base, 0f, 2.2f, 0f, 0.34f, 0.2f, 0.34f, hcl)
                piv(t3, base, 0f, 2.3f, 0f, rx = -20f)
                b(t3, 0f, 0.1f, 0f, 0.2f, 0.22f, 0.2f, hcl)
                b(base, 0f, 1.79f, 0f, 0.68f, 0.07f, 0.68f, gold)
                b(base, 0f, 1.79f, 0.345f, 0.1f, 0.1f, 0.02f, 0xFFF0A0)
            }
            2 -> {
                b(base, 0f, 1.76f, 0f, 0.9f, 0.12f, 0.9f, lite(hcl, 0.35f))
                b(base, 0f, 1.9f, 0f, 0.84f, 0.18f, 0.84f, hcl)
                b(base, 0f, 1.9f, 0f, 0.855f, 0.04f, 0.855f, lite(hcl, 0.5f))
                b(base, 0f, 2.03f, 0f, 0.66f, 0.12f, 0.66f, hcl)
                b(base, 0f, 2.15f, 0f, 0.2f, 0.2f, 0.2f, 0xF4F4F4)
            }
            3 -> {
                b(base, 0f, 1.8f, 0f, 0.88f, 0.18f, 0.88f, hcl)
                b(base, 0f, 1.9f, 0f, 0.6f, 0.04f, 0.6f, shadeC(hcl, 0.9f))
                b(base, 0f, 1.93f, 0f, 0.07f, 0.04f, 0.07f, lite(hcl, 0.3f))
                b(base, 0f, 1.725f, 0.52f, 0.62f, 0.045f, 0.34f, shadeC(hcl, 0.8f), rx = 10f)
                b(base, 0f, 1.82f, 0.445f, 0.12f, 0.08f, 0.02f, 0xFFFFFF)
            }
            4 -> {
                b(base, 0f, 1.78f, 0.34f, 0.74f, 0.1f, 0.06f, gold)
                b(base, 0f, 1.78f, -0.34f, 0.74f, 0.1f, 0.06f, gold)
                b(base, 0.34f, 1.78f, 0f, 0.06f, 0.1f, 0.62f, gold)
                b(base, -0.34f, 1.78f, 0f, 0.06f, 0.1f, 0.62f, gold)
                for (i in 0 until 4) {
                    val x = -0.3f + i * 0.2f
                    b(base, x, if (i % 2 == 0) 1.9f else 1.87f, 0.34f, 0.1f, if (i % 2 == 0) 0.16f else 0.1f, 0.06f, gold)
                    b(base, x, 1.88f, -0.34f, 0.1f, 0.14f, 0.06f, gold)
                }
                b(base, 0.34f, 1.88f, 0.12f, 0.06f, 0.14f, 0.1f, gold)
                b(base, -0.34f, 1.88f, 0.12f, 0.06f, 0.14f, 0.1f, gold)
                b(base, 0.34f, 1.88f, -0.12f, 0.06f, 0.14f, 0.1f, gold)
                b(base, -0.34f, 1.88f, -0.12f, 0.06f, 0.14f, 0.1f, gold)
                b(base, 0f, 1.78f, 0.375f, 0.07f, 0.07f, 0.02f, hcl)
                b(base, -0.2f, 1.78f, 0.375f, 0.05f, 0.05f, 0.02f, lite(hcl, 0.3f))
                b(base, 0.2f, 1.78f, 0.375f, 0.05f, 0.05f, 0.02f, lite(hcl, 0.3f))
            }
            5 -> {
                for (sd in intArrayOf(-1, 1)) {
                    val s = sd.toFloat()
                    b(base, s * 0.26f, 1.86f, -0.02f, 0.22f, 0.22f, 0.1f, hcl, rz = -s * 14f)
                    b(base, s * 0.3f, 1.99f, -0.02f, 0.12f, 0.12f, 0.1f, hcl, rz = -s * 20f)
                    b(base, s * 0.26f, 1.85f, 0.035f, 0.12f, 0.14f, 0.02f, 0xFFB0C8, rz = -s * 14f)
                }
            }
            6 -> {
                for (sd in intArrayOf(-1, 1)) {
                    val s = sd.toFloat()
                    b(base, s * 0.2f, 2.0f, -0.03f, 0.15f, 0.5f, 0.1f, hcl, rz = -s * 10f)
                    b(base, s * 0.235f, 2.3f, -0.03f, 0.13f, 0.14f, 0.1f, hcl, rz = -s * 20f)
                    b(base, s * 0.2f, 2.0f, 0.025f, 0.08f, 0.4f, 0.02f, 0xFFB0C8, rz = -s * 10f)
                }
            }
            7 -> {
                piv(t3, base, 0.28f, 1.78f, 0.1f, rz = -12f)
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
                b(base, 0f, 1.82f, 0f, 0.9f, 0.28f, 0.9f, iron)
                b(base, 0f, 1.69f, 0f, 0.92f, 0.05f, 0.92f, gold)
                b(base, 0f, 2.08f, -0.05f, 0.1f, 0.22f, 0.34f, hcl)
                b(base, 0f, 1.98f, 0f, 0.12f, 0.06f, 0.2f, gold)
                b(base, -0.455f, 1.6f, 0f, 0.05f, 0.18f, 0.4f, iron)
                b(base, 0.455f, 1.6f, 0f, 0.05f, 0.18f, 0.4f, iron)
            }
            9 -> {
                b(base, 0f, 1.745f, 0f, 1.34f, 0.04f, 1.34f, 0xE8C877)
                b(base, 0f, 1.86f, 0f, 0.68f, 0.2f, 0.68f, 0xEFD488)
                b(base, 0f, 1.79f, 0f, 0.7f, 0.06f, 0.7f, hcl)
            }
            10 -> {
                piv(t3, base, 0.3f, 1.74f, 0.2f)
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
        }

        // ---------- óculos (lente translúcida por último) ----------
        val fc = L[Look.FACE]
        if (fc == 1 || fc == 2) {
            val frame = if (fc == 1) 0x3A2E24 else 0x1E1E28
            val cy = 1.25f
            for (sd in intArrayOf(-1, 1)) {
                val s = sd.toFloat(); val cx = s * 0.2f
                if (fc == 1) {
                    b(base, cx, cy + 0.13f, 0.41f, 0.27f, 0.03f, 0.025f, frame)
                    b(base, cx, cy - 0.13f, 0.41f, 0.27f, 0.03f, 0.025f, frame)
                    b(base, cx - 0.135f, cy, 0.41f, 0.03f, 0.26f, 0.025f, frame)
                    b(base, cx + 0.135f, cy, 0.41f, 0.03f, 0.26f, 0.025f, frame)
                } else {
                    b(base, cx, cy, 0.41f, 0.25f, 0.2f, 0.03f, frame)
                    b(base, cx - 0.05f, cy + 0.04f, 0.428f, 0.07f, 0.03f, 0.01f, 0x6A6A88)
                }
                b(base, s * 0.41f, cy + 0.07f, 0.18f, 0.025f, 0.025f, 0.4f, frame)
            }
            b(base, 0f, cy + 0.04f, 0.41f, 0.1f, 0.03f, 0.025f, frame)
            if (fc == 2) b(base, 0f, cy + 0.1f, 0.41f, 0.56f, 0.035f, 0.03f, frame)
            if (fc == 1) {
                b(base, -0.2f, cy, 0.405f, 0.25f, 0.25f, 0.015f, 0xCFE8FF, a = 0.28f)
                b(base, 0.2f, cy, 0.405f, 0.25f, 0.25f, 0.015f, 0xCFE8FF, a = 0.28f)
            }
        }
    }
}
