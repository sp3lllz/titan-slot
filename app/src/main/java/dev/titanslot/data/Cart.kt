package dev.titanslot.data

import dev.titanslot.core.Platform
import java.io.File

data class Cart(
    val platform: Platform,
    val rom: File,
    /** The ROM's file name without extension; labels and saves are keyed by it. */
    val stem: String,
    /** [stem] without region and dump tags, for generated labels and the shelf caption. */
    val title: String,
    val label: File?,
    val shell: Shell,
    val shape: CartShape,
    val fit: ArtFit = ArtFit.FILL,
    /** The label's modification time, so a re-scraped or replaced label redraws the face. */
    val labelStamp: Long = label?.lastModified() ?: 0L,
) {
    val key: String get() = "${platform.folder}/$stem"

    /** The letter Up / Down jump between. Digits and symbols share '#'. */
    val initial: Char = title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
        ?.let { if (it.isLetter()) it else '#' } ?: '#'

    companion object {
        private val TAGS = Regex("""\s*[(\[][^)\]]*[)\]]""")
        private val SPACES = Regex("""\s+""")
        private val SCENE_NUMBER = Regex("""^\d{3,4}\s+-\s+""")

        fun titleOf(stem: String): String {
            val t = stem.replace(TAGS, "").replace(SCENE_NUMBER, "").replace('_', ' ').replace(SPACES, " ").trim()
            return t.ifEmpty { stem }
        }
    }
}
