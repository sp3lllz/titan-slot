package dev.titanslot.input

import android.view.KeyEvent

/**
 * Every key from the Titan's keyboard (or a gamepad) is translated through the key map before
 * any view sees it. Repeats are dropped: holds are timed by the app itself. Keys the system
 * owns (Back, volume, power) are left alone.
 */
object KeyRouter {
    private val PASSTHROUGH = setOf(
        KeyEvent.KEYCODE_BACK,
        KeyEvent.KEYCODE_VOLUME_UP,
        KeyEvent.KEYCODE_VOLUME_DOWN,
        KeyEvent.KEYCODE_VOLUME_MUTE,
        KeyEvent.KEYCODE_POWER,
        KeyEvent.KEYCODE_HOME,
        KeyEvent.KEYCODE_APP_SWITCH,
        KeyEvent.KEYCODE_CAMERA,
    )

    /** Returns false when the system should handle [event], true when it was used up. */
    fun route(
        event: KeyEvent,
        keyMap: KeyMap,
        capture: (keyCode: Int, down: Boolean) -> Boolean = { _, _ -> false },
        onKey: (Button, Boolean) -> Unit,
    ): Boolean {
        val code = event.keyCode
        if (code in PASSTHROUGH) return false
        val down = when (event.action) {
            KeyEvent.ACTION_DOWN -> true
            KeyEvent.ACTION_UP -> false
            else -> return true
        }
        if (capture(code, down)) return true
        if (down && event.repeatCount > 0) return true
        val button = keyMap.buttonFor(code) ?: return true
        onKey(button, down)
        return true
    }
}
