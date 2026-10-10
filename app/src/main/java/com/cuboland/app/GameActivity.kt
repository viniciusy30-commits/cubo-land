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
    private var flat = false
    private lateinit var saveFile: File
    private var hud: HudView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        val info = Worlds.get(this, intent.getStringExtra("wid") ?: "")
        if (info == null) { finish(); return }
        Worlds.touch(this, info.id)
        flat = info.type == Worlds.FLAT
        World.configure(flat)
        world = World()
        saveFile = Worlds.fileOf(this, info)
        if (!world.load(saveFile)) { if (flat) world.generateFlat(info.seed) else world.generate(info.seed) }
        else if (!flat && info.file == "mundo.bin") {
            val mark = File(filesDir, "arvores_v3")
            if (!mark.exists()) { world.replantTrees(1234); try { mark.writeText("1") } catch (_: Exception) {} }
        }
        game = Game(world)
        Look.load(this)
        getSharedPreferences("cfg", 0).getString("hb", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 8 }?.forEachIndexed { i, v -> if (Items.valid(v)) game.hotbar[i] = v }
        game.sens = getSharedPreferences("cfg", 0).getFloat("sens", 1f)
        game.creative = info.creative
        gl = GLSurfaceView(this)
        gl.setEGLContextClientVersion(2)
        gl.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        gl.setRenderer(GameRenderer(game))
        val root = FrameLayout(this)
        root.addView(gl)
        gl.post {   // renderiza em ~80% da resolução em telas grandes: bem mais fluido, o HUD continua nítido
            val w = gl.width; val h = gl.height
            val q = getSharedPreferences("cfg", 0).getInt("res", 0)   // 0 auto, 1 alta, 2 média, 3 leve
            val f = when (q) { 1 -> 1f; 2 -> 0.8f; 3 -> 0.6f; else -> if (w > 1500) 0.8f else 1f }
            if (f < 1f) gl.holder.setFixedSize((w * f).toInt(), (h * f).toInt())
        }
        val h = HudView(this, game, info.name) { finish() }
        hud = h
        root.addView(h)
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { hud?.togglePause() }
        })
    }

    override fun onPause() {
        super.onPause()
        if (!::gl.isInitialized) return
        gl.onPause()
        world.save(saveFile)
        getSharedPreferences("cfg", 0).edit().putString("hb", game.hotbar.joinToString(",")).apply()
    }

    override fun onResume() {
        super.onResume()
        if (::gl.isInitialized) gl.onResume()
    }
}
