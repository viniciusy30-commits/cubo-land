package com.cuboland.app

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class CharacterActivity : CozyActivity() {
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var cv: CharView
    private val refreshers = ArrayList<() -> Unit>()
    private val tabViews = ArrayList<TextView>()
    private val TABS = arrayOf("Looks", "Corpo", "Cabelo", "Rosto", "Roupas", "Acessórios")
    private val ANIMS = arrayOf("Parado", "Andando", "Correndo", "Pulando", "Atacando")
    private var current = -1
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    private fun changed() { Look.save(this); refreshers.forEach { it() } }

    private fun add(v: View) { content.addView(v, lpW(MATCH, WRAP).apply { bottomMargin = dp(12) }) }

    /** grade de opções (chips); a escolhida fica dourada */
    private fun grid(card: LinearLayout, label: String, idx: Int, names: Array<String>, perRow: Int = 3) {
        val tv = cTxt("", 13f, Cz.SOFT, false).apply { setPadding(0, dp(10), 0, dp(2)) }
        card.addView(tv)
        val chips = ArrayList<TextView>()
        var row: LinearLayout? = null
        for (i in names.indices) {
            if (i % perRow == 0) { row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; card.addView(row, lpW(MATCH, WRAP).apply { topMargin = dp(4) }) }
            val c = cChip(names[i]) { Look.set(idx, i); changed() }
            chips.add(c)
            row!!.addView(c, lpW(0, WRAP, 1f).apply { if (i % perRow != 0) leftMargin = dp(4) })
        }
        val rem = names.size % perRow
        if (rem != 0) for (k in rem until perRow) row!!.addView(View(this), lpW(0, 1, 1f).apply { leftMargin = dp(4) })
        refreshers.add {
            tv.text = "$label:  ${names[Look.v[idx].coerceIn(0, names.size - 1)]}"
            for ((i, c) in chips.withIndex()) c.styleChip(Look.v[idx] == i)
        }
    }

    /** bolinhas de cor */
    private fun swatch(card: LinearLayout, label: String, idx: Int, colors: IntArray, names: Array<String>, perRow: Int = 8) {
        val tv = cTxt("", 13f, Cz.SOFT, false).apply { setPadding(0, dp(10), 0, dp(2)) }
        card.addView(tv)
        val views = ArrayList<View>()
        var row: LinearLayout? = null
        for (i in colors.indices) {
            if (i % perRow == 0) { row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; card.addView(row) }
            val v = View(this)
            v.setOnClickListener { Look.set(idx, i); changed() }
            row!!.addView(v, LinearLayout.LayoutParams(dp(34), dp(34)).apply { setMargins(0, dp(3), dp(6), dp(3)) })
            views.add(v)
        }
        refreshers.add {
            tv.text = "$label:  ${names[Look.v[idx].coerceIn(0, names.size - 1)]}"
            for (i in views.indices) {
                val sel = Look.v[idx] == i
                views[i].background = SwatchDrawable(this, colors[i] or (0xFF shl 24), sel)
            }
        }
    }

    private fun select(i: Int) {
        if (i == current) return
        current = i
        refreshers.clear()
        for ((k, tv) in tabViews.withIndex()) tv.styleChip(k == i)
        content.removeAllViews(); scroll.scrollTo(0, 0)
        when (i) {
            0 -> {
                val c = cCard("Looks prontos", "Toque num look para vestir. Depois ajuste o que quiser nas outras abas.")
                val chips = ArrayList<TextView>()
                var row: LinearLayout? = null
                for (k in Look.PRESET_NAMES.indices) {
                    if (k % 2 == 0) { row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; c.addView(row, lpW(MATCH, WRAP).apply { topMargin = dp(6) }) }
                    val ch = cChip(Look.PRESET_NAMES[k]) { Look.applyPreset(k); changed() }
                    chips.add(ch); row!!.addView(ch, lpW(0, WRAP, 1f).apply { if (k % 2 != 0) leftMargin = dp(6) })
                }
                refreshers.add { for ((k, ch) in chips.withIndex()) ch.styleChip(Look.v.contentEquals(Look.PRESETS[k])) }
                add(c)
                val a = cCard("Surpresa")
                val br = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
                br.addView(cBtn("Aleatório", Cz.LILAC, 15f) { Look.randomize(); changed() }, lpW(0, WRAP, 1f).apply { rightMargin = dp(6) })
                br.addView(cBtn("Restaurar", Cz.STONE, 15f) { Look.v = Look.DEF.copyOf(); changed() }, lpW(0, WRAP, 1f).apply { leftMargin = dp(6) })
                a.addView(br); add(a)
            }
            1 -> {
                val c = cCard("Corpo"); grid(c, "Tipo", Look.GENDER, Look.GENDER_NAMES, 2); swatch(c, "Pele", Look.SKIN, Look.SKINS, Look.SKIN_NAMES, 6); add(c)
            }
            2 -> {
                val c = cCard("Cabelo"); grid(c, "Estilo", Look.HAIR, Look.HAIR_STYLES, 3); swatch(c, "Cor", Look.HAIRC, Look.HAIRS, Look.HAIR_NAMES); add(c)
            }
            3 -> {
                val c1 = cCard("Olhos"); grid(c1, "Estilo", Look.EYES, Look.EYE_STYLES, 3); swatch(c1, "Cor dos olhos", Look.EYEC, Look.EYE_COLORS, Look.EYE_COLOR_NAMES, 10); add(c1)
                val c2 = cCard("Boca e bochechas"); grid(c2, "Boca", Look.MOUTH, Look.MOUTHS, 3); grid(c2, "Bochechas coradas", Look.BLUSH, Look.ONOFF, 2); add(c2)
                val c3 = cCard("Detalhes do rosto"); grid(c3, "Detalhe", Look.FACE, Look.FACES, 3); add(c3)
            }
            4 -> {
                val c1 = cCard("Blusa"); grid(c1, "Modelo", Look.TOP, Look.TOPS, 3); swatch(c1, "Cor", Look.TOPC, Look.PAL, Look.PAL_NAMES); add(c1)
                val c2 = cCard("Parte de baixo"); grid(c2, "Modelo", Look.BOTTOM, Look.BOTTOMS, 3); swatch(c2, "Cor", Look.BOTTOMC, Look.PAL, Look.PAL_NAMES); add(c2)
                val c3 = cCard("Calçados"); grid(c3, "Modelo", Look.SHOES, Look.SHOE_STYLES, 3); swatch(c3, "Cor", Look.SHOEC, Look.PAL, Look.PAL_NAMES); add(c3)
            }
            else -> {
                val c1 = cCard("Cabeça"); grid(c1, "Item", Look.HAT, Look.HATS, 3); swatch(c1, "Cor", Look.HATC, Look.PAL, Look.PAL_NAMES); add(c1)
                val c2 = cCard("Pescoço"); grid(c2, "Item", Look.NECK, Look.NECKS, 3); swatch(c2, "Cor", Look.NECKC, Look.PAL, Look.PAL_NAMES); add(c2)
                val c3 = cCard("Costas"); grid(c3, "Item", Look.BACK, Look.BACKS, 3); swatch(c3, "Cor", Look.BACKC, Look.PAL, Look.PAL_NAMES); add(c3)
            }
        }
        refreshers.forEach { it() }
        for (k in 0 until content.childCount) content.getChildAt(k).popIn(60L * k, 16)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Look.load(this)
        val root = FrameLayout(this)
        root.addView(SceneBg(this, 0.5f))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(28), dp(18), dp(28), dp(14)) }
        val head = cHeader("Meu personagem", "Tudo é salvo automaticamente") { finish() }
        col.addView(head)
        val body = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        // ESQUERDA: pré-visualização 3D + animações
        val leftCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pv = FrameLayout(this).apply { background = CozyBox(this@CharacterActivity, Cz.PANEL3, 22f, false); setPadding(dp(8), dp(8), dp(8), dp(8)) }
        cv = CharView(this)
        pv.addView(cv, FrameLayout.LayoutParams(-1, -1))
        pv.addView(cTxt("Arraste para girar", 11f, Cz.SOFT, false), FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(4) })
        leftCol.addView(pv, lpW(MATCH, 0, 1f))
        val nameBox = EditText(this).apply {
            setText(Look.name(this@CharacterActivity)); hint = "Nome do personagem"
            setTextColor(Color.WHITE); setHintTextColor(Cz.SOFT); typeface = uiFont(); textSize = 15f
            maxLines = 1; isSingleLine = true; filters = arrayOf(InputFilter.LengthFilter(12))
            background = CozyBox(this@CharacterActivity, Cz.PANEL2, 12f, false)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(e: Editable?) { Look.saveName(this@CharacterActivity, e?.toString()?.trim() ?: "") }
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            })
        }
        leftCol.addView(nameBox, lpW(MATCH, WRAP).apply { topMargin = dp(8) })
        val animRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(8), 0, 0) }
        val animChips = ArrayList<TextView>()
        for (k in ANIMS.indices) {
            val c = cChip(ANIMS[k]) { cv.mode = k; for ((j, ch) in animChips.withIndex()) ch.styleChip(j == k) }
            animChips.add(c)
            animRow.addView(c, lpW(WRAP, WRAP).apply { if (k > 0) leftMargin = dp(5) })
        }
        animChips[0].styleChip(true)
        leftCol.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(animRow) }, lpW(MATCH, WRAP))
        body.addView(leftCol, lpW(0, MATCH, 0.8f))

        // DIREITA: abas + conteúdo
        val rightCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, t) in TABS.withIndex()) {
            val tv = cChip(t) { select(i) }
            tv.textSize = 14f
            tabViews.add(tv)
            tabRow.addView(tv, lpW(WRAP, WRAP).apply { if (i > 0) leftMargin = dp(6) })
        }
        rightCol.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(tabRow) }, lpW(MATCH, WRAP))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), dp(4), dp(10)) }
        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(content) }
        rightCol.addView(scroll, lpW(MATCH, 0, 1f))
        body.addView(rightCol, lpW(0, MATCH, 1.2f))

        col.addView(body, lpW(MATCH, 0, 1f).apply { topMargin = dp(10) })
        root.addView(col)
        setContentView(root)
        select(0)
    }
}
