package dev.titanslot.core

/**
 * A shelf. Each platform has its own folder under Games/, its own carts and its own cores.
 *
 * [nativeW] x [nativeH] is the picture the cores are configured to output (see [CoreOptions]),
 * which is what integer scaling multiplies.
 */
enum class Platform(
    val folder: String,
    val title: String,
    val tab: String,
    val extensions: Set<String>,
    val cores: List<Core>,
    val nativeW: Int,
    val nativeH: Int,
) {
    GB(
        folder = "GB",
        title = "Game Boy",
        tab = "GB",
        extensions = setOf("gb", "gbc", "sgb"),
        cores = listOf(Core.GAMBATTE, Core.MGBA),
        nativeW = 160, nativeH = 144,
    ),
    GBC(
        folder = "GBC",
        title = "Game Boy Color",
        tab = "GBC",
        extensions = setOf("gbc", "gb"),
        cores = listOf(Core.GAMBATTE, Core.MGBA),
        nativeW = 160, nativeH = 144,
    ),
    GBA(
        folder = "GBA",
        title = "Game Boy Advance",
        tab = "GBA",
        extensions = setOf("gba", "agb"),
        cores = listOf(Core.MGBA),
        nativeW = 240, nativeH = 160,
    );

    val defaultCore: Core get() = cores.first()

    companion object {
        fun fromFolder(name: String): Platform? = entries.firstOrNull { it.folder.equals(name, true) }
    }
}
