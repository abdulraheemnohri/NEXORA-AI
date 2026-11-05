package com.nexora.ai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** IO boundary for the local conversation database. */
class ChatRepository(private val database: LocalChatDatabase) {
    suspend fun createConversation(title: String): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        database.createConversation(id, title.trim().ifEmpty { "New conversation" })
        id
    }

    suspend fun saveMessage(conversationId: String, role: String, content: String): StoredMessage =
        withContext(Dispatchers.IO) {
            require(content.isNotBlank()) { "Message cannot be empty" }
            val message = StoredMessage(UUID.randomUUID().toString(), conversationId, role, content)
            database.addMessage(message)
            message
        }

    suspend fun conversations(): List<StoredConversation> =
        withContext(Dispatchers.IO) { database.listConversations() }

    suspend fun messages(conversationId: String): List<StoredMessage> =
        withContext(Dispatchers.IO) { database.listMessages(conversationId) }

    suspend fun deleteConversation(conversationId: String): Boolean =
        withContext(Dispatchers.IO) { database.deleteConversation(conversationId) }
}
