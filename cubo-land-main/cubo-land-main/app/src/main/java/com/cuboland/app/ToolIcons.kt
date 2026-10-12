package com.cuboland.app

import android.graphics.Bitmap

/** ícones das ferramentas: renderizados pelo próprio GL com o MESMO modelo 3D da mão */
object ToolIcons {
    val map = java.util.concurrent.ConcurrentHashMap<Int, Bitmap>()
    fun get(id: Int): Bitmap? = map[id]
}
