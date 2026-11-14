package com.nexora.ai.society

import com.nexora.ai.ai.SharedInferenceScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class MasxInferenceAdmissionTest {
    @Test fun admissionIsRecheckedAfterWaitingForTheSharedInferenceSlot() = runBlocking {
        val scheduler = SharedInferenceScheduler()
        val activeSlotEntered = CompletableDeferred<Unit>()
        val releaseActiveSlot = CompletableDeferred<Unit>()
        val activeRequest = async {
            scheduler.withSlot("chat") {
                activeSlotEntered.complete(Unit)
                releaseActiveSlot.await()
            }
        }
        activeSlotEntered.await()

        val currentlyAllowed = AtomicBoolean(true)
        val admissionChecks = AtomicInteger()
        val workCalls = AtomicInteger()
        val admission = MasxInferenceAdmission {
            admissionChecks.incrementAndGet()
            check(currentlyAllowed.get()) { "Resource conditions changed while waiting." }
        }
        val queuedRequest = async {
            runCatching {
                withInferenceAdmission(scheduler, "masx:worker", admission) {
                    workCalls.incrementAndGet()
                    "started"
                }
            }
        }

        withTimeout(2_000) {
            while (scheduler.state.value.waiting == 0) yield()
        }
        assertEquals(0, admissionChecks.get())

        currentlyAllowed.set(false)
        releaseActiveSlot.complete(Unit)

        val result = queuedRequest.await()
        assertTrue(result.isFailure)
        assertEquals(1, admissionChecks.get())
        assertEquals(0, workCalls.get())
        activeRequest.await()
    }

    @Test fun admittedWorkRunsOnlyAfterTheAdmissionCheck() = runBlocking {
        val scheduler = SharedInferenceScheduler()
        val events = mutableListOf<String>()
        val result = withInferenceAdmission(
            scheduler,
            "masx:worker",
            MasxInferenceAdmission { events += "admitted" }
        ) {
            events += "work"
            "completed"
        }

        assertEquals("completed", result)
        assertEquals(listOf("admitted", "work"), events)
    }
}
