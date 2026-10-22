package dev.polyrics.comojuecam.profiling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FrameIntervalTrackerTest {
    private lateinit var tracker: FrameIntervalTracker

    @Before
    fun setUp() {
        tracker = FrameIntervalTracker()
    }

    @Test
    fun `empty tracker has zero count`() {
        assertEquals(0, tracker.count())
    }

    @Test
    fun `empty tracker averages to zero`() {
        assertEquals(0L, tracker.averageNanos())
    }

    @Test
    fun `single interval returns itself for all stats`() {
        tracker.record(1000L)
        assertEquals(1, tracker.count())
        assertEquals(1000L, tracker.averageNanos())
        assertEquals(1000L, tracker.minNanos())
        assertEquals(1000L, tracker.maxNanos())
    }

    @Test
    fun `multiple intervals compute correct average`() {
        tracker.record(100L)
        tracker.record(200L)
        tracker.record(300L)
        assertEquals(3, tracker.count())
        assertEquals(200L, tracker.averageNanos())
    }

    @Test
    fun `tracks minimum correctly`() {
        tracker.record(500L)
        tracker.record(100L)
        tracker.record(300L)
        assertEquals(100L, tracker.minNanos())
    }

    @Test
    fun `tracks maximum correctly`() {
        tracker.record(500L)
        tracker.record(100L)
        tracker.record(300L)
        assertEquals(500L, tracker.maxNanos())
    }

    @Test
    fun `reset clears all state`() {
        tracker.record(1000L)
        tracker.record(2000L)
        tracker.reset()
        assertEquals(0, tracker.count())
        assertEquals(0L, tracker.averageNanos())
    }

    @Test
    fun `negative intervals are ignored`() {
        tracker.record(-100L)
        assertEquals(0, tracker.count())
    }
}
