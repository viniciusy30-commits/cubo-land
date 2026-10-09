package com.cuboland.app

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class HudView(ctx: Context, val game: Game, val onExit: () -> Unit) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val pt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var stickId = -1; private var lookId = -1
    private var scx = 0f; private var scy = 0f; private var sx = 0f; private var sy = 0f
    private var lx = 0f; private var ly = 0f
    private val btnId = HashMap<Int, Int>()
    private val press = FloatArray(5)
    // 0 atacar, 1 pular, 2 colocar, 3 câmera, 4 sair
    private fun bx(i: Int) = when (i) { 0 -> width - 100 * d; 1 -> width - 195 * d; 2 -> width - 70 * d; 3 -> width - 40 * d; else -> 40 * d }
    private fun byy(i: Int) = when (i) { 0 -> height - 100 * d; 1 -> height - 62 * d; 2 -> height - 190 * d; 3 -> 40 * d; else -> 40 * d }
    private fun br(i: Int) = when (i) { 0 -> 44 * d; 1 -> 34 * d; 2 -> 32 * d; else -> 22 * d }
    private val slot get() = 46 * d
    private fun hbLeft() = (width - 8 * slot - 7 * 4 * d) / 2f
    private fun hbTop() = height - slot - 10 * d

    private fun defStickX() = 110 * d
    private fun defStickY() = height - 100 * d

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val act = e.actionMasked; val idx = e.actionIndex
        when (act) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> down(e.getPointerId(idx), e.getX(idx), e.getY(idx))
            MotionEvent.ACTION_MOVE -> for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> up(e.getPointerId(idx), act == MotionEvent.ACTION_CANCEL)
        }
        return true
    }

    private fun down(id: Int, x: Float, y: Float) {
        for (i in 0 until 5) if (hypot(x - bx(i), y - byy(i)) < br(i) * 1.15f) {
            btnId[id] = i
            when (i) {
                0 -> game.wantAttack = true
                1 -> game.jumpHeld = true
                2 -> game.wantPlace = true
                3 -> game.wantCam = true
                4 -> onExit()
            }
            return
        }
        if (y > hbTop() - 4 * d) {
            val s = ((x - hbLeft()) / (slot + 4 * d)).toInt()
            if (x >= hbLeft() && s in 0..7) { game.sel = s; return }
        }
        if (x < width * 0.38f && stickId < 0) { stickId = id; scx = x; scy = y; sx = 0f; sy = 0f; return }
        if (lookId < 0) { lookId = id; lx = x; ly = y }
    }

    private fun move(id: Int, x: Float, y: Float) {
        if (id == stickId) {
            val r = 60 * d
            var dx = x - scx; var dy = y - scy
            val l = hypot(dx, dy)
            if (l > r) { dx = dx / l * r; dy = dy / l * r }
            sx = dx / r; sy = dy / r
            game.stickX = sx; game.stickY = -sy
        } else if (id == lookId) {
            game.addLook(x - lx, y - ly); lx = x; ly = y
        }
    }

    private fun up(id: Int, cancel: Boolean) {
        if (id == stickId) { stickId = -1; sx = 0f; sy = 0f; game.stickX = 0f; game.stickY = 0f }
        if (id == lookId) lookId = -1
        val b = btnId.remove(id)
        if (b == 1) game.jumpHeld = false
    }

    private fun heart(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        path.reset()
        path.moveTo(cx, cy + s * 0.9f)
        path.cubicTo(cx - s * 1.4f, cy - s * 0.1f, cx - s * 0.7f, cy - s * 1.1f, cx, cy - s * 0.35f)
        path.cubicTo(cx + s * 0.7f, cy - s * 1.1f, cx + s * 1.4f, cy - s * 0.1f, cx, cy + s * 0.9f)
        pt.style = Paint.Style.FILL; pt.color = color; c.drawPath(path, pt)
    }

    private fun miniCube(c: Canvas, cx: Float, cy: Float, s: Float, id: Int) {
        fun col(v: Int, m: Float): Int = Color.rgb(((v shr 16) and 255).let { (it * m).toInt() },
            ((v shr 8) and 255).let { (it * m).toInt() }, (v and 255).let { (it * m).toInt() })
        pt.style = Paint.Style.FILL
        path.reset(); path.moveTo(cx, cy - s); path.lineTo(cx + s, cy - s * 0.5f); path.lineTo(cx, cy); path.lineTo(cx - s, cy - s * 0.5f); path.close()
        pt.color = col(B.top[id], 1f); c.drawPath(path, pt)
        path.reset(); path.moveTo(cx - s, cy - s * 0.5f); path.lineTo(cx, cy); path.lineTo(cx, cy + s); path.lineTo(cx - s, cy + s * 0.5f); path.close()
        pt.color = col(B.side[id], 0.78f); c.drawPath(path, pt)
        path.reset(); path.moveTo(cx + s, cy - s * 0.5f); path.lineTo(cx, cy); path.lineTo(cx, cy + s); path.lineTo(cx + s, cy + s * 0.5f); path.close()
        pt.color = col(B.side[id], 0.6f); c.drawPath(path, pt)
    }

    private fun circle(c: Canvas, i: Int, active: Boolean) {
        press[i] += ((if (active) 1f else 0f) - press[i]) * 0.3f
        val r = br(i) * (1f - 0.08f * press[i])
        pt.style = Paint.Style.FILL; pt.color = Color.argb((90 + 70 * press[i]).toInt(), 20, 25, 40)
        c.drawCircle(bx(i), byy(i), r, pt)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 2.5f * d; pt.color = Color.argb(200, 255, 255, 255)
        c.drawCircle(bx(i), byy(i), r, pt)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        // vinheta de dano
        if (game.hurtFlash > 0f) { pt.style = Paint.Style.FILL; pt.color = Color.argb((game.hurtFlash * 90).toInt(), 255, 0, 0); c.drawRect(0f, 0f, w, h, pt) }
        // mira
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 2f * d; pt.color = Color.argb(200, 255, 255, 255); pt.strokeCap = Paint.Cap.ROUND
        c.drawLine(w / 2 - 8 * d, h / 2, w / 2 + 8 * d, h / 2, pt); c.drawLine(w / 2, h / 2 - 8 * d, w / 2, h / 2 + 8 * d, pt)
        // joystick
        val jx = if (stickId >= 0) scx else defStickX(); val jy = if (stickId >= 0) scy else defStickY()
        pt.style = Paint.Style.FILL; pt.color = Color.argb(60, 255, 255, 255); c.drawCircle(jx, jy, 60 * d, pt)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 2f * d; pt.color = Color.argb(150, 255, 255, 255); c.drawCircle(jx, jy, 60 * d, pt)
        pt.style = Paint.Style.FILL; pt.color = Color.argb(190, 255, 255, 255); c.drawCircle(jx + sx * 60 * d, jy + sy * 60 * d, 24 * d, pt)
        // botões
        for (i in 0 until 5) {
            circle(c, i, btnId.containsValue(i))
            val x = bx(i); val y = byy(i)
            pt.style = Paint.Style.STROKE; pt.strokeWidth = 4f * d; pt.color = Color.WHITE; pt.strokeCap = Paint.Cap.ROUND; pt.strokeJoin = Paint.Join.ROUND
            when (i) {
                0 -> { c.drawLine(x - 14 * d, y + 14 * d, x + 14 * d, y - 14 * d, pt); c.drawLine(x - 8 * d, y + 2 * d, x - 2 * d, y + 8 * d, pt)
                    c.drawLine(x - 18 * d, y + 18 * d, x - 12 * d, y + 12 * d, pt) }
                1 -> { path.reset(); path.moveTo(x - 12 * d, y + 5 * d); path.lineTo(x, y - 7 * d); path.lineTo(x + 12 * d, y + 5 * d); c.drawPath(path, pt)
                    c.drawLine(x, y - 7 * d, x, y + 12 * d, pt) }
                2 -> { c.drawRoundRect(x - 11 * d, y - 11 * d, x + 11 * d, y + 11 * d, 3 * d, 3 * d, pt); c.drawLine(x - 5 * d, y, x + 5 * d, y, pt); c.drawLine(x, y - 5 * d, x, y + 5 * d, pt) }
                3 -> { pt.strokeWidth = 3f * d; c.drawOval(x - 12 * d, y - 7 * d, x + 12 * d, y + 7 * d, pt); pt.style = Paint.Style.FILL; c.drawCircle(x, y, 3.5f * d, pt) }
                else -> { path.reset(); path.moveTo(x + 6 * d, y - 10 * d); path.lineTo(x - 6 * d, y); path.lineTo(x + 6 * d, y + 10 * d); c.drawPath(path, pt) }
            }
        }
        // hotbar
        val left = hbLeft(); val top = hbTop()
        for (i in 0 until 8) {
            val x = left + i * (slot + 4 * d)
            val s = i == game.sel
            pt.style = Paint.Style.FILL; pt.color = Color.argb(if (s) 190 else 120, 20, 25, 40)
            val rr = RectF(x, top - (if (s) 5 * d else 0f), x + slot, top + slot)
            c.drawRoundRect(rr, 8 * d, 8 * d, pt)
            pt.style = Paint.Style.STROKE; pt.strokeWidth = (if (s) 3f else 1.5f) * d; pt.color = if (s) Color.WHITE else Color.argb(120, 255, 255, 255)
            c.drawRoundRect(rr, 8 * d, 8 * d, pt)
            miniCube(c, x + slot / 2, rr.top + slot * 0.45f, slot * 0.3f, B.hotbar[i])
        }
        pt.style = Paint.Style.FILL; pt.color = Color.WHITE; pt.textSize = 14 * d; pt.textAlign = Paint.Align.CENTER
        pt.setShadowLayer(3 * d, 0f, 1f, Color.BLACK)
        c.drawText(B.names[B.hotbar[game.sel]], w / 2, top - 12 * d, pt)
        // vida
        val hp = game.hp
        for (i in 0 until 5) {
            val full = hp >= (i + 1) * 2; val half = hp == i * 2 + 1
            val cx = w / 2 - 60 * d + i * 30 * d
            heart(c, cx, 28 * d, 11 * d, Color.argb(120, 0, 0, 0))
            if (full) heart(c, cx, 28 * d, 10 * d, Color.rgb(255, 80, 100))
            else if (half) { c.save(); c.clipRect(cx - 14 * d, 0f, cx, 80 * d); heart(c, cx, 28 * d, 10 * d, Color.rgb(255, 80, 100)); c.restore() }
        }
        pt.textAlign = Paint.Align.RIGHT; pt.textSize = 15 * d
        c.drawText("Slimes: ${game.kills}", w - 78 * d, 46 * d, pt)
        if (game.deadTimer > 0f) { pt.textAlign = Paint.Align.CENTER; pt.textSize = 26 * d; c.drawText("Você desmaiou! Renascendo...", w / 2, h / 2 - 40 * d, pt) }
        pt.clearShadowLayer()
        postInvalidateOnAnimation()
    }
}
