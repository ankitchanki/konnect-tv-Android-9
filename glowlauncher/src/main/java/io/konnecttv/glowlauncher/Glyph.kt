package io.konnecttv.glowlauncher

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.cos
import kotlin.math.sin

/** Thin white line icons on a 24-unit grid, so the launcher ships no image files. */
class Glyph private constructor(private val shape: Path.() -> Unit) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        strokeWidth = 1.7f
        color = 0xEBFFFFFF.toInt()
    }
    private val path = Path().apply(shape)

    override fun draw(c: Canvas) {
        c.save()
        c.translate(bounds.left.toFloat(), bounds.top.toFloat())
        c.scale(bounds.width() / 24f, bounds.height() / 24f)
        c.drawPath(path, paint)
        c.restore()
    }

    override fun setAlpha(a: Int) { paint.alpha = a }
    override fun setColorFilter(cf: ColorFilter?) { paint.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
    override fun getIntrinsicWidth() = 48
    override fun getIntrinsicHeight() = 48

    companion object {
        private fun arc(p: Path, cx: Float, cy: Float, r: Float, start: Float, sweep: Float) =
            p.addArc(RectF(cx - r, cy - r, cx + r, cy + r), start, sweep)

        private fun Path.wifi() {
            arc(this, 12f, 19f, 4f, 225f, 90f)
            arc(this, 12f, 19f, 8f, 225f, 90f)
            arc(this, 12f, 19f, 12f, 228f, 84f)
            addCircle(12f, 19f, 0.4f, Path.Direction.CW)
        }

        val INPUT get() = Glyph {
            addRoundRect(RectF(3f, 5f, 21f, 17f), 2f, 2f, Path.Direction.CW)
            moveTo(9f, 20.5f); lineTo(15f, 20.5f)
            moveTo(9f, 11f); lineTo(14.5f, 11f); moveTo(12f, 8.5f); lineTo(14.5f, 11f); lineTo(12f, 13.5f)
        }
        val SETTINGS get() = Glyph {
            addCircle(12f, 12f, 2.8f, Path.Direction.CW)
            addCircle(12f, 12f, 6.4f, Path.Direction.CW)
            for (k in 0 until 8) {
                val a = Math.toRadians(k * 45.0)
                moveTo(12f + 6.4f * cos(a).toFloat(), 12f + 6.4f * sin(a).toFloat())
                lineTo(12f + 9f * cos(a).toFloat(), 12f + 9f * sin(a).toFloat())
            }
        }
        val WIFI get() = Glyph { wifi() }
        val OFFLINE get() = Glyph { wifi(); moveTo(4f, 4f); lineTo(20f, 20f) }
        val ETHERNET get() = Glyph {
            addRoundRect(RectF(5f, 8f, 19f, 17f), 1.5f, 1.5f, Path.Direction.CW)
            moveTo(12f, 3f); lineTo(12f, 8f)
            moveTo(9f, 17f); lineTo(9f, 21f); moveTo(15f, 17f); lineTo(15f, 21f)
        }
        val PLUS get() = Glyph { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }
    }
}
