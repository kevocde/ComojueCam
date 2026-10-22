package dev.polyrics.comojuecam.profiling

class Counter {
    private var value = 0L

    fun increment() {
        value++
    }

    fun add(amount: Long) {
        value += amount
    }

    fun value() = value

    fun reset() {
        value = 0L
    }
}
