package dev.polyrics.comojuecam.profiling

class PerformanceMetrics {
    private val frameIntervals = FrameIntervalTracker()
    private val operations = OperationTracker()
    private val counters = mutableMapOf<String, Counter>()
    private val gauges = mutableMapOf<String, GaugeTracker>()

    fun recordFrameInterval(intervalNanos: Long) {
        frameIntervals.record(intervalNanos)
    }

    fun recordOperation(name: String, durationNanos: Long) {
        operations.record(name, durationNanos)
    }

    fun incrementCounter(name: String) {
        counters.getOrPut(name) { Counter() }.increment()
    }

    fun recordGauge(name: String, value: Long) {
        gauges.getOrPut(name) { GaugeTracker() }.record(value)
    }

    fun snapshot(): MetricsSnapshot {
        return MetricsSnapshot(
            frameIntervalCount = frameIntervals.count(),
            frameIntervalAvgNanos = frameIntervals.averageNanos(),
            frameIntervalMinNanos = frameIntervals.minNanos(),
            frameIntervalMaxNanos = frameIntervals.maxNanos(),
            operations = operations.snapshot(),
            counters = counters.mapValues { (_, counter) -> counter.value() },
            gauges = gauges.mapNotNull { (name, gauge) ->
                gauge.toSnapshot()?.let { name to it }
            }.toMap()
        )
    }

    fun reset() {
        frameIntervals.reset()
        operations.reset()
        counters.clear()
        gauges.clear()
    }

    fun toLog(): String {
        val snapshot = snapshot()
        val sb = StringBuilder()
        sb.appendLine("=== Performance Metrics ===")
        if (snapshot.frameIntervalCount > 0) {
            sb.appendLine("frames: count=${snapshot.frameIntervalCount}, " +
                "avg=${nanosToMs(snapshot.frameIntervalAvgNanos)}ms, " +
                "min=${nanosToMs(snapshot.frameIntervalMinNanos)}ms, " +
                "max=${nanosToMs(snapshot.frameIntervalMaxNanos)}ms")
        }
        snapshot.operations.forEach { (name, op) ->
            sb.appendLine("$name: count=${op.count}, " +
                "avg=${nanosToMs(op.avgNanos)}ms, " +
                "min=${nanosToMs(op.minNanos)}ms, " +
                "max=${nanosToMs(op.maxNanos)}ms")
        }
        snapshot.counters.forEach { (name, value) ->
            sb.appendLine("$name: $value")
        }
        snapshot.gauges.forEach { (name, gauge) ->
            sb.appendLine("$name: last=${gauge.lastValue}, " +
                "min=${gauge.minValue}, max=${gauge.maxValue}")
        }
        return sb.toString()
    }

    private fun nanosToMs(nanos: Long) = nanos / 1_000_000.0
}
