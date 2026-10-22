package dev.polyrics.comojuecam.recording

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveBackoffTest {
    @Test
    fun `initial timeout is the configured value`() {
        val backoff = AdaptiveBackoff(initialMicros = 100)
        assertEquals(100L, backoff.nextTimeout())
    }

    @Test
    fun `single retry keeps initial timeout`() {
        val backoff = AdaptiveBackoff(initialMicros = 100)
        backoff.onRetry()
        assertEquals(100L, backoff.nextTimeout())
    }

    @Test
    fun `consecutive retries increase timeout exponentially`() {
        val backoff = AdaptiveBackoff(initialMicros = 100, multiplier = 2.0f, maxMicros = 10_000)
        backoff.onRetry()
        backoff.onRetry()
        assertEquals(200L, backoff.nextTimeout())
        backoff.onRetry()
        assertEquals(400L, backoff.nextTimeout())
        backoff.onRetry()
        assertEquals(800L, backoff.nextTimeout())
        backoff.onRetry()
        assertEquals(1600L, backoff.nextTimeout())
    }

    @Test
    fun `timeout does not exceed max`() {
        val backoff = AdaptiveBackoff(initialMicros = 100, multiplier = 2.0f, maxMicros = 500)
        repeat(20) { backoff.onRetry() }
        assertEquals(500L, backoff.nextTimeout())
    }

    @Test
    fun `success resets to initial timeout`() {
        val backoff = AdaptiveBackoff(initialMicros = 100, multiplier = 2.0f)
        backoff.onRetry()
        backoff.onRetry()
        backoff.onRetry()
        backoff.onSuccess()
        assertEquals(100L, backoff.nextTimeout())
    }

    @Test
    fun `success after retry resets consecutive counter`() {
        val backoff = AdaptiveBackoff(initialMicros = 100, multiplier = 2.0f)
        backoff.onRetry()
        backoff.onRetry()
        backoff.onSuccess()
        backoff.onRetry()
        assertEquals(100L, backoff.nextTimeout())
    }
}
