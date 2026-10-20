package io.konnecttv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.konnecttv.TvViewModel
import io.konnecttv.adbPort
import io.konnecttv.adb.FoundTv
import io.konnecttv.data.SavedTv

/** What a pair or manual sheet is about. */
private data class PairTarget(val host: String, val name: String?, val pairPort: Int?, val tag: String? = null)

@Composable
fun ConnectScreen(vm: TvViewModel, busyText: String?) {
    val found by vm.found.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    var pairing by remember { mutableStateOf<PairTarget?>(null) }
    var manual by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<SavedTv?>(null) }

    fun open(tv: FoundTv) = when {
        tv.pairPort != null -> pairing = PairTarget(tv.host, tv.name, tv.pairPort)
        tv.tlsPort != null -> vm.connect(tv.host, tv.tlsPort, tv.name)
        else -> vm.connect(tv.host, 5555, tv.name)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Text("Connect a TV", style = H1, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)) }

            if (busyText != null) item {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = C.Accent, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(busyText, style = Body, modifier = Modifier.weight(1f))
                        Text("Cancel", style = Small.copy(color = C.Accent), modifier = Modifier.clickable { vm.cancelConnect() }.padding(8.dp))
                    }
                }
            }

            if (saved.isNotEmpty()) {
                item {
                    Text("SAVED TVS", style = Section, modifier = Modifier.padding(top = 8.dp))
                    Text("Tap to reconnect using the saved IP. Edit names, tags or addresses for each TV.", style = Small)
                }
                items(saved, key = { "s" + it.id }) { tv ->
                    // A TV on wireless debugging gets a new port after each reboot; mDNS knows the current one.
                    val live = found.firstOrNull { it.host == tv.host }
                    Card(onClick = { vm.connect(tv.host, live?.tlsPort ?: tv.port, tv.name) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Monogram(tv.name)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(tv.name, style = Body)
                                if (tv.tag.isNotBlank()) Text(tv.tag, style = Small.copy(color = C.Accent))
                                Text("${tv.host}:${live?.tlsPort ?: tv.port} · ${if (live != null) "on Wi-Fi now" else "saved address"}", style = Small)
                            }
                            Text("Edit", style = Small.copy(color = C.Accent), modifier = Modifier.clickable { editing = tv }.padding(12.dp))
                        }
                    }
                }
            }

            item { Text("FOUND ON YOUR WI-FI", style = Section, modifier = Modifier.padding(top = 8.dp)) }
            val fresh = found.filter { f -> saved.none { it.host == f.host } }
            if (fresh.isEmpty()) item {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = C.Muted, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Looking for Android TVs…", style = Small)
                    }
                }
            }
            items(fresh, key = { "f" + it.host }) { tv ->
                Card(onClick = { open(tv) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Monogram(tv.name)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tv.name, style = Body)
                            Text(
                                "${tv.host} · " + when {
                                    tv.pairPort != null -> "ready to pair"
                                    tv.tlsPort != null -> "wireless debugging on"
                                    else -> "tries network debugging"
                                },
                                style = Small,
                            )
                        }
                        Text(if (tv.pairPort != null) "Pair" else "Connect", style = Body.copy(color = C.Accent))
                    }
                }
            }

            item { Spacer(Modifier.height(4.dp)) }
            item { SecondaryButton("Enter IP address manually") { manual = true } }
            item {
                Text(
                    "First time? On the TV: Settings › Device Preferences › About › tap Build 7 times. " +
                        "In Developer options, turn on USB debugging.\n\n" +
                        "Android TV 9–10: turn on Network debugging if available, then connect to the TV IP on port 5555 " +
                        "and approve Allow debugging on the TV. Some TVs need a one-time USB connection to a computer " +
                        "and the command adb tcpip 5555.\n\n" +
                        "Android TV 11+: if Wireless debugging is available, open Pair device with pairing code. " +
                        "Use the pairing port for Pair, and the separate connection port for Connect.",
                    style = Small,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            item { Spacer(Modifier.height(40.dp)) }
        }

        GlassSheet(pairing != null, onDismiss = { pairing = null }) {
            val target = pairing ?: return@GlassSheet
            var code by remember(target) { mutableStateOf("") }
            var port by remember(target) { mutableStateOf(target.pairPort?.toString().orEmpty()) }
            Text("Pair with ${target.name ?: target.host}", style = Body.copy(fontSize = 17.sp))
            Text("On the TV: Developer options › Wireless debugging › Pair device with pairing code.", style = Small)
            if (target.pairPort == null) Field(port, { port = it.filter(Char::isDigit) }, "Pairing port (shown on TV)", KeyboardType.Number)
            CodeBoxes(code) { code = it.filter(Char::isDigit).take(6) }
            PrimaryButton("Pair", enabled = code.length == 6 && adbPort(port) != null) {
                vm.pair(target.host, adbPort(port) ?: return@PrimaryButton, code, target.name, target.tag)
                pairing = null
            }
        }

        GlassSheet(manual, onDismiss = { manual = false }) {
            var host by remember { mutableStateOf("") }
            var port by remember { mutableStateOf("5555") }
            var name by remember { mutableStateOf("") }
            var tag by remember { mutableStateOf("") }
            Text("Connect by IP", style = Body.copy(fontSize = 17.sp))
            Text("Find the IP on the TV: Settings › Network › your Wi-Fi.", style = Small)
            saved.firstOrNull()?.let { last ->
                SecondaryButton("Use last IP · ${last.name}") {
                    host = last.host
                    port = (found.firstOrNull { it.host == last.host }?.tlsPort ?: last.port).toString()
                    name = last.name
                    tag = last.tag
                }
            }
            Field(host, { host = it.trim() }, "TV IP, e.g. 192.168.1.24", KeyboardType.Uri)
            Field(port, { port = it.filter(Char::isDigit) }, "Port (5555, or the one under Wireless debugging)", KeyboardType.Number)
            Field(name, { name = it.take(80) }, "TV name (optional), e.g. Living room")
            Field(tag, { tag = it.take(80) }, "Tag (optional), e.g. Friend's TV")
            Text("Remembered on this phone after a successful connection. Each TV keeps its own name, tag and address.", style = Small)
            PrimaryButton("Connect", enabled = host.isNotEmpty() && adbPort(port) != null) {
                vm.connect(host, adbPort(port) ?: return@PrimaryButton, name.takeIf { it.isNotBlank() }, tag = tag)
                manual = false
            }
            SecondaryButton("Pair with code instead") {
                if (host.isNotEmpty()) { pairing = PairTarget(host, name.takeIf { it.isNotBlank() }, null, tag); manual = false } else vm.say("Enter the TV IP first.")
            }
        }
        editing?.let { selected -> TvDetailsSheet(vm, selected) { editing = null } }
    }
}

