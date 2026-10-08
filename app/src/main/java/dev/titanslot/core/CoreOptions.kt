package dev.titanslot.core

import com.swordfish.libretrodroid.Variable
import dev.titanslot.data.GbPalette
import dev.titanslot.data.Settings

/**
 * Core variables. The video ones pin each core to the picture [Platform.nativeW] x
 * [Platform.nativeH] at square pixels, so integer scaling lands on whole pixels.
 */
object CoreOptions {

    fun variables(core: Core, platform: Platform, settings: Settings): Array<Variable> {
        val map = when (core) {
            Core.GAMBATTE -> gambatte(settings)
            Core.MGBA -> mgba(platform, settings)
        }
        return map.map { (k, v) -> Variable(k, v) }.toTypedArray()
    }

    private fun gambatte(settings: Settings): Map<String, String> {
        val palette = settings.gbPalette
        return buildMap {
            put("gambatte_gb_hwmode", "Auto")
            put("gambatte_gbc_color_correction", if (settings.colourCorrection) "GBC only" else "disabled")
            when (palette) {
                GbPalette.GREY -> put("gambatte_gb_colorization", "disabled")
                else -> {
                    put("gambatte_gb_colorization", "internal")
                    put("gambatte_gb_internal_palette", palette.gambatte)
                }
            }
        }
    }

    private fun mgba(platform: Platform, settings: Settings): Map<String, String> = buildMap {
        put("mgba_use_bios", "ON")
        put("mgba_skip_bios", "OFF")
        // Super Game Boy borders would turn a 160x144 picture into 256x224.
        put("mgba_sgb_borders", "OFF")
        put("mgba_color_correction", if (settings.colourCorrection) "Auto" else "OFF")
        put("mgba_idle_optimization", "Remove Known")
        if (platform == Platform.GB || platform == Platform.GBC) {
            put("mgba_gb_model", "Autodetect")
            put("mgba_gb_colors", settings.gbPalette.mgba)
        }
    }
}
