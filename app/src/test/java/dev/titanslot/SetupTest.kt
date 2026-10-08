package dev.titanslot

import dev.titanslot.core.CoreFiles
import dev.titanslot.core.Platform
import dev.titanslot.data.Cart
import dev.titanslot.data.CartShape
import dev.titanslot.data.Importer
import dev.titanslot.data.Paths
import dev.titanslot.data.Shells
import dev.titanslot.data.Storage
import dev.titanslot.input.Button
import dev.titanslot.input.KeyMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Save matching, core checks, the library move and the key map defaults. */
class SetupTest {

    private fun cart(platform: Platform, stem: String) =
        Cart(platform, File("$stem.${platform.folder.lowercase()}"), stem, Cart.titleOf(stem), null, Shells.GBA_DEFAULT, CartShape.GBA)

    private val carts = listOf(
        cart(Platform.GBA, "Pokemon - Emerald Version (USA, Europe)"),
        cart(Platform.GBA, "Pokemon - Ruby Version (USA, Europe)"),
        cart(Platform.GBA, "Metroid Fusion (USA)"),
        cart(Platform.GBA, "Metroid Fusion (Japan)"),
        cart(Platform.GB, "Legend of Zelda, The - Link's Awakening (USA, Europe) (Rev 2)"),
        cart(Platform.GBC, "Tetris DX (World)"),
    )

    @Test
    fun `saves match their game by file name first`() {
        assertEquals(carts[0], Importer.matchSave("Pokemon - Emerald Version (USA, Europe)", carts))
        assertEquals(carts[0], Importer.matchSave("pokemon - emerald version (usa, europe)", carts))
    }

    @Test
    fun `saves from other naming schemes match by title and region`() {
        assertEquals(carts[0], Importer.matchSave("Pokemon - Emerald Version (U)", carts))
        assertEquals(carts[3], Importer.matchSave("Metroid Fusion (J)", carts))
        assertEquals(carts[2], Importer.matchSave("Metroid Fusion (U)", carts))
        assertEquals(carts[4], Importer.matchSave("The Legend of Zelda - Link's Awakening", carts))
        assertEquals(carts[5], Importer.matchSave("Tetris DX", carts))
    }

    @Test
    fun `a loose match only counts when it is the only one`() {
        // "Pokemon Emerald" is close to Emerald Version and nothing else.
        assertEquals(carts[0], Importer.matchSave("Pokemon Emerald", carts))
        // "Pokemon" could be either version: no match rather than a wrong one.
        assertNull(Importer.matchSave("Pokemon", carts))
        assertNull(Importer.matchSave("Golden Sun", carts))
    }

    @Test
    fun `only 64-bit arm ELF files pass as cores`() {
        val arm64 = ByteArray(20).apply {
            this[0] = 0x7F; this[1] = 'E'.code.toByte(); this[2] = 'L'.code.toByte(); this[3] = 'F'.code.toByte()
            this[4] = 2; this[5] = 1; this[18] = 183.toByte(); this[19] = 0
        }
        assertTrue(CoreFiles.isArm64Elf(arm64))
        assertFalse(CoreFiles.isArm64Elf(arm64.copyOf().apply { this[18] = 62 })) // x86-64
        assertFalse(CoreFiles.isArm64Elf(arm64.copyOf().apply { this[4] = 1 })) // 32-bit
        assertFalse(CoreFiles.isArm64Elf("<html>not a core</html>".toByteArray()))
        assertFalse(CoreFiles.isArm64Elf(ByteArray(4)))
    }

    @Test
    fun `games can live on the card while saves stay on the phone`() {
        val paths = Paths(File("/card/app"), File("/phone/app"))
        assertEquals(File("/card/app/Games/GBA"), paths.games(Platform.GBA))
        assertEquals(File("/card/app/Labels/GB"), paths.labels(Platform.GB))
        assertEquals(File("/phone/app/Saves/GBA"), paths.saves(Platform.GBA))
        assertEquals(File("/phone/app/States/GBC"), paths.states(Platform.GBC))
        assertEquals(File("/phone/app/BIOS"), paths.bios)
        assertEquals(File("/phone/app/Config/carts.json"), paths.cartProps)
    }

    @Test
    fun `moving the library takes games and labels and leaves saves`() {
        val from = Files.createTempDirectory("phone").toFile()
        val to = Files.createTempDirectory("card").toFile()
        try {
            File(from, "Games/GBA/Metroid Fusion.gba").apply { parentFile.mkdirs(); writeText("rom") }
            File(from, "Games/GB/Sub/Tetris.gb").apply { parentFile.mkdirs(); writeText("tetris") }
            File(from, "Labels/GBA/Metroid Fusion.png").apply { parentFile.mkdirs(); writeText("png") }
            File(from, "Saves/GBA/Metroid Fusion.srm").apply { parentFile.mkdirs(); writeText("save") }
            var last = 0 to 0
            val error = Storage.moveLibrary(from, to, { d, t -> last = d to t }, { false })
            assertNull(error)
            assertEquals(3 to 3, last)
            assertEquals("rom", File(to, "Games/GBA/Metroid Fusion.gba").readText())
            assertEquals("tetris", File(to, "Games/GB/Sub/Tetris.gb").readText())
            assertEquals("png", File(to, "Labels/GBA/Metroid Fusion.png").readText())
            assertFalse(File(from, "Games").exists())
            assertFalse(File(from, "Labels").exists())
            assertTrue(File(from, "Saves/GBA/Metroid Fusion.srm").isFile)
            assertFalse(File(to, "Saves").exists())
        } finally {
            from.deleteRecursively()
            to.deleteRecursively()
        }
    }

    @Test
    fun `a cancelled move leaves the library where it was`() {
        val from = Files.createTempDirectory("phone").toFile()
        val to = Files.createTempDirectory("card").toFile()
        try {
            File(from, "Games/GBA/A.gba").apply { parentFile.mkdirs(); writeText("a") }
            File(from, "Games/GBA/B.gba").apply { parentFile.mkdirs(); writeText("b") }
            var calls = 0
            val error = Storage.moveLibrary(from, to, { _, _ -> }, { calls++ > 0 })
            assertEquals("Cancelled", error)
            assertTrue(File(from, "Games/GBA/A.gba").isFile)
            assertTrue(File(from, "Games/GBA/B.gba").isFile)
            assertTrue(to.walkTopDown().none { it.isFile })
        } finally {
            from.deleteRecursively()
            to.deleteRecursively()
        }
    }

    @Test
    fun `every button has its own default key`() {
        val defaults = KeyMap.TITAN_DEFAULTS
        assertEquals(Button.entries.toSet(), defaults.keys)
        val keys = defaults.values.flatten()
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(listOf(android.view.KeyEvent.KEYCODE_Q), defaults[Button.QUICK_SAVE])
    }
}
