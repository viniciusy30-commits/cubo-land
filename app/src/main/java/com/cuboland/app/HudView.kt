package com.cuboland.app

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class HudView(ctx: Context, val game: Game, val onExit: () -> Unit) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val pt = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bp = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val mx = Matrix()
    private val src = floatArrayOf(0f, 0f, 16f, 0f, 0f, 16f)
    private val dst = FloatArray(6)
    private val fLeft = LightingColorFilter(0xFFCCCCCC.toInt(), 0)
    private val fRight = LightingColorFilter(0xFFA0A0A0.toInt(), 0)
    private val path = Path()
    private var stickId = -1; private var lookId = -1
    private var scx = 0f; private var scy = 0f; private var sx = 0f; private var sy = 0f
    private var lx = 0f; private var ly = 0f
    private val btnId = HashMap<Int, Int>()
    private val press = FloatArray(6)
    private var invOpen = false; private var invSel = -1
    private var bounce = 0f; private var lastSel = -1
    // 0 atacar, 1 pular, 2 colocar, 3 câmera, 4 sair, 5 mochila
    private fun bx(i: Int) = when (i) { 0 -> width - 100 * d; 1 -> width - 195 * d; 2 -> width - 70 * d; 3 -> width - 40 * d; 5 -> width - 92 * d; else -> 40 * d }
    private fun byy(i: Int) = when (i) { 0 -> height - 100 * d; 1 -> height - 62 * d; 2 -> height - 190 * d; else -> 40 * d }
    private fun br(i: Int) = when (i) { 0 -> 44 * d; 1 -> 34 * d; 2 -> 32 * d; else -> 22 * d }
    private val slot get() = 46 * d
    private fun hbLeft() = (width - 8 * slot - 7 * 4 * d) / 2f
    private fun hbTop() = height - slot - 10 * d
    private fun defStickX() = 110 * d
    private fun defStickY() = height - 100 * d

    // painel da mochila
    private fun pl() = 24 * d
    private fun pt0() = 12 * d
    private fun pr() = width - 24 * d
    private fun pb() = hbTop() - 26 * d
    private val cell get() = 58 * d
    private fun cols() = max(1, ((pr() - pl() - 32 * d) / cell).toInt())
    private fun cellX(i: Int) = pl() + 16 * d + (i % cols()) * cell
    private fun cellY(i: Int) = pt0() + 48 * d + (i / cols()) * cell

    private fun col(v: Int) = v or (0xFF shl 24)

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val act = e.actionMasked; val idx = e.actionIndex
        when (act) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> down(e.getPointerId(idx), e.getX(idx), e.getY(idx))
            MotionEvent.ACTION_MOVE -> if (!invOpen) for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> up(e.getPointerId(idx))
        }
        return true
    }

    private fun toggleInv() {
        invOpen = !invOpen
        stickId = -1; lookId = -1; sx = 0f; sy = 0f
        game.stickX = 0f; game.stickY = 0f; game.jumpHeld = false; btnId.clear()
        invSel = game.cur()
    }

    private fun hotbarHit(x: Float, y: Float): Boolean {
        if (y > hbTop() - 8 * d) {
            val s = ((x - hbLeft()) / (slot + 4 * d)).toInt()
            if (x >= hbLeft() && s in 0..7) { game.sel = s; invSel = game.cur(); return true }
        }
        return false
    }

    private fun down(id: Int, x: Float, y: Float) {
        if (hypot(x - bx(5), y - byy(5)) < br(5) * 1.2f) { toggleInv(); return }
        if (invOpen) {
            if (hotbarHit(x, y)) return
            if (hypot(x - (pr() - 20 * d), y - (pt0() + 22 * d)) < 22 * d) { toggleInv(); return }
            for (i in Items.inventory.indices) {
                if (x >= cellX(i) && x <= cellX(i) + cell - 6 * d && y >= cellY(i) && y <= cellY(i) + cell - 6 * d) {
                    invSel = Items.inventory[i]; game.hotbar[game.sel] = invSel; bounce = 1f; return
                }
            }
            if (x < pl() || x > pr() || y < pt0() || y > pb()) toggleInv()
            return
        }
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
        if (hotbarHit(x, y)) return
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

    private fun up(id: Int) {
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

    private fun face(c: Canvas, tile: Int, a: Float, b: Float, cc: Float, dd: Float, ee: Float, ff: Float, f: ColorFilter?) {
        dst[0] = a; dst[1] = b; dst[2] = cc; dst[3] = dd; dst[4] = ee; dst[5] = ff
        mx.setPolyToPoly(src, 0, dst, 0, 3)
        bp.colorFilter = f
        c.drawBitmap(Atlas.tiles[tile], mx, bp)
    }

    private fun blockIcon(c: Canvas, cx: Float, cy: Float, s: Float, id: Int) {
        face(c, B.tTop[id], cx, cy - s, cx + s, cy - s / 2, cx - s, cy - s / 2, null)
        face(c, B.tSide[id], cx - s, cy - s / 2, cx, cy, cx - s, cy + s / 2, fLeft)
        face(c, B.tSide[id], cx, cy, cx + s, cy - s / 2, cx, cy + s, fRight)
    }

    private fun toolIcon(c: Canvas, cx: Float, cy: Float, s: Float, id: Int) {
        c.save(); c.translate(cx, cy); c.rotate(45f)
        pt.style = Paint.Style.FILL
        fun r(l: Float, t: Float, rr: Float, b: Float, color: Int) { pt.color = col(color); c.drawRoundRect(l * s, t * s, rr * s, b * s, 0.03f * s, 0.03f * s, pt) }
        when (id) {
            Items.SWORD -> { r(-0.09f, -0.62f, 0.09f, 0.12f, 0xE2ECF8); r(-0.03f, -0.6f, 0.03f, 0.1f, 0x9FD8FF); r(-0.3f, 0.1f, 0.3f, 0.2f, 0xFFD060)
                r(-0.06f, 0.2f, 0.06f, 0.42f, 0x7A5230); pt.color = col(0xFF6FA5); c.drawCircle(0f, 0.47f * s, 0.08f * s, pt) }
            Items.AXE -> { r(-0.05f, -0.6f, 0.05f, 0.5f, 0x7A5230)
                path.reset(); path.moveTo(0.05f * s, -0.58f * s); path.lineTo(0.5f * s, -0.45f * s); path.lineTo(0.5f * s, -0.02f * s); path.lineTo(0.05f * s, -0.15f * s); path.close()
                pt.color = col(0xC8D2DE); c.drawPath(path, pt); r(0.46f, -0.45f, 0.52f, -0.02f, 0xFFFFFF) }
            Items.STAFF -> { r(-0.05f, -0.5f, 0.05f, 0.55f, 0x9B6BE0); r(-0.09f, -0.42f, 0.09f, -0.36f, 0xFFD060)
                pt.color = Color.argb(90, 127, 232, 255); c.drawCircle(0f, -0.64f * s, 0.3f * s, pt)
                pt.color = col(0x7FE8FF); c.drawCircle(0f, -0.64f * s, 0.18f * s, pt); pt.color = Color.WHITE; c.drawCircle(-0.04f * s, -0.68f * s, 0.06f * s, pt) }
            Items.PICK -> { r(-0.05f, -0.55f, 0.05f, 0.5f, 0x7A5230); r(-0.5f, -0.58f, 0.5f, -0.44f, 0xC8D2DE); r(-0.52f, -0.5f, -0.4f, -0.3f, 0xFFFFFF); r(0.4f, -0.5f, 0.52f, -0.3f, 0xFFFFFF) }
        }
        c.restore()
    }

    private fun icon(c: Canvas, cx: Float, cy: Float, size: Float, id: Int) {
        if (Items.isBlock(id)) blockIcon(c, cx, cy, size * 0.3f, id) else toolIcon(c, cx, cy, size * 0.62f, id)
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
        if (game.hurtFlash > 0f) { pt.style = Paint.Style.FILL; pt.color = Color.argb((game.hurtFlash * 90).toInt(), 255, 0, 0); c.drawRect(0f, 0f, w, h, pt) }
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 2f * d; pt.color = Color.argb(200, 255, 255, 255); pt.strokeCap = Paint.Cap.ROUND
        if (!invOpen) { c.drawLine(w / 2 - 8 * d, h / 2, w / 2 + 8 * d, h / 2, pt); c.drawLine(w / 2, h / 2 - 8 * d, w / 2, h / 2 + 8 * d, pt) }
        if (!invOpen) {
            val jx = if (stickId >= 0) scx else defStickX(); val jy = if (stickId >= 0) scy else defStickY()
            pt.style = Paint.Style.FILL; pt.color = Color.argb(60, 255, 255, 255); c.drawCircle(jx, jy, 60 * d, pt)
            pt.style = Paint.Style.STROKE; pt.strokeWidth = 2f * d; pt.color = Color.argb(150, 255, 255, 255); c.drawCircle(jx, jy, 60 * d, pt)
            pt.style = Paint.Style.FILL; pt.color = Color.argb(190, 255, 255, 255); c.drawCircle(jx + sx * 60 * d, jy + sy * 60 * d, 24 * d, pt)
        }
        for (i in 0 until 6) {
            if (invOpen && i != 5) continue
            circle(c, i, btnId.containsValue(i) || (i == 5 && invOpen))
            val x = bx(i); val y = byy(i)
            pt.style = Paint.Style.STROKE; pt.strokeWidth = 4f * d; pt.color = Color.WHITE; pt.strokeCap = Paint.Cap.ROUND; pt.strokeJoin = Paint.Join.ROUND
            when (i) {
                0 -> { c.drawLine(x - 14 * d, y + 14 * d, x + 14 * d, y - 14 * d, pt); c.drawLine(x - 8 * d, y + 2 * d, x - 2 * d, y + 8 * d, pt)
                    c.drawLine(x - 18 * d, y + 18 * d, x - 12 * d, y + 12 * d, pt) }
                1 -> { path.reset(); path.moveTo(x - 12 * d, y + 5 * d); path.lineTo(x, y - 7 * d); path.lineTo(x + 12 * d, y + 5 * d); c.drawPath(path, pt)
                    c.drawLine(x, y - 7 * d, x, y + 12 * d, pt) }
                2 -> { c.drawRoundRect(x - 11 * d, y - 11 * d, x + 11 * d, y + 11 * d, 3 * d, 3 * d, pt); c.drawLine(x - 5 * d, y, x + 5 * d, y, pt); c.drawLine(x, y - 5 * d, x, y + 5 * d, pt) }
                3 -> { pt.strokeWidth = 3f * d; c.drawOval(x - 12 * d, y - 7 * d, x + 12 * d, y + 7 * d, pt); pt.style = Paint.Style.FILL; c.drawCircle(x, y, 3.5f * d, pt) }
                5 -> { pt.strokeWidth = 3f * d; c.drawRoundRect(x - 10 * d, y - 6 * d, x + 10 * d, y + 11 * d, 5 * d, 5 * d, pt)
                    path.reset(); path.moveTo(x - 6 * d, y - 6 * d); path.cubicTo(x - 6 * d, y - 15 * d, x + 6 * d, y - 15 * d, x + 6 * d, y - 6 * d); c.drawPath(path, pt)
                    c.drawLine(x - 10 * d, y, x + 10 * d, y, pt) }
                else -> { path.reset(); path.moveTo(x + 6 * d, y - 10 * d); path.lineTo(x - 6 * d, y); path.lineTo(x + 6 * d, y + 10 * d); c.drawPath(path, pt) }
            }
        }
        // hotbar
        if (game.sel != lastSel) { lastSel = game.sel; bounce = 1f }
        bounce = max(0f, bounce - 0.08f)
        val left = hbLeft(); val top = hbTop()
        for (i in 0 until 8) {
            val x = left + i * (slot + 4 * d)
            val s = i == game.sel
            val lift = if (s) 5 * d + 5 * d * sin(bounce * 3.14f) else 0f
            pt.style = Paint.Style.FILL; pt.color = Color.argb(if (s) 200 else 120, 20, 25, 40)
            val rr = RectF(x, top - lift, x + slot, top + slot - lift * 0.0f)
            c.drawRoundRect(rr, 8 * d, 8 * d, pt)
            pt.style = Paint.Style.STROKE; pt.strokeWidth = (if (s) 3f else 1.5f) * d; pt.color = if (s) Color.rgb(255, 224, 120) else Color.argb(120, 255, 255, 255)
            c.drawRoundRect(rr, 8 * d, 8 * d, pt)
            icon(c, x + slot / 2, rr.top + slot * 0.5f, slot, game.hotbar[i])
        }
        pt.style = Paint.Style.FILL; pt.color = Color.WHITE; pt.textSize = 14 * d; pt.textAlign = Paint.Align.CENTER
        pt.setShadowLayer(3 * d, 0f, 1f, Color.BLACK)
        if (!invOpen) c.drawText(Items.name(game.cur()), w / 2, top - 14 * d, pt)
        val hp = game.hp
        for (i in 0 until 5) {
            val full = hp >= (i + 1) * 2; val half = hp == i * 2 + 1
            val cx = w / 2 - 60 * d + i * 30 * d
            heart(c, cx, 28 * d, 11 * d, Color.argb(120, 0, 0, 0))
            if (full) heart(c, cx, 28 * d, 10 * d, Color.rgb(255, 80, 100))
            else if (half) { c.save(); c.clipRect(cx - 14 * d, 0f, cx, 80 * d); heart(c, cx, 28 * d, 10 * d, Color.rgb(255, 80, 100)); c.restore() }
        }
        pt.textAlign = Paint.Align.RIGHT; pt.textSize = 15 * d
        c.drawText("Slimes: ${game.kills}", w - 124 * d, 46 * d, pt)
        if (game.deadTimer > 0f) { pt.textAlign = Paint.Align.CENTER; pt.textSize = 26 * d; c.drawText("Você desmaiou! Renascendo...", w / 2, h / 2 - 40 * d, pt) }
        pt.clearShadowLayer()
        if (invOpen) drawInventory(c)
        postInvalidateOnAnimation()
    }

    private fun drawInventory(c: Canvas) {
        val rr = RectF(pl(), pt0(), pr(), pb())
        pt.style = Paint.Style.FILL; pt.color = Color.argb(215, 28, 34, 56); c.drawRoundRect(rr, 18 * d, 18 * d, pt)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 3f * d; pt.color = Color.argb(200, 255, 224, 120); c.drawRoundRect(rr, 18 * d, 18 * d, pt)
        pt.style = Paint.Style.FILL; pt.color = Color.WHITE; pt.textSize = 20 * d; pt.textAlign = Paint.Align.LEFT; pt.isFakeBoldText = true
        c.drawText("Mochila", pl() + 18 * d, pt0() + 32 * d, pt)
        pt.isFakeBoldText = false; pt.textSize = 12 * d; pt.color = Color.argb(200, 255, 255, 255)
        c.drawText("Toque num item para colocá-lo no slot selecionado", pl() + 110 * d, pt0() + 31 * d, pt)
        // fechar
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 3f * d; pt.color = Color.WHITE; pt.strokeCap = Paint.Cap.ROUND
        val xx = pr() - 20 * d; val yy = pt0() + 22 * d
        c.drawLine(xx - 7 * d, yy - 7 * d, xx + 7 * d, yy + 7 * d, pt); c.drawLine(xx + 7 * d, yy - 7 * d, xx - 7 * d, yy + 7 * d, pt)
        for ((i, id) in Items.inventory.withIndex()) {
            val x = cellX(i); val y = cellY(i); val s = cell - 6 * d
            pt.style = Paint.Style.FILL; pt.color = Color.argb(if (id == invSel) 150 else 80, 255, 255, 255)
            val cr = RectF(x, y, x + s, y + s); c.drawRoundRect(cr, 10 * d, 10 * d, pt)
            if (id == invSel) { pt.style = Paint.Style.STROKE; pt.strokeWidth = 3f * d; pt.color = Color.rgb(255, 224, 120); c.drawRoundRect(cr, 10 * d, 10 * d, pt) }
            icon(c, x + s / 2, y + s / 2, s, id)
        }
        val sel = if (invSel > 0) invSel else game.cur()
        val ty = cellY(Items.inventory.size - 1) + cell + 18 * d
        pt.style = Paint.Style.FILL; pt.color = Color.WHITE; pt.textSize = 17 * d; pt.isFakeBoldText = true
        if (ty < pb() - 24 * d) {
            c.drawText(Items.name(sel), pl() + 18 * d, ty, pt)
            pt.isFakeBoldText = false; pt.textSize = 13 * d; pt.color = Color.argb(220, 255, 255, 255)
            c.drawText(Items.desc(sel), pl() + 18 * d, ty + 20 * d, pt)
        }
        pt.isFakeBoldText = false
    }
}
