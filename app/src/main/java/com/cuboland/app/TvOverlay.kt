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
 * A TV do mundo: um navegador (WebView) que roda "dentro" da tela da TV.
 * - Zoom: a câmera encosta na tela da TV e o navegador real fica exatamente em cima dela (toque normal); dá pra ir pra tela cheia (a tela do celular toda).
 * - Fora do zoom: a página continua rodando; ela é copiada ~15x por segundo pra uma textura que o renderizador cola na tela da TV (no tamanho da TV, presa nela).
 */
class TvOverlay(private val act: Activity, private val root: FrameLayout, private val game: Game,
                private val tx: Int, private val ty: Int, private val tz: Int,
                private val onUi: (Boolean) -> Unit, private val onClosed: () -> Unit) {
    companion object {
        private const val HOME_BASE = "https://tv.local/"
        private const val RW = 1024; private const val RH = 675      // tamanho lógico da página (proporção da tela da TV)
        private const val BW = 512; private const val BH = 338       // tamanho da textura na TV
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
    private val BAR = act.dp(36)
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var full = false
    private var closed = false
    private var goalZoom = false       // quer estar com zoom
    private var uiShown = false        // navegador interativo + barra visíveis
    private var uiHidden = false       // HUD escondido (zoom em andamento ou ativo)
    private var reasons = 0
    private var pausedBefore = false
    private var tick = 0
    private var custom: View? = null
    private var customCb: WebChromeClient.CustomViewCallback? = null
    private var customHolder: FrameLayout? = null

    private val bar = LinearLayout(act)
    private val urlField = EditText(act)
    private lateinit var web: WebView
    private lateinit var fsBtn: TvIconBtn
    private val live = Game.TvLive(tx, ty, tz)

    init {
        web = buildWeb()
        buildBar()
        parkWeb()
        root.addView(web)
        bar.visibility = View.GONE
        root.addView(bar, FrameLayout.LayoutParams(MATCH, BAR, Gravity.TOP))
        game.tvLive = live
        loadHome()
        handler.post { loop.run() }
    }

    // ---------------------------------------------------------------- montagem
    @SuppressLint("ClickableViewAccessibility")
    private fun buildBar() {
        bar.orientation = LinearLayout.HORIZONTAL; bar.gravity = Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(Color.argb(210, 14, 15, 22))
        bar.setPadding(act.dp(6), act.dp(3), act.dp(6), act.dp(3))
        bar.isClickable = true
        fun add(b: TvIconBtn, wdp: Int, click: () -> Unit) {
            b.setOnClickListener { click() }
            bar.addView(b, LinearLayout.LayoutParams(act.dp(wdp), act.dp(30)).apply { rightMargin = act.dp(3) })
        }
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
        add(TvIconBtn(act, TvIconBtn.CLOSE), 34) { unzoom() }     // X = sai do zoom (a TV continua ligada)
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
                return !(u.startsWith("http://") || u.startsWith("https://") || u.startsWith("about:") || u.startsWith("data:"))
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) { showUrl(url) }
            override fun onPageFinished(view: WebView?, url: String?) { showUrl(url) }
        }
        w.webChromeClient = object : WebChromeClient() {
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

    // ---------------------------------------------------------------- posições do navegador
    /** fora do zoom: do tamanho da página, fora da tela do celular (continua rodando; a imagem vai pra tela da TV no mundo) */
    private fun parkWeb() {
        web.layoutParams = FrameLayout.LayoutParams(RW, RH, Gravity.TOP or Gravity.START)
        web.pivotX = 0f; web.pivotY = 0f; web.scaleX = 1f; web.scaleY = 1f
        web.translationX = -(RW + 300f); web.translationY = 0f
    }

    /** zoom: o navegador real fica exatamente em cima da tela da TV que a câmera enquadrou */
    private fun placeZoom() {
        val w = root.width.toFloat(); val h = root.height.toFloat()
        if (w <= 0f || h <= 0f) return
        game.tvAspect = w / h
        val p = game.tvZoomPlan() ?: return
        val pw = p[5] * w; val ph = p[6] * h
        web.layoutParams = FrameLayout.LayoutParams(RW, RH, Gravity.TOP or Gravity.START)
        web.pivotX = 0f; web.pivotY = 0f
        web.scaleX = pw / RW; web.scaleY = ph / RH
        web.translationX = w / 2f - pw / 2f; web.translationY = h * 0.55f - ph / 2f
    }

    private fun placeFull() {
        web.layoutParams = FrameLayout.LayoutParams(MATCH, MATCH).apply { topMargin = BAR }
        web.pivotX = 0f; web.pivotY = 0f; web.scaleX = 1f; web.scaleY = 1f
        web.translationX = 0f; web.translationY = 0f
    }

    fun relayout() { if (!closed && uiShown && !full) placeZoom() }

    // ---------------------------------------------------------------- zoom / tela cheia
    fun zoom() {
        if (closed || goalZoom) return
        goalZoom = true
        game.tvAspect = root.width.toFloat().coerceAtLeast(1f) / root.height.toFloat().coerceAtLeast(1f)
        if (!uiHidden) { uiHidden = true; onUi(true) }
        game.tvZoomGoal = 1f
    }

    fun unzoom() {
        if (closed || !goalZoom) return
        if (full) setFull(false)
        hideKeyboard()
        goalZoom = false
        capture()                       // último quadro: a TV mantém o que estava na tela
        uiShown = false
        bar.visibility = View.GONE
        parkWeb()
        game.tvZoomGoal = 0f
    }

    private fun showZoomUi() {
        uiShown = true
        placeZoom()
        bar.visibility = View.VISIBLE
        web.requestFocus()
    }

    private fun setFull(on: Boolean) {
        if (closed || full == on) return
        full = on
        if (on) {
            holdPause(); placeFull(); fsBtn.kind = TvIconBtn.WINDOW
        } else {
            releasePause(); placeZoom(); fsBtn.kind = TvIconBtn.FULL
        }
        fsBtn.invalidate()
    }

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

    // ---------------------------------------------------------------- imagem da página -> textura da TV
    private fun capture() {
        if (closed || live.ready) return
        try {
            var b = live.bmp
            if (b == null || b.isRecycled) { b = Bitmap.createBitmap(BW, BH, Bitmap.Config.ARGB_8888); live.bmp = b }
            val cv = Canvas(b)
            cv.drawColor(Color.BLACK)
            cv.scale(BW / RW.toFloat(), BH / RH.toFloat())
            web.draw(cv)
            live.ready = true
        } catch (_: Exception) {}
    }

    private val loop = object : Runnable {
        override fun run() {
            if (closed) return
            // o bloco da TV sumiu (quebrado): desliga
            if (!Furniture.isTv(game.world.get(tx, ty, tz))) { close(); return }
            tick++
            val t = game.tvZoomT
            if (goalZoom && !uiShown && t >= 0.999f) showZoomUi()
            if (!goalZoom && uiHidden && t <= 0.001f) { uiHidden = false; onUi(false) }
            if (!uiShown && !full && tick % 2 == 0 && live.seen) capture()
            handler.postDelayed(this, 33)
        }
    }

    // ---------------------------------------------------------------- ciclo de vida
    /** botão voltar: sai do vídeo em tela cheia, depois da tela cheia, depois do zoom; devolve false se não tinha nada a fazer */
    fun onBack(): Boolean {
        when {
            custom != null -> hideCustom()
            full -> setFull(false)
            goalZoom -> unzoom()
            else -> return false
        }
        return true
    }

    fun onPause() { if (closed) return; web.onPause(); web.pauseTimers() }
    fun onResume() { if (closed) return; web.resumeTimers(); web.onResume() }

    fun close() {
        if (closed) return
        hideCustom()
        if (full) { full = false; releasePause() }
        closed = true
        handler.removeCallbacksAndMessages(null)
        while (reasons > 0) releasePause()
        game.tvLive = null
        game.tvZoomGoal = 0f; game.tvZoomT = 0f
        try {
            web.stopLoading()
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        } catch (_: Exception) {}
        root.removeView(bar)
        if (uiHidden) { uiHidden = false; onUi(false) }
        onClosed()
    }
}
