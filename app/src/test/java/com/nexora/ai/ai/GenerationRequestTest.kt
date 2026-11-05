package com.nexora.ai.ai

import org.junit.Assert.assertThrows
import org.junit.Test

class GenerationRequestTest {
    @Test
    fun rejectsNonPositiveOutputTokenLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            GenerationRequest(conversationId = "conversation", prompt = "Hello", maxOutputTokens = 0)
        }
    }

    @Test
    fun rejectsOutputTokenLimitAboveSafetyCeiling() {
        assertThrows(IllegalArgumentException::class.java) {
            GenerationRequest(conversationId = "conversation", prompt = "Hello", maxOutputTokens = 8_193)
        }
    }
}
