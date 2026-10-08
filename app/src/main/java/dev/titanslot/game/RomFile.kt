package dev.titanslot.game

import dev.titanslot.data.Cart
import java.io.File
import java.util.zip.ZipFile

/** Cores want a bare ROM, so zipped ones are unpacked into the cache once and reused. */
object RomFile {

    fun resolve(cart: Cart, cacheDir: File): File {
        if (!cart.rom.extension.equals("zip", true)) return cart.rom
        val dir = File(cacheDir, "roms").apply { mkdirs() }
        ZipFile(cart.rom).use { zip ->
            val entry = zip.entries().asSequence().firstOrNull {
                !it.isDirectory && it.name.substringAfterLast('.').lowercase() in cart.platform.extensions
            } ?: error("No ${cart.platform.title} ROM inside ${cart.rom.name}")
            val out = File(dir, cart.stem + "." + entry.name.substringAfterLast('.').lowercase())
            val stamp = File(dir, out.name + ".src")
            val source = "${cart.rom.path}|${cart.rom.length()}|${cart.rom.lastModified()}"
            if (out.isFile && stamp.isFile && stamp.readText() == source) return out
            // Keep the cache to the ROM being played; DS images run to hundreds of megabytes.
            dir.listFiles()?.forEach { it.delete() }
            zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
            stamp.writeText(source)
            return out
        }
    }
}
