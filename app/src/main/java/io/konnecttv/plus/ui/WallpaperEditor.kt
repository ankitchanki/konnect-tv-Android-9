package io.konnecttv.plus.ui

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.konnecttv.plus.WallpaperCrop
import io.konnecttv.plus.WallpaperDraft
import io.konnecttv.plus.wallpaperPlacement
import java.util.Locale
import kotlin.math.abs

private val CropSaver = listSaver<WallpaperCrop, Any>(
    save = { listOf(it.fit, it.zoom, it.x, it.y, it.dim) },
    restore = { WallpaperCrop(it[0] as Boolean, it[1] as Float, it[2] as Float, it[3] as Float, it[4] as Float) },
)

@Composable
fun WallpaperEditor(draft: WallpaperDraft, busy: Boolean, onDismiss: () -> Unit, onApply: (WallpaperCrop) -> Unit, error: String? = null) {
    var crop by rememberSaveable(draft, stateSaver = CropSaver) { mutableStateOf(WallpaperCrop()) }
    val paint = remember { Paint(Paint.FILTER_BITMAP_FLAG) }
    val sliderColors = SliderDefaults.colors(thumbColor = C.Accent, activeTrackColor = C.Accent, inactiveTrackColor = C.Border)
    Dialog(onDismissRequest = { if (!busy) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(C.Bg).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("TV wallpaper", style = H1)
            Text(if (draft.detected) "TV screen: ${draft.screen.width} × ${draft.screen.height}" else "Screen size unavailable; preview uses 16:9.", style = Small)
            Text("Pinch to zoom and drag to choose the visible area. Clock and dock are preview guides.", style = Small)
            Box(Modifier.fillMaxWidth().aspectRatio(draft.screen.width.toFloat() / draft.screen.height).clipToBounds().background(Color.Black)) {
                Canvas(Modifier.fillMaxSize().pointerInput(draft, busy) {
                    if (!busy) detectTransformGestures { _, pan, zoom, _ ->
                        val next = crop.copy(zoom = (crop.zoom * zoom).coerceIn(1f, 4f))
                        val p = wallpaperPlacement(draft.bitmap.width, draft.bitmap.height, size.width.toFloat(), size.height.toFloat(), next)
                        crop = next.copy(
                            x = if (abs(size.width - p.width) > 1f) (next.x + pan.x / (size.width - p.width)).coerceIn(0f, 1f) else next.x,
                            y = if (abs(size.height - p.height) > 1f) (next.y + pan.y / (size.height - p.height)).coerceIn(0f, 1f) else next.y,
                        )
                    }
                }) {
                    val p = wallpaperPlacement(draft.bitmap.width, draft.bitmap.height, size.width, size.height, crop)
                    drawContext.canvas.nativeCanvas.drawBitmap(draft.bitmap, null, RectF(p.left, p.top, p.left + p.width, p.top + p.height), paint)
                    drawRect(Color.Black.copy(alpha = crop.dim))
                }
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("9:41", style = Body.copy(color = Color.White, fontSize = 34.sp))
                    Text("Glowlauncher preview", style = Small.copy(color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp))
                    Spacer(Modifier.height(20.dp))
                    Text("▣    ▣    ▣    ▣    +", style = Body.copy(color = Color.White), modifier = Modifier.background(Color.Black.copy(alpha = 0.45f)).padding(8.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Chip("Fill / crop", !crop.fit) { if (!busy) crop = crop.copy(fit = false, zoom = 1f, x = 0.5f, y = 0.5f) }
                Chip("Fit whole image", crop.fit) { if (!busy) crop = crop.copy(fit = true, zoom = 1f, x = 0.5f, y = 0.5f) }
            }
            Text(String.format(Locale.US, "Zoom · %.1f×", crop.zoom), style = Body)
            Slider(crop.zoom, { crop = crop.copy(zoom = it) }, valueRange = 1f..4f, enabled = !busy, colors = sliderColors,
                modifier = Modifier.semantics { contentDescription = "Wallpaper zoom" })
            Text("Horizontal position", style = Small)
            Slider(crop.x, { crop = crop.copy(x = it) }, enabled = !busy, colors = sliderColors,
                modifier = Modifier.semantics { contentDescription = "Wallpaper horizontal position" })
            Text("Vertical position", style = Small)
            Slider(crop.y, { crop = crop.copy(y = it) }, enabled = !busy, colors = sliderColors,
                modifier = Modifier.semantics { contentDescription = "Wallpaper vertical position" })
            Text("Darken background · ${(crop.dim * 100).toInt()}%", style = Small)
            Slider(crop.dim, { crop = crop.copy(dim = it) }, valueRange = 0f..0.7f, enabled = !busy, colors = sliderColors,
                modifier = Modifier.semantics { contentDescription = "Wallpaper darkening" })
            Text("Optimized on this phone; sent directly to this TV over ADB. Fit keeps the whole photo with black bars where needed.", style = Small)
            error?.let { Text(it, style = Small.copy(color = C.Danger)) }
            PrimaryButton(if (busy) "Sending wallpaper…" else "Apply to TV", enabled = !busy) { onApply(crop) }
            SecondaryButton("Reset crop") { if (!busy) crop = WallpaperCrop() }
            SecondaryButton("Cancel") { if (!busy) onDismiss() }
        }
    }
}
