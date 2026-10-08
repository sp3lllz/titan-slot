package dev.titanslot.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.titanslot.core.Core
import dev.titanslot.core.CoreStatus
import dev.titanslot.data.CartPropsStore
import dev.titanslot.data.Importer
import dev.titanslot.data.Library
import dev.titanslot.data.StateStore
import dev.titanslot.data.Storage
import dev.titanslot.input.Button
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SetupStep { WELCOME, STORAGE, CORES, GAMES, SAVES, CONTROLS, DONE }

/** One row of a setup step. */
sealed interface SetupRow {
    data class Place(val volume: Storage.Volume) : SetupRow
    data class Engine(val core: Core) : SetupRow
    data object Folder : SetupRow
    data object Files : SetupRow
    data object FetchArt : SetupRow
    data object Next : SetupRow
}

/** A copy or move in progress. [total] is 0 while the files are still being listed. */
data class Work(val label: String, val done: Int, val total: Int, val name: String)

/**
 * The setup wizard: where the library lives, the emulator cores, games, saves and controls.
 * On first launch it runs end to end, full screen; afterwards Settings opens single steps
 * (Add Games, Import Saves, Game Storage, Emulator Cores) or the whole guide again.
 */
class Setup(private val app: AppState) {
    var steps by mutableStateOf<List<SetupStep>>(emptyList())
        private set
    var index by mutableIntStateOf(0)
        private set
    val step: SetupStep? get() = steps.getOrNull(index)
    val active: Boolean get() = steps.isNotEmpty()
    /** The first run can't be closed, only finished. */
    var firstRun by mutableStateOf(false)
        private set
    var row by mutableIntStateOf(0)
        private set

    var volumes by mutableStateOf<List<Storage.Volume>>(emptyList())
        private set
    var cores by mutableStateOf<Map<Core, CoreStatus>>(emptyMap())
        private set
    var coreError by mutableStateOf<String?>(null)
        private set
    var work by mutableStateOf<Work?>(null)
        private set
    var gamesNote by mutableStateOf<String?>(null)
        private set
    var savesNote by mutableStateOf<String?>(null)
        private set
    /** The last key pressed on the controls step, to light it up on the keyboard. */
    var pressed by mutableStateOf<Int?>(null)
        private set
    var fetchArt by mutableStateOf(true)
        private set

    private var job: Job? = null
    private var coreJob: Job? = null
    @Volatile private var cancelled = false
    private var returnTo: Overlay? = null

    val busy: Boolean get() = job?.isActive == true

    fun start() = open(SetupStep.entries, firstRun = true)

    /** Opens [steps] over the shelf; closing goes back to [returnTo]. */
    fun open(steps: List<SetupStep>, firstRun: Boolean = false, returnTo: Overlay? = null) {
        this.steps = steps
        this.firstRun = firstRun
        this.returnTo = returnTo
        gamesNote = null
        savesNote = null
        coreError = null
        work = null
        index = 0
        enter()
    }

    fun close() {
        cancelled = true
        job?.cancel()
        steps = emptyList()
        app.closeSetup(returnTo)
    }

    private fun enter() {
        pressed = null
        when (step) {
            SetupStep.STORAGE -> {
                refreshVolumes()
                row = volumes.indexOfFirst { it.id == app.storage.libraryVolume }.coerceAtLeast(0)
                return
            }
            SetupStep.CORES -> {
                refreshCores()
                if (cores.values.any { it != CoreStatus.Installed }) installCores()
            }
            else -> Unit
        }
        row = 0
    }

    fun onResume() {
        when (step) {
            SetupStep.STORAGE -> refreshVolumes()
            SetupStep.CORES -> if (coreJob?.isActive != true) refreshCores()
            else -> Unit
        }
    }

    fun rows(): List<SetupRow> = when (step) {
        SetupStep.STORAGE -> volumes.map { SetupRow.Place(it) }
        SetupStep.CORES -> Core.entries.map { SetupRow.Engine(it) } + SetupRow.Next
        SetupStep.GAMES, SetupStep.SAVES -> listOf(SetupRow.Folder, SetupRow.Files, SetupRow.Next)
        SetupStep.DONE -> listOf(SetupRow.FetchArt, SetupRow.Next)
        SetupStep.WELCOME, SetupStep.CONTROLS -> listOf(SetupRow.Next)
        null -> emptyList()
    }

