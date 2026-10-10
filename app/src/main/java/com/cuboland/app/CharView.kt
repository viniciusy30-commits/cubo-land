package com.cuboland.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.opengl.Matrix
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Preview 3D do personagem: rasterizador próprio (z-buffer) em baixa resolução, usando o MESMO modelo do jogo. Arraste pra girar. */
class CharView(ctx: Context) : View(ctx) {
    private val W = 300; private val H = 380; private val S = 140f
    private val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
    private val pix = IntArray(W * H)
    private val zb = FloatArray(W * H)
    private val model = CharModel()
    private val base = FloatArray(16); private val tmp = FloatArray(16)
    private val paint = Paint().apply { isFilterBitmap = false }
    private val dst = Rect()
    private var yaw = -25f; private var drag = false; private var lastX = 0f
    private var tt = 0f; private var lastT = System.nanoTime()
    /** 0 parado, 1 andando, 2 correndo, 3 pulando, 4 atacando */
    var mode = 0
    private val an = Anim(); private var ph = 0f
    private val wx = FloatArray(8); private val wy = FloatArray(8); private val wz = FloatArray(8)
    private val faces = arrayOf(intArrayOf(4, 5, 7, 6), intArrayOf(1, 0, 2, 3), intArrayOf(5, 1, 3, 7), intArrayOf(0, 4, 6, 2), intArrayOf(6, 7, 3, 2), intArrayOf(0, 1, 5, 4))
    private val sx = FloatArray(4); private val sy = FloatArray(4); private val sz = FloatArray(4)

    private fun shadeCol(c: Int, k: Float): Int {
        val r = min(255, (Color.red(c) * k).toInt()); val g = min(255, (Color.green(c) * k).toInt()); val b = min(255, (Color.blue(c) * k).toInt())
        return Color.rgb(r, g, b)
    }

    private fun tri(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float, col: Int, alpha: Float) {
        val area = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
        if (abs(area) < 1e-6f) return
        val x0 = max(0, floor(min(ax, min(bx, cx))).toInt()); val x1 = min(W - 1, ceil(max(ax, max(bx, cx))).toInt())
        val y0 = max(0, floor(min(ay, min(by, cy))).toInt()); val y1 = min(H - 1, ceil(max(ay, max(by, cy))).toInt())
        for (y in y0..y1) for (x in x0..x1) {
            val px = x + 0.5f; val py = y + 0.5f
            val w0 = ((bx - px) * (cy - py) - (by - py) * (cx - px)) / area
            val w1 = ((cx - px) * (ay - py) - (cy - py) * (ax - px)) / area
            val w2 = 1f - w0 - w1
            if (w0 < -1e-4f || w1 < -1e-4f || w2 < -1e-4f) continue
            val z = w0 * az + w1 * bz + w2 * cz
            val i = y * W + x
            if (z <= zb[i]) continue
            if (alpha >= 0.99f) { zb[i] = z; pix[i] = col }
            else pix[i] = mixC(pix[i], col, alpha)
        }
    }

