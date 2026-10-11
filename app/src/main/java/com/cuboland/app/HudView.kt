package com.cuboland.app

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import kotlin.math.*

class HudView(ctx: Context, val game: Game, val worldName: String, val onMode: (Boolean) -> Unit, val onExit: () -> Unit) : View(ctx) {
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
    private var invOpen = false; private var invSel = -1; private var armItem = 0
    private var poseDownT = 0L; private var poseFired = false; private var poseTapT = 0L
    private var bounce = 0f; private var lastSel = -1; private var lastItem = -1; private var nameUntil = 0L
    // 1 pular/boiar, 3 câmera, 4 pausa, 5 mochila, 6 afundar, 7 correr
    private fun bx(i: Int) = when (i) { 1, 6 -> width * 0.858f; 7 -> width * 0.927f; 8 -> width * 0.934f; 3 -> width - 40 * d; 5 -> width - 92 * d; else -> 40 * d }
    private fun byy(i: Int) = when (i) { 1 -> if (wet()) height * 0.753f - 72 * d else height * 0.753f; 6 -> height * 0.753f; 7 -> height * 0.653f; 8 -> height * 0.833f; else -> 40 * d }
    private fun wet() = game.flying || game.player.inWater
    private fun br(i: Int) = (when (i) { 1 -> 32 * d; 7, 6 -> 28 * d; 8 -> 26 * d; else -> 22 * d }) * (if (i == 1 || i == 6 || i == 7 || i == 8) bs else 1f)
    private fun sn(v: Float) = (v / u).roundToInt() * u
    private val slot get() = sn(34 * d)
    private val gap get() = max(u, sn(4 * d))
    private fun hbLeft() = sn((width - 8 * slot - 7 * gap) / 2f)
    private fun hbTop() = height - slot - 14 * d
    private fun defStickX() = 110 * d
    private fun defStickY() = height - 100 * d

