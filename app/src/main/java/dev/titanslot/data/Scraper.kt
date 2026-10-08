package dev.titanslot.data

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import dev.titanslot.core.Platform
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fetches cart art.
 *
 * Cart labels are the real printed labels, scanned, from slot's art set (art.slot-cfw.fyi, the
 * set slot's Cart Studio uses, from ScreenScraper). It covers GB, GBC and GBA, keyed by the
 * ROM's CRC32, so a cart is found whatever its file is called.
 *
 * Box art, title screens and screenshots come from the libretro thumbnail server
 * (thumbnails.libretro.com), the set RetroArch uses. Files there are named after No-Intro ROM
 * names, so a cart named that way matches directly; anything else is matched against the
 * folder's listing by title and region.
 */
class Scraper(private val cacheDir: File) {

    enum class Kind(val label: String, val folder: String?) {
        LABEL("Cart Label", null),
        BOX("Box Art", "Named_Boxarts"),
        TITLE("Title Screen", "Named_Titles"),
        SNAP("Screenshot", "Named_Snaps"),
    }

    sealed interface Result {
        data class Found(val name: String) : Result
        data object NotFound : Result
        data class Failed(val reason: String) : Result
    }

    /** Downloads art for [cart] into [dest]. Blocking; call it off the main thread. */
    fun scrape(cart: Cart, kind: Kind, dest: File): Result = try {
        if (kind == Kind.LABEL) label(cart, dest) else thumbnail(cart, kind, dest)
    } catch (e: IOException) {
        Log.w(TAG, "Scrape failed for ${cart.key}", e)
        Result.Failed(if (e is java.net.UnknownHostException) "No connection" else (e.message ?: "Network error"))
    }

    private fun label(cart: Cart, dest: File): Result {
        if (cart.platform !in LABEL_PLATFORMS) return Result.NotFound
        val crc = crcOf(cart) ?: return Result.Failed("Could not read the ROM")
        val path = labelIndex()[crc] ?: return Result.NotFound
        download(ART_BASE + path, dest)
        return Result.Found(crc)
    }

    /** CRC32 -> label path, from the art set's index.json, cached for two weeks. */
    private fun labelIndex(): Map<String, String> {
        labels?.let { return it }
        val file = File(cacheDir, "thumbs/slot-art.json")
        val json = if (file.isFile && System.currentTimeMillis() - file.lastModified() < INDEX_TTL_MS) {
            file.readText()
        } else {
            get(ART_BASE + "index.json").toString(Charsets.UTF_8).also {
                file.parentFile?.mkdirs()
                file.writeText(it)
            }
        }
        val root = org.json.JSONObject(json)
        val map = HashMap<String, String>(root.length() * 2)
        root.keys().forEach { crc ->
            root.optJSONObject(crc)?.optString("support-texture")?.takeIf { it.isNotEmpty() }?.let { map[crc.uppercase()] = it }
        }
        labels = map
        return map
    }

    private fun thumbnail(cart: Cart, kind: Kind, dest: File): Result {
        val systems = systemsFor(cart.platform)
        val hit = systems.firstNotNullOfOrNull { system ->
            exact(system, kind, cart.stem) ?: fuzzy(system, kind, cart)
        }
        val (system, name) = hit ?: return Result.NotFound
        download(url(system, kind, name), dest)
        return Result.Found(name)
    }

    private fun exact(system: String, kind: Kind, stem: String): Pair<String, String>? {
        val name = sanitize(stem)
        return if (exists(url(system, kind, name))) system to name else null
    }

    private fun fuzzy(system: String, kind: Kind, cart: Cart): Pair<String, String>? {
        val names = index(system, kind)
        return best(cart.stem, names)?.let { system to it }
    }

    /** The folder listing, cached for two weeks; one name (without .png) per line. */
    private fun index(system: String, kind: Kind): List<String> {
        val file = File(cacheDir, "thumbs/${sanitize(system)}-${kind.folder}.txt")
        if (file.isFile && System.currentTimeMillis() - file.lastModified() < INDEX_TTL_MS) {
            return file.readLines()
        }
        val html = get(dirUrl(system, kind)).toString(Charsets.UTF_8)
        val names = HREF.findAll(html)
            .map { Uri.decode(it.groupValues[1]) }
            .filter { it.endsWith(".png", ignoreCase = true) }
            .map { it.removeSuffix(".png") }
            .toList()
        file.parentFile?.mkdirs()
        file.writeText(names.joinToString("\n"))
        return names
    }

