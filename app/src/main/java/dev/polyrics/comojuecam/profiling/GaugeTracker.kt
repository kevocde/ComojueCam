package dev.polyrics.comojuecam.profiling

class GaugeTracker {
    private var min = Long.MAX_VALUE
    private var max = Long.MIN_VALUE
    private var last = 0L
    private var hasValue = false

    fun record(value: Long) {
        if (!hasValue) {
            min = value
            max = value
            hasValue = true
        } else {
            if (value < min) min = value
            if (value > max) max = value
        }
        last = value
    }

    fun minValue() = if (!hasValue) 0L else min
    fun maxValue() = if (!hasValue) 0L else max
    fun lastValue() = last

    fun toSnapshot(): GaugeSnapshot? {
        if (!hasValue) return null
        return GaugeSnapshot(min, max, last)
    }

    fun reset() {
        min = Long.MAX_VALUE
        max = Long.MIN_VALUE
        last = 0L
        hasValue = false
    }
}