    // painel da mochila
    private fun pl() = 24 * d
    private fun pt0() = 12 * d
    private fun pr() = width - 24 * d
    private fun pb() = hbTop() - 26 * d
    private fun col(v: Int) = v or (0xFF shl 24)

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { game.aspect = w.toFloat() / max(1, h) }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        val act = e.actionMasked; val idx = e.actionIndex
        when (act) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> down(e.getPointerId(idx), e.getX(idx), e.getY(idx))
            MotionEvent.ACTION_MOVE -> if (invOpen) { for (i in 0 until e.pointerCount) invMove(e.getPointerId(i), e.getX(i), e.getY(i)) }
                else if (!paused) for (i in 0 until e.pointerCount) move(e.getPointerId(i), e.getX(i), e.getY(i))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val pid = e.getPointerId(idx)
                if (!(invOpen && invUp(pid, e.getX(idx), e.getY(idx)))) up(pid)
            }
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
        ptrId = -1; ptrDrag = false
    }

    private fun pbox() = RectF(width / 2f - 160 * d, height / 2f - 142 * d, width / 2f + 160 * d, height / 2f + 142 * d)
    private fun pbtn(k: Int): RectF { val b = pbox(); val t = b.top + 98 * d + k * 56 * d; return RectF(b.left + 22 * d, t, b.right - 22 * d, t + 48 * d) }

    private fun pauseDown(x: Float, y: Float) {
        if (pbtn(0).contains(x, y)) { togglePause(); return }
        if (pbtn(1).contains(x, y)) { game.creative = !game.creative; if (!game.creative) game.flying = false; onMode(game.creative); return }
        if (pbtn(2).contains(x, y)) { onExit(); return }
    }

    private fun toggleInv() {
        invOpen = !invOpen
        clearInput()
        invSel = game.cur(); ptrId = -1; ptrDrag = false; armItem = 0
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
        if (!invOpen && hypot(x - bx(5), y - byy(5)) < br(5) * 1.2f) { toggleInv(); return }
        if (invOpen) { invDown(id, x, y); return }
        for (i in intArrayOf(1, 3, 4, 6, 7, 8)) if ((i != 6 || wet()) && (i != 8 || !wet()) && hypot(x - bx(i), y - byy(i)) < br(i) * 1.15f) {
            btnId[id] = i
            when (i) {
                1 -> { game.jumpHeld = true; game.jumpTap() }
                6 -> { game.downHeld = true; game.downTap() }
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
    private fun pxPanel(c: Canvas, l0: Float, t0: Float, r0: Float, b0: Float, base: Int, hi: Boolean = false, pressed: Boolean = false, a: Int = 255, soft: Boolean = false) {
        val l = sn(l0); val t = sn(t0) + (if (pressed) u else 0f); val r = sn(r0); val b = sn(b0)
        val gold = if (hi) Color.rgb(255, 238, 150) else Color.rgb(255, 208, 96)
        val goldL = Color.rgb(255, 240, 170); val goldD = Color.rgb(196, 128, 48)
        if (!pressed) outer(c, l, t + u, r, b + u, al(Cz.INK, a))
        outer(c, l, t, r, b, al(Cz.INK, a))
        outer(c, l + u, t + u, r - u, b - u, al(gold, a))
        rc(c, l + 2 * u, t + u, r - 2 * u, t + 2 * u, al(goldL, a)); rc(c, l + u, t + 2 * u, l + 2 * u, b - 2 * u, al(goldL, a))
        rc(c, l + 2 * u, b - 2 * u, r - 2 * u, b - u, al(goldD, a)); rc(c, r - 2 * u, t + 2 * u, r - u, b - 2 * u, al(goldD, a))
        val light = mixC(base, Color.WHITE, if (soft) 0.5f else 0.3f); val dark = if (soft) mixC(base, Color.rgb(246, 190, 156), 0.55f) else shadeC(base, 0.62f)
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
    private fun pxText(c: Canvas, s: String, x: Float, y: Float, sc: Int, color: Int, align: Int = 0, shadow: Boolean = true, outline: Boolean = false, shColor: Int = Color.argb(210, 20, 14, 30)) {
        val w = PixFont.width(s, sc).toFloat()
        val x0 = (when (align) { 1 -> x - w / 2f; 2 -> x - w; else -> x }).roundToInt().toFloat()
        val yy = y.roundToInt().toFloat()
        if (outline) { val oc = Color.argb(235, 20, 14, 30); val p = sc.toFloat()
            for (ox in -1..1) for (oy in -1..1) if (ox != 0 || oy != 0) drawCells(c, s, x0 + ox * p, yy + oy * p, sc, oc) }
        else if (shadow) drawCells(c, s, x0 + sc, yy + sc, sc, shColor)
        drawCells(c, s, x0, yy, sc, color)
    }
    private fun tsc(f: Float) = max(1, (f * d).roundToInt())

    private fun face(c: Canvas, tile: Int, a: Float, b: Float, cc: Float, dd: Float, ee: Float, ff: Float, f: ColorFilter?) {
        dst[0] = a; dst[1] = b; dst[2] = cc; dst[3] = dd; dst[4] = ee; dst[5] = ff
        mx.setPolyToPoly(src, 0, dst, 0, 3)
        bp.colorFilter = f
        c.drawBitmap(Atlas.tiles[tile], mx, bp)
    }

    private val bPath = Path()
    private val bStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND }
    private fun blockIcon(c: Canvas, cx: Float, cy: Float, s: Float, id: Int) {
        face(c, B.tTop[id], cx, cy - s, cx + s, cy - s / 2, cx - s, cy - s / 2, null)
        face(c, B.tSide[id], cx - s, cy - s / 2, cx, cy, cx - s, cy + s / 2, fLeft)
        face(c, B.tSide[id], cx, cy, cx + s, cy - s / 2, cx, cy + s, fRight)
        val lw = max(2f, s * 0.12f)
        // luz suave na face de cima e sombra embaixo das laterais
        bPath.reset(); bPath.moveTo(cx, cy - s); bPath.lineTo(cx + s, cy - s / 2); bPath.lineTo(cx, cy); bPath.lineTo(cx - s, cy - s / 2); bPath.close()
        bStroke.style = Paint.Style.FILL; bStroke.color = Color.argb(34, 255, 255, 255); c.drawPath(bPath, bStroke)
        bPath.reset(); bPath.moveTo(cx - s, cy + s / 2); bPath.lineTo(cx, cy + s); bPath.lineTo(cx + s, cy + s / 2); bPath.lineTo(cx + s, cy + s / 2 - s * 0.22f); bPath.lineTo(cx, cy + s - s * 0.22f); bPath.lineTo(cx - s, cy + s / 2 - s * 0.22f); bPath.close()
        bStroke.color = Color.argb(46, 40, 20, 50); c.drawPath(bPath, bStroke)
        bStroke.style = Paint.Style.STROKE
        // arestas internas
        bStroke.strokeWidth = lw * 0.6f; bStroke.color = Color.argb(110, 40, 24, 50)
        c.drawLine(cx, cy, cx - s, cy - s / 2, bStroke); c.drawLine(cx, cy, cx + s, cy - s / 2, bStroke); c.drawLine(cx, cy, cx, cy + s, bStroke)
        // brilho nas bordas de cima
        bStroke.strokeWidth = lw * 0.7f; bStroke.color = Color.argb(150, 255, 255, 255)
        c.drawLine(cx - s + lw, cy - s / 2 - lw * 0.1f, cx, cy - s + lw * 0.9f, bStroke); c.drawLine(cx, cy - s + lw * 0.9f, cx + s - lw, cy - s / 2 - lw * 0.1f, bStroke)
        // contorno
        bPath.reset(); bPath.moveTo(cx, cy - s); bPath.lineTo(cx + s, cy - s / 2); bPath.lineTo(cx + s, cy + s / 2); bPath.lineTo(cx, cy + s); bPath.lineTo(cx - s, cy + s / 2); bPath.lineTo(cx - s, cy - s / 2); bPath.close()
        bStroke.strokeWidth = lw; bStroke.color = Color.argb(235, 36, 28, 48); c.drawPath(bPath, bStroke)
        // pontinho de brilho
        bStroke.style = Paint.Style.FILL; bStroke.color = Color.argb(230, 255, 255, 255)
        c.drawCircle(cx - s * 0.42f, cy - s * 0.56f, max(1.5f, s * 0.09f), bStroke)
        bStroke.style = Paint.Style.STROKE
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
            val s2 = size * 0.98f
            rf.set(cx - s2 / 2, cy - s2 / 2, cx + s2 / 2, cy + s2 / 2)
            // contorno escuro em volta da ferramenta (em vez de sombra embaixo)
            ip.colorFilter = shadowF; ip.alpha = 215
            val o = max(1.5f, size * 0.022f)
            for (k in 0 until 8) { val an = k * 0.7854f; c.save(); c.translate(cos(an) * o, sin(an) * o); c.drawBitmap(bm, null, rf, ip); c.restore() }
            ip.colorFilter = null; ip.alpha = 255
            c.drawBitmap(bm, null, rf, ip)
        } else if (Items.isBlock(id)) {
            blockIcon(c, cx, cy, size * 0.36f, id)
        }
    }


    private fun accent(i: Int) = when (i) { 8 -> Cz.LILAC; 1, 6 -> Cz.SKY; 3 -> Cz.TEAL; 5 -> Cz.GOLD; 7 -> Cz.ORANGE; else -> Cz.LILAC }
    private val rf = RectF()
    private val ip = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val shadowF = LightingColorFilter(0x000000, 0x000000)
    private val gold = Color.rgb(255, 214, 90)

    private val ICO_PLAY = arrayOf("##.......", "####.....", "######...", "########.", "#########", "########.", "######...", "####.....", "##.......")
    private val ICO_HEART = arrayOf(".##...##.", "####.####", "#########", "#########", "#########", ".#######.", "..#####..", "...###...", "....#....")
    private val ICO_STAR = arrayOf("....#....", "....#....", "...###...", "#########", ".#######.", "..#####..", "..##.##..", ".##...##.", ".#.....#.")
    private val ICO_SWAP = arrayOf("...#.....", "..##.....", ".#######.", "..##.....", "...#.....", ".....#...", ".....##..", ".#######.", ".....##..", ".....#...")
    private val ICO_EXIT = arrayOf("#####..#.", "#...#..##", "#...#####", "#...#..##", "#...#..#.", "#...#....", "#...#....", "#####....")
    private val ICO_PAUSE = arrayOf("##.##", "##.##", "##.##", "##.##", "##.##", "##.##", "##.##")

    /** ícone pixelado com sombra; '#' = cor, 'o' = escuro */
    private fun pxIcon(c: Canvas, rows: Array<String>, cx: Float, cy: Float, color: Int, p: Float = (u * 3f / 2f).roundToInt().toFloat()) {
        val x0 = (cx - rows[0].length * p / 2f).roundToInt().toFloat(); val y0 = (cy - rows.size * p / 2f).roundToInt().toFloat()
        for (pass in 0..1) for (r in rows.indices) for (q in 0 until rows[r].length) {
            val ch = rows[r][q]; if (ch == '.') continue
            val o = if (pass == 0) p * 0.6f else 0f
            rc(c, x0 + q * p + o, y0 + r * p + o, x0 + (q + 1) * p + o, y0 + (r + 1) * p + o, if (pass == 0) Color.argb(110, 20, 10, 30) else if (ch == 'o') Cz.INK else color)
        }
    }

    // ------------------------------------------------------------------ enfeites fofos (mochila e barra de itens)
    private val ART_BAG = arrayOf(
        "....kkkkkk....", "...k.kkkk.k...", "...k......k...", "..kkkkkkkkkk..", ".kooooooooook.", "kowwooooooooOk", "kowoooooooooOk",
        "koooooooooooOk", "kooookkkkoooOk", "kkkkkkkkkkkkkk", "kyyyyyyyyyyyYk", "kyyyyykkyyyyYk", "kYYYYYYYYYYYYk", ".kkkkkkkkkkkk.")
    private val ART_HAND = arrayOf(
        "..kk.kk.kk..", ".kssksskssk.", ".kssksskssk.", ".ksssssssssk", "kssssssssssk", "kswsssssssSk", "ksssssssssSk", ".kssssssssk.", "..kssssssk..", "...kkkkkk...")
    private val X_PAT = arrayOf("#...#", ".#.#.", "..#..", ".#.#.", "#...#")
    private val ART_STAR = arrayOf(".....k.....", "....kyk....", "....kyk....", "...kyyyk...", "kkkkyyykkkk", "kywyyyyyyyk", ".kyyyyyyyk.", "..kyyyyyk..", "..kyyyyyk..", ".kyyykyyyk.", ".kkkk.kkkk.")
    private val ART_SPARK = arrayOf("..y..", "..y..", "yywyy", "..y..", "..y..")

    private fun artCol(ch: Char): Int = when (ch) {
        'k' -> Cz.INK; 'o' -> Cz.ORANGE; 'O' -> Color.rgb(214, 100, 40); 'y' -> Cz.GOLD; 'Y' -> Color.rgb(222, 160, 56)
        'w' -> Color.WHITE; 's' -> Color.rgb(255, 214, 170); 'S' -> Color.rgb(226, 166, 120); else -> 0
    }

    /** desenha uma arte em pixels coloridos centrada em (cx,cy); p = tamanho de cada pixel da arte */
    private fun pxArt(c: Canvas, rows: Array<String>, cx: Float, cy: Float, p: Float, a: Int = 255) {
        val x0 = (cx - rows[0].length * p / 2f).roundToInt().toFloat(); val y0 = (cy - rows.size * p / 2f).roundToInt().toFloat()
        for (r in rows.indices) for (k in rows[r].indices) {
            val col = artCol(rows[r][k]); if (col == 0) continue
            rc(c, x0 + k * p, y0 + r * p, x0 + (k + 1) * p, y0 + (r + 1) * p, al(col, a))
        }
    }

    private fun handArt(c: Canvas, cx: Float, cy: Float, size: Float, a: Int) {
        val p = max(1f, (size * 0.7f / 12f).roundToInt().toFloat())
        pxArt(c, ART_HAND, cx, cy, p, a)
    }

    /** costura tracejada */
    private fun stitch(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        val xl = sn(l); val xe = sn(r); val yt = sn(t); val yb = sn(b)
        var x = xl; var on = true
        while (x < xe) { val e = min(x + 2 * u, xe); if (on) { rc(c, x, yt, e, yt + u, color); rc(c, x, yb - u, e, yb, color) }; on = !on; x += 2 * u }
        var y = yt; on = true
        while (y < yb) { val e = min(y + 2 * u, yb); if (on) { rc(c, xl, y, xl + u, e, color); rc(c, xe - u, y, xe, e, color) }; on = !on; y += 2 * u }
    }

    /** bolinhas espalhadas (estampa de tecido) */
    private fun dots(c: Canvas, l: Float, t: Float, r: Float, b: Float, color: Int) {
        val step = 8 * u; var y = sn(t); var row = 0
        while (y < b - u) {
            var x = sn(l) + (if (row % 2 == 0) 0f else step / 2f)
            while (x < r - u) { rc(c, x, y, x + u, y + u, color); x += step }
            y += step / 2f; row++
        }
    }

    /** plaquinha arredondada (etiquetas) */
    private fun pxPill(c: Canvas, l0: Float, t0: Float, r0: Float, b0: Float, fill: Int, a: Int = 255) {
        val l = sn(l0); val t = sn(t0); val r = sn(r0); val b = sn(b0)
        outer(c, l, t, r, b, al(Cz.INK, a))
        outer(c, l + u, t + u, r - u, b - u, al(fill, a))
        rc(c, l + 2 * u, t + u, r - 2 * u, t + 2 * u, al(mixC(fill, Color.WHITE, 0.45f), a))
        rc(c, l + 2 * u, b - 2 * u, r - 2 * u, b - u, al(shadeC(fill, 0.8f), a))
    }

    /** bolsinho de item "afundado": moldura amadeirada, interior creme com sombrinha em cima/esquerda. mode 1 = destacado (dourado) */
    private fun pxSlot(c: Canvas, l0: Float, t0: Float, r0: Float, b0: Float, mode: Int, a: Int = 255) {
        val l = sn(l0); val t = sn(t0); val r = sn(r0); val b = sn(b0)
        val fr = if (mode == 1) Color.rgb(255, 214, 90) else Color.rgb(200, 144, 88)
        val frL = if (mode == 1) Color.rgb(255, 242, 176) else Color.rgb(232, 184, 128)
        val frD = if (mode == 1) Color.rgb(214, 140, 40) else Color.rgb(140, 92, 56)
        outer(c, l, t, r, b, al(Cz.INK, a))
        outer(c, l + u, t + u, r - u, b - u, al(fr, a))
        rc(c, l + 2 * u, t + u, r - 2 * u, t + 2 * u, al(frL, a)); rc(c, l + u, t + 2 * u, l + 2 * u, b - 2 * u, al(frL, a))
        rc(c, l + 2 * u, b - 2 * u, r - 2 * u, b - u, al(frD, a)); rc(c, r - 2 * u, t + 2 * u, r - u, b - 2 * u, al(frD, a))
        val fl = l + 2 * u; val ft = t + 2 * u; val frr = r - 2 * u; val fb = b - 2 * u
        rc(c, fl, ft, frr, fb, al(if (mode == 1) Color.rgb(255, 240, 196) else Color.rgb(250, 238, 216), a))
        val sh = if (mode == 1) Color.rgb(240, 200, 120) else Color.rgb(222, 192, 158)
        rc(c, fl, ft, frr, ft + u, al(sh, a)); rc(c, fl, ft, fl + u, fb, al(sh, a))
        rc(c, fl + u, fb - u, frr, fb, al(Color.WHITE, a * 190 / 255))
    }

    private fun drawPause(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        rc(c, 0f, 0f, w, h, Color.argb(160, 8, 8, 24))
        val b = pbox()
        pxPanel(c, b.left, b.top, b.right, b.bottom, Cz.PANEL)
        // faixa do título
        val tl = b.left + 16 * d; val tr = b.right - 16 * d; val tt = b.top + 14 * d; val tb = b.top + 48 * d
        rc(c, sn(tl), sn(tt), sn(tr), sn(tb), Color.argb(46, 196, 128, 48))
        rc(c, sn(tl), sn(tb) - u, sn(tr), sn(tb), Color.argb(120, 196, 128, 48))
        pxText(c, "Pausado", w / 2, b.top + 24 * d, tsc(3.4f), Cz.GOLD_D, 1, false)
        pxIcon(c, ICO_PAUSE, b.left + 42 * d, (tt + tb) / 2f, Cz.GOLD_D); pxIcon(c, ICO_PAUSE, b.right - 42 * d, (tt + tb) / 2f, Cz.GOLD_D)
        // nome do mundo numa plaquinha escura
        val nm = if (worldName.length > 18) worldName.take(17) + "." else worldName
        val sc = tsc(1.6f); val nw = PixFont.width(nm, sc) + 28 * d
        val cy0 = b.top + 66 * d
        rc(c, sn(w / 2 - nw / 2), sn(cy0 - 9 * d), sn(w / 2 + nw / 2), sn(cy0 + 9 * d), Color.argb(215, 58, 36, 54))
        rc(c, sn(w / 2 - nw / 2), sn(cy0 - 9 * d), sn(w / 2 + nw / 2), sn(cy0 - 9 * d) + u, Color.argb(255, 255, 208, 96))
        rc(c, sn(w / 2 - nw / 2), sn(cy0 + 9 * d) - u, sn(w / 2 + nw / 2), sn(cy0 + 9 * d), Color.argb(255, 196, 128, 48))
        pxText(c, nm, w / 2, cy0 - 3.5f * sc, sc, Cz.CREAM, 1)
        // divisor com losango
        val dy = sn(b.top + 86 * d)
        rc(c, sn(b.left + 28 * d), dy, sn(w / 2 - 8 * d), dy + u, Color.argb(130, 196, 128, 48)); rc(c, sn(w / 2 + 8 * d), dy, sn(b.right - 28 * d), dy + u, Color.argb(130, 196, 128, 48))
        rc(c, w / 2 - u / 2, dy - u, w / 2 + u / 2, dy + 2 * u, Cz.GOLD_D); rc(c, w / 2 - u * 1.5f, dy, w / 2 + u * 1.5f, dy + u, Cz.GOLD_D)

        pxBtn(c, pbtn(0), Cz.GREEN, "Continuar", null, ICO_PLAY, null)
        val cre = game.creative
        pxBtn(c, pbtn(1), if (cre) Cz.LILAC else Cz.ORANGE, if (cre) "Criativo" else "Sobrevivência", "MODO DE JOGO", if (cre) ICO_STAR else ICO_HEART, ICO_SWAP)
        pxBtn(c, pbtn(2), Cz.RED, "Salvar e sair", null, ICO_EXIT, null)
        pxText(c, if (cre) "Toque para ir ao modo sobrevivência" else "Toque para ir ao modo criativo", w / 2, pbtn(2).bottom + 12 * d, tsc(1f), Cz.TXT_SOFT, 1, false)
    }

    private fun pxBtn(c: Canvas, r: RectF, base: Int, label: String, sub: String?, icon: Array<String>, right: Array<String>?) {
        pxPanel(c, r.left, r.top, r.right, r.bottom, base)
        val sc = tsc(2.1f)
        val label2 = label.replace("ª", "A")
        if (sub == null) pxText(c, label2, r.centerX(), r.centerY() - 3.5f * sc, sc, Color.WHITE, 1)
        else {
            val s1 = tsc(1.1f); val tot = 7f * s1 + 4 * d + 7f * sc; val y0 = r.centerY() - tot / 2f - 1.5f * d
            pxText(c, sub, r.centerX(), y0, s1, mixC(base, Color.WHITE, 0.8f), 1)
            pxText(c, label2, r.centerX(), y0 + 7f * s1 + 4 * d, sc, Color.WHITE, 1)
        }
        pxIcon(c, icon, r.left + 28 * d, r.centerY() - 1.5f * d, Color.WHITE)
        if (right != null) pxIcon(c, right, r.right - 28 * d, r.centerY() - 1.5f * d, mixC(base, Color.WHITE, 0.85f))
    }

    private fun rnd(i: Int, k: Int): Float { val v = sin(i * 12.9898f + k * 78.233f) * 43758.547f; return v - floor(v) }

    /** efeito de câmera molhada ao sair da água: véu azulado, brilho e gotas escorrendo (some em ~3s) */
    private fun drawWet(c: Canvas) {
        val wt = game.wetT
        if (wt <= 0f) return
        val w = width.toFloat(); val h = height.toFloat()
        val e = 3f - wt
        val f = min(1f, wt / 1.2f)
        pt.color = Color.argb((26 * f).toInt(), 120, 190, 255); c.drawRect(0f, 0f, w, h, pt)
        val fl = max(0f, 1f - e / 0.3f)
        if (fl > 0f) { pt.color = Color.argb((80 * fl).toInt(), 225, 247, 255); c.drawRect(0f, 0f, w, h, pt) }
        for (i in 0 until 38) {
            val life = 1.4f + rnd(i, 1) * 1.6f
            if (e > life) continue
            val x = sn(rnd(i, 2) * w)
            val y0 = rnd(i, 3) * h * 0.75f
            val spd = (6f + rnd(i, 4) * 40f) * d
            val y = y0 + spd * e + 16f * d * e * e
            if (y > h) continue
            val sz = sn(u * (1f + rnd(i, 5) * 2.4f)).coerceAtLeast(u)
            val a = (210f * f * min(1f, (life - e) * 3f)).toInt().coerceIn(0, 255)
            val trail = min(y - y0, 46f * d)
            pt.color = Color.argb(a * 35 / 100, 200, 235, 255); c.drawRect(x, y - trail, x + u, y, pt)
            pt.color = Color.argb(a * 55 / 100, 190, 225, 255); c.drawRect(x - sz / 2f, y, x + sz / 2f, y + sz * 1.4f, pt)
            pt.color = Color.argb(a * 60 / 100, 90, 150, 210); c.drawRect(x - sz / 2f, y + sz * 1.4f - u, x + sz / 2f, y + sz * 1.4f, pt)
            pt.color = Color.argb(a, 255, 255, 255); c.drawRect(x - sz / 2f + u * 0.5f, y + u * 0.5f, x - sz / 2f + u * 1.5f, y + u * 1.5f, pt)
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val clock = System.nanoTime() / 1e9f
        drawWet(c)
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
            if (invOpen) continue
            if (i == 6 && !wet()) continue
            if (i == 8 && wet()) continue
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
        val tot = 8 * slot + 7 * gap
        val bL = left - 5 * u; val bR = left + tot + 5 * u; val bT = top - 5 * u; val bB = top + slot + 5 * u
        // tabuleiro de fundo com costura e enfeites
        pxPanel(c, bL, bT, bR, bB, Cz.PANEL2, false, false, 236, true)
        stitch(c, sn(bL) + 3 * u, sn(bT) + 3 * u, sn(bR) - 3 * u, sn(bB) - 3 * u, Color.argb(130, 214, 150, 90))
        val invite = invOpen && armItem != 0
        val nb = max(1, tsc(0.9f)); val hf = max(1f, (u / 2f).roundToInt().toFloat())
        for (i in 0 until 8) {
            val x = left + i * (slot + gap)
            val s = i == game.sel
            val lift = if (s) sn(2 * d + 3 * d * sin(bounce * 3.14f)) else 0f
            val rr = RectF(x, top - lift, x + slot, top + slot - lift)
            if (s) { val g = (110 + 80 * sin(clock * 4f)).toInt(); frame(c, sn(rr.left) - u, sn(rr.top) - u, sn(rr.right) + u, sn(rr.bottom) + u, u, Color.argb(g, 255, 214, 90)) }
            else if (invite) { val g = (70 + 70 * sin(clock * 6f + i * 0.6f)).toInt().coerceIn(0, 255); frame(c, sn(rr.left) - u, sn(rr.top) - u, sn(rr.right) + u, sn(rr.bottom) + u, u, Color.argb(g, 255, 214, 90)) }
            pxSlot(c, rr.left, rr.top, rr.right, rr.bottom, if (s) 1 else 0)
            val itemId = game.hotbar[i]
            val hideIcon = invOpen && ptrId >= 0 && ptrDrag && ptrKind == 2 && ptrSlot == i
            if (itemId != 0) { if (!hideIcon) icon(c, x + slot / 2, rr.top + slot * 0.5f, slot, itemId) }
            else if (invite) pxText(c, "+", x + slot / 2, rr.top + slot * 0.5f - 3.5f * tsc(2f), tsc(2f), Color.argb((150 + 100 * sin(clock * 6f)).toInt().coerceIn(0, 255), 214, 118, 28), 1, false)
            else handArt(c, x + slot / 2, rr.top + slot * 0.5f, slot * 0.8f, 80)
            // plaquinha com o número do slot
            val bw = 5 * nb + 4 * hf; val bh = 7 * nb + 4 * hf
            val bx0 = sn(rr.left) + 2 * u; val by0 = sn(rr.top) + 2 * u
            rc(c, bx0, by0, bx0 + bw, by0 + bh, Cz.INK)
            rc(c, bx0 + hf, by0 + hf, bx0 + bw - hf, by0 + bh - hf, if (s) Color.rgb(255, 214, 90) else Color.rgb(176, 118, 76))
            pxText(c, "${i + 1}", bx0 + 2 * hf, by0 + 2 * hf, nb, if (s) Cz.INK else Color.WHITE, 0, false)
        }
        if (!invOpen) {
            if (System.currentTimeMillis() < nameUntil) {
                val nm = Items.name(game.cur())
                val ns0 = max(2, tsc(1.4f))
                pxText(c, nm, w / 2, top - 5 * u - 7 * ns0 - 8 * d, ns0, Color.WHITE, 1, outline = true)
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

    // ================= MOCHILA (abas por categoria, pesquisa, arrastar pra barra) =================
    private var query = ""; private var invCat = 0; private var invScroll = 0f
    private var ptrId = -1; private var ptrKind = 0; private var ptrItem = 0; private var ptrSlot = -1
    private var ptrX0 = 0f; private var ptrY0 = 0f; private var ptrX = 0f; private var ptrY = 0f; private var ptrDrag = false; private var ptrScroll0 = 0f
    private var bodyTop = 0f; private var bodyBot = 0f; private var tabL = 0f; private var tabR = 0f; private var tabH = 0f
    private var infoL = 0f; private var infoR = 0f; private var infoW = 0f
    private var gx0 = 0f; private var gx1 = 0f; private var gTop = 0f; private var gBot = 0f; private var cs = 0f; private var cols = 1; private var maxScroll = 0f
    private var sL = 0f; private var sR = 0f; private var sT = 0f; private var sB = 0f
    private var shownKey = ""; private var shownList: List<Int> = emptyList()
    private val MARKS = Regex("\\p{M}+")
    private val ICO_MAG = arrayOf("..###....", ".#...#...", "#.....#..", "#.....#..", "#.....#..", ".#...#...", "..###.#..", ".......#.", "........#")

    private fun normQ(s: String) = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD).replace(MARKS, "")

    private fun shown(): List<Int> {
        val key = "$invCat|$query"
        if (key == shownKey) return shownList
        val q = normQ(query.trim())
        shownList = if (q.isEmpty()) Items.inventory.filter { invCat == 0 || Items.cat(it) == invCat }
        else Items.inventory.filter { normQ(Items.name(it)).contains(q) || normQ(Items.CATS[Items.cat(it)]).contains(q) }
        shownKey = key
        return shownList
    }

    private fun catColor(k: Int) = when (k) { 0 -> Cz.GOLD_D; 1 -> Cz.SKY; 2 -> Cz.GREEN; 3 -> Cz.ORANGE; else -> Cz.LILAC }

    private fun layout() {
        val L = pl(); val R = pr(); val T = pt0(); val PB = pb()
        bodyTop = T + 54 * d; bodyBot = PB - 12 * d
        tabL = L + 14 * d; tabR = tabL + 52 * d
        tabH = min(40 * d, (bodyBot - bodyTop - 16 * d) / 5f)
        infoW = if ((R - L) / d >= 600f) 176 * d else 0f
        infoR = R - 14 * d; infoL = infoR - infoW
        gx0 = tabR + 10 * d; gx1 = if (infoW > 0f) infoL - 10 * d else R - 14 * d
        gTop = bodyTop + 36 * d; gBot = bodyBot
        cs = 54 * d
        cols = max(1, ((gx1 - gx0 - 22 * d) / cs).toInt())
        val rows = (shown().size + cols - 1) / cols
        maxScroll = max(0f, rows * cs + 8 * d - (gBot - gTop))
        invScroll = invScroll.coerceIn(0f, maxScroll)
        sL = L + 172 * d; sR = R - 54 * d; sT = T + 11 * d; sB = T + 45 * d
    }

    private fun cellX(i: Int) = gx0 + 8 * d + (i % cols) * cs
    private fun cellY(i: Int) = gTop + 4 * d + (i / cols) * cs - invScroll
    private fun tabY(k: Int) = bodyTop + k * (tabH + 4 * d)
    private fun slotAt(x: Float): Int {
        if (x < hbLeft() - 4 * d) return -1
        val s = ((x - hbLeft()) / (slot + gap)).toInt()
        return if (s in 0..7) s else -1
    }
    private fun dropSlot(x: Float, y: Float) = if (y < pb() - 8 * d) -1 else slotAt(x)

    private fun invDown(id: Int, x: Float, y: Float) {
        if (ptrId >= 0) return
        layout()
        fun start(kind: Int) { ptrId = id; ptrKind = kind; ptrX0 = x; ptrY0 = y; ptrX = x; ptrY = y; ptrDrag = false; ptrScroll0 = invScroll }
        if (hypot(x - (pr() - 26 * d), y - (pt0() + 28 * d)) < 22 * d) { toggleInv(); return }
        if (y >= pb()) {   // barra de itens continua usável com a mochila aberta
            val s = slotAt(x)
            if (s >= 0) { start(2); ptrSlot = s; ptrItem = game.hotbar[s] }
            return
        }
        if (x >= sL && x <= sR && y >= sT && y <= sB) {
            if (query.isNotEmpty() && x > sR - 30 * d) { query = ""; invScroll = 0f } else start(4)
            return
        }
        for (k in 0 until 5) if (x >= tabL && x <= tabR + 4 * d && y >= tabY(k) && y <= tabY(k) + tabH) { invCat = k; query = ""; invScroll = 0f; return }
        if (x >= gx0 && x <= gx1 && y >= gTop && y <= gBot) {
            val list = shown()
            for (i in list.indices) {
                val cx = cellX(i); val cy = cellY(i)
                if (x >= cx && x <= cx + cs - 6 * d && y >= cy && y <= cy + cs - 6 * d) { start(1); ptrItem = list[i]; invSel = ptrItem; return }
            }
            start(3); return
        }
        if (x < pl() || x > pr() || y < pt0() || y > pb()) toggleInv()
    }

    private fun invMove(id: Int, x: Float, y: Float) {
        if (id != ptrId) return
        ptrX = x; ptrY = y
        if (ptrKind == 3) { invScroll = (ptrScroll0 - (y - ptrY0)).coerceIn(0f, maxScroll); return }
        if (!ptrDrag && hypot(x - ptrX0, y - ptrY0) > 10 * d && (ptrKind == 1 || ptrKind == 2)) ptrDrag = true
        if (!ptrDrag && ptrKind == 4 && hypot(x - ptrX0, y - ptrY0) > 10 * d) ptrDrag = true
    }

    private fun invUp(id: Int, x: Float, y: Float): Boolean {
        if (id != ptrId) return false
        ptrId = -1
        val kind = ptrKind; val drag = ptrDrag; ptrDrag = false
        when (kind) {
            1 -> {
                invSel = ptrItem
                if (drag) { val s = dropSlot(x, y); if (s >= 0) { game.hotbar[s] = ptrItem; game.sel = s; bounce = 1f; armItem = 0 } }
                else armItem = if (armItem == ptrItem) 0 else ptrItem   // toque: escolhe o item (toque de novo desescolhe); agora é só tocar num slot
            }
            2 -> {
                if (drag) {
                    val s = dropSlot(x, y)
                    if (s >= 0 && s != ptrSlot) { val t = game.hotbar[s]; game.hotbar[s] = game.hotbar[ptrSlot]; game.hotbar[ptrSlot] = t; game.sel = s; bounce = 1f }
                    else if (s < 0 && y < pb() - 8 * d && game.hotbar[ptrSlot] != 0) { game.hotbar[ptrSlot] = 0; bounce = 1f }   // arrastou pra fora da barra: slot fica vazio (só a mão)
                } else if (armItem != 0) { game.hotbar[ptrSlot] = armItem; game.sel = ptrSlot; armItem = 0; bounce = 1f }
                else game.sel = ptrSlot
                invSel = game.cur()
            }
            4 -> if (!drag) openSearch()
        }
        return true
    }

    private fun openSearch() {
        val act = context as? android.app.Activity ?: return
        val et = android.widget.EditText(act)
        et.setText(query); et.setSelection(et.text.length); et.hint = "Nome do item ou categoria"
        et.isSingleLine = true; et.imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        val dlg = android.app.AlertDialog.Builder(act).setTitle("Pesquisar na mochila").setView(et)
            .setPositiveButton("Buscar") { _, _ -> query = et.text.toString().trim(); invScroll = 0f }
            .setNeutralButton("Limpar") { _, _ -> query = ""; invScroll = 0f }
            .setNegativeButton("Cancelar", null).create()
        et.setOnEditorActionListener { _, _, _ -> query = et.text.toString().trim(); invScroll = 0f; dlg.dismiss(); true }
        dlg.setOnDismissListener { act.goFullscreen() }
        dlg.window?.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        dlg.show(); et.requestFocus()
    }

    private fun wrap(s: String, sc: Int, maxW: Int): List<String> {
        val out = ArrayList<String>(); var line = ""
        for (wd in s.split(" ")) {
            val t = if (line.isEmpty()) wd else "$line $wd"
            if (line.isNotEmpty() && PixFont.width(t, sc) > maxW) { out.add(line); line = wd } else line = t
        }
        if (line.isNotEmpty()) out.add(line)
        return out
    }

    private fun pips(c: Canvas, label: String, x: Float, y: Float, wd: Float, n: Int, color: Int) {
        val s1 = tsc(1f)
        pxText(c, label, x, y + 1 * d, s1, Cz.TXT, 0, false)
        val ps = 6 * d; val st = 8 * d
        for (i in 0 until 5) {
            val px = x + wd - (5 - i) * st
            rc(c, px, y, px + ps, y + ps, Cz.INK)
            rc(c, px + u / 2, y + u / 2, px + ps - u / 2, y + ps - u / 2, if (i < n) color else Color.argb(90, 150, 120, 110))
        }
    }

    private fun drawInventory(c: Canvas) {
        layout()
        val w = width.toFloat()
        val clock = System.nanoTime() / 1e9f
        val hb = hbTop(); val L = pl(); val R = pr(); val T = pt0(); val PB = pb()
        // fundo escurecido, mais leve perto da barra de itens (que continua usável)
        rc(c, 0f, 0f, w, hb - 34 * d, Color.argb(160, 6, 8, 20))
        rc(c, 0f, hb - 34 * d, w, hb - 22 * d, Color.argb(110, 6, 8, 20))
        rc(c, 0f, hb - 22 * d, w, hb - 10 * d, Color.argb(50, 6, 8, 20))
        // painel principal: degradê quentinho, bolinhas e costura
        pxPanel(c, L, T, R, PB, Cz.PANEL, false, false, 252, true)
        val pl0 = sn(L); val pt1 = sn(T); val pr0 = sn(R); val pb0 = sn(PB)
        dots(c, pl0 + 3 * u, pt1 + 3 * u, pr0 - 3 * u, pb0 - 3 * u, Color.argb(85, 255, 190, 150))
        stitch(c, pl0 + 4 * u, pt1 + 4 * u, pr0 - 4 * u, pb0 - 4 * u, Color.argb(130, 214, 150, 90))
        // placa do título (laranja, letras brancas com contorno: bem legível)
        val plL = L + 12 * d; val plR = sL - 10 * d; val plT = T + 9 * d; val plB = T + 47 * d
        pxPanel(c, plL, plT, plR, plB, Cz.ORANGE, false, false, 255, true)
        var ts = tsc(2.4f); val tAvail = (plR - plL - 20 * d).toInt()
        while (ts > 1 && PixFont.width("Mochila", ts) > tAvail) ts--
        pxText(c, "Mochila", (plL + plR) / 2f, (plT + plB) / 2f - 3.5f * ts, ts, Color.WHITE, 1, false, true)
        // barra de pesquisa
        val sl = sn(sL); val st = sn(sT); val sr = sn(sR); val sb = sn(sB); val sm = (st + sb) / 2f
        outer(c, sl, st + u, sr, sb + u, Color.argb(90, 36, 28, 48))
        outer(c, sl, st, sr, sb, Cz.INK)
        outer(c, sl + u, st + u, sr - u, sb - u, if (query.isNotEmpty()) Cz.GOLD else Color.rgb(224, 176, 92))
        rc(c, sl + 2 * u, st + 2 * u, sr - 2 * u, sb - 2 * u, Color.rgb(255, 251, 242))
        rc(c, sl + 2 * u, st + 2 * u, sr - 2 * u, st + 3 * u, Color.argb(40, 120, 70, 40))
        pxIcon(c, ICO_MAG, sl + 16 * d, sm, Cz.TXT_SOFT, u)
        val tsx = tsc(1.7f); val tx0 = sl + 30 * d; val avail = (sr - tx0 - (if (query.isNotEmpty()) 30 * d else 10 * d)).toInt()
        if (query.isEmpty()) pxText(c, "Pesquisar item...", tx0, sm - 3.5f * tsx, tsx, Cz.TXT_SOFT, 0, false)
        else {
            var q = query; while (q.length > 1 && PixFont.width(q, tsx) > avail) q = q.substring(1)
            pxText(c, q, tx0, sm - 3.5f * tsx, tsx, Cz.TXT, 0, false)
            pxIcon(c, S_X, sr - 16 * d, sm, Cz.RED, u)
        }
        // botão de fechar: tudo centrado no mesmo ponto, no meio do espaço entre a busca e a borda
        val xx = sn((sR + R - 2 * u) / 2f); val yy = sn(T + 28 * d)
        pxDisc(c, xx, yy, 7, Cz.INK)
        pxDisc(c, xx, yy, 6, Color.rgb(255, 208, 96))
        pxDisc(c, xx, yy, 5, shadeC(Cz.RED, 0.72f))
        pxDisc(c, xx, yy, 4, Cz.RED)
        for (r in 0 until 5) for (k in 0 until 5) if (X_PAT[r][k] == '#')
            rc(c, xx + (k - 2.5f) * u, yy + (r - 2.5f) * u, xx + (k - 1.5f) * u, yy + (r - 1.5f) * u, Color.WHITE)
        // abas de categoria
        val searching = query.isNotEmpty()
        val tabIcon = intArrayOf(-1, Items.SWORD, B.GRASS, B.BRICK, B.PINK)
        for (k in 0 until 5) {
            val on = !searching && invCat == k
            val y0 = tabY(k)
            pxPanel(c, tabL, y0, tabR + (if (on) 4 * d else 0f), y0 + tabH, if (on) Cz.PANEL2 else Cz.PANEL3, on, false, 255, true)
            val icx = tabL + 26 * d; val icy = y0 + tabH / 2f
            if (tabIcon[k] < 0) pxArt(c, ART_STAR, icx, icy, max(2f, (tabH * 0.5f / 11f).roundToInt().toFloat()), if (on) 255 else 200) else icon(c, icx, icy, tabH * 0.92f, tabIcon[k])
            rc(c, sn(tabL) + 2 * u, sn(y0 + tabH * 0.25f), sn(tabL) + 3 * u, sn(y0 + tabH * 0.75f), if (on) catColor(k) else al(catColor(k), 130))
        }
        // bandeja dos itens
        val list = shown()
        val gl0 = sn(gx0); val gt0 = sn(bodyTop); val gr0 = sn(gx1); val gb0 = sn(bodyBot)
        outer(c, gl0, gt0, gr0, gb0, Cz.INK)
        outer(c, gl0 + u, gt0 + u, gr0 - u, gb0 - u, Color.rgb(226, 188, 136))
        rc(c, gl0 + 2 * u, gt0 + 2 * u, gr0 - 2 * u, gb0 - 2 * u, Color.rgb(251, 240, 220))
        dots(c, gl0 + 2 * u, sn(gTop), gr0 - 2 * u, gb0 - 2 * u, Color.argb(70, 226, 188, 136))
        rc(c, gl0 + 2 * u, gt0 + 2 * u, gr0 - 2 * u, sn(gTop) - u, Color.argb(90, 226, 188, 136))
        rc(c, gl0 + 2 * u, sn(gTop) - u, gr0 - 2 * u, sn(gTop), Color.argb(120, 196, 128, 48))
        // título da categoria e contador, em etiquetinhas
        val stripH = gTop - bodyTop
        var hs = tsc(2f); while (hs > 1 && 11 * hs + 4 * u > stripH - 2 * d) hs--
        val phh = 11 * hs + 4 * u; val pTop = bodyTop + (stripH - phh) / 2f
        val title = if (searching) "Resultados" else Items.CATS[invCat]
        val tcol = if (searching) Cz.GOLD_D else catColor(invCat)
        val tw = PixFont.width(title, hs) + 14 * d
        pxPill(c, gx0 + 8 * d, pTop, gx0 + 8 * d + tw, pTop + phh, tcol)
        pxText(c, title, gx0 + 8 * d + tw / 2f, pTop + 2 * u + 2 * hs, hs, Color.WHITE, 1, true, false, shadeC(tcol, 0.55f))
        val cnt = "${list.size} ${if (list.size == 1) "item" else "itens"}"
        val cw = PixFont.width(cnt, hs) + 14 * d
        pxPill(c, gx1 - 8 * d - cw, pTop, gx1 - 8 * d, pTop + phh, Cz.PANEL3)
        pxText(c, cnt, gx1 - 8 * d - cw / 2f, pTop + 2 * u + 2 * hs, hs, Cz.TXT, 1, false)
        c.save(); c.clipRect(gx0 + u, gTop, gx1 - u, gBot - u)
        for ((i, itm) in list.withIndex()) {
            val x = cellX(i); val y = cellY(i); val s = cs - 6 * d
            if (y > gBot || y + s < gTop) continue
            val picked = itm == armItem
            val on = itm == invSel
            if (picked) { val g = (130 + 100 * sin(clock * 6f)).toInt().coerceIn(0, 255); frame(c, sn(x) - u, sn(y) - u, sn(x + s) + u, sn(y + s) + u, u, Color.argb(g, 255, 214, 90)) }
            else if (itm == game.cur()) { val g = (110 + 80 * sin(clock * 4f)).toInt().coerceIn(0, 255); frame(c, sn(x) - u, sn(y) - u, sn(x + s) + u, sn(y + s) + u, u, Color.argb(g, 255, 214, 90)) }
            pxSlot(c, x, y, x + s, y + s, if (picked || on) 1 else 0)
            val bob = if (picked) sin(clock * 6f) * 1.5f * d else 0f
            icon(c, x + s / 2, y + s / 2 + bob, s, itm)
            for (hsl in 0 until 8) if (game.hotbar[hsl] == itm) {   // plaquinha com o número do slot onde o item está
                val bs3 = max(1, tsc(1f)); val hf = max(1f, (u / 2f).roundToInt().toFloat())
                val bw2 = 5 * bs3 + 4 * hf; val bh2 = 7 * bs3 + 4 * hf
                val bx0 = sn(x + s - bw2 - 2 * d); val by0 = sn(y + s - bh2 - 2 * d)
                rc(c, bx0, by0, bx0 + bw2, by0 + bh2, Cz.INK)
                rc(c, bx0 + hf, by0 + hf, bx0 + bw2 - hf, by0 + bh2 - hf, Cz.GREEN)
                pxText(c, "${hsl + 1}", bx0 + 2 * hf, by0 + 2 * hf, bs3, Color.WHITE, 0, false); break
            }
        }
        if (list.isEmpty()) {
            val my = (gTop + gBot) / 2f
            handArt(c, (gx0 + gx1) / 2f, my - 36 * d, 40 * d, 120)
            pxText(c, "Nenhum item encontrado", (gx0 + gx1) / 2f, my - 3 * d, tsc(1.6f), Cz.TXT, 1, false)
            pxText(c, "Tente outro nome ou categoria", (gx0 + gx1) / 2f, my + 18 * d, tsc(1.2f), Cz.TXT_SOFT, 1, false)
        }
        c.restore()
        if (maxScroll > 0f) {   // barra de rolagem
            val tl = sn(gx1 - 10 * d); val tr = tl + 3 * u; val trk = gBot - gTop - 2 * d
            rc(c, tl, gTop + d, tr, gTop + d + trk, Color.argb(60, 120, 70, 40))
            val th = max(18 * d, trk * (gBot - gTop) / (gBot - gTop + maxScroll)); val ty = gTop + d + (trk - th) * (invScroll / maxScroll)
            rc(c, tl, ty, tr, ty + th, Cz.GOLD_D)
        }
        // painel de informações do item
        val sel = if (invSel > 0) invSel else game.cur()
        if (infoW > 0f) {
            pxPanel(c, infoL, bodyTop, infoR, bodyBot, Cz.PANEL2, false, false, 255, true)
            val cx = (infoL + infoR) / 2f
            val bs2 = min(64 * d, (bodyBot - bodyTop) * 0.32f); val bt = bodyTop + 12 * d
            pxSlot(c, cx - bs2 / 2, bt, cx + bs2 / 2, bt + bs2, 1)
            val bob = sin(clock * 2f) * 1.5f * d
            if (sel > 0) icon(c, cx, bt + bs2 / 2f + bob, bs2 * 0.9f, sel) else handArt(c, cx, bt + bs2 / 2f + bob, bs2 * 0.9f, 255)
            var y = bt + bs2 + 12 * d
            val maxw = (infoW - 22 * d).toInt()
            val nm = Items.name(sel); var ns = tsc(1.9f); while (ns > 1 && PixFont.width(nm, ns) > maxw) ns--
            pxText(c, nm, cx, y, ns, Color.WHITE, 1, false, true); y += 7 * ns + 10 * d
            val cat = if (sel > 0) Items.cat(sel) else -1
            val lb = if (sel > 0) Items.CATS[cat] else "Livre"
            val pc = if (sel > 0) catColor(cat) else Cz.TEAL
            var cs2 = tsc(1.5f); while (cs2 > 1 && PixFont.width(lb, cs2) + 14 * d > maxw) cs2--
            val pw = PixFont.width(lb, cs2) + 14 * d; val ph = 11 * cs2 + 4 * u
            pxPill(c, cx - pw / 2f, y, cx + pw / 2f, y + ph, pc)
            pxText(c, lb, cx, y + 2 * u + 2 * cs2, cs2, Color.WHITE, 1, true, false, shadeC(pc, 0.55f))
            y += ph + 8 * d
            val tool = cat == 1
            val hsc = tsc(1f)
            val hint = if (armItem != 0) "Agora toque num slot da barra" else "Toque pra escolher ou arraste pra barra"
            val hintLines = wrap(hint, hsc, maxw)
            val hintY = bodyBot - 10 * d - hintLines.size * 9 * hsc
            val statsTop = hintY - 8 * d - 3 * 12 * d
            val limit = (if (tool) statsTop else hintY) - 4 * d
            val dtxt = if (sel > 0) Items.desc(sel) else "Sem nada na mão: o personagem fica de mãos livres."
            var dsc = tsc(1.4f); var lines = wrap(dtxt, dsc, maxw)
            while (dsc > 1 && lines.size * 10 * dsc + 2 * dsc > limit - y) { dsc--; lines = wrap(dtxt, dsc, maxw) }
            var ly = y + 2 * dsc
            for (ln in lines) { if (ly + 7 * dsc > limit) break; pxText(c, ln, infoL + 11 * d, ly, dsc, Cz.TXT, 0, false); ly += 10 * dsc }
            if (tool) {
                val sx0 = infoL + 11 * d; val sw = infoW - 22 * d
                pips(c, "Dano", sx0, statsTop, sw, Items.damage(sel).coerceIn(1, 5), Cz.RED)
                pips(c, "Alcance", sx0, statsTop + 12 * d, sw, Math.round((Items.reach(sel) - 2f) * 3.3f).coerceIn(1, 5), Cz.SKY)
                pips(c, "Rapidez", sx0, statsTop + 24 * d, sw, Math.round(5f - (Items.cooldown(sel) - 0.25f) / 0.4f * 4f).coerceIn(1, 5), Cz.GREEN)
            }
            val hc = if (armItem != 0) Color.rgb(214, 100, 40) else Cz.TXT_SOFT
            for ((li, hl) in hintLines.withIndex()) pxText(c, hl, cx, hintY + li * 9 * hsc, hsc, hc, 1, false)
        }
        // enfeites: brilhinhos piscando nos cantos do painel
        for (k in 0 until 4) {
            val ax = if (k % 2 == 0) pl0 else pr0; val ay = if (k < 2) pt1 else pb0
            pxArt(c, ART_SPARK, ax, ay, u, (170 + 85 * sin(clock * 3f + k * 1.7f)).toInt().coerceIn(0, 255))
        }
        // dica da barra de itens
        val hintTxt = if (armItem != 0) "Agora toque no slot onde quer colocar o item" else "Toque num slot para escolher · arraste para trocar · arraste pra cima para esvaziar"
        var hz = max(1, tsc(1f)); while (hz > 1 && PixFont.width(hintTxt, hz) > w * 0.96f) hz--
        val hcol = if (armItem != 0) Color.rgb(255, (214 + 40 * sin(clock * 6f)).toInt().coerceIn(0, 255), 120) else Color.WHITE
        pxText(c, hintTxt, w / 2, hb - 5 * u - 7 * hz - 6 * d, hz, hcol, 1, false, true)
        // alvo do arraste
        if (ptrId >= 0 && ptrDrag && ptrKind != 4) {
            val s = dropSlot(ptrX, ptrY)
            if (s >= 0) {
                val x0 = hbLeft() + s * (slot + gap); val g = (150 + 100 * sin(clock * 8f)).toInt().coerceIn(0, 255)
                frame(c, sn(x0) - 2 * u, sn(hbTop()) - 2 * u, sn(x0 + slot) + 2 * u, sn(hbTop() + slot) + 2 * u, u, Color.argb(g, 255, 236, 140))
            }
            if (ptrKind == 2 && s < 0 && ptrY < pb() - 8 * d && ptrItem != 0)
                pxText(c, "Solte para esvaziar", ptrX, ptrY - 28 * d - 46 * d, tsc(1.3f), Color.WHITE, 1, false, true)
            if (ptrItem != 0) icon(c, ptrX, ptrY - 28 * d, 58 * d, ptrItem)
        }
    }
}
