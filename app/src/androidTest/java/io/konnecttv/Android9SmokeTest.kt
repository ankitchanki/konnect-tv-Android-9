package io.konnecttv

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.lifecycle.ViewModelProvider
import io.konnecttv.adb.Adb
import io.konnecttv.data.SavedTv
import io.konnecttv.data.Store
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import io.konnecttv.ui.WallpaperEditor
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Run on API 28 to exercise the legacy discovery and solid-surface UI paths. */
@RunWith(AndroidJUnit4::class)
class Android9SmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun adbClientCanInitializeOnAndroid9() {
        Adb(ApplicationProvider.getApplicationContext<Context>()).use {
            assertFalse(it.isConnected)
        }
    }

    @Test fun connectionSheetHandlesInvalidPortsOnAndroid9() {
        compose.onNodeWithText("Connect a TV").assertIsDisplayed()
        compose.onNodeWithText("Enter IP address manually").performClick()
        compose.onNodeWithText("Connect by IP").assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("192.168.1.24")
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("99999999999999999999")
        compose.onNodeWithText("Connect", useUnmergedTree = false).assertIsNotEnabled()
        compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("5555")
        compose.onNodeWithText("Connect", useUnmergedTree = false).assertIsEnabled()
        compose.onNodeWithText("TV name (optional), e.g. Living room").assertExists()
        compose.onNodeWithText("Tag (optional), e.g. Friend's TV").assertExists()
    }

    @Test fun wallpaperEditorOffersFitAndCropOnAndroid9() {
        val bitmap = Bitmap.createBitmap(600, 900, Bitmap.Config.ARGB_8888)
        android.graphics.Canvas(bitmap).apply {
            val paint = android.graphics.Paint().apply {
                shader = android.graphics.LinearGradient(0f, 0f, 0f, 900f,
                    intArrayOf(0xFF426E9B.toInt(), 0xFFE7AC87.toInt(), 0xFF305B54.toInt()), null, android.graphics.Shader.TileMode.CLAMP)
            }
            drawRect(0f, 0f, 600f, 900f, paint)
        }
        var cancelled = false
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                WallpaperEditor(WallpaperDraft(bitmap, "test", TvScreen(), true), false, { cancelled = true }, onApply = {})
            }
        }
        compose.onNodeWithText("TV wallpaper").assertIsDisplayed()
        compose.onNodeWithText("Fit whole image").performClick()
        saveScreenshot("wallpaper-editor.png")
        compose.onNodeWithText("Fill / crop").performClick()
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(cancelled) }
    }

    @Test fun savedTvCanBeNamedTaggedAndReusedWithoutTypingTheIp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = Store(context)
        val vm = ViewModelProvider(compose.activity)[TvViewModel::class.java]
        val tv = SavedTv("ui-smoke-tv", "Bedroom", "192.168.1.24", 5555, tag = "Home")
        try {
            compose.runOnIdle { store.saveTv(tv); vm.saved.value = store.tvs() }
            compose.onNodeWithText("Bedroom").assertIsDisplayed()
            compose.onNodeWithText("Edit").performClick()
            compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("Living room")
            compose.onAllNodes(hasSetTextAction())[1].performTextReplacement("Friend's TV")
            compose.onNodeWithText("Save TV").performScrollTo().performClick()
            compose.onNodeWithText("Living room").assertIsDisplayed()
            compose.onNodeWithText("Friend's TV").assertIsDisplayed()
            saveScreenshot("saved-tvs.png")
            compose.onNodeWithText("Enter IP address manually").performScrollTo().performClick()
            compose.onNodeWithText("Use last IP · Living room").performClick()
            compose.onAllNodes(hasSetTextAction())[0].assertTextEquals("192.168.1.24")
            compose.onAllNodes(hasSetTextAction())[3].assertTextEquals("Friend's TV")
        } finally { compose.runOnIdle { vm.forget(tv.id) } }
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), name)
        instrumentation.uiAutomation.takeScreenshot()?.let { image ->
            try { file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) } }
            finally { image.recycle() }
            // AGP uninstalls test apps after the suite; keep previews outside app storage before that cleanup.
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("cp ${file.path} /data/local/tmp/konnecttv-$name")
            ).use { it.readBytes() }
        }
    }
}
