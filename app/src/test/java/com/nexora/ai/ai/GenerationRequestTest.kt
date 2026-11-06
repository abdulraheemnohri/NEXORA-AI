package com.nexora.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GenerationRequestTest {
    @Test
    fun defaultsTo512OutputTokens() {
        val request = GenerationRequest(conversationId = "chat-1", prompt = "Hello")
        assertEquals(512, request.maxOutputTokens)
    }

    @Test
    fun acceptsConfiguredOutputTokenBudget() {
        val request = GenerationRequest(
            conversationId = "chat-1",
            prompt = "Explain this",
            maxOutputTokens = 1024
        )
        assertEquals(1024, request.maxOutputTokens)
    }

    @Test
    fun rejectsZeroAndExcessiveOutputBudgets() {
        assertThrows(IllegalArgumentException::class.java) {
            GenerationRequest("chat-1", "Hello", maxOutputTokens = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            GenerationRequest("chat-1", "Hello", maxOutputTokens = 4097)
        }
    }
}
