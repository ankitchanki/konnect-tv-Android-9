package io.konnecttv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.konnecttv.TabBarClearance
import io.konnecttv.TvViewModel
import io.konnecttv.adb.Key
import io.konnecttv.data.SavedTv

@Composable
fun RemoteScreen(vm: TvViewModel, tv: SavedTv) {
    var typing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    // Every key already ticks the haptics through the shared press indication.
    fun press(code: Int) = vm.key(code)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TvHeader(tv, onSwitch = vm::disconnect, onTheme = vm::toggleLight) { menu = true }
            Spacer(Modifier.height(8.dp))
            DPad(::press)
            Spacer(Modifier.height(8.dp))
            KeyRow {
                RemoteKey("Back", Modifier.weight(1f), { Glyph(Glyphs.Back) }) { press(Key.BACK) }
                RemoteKey("Home", Modifier.weight(1f), { Glyph(Glyphs.Home) }) { press(Key.HOME) }
                RemoteKey("Type", Modifier.weight(1f), { Glyph(Glyphs.Keyboard) }) { typing = true }
            }
            KeyRow {
                RemoteKey("Vol −", Modifier.weight(1f), { Glyph(Glyphs.VolumeDown) }) { press(Key.VOL_DOWN) }
                RemoteKey("Mute", Modifier.weight(1f), { Glyph(Glyphs.Mute) }) { press(Key.MUTE) }
                RemoteKey("Vol +", Modifier.weight(1f), { Glyph(Glyphs.VolumeUp) }) { press(Key.VOL_UP) }
                RemoteKey("Power", Modifier.weight(1f), { PowerIcon(C.Danger, Modifier.size(20.dp)) }) { press(Key.POWER) }
            }
            KeyRow {
                RemoteKey("Play / Pause", Modifier.weight(2f), { Glyph(Glyphs.PlayPause) }) { press(Key.PLAY_PAUSE) }
                RemoteKey("Menu", Modifier.weight(1f), { Glyph(Glyphs.Menu) }) { press(Key.MENU) }
                RemoteKey("Settings", Modifier.weight(1f), { Glyph(Glyphs.Settings) }) { press(Key.SETTINGS) }
            }
            Spacer(Modifier.height(TabBarClearance))
        }

        GlassSheet(typing, onDismiss = { typing = false }) {
            var text by remember { mutableStateOf("") }
            Text("Type on TV", style = Body.copy(fontSize = 17.sp))
            Text("Select a search box on the TV first.", style = Small)
            Field(text, { text = it }, "Text to send")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("⌫ Delete", Modifier.weight(1f)) { vm.key(Key.DEL) }
                PrimaryButton("Send", Modifier.weight(1f), enabled = text.isNotEmpty()) { vm.type(text); text = "" }
            }
        }

        GlassSheet(menu, onDismiss = { menu = false }) {
            Text(tv.name, style = Body.copy(fontSize = 17.sp))
            Text("${tv.host}:${tv.port}", style = Small)
            SecondaryButton("Refresh everything") { vm.refreshAll(); menu = false }
            SecondaryButton("Switch TV") { vm.disconnect(); menu = false }
            SecondaryButton("Forget this TV", color = C.Danger) { vm.forget(tv.id); menu = false }
            Text("KonnectTV · open source · github.com/yashoncode/konnect-tv", style = Small)
        }
    }
}

@Composable
fun TvHeader(tv: SavedTv, onSwitch: () -> Unit, onTheme: () -> Unit, onMenu: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        // Tap the TV's name to go back and pick another TV.
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onSwitch).padding(vertical = 8.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(C.Accent))
            Spacer(Modifier.width(8.dp))
            Text(tv.name, style = Body)
            Glyph(Glyphs.Chevron, C.Muted, 20.dp)
        }
        // Shows the mode it switches to.
        Box(Modifier.clip(CircleShape).clickable(onClick = onTheme).padding(10.dp)) {
            Glyph(if (C.light) Glyphs.Moon else Glyphs.Sun, C.Muted, 20.dp)
        }
        Text("•••", style = Body.copy(color = C.Muted), modifier = Modifier.clip(CircleShape).clickable(onClick = onMenu).padding(10.dp))
    }
}

@Composable
private fun DPad(press: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(264.dp).clip(CircleShape).background(C.Card).border(1.dp, C.Border, CircleShape)) {
            val zone = Modifier.size(88.dp).clip(CircleShape)
            Arrow(180f, zone.align(Alignment.TopCenter)) { press(Key.UP) }
            Arrow(0f, zone.align(Alignment.BottomCenter)) { press(Key.DOWN) }
            Arrow(90f, zone.align(Alignment.CenterStart)) { press(Key.LEFT) }
            Arrow(270f, zone.align(Alignment.CenterEnd)) { press(Key.RIGHT) }
            Box(
                Modifier.align(Alignment.Center).size(100.dp).clip(CircleShape).background(C.Raised)
                    .border(1.dp, C.RaisedBorder, CircleShape).clickable { press(Key.OK) },
                contentAlignment = Alignment.Center,
            ) { Text("OK", style = Body.copy(color = C.Accent, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)) }
        }
    }
}

@Composable
private fun Arrow(rotation: Float, modifier: Modifier, onClick: () -> Unit) =
    Box(modifier.clickable(onClick = onClick), contentAlignment = Alignment.Center) { Glyph(Glyphs.Chevron, C.Muted, 30.dp, Modifier.rotate(rotation)) }

@Composable
private fun KeyRow(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), content = content)

@Composable
private fun RemoteKey(label: String, modifier: Modifier, icon: @Composable () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.height(66.dp).clip(shape).background(C.Card).border(1.dp, C.Border, shape).clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(Modifier.height(4.dp))
        Text(label, style = Small.copy(fontSize = 11.sp))
    }
}
