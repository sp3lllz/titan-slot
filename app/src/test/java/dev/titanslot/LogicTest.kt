package dev.titanslot

import dev.titanslot.app.ShelfModel
import dev.titanslot.core.Platform
import dev.titanslot.data.Cart
import dev.titanslot.data.CartShape
import dev.titanslot.data.Finish
import dev.titanslot.data.Library
import dev.titanslot.data.RomHeader
import dev.titanslot.data.Scaling
import dev.titanslot.data.Shells
import dev.titanslot.game.RewindBuffer
import dev.titanslot.ui.gameViewSize
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LogicTest {

    private fun shelf(vararg titles: String) = ShelfModel(
        Platform.GBA,
        titles.map { t ->
            Cart(Platform.GBA, File("$t.gba"), t, t, null, Shells.GBA_DEFAULT, CartShape.GBA)
        },
    )

    @Test
    fun `down jumps to the next letter and wraps`() {
        val s = shelf("Advance Wars", "Alpha", "Bomberman", "Castlevania", "Contra")
        s.jumpLetter(1)
        assertEquals(2, s.index)
        assertEquals(2f, s.ride)
        s.select(4)
        s.jumpLetter(1)
        assertEquals(0, s.index)
        assertEquals(5f, s.ride)
    }

    @Test
    fun `up goes to the start of this letter, then the previous one`() {
        val s = shelf("Advance Wars", "Alpha", "Bomberman", "Castlevania", "Contra")
        s.select(4)
        s.jumpLetter(-1)
        assertEquals(3, s.index)
        s.jumpLetter(-1)
        assertEquals(2, s.index)
        s.jumpLetter(-1)
        assertEquals(0, s.index)
        s.jumpLetter(-1)
        assertEquals(3, s.index)
    }

    @Test
    fun `stepping wraps and the spring settles exactly on the target`() {
        val s = shelf("A", "B", "C")
        s.step(-1)
        assertEquals(2, s.index)
        assertEquals(-1f, s.ride)
        repeat(240) { s.update(1f / 120f) }
        assertTrue(s.settled)
        assertEquals(s.ride, s.scroll)
    }

    @Test
    fun `cart offsets cover short shelves`() {
        val one = shelf("Solo")
        assertEquals(0, one.cartAt(0))
        assertNull(one.cartAt(1))
        val three = shelf("A", "B", "C")
        assertEquals(2, three.cartAt(-1))
        assertEquals(1, three.cartAt(4))
    }

    @Test
    fun `integer scaling on the Titan 2 Elite panel`() {
        val w = 1080f
        val h = 1200f
        assertEquals(960 to 864, gameViewSize(w, h, Platform.GB, Scaling.INTEGER))
        assertEquals(960 to 864, gameViewSize(w, h, Platform.GBC, Scaling.INTEGER))
        assertEquals(960 to 640, gameViewSize(w, h, Platform.GBA, Scaling.INTEGER))
        assertEquals(1080 to 972, gameViewSize(w, h, Platform.GB, Scaling.FIT))
        assertEquals(1080 to 720, gameViewSize(w, h, Platform.GBA, Scaling.FIT))
    }

    @Test
    fun `titles lose region and dump tags`() {
        assertEquals("Pokemon - Emerald Version", Cart.titleOf("Pokemon - Emerald Version (USA, Europe) [!]"))
        assertEquals("Tetris", Cart.titleOf("Tetris (World) (Rev 1)"))
        assertEquals("(Proto)", Cart.titleOf("(Proto)"))
        assertEquals(
            "Super Mario Advance 4 - Super Mario Bros 3",
            Cart.titleOf("1190 - Super Mario Advance 4 - Super Mario Bros 3 (E)(Menace)"),
        )
    }

    @Test
    fun `game boy headers pick plastics like slot does`() {
        val red = gbRom("POKEMON RED", code = "", cgb = 0x00, overseas = true)
        val (redShell, redShape) = Shells.of(Platform.GB, RomHeader.read(red, Platform.GB.extensions))
        assertEquals(0xC0282C, redShell.rgb)
        assertEquals(CartShape.GB_NOTCHED, redShape)

        val crystal = gbRom("PM_CRYSTAL", code = "BYTE", cgb = 0xC0, overseas = true)
        val (crystalShell, crystalShape) = Shells.of(Platform.GBC, RomHeader.read(crystal, Platform.GBC.extensions))
        assertEquals(Finish.GLITTER, crystalShell.finish)
        assertEquals(CartShape.GB_ROUNDED, crystalShape)

        val dual = gbRom("SOMEGAME", code = "AZZE", cgb = 0x80, overseas = true)
        val (dualShell, dualShape) = Shells.of(Platform.GBC, RomHeader.read(dual, Platform.GBC.extensions))
        assertEquals(Shells.DUAL_MODE, dualShell)
        assertEquals(CartShape.GB_NOTCHED, dualShape)
    }

    @Test
    fun `gba game codes pick plastics`() {
        val rom = File.createTempFile("emerald", ".gba").apply {
            deleteOnExit()
            val b = ByteArray(0xC0)
            "POKEMON EMER".toByteArray().copyInto(b, 0xA0)
            "BPEE".toByteArray().copyInto(b, 0xAC)
            writeBytes(b)
        }
        val (shell, shape) = Shells.of(Platform.GBA, RomHeader.read(rom, Platform.GBA.extensions))
        assertEquals(0x249C60, shell.rgb)
        assertEquals(Finish.CLEAR, shell.finish)
        assertEquals(CartShape.GBA, shape)
    }

    @Test
    fun `roms go on the shelf their extension says, folders settle the rest`() {
        assertEquals(Platform.GB, Library.platformOf(File("Zelda.gb"), Platform.GBA))
        assertEquals(Platform.GBC, Library.platformOf(File("Tetris DX.gb"), Platform.GBC))
        assertEquals(Platform.GBA, Library.platformOf(File("Metroid.gba"), Platform.GB))
        assertEquals(Platform.GBA, Library.platformOf(File("Advance Wars.zip"), Platform.GBA))
        assertNull(Library.platformOf(File("notes.txt"), Platform.GBA))
        // NES, SNES and DS are gone from the shelf.
        assertNull(Library.platformOf(File("Contra.nes"), null))
        assertNull(Library.platformOf(File("Platinum.nds"), null))
        assertNull(Library.platformOf(File("Mario World.sfc"), null))
    }

    @Test
    fun `rewind keeps the oldest state and respects its budget`() {
        val r = RewindBuffer(maxBytes = 30)
        r.push(ByteArray(10) { 1 })
        r.push(ByteArray(10) { 2 })
        r.push(ByteArray(10) { 3 })
        r.push(ByteArray(10) { 4 })
        assertArrayEquals(ByteArray(10) { 4 }, r.pop())
        assertArrayEquals(ByteArray(10) { 3 }, r.pop())
        assertArrayEquals(ByteArray(10) { 2 }, r.pop())
        assertArrayEquals(ByteArray(10) { 2 }, r.pop())
    }

    private fun gbRom(title: String, code: String, cgb: Int, overseas: Boolean): File =
        File.createTempFile("gbrom", ".gb").apply {
            deleteOnExit()
            val b = ByteArray(0x150)
            title.toByteArray().copyInto(b, 0x134)
            code.toByteArray().copyInto(b, 0x13F)
            b[0x143] = cgb.toByte()
            b[0x14A] = if (overseas) 1 else 0
            writeBytes(b)
        }
}