    // ---- input ----------------------------------------------------------------------------

    fun key(b: Button, down: Boolean) {
        if (!down) return
        if (busy) {
            if (b == Button.B) cancelWork()
            return
        }
        // Every key is fair game on the controls step; only A moves on.
        if (step == SetupStep.CONTROLS) {
            if (b == Button.A) next()
            return
        }
        val rows = rows()
        when (b) {
            Button.UP -> if (rows.isNotEmpty()) row = (row - 1).mod(rows.size)
            Button.DOWN -> if (rows.isNotEmpty()) row = (row + 1).mod(rows.size)
            Button.LEFT, Button.RIGHT -> if (rows.getOrNull(row) == SetupRow.FetchArt) fetchArt = !fetchArt
            Button.A -> rows.getOrNull(row)?.let(::activate)
            Button.B -> back()
            Button.MENU -> if (!firstRun) close()
            else -> Unit
        }
    }

    /** Raw keys on the controls step, so even unbound ones show as pressed. */
    fun capture(keyCode: Int) {
        if (step == SetupStep.CONTROLS) pressed = keyCode
    }

    fun back() {
        when {
            busy -> cancelWork()
            index > 0 -> {
                index--
                enter()
            }
            firstRun -> app.host.moveToBack()
            else -> close()
        }
    }

    fun select(i: Int) {
        if (busy) return
        val rows = rows()
        if (i !in rows.indices) return
        row = i
        activate(rows[i])
    }

    private fun activate(r: SetupRow) {
        when (r) {
            is SetupRow.Place -> choose(r.volume)
            is SetupRow.Engine -> if (cores[r.core] !is CoreStatus.Installed) installCores()
            SetupRow.Folder -> {
                val picked = app.host.pickFolder { uri -> import { app.importer.tree(uri) } }
                if (!picked) app.toast("No File Picker On This Phone")
            }
            SetupRow.Files -> {
                val picked = app.host.pickFiles { uris -> import { app.importer.files(uris) } }
                if (!picked) app.toast("No File Picker On This Phone")
            }
            SetupRow.FetchArt -> fetchArt = !fetchArt
            SetupRow.Next -> next()
        }
    }

    fun next() {
        if (step == SetupStep.CORES && coreJob?.isActive == true) {
            app.toast("Still Installing")
            return
        }
        if (index < steps.lastIndex) {
            index++
            enter()
        } else {
            finish()
        }
    }

    private fun finish() {
        val scrape = step == SetupStep.DONE && fetchArt
        job?.cancel()
        steps = emptyList()
        if (firstRun) app.finishSetup(scrape) else app.closeSetup(returnTo, scrape)
    }

    /** Single steps close once their job is done; the full guide moves on. */
    private fun advance() {
        if (steps.size == 1) close() else next()
    }

    private fun cancelWork() {
        cancelled = true
    }

    /** Runs [block] on the main thread; for progress reported from a worker thread. */
    private fun post(block: () -> Unit) {
        app.scope.launch(Dispatchers.Main.immediate) { block() }
    }

    // ---- storage ---------------------------------------------------------------------------

    private fun refreshVolumes() {
        volumes = app.storage.volumes()
    }

    val hasCard: Boolean get() = volumes.any { it.removable }

    private fun choose(volume: Storage.Volume) {
        val from = app.paths
        if (volume.id == app.storage.libraryVolume && from.libraryAvailable) {
            advance()
            return
        }
        cancelled = false
        job = app.scope.launch {
            val games = withContext(Dispatchers.IO) {
                from.libraryAvailable && from.games.walkTopDown().any { it.isFile && !it.name.startsWith(".") }
            }
            if (games) {
                work = Work("Moving your games to ${volume.name}", 0, 0, "")
                val error = try {
                    withContext(Dispatchers.IO) {
                        Storage.moveLibrary(
                            from.library, volume.root,
                            progress = { done, total -> post { work = Work("Moving your games to ${volume.name}", done, total, "") } },
                            cancelled = { cancelled },
                        )
                    }
                } finally {
                    work = null
                }
                if (error != null) {
                    app.toast(error)
                    return@launch
                }
            }
            app.storage.choose(volume)
            app.refreshPaths()
            app.toast("Games Go On ${volume.name}")
            advance()
        }
    }

