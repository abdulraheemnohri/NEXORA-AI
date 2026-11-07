package com.nexora.ai.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeInferenceCompletionTest {
    @Test
    fun collectorCancellationWaitsForNativeTerminalCallback() = runBlocking {
        val nativeCompletion = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val cleanupFinished = CompletableDeferred<Unit>()

        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                awaitCancellation()
            } finally {
                cleanupStarted.complete(Unit)
                awaitNativeCompletion(nativeCompletion)
                cleanupFinished.complete(Unit)
            }
        }

        job.cancel()
        cleanupStarted.await()
        assertFalse(cleanupFinished.isCompleted)

        nativeCompletion.complete(Unit)
        job.join()

        assertTrue(cleanupFinished.isCompleted)
    }
}
