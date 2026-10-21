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
import io.konnecttv.plus.RemotePreferences
import io.konnecttv.plus.Conn
import io.konnecttv.plus.TabBarClearance
import io.konnecttv.plus.TvViewModel
import io.konnecttv.plus.diagnostics.DiagnosticLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(vm: TvViewModel, onBack: (() -> Unit)? = null) {
    val haptics by RemotePreferences.haptics.collectAsStateWithLifecycle()
    val oneHanded by RemotePreferences.oneHanded.collectAsStateWithLifecycle()
    val touchpad by RemotePreferences.touchpad.collectAsStateWithLifecycle()
    val pending by vm.setupImport.collectAsStateWithLifecycle()
    val connected by vm.conn.collectAsStateWithLifecycle()
    val inputReady by vm.remoteInputAvailable.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var access by remember { mutableStateOf(false) }
    val importSetup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::previewSetup) }
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
        item { Card {
            Row { Text("Phone haptics",style=Body,modifier=Modifier.weight(1f));Toggle(haptics) { RemotePreferences.set("haptics",it) } }
            Text("Connection/pairing success, buttons and completed volume/seek changes. Requires a phone vibrator; button feedback also follows Android system haptic settings.",style=Small)
            Row { Text("One-handed Remote",style=Body,modifier=Modifier.weight(1f));Toggle(oneHanded) { RemotePreferences.set("oneHanded",it) } }
            Text("Compact 220dp pad sits lower, with extra controls in the bottom sheet.",style=Small)
            Row { Text("Touchpad instead of D-pad",style=Body,modifier=Modifier.weight(1f));Toggle(touchpad) { RemotePreferences.set("touchpad",it) } }
        } }
        item { Card {
            Text("TV cursor, dimming and message bubbles",style=Body)
            Text("Optional TV accessibility access can tap the screen and draw overlays across apps. No screen content is read. TV Home stays unchanged. Left edge adjusts screen dimming, not panel backlight; right edge adjusts exposed TV volume. Apps/firmware may ignore pointer taps or block overlays.",style=Small)
            PrimaryButton(if(busy) "Working…" else "Enable / update TV touchpad access",enabled=connected is Conn.On && !busy) { access=true }
            SecondaryButton("Check TV access") { if(!busy)vm.refreshRemoteInput() }
            SecondaryButton("Revoke TV touchpad access") { if(!busy)vm.disableRemoteInput() }
            Text(if(inputReady && connected is Conn.On) "TV input service connected" else "TV input service not checked / unavailable",style=Small)
        } }
        item { Card {
            Text("Share setup with another phone",style=Body)
            Text("Share names, tags, IPs and Remote preferences using Quick Share or a trusted file transfer. Review the recipient: local addresses are included. Import requires confirmation. ADB private keys, pairing grants, logs, history and wallpaper files are excluded. TV-side launcher setup is already shared; each new phone must approve debugging on the TV.",style=Small)
            SecondaryButton("Share setup file") { vm.shareSetup() }
            SecondaryButton("Import setup file") { importSetup.launch(arrayOf("application/json","text/plain","application/octet-stream")) }
        } }
        item { Card {
            Text("Away from home · mobile data",style=Body)
            Text("Mobile data works once a private VPN connects your phone to the TV/home network. Connect both ends with Tailscale/WireGuard, then enter the TV VPN IP and ADB connection port. Pair/authorize at home first. Status and bubbles use the same authorized connection; internet access alone is insufficient. There is no cloud relay, background surveillance or public ADB port forwarding in this app. TV must be awake and online; VPN support depends on its firmware.",style=Small)
        } }
        item { Text("Record connection attempts, discovery, TV command responses, installation errors, wallpaper transfers and app crashes. Logs stay on this phone until you export or share them.", style = Small) }
        item { Text("Phone package: io.konnecttv.plus. This is a separate installation from the older companion. Glowlauncher's TV package stays compatible with existing wallpaper and home settings.", style = Small) }
        item { Text("Package rules match the TV's actual packages. Critical components are protected; unreviewed system apps stay enabled. A model year alone cannot establish safety.", style = Small) }
        item { Spacer(Modifier.height(TabBarClearance)) }
    }
    GlassSheet(access,onDismiss={access=false}) {
        Text("Enable TV touchpad and bubbles?",style=Body)
        Text("Installs/updates the bundled TV companion and enables its separate accessibility service. It can tap positions and show dimming/cursor/bubbles over other apps, but does not retrieve screen content or intercept Home. Revoke here or in TV Accessibility settings. Everyone watching the TV can see sent messages.",style=Small)
        PrimaryButton("Enable on connected TV") { access=false;vm.enableRemoteInput() }
    }
    GlassSheet(pending!=null,onDismiss={vm.setupImport.value=null}) {
        Text("Import setup?",style=Body)
        Text("${pending?.tvs?.size ?: 0} saved TV hints and Remote preferences. Existing saved TVs are kept. This grants no TV access; approve this phone on each TV.",style=Small)
        pending?.tvs?.forEach { Text("${it.name} · ${it.host}:${it.port}",style=Small) }
        PrimaryButton("Import reviewed setup") { vm.confirmSetupImport() }
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
