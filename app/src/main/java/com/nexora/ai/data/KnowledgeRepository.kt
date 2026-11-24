package com.nexora.ai.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** SAF access, document parsing, and SQLite work all run on Dispatchers.IO. */
class KnowledgeRepository(private val database: LocalKnowledgeDatabase) {
    suspend fun importFromUri(
        resolver: ContentResolver,
        uri: Uri
    ): KnowledgeImportOutcome = withContext(Dispatchers.IO) {
        val coroutineContext = currentCoroutineContext()
        coroutineContext.ensureActive()
        val scheme = uri.scheme
        require(scheme == "content" || scheme == "file" || scheme == "android.resource") {
            "Only Android local/resource document URIs are supported; remote HTTP URIs are not read."
        }
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
            KnowledgeDocumentImporter.read(
                displayName,
                mimeType,
                it,
                checkActive = { coroutineContext.ensureActive() }
            )
        }
        coroutineContext.ensureActive()
        persist(document) { coroutineContext.ensureActive() }
    }

    private fun persist(
        document: ImportedKnowledgeDocument,
        checkActive: () -> Unit
    ): KnowledgeImportOutcome {
        val inserted = database.insertDocument(document, checkActive)
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

    suspend fun deleteAllDocuments(): Int =
        withContext(Dispatchers.IO) { database.deleteAllDocuments() }

    fun close() = database.close()
}
