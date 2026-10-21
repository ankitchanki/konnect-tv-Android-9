package io.konnecttv.plus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.konnecttv.plus.*

@Composable
fun BubbleSettingsScreen(vm: TvViewModel, onBack: () -> Unit) {
    BackHandler(onBack=onBack)
    val connection by vm.conn.collectAsStateWithLifecycle()
    val config by vm.bubbleConfig.collectAsStateWithLifecycle()
    val apps by vm.bubbleApps.collectAsStateWithLifecycle()
    val icons by vm.icons.collectAsStateWithLifecycle()
    val error by vm.bubbleSettingsError.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val tv=(connection as? Conn.On)?.tv
    var draft by remember(config,tv?.id) { mutableStateOf(config) }
    LaunchedEffect(tv?.id) { if(tv!=null) vm.loadBubbleConfig() }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { Text("TV message bubbles",style=H1,modifier=Modifier.padding(top=20.dp)) }
        item { SecondaryButton("Back to settings",onClick=onBack) }
        item { Text("Saved on ${tv?.name ?: "the TV"}, shared by every phone. The TV owner's overlays privacy switch always takes priority. Blocked messages report a reason; queued messages are checked again before display.",style=Small) }
        if(tv==null) item { Text("Connect a TV first.",style=Body) }
        error?.let { item { Text(it,style=Small.copy(color=C.Danger));SecondaryButton("Retry TV settings") { vm.loadBubbleConfig() } } }
        val value=draft
        if(tv!=null && value==null && error==null) item { Text("Reading settings from the TV…",style=Small) }
        if(tv!=null && value!=null) {
            item { Card { Row(Modifier.fillMaxWidth().heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                Text("Show messages on TV",style=Body,modifier=Modifier.weight(1f))
                Toggle(value.enabled) { draft=value.copy(enabled=it) }
            } } }
            item { Text("Where to show",style=Body) }
            items(BubbleScope.entries) { scope ->
                val label=when(scope) { BubbleScope.ANY -> "Over any app";BubbleScope.HOME -> "Only on the TV home screen (Glowlauncher)";BubbleScope.SELECTED -> "Only over selected apps" }
                Card(onClick={draft=value.copy(scope=scope)}) { Row(verticalAlignment=Alignment.CenterVertically) { Check(value.scope==scope);Spacer(Modifier.width(12.dp));Text(label,style=Body) } }
            }
            if(value.scope==BubbleScope.SELECTED) {
                item { Text("Select launchable TV apps · ${value.packages.size}/100",style=Small) }
                item { Card(onClick={
                    if(TV_HOME_PACKAGE !in value.packages && value.packages.size>=100) vm.say("Select at most 100 TV apps.")
                    else draft=value.copy(packages=if(TV_HOME_PACKAGE in value.packages) value.packages-TV_HOME_PACKAGE else value.packages+TV_HOME_PACKAGE)
                }) {
                    Row(verticalAlignment=Alignment.CenterVertically) { Check(TV_HOME_PACKAGE in value.packages);Spacer(Modifier.width(12.dp));AppIcon("TV home screen",icons[TV_HOME_PACKAGE]);Spacer(Modifier.width(12.dp));Text("TV home screen",style=Body) }
                } }
                items(apps,key={it.pkg}) { app ->
                    Card(onClick={
                        if(app.pkg in value.packages) draft=value.copy(packages=value.packages-app.pkg)
                        else if(value.packages.size<100) draft=value.copy(packages=value.packages+app.pkg)
                        else vm.say("Select at most 100 TV apps.")
                    }) { Row(verticalAlignment=Alignment.CenterVertically) {
                        Check(app.pkg in value.packages);Spacer(Modifier.width(12.dp));AppIcon(app.label,icons[app.pkg]);Spacer(Modifier.width(12.dp));Text(app.label,style=Body)
                    } }
                }
            }
            item { PrimaryButton(if(busy) "Working…" else "Save on TV",enabled=!busy && value.packages.size<=100) { vm.saveBubbleConfig(value) } }
            item { SecondaryButton("Reload saved TV settings") { if(!busy) vm.loadBubbleConfig() } }
        }
        item { Spacer(Modifier.height(TabBarClearance)) }
    }
}
