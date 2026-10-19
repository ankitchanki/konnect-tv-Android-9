package io.konnecttv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.konnecttv.TabBarClearance
import io.konnecttv.TvViewModel
import io.konnecttv.data.SavedTv
import io.konnecttv.data.TvApp
import java.util.Locale

private val SleepChoices = listOf(5, 15, 30, 60)
private val Mint = Color(0xFF3FD6A0)
private val Cyan = Color(0xFF9FF7FF)

/** Streaming apps worth pre-ticking for the clean home, besides the user's own installs. */
private val PopularHome = listOf("netflix", "amazonvideo", "primevideo", "youtube", "hotstar", "disney", "jiocinema", "zee5", "sonyliv", "spotify", "plex", "kodi")

@Composable
fun BoostScreen(vm: TvViewModel, tv: SavedTv) {
    val mem by vm.memory.collectAsStateWithLifecycle()
    val tweaks by vm.tweaks.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var pickSleep by remember { mutableStateOf(false) }
    var pickHome by remember { mutableStateOf(false) }
    var launcherMenu by remember { mutableStateOf(false) }
    val launcherOn = tweaks?.launcherOn == true

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Boost", style = H1, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val m = mem
                StatCard("RAM", m?.freeKb, m?.totalKb, Modifier.weight(1f))
                StatCard("Storage", m?.storageFreeKb, m?.storageTotalKb, Modifier.weight(1f))
            }
            tv.ramBefore?.let { Text("RAM free before KonnectTV: ${gb(it)}", style = Small, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }

            LauncherOrb(launcherOn, busy, tv.name) {
                if (launcherOn) launcherMenu = true else { vm.loadHomeChoices(); pickHome = true }
            }

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

        GlassSheet(launcherMenu, onDismiss = { launcherMenu = false }) {
            Text("Clean launcher", style = Body.copy(fontSize = 17.sp))
            Text("Active on ${tv.name}.", style = Small)
            PrimaryButton("Edit home apps") { launcherMenu = false; vm.loadHomeChoices(); pickHome = true }
            SecondaryButton("Turn off, bring back the TV's own home") { launcherMenu = false; vm.setLauncher(false) }
        }

        GlassSheet(pickHome, onDismiss = { pickHome = false }) {
            HomePicker(vm, editing = launcherOn) { picked ->
                pickHome = false
                if (launcherOn) vm.setHomeApps(picked) else vm.setLauncher(true, picked)
            }
        }
    }
}

/** The main feature: one glowing button. The ring fills and the core lights up when it's on. */
@Composable
private fun LauncherOrb(on: Boolean, busy: Boolean, tvName: String, onClick: () -> Unit) {
    val progress by animateFloatAsState(if (on) 1f else 0f, tween(900), label = "ring")
    val core by animateColorAsState(if (on) C.OrbOn else C.Raised, tween(600), label = "core")
    val spark by animateColorAsState(if (on) C.Accent else C.Muted, tween(500), label = "spark")
    Card {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(6.dp))
            Box(Modifier.size(190.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 7.dp.toPx()
                    val inset = w / 2 + 2.dp.toPx()
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                    val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                    // Soft glow behind the core once it's on.
                    drawCircle(Brush.radialGradient(listOf(Mint.copy(alpha = 0.28f * progress), Color.Transparent)), radius = size.minDimension / 2)
                    drawArc(C.Border, 0f, 360f, false, topLeft, arcSize, style = Stroke(w))
                    drawArc(Brush.linearGradient(listOf(Mint, Cyan)), -90f, 360f * progress, false, topLeft, arcSize, style = Stroke(w, cap = StrokeCap.Round))
                }
                Box(
                    Modifier.size(132.dp).clip(CircleShape).background(core).border(1.dp, C.Border, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (busy) CircularProgressIndicator(color = C.Accent, strokeWidth = 2.dp, modifier = Modifier.size(40.dp))
                    else Glyph(Glyphs.Sparkle, spark, 46.dp, Modifier.rotate(90f * progress).scale(1f + 0.08f * progress))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("Clean launcher", style = Body.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(8.dp))
            val pill = when {
                busy -> "WORKING…"
                on -> "ACTIVE ON ${tvName.uppercase()}"
                else -> "TAP THE ORB TO CLEAN"
            }
            Text(
                pill,
                style = Small.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = if (on) C.Accent else C.Muted),
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (on) C.Accent.copy(alpha = 0.13f) else C.Raised).padding(horizontal = 12.dp, vertical = 5.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text("A calm home screen with only the apps you pick. Undo anytime.", style = Small, textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Choose what the clean home shows. Preinstalled apps like Netflix and Prime are here too. */
@Composable
private fun HomePicker(vm: TvViewModel, editing: Boolean, onDone: (List<String>) -> Unit) {
    val choices by vm.homeChoices.collectAsStateWithLifecycle()
    val icons by vm.icons.collectAsStateWithLifecycle()
    val list = choices
    var picked by remember(list) {
        val saved = vm.homePicks()
        mutableStateOf(list.orEmpty().filter { saved?.contains(it.pkg) ?: defaultOnHome(it) }.map { it.pkg }.toSet())
    }
    Text(if (editing) "Home screen apps" else "Pick apps for your home", style = Body.copy(fontSize = 17.sp))
    Text("Your clean home shows these, plus inputs and a clock.", style = Small)
    if (list == null) {
        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.Accent, strokeWidth = 2.dp) }
    } else {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            list.forEach { app ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .clickable { picked = if (app.pkg in picked) picked - app.pkg else picked + app.pkg }
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppIcon(app.label, icons[app.pkg], 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(app.label, style = Body, modifier = Modifier.weight(1f))
                    Check(app.pkg in picked)
                }
            }
        }
    }
    PrimaryButton(
        when {
            editing -> "Save (${picked.size} apps)"
            else -> "Turn on with ${picked.size} apps"
        },
        enabled = list != null && picked.isNotEmpty(),
    ) { onDone(list.orEmpty().map { it.pkg }.filter { it in picked }) }
}

private fun defaultOnHome(app: TvApp) = !app.system || PopularHome.any { it in app.pkg }

@Composable
private fun StatCard(title: String, freeKb: Long?, totalKb: Long?, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = Section)
            Spacer(Modifier.height(10.dp))
            if (freeKb == null || totalKb == null || totalKb == 0L) {
                Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.Accent, strokeWidth = 2.dp) }
            } else {
                Ring(freeKb, totalKb, 110.dp)
            }
            Spacer(Modifier.height(8.dp))
            Text(if (totalKb != null && totalKb > 0) "${gb(totalKb)} total" else " ", style = Small)
        }
    }
}

@Composable
private fun Ring(freeKb: Long, totalKb: Long, size: Dp) {
    val fraction by animateFloatAsState(freeKb.toFloat() / totalKb, label = "ring")
    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = 9.dp.toPx()
            val inset = w / 2
            val arc = androidx.compose.ui.geometry.Size(this.size.width - w, this.size.height - w)
            val tl = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(C.Border, 0f, 360f, false, tl, arc, style = Stroke(w))
            drawArc(C.Accent, -90f, 360f * fraction, false, tl, arc, style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(gb(freeKb), style = Body.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold))
            Text("free", style = Small)
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

private fun gb(kb: Long) = String.format(Locale.US, "%.1f GB", kb / 1024.0 / 1024.0)
