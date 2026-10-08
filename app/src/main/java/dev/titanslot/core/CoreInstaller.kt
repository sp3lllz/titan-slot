package dev.titanslot.core

import android.content.Context
import java.io.File

/** Where a core is in being put on the phone, as the setup screen shows it. */
sealed interface CoreStatus {
    data object Missing : CoreStatus
    data object Waiting : CoreStatus
    /** [fraction] is null while the size isn't known yet. */
    data class Downloading(val fraction: Float?) : CoreStatus
    data object Installing : CoreStatus
    data object Installed : CoreStatus
    data class Failed(val reason: String) : CoreStatus
}

/**
 * Puts cores on the phone. Each build flavour has its own (see Delivery): Play builds get them
 * from Google Play as on-demand feature modules, direct builds download them from libretro.
 */
interface CoreInstaller {
    fun isInstalled(core: Core): Boolean

    /**
     * Installs whichever of [cores] are missing. [onStatus] may be called from any thread.
     * Returns true once every one of them is installed.
     */
    suspend fun install(cores: List<Core>, onStatus: (Core, CoreStatus) -> Unit): Boolean
}

/** Finds an installed core's library, wherever its delivery put it. */
object CoreFiles {
    /** Where direct builds keep downloaded cores. */
    fun dir(context: Context) = File(context.filesDir, "cores")

    /**
     * Cores are dlopen()ed by path. They are in the APK's native library folder when bundled
     * or installed as a Play split, under filesDir/splitcompat while Play's freshly downloaded
     * split is emulated until the next restart, or in [dir] when downloaded directly.
     */
    fun find(context: Context, core: Core): File? {
        // A split installed while the app runs moves the package, so ask for the current folder.
        val current = runCatching { context.packageManager.getApplicationInfo(context.packageName, 0).nativeLibraryDir }.getOrNull()
        return sequenceOf(current, context.applicationInfo.nativeLibraryDir)
            .filterNotNull()
            .distinct()
            .map(::File)
            .plus(dir(context))
            .plus(File(context.filesDir, "splitcompat"))
            .filter { it.isDirectory }
            .flatMap { it.walkBottomUp() }
            .firstOrNull { it.isFile && it.name == core.library }
    }

    /** True for a 64-bit little-endian ELF built for arm64, judged from its first 20 bytes. */
    fun isArm64Elf(header: ByteArray): Boolean {
        if (header.size < 20) return false
        val magic = header[0] == 0x7F.toByte() && header[1] == 'E'.code.toByte() &&
            header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte()
        val machine = (header[18].toInt() and 0xFF) or ((header[19].toInt() and 0xFF) shl 8)
        return magic && header[4] == 2.toByte() && header[5] == 1.toByte() && machine == EM_AARCH64
    }

    private const val EM_AARCH64 = 183
}
