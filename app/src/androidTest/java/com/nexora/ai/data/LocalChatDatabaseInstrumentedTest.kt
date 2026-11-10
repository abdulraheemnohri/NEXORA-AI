package com.nexora.ai.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalChatDatabaseInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: LocalChatDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DATABASE_NAME)
        database = LocalChatDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun messagesPersistAcrossDatabaseReopenInChronologicalOrder() {
        database.createConversation("conversation-1", "Persistence", now = 1L)
        database.addMessage(StoredMessage("message-later", "conversation-1", "assistant", "Second", 200L))
        database.addMessage(StoredMessage("message-earlier", "conversation-1", "user", "First", 100L))

        database.close()
        database = LocalChatDatabase(context)

        assertEquals(
            listOf("message-earlier", "message-later"),
            database.listMessages("conversation-1").map(StoredMessage::id)
        )
    }

    @Test
    fun deletingConversationCascadesToItsMessages() {
        database.createConversation("conversation-2", "Delete", now = 1L)
        database.addMessage(StoredMessage("message-1", "conversation-2", "user", "Delete me", 2L))

        assertTrue(database.deleteConversation("conversation-2"))
        assertTrue(database.listMessages("conversation-2").isEmpty())
    }

    @Test
    fun conversationListUsesMostRecentMessageTimestamp() {
        database.createConversation("older", "Older", now = 1L)
        database.createConversation("newer", "Newer", now = 2L)
        database.addMessage(StoredMessage("message-older", "older", "user", "Old", 10L))
        database.addMessage(StoredMessage("message-newer", "newer", "user", "New", 20L))

        assertEquals(
            listOf("newer", "older"),
            database.listConversations().map(StoredConversation::id)
        )
    }

    @Test
    fun searchReturnsRecentMatchingMessagesWithinRequestedLimit() {
        database.createConversation("search-1", "Search", now = 1L)
        database.addMessage(StoredMessage("match-old", "search-1", "user", "Local model first", 10L))
        database.addMessage(StoredMessage("match-new", "search-1", "assistant", "LOCAL MODEL ready", 20L))
        database.addMessage(StoredMessage("no-match", "search-1", "assistant", "Cloud only", 30L))

        val results = database.searchMessages("model", limit = 1)

        assertEquals(1, results.size)
        assertEquals("match-new", results.single().messageId)
        assertEquals("search-1", results.single().conversationId)
    }

    @Test
    fun searchTreatsSqlWildcardCharactersAsLiteralQueryText() {
        database.createConversation("search-2", "Literal search", now = 1L)
        database.addMessage(StoredMessage("literal", "search-2", "user", "Transfer reached 100% locally", 10L))
        database.addMessage(StoredMessage("different", "search-2", "assistant", "Transfer reached 100 percent", 20L))

        val results = database.searchMessages("100%")

        assertEquals(listOf("literal"), results.map { it.messageId })
    }

    private companion object {
        const val DATABASE_NAME = "nexora_local.db"
    }
}
