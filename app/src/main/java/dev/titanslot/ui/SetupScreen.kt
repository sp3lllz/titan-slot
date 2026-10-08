package dev.titanslot.ui

import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import dev.titanslot.app.AppState
import dev.titanslot.app.Setup
import dev.titanslot.app.SetupRow
import dev.titanslot.app.SetupStep
import dev.titanslot.app.Work
import dev.titanslot.core.CoreStatus
import dev.titanslot.core.Delivery
import dev.titanslot.data.Storage
import dev.titanslot.input.Button
import dev.titanslot.input.KeyMap

/** The setup wizard, full screen on first launch and over the shelf from Settings. */
@Composable
fun SetupScreen(app: AppState) {
    val setup = app.setup
    val step = setup.step ?: return
    val ui = LocalUi.current
    val keys = app.keyMap
    Sheet {
        Column(Modifier.fillMaxSize()) {
            StepHeader(setup)
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                when (step) {
                    SetupStep.WELCOME -> Welcome(app)
                    SetupStep.STORAGE -> Place(app)
                    SetupStep.CORES -> Cores(app)
                    SetupStep.GAMES -> Games(app)
                    SetupStep.SAVES -> Saves(app)
                    SetupStep.CONTROLS -> ControlsGuide(app)
                    SetupStep.DONE -> Done(app)
                }
            }
            val legend = when {
                setup.busy -> arrayOf(keys.hint(Button.B) to "Stop")
                step == SetupStep.CONTROLS -> arrayOf("any key" to "Try it", keys.hint(Button.A) to "Continue")
                step == SetupStep.WELCOME -> arrayOf(keys.hint(Button.A) to "Continue")
                else -> arrayOf(
                    "${keys.hint(Button.UP)} ${keys.hint(Button.DOWN)}" to "Choose",
                    keys.hint(Button.A) to "Select",
                    keys.hint(Button.B) to "Back",
                )
            }
            Legend(*legend, modifier = Modifier.padding(top = ui.dp(10f), bottom = ui.dp(30f)))
        }
    }
}

