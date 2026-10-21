package io.konnecttv.plus.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.konnecttv.plus.TabBarClearance
import io.konnecttv.plus.TvViewModel
import io.konnecttv.plus.data.Group
import androidx.compose.ui.platform.LocalUriHandler
import java.text.DateFormat
import java.util.Date

@Composable
fun DetoxScreen(vm: TvViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var showHistory by remember { mutableStateOf(false) }
    var showNeeded by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
    var showOff by remember { mutableStateOf(true) }
    val identity by vm.identity.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current

    val active = apps.filter { it.installed && !it.disabled }
    val safe = active.filter { vm.debloat.group(it) == Group.SAFE }
    val needed = active.filter { vm.debloat.group(it) == Group.NEEDED }
    val mine = active.filter { vm.debloat.group(it) == Group.YOURS }
    val review = active.filter { vm.debloat.group(it) == Group.REVIEW }
    val off = apps.filter { it.disabled || !it.installed }
    // Ticked by default unless the list marks the app optional ("keep if you use it").
    val ticked = remember(safe.map { it.pkg }) {
        safe.filter { vm.debloat.safe[it.pkg]?.optional != true }.map { it.pkg }.toMutableStateList()
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Detox", style = H1, modifier = Modifier.weight(1f))
                    Text("Refresh", style = Small.copy(color = C.Accent), modifier = Modifier.clickable { if (!busy) vm.refreshAll() }.padding(10.dp))
                    Text(
                        "⟲ History${if (history.isNotEmpty()) " · ${history.size}" else ""}",
                        style = Small.copy(fontSize = 13.sp, color = if (history.isNotEmpty()) C.Accent else C.Muted),
                        modifier = Modifier.clip(CircleShape).clickable { showHistory = true }.padding(10.dp),
                    )
                }
            }
            item {
                Card {
                    val device = identity
                    Text(device?.let { "${it.detectedBrand(apps.map { app -> app.pkg })} · ${it.model} · Android ${it.android}" } ?: "Detecting TV packages…", style = Small)
                    Text(
                        if (safe.isEmpty()) "No reviewed optional apps enabled" else "${safe.size} optional apps reviewed",
                        style = Body.copy(color = C.Accent, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text("Matches actual packages on this TV. Review feature trade-offs before disabling. System apps are turned off, never deleted.", style = Small)
                }
            }

            if (safe.isNotEmpty()) item { Text("OPTIONAL · REVIEW TRADE-OFFS · ${safe.size}", style = Section, modifier = Modifier.padding(top = 8.dp)) }
            items(safe, key = { it.pkg }) { app ->
                val entry = vm.debloat.safe[app.pkg]
                val on = app.pkg in ticked
                Card(onClick = { if (on) ticked.remove(app.pkg) else ticked.add(app.pkg) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Check(on)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = Body)
                            Text(entry?.reason.orEmpty(), style = Small)
                            Text(app.pkg, style = Small)
                            if (!entry?.source.isNullOrBlank()) Text("${entry?.brand} · Rule source", style = Small.copy(color = C.Accent), modifier = Modifier.clickable { runCatching { uri.openUri(entry!!.source) } }.padding(vertical = 6.dp))
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth().clickable { showNeeded = !showNeeded }.padding(top = 10.dp, bottom = 2.dp)) {
                    Text("NEEDED BY TV · ${needed.size} · LOCKED", style = Section, modifier = Modifier.weight(1f))
                    Text(if (showNeeded) "Hide" else "Show", style = Section.copy(color = C.Accent))
                }
            }
            if (showNeeded) items(needed, key = { "n" + it.pkg }) { app ->
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Check(false, enabled = false)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(app.label, style = Body.copy(color = C.Muted))
                            Text(app.pkg, style = Small)
                            Text(vm.debloat.reason(app), style = Small)
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().clickable { showReview = !showReview }.padding(top = 10.dp, bottom = 2.dp)) {
                    Text("NOT REVIEWED · ${review.size} · KEPT ENABLED", style = Section, modifier = Modifier.weight(1f))
                    Text(if (showReview) "Hide" else "Show", style = Section.copy(color = C.Accent))
                }
            }
            if (showReview) items(review, key = { "r" + it.pkg }) { app ->
                Card { Text(app.label, style = Body); Text(app.pkg, style = Small); Text(vm.debloat.reason(app), style = Small) }
            }
            item {
                Row(Modifier.fillMaxWidth().clickable { showOff = !showOff }.padding(top = 10.dp, bottom = 2.dp)) {
                    Text("ALREADY OFF · ${off.size}", style = Section, modifier = Modifier.weight(1f))
                    Text(if (showOff) "Hide" else "Show", style = Section.copy(color = C.Accent))
                }
                Text("Includes apps disabled before Plus was installed and factory-disabled components. Restore only features you need.", style = Small)
            }
            if (showOff) items(off, key = { "off" + it.pkg }) { app ->
                Card {
                    Text(app.label, style = Body)
                    Text(app.pkg, style = Small)
                    Text(if (app.installed) "Already disabled / off" else "Removed for TV profile · original system APK available", style = Small.copy(color = C.Accent))
                    Text(vm.debloat.reason(app), style = Small)
                    PrimaryButton(if (app.installed) "Enable" else "Restore original app", enabled = !busy) { vm.setDisabled(app, false) }
                }
            }
            item { Text("YOUR APPS · ${mine.size} · KEPT", style = Section, modifier = Modifier.padding(top = 10.dp)) }
            item { Text(mine.joinToString(" · ") { it.label }.ifEmpty { "None" }, style = Small) }

            item { Spacer(Modifier.height(8.dp)) }
            item {
                PrimaryButton(
                    if (ticked.isEmpty()) "Tick apps to turn off" else "Turn off ${ticked.size} apps",
                    enabled = ticked.isNotEmpty() && !busy,
                ) { vm.turnOff(ticked.toList()) }
            }
            item { Spacer(Modifier.height(TabBarClearance)) }
        }

        GlassSheet(showHistory, onDismiss = { showHistory = false }) {
            Text("Undo history", style = Body.copy(fontSize = 17.sp))
            if (history.isEmpty()) Text("No changes yet.", style = Small)
            LazyColumn(Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { "${it.time}${it.target}" }) { change ->
                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when (change.kind) {
                                        "disable" -> "${change.label} turned off"
                                        else -> change.label
                                    },
                                    style = Body,
                                )
                                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(change.time)), style = Small)
                            }
                            Text("Undo", style = Body.copy(color = C.Accent), modifier = Modifier.clip(CircleShape).clickable { vm.undo(change) }.padding(8.dp))
                        }
                    }
                }
            }
            if (history.isNotEmpty()) SecondaryButton("Undo everything") { vm.undoAll(); showHistory = false }
        }
    }

    LaunchedEffect(Unit) { if (apps.isEmpty()) vm.refreshAll() }
}
