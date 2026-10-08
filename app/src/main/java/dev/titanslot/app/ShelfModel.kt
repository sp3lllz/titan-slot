package dev.titanslot.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.titanslot.core.Platform
import dev.titanslot.data.Cart
import kotlin.math.abs

/**
 * One platform's carousel, after slot's Shelf (crates/slot-ui/src/shelf.rs): an index that
 * wraps, an unwrapped target [ride], and a critically damped spring pulling [scroll] onto it.
 */
class ShelfModel(val platform: Platform, carts: List<Cart>) {
    var carts by mutableStateOf(carts)
        private set

    var index by mutableIntStateOf(0)
        private set
    var ride by mutableFloatStateOf(0f)
        private set
    var scroll by mutableFloatStateOf(0f)
        private set
    private var vel = 0f

    val size: Int get() = carts.size
    val current: Cart? get() = carts.getOrNull(index)
    val settled: Boolean get() = scroll == ride && vel == 0f

    fun select(i: Int) {
        if (size == 0) return
        index = i.mod(size)
        ride = index.toFloat()
        scroll = ride
        vel = 0f
    }

    /**
     * Swaps in an edited cart. With [focus] the shelf centres on it (its new name may sort it
     * elsewhere); without, whatever was centred stays centred.
     */
    fun replace(cart: Cart, focus: Boolean = true) {
        val keep = if (focus) cart.key else current?.key
        val next = carts.map { if (it.key == cart.key) cart else it }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { c -> c.title.trimStart { !it.isLetterOrDigit() } })
        carts = next
        select(next.indexOfFirst { it.key == keep }.coerceAtLeast(0))
    }

    fun step(by: Int) {
        if (size < 2) return
        index = (index + by).mod(size)
        ride += by
    }

    fun jumpLetter(dir: Int) {
        val n = size
        if (n < 2) return
        val here = carts[index].initial
        if (carts.all { it.initial == here }) return
        val target = if (dir > 0) {
            generateSequence((index + 1).mod(n)) { (it + 1).mod(n) }.take(n).first { carts[it].initial != here }
        } else {
            val start = startOfLetter(index)
            if (start == index) startOfLetter((start - 1).mod(n)) else start
        }
        val ahead = (target - index).mod(n)
        ride += if (dir > 0) ahead else ahead - n
        index = target
    }

    private fun startOfLetter(from: Int): Int {
        val n = size
        val letter = carts[from].initial
        var at = from
        repeat(n) {
            val before = (at - 1).mod(n)
            if (carts[before].initial != letter) return at
            at = before
        }
        return at
    }

    /** Which cart sits [offset] places from the centre, or null when the shelf is too short. */
    fun cartAt(offset: Int): Int? = when {
        size == 0 -> null
        size == 1 -> if (offset == 0) index else null
        else -> (index + offset).mod(size)
    }

    fun update(dt: Float) {
        val accel = -2f * OMEGA * vel - OMEGA * OMEGA * (scroll - ride)
        vel += accel * dt
        scroll += vel * dt
        if (abs(scroll - ride) < 0.001f && abs(vel) < 0.01f) {
            scroll = ride
            vel = 0f
        }
    }

    private companion object {
        const val OMEGA = 16f
    }
}
