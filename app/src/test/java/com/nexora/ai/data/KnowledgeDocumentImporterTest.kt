package com.nexora.ai.data

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeDocumentImporterTest {
    @Test
    fun importsTextMarkdownAndJsonAsBoundedUtf8Text() {
        val text = KnowledgeDocumentImporter.read(
            "notes.txt", "text/plain", "Local notes".byteInput(), now = 10L
        )
        val markdown = KnowledgeDocumentImporter.read(
            "guide.md", "text/markdown", "# Local guide".byteInput(), now = 11L
        )
        val json = KnowledgeDocumentImporter.read(
            "settings.json", "application/json", """{"mode":"offline"}""".byteInput(), now = 12L
        )

        assertEquals(KnowledgeFormat.TEXT, text.format)
        assertEquals(KnowledgeFormat.MARKDOWN, markdown.format)
        assertEquals(KnowledgeFormat.JSON, json.format)
        assertEquals(10L, text.importedAt)
        assertEquals(listOf("Local notes"), text.chunks)
    }

    @Test
    fun rejectsUnsupportedExtensionsAndMismatchedMimeTypes() {
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeDocumentImporter.read("manual.pdf", "application/pdf", "not pdf".byteInput())
        }
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeDocumentImporter.read("manual.txt", "application/json", "text".byteInput())
        }
    }

    @Test
    fun rejectsFilesLargerThanTheByteLimit() {
        val oversized = ByteArray(KnowledgeDocumentImporter.MAX_DOCUMENT_BYTES + 1) { 'a'.code.toByte() }
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeDocumentImporter.read("large.txt", "text/plain", ByteArrayInputStream(oversized))
        }
    }

    @Test
    fun rejectsMalformedUtf8AndControlCharacters() {
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeDocumentImporter.read("broken.txt", "text/plain", ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            KnowledgeDocumentImporter.read("binary.txt", "text/plain", byteArrayOf(0x41, 0x00, 0x42).input())
        }
    }

    @Test
    fun chunksLongTextWithAStableOverlapAndBoundedChunkSize() {
        val content = "a".repeat(5_000)
        val chunks = KnowledgeDocumentImporter.chunk(content)

        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= KnowledgeDocumentImporter.CHUNK_CHARACTERS })
        assertEquals(
            chunks.first().takeLast(KnowledgeDocumentImporter.CHUNK_OVERLAP_CHARACTERS),
            chunks[1].take(KnowledgeDocumentImporter.CHUNK_OVERLAP_CHARACTERS)
        )
        assertFalse(chunks.any(String::isBlank))
    }

    @Test
    fun identicalBytesProduceTheSameContentHash() {
        val first = KnowledgeDocumentImporter.read("one.txt", "text/plain", "same content".byteInput())
        val second = KnowledgeDocumentImporter.read("two.txt", "text/plain", "same content".byteInput())

        assertEquals(first.sha256, second.sha256)
        assertEquals(64, first.sha256.length)
    }

    private fun String.byteInput() = ByteArrayInputStream(toByteArray(Charsets.UTF_8))
    private fun ByteArray.input() = ByteArrayInputStream(this)
}
