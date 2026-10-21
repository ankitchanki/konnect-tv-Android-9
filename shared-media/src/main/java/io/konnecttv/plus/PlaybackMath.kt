package io.konnecttv.plus

internal fun mediaPosition(position: Long, duration: Long, playing: Boolean, speed: Float, elapsed: Long): Long {
    if (duration <= 0) return 0
    val advance = if (playing && speed.isFinite()) elapsed.coerceAtLeast(0).toDouble() * speed else 0.0
    return (position.toDouble() + advance).coerceIn(0.0, duration.toDouble()).toLong()
}
