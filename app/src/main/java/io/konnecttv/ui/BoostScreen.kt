package io.konnecttv.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.konnecttv.GLOW_ACCENTS
import io.konnecttv.LAUNCHER_STEPS
import io.konnecttv.RGB_GLOW
import io.konnecttv.LauncherSetup
import io.konnecttv.TabBarClearance
import io.konnecttv.TvViewModel
import io.konnecttv.data.SavedTv
import io.konnecttv.data.TvApp
import kotlinx.coroutines.delay
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
            Text("Glowlauncher", style = Body.copy(fontSize = 17.sp))
            Text("Active on ${tv.name}.", style = Small)
            PrimaryButton("Edit home apps") { launcherMenu = false; vm.loadHomeChoices(); pickHome = true }
            SecondaryButton("Turn off, bring back the TV's own home") { launcherMenu = false; vm.setLauncher(false) }
        }

        val setup by vm.launcherSetup.collectAsStateWithLifecycle()
        setup?.let { st ->
            // Can't be swiped away while the TV is switching: closing it mid-way hides the only sign that work is going on.
            GlassSheet(true, onDismiss = { if (st.done || st.error != null) vm.closeLauncherSetup() }) {
                SetupProgress(st) { vm.closeLauncherSetup() }
            }
        }

        GlassSheet(pickHome, onDismiss = { pickHome = false }) {
            HomePicker(vm, editing = launcherOn) { picked, accent ->
                pickHome = false
                if (launcherOn) vm.setHomeApps(picked, accent) else vm.setLauncher(true, picked, accent)
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
            Text("Glowlauncher", style = Body.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold))
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

/** Turning Glowlauncher on: every step as it happens, a running clock, and why the TV may look stuck. */
@Composable
private fun SetupProgress(st: LauncherSetup, onClose: () -> Unit) {
    val running = !st.done && st.error == null
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) { while (running) { now = System.currentTimeMillis(); delay(1000) } }
    val secs = ((now - st.startedAt) / 1000).coerceAtLeast(0)
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "angle")
    val glow by spin.animateFloat(0.15f, 0.4f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "glow")

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 5.dp.toPx()
                val inset = w / 2 + 2.dp.toPx()
                val arcSize = androidx.compose.ui.geometry.Size(size.width - inset * 2, size.height - inset * 2)
                val tl = androidx.compose.ui.geometry.Offset(inset, inset)
                val ok = st.error == null
                drawCircle(Brush.radialGradient(listOf((if (ok) Mint else C.Danger).copy(alpha = if (running) glow else 0.25f), Color.Transparent)), radius = size.minDimension / 2)
                drawArc(C.Border, 0f, 360f, false, tl, arcSize, style = Stroke(w))
                if (running) drawArc(Brush.sweepGradient(listOf(Color.Transparent, Mint, Cyan)), angle, 270f, false, tl, arcSize, style = Stroke(w, cap = StrokeCap.Round))
                else drawArc(if (ok) Mint else C.Danger, -90f, 360f, false, tl, arcSize, style = Stroke(w))
            }
            Glyph(Glyphs.Sparkle, if (st.error != null) C.Danger else C.Accent, 38.dp, Modifier.rotate(if (running) angle / 4 else 0f))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when { st.error != null -> "Didn't finish"; st.done -> "Glowlauncher is on"; else -> "Turning on Glowlauncher" },
            style = Body.copy(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
        )
        Text(String.format(Locale.US, "%d:%02d", secs / 60, secs % 60), style = Small.copy(fontFeatureSettings = "tnum"))
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LAUNCHER_STEPS.forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    when {
                        i < st.step -> Check(true)
                        i == st.step && st.error != null -> Text("!", style = Body.copy(color = C.Danger, fontWeight = FontWeight.Bold))
                        i == st.step -> CircularProgressIndicator(color = C.Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        else -> Box(Modifier.size(8.dp).clip(CircleShape).background(C.Border))
                    }
                }
                Spacer(Modifier.width(12.dp))
                Text(label, style = Body.copy(color = if (i <= st.step) C.Text else C.Muted))
            }
        }
    }
    when {
        st.error != null -> Text(st.error, style = Small.copy(color = C.Danger))
        st.done -> Text("Press Home on the remote to see it.", style = Small)
        else -> Text(
            "The TV can look frozen for up to 2 minutes while it switches home screens. That's normal. " +
                "Leave it on and don't unplug it.",
            style = Small.copy(color = C.Muted),
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(C.Raised).padding(12.dp),
        )
    }
    if (!running) PrimaryButton("Done", onClick = onClose)
}

/** Choose what the clean home shows. Preinstalled apps like Netflix and Prime are here too. */
@Composable
private fun HomePicker(vm: TvViewModel, editing: Boolean, onDone: (List<String>, Long) -> Unit) {
    val choices by vm.homeChoices.collectAsStateWithLifecycle()
    val icons by vm.icons.collectAsStateWithLifecycle()
    val list = choices
    var picked by remember(list) {
        val saved = vm.homePicks()
        mutableStateOf(list.orEmpty().filter { saved?.contains(it.pkg) ?: defaultOnHome(it) }.map { it.pkg }.toSet())
    }
    var accent by remember { mutableLongStateOf(vm.homeAccent()) }
    Text(if (editing) "Glowlauncher" else "Set up Glowlauncher", style = Body.copy(fontSize = 17.sp))
    GlowPreview(accent)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        GLOW_ACCENTS.forEach { g ->
            val on = g.rgb == accent
            val fill = if (g.rgb == RGB_GLOW) Modifier.background(Brush.sweepGradient(Rainbow)) else Modifier.background(Color(0xFF000000 or g.rgb))
            Box(
                Modifier.size(34.dp).clip(CircleShape).then(fill)
                    .border(if (on) 3.dp else 1.dp, if (on) C.Text else C.Border, CircleShape)
                    .clickable { accent = g.rgb },
            )
        }
    }
    Text("Glow: ${GLOW_ACCENTS.first { it.rgb == accent }.name}", style = Small)
    Text("Apps on your home", style = Section)
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
    ) { onDone(list.orEmpty().map { it.pkg }.filter { it in picked }, accent) }
}

private val Rainbow = listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)

/** A tiny Glowlauncher: the clock over a dock, lit in the picked colour (or fading through them all for RGB). */
@Composable
private fun GlowPreview(accent: Long) {
    val hue by rememberInfiniteTransition(label = "rgb").animateFloat(0f, 360f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "hue")
    val target = if (accent == RGB_GLOW) Color.hsv(hue, 0.65f, 1f) else Color(0xFF000000 or accent)
    val glow by animateColorAsState(target, tween(if (accent == RGB_GLOW) 0 else 400), label = "glow")
    Box(
        Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF08090B)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.35f), Color.Transparent), center = center.copy(y = size.height * 0.72f), radius = size.width * 0.38f))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("9:41", style = Body.copy(color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Light))
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.06f)).padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                repeat(5) { i ->
                    Box(
                        Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF16191E))
                            .then(if (i == 0) Modifier.border(2.dp, lerp(glow, Color.White, 0.5f), RoundedCornerShape(6.dp)) else Modifier),
                    )
                }
            }
        }
    }
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
