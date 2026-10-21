package io.konnecttv.plus

import org.json.JSONObject
import kotlin.math.roundToInt

data class RemoteVolume(val index: Int = 0, val min: Int = 0, val max: Int = 0, val supported: Boolean = false,
    val hint: String = "Checking TV volume…") {
    val percent: Int get() = if (max > min) (((index - min).toFloat() / (max - min)) * 100).roundToInt().coerceIn(0, 100) else 0
}

data class RemotePlayback(val key: String = "", val title: String = "No active playback", val durationMs: Long = 0,
    val positionMs: Long = 0, val playing: Boolean = false, val speed: Float = 1f, val seekSupported: Boolean = false,
    val hint: String = "Open a video or music app on the TV.", val receivedAt: Long = 0, val appPackage: String = "") {
    fun positionAt(now: Long) = mediaPosition(positionMs, durationMs, playing, speed, now - receivedAt)
}

data class RemoteMedia(val volume: RemoteVolume = RemoteVolume(), val playback: RemotePlayback = RemotePlayback(), val warnings: List<String> = emptyList(), val needsAccess: Boolean = false)

internal fun mediaTime(ms: Long): String {
    val seconds = ms.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60)
    else "%d:%02d".format(seconds / 60, seconds % 60)
}

internal fun parseRemoteMedia(raw: String, now: Long): RemoteMedia {
    val encoded = Regex("PLUS_MEDIA=([A-Za-z0-9+/=]+)").find(raw)?.groupValues?.get(1)
        ?: error("Enable TV media sliders to install or update the TV companion.")
    val json = JSONObject(String(android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP), Charsets.UTF_8))
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
            p.optBoolean("seekSupported") && duration > 0 && p.optString("key").isNotBlank(), p.optString("hint"), now, p.optString("package")),
        listOf(v.optString("diagnostic"), p.optString("diagnostic")).filter { it.isNotBlank() },
        json.optBoolean("needsAccess"),
    )
}