@Composable
private fun StepHeader(setup: Setup) {
    val ui = LocalUi.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = ui.dp(48f), bottom = ui.dp(10f)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (setup.steps.size > 1) {
            BasicText(
                "STEP ${setup.index + 1} OF ${setup.steps.size}",
                style = label(14f, Ink.faint, FontWeight.Bold, 0.2f),
            )
            Row(
                Modifier.padding(top = ui.dp(8f)),
                horizontalArrangement = Arrangement.spacedBy(ui.dp(7f)),
            ) {
                setup.steps.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(ui.dp(if (i == setup.index) 9f else 6f))
                            .background(if (i <= setup.index) Ink.menu else Ink.edge, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun Title(text: String) {
    val ui = LocalUi.current
    BasicText(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ui.dp(40f), vertical = ui.dp(6f)),
        style = label(30f, Ink.menu, FontWeight.Bold).copy(textAlign = TextAlign.Center),
    )
}

@Composable
private fun Body(text: String, size: Float = 17f) {
    val ui = LocalUi.current
    BasicText(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ui.dp(44f), vertical = ui.dp(6f)),
        style = label(size, Ink.dim).copy(textAlign = TextAlign.Center),
    )
}

@Composable
private fun Note(text: String) {
    val ui = LocalUi.current
    BasicText(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ui.dp(44f), vertical = ui.dp(8f)),
        style = label(16f, Ink.menu).copy(textAlign = TextAlign.Center),
    )
}

/** The step's rows, with the selection bar on the current one. */
@Composable
private fun Rows(setup: Setup, label: (SetupRow) -> String, value: (SetupRow) -> String? = { null }) {
    setup.rows().forEachIndexed { i, row ->
        MenuRow(
            text = label(row),
            value = value(row),
            selected = !setup.busy && i == setup.row,
            pitch = 52f,
            onClick = { setup.select(i) },
            carets = row == SetupRow.FetchArt,
            textSize = 23f,
            valueWidth = 190f,
        )
    }
}

@Composable
private fun Progress(work: Work) {
    val ui = LocalUi.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ui.dp(44f), vertical = ui.dp(12f)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ui.dp(8f)),
    ) {
        BasicText(
            if (work.total > 0) "${work.label}: ${work.done} of ${work.total}" else "${work.label}…",
            style = label(17f, Ink.menu),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(ui.dp(6f))
                .background(Ink.recess, RoundedCornerShape(ui.dp(3f))),
        ) {
            if (work.total > 0) {
                Box(
                    Modifier
                        .fillMaxWidth(work.done.toFloat() / work.total)
                        .height(ui.dp(6f))
                        .background(Ink.menu, RoundedCornerShape(ui.dp(3f))),
                )
            }
        }
        if (work.name.isNotEmpty()) {
            BasicText(work.name, style = label(14f, Ink.faint), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun nextLabel(setup: Setup, last: String = "Continue"): String =
    if (setup.steps.size == 1) "Done" else last

// ---- steps ---------------------------------------------------------------------------------

@Composable
private fun ColumnScope.Welcome(app: AppState) {
    val ui = LocalUi.current
    Spacer(Modifier.height(ui.dp(40f)))
    BasicText(
        "titan slot.",
        modifier = Modifier.fillMaxWidth(),
        style = label(56f, Ink.menu, FontWeight.Bold).copy(textAlign = TextAlign.Center),
    )
    Body("Your Game Boy, Game Boy Color and Game Boy Advance carts on a shelf, played on the Titan's keyboard.", 19f)
    Spacer(Modifier.height(ui.dp(26f)))
    listOf(
        "Choose where your games live",
        "Install the emulators",
        "Add your games and saves",
        "Learn the controls",
    ).forEachIndexed { i, text ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ui.dp(150f), vertical = ui.dp(5f)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("${i + 1}", modifier = Modifier.width(ui.dp(34f)), style = label(18f, Ink.faint, FontWeight.Bold))
            BasicText(text, style = label(18f, Ink.dim))
        }
    }
    Spacer(Modifier.weight(1f))
    Rows(app.setup, label = { "Get Started" })
}

@Composable
private fun Place(app: AppState) {
    val setup = app.setup
    Title("Where should your games live?")
    Body(
        "Games and cart art go here. Battery saves and save states always stay on the phone, " +
            "so swapping cards never loses your progress.",
    )
    val current = app.storage.libraryVolume
    Rows(
        setup,
        label = { row ->
            val v = (row as SetupRow.Place).volume
            if (v.id == current) "${v.name}  ✓" else v.name
        },
        value = { row -> "${size((row as SetupRow.Place).volume.free)} free" },
    )
    if (!setup.hasCard) Note("No microSD card found. Put one in and it shows up here.")
    setup.work?.let { Progress(it) }
    if (current != Storage.PHONE && !app.paths.libraryAvailable) {
        Note("${app.storage.libraryName} isn't in the phone. Put it back, or pick somewhere else.")
    }
}

@Composable
private fun Cores(app: AppState) {
    val setup = app.setup
    Title("Install the emulators")
    Body(
        "Gambatte plays Game Boy and Game Boy Color games, mGBA plays Game Boy Advance. " +
            "They come from ${Delivery.SOURCE} and take a few megabytes.",
    )
    Rows(
        setup,
        label = { row ->
            when (row) {
                is SetupRow.Engine -> row.core.title
                else -> if (setup.coresReady) nextLabel(setup) else "Skip For Now"
            }
        },
        value = { row -> (row as? SetupRow.Engine)?.let { status(setup.cores[it.core]) } },
    )
    setup.coreError?.let { Note("Couldn't install: $it. Select an emulator to try again.") }
    if (!setup.coresReady && setup.coreError == null) Body("Keep the app open while they install.", 15f)
}

private fun status(s: CoreStatus?): String = when (s) {
    null, CoreStatus.Missing -> "Not installed"
    CoreStatus.Waiting -> "Waiting"
    is CoreStatus.Downloading -> s.fraction?.let { "${(it * 100).toInt()}%" } ?: "Downloading"
    CoreStatus.Installing -> "Installing"
    CoreStatus.Installed -> "Installed"
    is CoreStatus.Failed -> "Try again"
}

@Composable
private fun Games(app: AppState) {
    val setup = app.setup
    Title("Add your games")
    Body(
        "Pick a folder (sub-folders are searched too) or single files: .gb, .gbc and .gba ROMs, " +
            "or zips of them. They're copied to ${app.storage.libraryName}; the originals stay where they are.",
    )
    Rows(
        setup,
        label = { row ->
            when (row) {
                SetupRow.Folder -> "Choose a Folder"
                SetupRow.Files -> "Choose Files"
                else -> nextLabel(setup)
            }
        },
    )
    val work = setup.work
    when {
        work != null -> Progress(work)
        setup.gamesNote != null -> Note(setup.gamesNote!!)
    }
    val count = app.shelves.sumOf { it.size }
    if (work == null && count > 0) Body("${if (count == 1) "1 game" else "$count games"} on the shelf", 15f)
    Body("Bring your own legally obtained games.", 14f)
}

@Composable
private fun Saves(app: AppState) {
    val setup = app.setup
    Title("Bring your saves")
    Body(
        "Battery saves (.sav or .srm) from another emulator or a flash cart pick up where you " +
            "left off. They're matched to your games by file name, so keep the names alike.",
    )
    Rows(
        setup,
        label = { row ->
            when (row) {
                SetupRow.Folder -> "Choose a Folder"
                SetupRow.Files -> "Choose Files"
                else -> nextLabel(setup)
            }
        },
    )
    val work = setup.work
    when {
        work != null -> Progress(work)
        setup.savesNote != null -> Note(setup.savesNote!!)
    }
    if (work == null) Body("A save that replaces one already here keeps the old one as a .bak file.", 14f)
}

@Composable
private fun ColumnScope.ControlsGuide(app: AppState) {
    val ui = LocalUi.current
    val keys = app.keyMap
    Title("Your keyboard is the controller")
    Spacer(Modifier.height(ui.dp(8f)))
    KeyboardDiagram(keys, app.setup.pressed)
    BasicText(
        pressedText(keys, app.setup.pressed),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = ui.dp(30f), vertical = ui.dp(14f)),
        style = label(18f, Ink.menu).copy(textAlign = TextAlign.Center),
    )
    val menu = keys.hint(Button.MENU)
    val quick = keys.hint(Button.QUICK_SAVE)
    val playing = listOf(
        "tap $menu" to "Pause menu",
        "hold $menu" to "Save and eject",
        quick to "Quick save",
        "hold $quick" to "Load newest save",
        "hold ${keys.hint(Button.FAST_FORWARD)}" to "Fast forward",
        "hold ${keys.hint(Button.REWIND)}" to "Rewind",
    )
    val shelf = listOf(
        keys.hint(Button.A) to "Play",
        "hold ${keys.hint(Button.A)}" to "New game",
        "${keys.hint(Button.LEFT)} ${keys.hint(Button.RIGHT)}" to "Browse",
        "${keys.hint(Button.L)} ${keys.hint(Button.R)}" to "Switch shelf",
        keys.hint(Button.START) to "Cart options",
        menu to "Settings",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ui.dp(40f)),
        horizontalArrangement = Arrangement.spacedBy(ui.dp(30f)),
    ) {
        ShortcutColumn("In a game", playing, Modifier.weight(1f))
        ShortcutColumn("On the shelf", shelf, Modifier.weight(1f))
    }
    Spacer(Modifier.weight(1f))
    Body("Change any key in Settings › Controls. A Bluetooth controller works too.", 14f)
}

