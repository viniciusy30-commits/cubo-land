package com.cuboland.app

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class SettingsActivity : CozyActivity() {
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private val tabViews = ArrayList<TextView>()
    private val TABS = arrayOf("☀  Geral", "✥  Controles", "◆  Gráficos", "✦  Novidades", "ℹ  Sobre")
    private var current = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        root.addView(SceneBg(this, 0.5f))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(28), dp(18), dp(28), dp(14)) }
        col.addView(cHeader("Ajustes", "Deixe o jogo do seu jeito") { finish() })
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val side = cPanel(Cz.PANEL3, 18f, 8)
        for ((i, t) in TABS.withIndex()) {
            val tv = cTxt(t, 15f, Color.WHITE, false)
            tv.setPadding(dp(14), dp(11), dp(14), dp(11)); tv.isClickable = true
            tv.setOnClickListener { select(i) }
            side.addView(tv, lpW(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(4) })
            tabViews.add(tv)
        }
        body.addView(side, lpW(dp(170), ViewGroup.LayoutParams.WRAP_CONTENT))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(4), dp(10)) }
        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(content) }
        body.addView(scroll, lpW(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        col.addView(body, lpW(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = dp(12) })
        root.addView(col)
        setContentView(root)
        select(intent.getIntExtra("tab", 0))
    }

    private fun select(i: Int) {
        if (i == current) return
        current = i
        for ((k, tv) in tabViews.withIndex()) {
            val on = k == i
            tv.background = if (on) CozyBox(this, Cz.GOLD, 12f, false, Color.rgb(120, 80, 20)) else null
            tv.setTextColor(if (on) Cz.INK else Color.WHITE)
        }
        content.removeAllViews(); scroll.scrollTo(0, 0)
        when (i) { 0 -> general(); 1 -> controls(); 2 -> graphics(); 3 -> news(); else -> about() }
        for (k in 0 until content.childCount) content.getChildAt(k).popIn(50L * k, 18)
    }

    private fun add(v: View) { content.addView(v, lpW(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }) }

    private fun slider(title: String, valueText: (Float) -> String, lo: Float, hi: Float, cur: Float, onChange: (Float) -> Unit): LinearLayout {
        val card = cPanel(Cz.PANEL, 16f, 14)
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        head.addView(cTxt(title, 15f, Cz.CREAM), lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val vt = cTxt(valueText(cur), 14f, Cz.GOLD); head.addView(vt)
        card.addView(head)
        val bar = SeekBar(this).apply {
            max = 100; progress = ((cur - lo) / (hi - lo) * 100).toInt().coerceIn(0, 100)
            progressTintList = ColorStateList.valueOf(Cz.GOLD); thumbTintList = ColorStateList.valueOf(Cz.CREAM)
            progressBackgroundTintList = ColorStateList.valueOf(Color.argb(90, 255, 255, 255))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { val v = lo + p / 100f * (hi - lo); vt.text = valueText(v); onChange(v) }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        card.addView(bar, lpW(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        return card
    }

    private fun general() {
        val sp = getSharedPreferences("cfg", 0)
        val c1 = cCard("Meu personagem", "Mude cabelo, roupas, acessórios e muito mais")
        c1.addView(cBtn("☺  Editar personagem", Cz.ORANGE, 15f) { startActivity(Intent(this, CharacterActivity::class.java)) }, lpW(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        add(c1)
        val c2 = cCard("Meus mundos", "Crie, edite, duplique e exclua mundos")
        c2.addView(cBtn("▶  Abrir seleção de mundos", Cz.GREEN, 15f) { startActivity(Intent(this, WorldsActivity::class.java)) }, lpW(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        add(c2)
        val c3 = cCard("Atualização", "Versão instalada: ${packageManager.getPackageInfo(packageName, 0).versionName}")
        c3.addView(cBtn("⟳  Verificar atualização", Cz.TEAL, 15f) { Updater.check(this, true) }, lpW(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        add(c3)
        if (sp.getBoolean("creative", false)) { /* ajuste antigo: agora o modo criativo é por mundo */ }
    }

    private fun controls() {
        val sp = getSharedPreferences("cfg", 0)
        add(slider("Sensibilidade da câmera", { "%.1fx".format(it) }, 0.4f, 2f, sp.getFloat("sens", 1f)) { sp.edit().putFloat("sens", it).apply() })
        add(slider("Tamanho dos botões", { "${(it * 100).toInt()}%" }, 0.8f, 1.25f, sp.getFloat("btn", 1f)) { sp.edit().putFloat("btn", it).apply() })
        val card = cPanel(Cz.PANEL, 16f, 14)
        card.addView(cToggleRow("Toque na tela como no Minecraft", "Toque rápido bate ou coloca bloco; segurar quebra e ataca; arrastar move a câmera", sp.getBoolean("tap", true)) { sp.edit().putBoolean("tap", it).apply() })
        add(card)
        val tips = cCard("Como jogar")
        for (t in listOf("Lado esquerdo: analógico para andar", "Lado direito: arraste para olhar em volta", "Toque rápido: coloca o bloco (ou ataca com ferramenta)", "Segure: quebra blocos e golpeia sem parar", "Espada e machado: segure o botão de ataque para carregar um golpe forte", "No criativo: toque duas vezes no pulo para voar"))
            tips.addView(cTxt("•  $t", 12.5f, Cz.CREAM, false).apply { setPadding(0, dp(4), 0, 0) })
        add(tips)
    }

    private fun graphics() {
        val sp = getSharedPreferences("cfg", 0)
        val card = cCard("Resolução do mundo 3D", "Menos resolução = mais fluidez em aparelhos fracos. Os botões continuam nítidos.")
        val names = arrayOf("Automática", "Alta (100%)", "Média (80%)", "Leve (60%)")
        val chips = ArrayList<TextView>()
        val grid = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(10), 0, 0) }
        fun paint() { for ((k, c) in chips.withIndex()) c.styleChip(k == sp.getInt("res", 0)) }
        for (k in names.indices) {
            val c = cChip(names[k]) { sp.edit().putInt("res", k).apply(); paint() }
            chips.add(c)
            grid.addView(c, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (k > 0) leftMargin = dp(6) })
        }
        paint(); card.addView(grid); add(card)
        add(cCard("Dica", "A resolução vale na próxima vez que você entrar em um mundo.").apply { })
    }

    private fun news() {
        Novidades.markSeen(this)
        val list = Novidades.parse(this)
        if (list.isEmpty()) { add(cCard("Nada por aqui ainda")); return }
        for ((i, e) in list.withIndex()) {
            val card = cPanel(if (i == 0) Cz.PANEL2 else Cz.PANEL, 16f, 14)
            val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            if (i == 0) head.addView(cTxt("NOVO", 10f, Cz.INK, false).apply { background = CozyBox(this@SettingsActivity, Cz.GOLD, 8f, false); setPadding(dp(7), dp(2), dp(7), dp(2)) }, lpW(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(8) })
            head.addView(cTxt(e.data(), 11.5f, Cz.SOFT, false))
            card.addView(head)
            card.addView(cTxt(e.titulo, 16f, Cz.GOLD).apply { setPadding(0, dp(4), 0, dp(4)) })
            for (item in e.itens) card.addView(cTxt("•  $item", 12.5f, Cz.CREAM, false).apply { setPadding(0, dp(2), 0, dp(2)) })
            add(card)
        }
    }

    private fun about() {
        val c = cCard("CuboLand", "Versão ${packageManager.getPackageInfo(packageName, 0).versionName}")
        c.addView(cTxt("Um mundinho de cubos para construir, explorar e lutar contra slimes. Feito com carinho, um bloco de cada vez. ♥", 13f, Cz.CREAM, false).apply { setPadding(0, dp(8), 0, dp(10)) })
        c.addView(cBtn("⟳  Verificar atualização", Cz.TEAL, 15f) { Updater.check(this, true) }, lpW(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        add(c)
    }
}
