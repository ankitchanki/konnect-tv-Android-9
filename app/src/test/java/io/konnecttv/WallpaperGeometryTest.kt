package io.konnecttv

import org.junit.Assert.*
import org.junit.Test

class WallpaperGeometryTest {
    @Test fun fillCropsPortraitImageAndAllowsChoosingTopOrBottom() {
        val top = wallpaperPlacement(1000, 2000, 1920f, 1080f, WallpaperCrop(y = 0f))
        val bottom = wallpaperPlacement(1000, 2000, 1920f, 1080f, WallpaperCrop(y = 1f))
        assertEquals(1920f, top.width, 0.01f)
        assertEquals(3840f, top.height, 0.01f)
        assertEquals(0f, top.top, 0.01f)
        assertEquals(-2760f, bottom.top, 0.01f)
    }

    @Test fun fitKeepsEntireImageAndZoomCanFillTheBars() {
        val fit = wallpaperPlacement(1000, 1000, 1920f, 1080f, WallpaperCrop(fit = true))
        assertEquals(420f, fit.left, 0.01f)
        assertEquals(1080f, fit.height, 0.01f)
        val zoom = wallpaperPlacement(1000, 1000, 1920f, 1080f, WallpaperCrop(fit = true, zoom = 2f))
        assertTrue(zoom.width > 1920)
        assertTrue(zoom.height > 1080)
    }

    @Test fun screenUsesOverrideAndCaps4kWithoutChangingRatio() {
        assertEquals(TvScreen(1280, 720), parseTvScreen("Physical size: 3840x2160\nOverride size: 1280x720"))
        assertEquals(1920 to 1080, TvScreen(3840, 2160).output)
        assertEquals(1440 to 1080, TvScreen(1440, 1080).output)
        assertNull(parseTvScreen("Permission denied"))
        assertNull(parseTvScreen("Physical size: 99999999999999x1080"))
    }

    @Test fun cropCannotPanPastTheImageEdge() {
        val p = wallpaperPlacement(1920, 1080, 1920f, 1080f, WallpaperCrop(zoom = 2f, x = 5f, y = -5f))
        assertEquals(-1920f, p.left, 0.01f)
        assertEquals(0f, p.top, 0.01f)
    }

    @Test fun unusualAspectRatiosStayWithinTheTvMemoryBudget() {
        for (width in listOf(1366, 1900, 1920, 3440, 4096)) {
            for (height in listOf(768, 1081, 1900, 2160)) {
                val (w, h) = TvScreen(width, height).output
                assertTrue(w <= 1920 && h <= 1920 && w.toLong() * h <= 2_073_600)
            }
        }
    }
}
