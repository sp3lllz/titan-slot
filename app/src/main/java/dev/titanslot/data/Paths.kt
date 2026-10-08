package dev.titanslot.data

import dev.titanslot.core.Platform
import java.io.File

/**
 * Where everything lives, after slot's card layout, split in two:
 *
 *   library (phone storage or the microSD card, chosen in setup)
 *     Games/<GB|GBC|GBA>/                ROMs, any sub-folders you like (.zip works too)
 *     Labels/<platform>/<rom name>.png   cart label art
 *
 *   data (always the phone, so swapping cards never loses progress)
 *     Saves/<platform>/                  battery saves (.srm)
 *     States/<platform>/<rom name>/      save states and their thumbnails
 *     BIOS/                              optional BIOS files, passed to the cores as the system dir
 *     Config/carts.json                  what you changed in each cart sheet
 *
 * Both are the app's own folders (Android/data/dev.titanslot/files on their volume), which
 * need no storage permission. [libraryAvailable] is false while the card the library is on
 * is out of the phone.
 */
class Paths(val library: File, val data: File, val libraryAvailable: Boolean = true) {
    val games = File(library, "Games")
    val labels = File(library, "Labels")
    val saves = File(data, "Saves")
    val states = File(data, "States")
    val bios = File(data, "BIOS")
    val config = File(data, "Config")
    val cartProps = File(config, "carts.json")

    fun games(platform: Platform) = File(games, platform.folder)
    fun labels(platform: Platform) = File(labels, platform.folder)
    fun saves(platform: Platform) = File(saves, platform.folder)
    fun states(platform: Platform) = File(states, platform.folder)

    fun ensure() {
        if (libraryAvailable) {
            listOf(games, labels).forEach { parent -> Platform.entries.forEach { File(parent, it.folder).mkdirs() } }
            val readme = File(library, "README.txt")
            if (!readme.exists()) runCatching { readme.writeText(README) }
        }
        listOf(saves, states).forEach { parent -> Platform.entries.forEach { File(parent, it.folder).mkdirs() } }
        bios.mkdirs()
        config.mkdirs()
    }

    private companion object {
        val README = """
            Titan Slot
            ==========

            The easiest way to add games is in the app: Settings > Add Games. If you'd rather
            copy them here yourself, put your legally obtained ROMs in the folder for their
            platform, then pick Settings > Rescan Games:

              Games/GB     .gb
              Games/GBC    .gbc
              Games/GBA    .gba

            Zipped ROMs work too. Sub-folders are fine.

            Cart labels are optional: drop a .png or .jpg into Labels/<platform>/ named after
            the ROM, e.g. Labels/GBA/Metroid Fusion.png for Games/GBA/Metroid Fusion.gba.
            Or press V on a cart and scrape art, or pick an image from the phone.

            Battery saves, save states, BIOS files and your cart settings are kept in the
            app's folder on the phone's own storage, not here.
        """.trimIndent() + "\n"
    }
}
