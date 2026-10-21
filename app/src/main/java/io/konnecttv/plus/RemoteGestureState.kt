package io.konnecttv.plus

import io.konnecttv.plus.adb.Key
import kotlin.math.abs

/** Accumulates only whole 24dp steps; reversal retains a signed remainder. */
class VolumeKeyStrip(private val stepDp: Float = 24f) {
    init { require(stepDp.isFinite() && stepDp>0f) }
    private var remainder = 0f
    fun travel(deltaDp: Float): List<Int> {
        if (!deltaDp.isFinite()) return emptyList()
        remainder += deltaDp.coerceIn(-240f,240f)
        val keys = mutableListOf<Int>()
        while(abs(remainder) >= stepDp) {
            val down = remainder > 0
            keys += if(down) Key.VOL_DOWN else Key.VOL_UP
            remainder += if(down) -stepDp else stepDp
        }
        return keys
    }
}

data class PointerDelta(val tvId: String, val dx: Int, val dy: Int)
/** tryTake never drains until the caller has acquired the operation lock. */
class PendingPointer {
    private var pending: PointerDelta? = null
    @Synchronized fun add(id: String, dx: Int, dy: Int) {
        val old = pending?.takeIf { it.tvId == id }
        pending = PointerDelta(id,((old?.dx ?: 0)+dx).coerceIn(-250,250),((old?.dy ?: 0)+dy).coerceIn(-250,250))
    }
    @Synchronized fun tryTake(lockAcquired: Boolean): PointerDelta? = if(lockAcquired) pending.also { pending=null } else null
    @Synchronized fun clear() { pending=null }
}
