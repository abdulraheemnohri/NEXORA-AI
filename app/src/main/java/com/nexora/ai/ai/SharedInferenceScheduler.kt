package com.nexora.ai.ai

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

data class InferenceQueueState(
    val activeOwner: String? = null,
    val waiting: Int = 0,
    val completed: Long = 0,
    val rejected: Long = 0
)

/**
 * App-wide gate shared by chat and MAS-X. The native LiteRT-LM runtime remains the single
 * owner of model state; callers must use this gate instead of starting competing generations.
 */
class SharedInferenceScheduler(private val maxWaiting: Int = 8) {
    private val mutex = Mutex()
    private val waiting = AtomicInteger(0)
    private val mutableState = MutableStateFlow(InferenceQueueState())
    val state = mutableState.asStateFlow()

    suspend fun <T> withSlot(owner: String, block: suspend () -> T): T {
        require(owner.isNotBlank())
        val position = waiting.incrementAndGet()
        if (position > maxWaiting + 1) {
            waiting.decrementAndGet()
            mutableState.value = mutableState.value.copy(rejected = mutableState.value.rejected + 1)
            throw IllegalStateException("Local inference queue is full. Try again after the active task finishes.")
        }
        mutableState.value = mutableState.value.copy(waiting = (position - 1).coerceAtLeast(0))
        try {
            return mutex.withLock {
                waiting.decrementAndGet()
                mutableState.value = mutableState.value.copy(activeOwner = owner, waiting = waiting.get().coerceAtLeast(0))
                try {
                    block()
                } finally {
                    mutableState.value = mutableState.value.copy(
                        activeOwner = null,
                        waiting = waiting.get().coerceAtLeast(0),
                        completed = mutableState.value.completed + 1
                    )
                }
            }
        } catch (error: Throwable) {
            if (waitingSlot) waiting.decrementAndGet()
            throw error
        }
    }
}
