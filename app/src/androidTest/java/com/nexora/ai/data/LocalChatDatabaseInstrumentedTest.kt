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
        database.addMessage(StoredMessage("system-match", "search-1", "system", "Internal model instructions", 40L))

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

    @Test
    fun searchOnlyScansTheBoundedPrefixOfEachMessage() {
        database.createConversation("search-3", "Bounded search", now = 1L)
        val content = "a".repeat(12_000) + "needle"
        database.addMessage(StoredMessage("long-message", "search-3", "user", content, 10L))

        assertTrue(database.searchMessages("needle").isEmpty())
    }


    @Test
    fun localNotesCanBeCreatedListedAndDeleted() {
        val created = database.createLocalNote(
            id = "note-1",
            title = "Solar log",
            content = "R phase = 230 V",
            now = 10L
        )

        assertEquals("Solar log", created.title)
        assertEquals("R phase = 230 V", created.content)
        assertEquals(listOf("note-1"), database.listLocalNotes().map(LocalNote::id))
        assertTrue(database.deleteLocalNote("note-1"))
        assertTrue(database.listLocalNotes().isEmpty())
    }


    @Test
    fun localNoteStoreRejectsMoreThanTwoHundredNotes() {
        repeat(200) { index ->
            database.createLocalNote(
                id = "quota-note-$index",
                title = "Note $index",
                content = "Bounded local note",
                now = index.toLong()
            )
        }

        try {
            database.createLocalNote("quota-overflow", "Overflow", "Must not be stored")
            throw AssertionError("The local note count must be bounded.")
        } catch (expected: IllegalArgumentException) {
            assertEquals(
                "Local notes limit reached (200). Delete a note before adding another.",
                expected.message
            )
        }
        assertEquals(200, database.listLocalNotes().size)
    }

    @Test
    fun versionOneMigrationPreservesChatsAndAddsLocalNotesTable() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
        val legacy = context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null)
        legacy.execSQL(
            "CREATE TABLE conversations (id TEXT PRIMARY KEY NOT NULL,title TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)"
        )
        legacy.execSQL(
            "CREATE TABLE messages (id TEXT PRIMARY KEY NOT NULL,conversation_id TEXT NOT NULL,role TEXT NOT NULL CHECK(role IN ('user','assistant','system')),content TEXT NOT NULL,created_at INTEGER NOT NULL,FOREIGN KEY(conversation_id) REFERENCES conversations(id) ON DELETE CASCADE)"
        )
        legacy.execSQL("CREATE INDEX index_messages_conversation_time ON messages(conversation_id, created_at)")
        legacy.execSQL("INSERT INTO conversations(id,title,created_at,updated_at) VALUES('legacy','Legacy chat',1,2)")
        legacy.execSQL("INSERT INTO messages(id,conversation_id,role,content,created_at) VALUES('legacy-message','legacy','user','Keep this chat',2)")
        legacy.version = 1
        legacy.close()

        database = LocalChatDatabase(context)

        assertEquals("Legacy chat", database.listConversations().single().title)
        assertEquals("Keep this chat", database.listMessages("legacy").single().content)
        database.createLocalNote("migrated-note", "Migrated note", "New table is available", 3L)
        assertEquals("Migrated note", database.listLocalNotes().single().title)
    }

    private companion object {
        const val DATABASE_NAME = "nexora_local.db"
    }
}
