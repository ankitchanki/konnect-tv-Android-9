package io.konnecttv.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.konnecttv.TabBarClearance
import io.konnecttv.TvViewModel
import io.konnecttv.data.TvApp

private enum class Filter(val title: String) { Mine("Installed"), System("System"), Off("Disabled") }

@Composable
fun AppsScreen(vm: TvViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val icons by vm.icons.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.Mine) }
    var selected by remember { mutableStateOf<TvApp?>(null) }
    val pickApk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::installApk) }

    val shown = apps.filter {
        when (filter) {
            Filter.Mine -> !it.system
            Filter.System -> it.system
            Filter.Off -> it.disabled
        } && (query.isBlank() || it.label.contains(query, true) || it.pkg.contains(query, true))
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Row(Modifier.padding(top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text("Apps", style = H1)
                    Spacer(Modifier.width(8.dp))
                    Text("${apps.size}", style = Small.copy(fontSize = 14.sp), modifier = Modifier.padding(bottom = 3.dp))
                }
            }
            item { Field(query, { query = it }, "Search apps") }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                    Filter.entries.forEach { f -> Chip(f.title, f == filter) { filter = f } }
                }
            }
            items(shown, key = { it.pkg }) { app ->
                Card(onClick = { selected = app }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(app.label, icons[app.pkg])
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(app.label, style = Body.copy(color = if (app.disabled) C.Muted else C.Text))
                            Text(app.pkg + if (app.disabled) " · off" else "", style = Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("⋯", style = Body.copy(color = C.Muted, fontSize = 18.sp))
                    }
                }
            }
            if (shown.isEmpty()) item { Text("Nothing here.", style = Small, modifier = Modifier.padding(8.dp)) }
            item { Spacer(Modifier.height(4.dp)) }
            item { SecondaryButton("＋ Install APK from phone") { pickApk.launch(arrayOf("application/vnd.android.package-archive")) } }
            item { Spacer(Modifier.height(TabBarClearance)) }
        }

        GlassSheet(selected != null, onDismiss = { selected = null }) {
            val app = selected ?: return@GlassSheet
            // Destructive actions ask twice: the first tap arms, the second does it.
            var armed by remember(app) { mutableStateOf<String?>(null) }
            fun confirm(id: String, action: () -> Unit) {
                if (armed == id) { action(); selected = null } else armed = id
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app.label, icons[app.pkg], 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(app.label, style = Body.copy(fontSize = 17.sp))
                    Text(app.pkg, style = Small)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Open", Modifier.weight(1f)) { vm.launch(app.pkg); selected = null }
                SecondaryButton("Force stop", Modifier.weight(1f)) { vm.forceStop(app.pkg); selected = null }
            }
            SecondaryButton(if (armed == "clear") "Tap again: erase all its data" else "Clear data") { confirm("clear") { vm.clearData(app.pkg) } }
            when {
                app.disabled -> PrimaryButton("Turn back on") { vm.setDisabled(app, false); selected = null }
                vm.debloat.isProtected(app.pkg) -> Text("Needed by the TV. KonnectTV won't turn this off.", style = Small)
                else -> SecondaryButton("Turn off (undo anytime)") { vm.setDisabled(app, true); selected = null }
            }
            if (!app.system) {
                SecondaryButton(if (armed == "rm") "Tap again to uninstall" else "Uninstall", color = C.Danger) { confirm("rm") { vm.uninstall(app) } }
            }
        }
    }
}

@Composable
fun Chip(text: String, on: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Text(
        text,
        style = Small.copy(color = if (on) C.Accent else C.Text, fontSize = 13.sp),
        modifier = Modifier.clip(shape)
            .background(if (on) C.Accent.copy(alpha = 0.13f) else C.Card)
            .border(1.dp, if (on) C.Accent.copy(alpha = 0.4f) else C.Border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
