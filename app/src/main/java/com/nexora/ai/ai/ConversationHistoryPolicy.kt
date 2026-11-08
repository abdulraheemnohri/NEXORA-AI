package com.nexora.ai.ai

import com.google.ai.edge.litertlm.Message

/**
 * Bounds transcript replay before it is copied into LiteRT-LM's native conversation state.
 * The character budget is a conservative memory guard, not a token-count guarantee.
 */
internal object ConversationHistoryPolicy {
    const val MAX_REPLAY_MESSAGES = 16
    const val MAX_REPLAY_CHARACTERS = 12_000
    const val TRUNCATION_MARKER = "[Earlier conversation content truncated]\n"

    fun select(messages: List<GenerationHistoryMessage>): List<GenerationHistoryMessage> {
        val selected = mutableListOf<GenerationHistoryMessage>()
        var remainingCharacters = MAX_REPLAY_CHARACTERS

        for (message in messages.asReversed()) {
            if (selected.size >= MAX_REPLAY_MESSAGES || remainingCharacters <= 0) break
            if (message.content.length <= remainingCharacters) {
                selected.add(0, message)
                remainingCharacters -= message.content.length
            } else {
                val marker = TRUNCATION_MARKER.take(remainingCharacters)
                val contentBudget = (remainingCharacters - marker.length).coerceAtLeast(0)
                selected.add(0, message.copy(content = marker + message.content.takeLast(contentBudget)))
                remainingCharacters = 0
            }
        }
        return selected
    }
}

internal fun GenerationHistoryMessage.toLiteRtMessage(): Message = when (role) {
    "user" -> Message.user(content)
    "assistant" -> Message.model(content)
    else -> error("Unsupported conversation history role: $role")
}
