package io.konnecttv.plus.adb

/** A TV seen on Wi-Fi. Pairing and connection services have independent lifetimes. */
data class FoundTv(val host: String, val name: String, val pairPort: Int? = null, val tlsPort: Int? = null)
internal enum class DiscoveryKind { REMOTE, PAIR, CONNECT }

/** Track service identity/generation, so late resolution cannot resurrect a lost service or old IP. */
internal class DiscoveryInventory {
    private data class Service(val generation: Long, val host: String, val name: String, val kind: DiscoveryKind, val port: Int)
    private var counter = 0L
    private val pending = mutableMapOf<String, Long>()
    private val resolved = linkedMapOf<String, Service>()

    @Synchronized fun begin(key: String): Long = (++counter).also { pending[key] = it; resolved.remove(key) }
    @Synchronized fun update(key: String, generation: Long, host: String, name: String, kind: DiscoveryKind, port: Int) {
        if (pending[key] == generation) resolved[key] = Service(generation, host, name, kind, port)
    }
    @Synchronized fun lost(key: String, generation: Long? = null) {
        if (generation == null || pending[key] == generation) { pending.remove(key); resolved.remove(key) }
    }
    @Synchronized fun clear() { pending.clear(); resolved.clear() }
    @Synchronized fun list(): List<FoundTv> = resolved.values.groupBy { it.host }.map { (host, services) ->
        FoundTv(host, services.lastOrNull { it.kind == DiscoveryKind.REMOTE }?.name ?: "Android device",
            services.lastOrNull { it.kind == DiscoveryKind.PAIR }?.port,
            services.lastOrNull { it.kind == DiscoveryKind.CONNECT }?.port)
    }
}
