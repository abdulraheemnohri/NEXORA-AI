package com.nexora.ai.ai
import kotlinx.coroutines.flow.Flow

/** Contract for a production adapter implemented against Google's official LiteRT-LM Kotlin API. */
interface LocalModelRuntime {
    val state: Flow<RuntimeState>
    suspend fun load(modelPath: String): Result<Unit>
    fun generate(request: GenerationRequest): Flow<GenerationEvent>
    suspend fun unload()
}
data class GenerationRequest(
    val conversationId: String,
    val prompt: String,
    val systemInstruction: String? = null,
    val maxOutputTokens: Int = 512
)
sealed interface GenerationEvent {
    data class Token(val text: String) : GenerationEvent
    data object Completed : GenerationEvent
    data class Failed(val reason: String) : GenerationEvent
}
sealed interface RuntimeState {
    data object NotConfigured : RuntimeState
    data object Downloading : RuntimeState
    data object Loading : RuntimeState
    data object Ready : RuntimeState
    data class Error(val message: String) : RuntimeState
}
