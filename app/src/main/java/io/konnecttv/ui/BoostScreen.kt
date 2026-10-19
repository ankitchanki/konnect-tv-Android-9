package io.konnecttv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.konnecttv.TabBarClearance
import io.konnecttv.TvViewModel
import io.konnecttv.data.SavedTv
import java.util.Locale

private val SleepChoices = listOf(5, 15, 30, 60)

@Composable
fun BoostScreen(vm: TvViewModel, tv: SavedTv) {
    val mem by vm.memory.collectAsStateWithLifecycle()
    val tweaks by vm.tweaks.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var pickSleep by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Boost", style = H1, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
            mem?.let { m ->
                RamRing(m.freeKb, m.totalKb)
                tv.ramBefore?.let {
                    Text("Before KonnectTV: ${gb(it)} free", style = Small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            } ?: Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = C.Accent, strokeWidth = 2.dp)
            }
            Spacer(Modifier.height(4.dp))

            val t = tweaks
            TweakRow("Animations off", "Menus open instantly", t?.animationsOff == true) { vm.setAnimationsOff(it) }
            TweakRow("Screensaver off", "No background work while idle", t?.screensaverOff == true) { vm.setScreensaverOff(it) }
            Card(onClick = { pickSleep = true }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sleep after", style = Body)
                        Text("TV turns off when nobody uses it", style = Small)
                    }
                    // Android stores "never" as Integer.MAX_VALUE ms, about 35791 minutes.
                    val sleep = t?.sleepMinutes?.let { if (it > 24 * 60) "Never" else "$it min" }
                    Text(sleep?.let { "$it ›" } ?: "›", style = Body.copy(color = C.Accent))
                }
            }
            TweakRow("Clean launcher", "Calm home screen: your apps, inputs, clock. Undo anytime", t?.launcherOn == true) { vm.setLauncher(it) }
            Spacer(Modifier.height(4.dp))
            SecondaryButton(if (busy) "Working…" else "Kill background apps + free cache") { vm.killBackground() }
            Spacer(Modifier.height(TabBarClearance))
        }

        GlassSheet(pickSleep, onDismiss = { pickSleep = false }) {
            Text("Sleep after", style = Body.copy(fontSize = 17.sp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SleepChoices.forEach { m -> Chip("$m min", tweaks?.sleepMinutes == m) { vm.setSleep(m); pickSleep = false } }
            }
        }
    }
}

@Composable
private fun TweakRow(title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Card(onClick = { onChange(!on) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = Body)
                Text(sub, style = Small)
            }
            Spacer(Modifier.width(12.dp))
            Toggle(on, onChange)
        }
    }
}

@Composable
private fun RamRing(freeKb: Long, totalKb: Long) {
    val fraction by animateFloatAsState(if (totalKb > 0) freeKb.toFloat() / totalKb else 0f, label = "ram")
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(170.dp)) {
            val w = 14.dp.toPx()
            drawArc(C.Card, 0f, 360f, false, style = Stroke(w))
            drawArc(C.Accent, -90f, 360f * fraction, false, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(gb(freeKb), style = H1.copy(fontWeight = FontWeight.SemiBold))
            Text("free of ${gb(totalKb)}", style = Small)
        }
    }
}

private fun gb(kb: Long) = String.format(Locale.US, "%.1f GB", kb / 1024.0 / 1024.0)
