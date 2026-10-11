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
import kotlin.math.max

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
        saveFile = Worlds.fileOf(this, info)
        val root = FrameLayout(this)
        val loading = LoadingView(this, info.name, flat)
        root.addView(loading)
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { if (hud != null) hud?.togglePause() }
        })
        val t0 = System.currentTimeMillis()
        Thread {
            World.configure(flat)
            val w = World()
            if (!w.load(saveFile)) { if (flat) w.generateFlat(info.seed) else w.generate(info.seed) }
            else if (!flat && info.file == "mundo.bin") {
                val mark = File(filesDir, "arvores_v3")
                if (!mark.exists()) { w.replantTrees(1234); try { mark.writeText("1") } catch (_: Exception) {} }
            }
            loading.setProgress(0.9f)
            val wait = max(0L, 1500L - (System.currentTimeMillis() - t0))   // mostra a telinha por pelo menos 1,5s
            loading.postDelayed({
                if (isFinishing || isDestroyed) return@postDelayed
                world = w
                startGame(root, info, loading)
            }, wait)
        }.start()
    }

    private fun startGame(root: FrameLayout, info: Worlds.Info, loading: LoadingView) {
        game = Game(world)
        Look.load(this)
        getSharedPreferences("cfg", 0).getString("hb", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
            ?.takeIf { it.size == 8 }?.forEachIndexed { i, v -> if (v == 0 || Items.valid(v)) game.hotbar[i] = v }
        game.sens = getSharedPreferences("cfg", 0).getFloat("sens", 1f)
        game.creative = info.creative
        gl = GLSurfaceView(this)
        gl.setEGLContextClientVersion(2)
        gl.setEGLConfigChooser(8, 8, 8, 8, 24, 0)
        gl.setRenderer(GameRenderer(game))
        root.addView(gl, 0)
        gl.post {   // renderiza em ~80% da resolução em telas grandes: bem mais fluido, o HUD continua nítido
            val w = gl.width; val h = gl.height
            val q = getSharedPreferences("cfg", 0).getInt("res", 0)   // 0 auto, 1 alta, 2 média, 3 leve
            val f = when (q) { 1 -> 1f; 2 -> 0.8f; 3 -> 0.6f; else -> if (w > 1500) 0.8f else 1f }
            if (f < 1f) gl.holder.setFixedSize((w * f).toInt(), (h * f).toInt())
        }
        val h = HudView(this, game, info.name, { cr -> info.creative = cr; Worlds.update(this, info) }) { finish() }
        hud = h
        root.addView(h, 1)
        loading.setProgress(1f)
        loading.postDelayed({ loading.animate().alpha(0f).setDuration(450).withEndAction { root.removeView(loading) }.start() }, 500)
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
