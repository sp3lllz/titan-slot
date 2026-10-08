package dev.titanslot.input

import android.view.KeyEvent

/**
 * What a key means. The first twelve are console buttons and carry the Android gamepad
 * keycode LibretroDroid turns into the matching RETRO_DEVICE_ID_JOYPAD_* (A is the right
 * face button, B the bottom one, Nintendo style). The last three are frontend hotkeys.
 */
enum class Button(val label: String, val gamepadKey: Int?) {
    UP("D-Pad Up", KeyEvent.KEYCODE_DPAD_UP),
    DOWN("D-Pad Down", KeyEvent.KEYCODE_DPAD_DOWN),
    LEFT("D-Pad Left", KeyEvent.KEYCODE_DPAD_LEFT),
    RIGHT("D-Pad Right", KeyEvent.KEYCODE_DPAD_RIGHT),
    A("A", KeyEvent.KEYCODE_BUTTON_A),
    B("B", KeyEvent.KEYCODE_BUTTON_B),
    X("X", KeyEvent.KEYCODE_BUTTON_X),
    Y("Y", KeyEvent.KEYCODE_BUTTON_Y),
    L("L Bumper", KeyEvent.KEYCODE_BUTTON_L1),
    R("R Bumper", KeyEvent.KEYCODE_BUTTON_R1),
    START("Start", KeyEvent.KEYCODE_BUTTON_START),
    SELECT("Select", KeyEvent.KEYCODE_BUTTON_SELECT),
    MENU("Menu", null),
    FAST_FORWARD("Fast Forward", null),
    REWIND("Rewind", null);

    val isConsole: Boolean get() = gamepadKey != null
}
