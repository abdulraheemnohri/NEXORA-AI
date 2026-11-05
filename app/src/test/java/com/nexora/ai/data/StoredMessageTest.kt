package com.nexora.ai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class StoredMessageTest {
    @Test
    fun acceptsSupportedRolesAndPreservesMessageFields() {
        val message = StoredMessage(
            id = "message-1",
            conversationId = "conversation-1",
            role = "assistant",
            content = "Hello from local NEXORA",
            createdAt = 123L
        )

        assertEquals("message-1", message.id)
        assertEquals("conversation-1", message.conversationId)
        assertEquals("assistant", message.role)
        assertEquals("Hello from local NEXORA", message.content)
        assertEquals(123L, message.createdAt)
    }

    @Test
    fun rejectsUnsupportedRoles() {
        assertThrows(IllegalArgumentException::class.java) {
            StoredMessage("message-2", "conversation-1", "tool", "Untrusted tool output")
        }
    }

    @Test
    fun rejectsMessagesLargerThanStorageLimit() {
        val oversizedContent = "x".repeat(100_001)
        assertThrows(IllegalArgumentException::class.java) {
            StoredMessage("message-3", "conversation-1", "user", oversizedContent)
        }
    }
}
