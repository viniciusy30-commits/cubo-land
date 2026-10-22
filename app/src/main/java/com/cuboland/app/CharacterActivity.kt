package com.cuboland.app

import android.graphics.Color
import android.content.Intent
import android.graphics.Bitmap
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
import android.widget.ImageView
import android.widget.Toast

class CharacterActivity : CozyActivity() {
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var cv: CharView
    private lateinit var importedSkinAvatarPreview: MinecraftSkinPreviewView
    private lateinit var skinPreviewHint: TextView
    private lateinit var skinLibraryRow: LinearLayout
    private lateinit var importedSkinPreview: ImageView
    private lateinit var importedSkinStatus: TextView
    private val PICK_SKIN_PNG = 7041
    private val refreshers = ArrayList<() -> Unit>()
    private val tabViews = ArrayList<TextView>()
    private val TABS = arrayOf("Looks", "Corpo", "Cabelo", "Rosto", "Roupas", "Acessórios", "Acabamento")
    private val TAB_COL = intArrayOf(Cz.RED, Cz.ORANGE, Cz.GREEN, Cz.SKY, Cz.LILAC, Cz.TEAL, Cz.GOLD)
    private val ANIMS = arrayOf("Parado", "Andando", "Correndo", "Pulando", "Atacando", "Agachado", "Sentado", "Deitado", "Rastejando", "Voando", "Voo rápido", "Nadando", "Mergulho")
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

