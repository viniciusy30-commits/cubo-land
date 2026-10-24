package com.cuboland.app

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Painel da galeria de blocos com revisão de blocos: ao entrar no mapa abre sozinho, o jogador escolhe a categoria (e, se quiser, só uma seção dela)
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
    private val reviewPrefs = act.getSharedPreferences("block_review", Context.MODE_PRIVATE)
    private lateinit var inspector: BlockInspector
    private var preferInspector = false
    private val gold = Color.rgb(255, 214, 120)

    private val catColors = intArrayOf(Color.rgb(90, 200, 90), Color.rgb(224, 84, 72), Color.rgb(240, 120, 180), Color.rgb(84, 144, 232), Color.rgb(150, 150, 160))

    init {
        view.visibility = View.GONE
        view.isClickable = true; view.isFocusable = true
        view.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.rgb(20, 25, 34), Color.rgb(9, 11, 17))).apply { alpha = 246 }

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
        sub.setTextColor(Color.rgb(176, 184, 198)); sub.textSize = 13f; sub.gravity = Gravity.CENTER
        sub.setPadding(0, act.dp(4), 0, act.dp(6))
        col.addView(sub)

        body.orientation = LinearLayout.VERTICAL
        val sv = ScrollView(act); sv.addView(body); sv.isVerticalScrollBarEnabled = false
        col.addView(sv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        inspector = BlockInspector(act, menu,
            onBack = { showCats() },
            onClose = { close() },
            onTest = { id -> pick(-1, id, World.GAL_BLOCK, "Teste do bloco: ${B.names.getOrNull(id).orEmpty()} (#$id)") }
        )
        inspector.hide()
        // Sobrepõe a tela da galeria sem sair do jogo; lista e prévias têm rolagem própria.
        view.addView(inspector.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

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
        if (preferInspector) showInspector() else showCats()
    }

    /** botão voltar do aparelho: fecha o painel, menos enquanto monta o mapa */
    fun onBack() { if (!busy) close() }

    fun close() {
        if (!isOpen) return
        isOpen = false; busy = false
        view.visibility = View.GONE; fab.visibility = View.VISIBLE
        inspector.hide()
        onClose()
    }

    private fun rounded(fill: Int, stroke: Int, radiusDp: Int, strokeDp: Int = 2): GradientDrawable {
        val g = GradientDrawable(); g.setColor(fill); g.cornerRadius = act.dp(radiusDp).toFloat(); g.setStroke(act.dp(strokeDp), stroke); return g
    }

    private fun label(text: String, sizeSp: Float, color: Int, bold: Boolean = false, font: Boolean = true): TextView {
        val t = TextView(act); t.text = text; t.textSize = sizeSp; t.setTextColor(color)
        if (font) t.typeface = act.mcFont() else t.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        return t
    }

    /** cartão tocável: selo colorido à esquerda, título, subtítulo e seta */
    private fun card(titleT: String, subT: String, accent: Int, badge: String, click: () -> Unit): View {
        val row = LinearLayout(act); row.orientation = LinearLayout.HORIZONTAL; row.gravity = Gravity.CENTER_VERTICAL
        row.background = rounded(Color.rgb(32, 37, 46), accent, 14)
        row.setPadding(act.dp(10), act.dp(10), act.dp(12), act.dp(10))
        val b = TextView(act); b.text = badge; b.textSize = 18f; b.typeface = act.mcFont(); b.gravity = Gravity.CENTER
        b.setTextColor(Color.rgb(24, 24, 30)); b.background = rounded(accent, accent, 10, 0)
        row.addView(b, LinearLayout.LayoutParams(act.dp(46), act.dp(46)))
        val tc = LinearLayout(act); tc.orientation = LinearLayout.VERTICAL
        tc.addView(label(titleT, 15f, Color.WHITE))
        if (subT.isNotEmpty()) tc.addView(label(subT, 12f, Color.rgb(176, 184, 198), font = false))
        row.addView(tc, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = act.dp(12) })
        row.addView(label("›", 26f, accent, bold = true, font = false))
        row.isClickable = true
        row.setOnClickListener { click() }
        row.setOnTouchListener { v, e ->
            when (e.actionMasked) {
                android.view.MotionEvent.ACTION_DOWN -> v.alpha = 0.65f
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.alpha = 1f
            }
            false
        }
        return row
    }

    private fun addGrid(items: List<View>, cols: Int) {
        var i = 0
        while (i < items.size) {
            val row = LinearLayout(act); row.orientation = LinearLayout.HORIZONTAL
            for (k in 0 until cols) {
                val v = items.getOrNull(i + k)
                val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f); lp.setMargins(act.dp(5), act.dp(5), act.dp(5), act.dp(5))
                row.addView(v ?: View(act), lp)
            }
            body.addView(row)
            i += cols
        }
    }

    private fun heading(text: String) {
        val sp = label(text, 13f, gold); sp.setPadding(act.dp(8), act.dp(14), 0, act.dp(2)); body.addView(sp)
    }

    private fun cols() = if (act.resources.displayMetrics.widthPixels / act.resources.displayMetrics.density > 700) 2 else 1

    private fun pick(cat: Int, sec: Int, extra: Int, what: String) {
        if (busy) return
        busy = true
        inspector.hide()
        body.removeAllViews(); backBtn.visibility = View.INVISIBLE; closeBtn.visibility = View.INVISIBLE
        title.text = "Montando a sala..."; sub.text = what
        val t = label("Carregando os blocos no mapa, só um instante", 15f, Color.WHITE); t.gravity = Gravity.CENTER
        t.setPadding(0, act.dp(40), 0, 0)
        body.addView(t)
        onPick(cat, sec, extra)
    }

    /** passo 1: categorias e vitrines */
    private fun showCats() {
        preferInspector = false
        inspector.hide()
        busy = false
        body.removeAllViews(); backBtn.visibility = View.INVISIBLE; closeBtn.visibility = View.VISIBLE
        title.text = "GALERIA DE BLOCOS"; sub.text = "Escolha uma categoria, depois um tipo de bloco"
        heading("REVISÃO DE BLOCOS")
        val validCount = B.names.indices.count { reviewPrefs.getString("status_$it", null) == "valid" }
        val invalidCount = B.names.indices.count { reviewPrefs.getString("status_$it", null) == "invalid" }
        val reviewTabs = listOf(
            card("VÁLIDOS", "$validCount blocos aprovados", Color.rgb(45, 165, 95), "✓") { showReviewed("valid") },
            card("INVÁLIDOS", "$invalidCount blocos reprovados", Color.rgb(200, 65, 70), "✕") { showReviewed("invalid") }
        )
        addGrid(reviewTabs, 2)
        heading("BLOCOS")
        val cs = ArrayList<View>()
        for ((i, p) in menu.withIndex()) {
            val c = p.first
            cs.add(card(c.name, "${p.second.size} tipos  -  ${c.ids.size} blocos", catColors[i % catColors.size], c.name.take(1).uppercase()) { showSecs(i) })
        }
        addGrid(cs, cols())
        heading("PASSEIOS")
        val ex = ArrayList<View>()
        ex.add(card("MANSÃO", "casa completa com móveis, garagem e piscina", Color.rgb(255, 190, 80), "M") { pick(-1, -1, World.GAL_HOUSE, "Mansão") })
        ex.add(card("APLICAÇÕES", "ambientes com todos os móveis e decorações", Color.rgb(170, 110, 220), "P") { pick(-1, -1, World.GAL_APPS, "Aplicações de móveis e decorações") })
        ex.add(card("MINI PRÉDIO", "4 andares decorados, com elevador", Color.rgb(60, 150, 200), "E") { pick(-1, -1, World.GAL_BUILDING, "Mini prédio com elevador") })
        ex.add(card("ÁRVORES", "todas as árvores plantadas", Color.rgb(90, 200, 90), "A") { pick(-1, -1, World.GAL_TREES, "Galeria de árvores") })
        ex.add(card("VEÍCULOS", "pista para testar os carros", Color.rgb(212, 60, 60), "V") { pick(-1, -1, World.GAL_CAR, "Teste de veículos") })
        addGrid(ex, cols())
        heading("FERRAMENTAS DE TESTE")
        val tools = listOf(card("INSPETOR DE BLOCOS", "Busca por nome/ID, prévias das texturas e teste individual", Color.rgb(255, 190, 80), "🔎") { showInspector() })
        addGrid(tools, cols())
    }

    /** Abre o inspetor de texturas sem sair da galeria. Ao fechar um teste, ele reabre na mesma ferramenta. */
    private fun showInspector() {
        preferInspector = true
        busy = false
        inspector.show()
    }

    /** Revisão por estado: mostra todos de uma vez no mapa. Aqui só se escolhe o recorte (tudo ou uma categoria); lá dentro cada categoria/seção tem a sua sala. */
    private fun showReviewed(status: String) {
        preferInspector = false
        inspector.hide()
        busy = false
        body.removeAllViews(); backBtn.visibility = View.VISIBLE; closeBtn.visibility = View.VISIBLE
        backBtn.setOnClickListener { showCats() }
        val valid = status == "valid"
        title.text = if (valid) "BLOCOS VÁLIDOS" else "BLOCOS INVÁLIDOS"
        sub.text = if (valid) "Blocos aprovados por você" else "Blocos que precisam de correção"
        val accent = if (valid) Color.rgb(45, 165, 95) else Color.rgb(200, 65, 70)
        val ids = B.names.indices.filter { reviewPrefs.getString("status_$it", null) == status }
        if (ids.isEmpty()) {
            val empty = label(if (valid) "Você ainda não validou nenhum bloco." else "Você ainda não invalidou nenhum bloco.", 14f, Color.rgb(176, 184, 198), font = false)
            empty.gravity = Gravity.CENTER
            empty.setPadding(act.dp(12), act.dp(28), act.dp(12), act.dp(28))
            body.addView(empty)
            return
        }
        val set = ids.toHashSet()
        val extra = if (valid) World.GAL_REVIEW_VALID else World.GAL_REVIEW_INVALID
        fun openMap(ci: Int, what: String) { World.reviewIds = ids; pick(ci, -1, extra, what) }
        heading("VER NO MAPA")
        addGrid(listOf(card("TODOS (${ids.size})", "todas as categorias, cada seção numa sala", accent, if (valid) "✓" else "✕") {
            openMap(-1, (if (valid) "Blocos válidos" else "Blocos inválidos") + " - todos")
        }), 1)
        heading("POR CATEGORIA")
        val cards = ArrayList<View>()
        for ((i, p) in menu.withIndex()) {
            val n = p.first.ids.count { it in set }
            if (n == 0) continue
            cards.add(card(p.first.name, "$n blocos", catColors[i % catColors.size], p.first.name.take(1).uppercase()) {
                openMap(i, (if (valid) "Válidos" else "Inválidos") + " - " + p.first.name)
            })
        }
        addGrid(cards, cols())
    }

    /** passo 2: tipos de bloco da categoria; cada um abre com TODOS os blocos numa sala só (sem partes). O primeiro cartão mostra a categoria inteira. */
    private fun showSecs(ci: Int) {
        val (cat, secs) = menu[ci]
        body.removeAllViews(); backBtn.visibility = View.VISIBLE; closeBtn.visibility = View.VISIBLE
        backBtn.setOnClickListener { showCats() }
        title.text = cat.name; sub.text = "Galeria  ›  ${cat.name}"
        val color = catColors[ci % catColors.size]
        heading("CATEGORIA INTEIRA")
        addGrid(listOf(card("VER TUDO", "${cat.ids.size} blocos  -  ${secs.size} salas, uma por tipo", color, "★") {
            pick(ci, -1, World.GAL_PAGE, cat.name + " - tudo")
        }), 1)
        heading("TIPOS")
        val cs = secs.map { s ->
            card(s.title, "${s.count} blocos", color, s.title.take(1).uppercase()) { pick(ci, s.idx, World.GAL_PAGE, cat.name + " - " + s.title) }
        }
        addGrid(cs, cols())
    }
}
