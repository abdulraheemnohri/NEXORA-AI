package com.nexora.ai.ai

import kotlinx.coroutines.flow.Flow

/** Stable app-facing contract; implementations may wrap LiteRT-LM or a test fake. */
interface LocalModelRuntime {
    val state: Flow<RuntimeState>
    suspend fun load(modelPath: String): Result<Unit>
    fun generate(request: GenerationRequest): Flow<GenerationEvent>
    suspend fun forgetConversation(conversationId: String)
    suspend fun unload()
}

data class GenerationHistoryMessage(
    val role: String,
    val content: String
) {
    init {
        require(role == "user" || role == "assistant") { "History role must be user or assistant." }
        require(content.isNotBlank()) { "History messages cannot be blank." }
        require(content.length <= 100_000) { "History message exceeds the local storage limit." }
    }
}

data class GenerationRequest(
    val conversationId: String,
    val prompt: String,
    val systemInstruction: String? = null,
    val history: List<GenerationHistoryMessage> = emptyList(),
    val maxOutputTokens: Int = 512
) {
    init {
        require(conversationId.isNotBlank()) { "Conversation ID cannot be blank." }
        require(prompt.isNotBlank()) { "Prompt cannot be blank." }
        require(maxOutputTokens in 1..4096) {
            "maxOutputTokens must be between 1 and 4096."
        }
    }
}

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
