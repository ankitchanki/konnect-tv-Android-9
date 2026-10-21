package io.konnecttv.plus.diagnostics

import android.content.Context
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class LogEntry(val time: Long, val level: String, val category: String, val message: String)

/** Local, bounded, redacted diagnostics. Pairing codes, text input, keys and image bytes are never recorded. */
object DiagnosticLog {
    private const val LIMIT = 512 * 1024
    private var folder: File? = null
    private var exportFolder: File? = null
    private var preferences: android.content.SharedPreferences? = null
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries = _entries.asStateFlow()
    val enabled = MutableStateFlow(true)
    val verbose = MutableStateFlow(true)
    var environment = "Konnect TV Plus"
        private set
    private val session = UUID.randomUUID().toString().take(8)

    @Synchronized fun init(context: Context) {
        if (folder != null) return
        folder = File(context.filesDir, "diagnostics").apply { mkdirs() }
        exportFolder = File(context.cacheDir, "diagnostic-exports")
        preferences = context.getSharedPreferences("diagnostics", 0)
        enabled.value = preferences?.getBoolean("enabled", true) ?: true
        verbose.value = preferences?.getBoolean("verbose", true) ?: true
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        environment = "Konnect TV Plus ${info.versionName} (${info.longVersionCode})\nPackage: ${context.packageName}\nPhone: ${Build.MANUFACTURER} ${Build.MODEL}; Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}"
        _entries.value = readFiles().takeLast(400).reversed()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Write synchronously before delegating to Android's normal crash handling.
            try { record("ERROR", "Crash", "Uncaught exception on ${thread.name}", error) }
            finally { previous?.uncaughtException(thread, error) }
        }
        record("INFO", "App", "Session $session started. $environment")
    }

    @Synchronized fun record(level: String, category: String, message: String, error: Throwable? = null, force: Boolean = false) {
        if (!enabled.value && !force) return
        val body = LogSanitizer.clean(message + (error?.let { "\n${it.stackTraceToString()}" } ?: "")).take(16000)
        val entry = LogEntry(System.currentTimeMillis(), level, category, body)
        _entries.value = (listOf(entry) + _entries.value).take(400)
        // A logging failure must never replace the original operation or crash.
        runCatching {
            val dir = folder ?: return@runCatching
            val current = File(dir, "current.jsonl")
            if (current.length() >= LIMIT) {
                File(dir, "previous.jsonl").delete()
                check(current.renameTo(File(dir, "previous.jsonl")))
            }
            current.appendText(JSONObject().put("time", entry.time).put("level", level).put("category", category).put("message", body).toString() + "\n")
        }
    }

    @Synchronized fun setEnabled(on: Boolean) {
        record("INFO", "App", if (on) "Diagnostic recording enabled" else "Diagnostic recording paused", force = true)
        enabled.value = on
        preferences?.edit()?.putBoolean("enabled", on)?.apply()
    }

    fun setVerbose(on: Boolean) {
        verbose.value = on
        preferences?.edit()?.putBoolean("verbose", on)?.apply()
        record("INFO", "App", "Detailed ADB logging ${if (on) "enabled" else "disabled"}")
    }

    @Synchronized fun clear() {
        folder?.listFiles()?.forEach { it.delete() }
        exportFolder?.listFiles()?.forEach { it.delete() }
        _entries.value = emptyList()
    }

    @Synchronized fun report(): String {
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.US)
        return buildString {
            appendLine(environment)
            appendLine("Session: $session")
            appendLine("Local diagnostics. Addresses/identifiers redacted; pairing codes, typed text and keys omitted.")
            appendLine("TV snapshots may contain installed package names. Review this file before sharing.")
            appendLine()
            readFiles().forEach { appendLine("${format.format(Date(it.time))} ${it.level} [${it.category}] ${it.message}") }
        }
    }

    private fun readFiles(): List<LogEntry> = listOf("previous.jsonl", "current.jsonl").flatMap { name ->
        val file = folder?.let { File(it, name) }
        runCatching { file?.readLines().orEmpty().mapNotNull { line ->
            runCatching { JSONObject(line).let { LogEntry(it.getLong("time"), it.getString("level"), it.getString("category"), it.getString("message")) } }.getOrNull()
        } }.getOrDefault(emptyList())
    }
}
