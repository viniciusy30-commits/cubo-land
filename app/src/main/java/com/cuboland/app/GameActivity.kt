package com.cuboland.app

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
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
    private var test = false   // galeria de blocos
    private lateinit var saveFile: File
    private var hud: HudView? = null
    private var galPanel: GalleryPanel? = null   // painel de categorias da galeria de blocos
    private var galMenu: List<Pair<World.GalCat, List<World.GalSec>>>? = null
    private var galWasPaused = false
    private var tv: TvOverlay? = null   // janela da TV (navegador) enquanto estiver ligada

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
        test = info.type == Worlds.TEST
        flat = info.type == Worlds.FLAT || test
        saveFile = Worlds.fileOf(this, info)
        val root = FrameLayout(this)
        val loading = LoadingView(this, info.name, flat)
        root.addView(loading)
        setContentView(root)
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { val t = tv; if (t != null && t.onBack()) { } else if (galPanel?.isOpen == true) galPanel?.onBack() else if (hud != null) hud?.togglePause() }
        })
        val t0 = System.currentTimeMillis()
        Thread {
            World.configure(flat, test)
            val w = World()
            if (test) { w.generateTest(); galMenu = w.galleryMenu() }   // sempre gerada de novo (só o saguão: os blocos vêm pelo painel de categorias)
            else if (!w.load(saveFile)) { if (flat) w.generateFlat(info.seed) else w.generate(info.seed) }
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
        game.creative = info.creative || test
        game.noMobs = test
        gl = GLSurfaceView(this)
        gl.setEGLContextClientVersion(2)
        gl.setEGLConfigChooser(8, 8, 8, 8, 24, 8)
        gl.setRenderer(GameRenderer(game, this))
        root.addView(gl, 0)
        gl.post {   // renderiza em ~80% da resolução em telas grandes: bem mais fluido, o HUD continua nítido
            val w = gl.width; val h = gl.height
            val q = getSharedPreferences("cfg", 0).getInt("res", 0)   // 0 auto, 1 alta, 2 média, 3 leve
            val f = when (q) { 1 -> 1f; 2 -> 0.8f; 3 -> 0.6f; else -> if (w > 1500) 0.8f else 1f }
            if (f < 1f) gl.holder.setFixedSize((w * f).toInt(), (h * f).toInt())
        }
        val h = HudView(this, game, info.name, { cr -> info.creative = cr; Worlds.update(this, info) }) { finish() }
        hud = h
        h.onUseTv = { x, y, z -> openTv(root, x, y, z) }
        h.onTvOff = { tv?.close() }
        h.onTvSized = { tv?.relayout() }
        h.onTvZoom = { tv?.zoom() }
        root.addView(h, 1)
        if (test) setupGalleryPanel(root, h)
        loading.setProgress(1f)
        loading.postDelayed({ loading.animate().alpha(0f).setDuration(450).withEndAction { root.removeView(loading) }.start() }, 500)
    }

    /** painel de categorias da galeria: abre ao entrar; escolher regera o mapa só com a categoria/seção pedida */
    private fun setupGalleryPanel(root: FrameLayout, h: HudView) {
        val menu = galMenu ?: return
        val panel = GalleryPanel(this, menu,
            onOpen = { galWasPaused = game.paused; game.paused = true; h.releaseInput() },
            onClose = { game.paused = galWasPaused },
            onPick = { cat, sec, extra ->
                val w = world
                Thread {
                    w.generateTest(cat, sec, extra)
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        game.respawn()
                        galPanel?.view?.postDelayed({ galPanel?.close() }, 1400)   // dá tempo do mapa novo aparecer por baixo
                    }
                }.start()
            })
        galPanel = panel
        val at = root.childCount - 1   // abaixo da telinha de carregamento, que é a última
        root.addView(panel.fab, at, panel.fabParams())
        root.addView(panel.view, at + 1, FrameLayout.LayoutParams(-1, -1))
        panel.open()
    }

    /** liga a TV: abre o navegador numa janela por cima do jogo */
    private fun openTv(root: FrameLayout, x: Int, y: Int, z: Int) {
        if (tv != null || !::game.isInitialized) return
        try {
            hud?.tvOn = true
            tv = TvOverlay(this, root, game, x, y, z, { hide ->
                hud?.let { if (hide) { it.releaseInput(); it.visibility = View.GONE } else it.visibility = View.VISIBLE }
            }) { tv = null; hud?.tvOn = false }
            tv?.zoom()   // ao ligar, a câmera já dá zoom na tela
        } catch (e: Exception) {   // aparelho sem WebView instalado/atualizado
            tv = null; hud?.tvOn = false
            Toast.makeText(this, "Navegador indisponível neste aparelho (instale/atualize o Android System WebView)", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        tv?.close()
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        tv?.onPause()
        if (!::gl.isInitialized) return
        gl.onPause()
        if (!test) world.save(saveFile)
        getSharedPreferences("cfg", 0).edit().putString("hb", game.hotbar.joinToString(",")).apply()
    }

    override fun onResume() {
        super.onResume()
        tv?.onResume()
        if (::gl.isInitialized) gl.onResume()
    }
}
