package com.cuboland.app

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Painel da galeria de blocos: ao entrar no mapa abre sozinho, o jogador escolhe a categoria (e, se quiser, só uma seção dela)
 * e o mapa é regerado só com isso. O botão CATEGORIAS, no alto da tela, abre o painel de novo a qualquer hora.
 * onPick(categoria, seção, extra): categoria/seção = índices de World.galleryMenu (seção -1 = todas); extra = World.GAL_*.
 */
class GalleryPanel(
    private val act: Activity,
    private val menu: List<Pair<World.GalCat, List<World.GalSec>>>,
    private val onOpen: () -> Unit,
    private val onClose: () -> Unit,
    private val onPick: (cat: Int, sec: Int, extra: Int) -> Unit
) {
    val view = FrameLayout(act)
    val fab: Button
    var isOpen = false; private set
    private var busy = false

    private val title = TextView(act)
    private val sub = TextView(act)
    private val backBtn: Button
    private val closeBtn: Button
    private val body = LinearLayout(act)
    private val gold = Color.rgb(255, 214, 120)

    private val catColors = intArrayOf(Color.rgb(90, 200, 90), Color.rgb(224, 84, 72), Color.rgb(240, 120, 180), Color.rgb(84, 144, 232), Color.rgb(150, 150, 160))

    init {
        view.visibility = View.GONE
        view.isClickable = true; view.isFocusable = true
        view.background = act.dirtBackground().apply { alpha = 240 }

        val col = LinearLayout(act); col.orientation = LinearLayout.VERTICAL
        val wpx = act.resources.displayMetrics.widthPixels
        val colW = minOf(wpx - act.dp(24), act.dp(760))
        view.addView(col, FrameLayout.LayoutParams(colW, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL).apply { topMargin = act.dp(8); bottomMargin = act.dp(8) })

        // cabeçalho: voltar | título | fechar
        val head = LinearLayout(act); head.orientation = LinearLayout.HORIZONTAL; head.gravity = Gravity.CENTER_VERTICAL
        backBtn = small("← Voltar", Color.rgb(150, 150, 160)) { }
        closeBtn = small("Fechar ✕", Color.rgb(224, 84, 72)) { if (!busy) close() }
        title.setTextColor(gold); title.textSize = 19f; title.typeface = act.mcFont(); title.gravity = Gravity.CENTER
        title.setShadowLayer(0.5f, act.dp(2).toFloat(), act.dp(2).toFloat(), Color.rgb(40, 28, 20))
        head.addView(backBtn, LinearLayout.LayoutParams(act.dp(104), ViewGroup.LayoutParams.WRAP_CONTENT))
        head.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(closeBtn, LinearLayout.LayoutParams(act.dp(104), ViewGroup.LayoutParams.WRAP_CONTENT))
        col.addView(head)
        sub.setTextColor(Color.rgb(236, 226, 206)); sub.textSize = 13f; sub.typeface = Typeface.MONOSPACE; sub.gravity = Gravity.CENTER
        sub.setPadding(0, act.dp(4), 0, act.dp(6))
        col.addView(sub)

        body.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(act); sv.addView(body); sv.isVerticalScrollBarEnabled = false
        col.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        fab = small("CATEGORIAS", Color.rgb(255, 170, 60)) { open() }
        fab.textSize = 13f
    }

    /** posição do botão flutuante (centro, no alto) */
    fun fabParams() = FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = act.dp(6) }

    private fun small(text: String, color: Int, click: () -> Unit): Button {
        val b = act.btn(text, color, click)
        b.textSize = 13f; b.minHeight = act.dp(40); b.minimumHeight = act.dp(40); b.setPadding(act.dp(8), act.dp(4), act.dp(8), act.dp(4))
        return b
    }

    fun open() {
        if (isOpen) return
        isOpen = true; busy = false
        view.visibility = View.VISIBLE; fab.visibility = View.GONE
        onOpen()
        showCats()
    }

    /** botão voltar do aparelho: fecha o painel, menos enquanto monta o mapa */
    fun onBack() { if (!busy) close() }

    fun close() {
        if (!isOpen) return
        isOpen = false; busy = false
        view.visibility = View.GONE; fab.visibility = View.VISIBLE
        onClose()
    }

    private fun addRow(items: List<Button>, cols: Int) {
        var i = 0
        while (i < items.size) {
            val row = LinearLayout(act); row.orientation = LinearLayout.HORIZONTAL
            for (k in 0 until cols) {
                val b = items.getOrNull(i + k)
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); lp.setMargins(act.dp(4), act.dp(4), act.dp(4), act.dp(4))
                row.addView(b ?: View(act), lp)
            }
            body.addView(row)
            i += cols
        }
    }

    private fun big(text: String, color: Int, click: () -> Unit): Button {
        val b = act.btn(text, color, click)
        b.textSize = 15f; b.minHeight = act.dp(64); b.minimumHeight = act.dp(64)
        return b
    }

    private fun pick(cat: Int, sec: Int, extra: Int, what: String) {
        if (busy) return
        busy = true
        body.removeAllViews(); backBtn.visibility = View.INVISIBLE; closeBtn.visibility = View.INVISIBLE
        title.text = "Montando..."; sub.text = what
        val t = TextView(act); t.text = "Carregando os blocos no mapa, só um instante"; t.setTextColor(Color.WHITE); t.textSize = 15f; t.typeface = act.mcFont(); t.gravity = Gravity.CENTER
        t.setPadding(0, act.dp(40), 0, 0)
        body.addView(t)
        onPick(cat, sec, extra)
    }

    /** passo 1: categorias */
    private fun showCats() {
        busy = false
        body.removeAllViews(); backBtn.visibility = View.INVISIBLE; closeBtn.visibility = View.VISIBLE
        title.text = "GALERIA DE BLOCOS"; sub.text = "1. categoria   2. tipo de bloco   3. entre na sala e passeie"
        val bs = ArrayList<Button>()
        for ((i, p) in menu.withIndex()) {
            val c = p.first
            bs.add(big("${c.name}\n${c.ids.size} blocos", catColors[i % catColors.size]) { showSecs(i) })
        }
        addRow(bs, 2)
        val sp = TextView(act); sp.text = "VITRINES"; sp.setTextColor(gold); sp.textSize = 13f; sp.typeface = act.mcFont(); sp.setPadding(act.dp(6), act.dp(12), 0, act.dp(2))
        body.addView(sp)
        val ex = ArrayList<Button>()
        ex.add(big("MANSÃO\ncompleta", Color.rgb(150, 150, 160)) { pick(-1, -1, World.GAL_HOUSE, "Mansão") })
        ex.add(big("ÁRVORES", Color.rgb(90, 200, 90)) { pick(-1, -1, World.GAL_TREES, "Galeria de árvores") })
        ex.add(big("VEÍCULOS\nteste", Color.rgb(212, 20, 28)) { pick(-1, -1, World.GAL_CAR, "Teste de veículos") })
        addRow(ex, 3)
    }

    /** passo 2: seções da categoria (cada uma vira uma sala de exposição) */
    private fun showSecs(ci: Int) {
        val (cat, secs) = menu[ci]
        body.removeAllViews(); backBtn.visibility = View.VISIBLE; closeBtn.visibility = View.VISIBLE
        backBtn.setOnClickListener { showCats() }
        title.text = cat.name; sub.text = "Escolha um tipo de bloco: ele vira uma sala de exposição"
        val color = catColors[ci % catColors.size]
        val wdp = act.resources.displayMetrics.widthPixels / act.resources.displayMetrics.density
        val bs = secs.map { s ->
            val pages = (s.count + Gallery.PER_PAGE - 1) / Gallery.PER_PAGE
            val b = act.btn(s.title + "\n" + s.count + (if (pages > 1) " blocos - $pages partes" else " blocos"), color) {
                if (pages > 1) showPages(ci, s, pages) else pick(ci, s.idx, World.GAL_PAGE, cat.name + " - " + s.title)
            }
            b.textSize = 13f; b.minHeight = act.dp(56); b.minimumHeight = act.dp(56); b.setPadding(act.dp(6), act.dp(4), act.dp(6), act.dp(4))
            b
        }
        addRow(bs, if (wdp > 800) 3 else 2)
    }

    /** passo 3 (só seções grandes): escolher a parte, 24 blocos por vez */
    private fun showPages(ci: Int, s: World.GalSec, pages: Int) {
        val cat = menu[ci].first
        body.removeAllViews(); backBtn.visibility = View.VISIBLE; closeBtn.visibility = View.VISIBLE
        backBtn.setOnClickListener { showSecs(ci) }
        title.text = s.title; sub.text = "${s.count} blocos em $pages partes"
        val color = catColors[ci % catColors.size]
        val bs = (0 until pages).map { pg ->
            val a = pg * Gallery.PER_PAGE + 1; val z = minOf(s.count, (pg + 1) * Gallery.PER_PAGE)
            act.btn("PARTE ${pg + 1}\nblocos $a a $z", color) { pick(ci, s.idx, World.GAL_PAGE + pg, cat.name + " - " + s.title + " (parte ${pg + 1})") }.also {
                it.textSize = 13f; it.minHeight = act.dp(56); it.minimumHeight = act.dp(56)
            }
        }
        addRow(bs, 2)
    }
}