@Composable
private fun ShortcutColumn(title: String, items: List<Pair<String, String>>, modifier: Modifier) {
    val ui = LocalUi.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ui.dp(7f))) {
        BasicText(title.uppercase(), style = label(13f, Ink.faint, FontWeight.Bold, 0.16f))
        items.forEach { (key, what) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeyCap(key)
                BasicText(
                    what,
                    modifier = Modifier.padding(start = ui.dp(8f)),
                    style = label(15f, Ink.dim),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val KEY_ROWS = listOf("QWERTYUIOP", "ASDFGHJKL", "ZXCVBNM")

/** The Titan's letter keys, each marked with what it does; the last key pressed lights up. */
@Composable
private fun KeyboardDiagram(keys: KeyMap, pressed: Int?) {
    val ui = LocalUi.current
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ui.dp(6f)),
    ) {
        KEY_ROWS.forEach { letters ->
            Row(horizontalArrangement = Arrangement.spacedBy(ui.dp(6f))) {
                letters.forEach { ch ->
                    val code = KeyEvent.KEYCODE_A + (ch - 'A')
                    val button = keys.buttonFor(code)
                    val lit = code == pressed
                    val bg = when {
                        lit -> Ink.menu
                        button != null -> Ink.edge
                        else -> Ink.recess
                    }
                    Column(
                        Modifier
                            .size(ui.dp(60f), ui.dp(62f))
                            .background(bg, RoundedCornerShape(ui.dp(7f))),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        BasicText(
                            ch.toString(),
                            style = label(21f, if (lit) Ink.housing else if (button != null) Ink.menu else Ink.faint, FontWeight.Bold),
                        )
                        if (button != null) {
                            BasicText(
                                short(button),
                                style = label(11f, if (lit) Ink.housing else Ink.dim, FontWeight.Bold),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun short(b: Button): String = when (b) {
    Button.UP -> "↑"
    Button.DOWN -> "↓"
    Button.LEFT -> "←"
    Button.RIGHT -> "→"
    Button.A -> "A"
    Button.B -> "B"
    Button.X -> "X"
    Button.Y -> "Y"
    Button.L -> "L"
    Button.R -> "R"
    Button.START -> "Start"
    Button.SELECT -> "Select"
    Button.MENU -> "Menu"
    Button.FAST_FORWARD -> "Fast"
    Button.REWIND -> "Rewind"
    Button.QUICK_SAVE -> "Save"
}

private fun pressedText(keys: KeyMap, pressed: Int?): String {
    val code = pressed ?: return "Press any key to see what it does"
    val name = KeyMap.keyName(code)
    val what = when (keys.buttonFor(code)) {
        null -> return "$name isn't used"
        Button.UP -> "up on the D-pad"
        Button.DOWN -> "down on the D-pad"
        Button.LEFT -> "left on the D-pad"
        Button.RIGHT -> "right on the D-pad"
        Button.A -> "the A button: play, confirm"
        Button.B -> "the B button: back"
        Button.X -> "the X button"
        Button.Y -> "the Y button"
        Button.L -> "the L shoulder button"
        Button.R -> "the R shoulder button"
        Button.START -> "Start: cart options on the shelf"
        Button.SELECT -> "Select"
        Button.MENU -> "the menu: tap to pause, hold to save and eject"
        Button.FAST_FORWARD -> "fast forward: hold it, or double-tap to lock"
        Button.REWIND -> "rewind: hold it"
        Button.QUICK_SAVE -> "quick save: tap to save, hold to load"
    }
    return "$name is $what"
}

@Composable
private fun Done(app: AppState) {
    val setup = app.setup
    val ui = LocalUi.current
    Spacer(Modifier.height(ui.dp(20f)))
    Title("You're all set")
    val count = app.shelves.sumOf { it.size }
    Body(
        when (count) {
            0 -> "No games yet: add them any time from Settings › Add Games."
            1 -> "1 game is on the shelf."
            else -> "$count games are on the shelf."
        },
        19f,
    )
    if (!setup.coresReady) Body("The emulators aren't installed yet: Settings › Emulator Cores.", 17f)
    Body("Fetch Cart Art downloads the real printed labels for your carts. It needs internet.", 15f)
    Spacer(Modifier.height(ui.dp(16f)))
    Rows(
        setup,
        label = { row ->
            when (row) {
                SetupRow.FetchArt -> "Fetch Cart Art"
                else -> if (setup.firstRun) "Start Playing" else "Done"
            }
        },
        value = { row -> if (row == SetupRow.FetchArt) (if (setup.fetchArt) "On" else "Off") else null },
    )
}

private fun size(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    return when {
        gb >= 100 -> "%.0f GB".format(gb)
        gb >= 1 -> "%.1f GB".format(gb)
        else -> "%.0f MB".format(bytes / 1_000_000.0)
    }
}
