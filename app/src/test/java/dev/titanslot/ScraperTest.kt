package dev.titanslot

import dev.titanslot.core.Platform
import dev.titanslot.data.Cart
import dev.titanslot.data.CartPropsStore
import dev.titanslot.data.CartShape
import dev.titanslot.data.Shells
import dev.titanslot.data.Scraper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Matching against real names from thumbnails.libretro.com listings. */
class ScraperTest {
    private val gba = listOf(
        "Fire Emblem - The Sacred Stones (Europe) (En,Fr,De,Es,It) (Virtual Console)",
        "Fire Emblem - The Sacred Stones (Europe) (En,Fr,De,Es,It)",
        "Fire Emblem - The Sacred Stones (USA) (Virtual Console)",
        "Fire Emblem - The Sacred Stones (USA, Australia)",
        "Pokemon - Emerald Version (USA, Europe) (Alternate)",
        "Pokemon - Emerald Version (USA, Europe)",
        "Pokemon - Theta Emerald EX Version (Hack)",
        "Super Mario Advance 4 - Super Mario 3 + Mario Brothers (Japan)",
        "Super Mario Advance 4 - Super Mario Bros. 3 (Europe) (En,Fr,De,Es,It) (Rev 1) (Switch Online)",
        "Super Mario Advance 4 - Super Mario Bros. 3 (Europe) (En,Fr,De,Es,It)",
        "Super Mario Advance 4 - Super Mario Bros. 3 (USA)",
        "Super Mario Advance 4 - Super Mario Bros. 3 (USA, Australia) (Rev 1)",
        "Super Mario Advance 4-e - Series 1 - 07-A001 - Classic World 1-1 (USA)",
    )

    private val gb = listOf(
        "Legend of Zelda, The - Link's Awakening (Canada) (Fr)",
        "Legend of Zelda, The - Link's Awakening (France)",
        "Legend of Zelda, The - Link's Awakening (Germany)",
        "Legend of Zelda, The - Link's Awakening (USA, Europe) (Rev 1)",
        "Legend of Zelda, The - Link's Awakening (USA, Europe) (Rev 2)",
        "Legend of Zelda, The - Link's Awakening (USA, Europe)",
    )

    @Test
    fun `scene releases match on title and region`() {
        assertEquals(
            "Super Mario Advance 4 - Super Mario Bros. 3 (Europe) (En,Fr,De,Es,It)",
            Scraper.best("1190 - Super Mario Advance 4 - Super Mario Bros 3 (E)(Menace)", gba),
        )
    }

    @Test
    fun `GoodTools names find the No-Intro entry`() {
        assertEquals(
            "Legend of Zelda, The - Link's Awakening (USA, Europe)",
            Scraper.best("Legend of Zelda, The - Link's Awakening (V1.2) (U) [!]", gb),
        )
    }

    @Test
    fun `originals beat re-releases and hacks`() {
        assertEquals("Pokemon - Emerald Version (USA, Europe)", Scraper.best("Pokemon Emerald (U)", gba))
        assertEquals(
            "Fire Emblem - The Sacred Stones (USA, Australia)",
            Scraper.best("Fire Emblem - The Sacred Stones (USA)", gba),
        )
    }

    @Test
    fun `unrelated titles do not match`() {
        assertNull(Scraper.best("Metroid Fusion (USA)", gba))
        assertNull(Scraper.best("Super Mario", gba))
    }

    @Test
    fun `normalising ignores articles, punctuation and accents`() {
        assertEquals(
            Scraper.normalize("The Legend of Zelda: Link's Awakening"),
            Scraper.normalize("Legend of Zelda, The - Link's Awakening (USA)"),
        )
        assertEquals("pokemonemerald", Scraper.normalize("Pokémon Emerald"))
        assertEquals("breatheeasy", Scraper.normalize("Breathe Easy"))
    }

    @Test
    fun `thumbnail names replace reserved characters`() {
        assertEquals("Zelda _ Four Swords", Scraper.sanitize("Zelda & Four Swords"))
        assertEquals("What_ Me Worry_", Scraper.sanitize("What? Me Worry?"))
    }

    @Test
    fun `labels are keyed by the ROM's CRC32`() {
        val rom = java.io.File.createTempFile("crcrom", ".gba").apply {
            deleteOnExit()
            writeText("123456789")
        }
        val cart = Cart(Platform.GBA, rom, "x", "x", null, Shells.GBA_DEFAULT, CartShape.GBA)
        assertEquals("CBF43926", Scraper.crcOf(cart))
    }

    @Test
    fun `every shelf has a libretro thumbnail system`() {
        Platform.entries.forEach { assertTrue(Scraper.systemsFor(it).isNotEmpty()) }
        assertEquals("Nintendo - Game Boy Advance", Scraper.systemsFor(Platform.GBA).single())
    }

    @Test
    fun `hex colours parse in the forms people type`() {
        assertEquals(0x2F5CC0, CartPropsStore.parseHex("#2F5CC0"))
        assertEquals(0x2F5CC0, CartPropsStore.parseHex("2f5cc0"))
        assertEquals(0xFFFFFF, CartPropsStore.parseHex("#fff"))
        assertNull(CartPropsStore.parseHex("#12345"))
        assertEquals("#0A0B0C", CartPropsStore.hex(0x0A0B0C))
    }
}