    /** estilos de cabelo só do tipo escolhido (menino ou menina) */
    private fun hairGrid(card: LinearLayout) {
        val list = Look.hairList()
        val tv = cTxt("", 13f, Cz.SOFT, false).apply { setPadding(0, dp(10), 0, dp(2)) }
        card.addView(tv)
        val chips = ArrayList<TextView>()
        var row: LinearLayout? = null
        val perRow = 3
        for ((n, id) in list.withIndex()) {
            if (n % perRow == 0) { row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; card.addView(row, lpW(MATCH, WRAP).apply { topMargin = dp(4) }) }
            val c = cChip("✦ " + Look.HAIR_STYLES[id]) { Look.set(Look.HAIR, id); changed() }
            chips.add(c)
            row!!.addView(c, lpW(0, WRAP, 1f).apply { if (n % perRow != 0) leftMargin = dp(4) })
        }
        val rem = list.size % perRow
        if (rem != 0) for (k in rem until perRow) row!!.addView(View(this), lpW(0, 1, 1f).apply { leftMargin = dp(4) })
        refreshers.add {
            tv.text = "Estilo:  ${Look.HAIR_STYLES[Look.v[Look.HAIR].coerceIn(0, Look.HAIR_STYLES.size - 1)]}"
            for ((n, c) in chips.withIndex()) c.styleChip(Look.v[Look.HAIR] == list[n])
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

    private fun refreshSkinLibrary() {
        if (!::skinLibraryRow.isInitialized) return
        skinLibraryRow.removeAllViews()
        val skins = MinecraftSkinLibrary.list(this)
        if (skins.isEmpty()) {
            skinLibraryRow.addView(cTxt("Nenhuma skin salva ainda. Importe PNGs 64×64 para montar sua coleção.", 12f, Cz.SOFT, false))
            return
        }
        for (fileName in skins) {
            val label = MinecraftSkinLibrary.displayName(fileName)
            val chip = cChip(label) {
                if (MinecraftSkinLibrary.activate(this, fileName)) {
                    val bitmap = MinecraftSkinLibrary.load(this)
                    if (bitmap != null) {
                        importedSkinPreview.visibility = View.VISIBLE
                        importedSkinPreview.setImageBitmap(bitmap)
                        importedSkinAvatarPreview.setSkin(bitmap)
                        cv.visibility = View.GONE
                        skinPreviewHint.text = "SKIN ATIVA: $label"
                        importedSkinStatus.text = "Skin ativa: $label • aplicada ao avatar 3D ao entrar no mundo"
                        importedSkinStatus.setTextColor(Cz.GREEN)
                    }
                }
            }
            skinLibraryRow.addView(chip, lpW(WRAP, WRAP).apply { rightMargin = dp(6) })
        }
    }

    private fun select(i: Int) {
        if (i == current) return
        current = i
        refreshers.clear()
        for ((k, tv) in tabViews.withIndex()) { tv.background = CozyBox(this, if (k == i) TAB_COL[k] else Cz.PANEL2, 12f, false, if (k == i) Cz.INK else Color.rgb(196, 140, 120)); tv.setTextColor(if (k == i) Color.WHITE else Cz.TXT) }
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
                val c = cCard(if (Look.v[Look.GENDER] == 1) "Ateliê de cabelo feminino" else "Ateliê de cabelo masculino", "✦ NOVO identifica os 50 penteados reformulados. Gire o personagem para conferir frente, lado e costas.")
                hairGrid(c)
                swatch(c, "Cor base", Look.HAIRC, Look.HAIRS, Look.HAIR_NAMES, 8)
                swatch(c, "Mechas / reflexos", Look.HAIR_ACCENT_COLOR, Look.PAL, Look.PAL_NAMES, 8)
                add(c)
            }
            3 -> {
                val c1 = cCard("Olhos"); grid(c1, "Estilo", Look.EYES, Look.EYE_STYLES, 3); swatch(c1, "Cor dos olhos", Look.EYEC, Look.EYE_COLORS, Look.EYE_COLOR_NAMES, 10); add(c1)
                val c2 = cCard("Boca e bochechas"); grid(c2, "Boca", Look.MOUTH, Look.MOUTHS, 3); grid(c2, "Bochechas coradas", Look.BLUSH, Look.ONOFF, 2); add(c2)
                val c3 = cCard("Detalhes do rosto"); grid(c3, "Detalhe", Look.FACE, Look.FACES, 3); add(c3)
            }
            4 -> {
                val c1 = cCard("Parte de cima", "Acabamentos em camadas para camisetas, jaquetas, armaduras, túnicas e vestidos.")
                grid(c1, "Modelo", Look.TOP, Look.TOPS, 3)
                swatch(c1, "Cor principal", Look.TOPC, Look.PAL, Look.PAL_NAMES, 8)
                grid(c1, "Estampa / acabamento", Look.TOP_PATTERN, Look.TOP_PATTERNS, 2)
                swatch(c1, "Cor dos detalhes", Look.TOP_ACCENT, Look.PAL, Look.PAL_NAMES, 8)
                add(c1)
                val c2 = cCard("Parte de baixo", "Costuras, patches, faixas e detalhes coordenados nas duas pernas.")
                grid(c2, "Modelo", Look.BOTTOM, Look.BOTTOMS, 3)
                swatch(c2, "Cor principal", Look.BOTTOMC, Look.PAL, Look.PAL_NAMES, 8)
                grid(c2, "Detalhes", Look.BOTTOM_PATTERN, Look.BOTTOM_PATTERNS, 2)
                swatch(c2, "Cor dos detalhes", Look.BOTTOM_ACCENT, Look.PAL, Look.PAL_NAMES, 8)
                add(c2)
                val c3 = cCard("Calçados", "Escolha a silhueta e combine a cor da sola, cabedal e detalhes.")
                grid(c3, "Modelo", Look.SHOES, Look.SHOE_STYLES, 3)
                swatch(c3, "Cor", Look.SHOEC, Look.PAL, Look.PAL_NAMES, 8)
                add(c3)
            }
            5 -> {
                val c1 = cCard("Cabeça", "Chapéus, capacetes, coroas, orelhas e acessórios de cabelo.")
                grid(c1, "Item", Look.HAT, Look.HATS, 3); swatch(c1, "Cor / metal / fita", Look.HATC, Look.PAL, Look.PAL_NAMES, 8); add(c1)
                val c2 = cCard("Pescoço", "Camadas independentes para cachecóis, laços, colares e medalhas.")
                grid(c2, "Item", Look.NECK, Look.NECKS, 3); swatch(c2, "Cor", Look.NECKC, Look.PAL, Look.PAL_NAMES, 8); add(c2)
                val c3 = cCard("Costas", "Capas, mochilas, asas e itens grandes com cor própria.")
                grid(c3, "Item", Look.BACK, Look.BACKS, 3); swatch(c3, "Cor", Look.BACKC, Look.PAL, Look.PAL_NAMES, 8); add(c3)
            }
            else -> {
                val c1 = cCard("Acabamento de roupa", "Combine estampas com uma cor de destaque diferente da cor principal.")
                grid(c1, "Estampa superior", Look.TOP_PATTERN, Look.TOP_PATTERNS, 2)
                swatch(c1, "Destaque superior", Look.TOP_ACCENT, Look.PAL, Look.PAL_NAMES, 8)
                grid(c1, "Detalhes inferiores", Look.BOTTOM_PATTERN, Look.BOTTOM_PATTERNS, 2)
                swatch(c1, "Destaque inferior", Look.BOTTOM_ACCENT, Look.PAL, Look.PAL_NAMES, 8)
                add(c1)
                val c2 = cCard("Mechas de cabelo", "Crie contraste, pontas coloridas ou reflexos neon sem mudar a cor base.")
                grid(c2, "Distribuição", Look.HAIR_ACCENT, Look.HAIR_ACCENTS, 3)
                swatch(c2, "Cor das mechas", Look.HAIR_ACCENT_COLOR, Look.PAL, Look.PAL_NAMES, 8)
                add(c2)
                val c3 = cCard("Combinações rápidas", "Gere uma skin completa e refine cada peça sem perder as alterações salvas.")
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                row.addView(cBtn("Skin aleatória", Cz.LILAC, 14f) { Look.randomize(); changed() }, lpW(0, WRAP, 1f).apply { rightMargin = dp(5) })
                row.addView(cBtn("Voltar ao padrão", Cz.STONE, 14f) { Look.v = Look.DEF.copyOf(); changed() }, lpW(0, WRAP, 1f).apply { leftMargin = dp(5) })
                c3.addView(row); add(c3)
            }
        }
        refreshers.forEach { it() }
        for (k in 0 until content.childCount) content.getChildAt(k).popIn(60L * k, 16)
    }

