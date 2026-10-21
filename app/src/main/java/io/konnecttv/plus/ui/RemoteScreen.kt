package io.konnecttv.plus.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.konnecttv.plus.QuickApp
import io.konnecttv.plus.TabBarClearance
import io.konnecttv.plus.TvViewModel
import io.konnecttv.plus.adb.Key
import io.konnecttv.plus.data.SavedTv

@Composable
fun RemoteScreen(vm: TvViewModel, tv: SavedTv, active: Boolean = true) {
    var typing by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var editTv by remember { mutableStateOf(false) }
    val media by vm.remoteMedia.collectAsStateWithLifecycle()
    val mediaError by vm.remoteMediaError.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(tv.id, lifecycle, active) {
        if (!active) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) { vm.refreshRemoteMedia().join(); delay(if (vm.remoteMediaError.value == null) 3_000 else 15_000) }
        }
    }
    // Every key already ticks the haptics through the shared press indication.
    fun press(code: Int) = vm.key(code)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // The D-pad grows into whatever height the keys leave, so the remote reaches the tab bar with no empty band.
        FillScreenColumn(
            screen = maxHeight, grow = 1, gap = 10.dp,
            modifier = Modifier.fillMaxWidth().testTag("remote-scroll").verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
            TvHeader(tv, onSwitch = vm::disconnect, onTheme = vm::toggleLight) { menu = true }
            DPad(::press)
            // Volume keys are gone: the volume line in the media card sets it directly.
            KeyRow {
                RemoteKey("Type", Modifier.weight(1f), { Glyph(Glyphs.Keyboard) }) { typing = true }
                RemoteKey("YouTube", Modifier.weight(1f), { YouTubeMark() }) { vm.launch(QuickApp.YouTube) }
                RemoteKey("Hotstar", Modifier.weight(1f), { HotstarMark() }) { vm.launch(QuickApp.Hotstar) }
                RemoteKey("Play/Pause", Modifier.weight(1f), { Glyph(Glyphs.PlayPause) }) { press(Key.PLAY_PAUSE) }
                RemoteKey("Menu", Modifier.weight(1f), { Glyph(Glyphs.Menu) }) { press(Key.MENU) }
            }
            // The keys used most while watching sit right under the seek and volume lines, with almost no gap.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                RemoteMediaControls(media, mediaError, busy, vm::setTvVolume, vm::seekTvPlayback, { vm.refreshRemoteMedia() }, { vm.enableMediaControls() })
                KeyRow(Modifier.testTag("remote-bottom-keys")) {
                    RemoteKey("Home", Modifier.weight(1f), { Glyph(Glyphs.Home) }) { press(Key.HOME) }
                    RemoteKey("Back", Modifier.weight(1f), { Glyph(Glyphs.Back) }) { press(Key.BACK) }
                    RemoteKey("Settings", Modifier.weight(1f), { Glyph(Glyphs.Settings) }) { press(Key.SETTINGS) }
                    RemoteKey("Mute", Modifier.weight(1f), { Glyph(Glyphs.Mute) }) { press(Key.MUTE) }
                    RemoteKey("Off", Modifier.weight(1f), { PowerIcon(C.Danger, Modifier.size(20.dp)) }) { press(Key.POWER) }
                }
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
            if (tv.tag.isNotBlank()) Text(tv.tag, style = Small)
            SecondaryButton("Edit TV name, tag or IP") { menu = false; editTv = true }
            if (media != null && media?.needsAccess == false) SecondaryButton("Disable playback access") { vm.disableMediaControls(); menu = false }
            SecondaryButton("Refresh everything") { vm.refreshAll(); menu = false }
            SecondaryButton("Switch TV") { vm.disconnect(); menu = false }
            SecondaryButton("Forget this TV", color = C.Danger) { vm.forget(tv.id); menu = false }
            Text("Konnect TV Plus · Android 9+ fork · github.com/ankitchanki/konnect-tv-Android-9", style = Small)
        }
        if (editTv) TvDetailsSheet(vm, tv) { editTv = false }
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

