package io.konnecttv.plus

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.konnecttv.plus.companion.*
import io.konnecttv.plus.ui.C
import io.konnecttv.plus.ui.CompanionReplacementSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real, differently signed APK; production certificate inspection, confirmation and installer. */
@RunWith(AndroidJUnit4::class)
class CompanionReplacementTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private fun raw(command: String) = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
    private fun shell(command: String): String {
        val script=File(context.getExternalFilesDir(null),"replacement-command.sh")
        script.writeText("$command\nrc=\$?\necho __RESULT__\$rc\n")
        val result=raw("sh ${script.path}")
        val code=result.substringAfterLast("__RESULT__").trim().toIntOrNull()
        val output=result.substringBeforeLast("__RESULT__").trim()
        if(code!=0 && command!="pm path $COMPANION_PACKAGE") error("$command failed: $output")
        return output
    }
    private val transport get() = object: CompanionTransport {
        override fun shell(command: String)=this@CompanionReplacementTest.shell(command)
        override fun copyApk(remotePath: String, destination: File) {
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("cat $remotePath")).use { input -> destination.outputStream().use { input.copyTo(it) } }
        }
        override fun install(apk: File) {
            val staged=File(context.getExternalFilesDir(null),"replacement-stage.apk")
            apk.copyTo(staged,true)
            shell("cp ${staged.path} /data/local/tmp/replacement-stage.apk")
            val result=shell("pm install -r /data/local/tmp/replacement-stage.apk")
            check(result=="Success") { result }
        }
    }
    @Test fun cancelKeepsForeignBuildAndReplaceContinuesToWorkingMediaProvider() {
        val foreign=File(context.cacheDir,"foreign-fixture.apk")
        val bundled=File(context.cacheDir,"bundled-fixture.apk")
        instrumentation.context.assets.open("foreigncompanion-debug.apk").use { input -> foreign.outputStream().use { input.copyTo(it) } }
        context.assets.open("glowlauncher.apk").use { input -> bundled.outputStream().use { input.copyTo(it) } }
        val consent=ReplacementConsent()
        val gate=Mutex()
        var removals=0
        var replacements=0
        val installer=CompanionInstaller(context,transport,gate,{consent.request(it)},
            beforeRemoval={restoreStockHome(transport)},removed={removals++},replaced={replacements++})
        assertNotEquals(installer.identity(foreign),installer.identity(bundled))
        try {
            shell("pm uninstall $COMPANION_PACKAGE")
            transport.install(foreign)
            shell("cmd package set-home-activity io.konnecttv.glowlauncher/io.konnecttv.fixture.ForeignHomeActivity")
            compose.activity.runOnUiThread {
                compose.activity.setContent { Box(Modifier.fillMaxSize().background(C.Bg)) { CompanionReplacementSheet(consent) } }
            }
            fun start(): Pair<CountDownLatch,AtomicReference<Throwable?>> {
                val done=CountDownLatch(1);val failure=AtomicReference<Throwable?>()
                compose.activity.lifecycleScope.launch(Dispatchers.IO) {
                    try { installer.install("Test Android TV") } catch(e:Throwable) { failure.set(e) } finally { done.countDown() }
                }
                return done to failure
            }
            val cancelled=start()
            compose.waitUntil(30000) { consent.pending.value!=null }
            compose.onNodeWithText("Replace older Glowlauncher?").assertIsDisplayed()
            compose.onNodeWithText("Cancel").performClick()
            assertTrue(cancelled.first.await(30,TimeUnit.SECONDS))
            assertTrue(cancelled.second.get() is ReplacementCancelled)
            assertEquals(0,removals)
            assertTrue(shell("dumpsys package $COMPANION_PACKAGE").contains("versionName=foreign-test-only"))
            assertTrue(shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME").contains("ForeignHomeActivity"))
            val replaced=start()
            compose.waitUntil(30000) { consent.pending.value!=null }
            compose.onNodeWithText("Replace Glowlauncher").assertIsDisplayed()
            compose.waitForIdle()
            val screenshot=File(context.getExternalFilesDir(null),"companion-replacement.png")
            instrumentation.uiAutomation.takeScreenshot()?.let { image -> try { screenshot.outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { image.recycle() } }
            shell("cp ${screenshot.path} /data/local/tmp/konnecttv-companion-replacement.png")
            compose.onNodeWithText("Replace Glowlauncher").performClick()
            assertTrue("Replacement timed out",replaced.first.await(90,TimeUnit.SECONDS))
            replaced.second.get()?.let { throw AssertionError("Replacement failed",it) }
            assertEquals(1,removals);assertEquals(1,replacements)
            assertFalse(shell("cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME").contains(COMPANION_PACKAGE))
            val actual=File(context.cacheDir,"actually-installed.apk")
            transport.copyApk(checkNotNull(installedApkPath(shell("pm path $COMPANION_PACKAGE"))),actual)
            assertEquals(installer.identity(bundled),installer.identity(actual));actual.delete()
            // Continue the original Enable media sliders action against the replacement, not a mock.
            shell("cmd notification allow_listener $COMPANION_PACKAGE/.MediaAccessService 0")
            var state:RemoteMedia?=null
            val deadline=SystemClock.uptimeMillis()+10000
            do { state=parseRemoteMedia(shell("content call --uri content://$COMPANION_PACKAGE.media --method status"),SystemClock.elapsedRealtime());if(!state.needsAccess) break;SystemClock.sleep(200) } while(SystemClock.uptimeMillis()<deadline)
            assertFalse("Bundled companion's media provider did not answer with granted access",checkNotNull(state).needsAccess)
        } finally {
            runCatching { shell("pm uninstall $COMPANION_PACKAGE") }
            transport.install(bundled)
            shell("am start -W -n io.konnecttv.plus/.MainActivity")
            foreign.delete();bundled.delete()
        }
    }
}
