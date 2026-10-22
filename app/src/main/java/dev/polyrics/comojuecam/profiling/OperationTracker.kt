package dev.polyrics.comojuecam.profiling

class OperationTracker {
    private val operations = mutableMapOf<String, OperationData>()

    fun record(name: String, durationNanos: Long) {
        if (durationNanos < 0) return
        val data = operations.getOrPut(name) { OperationData() }
        data.count++
        data.sum += durationNanos
        if (durationNanos < data.min) data.min = durationNanos
        if (durationNanos > data.max) data.max = durationNanos
    }

    fun count(name: String) = operations[name]?.count ?: 0

    fun averageNanos(name: String): Long {
        val data = operations[name] ?: return 0L
        return if (data.count == 0) 0L else data.sum / data.count
    }

    fun minNanos(name: String): Long {
        val data = operations[name] ?: return 0L
        return if (data.count == 0) 0L else data.min
    }

    fun maxNanos(name: String): Long {
        val data = operations[name] ?: return 0L
        return if (data.count == 0) 0L else data.max
    }

    fun operations() = operations.keys.toSet()

    fun snapshot(): Map<String, OperationMetrics> {
        return operations.mapValues { (_, data) ->
            OperationMetrics(
                count = data.count,
                avgNanos = if (data.count == 0) 0L else data.sum / data.count,
                minNanos = if (data.count == 0) 0L else data.min,
                maxNanos = if (data.count == 0) 0L else data.max
            )
        }
    }

    fun reset() {
        operations.clear()
    }

    private class OperationData {
        var count = 0
        var sum = 0L
        var min = Long.MAX_VALUE
        var max = Long.MIN_VALUE
    }
}
