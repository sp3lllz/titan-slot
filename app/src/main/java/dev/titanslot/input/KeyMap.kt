package dev.titanslot.input

import android.content.Context
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit

/**
 * Keyboard bindings. Out of the box they are laid out for the Titan 2 Elite's QWERTY
 * keyboard; every one can be rebound from Settings > Controls.
 *
 *   W A S D  d-pad          K  A        L  B
 *   Z        L bumper       M  R bumper
 *   V        Start          B  Select
 *   I        X              O  Y
 *   P        Menu (and the system Back gesture)
 *   N        fast-forward   X  rewind
 *
 * A Bluetooth gamepad works too: its buttons are always mapped, see [GAMEPAD].
 */
class KeyMap(context: Context) {
    private val prefs = context.getSharedPreferences("keymap", Context.MODE_PRIVATE)

    var bindings: Map<Button, List<Int>> by mutableStateOf(load())
        private set

    private var lookup: Map<Int, Button> = index(bindings)

    fun buttonFor(keyCode: Int): Button? = lookup[keyCode] ?: GAMEPAD[keyCode]

    /** Binds [keyCode] to [button] alone, taking it away from whatever had it before. */
    fun bind(button: Button, keyCode: Int) {
        val next = bindings.mapValues { (_, keys) -> keys - keyCode }.toMutableMap()
        next[button] = listOf(keyCode)
        commit(next)
    }

    fun resetToDefaults() = commit(TITAN_DEFAULTS)

    private fun commit(next: Map<Button, List<Int>>) {
        bindings = next
        lookup = index(next)
        prefs.edit {
            clear()
            next.forEach { (button, keys) -> putString(button.name, keys.joinToString(",")) }
        }
    }

    private fun load(): Map<Button, List<Int>> {
        if (prefs.all.isEmpty()) return TITAN_DEFAULTS
        return Button.entries.associateWith { button ->
            prefs.getString(button.name, null)
                ?.split(',')
                ?.mapNotNull { it.trim().toIntOrNull() }
                ?: TITAN_DEFAULTS[button].orEmpty()
        }
    }

    private fun index(map: Map<Button, List<Int>>): Map<Int, Button> =
        buildMap { map.forEach { (button, keys) -> keys.forEach { put(it, button) } } }

    companion object {
        val TITAN_DEFAULTS: Map<Button, List<Int>> = mapOf(
            Button.UP to listOf(KeyEvent.KEYCODE_W),
            Button.LEFT to listOf(KeyEvent.KEYCODE_A),
            Button.DOWN to listOf(KeyEvent.KEYCODE_S),
            Button.RIGHT to listOf(KeyEvent.KEYCODE_D),
            Button.A to listOf(KeyEvent.KEYCODE_K),
            Button.B to listOf(KeyEvent.KEYCODE_L),
            Button.X to listOf(KeyEvent.KEYCODE_I),
            Button.Y to listOf(KeyEvent.KEYCODE_O),
            Button.L to listOf(KeyEvent.KEYCODE_Z),
            Button.R to listOf(KeyEvent.KEYCODE_M),
            Button.START to listOf(KeyEvent.KEYCODE_V),
            Button.SELECT to listOf(KeyEvent.KEYCODE_B),
            Button.MENU to listOf(KeyEvent.KEYCODE_P),
            Button.FAST_FORWARD to listOf(KeyEvent.KEYCODE_N),
            Button.REWIND to listOf(KeyEvent.KEYCODE_X),
        )

        /** Gamepad and arrow keys, always on so a controller works without setup. */
        val GAMEPAD: Map<Int, Button> = mapOf(
            KeyEvent.KEYCODE_DPAD_UP to Button.UP,
            KeyEvent.KEYCODE_DPAD_DOWN to Button.DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT to Button.LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT to Button.RIGHT,
            KeyEvent.KEYCODE_BUTTON_A to Button.A,
            KeyEvent.KEYCODE_BUTTON_B to Button.B,
            KeyEvent.KEYCODE_BUTTON_X to Button.X,
            KeyEvent.KEYCODE_BUTTON_Y to Button.Y,
            KeyEvent.KEYCODE_BUTTON_L1 to Button.L,
            KeyEvent.KEYCODE_BUTTON_R1 to Button.R,
            KeyEvent.KEYCODE_BUTTON_L2 to Button.REWIND,
            KeyEvent.KEYCODE_BUTTON_R2 to Button.FAST_FORWARD,
            KeyEvent.KEYCODE_BUTTON_START to Button.START,
            KeyEvent.KEYCODE_BUTTON_SELECT to Button.SELECT,
            KeyEvent.KEYCODE_BUTTON_MODE to Button.MENU,
        )

        fun keyName(keyCode: Int): String =
            KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").replace('_', ' ')
    }
}
