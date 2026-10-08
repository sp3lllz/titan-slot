package dev.titanslot.data

import android.graphics.Bitmap
import dev.titanslot.core.Core
import java.io.File

data class SaveState(val file: File, val thumb: File, val time: Long, val auto: Boolean)

/**
 * Save states live in States/<platform>/<rom>/<core>/, since a state only loads in the core
 * that wrote it. "auto" is the one written on eject and when the app is backgrounded; the
 * rest are timestamped, one per SELECT + R.
 */
class StateStore(private val paths: Paths) {

    fun dir(cart: Cart, core: Core) = File(paths.states(cart.platform), "${cart.stem}/${core.id}")

    fun sram(cart: Cart) = File(paths.saves(cart.platform), "${cart.stem}.srm")

    fun list(cart: Cart, core: Core): List<SaveState> {
        val files = dir(cart, core).listFiles { f -> f.extension == "state" } ?: return emptyList()
        return files.map { f ->
            SaveState(
                file = f,
                thumb = File(f.parentFile, f.nameWithoutExtension + ".png"),
                time = f.lastModified(),
                auto = f.nameWithoutExtension == AUTO,
            )
        }.sortedByDescending { it.time }
    }

    /** What tapping A resumes: the newest state, auto or manual. */
    fun latest(cart: Cart, core: Core): SaveState? = list(cart, core).firstOrNull()

    fun write(cart: Cart, core: Core, bytes: ByteArray, thumb: Bitmap?, auto: Boolean): SaveState {
        val dir = dir(cart, core).apply { mkdirs() }
        val name = if (auto) AUTO else System.currentTimeMillis().toString()
        val file = File(dir, "$name.state")
        val tmp = File(dir, "$name.state.tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(file)
        val png = File(dir, "$name.png")
        if (thumb != null) {
            png.outputStream().use { thumb.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } else {
            png.delete()
        }
        return SaveState(file, png, file.lastModified(), auto)
    }

    fun delete(state: SaveState) {
        state.file.delete()
        state.thumb.delete()
    }

    fun readSram(cart: Cart): ByteArray? = sram(cart).takeIf { it.isFile }?.readBytes()

    fun writeSram(cart: Cart, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val file = sram(cart)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(file)
    }

    private companion object {
        const val AUTO = "auto"
    }
}
