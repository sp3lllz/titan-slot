package dev.titanslot.game

/**
 * Recent save states, newest last, capped by total size. Only touched from the emulation
 * thread (inside GLRetroView.queueEvent), so it needs no locking.
 */
class RewindBuffer(private val maxBytes: Long) {
    private val states = ArrayDeque<ByteArray>()
    private var bytes = 0L

    fun push(state: ByteArray) {
        if (state.isEmpty()) return
        states.addLast(state)
        bytes += state.size
        while (bytes > maxBytes && states.size > 1) {
            bytes -= states.removeFirst().size
        }
    }

    /** Steps one snapshot back. The oldest one is kept, so holding rewind parks there. */
    fun pop(): ByteArray? {
        if (states.isEmpty()) return null
        if (states.size == 1) return states.first()
        val s = states.removeLast()
        bytes -= s.size
        return s
    }

    fun clear() {
        states.clear()
        bytes = 0
    }
}
