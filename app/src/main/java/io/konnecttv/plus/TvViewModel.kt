package io.konnecttv.plus

import android.app.Application
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.FileProvider
import io.konnecttv.plus.diagnostics.DiagnosticLog
import io.konnecttv.plus.data.TvIdentity
import io.konnecttv.plus.data.parsePackageInventory
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.konnecttv.plus.adb.Adb
import io.konnecttv.plus.adb.FoundTv
import io.konnecttv.plus.adb.discoverTvs
import io.konnecttv.plus.data.Change
import io.konnecttv.plus.data.DebloatList
import io.konnecttv.plus.data.SavedTv
import io.konnecttv.plus.data.Store
import io.konnecttv.plus.data.TvApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

sealed interface Conn {
    data object Off : Conn
    data class Busy(val text: String) : Conn
    data class On(val tv: SavedTv) : Conn
}

data class Memory(val totalKb: Long, val freeKb: Long, val storageTotalKb: Long = 0, val storageFreeKb: Long = 0)

/** Where turning on Glowlauncher has got to, for the progress screen. */
data class LauncherSetup(val step: Int, val startedAt: Long, val done: Boolean = false, val error: String? = null)

/** Glow colours Glowlauncher can use, picked when turning it on. [RGB_GLOW] fades through all of them. */
data class GlowAccent(val name: String, val rgb: Long)

const val RGB_GLOW = -1L

val GLOW_ACCENTS = listOf(
    GlowAccent("Mint", 0x3FD6A0), GlowAccent("Sky", 0x4FB3FF), GlowAccent("Violet", 0x9B7BFF),
    GlowAccent("Rose", 0xFF6B9A), GlowAccent("Amber", 0xFFB547), GlowAccent("Snow", 0xE8ECF2), GlowAccent("RGB", RGB_GLOW),
)

val LAUNCHER_STEPS = listOf("Making room on the TV", "Installing Glowlauncher", "Setting it as the home screen", "Adding your apps")

data class Tweaks(val animationsOff: Boolean, val screensaverOff: Boolean, val sleepMinutes: Int?, val launcherOn: Boolean)

/** Our clean TV home screen, shipped inside this app's assets. */
private const val LAUNCHER_PKG = "io.konnecttv.glowlauncher"
/** The clean launcher older Konnect TV Plus versions downloaded; replaced by Glowlauncher. */
private const val OLD_LAUNCHER_PKG = "com.ansub.tvhome"
/** Where our APK sits on the TV while it draws icons; deleted right after. */
private const val ICON_HELPER = "/data/local/tmp/konnecttv-icons.jar"
private const val MEDIA_HELPER = "/data/local/tmp/konnecttv-plus-media.jar"
private const val LAUNCHER_ACTIVITY = "$LAUNCHER_PKG/.HomeActivity"

/** Glowlauncher's accessibility service that answers Home on TVs that keep their own home (Google TV). */
private const val HOME_SERVICE = "$LAUNCHER_PKG/$LAUNCHER_PKG.HomeButtonService"
private val HOME_SERVICE_SETTINGS = setOf("secure:enabled_accessibility_services", "secure:accessibility_enabled")

/** The TV's enabled accessibility services with ours added, keeping whatever else was on. */
internal fun withService(enabled: String, service: String): String =
    (enabled.trim().split(':').filter { it.isNotBlank() && it != "null" } + service).distinct().joinToString(":")

private val ANIMATION_KEYS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")