    private val boxFn: BoxFn = { m, ox, oy, oz, rx, ry, bsx, bsy, bsz, off, col, a, rz ->
        System.arraycopy(m, 0, tmp, 0, 16)
        Matrix.translateM(tmp, 0, ox, oy, oz)
        if (rz != 0f) Matrix.rotateM(tmp, 0, rz, 0f, 0f, 1f)
        if (ry != 0f) Matrix.rotateM(tmp, 0, ry, 0f, 1f, 0f)
        if (rx != 0f) Matrix.rotateM(tmp, 0, rx, 1f, 0f, 0f)
        Matrix.translateM(tmp, 0, 0f, off, 0f)
        Matrix.scaleM(tmp, 0, bsx, bsy, bsz)
        for (i in 0 until 8) {
            val x = if ((i and 1) != 0) 0.5f else -0.5f
            val y = if ((i and 2) != 0) 0.5f else -0.5f
            val z = if ((i and 4) != 0) 0.5f else -0.5f
            wx[i] = tmp[0] * x + tmp[4] * y + tmp[8] * z + tmp[12]
            wy[i] = tmp[1] * x + tmp[5] * y + tmp[9] * z + tmp[13]
            wz[i] = tmp[2] * x + tmp[6] * y + tmp[10] * z + tmp[14]
        }
        for (f in faces) {
            val a0 = f[0]; val a1 = f[1]; val a2 = f[2]
            val ux = wx[a1] - wx[a0]; val uy = wy[a1] - wy[a0]; val uz = wz[a1] - wz[a0]
            val vx = wx[a2] - wx[a0]; val vy = wy[a2] - wy[a0]; val vz = wz[a2] - wz[a0]
            var nx = uy * vz - uz * vy; var ny = uz * vx - ux * vz; var nz = ux * vy - uy * vx
            val nl = sqrt(nx * nx + ny * ny + nz * nz)
            if (nl > 1e-8f) {
                nx /= nl; ny /= nl; nz /= nl
                if (nz > 0f) {
                    val lit = max(0f, nx * -0.35f + ny * 0.75f + nz * 0.55f)
                    val c = shadeCol(col, 0.58f + 0.5f * lit)
                    for (k in 0 until 4) {
                        sx[k] = W * 0.5f + wx[f[k]] * S
                        sy[k] = H * 0.95f - wy[f[k]] * S
                        sz[k] = wz[f[k]]
                    }
                    tri(sx[0], sy[0], sz[0], sx[1], sy[1], sz[1], sx[2], sy[2], sz[2], c, a)
                    tri(sx[0], sy[0], sz[0], sx[2], sy[2], sz[2], sx[3], sy[3], sz[3], c, a)
                }
            }
        }
    }

    private fun background() {
        val top = Color.rgb(150, 205, 250); val bot = Color.rgb(214, 238, 255)
        for (y in 0 until H) {
            val c = mixC(top, bot, y / (H - 1f))
            val row = y * W
            for (x in 0 until W) { pix[row + x] = c; zb[row + x] = -1e9f }
        }
        // chão e sombrinha
        val gy = (H * 0.95f).toInt()
        for (y in gy - 6..min(H - 1, gy + 14)) for (x in 0 until W) {
            val dx = (x - W * 0.5f) / 70f; val dy = (y - gy) / 12f
            val d = dx * dx + dy * dy
            val i = y * W + x
            if (d < 1f) pix[i] = mixC(pix[i], Color.rgb(40, 60, 40), 0.35f * (1f - d))
        }
    }

    override fun onDraw(c: Canvas) {
        val now = System.nanoTime(); val dt = min(0.1f, (now - lastT) / 1e9f); lastT = now
        tt += dt
        if (!drag) yaw += dt * 28f
        background()
        Matrix.setIdentityM(base, 0)
        Matrix.rotateM(base, 0, yaw, 0f, 1f, 0f)
        an.t = tt
        var hop = 0f
        an.move = 0f; an.run = 0f; an.air = false; an.landT = 0f; an.atk = 1f; an.atkArm = 0f; an.hasTool = false; an.vy = 0f
        when (mode) {
            1 -> { ph += dt * 8f; an.move = 1f }
            2 -> { ph += dt * 14f; an.move = 1f; an.run = 1f }
            3 -> {
                val jt = tt % 1.7f
                if (jt < 0.9f) { an.air = true; an.vy = if (jt < 0.45f) 5f else -5f; hop = sin(jt / 0.9f * 3.1416f) * 0.45f }
                else if (jt < 1.2f) an.landT = 1f - (jt - 0.9f) / 0.3f
            }
            4 -> { val cyc = (tt % 0.9f) / 0.9f; an.atk = cyc; an.atkArm = CharModel.atkDelta(cyc); an.hasTool = true }
        }
        an.phase = ph
        if (hop > 0f) Matrix.translateM(base, 0, 0f, hop, 0f)
        model.draw(boxFn, base, an, null)
        bmp.setPixels(pix, 0, W, 0, 0, W, H)
        val sc = min(width / W.toFloat(), height / H.toFloat())
        val dw = (W * sc).toInt(); val dh = (H * sc).toInt()
        val l = (width - dw) / 2; val t = (height - dh) / 2
        dst.set(l, t, l + dw, t + dh)
        c.drawBitmap(bmp, null, dst, paint)
        postInvalidateOnAnimation()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { drag = true; lastX = e.x }
            MotionEvent.ACTION_MOVE -> { yaw += (e.x - lastX) * 0.6f; lastX = e.x }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> drag = false
        }
        return true
    }
}