/**
 * Stacks children top to bottom. Child [grow] is measured last as a square that takes the height the
 * others leave on a [screen]-tall page: never smaller than [min], never wider than the column.
 * When the page is too short for that minimum, the column grows taller and scrolls.
 */
@Composable
private fun FillScreenColumn(screen: Dp, grow: Int, gap: Dp, modifier: Modifier = Modifier, min: Dp = 220.dp, content: @Composable () -> Unit) =
    Layout(content, modifier) { measurables, constraints ->
        val gapPx = gap.roundToPx()
        val width = constraints.maxWidth
        val loose = Constraints(maxWidth = width)
        val placed = arrayOfNulls<Placeable>(measurables.size)
        measurables.forEachIndexed { i, m -> if (i != grow) placed[i] = m.measure(loose) }
        val used = placed.sumOf { it?.height ?: 0 } + gapPx * (measurables.size - 1)
        val side = (screen.roundToPx() - used).coerceAtMost(width).coerceAtLeast(min.roundToPx().coerceAtMost(width))
        placed[grow] = measurables[grow].measure(Constraints.fixed(width, side))
        val height = used + side
        layout(width, height) {
            var y = 0
            placed.forEach { p -> p!!.placeRelative((width - p.width) / 2, y); y += p.height + gapPx }
        }
    }

/** Fills the square it is given; arrows, their touch zones and OK keep the original 264dp proportions. */
@Composable
private fun DPad(press: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight)
        val scale = side / 264.dp
        Box(Modifier.size(side).testTag("remote-dpad").clip(CircleShape).background(C.Card).border(1.dp, C.Border, CircleShape)) {
            val zone = Modifier.size(side / 3).clip(CircleShape)
            Arrow(180f, scale, zone.align(Alignment.TopCenter)) { press(Key.UP) }
            Arrow(0f, scale, zone.align(Alignment.BottomCenter)) { press(Key.DOWN) }
            Arrow(90f, scale, zone.align(Alignment.CenterStart)) { press(Key.LEFT) }
            Arrow(270f, scale, zone.align(Alignment.CenterEnd)) { press(Key.RIGHT) }
            Box(
                Modifier.align(Alignment.Center).size(100.dp * scale).clip(CircleShape).background(C.Raised)
                    .border(1.dp, C.RaisedBorder, CircleShape).clickable { press(Key.OK) },
                contentAlignment = Alignment.Center,
            ) { Text("OK", style = Body.copy(color = C.Accent, fontWeight = FontWeight.SemiBold, fontSize = 17.sp * scale)) }
        }
    }
}

@Composable
private fun Arrow(rotation: Float, scale: Float, modifier: Modifier, onClick: () -> Unit) =
    Box(modifier.clickable(onClick = onClick), contentAlignment = Alignment.Center) { Glyph(Glyphs.Chevron, C.Muted, 30.dp * scale, Modifier.rotate(rotation)) }

@Composable
private fun KeyRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) =
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)

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
        // Five keys a row leave little width on small phones: shorten rather than clip mid-letter.
        Text(label, style = Small.copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** YouTube's red play badge, drawn so the quick-launch key needs no bundled logo. */
@Composable
private fun YouTubeMark() = Canvas(Modifier.size(28.dp, 20.dp)) {
    drawRoundRect(Color(0xFFFF0000), cornerRadius = CornerRadius(6.dp.toPx()))
    val p = Path().apply {
        moveTo(size.width * .4f, size.height * .27f); lineTo(size.width * .66f, size.height * .5f); lineTo(size.width * .4f, size.height * .73f); close()
    }
    drawPath(p, Color.White)
}

/** Hotstar's navy tile with a star. */
@Composable
private fun HotstarMark() = Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF0F1E5C)), contentAlignment = Alignment.Center) {
    Glyph(Glyphs.Sparkle, Color.White, 14.dp)
}
