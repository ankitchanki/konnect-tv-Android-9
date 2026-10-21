package io.konnecttv.glowlauncher

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.SystemClock
import io.konnecttv.plus.mediaPosition
import org.json.JSONObject
import java.security.MessageDigest

/** Media transport inside the TV companion, using its explicitly enabled notification-listener access. */
internal object MediaBridge {
    fun execute(context: Context, args: Array<String>): JSONObject {
        return runCatching {
            val audio = context.getSystemService(AudioManager::class.java)
            val sessions = context.getSystemService(MediaSessionManager::class.java)
            when (args.firstOrNull() ?: "status") {
                "status" -> Unit
                "volume" -> {
                    check(!audio.isVolumeFixed) { "TV volume is fixed. Use the TV/audio receiver controls." }
                    val index = args.getOrNull(1)?.toIntOrNull() ?: error("Invalid volume value.")
                    val min = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
                    val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    check(max > min) { "This TV does not expose an adjustable media volume." }
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, index.coerceIn(min, max), 0)
                }
                "seek" -> {
                    val controller = selected(sessions, context) ?: error("No active TV media session.")
                    val expected = args.getOrNull(1).orEmpty()
                    check(expected.isNotBlank() && expected == mediaKey(controller)) { "Playback changed. Refresh and try again." }
                    val duration = controller.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0
                    val state = controller.playbackState
                    check(duration > 0 && state != null && (state.actions and PlaybackState.ACTION_SEEK_TO) != 0L) {
                        "This app or live stream does not support seeking."
                    }
                    val target = args.getOrNull(2)?.toLongOrNull() ?: error("Invalid playback position.")
                    controller.transportControls.seekTo(target.coerceIn(0, duration))
                }
                else -> error("Unknown media command.")
            }
            snapshot(audio, sessions, context).put("ok", true).put("needsAccess", !context.getSystemService(NotificationManager::class.java).isNotificationListenerAccessGranted(ComponentName(context, MediaAccessService::class.java)))
        }.getOrElse { JSONObject().put("ok", false).put("error", "${it.javaClass.simpleName}: ${it.message}") }
    }

    private fun snapshot(audio: AudioManager, sessions: MediaSessionManager, context: Context): JSONObject {
        val volume = runCatching {
            val min = audio.getStreamMinVolume(AudioManager.STREAM_MUSIC)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val supported = !audio.isVolumeFixed && max > min
            JSONObject().put("index", audio.getStreamVolume(AudioManager.STREAM_MUSIC)).put("min", min).put("max", max)
                .put("supported", supported).put("hint", if (supported) "TV media volume" else "Fixed TV/HDMI volume. Use the TV or receiver's volume controls.")
        }.getOrElse { JSONObject().put("supported", false).put("hint", "The TV did not allow media-volume access.").put("diagnostic", "Volume access: ${it.javaClass.simpleName}: ${it.message}") }
        val playback = runCatching {
            val c = selected(sessions, context)
            if (c == null) {
                val shared=context.getSharedPreferences("remote_privacy",0).getBoolean("sharePlayback",true)
                JSONObject().put("title",if(shared) "No active playback" else "TV playback sharing off").put("hint",if(shared) "Open a video or music app on the TV." else "TV owner disabled sharing in Glowlauncher phone privacy settings.")
            }
            else {
                val m = c.metadata
                val s = c.playbackState
                val duration = m?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.coerceAtLeast(0) ?: 0
                val playing = s?.state == PlaybackState.STATE_PLAYING
                val position = mediaPosition(s?.position ?: 0, duration, playing, s?.playbackSpeed ?: 1f,
                    if (s != null && s.lastPositionUpdateTime > 0) SystemClock.elapsedRealtime() - s.lastPositionUpdateTime else 0)
                val canSeek = duration > 0 && s != null && (s.actions and PlaybackState.ACTION_SEEK_TO) != 0L
                JSONObject().put("package",c.packageName).put("key", mediaKey(c)).put("title", (m?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: c.packageName).take(160))
                    .put("duration", duration).put("position", position).put("playing", playing).put("speed", s?.playbackSpeed ?: 1f)
                    .put("seekSupported", canSeek).put("hint", if (canSeek) "Tap or drag to seek" else if (duration <= 0) "Live or unknown duration: seeking unavailable." else "This app does not expose seeking.")
            }
        }.getOrElse { JSONObject().put("title", "Playback unavailable").put("hint", "The TV blocked media-session access. Other remote buttons still work.").put("diagnostic", "Media-session access: ${it.javaClass.simpleName}: ${it.message}") }
        return JSONObject().put("volume", volume).put("playback", playback)
    }

    private fun selected(manager: MediaSessionManager, context: Context): MediaController? = if(!context.getSharedPreferences("remote_privacy",0).getBoolean("sharePlayback",true)) null else manager.getActiveSessions(ComponentName(context, MediaAccessService::class.java))
        .let { list -> list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PAUSED }
            ?: list.firstOrNull() }

    private fun mediaKey(c: MediaController): String {
        val m = c.metadata
        val identity = listOf(c.packageName, if (Build.VERSION.SDK_INT >= 30) c.tag else "", m?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID),
            m?.getString(MediaMetadata.METADATA_KEY_TITLE), m?.getLong(MediaMetadata.METADATA_KEY_DURATION)).joinToString("|")
        return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray()).take(12).joinToString("") { "%02x".format(it) }
    }

}