class TvViewModel(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    val debloat = DebloatList.load(app)
    private var adb: Adb? = null
    // libadb streams are thread-safe, but connect/disconnect and history writes must not interleave.
    private val lock = Mutex()
    private val operationLock = Mutex()

    private val _conn = MutableStateFlow<Conn>(Conn.Off)
    val conn = _conn.asStateFlow()
    val saved = MutableStateFlow(store.tvs())
    val apps = MutableStateFlow<List<TvApp>>(emptyList())
    val memory = MutableStateFlow<Memory?>(null)
    val tweaks = MutableStateFlow<Tweaks?>(null)
    val history = MutableStateFlow<List<Change>>(emptyList())
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    val identity = MutableStateFlow<TvIdentity?>(null)

    val found: StateFlow<List<FoundTv>> = discoverTvs(app)
        .catch { DiagnosticLog.record("ERROR", "Discovery", "TV discovery failed; manual IP connection remains available", it); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val tv get() = (conn.value as? Conn.On)?.tv

    init {
        // Reconnect to the last TV quietly; the connect screen stays up if it isn't there.
        saved.value.firstOrNull()?.let { connect(it.host, it.port, it.name, quiet = true) }
    }

    fun say(text: String) { DiagnosticLog.record("INFO", "Status", text); message.value = text }

    // ---------- connection ----------

    /** The connection being opened, so Cancel or the watchdog can close it and unblock connect(). */
    @Volatile private var pending: Adb? = null
    @Volatile private var connectionCancelled = false

    fun cancelConnect() {
        DiagnosticLog.record("INFO", "Connection", "Connection cancelled or timed out")
        connectionCancelled = true
        pending?.runCatching { close() }
    }

    fun connect(host: String, port: Int, name: String? = null, quiet: Boolean = false, tag: String? = null) = viewModelScope.launch {
        if (host.isBlank() || port !in 1..65535) return@launch say("Enter the TV IP and a port from 1 to 65535.")
        if (conn.value is Conn.Busy) return@launch say("A connection is already opening. Cancel it before trying another TV.")
        connectionCancelled = false
        DiagnosticLog.record("INFO", "Connection", "Connecting to $host:$port; quiet=$quiet")
        _conn.value = Conn.Busy(if (quiet) "Reconnecting…" else "Connecting… If the TV asks \"Allow debugging?\", tick Always allow and press Allow.")
        // adbd waits for the Allow tap with no timeout of its own.
        val watchdog = launch { kotlinx.coroutines.delay(if (quiet) 8_000 else 45_000); cancelConnect() }
        val result = withContext(Dispatchers.IO) {
            operationLock.withLock {
                lock.withLock {
                    runCatching { openTv(host, port, name, tag) }
                }
            }
        }
        watchdog.cancel()
        pending = null
        result.onSuccess { savedTv ->
            DiagnosticLog.record("INFO", "Connection", "ADB authorization and connection succeeded; saved TV record loaded")
            saved.value = store.tvs()
            history.value = store.history(savedTv.id)
            apps.value = emptyList()
            memory.value = null
            tweaks.value = null
            identity.value = null
            remoteMedia.value = null
            remoteMediaError.value = null
            homeChoices.value = null
            wallpaperDraft.value = null
            _conn.value = Conn.On(savedTv)
            refreshAll()
        }.onFailure { e ->
            DiagnosticLog.record("ERROR", "Connection", "Connection to $host:$port failed; ${e.javaClass.simpleName}", e)
            _conn.value = Conn.Off
            if (!quiet) say(
                when (e) {
                    is AdbPairingRequiredException -> "This TV uses Wireless debugging. Pair with the code first."
                    else -> if (connectionCancelled) "Connection cancelled. See Settings › Diagnostic logs." else "Can't reach $host:$port: ${e.javaClass.simpleName}. Check the TV's debugging approval and Wi-Fi. See Settings › Diagnostic logs."
                }
            )
        }
    }

    private fun openTv(host: String, port: Int, name: String?, tag: String?): SavedTv {
        check(!connectionCancelled) { "Connection cancelled." }
        adb?.runCatching { close() }
        adb = null
        val a = Adb(getApplication()).also { pending = it }
        try {
            check(a.connect(host, port)) { "ADB did not complete the connection. Check the TV authorization prompt and connection port." }
            fun deviceId(value: String) = value.trim().takeUnless {
                it.isBlank() || it.equals("null", true) || it.equals("unknown", true)
            }
            val id = deviceId(a.sh("getprop ro.serialno")) ?: deviceId(a.sh("settings get secure android_id")) ?: host
            val model = name?.trim()?.takeIf { it.isNotEmpty() } ?: a.sh("getprop ro.product.model").ifBlank { host }
            val old = store.tvs().firstOrNull { it.id == id }
            // A discovery label must not overwrite a custom name when DHCP changes a TV's IP.
            val savedName = if (tag != null && !name.isNullOrBlank()) name.trim() else old?.name ?: model
            val savedTv = SavedTv(id, savedName.take(80), host, port, old?.ramBefore ?: readMemory(a).freeKb,
                (tag?.trim() ?: old?.tag.orEmpty()).take(80))
            check(!connectionCancelled) { "Connection cancelled." }
            store.saveTv(savedTv)
            adb = a
            return savedTv
        } catch (e: Exception) {
            a.runCatching { close() }
            throw e
        }
    }

    /** Wireless debugging: pair with the 6-digit code, then connect on the TLS port. */
    fun pair(host: String, pairPort: Int, code: String, name: String?, tag: String? = null) = viewModelScope.launch {
        if (host.isBlank() || pairPort !in 1..65535 || code.length != 6 || !code.all(Char::isDigit)) {
            return@launch say("Enter the TV IP, pairing port (1–65535), and six-digit code.")
        }
        if (conn.value is Conn.Busy) return@launch say("Cancel the current connection first.")
        connectionCancelled = false
        DiagnosticLog.record("INFO", "Pairing", "Pairing requested at $host:$pairPort (code omitted)")
        _conn.value = Conn.Busy("Pairing…")
        val watchdog = launch { delay(45_000); cancelConnect() }
        val ok = withContext(Dispatchers.IO) {
            runCatching { Adb(getApplication()).use { pending = it; it.pair(host, pairPort, code) && !connectionCancelled } }
                .onFailure { DiagnosticLog.record("ERROR", "Pairing", "Pairing failed: ${it.javaClass.simpleName}; check pairing port, approval and code. Code and native error payload omitted.") }.getOrDefault(false)
        }
        watchdog.cancel()
        pending = null
        if (!ok) {
            DiagnosticLog.record("ERROR", "Pairing", "TV did not accept pairing. No pairing code was stored in diagnostics.")
            _conn.value = Conn.Off
            say("Pairing failed. Check the code on the TV and try again.")
            return@launch
        }
        DiagnosticLog.record("INFO", "Pairing", "Pairing succeeded; waiting for wireless connection-port discovery")
        // The TLS connect port is announced over mDNS; give it a few seconds to show up.
        repeat(20) {
            if (connectionCancelled) { _conn.value = Conn.Off; return@launch }
            found.value.firstOrNull { it.host == host }?.tlsPort?.let {
                _conn.value = Conn.Off
                return@launch connect(host, it, name, tag = tag).let {}
            }
            kotlinx.coroutines.delay(500)
        }
        _conn.value = Conn.Off
        say("Paired. Now tap the TV again, or enter the IP and port shown under Wireless debugging.")
    }

    fun disconnect() = viewModelScope.launch {
        operationLock.withLock {
            withContext(Dispatchers.IO) { lock.withLock { adb?.runCatching { close() }; adb = null } }
            wallpaperDraft.value = null
            remoteMedia.value = null
            remoteMediaError.value = null
            identity.value = null
            DiagnosticLog.record("INFO", "Connection", "Disconnected from TV")
            _conn.value = Conn.Off
        }
    }

    fun forget(id: String) {
        store.forgetTv(id)
        saved.value = store.tvs()
        if (tv?.id == id) disconnect()
    }

    fun editTv(id: String, name: String, tag: String, host: String, port: Int) {
        if (name.isBlank() || host.isBlank() || port !in 1..65535) return say("Enter a TV name, IP and valid port.")
        val current = store.tvs().firstOrNull { it.id == id } ?: return
        val edited = current.copy(name = name.trim().take(80), tag = tag.trim().take(80), host = host.trim(), port = port)
        store.updateTv(edited)
        saved.value = store.tvs()
        // Editing a saved address applies to the next connection, not the active socket.
        tv?.takeIf { it.id == id }?.let { _conn.value = Conn.On(it.copy(name = edited.name, tag = edited.tag)) }
        say("TV details saved on this phone.")
    }

    // ---------- helpers ----------

    /** Runs TV work off the main thread; any failure becomes a message, a dropped link goes back to Connect. */
    private fun work(name: String = "TV operation", onFailure: ((String) -> Unit)? = null, block: suspend (Adb) -> Unit) = viewModelScope.launch {
        val a = adb ?: return@launch say("Not connected.")
        val target = tv?.id ?: return@launch say("Not connected.")
        operationLock.withLock {
            if (a !== adb || tv?.id != target) return@withLock say("The TV changed. Try again on the selected TV.")
            busy.value = true
            DiagnosticLog.record("INFO", "Operation", "$name started")
            try {
                withContext(Dispatchers.IO) { block(a) }
                DiagnosticLog.record("INFO", "Operation", "$name completed")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                DiagnosticLog.record("ERROR", "Operation", "$name failed", e)
                if (!a.isConnected) {
                    _conn.value = Conn.Off
                    say("Lost connection to the TV.")
                } else {
                    val text = e.message ?: "Something went wrong."
                    onFailure?.invoke(text)
                    say(text)
                }
            } finally {
                busy.value = false
            }
        }
    }

    private suspend fun record(change: Change) = lock.withLock {
        val id = tv?.id ?: return@withLock
        history.value = listOf(change) + history.value
        store.setHistory(id, history.value)
    }

    private suspend fun forgetChange(change: Change) = lock.withLock {
        val id = tv?.id ?: return@withLock
        history.value = history.value - change
        store.setHistory(id, history.value)
    }

    fun refreshAll() = work("Refresh TV information") { a ->
        identity.value = readIdentity(a)
        apps.value = readApps(a)
        loadIcons(a)
        memory.value = readMemory(a)
        tweaks.value = readTweaks(a)
    }

    // ---------- icons ----------

    private val iconDir = File(app.filesDir, "icons").apply {
        mkdirs()
        // After an app update, retry the apps that came back without an icon last time.
        val stamp = File(this, ".version")
        val version = File(app.applicationInfo.sourceDir).lastModified().toString()
        if (stamp.takeIf { it.exists() }?.readText() != version) {
            listFiles()?.filter { it.length() == 0L }?.forEach { it.delete() }
            stamp.writeText(version)
        }
    }
    val icons = MutableStateFlow<Map<String, ImageBitmap>>(emptyMap())

    /** Fetches icons we haven't cached yet, in the background, without holding up the screen. */
    private fun loadIcons(a: Adb) = viewModelScope.launch(Dispatchers.IO) {
        // One fetch at a time, so two refreshes never push the helper over each other.
        iconLock.withLock { runCatching { fetchIcons(a) }.onFailure { DiagnosticLog.record("WARN", "Icons", "App icon retrieval failed", it) } }
    }
    private val iconLock = Mutex()

    /**
     * ADB has no "get icon" command, so the TV draws them: our own APK goes to /data/local/tmp for a
     * moment and [io.konnecttv.plus.adb.IconDump] runs from it under app_process. Nothing is installed.
     * Icons are cached per package forever; an empty file means "this app has none".
     */
    private fun fetchIcons(a: Adb) {
        val pkgs = apps.value.map { it.pkg }
        val decoded = pkgs.filter { it !in icons.value && png(it).length() > 0 }
            .mapNotNull { p -> BitmapFactory.decodeFile(png(p).path)?.let { p to it.asImageBitmap() } }
        icons.value += decoded
        // Your own apps first so the Installed list fills in fast, then the system ones in one run.
        val missing = apps.value.filter { !labelFile(it.pkg).exists() }.partition { !it.system }.toList()
            .map { group -> group.map { it.pkg } }.filter { it.isNotEmpty() }
        if (missing.isEmpty()) return
        a.push(helperJar(), ICON_HELPER)
        try {
            for (batch in missing) {
                // One "package<TAB>label<TAB>base64-png" line per app.
                val got = a.sh("CLASSPATH=$ICON_HELPER app_process /system/bin io.konnecttv.plus.adb.IconDump ${batch.joinToString(" ")} 2>/dev/null")
                    .lines().map { it.trimEnd('\r').split('\t') }.filter { it.size == 3 }.associateBy { it[0] }
                // Nothing at all back means the helper didn't run; leave the batch to retry next time.
                if (got.isEmpty()) continue
                for (p in batch) {
                    val (_, label, b64) = got[p] ?: listOf(p, "", "")
                    png(p).writeBytes(runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrDefault(ByteArray(0)))
                    labelFile(p).writeText(label)
                }
                icons.value += batch.mapNotNull { p -> png(p).takeIf { it.length() > 0 }?.let { BitmapFactory.decodeFile(it.path) }?.let { p to it.asImageBitmap() } }
                apps.value = apps.value.map { it.copy(label = label(it.pkg)) }.sortedBy { it.label.lowercase() }
            }
        } finally {
            a.sh("rm -f $ICON_HELPER")
        }
    }

    private fun png(pkg: String) = File(iconDir, "$pkg.png")
    private fun labelFile(pkg: String) = File(iconDir, "$pkg.txt")

    /** The app's real name from the TV once we have it, else our best guess from the package name. */
    private fun label(pkg: String) = labelFile(pkg).takeIf { it.length() > 0 }?.readText() ?: debloat.label(pkg)

    /** Just our code, compressed: ~1 MB to send instead of the whole 7.6 MB APK with native libraries. */
    @Synchronized private fun helperJar(): File {
        val ctx = getApplication<Application>()
        val apk = File(ctx.applicationInfo.sourceDir)
        val jar = File(ctx.cacheDir, "icon-helper-${apk.lastModified()}.jar")
        if (!jar.exists()) {
            val tmp = File(ctx.cacheDir, "icon-helper.tmp")
            ZipOutputStream(tmp.outputStream()).use { out ->
                ZipFile(apk).use { zip ->
                    zip.entries().asSequence().filter { it.name.matches(Regex("classes\\d*\\.dex")) }.forEach { entry ->
                        out.putNextEntry(ZipEntry(entry.name))
                        zip.getInputStream(entry).use { it.copyTo(out) }
                        out.closeEntry()
                    }
                }
            }
            check(tmp.renameTo(jar)) { "Could not prepare the TV helper." }
        }
        return jar
    }

    private fun readApps(a: Adb): List<TvApp> {
        val output = a.checked("pm list packages --user 0 && echo __PLUS_SECTION__ && pm list packages -s --user 0 && echo __PLUS_SECTION__ && pm list packages -d --user 0 && echo __PLUS_SECTION__ && pm list packages -u -s --user 0")
        val result = parsePackageInventory(output, ::label)
        DiagnosticLog.record("INFO", "Inventory", "TV owner profile: ${result.count { it.installed }} installed; ${result.count { it.installed && it.disabled }} disabled; ${result.count { !it.installed }} system APKs not installed for owner")
        return result
    }

    private fun readIdentity(a: Adb): TvIdentity {
        val fields = a.sh("getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk; getprop ro.product.brand").lines()
        return TvIdentity(fields.getOrElse(0) { "" }, fields.getOrElse(1) { "" }, fields.getOrElse(2) { "" }, fields.getOrNull(3)?.toIntOrNull() ?: 28, fields.getOrElse(4) { "" }).also {
            DiagnosticLog.record("INFO", "Device", "Manufacturer=${it.manufacturer}; model=${it.model}; Android=${it.android}; API=${it.sdk}; brand=${it.brand}")
        }
    }

    private fun readMemory(a: Adb): Memory {
        val info = a.sh("cat /proc/meminfo").lines().associate { line ->
            line.substringBefore(':') to (line.substringAfter(':').trim().substringBefore(' ').toLongOrNull() ?: 0L)
        }
        // Internal storage, where apps live: "Filesystem 1K-blocks Used Available Use% Mounted".
        val df = a.sh("df -k /data | tail -1").split(Regex("\\s+"))
        return Memory(
            info["MemTotal"] ?: 0, info["MemAvailable"] ?: info["MemFree"] ?: 0,
            df.getOrNull(1)?.toLongOrNull() ?: 0, df.getOrNull(3)?.toLongOrNull() ?: 0,
        )
    }

    private fun readTweaks(a: Adb): Tweaks {
        val v = a.sh(
            "settings get global window_animation_scale; settings get secure screensaver_enabled; " +
                "settings get system screen_off_timeout; (pm path $LAUNCHER_PKG; pm path $OLD_LAUNCHER_PKG) 2>/dev/null | head -1"
        ).lines()
        return Tweaks(
            animationsOff = v.getOrNull(0)?.toFloatOrNull() == 0f,
            screensaverOff = v.getOrNull(1) == "0",
            sleepMinutes = v.getOrNull(2)?.toLongOrNull()?.let { (it / 60_000).toInt() },
            // Installed = on, so switching it off always cleans up, even after a half-finished setup.
            launcherOn = v.getOrNull(3)?.startsWith("package:") == true,
        )
    }

    private fun isHomeActive(a: Adb) = matchesHomeComponent(
        a.sh("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1"),
        LAUNCHER_PKG, ".HomeActivity",
    )

    /** Changes a setting and keeps its original value once, so undo restores what the TV had. */
    private suspend fun putSetting(a: Adb, namespace: String, key: String, value: String, label: String) {
        val target = "$namespace:$key"
        val change = if (history.value.none { it.kind == "setting" && it.target == target }) {
            val old = a.sh("settings get $namespace $key").takeUnless { it == "null" || it.isBlank() }
            Change(System.currentTimeMillis(), "setting", target, label, old)
        } else null
        a.checked("settings put $namespace $key $value")
        change?.let { record(it) }
    }

    /** Switching a tweak back on: undo our change if we made one, otherwise set [fallback]. */
    private suspend fun restoreSetting(a: Adb, namespace: String, key: String, fallback: String) {
        val change = history.value.firstOrNull { it.kind == "setting" && it.target == "$namespace:$key" }
        if (change != null) undoOne(a, change) else a.checked("settings put $namespace $key $fallback")
    }

    // ---------- remote media controls ----------

    val remoteMedia = MutableStateFlow<RemoteMedia?>(null)
    val remoteMediaError = MutableStateFlow<String?>(null)
    private var mediaHelperConnection: Adb? = null

    private fun mediaCommand(a: Adb, args: String = "status"): RemoteMedia {
        if (mediaHelperConnection !== a) {
            a.checked("rm -f $MEDIA_HELPER")
            a.push(helperJar(), MEDIA_HELPER)
            a.checked("chmod 444 $MEDIA_HELPER")
            mediaHelperConnection = a
        }
        val raw = a.checked("CLASSPATH=$MEDIA_HELPER app_process /system/bin io.konnecttv.plus.adb.MediaBridge $args", seconds = 15)
        return parseRemoteMedia(raw, android.os.SystemClock.elapsedRealtime()).also { value ->
            if (value.warnings != remoteMedia.value?.warnings) value.warnings.forEach { DiagnosticLog.record("WARN", "Remote media", it) }
        }
    }

    /** Only the visible Remote screen polls. Skip a poll while another TV operation owns the connection. */
    fun refreshRemoteMedia() = viewModelScope.launch {
        val a = adb ?: return@launch
        val id = tv?.id ?: return@launch
        if (!operationLock.tryLock()) return@launch
        try {
            if (a !== adb || tv?.id != id) return@launch
            val value = withContext(Dispatchers.IO) { mediaCommand(a) }
            if (a === adb && tv?.id == id) { remoteMedia.value = value; remoteMediaError.value = null }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            mediaHelperConnection = null
            val text = e.message ?: "TV media controls unavailable."
            if (remoteMediaError.value != text) DiagnosticLog.record("WARN", "Remote media", "Media refresh failed", e)
            remoteMedia.value = null
            remoteMediaError.value = text
            if (!a.isConnected) _conn.value = Conn.Off
        } finally { operationLock.unlock() }
    }

    fun setTvVolume(index: Int) = work("Set TV media volume") { a ->
        val volume = remoteMedia.value?.volume ?: error("Refresh the TV volume first.")
        check(volume.supported) { volume.hint }
        remoteMedia.value = mediaCommand(a, "volume ${index.coerceIn(volume.min, volume.max)}")
        remoteMediaError.value = null
    }

    fun seekTvPlayback(key: String, position: Long) = work("Seek TV playback") { a ->
        val playback = remoteMedia.value?.playback ?: error("Refresh playback first.")
        check(playback.seekSupported && key == playback.key && key.matches(Regex("[a-f0-9]{24}"))) { "Playback changed or seeking is unavailable. Refresh and try again." }
        remoteMedia.value = mediaCommand(a, "seek $key ${position.coerceIn(0, playback.durationMs)}")
        remoteMediaError.value = null
    }

    // ---------- remote ----------

    fun key(code: Int) = work { it.keyevent(code) }
    fun type(text: String) = work { it.type(text) }

    // ---------- apps ----------

    fun launch(pkg: String) = work { a ->
        val out = a.sh("monkey -p $pkg -c android.intent.category.LEANBACK_LAUNCHER 1 2>&1")
        if ("aborted" in out) a.sh("monkey -p $pkg -c android.intent.category.LAUNCHER 1")
    }

    private fun requireEditablePackage(pkg: String) {
        val app = apps.value.firstOrNull { it.pkg == pkg } ?: error("Refresh the TV package list first.")
        check(!debloat.isProtected(pkg) && (!app.system || pkg in debloat.safe)) { debloat.reason(app) }
    }

    fun forceStop(pkg: String) = work("Force stop $pkg") { requireEditablePackage(pkg); it.checked("am force-stop --user 0 $pkg"); say("Stopped.") }

    fun clearData(pkg: String) = work("Clear data $pkg") { a -> requireEditablePackage(pkg); check(a.checked("pm clear --user 0 $pkg").contains("Success")) { "TV refused to clear app data." }; say("Data cleared.") }

    fun setDisabled(app: TvApp, off: Boolean) = work(if (off) "Disable ${app.pkg}" else "Restore ${app.pkg}") { a ->
        val current = readApps(a).firstOrNull { it.pkg == app.pkg } ?: error("This package is no longer available on the TV.")
        if (off) {
            if (current.disabled) return@work say("${app.label} is already off.")
            check(debloat.canDisable(current)) { debloat.reason(current) }
            disable(a, current.pkg, current.label)
        } else {
            if (!current.installed) a.checked("cmd package install-existing --user 0 ${app.pkg}")
            a.checked("pm enable --user 0 ${app.pkg}")
            val restored = readApps(a).firstOrNull { it.pkg == app.pkg }
            check(restored?.installed == true && !restored.disabled) { "The TV did not restore ${app.label}. Check Diagnostic logs for its response." }
            history.value.filter { it.kind == "disable" && it.target == app.pkg }.forEach { forgetChange(it) }
            labelFile(app.pkg).delete(); png(app.pkg).delete(); icons.value -= app.pkg
            say("${app.label} is enabled.")
        }
        apps.value = readApps(a)
        loadIcons(a)
    }

    private suspend fun disable(a: Adb, pkg: String, label: String) {
        val out = a.checked("pm disable-user --user 0 $pkg")
        if ("disabled" !in out) error("Couldn't turn off $label: $out")
        check(a.checked("pm list packages -d --user 0 $pkg").lines().any { it.trim() == "package:$pkg" }) { "The TV did not report $label as disabled." }
        record(Change(System.currentTimeMillis(), "disable", pkg, label))
    }

    fun uninstall(app: TvApp) = work { a ->
        // System apps are only ever disabled, never removed.
        if (app.system) return@work say("System apps can only be turned off.")
        requireEditablePackage(app.pkg)
        check(a.checked("pm uninstall --user 0 ${app.pkg}").contains("Success")) { "TV refused to uninstall ${app.label}." }
        say("${app.label} uninstalled.")
        apps.value = readApps(a)
    }

    fun installApk(uri: Uri) = work("Install APK from phone") { a ->
        val resolver = getApplication<Application>().contentResolver
        val file = File.createTempFile("install-", ".apk", getApplication<Application>().cacheDir)
        try {
            // Some document providers don't report SIZE. Stage the actual bytes instead of guessing.
            resolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } } ?: error("Can't read the selected APK.")
            say("Installing… Approve any installation prompt on the TV.")
            file.inputStream().use { a.install(it, file.length()) }
            say("Installed.")
        } finally { file.delete() }
        apps.value = readApps(a)
        loadIcons(a)
    }

    // ---------- detox ----------

    fun turnOff(pkgs: List<String>) = work { a ->
        var done = 0
        val failures = mutableListOf<String>()
        val snapshot = readApps(a).associateBy { it.pkg }
        for (pkg in pkgs) {
            val app = snapshot[pkg] ?: continue
            if (!debloat.canDisable(app)) continue
            runCatching { disable(a, pkg, app.label) }.onSuccess { done++ }.onFailure {
                failures += app.label
                DiagnosticLog.record("ERROR", "Detox", "Couldn't disable $pkg", it)
            }
        }
        apps.value = readApps(a)
        memory.value = readMemory(a)
        say("Turned off $done apps." + if (failures.isEmpty()) " Undo anytime from history." else " Failed: ${failures.joinToString()}. See Diagnostic logs.")
    }

    fun undo(change: Change) = work { a -> undoOne(a, change); apps.value = readApps(a); tweaks.value = readTweaks(a) }

    fun undoAll() = work { a ->
        history.value.forEach { undoOne(a, it) }
        apps.value = readApps(a)
        tweaks.value = readTweaks(a)
        say("Everything is back the way it was.")
    }

    private suspend fun undoOne(a: Adb, change: Change) {
        when (change.kind) {
            "disable", "home" -> a.checked("pm enable --user 0 ${change.target}")
            "install" -> a.checked("pm uninstall ${change.target}")
            "setting" -> {
                val (ns, key) = change.target.split(':', limit = 2)
                a.checked(if (change.old == null) "settings delete $ns $key" else "settings put $ns $key ${change.old}")
            }
        }
        forgetChange(change)
    }

    // ---------- boost ----------

    fun setAnimationsOff(off: Boolean) = work { a ->
        ANIMATION_KEYS.forEach {
            if (off) putSetting(a, "global", it, "0", "Animations off") else restoreSetting(a, "global", it, "1.0")
        }
        tweaks.value = readTweaks(a)
    }

    fun setScreensaverOff(off: Boolean) = work { a ->
        if (off) putSetting(a, "secure", "screensaver_enabled", "0", "Screensaver off")
        else restoreSetting(a, "secure", "screensaver_enabled", "1")
        tweaks.value = readTweaks(a)
    }

    fun setSleep(minutes: Int) = work { a ->
        val ms = (minutes * 60_000L).toString()
        // Android TV reads both, depending on brand and version.
        putSetting(a, "system", "screen_off_timeout", ms, "Sleep after $minutes min")
        putSetting(a, "secure", "sleep_timeout", ms, "Sleep after $minutes min")
        tweaks.value = readTweaks(a)
    }

    fun killBackground() = work("Clear background apps") { a ->
        val before = readMemory(a).freeKb
        a.sh("am kill-all; pm trim-caches 999G")
        memory.value = readMemory(a)
        say("Freed ${((memory.value!!.freeKb - before).coerceAtLeast(0)) / 1024} MB.")
    }

    fun setLauncher(on: Boolean, homeApps: List<String> = emptyList(), accent: Long = GLOW_ACCENTS.first().rgb) =
        work(if (on) "Install and configure Glowlauncher" else "Remove Glowlauncher") { a -> if (on) installLauncher(a, homeApps, accent) else removeLauncher(a) }

    /** Apps the clean home can show: anything with a TV launcher icon, preinstalled ones (Netflix, Prime) included. */
    val homeChoices = MutableStateFlow<List<TvApp>?>(null)

    fun loadHomeChoices() = work { a ->
        homeChoices.value = null
        val launchable = a.sh("cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LEANBACK_LAUNCHER")
            .lines().filter { '/' in it }.map { it.trim().substringBefore('/') }.toSet() - LAUNCHER_PKG
        if (apps.value.isEmpty()) apps.value = readApps(a)
        homeChoices.value = apps.value.filter { it.pkg in launchable && !it.disabled }
        loadIcons(a)
    }

    /** Changes which apps the clean home shows, while it is already on. */
    fun setHomeApps(homeApps: List<String>, accent: Long) = work { a ->
        sendHomeApps(a, homeApps, accent)
        a.keyevent(io.konnecttv.plus.adb.Key.HOME)
        say("Home screen updated.")
    }

    // ---------- launcher wallpaper ----------

    val wallpaperDraft = MutableStateFlow<WallpaperDraft?>(null)
    val wallpaperError = MutableStateFlow<String?>(null)

    fun prepareWallpaper(uri: Uri) = work("Prepare wallpaper preview") { a ->
        wallpaperError.value = null
        val id = tv?.id ?: return@work
        val screen = parseTvScreen(a.sh("wm size"))
        val bitmap = loadWallpaper(getApplication<Application>().contentResolver, uri)
        wallpaperDraft.value = WallpaperDraft(bitmap, id, screen ?: TvScreen(), screen != null)
    }

    fun closeWallpaper() { wallpaperDraft.value = null; wallpaperError.value = null }

    private fun ensureWallpaperLauncher(a: Adb) {
        if ("status=ready" in a.sh("content call --uri content://$LAUNCHER_PKG.wallpaper --method status")) return
        // Update in place with the bundled APK; keep the existing apps, glow and wallpaper.
        val file = File(getApplication<Application>().cacheDir, "glowlauncher.apk")
        getApplication<Application>().assets.open("glowlauncher.apk").use { input -> file.outputStream().use { input.copyTo(it) } }
        val result = file.inputStream().use { a.install(it, file.length()) }
        check(result.startsWith("Success")) { "Couldn't update Glowlauncher: $result" }
        check("status=ready" in a.sh("content call --uri content://$LAUNCHER_PKG.wallpaper --method status")) { "The TV couldn't open the wallpaper inbox." }
    }

    fun applyWallpaper(crop: WallpaperCrop) = work("Apply TV wallpaper", onFailure = { wallpaperError.value = it }) { a ->
        wallpaperError.value = null
        val draft = wallpaperDraft.value ?: return@work
        check(draft.tvId == tv?.id) { "Reconnect to the TV used for this wallpaper preview." }
        ensureWallpaperLauncher(a)
        val file = File.createTempFile("wallpaper-", ".jpg", getApplication<Application>().cacheDir)
        try {
            exportWallpaper(draft, crop, file)
            val hash = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            a.pushWallpaper(file)
            check("status=ok" in a.sh("content call --uri content://$LAUNCHER_PKG.wallpaper --method commit --arg $hash")) { "The TV couldn't save the wallpaper. The previous background is unchanged." }
            a.sh("am start -n $LAUNCHER_ACTIVITY")
            wallpaperDraft.value = null
            say("Wallpaper applied to ${tv?.name}.")
        } finally { file.delete() }
    }

    fun clearWallpaper() = work("Restore glow background") { a ->
        ensureWallpaperLauncher(a)
        check("status=ok" in a.sh("content call --uri content://$LAUNCHER_PKG.wallpaper --method clear")) { "Couldn't remove the wallpaper." }
        a.sh("am start -n $LAUNCHER_ACTIVITY")
        say("Original glow background restored.")
    }

    // ---------- local diagnostics ----------

    fun collectTvDiagnostics() = work("Collect TV diagnostics") { a ->
        identity.value = readIdentity(a)
        apps.value = readApps(a)
        memory.value = readMemory(a)
        DiagnosticLog.record("INFO", "Device", "Memory/storage snapshot: ${memory.value}")
        apps.value.chunked(20).forEachIndexed { index, group ->
            DiagnosticLog.record("INFO", "Inventory", "Package snapshot ${index + 1}:\n" + group.joinToString("\n") {
                "${it.pkg} | ${if (!it.installed) "not installed for owner" else if (it.disabled) "disabled" else "enabled"} | ${debloat.group(it)} | ${debloat.reason(it)}"
            })
        }
        val recent = a.sh("logcat -d -t 300 -v threadtime PackageManager:I PackageInstaller:I installd:I AndroidRuntime:E ActivityTaskManager:W ActivityManager:W '*:S'")
        recent.lines().chunked(25).forEachIndexed { i, lines -> DiagnosticLog.record("INFO", "TV logcat", "Recent TV logcat ${i + 1}:\n${lines.joinToString("\n")}") }
        say("TV diagnostics collected. Review or export them from Diagnostic logs.")
    }

    fun exportLogs(uri: Uri) = viewModelScope.launch {
        runCatching {
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(DiagnosticLog.report()) }
                    ?: error("The selected location couldn't be opened.")
            }
        }.onSuccess { say("Diagnostic logs exported.") }.onFailure { DiagnosticLog.record("ERROR", "Export", "Log export failed", it); say("Couldn't export logs: ${it.javaClass.simpleName}") }
    }

    fun shareLogs() = viewModelScope.launch {
        runCatching {
            val app = getApplication<Application>()
            val file = withContext(Dispatchers.IO) {
                val dir = File(app.cacheDir, "diagnostic-exports").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                File(dir, "KonnectTVPlus-diagnostics.txt").apply { writeText(DiagnosticLog.report()) }
            }
            val uri = FileProvider.getUriForFile(app, app.packageName + ".logs", file)
            val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply { clipData = android.content.ClipData.newRawUri("Konnect TV Plus diagnostics", uri) }
            app.startActivity(Intent.createChooser(intent, "Share diagnostic logs").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { DiagnosticLog.record("ERROR", "Export", "Log sharing failed", it); say("Couldn't open a sharing app. Use Export logs instead.") }
    }

    private val prefs = app.getSharedPreferences("konnecttv", 0)

    fun toggleLight() {
        io.konnecttv.plus.ui.C.light = !io.konnecttv.plus.ui.C.light
        prefs.edit().putBoolean("light", io.konnecttv.plus.ui.C.light).apply()
    }

    /** What the user last put on this TV's clean home, so editing starts from their picks. */
    fun homePicks(): Set<String>? = tv?.let { prefs.getStringSet("home-${it.id}", null) }

    /** The glow colour last picked for this TV, as 0xRRGGBB. */
    fun homeAccent(): Long = tv?.let { prefs.getLong("accent-${it.id}", GLOW_ACCENTS.first().rgb) } ?: GLOW_ACCENTS.first().rgb

    private fun sendHomeApps(a: Adb, homeApps: List<String>, accent: Long) {
        if (homeApps.isEmpty()) return
        tv?.let { prefs.edit().putStringSet("home-${it.id}", homeApps.toSet()).putLong("accent-${it.id}", accent).apply() }
        val hex = if (accent == RGB_GLOW) "rgb" else "%06X".format(accent and 0xFFFFFF)
        a.checked("am start -n $LAUNCHER_ACTIVITY --es apps '${homeApps.joinToString(",")}' --es accent $hex --es stamp ${System.currentTimeMillis()}")
    }

    val launcherSetup = MutableStateFlow<LauncherSetup?>(null)

    fun closeLauncherSetup() { launcherSetup.value = null }

    private fun setupStep(step: Int) = launcherSetup.update { it?.copy(step = step) }

    private suspend fun installLauncher(a: Adb, homeApps: List<String>, accent: Long) {
        launcherSetup.value = LauncherSetup(0, System.currentTimeMillis())
        try {
            // Switching home is the heaviest thing a TV does; on 1-2 GB TVs it can stall for minutes
            // when memory is full. Clear background apps first so the switch has room.
            a.sh("am kill-all")
            setupStep(1)
            val file = File(getApplication<Application>().cacheDir, "glowlauncher.apk")
            val alreadyInstalled = a.sh("pm path $LAUNCHER_PKG").startsWith("package:")
            getApplication<Application>().assets.open("glowlauncher.apk").use { input -> file.outputStream().use { input.copyTo(it) } }
            val result = file.inputStream().use { a.install(it, file.length()) }
            if (!result.startsWith("Success")) error("Install failed: $result")
            if (!alreadyInstalled && history.value.none { it.kind == "install" && it.target == LAUNCHER_PKG }) {
                record(Change(System.currentTimeMillis(), "install", LAUNCHER_PKG, "Glowlauncher installed"))
            }
            // The launcher older versions downloaded: two home screens would fight over Home.
            history.value.filter { it.kind == "install" && it.target == OLD_LAUNCHER_PKG }.forEach { undoOne(a, it) }
            setupStep(2)
            // Older versions turned off whole launcher apps; give them back. Nothing is turned off any more.
            history.value.filter { it.kind == "home" }.forEach { undoOne(a, it) }
            // Become home first, then open once with the picked apps: launching it before the switch
            // meant starting it twice on a TV that's short of memory.
            val sdk = a.sh("getprop ro.build.version.sdk").toIntOrNull() ?: 28
            a.sh(homeSelectionCommand(sdk, LAUNCHER_PKG, LAUNCHER_ACTIVITY))
            if (!isHomeActive(a)) {
                // The TV ranks its own home above any installed app (Google TV does). Leave it on and
                // let Glowlauncher's Home button service answer Home instead.
                val enabled = a.sh("settings get secure enabled_accessibility_services")
                putSetting(a, "secure", "enabled_accessibility_services", withService(enabled, HOME_SERVICE), "Home button opens Glowlauncher")
                putSetting(a, "secure", "accessibility_enabled", "1", "Home button opens Glowlauncher")
                delay(1500)
                if ("Glowlauncher Home button" !in a.sh("dumpsys accessibility | grep 'Bound services'")) {
                    undoHomeService(a)
                    error("The TV didn't start Glowlauncher's Home button service. Review Diagnostic logs; Boost can restore the recorded settings.")
                }
            }
            setupStep(3)
            sendHomeApps(a, homeApps, accent)
            a.keyevent(io.konnecttv.plus.adb.Key.HOME)
            tweaks.value = readTweaks(a)
            apps.value = readApps(a)
            launcherSetup.update { it?.copy(step = LAUNCHER_STEPS.size, done = true) }
        } catch (e: Exception) {
            launcherSetup.update { it?.copy(error = e.message ?: "Something went wrong.") }
            runCatching { tweaks.value = readTweaks(a); apps.value = readApps(a) }
            throw e
        }
    }

    private suspend fun undoHomeService(a: Adb) =
        history.value.filter { it.kind == "setting" && it.target in HOME_SERVICE_SETTINGS }.forEach { undoOne(a, it) }

    private suspend fun removeLauncher(a: Adb) {
        history.value.filter { it.kind == "home" }.forEach { undoOne(a, it) }
        undoHomeService(a)
        history.value.filter { it.kind == "install" && (it.target == LAUNCHER_PKG || it.target == OLD_LAUNCHER_PKG) }.forEach { undoOne(a, it) }
        if (a.sh("pm path $LAUNCHER_PKG").startsWith("package:")) a.checked("pm uninstall $LAUNCHER_PKG")
        tweaks.value = readTweaks(a)
        apps.value = readApps(a)
        say("TV's own home screen is back.")
    }

    override fun onCleared() {
        pending?.runCatching { close() }
        adb?.runCatching { close() }
    }
}
