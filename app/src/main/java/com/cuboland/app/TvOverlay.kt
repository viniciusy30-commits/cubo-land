package com.cuboland.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.*

/** Botão com ícone desenhado em código (não depende de fonte de símbolos do aparelho). */
class TvIconBtn(ctx: Context, var kind: Int) : View(ctx) {
    companion object { const val BACK = 0; const val RELOAD = 1; const val HOME = 2; const val FULL = 3; const val WINDOW = 4; const val CLOSE = 5; const val GRIP = 6 }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND; color = Color.WHITE }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
    private val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val rect = RectF()

    override fun setPressed(pressed: Boolean) { super.setPressed(pressed); invalidate() }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        bg.color = if (kind == CLOSE) { if (isPressed) Color.rgb(222, 96, 96) else Color.rgb(182, 62, 66) }
                   else { if (isPressed) Color.rgb(96, 104, 142) else Color.rgb(54, 58, 78) }
        c.drawRoundRect(1f, 1f, w - 1f, h - 1f, h * 0.28f, h * 0.28f, bg)
        val cx = w / 2f; val cy = h / 2f; val r = min(w, h) * 0.24f
        line.strokeWidth = max(2f, min(w, h) * 0.085f)
        when (kind) {
            BACK -> {
                c.drawLine(cx - r, cy, cx + r, cy, line)
                c.drawLine(cx - r, cy, cx - r + r * 0.85f, cy - r * 0.85f, line)
                c.drawLine(cx - r, cy, cx - r + r * 0.85f, cy + r * 0.85f, line)
            }
            RELOAD -> {
                rect.set(cx - r, cy - r, cx + r, cy + r)
                c.drawArc(rect, 50f, 280f, false, line)
                val th = Math.toRadians(330.0)
                val ex = cx + r * cos(th).toFloat(); val ey = cy + r * sin(th).toFloat()
                val nx = cos(th).toFloat(); val ny = sin(th).toFloat()     // radial
                val tx = -ny; val ty = nx                                   // tangente (sentido horário)
                path.reset()
                path.moveTo(ex + nx * r * 0.55f, ey + ny * r * 0.55f)
                path.lineTo(ex - nx * r * 0.55f, ey - ny * r * 0.55f)
                path.lineTo(ex + tx * r * 0.7f, ey + ty * r * 0.7f)
                path.close()
                c.drawPath(path, fill)
            }
            HOME -> {
                path.reset()
                path.moveTo(cx - r * 1.1f, cy - r * 0.05f); path.lineTo(cx, cy - r * 1.05f); path.lineTo(cx + r * 1.1f, cy - r * 0.05f)
                c.drawPath(path, line)
                rect.set(cx - r * 0.75f, cy - r * 0.05f, cx + r * 0.75f, cy + r * 0.95f)
                c.drawRect(rect, line)
            }
            FULL -> for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
                val px = cx + sx * r; val py = cy + sy * r
                c.drawLine(px, py, px, py - sy * r * 0.65f, line); c.drawLine(px, py, px - sx * r * 0.65f, py, line)
            }
            WINDOW -> for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
                val px = cx + sx * r * 0.35f; val py = cy + sy * r * 0.35f
                c.drawLine(px, py, px + sx * r * 0.65f, py, line); c.drawLine(px, py, px, py + sy * r * 0.65f, line)
            }
            CLOSE -> { c.drawLine(cx - r, cy - r, cx + r, cy + r, line); c.drawLine(cx - r, cy + r, cx + r, cy - r, line) }
            GRIP -> for (k in -1..1) c.drawLine(cx - r, cy + k * r * 0.62f, cx + r, cy + k * r * 0.62f, line)
        }
    }
}

/**
 * A janela da TV: um navegador (WebView, o motor do Chrome) numa moldura de TV por cima do jogo.
 * Modo janela: o jogo continua rodando atrás, dá pra arrastar pela barra. Modo tela cheia: o jogo pausa.
 * O tamanho da janela acompanha o tamanho da TV no mundo.
 */
