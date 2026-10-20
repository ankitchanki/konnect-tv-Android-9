package io.konnecttv.ui

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Mono matte: solid surfaces, frosted glass only on things that float. Dark by default; [light]
 * is Compose state, so flipping it recolours every screen.
 */
object C {
    var light by mutableStateOf(false)
    private fun pick(dark: Long, light: Long) = Color(if (this.light) light else dark)

    val Bg get() = pick(0xFF0C0C0F, 0xFFF4F5F7)
    val Card get() = pick(0xFF16161B, 0xFFFFFFFF)
    val Border get() = pick(0xFF222229, 0xFFE3E5EA)
    val Raised get() = pick(0xFF1F1F26, 0xFFEEF0F3)
    val RaisedBorder get() = pick(0xFF2A2A33, 0xFFDADDE3)
    val Text get() = pick(0xFFECEEF4, 0xFF14151A)
    val Muted get() = pick(0xFF8A8D99, 0xFF6B6F7B)
    val Accent get() = pick(0xFF7CF0B0, 0xFF12B76A)
    val OnAccent get() = pick(0xFF06140C, 0xFFFFFFFF)
    val Danger get() = pick(0xFFFF7A7A, 0xFFE5484D)
    val GlassTint get() = pick(0x991E1E24, 0xB3FFFFFF)
    val Hairline get() = pick(0x14FFFFFF, 0x14000000)
    val Knob get() = pick(0xFF777777, 0xFFB0B4BD)
    val Handle get() = pick(0xFF555555, 0xFFC4C7CE)
    val Scrim get() = pick(0x99000000, 0x66000000)
    val OrbOn get() = pick(0xFF10201A, 0xFFE3F8EE)
}

val LocalHaze = staticCompositionLocalOf { HazeState() }

private val GlassStyle get() = HazeStyle(
    backgroundColor = C.Bg,
    tints = listOf(HazeTint(C.GlassTint)),
    blurRadius = 24.dp,
    noiseFactor = 0.04f,
)

/** Android 12+ gets backdrop blur; Android 9–11 gets an opaque, readable surface. */
@Composable
fun Modifier.glass(shape: RoundedCornerShape): Modifier {
    val surface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.hazeEffect(LocalHaze.current, GlassStyle)
    } else {
        Modifier.background(C.Card)
    }
    return this.clip(shape).then(surface).border(1.dp, C.Hairline, shape)
}

/**
 * Wraps the press highlight so every clickable also ticks the phone's haptics on press.
 * One place covers every button, card, key and toggle in the app.
 */
class HapticIndication(private val inner: IndicationNodeFactory) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode =
        object : DelegatingNode(), CompositionLocalConsumerModifierNode {
            init { delegate(inner.create(interactionSource)) }

            override fun onAttach() {
                // Undispatched so the first press isn't missed: interactions don't replay.
                coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
                    interactionSource.interactions.collect {
                        if (it is PressInteraction.Press) currentValueOf(LocalHapticFeedback).performHapticFeedback(HapticFeedbackType.VirtualKey)
                    }
                }
            }
        }

    override fun equals(other: Any?) = other is HapticIndication && other.inner == inner
    override fun hashCode() = inner.hashCode()
}

val H1 get() = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = C.Text)
val Body get() = TextStyle(fontSize = 14.sp, color = C.Text)
val Small get() = TextStyle(fontSize = 12.sp, color = C.Muted)
val Section get() = TextStyle(fontSize = 11.sp, color = C.Muted, letterSpacing = 0.8.sp, fontWeight = FontWeight.Medium)

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
                .background(if (on) C.OnAccent else C.Knob),
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
            .border(1.5.dp, if (on) C.Accent else if (enabled) C.Handle else C.Border, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Canvas(Modifier.size(12.dp)) {
            val p = Path().apply { moveTo(size.width * .1f, size.height * .55f); lineTo(size.width * .4f, size.height * .85f); lineTo(size.width * .92f, size.height * .2f) }
            drawPath(p, C.OnAccent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

/** The app's real icon from the TV, or its letter tile until that arrives (or if it has none). */
@Composable
fun AppIcon(label: String, icon: ImageBitmap?, size: Dp = 36.dp) {
    if (icon == null) Monogram(label, size)
    else Image(icon, null, Modifier.size(size).clip(RoundedCornerShape(11.dp)))
}

/** A letter tile, for TVs and for apps whose icon hasn't loaded. */
@Composable
fun Monogram(label: String, size: Dp = 36.dp) {
    val hue = remember(label) { (label.hashCode() and 0x7fffffff) % 360 }
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(11.dp))
            .background(Color.hsl(hue.toFloat(), if (C.light) 0.45f else 0.25f, if (C.light) 0.9f else 0.22f)),
        contentAlignment = Alignment.Center,
    ) { Text(label.take(1).uppercase(), style = Body.copy(fontWeight = FontWeight.SemiBold, color = Color.hsl(hue.toFloat(), 0.6f, if (C.light) 0.35f else 0.8f))) }
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
                .background(C.Scrim)
                .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        )
    }
    AnimatedVisibility(
        visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
    ) {
        // Swipe down to close: follows the finger, closes past a threshold or on a quick flick.
        val drag = remember(last) { Animatable(0f) }
        val scope = rememberCoroutineScope()
        val closeAt = with(LocalDensity.current) { 110.dp.toPx() }
        Column(
            Modifier
                .offset { IntOffset(0, drag.value.roundToInt()) }
                .draggable(
                    rememberDraggableState { delta -> scope.launch { drag.snapTo((drag.value + delta).coerceAtLeast(0f)) } },
                    Orientation.Vertical,
                    onDragStopped = { velocity -> if (drag.value > closeAt || velocity > 1800f) onDismiss() else drag.animateTo(0f) },
                )
                .fillMaxWidth()
                .glass(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clickable(remember { MutableInteractionSource() }, indication = null) {}
                // Rides up with the keyboard so text fields stay visible.
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                .padding(horizontal = 18.dp)
                .padding(top = 10.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).width(36.dp).height(4.dp).clip(CircleShape).background(C.Handle))
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
