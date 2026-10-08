package dev.titanslot.data

import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/** The few header fields the shelf needs to pick a cart's plastic. */
sealed interface RomHeader {
    data class Gb(
        val title: String,
        val code: String,
        val cgbFlag: Int,
        val overseas: Boolean,
    ) : RomHeader {
        val colourOnly get() = cgbFlag == 0xC0
        val dualMode get() = cgbFlag == 0x80
    }

    data class Gba(val title: String, val code: String) : RomHeader

    companion object {
        private const val GB_END = 0x150
        private const val GBA_END = 0xC0

        fun read(rom: File, extensions: Set<String>): RomHeader? = runCatching {
            if (rom.extension.equals("zip", true)) {
                ZipFile(rom).use { zip ->
                    val entry = zip.entries().asSequence().firstOrNull {
                        !it.isDirectory && it.name.substringAfterLast('.').lowercase() in extensions
                    } ?: return null
                    zip.getInputStream(entry).use { parse(entry.name.substringAfterLast('.'), it) }
                }
            } else {
                rom.inputStream().use { parse(rom.extension, it) }
            }
        }.getOrNull()

        private fun parse(extension: String, input: InputStream): RomHeader? {
            return when (extension.lowercase()) {
                "gb", "gbc", "sgb" -> gb(input.readUpTo(GB_END))
                "gba", "agb" -> gba(input.readUpTo(GBA_END))
                else -> null
            }
        }

        private fun gb(b: ByteArray): Gb? {
            if (b.size < GB_END) return null
            val cgb = b[0x143].toInt() and 0xFF
            // Carts with a CGB flag shorten the title to 11 bytes and use the rest for a code.
            val titleEnd = if (cgb and 0x80 != 0) 0x13F else 0x144
            val title = ascii(b, 0x134, titleEnd)
            val code = if (cgb and 0x80 != 0) ascii(b, 0x13F, 0x143) else ""
            return Gb(title, code, cgb, overseas = b[0x14A].toInt() != 0)
        }

        private fun gba(b: ByteArray): Gba? {
            if (b.size < GBA_END) return null
            return Gba(ascii(b, 0xA0, 0xAC), ascii(b, 0xAC, 0xB0))
        }

        private fun ascii(b: ByteArray, from: Int, to: Int): String = buildString {
            for (i in from until to) {
                val c = b[i].toInt() and 0xFF
                if (c == 0) break
                if (c in 0x20..0x7E) append(c.toChar())
            }
        }.trim()

        private fun InputStream.readUpTo(n: Int): ByteArray {
            val out = ByteArray(n)
            var read = 0
            while (read < n) {
                val r = read(out, read, n - read)
                if (r < 0) break
                read += r
            }
            return out.copyOf(read)
        }
    }
}
