package com.cuboland.app

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class CharacterActivity : AppCompatActivity() {
    private lateinit var col: LinearLayout
    private val refreshers = ArrayList<() -> Unit>()
    private var presetIdx = 0
    private val ANIMS = arrayOf("Parado", "Andando", "Correndo", "Pulando", "Atacando")

    private fun label(t: String, size: Float = 15f, g: Int = Gravity.START) = TextView(this).apply {
        text = t; textSize = size; setTextColor(Color.WHITE); typeface = mcFont(); gravity = g
        setShadowLayer(0.5f, dp(2).toFloat(), dp(2).toFloat(), Color.rgb(40, 40, 40)); setPadding(0, dp(4), 0, dp(2))
    }

    private fun changed() { Look.save(this); refreshers.forEach { it() } }

    private fun header(t: String) {
        col.addView(label(t, 18f).apply { setTextColor(Color.rgb(255, 230, 120)); setPadding(0, dp(14), 0, dp(2)) })
    }

    private fun stepper(title: String, idx: Int, names: Array<String>) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val tv = label("", 15f, Gravity.CENTER)
        val prev = btn("<", Color.rgb(66, 133, 244)) { Look.set(idx, Look.v[idx] - 1); changed() }
        val next = btn(">", Color.rgb(66, 133, 244)) { Look.set(idx, Look.v[idx] + 1); changed() }
        row.addView(prev, LinearLayout.LayoutParams(dp(54), dp(46)))
        row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(next, LinearLayout.LayoutParams(dp(54), dp(46)))
        refreshers.add { tv.text = "$title: ${names[Look.v[idx].coerceIn(0, names.size - 1)]}" }
        col.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
    }

    private fun swatches(title: String, idx: Int, colors: IntArray, names: Array<String>) {
        val tv = label("", 14f)
        refreshers.add { tv.text = "$title: ${names[Look.v[idx].coerceIn(0, names.size - 1)]}" }
        col.addView(tv)
        val perRow = 6
        var row: LinearLayout? = null
        val views = ArrayList<View>()
        for (i in colors.indices) {
            if (i % perRow == 0) { row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }; col.addView(row) }
            val v = View(this)
            v.setOnClickListener { Look.set(idx, i); changed() }
            row!!.addView(v, LinearLayout.LayoutParams(dp(36), dp(36)).apply { setMargins(0, dp(3), dp(6), dp(3)) })
            views.add(v)
        }
        refreshers.add {
            for (i in views.indices) {
                val sel = Look.v[idx] == i
                views[i].background = GradientDrawable().apply {
                    setColor(colors[i] or (0xFF shl 24)); setStroke(if (sel) dp(3) else dp(1), if (sel) Color.WHITE else Color.BLACK)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Look.load(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; background = dirtBackground(); setPadding(dp(16), dp(10), dp(16), dp(10)) }

        val left = FrameLayout(this)
        val cv = CharView(this)
        left.addView(cv, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        var animBtn: android.widget.Button? = null
        animBtn = btn("Animação: " + ANIMS[0], Color.rgb(66, 133, 244)) {
            cv.mode = (cv.mode + 1) % ANIMS.size
            animBtn?.text = "Animação: " + ANIMS[cv.mode]
        }
        left.addView(animBtn, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, dp(44), Gravity.TOP or Gravity.START).apply { topMargin = dp(4); leftMargin = dp(4) })
        left.addView(label("Arraste pra girar", 12f, Gravity.CENTER), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        root.addView(left, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0.9f))

        col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, dp(14), dp(12)) }
        col.addView(label("Meu personagem", 26f))
        header("Looks prontos")
        run {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val tv = label("", 15f, Gravity.CENTER)
            val prev = btn("<", Color.rgb(171, 71, 188)) { presetIdx = (presetIdx + Look.PRESETS.size - 1) % Look.PRESETS.size; Look.applyPreset(presetIdx); changed(); tv.text = Look.PRESET_NAMES[presetIdx] }
            val next = btn(">", Color.rgb(171, 71, 188)) { presetIdx = (presetIdx + 1) % Look.PRESETS.size; Look.applyPreset(presetIdx); changed(); tv.text = Look.PRESET_NAMES[presetIdx] }
            tv.text = "Toque nas setas"
            row.addView(prev, LinearLayout.LayoutParams(dp(54), dp(46)))
            row.addView(tv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(next, LinearLayout.LayoutParams(dp(54), dp(46)))
            col.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
        }
        header("Corpo")
        stepper("Tipo", Look.GENDER, Look.GENDER_NAMES)
        swatches("Pele", Look.SKIN, Look.SKINS, Look.SKIN_NAMES)
        header("Cabelo")
        stepper("Estilo", Look.HAIR, Look.HAIR_STYLES)
        swatches("Cor", Look.HAIRC, Look.HAIRS, Look.HAIR_NAMES)
        header("Rosto")
        stepper("Olhos", Look.EYES, Look.EYE_STYLES)
        swatches("Cor dos olhos", Look.EYEC, Look.EYE_COLORS, Look.EYE_COLOR_NAMES)
        stepper("Boca", Look.MOUTH, Look.MOUTHS)
        stepper("Bochechas coradas", Look.BLUSH, Look.ONOFF)
        stepper("Rosto", Look.FACE, Look.FACES)
        header("Roupas")
        stepper("Blusa", Look.TOP, Look.TOPS)
        swatches("Cor da blusa", Look.TOPC, Look.PAL, Look.PAL_NAMES)
        stepper("Parte de baixo", Look.BOTTOM, Look.BOTTOMS)
        swatches("Cor", Look.BOTTOMC, Look.PAL, Look.PAL_NAMES)
        stepper("Calçados", Look.SHOES, Look.SHOE_STYLES)
        swatches("Cor dos calçados", Look.SHOEC, Look.PAL, Look.PAL_NAMES)
        header("Acessórios")
        stepper("Cabeça", Look.HAT, Look.HATS)
        swatches("Cor", Look.HATC, Look.PAL, Look.PAL_NAMES)
        stepper("Pescoço", Look.NECK, Look.NECKS)
        swatches("Cor", Look.NECKC, Look.PAL, Look.PAL_NAMES)
        stepper("Costas", Look.BACK, Look.BACKS)
        swatches("Cor", Look.BACKC, Look.PAL, Look.PAL_NAMES)
        header("")
        col.addView(btn("Aleatório", Color.rgb(171, 71, 188)) { Look.randomize(); changed() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        col.addView(btn("Voltar", Color.rgb(90, 100, 120)) { finish() }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(8) })

        val sv = ScrollView(this).apply { addView(col) }
        root.addView(sv, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1.1f))
        setContentView(root)
        changed()
    }
}
