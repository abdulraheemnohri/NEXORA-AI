package com.nexora.ai.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Small, dependency-free local store. All methods are synchronous; call them on Dispatchers.IO. */
class LocalChatDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE conversations (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE messages (
                id TEXT PRIMARY KEY NOT NULL,
                conversation_id TEXT NOT NULL,
                role TEXT NOT NULL CHECK(role IN ('user','assistant','system')),
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX index_messages_conversation_time ON messages(conversation_id, created_at)")
        createLocalNotesTable(db)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema migrations must be additive. Never silently erase a user's local conversations.
        if (oldVersion < 2 && newVersion >= 2) {
            createLocalNotesTable(db)
        }
    }

    fun createConversation(id: String, title: String, now: Long = System.currentTimeMillis()) {
        val values = ContentValues().apply {
            put("id", id); put("title", title); put("created_at", now); put("updated_at", now)
        }
        writableDatabase.insertOrThrow("conversations", null, values)
    }

    fun updateConversationTitle(id: String, title: String): Boolean {
        val safeTitle = title.trim().take(60).ifEmpty { "New conversation" }
        val values = ContentValues().apply { put("title", safeTitle) }
        return writableDatabase.update("conversations", values, "id=?", arrayOf(id)) > 0
    }

    fun addMessage(message: StoredMessage) {
        val values = ContentValues().apply {
            put("id", message.id)
            put("conversation_id", message.conversationId)
            put("role", message.role)
            put("content", message.content)
            put("created_at", message.createdAt)
        }
        writableDatabase.beginTransaction()
        try {
            writableDatabase.insertOrThrow("messages", null, values)
            writableDatabase.execSQL(
                "UPDATE conversations SET updated_at=? WHERE id=?",
                arrayOf<Any>(message.createdAt, message.conversationId)
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun listConversations(limit: Int = 100): List<StoredConversation> {
        val safeLimit = limit.coerceIn(1, 500)
        val result = mutableListOf<StoredConversation>()
        readableDatabase.rawQuery(
            "SELECT id,title,created_at,updated_at FROM conversations ORDER BY updated_at DESC LIMIT ?",
            arrayOf(safeLimit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) result += StoredConversation(
                cursor.getString(0), cursor.getString(1), cursor.getLong(2), cursor.getLong(3)
            )
        }
        return result
    }

    fun listMessages(conversationId: String, limit: Int = 500): List<StoredMessage> {
        val safeLimit = limit.coerceIn(1, 2000)
        val result = mutableListOf<StoredMessage>()
        readableDatabase.rawQuery(
            "SELECT id,conversation_id,role,content,created_at FROM messages WHERE conversation_id=? ORDER BY created_at DESC, rowid DESC LIMIT ?",
            arrayOf(conversationId, safeLimit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) result += StoredMessage(
                cursor.getString(0), cursor.getString(1), cursor.getString(2),
                cursor.getString(3), cursor.getLong(4)
            )
        }
        return result.asReversed()
    }

    /** Searches message bodies only; query text is bound as data, never interpreted as SQL. */
    fun searchMessages(query: String, limit: Int = 20): List<ConversationSearchHit> {
        val normalized = query.trim()
        require(normalized.length in MIN_SEARCH_QUERY_LENGTH..MAX_SEARCH_QUERY_LENGTH) {
            "Search query must contain 2–128 characters."
        }
        val safeLimit = limit.coerceIn(1, MAX_SEARCH_RESULTS)
        val result = mutableListOf<ConversationSearchHit>()
        readableDatabase.rawQuery(
            """SELECT m.id,c.id,c.title,m.role,m.searchable_content,m.created_at,m.rowid
                FROM (
                    SELECT id,conversation_id,role,
                        substr(content,1,?) AS searchable_content,created_at,rowid
                    FROM messages
                    WHERE role IN ('user','assistant')
                    ORDER BY rowid DESC
                    LIMIT ?
                ) m
                INNER JOIN conversations c ON c.id=m.conversation_id
                WHERE instr(m.searchable_content,?) > 0
                    OR instr(lower(m.searchable_content),lower(?)) > 0
                ORDER BY m.created_at DESC,m.rowid DESC
                LIMIT ?""".trimIndent(),
            arrayOf(
                MAX_SEARCHABLE_MESSAGE_CHARACTERS.toString(),
                MAX_SEARCH_SCAN_MESSAGES.toString(),
                normalized,
                normalized,
                safeLimit.toString()
            )
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val content = cursor.getString(4)
                val matchIndex = content.indexOf(normalized, ignoreCase = true)
                val start = if (matchIndex >= 0) (matchIndex - 64).coerceAtLeast(0) else 0
                val end = if (matchIndex >= 0) {
                    (matchIndex + normalized.length + 96).coerceAtMost(content.length)
                } else {
                    content.length.coerceAtMost(160)
                }
                val snippet = buildString {
                    if (start > 0) append('…')
                    append(content.substring(start, end))
                    if (end < content.length) append('…')
                }.replace('\n', ' ').replace('\r', ' ').trim()
                result += ConversationSearchHit(
                    messageId = cursor.getString(0),
                    conversationId = cursor.getString(1),
                    title = cursor.getString(2),
                    role = cursor.getString(3),
                    snippet = snippet,
                    createdAt = cursor.getLong(5)
                )
            }
        }
        return result
    }

    fun createLocalNote(
        id: String,
        title: String,
        content: String,
        now: Long = System.currentTimeMillis()
    ): LocalNote {
        val safeTitle = title.trim().take(MAX_LOCAL_NOTE_TITLE_CHARACTERS).ifEmpty { "Untitled note" }
        val safeContent = content.trim()
        require(safeContent.isNotEmpty()) { "Note content cannot be empty." }
        require(safeContent.length <= MAX_LOCAL_NOTE_CHARACTERS) { "Note exceeds the 4,000 character limit." }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val count = db.rawQuery("SELECT COUNT(*) FROM local_notes", null).use { cursor ->
                cursor.moveToFirst()
                cursor.getInt(0)
            }
            require(count < MAX_LOCAL_NOTES) {
                "Local notes limit reached (200). Delete a note before adding another."
            }
            val values = ContentValues().apply {
                put("id", id)
                put("title", safeTitle)
                put("content", safeContent)
                put("created_at", now)
                put("updated_at", now)
            }
            db.insertOrThrow("local_notes", null, values)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return LocalNote(id, safeTitle, safeContent, now, now)
    }

    fun listLocalNotes(limit: Int = MAX_LOCAL_NOTES): List<LocalNote> {
        val safeLimit = limit.coerceIn(1, 200)
        val result = mutableListOf<LocalNote>()
        readableDatabase.rawQuery(
            "SELECT id,title,content,created_at,updated_at FROM local_notes ORDER BY updated_at DESC,rowid DESC LIMIT ?",
            arrayOf(safeLimit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) result += LocalNote(
                cursor.getString(0), cursor.getString(1), cursor.getString(2),
                cursor.getLong(3), cursor.getLong(4)
            )
        }
        return result
    }

    fun deleteLocalNote(id: String): Boolean =
        writableDatabase.delete("local_notes", "id=?", arrayOf(id)) > 0

    private fun createLocalNotesTable(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE IF NOT EXISTS local_notes (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_local_notes_updated ON local_notes(updated_at)")
    }

    fun deleteConversation(id: String): Boolean =
        writableDatabase.delete("conversations", "id=?", arrayOf(id)) > 0

    companion object {
        private const val DATABASE_NAME = "nexora_local.db"
        private const val DATABASE_VERSION = 2
        private const val MAX_LOCAL_NOTE_TITLE_CHARACTERS = 80
        private const val MAX_LOCAL_NOTE_CHARACTERS = 4_000
        private const val MAX_LOCAL_NOTES = 200
        private const val MIN_SEARCH_QUERY_LENGTH = 2
        private const val MAX_SEARCH_QUERY_LENGTH = 128
        private const val MAX_SEARCH_RESULTS = 50
        private const val MAX_SEARCH_SCAN_MESSAGES = 2_000
        private const val MAX_SEARCHABLE_MESSAGE_CHARACTERS = 12_000
    }
}

data class StoredConversation(
    val id: String, val title: String, val createdAt: Long, val updatedAt: Long
)

data class ConversationSearchHit(
    val messageId: String,
    val conversationId: String,
    val title: String,
    val role: String,
    val snippet: String,
    val createdAt: Long
)

data class LocalNote(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Long,
    val updatedAt: Long
)

data class StoredMessage(
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(role in setOf("user", "assistant", "system")) { "Unsupported message role" }
        require(content.length <= 100_000) { "Message exceeds local storage limit" }
    }
}
