package io.konnecttv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** Mono matte: solid dark surfaces, frosted glass only on things that float. */
object C {
    val Bg = Color(0xFF0C0C0F)
    val Card = Color(0xFF16161B)
    val Border = Color(0xFF222229)
    val Raised = Color(0xFF1F1F26)
    val RaisedBorder = Color(0xFF2A2A33)
    val Text = Color(0xFFECEEF4)
    val Muted = Color(0xFF8A8D99)
    val Accent = Color(0xFF7CF0B0)
    val OnAccent = Color(0xFF06140C)
    val Danger = Color(0xFFFF7A7A)
}

val LocalHaze = staticCompositionLocalOf { HazeState() }

private val GlassStyle = HazeStyle(
    backgroundColor = C.Bg,
    tints = listOf(HazeTint(Color(0x991E1E24))),
    blurRadius = 24.dp,
    noiseFactor = 0.04f,
)

/** Frosted glass: blur of whatever scrolls underneath, a hairline edge. */
@Composable
fun Modifier.glass(shape: RoundedCornerShape): Modifier = this
    .clip(shape)
    .hazeEffect(LocalHaze.current, GlassStyle)
    .border(1.dp, Color(0x14FFFFFF), shape)

val H1 = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = C.Text)
val Body = TextStyle(fontSize = 14.sp, color = C.Text)
val Small = TextStyle(fontSize = 12.sp, color = C.Muted)
val Section = TextStyle(fontSize = 11.sp, color = C.Muted, letterSpacing = 0.8.sp, fontWeight = FontWeight.Medium)

@Composable
fun Card(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(C.Card)
            .border(1.dp, C.Border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) C.Accent else C.Raised)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Body.copy(fontWeight = FontWeight.SemiBold, color = if (enabled) C.OnAccent else C.Muted)) }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, color: Color = C.Text, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .background(C.Raised)
            .border(1.dp, C.RaisedBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Body.copy(color = color)) }
}

@Composable
fun Toggle(on: Boolean, onChange: (Boolean) -> Unit) {
    val track by animateColorAsState(if (on) C.Accent else C.RaisedBorder, label = "track")
    val x by animateDpAsState(if (on) 20.dp else 2.dp, label = "thumb")
    Box(
        Modifier
            .size(44.dp, 26.dp)
            .clip(CircleShape)
            .background(track)
            .clickable { onChange(!on) },
    ) {
        Box(
            Modifier
                .offset(x = x, y = 3.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (on) C.OnAccent else Color(0xFF777777)),
        )
    }
}

@Composable
fun Check(on: Boolean, enabled: Boolean = true) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .size(20.dp)
            .clip(shape)
            .background(if (on) C.Accent else Color.Transparent)
            .border(1.5.dp, if (on) C.Accent else if (enabled) Color(0xFF555555) else C.Border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Canvas(Modifier.size(12.dp)) {
            val p = Path().apply { moveTo(size.width * .1f, size.height * .55f); lineTo(size.width * .4f, size.height * .85f); lineTo(size.width * .92f, size.height * .2f) }
            drawPath(p, C.OnAccent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** A letter tile, since ADB can't fetch app icons without pulling every APK. */
@Composable
fun Monogram(label: String, size: Dp = 36.dp) {
    val hue = remember(label) { (label.hashCode() and 0x7fffffff) % 360 }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(11.dp))
            .background(Color.hsl(hue.toFloat(), 0.25f, 0.22f)),
        contentAlignment = Alignment.Center,
    ) { Text(label.take(1).uppercase(), style = Body.copy(fontWeight = FontWeight.SemiBold, color = Color.hsl(hue.toFloat(), 0.6f, 0.8f))) }
}

class Sheet(val onDismiss: () -> Unit, val content: @Composable ColumnScope.() -> Unit)

/** Holds the one open sheet. Screens live inside the blur source, so their sheets draw at the root. */
class SheetHost {
    var sheet by mutableStateOf<Sheet?>(null)
}

val LocalSheetHost = staticCompositionLocalOf { SheetHost() }

/** Opens a frosted bottom sheet while [visible]; rendered by [SheetOverlay]. */
@Composable
fun GlassSheet(visible: Boolean, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    if (!visible) return
    val host = LocalSheetHost.current
    val latestDismiss by rememberUpdatedState(onDismiss)
    val latestContent by rememberUpdatedState(content)
    DisposableEffect(host) {
        val sheet = Sheet({ latestDismiss() }) { latestContent(this) }
        host.sheet = sheet
        onDispose { if (host.sheet === sheet) host.sheet = null }
    }
}

/** The frosted sheet itself: slides up from the bottom, in the same window so the blur works. */
@Composable
fun BoxScope.SheetOverlay(host: SheetHost) {
    val current = host.sheet
    // Keep drawing the last sheet while it slides away.
    var last by remember { mutableStateOf<Sheet?>(null) }
    if (current != null) last = current
    val visible = current != null
    val onDismiss = { current?.onDismiss?.invoke(); Unit }
    if (visible) BackHandler(onBack = onDismiss)
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .glass(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                // Rides up with the keyboard so text fields stay visible.
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 18.dp)
                .padding(top = 10.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(CircleShape).background(Color(0xFF555555)))
            last?.content?.invoke(this)
        }
    }
}

// ---------- tab icons, drawn so the app needs no icon library ----------

enum class Tab(val title: String) { Remote("Remote"), Apps("Apps"), Detox("Detox"), Boost("Boost") }

@Composable
fun TabIcon(tab: Tab, color: Color, modifier: Modifier = Modifier.size(22.dp)) = Canvas(modifier) {
    val s = size.minDimension
    val stroke = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round)
    when (tab) {
        Tab.Remote -> {
            drawRoundRect(color, Offset(s * .28f, s * .06f), Size(s * .44f, s * .88f), androidx.compose.ui.geometry.CornerRadius(s * .14f), style = stroke)
            drawCircle(color, s * .09f, Offset(s * .5f, s * .32f), style = stroke)
            drawCircle(color, s * .04f, Offset(s * .5f, s * .62f))
        }
        Tab.Apps -> {
            val g = s * .38f
            for (i in 0..1) for (j in 0..1) drawRoundRect(
                color, Offset(s * .08f + i * (g + s * .08f), s * .08f + j * (g + s * .08f)), Size(g, g),
                androidx.compose.ui.geometry.CornerRadius(s * .1f), style = stroke,
            )
        }
        Tab.Detox -> {
            drawCircle(color, s * .4f, style = stroke)
            drawLine(color, Offset(s * .22f, s * .22f), Offset(s * .78f, s * .78f), stroke.width, StrokeCap.Round)
        }
        Tab.Boost -> {
            val p = Path().apply {
                moveTo(s * .58f, s * .04f); lineTo(s * .2f, s * .56f); lineTo(s * .48f, s * .56f)
                lineTo(s * .4f, s * .96f); lineTo(s * .8f, s * .42f); lineTo(s * .52f, s * .42f); close()
            }
            drawPath(p, color, style = Stroke(1.6.dp.toPx(), join = androidx.compose.ui.graphics.StrokeJoin.Round))
        }
    }
}

@Composable
fun PowerIcon(color: Color, modifier: Modifier = Modifier.size(18.dp)) = Canvas(modifier) {
    val s = size.minDimension
    val w = 1.8.dp.toPx()
    drawArc(color, -60f, 300f, false, Offset(s * .12f, s * .16f), Size(s * .76f, s * .76f), style = Stroke(w, cap = StrokeCap.Round))
    drawLine(color, Offset(s * .5f, s * .04f), Offset(s * .5f, s * .46f), w, StrokeCap.Round)
}
