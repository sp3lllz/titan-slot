# Credits and acknowledgements

Titan Slot stands on a lot of other people's work. Thank you to everyone below.

## slot, by Brandon Kowalski

**[github.com/BrandonKowalski/slot](https://github.com/BrandonKowalski/slot)** · [slot-cfw.fyi](https://slot-cfw.fyi) · GPL-3.0

Titan Slot is a port of slot's idea and look to an Android phone. slot is a Game Boy-centric
frontend for the Anbernic RG SP that recreates the feeling of sliding a cart into a GBA SP. The
shelf of carts, the slot they go into, and the way the whole thing feels are slot's.

Taken from slot, or closely following it:

- **Cartridge outlines and moulding**: the GBA, Game Boy (DMG) and Game Boy Color silhouettes,
  ridges, cuts and gloss are slot's SVG path data (`crates/slot-ui/assets/cart.svg`,
  `gb_cart.svg`, `gbc_cart.svg` and their `*_detail.svg`), redrawn in
  [`CartArt.kt`](app/src/main/java/dev/titanslot/ui/CartArt.kt).
- **Cart plastics**: the shell colour tables that give Pokémon Ruby, Crystal, Kirby and others
  their real colours, from `crates/slot-ui/src/shell.rs`
  ([`Shells.kt`](app/src/main/java/dev/titanslot/data/Shells.kt)).
- **The carousel**: the critically damped spring, side cart scale and fade, wrap-around, letter
  jumping and accelerating key repeat, from `crates/slot-ui/src/shelf.rs`
  ([`ShelfModel.kt`](app/src/main/java/dev/titanslot/app/ShelfModel.kt)).
- **The slot and the insert**: the socket's housing, lip, slit and scoop, and the insert travel
  (ease to the catch, creep, push home), from `crates/slot-ui/src/slot_chrome.rs`.
- **The theme**: housing, recess, opening and edge colours, and menu inks
  (`crates/slot-store/src/theme.rs`, `quick_menu.rs`, `power_menu.rs`).
- **Controls**: tap A to resume, hold A to start fresh, START to open a cart, hold MENU to eject,
  double-tap MENU for the save state switcher, SELECT + R / L to save / load, hold or double-tap
  fast-forward, hold rewind, and SELECT reaching the game only once it can't be a shortcut.
- **Sounds**: the insert and eject sounds are slot's recording of a cart going into Brandon's
  childhood GBA (`crates/slot/assets/insert.pcm`, `eject.pcm`), wrapped as WAV.
- **Labels folder layout**: `Games/`, `Labels/<platform>/<rom name>.png`, `BIOS/`.
- **Real cart labels**: Titan Slot fetches label scans from slot's art set at
  [art.slot-cfw.fyi](https://art.slot-cfw.fyi), the one slot's
  [Cart Studio](https://studio.slot-cfw.fyi) uses, looked up by ROM CRC32 the way Cart Studio
  does.

## Emulation

Titan Slot is a frontend. The emulators are [libretro](https://www.libretro.com) cores, used
unmodified as built by the [libretro buildbot](https://buildbot.libretro.com). They are not
part of this repository: `scripts/fetch-cores.sh` downloads them into their feature modules for
Play builds, and direct builds download them on the phone during setup.

| System | Core | Upstream | License |
| --- | --- | --- | --- |
| Game Boy / Color | Gambatte | [libretro/gambatte-libretro](https://github.com/libretro/gambatte-libretro), originally by Sindre Aamås | GPL-2.0 |
| Game Boy Advance (and GB / GBC) | mGBA | [libretro/mgba](https://github.com/libretro/mgba), [mgba.io](https://mgba.io) by Vicki Pfau (endrift) | MPL-2.0 |

## LibretroDroid and Lemuroid, by Filippo Scognamiglio

**[github.com/Swordfish90/LibretroDroid](https://github.com/Swordfish90/LibretroDroid)** · GPL-3.0

LibretroDroid is the Android libretro frontend library that runs every core here: video,
audio, input, touch, save states and battery saves. Titan Slot uses it unmodified.

**[github.com/Swordfish90/Lemuroid](https://github.com/Swordfish90/Lemuroid)** · GPL-3.0

Lemuroid, LibretroDroid's app, was the reference for running each game in its own `:game`
process, for delivering cores as on-demand feature modules on Google Play (and finding them
afterwards), and for its Gambatte core settings.

## Art

- **Cartridge label scans** for GB, GBC and GBA come from
  [ScreenScraper](https://www.screenscraper.fr), by way of slot's art set. ScreenScraper's media
  is licensed [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/).
- **Box art, title screens and screenshots** (picked per cart in the cart sheet) come from the
  [libretro thumbnails](https://github.com/libretro-thumbnails/libretro-thumbnails), served at
  thumbnails.libretro.com, collected by the libretro community for RetroArch.
- The scans and box art are the work of their publishers and of the people who scanned and
  shared them. Titan Slot downloads them to your phone on request and does not include them.

## Type

**Open Sans**, by the Open Sans Project Authors
([github.com/googlefonts/opensans](https://github.com/googlefonts/opensans)), under the
[SIL Open Font License 1.1](licenses/OpenSans-OFL.txt). The font file comes from slot.

## Libraries and tools

- [Kotlin](https://kotlinlang.org) and [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines), JetBrains, Apache-2.0
- [Jetpack Compose and AndroidX](https://developer.android.com/jetpack) (Activity, Core, Lifecycle), Google, Apache-2.0
- [Android Gradle Plugin](https://developer.android.com/build) and the [Gradle](https://gradle.org) wrapper, Apache-2.0
- [Play Feature Delivery](https://developer.android.com/guide/playcore/feature-delivery), Google, in Play builds only (Play Core Software Development Kit Terms of Service)
- [JitPack](https://jitpack.io), which builds LibretroDroid for Gradle

## Trademarks

Game Boy, Game Boy Color and Game Boy Advance are trademarks of Nintendo. Titan and Titan 2 Elite
are products of Unihertz. Titan Slot is a fan project and is not affiliated with or endorsed by
Nintendo, Unihertz, or any of the projects above.

Bring your own legally obtained games. Titan Slot does not include or download any ROMs or BIOS
files.
