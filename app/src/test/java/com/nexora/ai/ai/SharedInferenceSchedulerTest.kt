package com.nexora.ai.ai

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class SharedInferenceSchedulerTest {
    @Test fun concurrentCallersAreSerialized() = runBlocking {
        val scheduler = SharedInferenceScheduler(maxWaiting = 8)
        val active = AtomicInteger(0)
        val maximum = AtomicInteger(0)
        (1..6).map { index ->
            async {
                scheduler.withSlot("agent-$index") {
                    val now = active.incrementAndGet()
                    maximum.updateAndGet { old -> maxOf(old,now) }
                    delay(15)
                    active.decrementAndGet()
                }
            }
        }.awaitAll()
        assertEquals(1,maximum.get())
        assertEquals(6L,scheduler.state.value.completed)
        assertEquals(0,scheduler.state.value.waiting)
    }
}
