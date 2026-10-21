package io.konnecttv.plus.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import io.konnecttv.plus.diagnostics.DiagnosticLog
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address
import java.util.concurrent.Executor

private val REMOTE_SERVICES = listOf("_androidtvremote2._tcp.", "_androidtvremote._tcp.")
private const val PAIR = "_adb-tls-pairing._tcp."
private const val CONNECT = "_adb-tls-connect._tcp."
private fun kind(type: String) = when (type) { PAIR -> DiscoveryKind.PAIR; CONNECT -> DiscoveryKind.CONNECT; else -> DiscoveryKind.REMOTE }
private data class ResolveRequest(val type: String, val info: NsdServiceInfo, val key: String, val generation: Long)

/** Local service discovery; saved TVs remain separate from this live inventory. */
fun discoverTvs(context: Context): Flow<List<FoundTv>> = callbackFlow {
    DiagnosticLog.record("INFO", "Discovery", "Starting local TV discovery (mDNS)")
    val nsd = context.getSystemService(NsdManager::class.java)
    val lock = context.getSystemService(WifiManager::class.java).createMulticastLock("konnecttv").apply { acquire() }
    val inventory = DiscoveryInventory()
    val executor = Executor(Runnable::run)
    val listeners = mutableListOf<NsdManager.DiscoveryListener>()
    val callbacks = mutableMapOf<String, NsdManager.ServiceInfoCallback>()
    val queue = ArrayDeque<ResolveRequest>()
    var resolving = false
    fun emitInventory() { trySend(inventory.list()) }
    fun merge(request: ResolveRequest, info: NsdServiceInfo) {
        if (isClosedForSend) return
        val address = if (Build.VERSION.SDK_INT >= 34) info.hostAddresses.firstOrNull { it is Inet4Address }
        else { @Suppress("DEPRECATION") info.host?.takeIf { it is Inet4Address } }
        val host = address?.hostAddress ?: return
        inventory.update(request.key, request.generation, host, info.serviceName, kind(request.type), info.port)
        if (DiagnosticLog.verbose.value) DiagnosticLog.record("DEBUG", "Discovery", "Resolved ${request.type} at $host:${info.port}")
        emitInventory()
    }
    fun lost(key: String, generation: Long? = null) { inventory.lost(key, generation); emitInventory() }

    // Android 9–13 permit only one service resolution at a time.
    fun resolveNext() {
        if (isClosedForSend) return
        val request = synchronized(queue) {
            if (resolving) return
            queue.removeFirstOrNull()?.also { resolving = true } ?: return
        }
        fun finish() { synchronized(queue) { resolving = false }; resolveNext() }
        try {
            @Suppress("DEPRECATION")
            nsd.resolveService(request.info, object : NsdManager.ResolveListener {
                override fun onServiceResolved(info: NsdServiceInfo) { try { merge(request, info) } finally { finish() } }
                override fun onResolveFailed(info: NsdServiceInfo, code: Int) {
                    DiagnosticLog.record("WARN", "Discovery", "mDNS resolve failed for ${request.type} (code $code)")
                    lost(request.key, request.generation); finish()
                }
            })
        } catch (e: Exception) {
            DiagnosticLog.record("WARN", "Discovery", "Couldn't resolve ${request.type}", e)
            lost(request.key, request.generation); finish()
        }
    }
    fun cleanup() {
        inventory.clear()
        synchronized(queue) { queue.clear() }
        listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        if (Build.VERSION.SDK_INT >= 34) synchronized(callbacks) {
            callbacks.values.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } }; callbacks.clear()
        }
        runCatching { if (lock.isHeld) lock.release() }
        DiagnosticLog.record("INFO", "Discovery", "Local TV discovery stopped")
    }
    try {
        (REMOTE_SERVICES + listOf(PAIR, CONNECT)).forEach { type ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onServiceFound(info: NsdServiceInfo) {
                    if (isClosedForSend) return
                    val key = "$type|${info.serviceName}"
                    val request = ResolveRequest(type, info, key, inventory.begin(key))
                    if (Build.VERSION.SDK_INT < 34) {
                        synchronized(queue) { queue.removeAll { it.key == key }; queue += request }
                        resolveNext(); return
                    }
                    val callback = object : NsdManager.ServiceInfoCallback {
                        override fun onServiceUpdated(resolved: NsdServiceInfo) = merge(request, resolved)
                        override fun onServiceInfoCallbackRegistrationFailed(code: Int) {
                            DiagnosticLog.record("WARN", "Discovery", "mDNS callback failed for $type (code $code)")
                            lost(key, request.generation)
                        }
                        override fun onServiceLost() { lost(key, request.generation) }
                        override fun onServiceInfoCallbackUnregistered() {}
                    }
                    synchronized(callbacks) {
                        callbacks.remove(key)?.let { runCatching { nsd.unregisterServiceInfoCallback(it) } }
                        callbacks[key] = callback
                    }
                    try { nsd.registerServiceInfoCallback(info, executor, callback) }
                    catch (e: Exception) { DiagnosticLog.record("WARN", "Discovery", "mDNS callback registration failed", e); lost(key, request.generation) }
                }
                override fun onServiceLost(info: NsdServiceInfo) {
                    val key = "$type|${info.serviceName}"
                    lost(key)
                    synchronized(queue) { queue.removeAll { it.key == key } }
                    if (Build.VERSION.SDK_INT >= 34) synchronized(callbacks) {
                        callbacks.remove(key)?.let { runCatching { nsd.unregisterServiceInfoCallback(it) } }
                    }
                }
                override fun onDiscoveryStarted(type: String) {}
                override fun onDiscoveryStopped(type: String) {}
                override fun onStartDiscoveryFailed(type: String, code: Int) { DiagnosticLog.record("WARN", "Discovery", "mDNS start failed for $type (code $code)") }
                override fun onStopDiscoveryFailed(type: String, code: Int) { DiagnosticLog.record("WARN", "Discovery", "mDNS stop failed for $type (code $code)") }
            }
            listeners += listener
            nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
        }
        emitInventory()
        awaitClose { cleanup() }
    } catch (e: Throwable) { cleanup(); throw e }
}
