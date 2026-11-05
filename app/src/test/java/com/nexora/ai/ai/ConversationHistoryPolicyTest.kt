package com.nexora.ai.ai

import com.google.ai.edge.litertlm.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationHistoryPolicyTest {
    @Test
    fun keepsNewestMessagesInOriginalOrderWithinMessageLimit() {
        val history = (0 until 20).map { index ->
            GenerationHistoryMessage(
                role = if (index % 2 == 0) "user" else "assistant",
                content = "message-$index"
            )
        }

        val replay = ConversationHistoryPolicy.select(history)

        assertEquals(ConversationHistoryPolicy.MAX_REPLAY_MESSAGES, replay.size)
        assertEquals("message-4", replay.first().content)
        assertEquals("message-19", replay.last().content)
    }

    @Test
    fun appliesCharacterBudgetAndMarksTruncatedOlderContent() {
        val history = listOf(
            GenerationHistoryMessage("user", "a".repeat(8_000)),
            GenerationHistoryMessage("assistant", "b".repeat(8_000))
        )

        val replay = ConversationHistoryPolicy.select(history)

        assertEquals(ConversationHistoryPolicy.MAX_REPLAY_CHARACTERS, replay.sumOf { it.content.length })
        assertTrue(replay.first().content.startsWith(ConversationHistoryPolicy.TRUNCATION_MARKER))
        assertEquals("b".repeat(8_000), replay.last().content)
    }

    @Test
    fun mapsPersistedRolesToLiteRtLmMessageRoles() {
        assertEquals(Role.USER, GenerationHistoryMessage("user", "Question").toLiteRtMessage().role)
        assertEquals(Role.MODEL, GenerationHistoryMessage("assistant", "Answer").toLiteRtMessage().role)
    }

    @Test
    fun rejectsUnsupportedHistoryRoles() {
        assertThrows(IllegalArgumentException::class.java) {
            GenerationHistoryMessage("tool", "Tool output")
        }
    }
}
