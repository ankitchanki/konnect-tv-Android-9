package io.konnecttv

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Exercises the real API-28 `content write` binary pipe, launcher persistence and caller restriction. */
@RunWith(AndroidJUnit4::class)
class WallpaperTransferTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val authority = "content://io.konnecttv.glowlauncher.wallpaper"

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }.also { android.util.Log.i("WallpaperTest", "$command: $it") }

    @Test fun wallpaperTransferIsVerifiedAndOtherAppsCannotWriteIt() {
        val context = instrumentation.targetContext
        val bitmap = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        val image = File(context.cacheDir, "wallpaper-smoke.jpg")
        val script = File(context.getExternalFilesDir(null), "wallpaper-upload.sh")
        val draft = WallpaperDraft(bitmap, "test", TvScreen(1280, 720), true)
        try {
            exportWallpaper(draft, WallpaperCrop(dim = 0f), image)
            val hash = MessageDigest.getInstance("SHA-256").digest(image.readBytes()).joinToString("") { "%02x".format(it) }
            val status = shell("content call --uri $authority --method status")
            assertTrue(status, status.contains("status=ready"))
            // UiAutomation tokenizes arguments without shell quote handling. Use a real script for the pipe.
            script.writeText("run-as io.konnecttv cat cache/wallpaper-smoke.jpg | head -c ${image.length()} | content write --uri $authority/wallpaper\n")
            shell("sh ${script.path}")
            val committed = shell("content call --uri $authority --method commit --arg $hash")
            assertTrue(committed, committed.contains("status=ok"))
            shell("am start -W -n io.konnecttv.glowlauncher/.HomeActivity")
            assertWallpaperIsRed(true)
            // A force-stop and new launch must retain the background.
            shell("am force-stop io.konnecttv.glowlauncher")
            shell("am start -W -n io.konnecttv.glowlauncher/.HomeActivity")
            assertWallpaperIsRed(true)
            shell("sh ${script.path}")
            assertFalse(shell("content call --uri $authority --method commit --arg ${"0".repeat(64)}").contains("status=ok"))
            assertWallpaperIsRed(true)
            try {
                context.contentResolver.call(Uri.parse(authority), "clear", null, null)
                fail("An ordinary app must not be allowed to change TV wallpaper")
            } catch (_: SecurityException) { /* expected: only authorized ADB shell has access */ }
            assertTrue(shell("content call --uri $authority --method clear").contains("status=ok"))
            shell("am start -W -n io.konnecttv.glowlauncher/.HomeActivity")
            assertWallpaperIsRed(false)
        } finally {
            shell("content call --uri $authority --method clear")
            shell("am force-stop io.konnecttv.glowlauncher")
            bitmap.recycle()
            image.delete()
            script.delete()
        }
    }

    private fun assertWallpaperIsRed(expected: Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8000
        do {
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            val red = screenshot?.let {
                val p = it.getPixel(it.width / 5, it.height / 5)
                val result = Color.red(p) > 200 && Color.green(p) < 40 && Color.blue(p) < 40
                it.recycle()
                result
            }
            if (red == expected) return
            SystemClock.sleep(200)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("Launcher wallpaper red=$expected was not displayed")
    }
}
