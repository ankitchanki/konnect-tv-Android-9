package io.konnecttv.plus

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

data class WallpaperDraft(val bitmap: Bitmap, val tvId: String, val screen: TvScreen, val detected: Boolean)

/** ImageDecoder (API 28+) applies camera EXIF rotation; software allocation also supports Canvas export. */
fun loadWallpaper(resolver: ContentResolver, uri: Uri): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, uri)) { decoder, info, _ ->
        require(info.size.width > 0 && info.size.height > 0) { "This image has no valid dimensions." }
        val scale = minOf(1.0, 2560.0 / max(info.size.width, info.size.height),
            kotlin.math.sqrt(3_000_000.0 / info.size.width / info.size.height))
        decoder.setTargetSize(max(1, (info.size.width * scale).roundToInt()), max(1, (info.size.height * scale).roundToInt()))
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = false
    }

fun exportWallpaper(draft: WallpaperDraft, crop: WallpaperCrop, file: File) {
    val (w, h) = draft.screen.output
    val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    try {
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        val p = wallpaperPlacement(draft.bitmap.width, draft.bitmap.height, w.toFloat(), h.toFloat(), crop)
        canvas.drawBitmap(draft.bitmap, null, RectF(p.left, p.top, p.left + p.width, p.top + p.height), Paint(Paint.FILTER_BITMAP_FLAG))
        canvas.drawColor(Color.argb((crop.dim.coerceIn(0f, 0.7f) * 255).roundToInt(), 0, 0, 0))
        file.outputStream().use { check(output.compress(Bitmap.CompressFormat.JPEG, 90, it)) { "Couldn't prepare the wallpaper." } }
    } finally { output.recycle() }
}
