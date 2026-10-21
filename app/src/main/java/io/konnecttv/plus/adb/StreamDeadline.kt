package io.konnecttv.plus.adb

import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private val streamTimer = Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "Plus-ADB-watchdog").apply { isDaemon = true } }

/** Close the underlying stream to unblock actual IO; coroutine cancellation alone cannot do that. */
internal fun <T> withStreamDeadline(milliseconds: Long, close: () -> Unit, action: () -> T): T {
    val expired = AtomicBoolean(false)
    val timer = streamTimer.schedule({ expired.set(true); runCatching(close) }, milliseconds, TimeUnit.MILLISECONDS)
    try {
        return action().also { if (expired.get()) throw IOException("TV command timed out after ${milliseconds / 1000.0} seconds.") }
    } catch (e: Exception) {
        if (expired.get()) throw IOException("TV command timed out after ${milliseconds / 1000.0} seconds.", e)
        throw e
    } finally { timer.cancel(false) }
}
