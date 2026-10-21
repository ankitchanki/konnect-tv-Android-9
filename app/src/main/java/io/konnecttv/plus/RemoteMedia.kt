package io.konnecttv.plus

import org.json.JSONObject
import kotlin.math.roundToInt

data class RemoteVolume(val index: Int = 0, val min: Int = 0, val max: Int = 0, val supported: Boolean = false,
    val hint: String = "Checking TV volume…") {
    val percent: Int get() = if (max > min) (((index - min).toFloat() / (max - min)) * 100).roundToInt().coerceIn(0, 100) else 0
}

data class RemotePlayback(val key: String = "", val title: String = "No active playback", val durationMs: Long = 0,
    val positionMs: Long = 0, val playing: Boolean = false, val speed: Float = 1f, val seekSupported: Boolean = false,
    val hint: String = "Open a video or music app on the TV.", val receivedAt: Long = 0) {
    fun positionAt(now: Long) = mediaPosition(positionMs, durationMs, playing, speed, now - receivedAt)
}

data class RemoteMedia(val volume: RemoteVolume = RemoteVolume(), val playback: RemotePlayback = RemotePlayback(), val warnings: List<String> = emptyList())

internal fun mediaPosition(position: Long, duration: Long, playing: Boolean, speed: Float, elapsed: Long): Long {
    if (duration <= 0) return 0
    val advance = if (playing && speed.isFinite()) elapsed.coerceAtLeast(0).toDouble() * speed else 0.0
    return (position.toDouble() + advance).coerceIn(0.0, duration.toDouble()).toLong()
}

internal fun mediaTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}

internal fun parseRemoteMedia(raw: String, now: Long): RemoteMedia {
    val line = raw.lineSequence().lastOrNull { it.startsWith("PLUS_MEDIA:") }
        ?: error("The TV couldn't start media controls. Open Diagnostic logs for its response.")
    val json = JSONObject(line.removePrefix("PLUS_MEDIA:"))
    check(json.optBoolean("ok")) { json.optString("error", "TV media command failed.") }
    val v = json.getJSONObject("volume")
    val p = json.getJSONObject("playback")
    val min = v.optInt("min").coerceAtLeast(0)
    val max = v.optInt("max").coerceAtLeast(min)
    val duration = p.optLong("duration").coerceAtLeast(0)
    return RemoteMedia(
        RemoteVolume(v.optInt("index").coerceIn(min, max), min, max, v.optBoolean("supported") && max > min, v.optString("hint")),
        RemotePlayback(p.optString("key"), p.optString("title", "No active playback"), duration,
            p.optLong("position").coerceIn(0, duration), p.optBoolean("playing"), p.optDouble("speed", 1.0).toFloat(),
            p.optBoolean("seekSupported") && duration > 0 && p.optString("key").isNotBlank(), p.optString("hint"), now),
        listOf(v.optString("diagnostic"), p.optString("diagnostic")).filter { it.isNotBlank() },
    )
}
