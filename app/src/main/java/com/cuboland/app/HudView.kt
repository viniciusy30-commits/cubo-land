package com.cuboland.app

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class HudView(ctx: Context, val game: Game, val worldName: String, val onExit: () -> Unit) : View(ctx) {
    private val prefs = ctx.getSharedPreferences("cfg", 0)
    private val tapMode = prefs.getBoolean("tap", true)
    private val bs = prefs.getFloat("btn", 1f).coerceIn(0.8f, 1.25f)
    private var paused = false
    private var lookDownT = 0L; private var lookMoved = 0f; private var lookHold = false
    private val rf2 = RectF()
    private fun al(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
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
    private fun br(i: Int) = (when (i) { 0 -> 44 * d; 1 -> 34 * d; 6 -> 30 * d; 2 -> 32 * d; else -> 22 * d }) * (if (i in 0..2 || i == 6) bs else 1f)
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
            MotionEvent.ACTION_MOVE -> if (!invOpen && !paused) for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> up(e.getPointerId(idx))
        }
        return true
    }

    fun togglePause() {
        paused = !paused
        game.paused = paused
        stickId = -1; lookId = -1; sx = 0f; sy = 0f; lookHold = false
        game.stickX = 0f; game.stickY = 0f; game.jumpHeld = false; game.downHeld = false; game.attackHeld = false; btnId.clear()
        if (invOpen) invOpen = false
    }

    private fun pbox() = RectF(width / 2f - 150 * d, height / 2f - 118 * d, width / 2f + 150 * d, height / 2f + 118 * d)
    private fun pbtn(k: Int): RectF { val b = pbox(); val t = b.top + 70 * d + k * 56 * d; return RectF(b.left + 22 * d, t, b.right - 22 * d, t + 46 * d) }

    private fun pauseDown(x: Float, y: Float) {
        if (pbtn(0).contains(x, y)) { togglePause(); return }
        if (pbtn(1).contains(x, y)) { game.thirdPerson = !game.thirdPerson; return }
        if (pbtn(2).contains(x, y)) { onExit(); return }
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
        if (paused) { pauseDown(x, y); return }
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
        for (i in intArrayOf(0, 1, 2, 3, 4, 6)) if ((i != 6 || game.flying || game.player.inWater) && hypot(x - bx(i), y - byy(i)) < br(i) * 1.15f) {
            btnId[id] = i
            when (i) {
                0 -> { game.attackPress = true; game.attackHeld = true }
                1 -> { game.jumpHeld = true; game.jumpTap() }
                6 -> game.downHeld = true
                2 -> game.wantPlace = true
                3 -> game.wantCam = true
                4 -> togglePause()
            }
            return
        }
        if (hotbarHit(x, y)) return
        if (x < width * 0.38f && stickId < 0) { stickId = id; scx = x; scy = y; sx = 0f; sy = 0f; return }
        if (lookId < 0) { lookId = id; lx = x; ly = y; lookDownT = System.currentTimeMillis(); lookMoved = 0f; lookHold = false }
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
            lookMoved += hypot(x - lx, y - ly); game.addLook(x - lx, y - ly); lx = x; ly = y
        }
    }

    private fun up(id: Int) {
        if (id == stickId) { stickId = -1; sx = 0f; sy = 0f; game.stickX = 0f; game.stickY = 0f }
        if (id == lookId) {
            lookId = -1
            if (tapMode && !paused && !invOpen) {
                if (lookHold) { game.attackHeld = false; game.attackRelease = true }
                else if (lookMoved < 14 * d && System.currentTimeMillis() - lookDownT < 320) {
                    if (Items.isBlock(game.cur())) game.wantPlace = true else { game.attackPress = true; game.attackRelease = true }
                }
            }
            lookHold = false
        }
        val b = btnId.remove(id)
        if (b == 1) game.jumpHeld = false
        if (b == 6) game.downHeld = false
        if (b == 0) { game.attackHeld = false; game.attackRelease = true }
    }

    private val HEART = arrayOf(".XX...XX.", "XXXX.XXXX", "XXXXXXXXX", "XXXXXXXXX", ".XXXXXXX.", "..XXXXX..", "...XXX...", "....X....")
    /** coração em pixels (9x8) como no Minecraft: contorno preto, vermelho e brilho; half = só a metade esquerda */
    private fun pxHeart(c: Canvas, cx: Float, cy: Float, px: Float, fill: Int, half: Boolean = false, empty: Boolean = false) {
        val x0 = cx - 4.5f * px; val y0 = cy - 4f * px
        pt.style = Paint.Style.FILL
        pt.color = Color.BLACK
        for (r in 0 until 8) for (k in 0 until 9) if (HEART[r][k] == 'X') c.drawRect(x0 + (k - 1) * px, y0 + (r - 1) * px, x0 + (k + 2) * px, y0 + (r + 2) * px, pt)
        for (r in 0 until 8) for (k in 0 until 9) {
            if (HEART[r][k] != 'X') continue
            pt.color = if (empty || (half && k > 4)) Color.rgb(60, 24, 30) else if (r >= 4) shadeC(fill, 0.82f) else fill
            c.drawRect(x0 + k * px, y0 + r * px, x0 + (k + 1) * px, y0 + (r + 1) * px, pt)
        }
        if (!empty) { pt.color = Color.argb(230, 255, 255, 255); c.drawRect(x0 + px, y0 + px, x0 + 3 * px, y0 + 2 * px, pt); c.drawRect(x0 + px, y0 + 2 * px, x0 + 2 * px, y0 + 3 * px, pt) }
    }

    /** painel quadrado de pedra: borda preta + chanfro */
    private fun mcPanel(c: Canvas, r: RectF, fill: Int, sel: Boolean) {
        val rad = 10 * d
        pt.style = Paint.Style.FILL; pt.shader = null
        pt.color = fill; c.drawRoundRect(r, rad, rad, pt)
        pt.color = Color.argb(34, 255, 255, 255)
        rf2.set(r.left + 3 * d, r.top + 3 * d, r.right - 3 * d, r.top + r.height() * 0.46f); c.drawRoundRect(rf2, 7 * d, 7 * d, pt)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = (if (sel) 3f else 2f) * d
        pt.color = if (sel) gold else Cz.INK; c.drawRoundRect(r, rad, rad, pt)
        pt.style = Paint.Style.FILL
    }

    /** botão desenhado (menu de pausa) */
    private fun czBtn(c: Canvas, r: RectF, fill: Int, label: String) {
        val lip = 4 * d; val rad = 14 * d
        pt.style = Paint.Style.FILL; pt.shader = null
        rf2.set(r.left, r.top + lip, r.right, r.bottom + lip); pt.color = shadeC(fill, 0.55f); c.drawRoundRect(rf2, rad, rad, pt)
        pt.shader = LinearGradient(0f, r.top, 0f, r.bottom, shadeC(fill, 1.15f), shadeC(fill, 0.92f), Shader.TileMode.CLAMP); c.drawRoundRect(r, rad, rad, pt); pt.shader = null
        pt.color = Color.argb(40, 255, 255, 255); rf2.set(r.left + 4 * d, r.top + 3 * d, r.right - 4 * d, r.top + r.height() * 0.45f); c.drawRoundRect(rf2, rad * 0.7f, rad * 0.7f, pt)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 2 * d; pt.color = Cz.INK; c.drawRoundRect(r, rad, rad, pt)
        pt.style = Paint.Style.FILL; pt.textAlign = Paint.Align.CENTER; pt.textSize = 16 * d; pt.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        pt.setShadowLayer(0.5f, 1.5f * d, 1.5f * d, Color.argb(160, 20, 14, 30)); pt.color = Color.WHITE
        c.drawText(label, r.centerX(), r.centerY() + 5.5f * d, pt); pt.clearShadowLayer()
    }

    private fun drawPause(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        pt.style = Paint.Style.FILL; pt.color = Color.argb(150, 8, 8, 24); c.drawRect(0f, 0f, w, h, pt)
        val b = pbox()
        pt.color = Cz.PANEL; c.drawRoundRect(b, 22 * d, 22 * d, pt)
        pt.color = Color.argb(30, 255, 255, 255); rf2.set(b.left + 4 * d, b.top + 4 * d, b.right - 4 * d, b.top + 56 * d); c.drawRoundRect(rf2, 18 * d, 18 * d, pt)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 3 * d; pt.color = Cz.INK; c.drawRoundRect(b, 22 * d, 22 * d, pt)
        pt.strokeWidth = 1.2f * d; pt.color = Color.argb(70, 255, 255, 255); rf2.set(b.left + 5 * d, b.top + 5 * d, b.right - 5 * d, b.bottom - 5 * d); c.drawRoundRect(rf2, 18 * d, 18 * d, pt)
        pt.style = Paint.Style.FILL; pt.textAlign = Paint.Align.CENTER; pt.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        pt.textSize = 22 * d; pt.color = gold; c.drawText("Pausado", w / 2, b.top + 36 * d, pt)
        pt.textSize = 12 * d; pt.color = Color.argb(200, 255, 255, 255); c.drawText(worldName, w / 2, b.top + 54 * d, pt)
        czBtn(c, pbtn(0), Cz.GREEN, "▶  Continuar")
        czBtn(c, pbtn(1), Cz.SKY, if (game.thirdPerson) "Câmera: 3ª pessoa" else "Câmera: 1ª pessoa")
        czBtn(c, pbtn(2), Cz.RED, "Salvar e sair")
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

    private fun accent(i: Int) = when (i) { 0 -> Cz.RED; 1, 6 -> Cz.SKY; 2 -> Cz.GREEN; 3 -> Cz.TEAL; 5 -> Cz.GOLD; else -> Cz.LILAC }
    private val rf = RectF()
    private val ip = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val shadowF = LightingColorFilter(0x000000, 0x000000)
    private val gold = Color.rgb(255, 214, 90)

    private fun circle(c: Canvas, i: Int, active: Boolean) {
        press[i] += ((if (active) 1f else 0f) - press[i]) * 0.3f
        val cx = bx(i); val cy = byy(i); val r = br(i) * (1f - 0.06f * press[i]); val ac = accent(i)
        val a = (170 + 70 * press[i]).toInt()
        pt.style = Paint.Style.FILL; pt.color = Color.argb(70, 0, 0, 0); c.drawCircle(cx, cy + 4 * d, r, pt)
        pt.shader = LinearGradient(cx, cy - r, cx, cy + r, al(shadeC(ac, 1.0f), a), al(shadeC(ac, 0.55f), a), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, pt); pt.shader = null
        pt.color = Color.argb(55, 255, 255, 255); rf.set(cx - r * 0.68f, cy - r * 0.9f, cx + r * 0.68f, cy - r * 0.08f); c.drawOval(rf, pt)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 3 * d; pt.color = Color.argb(235, 36, 28, 48); c.drawCircle(cx, cy, r, pt)
        pt.strokeWidth = 1.5f * d; pt.color = Color.argb(200, 255, 244, 214); c.drawCircle(cx, cy, r - 3 * d, pt)
    }

    private fun pill(c: Canvas, l: Float, t: Float, r: Float, b: Float, a: Int = 110) {
        rf.set(l, t, r, b); mcPanel(c, rf, Cz.PANEL3, false)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val clock = System.nanoTime() / 1e9f
        if (game.hurtFlash > 0f) { pt.style = Paint.Style.FILL; pt.color = Color.argb((game.hurtFlash * 90).toInt(), 255, 0, 0); c.drawRect(0f, 0f, w, h, pt) }
        pt.strokeCap = Paint.Cap.ROUND
        if (tapMode && lookId >= 0 && !paused && !invOpen) {
            if (!lookHold && lookMoved < 16 * d && System.currentTimeMillis() - lookDownT > 320) {
                lookHold = true
                game.attackHeld = true; game.attackPress = true
            } else if (lookHold && game.cur() != Items.SWORD && game.cur() != Items.AXE) game.attackPress = true
        }
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
            if (i == 6 && !game.flying && !game.player.inWater) continue
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
                else -> { pt.strokeWidth = 4.5f * d; c.drawLine(x - 5 * d, y - 9 * d, x - 5 * d, y + 9 * d, pt); c.drawLine(x + 5 * d, y - 9 * d, x + 5 * d, y + 9 * d, pt) }
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
        rf.set(left - 7 * d, top - 7 * d, left + 8 * slot + 7 * 4 * d + 7 * d, top + slot + 7 * d)
        pt.style = Paint.Style.FILL; pt.color = Color.argb(150, 20, 18, 40); c.drawRoundRect(rf, 14 * d, 14 * d, pt)
        for (i in 0 until 8) {
            val x = left + i * (slot + 4 * d)
            val s = i == game.sel
            val lift = if (s) 3 * d + 4 * d * sin(bounce * 3.14f) else 0f
            val rr = RectF(x, top - lift, x + slot, top + slot - lift)
            mcPanel(c, rr, if (s) Cz.PANEL2 else Cz.PANEL, s)
            if (s) { pt.style = Paint.Style.STROKE; pt.strokeWidth = 2 * d; pt.color = Color.argb((110 + 80 * sin(clock * 4f)).toInt(), 255, 214, 90); c.drawRoundRect(rr.left - 3 * d, rr.top - 3 * d, rr.right + 3 * d, rr.bottom + 3 * d, 12 * d, 12 * d, pt) }
            icon(c, x + slot / 2, rr.top + slot * 0.5f, slot, game.hotbar[i])
            pt.style = Paint.Style.FILL; pt.textSize = 9 * d; pt.textAlign = Paint.Align.LEFT; pt.color = Color.argb(if (s) 255 else 150, 255, 255, 255)
            pt.typeface = Typeface.MONOSPACE; pt.setShadowLayer(0.5f, 1.5f * d, 1.5f * d, Color.BLACK); c.drawText("${i + 1}", rr.left + 6 * d, rr.top + 14 * d, pt); pt.clearShadowLayer()
        }
        if (!invOpen) {
            pt.textSize = 14 * d; pt.typeface = Typeface.DEFAULT_BOLD; pt.textAlign = Paint.Align.CENTER
            val nm = Items.name(game.cur()); val tw = pt.measureText(nm)
            rf.set(w / 2 - tw / 2 - 12 * d, top - 40 * d, w / 2 + tw / 2 + 12 * d, top - 16 * d); pt.style = Paint.Style.FILL; pt.color = Color.argb(150, 20, 18, 40); c.drawRoundRect(rf, 10 * d, 10 * d, pt)
            pt.typeface = Typeface.MONOSPACE; pt.color = Color.rgb(60, 60, 60); c.drawText(nm, w / 2 + 1.5f * d, top - 22 * d + 1.5f * d, pt); pt.color = Color.WHITE; c.drawText(nm, w / 2, top - 22 * d, pt)
            if (game.pickT > 0f) {   // "+1 Terra" ao pegar um item do chão
                val al = (kotlin.math.min(1f, game.pickT * 2f) * 255).toInt()
                pt.textSize = 13 * d; pt.typeface = Typeface.MONOSPACE; pt.textAlign = Paint.Align.CENTER
                val pm = game.pickMsg; val pw = pt.measureText(pm)
                val py2 = top - 56 * d - (1.6f - game.pickT) * 14 * d
                pt.style = Paint.Style.FILL; pt.color = Color.argb(al * 140 / 255, 0, 0, 0)
                rf.set(w / 2 - pw / 2 - 10 * d, py2 - 16 * d, w / 2 + pw / 2 + 10 * d, py2 + 6 * d); c.drawRect(rf, pt)
                pt.color = Color.argb(al, 255, 236, 140); c.drawText(pm, w / 2, py2, pt)
            }
        }
        // corações
        val hp = game.hp
        for (i in 0 until 5) {
            val full = hp >= (i + 1) * 2; val half = hp == i * 2 + 1
            val cx = w / 2 - 60 * d + i * 30 * d
            val beat = if (hp <= 2 && hp > 0) 1f + 0.08f * sin(clock * 9f) else 1f
            val bob = if (hp <= 2 && hp > 0) sin(clock * 12f + i) * 1.5f * d else 0f
            pxHeart(c, cx, 28 * d + bob, 2.4f * d * beat, Color.rgb(228, 36, 48), half && !full, !(full || half))
            if (half && !full) pxHeart(c, cx, 28 * d + bob, 2.4f * d * beat, Color.rgb(228, 36, 48), true, false)
        }
        pt.style = Paint.Style.FILL; pt.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD); pt.textSize = 12 * d; pt.textAlign = Paint.Align.LEFT
        pt.color = Color.argb(200, 255, 255, 255); pt.setShadowLayer(2 * d, 0f, 1f, Color.BLACK); c.drawText(worldName, 40 * d + 30 * d, 44 * d, pt); pt.clearShadowLayer()
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
        if (paused) drawPause(c)
        postInvalidateOnAnimation()
    }

    private fun drawInventory(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        pt.style = Paint.Style.FILL; pt.color = Color.argb(150, 6, 8, 20); c.drawRect(0f, 0f, w, h, pt)
        val rr = RectF(pl(), pt0(), pr(), pb())
        pt.style = Paint.Style.FILL
        pt.shader = LinearGradient(0f, rr.top, 0f, rr.bottom, Color.argb(244, 56, 54, 98), Color.argb(244, 36, 34, 68), Shader.TileMode.CLAMP)
        c.drawRoundRect(rr, 20 * d, 20 * d, pt); pt.shader = null
        c.save(); c.clipRect(rr.left, rr.top, rr.right, rr.top + 46 * d)
        pt.color = Color.argb(46, 255, 214, 90); c.drawRoundRect(rr, 20 * d, 20 * d, pt); c.restore()
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 3.5f * d; pt.color = Cz.INK; c.drawRoundRect(rr, 20 * d, 20 * d, pt)
        pt.strokeWidth = 1.5f * d; pt.color = Color.argb(200, 255, 214, 90); rf2.set(rr.left + 4 * d, rr.top + 4 * d, rr.right - 4 * d, rr.bottom - 4 * d); c.drawRoundRect(rf2, 16 * d, 16 * d, pt)
        pt.strokeWidth = 1f * d; pt.color = Color.argb(60, 255, 255, 255); c.drawLine(rr.left + 14 * d, rr.top + 46 * d, rr.right - 14 * d, rr.top + 46 * d, pt)
        pt.style = Paint.Style.FILL; pt.color = Color.WHITE; pt.textSize = 20 * d; pt.textAlign = Paint.Align.LEFT; pt.typeface = Typeface.create("sans-serif-rounded", Typeface.BOLD)
        c.drawText("Mochila", pl() + 18 * d, pt0() + 31 * d, pt)
        // fechar
        pt.color = Cz.RED; c.drawCircle(pr() - 20 * d, pt0() + 22 * d, 14 * d, pt)
        pt.style = Paint.Style.STROKE; pt.strokeWidth = 2.5f * d; pt.color = Cz.INK; c.drawCircle(pr() - 20 * d, pt0() + 22 * d, 14 * d, pt)
        pt.strokeWidth = 3f * d; pt.color = Color.WHITE; pt.strokeCap = Paint.Cap.ROUND
        val xx = pr() - 20 * d; val yy = pt0() + 22 * d
        c.drawLine(xx - 5 * d, yy - 5 * d, xx + 5 * d, yy + 5 * d, pt); c.drawLine(xx + 5 * d, yy - 5 * d, xx - 5 * d, yy + 5 * d, pt)
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
            pt.style = Paint.Style.FILL; pt.color = if (on) Color.argb(235, 84, 82, 138) else Color.argb(220, 46, 44, 82)
            val cr = RectF(x, y, x + s, y + s); c.drawRoundRect(cr, 11 * d, 11 * d, pt)
            rf.set(x + 3 * d, y + 3 * d, x + s - 3 * d, y + s * 0.45f); pt.color = Color.argb(if (on) 40 else 18, 255, 255, 255); c.drawRoundRect(rf, 8 * d, 8 * d, pt)
            pt.style = Paint.Style.STROKE; pt.strokeWidth = (if (on) 3f else 2f) * d; pt.color = if (on) gold else Cz.INK
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
