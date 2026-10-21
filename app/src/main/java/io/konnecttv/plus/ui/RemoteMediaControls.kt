package io.konnecttv.plus.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.konnecttv.plus.RemoteMedia
import io.konnecttv.plus.mediaTime
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun RemoteMediaControls(state: RemoteMedia?, error: String?, busy: Boolean,
    onVolume: (Int) -> Unit, onSeek: (String, Long) -> Unit, onRefresh: () -> Unit, onEnable: () -> Unit = {}, appLabel: String? = null) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state?.playback?.key) { while (true) { now = SystemClock.elapsedRealtime(); delay(500) } }
    // A short bottom edge so the keys under the card sit right below the volume line.
    Card(bottom = 6.dp) {
        val playback = state?.playback
        Text(playback?.title ?: if (error == null) "Checking TV media…" else "Media controls unavailable", style = Body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!playback?.appPackage.isNullOrBlank()) Text("${appLabel ?: "TV player"} · ${if(playback?.playing==true) "Playing" else "Paused / stopped"}",style=Small,maxLines=1,overflow=TextOverflow.Ellipsis)
        var seekDrag by remember(playback?.key) { mutableStateOf<Float?>(null) }
        var seekTouching by remember(playback?.key) { mutableStateOf(false) }
        LaunchedEffect(seekDrag, seekTouching) { if (seekDrag != null && !seekTouching) { delay(3_500); seekDrag = null } }
        val duration = playback?.durationMs ?: 0
        val actual = playback?.positionAt(now) ?: 0
        val fraction = seekDrag ?: if (duration > 0) actual.toFloat() / duration else 0f
        MediaLine(fraction, "Video playback position", playback?.seekSupported == true && !busy,
            { seekTouching = true; seekDrag = it }, {
                val requested = seekDrag
                seekTouching = false
                if (requested != null && playback != null) onSeek(playback.key, (requested.toDouble() * duration).toLong())
            })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(mediaTime(if (seekDrag != null) (fraction * duration).toLong() else actual), style = Small)
            Text(if (duration > 0) mediaTime(duration) else "—:—", style = Small)
        }
        Text(if (seekDrag != null && !seekTouching) "Seeking… waiting for the player" else playback?.hint ?: "Connect a TV to read playback and volume.", style = Small)
        val volume = state?.volume
        var volumeDrag by remember { mutableStateOf<Float?>(null) }
        var volumeTouching by remember { mutableStateOf(false) }
        LaunchedEffect(volumeDrag, volumeTouching) { if (volumeDrag != null && !volumeTouching) { delay(3_500); volumeDrag = null } }
        LaunchedEffect(volume?.index) { if (!volumeTouching) volumeDrag = null }
        val volumeFraction = volumeDrag ?: if (volume != null && volume.max > volume.min) (volume.index - volume.min).toFloat() / (volume.max - volume.min) else 0f
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Volume", style = Small)
            Text(if (volume?.supported == true) "${(volumeFraction * 100).roundToInt()}%" else "Unavailable", style = Small)
        }
        MediaLine(volumeFraction, "TV volume", volume?.supported == true && !busy,
            { volumeTouching = true; volumeDrag = it }, {
                val requested = volumeDrag
                volumeTouching = false
                if (requested != null && volume != null) onVolume(volume.min + (requested * (volume.max - volume.min)).roundToInt())
            })
        if (volume?.supported != true) Text(volume?.hint ?: "Waiting for TV volume…", style = Small)
        if (error != null || state?.needsAccess == true) {
            Text("Enable installs/updates the bundled TV companion and grants Android notification-listener access for playback controls. Notification contents are not read or saved. TV Home is kept. Removing the companion also removes these sliders.", style = Small)
            PrimaryButton(if (busy) "Setting up…" else "Enable media sliders", enabled = !busy, onClick = onEnable)
            SecondaryButton("Refresh media state") { if (!busy) onRefresh() }
        }
    }
}

/** A slim rounded line with tap/drag and Android accessibility slider semantics. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaLine(value: Float, label: String, enabled: Boolean, onChange: (Float) -> Unit, onDone: () -> Unit) {
    val active = if (enabled) C.Accent else C.Muted
    val inactive = C.Border
    Slider(value = value.coerceIn(0f, 1f), onValueChange = onChange, onValueChangeFinished = onDone, enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(34.dp).semantics { contentDescription = label },
        colors = SliderDefaults.colors(thumbColor = active, activeTrackColor = active, inactiveTrackColor = inactive),
        thumb = { Box(Modifier.size(12.dp).background(active, CircleShape)) },
        track = { slider ->
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                drawLine(inactive, Offset(0f, center.y), Offset(size.width, center.y), size.height, StrokeCap.Round)
                val fraction = slider.value.coerceIn(0f, 1f)
                drawLine(active, Offset(0f, center.y), Offset(size.width * fraction, center.y), size.height, StrokeCap.Round)
            }
        })
}
