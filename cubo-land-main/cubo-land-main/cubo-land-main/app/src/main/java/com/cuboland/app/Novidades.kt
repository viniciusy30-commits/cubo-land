package com.cuboland.app

import android.app.Activity
import androidx.appcompat.app.AlertDialog

object Novidades {
    class Entry(val id: String, val titulo: String, val itens: List<String>)

    fun parse(a: Activity): List<Entry> {
        val out = ArrayList<Entry>()
        try {
            var id = ""; var titulo = ""; var itens = ArrayList<String>()
            fun flush() { if (id.isNotEmpty()) out.add(Entry(id, titulo, itens)); itens = ArrayList() }
            for (raw in a.assets.open("novidades.txt").bufferedReader().readLines()) {
                val l = raw.trim()
                when {
                    l.startsWith("#id ") -> { flush(); id = l.substring(4).trim(); titulo = "" }
                    l.startsWith("#titulo ") -> titulo = l.substring(8).trim()
                    l.startsWith("- ") -> itens.add(l.substring(2).trim())
                }
            }
            flush()
        } catch (_: Exception) {}
        return out
    }

    private fun show(a: Activity, e: Entry) {
        if (a.isFinishing) return
        AlertDialog.Builder(a).setTitle("✨ " + e.titulo)
            .setMessage(e.itens.joinToString("\n") { "• $it" })
            .setPositiveButton("Legal!", null).show()
    }

    fun showIfNew(a: Activity) {
        val top = parse(a).firstOrNull() ?: return
        val sp = a.getSharedPreferences("novidades", 0)
        if (sp.getString("seen", "") == top.id) return
        sp.edit().putString("seen", top.id).apply()
        show(a, top)
    }

    fun showLatest(a: Activity) { parse(a).firstOrNull()?.let { show(a, it) } }
}
