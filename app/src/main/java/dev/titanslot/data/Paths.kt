package dev.titanslot.data

import android.os.Environment
import dev.titanslot.core.Platform
import java.io.File

/**
 * The card layout, borrowed from slot: one folder on shared storage that you can fill
 * from a computer over USB.
 *
 *   TitanSlot/
 *     Games/<GB|GBC|GBA|NES|SNES|NDS>/   ROMs, any sub-folders you like (.zip works too)
 *     Labels/<platform>/<rom name>.png   optional cart label art
 *     Saves/<platform>/                  battery saves (.srm)
 *     States/<platform>/<rom name>/      save states and their thumbnails
 *     BIOS/                              optional BIOS files, passed to the cores as the system dir
 */
class Paths(val root: File) {
    val games = File(root, "Games")
    val labels = File(root, "Labels")
    val saves = File(root, "Saves")
    val states = File(root, "States")
    val bios = File(root, "BIOS")
    val config = File(root, "Config")
    val cartProps = File(config, "carts.json")

    fun games(platform: Platform) = File(games, platform.folder)
    fun labels(platform: Platform) = File(labels, platform.folder)
    fun saves(platform: Platform) = File(saves, platform.folder)
    fun states(platform: Platform) = File(states, platform.folder)

    fun ensure() {
        listOf(games, labels, saves, states).forEach { parent ->
            Platform.entries.forEach { File(parent, it.folder).mkdirs() }
        }
        bios.mkdirs()
        config.mkdirs()
        val readme = File(root, "README.txt")
        if (!readme.exists()) runCatching { readme.writeText(README) }
    }

    companion object {
        fun default() = Paths(File(Environment.getExternalStorageDirectory(), "TitanSlot"))

        private val README = """
            Titan Slot
            ==========

            Put your legally obtained ROMs in the folder for their platform:

              Games/GB     .gb
              Games/GBC    .gbc
              Games/GBA    .gba
              Games/NES    .nes .fds
              Games/SNES   .sfc .smc
              Games/NDS    .nds

            Zipped ROMs work too. Sub-folders are fine.

            Cart labels are optional: drop a .png or .jpg into Labels/<platform>/ named after
            the ROM, e.g. Labels/GBA/Metroid Fusion.png for Games/GBA/Metroid Fusion.gba.
            Or press V on a cart and scrape art, or pick an image from the phone.

            Each cart's name, colour, finish and outline live in Config/carts.json.

            BIOS files are optional and go straight into BIOS/:
              gba_bios.bin                       GBA boot animation (mGBA)
              gb_bios.bin, gbc_bios.bin          GB/GBC boot animation
              bios7.bin, bios9.bin, firmware.bin Nintendo DS (melonDS uses FreeBIOS without them)
              disksys.rom                        Famicom Disk System
        """.trimIndent() + "\n"
    }
}
