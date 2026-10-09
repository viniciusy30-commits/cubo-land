package com.cuboland.app

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sp = getSharedPreferences("cfg", 0)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(40), dp(24), dp(40), dp(24)) }
        fun label(t: String, size: Float = 16f, bold: Boolean = false) = TextView(this).apply {
            text = t; textSize = size; setTextColor(Color.WHITE); if (bold) typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(6f, 0f, 3f, Color.argb(120, 20, 40, 90)); setPadding(0, dp(8), 0, dp(4))
        }
        col.addView(label("Configurações", 30f, true))
        val sensLabel = label("")
        val sens = sp.getFloat("sens", 1f)
        fun upd(v: Float) { sensLabel.text = "Sensibilidade da câmera: ${"%.1f".format(v)}x" }
        upd(sens)
        col.addView(sensLabel)
        val bar = SeekBar(this).apply {
            max = 100; progress = ((sens - 0.4f) / 1.6f * 100).toInt()
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { val v = 0.4f + p / 100f * 1.6f; upd(v); sp.edit().putFloat("sens", v).apply() }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        col.addView(bar)
        fun add(b: android.view.View) {
            val lp = LinearLayout.LayoutParams(dp(300), LinearLayout.LayoutParams.WRAP_CONTENT); lp.topMargin = dp(12); col.addView(b, lp)
        }
        fun cTxt() = if (sp.getBoolean("creative", false)) "🕊  Modo criativo: LIGADO" else "🕊  Modo criativo: desligado"
        val cHold = arrayOfNulls<android.widget.Button>(1)
        cHold[0] = btn(cTxt(), Color.rgb(46, 160, 100)) { sp.edit().putBoolean("creative", !sp.getBoolean("creative", false)).apply(); cHold[0]?.text = cTxt() }
        add(cHold[0]!!)
        col.addView(label("No criativo: toque duas vezes no pulo pra voar, quebra tudo de uma batida e nada te machuca.", 13f))
        add(btn("🔄  Verificar atualização", Color.rgb(66, 133, 244)) { Updater.check(this, true) })
        add(btn("✨  O que mudou", Color.rgb(171, 71, 188)) { Novidades.showLatest(this) })
        add(btn("🗑  Recomeçar o mundo", Color.rgb(229, 80, 70)) {
            AlertDialog.Builder(this).setTitle("Recomeçar o mundo?")
                .setMessage("Tudo que você construiu será apagado e um mundo novo será criado.")
                .setPositiveButton("Apagar") { _, _ -> File(filesDir, "mundo.bin").delete() }
                .setNegativeButton("Cancelar", null).show()
        })
        add(btn("←  Voltar", Color.rgb(90, 100, 120)) { finish() })
        col.addView(label("Versão instalada: ${packageManager.getPackageInfo(packageName, 0).versionName}", 13f))
        val sv = ScrollView(this)
        sv.background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.rgb(64, 140, 232), Color.rgb(140, 208, 250), Color.rgb(255, 236, 205)))
        sv.addView(col)
        setContentView(sv)
    }
}
