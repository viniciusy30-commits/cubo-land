package com.cuboland.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

/**
 * Importa texturas originais de skin Minecraft sem recomprimir os pixels.
 * O formato suportado é o atlas Java/Bedrock moderno 64x64 RGBA.
 */
object MinecraftSkinLibrary {
    private const val FILE_NAME = "minecraft_skin_imported.png"

    data class Result(val ok: Boolean, val message: String, val bitmap: Bitmap? = null)

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
            val target = File(context.filesDir, FILE_NAME)
            context.contentResolver.openInputStream(uri)!!.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            Result(true, "Skin importada: 64×64 • textura original preservada", decoded)
        } catch (_: Exception) {
            decoded.recycle()
            Result(false, "Falha ao salvar a skin no armazenamento do CuboLand.")
        }
    }

    fun load(context: Context): Bitmap? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return try { BitmapFactory.decodeFile(file.absolutePath) } catch (_: Exception) { null }
    }

    fun exists(context: Context): Boolean = File(context.filesDir, FILE_NAME).exists()
}
