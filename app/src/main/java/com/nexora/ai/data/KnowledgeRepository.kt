package com.nexora.ai.data

import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** All document parsing and SQLite work runs away from the main thread. */
class KnowledgeRepository(private val database: LocalKnowledgeDatabase) {
    suspend fun importDocument(
        displayName: String,
        mimeType: String?,
        input: InputStream
    ): KnowledgeImportOutcome = withContext(Dispatchers.IO) {
        val document = KnowledgeDocumentImporter.read(displayName, mimeType, input)
        val inserted = database.insertDocument(document)
        if (inserted) {
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