@Composable
fun TvDetailsSheet(vm: TvViewModel, tv: SavedTv, onDismiss: () -> Unit) {
    GlassSheet(true, onDismiss = onDismiss) {
        var name by remember(tv.id) { mutableStateOf(tv.name) }
        var tag by remember(tv.id) { mutableStateOf(tv.tag) }
        var host by remember(tv.id) { mutableStateOf(tv.host) }
        var port by remember(tv.id) { mutableStateOf(tv.port.toString()) }
        Text("Edit saved TV", style = Body.copy(fontSize = 17.sp))
        Text("TV name", style = Small)
        Field(name, { name = it.take(80) }, "Living room TV")
        Text("Tag (optional)", style = Small)
        Field(tag, { tag = it.take(80) }, "Home, Friend's TV, Bedroom…")
        Text("Address for the next connection", style = Small)
        Field(host, { host = it.trim() }, "TV IP", KeyboardType.Uri)
        Field(port, { port = it.filter(Char::isDigit) }, "Connection port", KeyboardType.Number)
        PrimaryButton("Save TV", enabled = name.isNotBlank() && host.isNotBlank() && adbPort(port) != null) {
            vm.editTv(tv.id, name, tag, host, adbPort(port) ?: return@PrimaryButton)
            onDismiss()
        }
        SecondaryButton("Forget this TV", color = C.Danger) { vm.forget(tv.id); onDismiss() }
    }
}

@Composable
fun Field(value: String, onChange: (String) -> Unit, hint: String, type: KeyboardType = KeyboardType.Text) {
    val shape = RoundedCornerShape(14.dp)
    BasicTextField(
        value, onChange,
        singleLine = true,
        textStyle = Body,
        cursorBrush = SolidColor(C.Accent),
        keyboardOptions = KeyboardOptions(keyboardType = type),
        modifier = Modifier.fillMaxWidth().background(C.Card, shape).border(1.dp, C.RaisedBorder, shape).padding(14.dp),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, style = Small.copy(fontSize = 14.sp)); inner() } },
    )
}

/** Six boxes over one hidden field, so the phone keyboard and paste both work. */
@Composable
private fun CodeBoxes(code: String, onChange: (String) -> Unit) {
    BasicTextField(
        code, onChange,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        cursorBrush = SolidColor(androidx.compose.ui.graphics.Color.Transparent),
        textStyle = Body.copy(color = androidx.compose.ui.graphics.Color.Transparent),
        decorationBox = { inner ->
            Box {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                    repeat(6) { i ->
                        val shape = RoundedCornerShape(12.dp)
                        Box(
                            Modifier.size(44.dp, 54.dp).background(C.Card, shape)
                                .border(1.dp, if (i == code.length) C.Accent else C.RaisedBorder, shape),
                            contentAlignment = Alignment.Center,
                        ) { Text(code.getOrNull(i)?.toString().orEmpty(), style = H1, textAlign = TextAlign.Center) }
                    }
                }
                Box(Modifier.size(1.dp)) { inner() }
            }
        },
    )
}
