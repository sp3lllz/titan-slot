package dev.titanslot

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import dev.titanslot.app.GameController
import dev.titanslot.app.GameHost
import dev.titanslot.app.SystemStatus
import dev.titanslot.core.Core
import dev.titanslot.core.Platform
import dev.titanslot.data.Cart
import dev.titanslot.data.Library
import dev.titanslot.data.Paths
import dev.titanslot.data.Settings
import dev.titanslot.data.StateStore
import dev.titanslot.game.GameSession
import dev.titanslot.input.KeyMap
import dev.titanslot.input.KeyRouter
import dev.titanslot.ui.GameApp
import java.io.File
import kotlin.math.abs

/**
 * One cart in the slot. Runs in its own process (":game", as Lemuroid does): the emulator view
 * gets a fresh window, so its surface is always created, and the process exits when the cart
 * comes out, so every core starts from a clean slate.
 */
class GameActivity : ComponentActivity(), GameHost {
    private lateinit var controller: GameController
    private lateinit var keyMap: KeyMap
    private lateinit var status: SystemStatus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        preferSixtyHertz()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, android.R.anim.fade_in, 0)
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)
        }

        val platform = intent.getStringExtra(EXTRA_PLATFORM)?.let { runCatching { Platform.valueOf(it) }.getOrNull() }
        val rom = intent.getStringExtra(EXTRA_ROM)?.let(::File)
        if (platform == null || rom == null) {
            finish()
            return
        }
        val core = Core.byId(intent.getStringExtra(EXTRA_CORE))?.takeIf { it in platform.cores } ?: platform.defaultCore
        val settings = Settings(this)
        val paths = Paths.default()
        keyMap = KeyMap(this)
        status = SystemStatus(this)
        val session = GameSession(
            cart = Library.cartFor(platform, rom),
            core = core,
            fresh = intent.getBooleanExtra(EXTRA_FRESH, false),
            parent = lifecycle,
            settings = settings,
            paths = paths,
            store = StateStore(paths),
        )
        controller = GameController(this, settings, keyMap, status, session, lifecycleScope)
        onBackPressedDispatcher.addCallback(this) { controller.onBack() }
        setContent { GameApp(controller) }
    }

    override fun onResume() {
        super.onResume()
        if (!::controller.isInitialized) return
        hideSystemBars()
        status.start()
    }

    override fun onPause() {
        if (::controller.isInitialized) {
            controller.onPause()
            status.stop()
        }
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // Lint flags any override of ComponentActivity.dispatchKeyEvent as a restricted API; it is
    // the framework Activity method and overriding it is supported.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::controller.isInitialized) return super.dispatchKeyEvent(event)
        return KeyRouter.route(event, keyMap) { b, down -> controller.onKey(b, down) } ||
            super.dispatchKeyEvent(event)
    }

    override fun finishGame() {
        setResult(RESULT_OK)
        finish()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, android.R.anim.fade_out)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Saves are written before finishGame(); end the process so the next cart's core
        // loads into a clean one.
        if (isFinishing) Process.killProcess(Process.myPid())
    }

    private fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /**
     * The panel runs at 120 Hz; the consoles at about 60. Asking for the 60 Hz mode as the
     * window opens lets every emulated frame land on a vsync.
     */
    private fun preferSixtyHertz() {
        val display = display ?: return
        val current = display.mode
        val target = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .minByOrNull { abs(it.refreshRate - 60f) } ?: return
        window.attributes = window.attributes.apply { preferredDisplayModeId = target.modeId }
    }

    companion object {
        private const val EXTRA_PLATFORM = "platform"
        private const val EXTRA_ROM = "rom"
        private const val EXTRA_CORE = "core"
        private const val EXTRA_FRESH = "fresh"

        fun intent(context: Context, cart: Cart, core: Core, fresh: Boolean): Intent =
            Intent(context, GameActivity::class.java)
                .putExtra(EXTRA_PLATFORM, cart.platform.name)
                .putExtra(EXTRA_ROM, cart.rom.path)
                .putExtra(EXTRA_CORE, core.id)
                .putExtra(EXTRA_FRESH, fresh)
    }
}