class TvOverlay(private val act: Activity, private val root: FrameLayout, private val game: Game,
                private val tx: Int, private val ty: Int, private val tz: Int, private val onClosed: () -> Unit) {
    companion object {
        private const val HOME_BASE = "https://tv.local/"
        private val FRAC = floatArrayOf(0.40f, 0.52f, 0.64f, 0.78f)   // largura da janela por tamanho da TV
        private const val HOME = """<!doctype html><html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<style>
html,body{margin:0;height:100%;background:#12141f;color:#fff;font-family:sans-serif}
body{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:14px}
h1{margin:0;font-size:20px;letter-spacing:3px;color:#ffd463}
form{width:80%;max-width:420px}
input{width:100%;box-sizing:border-box;padding:10px 14px;border-radius:12px;border:2px solid #3b4160;background:#1d2033;color:#fff;font-size:16px}
.g{display:flex;flex-wrap:wrap;gap:10px;justify-content:center;width:92%}
a{flex:1 1 120px;max-width:170px;padding:14px 8px;border-radius:14px;text-align:center;text-decoration:none;color:#fff;font-weight:bold;font-size:15px}
</style></head><body>
<h1>TV CUBO</h1>
<form action="https://www.google.com/search" method="get"><input name="q" placeholder="Pesquisar no Google" autocomplete="off"></form>
<div class="g">
<a style="background:#e52d27" href="https://m.youtube.com/">YouTube</a>
<a style="background:#4285f4" href="https://www.google.com/">Google</a>
<a style="background:#9146ff" href="https://m.twitch.tv/">Twitch</a>
<a style="background:#555b73" href="https://pt.m.wikipedia.org/">Wikipédia</a>
</div></body></html>"""
    }

    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val BEZ = act.dp(6)
    private val BAR = act.dp(34)
    private val GAP = act.dp(4)
    private var full = false
    private var closed = false
    private var reasons = 0
    private var pausedBefore = false
    private var dragDx = 0f; private var dragDy = 0f
    private var custom: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var customHolder: FrameLayout? = null

    private val panel = LinearLayout(act)
    private val urlField = EditText(act)
    private lateinit var web: WebView
    private lateinit var fsBtn: TvIconBtn

    init {
        build()
        root.addView(panel)
        relayout()
        loadHome()
    }

    // ---------------------------------------------------------------- montagem
    private fun bezelBg() = GradientDrawable().apply { setColor(Color.rgb(24, 25, 34)); cornerRadius = act.dp(10).toFloat(); setStroke(act.dp(2), Color.rgb(96, 100, 128)) }

    @SuppressLint("ClickableViewAccessibility")
    private fun build() {
        web = buildWeb()
        panel.orientation = LinearLayout.VERTICAL
        panel.isClickable = true            // não deixa o toque na moldura "vazar" pro jogo
        panel.setPadding(BEZ, BEZ, BEZ, BEZ)
        panel.background = bezelBg()
        panel.elevation = act.dp(8).toFloat()

        val bar = LinearLayout(act); bar.orientation = LinearLayout.HORIZONTAL; bar.gravity = Gravity.CENTER_VERTICAL
        fun add(b: TvIconBtn, wdp: Int, click: (() -> Unit)?) {
            if (click != null) b.setOnClickListener { click() }
            bar.addView(b, LinearLayout.LayoutParams(act.dp(wdp), act.dp(30)).apply { rightMargin = act.dp(3) })
        }
        val grip = TvIconBtn(act, TvIconBtn.GRIP)
        grip.setOnTouchListener { v, e ->
            if (!full) when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { dragDx = e.rawX - panel.translationX; dragDy = e.rawY - panel.translationY; v.isPressed = true }
                MotionEvent.ACTION_MOVE -> {
                    val lp = panel.layoutParams
                    val baseL = (root.width - lp.width) / 2f; val baseT = act.dp(6).toFloat()
                    panel.translationX = clampF(e.rawX - dragDx, -baseL, root.width - lp.width - baseL)
                    panel.translationY = clampF(e.rawY - dragDy, -baseT, root.height - lp.height - baseT)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.isPressed = false
            }
            true
        }
        add(grip, 26, null)
        add(TvIconBtn(act, TvIconBtn.BACK), 34) { if (web.canGoBack()) web.goBack() }
        add(TvIconBtn(act, TvIconBtn.RELOAD), 34) { web.reload() }
        add(TvIconBtn(act, TvIconBtn.HOME), 34) { loadHome() }

        urlField.setSingleLine(true)
        urlField.textSize = 12f
        urlField.setTextColor(Color.WHITE)
        urlField.setHintTextColor(Color.argb(140, 255, 255, 255))
        urlField.hint = "Pesquisar ou digitar endereço"
        urlField.gravity = Gravity.CENTER_VERTICAL
        urlField.background = GradientDrawable().apply { setColor(Color.rgb(16, 17, 26)); cornerRadius = act.dp(8).toFloat(); setStroke(act.dp(1), Color.rgb(70, 76, 104)) }
        urlField.setPadding(act.dp(8), 0, act.dp(8), 0)
        urlField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        urlField.imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        urlField.setSelectAllOnFocus(true)
        urlField.setOnEditorActionListener { _, a, _ -> if (a == EditorInfo.IME_ACTION_GO) { go(urlField.text.toString()); true } else false }
        bar.addView(urlField, LinearLayout.LayoutParams(0, act.dp(30), 1f).apply { rightMargin = act.dp(3) })

        fsBtn = TvIconBtn(act, TvIconBtn.FULL)
        add(fsBtn, 34) { setFull(!full) }
        add(TvIconBtn(act, TvIconBtn.CLOSE), 34) { close() }

        val screen = FrameLayout(act); screen.setBackgroundColor(Color.BLACK)
        screen.addView(web, FrameLayout.LayoutParams(MATCH, MATCH))
        panel.addView(bar, LinearLayout.LayoutParams(MATCH, BAR))
        panel.addView(screen, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = GAP })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun buildWeb(): WebView {
        val w = WebView(act)
        val st = w.settings
        st.javaScriptEnabled = true
        st.domStorageEnabled = true
        st.mediaPlaybackRequiresUserGesture = false
        st.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        st.useWideViewPort = true
        st.loadWithOverviewMode = true
        st.setSupportZoom(true); st.builtInZoomControls = true; st.displayZoomControls = false
        w.setBackgroundColor(Color.BLACK)
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
        w.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val u = request?.url?.toString() ?: return false
                // só navega dentro da TV; esquemas de outros apps (intent://, youtube://...) são ignorados
                return !(u.startsWith("http://") || u.startsWith("https://") || u.startsWith("about:") || u.startsWith("data:"))
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { showUrl(url) }
            override fun onPageFinished(view: WebView?, url: String?) { showUrl(url) }
        }
        w.webChromeClient = object : WebChromeClient() {
            // botão de tela cheia do próprio vídeo (YouTube etc.)
            override fun onShowCustomView(view: View?, callback: WebChromeClient.CustomViewCallback?) {
                if (view == null) return
                if (custom != null) { callback?.onCustomViewHidden(); return }
                custom = view; customCb = callback
                val h = FrameLayout(act); h.setBackgroundColor(Color.BLACK); h.isClickable = true
                h.addView(view, FrameLayout.LayoutParams(MATCH, MATCH))
                customHolder = h
                root.addView(h, FrameLayout.LayoutParams(MATCH, MATCH))
                holdPause()
            }
            override fun onHideCustomView() { hideCustom() }
        }
        return w
    }

    // ---------------------------------------------------------------- navegação
    private fun loadHome() { web.loadDataWithBaseURL(HOME_BASE, HOME, "text/html", "UTF-8", null) }

    private fun showUrl(url: String?) {
        if (closed || urlField.hasFocus()) return
        urlField.setText(if (url == null || url.startsWith(HOME_BASE) || url.startsWith("about:") || url.startsWith("data:")) "" else url)
    }

    private fun go(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) return
        val url = when {
            t.startsWith("http://") || t.startsWith("https://") -> t
            !t.contains(' ') && t.contains('.') -> "https://" + t
            else -> "https://www.google.com/search?q=" + java.net.URLEncoder.encode(t, "UTF-8")
        }
        web.loadUrl(url)
        hideKeyboard()
    }

    private fun hideKeyboard() {
        val im = act.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        im.hideSoftInputFromWindow(urlField.windowToken, 0)
        urlField.clearFocus()
        web.requestFocus()
    }

    // ---------------------------------------------------------------- tamanho / tela cheia
    private fun clampF(v: Float, lo: Float, hi: Float) = min(max(v, lo), max(lo, hi))

    /** janela do tamanho da TV (16:9, sem passar da tela); chamado de novo quando o tamanho da TV muda */
    fun relayout() {
        if (closed || full) return
        val rw = root.width; val rh = root.height
        if (rw <= 0 || rh <= 0) { root.post { relayout() }; return }
        val lv = game.tvLevel(tx, ty, tz).coerceIn(0, FRAC.size - 1)
        var pw = min(max(act.dp(360), (rw * FRAC[lv]).toInt()), rw - act.dp(12))
        var sh = ((pw - 2 * BEZ) * 9f / 16f).toInt()
        val maxH = (rh * 0.94f).toInt()
        val chrome = BAR + GAP + 2 * BEZ
        if (sh + chrome > maxH) { sh = maxH - chrome; pw = (sh * 16f / 9f).toInt() + 2 * BEZ }
        val lp = FrameLayout.LayoutParams(pw, sh + chrome, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        lp.topMargin = act.dp(6)
        panel.layoutParams = lp
        panel.translationX = 0f; panel.translationY = 0f
    }

    private fun setFull(on: Boolean) {
        if (closed || full == on) return
        full = on
        panel.translationX = 0f; panel.translationY = 0f
        if (on) {
            holdPause()
            panel.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH)
            panel.setPadding(0, 0, 0, 0)
            panel.background = ColorDrawable(Color.BLACK)
            fsBtn.kind = TvIconBtn.WINDOW
        } else {
            releasePause()
            panel.setPadding(BEZ, BEZ, BEZ, BEZ)
            panel.background = bezelBg()
            fsBtn.kind = TvIconBtn.FULL
            relayout()
        }
        fsBtn.invalidate()
    }

    // ---------------------------------------------------------------- pausa do jogo (tela cheia / vídeo em tela cheia)
    private fun holdPause() { if (reasons++ == 0) pausedBefore = game.paused; game.paused = true }
    private fun releasePause() { if (reasons > 0 && --reasons == 0) game.paused = pausedBefore }

    private fun hideCustom() {
        val v = custom ?: return
        custom = null
        val cb = customCb; customCb = null
        val h = customHolder; customHolder = null
        if (h != null) { h.removeView(v); root.removeView(h) }
        releasePause()
        cb?.onCustomViewHidden()
    }

    // ---------------------------------------------------------------- ciclo de vida
    /** botão voltar do celular: sai do vídeo em tela cheia, depois da tela cheia, depois desliga a TV */
    fun onBack() {
        when {
            custom != null -> hideCustom()
            full -> setFull(false)
            else -> close()
        }
    }

    fun onPause() { if (closed) return; web.onPause(); web.pauseTimers() }
    fun onResume() { if (closed) return; web.resumeTimers(); web.onResume() }

    fun close() {
        if (closed) return
        hideCustom()
        closed = true
        while (reasons > 0) releasePause()
        try {
            web.stopLoading()
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        } catch (_: Exception) {}
        root.removeView(panel)
        onClosed()
    }
}
