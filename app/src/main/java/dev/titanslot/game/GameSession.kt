package dev.titanslot.game

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.PixelCopy
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale
import androidx.lifecycle.Lifecycle
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import dev.titanslot.core.Core
import dev.titanslot.core.CoreOptions
import dev.titanslot.data.Cart
import dev.titanslot.data.Paths
import dev.titanslot.data.SaveState
import dev.titanslot.data.Settings
import dev.titanslot.data.StateStore
import dev.titanslot.input.Button
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

/**
 * One cart in the slot: a LibretroDroid view running [core] on [cart], plus everything the
 * frontend layers on top (fast-forward, rewind, save states, battery saves).
 */
class GameSession(
    val cart: Cart,
    val core: Core,
    private val fresh: Boolean,
    parent: Lifecycle,
    private val settings: Settings,
    private val paths: Paths,
    private val store: StateStore,
) {
    val lifecycle = ChildLifecycle(parent)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Set once the ROM is unpacked and SRAM read; the view can be built after that. */
    var prepared by mutableStateOf(false)
        private set

    /** Set once the first frame is up and the resume state (if any) is loaded. */
    var ready by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    var fastForward by mutableStateOf(false)
        private set
    var fastForwardLocked by mutableStateOf(false)
        private set
    var rewinding by mutableStateOf(false)
        private set
    var paused by mutableStateOf(false)
        private set

    private var romPath: File? = null
    private var sram: ByteArray? = null
    private var view: GLRetroView? = null
    private val rewind = RewindBuffer(maxBytes = 96L * 1024 * 1024)
    private val held = mutableSetOf<Button>()

    init {
        lifecycle.beforePause = { persistBlocking() }
    }

    suspend fun prepare(context: Context) {
        Log.i(TAG, "prepare ${cart.rom.name}")
        try {
            withContext(Dispatchers.IO) {
                romPath = RomFile.resolve(cart, context.cacheDir)
                sram = store.readSram(cart)
                paths.saves(cart.platform).mkdirs()
            }
            prepared = true
            Log.i(TAG, "prepared")
        } catch (e: Exception) {
            Log.e(TAG, "Could not prepare ${cart.rom}", e)
            error = e.message ?: "Could not read ${cart.rom.name}"
        }
    }

    fun createView(context: Context): GLRetroView {
        view?.let { return it }
        Log.i(TAG, "createView core=${core.library}")
        val data = GLRetroViewData(context).apply {
            coreFilePath = File(context.applicationInfo.nativeLibraryDir, core.library).path
            gameFilePath = romPath!!.path
            systemDirectory = paths.bios.apply { mkdirs() }.path
            savesDirectory = paths.saves(cart.platform).path
            variables = CoreOptions.variables(core, cart.platform, settings)
            saveRAMState = sram
            shader = settings.filter.shader
            rumbleEventsEnabled = false
            preferLowLatencyAudio = true
            skipDuplicateFrames = false
        }
        val v = GLRetroView(context, data)
        // Keys come from the activity, translated through the key map, not from the view.
        v.isFocusable = false
        v.isFocusableInTouchMode = false
        view = v
        lifecycle.lifecycle.addObserver(v)

        scope.launch {
            v.getGLRetroErrors().collect { code ->
                Log.e(TAG, "retro error $code")
                error = when (code) {
                    GLRetroView.ERROR_LOAD_LIBRARY -> "${core.title} failed to load"
                    GLRetroView.ERROR_LOAD_GAME -> "${core.title} could not start ${cart.rom.name}"
                    GLRetroView.ERROR_GL_NOT_COMPATIBLE -> "This GPU is not supported by ${core.title}"
                    else -> "${core.title} stopped (error $code)"
                }
            }
        }
        scope.launch {
            v.getGLRetroEvents().filterIsInstance<GLRetroView.GLRetroEvents.FrameRendered>().first()
            Log.i(TAG, "first frame")
            if (!fresh) store.latest(cart, core)?.let { Log.i(TAG, "loading ${it.file.name}: ${load(it)}") }
            ready = true
            Log.i(TAG, "ready")
            record(v)
        }
        return v
    }

    /** Snapshots for rewind every 4th frame, and plays them back while it is held. */
    private suspend fun record(v: GLRetroView) {
        if (!cart.platform.rewind) return
        var frame = 0
        v.getGLRetroEvents().filterIsInstance<GLRetroView.GLRetroEvents.FrameRendered>().collect {
            frame++
            if (paused) return@collect
            if (rewinding) {
                if (frame % 2 == 0) v.queueEvent { rewind.pop()?.let { v.unserializeState(it, false) } }
            } else if (settings.rewind && frame % 4 == 0) {
                v.queueEvent { rewind.push(v.serializeState(false)) }
            }
        }
    }

    fun press(button: Button, down: Boolean) {
        val key = button.gamepadKey ?: return
        val v = view ?: return
        if (down) held += button else if (!held.remove(button)) return
        v.sendKeyEvent(if (down) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, key, 0)
    }

    fun releaseAll() {
        held.toList().forEach { press(it, false) }
    }

    fun setFastForward(on: Boolean, locked: Boolean = false) {
        fastForward = on
        fastForwardLocked = on && locked
        applySpeed()
    }

    fun holdRewind(on: Boolean) {
        if (!cart.platform.rewind || !settings.rewind) return
        rewinding = on
        applySpeed()
    }

    fun pause(on: Boolean) {
        paused = on
        if (on) releaseAll()
        applySpeed()
    }

    private fun applySpeed() {
        val v = view ?: return
        when {
            paused -> {
                v.frameSpeed = 0
                v.audioEnabled = false
            }
            rewinding -> {
                v.frameSpeed = 1
                v.audioEnabled = false
            }
            fastForward -> {
                v.frameSpeed = settings.ffSpeed
                v.audioEnabled = settings.ffSound
            }
            else -> {
                v.frameSpeed = 1
                v.audioEnabled = true
            }
        }
    }

    fun reset() {
        val v = view ?: return
        v.queueEvent { rewind.clear() }
        scope.launch(Dispatchers.IO) { v.reset() }
    }

    suspend fun saveState(auto: Boolean = false): SaveState? {
        val v = view ?: return null
        if (!ready || !lifecycle.isResumed) return null
        val bytes = withContext(Dispatchers.IO) { v.serializeState() }
        if (bytes.isEmpty()) return null
        val thumb = thumbnail(v)
        return withContext(Dispatchers.IO) { store.write(cart, core, bytes, thumb, auto) }
    }

    suspend fun load(state: SaveState): Boolean {
        val v = view ?: return false
        if (!lifecycle.isResumed) return false
        val bytes = withContext(Dispatchers.IO) { runCatching { state.file.readBytes() }.getOrNull() } ?: return false
        val ok = withContext(Dispatchers.IO) { v.unserializeState(bytes) }
        v.queueEvent { rewind.clear() }
        return ok
    }

    fun states(): List<SaveState> = store.list(cart, core)

    fun deleteState(state: SaveState) = store.delete(state)

    private suspend fun saveSram() {
        val v = view ?: return
        if (!ready || !lifecycle.isResumed) return
        val bytes = withContext(Dispatchers.IO) { v.serializeSRAM() }
        withContext(Dispatchers.IO) { store.writeSram(cart, bytes) }
    }

    /** The cart is coming out: write the battery save and a resume state, then stop. */
    suspend fun eject() {
        pause(true)
        runCatching {
            saveSram()
            saveState(auto = true)
        }.onFailure { Log.e(TAG, "Saving on eject failed", it) }
        close()
    }

    fun close() {
        lifecycle.destroy()
        scope.cancel()
        view = null
    }

    /**
     * The app is being backgrounded. This runs on the main thread before the emulator pauses,
     * and blocks for the round trip to the emulation thread (a few milliseconds).
     */
    private fun persistBlocking() {
        val v = view
        Log.i(TAG, "persist on pause: view=${v != null} ready=$ready")
        if (v == null || !ready) return
        runCatching {
            val sram = v.serializeSRAM()
            store.writeSram(cart, sram)
            val state = v.serializeState()
            if (state.isNotEmpty()) store.write(cart, core, state, null, auto = true)
            Log.i(TAG, "persisted sram=${sram.size} state=${state.size}")
        }.onFailure { Log.e(TAG, "Saving on pause failed", it) }
    }

    private suspend fun thumbnail(v: GLRetroView): Bitmap? {
        if (v.width <= 0 || v.height <= 0) return null
        val full = createBitmap(v.width, v.height)
        val ok = suspendCancellableCoroutine { cont ->
            PixelCopy.request(v, full, { result -> cont.resume(result == PixelCopy.SUCCESS) }, Handler(Looper.getMainLooper()))
        }
        if (!ok) {
            full.recycle()
            return null
        }
        val w = THUMB_W.coerceAtMost(full.width)
        val small = full.scale(w, (full.height * w.toFloat() / full.width).toInt().coerceAtLeast(1))
        if (small !== full) full.recycle()
        return small
    }

    private companion object {
        const val TAG = "GameSession"
        const val THUMB_W = 480
    }
}
