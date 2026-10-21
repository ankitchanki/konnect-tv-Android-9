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
    @Test fun touchpadDefaultsOnButExistingChoiceSurvives() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val prefs=context.getSharedPreferences("remote",Context.MODE_PRIVATE)
        val present=prefs.contains("touchpad");val old=prefs.getBoolean("touchpad",true)
        try {
            prefs.edit().remove("touchpad").commit();RemotePreferences.init(context)
            assertTrue(RemotePreferences.touchpad.value)
            RemotePreferences.set("touchpad",false);RemotePreferences.init(context)
            assertFalse(RemotePreferences.touchpad.value)
        } finally {
            if(present) prefs.edit().putBoolean("touchpad",old).commit() else prefs.edit().remove("touchpad").commit()
            RemotePreferences.init(context)
        }
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
            compose.onNodeWithText("Menu").assertIsDisplayed()
            compose.onNodeWithText("Mic").assertDoesNotExist()
            screenshot("touchpad.png")
            compose.onNodeWithText("Controls").performScrollTo().performClick()
            compose.onNodeWithText("One-handed mode: on").assertExists()
        } finally { RemotePreferences.set("oneHanded",false);RemotePreferences.set("touchpad",false) }
    }
    @Test fun tvRemoteProviderRejectsOtherAppUids() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        for(method in listOf("status","bubbleConfig","setBubbleConfig")) {
            try { context.contentResolver.call(android.net.Uri.parse("content://io.konnecttv.glowlauncher.remote"),method,null,null);fail("Other app accessed $method") }
            catch(expected:SecurityException) { assertTrue(expected.message.orEmpty().contains("ADB shell")) }
        }
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
    @Test fun tvBubbleConfigPersistsBlocksWithReasonAndQueuesMessages() = withTvInput {
        val saved=remote("bubbleConfig").getJSONObject("config")
        try {
            fun config(enabled:Boolean,scope:String="ANY",packages:List<String> = emptyList()) =
                remote("setBubbleConfig",JSONObject().put("enabled",enabled).put("scope",scope).put("packages",org.json.JSONArray(packages)))
            config(false)
            assertFalse(remote("bubbleConfig").getJSONObject("config").getBoolean("enabled"))
            val blocked=remote("message",JSONObject().put("text","private test message"))
            assertFalse(blocked.getBoolean("ok"));assertTrue(blocked.getString("error").contains("Show messages on TV is off"))
            assertFalse(blocked.toString().contains("private test message"))
            config(true,"HOME")
            shell("am start -W -a android.settings.SETTINGS")
            val foregroundDeadline=SystemClock.elapsedRealtime()+5000
            while(remote("status").optString("foregroundPackage").isBlank() && SystemClock.elapsedRealtime()<foregroundDeadline) SystemClock.sleep(100)
            val ruleBlocked=remote("message",JSONObject().put("text","blocked by home rule"))
            assertFalse(ruleBlocked.getBoolean("ok"));assertTrue(ruleBlocked.getString("error").contains("blocked in TV message settings"))
            config(true)
            val first=remote("message",JSONObject().put("text","First queued message"));val firstId=first.getLong("messageId")
            val second=remote("message",JSONObject().put("text","Second queued message"));val secondId=second.getLong("messageId")
            assertEquals(firstId,second.getLong("currentMessageId"));assertEquals(1,second.getInt("queuedMessages"))
            val nextDeadline=SystemClock.elapsedRealtime()+10000
            while(remote("status").optLong("currentMessageId")!=secondId && SystemClock.elapsedRealtime()<nextDeadline) SystemClock.sleep(200)
            val status=remote("status")
            assertEquals(secondId,status.getLong("currentMessageId"))
            assertFalse(status.toString().contains("First queued message"))
            // Settings live on the TV even after the input service is disconnected/reconnected.
            config(true,"SELECTED",listOf(TV_HOME_PACKAGE))
            assertEquals("SELECTED",remote("bubbleConfig").getJSONObject("config").getString("scope"))
            assertEquals(TV_HOME_PACKAGE,remote("bubbleConfig").getJSONObject("config").getJSONArray("packages").getString(0))
        } finally {
            remote("setBubbleConfig",saved)
            shell("am start -W -n io.konnecttv.plus/.MainActivity")
        }
    }
    @Test fun screenOffClearsDimmingAndWakeDoesNotRestoreIt() = withTvInput {
        try {
            assertEquals(50,remote("dim",JSONObject().put("value",50)).getInt("brightness"))
            shell("input keyevent 223")
            val deadline=SystemClock.elapsedRealtime()+5000
            while(remote("status").optInt("brightness")!=100 && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(100)
            assertEquals(100,remote("status").getInt("brightness"))
        } finally { shell("input keyevent 224");shell("wm dismiss-keyguard") }
        assertEquals(100,remote("status").getInt("brightness"))
    }
    private fun withTvInput(test:()->Unit) {
        val key="enabled_accessibility_services"
        val previous=shell("settings get secure $key")
        val globallyEnabled=shell("settings get secure accessibility_enabled")
        shell("input keyevent 224");shell("wm dismiss-keyguard")
        shell("settings put secure $key ${withService(previous,"io.konnecttv.glowlauncher/.RemoteInputService")}")
        shell("settings put secure accessibility_enabled 1")
        try {
            val deadline=SystemClock.elapsedRealtime()+8000
            while(!remote("status").optBoolean("available") && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(200)
            assertTrue(remote("status").getBoolean("available"));test()
        } finally {
            shell("input keyevent 224");shell("wm dismiss-keyguard")
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
