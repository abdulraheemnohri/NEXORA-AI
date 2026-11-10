package com.nexora.ai.agent

import com.nexora.ai.data.ChatRepository
import com.nexora.ai.data.ConversationSearchHit

/** User-enabled, read-only lookup over message text already stored in the app-private database. */
class SearchSavedConversationsTool(
    private val repository: ChatRepository,
    enabledByUser: Boolean
) : AgentTool {
    override val descriptor = ToolDescriptor(
        id = TOOL_ID,
        description = "Search saved local conversation message text.",
        risk = ToolRisk.READ_ONLY,
        enabledByUser = enabledByUser
    )

    override suspend fun execute(input: String): ToolOutput {
        val query = input.trim()
        require(query.length in MIN_QUERY_LENGTH..MAX_QUERY_LENGTH) {
            "Search query must contain 2–128 characters."
        }
        val matches: List<ConversationSearchHit> = repository.searchConversations(query, MAX_RESULTS)
        return ToolOutput.ConversationMatches(matches)
    }

    companion object {
        const val TOOL_ID = "local.conversation_search"
        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_QUERY_LENGTH = 128
        private const val MAX_RESULTS = 20
    }
}
