package io.konnecttv

import android.app.Application
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.muntashirakon.adb.AdbPairingRequiredException
import io.konnecttv.adb.Adb
import io.konnecttv.adb.FoundTv
import io.konnecttv.adb.discoverTvs
import io.konnecttv.data.Change
import io.konnecttv.data.DebloatList
import io.konnecttv.data.SavedTv
import io.konnecttv.data.Store
import io.konnecttv.data.TvApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
/** The clean launcher older KonnectTV versions downloaded; replaced by Glowlauncher. */
private const val OLD_LAUNCHER_PKG = "com.ansub.tvhome"
/** Where our APK sits on the TV while it draws icons; deleted right after. */
private const val ICON_HELPER = "/data/local/tmp/konnecttv-icons.jar"
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

    private val _conn = MutableStateFlow<Conn>(Conn.Off)
    val conn = _conn.asStateFlow()
    val saved = MutableStateFlow(store.tvs())
    val apps = MutableStateFlow<List<TvApp>>(emptyList())
    val memory = MutableStateFlow<Memory?>(null)
    val tweaks = MutableStateFlow<Tweaks?>(null)
    val history = MutableStateFlow<List<Change>>(emptyList())
    val message = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    val found: StateFlow<List<FoundTv>> = discoverTvs(app)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val tv get() = (conn.value as? Conn.On)?.tv

    init {
        // Reconnect to the last TV quietly; the connect screen stays up if it isn't there.
        saved.value.firstOrNull()?.let { connect(it.host, it.port, it.name, quiet = true) }
    }

    fun say(text: String) { message.value = text }

    // ---------- connection ----------

    /** The connection being opened, so Cancel or the watchdog can close it and unblock connect(). */
    @Volatile private var pending: Adb? = null

    fun cancelConnect() {
        pending?.runCatching { close() }
    }

    fun connect(host: String, port: Int, name: String? = null, quiet: Boolean = false) = viewModelScope.launch {
        if (host.isBlank() || port !in 1..65535) return@launch say("Enter the TV IP and a port from 1 to 65535.")
        _conn.value = Conn.Busy(if (quiet) "Reconnecting…" else "Connecting… If the TV asks \"Allow debugging?\", tick Always allow and press Allow.")
        // adbd waits for the Allow tap with no timeout of its own.
        val watchdog = launch { kotlinx.coroutines.delay(if (quiet) 8_000 else 45_000); cancelConnect() }
        val result = withContext(Dispatchers.IO) {
            lock.withLock {
                runCatching {
                    adb?.runCatching { close() }
                    val a = Adb(getApplication()).also { pending = it }
                    if (!a.connect(host, port)) error("The TV refused the connection.")
                    android.util.Log.i("KonnectTV", "adb connected $host:$port")
                    adb = a
                    val id = a.sh("getprop ro.serialno").ifBlank { a.sh("settings get secure android_id") }.ifBlank { host }
                    val model = name ?: a.sh("getprop ro.product.model").ifBlank { host }
                    val old = store.tvs().firstOrNull { it.id == id }
                    SavedTv(id, old?.name ?: model, host, port, old?.ramBefore ?: readMemory(a).freeKb).also(store::saveTv)
                }
            }
        }
        watchdog.cancel()
        pending = null
        result.onSuccess { savedTv ->
            saved.value = store.tvs()
            history.value = store.history(savedTv.id)
            _conn.value = Conn.On(savedTv)
            refreshAll()
        }.onFailure { e ->
            android.util.Log.w("KonnectTV", "connect $host:$port failed", e)
            _conn.value = Conn.Off
            if (!quiet) say(
                when (e) {
                    is AdbPairingRequiredException -> "This TV uses Wireless debugging. Pair with the code first."
                    else -> "Can't reach $host:$port. Check the TV is on, on this Wi-Fi, and debugging is turned on."
                }
            )
        }
    }

    /** Wireless debugging: pair with the 6-digit code, then connect on the TLS port. */
    fun pair(host: String, pairPort: Int, code: String, name: String?) = viewModelScope.launch {
        if (host.isBlank() || pairPort !in 1..65535 || code.length != 6 || !code.all(Char::isDigit)) {
            return@launch say("Enter the TV IP, pairing port (1–65535), and six-digit code.")
        }
        _conn.value = Conn.Busy("Pairing…")
        val ok = withContext(Dispatchers.IO) {
            runCatching { Adb(getApplication()).use { it.pair(host, pairPort, code) } }.getOrDefault(false)
        }
        if (!ok) {
            _conn.value = Conn.Off
            say("Pairing failed. Check the code on the TV and try again.")
            return@launch
        }
        // The TLS connect port is announced over mDNS; give it a few seconds to show up.
        repeat(20) {
            found.value.firstOrNull { it.host == host }?.tlsPort?.let { return@launch connect(host, it, name).let {} }
            kotlinx.coroutines.delay(500)
        }
        _conn.value = Conn.Off
        say("Paired. Now tap the TV again, or enter the IP and port shown under Wireless debugging.")
    }

    fun disconnect() = viewModelScope.launch(Dispatchers.IO) {
        lock.withLock { adb?.runCatching { close() }; adb = null }
        _conn.value = Conn.Off
    }

    fun forget(id: String) {
        store.forgetTv(id)
        saved.value = store.tvs()
        if (tv?.id == id) disconnect()
    }

    // ---------- helpers ----------

    /** Runs TV work off the main thread; any failure becomes a message, a dropped link goes back to Connect. */
    private fun work(block: suspend (Adb) -> Unit) = viewModelScope.launch {
        val a = adb ?: return@launch say("Not connected.")
        busy.value = true
        try {
            withContext(Dispatchers.IO) { block(a) }
        } catch (e: Exception) {
            if (!a.isConnected) {
                _conn.value = Conn.Off
                say("Lost connection to the TV.")
            } else {
                say(e.message ?: "Something went wrong.")
            }
        } finally {
            busy.value = false
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

    fun refreshAll() = work { a ->
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
        iconLock.withLock { runCatching { fetchIcons(a) }.onFailure { android.util.Log.w("KonnectTV", "icons failed", it) } }
    }
    private val iconLock = Mutex()

    /**
     * ADB has no "get icon" command, so the TV draws them: our own APK goes to /data/local/tmp for a
     * moment and [io.konnecttv.adb.IconDump] runs from it under app_process. Nothing is installed.
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
                val got = a.sh("CLASSPATH=$ICON_HELPER app_process /system/bin io.konnecttv.adb.IconDump ${batch.joinToString(" ")} 2>/dev/null")
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
    private fun helperJar(): File {
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
            tmp.renameTo(jar)
        }
        return jar
    }

    private fun readApps(a: Adb): List<TvApp> {
        val out = a.sh("pm list packages; echo ---; pm list packages -s; echo ---; pm list packages -d").split("---")
        fun pkgs(i: Int) = out.getOrElse(i) { "" }.lines().map { it.removePrefix("package:").trim() }.filter { it.isNotEmpty() }.toSet()
        val system = pkgs(1)
        val disabled = pkgs(2)
        return pkgs(0).map { TvApp(it, label(it), it in system, it in disabled) }.sortedBy { it.label.lowercase() }
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
        if (history.value.none { it.kind == "setting" && it.target == target }) {
            val old = a.sh("settings get $namespace $key").takeUnless { it == "null" || it.isBlank() }
            record(Change(System.currentTimeMillis(), "setting", target, label, old))
        }
        a.sh("settings put $namespace $key $value")
    }

    /** Switching a tweak back on: undo our change if we made one, otherwise set [fallback]. */
    private suspend fun restoreSetting(a: Adb, namespace: String, key: String, fallback: String) {
        val change = history.value.firstOrNull { it.kind == "setting" && it.target == "$namespace:$key" }
        if (change != null) undoOne(a, change) else a.sh("settings put $namespace $key $fallback")
    }

    // ---------- remote ----------

    fun key(code: Int) = work { it.keyevent(code) }
    fun type(text: String) = work { it.type(text) }

    // ---------- apps ----------

    fun launch(pkg: String) = work { a ->
        val out = a.sh("monkey -p $pkg -c android.intent.category.LEANBACK_LAUNCHER 1 2>&1")
        if ("aborted" in out) a.sh("monkey -p $pkg -c android.intent.category.LAUNCHER 1")
    }

    fun forceStop(pkg: String) = work { it.sh("am force-stop $pkg"); say("Stopped.") }

    fun clearData(pkg: String) = work { a -> say(if (a.sh("pm clear $pkg").contains("Success")) "Data cleared." else "TV refused to clear data.") }

    fun setDisabled(app: TvApp, off: Boolean) = work { a ->
        if (off) {
            if (debloat.isProtected(app.pkg)) return@work say("${app.label} is needed by the TV and stays on.")
            disable(a, app.pkg, app.label)
        } else {
            a.sh("pm enable ${app.pkg}")
            history.value.filter { it.kind == "disable" && it.target == app.pkg }.forEach { forgetChange(it) }
        }
        apps.value = readApps(a)
    }

    private suspend fun disable(a: Adb, pkg: String, label: String) {
        val out = a.sh("pm disable-user --user 0 $pkg")
        if ("disabled" !in out) error("Couldn't turn off $label: $out")
        record(Change(System.currentTimeMillis(), "disable", pkg, label))
    }

    fun uninstall(app: TvApp) = work { a ->
        // System apps are only ever disabled, never removed.
        if (app.system) return@work say("System apps can only be turned off.")
        say(if (a.sh("pm uninstall ${app.pkg}").contains("Success")) "${app.label} uninstalled." else "Uninstall failed.")
        apps.value = readApps(a)
    }

    fun installApk(uri: Uri) = work { a ->
        val resolver = getApplication<Application>().contentResolver
        val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getLong(0) else null
        } ?: error("Can't read that file.")
        say("Installing… If the TV shows a Play Protect message, allow it on the TV.")
        val result = resolver.openInputStream(uri)!!.use { a.install(it, size) }
        say(if (result.startsWith("Success")) "Installed." else "Install failed: $result")
        apps.value = readApps(a)
        loadIcons(a)
    }

    // ---------- detox ----------

    fun turnOff(pkgs: List<String>) = work { a ->
        var done = 0
        for (pkg in pkgs) {
            if (debloat.isProtected(pkg)) continue
            runCatching { disable(a, pkg, debloat.label(pkg)) }.onSuccess { done++ }
        }
        apps.value = readApps(a)
        memory.value = readMemory(a)
        say("Turned off $done apps. Undo anytime from history.")
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
            "disable", "home" -> a.sh("pm enable ${change.target}")
            "install" -> a.sh("pm uninstall ${change.target}")
            "setting" -> {
                val (ns, key) = change.target.split(':', limit = 2)
                a.sh(if (change.old == null) "settings delete $ns $key" else "settings put $ns $key ${change.old}")
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

    fun killBackground() = work { a ->
        val before = readMemory(a).freeKb
        a.sh("am kill-all; pm trim-caches 999G")
        memory.value = readMemory(a)
        say("Freed ${((memory.value!!.freeKb - before).coerceAtLeast(0)) / 1024} MB.")
    }

    fun setLauncher(on: Boolean, homeApps: List<String> = emptyList(), accent: Long = GLOW_ACCENTS.first().rgb) =
        work { a -> if (on) installLauncher(a, homeApps, accent) else removeLauncher(a) }

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
        a.keyevent(io.konnecttv.adb.Key.HOME)
        say("Home screen updated.")
    }

    private val prefs = app.getSharedPreferences("konnecttv", 0)

    fun toggleLight() {
        io.konnecttv.ui.C.light = !io.konnecttv.ui.C.light
        prefs.edit().putBoolean("light", io.konnecttv.ui.C.light).apply()
    }

    /** What the user last put on this TV's clean home, so editing starts from their picks. */
    fun homePicks(): Set<String>? = tv?.let { prefs.getStringSet("home-${it.id}", null) }

    /** The glow colour last picked for this TV, as 0xRRGGBB. */
    fun homeAccent(): Long = tv?.let { prefs.getLong("accent-${it.id}", GLOW_ACCENTS.first().rgb) } ?: GLOW_ACCENTS.first().rgb

    private fun sendHomeApps(a: Adb, homeApps: List<String>, accent: Long) {
        if (homeApps.isEmpty()) return
        tv?.let { prefs.edit().putStringSet("home-${it.id}", homeApps.toSet()).putLong("accent-${it.id}", accent).apply() }
        val hex = if (accent == RGB_GLOW) "rgb" else "%06X".format(accent and 0xFFFFFF)
        a.sh("am start -n $LAUNCHER_ACTIVITY --es apps '${homeApps.joinToString(",")}' --es accent $hex --es stamp ${System.currentTimeMillis() / 1000}")
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
            getApplication<Application>().assets.open("glowlauncher.apk").use { input -> file.outputStream().use { input.copyTo(it) } }
            val result = file.inputStream().use { a.install(it, file.length()) }
            if (!result.startsWith("Success")) error("Install failed: $result")
            if (history.value.none { it.kind == "install" && it.target == LAUNCHER_PKG }) {
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
                    error("The TV didn't start Glowlauncher's Home button service, so Home would still open the TV's own home. Nothing was changed.")
                }
            }
            setupStep(3)
            sendHomeApps(a, homeApps, accent)
            a.keyevent(io.konnecttv.adb.Key.HOME)
            tweaks.value = readTweaks(a)
            apps.value = readApps(a)
            launcherSetup.update { it?.copy(step = LAUNCHER_STEPS.size, done = true) }
        } catch (e: Exception) {
            launcherSetup.update { it?.copy(error = e.message ?: "Something went wrong.") }
            throw e
        }
    }

    private suspend fun undoHomeService(a: Adb) =
        history.value.filter { it.kind == "setting" && it.target in HOME_SERVICE_SETTINGS }.forEach { undoOne(a, it) }

    private suspend fun removeLauncher(a: Adb) {
        history.value.filter { it.kind == "home" }.forEach { undoOne(a, it) }
        undoHomeService(a)
        history.value.filter { it.kind == "install" && (it.target == LAUNCHER_PKG || it.target == OLD_LAUNCHER_PKG) }.forEach { undoOne(a, it) }
        a.sh("pm uninstall $LAUNCHER_PKG")
        tweaks.value = readTweaks(a)
        apps.value = readApps(a)
        say("TV's own home screen is back.")
    }

    override fun onCleared() {
        adb?.runCatching { close() }
    }
}
