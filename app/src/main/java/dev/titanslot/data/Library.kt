package dev.titanslot.data

import dev.titanslot.core.Platform
import java.io.File
import java.util.zip.ZipFile

/**
 * Walks Games/ and builds one alphabetical shelf per platform.
 *
 * A ROM goes on the shelf its extension says, wherever it sits under Games/, so a .gb copied
 * into GBA/ still turns up on the Game Boy shelf. The folder only decides what the extension
 * can't: a .gb kept in GBC/ stays there, and a .zip goes by its folder (or, outside one, by
 * the ROM inside it).
 */
object Library {
    private val LABEL_EXTENSIONS = listOf("png", "jpg", "jpeg", "webp")

    private val BY_EXTENSION: Map<String, Platform> = mapOf(
        "gb" to Platform.GB, "sgb" to Platform.GB,
        "gbc" to Platform.GBC,
        "gba" to Platform.GBA, "agb" to Platform.GBA,
    )

    /** File types Add Games copies in: ROMs, and zips holding them. */
    val IMPORTABLE: Set<String> = BY_EXTENSION.keys + "zip"

    fun scan(paths: Paths, props: CartPropsStore): Map<Platform, List<Cart>> {
        val found = Platform.entries.associateWith { mutableListOf<File>() }
        if (!paths.libraryAvailable) return found.mapValues { emptyList() }
        val root = paths.games
        root.walkTopDown()
            .onEnter { it == root || !it.name.startsWith(".") }
            .filter { it.isFile && !it.name.startsWith(".") }
            .forEach { rom -> platformOf(rom, folderOf(root, rom))?.let { found.getValue(it) += rom } }

        return found.mapValues { (platform, roms) ->
            val labels = labelIndex(paths.labels(platform))
            roms.map { cart(platform, it, labels, props) }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { sortKey(it.title) })
        }
    }

    /** The platform folder directly under Games/ that holds [rom], if any. */
    private fun folderOf(root: File, rom: File): Platform? {
        val top = rom.relativeTo(root).path.substringBefore(File.separatorChar)
        return if (top == rom.name) null else Platform.fromFolder(top)
    }

    fun platformOf(rom: File, folder: Platform?): Platform? {
        val ext = rom.extension.lowercase()
        if (ext == "zip") return folder ?: zipContents(rom)
        if (folder != null && ext in folder.extensions) return folder
        return BY_EXTENSION[ext]
    }

    /** Every ROM inside a zip, by entry name, for zips that hold a whole collection. */
    fun romsInZip(zip: java.util.zip.ZipFile): List<java.util.zip.ZipEntry> =
        zip.entries().asSequence()
            .filter { !it.isDirectory && BY_EXTENSION.containsKey(it.name.substringAfterLast('.').lowercase()) }
            .toList()

    private fun zipContents(rom: File): Platform? = runCatching {
        ZipFile(rom).use { zip ->
            zip.entries().asSequence()
                .filter { !it.isDirectory }
                .firstNotNullOfOrNull { BY_EXTENSION[it.name.substringAfterLast('.').lowercase()] }
        }
    }.getOrNull()

    /** One cart, rebuilt after an edit, or for the game process (which is handed a ROM). */
    fun cartFor(platform: Platform, rom: File, paths: Paths, props: CartPropsStore? = null): Cart =
        cart(platform, rom, labelIndex(paths.labels(platform)), props)

    /** Where a cart's own label lives: scraped, picked from the phone or dropped in by hand. */
    fun labelFile(paths: Paths, cart: Cart): File = cart.label ?: File(paths.labels(cart.platform), "${cart.stem}.png")

    private fun cart(platform: Platform, rom: File, labels: Map<String, File>, props: CartPropsStore?): Cart {
        val stem = rom.nameWithoutExtension
        val auto = Cart.titleOf(stem)
        val header = RomHeader.read(rom, platform.extensions)
        val (shell, shape) = Shells.of(platform, header)
        val p = props?.get("${platform.folder}/$stem") ?: CartProps()
        val gb = platform == Platform.GB || platform == Platform.GBC
        return Cart(
            platform = platform,
            rom = rom,
            stem = stem,
            title = p.name ?: auto,
            label = labels[stem.lowercase()] ?: labels[auto.lowercase()],
            shell = Shell(p.colour ?: shell.rgb, p.finish ?: shell.finish),
            shape = p.shape?.takeIf { gb && (it == CartShape.GB_NOTCHED || it == CartShape.GB_ROUNDED) } ?: shape,
            fit = p.fit ?: ArtFit.FILL,
        )
    }

    private fun labelIndex(dir: File): Map<String, File> {
        val files = dir.listFiles() ?: return emptyMap()
        return files
            .filter { it.isFile && it.extension.lowercase() in LABEL_EXTENSIONS }
            .associateBy { it.nameWithoutExtension.lowercase() }
    }

    private fun sortKey(title: String): String = title.trimStart { !it.isLetterOrDigit() }
}
