package com.cuboland.app

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File

class GameActivity : AppCompatActivity() {
    private lateinit var gl: GLSurfaceView
    private lateinit var world: World
    private lateinit var game: Game

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        world = World()
        if (!world.load(File(filesDir, "mundo.bin"))) world.generate(1234)
        else {
            val mark = File(filesDir, "arvores_v2")
            if (!mark.exists()) { world.replantTrees(1234); try { mark.writeText("1") } catch (_: Exception) {} }
        }
        File(filesDir, "arvores_v2").let { if (!it.exists()) try { it.writeText("1") } catch (_: Exception) {} }
        game = Game(world)
        getSharedPreferences("cfg", 0).getString("hb", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 8 }?.forEachIndexed { i, v -> if (Items.valid(v)) game.hotbar[i] = v }
        game.sens = getSharedPreferences("cfg", 0).getFloat("sens", 1f)
        gl = GLSurfaceView(this)
        gl.setEGLContextClientVersion(2)
        gl.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        gl.setRenderer(GameRenderer(game))
        val root = FrameLayout(this)
        root.addView(gl)
        root.addView(HudView(this, game) { finish() })
        setContentView(root)
    }

    override fun onPause() {
        super.onPause()
        gl.onPause()
        world.save(File(filesDir, "mundo.bin"))
        getSharedPreferences("cfg", 0).edit().putString("hb", game.hotbar.joinToString(",")).apply()
    }

    override fun onResume() {
        super.onResume()
        gl.onResume()
    }
}
