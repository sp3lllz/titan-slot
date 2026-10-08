package dev.titanslot.core

/**
 * The libretro cores bundled in the APK. [library] is the file name inside the app's
 * nativeLibraryDir, as written by scripts/fetch-cores.sh.
 */
enum class Core(val id: String, val title: String, val library: String) {
    GAMBATTE("gambatte", "Gambatte", "libgambatte_libretro_android.so"),
    MGBA("mgba", "mGBA", "libmgba_libretro_android.so"),
    MELONDS("melonds", "melonDS", "libmelonds_libretro_android.so"),
    FCEUMM("fceumm", "FCEUmm", "libfceumm_libretro_android.so"),
    SNES9X("snes9x", "Snes9x", "libsnes9x_libretro_android.so");

    companion object {
        fun byId(id: String?): Core? = entries.firstOrNull { it.id == id }
    }
}
