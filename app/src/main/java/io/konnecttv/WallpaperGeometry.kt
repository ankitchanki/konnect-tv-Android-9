package io.konnecttv

import kotlin.math.max
import kotlin.math.min

/** TV output is capped at 1080p-equivalent pixels to keep older launchers within a small RAM budget. */
data class TvScreen(val width: Int = 1920, val height: Int = 1080) {
    init { require(width > 0 && height > 0) }
    val output: Pair<Int, Int> get() {
        val scale = min(1.0, min(1920.0 / max(width, height), kotlin.math.sqrt(2_073_600.0 / width / height)))
        return max(1, (width * scale).toInt()) to max(1, (height * scale).toInt())
    }
}

internal fun parseTvScreen(output: String): TvScreen? =
    Regex("(?:Physical|Override) size:\\s*(\\d+)x(\\d+)").findAll(output).lastOrNull()?.let {
        val w = it.groupValues[1].toIntOrNull() ?: return null
        val h = it.groupValues[2].toIntOrNull() ?: return null
        if (w !in 240..16384 || h !in 240..16384) null else TvScreen(max(w, h), min(w, h))
    }

data class WallpaperCrop(
    val fit: Boolean = false,
    val zoom: Float = 1f,
    val x: Float = 0.5f,
    val y: Float = 0.5f,
    val dim: Float = 0.2f,
)

data class ImagePlacement(val left: Float, val top: Float, val width: Float, val height: Float)

/** Shared by preview and export: x/y select the visible part, or position the image within Fit bars. */
fun wallpaperPlacement(iw: Int, ih: Int, vw: Float, vh: Float, crop: WallpaperCrop): ImagePlacement {
    require(iw > 0 && ih > 0 && vw > 0 && vh > 0)
    val base = if (crop.fit) min(vw / iw, vh / ih) else max(vw / iw, vh / ih)
    val scale = base * crop.zoom.coerceIn(1f, 4f)
    val w = iw * scale
    val h = ih * scale
    return ImagePlacement((vw - w) * crop.x.coerceIn(0f, 1f), (vh - h) * crop.y.coerceIn(0f, 1f), w, h)
}