    // ---- cores -------------------------------------------------------------------------------

    private fun refreshCores() {
        cores = Core.entries.associateWith { core ->
            when {
                app.cores.isInstalled(core) -> CoreStatus.Installed
                cores[core] is CoreStatus.Failed -> cores.getValue(core)
                else -> CoreStatus.Missing
            }
        }
    }

    val coresReady: Boolean get() = Core.entries.all { cores[it] == CoreStatus.Installed }

    private fun installCores() {
        if (coreJob?.isActive == true) return
        coreError = null
        coreJob = app.scope.launch {
            val ok = app.cores.install(Core.entries) { core, status ->
                post {
                    cores = cores + (core to status)
                    if (status is CoreStatus.Failed) coreError = status.reason
                }
            }
            if (ok) {
                refreshCores()
                if (step == SetupStep.CORES) app.toast("Emulators Ready")
            }
        }
    }

    // ---- games and saves ------------------------------------------------------------------------

    private fun import(list: () -> List<Importer.Picked>) {
        if (busy) return
        val paths = app.paths
        if (!paths.libraryAvailable) {
            app.toast("Put ${app.storage.libraryName} Back In First")
            return
        }
        cancelled = false
        val saves = step == SetupStep.SAVES
        job = app.scope.launch {
            work = Work(if (saves) "Looking for saves" else "Looking for games", 0, 0, "")
            val note = try {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val picked = list()
                        if (saves) importSaves(picked, paths) else importGames(picked, paths)
                    }.getOrElse { it.message ?: "Couldn't read that" }
                }
            } finally {
                work = null
            }
            if (saves) savesNote = note else gamesNote = note
            app.rescan()
        }
    }

    private fun importGames(picked: List<Importer.Picked>, paths: dev.titanslot.data.Paths): String {
        val r = app.importer.importGames(
            picked, paths,
            progress = { p -> post { work = Work("Copying games", p.done, p.total, p.name) } },
            cancelled = { cancelled },
        )
        return buildList {
            when {
                r.added > 0 -> add("Added ${plural(r.added, "game")}")
                r.existing == 0 && r.error == null -> add("No Game Boy, Game Boy Color or Game Boy Advance games there")
            }
            if (r.existing > 0) add("${plural(r.existing, "game")} already on the shelf")
            if (r.skipped > 0) add("${plural(r.skipped, "other file")} left out")
            r.error?.let { add("Stopped: $it") }
        }.joinToString(" · ")
    }

    private fun importSaves(picked: List<Importer.Picked>, paths: dev.titanslot.data.Paths): String {
        val carts = Library.scan(paths, CartPropsStore(paths.cartProps)).values.flatten()
        if (carts.isEmpty()) return "Add your games first: saves are matched to them by name"
        val r = app.importer.importSaves(
            picked, carts, StateStore(paths),
            progress = { p -> post { work = Work("Copying saves", p.done, p.total, p.name) } },
            cancelled = { cancelled },
        )
        r.matched.forEach { app.settings.startFreshNextTime(it) }
        return buildList {
            when {
                r.matched.isNotEmpty() -> add("Imported ${plural(r.matched.size, "save")}")
                r.same == 0 && r.unmatched.isEmpty() && r.error == null -> add("No .sav or .srm files there")
            }
            if (r.same > 0) add("${plural(r.same, "save")} already up to date")
            if (r.unmatched.isNotEmpty()) {
                val names = r.unmatched.take(3).joinToString(", ") + if (r.unmatched.size > 3) " and ${r.unmatched.size - 3} more" else ""
                add("No game found for $names")
            }
            r.error?.let { add("Stopped: $it") }
        }.joinToString(" · ")
    }

    private fun plural(n: Int, what: String) = if (n == 1) "1 $what" else "$n ${what}s"
}
