package dev.polyrics.comojuecam.profiling

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class CounterTest {
    private lateinit var counter: Counter

    @Before
    fun setUp() {
        counter = Counter()
    }

    @Test
    fun `new counter starts at zero`() {
        assertEquals(0L, counter.value())
    }

    @Test
    fun `increment increases by one`() {
        counter.increment()
        assertEquals(1L, counter.value())
    }

    @Test
    fun `multiple increments accumulate`() {
        counter.increment()
        counter.increment()
        counter.increment()
        assertEquals(3L, counter.value())
    }

    @Test
    fun `add increases by given amount`() {
        counter.add(5)
        assertEquals(5L, counter.value())
    }

    @Test
    fun `reset returns to zero`() {
        counter.increment()
        counter.increment()
        counter.reset()
        assertEquals(0L, counter.value())
    }
}
