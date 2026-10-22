package dev.polyrics.comojuecam.profiling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PerformanceMetricsTest {
    private lateinit var metrics: PerformanceMetrics

    @Before
    fun setUp() {
        metrics = PerformanceMetrics()
    }

    @Test
    fun `snapshot reflects frame interval data`() {
        metrics.recordFrameInterval(10_000_000L)
        metrics.recordFrameInterval(20_000_000L)
        val snapshot = metrics.snapshot()
        assertEquals(2, snapshot.frameIntervalCount)
        assertEquals(15_000_000L, snapshot.frameIntervalAvgNanos)
        assertEquals(10_000_000L, snapshot.frameIntervalMinNanos)
        assertEquals(20_000_000L, snapshot.frameIntervalMaxNanos)
    }

    @Test
    fun `snapshot reflects operation data`() {
        metrics.recordOperation("startRecording", 50_000_000L)
        metrics.recordOperation("startRecording", 70_000_000L)
        val snapshot = metrics.snapshot()
        val startOp = snapshot.operations["startRecording"]!!
        assertEquals(2, startOp.count)
        assertEquals(60_000_000L, startOp.avgNanos)
    }

    @Test
    fun `snapshot reflects counter data`() {
        metrics.incrementCounter("frameDrops")
        metrics.incrementCounter("frameDrops")
        metrics.incrementCounter("dequeueRetries")
        val snapshot = metrics.snapshot()
        assertEquals(2L, snapshot.counters["frameDrops"])
        assertEquals(1L, snapshot.counters["dequeueRetries"])
    }

    @Test
    fun `snapshot reflects gauge data`() {
        metrics.recordGauge("bufferSize", 100)
        metrics.recordGauge("bufferSize", 200)
        metrics.recordGauge("bufferSize", 150)
        val snapshot = metrics.snapshot()
        val gauge = snapshot.gauges["bufferSize"]!!
        assertEquals(100L, gauge.minValue)
        assertEquals(200L, gauge.maxValue)
        assertEquals(150L, gauge.lastValue)
    }

    @Test
    fun `reset clears everything`() {
        metrics.recordFrameInterval(1000L)
        metrics.recordOperation("test", 2000L)
        metrics.incrementCounter("drops")
        metrics.recordGauge("buf", 50)
        metrics.reset()
        val snapshot = metrics.snapshot()
        assertEquals(0, snapshot.frameIntervalCount)
        assertTrue(snapshot.operations.isEmpty())
        assertTrue(snapshot.counters.isEmpty())
        assertTrue(snapshot.gauges.isEmpty())
    }

    @Test
    fun `toLog returns formatted string`() {
        metrics.recordFrameInterval(33_000_000L)
        metrics.recordOperation("startRecording", 50_000_000L)
        metrics.incrementCounter("frameDrops")
        val log = metrics.toLog()
        assertTrue(log.contains("frames"))
        assertTrue(log.contains("startRecording"))
        assertTrue(log.contains("frameDrops"))
    }
}
