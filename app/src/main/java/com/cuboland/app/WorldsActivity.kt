package com.cuboland.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class WorldsActivity : CozyActivity() {
    private lateinit var strip: LinearLayout
    private lateinit var countTxt: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = FrameLayout(this)
        root.addView(SceneBg(this, 0.45f))
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(28), dp(18), dp(28), dp(14)) }
        val head = cHeader("Escolha seu mundo", "Crie, edite e organize seus mundos") { finish() }
        countTxt = cTxt("", 13f, Cz.GOLD)
        head.addView(countTxt, lpW(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(14) })
        head.addView(cBtn("＋  Novo mundo", Cz.GREEN, 15f) { editor(null) })
        col.addView(head)
        strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(4), dp(10), dp(4), dp(10)) }
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(strip) }
        col.addView(hs, lpW(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(col)
        setContentView(root)
        head.popIn(0, -16)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        strip.removeAllViews()
        val list = Worlds.all(this)
        countTxt.text = if (list.isEmpty()) "" else "${list.size} mundo${if (list.size > 1) "s" else ""}"
        for ((i, w) in list.withIndex()) {
            val card = worldCard(w, i == 0)
            strip.addView(card, lpW(dp(236), ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(14) })
            card.popIn(70L * i, 30)
        }
        val add = newCard()
        strip.addView(add, lpW(dp(236), dp(270)))
        add.popIn(70L * list.size, 30)
    }

    private fun badge(t: String, color: Int): TextView = cTxt(t, 11f, Cz.INK, false).apply {
        background = CozyBox(this@WorldsActivity, color, 9f, false); setPadding(dp(8), dp(3), dp(8), dp(3))
    }

    private fun worldCard(w: Worlds.Info, last: Boolean): LinearLayout {
        val card = cPanel(Cz.PANEL, 20f, 10)
        card.addView(WorldThumb(this, w.seed, w.type), lpW(ViewGroup.LayoutParams.MATCH_PARENT, dp(110)))
        card.addView(cTxt(w.name, 18f, Color.WHITE).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END; setPadding(0, dp(8), 0, 0) })
        val tags = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, dp(4)) }
        tags.addView(badge(Worlds.TYPE_NAMES[w.type.coerceIn(0, 1)], if (w.type == 1) Cz.SKY else Cz.GREEN))
        tags.addView(badge(if (w.creative) "Criativo" else "Sobrevivência", if (w.creative) Cz.GOLD else Cz.ORANGE), lpW(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(6) })
        card.addView(tags)
        card.addView(cTxt((if (last) "★ Último jogado · " else "") + Worlds.agoText(w.played) + " · " + Worlds.sizeText(this, w), 11f, Cz.SOFT, false).apply { setPadding(0, 0, 0, dp(8)) })
        card.addView(cBtn("▶  Jogar", Cz.GREEN, 16f) { play(w) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(6), 0, 0) }
        row.addView(cBtn("✎ Editar", Cz.SKY, 12f) { editor(w) }, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(4) })
        row.addView(cBtn("⧉", Cz.LILAC, 14f) { Worlds.duplicate(this, w); Toast.makeText(this, "Mundo duplicado", Toast.LENGTH_SHORT).show(); refresh() }, lpW(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(4) })
        row.addView(cBtn("✕", Cz.RED, 14f) {
            cozyConfirm("Excluir \"${w.name}\"?", "Tudo que você construiu nesse mundo será apagado. Isso não tem volta.", "Excluir", Cz.RED) { Worlds.delete(this, w); refresh() }
        }, lpW(dp(48), ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(row)
        return card
    }

    private fun newCard(): LinearLayout {
        val card = cPanel(Cz.PANEL3, 20f, 14)
        card.gravity = Gravity.CENTER
        card.isClickable = true
        card.setOnClickListener { editor(null) }
        card.addView(cTxt("＋", 54f, Cz.GOLD))
        card.addView(cTxt("Criar novo mundo", 16f, Cz.CREAM).apply { gravity = Gravity.CENTER })
        card.addView(cTxt("Ilha ou planície com rio gigante", 11f, Cz.SOFT, false).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, 0) })
        return card
    }

    private fun play(w: Worlds.Info) {
        startActivity(Intent(this, GameActivity::class.java).putExtra("wid", w.id))
    }

    /** criar (w == null) ou editar um mundo */
    private fun editor(w: Worlds.Info?) {
        cozyDialog(480) { p, close ->
            p.addView(cTxt(if (w == null) "Novo mundo" else "Editar mundo", 22f, Cz.GOLD))
            p.addView(cTxt("Nome", 12f, Cz.SOFT, false).apply { setPadding(0, dp(10), 0, dp(2)) })
            val name = EditText(this).apply {
                setText(w?.name ?: ""); hint = "Meu mundo incrível"; setHintTextColor(Color.argb(110, 255, 255, 255))
                setTextColor(Color.WHITE); typeface = uiFont(); textSize = 16f; maxLines = 1; isSingleLine = true
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_DONE
                background = CozyBox(this@WorldsActivity, Cz.PANEL3, 12f, false); setPadding(dp(12), dp(10), dp(12), dp(10))
                filters = arrayOf(android.text.InputFilter.LengthFilter(22))
            }
            p.addView(name)

            var type = w?.type ?: Worlds.ISLAND
            var creative = w?.creative ?: false
            if (w == null) {
                p.addView(cTxt("Tipo de mapa", 12f, Cz.SOFT, false).apply { setPadding(0, dp(12), 0, dp(4)) })
                val tr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val boxes = ArrayList<LinearLayout>()
                val infos = arrayOf("Ilha" to "Mundo médio com colinas, lagos e florestas", "Planície e rio" to "Gigante e plano, com um rio enorme")
                fun paint() { for ((k, b) in boxes.withIndex()) b.background = CozyBox(this, if (k == type) Cz.PANEL2 else Cz.PANEL3, 14f, false, if (k == type) Cz.GOLD else Cz.INK) }
                for (k in 0..1) {
                    val b = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(8), dp(8), dp(8), dp(8)); isClickable = true }
                    b.addView(WorldThumb(this, if (k == 0) 1234 else 4321, k), lpW(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)))
                    b.addView(cTxt(infos[k].first, 14f, Color.WHITE).apply { setPadding(0, dp(6), 0, 0) })
                    b.addView(cTxt(infos[k].second, 10.5f, Cz.SOFT, false))
                    b.setOnClickListener { type = k; paint() }
                    boxes.add(b)
                    tr.addView(b, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (k == 0) rightMargin = dp(6) else leftMargin = dp(6) })
                }
                paint(); p.addView(tr)
            }
            p.addView(cTxt("Modo de jogo", 12f, Cz.SOFT, false).apply { setPadding(0, dp(12), 0, dp(4)) })
            val mr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val m0 = cChip("⚔  Sobrevivência") {}; val m1 = cChip("✦  Criativo (voar, tudo quebra)") {}
            fun pm() { m0.styleChip(!creative); m1.styleChip(creative) }
            m0.setOnClickListener { creative = false; pm() }; m1.setOnClickListener { creative = true; pm() }
            pm()
            mr.addView(m0, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
            mr.addView(m1, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f))
            p.addView(mr)

            var seedEt: EditText? = null
            if (w == null) {
                p.addView(cTxt("Semente (opcional — deixe vazio para aleatória)", 12f, Cz.SOFT, false).apply { setPadding(0, dp(12), 0, dp(2)) })
                seedEt = EditText(this).apply {
                    hint = "ex: 1234"; setHintTextColor(Color.argb(110, 255, 255, 255)); setTextColor(Color.WHITE); typeface = uiFont(); textSize = 15f
                    isSingleLine = true; inputType = InputType.TYPE_CLASS_NUMBER; imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_DONE
                    background = CozyBox(this@WorldsActivity, Cz.PANEL3, 12f, false); setPadding(dp(12), dp(8), dp(12), dp(8))
                    filters = arrayOf(android.text.InputFilter.LengthFilter(8))
                }
                p.addView(seedEt)
            } else {
                p.addView(cTxt("Gerar de novo apaga as construções e cria o terreno do zero.", 11.5f, Cz.SOFT, false).apply { setPadding(0, dp(12), 0, dp(4)) })
                p.addView(cBtn("↻  Gerar terreno de novo", Cz.ORANGE, 13f) {
                    cozyConfirm("Gerar de novo?", "As construções de \"${w.name}\" serão apagadas e o terreno volta ao começo.", "Gerar", Cz.ORANGE) {
                        Worlds.regenerate(this, w); Toast.makeText(this, "Terreno será recriado ao jogar", Toast.LENGTH_SHORT).show(); refresh()
                    }
                })
            }

            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, 0) }
            row.addView(cBtn("Cancelar", Cz.STONE, 15f) { close() }, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(6) })
            row.addView(cBtn(if (w == null) "Criar e jogar" else "Salvar", Cz.GREEN, 15f) {
                val nm = name.text.toString().trim()
                if (w == null) {
                    val sd = seedEt?.text?.toString()?.toIntOrNull() ?: (System.nanoTime() % 99999).toInt().let { if (it < 0) -it else it }
                    val n = Worlds.create(this, nm, type, sd, creative)
                    close(); refresh(); play(n)
                } else {
                    w.name = nm.ifEmpty { w.name }; w.creative = creative
                    Worlds.update(this, w); close(); refresh()
                }
            }, lpW(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(6) })
            p.addView(row)
        }
    }
}
