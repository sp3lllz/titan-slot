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
    val usesShoulders: Boolean,
    val rewind: Boolean,
) {
    GB(
        folder = "GB",
        title = "Game Boy",
        tab = "GB",
        extensions = setOf("gb", "gbc", "sgb"),
        cores = listOf(Core.GAMBATTE, Core.MGBA),
        nativeW = 160, nativeH = 144,
        usesShoulders = false,
        rewind = true,
    ),
    GBC(
        folder = "GBC",
        title = "Game Boy Color",
        tab = "GBC",
        extensions = setOf("gbc", "gb"),
        cores = listOf(Core.GAMBATTE, Core.MGBA),
        nativeW = 160, nativeH = 144,
        usesShoulders = false,
        rewind = true,
    ),
    GBA(
        folder = "GBA",
        title = "Game Boy Advance",
        tab = "GBA",
        extensions = setOf("gba", "agb"),
        cores = listOf(Core.MGBA),
        nativeW = 240, nativeH = 160,
        usesShoulders = true,
        rewind = true,
    ),
    NES(
        folder = "NES",
        title = "Nintendo Entertainment System",
        tab = "NES",
        extensions = setOf("nes", "fds", "unf", "unif"),
        cores = listOf(Core.FCEUMM),
        nativeW = 256, nativeH = 224,
        usesShoulders = false,
        rewind = true,
    ),
    SNES(
        folder = "SNES",
        title = "Super Nintendo",
        tab = "SNES",
        extensions = setOf("sfc", "smc", "swc", "fig", "bs"),
        cores = listOf(Core.SNES9X),
        nativeW = 256, nativeH = 224,
        usesShoulders = true,
        rewind = true,
    ),
    NDS(
        folder = "NDS",
        title = "Nintendo DS",
        tab = "DS",
        extensions = setOf("nds"),
        cores = listOf(Core.MELONDS),
        // Both screens stacked, no gap.
        nativeW = 256, nativeH = 384,
        usesShoulders = true,
        // DS states are several megabytes; snapshotting them 15 times a second is not worth it.
        rewind = false,
    );

    val defaultCore: Core get() = cores.first()

    companion object {
        fun fromFolder(name: String): Platform? = entries.firstOrNull { it.folder.equals(name, true) }
    }
}
