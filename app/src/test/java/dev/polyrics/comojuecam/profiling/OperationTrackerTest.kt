package dev.polyrics.comojuecam.profiling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OperationTrackerTest {
    private lateinit var tracker: OperationTracker

    @Before
    fun setUp() {
        tracker = OperationTracker()
    }

    @Test
    fun `unknown operation has zero count`() {
        assertEquals(0, tracker.count("startRecording"))
    }

    @Test
    fun `unknown operation averages to zero`() {
        assertEquals(0L, tracker.averageNanos("startRecording"))
    }

    @Test
    fun `single recording returns itself for all stats`() {
        tracker.record("stop", 5000L)
        assertEquals(1, tracker.count("stop"))
        assertEquals(5000L, tracker.averageNanos("stop"))
        assertEquals(5000L, tracker.minNanos("stop"))
        assertEquals(5000L, tracker.maxNanos("stop"))
    }

    @Test
    fun `multiple recordings of same name compute correct stats`() {
        tracker.record("session", 100L)
        tracker.record("session", 200L)
        tracker.record("session", 300L)
        assertEquals(3, tracker.count("session"))
        assertEquals(200L, tracker.averageNanos("session"))
        assertEquals(100L, tracker.minNanos("session"))
        assertEquals(300L, tracker.maxNanos("session"))
    }

    @Test
    fun `tracks different operations independently`() {
        tracker.record("start", 1000L)
        tracker.record("stop", 2000L)
        assertEquals(1, tracker.count("start"))
        assertEquals(1, tracker.count("stop"))
        assertEquals(1000L, tracker.averageNanos("start"))
        assertEquals(2000L, tracker.averageNanos("stop"))
    }

    @Test
    fun `returns all tracked operation names`() {
        tracker.record("start", 100L)
        tracker.record("stop", 200L)
        tracker.record("session", 300L)
        val operations = tracker.operations()
        assertEquals(setOf("start", "stop", "session"), operations)
    }

    @Test
    fun `reset clears all state`() {
        tracker.record("start", 1000L)
        tracker.record("stop", 2000L)
        tracker.reset()
        assertEquals(0, tracker.count("start"))
        assertEquals(0, tracker.count("stop"))
        assertTrue(tracker.operations().isEmpty())
    }

    @Test
    fun `negative durations are ignored`() {
        tracker.record("start", -100L)
        assertEquals(0, tracker.count("start"))
    }
}
