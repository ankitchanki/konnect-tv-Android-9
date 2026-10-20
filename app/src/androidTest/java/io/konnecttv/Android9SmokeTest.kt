package io.konnecttv

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.core.app.ApplicationProvider
import io.konnecttv.adb.Adb
import org.junit.Assert.assertFalse
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
    }
}
