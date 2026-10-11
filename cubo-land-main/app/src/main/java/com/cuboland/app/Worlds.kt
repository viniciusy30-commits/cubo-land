package com.cuboland.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Lista de mundos salvos (nome, tipo, semente, modo) + arquivos de save. */
object Worlds {
    const val ISLAND = 0
    const val FLAT = 1
    val TYPE_NAMES = arrayOf("Ilha", "Planície e rio")

    class Info(val id: String, var name: String, val type: Int, val seed: Int, var creative: Boolean,
               val created: Long, var played: Long, val file: String)

    private fun sp(ctx: Context) = ctx.getSharedPreferences("worlds", 0)

    fun fileOf(ctx: Context, i: Info) = File(ctx.filesDir, i.file)

    private fun toJson(l: List<Info>): String {
        val a = JSONArray()
        for (i in l) a.put(JSONObject().put("id", i.id).put("name", i.name).put("type", i.type).put("seed", i.seed)
            .put("creative", i.creative).put("created", i.created).put("played", i.played).put("file", i.file))
        return a.toString()
    }

    fun saveAll(ctx: Context, l: List<Info>) { sp(ctx).edit().putString("list", toJson(l)).apply() }

    private fun migrate(ctx: Context) {
        val s = sp(ctx)
        if (s.getBoolean("migrated", false)) return
        val out = ArrayList<Info>()
        val cre = ctx.getSharedPreferences("cfg", 0).getBoolean("creative", false)
        val now = System.currentTimeMillis()
        if (File(ctx.filesDir, "mundo.bin").exists()) out.add(Info("ilha_antigo", "Minha ilha", ISLAND, 1234, cre, now - 2000, now - 2000, "mundo.bin"))
        if (File(ctx.filesDir, "mundo_plano.bin").exists()) out.add(Info("plano_antigo", "Planície e rio", FLAT, 4321, cre, now - 4000, now - 4000, "mundo_plano.bin"))
        s.edit().putString("list", toJson(out)).putBoolean("migrated", true).apply()
    }

    fun all(ctx: Context): MutableList<Info> {
        migrate(ctx)
        val out = ArrayList<Info>()
        try {
            val a = JSONArray(sp(ctx).getString("list", "[]"))
            for (k in 0 until a.length()) {
                val o = a.getJSONObject(k)
                out.add(Info(o.getString("id"), o.getString("name"), o.getInt("type"), o.getInt("seed"), o.getBoolean("creative"),
                    o.getLong("created"), o.getLong("played"), o.getString("file")))
            }
        } catch (_: Exception) {}
        out.sortByDescending { it.played }
        return out
    }

    fun get(ctx: Context, id: String): Info? = all(ctx).firstOrNull { it.id == id }

    fun create(ctx: Context, name: String, type: Int, seed: Int, creative: Boolean): Info {
        val l = all(ctx)
        val id = java.lang.Long.toString(System.currentTimeMillis(), 36) + (l.size)
        val now = System.currentTimeMillis()
        val i = Info(id, name.trim().ifEmpty { "Mundo novo" }, type, seed, creative, now, now, "mundo_$id.bin")
        l.add(i); saveAll(ctx, l)
        return i
    }

    fun update(ctx: Context, i: Info) {
        val l = all(ctx)
        val idx = l.indexOfFirst { it.id == i.id }
        if (idx >= 0) { l[idx] = i; saveAll(ctx, l) }
    }

    fun touch(ctx: Context, id: String) {
        val i = get(ctx, id) ?: return
        i.played = System.currentTimeMillis(); update(ctx, i)
    }

    fun delete(ctx: Context, i: Info) {
        try { fileOf(ctx, i).delete() } catch (_: Exception) {}
        saveAll(ctx, all(ctx).filter { it.id != i.id })
    }

    /** apaga só as construções (o mundo é gerado de novo na próxima vez) */
    fun regenerate(ctx: Context, i: Info) { try { fileOf(ctx, i).delete() } catch (_: Exception) {} }

    fun duplicate(ctx: Context, i: Info): Info {
        val n = create(ctx, i.name + " (cópia)", i.type, i.seed, i.creative)
        try { if (fileOf(ctx, i).exists()) fileOf(ctx, i).copyTo(fileOf(ctx, n), true) } catch (_: Exception) {}
        return n
    }

    fun hasSave(ctx: Context, i: Info) = fileOf(ctx, i).exists()

    fun sizeText(ctx: Context, i: Info): String {
        val f = fileOf(ctx, i)
        if (!f.exists()) return "novo"
        val kb = f.length() / 1024
        return if (kb >= 1024) "%.1f MB".format(kb / 1024f) else "$kb KB"
    }

    fun agoText(t: Long): String {
        val s = (System.currentTimeMillis() - t) / 1000
        return when {
            s < 60 -> "agora mesmo"
            s < 3600 -> "há ${s / 60} min"
            s < 86400 -> "há ${s / 3600} h"
            s < 86400 * 30 -> "há ${s / 86400} d"
            else -> "faz tempo"
        }
    }
}
