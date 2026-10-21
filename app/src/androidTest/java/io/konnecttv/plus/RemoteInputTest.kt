package io.konnecttv.plus

import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Base64
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import io.konnecttv.plus.ui.SheetHost
import io.konnecttv.plus.ui.LocalSheetHost
import io.konnecttv.plus.ui.SheetOverlay
import android.app.UiAutomation
import android.graphics.Bitmap
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.konnecttv.plus.data.SavedTv
import io.konnecttv.plus.ui.C
import io.konnecttv.plus.ui.RemoteScreen
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RemoteInputTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val automation get() = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
    private fun shell(command:String)=ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText().trim() }
    private fun remote(method:String,data:JSONObject=JSONObject()):JSONObject {
        val arg=Base64.encodeToString(data.toString().toByteArray(),Base64.NO_WRAP)
        val raw=shell("content call --uri content://io.konnecttv.glowlauncher.remote --method $method --arg $arg")
        val encoded=Regex("PLUS_REMOTE=([A-Za-z0-9+/=]+)").find(raw)?.groupValues?.get(1) ?: error(raw)
        return JSONObject(String(Base64.decode(encoded,Base64.NO_WRAP)))
    }
    @Test fun setupFilesExcludeCredentialsAndValidateBeforeImport() {
        val original=SetupTransfer(listOf(SavedTv("tv1","Living room","192.168.1.10",5555,tag="Home")),false,true,true)
        val json=original.json();val parsed=SetupTransfer.parse(json)
        assertEquals(original,parsed)
        for(secret in listOf("adb.key","adb.crt","privateKey","history","pairingCode")) assertFalse(json.contains(secret))
        for(bad in listOf(json.replace("5555","70000"),json.replace("192.168.1.10","bad;reboot"),json.replace("\"version\": 1","\"version\": 9"),"x".repeat(32769))) {
            try { SetupTransfer.parse(bad);fail("Invalid import accepted") } catch(expected:Exception) { assertTrue(expected is IllegalArgumentException || expected is org.json.JSONException) }
        }
    }
    @Test fun hapticsPreferencePersistsAndCanBeTurnedOff() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val previous=RemotePreferences.haptics.value
        try {
            RemotePreferences.set("haptics",false)
            RemotePreferences.feedback(success=true)
            RemotePreferences.init(context)
            assertFalse(RemotePreferences.haptics.value)
        } finally { RemotePreferences.set("haptics",previous) }
    }
    @Test fun touchpadAndCompactModeKeepAnOkButtonAndCurrentShortcuts() {
        RemotePreferences.set("oneHanded",true);RemotePreferences.set("touchpad",true)
        try {
            val vm=ViewModelProvider(compose.activity)[TvViewModel::class.java]
            compose.runOnIdle { compose.activity.setContent { val host=remember { SheetHost() }
                CompositionLocalProvider(LocalSheetHost provides host) {
                    Box(Modifier.fillMaxSize().background(C.Bg).safeDrawingPadding()) {
                        RemoteScreen(vm,SavedTv("fixture","Living room TV","192.168.1.1",5555),active=false)
                        SheetOverlay(host)
                    }
                } } }
            compose.onNodeWithTag("remote-dpad").assertHeightIsEqualTo(220.dp)
            compose.onNodeWithText("OK").assertIsDisplayed()
            compose.onNodeWithText("Cloudstream").performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("JioHotstar").assertIsDisplayed()
            compose.onNodeWithText("Mic").assertIsDisplayed()
            screenshot("touchpad.png")
            compose.onNodeWithText("Controls").performScrollTo().performClick()
            compose.onNodeWithText("One-handed mode: on").assertExists()
        } finally { RemotePreferences.set("oneHanded",false);RemotePreferences.set("touchpad",false) }
    }
    @Test fun tvRemoteProviderRejectsOtherAppUids() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        try { context.contentResolver.call(android.net.Uri.parse("content://io.konnecttv.glowlauncher.remote"),"status",null,null);fail("Other app read TV input state") }
        catch(expected:SecurityException) { assertTrue(expected.message.orEmpty().contains("ADB shell")) }
    }
    @Test fun tvInputServiceMovesCursorDimsAndExpiresBubbleWithoutSavingMessage() {
        val key="enabled_accessibility_services"
        val previous=shell("settings get secure $key")
        val globallyEnabled=shell("settings get secure accessibility_enabled")
        val component="io.konnecttv.glowlauncher/.RemoteInputService"
        val enabled=withService(previous,component)
        shell("settings put secure $key $enabled")
        shell("settings put secure accessibility_enabled 1")
        try {
            val deadline=SystemClock.elapsedRealtime()+8000
            while(!remote("status").optBoolean("available") && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(200)
            assertTrue(remote("status").toString(),remote("status").optBoolean("available"))
            assertTrue(remote("move",JSONObject().put("dx",35).put("dy",20)).getBoolean("cursorVisible"))
            assertEquals(65,remote("dim",JSONObject().put("value",65)).getInt("brightness"))
            val bubble=remote("message",JSONObject().put("text","Hello from test phone"))
            assertTrue(bubble.getBoolean("bubbleVisible"));assertFalse(bubble.toString().contains("Hello from test phone"))
            val expiry=SystemClock.elapsedRealtime()+9000
            while(remote("status").optBoolean("bubbleVisible") && SystemClock.elapsedRealtime()<expiry) SystemClock.sleep(250)
            assertFalse("Bubble must expire",remote("status").getBoolean("bubbleVisible"))
            assertEquals(100,remote("dim",JSONObject().put("value",100)).getInt("brightness"))
            assertFalse(remote("hide").getBoolean("cursorVisible"))
        } finally {
            remote("dim",JSONObject().put("value",100));remote("hide")
            if(previous=="null" || previous.isBlank()) shell("settings delete secure $key") else shell("settings put secure $key $previous")
            if(globallyEnabled=="null") shell("settings delete secure accessibility_enabled") else shell("settings put secure accessibility_enabled $globallyEnabled")
        }
    }
    private fun screenshot(name:String) {
        compose.waitForIdle();instrumentation.waitForIdleSync();SystemClock.sleep(200)
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),name)
        val bitmap=requireNotNull(automation.takeScreenshot())
        try { file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } } finally { bitmap.recycle() }
        shell("cp ${file.path} /data/local/tmp/konnecttv-$name")
    }

}
