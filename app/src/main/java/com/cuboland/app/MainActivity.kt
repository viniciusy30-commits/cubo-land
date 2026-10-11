package com.cuboland.app

import android.content.Intent
import android.os.Bundle

class MainActivity : CozyActivity() {
    private var scene: MenuScene? = null

    private fun continueLabel(): String {
        val last = Worlds.all(this).firstOrNull() ?: return "CRIE SEU PRIMEIRO MUNDO!"
        val nm = if (last.name.length > 14) last.name.take(13) + ".." else last.name
        return "CONTINUAR: $nm"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Look.load(this)   // o personagem do menu usa o visual salvo (antes ficava no padrão até abrir a tela Personagem)
        val ver = packageManager.getPackageInfo(packageName, 0).versionName ?: ""
        val s = MenuScene(
            this, ver, continueLabel(), Novidades.hasUnseen(this), Look.name(this),
            onPlay = { startActivity(Intent(this, WorldsActivity::class.java)) },
            onChar = { startActivity(Intent(this, CharacterActivity::class.java)) },
            onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
            onNews = { startActivity(Intent(this, SettingsActivity::class.java).putExtra("tab", 3)) },
            onUpdate = { Updater.check(this, true) })
        scene = s
        setContentView(s)
        Novidades.showIfNew(this)
        Updater.autoCheck(this)
    }

    override fun onResume() {
        super.onResume()
        Look.load(this)
        Novidades.showIfNew(this)
        scene?.let {
            it.setNews(Novidades.hasUnseen(this))
            it.setContinue(continueLabel())
            it.setName(Look.name(this))
            it.replay()
        }
    }
}
