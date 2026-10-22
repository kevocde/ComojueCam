package dev.polyrics.comojuecam.profiling

class FrameIntervalTracker {
    private var count = 0
    private var sum = 0L
    private var min = Long.MAX_VALUE
    private var max = Long.MIN_VALUE

    fun record(intervalNanos: Long) {
        if (intervalNanos < 0) return
        count++
        sum += intervalNanos
        if (intervalNanos < min) min = intervalNanos
        if (intervalNanos > max) max = intervalNanos
    }

    fun count() = count

    fun averageNanos() = if (count == 0) 0L else sum / count

    fun minNanos() = if (count == 0) 0L else min

    fun maxNanos() = if (count == 0) 0L else max

    fun reset() {
        count = 0
        sum = 0L
        min = Long.MAX_VALUE
        max = Long.MIN_VALUE
    }
}
