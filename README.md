# Titan Slot

An emulator frontend built for the **Unihertz Titan 2 Elite**, with the phone's QWERTY keyboard
as the controller.

Titan Slot is a port of **[slot](https://github.com/BrandonKowalski/slot)** by Brandon Kowalski,
a Game Boy-centric frontend for the Anbernic RG SP. The shelf of carts, the slot they go into,
the cart shapes and colours, the sounds and the controls are all slot's; Titan Slot brings them
to an Android phone and adds NES, SNES and DS shelves. If you have an RG SP, go use slot.

> [!IMPORTANT]
> **Titan Slot is meant for the Unihertz Titan 2 Elite only.** It has been built for, and tested
> on, that one phone and nothing else. The layout (its 1080 × 1200 screen, the camera cutout in
> the top-left corner and the rounded corners), the default keyboard controls, the arm64-only
> cores and the 60 Hz switch while playing all assume a Titan 2 Elite. It may install on other
> Android devices, but they are untested and unsupported.

> [!NOTE]
> **Coming next: the Clicks Communicator.** Once Clicks' keyboard phone launches, the plan is
> to add support for it as well.

| Shelf | Core |
| --- | --- |
| Game Boy | Gambatte (or mGBA, swap the chip in the cart sheet) |
| Game Boy Color | Gambatte (or mGBA) |
| Game Boy Advance | mGBA |
| NES | FCEUmm |
| SNES | Snes9x (current) |
| Nintendo DS | melonDS (top/bottom screens, touch the bottom one) |

The cores are the official [libretro](https://www.libretro.com) builds, run through
[LibretroDroid](https://github.com/Swordfish90/LibretroDroid). They aren't in this repository;
`scripts/fetch-cores.sh` downloads them.

## Build

Requires JDK 17 and the Android SDK (platform 36).

```bash
scripts/fetch-cores.sh          # downloads the five arm64 cores from buildbot.libretro.com
./gradlew assembleDebug         # -> app/build/outputs/apk/debug/app-debug.apk
```

Install on the phone (USB debugging on):

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On first launch the app asks for **All files access**, then creates `TitanSlot/` in the
phone's storage.

## Your games

```
TitanSlot/
  Games/GB  GBC  GBA  NES  SNES  NDS    ROMs (.zip works, sub-folders are fine)
  Labels/<same folders>/<rom name>.png  optional cart label art
  Saves/                                battery saves
  States/                               save states and their thumbnails
  BIOS/                                 optional: gba_bios.bin, gb_bios.bin, gbc_bios.bin,
                                        bios7.bin, bios9.bin, firmware.bin, disksys.rom
```

Without DS BIOS files melonDS uses its built-in FreeBIOS.

## Controls

Defaults for the Titan 2 Elite keyboard. Every one can be rebound in Settings > Controls, and
a Bluetooth gamepad works without setup.

| Key | Button |
| --- | --- |
| W A S D | D-pad |
| K | A |
| L | B |
| I | X |
| O | Y |
| Z | L bumper |
| M | R bumper |
| V | Start |
| B | Select |
| P or Back gesture | Menu |
| N | Fast-forward |
| X | Rewind |

**On the shelf:** A/D flip through carts, W/S jump a letter, Z/M switch shelves, tap K to
resume where you left off, hold K to start fresh, V to open the cart sheet, P for settings.
Touch works too: tap the sides to scroll, the middle to play.

**In game:**

| | |
| --- | --- |
| Tap P | Pause menu (save, load, states, reset, eject) |
| Hold P | Save a resume state and eject to the shelf |
| Double-tap P | Save state switcher (O deletes) |
| B + M | Save state |
| B + Z | Load the latest state |
| Hold N | Fast-forward; double-tap N locks it on |
| Hold X | Rewind (not on DS) |

Select still reaches the game: it is held back only until it's clear you aren't doing a
shortcut.

The clock and battery sit above the picture while you play (beside it on DS), along with
fast-forward and rewind marks.

## Cart art

Settings > **Scrape Cart Art** dresses the whole shelf:

- **GB, GBC and GBA** get the real printed cartridge label, scanned, from slot's art set
  (the one slot's Cart Studio uses). Carts are found by the ROM's CRC32, so file names don't
  matter. About 7,000 games are covered.
- **NES, SNES and DS** have no open source of label scans, so they get box art from the
  libretro thumbnails, matched by name (No-Intro names match directly; others by title and
  region).

It only fills carts without art, and upgrades box art it scraped earlier once a real label
exists. Art you put in `Labels/` yourself, or picked from the phone, is never replaced.

## The cart sheet

Press V (Start) on a cart. A live preview sits on top, and you can change:

| Row | |
| --- | --- |
| Name | Type a new one on the keyboard (Enter saves, Back cancels) |
| Scrape Art | Cart Label, Box Art, Title Screen or Screenshot; A/D picks, K fetches |
| Use Image From Phone | Any picture from the photo picker becomes the label |
| Art Fit | Fill the label panel, or fit the whole image |
| Remove Art | Back to a printed label with the cart's name |
| Colour | Auto (from the ROM, slot's tables) or a preset; K to type a hex code like `#2F5CC0` |
| Finish | Solid, Clear (shows the board) or Glitter |
| Outline | Game Boy carts: the notched DMG shell or the rounded Color one |
| Chip | GB/GBC: Gambatte or mGBA |
| Reset Cart | Undo all of the above |

Everything is saved to `TitanSlot/Config/carts.json`, which you can edit or copy to another
phone.

## Save states

- **Manual:** B + M, or Save State in the pause menu. Every one is kept, with a thumbnail.
  B + Z loads the newest; double-tap P to flick through them all.
- **Automatic:** a resume state is written when you eject a cart and whenever the app leaves
  the screen: Home, switching apps, swiping it away, or the screen turning off.
- On the shelf, tap K to pick up from the newest state (auto or manual), or hold K to start
  the game fresh. Battery saves (`.srm`, or melonDS's own `.sav`) are kept either way.

States are per core, in `States/<platform>/<rom name>/<core>/`.

## How it runs

The shelf is `MainActivity`. A game runs in `GameActivity` in its own `:game` process, as
Lemuroid does: the emulator view always gets a fresh window and surface, and the process exits
when the cart comes out, so each launch loads its core cleanly. The panel switches to its
60 Hz mode while a game is up.

## Settings

Fast-forward speed and sound, rewind, integer or fit scaling, screen filter (Sharp, Smooth,
LCD, CRT), colour correction, original Game Boy palette, controls, rescan, about.

## Credits

Full credits and acknowledgements are in **[CREDITS.md](CREDITS.md)**. In short:

- **[slot](https://github.com/BrandonKowalski/slot)** by Brandon Kowalski (GPL-3.0): the
  design, cart outlines, shell colour tables, carousel and insert animation, controls, cart
  sounds, and the real cart label scans from its art set.
- **[LibretroDroid](https://github.com/Swordfish90/LibretroDroid)** by Filippo Scognamiglio
  (GPL-3.0) runs the cores; **[Lemuroid](https://github.com/Swordfish90/Lemuroid)** was the
  reference for running games in their own process.
- **Cores:** Gambatte (GPL-2.0), mGBA (MPL-2.0), melonDS (GPL-3.0), FCEUmm (GPL-2.0) and
  Snes9x (non-commercial license), from [libretro](https://www.libretro.com).
- **Art:** label scans from [ScreenScraper](https://www.screenscraper.fr) (CC BY-NC-SA 4.0) by
  way of slot's art set; box art from the
  [libretro thumbnails](https://github.com/libretro-thumbnails/libretro-thumbnails).
- **Type:** Open Sans, SIL Open Font License.

## License

Titan Slot is licensed under the [GNU General Public License v3.0](LICENSE), the same as slot
and LibretroDroid. Snes9x's own license allows non-commercial use only, so builds that include it
must not be sold.

Game Boy, Nintendo DS, NES and Super Nintendo are trademarks of Nintendo, and Titan 2 Elite is a
Unihertz product. This is a fan project, not affiliated with either. Bring your own legally
obtained games; none are included or downloaded.
