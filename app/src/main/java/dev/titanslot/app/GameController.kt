package dev.titanslot.app

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.titanslot.data.SaveState
import dev.titanslot.data.Settings
import dev.titanslot.game.GameSession
import dev.titanslot.input.Button
import dev.titanslot.input.KeyMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class GameOverlay { PAUSE, SWITCHER }

/** What the game activity does for the controller. */
interface GameHost {
    /** The cart is out (saved, or abandoned): go back to the shelf. */
    fun finishGame()
}

/**
 * Everything that happens with a cart in the slot, after slot's in-game controls:
 * tap MENU for the pause menu, hold it to eject, double-tap it for the save state switcher,
 * SELECT + R / L to save / load, hold or double-tap fast-forward, hold rewind.
 */
class GameController(
    private val host: GameHost,
    val settings: Settings,
    val keyMap: KeyMap,
    val status: SystemStatus,
    val session: GameSession,
    private val scope: CoroutineScope,
) {
    var overlay by mutableStateOf<GameOverlay?>(null)
        private set
    var toast by mutableStateOf<Toast?>(null)
        private set
    var pauseRow by mutableStateOf(PauseRow.RESUME)
        private set
    var switcher by mutableStateOf<List<SaveState>>(emptyList())
        private set
    var switcherIndex by mutableIntStateOf(0)
        private set

    private var menuJob: Job? = null
    private var menuTapJob: Job? = null
    private var menuHeld = false
    private var menuIgnoreUp = false
    private var lastFfUp = 0L
    private var ffIgnoreUp = false
    private var selectPending = false
    private var selectSent = false
    private var selectCombo = false
    private val swallowed = mutableSetOf<Button>()
    private var leaving = false

    fun toast(text: String) {
        toast = Toast(text)
    }

    fun onPause() {
        session.releaseAll()
    }

    fun onKey(b: Button, down: Boolean) {
        val s = session
        if (leaving) return
        if (s.error != null) {
            if (down && (b == Button.A || b == Button.B || b == Button.MENU)) abandon()
            return
        }
        if (!s.ready) return
        if (overlay != null && b != Button.MENU) {
            if (down) overlayKey(b)
            return
        }
        when (b) {
            Button.MENU -> menuKey(down)
            Button.FAST_FORWARD -> fastForwardKey(down)
            Button.REWIND -> s.holdRewind(down)
            Button.SELECT -> selectKey(down)
            else -> {
                if (!down && swallowed.remove(b)) return
                if (down && selectPending && !selectSent) {
                    when (b) {
                        Button.R -> {
                            selectCombo = true
                            swallowed += b
                            quickSave()
                            return
                        }
                        Button.L -> {
                            selectCombo = true
                            swallowed += b
                            quickLoad()
                            return
                        }
                        else -> {
                            // Not a shortcut after all: SELECT goes to the game first.
                            selectSent = true
                            s.press(Button.SELECT, true)
                        }
                    }
                }
                s.press(b, down)
            }
        }
    }

    /** The system Back gesture: closes whatever is open, else acts as a tap of MENU. */
    fun onBack() {
        when {
            leaving -> Unit
            session.error != null -> abandon()
            !session.ready -> Unit
            overlay != null -> closeOverlay()
            else -> openPause()
        }
    }

    // ---- shortcuts -----------------------------------------------------------------------

    private fun selectKey(down: Boolean) {
        if (down) {
            selectPending = true
            selectSent = false
            selectCombo = false
            return
        }
        when {
            selectSent -> session.press(Button.SELECT, false)
            !selectCombo -> scope.launch {
                session.press(Button.SELECT, true)
                delay(TAP_MS)
                session.press(Button.SELECT, false)
            }
        }
        selectPending = false
        selectSent = false
    }

    private fun menuKey(down: Boolean) {
        if (down) {
            if (overlay != null) {
                closeOverlay()
                menuIgnoreUp = true
                return
            }
            menuHeld = false
            menuJob?.cancel()
            menuJob = scope.launch {
                delay(MENU_HOLD_MS)
                menuHeld = true
                menuTapJob?.cancel()
                eject()
            }
            return
        }
        if (menuIgnoreUp) {
            menuIgnoreUp = false
            return
        }
        menuJob?.cancel()
        if (menuHeld) return
        if (menuTapJob?.isActive == true) {
            menuTapJob?.cancel()
            openSwitcher()
        } else {
            menuTapJob = scope.launch {
                delay(DOUBLE_TAP_MS)
                openPause()
            }
        }
    }

    private fun fastForwardKey(down: Boolean) {
        val now = SystemClock.uptimeMillis()
        if (down) {
            if (session.fastForwardLocked) {
                session.setFastForward(false)
                ffIgnoreUp = true
                return
            }
            if (now - lastFfUp < DOUBLE_TAP_MS) {
                session.setFastForward(true, locked = true)
                ffIgnoreUp = true
                return
            }
            session.setFastForward(true)
        } else {
            if (ffIgnoreUp) {
                ffIgnoreUp = false
                return
            }
            session.setFastForward(false)
            lastFfUp = now
        }
    }

    private fun quickSave() = scope.launch {
        toast(if (session.saveState() != null) "State Saved" else "Could Not Save")
    }

    private fun quickLoad() = scope.launch {
        val latest = session.states().firstOrNull()
        toast(
            when {
                latest == null -> "No Save States"
                session.load(latest) -> "State Loaded"
                else -> "Could Not Load"
            },
        )
    }

    // ---- eject -----------------------------------------------------------------------------

    /** Writes the battery save and a resume state, then hands back to the shelf. */
    fun eject() {
        if (leaving || !session.ready) return
        leaving = true
        overlay = null
        scope.launch {
            session.eject()
            host.finishGame()
        }
    }

    /** A cart that never got going (bad ROM, core error): pull it without saving. */
    private fun abandon() {
        if (leaving) return
        leaving = true
        session.close()
        host.finishGame()
    }

    // ---- overlays --------------------------------------------------------------------------

    private fun openPause() {
        if (overlay != null || leaving) return
        session.pause(true)
        pauseRow = PauseRow.RESUME
        overlay = GameOverlay.PAUSE
    }

    private fun openSwitcher() {
        val states = session.states()
        if (states.isEmpty()) {
            toast("No Save States")
            return
        }
        session.pause(true)
        switcher = states
        switcherIndex = 0
        overlay = GameOverlay.SWITCHER
    }

    private fun closeOverlay() {
        overlay = null
        session.pause(false)
    }

    private fun overlayKey(b: Button) {
        when (overlay) {
            GameOverlay.PAUSE -> when (b) {
                Button.UP -> pauseRow = PauseRow.entries[(pauseRow.ordinal - 1).mod(PauseRow.entries.size)]
                Button.DOWN -> pauseRow = PauseRow.entries[(pauseRow.ordinal + 1).mod(PauseRow.entries.size)]
                Button.A -> activatePause(pauseRow)
                Button.B -> closeOverlay()
                else -> Unit
            }
            GameOverlay.SWITCHER -> when (b) {
                Button.LEFT -> switcherIndex = (switcherIndex - 1).coerceAtLeast(0)
                Button.RIGHT -> switcherIndex = (switcherIndex + 1).coerceAtMost(switcher.lastIndex)
                Button.A -> switcher.getOrNull(switcherIndex)?.let(::loadFromSwitcher)
                Button.Y -> switcher.getOrNull(switcherIndex)?.let(::deleteFromSwitcher)
                Button.B -> closeOverlay()
                else -> Unit
            }
            null -> Unit
        }
    }

    fun activatePause(row: PauseRow) {
        pauseRow = row
        when (row) {
            PauseRow.RESUME -> closeOverlay()
            PauseRow.SAVE_STATE -> {
                closeOverlay()
                quickSave()
            }
            PauseRow.LOAD_STATE -> {
                closeOverlay()
                quickLoad()
            }
            PauseRow.STATES -> {
                overlay = null
                openSwitcher()
                if (overlay == null) session.pause(false)
            }
            PauseRow.RESET -> {
                closeOverlay()
                session.reset()
            }
            PauseRow.EJECT -> eject()
        }
    }

    fun selectSwitcher(i: Int) {
        if (i == switcherIndex) switcher.getOrNull(i)?.let(::loadFromSwitcher)
        else switcherIndex = i.coerceIn(0, switcher.lastIndex.coerceAtLeast(0))
    }

    private fun loadFromSwitcher(state: SaveState) {
        scope.launch {
            closeOverlay()
            toast(if (session.load(state)) "State Loaded" else "Could Not Load")
        }
    }

    private fun deleteFromSwitcher(state: SaveState) {
        session.deleteState(state)
        switcher = switcher - state
        switcherIndex = switcherIndex.coerceAtMost(switcher.lastIndex).coerceAtLeast(0)
        toast("State Deleted")
        if (switcher.isEmpty()) closeOverlay()
    }

    private companion object {
        const val MENU_HOLD_MS = 650L
        const val DOUBLE_TAP_MS = 280L
        const val TAP_MS = 70L
    }
}
