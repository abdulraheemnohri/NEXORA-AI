package com.nexora.ai.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** SAF access, document parsing, and SQLite work all run on Dispatchers.IO. */
class KnowledgeRepository(private val database: LocalKnowledgeDatabase) {
    suspend fun importFromUri(
        resolver: ContentResolver,
        uri: Uri
    ): KnowledgeImportOutcome = withContext(Dispatchers.IO) {
        val displayName = resolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        } ?: uri.lastPathSegment ?: "document.txt"
        val mimeType = resolver.getType(uri)
        val input = resolver.openInputStream(uri)
            ?: error("Android could not open the selected document.")
        val document = input.use {
            KnowledgeDocumentImporter.read(displayName, mimeType, it)
        }
        persist(document)
    }

    private fun persist(document: ImportedKnowledgeDocument): KnowledgeImportOutcome {
        val inserted = database.insertDocument(document)
        return if (inserted) {
            KnowledgeImportOutcome.Imported(
                KnowledgeDocumentSummary(
                    id = document.id,
                    displayName = document.displayName,
                    format = document.format,
                    byteSize = document.byteSize,
                    chunkCount = document.chunks.size,
                    importedAt = document.importedAt
                )
            )
        } else {
            KnowledgeImportOutcome.Duplicate
        }
    }

    suspend fun documents(): List<KnowledgeDocumentSummary> =
        withContext(Dispatchers.IO) { database.listDocuments() }

    suspend fun search(query: String, limit: Int = 20): List<KnowledgeSearchHit> =
        withContext(Dispatchers.IO) { database.search(query, limit) }

    suspend fun deleteDocument(id: String): Boolean =
        withContext(Dispatchers.IO) { database.deleteDocument(id) }

    fun close() = database.close()
}
