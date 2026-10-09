package com.cuboland.app

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

/** Atlas de texturas desenhado por código (16 tiles de 16x16 px). */
object Atlas {
    const val T = 16
    val bmp: Bitmap by lazy { build() }
    val tiles: Array<Bitmap> by lazy { Array(16) { Bitmap.createBitmap(bmp, it * T, 0, T, T) } }

    private fun h(x: Int, y: Int, s: Int): Float {
        var k = x * 73856093 xor (y * 19349663) xor (s * 83492791)
        k = (k xor (k ushr 13)) * 1274126177
        k = k xor (k ushr 16)
        return (k and 0xffff) / 65535f
    }

    private fun mul(c: Int, m: Float): Int {
        val r = (((c shr 16) and 255) * m).toInt().coerceIn(0, 255)
        val g = (((c shr 8) and 255) * m).toInt().coerceIn(0, 255)
        val b = ((c and 255) * m).toInt().coerceIn(0, 255)
        return (255 shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun px(t: Int, x: Int, y: Int): Int {
        val n = h(x, y, t)
        return when (t) {
            0 -> -1
            1 -> if (h(x, y, 2) > 0.9f) mul(0x8EE06A, 1f) else mul(0x5DBB45, 0.86f + 0.28f * n)
            2 -> {
                val depth = 3 + (h(x, 0, 5) * 3).toInt()
                if (y < depth) px(1, x, y) else px(3, x, y)
            }
            3 -> if (n > 0.92f) mul(0x6B4527, 1f) else mul(0x8C6039, 0.88f + 0.27f * n)
            4 -> {
                var m = 0.93f + 0.14f * n
                if (h(x / 3, y / 3, 9) > 0.72f) m *= 0.86f
                if (n > 0.96f) m *= 1.12f
                mul(0x9AA0A8, m)
            }
            5 -> if (n > 0.9f) mul(0xD9C27A, 1f) else mul(0xF0DE9A, 0.95f + 0.08f * n)
            6 -> mul(0x7A5230, 0.78f + 0.4f * h(x, 0, 3) + 0.06f * n)
            7 -> {
                val dx = x - 7.5f; val dy = y - 7.5f
                if (maxOf(abs(dx), abs(dy)) > 6.5f) mul(0x7A5230, 0.9f + 0.1f * n)
                else if (((sqrt(dx * dx + dy * dy) * 1.5f).toInt() and 1) == 0) mul(0xC89A62, 0.95f + 0.1f * n) else mul(0xB98A55, 0.95f + 0.1f * n)
            }
            8 -> when { n < 0.25f -> mul(0x2C7F2E, 1f); n > 0.8f -> mul(0x7ADB5A, 1f); else -> mul(0x3FA23B, 0.9f + 0.2f * n) }
            9 -> {
                val row = y / 4
                val seam = (x + (row % 2) * 8) % 16
                if (y % 4 == 3) mul(0xD9A864, 0.7f)
                else if (seam == 0) mul(0xD9A864, 0.75f)
                else mul(0xD9A864, 0.9f + 0.18f * h(row, x / 4, 4) + 0.04f * n)
            }
            10 -> {
                val w = 0.5f + 0.5f * sin((x + y) * 0.9f)
                if (n > 0.94f) mul(0xA8D8FF, 1f) else mul(0x3A8DDE, 0.92f + 0.14f * w)
            }
            11 -> {
                val row = y / 4; val xx = (x + (row % 2) * 4) % 8
                if (y % 4 == 3 || xx == 7) mul(0xC8C0B8, 0.95f + 0.05f * n)
                else mul(0xC4553F, 0.88f + 0.22f * h(row * 3 + (x + (row % 2) * 4) / 8, row, 6))
            }
            12 -> mul(0xFF8FC8, (if ((x + y) % 2 == 0) 1f else 0.94f) + 0.04f * n)
            13 -> mul(0x4F9BFF, (if ((x + y) % 2 == 0) 1f else 0.94f) + 0.04f * n)
            14 -> mul(0xFFD84A, (if ((x + y) % 2 == 0) 1f else 0.94f) + 0.04f * n)
            15 -> {
                val dx = x - 7.5f; val dy = y - 7.5f
                val frame = x < 2 || x > 13 || y < 2 || y > 13 || x == 7 || x == 8
                if (frame) mul(0x9A7424, 0.9f + 0.2f * n)
                else mul(0xFFF2B0, 1f - sqrt(dx * dx + dy * dy) * 0.035f)
            }
            else -> -1
        }
    }

    private fun build(): Bitmap {
        val b = Bitmap.createBitmap(T * 16, T, Bitmap.Config.ARGB_8888)
        for (t in 0 until 16) for (y in 0 until T) for (x in 0 until T) b.setPixel(t * T + x, y, px(t, x, y))
        return b
    }
}
