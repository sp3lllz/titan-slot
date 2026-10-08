package dev.titanslot.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.titanslot.core.Core
import dev.titanslot.core.CoreInstaller
import dev.titanslot.core.Platform
import dev.titanslot.data.ArtFit
import dev.titanslot.data.ArtSource
import dev.titanslot.data.Cart
import dev.titanslot.data.CartProps
import dev.titanslot.data.CartPropsStore
import dev.titanslot.data.CartShape
import dev.titanslot.data.Finish
import dev.titanslot.data.Scraper
import dev.titanslot.data.Shell
import dev.titanslot.data.Shells
import dev.titanslot.data.Filter
import dev.titanslot.data.GbPalette
import dev.titanslot.data.Importer
import dev.titanslot.data.Library
import dev.titanslot.data.Paths
import dev.titanslot.data.Scaling
import dev.titanslot.data.Settings
import dev.titanslot.data.Storage
import dev.titanslot.input.Button
import dev.titanslot.input.KeyMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream

sealed interface Mode {
    data object Setup : Mode
    data object Shelf : Mode
    data class Inserting(val cart: Cart, val fresh: Boolean) : Mode
    /** The cart is in and its game runs in GameActivity, in front of the shelf. */
    data class Playing(val cart: Cart) : Mode
    data class Ejecting(val cart: Cart) : Mode
}

enum class Overlay { QUICK_MENU, CART, CONTROLS, ABOUT, SETUP }

/** A line being typed on the keyboard in the cart sheet. */
data class Edit(val row: CartRow, val text: String)

/** What the activity does for the shelf: things that need a window or an intent. */
interface Host {
    fun launchGame(cart: Cart, core: Core, fresh: Boolean, paths: Paths)
    fun moveToBack()
    /** Opens the system photo picker. */
    fun pickImage(onPicked: (Uri) -> Unit)
    /** Opens the system file picker on folders. False when the phone has none. */
    fun pickFolder(onPicked: (Uri) -> Unit): Boolean
    /** Opens the system file picker for any number of files. False when the phone has none. */
    fun pickFiles(onPicked: (List<Uri>) -> Unit): Boolean
    fun openStream(uri: Uri): InputStream?
}

/**
 * The shelf as one state machine: carousel, insert and eject, and the menus on top of it.
 * Keys arrive here already translated into [Button]s. The game itself runs in GameActivity
 * (see [GameController]).
 */
