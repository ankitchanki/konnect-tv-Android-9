package io.konnecttv.plus

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.konnecttv.plus.data.SavedTv
import io.konnecttv.plus.ui.C
import io.konnecttv.plus.ui.RemoteMediaControls
import io.konnecttv.plus.ui.RemoteScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicLong

@RunWith(AndroidJUnit4::class)
class RemoteMediaTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun shell(command: String) = ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
        .bufferedReader().use { it.readText() }

    @Test fun sliderTapAndDragSetAbsoluteVolumeAndPlaybackPosition() {
        var volume = -1
        var seek = -1L
        val state = sample()
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                Column(Modifier.fillMaxSize().background(C.Bg).padding(16.dp)) {
                    RemoteMediaControls(state,null,false,{volume=it},{key,position-> assertEquals(state.playback.key,key);seek=position},{})
                }
            }
        }
        compose.onNodeWithContentDescription("TV volume").performTouchInput { click(Offset(width * .75f, centerY)) }
        compose.runOnIdle { assertTrue("Volume $volume", volume in 10..12) }
        compose.onNodeWithContentDescription("Video playback position").performTouchInput { click(Offset(width * .5f, centerY)) }
        compose.runOnIdle { assertTrue("Seek $seek",seek in 110000..130000) }
        compose.onNodeWithContentDescription("TV volume").performTouchInput { swipe(Offset(width*.75f,centerY),Offset(width*.25f,centerY),300) }
        compose.runOnIdle { assertTrue("Dragged volume $volume",volume in 3..5) }
    }

    @Test fun unsupportedPlaybackAndFixedVolumeAreDisabled() {
        compose.activity.runOnUiThread {
            compose.activity.setContent { RemoteMediaControls(RemoteMedia(RemoteVolume(hint="Fixed TV volume"),RemotePlayback(hint="Live stream")),null,false,{fail("Fixed volume must not write")},{_,_->fail("Live stream must not seek")},{}) }
        }
        compose.onNodeWithContentDescription("TV volume").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Video playback position").assertIsNotEnabled()
        compose.onNodeWithText("Live stream").assertExists()
    }

    @Test fun currentRemoteScreenshotsIncludeTheNewSlidersAndOriginalButtons() {
        val vm = ViewModelProvider(compose.activity)[TvViewModel::class.java]
        compose.runOnIdle {
            vm.remoteMedia.value = sample()
            compose.activity.setContent { RemoteScreen(vm,SavedTv("remote-preview","Living room TV","192.168.1.24",5555)) }
        }
        compose.onNodeWithContentDescription("TV volume").assertIsDisplayed()
        screenshot("remote-sliders.png")
        compose.onNodeWithText("Play / Pause").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Power").assertIsDisplayed()
        screenshot("remote-buttons.png")
    }

    @Test fun shellMediaBridgeReadsAndSeeksAnActualMediaSession() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val session=MediaSession(context,"Plus integration test")
        val sought=AtomicLong(-1)
        fun playback(position:Long)=PlaybackState.Builder().setActions(PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_PLAY_PAUSE)
            .setState(PlaybackState.STATE_PAUSED,position,1f,SystemClock.elapsedRealtime()).build()
        session.setCallback(object:MediaSession.Callback(){
            override fun onSeekTo(pos:Long){sought.set(pos);session.setPlaybackState(playback(pos))}
        },Handler(Looper.getMainLooper()))
        session.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,"Plus integration video")
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID,"plus-test-media").putLong(MediaMetadata.METADATA_KEY_DURATION,240000).build())
        session.setPlaybackState(playback(60000));session.isActive=true
        val audio=context.getSystemService(AudioManager::class.java)
        val original=audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        val script=File(context.getExternalFilesDir(null),"media-bridge-test.sh")
        fun bridge(args:String):String {
            script.writeText("CLASSPATH=${context.applicationInfo.sourceDir} app_process /system/bin io.konnecttv.plus.adb.MediaBridge $args 2>&1\n")
            return shell("sh ${script.path}")
        }
        try {
            val raw=bridge("status")
            val status=parseRemoteMedia(raw,SystemClock.elapsedRealtime())
            assertTrue(raw,status.playback.seekSupported)
            assertEquals("Plus integration video",status.playback.title)
            assertEquals(240000L,status.playback.durationMs)
            val response=bridge("seek ${status.playback.key} 120000")
            parseRemoteMedia(response,SystemClock.elapsedRealtime())
            compose.waitUntil(5000){sought.get()==120000L}
            val after=parseRemoteMedia(bridge("status"),SystemClock.elapsedRealtime())
            assertEquals(120000L,after.playback.positionMs)
            val rejected=bridge("seek 000000000000000000000000 220000")
            assertTrue(rejected,rejected.contains("Playback changed"))
            assertEquals(120000L,sought.get())
            if(!audio.isVolumeFixed && audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)>audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)) assertTrue(raw,status.volume.supported)
            if(status.volume.supported) {
                val target=if(original>status.volume.min)original-1 else (original+1).coerceAtMost(status.volume.max)
                parseRemoteMedia(bridge("volume $target"),SystemClock.elapsedRealtime())
                compose.waitUntil(5000){audio.getStreamVolume(AudioManager.STREAM_MUSIC)==target}
            }
        } finally {
            if(!audio.isVolumeFixed)runCatching { bridge("volume $original") }
            session.release();script.delete()
        }
    }

    private fun sample()=RemoteMedia(RemoteVolume(6,0,15,true,"TV media volume"),
        RemotePlayback("1234567890abcdef12345678","Sample video",240000,60000,false,1f,true,"Tap or drag to seek",SystemClock.elapsedRealtime()))

    private fun screenshot(name:String) {
        compose.waitForIdle()
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),name)
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            try{file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }}finally{bitmap.recycle()}
            shell("cp ${file.path} /data/local/tmp/konnecttv-$name")
        }
    }
}