    private fun download(url: String, dest: File) {
        val bytes = get(url)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0) throw IOException("Not an image")
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(dest)) throw IOException("Could not write ${dest.name}")
    }

    private fun exists(url: String): Boolean {
        val c = open(url).apply { requestMethod = "HEAD" }
        return try {
            c.responseCode == HttpURLConnection.HTTP_OK
        } finally {
            c.disconnect()
        }
    }

    private fun get(url: String): ByteArray {
        val c = open(url)
        try {
            if (c.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.use { it.readBytes() }
        } finally {
            c.disconnect()
        }
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", "TitanSlot/1.0 (Android)")
    }

    private fun url(system: String, kind: Kind, name: String) =
        "$BASE/${Uri.encode(system)}/${kind.folder}/${Uri.encode("$name.png")}"

    private fun dirUrl(system: String, kind: Kind) = "$BASE/${Uri.encode(system)}/${kind.folder}/"

    companion object {
        private const val TAG = "Scraper"
        private const val BASE = "https://thumbnails.libretro.com"
        private const val ART_BASE = "https://art.slot-cfw.fyi/"

        /** Platforms slot's art set has real label scans for. */
        val LABEL_PLATFORMS = setOf(Platform.GB, Platform.GBC, Platform.GBA)

        /** The art kinds worth offering for a platform, best first. */
        fun kindsFor(platform: Platform): List<Kind> =
            if (platform in LABEL_PLATFORMS) Kind.entries else Kind.entries - Kind.LABEL

        @Volatile private var labels: Map<String, String>? = null

        /** The ROM's CRC32 as 8 upper-case hex digits; a zip's entry already carries one. */
        fun crcOf(cart: Cart): String? = runCatching {
            val value = if (cart.rom.extension.equals("zip", true)) {
                java.util.zip.ZipFile(cart.rom).use { zip ->
                    zip.entries().asSequence().firstOrNull {
                        !it.isDirectory && it.name.substringAfterLast('.').lowercase() in cart.platform.extensions
                    }?.crc?.takeIf { it >= 0 }
                }
            } else {
                val crc = java.util.zip.CRC32()
                val buf = ByteArray(1 shl 16)
                cart.rom.inputStream().use { input ->
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        crc.update(buf, 0, n)
                    }
                }
                crc.value
            }
            value?.let { "%08X".format(it) }
        }.getOrNull()
        private const val INDEX_TTL_MS = 14L * 24 * 60 * 60 * 1000
        private val HREF = Regex("""href="([^"?/][^"]*)"""")

        /** libretro thumbnail systems, in the order to try. GB and GBC games cross over. */
        fun systemsFor(platform: Platform): List<String> = when (platform) {
            Platform.GB -> listOf("Nintendo - Game Boy", "Nintendo - Game Boy Color")
            Platform.GBC -> listOf("Nintendo - Game Boy Color", "Nintendo - Game Boy")
            Platform.GBA -> listOf("Nintendo - Game Boy Advance")
            Platform.NES -> listOf("Nintendo - Nintendo Entertainment System")
            Platform.SNES -> listOf("Nintendo - Super Nintendo Entertainment System")
            Platform.NDS -> listOf("Nintendo - Nintendo DS")
        }

        /** The characters libretro replaces with '_' in thumbnail file names. */
        fun sanitize(name: String): String = name.replace(Regex("""[&*/:`<>?\\|"]"""), "_")

        private val TAG_GROUP = Regex("""\(([^)]*)\)|\[([^\]]*)]""")
        private val NON_WORD = Regex("""[^\p{L}\p{N}]+""")
        private val MARKS = Regex("""\p{M}+""")
        private val BAD = listOf("beta", "proto", "demo", "kiosk", "sample", "hack", "pirate", "unl", "promo")
        private val REISSUE = listOf("virtual console", "switch online", "alternate", "collection", "classic mini")

        /** "Legend of Zelda, The - Link's Awakening" and "The Legend of Zelda: Link's..." agree. */
        fun normalize(name: String): String {
            val plain = java.text.Normalizer.normalize(name.replace(TAG_GROUP, " "), java.text.Normalizer.Form.NFD)
                .replace(MARKS, "")
                .lowercase()
                .replace("&", " and ")
            return plain.split(NON_WORD).filter { it.isNotEmpty() && it != "the" }.joinToString("")
        }

        private fun regionOf(name: String): String? {
            val tags = TAG_GROUP.findAll(name).joinToString(" ") { it.value }.lowercase()
            return when {
                "usa" in tags || "(u)" in tags || "(ue)" in tags -> "usa"
                "world" in tags -> "world"
                "europe" in tags || "(e)" in tags -> "europe"
                "japan" in tags || "(j)" in tags -> "japan"
                else -> null
            }
        }

        /** The listing entry that is the same game as [stem], preferring the same region. */
        fun best(stem: String, names: List<String>): String? {
            val want = normalize(Cart.titleOf(stem))
            if (want.isEmpty()) return null
            val region = regionOf(stem)
            val same = names.filter { normalize(it) == want }
            val pool = same.ifEmpty {
                // "Pokemon Emerald" vs "Pokemon - Emerald Version": allow one to contain the other.
                names.filter { n ->
                    val have = normalize(n)
                    have.length >= 4 && (have.startsWith(want) || want.startsWith(have)) &&
                        minOf(have.length, want.length) * 10 >= maxOf(have.length, want.length) * 6
                }
            }
            return pool.minByOrNull { n ->
                val tags = TAG_GROUP.findAll(n).joinToString(" ") { it.value }.lowercase()
                var score = 0
                if (BAD.any { it in tags }) score += 100
                if (REISSUE.any { it in tags }) score += 10
                score += when (regionOf(n)) {
                    region -> 0
                    "usa" -> 2
                    "world" -> 3
                    "europe" -> 4
                    else -> 6
                }
                if ("rev" in tags) score += 1
                score * 1000 + abs(normalize(n).length - want.length)
            }
        }

        private fun abs(x: Int) = if (x < 0) -x else x
    }
}
