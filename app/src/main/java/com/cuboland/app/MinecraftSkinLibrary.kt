package com.cuboland.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/** Biblioteca local e coleção de skins Minecraft integradas ao CuboLand. */
object MinecraftSkinLibrary {
    private const val ACTIVE_FILE = "minecraft_skin_imported.png"
    private const val LIBRARY_DIR = "minecraft_skins"
    private val BUILTIN_SKINS = listOf(
        "01_crimson_mage.png" to "Mago Carmesim",
        "02_void_knight.png" to "Cavaleiro do Vazio",
        "03_cyber_ninja.png" to "Ninja Cibernético",
        "04_royal_mage.png" to "Maga Imperial",
        "05_forest_ranger.png" to "Patrulheiro Élfico",
        "06_gothic_rose.png" to "Gótica Carmesim",
        "07_frost_guardian.png" to "Guardião Glacial",
        "08_streetwear.png" to "Streetwear Neon",
        "09_sun_paladin.png" to "Paladino Solar",
        "10_shadow_assassin.png" to "Assassino Sombrio"
    )

    data class Result(val ok: Boolean, val message: String, val bitmap: Bitmap? = null, val savedName: String? = null)

    fun import(context: Context, uri: Uri): Result {
        val decoded = try {
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        } catch (_: Exception) { null } ?: return Result(false, "Não consegui ler esse arquivo PNG.")
        if (decoded.width != 64 || decoded.height != 64) {
            val dims = "${decoded.width}×${decoded.height}"
            decoded.recycle()
            return Result(false, "A skin precisa ser PNG 64×64. Arquivo recebido: $dims.")
        }
        return try {
            val dir = File(context.filesDir, LIBRARY_DIR).apply { mkdirs() }
            var index = 1
            var slot = File(dir, "skin_%02d.png".format(index))
            while (slot.exists()) { index++; slot = File(dir, "skin_%02d.png".format(index)) }
            context.contentResolver.openInputStream(uri)!!.use { input ->
                FileOutputStream(slot).use { output -> input.copyTo(output) }
            }
            slot.copyTo(File(context.filesDir, ACTIVE_FILE), true)
            Result(true, "Skin salva na biblioteca: ${slot.nameWithoutExtension}", decoded, slot.name)
        } catch (_: Exception) {
            decoded.recycle()
            Result(false, "Falha ao salvar a skin no armazenamento do CuboLand.")
        }
    }

    fun list(context: Context): List<String> {
        val dir = File(context.filesDir, LIBRARY_DIR)
        val imported = dir.listFiles()?.filter { it.isFile && it.extension.equals("png", true) }
            ?.sortedBy { it.name }?.map { it.name } ?: emptyList()
        return BUILTIN_SKINS.map { "preset_${it.first}" } + imported
    }

    fun displayName(fileName: String): String {
        if (fileName.startsWith("preset_")) {
            val asset = fileName.removePrefix("preset_")
            return BUILTIN_SKINS.firstOrNull { it.first == asset }?.second ?: asset.removeSuffix(".png")
        }
        return fileName.removeSuffix(".png").replace("_", " ").uppercase()
    }

    fun activate(context: Context, fileName: String): Boolean {
        val destination = File(context.filesDir, ACTIVE_FILE)
        return try {
            if (fileName.startsWith("preset_")) {
                val assetName = fileName.removePrefix("preset_")
                if (BUILTIN_SKINS.none { it.first == assetName }) return false
                context.assets.open("preset_skins/$assetName").use { input ->
                    FileOutputStream(destination).use { output -> input.copyTo(output) }
                }
            } else {
                if (!fileName.matches(Regex("skin_[0-9]{2,}\\.png"))) return false
                val source = File(File(context.filesDir, LIBRARY_DIR), fileName)
                if (!source.isFile) return false
                source.copyTo(destination, true)
            }
            true
        } catch (_: Exception) { false }
    }

    fun load(context: Context): Bitmap? {
        val file = File(context.filesDir, ACTIVE_FILE)
        if (!file.exists()) return null
        return try { BitmapFactory.decodeFile(file.absolutePath) } catch (_: Exception) { null }
    }

    fun exists(context: Context): Boolean = File(context.filesDir, ACTIVE_FILE).exists()
}
