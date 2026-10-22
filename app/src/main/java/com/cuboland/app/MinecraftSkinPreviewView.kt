package com.cuboland.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import kotlin.math.min

/** Prévia frontal em pixel-perfect para mostrar a skin real no Estúdio sem distorcer seus pixels. */
class MinecraftSkinPreviewView(context: Context) : View(context) {
    private var skin: Bitmap? = null
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
    private val src = Rect()
    private val dst = Rect()

    fun setSkin(bitmap: Bitmap?) {
        skin = bitmap
        visibility = if (bitmap == null) GONE else VISIBLE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val b = skin ?: return
        if (b.width != 64 || b.height != 64) return
        canvas.drawColor(Color.rgb(42, 39, 50))
        // 8x8 cabeça, 8x12 tronco, braços e pernas de 4x12 pixels.
        val unit = min(width / 16f, height / 32f)
        if (unit <= 0f) return
        val totalW = 16f * unit
        val totalH = 32f * unit
        val ox = (width - totalW) * .5f
        val oy = (height - totalH) * .5f
        fun piece(sx: Int, sy: Int, sw: Int, sh: Int, x: Float, y: Float, w: Float, h: Float) {
            src.set(sx, sy, sx + sw, sy + sh)
            dst.set((ox + x * unit).toInt(), (oy + y * unit).toInt(),
                (ox + (x + w) * unit).toInt(), (oy + (y + h) * unit).toInt())
            canvas.drawBitmap(b, src, dst, paint)
        }
        // Base layer
        piece(8,8,8,8,4f,0f,8f,8f)
        piece(20,20,8,12,4f,8f,8f,12f)
        piece(44,20,4,12,0f,8f,4f,12f)
        piece(36,52,4,12,12f,8f,4f,12f)
        piece(4,20,4,12,4f,20f,4f,12f)
        piece(20,52,4,12,8f,20f,4f,12f)
        // Outer layer, including transparent pixels, overlays the matching base regions.
        piece(40,8,8,8,4f,0f,8f,8f)
        piece(20,36,8,12,4f,8f,8f,12f)
        piece(44,36,4,12,0f,8f,4f,12f)
        piece(52,52,4,12,12f,8f,4f,12f)
        piece(4,36,4,12,4f,20f,4f,12f)
        piece(4,52,4,12,8f,20f,4f,12f)
    }
}
