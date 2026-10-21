package io.konnecttv.plus.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.konnecttv.plus.Conn
import io.konnecttv.plus.TabBarClearance
import io.konnecttv.plus.TvViewModel
import io.konnecttv.plus.diagnostics.DiagnosticLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(vm: TvViewModel, onBack: (() -> Unit)? = null) {
    var logs by rememberSaveable { mutableStateOf(false) }
    if (logs) {
        BackHandler { logs = false }
        DiagnosticLogsScreen(vm) { logs = false }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Settings", style = H1, modifier = Modifier.padding(top = 20.dp)) }
        if (onBack != null) item { SecondaryButton("Back to connections", onClick = onBack) }
        item { Card { Text("Konnect TV Plus", style = Body); Text(DiagnosticLog.environment, style = Small) } }
        item { PrimaryButton("Diagnostic logs") { logs = true } }
        item { Text("Record connection attempts, discovery, TV command responses, installation errors, wallpaper transfers and app crashes. Logs stay on this phone until you export or share them.", style = Small) }
        item { Text("Phone package: io.konnecttv.plus. This is a separate installation from the older companion. Glowlauncher's TV package stays compatible with existing wallpaper and home settings.", style = Small) }
        item { Text("Package rules match the TV's actual packages. Critical components are protected; unreviewed system apps stay enabled. A model year alone cannot establish safety.", style = Small) }
        item { Spacer(Modifier.height(TabBarClearance)) }
    }
}

@Composable
private fun DiagnosticLogsScreen(vm: TvViewModel, onBack: () -> Unit) {
    val entries by DiagnosticLog.entries.collectAsStateWithLifecycle()
    val recording by DiagnosticLog.enabled.collectAsStateWithLifecycle()
    val verbose by DiagnosticLog.verbose.collectAsStateWithLifecycle()
    val conn by vm.conn.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var errors by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var clear by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { it?.let(vm::exportLogs) }
    val format = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.US) }
    val shown = entries.filter { (!errors || it.level in setOf("ERROR", "WARN")) && (query.isBlank() || "${it.category} ${it.message}".contains(query, true)) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Diagnostic logs", style = H1, modifier = Modifier.padding(top = 20.dp)) }
        item { SecondaryButton("Back to settings", onClick = onBack) }
        item {
            Card {
                Text(if (recording) "Recording on this phone" else "Recording paused", style = Body)
                Text("Local files rotate at about 1 MB; the viewer shows the latest 400 events. Pairing codes, typed text, private keys and image bytes are excluded. Addresses/identifiers are redacted. TV snapshots can include installed app names; review before sharing.", style = Small)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(if (recording) "Pause recording" else "Resume recording", Modifier.weight(1f)) { DiagnosticLog.setEnabled(!recording) }
                SecondaryButton(if (verbose) "Detailed ADB: on" else "Detailed ADB: off", Modifier.weight(1f)) { DiagnosticLog.setVerbose(!verbose) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Export logs", Modifier.weight(1f)) { export.launch("KonnectTVPlus-logs.txt") }
                SecondaryButton("Share logs", Modifier.weight(1f)) { vm.shareLogs() }
            }
        }
        item { PrimaryButton(if (busy) "Working…" else "Collect TV diagnostics", enabled = conn is Conn.On && !busy && recording) { vm.collectTvDiagnostics() } }
        if (conn !is Conn.On) item { Text("Connect a TV to add its device information, package states and recent package-manager/crash logcat entries. Connection failures are already recorded here.", style = Small) }
        item { SecondaryButton("Clear logs", color = C.Danger) { clear = true } }
        item { Field(query, { query = it }, "Search logs or installation error code") }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Chip("All events", !errors) { errors = false }; Chip("Errors / warnings", errors) { errors = true } } }
        item { Text("${shown.size} visible · newest first", style = Small) }
        if (shown.isEmpty()) item { Text("No matching log events.", style = Small) }
        itemsIndexed(shown, key = { index, entry -> "${entry.time}-$index" }) { _, entry ->
            Card {
                Text("${format.format(Date(entry.time))} · ${entry.level} · ${entry.category}", style = Small.copy(color = if (entry.level == "ERROR") C.Danger else C.Accent))
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(entry.message, style = Small.copy(fontFamily = FontFamily.Monospace, color = C.Text))
                }
            }
        }
        item { Spacer(Modifier.height(TabBarClearance)) }
    }
    GlassSheet(clear, onDismiss = { clear = false }) {
        Text("Clear diagnostic logs?", style = Body)
        Text("Deletes the diagnostic events stored on this phone. TV settings and apps are unaffected.", style = Small)
        PrimaryButton("Delete local logs") { DiagnosticLog.clear(); clear = false }
    }
}
