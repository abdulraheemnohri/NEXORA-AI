package com.nexora.ai.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Separate app-private database so knowledge migrations cannot disturb conversation history. */
class LocalKnowledgeDatabase(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE knowledge_documents (
                id TEXT PRIMARY KEY NOT NULL,
                display_name TEXT NOT NULL,
                format TEXT NOT NULL CHECK(format IN ('TEXT','MARKDOWN','JSON')),
                mime_type TEXT,
                byte_size INTEGER NOT NULL CHECK(byte_size > 0),
                sha256 TEXT NOT NULL UNIQUE,
                imported_at INTEGER NOT NULL
            )""".trimIndent()
        )
        db.execSQL(
            """CREATE TABLE knowledge_chunks (
                id TEXT PRIMARY KEY NOT NULL,
                document_id TEXT NOT NULL,
                chunk_index INTEGER NOT NULL CHECK(chunk_index >= 0),
                content TEXT NOT NULL,
                UNIQUE(document_id, chunk_index),
                FOREIGN KEY(document_id) REFERENCES knowledge_documents(id) ON DELETE CASCADE
            )""".trimIndent()
        )
        db.execSQL("CREATE INDEX index_knowledge_chunks_document ON knowledge_chunks(document_id, chunk_index)")
        db.execSQL("CREATE INDEX index_knowledge_documents_imported ON knowledge_documents(imported_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // This database is introduced at version 1. Future upgrades must be additive.
    }

    /** Returns false for an identical SHA-256 document; quota failures throw before any commit. */
    fun insertDocument(document: ImportedKnowledgeDocument): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val duplicate = db.rawQuery(
                "SELECT 1 FROM knowledge_documents WHERE sha256=? LIMIT 1",
                arrayOf(document.sha256)
            ).use { it.moveToFirst() }
            if (duplicate) return false

            db.rawQuery(
                "SELECT COUNT(*), COALESCE(SUM(byte_size),0) FROM knowledge_documents",
                null
            ).use { cursor ->
                check(cursor.moveToFirst())
                val count = cursor.getInt(0)
                val storedBytes = cursor.getLong(1)
                require(count < MAX_DOCUMENTS) {
                    "The local vault is limited to " + MAX_DOCUMENTS + " documents. Delete one before importing another."
                }
                require(storedBytes + document.byteSize <= MAX_TOTAL_BYTES) {
                    "The local vault has reached its " + (MAX_TOTAL_BYTES / (1024 * 1024)) + "-MiB storage limit."
                }
            }

            val values = ContentValues().apply {
                put("id", document.id)
                put("display_name", document.displayName)
                put("format", document.format.name)
                put("mime_type", document.mimeType)
                put("byte_size", document.byteSize)
                put("sha256", document.sha256)
                put("imported_at", document.importedAt)
            }
            db.insertOrThrow("knowledge_documents", null, values)

            document.chunks.forEachIndexed { index, chunk ->
                val chunkValues = ContentValues().apply {
                    put("id", document.id + "-" + index)
                    put("document_id", document.id)
                    put("chunk_index", index)
                    put("content", chunk)
                }
                db.insertOrThrow("knowledge_chunks", null, chunkValues)
            }
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    fun listDocuments(limit: Int = 100): List<KnowledgeDocumentSummary> {
        val safeLimit = limit.coerceIn(1, MAX_DOCUMENTS)
        val result = mutableListOf<KnowledgeDocumentSummary>()
        readableDatabase.rawQuery(
            """SELECT d.id,d.display_name,d.format,d.byte_size,
                (SELECT COUNT(*) FROM knowledge_chunks c WHERE c.document_id=d.id),
                d.imported_at
                FROM knowledge_documents d
                ORDER BY d.imported_at DESC,d.rowid DESC
                LIMIT ?""".trimIndent(),
            arrayOf(safeLimit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) result += KnowledgeDocumentSummary(
                id = cursor.getString(0),
                displayName = cursor.getString(1),
                format = KnowledgeFormat.valueOf(cursor.getString(2)),
                byteSize = cursor.getInt(3),
                chunkCount = cursor.getInt(4),
                importedAt = cursor.getLong(5)
            )
        }
        return result
    }

    /** Searches bounded local chunks only; no document text is sent to Gemma or a network service. */
    fun search(query: String, limit: Int = 20): List<KnowledgeSearchHit> {
        val normalized = query.trim()
        require(normalized.length in MIN_QUERY_LENGTH..MAX_QUERY_LENGTH) {
            "Search query must contain 2–128 characters."
        }
        val safeLimit = limit.coerceIn(1, MAX_RESULTS)
        val result = mutableListOf<KnowledgeSearchHit>()
        readableDatabase.rawQuery(
            """SELECT d.id,d.display_name,d.format,c.chunk_index,c.content,d.imported_at
                FROM knowledge_chunks c
                INNER JOIN knowledge_documents d ON d.id=c.document_id
                WHERE instr(c.content,?) > 0 OR instr(lower(c.content),lower(?)) > 0
                ORDER BY d.imported_at DESC,c.chunk_index ASC
                LIMIT ?""".trimIndent(),
            arrayOf(normalized, normalized, safeLimit.toString())
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val content = cursor.getString(4)
                val matchIndex = content.indexOf(normalized, ignoreCase = true).coerceAtLeast(0)
                val start = (matchIndex - SNIPPET_PREFIX_CHARACTERS).coerceAtLeast(0)
                val end = (start + MAX_SNIPPET_CHARACTERS).coerceAtMost(content.length)
                val snippet = buildString {
                    if (start > 0) append('…')
                    append(content.substring(start, end))
                    if (end < content.length) append('…')
                }.replace('\n', ' ').replace('\r', ' ').trim()
                result += KnowledgeSearchHit(
                    documentId = cursor.getString(0),
                    documentName = cursor.getString(1),
                    format = KnowledgeFormat.valueOf(cursor.getString(2)),
                    chunkIndex = cursor.getInt(3),
                    snippet = snippet,
                    importedAt = cursor.getLong(5)
                )
            }
        }
        return result
    }

    fun deleteDocument(id: String): Boolean =
        writableDatabase.delete("knowledge_documents", "id=?", arrayOf(id)) > 0

    companion object {
        const val DATABASE_NAME = "nexora_knowledge.db"
        const val MAX_DOCUMENTS = 50
        const val MAX_TOTAL_BYTES = 8L * 1024 * 1024
        private const val DATABASE_VERSION = 1
        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_QUERY_LENGTH = 128
        private const val MAX_RESULTS = 20
        private const val SNIPPET_PREFIX_CHARACTERS = 80
        private const val MAX_SNIPPET_CHARACTERS = 240
    }
}
