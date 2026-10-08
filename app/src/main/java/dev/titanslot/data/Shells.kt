package dev.titanslot.data

import dev.titanslot.core.Platform

enum class Finish(val label: String) { SOLID("Solid"), CLEAR("Clear"), GLITTER("Glitter") }

/** The outline a cart is drawn with. GB carts come notched (DMG) or rounded (GBC-only). */
enum class CartShape { GBA, GB_NOTCHED, GB_ROUNDED, NES, SNES, DS }

data class Shell(val rgb: Int, val finish: Finish = Finish.SOLID)

/**
 * Cart plastics. The GB/GBC/GBA tables are slot's (crates/slot-ui/src/shell.rs): exact
 * game codes first, then the code's family letter, then the platform default.
 */
object Shells {
    val GBA_DEFAULT = Shell(0x35353A)
    val DMG = Shell(0x9A978F)
    val DUAL_MODE = Shell(0x333031)
    val GB_CLEAR = Shell(0x7C7A8A, Finish.CLEAR)
    val NES = Shell(0x8C8A86)
    val SNES = Shell(0x9C9BA0)
    val DS = Shell(0x3A3B40)

    private val CLEAR = Shell(0xD9DBD8, Finish.CLEAR)
    private val YOSHI = Shell(0x2F8F4E)
    private val CRYSTAL = Shell(0x86B9BF, Finish.GLITTER)
    private val KIRBY = Shell(0xEC94B4, Finish.CLEAR)

    private val GBA_EXACT = mapOf(
        "AXV" to Shell(0xC2332E, Finish.CLEAR),
        "AXP" to Shell(0x2F5CC0, Finish.CLEAR),
        "BPE" to Shell(0x249C60, Finish.CLEAR),
        "BPR" to Shell(0xD85224),
        "BPG" to Shell(0x63B044),
        "U3I" to CLEAR,
        "U32" to CLEAR,
        "U33" to CLEAR,
        "V49" to Shell(0xA9513B),
        "KYGE" to YOSHI,
        "KYGP" to YOSHI,
        "RZW" to Shell(0x5F6264, Finish.CLEAR),
        "RZWJ" to Shell(0xECEEE8),
    )

    private val GBA_FAMILY = mapOf('M' to Shell(0xC6C6C9))

    private val GB_CODES = mapOf(
        "AAU" to Shell(0xB38B3A),
        "AAUJ" to DUAL_MODE,
        "AAX" to Shell(0xA9AAA7),
        "AAXJ" to DUAL_MODE,
        "BYT" to CRYSTAL,
        "BXT" to CRYSTAL,
        "KTN" to KIRBY,
        "KKK" to KIRBY,
        "KCE" to Shell(0x1F9FB6, Finish.CLEAR),
        "VCA" to Shell(0xF0A95E, Finish.CLEAR),
        "VPHJ" to Shell(0xE2B413),
        "BMG" to DUAL_MODE,
    )

    private val GB_OVERSEAS_TITLES = mapOf(
        "POKEMON RED" to Shell(0xC0282C),
        "POKEMON BLU" to Shell(0x2B3A88),
        "POKEMON YEL" to Shell(0xE9A826),
    )

    /** Plastics to pick from in the cart sheet. */
    val PRESETS: List<Pair<String, Shell>> = listOf(
        "Charcoal" to GBA_DEFAULT,
        "Game Boy Grey" to DMG,
        "Game Boy Black" to DUAL_MODE,
        "Clear" to CLEAR,
        "Clear Purple" to GB_CLEAR,
        "Atomic Purple" to Shell(0x6A4C9C, Finish.CLEAR),
        "Glacier" to Shell(0x8FC6E0, Finish.CLEAR),
        "Smoke" to Shell(0x5F6264, Finish.CLEAR),
        "Ruby" to Shell(0xC2332E, Finish.CLEAR),
        "Sapphire" to Shell(0x2F5CC0, Finish.CLEAR),
        "Emerald" to Shell(0x249C60, Finish.CLEAR),
        "FireRed" to Shell(0xD85224),
        "LeafGreen" to Shell(0x63B044),
        "Pokémon Red" to Shell(0xC0282C),
        "Pokémon Blue" to Shell(0x2B3A88),
        "Pokémon Yellow" to Shell(0xE9A826),
        "Gold" to Shell(0xB38B3A),
        "Silver" to Shell(0xA9AAA7),
        "Crystal" to CRYSTAL,
        "Kirby Pink" to KIRBY,
        "Yoshi Green" to YOSHI,
        "Turquoise" to Shell(0x1F9FB6, Finish.CLEAR),
        "Orange" to Shell(0xF0A95E, Finish.CLEAR),
        "White" to Shell(0xECEEE8),
        "NES Grey" to NES,
        "SNES Grey" to SNES,
        "DS Black" to DS,
    )

    fun of(platform: Platform, header: RomHeader?): Pair<Shell, CartShape> = when (platform) {
        Platform.GBA -> gba((header as? RomHeader.Gba)?.code.orEmpty()) to CartShape.GBA
        Platform.GB, Platform.GBC -> gb(platform, header as? RomHeader.Gb)
        Platform.NES -> NES to CartShape.NES
        Platform.SNES -> SNES to CartShape.SNES
        Platform.NDS -> DS to CartShape.DS
    }

    private fun gba(code: String): Shell =
        byCode(code, GBA_EXACT)
            ?: code.firstOrNull()?.let { GBA_FAMILY[it] }
            ?: GBA_DEFAULT

    private fun gb(platform: Platform, h: RomHeader.Gb?): Pair<Shell, CartShape> {
        if (h == null) {
            // No header to go on (unreadable zip, odd dump): trust the folder.
            return if (platform == Platform.GBC) GB_CLEAR to CartShape.GB_ROUNDED
            else DMG to CartShape.GB_NOTCHED
        }
        val shape = if (h.colourOnly) CartShape.GB_ROUNDED else CartShape.GB_NOTCHED
        val shell = byCode(h.code, GB_CODES)
            ?: GB_OVERSEAS_TITLES.entries.firstOrNull { h.overseas && h.title == it.key }?.value
            ?: when {
                h.colourOnly -> GB_CLEAR
                h.dualMode -> DUAL_MODE
                else -> DMG
            }
        return shell to shape
    }

    private fun byCode(code: String, table: Map<String, Shell>): Shell? {
        if (code.isEmpty()) return null
        return table[code.take(4)] ?: table[code.take(3)]
    }
}
