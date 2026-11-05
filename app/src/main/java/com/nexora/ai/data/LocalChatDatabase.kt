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
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Schema migrations must be additive. Never silently erase a user's local conversations.
        if (oldVersion < 2) {
            // Reserved for the first additive migration.
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
                arrayOf(message.createdAt, message.conversationId)
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
            "SELECT id,conversation_id,role,content,created_at FROM messages WHERE conversation_id=? ORDER BY created_at ASC, rowid ASC LIMIT ?",
            arrayOf(conversationId, safeLimit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) result += StoredMessage(
                cursor.getString(0), cursor.getString(1), cursor.getString(2),
                cursor.getString(3), cursor.getLong(4)
            )
        }
        return result
    }

    fun deleteConversation(id: String): Boolean =
        writableDatabase.delete("conversations", "id=?", arrayOf(id)) > 0

    companion object {
        private const val DATABASE_NAME = "nexora_local.db"
        private const val DATABASE_VERSION = 1
    }
}

data class StoredConversation(
    val id: String, val title: String, val createdAt: Long, val updatedAt: Long
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
