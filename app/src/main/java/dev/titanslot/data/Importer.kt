package dev.titanslot.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import dev.titanslot.core.Platform
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile

/**
 * Brings games and saves in from wherever they are (Downloads, a folder on the microSD card,
 * a cloud drive) through the system file picker, so the app needs no storage permission.
 * Games are copied into the library; saves are matched to games by file name.
 *
 * Everything here blocks; run it off the main thread.
 */
class Importer(private val resolver: ContentResolver) {

    /** A file picked in the system picker, or found under a picked folder. */
    data class Picked(val uri: Uri, val name: String, val size: Long, val folder: String?)

    data class Progress(val done: Int, val total: Int, val name: String)

    data class Games(val added: Int, val existing: Int, val skipped: Int, val error: String? = null)

    data class Saves(
        val matched: List<Cart>,
        val same: Int,
        val unmatched: List<String>,
        val skipped: Int,
        val error: String? = null,
    )

    /** Names and sizes for files picked one by one. */
    fun files(uris: List<Uri>): List<Picked> = uris.mapNotNull { uri ->
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val name = c.getString(0) ?: return@use null
                Picked(uri, name, if (c.isNull(1)) -1L else c.getLong(1), folder = null)
            }
        }.getOrNull()
    }

    /** Every file under a picked folder, sub-folders included. */
    fun tree(tree: Uri): List<Picked> {
        val out = mutableListOf<Picked>()
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val rootName = runCatching {
            resolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, rootId), arrayOf(COLUMNS[1]), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        val pending = ArrayDeque<Triple<String, String?, Int>>()
        pending += Triple(rootId, rootName, 0)
        while (pending.isNotEmpty() && out.size < MAX_FILES) {
            val (id, folder, depth) = pending.removeFirst()
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, id)
            resolver.query(children, COLUMNS, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val childId = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    if (name.startsWith(".")) continue
                    if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < MAX_DEPTH) pending += Triple(childId, name, depth + 1)
                    } else {
                        val size = if (c.isNull(3)) -1L else c.getLong(3)
                        out += Picked(DocumentsContract.buildDocumentUriUsingTree(tree, childId), name, size, folder)
                    }
                }
            }
        }
        return out
    }

    /**
     * Copies the ROMs among [picked] into the library, each onto the shelf its extension (or
     * the folder it came from) says. A zip holding one ROM is kept as it is; a zip holding a
     * collection is unpacked into one cart per ROM. Anything already in the library is skipped.
     */
    fun importGames(picked: List<Picked>, paths: Paths, progress: (Progress) -> Unit, cancelled: () -> Boolean): Games {
        val roms = picked.filter { extension(it.name) in Library.IMPORTABLE }
        var added = 0
        var existing = 0
        var skipped = picked.size - roms.size
        paths.games.mkdirs()
        for ((i, p) in roms.withIndex()) {
            if (cancelled()) break
            progress(Progress(i, roms.size, p.name))
            if (p.size > 0 && p.size > paths.games.usableSpace) {
                return Games(added, existing, skipped, "Not enough space for ${p.name}")
            }
            val name = safeName(p.name)
            val tmp = File(paths.games, ".importing-$name")
            try {
                resolver.openInputStream(p.uri)?.use { input -> tmp.outputStream().use { input.copyTo(it) } }
                    ?: throw IOException("Could not open ${p.name}")
                val hint = p.folder?.let(Platform::fromFolder)
                if (extension(name) == "zip") {
                    val (a, e, s) = placeZip(tmp, name, hint, paths)
                    added += a
                    existing += e
                    skipped += s
                } else {
                    when (val platform = Library.platformOf(File(name), hint)) {
                        null -> skipped++
                        else -> if (place(tmp, File(paths.games(platform), name))) added++ else existing++
                    }
                }
            } catch (e: IOException) {
                return Games(added, existing, skipped, e.message ?: "Could not copy ${p.name}")
            } finally {
                tmp.delete()
            }
        }
        progress(Progress(roms.size, roms.size, ""))
        return Games(added, existing, skipped)
    }

    /** Returns (added, already there, not a ROM) for one zip. */
    private fun placeZip(tmp: File, name: String, hint: Platform?, paths: Paths): Triple<Int, Int, Int> {
        val entries = ZipFile(tmp).use { zip ->
            val roms = Library.romsInZip(zip)
            if (roms.size <= 1) return@use roms.map { it.name }
            // A collection: unpack every ROM as its own cart.
            var added = 0
            var existing = 0
            for (entry in roms) {
                val entryName = safeName(entry.name.substringAfterLast('/'))
                val entryHint = entry.name.substringBeforeLast('/', "").substringAfterLast('/')
                    .let(Platform::fromFolder) ?: hint
                val platform = Library.platformOf(File(entryName), entryHint) ?: continue
                val out = File(paths.games, ".unpacking-$entryName")
                try {
                    zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                    if (place(out, File(paths.games(platform), entryName))) added++ else existing++
                } finally {
                    out.delete()
                }
            }
            return Triple(added, existing, 0)
        }
        val inside = entries.singleOrNull() ?: return Triple(0, 0, 1)
        val platform = Library.platformOf(File(inside.substringAfterLast('/')), hint) ?: return Triple(0, 0, 1)
        return if (place(tmp, File(paths.games(platform), name))) Triple(1, 0, 0) else Triple(0, 1, 0)
    }

    /**
     * Moves a copied file to [dest]. Returns false when the same file is already there. A
     * different file with the same name gets a numbered name instead of replacing it.
     */
    private fun place(src: File, dest: File): Boolean {
        dest.parentFile?.mkdirs()
        var target = dest
        var n = 2
        while (target.exists()) {
            if (target.length() == src.length()) return false
            target = File(dest.parentFile, "${dest.nameWithoutExtension} ($n).${dest.extension}")
            n++
        }
        if (!src.renameTo(target)) throw IOException("Could not write ${target.name}")
        return true
    }

    /**
     * Battery saves (.sav, .srm) from other emulators or flash carts, matched to the library's
     * carts by name. A save that replaces a different one keeps the old one as .srm.bak.
     */
    fun importSaves(
        picked: List<Picked>,
        carts: List<Cart>,
        store: StateStore,
        progress: (Progress) -> Unit,
        cancelled: () -> Boolean,
    ): Saves {
        val saves = picked.filter { extension(it.name) in SAVE_EXTENSIONS }
        val matched = mutableListOf<Cart>()
        val unmatched = mutableListOf<String>()
        var same = 0
        var skipped = picked.size - saves.size
        for ((i, p) in saves.withIndex()) {
            if (cancelled()) break
            progress(Progress(i, saves.size, p.name))
            val cart = matchSave(p.name.substringBeforeLast('.'), carts)
            if (cart == null) {
                unmatched += p.name
                continue
            }
            try {
                if (p.size > MAX_SAVE_BYTES) {
                    skipped++
                    continue
                }
                val bytes = resolver.openInputStream(p.uri)?.use { it.readBytes() }
                    ?: throw IOException("Could not open ${p.name}")
                if (bytes.isEmpty() || bytes.size > MAX_SAVE_BYTES) {
                    skipped++
                    continue
                }
                val dest = store.sram(cart)
                if (dest.isFile && dest.readBytes().contentEquals(bytes)) {
                    same++
                    continue
                }
                if (dest.isFile) dest.copyTo(File(dest.parentFile, dest.name + ".bak"), overwrite = true)
                store.writeSram(cart, bytes)
                matched += cart
            } catch (e: IOException) {
                return Saves(matched, same, unmatched, skipped, e.message ?: "Could not read ${p.name}")
            }
        }
        progress(Progress(saves.size, saves.size, ""))
        return Saves(matched, same, unmatched, skipped)
    }

    companion object {
        val SAVE_EXTENSIONS = setOf("sav", "srm")
        private const val MAX_SAVE_BYTES = 1L shl 20
        private const val MAX_DEPTH = 6
        private const val MAX_FILES = 20_000
        private val COLUMNS = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
        )

        private fun extension(name: String) = name.substringAfterLast('.', "").lowercase()

        private fun safeName(name: String) = name.replace('/', '_').replace('\u0000', '_').trim()

        /**
         * The cart a save belongs to: the same file name first, then the same game by title
         * ("Pokemon - Emerald Version (U).sav" for "Pokemon - Emerald Version (USA, Europe)"),
         * preferring the same region. A looser match ("Pokemon Emerald") only counts when it
         * points at a single cart, so a save never lands on the wrong game.
         */
        fun matchSave(stem: String, carts: List<Cart>): Cart? {
            carts.firstOrNull { it.stem.equals(stem, ignoreCase = true) }?.let { return it }
            val want = Scraper.normalize(Cart.titleOf(stem))
            if (want.isEmpty()) return null
            val same = carts.filter { Scraper.normalize(Cart.titleOf(it.stem)) == want }
            if (same.isNotEmpty()) {
                val region = Scraper.regionOf(stem)
                return same.firstOrNull { Scraper.regionOf(it.stem) == region } ?: same.first()
            }
            return carts.filter { c ->
                val have = Scraper.normalize(Cart.titleOf(c.stem))
                have.length >= 4 && (have.startsWith(want) || want.startsWith(have)) &&
                    minOf(have.length, want.length) * 10 >= maxOf(have.length, want.length) * 6
            }.singleOrNull()
        }
    }
}
