package dev.titanslot.data

import android.content.Context
import android.os.Environment
import android.os.storage.StorageManager
import androidx.core.content.edit
import java.io.File
import java.io.IOException

/**
 * Which volume the library is on, and the device-only flags that go with it. These prefs are
 * left out of cloud backup: a card's id and "setup is done" mean nothing on a new phone.
 */
class Storage(context: Context) {
    private val context = context.applicationContext
    private val prefs = context.getSharedPreferences("device", Context.MODE_PRIVATE)

    /** A place the library can live: the phone itself, or a microSD card. */
    data class Volume(val id: String, val name: String, val root: File, val removable: Boolean) {
        val free: Long get() = runCatching { root.usableSpace }.getOrDefault(0L)
    }

    /** Mounted volumes with an app folder, the phone first. */
    fun volumes(): List<Volume> {
        val manager = context.getSystemService(StorageManager::class.java)
        return context.getExternalFilesDirs(null).filterNotNull().mapNotNull { dir ->
            val volume = runCatching { manager.getStorageVolume(dir) }.getOrNull() ?: return@mapNotNull null
            if (volume.state != Environment.MEDIA_MOUNTED) return@mapNotNull null
            if (volume.isPrimary) {
                Volume(PHONE, "Phone storage", dir, removable = false)
            } else {
                val id = volume.uuid ?: return@mapNotNull null
                val name = volume.getDescription(context)?.takeIf { it.isNotBlank() } ?: "microSD card"
                Volume(id, name, dir, removable = volume.isRemovable)
            }
        }
    }

    var setupDone: Boolean
        get() = prefs.getBoolean("setup_done", false)
        set(v) = prefs.edit { putBoolean("setup_done", v) }

    /** The id of the volume holding the library ([PHONE] or a card's uuid). */
    val libraryVolume: String get() = prefs.getString("library_volume", PHONE) ?: PHONE

    /** The volume's name as it was when chosen, to say which card is missing. */
    val libraryName: String get() = prefs.getString("library_name", null) ?: "Phone storage"

    fun choose(volume: Volume) = prefs.edit {
        putString("library_volume", volume.id)
        putString("library_name", volume.name)
        putString("library_path", volume.root.path)
    }

    /** Saves, states, BIOS and config: always the phone's app folder. */
    fun dataRoot(): File = context.getExternalFilesDir(null) ?: File(context.filesDir, "external")

    fun paths(): Paths {
        val volume = volumes().firstOrNull { it.id == libraryVolume }
        val data = dataRoot()
        return when {
            volume != null -> Paths(volume.root, data)
            libraryVolume == PHONE -> Paths(data, data)
            else -> Paths(File(prefs.getString("library_path", null) ?: data.path), data, libraryAvailable = false)
        }
    }

    companion object {
        const val PHONE = "phone"

        /**
         * Moves Games/ and Labels/ from one library root to another, file by file. Nothing is
         * deleted until everything has been copied; on failure the copies are removed and the
         * originals stay put. [progress] gets (files done, files in all).
         */
        fun moveLibrary(from: File, to: File, progress: (Int, Int) -> Unit, cancelled: () -> Boolean): String? {
            val files = listOf("Games", "Labels")
                .map { File(from, it) }
                .flatMap { dir -> dir.walkTopDown().filter { it.isFile && !it.name.startsWith(".") }.toList() }
            val bytes = files.sumOf { it.length() }
            if (bytes > runCatching { to.usableSpace }.getOrDefault(0L)) return "Not enough space there"
            val copied = mutableListOf<File>()
            try {
                files.forEachIndexed { i, src ->
                    if (cancelled()) throw IOException("Cancelled")
                    progress(i, files.size)
                    val dest = File(to, src.relativeTo(from).path)
                    if (dest.isFile && dest.length() == src.length()) return@forEachIndexed
                    dest.parentFile?.mkdirs()
                    val tmp = File(dest.parentFile, ".moving-" + dest.name)
                    src.inputStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    if (tmp.length() != src.length() || !tmp.renameTo(dest)) {
                        tmp.delete()
                        throw IOException("Could not copy ${src.name}")
                    }
                    copied += dest
                }
            } catch (e: IOException) {
                copied.forEach { it.delete() }
                return e.message ?: "Could not move the library"
            }
            progress(files.size, files.size)
            files.forEach { it.delete() }
            listOf("Games", "Labels").forEach { name ->
                File(from, name).walkBottomUp().filter { it.isDirectory }.forEach { it.delete() }
            }
            return null
        }
    }
}
