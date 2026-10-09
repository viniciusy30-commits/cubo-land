package com.cuboland.app

import kotlin.math.abs
import kotlin.math.sqrt

/** Ferramentas em pixel-art 16x16 (estilo Minecraft): cada pixel vira um cubinho, com contorno escuro. */
object ToolSprites {
    class Run(val x0: Int, val x1: Int, val y: Int, val c: Int)
    class Sprite(val runs: List<Run>, val gx: Int, val gy: Int)

    private const val N = 16
    private const val FLAG = 0x1000000
    private const val OUT = 0x4A3F5C

    private fun put(g: IntArray, x: Int, y: Int, c: Int) { if (x in 0 until N && y in 0 until N) g[y * N + x] = c or FLAG }

    private fun line(g: IntArray, x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
        var x = x0; var y = y0
        val dx = abs(x1 - x0); val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1; val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            put(g, x, y, c)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    private fun finish(g: IntArray, gx: Int, gy: Int): Sprite {
        val o = g.copyOf()
        for (y in 0 until N) for (x in 0 until N) {
            if (g[y * N + x] != 0) continue
            var near = false
            if (x > 0 && g[y * N + x - 1] != 0) near = true
            if (x < N - 1 && g[y * N + x + 1] != 0) near = true
            if (y > 0 && g[(y - 1) * N + x] != 0) near = true
            if (y < N - 1 && g[(y + 1) * N + x] != 0) near = true
            if (near) o[y * N + x] = OUT or FLAG
        }
        val runs = ArrayList<Run>()
        for (y in 0 until N) {
            var x = 0
            while (x < N) {
                val c = o[y * N + x]
                if (c == 0) { x++; continue }
                var e = x
                while (e + 1 < N && o[y * N + e + 1] == c) e++
                runs.add(Run(x, e, y, c))
                x = e + 1
            }
        }
        return Sprite(runs, gx, gy)
    }

    private const val WOOD = 0x9A6A3E; private const val WOOD_D = 0x7A5230; private const val GOLD = 0xFFD060; private const val GOLD_D = 0xD9A93A

    val axe: Sprite by lazy {
        val g = IntArray(N * N)
        line(g, 2, 13, 11, 4, WOOD); line(g, 3, 13, 12, 4, WOOD_D)
        line(g, 5, 11, 6, 10, GOLD)
        val rows = arrayOf(intArrayOf(1, 9, 12), intArrayOf(2, 8, 13), intArrayOf(3, 8, 14), intArrayOf(4, 9, 14),
            intArrayOf(5, 10, 14), intArrayOf(6, 11, 14), intArrayOf(7, 12, 14))
        for (r in rows) for (x in r[1]..r[2]) {
            val c = when { x == r[2] -> 0xF2F7FF; x == r[2] - 1 -> 0xD5DFEC; r[0] == 1 -> 0x8E99AB; else -> 0xA9B5C6 }
            put(g, x, r[0], c)
        }
        finish(g, 3, 12)
    }

    val sword: Sprite by lazy {
        val g = IntArray(N * N)
        line(g, 5, 9, 13, 1, 0xE8F4FF); line(g, 6, 9, 14, 1, 0x9ADBFF)
        put(g, 14, 0, 0xFFFFFF)
        line(g, 4, 10, 2, 12, WOOD); line(g, 5, 10, 3, 12, WOOD_D)
        line(g, 3, 7, 7, 11, GOLD); line(g, 3, 8, 7, 12, GOLD_D)
        put(g, 5, 9, 0xFF8CBF)
        put(g, 1, 13, 0xFF8CBF); put(g, 2, 13, 0xFF8CBF); put(g, 1, 12, 0xFFB3D6)
        finish(g, 3, 11)
    }

    val pick: Sprite by lazy {
        val g = IntArray(N * N)
        line(g, 2, 13, 12, 3, WOOD); line(g, 3, 13, 13, 3, WOOD_D)
        val pts = arrayOf(intArrayOf(2, 6), intArrayOf(3, 4), intArrayOf(5, 2), intArrayOf(8, 1), intArrayOf(11, 2), intArrayOf(13, 4), intArrayOf(14, 7))
        for (i in 0 until pts.size - 1) {
            line(g, pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], 0xC4CEDB)
            line(g, pts[i][0], pts[i][1] + 1, pts[i + 1][0], pts[i + 1][1] + 1, 0x8E99AB)
        }
        finish(g, 3, 12)
    }

    val staff: Sprite by lazy {
        val g = IntArray(N * N)
        line(g, 2, 13, 10, 5, 0x9B6FE0); line(g, 3, 13, 11, 5, 0x7B54C4)
        put(g, 9, 7, GOLD); put(g, 10, 6, GOLD); put(g, 11, 5, GOLD); put(g, 10, 7, GOLD_D)
        put(g, 12, 1, GOLD); put(g, 15, 4, GOLD); put(g, 12, 7, GOLD); put(g, 9, 4, GOLD)
        for (y in 0 until N) for (x in 0 until N) {
            val d = sqrt(((x - 12) * (x - 12) + (y - 4) * (y - 4)).toFloat())
            if (d <= 2.3f) put(g, x, y, if (d <= 1.1f) 0xFFFFFF else 0x7FE8FF)
        }
        finish(g, 3, 12)
    }

    fun get(id: Int): Sprite? = when (id) {
        Items.AXE -> axe; Items.SWORD -> sword; Items.PICK -> pick; Items.STAFF -> staff; else -> null
    }
}
