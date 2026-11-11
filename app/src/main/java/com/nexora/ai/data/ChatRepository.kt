package com.nexora.ai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** Storage contract used by the permissioned local-note tool. */
interface LocalNoteStore {
    suspend fun createLocalNote(title: String, content: String): LocalNote
    suspend fun localNotes(): List<LocalNote>
    suspend fun deleteLocalNote(id: String): Boolean
}

/** IO boundary for the local conversation database. */
class ChatRepository(private val database: LocalChatDatabase) : LocalNoteStore {
    suspend fun createConversation(title: String): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        database.createConversation(id, title.trim().ifEmpty { "New conversation" })
        id
    }

    suspend fun updateConversationTitle(conversationId: String, title: String): Boolean =
        withContext(Dispatchers.IO) { database.updateConversationTitle(conversationId, title) }

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

    suspend fun searchConversations(query: String, limit: Int = 20): List<ConversationSearchHit> =
        withContext(Dispatchers.IO) { database.searchMessages(query, limit) }

    suspend fun deleteConversation(conversationId: String): Boolean =
        withContext(Dispatchers.IO) { database.deleteConversation(conversationId) }

    override suspend fun createLocalNote(title: String, content: String): LocalNote =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            database.createLocalNote(
                id = UUID.randomUUID().toString(),
                title = title.trim().take(80).ifEmpty { "Untitled note" },
                content = content.trim(),
                now = now
            )
        }

    override suspend fun localNotes(): List<LocalNote> =
        withContext(Dispatchers.IO) { database.listLocalNotes() }

    override suspend fun deleteLocalNote(id: String): Boolean =
        withContext(Dispatchers.IO) { database.deleteLocalNote(id) }
}
