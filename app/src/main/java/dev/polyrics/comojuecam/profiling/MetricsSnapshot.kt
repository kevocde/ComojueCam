package dev.polyrics.comojuecam.profiling

data class OperationMetrics(
    val count: Int,
    val avgNanos: Long,
    val minNanos: Long,
    val maxNanos: Long
)

data class GaugeSnapshot(
    val minValue: Long,
    val maxValue: Long,
    val lastValue: Long
)

data class MetricsSnapshot(
    val frameIntervalCount: Int,
    val frameIntervalAvgNanos: Long,
    val frameIntervalMinNanos: Long,
    val frameIntervalMaxNanos: Long,
    val operations: Map<String, OperationMetrics>,
    val counters: Map<String, Long>,
    val gauges: Map<String, GaugeSnapshot>
)
