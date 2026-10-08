package dev.titanslot.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.swordfish.libretrodroid.ShaderConfig
import dev.titanslot.core.Core

enum class Scaling(val label: String) { INTEGER("Integer"), FIT("Fit") }

enum class Filter(val label: String, val shader: ShaderConfig) {
    SHARP("Sharp", ShaderConfig.Sharp),
    SMOOTH("Smooth", ShaderConfig.Default),
    LCD("LCD", ShaderConfig.LCD),
    CRT("CRT", ShaderConfig.CRT),
}

/** Palettes for original Game Boy games, named for each core's option values. */
enum class GbPalette(val label: String, val gambatte: String, val mgba: String) {
    DMG("DMG", "GB - DMG", "DMG Green"),
    POCKET("Pocket", "GB - Pocket", "GB Pocket"),
    LIGHT("Light", "GB - Light", "GB Light"),
    GREY("Grey", "", "Grayscale"),
}

/** Everything the quick menu changes, kept in SharedPreferences and observable from Compose. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var ffSpeed by intPref("ff_speed", 4)
    var ffSound by boolPref("ff_sound", false)
    var rewind by boolPref("rewind", true)
    var colourCorrection by boolPref("colour_correction", true)
    var scaling by enumPref("scaling", Scaling.INTEGER)
    var filter by enumPref("filter", Filter.SHARP)
    var gbPalette by enumPref("gb_palette", GbPalette.DMG)

    var lastShelf: String?
        get() = prefs.getString("last_shelf", null)
        set(v) = prefs.edit { putString("last_shelf", v) }

    var lastCart: String?
        get() = prefs.getString("last_cart", null)
        set(v) = prefs.edit { putString("last_cart", v) }

    fun coreFor(cart: Cart): Core {
        val chosen = Core.byId(prefs.getString("core:${cart.key}", null))
        return chosen?.takeIf { it in cart.platform.cores } ?: cart.platform.defaultCore
    }

    fun setCore(cart: Cart, core: Core) = prefs.edit { putString("core:${cart.key}", core.id) }

    private fun intPref(key: String, default: Int) = Pref(
        mutableIntStateOf(prefs.getInt(key, default)),
    ) { prefs.edit { putInt(key, it) } }

    private fun boolPref(key: String, default: Boolean) = Pref(
        mutableStateOf(prefs.getBoolean(key, default)),
    ) { prefs.edit { putBoolean(key, it) } }

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E): Pref<E> {
        val stored = prefs.getString(key, null)
        val value = enumValues<E>().firstOrNull { it.name == stored } ?: default
        return Pref(mutableStateOf(value)) { prefs.edit { putString(key, it.name) } }
    }

    class Pref<T>(
        private val state: androidx.compose.runtime.MutableState<T>,
        private val write: (T) -> Unit,
    ) {
        operator fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = state.value
        operator fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: T) {
            state.value = value
            write(value)
        }
    }

    companion object {
        val FF_SPEEDS = listOf(2, 3, 4, 6)
    }
}
