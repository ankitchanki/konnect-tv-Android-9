package io.konnecttv.plus

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import io.konnecttv.plus.adb.installationCommand
import io.konnecttv.plus.adb.parseShellResult
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.konnecttv.plus.data.DebloatList
import io.konnecttv.plus.data.Group
import io.konnecttv.plus.data.TvApp
import io.konnecttv.plus.data.TvIdentity
import io.konnecttv.plus.diagnostics.DiagnosticLog
import io.konnecttv.plus.ui.DetoxScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PlusDiagnosticsTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun firstLaunchLogsWorkWithoutATvAndPersistRedactedErrors() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("io.konnecttv.plus", context.packageName)
        assertEquals("Konnect TV Plus", context.applicationInfo.loadLabel(context.packageManager).toString())
        DiagnosticLog.setEnabled(true)
        DiagnosticLog.clear()
        DiagnosticLog.record("ERROR", "Install", "INSTALL_FAILED_OLDER_SDK endpoint 192.168.10.20 token=PRIVATE_VALUE pairingCode=123456")
        val persisted = File(context.filesDir, "diagnostics/current.jsonl").readText()
        assertTrue(persisted.contains("INSTALL_FAILED_OLDER_SDK"))
        for (secret in listOf("192.168.10.20", "PRIVATE_VALUE", "123456")) assertFalse(persisted, persisted.contains(secret))
        assertTrue(DiagnosticLog.report().contains("INSTALL_FAILED_OLDER_SDK"))
        compose.onNodeWithText("Settings / logs").performClick()
        compose.onNodeWithText("Diagnostic logs").performClick()
        compose.onNodeWithText("Export logs").assertIsDisplayed()
        compose.onNodeWithText("Collect TV diagnostics").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("Errors / warnings").performScrollTo().performClick()
        compose.onNodeWithText("INSTALL_FAILED_OLDER_SDK", substring = true).performScrollTo().assertIsDisplayed()
        screenshot("diagnostic-logs.png")
        DiagnosticLog.setEnabled(false)
        DiagnosticLog.record("ERROR", "Test", "MUST_NOT_BE_RECORDED")
        assertFalse(DiagnosticLog.report().contains("MUST_NOT_BE_RECORDED"))
        DiagnosticLog.setEnabled(true)
    }

    @Test fun alreadyDisabledAndOwnerRemovedAppsOfferRecoveryWithoutHistory() {
        val vm = ViewModelProvider(compose.activity)[TvViewModel::class.java]
        compose.runOnIdle {
            vm.apps.value = listOf(
                TvApp("com.tcl.bi", "TCL analytics", true, true),
                TvApp("com.tcl.gallery", "TCL gallery", true, true, installed = false),
            )
            vm.identity.value = TvIdentity("TCL", "Test TV", "9", 28, "TCL")
            compose.activity.setContent { DetoxScreen(vm) }
        }
        compose.onNodeWithText("TCL · Test TV · Android 9").assertIsDisplayed()
        compose.onNodeWithText("ALREADY OFF · 2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Already disabled / off").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Enable").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Restore original app").performScrollTo().assertIsDisplayed()
        // UI fixture only: commands must remain bound to a real authorized connection.
        assertTrue(vm.history.value.isEmpty())
        screenshot("already-off.png")
    }

    @Test fun tvPackageManagerReturnsActualInstallFailureAndExitCode() {
        val marker = "__PLUS_TEST_INSTALL__"
        val command = "printf invalid | sh -c '${installationCommand(7, marker)}'"
        val raw = ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ).bufferedReader().use { it.readText() }
        val result = parseShellResult(raw, marker)
        assertNotEquals(raw, 0, result.exitCode)
        assertFalse(raw, result.output.lines().any { it.trim() == "Success" })
        assertTrue(raw, result.output.contains("Failure", true) || result.output.contains("Error", true))
        DiagnosticLog.record("ERROR", "Install", "Expected invalid test APK response: ${result.output}")
        assertTrue(DiagnosticLog.report().contains("Expected invalid test APK response"))
    }

    @Test fun shippedCatalogLocksTvInputsAndLeavesNewVendorRulesUnticked() {
        val rules = DebloatList.load(ApplicationProvider.getApplicationContext())
        for (pkg in listOf("com.tcl.tv", "com.tcl.tvweishi", "com.tcl.guard", "com.tcl.ttvs", "org.droidtv.fastip", "com.google.android.gms")) {
            val app = TvApp(pkg, pkg, true, false)
            assertEquals(pkg, Group.NEEDED, rules.group(app))
            assertFalse(pkg, rules.canDisable(app))
            assertTrue(pkg, rules.reason(app).isNotBlank())
        }
        assertTrue(rules.safe.values.count { it.brand == "TCL" } >= 15)
        for (entry in rules.safe.values.filter { it.source.isNotBlank() }) {
            assertTrue(entry.pkg, entry.optional)
            assertTrue(entry.pkg, entry.reason.length > 30)
            assertTrue(entry.source, entry.source.startsWith("https://github.com/"))
        }
        assertEquals(Group.REVIEW, rules.group(TvApp("com.unknown.tvservice", "Unknown", true, false)))
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val file = File(instrumentation.targetContext.getExternalFilesDir(null), name)
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            try { file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
            finally { bitmap.recycle() }
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
                "cp ${file.path} /data/local/tmp/konnecttv-$name"
            )).use { it.readBytes() }
        }
    }

}
