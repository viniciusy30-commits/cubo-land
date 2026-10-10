package com.cuboland.app

import android.app.Activity
import android.view.ViewGroup

object Novidades {
    class Entry(val id: String, val titulo: String, val itens: List<String>) {
        /** "2026-10-10-o" -> "10/10/2026" */
        fun data(): String {
            val p = id.split("-")
            return if (p.size >= 3) "${p[2]}/${p[1]}/${p[0]}" else id
        }
    }

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

    fun hasUnseen(a: Activity): Boolean {
        val top = parse(a).firstOrNull() ?: return false
        return a.getSharedPreferences("novidades", 0).getString("seen", "") != top.id
    }

    fun markSeen(a: Activity) {
        val top = parse(a).firstOrNull() ?: return
        a.getSharedPreferences("novidades", 0).edit().putString("seen", top.id).apply()
    }

    private fun show(a: Activity, e: Entry) {
        if (a.isFinishing) return
        a.cozyDialog(460) { p, close ->
            p.addView(a.cTxt("✦ Novidades · ${e.data()}", 12f, Cz.SOFT, false))
            p.addView(a.cTxt(e.titulo, 19f, Cz.GOLD).apply { setPadding(0, a.dp(2), 0, a.dp(8)) })
            for (item in e.itens) p.addView(a.cTxt("•  $item", 13f, Cz.CREAM, false).apply { setPadding(0, a.dp(2), 0, a.dp(2)) })
            p.addView(a.cBtn("Legal!", Cz.GREEN, 16f) { close() }, android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = a.dp(12) })
        }
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
