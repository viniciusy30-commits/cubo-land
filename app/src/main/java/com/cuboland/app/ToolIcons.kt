package com.cuboland.app

import android.graphics.Bitmap
import android.util.LruCache

/** ícones das ferramentas: renderizados pelo próprio GL com o MESMO modelo 3D da mão.
 *  Ícones dos itens de decoração/mobília: renderizados sob demanda a partir do modelo real do item. */
object ToolIcons {
    val map = java.util.concurrent.ConcurrentHashMap<Int, Bitmap>()
    fun get(id: Int): Bitmap? = map[id]

    private val decoCache = LruCache<Int, Bitmap>(320)
    private val queue = java.util.concurrent.ConcurrentLinkedQueue<Int>()
    private val asked = java.util.concurrent.ConcurrentHashMap<Int, Boolean>()

    /** ícone pronto do item de decoração (ou null; nesse caso já pede pro GL gerar) */
    fun deco(id: Int): Bitmap? {
        val b = synchronized(decoCache) { decoCache.get(id) }
        if (b != null) return b
        if (asked.putIfAbsent(id, true) == null) queue.add(id)
        return null
    }
    fun hasPending(): Boolean = !queue.isEmpty()
    fun nextPending(): Int { val v = queue.poll(); return if (v == null) -1 else v }
    fun putDeco(id: Int, b: Bitmap) { synchronized(decoCache) { decoCache.put(id, b) }; asked.remove(id) }
}
