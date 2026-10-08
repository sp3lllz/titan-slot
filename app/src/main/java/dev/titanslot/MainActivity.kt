package dev.titanslot

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import dev.titanslot.app.AppState
import dev.titanslot.app.Host
import dev.titanslot.app.Sfx
import dev.titanslot.app.SystemStatus
import dev.titanslot.core.Core
import dev.titanslot.data.Cart
import dev.titanslot.data.Settings
import dev.titanslot.input.KeyMap
import dev.titanslot.input.KeyRouter
import dev.titanslot.ui.TitanSlotApp
import java.io.InputStream

/** The shelf. Games run in [GameActivity], launched once a cart is all the way in. */
class MainActivity : ComponentActivity(), Host {
    private lateinit var app: AppState
    private lateinit var sfx: Sfx

    private val game = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        app.onGameFinished()
    }

    private var onPicked: ((Uri) -> Unit)? = null
    private val picker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { onPicked?.invoke(it) }
        onPicked = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Some launchers start a second shelf on top of a running game instead of bringing
        // the task forward. Step aside so the game underneath comes back.
        if (!isTaskRoot && intent?.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER)) {
            finish()
            return
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        sfx = Sfx(this)
        app = AppState(
            host = this,
            settings = Settings(this),
            keyMap = KeyMap(this),
            status = SystemStatus(this),
            sfx = sfx,
            scope = lifecycleScope,
            cacheDir = cacheDir,
        )
        onBackPressedDispatcher.addCallback(this) { app.onBack() }
        setContent { TitanSlotApp(app) }
    }

    override fun onResume() {
        super.onResume()
        if (!::app.isInitialized) return
        hideSystemBars()
        app.status.start()
        app.onResume()
    }

    override fun onPause() {
        if (::app.isInitialized) {
            app.onPause()
            app.status.stop()
        }
        super.onPause()
    }

    override fun onDestroy() {
        if (::sfx.isInitialized) sfx.release()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // Lint flags any override of ComponentActivity.dispatchKeyEvent as a restricted API; it is
    // the framework Activity method and overriding it is supported.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::app.isInitialized) return super.dispatchKeyEvent(event)
        if (app.editKey(event)) return true
        return KeyRouter.route(event, app.keyMap, app::captureKey, app::onKey) || super.dispatchKeyEvent(event)
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    // ---- Host ----------------------------------------------------------------------------

    override fun requestStorage() {
        val intent = Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, "package:$packageName".toUri())
        runCatching { startActivity(intent) }.onFailure {
            startActivity(Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        }
    }

    override fun launchGame(cart: Cart, core: Core, fresh: Boolean) {
        game.launch(
            GameActivity.intent(this, cart, core, fresh),
            ActivityOptionsCompat.makeCustomAnimation(this, android.R.anim.fade_in, 0),
        )
    }

    override fun moveToBack() {
        moveTaskToBack(true)
    }

    override fun pickImage(onPicked: (Uri) -> Unit) {
        this.onPicked = onPicked
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    override fun openStream(uri: Uri): InputStream? = contentResolver.openInputStream(uri)
}
