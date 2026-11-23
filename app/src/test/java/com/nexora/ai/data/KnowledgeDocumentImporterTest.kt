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
        assertFalse(chunks.any { it.isBlank() })
    }

    @Test
    fun chunkingDoesNotSplitUtf16SurrogatePairs() {
        val content = "a".repeat(KnowledgeDocumentImporter.CHUNK_CHARACTERS - 1) +
            "\uD83D\uDE00" + "b".repeat(KnowledgeDocumentImporter.CHUNK_CHARACTERS)

        val chunks = KnowledgeDocumentImporter.chunk(content)

        assertTrue(chunks.any { it.contains("\uD83D\uDE00") })
        assertTrue(chunks.all { chunk ->
            chunk.indices.all { index ->
                val character = chunk[index]
                (!Character.isHighSurrogate(character) ||
                    (index + 1 < chunk.length && Character.isLowSurrogate(chunk[index + 1]))) &&
                    (!Character.isLowSurrogate(character) ||
                        (index > 0 && Character.isHighSurrogate(chunk[index - 1])))
            }
        })
    }

    @Test
    fun manyMediumLinesCannotExceedTheChunkBudget() {
        val content = ("a".repeat(1_000) + "\n").repeat(225)
        val chunks = KnowledgeDocumentImporter.chunk(content)

        assertTrue(chunks.size <= KnowledgeDocumentImporter.MAX_CHUNKS)
        assertTrue(chunks.all { it.length <= KnowledgeDocumentImporter.CHUNK_CHARACTERS })
    }

    @Test
    fun identicalBytesProduceTheSameContentHash() {
        val first = KnowledgeDocumentImporter.read("one.txt", "text/plain", "same content".byteInput())
        val second = KnowledgeDocumentImporter.read("two.txt", "text/plain", "same content".byteInput())

        assertEquals(first.sha256, second.sha256)
        assertEquals(64, first.sha256.length)
    }

    @Test
    fun chunkingKeepsExtendedCharacterClustersTogetherAtChunkAndOverlapBoundaries() {
        val clusters = listOf(
            "👨‍👩‍👧‍👦",
            "👍🏽",
            "🇵🇰",
            "e\u0301"
        )
        clusters.forEach { cluster ->
            listOf(
                KnowledgeDocumentImporter.CHUNK_CHARACTERS -
                    KnowledgeDocumentImporter.CHUNK_OVERLAP_CHARACTERS - 1,
                KnowledgeDocumentImporter.CHUNK_CHARACTERS - 1
            ).forEach { position ->
                val content = "a".repeat(position) + cluster + "b".repeat(2_500)
                val chunks = KnowledgeDocumentImporter.chunk(content)
                assertTrue(chunks.all {
                    it.length <= KnowledgeDocumentImporter.CHUNK_CHARACTERS
                })

                val codePoints = cluster.codePoints().toArray()
                    .map { codePoint -> String(Character.toChars(codePoint)) }
                val chunksContainingAnyPart = chunks.filter { chunk ->
                    codePoints.any { part -> chunk.contains(part) }
                }
                assertTrue(chunksContainingAnyPart.isNotEmpty())
                assertTrue(chunksContainingAnyPart.all { it.contains(cluster) })
            }
        }
    }

    @Test
    fun pathologicalGraphemeLongerThanChunkLimitStillRespectsHardBounds() {
        val content = "x" + "\u0301".repeat(
            KnowledgeDocumentImporter.CHUNK_CHARACTERS + 100
        ) + "z"

        val chunks = KnowledgeDocumentImporter.chunk(content)

        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.size <= KnowledgeDocumentImporter.MAX_CHUNKS)
        assertTrue(chunks.all {
            it.length <= KnowledgeDocumentImporter.CHUNK_CHARACTERS
        })
    }

    private fun String.byteInput() = ByteArrayInputStream(toByteArray(Charsets.UTF_8))
    private fun ByteArray.input() = ByteArrayInputStream(this)
}
