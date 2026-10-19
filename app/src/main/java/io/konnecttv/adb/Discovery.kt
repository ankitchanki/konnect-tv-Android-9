package io.konnecttv.adb

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address
import java.util.concurrent.Executor

/** A TV seen on the Wi-Fi. Ports are null until that service shows up. */
data class FoundTv(
    val host: String,
    val name: String,
    /** Wireless debugging is on and showing "Pair with code" right now. */
    val pairPort: Int? = null,
    /** Wireless debugging (TLS) connect port, already paired or not. */
    val tlsPort: Int? = null,
)

private const val REMOTE = "_androidtvremote2._tcp." // every Android/Google TV, debugging or not
private const val PAIR = "_adb-tls-pairing._tcp."
private const val CONNECT = "_adb-tls-connect._tcp."

/**
 * Finds Android TVs over mDNS. Emits the full, merged list each time something new resolves.
 * Classic "network debugging" on port 5555 doesn't announce itself, so those TVs show up
 * through the remote service and get tried on 5555.
 */
fun discoverTvs(context: Context): Flow<List<FoundTv>> = callbackFlow {
    val nsd = context.getSystemService(NsdManager::class.java)
    // NSD drops multicast packets unless the app holds this lock.
    val lock = context.getSystemService(WifiManager::class.java).createMulticastLock("konnecttv").apply { acquire() }
    val tvs = LinkedHashMap<String, FoundTv>()
    // Run callbacks inline on NSD's own thread. A private executor crashes the app: NSD posts
    // onServiceInfoCallbackUnregistered after we close, and a shut-down executor rejects it.
    val executor = Executor(Runnable::run)

    fun merge(type: String, info: NsdServiceInfo): Unit = synchronized(tvs) {
        val address = if (Build.VERSION.SDK_INT >= 34) {
            info.hostAddresses.firstOrNull { it is Inet4Address }
        } else {
            @Suppress("DEPRECATION") info.host?.takeIf { it is Inet4Address }
        }
        val host = address?.hostAddress ?: return@synchronized
        // Only the TV remote service carries a friendly name; adb services are named "adb-<serial>-xyz".
        val old = tvs[host] ?: FoundTv(host, if (type == REMOTE) info.serviceName else "Android device")
        tvs[host] = when (type) {
            REMOTE -> old.copy(name = info.serviceName)
            PAIR -> old.copy(pairPort = info.port)
            else -> old.copy(tlsPort = info.port)
        }
        trySend(tvs.values.toList())
        Unit
    }

    // Android 12-13 can only resolve one service at a time, so those go through a queue.
    val queue = ArrayDeque<Pair<String, NsdServiceInfo>>()
    var resolving = false
    fun resolveNext() {
        val (type, info) = synchronized(queue) {
            if (resolving) return
            queue.removeFirstOrNull()?.also { resolving = true } ?: return
        }
        @Suppress("DEPRECATION")
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onServiceResolved(resolved: NsdServiceInfo) = done { merge(type, resolved) }
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) = done {}
            fun done(block: () -> Unit) {
                block()
                synchronized(queue) { resolving = false }
                resolveNext()
            }
        })
    }

    val callbacks = mutableListOf<NsdManager.ServiceInfoCallback>()
    val listeners = listOf(REMOTE, PAIR, CONNECT).map { type ->
        object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                if (isClosedForSend) return
                if (Build.VERSION.SDK_INT < 34) {
                    synchronized(queue) { queue += type to info }
                    resolveNext()
                    return
                }
                val cb = object : NsdManager.ServiceInfoCallback {
                    override fun onServiceUpdated(resolved: NsdServiceInfo) = merge(type, resolved)
                    override fun onServiceInfoCallbackRegistrationFailed(code: Int) {}
                    override fun onServiceLost() {}
                    override fun onServiceInfoCallbackUnregistered() {}
                }
                synchronized(callbacks) { callbacks += cb }
                nsd.registerServiceInfoCallback(info, executor, cb)
            }
            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onDiscoveryStarted(type: String) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, code: Int) {}
            override fun onStopDiscoveryFailed(type: String, code: Int) {}
        }.also { nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, it) }
    }

    trySend(emptyList())
    awaitClose {
        listeners.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
        if (Build.VERSION.SDK_INT >= 34) {
            synchronized(callbacks) { callbacks.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } } }
        }
        lock.release()
    }
}
