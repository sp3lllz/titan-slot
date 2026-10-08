package dev.titanslot.game

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry

/**
 * A lifecycle for one cart in the slot. It follows the activity (so the emulator pauses
 * when the app does) but can be destroyed on its own when the cart is ejected, which is
 * what tears LibretroDroid down. The activity itself stays on the shelf.
 */
class ChildLifecycle(private val parent: Lifecycle) : LifecycleOwner {
    private val registry = LifecycleRegistry(this)
    private var closed = false

    /** Runs on the activity's ON_PAUSE, while the emulation thread is still running. */
    var beforePause: (() -> Unit)? = null

    override val lifecycle: Lifecycle get() = registry

    private val observer = LifecycleEventObserver { _, event ->
        if (closed) return@LifecycleEventObserver
        if (event == Lifecycle.Event.ON_PAUSE) {
            android.util.Log.i("GameSession", "host pause, saving=${beforePause != null}")
            beforePause?.invoke()
        }
        registry.currentState = parent.currentState
    }

    init {
        registry.currentState = Lifecycle.State.CREATED
        parent.addObserver(observer)
    }

    val isResumed: Boolean get() = !closed && registry.currentState.isAtLeast(Lifecycle.State.RESUMED)

    fun destroy() {
        if (closed) return
        closed = true
        parent.removeObserver(observer)
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
