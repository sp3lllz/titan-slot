package dev.titanslot.core

/**
 * The libretro cores Titan Slot runs. They aren't in the base APK: the setup wizard installs
 * them (see [CoreInstaller]) and [CoreFiles] finds them by [library], the file name
 * scripts/fetch-cores.sh gives them.
 *
 * [module] is the on-demand feature module that carries the core in Play builds.
 */
enum class Core(val id: String, val title: String, val library: String, val systems: String) {
    GAMBATTE("gambatte", "Gambatte", "libgambatte_libretro_android.so", "Game Boy and Game Boy Color"),
    MGBA("mgba", "mGBA", "libmgba_libretro_android.so", "Game Boy Advance");

    val module: String get() = "core_$id"

    companion object {
        fun byId(id: String?): Core? = entries.firstOrNull { it.id == id }
    }
}
