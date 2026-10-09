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
    private val src = floatArrayOf(0f, 0f, Atlas.T.toFloat(), 0f, 0f, Atlas.T.toFloat())
    private val dst = FloatArray(6)
    private val fLeft = LightingColorFilter(0xFFCCCCCC.toInt(), 0)
    private val fRight = LightingColorFilter(0xFFA0A0A0.toInt(), 0)
    private val path = Path()
    private var stickId = -1; private var lookId = -1
    private var scx = 0f; private var scy = 0f; private var sx = 0f; private var sy = 0f
    private var lx = 0f; private var ly = 0f
    private val btnId = HashMap<Int, Int>()
    private val press = FloatArray(7)
    private var invOpen = false; private var invSel = -1
    private var bounce = 0f; private var lastSel = -1
    // 0 atacar, 1 pular, 2 colocar, 3 câmera, 4 sair, 5 mochila
    private fun bx(i: Int) = when (i) { 0 -> width - 100 * d; 1 -> width - 195 * d; 6 -> width - 195 * d; 2 -> width - 70 * d; 3 -> width - 40 * d; 5 -> width - 92 * d; else -> 40 * d }
    private fun byy(i: Int) = when (i) { 0 -> height - 100 * d; 1 -> height - 62 * d; 6 -> height - 152 * d; 2 -> height - 190 * d; else -> 40 * d }
    private fun br(i: Int) = when (i) { 0 -> 44 * d; 1 -> 34 * d; 6 -> 30 * d; 2 -> 32 * d; else -> 22 * d }
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
        game.stickX = 0f; game.stickY = 0f; game.jumpHeld = false; game.downHeld = false; game.attackHeld = false; btnId.clear()
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
        for (i in intArrayOf(0, 1, 2, 3, 4, 6)) if ((i != 6 || game.flying) && hypot(x - bx(i), y - byy(i)) < br(i) * 1.15f) {
            btnId[id] = i
            when (i) {
                0 -> { game.attackPress = true; game.attackHeld = true }
                1 -> { game.jumpHeld = true; game.jumpTap() }
                6 -> game.downHeld = true
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
        if (b == 6) game.downHeld = false
        if (b == 0) { game.attackHeld = false; game.attackRelease = true }
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

    /** mesmo pixel-art da ferramenta na mão, em 2D */
    private fun spriteIcon(c: Canvas, cx: Float, cy: Float, size: Float, sp: ToolSprites.Sprite) {
        val px = size * 0.84f / 16f; val ox = cx - 8 * px; val oy = cy - 8 * px
        bp.colorFilter = null; bp.style = Paint.Style.FILL
        bp.color = Color.argb(70, 0, 0, 0)
        for (r in sp.runs) c.drawRect(ox + r.x0 * px + px * 0.6f, oy + r.y * px + px * 0.8f, ox + (r.x1 + 1) * px + px * 0.6f, oy + (r.y + 1) * px + px * 0.8f, bp)
        for (r in sp.runs) { bp.color = col(r.c); c.drawRect(ox + r.x0 * px, oy + r.y * px, ox + (r.x1 + 1) * px, oy + (r.y + 1) * px, bp) }
    }

    private fun icon(c: Canvas, cx: Float, cy: Float, size: Float, id: Int) {
        val bm = ToolIcons.get(id)
        if (bm != null) {
            val s2 = size * 0.92f
            rf.set(cx - s2 / 2, cy - s2 / 2, cx + s2 / 2, cy + s2 / 2)
            ip.colorFilter = shadowF; ip.alpha = 90
            val so = size * 0.035f
            c.save(); c.translate(so, so * 1.4f); c.drawBitmap(bm, null, rf, ip); c.restore()
            ip.colorFilter = null; ip.alpha = 255
            c.drawBitmap(bm, null, rf, ip)
        } else if (Items.isBlock(id)) {
            pt.style = Paint.Style.FILL; pt.color = Color.argb(60, 0, 0, 0)
            c.drawOval(cx - size * 0.28f, cy + size * 0.22f, cx + size * 0.28f, cy + size * 0.34f, pt)
            blockIcon(c, cx, cy - size * 0.02f, size * 0.3f, id)
        }
    }

    private fun accent(i: Int) = when (i) { 0 -> Color.rgb(255, 120, 100); 1, 6 -> Color.rgb(120, 195, 255); 2 -> Color.rgb(130, 230, 130); else -> Color.rgb(200, 210, 235) }
    private val rf = RectF()
    private val ip = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val shadowF = LightingColorFilter(0x000000, 0x000000)
    private val gold = Color.rgb(255, 214, 90)

    private fun circle(c: Canvas, i: Int, active: Boolean) {
        press[i] += ((if (active) 1f else 0f) - press[i]) * 0.3f
        val cx = bx(i); val cy = byy(i); val r = br(i) * (1f - 0.08f * press[i]); val ac = accent(i)
        pt.style = Paint.Style.FILL; pt.color = Color.argb(70, 0, 0, 0); c.drawCircle(cx, cy + 3 * d, r, pt)
        pt.color = Color.argb((120 + 70 * press[i]).toInt(), 16, 20, 36); c.drawCircle(cx, cy, r, pt)
        pt.color = Color.argb((35 + 70 * press[i]).toInt(), Color.red(ac), Color.green(ac), Color.blue(ac)); c.drawCircle(cx, cy, r * 0.86f, pt)
        rf.set(cx - r * 0.78f, cy - r * 0.78f, cx + r * 0.78f, cy + r * 0.78f)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 3f * d; pt.strokeCap = Paint.Cap.ROUND
        pt.color = Color.argb(70, 255, 255, 255); c.drawArc(rf, 200f, 100f, false, pt)
        pt.strokeWidth = 2.5f * d; pt.color = Color.argb(235, Color.red(ac), Color.green(ac), Color.blue(ac)); c.drawCircle(cx, cy, r, pt)
        pt.strokeWidth = 1f * d; pt.color = Color.argb(90, 255, 255, 255); c.drawCircle(cx, cy, r + 2 * d, pt)
    }

    private fun pill(c: Canvas, l: Float, t: Float, r: Float, b: Float, a: Int = 110) {
        rf.set(l, t, r, b); pt.style = Paint.Style.FILL; pt.color = Color.argb(a, 14, 18, 34); c.drawRoundRect(rf, (b - t) / 2, (b - t) / 2, pt)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 1.5f * d; pt.color = Color.argb(90, 255, 255, 255); c.drawRoundRect(rf, (b - t) / 2, (b - t) / 2, pt)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val clock = System.nanoTime() / 1e9f
        if (game.hurtFlash > 0f) { pt.style = Paint.Style.FILL; pt.color = Color.argb((game.hurtFlash * 90).toInt(), 255, 0, 0); c.drawRect(0f, 0f, w, h, pt) }
        pt.strokeCap = Paint.Cap.ROUND
        if (!invOpen) {
            // mira com contorno
            for (k in 0..1) {
                pt.style = Paint.Style.STROKE; pt.strokeWidth = if (k == 0) 4f * d else 2f * d; pt.color = if (k == 0) Color.argb(90, 0, 0, 0) else Color.argb(230, 255, 255, 255)
                c.drawLine(w / 2 - 8 * d, h / 2, w / 2 + 8 * d, h / 2, pt); c.drawLine(w / 2, h / 2 - 8 * d, w / 2, h / 2 + 8 * d, pt)
            }
            // joystick
            val jx = if (stickId >= 0) scx else defStickX(); val jy = if (stickId >= 0) scy else defStickY()
            pt.style = Paint.Style.FILL; pt.color = Color.argb(48, 255, 255, 255); c.drawCircle(jx, jy, 60 * d, pt)
            pt.style = Paint.Style.STROKE; pt.strokeWidth = 2.5f * d; pt.color = Color.argb(170, 255, 255, 255); c.drawCircle(jx, jy, 60 * d, pt)
            pt.style = Paint.Style.FILL; pt.color = Color.argb(150, 255, 255, 255)
            for (k in 0 until 4) { val an = k * 1.5708f; c.drawCircle(jx + cos(an) * 46 * d, jy + sin(an) * 46 * d, 2.2f * d, pt) }
            val kx = jx + sx * 60 * d; val ky = jy + sy * 60 * d
            pt.color = Color.argb(70, 0, 0, 0); c.drawCircle(kx, ky + 3 * d, 25 * d, pt)
            pt.color = Color.argb(235, 255, 255, 255); c.drawCircle(kx, ky, 25 * d, pt)
            pt.color = Color.rgb(205, 220, 240); c.drawCircle(kx, ky, 17 * d, pt)
            pt.color = Color.argb(200, 255, 255, 255); c.drawCircle(kx - 4 * d, ky - 5 * d, 6 * d, pt)
        }
        for (i in 0 until 7) {
            if (invOpen && i != 5) continue
            if (i == 6 && !game.flying) continue
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
                6 -> { path.reset(); path.moveTo(x - 12 * d, y - 5 * d); path.lineTo(x, y + 7 * d); path.lineTo(x + 12 * d, y - 5 * d); c.drawPath(path, pt)
                    c.drawLine(x, y + 7 * d, x, y - 12 * d, pt) }
                5 -> { pt.strokeWidth = 3f * d; c.drawRoundRect(x - 10 * d, y - 6 * d, x + 10 * d, y + 11 * d, 5 * d, 5 * d, pt)
                    path.reset(); path.moveTo(x - 6 * d, y - 6 * d); path.cubicTo(x - 6 * d, y - 15 * d, x + 6 * d, y - 15 * d, x + 6 * d, y - 6 * d); c.drawPath(path, pt)
                    c.drawLine(x - 10 * d, y, x + 10 * d, y, pt) }
                else -> { path.reset(); path.moveTo(x + 6 * d, y - 10 * d); path.lineTo(x - 6 * d, y); path.lineTo(x + 6 * d, y + 10 * d); c.drawPath(path, pt) }
            }
        }
        // anel de carga do golpe poderoso
        if (game.charging && !invOpen) {
            val rr0 = br(0) + 7 * d; val ch = game.charge
            val rc = RectF(bx(0) - rr0, byy(0) - rr0, bx(0) + rr0, byy(0) + rr0)
            pt.style = Paint.Style.STROKE; pt.strokeWidth = 5f * d; pt.strokeCap = Paint.Cap.ROUND
            pt.color = Color.argb(70, 255, 255, 255); c.drawArc(rc, 0f, 360f, false, pt)
            pt.color = if (ch >= 1f) Color.rgb(255, 214, 90) else Color.rgb(255, (255 - 60 * ch).toInt(), (255 - 170 * ch).toInt())
            c.drawArc(rc, -90f, 360f * ch, false, pt)
        }
        // hotbar
        if (game.sel != lastSel) { lastSel = game.sel; bounce = 1f }
        bounce = max(0f, bounce - 0.08f)
        val left = hbLeft(); val top = hbTop()
        pill(c, left - 8 * d, top - 8 * d, left + 8 * slot + 7 * 4 * d + 8 * d, top + slot + 6 * d, 95)
        for (i in 0 until 8) {
            val x = left + i * (slot + 4 * d)
            val s = i == game.sel
            val lift = if (s) 4 * d + 5 * d * sin(bounce * 3.14f) else 0f
            val rr = RectF(x, top - lift, x + slot, top + slot - lift)
            if (s) {
                val gl = 0.55f + 0.45f * sin(clock * 4f)
                pt.style = Paint.Style.STROKE; pt.strokeWidth = 7f * d; pt.color = Color.argb((60 * gl).toInt(), 255, 214, 90)
                c.drawRoundRect(rr, 10 * d, 10 * d, pt)
            }
            pt.style = Paint.Style.FILL; pt.color = Color.argb(if (s) 215 else 135, if (s) 44 else 24, if (s) 48 else 30, if (s) 72 else 50)
            c.drawRoundRect(rr, 9 * d, 9 * d, pt)
            rf.set(rr.left + 3 * d, rr.top + 3 * d, rr.right - 3 * d, rr.top + slot * 0.42f)
            pt.color = Color.argb(if (s) 40 else 22, 255, 255, 255); c.drawRoundRect(rf, 6 * d, 6 * d, pt)
            pt.style = Paint.Style.STROKE; pt.strokeWidth = (if (s) 2.8f else 1.4f) * d; pt.color = if (s) gold else Color.argb(110, 255, 255, 255)
            c.drawRoundRect(rr, 9 * d, 9 * d, pt)
            icon(c, x + slot / 2, rr.top + slot * 0.5f, slot, game.hotbar[i])
            pt.style = Paint.Style.FILL; pt.textSize = 9 * d; pt.textAlign = Paint.Align.LEFT; pt.color = Color.argb(if (s) 255 else 150, 255, 255, 255)
            pt.typeface = Typeface.DEFAULT_BOLD; c.drawText("${i + 1}", rr.left + 5 * d, rr.top + 11 * d, pt)
        }
        if (!invOpen) {
            pt.textSize = 14 * d; pt.typeface = Typeface.DEFAULT_BOLD; pt.textAlign = Paint.Align.CENTER
            val nm = Items.name(game.cur()); val tw = pt.measureText(nm)
            pill(c, w / 2 - tw / 2 - 14 * d, top - 40 * d, w / 2 + tw / 2 + 14 * d, top - 16 * d, 120)
            pt.style = Paint.Style.FILL; pt.color = Color.WHITE; c.drawText(nm, w / 2, top - 22 * d, pt)
        }
        // corações
        val hp = game.hp
        for (i in 0 until 5) {
            val full = hp >= (i + 1) * 2; val half = hp == i * 2 + 1
            val cx = w / 2 - 60 * d + i * 30 * d
            val beat = if (hp <= 2 && hp > 0) 1f + 0.08f * sin(clock * 9f) else 1f
            heart(c, cx, 28 * d, 12.5f * d * beat, Color.argb(190, 20, 10, 25))
            heart(c, cx, 28 * d, 10.5f * d * beat, Color.argb(120, 80, 40, 60))
            if (full || half) {
                if (half) { c.save(); c.clipRect(cx - 16 * d, 0f, cx, 80 * d) }
                heart(c, cx, 28 * d, 10.5f * d * beat, Color.rgb(255, 72, 96))
                heart(c, cx, 26 * d, 6f * d * beat, Color.rgb(255, 120, 135))
                pt.style = Paint.Style.FILL; pt.color = Color.argb(230, 255, 255, 255); c.drawCircle(cx - 4.5f * d, 22.5f * d, 2f * d, pt)
                if (half) c.restore()
            }
        }
        // contador de slimes
        val ktxt = "${game.kills}"; pt.textSize = 15 * d; pt.typeface = Typeface.DEFAULT_BOLD; pt.textAlign = Paint.Align.LEFT
        val kw = pt.measureText(ktxt) + 44 * d; val kr = w - 118 * d
        pill(c, kr - kw, 18 * d, kr, 46 * d)
        pt.style = Paint.Style.FILL; pt.color = Color.rgb(120, 225, 110); c.drawRoundRect(kr - kw + 8 * d, 26 * d, kr - kw + 26 * d, 40 * d, 5 * d, 5 * d, pt)
        pt.color = Color.rgb(20, 60, 30); c.drawRect(kr - kw + 12 * d, 30 * d, kr - kw + 14.5f * d, 33 * d, pt); c.drawRect(kr - kw + 19.5f * d, 30 * d, kr - kw + 22 * d, 33 * d, pt)
        pt.color = Color.WHITE; c.drawText(ktxt, kr - kw + 32 * d, 38 * d, pt)
        pt.setShadowLayer(4 * d, 0f, 1f, Color.BLACK)
        if (game.deadTimer > 0f) { pt.textAlign = Paint.Align.CENTER; pt.textSize = 26 * d; pt.color = Color.WHITE; c.drawText("Você desmaiou! Renascendo...", w / 2, h / 2 - 40 * d, pt) }
        pt.clearShadowLayer(); pt.typeface = Typeface.DEFAULT
        if (invOpen) drawInventory(c)
        postInvalidateOnAnimation()
    }

    private fun drawInventory(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        pt.style = Paint.Style.FILL; pt.color = Color.argb(150, 6, 8, 20); c.drawRect(0f, 0f, w, h, pt)
        val rr = RectF(pl(), pt0(), pr(), pb())
        pt.shader = LinearGradient(0f, rr.top, 0f, rr.bottom, Color.argb(240, 40, 50, 88), Color.argb(240, 20, 24, 48), Shader.TileMode.CLAMP)
        c.drawRoundRect(rr, 18 * d, 18 * d, pt); pt.shader = null
        c.save(); c.clipRect(rr.left, rr.top, rr.right, rr.top + 46 * d)
        pt.color = Color.argb(60, 255, 214, 90); c.drawRoundRect(rr, 18 * d, 18 * d, pt); c.restore()
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 3f * d; pt.color = Color.argb(230, 255, 214, 90); c.drawRoundRect(rr, 18 * d, 18 * d, pt)
        pt.strokeWidth = 1f * d; pt.color = Color.argb(60, 255, 255, 255); c.drawLine(rr.left + 14 * d, rr.top + 46 * d, rr.right - 14 * d, rr.top + 46 * d, pt)
        pt.style = Paint.Style.FILL; pt.color = Color.WHITE; pt.textSize = 20 * d; pt.textAlign = Paint.Align.LEFT; pt.typeface = Typeface.DEFAULT_BOLD
        c.drawText("Mochila", pl() + 18 * d, pt0() + 31 * d, pt)
        // fechar
        pt.color = Color.argb(90, 255, 255, 255); c.drawCircle(pr() - 20 * d, pt0() + 22 * d, 14 * d, pt)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 3f * d; pt.color = Color.WHITE; pt.strokeCap = Paint.Cap.ROUND
        val xx = pr() - 20 * d; val yy = pt0() + 22 * d
        c.drawLine(xx - 6 * d, yy - 6 * d, xx + 6 * d, yy + 6 * d, pt); c.drawLine(xx + 6 * d, yy - 6 * d, xx - 6 * d, yy + 6 * d, pt)
        // info do item selecionado no cabeçalho
        val sel = if (invSel > 0) invSel else game.cur()
        val ix = pl() + 130 * d; val avail = pr() - 50 * d - ix
        pt.style = Paint.Style.FILL; pt.color = Color.rgb(255, 224, 130); pt.textSize = 15 * d
        c.drawText(Items.name(sel), ix, pt0() + 20 * d, pt)
        pt.typeface = Typeface.DEFAULT; pt.color = Color.argb(225, 255, 255, 255); pt.textSize = 11.5f * d
        val ds = Items.desc(sel); val dw = pt.measureText(ds); if (dw > avail) pt.textSize = 11.5f * d * avail / dw
        c.drawText(ds, ix, pt0() + 37 * d, pt)
        for ((i, id) in Items.inventory.withIndex()) {
            val x = cellX(i); val y = cellY(i); val s = cell - 6 * d
            val on = id == invSel
            pt.style = Paint.Style.FILL; pt.color = Color.argb(if (on) 70 else 34, 255, 255, 255)
            val cr = RectF(x, y, x + s, y + s); c.drawRoundRect(cr, 11 * d, 11 * d, pt)
            rf.set(x + 3 * d, y + 3 * d, x + s - 3 * d, y + s * 0.45f); pt.color = Color.argb(if (on) 40 else 18, 255, 255, 255); c.drawRoundRect(rf, 8 * d, 8 * d, pt)
            pt.style = Paint.Style.STROKE; pt.strokeWidth = (if (on) 3f else 1.2f) * d; pt.color = if (on) gold else Color.argb(80, 255, 255, 255)
            c.drawRoundRect(cr, 11 * d, 11 * d, pt)
            icon(c, x + s / 2, y + s / 2, s, id)
        }
        val ty = cellY(Items.inventory.size - 1) + cell + 8 * d
        if (ty < pb() - 4 * d) {
            pt.style = Paint.Style.FILL; pt.color = Color.argb(170, 255, 255, 255); pt.textSize = 11.5f * d; pt.textAlign = Paint.Align.LEFT
            c.drawText("Toque num item para colocá-lo no slot selecionado", pl() + 18 * d, ty + 6 * d, pt)
        }
        pt.typeface = Typeface.DEFAULT
    }
}
