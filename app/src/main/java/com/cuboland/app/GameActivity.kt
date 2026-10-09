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
        val game = Game(world)
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
    }

    override fun onResume() {
        super.onResume()
        gl.onResume()
    }
}