class AppState(
    val host: Host,
    val settings: Settings,
    val keyMap: KeyMap,
    val status: SystemStatus,
    val sfx: Sfx,
    val scope: CoroutineScope,
    cacheDir: File,
    val storage: Storage,
    val importer: Importer,
    val cores: CoreInstaller,
) {
    /** Where the library and saves are; changes when the card comes or goes, or the library moves. */
    var paths by mutableStateOf(storage.paths())
        private set
    private val scraper = Scraper(cacheDir)
    private var props = CartPropsStore(paths.cartProps)
    val setup = Setup(this)
    private var restored = false
    /** Whether every core is installed, checked on resume and after setup (it walks folders). */
    var coresReady by mutableStateOf(Core.entries.all(cores::isInstalled))
        private set

    var mode by mutableStateOf<Mode>(if (storage.setupDone) Mode.Shelf else Mode.Setup)
        private set
    var overlay by mutableStateOf<Overlay?>(null)
        private set
    var toast by mutableStateOf<Toast?>(null)
        private set

    var shelves by mutableStateOf<List<ShelfModel>>(emptyList())
        private set
    var shelfIndex by mutableIntStateOf(0)
        private set
    val shelf: ShelfModel? get() = shelves.getOrNull(shelfIndex)
    var scanned by mutableStateOf(false)
        private set
    /** The letter Up / Down just jumped to, shown in the slot for a moment. */
    var letter by mutableStateOf<Pair<Char, Long>?>(null)
        private set

    var quickRow by mutableStateOf(QuickRow.FAST_FORWARD)
        private set
    var cartRow by mutableStateOf(CartRow.SCRAPE)
        private set
    var scrapeKind by mutableStateOf(Scraper.Kind.LABEL)
        private set
    /** Remove Game asks twice. */
    var confirmRemove by mutableStateOf(false)
        private set
    var edit by mutableStateOf<Edit?>(null)
        private set
    private var bulkJob: Job? = null
    /** Bumped on every cart sheet change, so rows that don't alter the face still redraw. */
    private var sheetTick by mutableIntStateOf(0)
    var controlsRow by mutableIntStateOf(0)
        private set
    var listening by mutableStateOf<Button?>(null)
        private set

    init {
        if (mode == Mode.Setup) setup.start()
    }

    // ---- lifecycle -------------------------------------------------------------------

    fun onResume() {
        refreshPaths()
        coresReady = Core.entries.all(cores::isInstalled)
        when {
            setup.active -> setup.onResume()
            mode == Mode.Shelf && !restored -> {
                restored = true
                rescan(restore = true)
            }
            // Coming back to the app: pick up a card that went in or out.
            mode == Mode.Shelf && overlay == null -> rescan()
        }
    }

    fun onPause() {
        repeatJob?.cancel()
        aHeld = false
    }

    fun refreshPaths() {
        val next = storage.paths()
        if (next.library != paths.library || next.libraryAvailable != paths.libraryAvailable) paths = next
    }

    fun rescan(restore: Boolean = false, then: (() -> Unit)? = null) {
        scope.launch {
            val keep = if (restore) settings.lastCart else shelf?.current?.key
            val keepShelf = if (restore) settings.lastShelf else shelf?.platform?.folder
            val at = paths
            val found = withContext(Dispatchers.IO) {
                at.ensure()
                props = CartPropsStore(at.cartProps)
                Library.scan(at, props)
            }
            val models = found.filterValues { it.isNotEmpty() }.map { (p, carts) -> ShelfModel(p, carts) }
            shelves = models
            shelfIndex = models.indexOfFirst { it.platform.folder == keepShelf }.coerceAtLeast(0)
            shelf?.let { s -> s.select(s.carts.indexOfFirst { it.key == keep }.coerceAtLeast(0)) }
            scanned = true
            then?.invoke()
        }
    }

    // ---- setup ---------------------------------------------------------------------------

    /** Opens setup [steps] over the shelf, e.g. Add Games from Settings. */
    fun openSetup(vararg steps: SetupStep) {
        val from = overlay
        overlay = Overlay.SETUP
        setup.open(steps.toList(), returnTo = from)
    }

    /** A on an empty shelf: add games, or find the card they're on. */
    private fun openEmptyShelf() = openSetup(if (paths.libraryAvailable) SetupStep.GAMES else SetupStep.STORAGE)

    /** The first run's last step: on to the shelf, and fetch art for what was imported. */
    fun finishSetup(scrapeArt: Boolean) {
        storage.setupDone = true
        coresReady = Core.entries.all(cores::isInstalled)
        overlay = null
        mode = Mode.Shelf
        restored = true
        rescan(restore = true) { if (scrapeArt && shelves.isNotEmpty()) scrapeMissing() }
    }

    fun closeSetup(returnTo: Overlay?, scrapeArt: Boolean = false) {
        overlay = returnTo
        coresReady = Core.entries.all(cores::isInstalled)
        rescan { if (scrapeArt && shelves.isNotEmpty()) scrapeMissing() }
    }

    // ---- toasts ------------------------------------------------------------------------

    fun toast(text: String) {
        toast = Toast(text)
    }

    // ---- input ---------------------------------------------------------------------------

    /** Raw key hook for rebinding. Returns true when the key was taken. */
    fun captureKey(keyCode: Int, down: Boolean): Boolean {
        if (swallowKeyUp == keyCode && !down) {
            swallowKeyUp = null
            return true
        }
        if (down && setup.active) setup.capture(keyCode)
        val target = listening ?: return false
        if (keyCode in SYSTEM_KEYS) return false
        if (!down) return true
        keyMap.bind(target, keyCode)
        listening = null
        swallowKeyUp = keyCode
        return true
    }

    private var swallowKeyUp: Int? = null

    fun onKey(button: Button, down: Boolean) {
        when (mode) {
            Mode.Setup -> setup.key(button, down)
            Mode.Shelf -> when {
                overlay == Overlay.SETUP -> setup.key(button, down)
                overlay != null -> shelfOverlayKey(button, down)
                else -> shelfKey(button, down)
            }
            is Mode.Inserting, is Mode.Playing, is Mode.Ejecting -> Unit
        }
    }

    /** The system Back gesture: closes whatever is open, else leaves the app. */
    fun onBack() {
        if (edit != null) {
            edit = null
            return
        }
        when (mode) {
            Mode.Setup -> setup.back()
            Mode.Shelf -> when (overlay) {
                null -> host.moveToBack()
                Overlay.SETUP -> setup.back()
                Overlay.CONTROLS, Overlay.ABOUT -> {
                    listening = null
                    overlay = Overlay.QUICK_MENU
                }
                else -> overlay = null
            }
            else -> Unit
        }
    }

    // ---- shelf -------------------------------------------------------------------------

    private var repeatJob: Job? = null
    private var repeatDir = 0
    private var aJob: Job? = null
    private var aHeld = false

    private fun shelfKey(b: Button, down: Boolean) {
        val s = shelf
        when (b) {
            Button.LEFT, Button.RIGHT -> {
                val dir = if (b == Button.LEFT) -1 else 1
                if (down) {
                    s?.step(dir)
                    repeatDir = dir
                    repeatJob?.cancel()
                    repeatJob = scope.launch {
                        delay(REPEAT_DELAY_MS)
                        var fired = 0
                        while (isActive) {
                            s?.step(dir)
                            delay(REPEAT_MS[fired.coerceAtMost(REPEAT_MS.lastIndex)])
                            fired++
                        }
                    }
                } else if (repeatDir == dir) {
                    repeatJob?.cancel()
                    repeatJob = null
                }
            }
            Button.UP -> if (down) jumpLetter(-1)
            Button.DOWN -> if (down) jumpLetter(1)
            Button.L -> if (down) switchShelf(-1)
            Button.R -> if (down) switchShelf(1)
            Button.A -> {
                if (down) {
                    if (s?.current == null) {
                        if (scanned) openEmptyShelf()
                        return
                    }
                    aHeld = true
                    aJob?.cancel()
                    aJob = scope.launch {
                        delay(HOLD_MS)
                        if (aHeld) {
                            aHeld = false
                            insert(fresh = true)
                        }
                    }
                } else if (aHeld) {
                    aHeld = false
                    aJob?.cancel()
                    insert(fresh = false)
                }
            }
            Button.START -> if (down) openCart()
            Button.MENU -> if (down) {
                quickRow = QuickRow.FAST_FORWARD
                overlay = Overlay.QUICK_MENU
            }
            else -> Unit
        }
    }

    fun stepShelf(dir: Int) = shelf?.step(dir)

    fun clearLetter() {
        letter = null
    }

    private fun jumpLetter(dir: Int) {
        val s = shelf ?: return
        s.jumpLetter(dir)
        s.current?.let { letter = it.initial to SystemClock.uptimeMillis() }
    }

    fun switchShelf(dir: Int) {
        if (shelves.size < 2) return
        repeatJob?.cancel()
        shelfIndex = (shelfIndex + dir).mod(shelves.size)
    }

    fun insert(fresh: Boolean) {
        if (mode != Mode.Shelf) return
        val cart = shelf?.current ?: run {
            if (scanned && overlay == null) openEmptyShelf()
            return
        }
        val core = settings.coreFor(cart)
        if (!cores.isInstalled(core)) {
            toast("${core.title} Isn't Installed Yet")
            openSetup(SetupStep.CORES)
            return
        }
        repeatJob?.cancel()
        settings.lastShelf = cart.platform.folder
        settings.lastCart = cart.key
        overlay = null
        sfx.insert()
        Log.i(TAG, "insert ${cart.key} fresh=$fresh")
        mode = Mode.Inserting(cart, fresh)
    }

    /** The shelf calls this when the cart has gone all the way in. */
    fun onInserted() {
        val m = mode as? Mode.Inserting ?: return
        val core = settings.coreFor(m.cart)
        // After a save import the old resume state would bring the old save back.
        val fresh = settings.takeFreshStart(m.cart) || m.fresh
        Log.i(TAG, "inserted ${m.cart.key} core=${core.id} fresh=$fresh")
        mode = Mode.Playing(m.cart)
        host.launchGame(m.cart, core, fresh, paths)
    }

    /** GameActivity has finished (ejected, abandoned or crashed): the cart comes back out. */
    fun onGameFinished() {
        val m = mode as? Mode.Playing ?: return
        Log.i(TAG, "game finished ${m.cart.key}")
        sfx.eject()
        mode = Mode.Ejecting(m.cart)
    }

    /** The shelf calls this when an ejected cart is back on the shelf. */
    fun onEjected() {
        Log.i(TAG, "ejected")
        if (mode is Mode.Ejecting) mode = Mode.Shelf
    }

    // ---- shelf overlays ------------------------------------------------------------------

    private fun shelfOverlayKey(b: Button, down: Boolean) {
        if (!down) return
        when (overlay) {
            Overlay.QUICK_MENU -> quickMenuKey(b)
            Overlay.CART -> cartKey(b)
            Overlay.CONTROLS -> controlsKey(b)
            Overlay.ABOUT -> if (b == Button.A || b == Button.B || b == Button.MENU) overlay = Overlay.QUICK_MENU
            else -> Unit
        }
    }

    private fun quickMenuKey(b: Button) {
        val rows = QuickRow.entries
        when (b) {
            Button.UP -> quickRow = rows[(quickRow.ordinal - 1).mod(rows.size)]
            Button.DOWN -> quickRow = rows[(quickRow.ordinal + 1).mod(rows.size)]
            Button.LEFT -> changeQuick(quickRow, -1)
            Button.RIGHT -> changeQuick(quickRow, 1)
            Button.A -> activateQuick(quickRow)
            Button.B, Button.MENU -> overlay = null
            else -> Unit
        }
    }

    fun activateQuick(row: QuickRow) {
        quickRow = row
        when (row) {
            QuickRow.CONTROLS -> {
                controlsRow = 0
                overlay = Overlay.CONTROLS
            }
            QuickRow.RESCAN -> {
                rescan()
                toast("Games Rescanned")
            }
            QuickRow.SCRAPE_MISSING -> {
                overlay = null
                scrapeMissing()
            }
            QuickRow.ADD_GAMES -> openSetup(SetupStep.GAMES)
            QuickRow.IMPORT_SAVES -> openSetup(SetupStep.SAVES)
            QuickRow.STORAGE -> openSetup(SetupStep.STORAGE)
            QuickRow.CORES -> openSetup(SetupStep.CORES)
            QuickRow.SETUP -> openSetup(*SetupStep.entries.filter { it != SetupStep.WELCOME }.toTypedArray())
            QuickRow.ABOUT -> overlay = Overlay.ABOUT
            else -> changeQuick(row, 1)
        }
    }

    fun quickValue(row: QuickRow): String? = when (row) {
        QuickRow.FAST_FORWARD -> "${settings.ffSpeed}×"
        QuickRow.FAST_FORWARD_SOUND -> onOff(settings.ffSound)
        QuickRow.REWIND -> onOff(settings.rewind)
        QuickRow.SCALING -> settings.scaling.label
        QuickRow.FILTER -> settings.filter.label
        QuickRow.COLOUR_CORRECTION -> onOff(settings.colourCorrection)
        QuickRow.GB_PALETTE -> settings.gbPalette.label
        QuickRow.STORAGE -> if (storage.libraryVolume == Storage.PHONE) "Phone" else "microSD"
        QuickRow.CORES -> if (coresReady) "Ready" else "Missing"
        else -> null
    }

    private fun changeQuick(row: QuickRow, dir: Int) {
        when (row) {
            QuickRow.FAST_FORWARD -> settings.ffSpeed = Settings.FF_SPEEDS.cycle(settings.ffSpeed, dir)
            QuickRow.FAST_FORWARD_SOUND -> settings.ffSound = !settings.ffSound
            QuickRow.REWIND -> settings.rewind = !settings.rewind
            QuickRow.SCALING -> settings.scaling = Scaling.entries.cycle(settings.scaling, dir)
            QuickRow.FILTER -> settings.filter = Filter.entries.cycle(settings.filter, dir)
            QuickRow.COLOUR_CORRECTION -> settings.colourCorrection = !settings.colourCorrection
            QuickRow.GB_PALETTE -> settings.gbPalette = GbPalette.entries.cycle(settings.gbPalette, dir)
            else -> Unit
        }
    }

    // ---- cart sheet ----------------------------------------------------------------------

    private fun openCart() {
        if (shelf?.current == null) return
        scrapeKind = Scraper.Kind.LABEL
        cartRow = CartRow.SCRAPE
        confirmRemove = false
        edit = null
        overlay = Overlay.CART
    }

    fun cartRows(cart: Cart): List<CartRow> = buildList {
        add(CartRow.NAME)
        add(CartRow.SCRAPE)
        add(CartRow.PICK)
        if (cart.label != null) {
            add(CartRow.FIT)
            add(CartRow.REMOVE_ART)
        }
        add(CartRow.COLOUR)
        add(CartRow.FINISH)
        if (cart.platform == Platform.GB || cart.platform == Platform.GBC) add(CartRow.SHAPE)
        if (cart.platform.cores.size > 1) add(CartRow.CHIP)
        add(CartRow.RESET)
        add(CartRow.REMOVE)
    }

    fun cartValue(cart: Cart, row: CartRow): String? {
        sheetTick
        val p = props[cart.key]
        edit?.takeIf { it.row == row }?.let { return it.text + "▏" }
        return when (row) {
            CartRow.NAME -> cart.title
            CartRow.SCRAPE -> scrapeKind.label
            CartRow.FIT -> cart.fit.label
            CartRow.COLOUR -> when (val rgb = p.colour) {
                null -> "Auto"
                else -> Shells.PRESETS.firstOrNull { it.second.rgb == rgb }?.first ?: CartPropsStore.hex(rgb)
            }
            CartRow.FINISH -> p.finish?.label ?: "Auto"
            CartRow.SHAPE -> when (p.shape) {
                CartShape.GB_NOTCHED -> "Game Boy"
                CartShape.GB_ROUNDED -> "Color"
                else -> "Auto"
            }
            CartRow.CHIP -> settings.coreFor(cart).title
            CartRow.REMOVE -> if (confirmRemove) "${keyMap.hint(Button.A)} Again To Remove" else null
            CartRow.PICK, CartRow.REMOVE_ART, CartRow.RESET -> null
        }
    }

    private fun cartKey(b: Button) {
        val cart = shelf?.current ?: run { overlay = null; return }
        val rows = cartRows(cart)
        val at = rows.indexOf(cartRow).coerceAtLeast(0)
        if (b != Button.A) confirmRemove = false
        when (b) {
            Button.UP -> cartRow = rows[(at - 1).mod(rows.size)]
            Button.DOWN -> cartRow = rows[(at + 1).mod(rows.size)]
            Button.LEFT -> changeCart(cart, cartRow, -1)
            Button.RIGHT -> changeCart(cart, cartRow, 1)
            Button.A -> activateCart(cartRow)
            Button.B, Button.START, Button.MENU -> overlay = null
            else -> Unit
        }
    }

    fun activateCart(row: CartRow) {
        val cart = shelf?.current ?: return
        if (row != CartRow.REMOVE || cartRow != CartRow.REMOVE) confirmRemove = false
        cartRow = row
        when (row) {
            CartRow.NAME -> edit = Edit(row, cart.title)
            CartRow.SCRAPE -> scrape(cart, scrapeKind)
            CartRow.PICK -> host.pickImage { uri -> importImage(cart, uri) }
            CartRow.REMOVE_ART -> removeArt(cart)
            CartRow.COLOUR -> edit = Edit(row, CartPropsStore.hex(cart.shell.rgb))
            CartRow.RESET -> {
                // Back to how the ROM and its folder say it looks; where its art came from stays.
                props.update(cart.key) { CartProps(art = it.art) }
                sheetTick++
                refresh(cart)
                toast("Cart Reset")
            }
            CartRow.REMOVE -> if (confirmRemove) removeGame(cart) else confirmRemove = true
            else -> changeCart(cart, row, 1)
        }
    }

    private fun changeCart(cart: Cart, row: CartRow, dir: Int) {
        when (row) {
            CartRow.SCRAPE -> scrapeKind = Scraper.Kind.entries.cycle(scrapeKind, dir)
            CartRow.FIT -> setProps(cart) { it.copy(fit = ArtFit.entries.cycle(cart.fit, dir)) }
            CartRow.COLOUR -> {
                // Auto, then every preset; a preset brings its finish with it.
                val options = listOf<Pair<String, Shell>?>(null) + Shells.PRESETS
                val now = props[cart.key].colour
                val at = options.indexOfFirst { it?.second?.rgb == now }.coerceAtLeast(0)
                val next = options[(at + dir).mod(options.size)]
                setProps(cart) { it.copy(colour = next?.second?.rgb, finish = next?.second?.finish) }
            }
            CartRow.FINISH -> {
                val options = listOf<Finish?>(null) + Finish.entries
                setProps(cart) { it.copy(finish = options.cycle(it.finish, dir)) }
            }
            CartRow.SHAPE -> {
                val options = listOf(null, CartShape.GB_NOTCHED, CartShape.GB_ROUNDED)
                setProps(cart) { it.copy(shape = options.cycle(it.shape, dir)) }
            }
            CartRow.CHIP -> {
                val cores = cart.platform.cores
                settings.setCore(cart, cores.cycle(settings.coreFor(cart), dir))
                sheetTick++
            }
            else -> Unit
        }
    }

    private fun setProps(cart: Cart, change: (CartProps) -> CartProps) {
        props.update(cart.key, change)
        sheetTick++
        refresh(cart)
    }

    /** Takes the ROM and its label off the phone. Saves, states and the cart's settings stay. */
    private fun removeGame(cart: Cart) {
        confirmRemove = false
        scope.launch {
            val gone = withContext(Dispatchers.IO) { cart.rom.delete().also { if (it) cart.label?.delete() } }
            if (gone) {
                overlay = null
                toast("Removed · Saves Kept")
                rescan()
            } else {
                toast("Could Not Remove It")
            }
        }
    }

    /** Rebuilds [cart] from disk (header, label, properties) and swaps it onto its shelf. */
    private fun refresh(cart: Cart, focus: Boolean = true) {
        scope.launch {
            val next = withContext(Dispatchers.IO) { Library.cartFor(cart.platform, cart.rom, paths, props) }
            shelves.firstOrNull { it.platform == cart.platform }?.replace(next, focus)
        }
    }

    /** Typing on the Titan's keyboard while a cart sheet row is being edited. */
    fun editKey(event: KeyEvent): Boolean {
        val e = edit ?: return false
        when (event.keyCode) {
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_HOME -> return false
        }
        if (event.action != KeyEvent.ACTION_DOWN) return true
        when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> commitEdit(e)
            KeyEvent.KEYCODE_DEL -> edit = e.copy(text = e.text.dropLast(1))
            KeyEvent.KEYCODE_ESCAPE -> edit = null
            else -> {
                val code = event.getUnicodeChar(event.metaState)
                if (code == 0 || Character.isISOControl(code)) return true
                val ch = code.toChar()
                val text = when (e.row) {
                    CartRow.COLOUR -> if ((ch.isDigit() || ch.lowercaseChar() in 'a'..'f' || ch == '#') && e.text.length < 7) e.text + ch else e.text
                    else -> if (e.text.length < MAX_NAME) e.text + ch else e.text
                }
                edit = e.copy(text = text)
            }
        }
        return true
    }

    private fun commitEdit(e: Edit) {
        val cart = shelf?.current ?: run { edit = null; return }
        edit = null
        when (e.row) {
            CartRow.NAME -> {
                val name = e.text.trim().takeIf { it.isNotEmpty() && it != Cart.titleOf(cart.stem) }
                setProps(cart) { it.copy(name = name) }
            }
            CartRow.COLOUR -> {
                val rgb = CartPropsStore.parseHex(e.text)
                if (rgb == null) toast("Type a colour like #2F5CC0") else setProps(cart) { it.copy(colour = rgb) }
            }
            else -> Unit
        }
    }

    // ---- art ---------------------------------------------------------------------------------

    private fun scrape(cart: Cart, kind: Scraper.Kind) {
        if (bulkJob?.isActive == true) return
        bulkJob = scope.launch {
            toast("Looking for ${kind.label}")
            when (val r = scrapeInto(cart, kind)) {
                is Scraper.Result.Found -> toast("Art Found")
                Scraper.Result.NotFound -> toast("No ${kind.label} Found")
                is Scraper.Result.Failed -> toast(r.reason)
            }
        }
    }

    /**
     * A real cart label scan for every cart that needs one. Fills carts with no art, and swaps
     * box art this app scraped for a real label once one exists; art you put in Labels/
     * yourself, or picked from the phone, is left alone.
     */
    fun scrapeMissing() {
        if (bulkJob?.isActive == true) return
        val scraped = setOf(ArtSource.BOX, ArtSource.TITLE, ArtSource.SNAP)
        val todo = shelves.flatMap { it.carts }.filter { cart ->
            cart.label == null || props[cart.key].art in scraped
        }
        if (todo.isEmpty()) {
            toast("Every Cart Has Art")
            return
        }
        bulkJob = scope.launch {
            var found = 0
            for ((i, cart) in todo.withIndex()) {
                toast("Scraping ${i + 1} of ${todo.size}")
                when (val r = scrapeInto(cart, Scraper.Kind.LABEL, focus = false)) {
                    is Scraper.Result.Found -> found++
                    is Scraper.Result.Failed -> {
                        toast(r.reason)
                        return@launch
                    }
                    Scraper.Result.NotFound -> Unit
                }
            }
            toast("Found Art For $found of ${todo.size}")
        }
    }

    private suspend fun scrapeInto(cart: Cart, kind: Scraper.Kind, focus: Boolean = true): Scraper.Result {
        val dest = File(paths.labels(cart.platform), "${cart.stem}.png")
        val r = withContext(Dispatchers.IO) {
            scraper.scrape(cart, kind, dest).also { result ->
                if (result is Scraper.Result.Found) cart.label?.takeIf { it != dest }?.delete()
            }
        }
        if (r is Scraper.Result.Found) {
            props.update(cart.key) { it.copy(art = ArtSource.valueOf(kind.name)) }
            sheetTick++
            refresh(cart, focus)
        }
        return r
    }

    private fun importImage(cart: Cart, uri: Uri) {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    host.openStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_ART_PX) sample *= 2
                    val bmp = host.openStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                    } ?: return@runCatching false
                    val dest = File(paths.labels(cart.platform), "${cart.stem}.png")
                    dest.parentFile?.mkdirs()
                    dest.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    bmp.recycle()
                    cart.label?.takeIf { it != dest }?.delete()
                    true
                }.getOrDefault(false)
            }
            toast(if (ok) "Art Set" else "Could Not Read That Image")
            if (ok) {
                props.update(cart.key) { it.copy(art = ArtSource.PHONE) }
                refresh(cart)
            }
        }
    }

    private fun removeArt(cart: Cart) {
        cart.label?.delete()
        props.update(cart.key) { it.copy(art = null) }
        cartRow = CartRow.SCRAPE
        refresh(cart)
        toast("Art Removed")
    }

    /** Controls rows: every [Button], then "Reset to Titan defaults". */
    val controlsRows: Int get() = Button.entries.size + 1

    private fun controlsKey(b: Button) {
        if (listening != null) return
        when (b) {
            Button.UP -> controlsRow = (controlsRow - 1).mod(controlsRows)
            Button.DOWN -> controlsRow = (controlsRow + 1).mod(controlsRows)
            Button.A -> activateControl(controlsRow)
            Button.B, Button.MENU -> overlay = Overlay.QUICK_MENU
            else -> Unit
        }
    }

    fun activateControl(row: Int) {
        controlsRow = row
        if (row < Button.entries.size) {
            // The next key pressed becomes the binding; see captureKey.
            listening = Button.entries[row]
        } else {
            keyMap.resetToDefaults()
            toast("Titan Defaults Restored")
        }
    }

    private companion object {
        const val TAG = "TitanSlot"
        const val HOLD_MS = 500L
        const val MAX_NAME = 60
        const val MAX_ART_PX = 1200
        const val REPEAT_DELAY_MS = 400L
        val REPEAT_MS = longArrayOf(110, 85, 65, 50)
        val SYSTEM_KEYS = setOf(
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_HOME, KeyEvent.KEYCODE_POWER,
            KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_APP_SWITCH,
        )

        fun onOff(b: Boolean) = if (b) "On" else "Off"

        fun <T> List<T>.cycle(current: T, dir: Int): T = this[(indexOf(current) + dir).mod(size)]
    }
}