    @Deprecated("Deprecated in Android API 33; kept for compatibility with the current app") 
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK_SKIN_PNG || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val result = MinecraftSkinLibrary.import(this, uri)
        importedSkinStatus.text = result.message
        if (result.ok) {
            importedSkinStatus.setTextColor(Cz.GREEN)
            importedSkinPreview.visibility = View.VISIBLE
            importedSkinPreview.setImageBitmap(result.bitmap)
            importedSkinAvatarPreview.setSkin(result.bitmap)
            cv.visibility = View.GONE
            skinPreviewHint.text = "PRÉVIA DA SKIN IMPORTADA"
            refreshSkinLibrary()
            Toast.makeText(this, "Skin salva na biblioteca e aplicada ao avatar 3D ao entrar no mundo.", Toast.LENGTH_SHORT).show()
        } else {
            importedSkinStatus.setTextColor(Cz.RED)
            Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Look.load(this)
        val root = FrameLayout(this)
        root.addView(SceneBg(this, 0.1f))
        val narrow = resources.configuration.screenWidthDp < 720
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(if (narrow) 12 else 28), dp(if (narrow) 8 else 18), dp(if (narrow) 12 else 28), dp(if (narrow) 8 else 14)) }
        val head = cHeader("Estúdio de Skin", "Customização voxel avançada • tudo salvo automaticamente") { finish() }
        col.addView(head, lpW(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
        val wideLayout = resources.configuration.screenWidthDp >= 720
        val body = LinearLayout(this).apply { orientation = if (wideLayout) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }

        // ESQUERDA: pré-visualização 3D + animações
        val leftCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pv = FrameLayout(this).apply { background = CozyBox(this@CharacterActivity, Cz.GOLD, 22f, false); setPadding(dp(6), dp(6), dp(6), dp(6)) }
        cv = CharView(this); cv.scenic = true
        pv.addView(cv, FrameLayout.LayoutParams(-1, -1))
        importedSkinAvatarPreview = MinecraftSkinPreviewView(this)
        pv.addView(importedSkinAvatarPreview, FrameLayout.LayoutParams(-1, -1))
        skinPreviewHint = cTxtL("⟳  ARRASTE PARA GIRAR  ⟳", 11f, Color.WHITE, true)
        pv.addView(skinPreviewHint, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(6) })
        val savedSkinPreview = MinecraftSkinLibrary.load(this)
        if (savedSkinPreview != null) { importedSkinAvatarPreview.setSkin(savedSkinPreview); cv.visibility = View.GONE; skinPreviewHint.text = "PRÉVIA DA SKIN IMPORTADA" }
        leftCol.addView(pv, lpW(MATCH, 0, 1f))
        val skinImportCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = CozyBox(this@CharacterActivity, Cz.PANEL, 14f, false)
        }
        skinImportCard.addView(cTxt("SKIN PNG DO MINECRAFT", 12f, Cz.GOLD, true))
        importedSkinStatus = cTxt(
            if (MinecraftSkinLibrary.exists(this)) "Skin PNG importada neste dispositivo." else "Importe uma skin original em PNG (64×64).",
            12f, Cz.SOFT, false
        )
        skinImportCard.addView(importedSkinStatus)
        val skinImportRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        skinImportRow.addView(cBtn("Importar PNG", Cz.LILAC, 13f) {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/png"
            }
            startActivityForResult(intent, PICK_SKIN_PNG)
        }, lpW(0, WRAP, 1f).apply { rightMargin = dp(8) })
        importedSkinPreview = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(Color.rgb(35, 35, 43))
            setPadding(dp(4), dp(4), dp(4), dp(4))
            val b = MinecraftSkinLibrary.load(this@CharacterActivity)
            if (b != null) setImageBitmap(b) else visibility = View.GONE
        }
        skinImportRow.addView(importedSkinPreview, LinearLayout.LayoutParams(dp(64), dp(64)))
        skinImportCard.addView(skinImportRow)
        leftCol.addView(skinImportCard, lpW(MATCH, WRAP).apply { topMargin = dp(8) })
        val skinLibraryCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = CozyBox(this@CharacterActivity, Cz.PANEL, 14f, false)
        }
        skinLibraryCard.addView(cTxt("MINHA BIBLIOTECA DE SKINS", 12f, Cz.GOLD, true))
        skinLibraryRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        skinLibraryCard.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(skinLibraryRow)
        }, lpW(MATCH, WRAP).apply { topMargin = dp(6) })
        leftCol.addView(skinLibraryCard, lpW(MATCH, WRAP).apply { topMargin = dp(8) })
        refreshSkinLibrary()
        val nameBox = EditText(this).apply {
            setText(Look.name(this@CharacterActivity)); hint = "Nome do personagem"
            setTextColor(Cz.TXT); setHintTextColor(Cz.TXT_SOFT); typeface = uiFont(); textSize = 15f
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
        body.addView(leftCol, if (wideLayout) lpW(0, MATCH, 0.8f) else lpW(MATCH, dp(300)))

        // DIREITA: abas + conteúdo
        val rightCol = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((i, t) in TABS.withIndex()) {
            val tv = cChip(t) { select(i) }
            tv.textSize = 14f; tv.setPadding(dp(14), dp(10), dp(14), dp(10))
            tabViews.add(tv)
            tabRow.addView(tv, lpW(WRAP, WRAP).apply { if (i > 0) leftMargin = dp(6) })
        }
        rightCol.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(tabRow) }, lpW(MATCH, WRAP))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(10), dp(4), dp(10)) }
        scroll = ScrollView(this).apply { isVerticalScrollBarEnabled = false; addView(content) }
        rightCol.addView(scroll, lpW(MATCH, 0, 1f))
        body.addView(rightCol, if (wideLayout) lpW(0, MATCH, 1.2f) else lpW(MATCH, 0, 1f))

        col.addView(body, lpW(MATCH, 0, 1f).apply { topMargin = dp(10) })
        root.addView(col)
        setContentView(root)
        select(0)
    }
}
