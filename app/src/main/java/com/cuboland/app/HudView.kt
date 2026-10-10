package com.cuboland.app

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class HudView(ctx: Context, val game: Game, val worldName: String, val onExit: () -> Unit) : View(ctx) {
    private val prefs = ctx.getSharedPreferences("cfg", 0)
    private val aimTouch = prefs.getBoolean("aimTouch", false)   // mira no ponto do toque (círculo no dedo)
    private val bs = prefs.getFloat("btn", 1f).coerceIn(0.8f, 1.25f)
    private var paused = false
    private var lookDownT = 0L; private var lookMoved = 0f; private var lookHold = false; private var lastUpT = 0L
    private fun al(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
    private val d = resources.displayMetrics.density
    private val u = max(2, (2 * d).roundToInt()).toFloat()          // 1 "pixel de arte" na tela
    private val pt = Paint().apply { isAntiAlias = false }
    private val bp = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val mx = Matrix()
    private val src = floatArrayOf(0f, 0f, Atlas.T.toFloat(), 0f, 0f, Atlas.T.toFloat())
    private val dst = FloatArray(6)
    private val fLeft = LightingColorFilter(0xFFCCCCCC.toInt(), 0)
    private val fRight = LightingColorFilter(0xFFA0A0A0.toInt(), 0)
    private var stickId = -1; private var lookId = -1
    private var scx = 0f; private var scy = 0f; private var sx = 0f; private var sy = 0f
    private var lx = 0f; private var ly = 0f; private var fx0 = 0f; private var fy0 = 0f
    private val btnId = HashMap<Int, Int>()
    private val press = FloatArray(10)
    private var invOpen = false; private var invSel = -1
    private var poseDownT = 0L; private var poseFired = false; private var poseTapT = 0L
    private var bounce = 0f; private var lastSel = -1; private var lastItem = -1; private var nameUntil = 0L
    // 1 pular/boiar, 3 câmera, 4 pausa, 5 mochila, 6 afundar, 7 correr
    private fun bx(i: Int) = when (i) { 1, 6 -> width * 0.858f; 7 -> width * 0.927f; 8 -> width * 0.934f; 3 -> width - 40 * d; 5 -> width - 92 * d; else -> 40 * d }
    private fun byy(i: Int) = when (i) { 1 -> if (wet()) height * 0.753f - 72 * d else height * 0.753f; 6 -> height * 0.753f; 7 -> height * 0.653f; 8 -> height * 0.833f; else -> 40 * d }
    private fun wet() = game.flying || game.player.inWater
    private fun br(i: Int) = (when (i) { 1 -> 32 * d; 7, 6 -> 28 * d; 8 -> 26 * d; else -> 22 * d }) * (if (i == 1 || i == 6 || i == 7 || i == 8) bs else 1f)
    private fun sn(v: Float) = (v / u).roundToInt() * u
    private val slot get() = sn(46 * d)
    private val gap get() = max(u, sn(4 * d))
    private fun hbLeft() = sn((width - 8 * slot - 7 * gap) / 2f)
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

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { game.aspect = w.toFloat() / max(1, h) }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val act = e.actionMasked; val idx = e.actionIndex
        when (act) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> down(e.getPointerId(idx), e.getX(idx), e.getY(idx))
            MotionEvent.ACTION_MOVE -> if (!invOpen && !paused) for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> up(e.getPointerId(idx))
        }
        return true
    }

    private fun clearInput() {
        stickId = -1; lookId = -1; sx = 0f; sy = 0f; lookHold = false
        game.stickX = 0f; game.stickY = 0f; game.jumpHeld = false; game.downHeld = false; game.attackHeld = false; game.aimOn = false; btnId.clear()
    }

    fun togglePause() {
        paused = !paused
        game.paused = paused
        clearInput()
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
        clearInput()
        invSel = game.cur()
    }

    private fun hotbarHit(x: Float, y: Float): Boolean {
        if (y > hbTop() - 8 * d) {
            val s = ((x - hbLeft()) / (slot + gap)).toInt()
            if (x >= hbLeft() && s in 0..7) { game.sel = s; invSel = game.cur(); return true }
        }
        return false
    }

    private fun aimAt(x: Float, y: Float) {
        if (!aimTouch) return
        game.aimNx = ((x - width / 2f) / (width / 2f)).coerceIn(-1f, 1f)
        game.aimNy = ((y - height / 2f) / (height / 2f)).coerceIn(-1f, 1f)
        game.aimOn = true
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
        for (i in intArrayOf(1, 3, 4, 6, 7, 8)) if ((i != 6 || wet()) && (i != 7 || !wet()) && (i != 8 || !wet()) && hypot(x - bx(i), y - byy(i)) < br(i) * 1.15f) {
            btnId[id] = i
            when (i) {
                1 -> { game.jumpHeld = true; game.jumpTap() }
                6 -> game.downHeld = true
                7 -> game.sprint = !game.sprint      // toque liga/desliga; parar de andar desliga sozinho
                8 -> { poseDownT = System.currentTimeMillis(); poseFired = false }
                3 -> game.wantCam = true
                4 -> togglePause()
            }
            return
        }
        if (hotbarHit(x, y)) return
        if (x < width * 0.38f && stickId < 0) { stickId = id; scx = x; scy = y; sx = 0f; sy = 0f; return }
        if (lookId < 0) { lookId = id; lx = x; ly = y; fx0 = x; fy0 = y; lookDownT = System.currentTimeMillis(); lookMoved = 0f; lookHold = false; aimAt(x, y) }
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
            lookMoved += hypot(x - lx, y - ly)
            // mira por toque: só gira a câmera depois de arrastar um pouco; antes disso o dedo só mira
            if (!aimTouch || lookMoved > 12 * d) game.addLook(x - lx, y - ly)
            lx = x; ly = y; aimAt(x, y)
        }
    }

    private fun up(id: Int) {
        if (id == stickId) { stickId = -1; sx = 0f; sy = 0f; game.stickX = 0f; game.stickY = 0f }
        if (id == lookId) {
            lookId = -1; lastUpT = System.currentTimeMillis()
            if (!paused && !invOpen) {
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
        if (b == 8 && !poseFired) {   // toque: 1x agacha, 2x senta (espera um pouco pra ver se vem o 2º toque)
            val now = System.currentTimeMillis()
            if (poseTapT > 0L && now - poseTapT < 320) { poseTapT = 0L; game.requestPosture(2) } else poseTapT = now
        }
        if (b == 8) poseFired = false
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


    // ------------------------------------------------------------------ pixel art
    private fun rc(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) { pt.style = Paint.Style.FILL; pt.color = color; c.drawRect(l, t, r, b, pt) }
    private fun outer(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) { rc(c, l + u, t, r - u, b, color); rc(c, l, t + u, r, b - u, color) }

    /** painel/botão no estilo do menu: contorno preto, moldura dourada, face com degradê em degraus e rebites */
    private fun pxPanel(c: Canvas, l0: Float, t0: Float, r0: Float, b0: Float, base: Int, hi: Boolean = false, pressed: Boolean = false, a: Int = 255) {
        val l = sn(l0); val t = sn(t0) + (if (pressed) u else 0f); val r = sn(r0); val b = sn(b0)
        val gold = if (hi) Color.rgb(255, 238, 150) else Color.rgb(255, 208, 96)
        val goldL = Color.rgb(255, 240, 170); val goldD = Color.rgb(196, 128, 48)
        if (!pressed) outer(c, l, t + u, r, b + u, al(Cz.INK, a))
        outer(c, l, t, r, b, al(Cz.INK, a))
        outer(c, l + u, t + u, r - u, b - u, al(gold, a))
        rc(c, l + 2 * u, t + u, r - 2 * u, t + 2 * u, al(goldL, a)); rc(c, l + u, t + 2 * u, l + 2 * u, b - 2 * u, al(goldL, a))
        rc(c, l + 2 * u, b - 2 * u, r - 2 * u, b - u, al(goldD, a)); rc(c, r - 2 * u, t + 2 * u, r - u, b - 2 * u, al(goldD, a))
        val light = mixC(base, Color.WHITE, 0.3f); val dark = shadeC(base, 0.62f)
        val fl = l + 2 * u; val fr = r - 2 * u; val ft = t + 2 * u; val fb = b - 2 * u
        var y = ft
        while (y < fb - 0.5f) {
            val f = (y - ft) / max(u, fb - ft)
            val cc = if (f < 0.45f) mixC(light, base, min(1f, f * 2.2f)) else mixC(base, dark, (f - 0.45f) / 0.55f)
            rc(c, fl, y, fr, min(y + u, fb), al(cc, a)); y += u
        }
        if (r - l > 9 * u && b - t > 9 * u) {
            for (k in 0 until 4) { val rx = if (k % 2 == 0) l + 3 * u else r - 4 * u; val ry = if (k < 2) t + 3 * u else b - 4 * u
                rc(c, rx, ry, rx + u, ry + u, al(goldD, a)) }
        }
    }

    private fun frame(c: Canvas, l: Float, t: Float, r: Float, b: Float, th: Float, color: Int) {
        rc(c, l, t, r, t + th, color); rc(c, l, b - th, r, b, color); rc(c, l, t + th, l + th, b - th, color); rc(c, r - th, t + th, r, b - th, color)
    }

    /** moldura transparente: só contorno preto + dourado e um brilho leve de vidro por dentro */
    private fun pxGlass(c: Canvas, l0: Float, t0: Float, r0: Float, b0: Float, hi: Boolean) {
        val l = sn(l0); val t = sn(t0); val r = sn(r0); val b = sn(b0)
        val g = if (hi) Color.rgb(255, 232, 140) else Color.rgb(236, 190, 92); val gl = Color.rgb(255, 244, 186); val gd = Color.rgb(176, 116, 44)
        val ink = Color.argb(200, 36, 28, 48)
        rc(c, l + 2 * u, t + 2 * u, r - 2 * u, b - 2 * u, Color.argb(if (hi) 40 else 22, 255, 255, 255))
        rc(c, l + 2 * u, t + 2 * u, r - 2 * u, t + 3 * u, Color.argb(if (hi) 70 else 40, 255, 255, 255))
        rc(c, l + u, t, r - u, t + u, ink); rc(c, l + u, b - u, r - u, b, ink); rc(c, l, t + u, l + u, b - u, ink); rc(c, r - u, t + u, r, b - u, ink)
        rc(c, l + u, t + u, r - u, t + 2 * u, gl); rc(c, l + u, t + 2 * u, l + 2 * u, b - 2 * u, g)
        rc(c, l + u, b - 2 * u, r - u, b - u, gd); rc(c, r - 2 * u, t + 2 * u, r - u, b - 2 * u, gd)
    }

    private fun pxDisc(c: Canvas, cx: Float, cy: Float, R: Int, color: Int) {
        for (k in -R..R) {
            val hw = floor(sqrt(max(0f, R * R + R * 0.6f - k * k))).toInt()
            rc(c, cx - (hw + 0.5f) * u, cy + (k - 0.5f) * u, cx + (hw + 0.5f) * u, cy + (k + 0.5f) * u, color)
        }
    }

    private fun pxRing(c: Canvas, cx: Float, cy: Float, R: Int, th: Int, color: Int) {
        val r2 = R - th
        for (k in -R..R) {
            val hw = floor(sqrt(max(0f, R * R + R * 0.6f - k * k))).toInt()
            val iv = r2 * r2 + r2 * 0.6f - k * k
            val y0 = cy + (k - 0.5f) * u; val y1 = cy + (k + 0.5f) * u
            if (r2 > 0 && iv >= 0f) {
                val hi = floor(sqrt(iv)).toInt()
                rc(c, cx - (hw + 0.5f) * u, y0, cx - (hi + 0.5f) * u, y1, color); rc(c, cx + (hi + 0.5f) * u, y0, cx + (hw + 0.5f) * u, y1, color)
            } else rc(c, cx - (hw + 0.5f) * u, y0, cx + (hw + 0.5f) * u, y1, color)
        }
    }

    /** botão redondo pixelado */
    private fun pxRound(c: Canvas, cx0: Float, cy0: Float, R: Int, base: Int, pressed: Boolean) {
        val cx = sn(cx0); val cy = sn(cy0) + (if (pressed) u else 0f)
        if (!pressed) pxDisc(c, cx, cy + u, R, Cz.INK)
        pxDisc(c, cx, cy, R, Cz.INK)
        pxDisc(c, cx, cy, R - 1, Color.rgb(255, 208, 96))
        pxDisc(c, cx, cy, R - 2, shadeC(base, 0.72f))
        pxDisc(c, cx, cy - u, R - 3, base)
        pxDisc(c, cx - R * 0.28f * u, cy - R * 0.34f * u, max(1, R / 4), mixC(base, Color.WHITE, 0.4f))
    }

    private val S_UP = arrayOf("....#....", "...###...", "..#####..", ".#######.", "...###...", "...###...", "...###...", "...###...", "...###...")
    private val S_DOWN = arrayOf("...###...", "...###...", "...###...", "...###...", "...###...", ".#######.", "..#####..", "...###...", "....#....")
    private val S_RUN = arrayOf("##..##...", ".##..##..", "..##..##.", "...##..##", "..##..##.", ".##..##..", "##..##...")
    private val S_BAG = arrayOf("...###...", "..#...#..", ".#######.", "#########", "##.....##", "##.###.##", "##.###.##", "#########", ".#######.")
    private val S_EYE = arrayOf("..#####..", ".#######.", ".##...##.", "##..#..##", ".##...##.", ".#######.", "..#####..")
    private val S_PAUSE = arrayOf("###.###", "###.###", "###.###", "###.###", "###.###", "###.###", "###.###", "###.###", "###.###")
    private val S_POSE = arrayOf("...###...", "...###...", "....#....", ".#######.", "#..###..#", "...###...", "..##.##..", ".##...##.", "##.....##")
    private val S_X = arrayOf("##...##", "###.###", ".#####.", "..###..", ".#####.", "###.###", "##...##")
    private val S_SLIME = arrayOf("..ggggg..", ".ggggggg.", "ggkgggkgg", "ggkgggkgg", "ggggggggg", "gGGGGGGGg", ".ggggggg.")

    /** desenha o sprite centralizado pela caixa dos pixels realmente usados (cx,cy = centro do botão) */
    private fun sprite(c: Canvas, rows: Array<String>, cx: Float, cy: Float, base: Int, white: Int = Color.WHITE) {
        var minC = 99; var maxC = -1; var minR = 99; var maxR = -1
        for (r in rows.indices) for (k in rows[r].indices) if (rows[r][k] != '.') { minC = min(minC, k); maxC = max(maxC, k); minR = min(minR, r); maxR = max(maxR, r) }
        val wb = maxC - minC + 1; val hb = maxR - minR + 1
        val x0 = cx - wb * u / 2f - minC * u; val y0 = cy - hb * u / 2f - minR * u - u / 2f   // -u/2: compensa a sombra de baixo
        for (pass in 0..1) for (r in rows.indices) for (k in rows[r].indices) {
            val ch = rows[r][k]; if (ch == '.') continue
            if (pass == 0 && ch != '#') continue
            val color = when (ch) { '#' -> if (pass == 0) shadeC(base, 0.4f) else white; 'g' -> Color.rgb(120, 225, 110); 'G' -> Color.rgb(70, 170, 80); else -> Color.rgb(20, 60, 30) }
            val oy = if (pass == 0) u else 0f
            rc(c, x0 + k * u, y0 + r * u + oy, x0 + (k + 1) * u, y0 + (r + 1) * u + oy, color)
        }
    }

    private fun drawCells(c: Canvas, s: String, x: Float, y: Float, sc: Int, color: Int) {
        var cx = x; val p = sc.toFloat()
        for (cell in PixFont.parse(s)) {
            for (ry in 0 until 7) {
                val bits = cell.rows[ry]; var rx = 0
                while (rx < 5) {
                    if (((bits shr (4 - rx)) and 1) != 0) {
                        var e = rx
                        while (e + 1 < 5 && ((bits shr (4 - (e + 1))) and 1) != 0) e++
                        rc(c, cx + rx * p, y + ry * p, cx + (e + 1) * p, y + (ry + 1) * p, color); rx = e + 1
                    } else rx++
                }
            }
            val mk = PixFont.marks(cell.mark); var i = 0
            while (i + 1 < mk.size) { rc(c, cx + mk[i] * p, y + mk[i + 1] * p, cx + (mk[i] + 1) * p, y + (mk[i + 1] + 1) * p, color); i += 2 }
            cx += 6 * p
        }
    }

    /** texto em fonte pixelada; align 0 esquerda, 1 centro, 2 direita; y = topo das letras */
    private fun pxText(c: Canvas, s: String, x: Float, y: Float, sc: Int, color: Int, align: Int = 0, shadow: Boolean = true, outline: Boolean = false) {
        val w = PixFont.width(s, sc).toFloat()
        val x0 = when (align) { 1 -> x - w / 2f; 2 -> x - w; else -> x }
        if (outline) { val oc = Color.argb(220, 20, 14, 30); val p = sc.toFloat()
            drawCells(c, s, x0 - p, y, sc, oc); drawCells(c, s, x0 + p, y, sc, oc); drawCells(c, s, x0, y - p, sc, oc); drawCells(c, s, x0, y + p, sc, oc); drawCells(c, s, x0 + p, y + p, sc, oc) }
        else if (shadow) drawCells(c, s, x0 + sc, y + sc, sc, Color.argb(210, 20, 14, 30))
        drawCells(c, s, x0, y, sc, color)
    }
    private fun tsc(f: Float) = max(1, (f * d).roundToInt())

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


    private fun accent(i: Int) = when (i) { 8 -> Cz.LILAC; 1, 6 -> Cz.SKY; 3 -> Cz.TEAL; 5 -> Cz.GOLD; 7 -> Cz.ORANGE; else -> Cz.LILAC }
    private val rf = RectF()
    private val ip = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val shadowF = LightingColorFilter(0x000000, 0x000000)
    private val gold = Color.rgb(255, 214, 90)

    private fun drawPause(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        rc(c, 0f, 0f, w, h, Color.argb(150, 8, 8, 24))
        val b = pbox()
        pxPanel(c, b.left, b.top, b.right, b.bottom, Cz.PANEL)
        pxText(c, "Pausado", w / 2, b.top + 20 * d, tsc(3.4f), gold, 1)
        pxText(c, worldName, w / 2, b.top + 48 * d, tsc(1.6f), Color.argb(220, 255, 255, 255), 1)
        pxBtn(c, pbtn(0), Cz.GREEN, "Continuar")
        pxBtn(c, pbtn(1), Cz.SKY, if (game.thirdPerson) "Câmera: 3ª pessoa" else "Câmera: 1ª pessoa")
        pxBtn(c, pbtn(2), Cz.RED, "Salvar e sair")
    }

    private fun pxBtn(c: Canvas, r: RectF, base: Int, label: String) {
        pxPanel(c, r.left, r.top, r.right, r.bottom, base)
        val sc = tsc(2.2f)
        pxText(c, label.replace("ª", "A"), r.centerX(), r.centerY() - 3.5f * sc, sc, Color.WHITE, 1)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val clock = System.nanoTime() / 1e9f
        if (game.hurtFlash > 0f) rc(c, 0f, 0f, w, h, Color.argb((game.hurtFlash * 90).toInt(), 255, 0, 0))
        if (aimTouch && lookId < 0 && game.aimOn && System.currentTimeMillis() - lastUpT > 350) game.aimOn = false
        if (!paused && !invOpen && lookId >= 0) {
            if (!lookHold && lookMoved < 16 * d && System.currentTimeMillis() - lookDownT > 320) {
                lookHold = true
                game.attackHeld = true; game.attackPress = true
            } else if (lookHold && game.cur() != Items.SWORD && game.cur() != Items.AXE) game.attackPress = true
        }
        val nowP = System.currentTimeMillis()
        if (poseTapT > 0L && nowP - poseTapT >= 320) { poseTapT = 0L; game.requestPosture(1) }
        if (btnId.containsValue(8) && !poseFired && nowP - poseDownT > 380) { poseFired = true; poseTapT = 0L; game.requestPosture(3) }
        if (wet() && game.posture != 0) game.posture = 0
        // ponto de mira (centro, ou no dedo no modo de mira por toque)
        val ax: Float; val ay: Float
        if (aimTouch) { ax = lx; ay = ly } else { ax = w / 2; ay = h / 2 }
        if (!invOpen) {
            if (!aimTouch) {
                val a = 2 * u; val t = u / 2f
                rc(c, ax - a - u / 2, ay - t - u / 2, ax + a + u / 2, ay + t + u / 2, Color.argb(150, 0, 0, 0)); rc(c, ax - t - u / 2, ay - a - u / 2, ax + t + u / 2, ay + a + u / 2, Color.argb(150, 0, 0, 0))
                rc(c, ax - a, ay - t, ax + a, ay + t, Color.WHITE); rc(c, ax - t, ay - a, ax + t, ay + a, Color.WHITE)
            } else if (lookId >= 0) {
                val R = max(5, (26 * d / u).roundToInt())
                pxRing(c, ax, ay, R + 1, 2, Color.argb(190, 20, 14, 30)); pxRing(c, ax, ay, R, 1, Color.argb(235, 255, 255, 255))
                rc(c, ax - u / 2, ay - u / 2, ax + u / 2, ay + u / 2, Color.argb(235, 255, 214, 90))
            }
            // joystick pixelado
            val jx = if (stickId >= 0) scx else defStickX(); val jy = if (stickId >= 0) scy else defStickY()
            val JR = max(6, (60 * d / u).roundToInt())
            pxDisc(c, jx, jy, JR, Color.argb(44, 255, 255, 255))
            pxRing(c, jx, jy, JR + 1, 1, Color.argb(200, 20, 14, 30)); pxRing(c, jx, jy, JR, 1, Color.argb(190, 255, 214, 90))
            for (k in 0 until 4) { val an = k * 1.5708f; rc(c, jx + cos(an) * 46 * d - u / 2, jy + sin(an) * 46 * d - u / 2, jx + cos(an) * 46 * d + u / 2, jy + sin(an) * 46 * d + u / 2, Color.argb(190, 255, 255, 255)) }
            val kx = sn(jx + sx * 60 * d); val ky = sn(jy + sy * 60 * d); val KR = max(3, (24 * d / u).roundToInt())
            pxDisc(c, kx, ky + u, KR, Color.argb(120, 0, 0, 0)); pxDisc(c, kx, ky, KR, Cz.INK)
            pxDisc(c, kx, ky, KR - 1, Color.rgb(255, 208, 96)); pxDisc(c, kx, ky, KR - 2, Color.rgb(222, 232, 248)); pxDisc(c, kx - u, ky - u, max(1, KR / 3), Color.WHITE)
        }
        // botões
        for (i in intArrayOf(7, 8, 1, 6, 3, 5, 4)) {
            if (invOpen && i != 5) continue
            if (i == 6 && !wet()) continue
            if ((i == 7 || i == 8) && wet()) continue
            val active = btnId.containsValue(i) || (i == 5 && invOpen) || (i == 7 && game.sprint) || (i == 8 && game.posture != 0)
            press[i] += ((if (active) 1f else 0f) - press[i]) * 0.5f
            val on = press[i] > 0.5f
            val base = if ((i == 7 && game.sprint) || (i == 8 && game.posture != 0)) Cz.GREEN else accent(i)
            val R = max(4, (br(i) / u).roundToInt())
            val cx = bx(i); val cy = byy(i) + (if (on) u else 0f)
            pxRound(c, cx, byy(i), R, base, on)
            val spr = when (i) { 1 -> S_UP; 6 -> S_DOWN; 7 -> S_RUN; 8 -> S_POSE; 3 -> S_EYE; 5 -> S_BAG; else -> S_PAUSE }
            sprite(c, spr, sn(cx), cy, base)
        }
        // barra de carga do golpe poderoso (perto da mira)
        if (game.charging && !invOpen) {
            val bw = 14 * u; val ch = game.charge; val by = ay + 30 * d
            outer(c, ax - bw / 2 - u, by - u, ax + bw / 2 + u, by + 2 * u, Cz.INK)
            rc(c, ax - bw / 2, by, ax + bw / 2, by + u, Color.argb(120, 255, 255, 255))
            rc(c, ax - bw / 2, by, ax - bw / 2 + sn(bw * ch), by + u, if (ch >= 1f) Color.rgb(255, 214, 90) else Color.rgb(255, (255 - 60 * ch).toInt(), (255 - 170 * ch).toInt()))
        }
        // hotbar
        if (game.sel != lastSel) { lastSel = game.sel; bounce = 1f; nameUntil = System.currentTimeMillis() + 2000 }
        if (game.cur() != lastItem) { lastItem = game.cur(); nameUntil = System.currentTimeMillis() + 2000 }
        bounce = max(0f, bounce - 0.08f)
        val left = hbLeft(); val top = sn(hbTop())
        for (i in 0 until 8) {
            val x = left + i * (slot + gap)
            val s = i == game.sel
            val lift = if (s) sn(3 * d + 4 * d * sin(bounce * 3.14f)) else 0f
            val rr = RectF(x, top - lift, x + slot, top + slot - lift)
            if (s) { val g = (110 + 80 * sin(clock * 4f)).toInt(); frame(c, sn(rr.left) - u, sn(rr.top) - u, sn(rr.right) + u, sn(rr.bottom) + u, u, Color.argb(g, 255, 214, 90)) }
            pxGlass(c, rr.left, rr.top, rr.right, rr.bottom, s)
            icon(c, x + slot / 2, rr.top + slot * 0.5f, slot, game.hotbar[i])
            pxText(c, "${i + 1}", rr.left + 3 * u, rr.top + 3 * u, tsc(1.1f), Color.argb(if (s) 255 else 170, 255, 255, 255))
        }
        if (!invOpen) {
            if (System.currentTimeMillis() < nameUntil) {
                val nm = Items.name(game.cur())
                pxText(c, nm, w / 2, top - 24 * d, 2, Color.WHITE, 1, outline = true)
            }
            if (game.pickT > 0f) {
                val al = (min(1f, game.pickT * 2f) * 255).toInt()
                val py2 = top - 52 * d - (1.6f - game.pickT) * 14 * d
                pxText(c, game.pickMsg, w / 2, py2, tsc(1.6f), Color.argb(al, 255, 236, 140), 1)
            }
        }
        // corações
        val hp = game.hp
        for (i in 0 until 5) {
            val full = hp >= (i + 1) * 2; val half = hp == i * 2 + 1
            val cx = 84 * d + i * 28 * d; val hy = 40 * d
            val beat = if (hp <= 2 && hp > 0) 1f + 0.08f * sin(clock * 9f) else 1f
            val bob = if (hp <= 2 && hp > 0) sin(clock * 12f + i) * 1.5f * d else 0f
            pxHeart(c, cx, hy + bob, 2.4f * d * beat, Color.rgb(228, 36, 48), half && !full, !(full || half))
            if (half && !full) pxHeart(c, cx, hy + bob, 2.4f * d * beat, Color.rgb(228, 36, 48), true, false)
        }
        if (game.deadTimer > 0f) pxText(c, "Você desmaiou! Renascendo...", w / 2, h / 2 - 40 * d, tsc(2.6f), Color.WHITE, 1)
        if (invOpen) drawInventory(c)
        if (paused) drawPause(c)
        postInvalidateOnAnimation()
    }

    private fun drawInventory(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        rc(c, 0f, 0f, w, h, Color.argb(150, 6, 8, 20))
        pxPanel(c, pl(), pt0(), pr(), pb(), Cz.PANEL, false, false, 250)
        pxText(c, "Mochila", pl() + 18 * d, pt0() + 16 * d, tsc(3f), Color.WHITE)
        // fechar
        val xx = pr() - 20 * d; val yy = pt0() + 22 * d
        pxRound(c, xx, yy, 5, Cz.RED, false); sprite(c, S_X, sn(xx), sn(yy), Cz.RED)
        // info do item selecionado no cabeçalho
        val sel = if (invSel > 0) invSel else game.cur()
        val ix = pl() + 150 * d; val avail = (pr() - 50 * d - ix).toInt()
        pxText(c, Items.name(sel), ix, pt0() + 10 * d, tsc(1.9f), Color.rgb(255, 224, 130))
        val ds = Items.desc(sel); var dsc = tsc(1.2f)
        while (dsc > 1 && PixFont.width(ds, dsc) > avail) dsc--
        pxText(c, ds, ix, pt0() + 30 * d, dsc, Color.argb(235, 255, 255, 255))
        for ((i, id) in Items.inventory.withIndex()) {
            val x = cellX(i); val y = cellY(i); val s = cell - 6 * d
            val on = id == invSel
            pxPanel(c, x, y, x + s, y + s, if (on) Cz.PANEL2 else Cz.PANEL3, on)
            icon(c, x + s / 2, y + s / 2, s, id)
        }
        val ty = cellY(Items.inventory.size - 1) + cell + 8 * d
        if (ty < pb() - 4 * d) pxText(c, "Toque num item para colocá-lo no slot selecionado", pl() + 18 * d, ty, tsc(1.2f), Color.argb(190, 255, 255, 255))
    }
}
