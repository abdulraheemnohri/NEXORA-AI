package com.nexora.ai.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalKnowledgeDatabaseInstrumentedTest {
    private lateinit var context: Context
    private lateinit var database: LocalKnowledgeDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalKnowledgeDatabase.DATABASE_NAME)
        database = LocalKnowledgeDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(LocalKnowledgeDatabase.DATABASE_NAME)
    }

    @Test
    fun documentAndChunksPersistAndSearchReturnsBoundedSnippet() {
        val imported = import("electrical-guide.txt", "A local transformer safety guide contains the word isolation.")
        assertTrue(database.insertDocument(imported))

        database.close()
        database = LocalKnowledgeDatabase(context)

        val documents = database.listDocuments()
        assertEquals(1, documents.size)
        assertEquals("electrical-guide.txt", documents.single().displayName)
        assertEquals(imported.chunks.size, documents.single().chunkCount)

        val results = database.search("transformer")
        assertEquals(1, results.size)
        assertEquals(documents.single().id, results.single().documentId)
        assertTrue(results.single().snippet.contains("transformer", ignoreCase = true))
        assertTrue(results.single().snippet.length <= 240)
    }

    @Test
    fun identicalContentIsRejectedAndDeleteCascadesToChunks() {
        val first = import("first.txt", "Repeated local content")
        val duplicate = import("renamed.txt", "Repeated local content")

        assertTrue(database.insertDocument(first))
        assertFalse(database.insertDocument(duplicate))
        assertEquals(1, database.listDocuments().size)

        assertTrue(database.deleteDocument(first.id))
        assertTrue(database.listDocuments().isEmpty())
        assertTrue(database.search("local").isEmpty())
    }

    @Test
    fun searchTreatsSqlWildcardCharactersAsLiteralText() {
        val imported = import("literal.txt", "The local budget is 100% protected.")
        assertTrue(database.insertDocument(imported))

        assertEquals(1, database.search("100%").size)
        assertTrue(database.search("100_").isEmpty())
    }

    @Test
    fun documentCountQuotaRejectsTheNextUniqueDocument() {
        repeat(LocalKnowledgeDatabase.MAX_DOCUMENTS) { index ->
            assertTrue(database.insertDocument(import("document-" + index + ".txt", "unique content " + index)))
        }

        val overflow = import("document-overflow.txt", "a different unique document")
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            database.insertDocument(overflow)
        }
        assertEquals(LocalKnowledgeDatabase.MAX_DOCUMENTS, database.listDocuments().size)
    }

    @Test
    fun deleteAllDocumentsRemovesEveryDocumentAndCascadedChunk() {
        val first = import("first.txt", "first shared vault content")
        val second = import("second.txt", "second shared vault content")
        assertTrue(database.insertDocument(first))
        assertTrue(database.insertDocument(second))
        assertEquals(2, database.search("shared").size)

        assertEquals(2, database.deleteAllDocuments())
        assertTrue(database.listDocuments().isEmpty())
        assertTrue(database.search("shared").isEmpty())
        assertEquals(0, database.deleteAllDocuments())
    }

    private fun import(name: String, content: String): ImportedKnowledgeDocument =
        KnowledgeDocumentImporter.read(
            name,
            "text/plain",
            ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)),
            now = 100L
        )
}
