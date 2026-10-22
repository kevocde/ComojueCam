package dev.polyrics.comojuecam.recording

class AdaptiveBackoff(
    private val initialMicros: Long = 100,
    private val maxMicros: Long = 10_000,
    private val multiplier: Float = 2.0f
) {
    private var currentMicros = initialMicros
    private var consecutiveRetries = 0

    fun nextTimeout(): Long = currentMicros

    fun onRetry() {
        consecutiveRetries++
        if (consecutiveRetries > 1) {
            currentMicros = (currentMicros * multiplier).toLong().coerceAtMost(maxMicros)
        }
    }

    fun onSuccess() {
        consecutiveRetries = 0
        currentMicros = initialMicros
    }
}
