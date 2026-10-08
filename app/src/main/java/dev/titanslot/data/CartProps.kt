package dev.titanslot.data

import android.util.Log
import org.json.JSONObject
import java.io.File

/** How label art sits in the label panel. */
enum class ArtFit(val label: String) {
    /** Cover the panel, cropping the overflow (biased toward the top, where logos live). */
    FILL("Fill"),

    /** Show the whole image, on a field of its own average colour. */
    FIT("Fit"),
}

/** Where a cart's label came from, so a later scrape knows what it may replace. */
enum class ArtSource { LABEL, BOX, TITLE, SNAP, PHONE }

/**
 * What you've changed about a cart in the cart sheet. Null means "work it out" (from the
 * ROM header, the file name, or slot's tables).
 */
data class CartProps(
    val name: String? = null,
    val colour: Int? = null,
    val finish: Finish? = null,
    val shape: CartShape? = null,
    val fit: ArtFit? = null,
    /** Null for art that was put in Labels/ by hand. */
    val art: ArtSource? = null,
) {
    val isEmpty: Boolean get() = this == CartProps()
}

/**
 * Every cart's properties, in TitanSlot/Config/carts.json so they survive a reinstall and can
 * be edited (or copied to another phone) by hand:
 *
 *   { "GBA/Pokemon - Emerald Version (USA, Europe)": { "colour": "#249C60", "finish": "clear" } }
 */
class CartPropsStore(private val file: File) {
    private var all: MutableMap<String, CartProps> = load()

    operator fun get(key: String): CartProps = all[key] ?: CartProps()

    fun update(key: String, change: (CartProps) -> CartProps) {
        val next = change(get(key))
        if (next.isEmpty) all.remove(key) else all[key] = next
        save()
    }

    private fun load(): MutableMap<String, CartProps> {
        if (!file.isFile) return mutableMapOf()
        return runCatching {
            val root = JSONObject(file.readText())
            root.keys().asSequence().associateWith { key -> parse(root.getJSONObject(key)) }.toMutableMap()
        }.onFailure { Log.e(TAG, "Could not read ${file.path}", it) }.getOrDefault(mutableMapOf())
    }

    private fun save() {
        runCatching {
            val root = JSONObject()
            all.toSortedMap().forEach { (key, p) -> root.put(key, write(p)) }
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(root.toString(2))
            tmp.renameTo(file)
        }.onFailure { Log.e(TAG, "Could not write ${file.path}", it) }
    }

    private fun parse(o: JSONObject) = CartProps(
        name = o.optString("name").ifBlank { null },
        colour = o.optString("colour").let(::parseHex),
        finish = o.optString("finish").let { s -> Finish.entries.firstOrNull { it.name.equals(s, true) } },
        shape = o.optString("shape").let { s -> CartShape.entries.firstOrNull { it.name.equals(s, true) } },
        fit = o.optString("fit").let { s -> ArtFit.entries.firstOrNull { it.name.equals(s, true) } },
        art = o.optString("art").let { s -> ArtSource.entries.firstOrNull { it.name.equals(s, true) } },
    )

    private fun write(p: CartProps) = JSONObject().apply {
        p.name?.let { put("name", it) }
        p.colour?.let { put("colour", hex(it)) }
        p.finish?.let { put("finish", it.name.lowercase()) }
        p.shape?.let { put("shape", it.name.lowercase()) }
        p.fit?.let { put("fit", it.name.lowercase()) }
        p.art?.let { put("art", it.name.lowercase()) }
    }

    companion object {
        private const val TAG = "CartProps"

        fun hex(rgb: Int): String = "#%06X".format(rgb and 0xFFFFFF)

        /** "#249C60", "249c60" or "#fff" to 0xRRGGBB, or null. */
        fun parseHex(s: String?): Int? {
            val h = s?.trim()?.removePrefix("#") ?: return null
            val full = when (h.length) {
                3 -> h.map { "$it$it" }.joinToString("")
                6 -> h
                else -> return null
            }
            return full.toIntOrNull(16)
        }
    }
}
