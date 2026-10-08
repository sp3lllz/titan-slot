# Titan Slot

A Game Boy, Game Boy Color and Game Boy Advance frontend built for the **Unihertz Titan 2
Elite**, with the phone's QWERTY keyboard as the controller.

Titan Slot is a port of **[slot](https://github.com/BrandonKowalski/slot)** by Brandon Kowalski,
a Game Boy-centric frontend for the Anbernic RG SP. The shelf of carts, the slot they go into,
the cart shapes and colours, the sounds and the controls are all slot's; Titan Slot brings them
to an Android phone. If you have an RG SP, go use slot.

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

The cores are the official [libretro](https://www.libretro.com) builds, run through
[LibretroDroid](https://github.com/Swordfish90/LibretroDroid). They aren't in the base APK: the
setup wizard installs them on first launch.

## First launch

A short setup walks through everything, and any step can be run again from Settings:

1. **Where your games live:** the phone's storage or the microSD card. Battery saves and save
   states always stay on the phone, so swapping cards never loses progress. Settings > Game
   Storage moves the whole shelf later.
2. **The emulators:** Gambatte and mGBA download (a few megabytes) with a progress bar.
3. **Your games:** pick a folder (sub-folders are searched) or single files in the system file
   picker. `.gb`, `.gbc` and `.gba` ROMs and zips of them are copied in; a zip holding a whole
   collection is unpacked into one cart per game. Settings > Add Games does this any time.
4. **Your saves:** `.sav` or `.srm` battery saves from another emulator or a flash cart, matched
   to your games by name (so `Pokemon - Emerald Version (U).sav` finds
   `Pokemon - Emerald Version (USA, Europe).gba`). A save that replaces one already there keeps
   the old one as `.srm.bak`, and that game's next start skips its old resume state.
5. **Controls:** the keyboard, drawn with what every key does. Press keys to try them.
6. **Done:** optionally fetch the real printed cart labels for everything you imported.

No storage permission is asked for: everything goes through the system file picker.

## Build

Requires JDK 17 and the Android SDK (platform 36). There are two flavours, which differ only in
where the cores come from:

| Flavour | For | Cores |
| --- | --- | --- |
| `play` | Google Play | On-demand feature modules (`:core_gambatte`, `:core_mgba`) that Play delivers during setup. Google Play doesn't allow apps to download native code from anywhere else. |
| `direct` | Sideloading, your own testing | Setup downloads them from the libretro buildbot into the app's private storage. |

```bash
# Sideload build: nothing to fetch first.
./gradlew assembleDirectDebug        # -> app/build/outputs/apk/direct/debug/app-direct-debug.apk
adb install -r app/build/outputs/apk/direct/debug/app-direct-debug.apk

# Play build: put the cores into their feature modules, then build the bundle.
scripts/fetch-cores.sh               # downloads the two arm64 cores from buildbot.libretro.com
./gradlew bundlePlayRelease          # -> app/build/outputs/bundle/playRelease/app-play-release.aab
```

To try the Play flavour's on-demand install before publishing, upload the bundle to Play
Console's internal app sharing or internal testing track, or use bundletool's
`build-apks --local-testing`.

`fetch-cores.sh` warns when a core isn't aligned for 16 KB memory pages, which Google Play
requires of apps targeting Android 15 and later; such a core needs rebuilding with
`-Wl,-z,max-page-size=16384` before the bundle will be accepted.

## Your games

Titan Slot keeps everything in its own folders (`Android/data/dev.titanslot/files`): games and
cart art on the volume you chose, everything else on the phone.

```
On the volume you chose (phone or microSD card):
  Games/GB  GBC  GBA            ROMs (.zip works, sub-folders are fine)
  Labels/<same folders>/<rom>   optional cart label art

Always on the phone:
  Saves/                        battery saves
  States/                       save states and their thumbnails
  BIOS/                         optional: gba_bios.bin, gb_bios.bin, gbc_bios.bin
  Config/carts.json             what you changed in each cart sheet
```

Settings > About shows both paths. Copying ROMs into `Games/` over USB and picking Settings >
Rescan Games works too, though Add Games is easier.

Battery saves and cart settings are included in Android's backup; games and save states are
not. Uninstalling asks whether to keep the app's data.

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
| Q | Quick save (hold: quick load) |

**On the shelf:** A/D flip through carts, W/S jump a letter, Z/M switch shelves, tap K to
resume where you left off, hold K to start fresh, V to open the cart sheet, P for settings.
Touch works too: tap the sides to scroll, the middle to play.

**In game:**

| | |
| --- | --- |
| Tap Q | Quick save: a new save state, no menu |
| Hold Q | Quick load: the newest save state |
| Tap P | Pause menu (save, load, undo load, states, reset, eject) |
| Hold P | Save a resume state and eject to the shelf |
| Double-tap P | Save state switcher (O deletes) |
| B + M | Save state |
| B + Z | Load the latest state |
| Hold N | Fast-forward; double-tap N locks it on |
| Hold X | Rewind |

Loaded the wrong state? **Undo Load** in the pause menu puts the game back as it was.

Select still reaches the game: it is held back only until it's clear you aren't doing a
shortcut.

The clock and battery sit above the picture while you play, along with fast-forward and rewind
marks. The screen stays on while a game runs and may sleep when it's paused.

## Cart art

Settings > **Scrape Cart Art** dresses the whole shelf with the real printed cartridge labels,
scanned, from slot's art set (the one slot's Cart Studio uses). Carts are found by the ROM's
CRC32, so file names don't matter. About 7,000 games are covered. Box art, title screens and
screenshots from the libretro thumbnails can be picked per cart in the cart sheet.

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
| Remove Game | Press K twice: the ROM and its art go, its saves and states stay |

Everything is saved to `Config/carts.json`, which you can edit or copy to another phone.

## Save states

- **Manual:** Q, B + M, or Save State in the pause menu. Every one is kept, with a thumbnail.
  Hold Q or B + Z to load the newest; double-tap P to flick through them all.
- **Automatic:** a resume state is written when you eject a cart and whenever the app leaves
  the screen: Home, switching apps, swiping it away, or the screen turning off.
- On the shelf, tap K to pick up from the newest state (auto or manual), or hold K to start
  the game fresh. Battery saves (`.srm`) are kept either way, and written every 30 seconds
  while they change, so a crash or a flat battery loses little.

States are per core, in `States/<platform>/<rom name>/<core>/`.

## How it runs

The shelf is `MainActivity`. A game runs in `GameActivity` in its own `:game` process, as
Lemuroid does: the emulator view always gets a fresh window and surface, and the process exits
when the cart comes out, so each launch loads its core cleanly. The panel switches to its
60 Hz mode while a game is up.

## Settings

Fast-forward speed and sound, rewind, integer or fit scaling, screen filter (Sharp, Smooth,
LCD, CRT), colour correction, original Game Boy palette, controls, add games, import saves,
scrape cart art, game storage (phone or microSD), emulator cores, rescan, the setup guide, and
about.

## Credits

Full credits and acknowledgements are in **[CREDITS.md](CREDITS.md)**. In short:

- **[slot](https://github.com/BrandonKowalski/slot)** by Brandon Kowalski (GPL-3.0): the
  design, cart outlines, shell colour tables, carousel and insert animation, controls, cart
  sounds, and the real cart label scans from its art set.
- **[LibretroDroid](https://github.com/Swordfish90/LibretroDroid)** by Filippo Scognamiglio
  (GPL-3.0) runs the cores; **[Lemuroid](https://github.com/Swordfish90/Lemuroid)** was the
  reference for running games in their own process and delivering cores as feature modules.
- **Cores:** Gambatte (GPL-2.0) and mGBA (MPL-2.0), from [libretro](https://www.libretro.com).
- **Art:** label scans from [ScreenScraper](https://www.screenscraper.fr) (CC BY-NC-SA 4.0) by
  way of slot's art set; box art from the
  [libretro thumbnails](https://github.com/libretro-thumbnails/libretro-thumbnails).
- **Type:** Open Sans, SIL Open Font License.

## License

Titan Slot is licensed under the [GNU General Public License v3.0](LICENSE), the same as slot
and LibretroDroid. Builds may be sold, but whoever receives one is entitled to its complete
source code under the same license; About links to this repository for that.

Game Boy, Game Boy Color and Game Boy Advance are trademarks of Nintendo, and Titan 2 Elite is a
Unihertz product. This is a fan project, not affiliated with either. Bring your own legally
obtained games; none are included or downloaded.
