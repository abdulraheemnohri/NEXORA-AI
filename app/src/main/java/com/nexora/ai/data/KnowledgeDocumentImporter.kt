package com.nexora.ai.data

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.text.BreakIterator
import java.util.Locale
import java.util.UUID

enum class KnowledgeFormat {
    TEXT,
    MARKDOWN,
    JSON
}

data class ImportedKnowledgeDocument(
    val id: String,
    val displayName: String,
    val format: KnowledgeFormat,
    val mimeType: String?,
    val byteSize: Int,
    val sha256: String,
    val chunks: List<String>,
    val importedAt: Long
)

data class KnowledgeDocumentSummary(
    val id: String,
    val displayName: String,
    val format: KnowledgeFormat,
    val byteSize: Int,
    val chunkCount: Int,
    val importedAt: Long
)

data class KnowledgeSearchHit(
    val documentId: String,
    val documentName: String,
    val format: KnowledgeFormat,
    val chunkIndex: Int,
    val snippet: String,
    val importedAt: Long
)

sealed interface KnowledgeImportOutcome {
    data class Imported(val document: KnowledgeDocumentSummary) : KnowledgeImportOutcome
    data object Duplicate : KnowledgeImportOutcome
}

/**
 * Bounded, deterministic text ingestion. The source URI is read once by the caller;
 * no URI permission is retained and no content is sent to the model or network.
 */
object KnowledgeDocumentImporter {
    const val MAX_DOCUMENT_BYTES = 512 * 1024
    const val MAX_DOCUMENT_CHARACTERS = 450_000
    const val CHUNK_CHARACTERS = 2_000
    const val CHUNK_OVERLAP_CHARACTERS = 200
    const val MAX_CHUNKS = 256

    fun read(
        fileName: String,
        mimeType: String?,
        input: InputStream,
        now: Long = System.currentTimeMillis()
    ): ImportedKnowledgeDocument {
        val safeName = normalizeDisplayName(fileName)
        val format = resolveFormat(safeName, mimeType)
        val bytes = readBounded(input)
        require(bytes.isNotEmpty()) { "The selected document is empty." }

        val decoded = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            throw IllegalArgumentException("Only valid UTF-8 text documents are supported.")
        }

        require(decoded.length <= MAX_DOCUMENT_CHARACTERS) {
            "The document exceeds the " + MAX_DOCUMENT_CHARACTERS + "-character limit."
        }
        require(decoded.none { character ->
            character.code == 0 || (character.code < 32 && character != '\n' && character != '\r' && character != '\t') ||
                character.code == 127
        }) {
            "The document contains unsupported control characters."
        }

        val normalized = decoded.replace("\r\n", "\n").replace('\r', '\n').trim()
        require(normalized.isNotEmpty()) { "The selected document contains no searchable text." }
        val chunks = chunk(normalized)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

        return ImportedKnowledgeDocument(
            id = UUID.randomUUID().toString(),
            displayName = safeName,
            format = format,
            mimeType = mimeType?.substringBefore(';')?.trim()?.take(128),
            byteSize = bytes.size,
            sha256 = digest,
            chunks = chunks,
            importedAt = now
        )
    }

    fun chunk(text: String): List<String> {
        require(text.isNotBlank()) { "Document text cannot be blank." }
        require(text.length <= MAX_DOCUMENT_CHARACTERS) { "Document text exceeds the ingestion limit." }

        val chunks = mutableListOf<String>()
        val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
        var start = 0
        while (start < text.length) {
            val targetEnd = (start + CHUNK_CHARACTERS).coerceAtMost(text.length)
            val end = findChunkEnd(boundaries, text, start, targetEnd)
            val value = text.substring(start, end).trim()
            if (value.isNotEmpty()) chunks += value
            require(chunks.size <= MAX_CHUNKS) {
                "The document would create more than " + MAX_CHUNKS + " searchable chunks."
            }
            if (end >= text.length) break
            start = findOverlapStart(boundaries, text, start, end)
        }
        require(chunks.isNotEmpty()) { "The selected document contains no searchable text." }
        return chunks
    }

    /** Prefer a BreakIterator character boundary without exceeding the hard UTF-16 chunk cap. */
    private fun findChunkEnd(
        iterator: BreakIterator,
        text: String,
        start: Int,
        targetEnd: Int
    ): Int {
        if (targetEnd >= text.length) return text.length
        val boundary = iterator.preceding(targetEnd + 1)
        return if (boundary > start) boundary else codePointBoundaryBefore(text, targetEnd, start)
    }

    /** Move the overlap start forward to a character boundary; fall back only for an overlong cluster. */
    private fun findOverlapStart(
        iterator: BreakIterator,
        text: String,
        start: Int,
        end: Int
    ): Int {
        val target = (end - CHUNK_OVERLAP_CHARACTERS).coerceAtLeast(start + 1)
        if (target >= end) return end
        val boundary = if (iterator.isBoundary(target)) target else iterator.following(target)
        return if (boundary != BreakIterator.DONE && boundary <= end) {
            boundary
        } else {
            codePointBoundaryAfter(text, target, end)
        }
    }

    /** Keep a valid UTF-16 surrogate pair intact if a single cluster exceeds the chunk cap. */
    private fun codePointBoundaryBefore(text: String, offset: Int, lowerBound: Int): Int =
        if (offset > lowerBound && offset < text.length &&
            Character.isHighSurrogate(text[offset - 1]) && Character.isLowSurrogate(text[offset])) {
            offset - 1
        } else offset

    private fun codePointBoundaryAfter(text: String, offset: Int, upperBound: Int): Int =
        if (offset > 0 && offset < upperBound &&
            Character.isHighSurrogate(text[offset - 1]) && Character.isLowSurrogate(text[offset])) {
            offset + 1
        } else offset

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) {
                val single = input.read()
                if (single < 0) break
                total += 1
                require(total <= MAX_DOCUMENT_BYTES) {
                    "The document exceeds the " + (MAX_DOCUMENT_BYTES / 1024) + "-KiB import limit."
                }
                output.write(single)
            } else {
                total += count
                require(total <= MAX_DOCUMENT_BYTES) {
                    "The document exceeds the " + (MAX_DOCUMENT_BYTES / 1024) + "-KiB import limit."
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toByteArray()
    }

    private fun normalizeDisplayName(fileName: String): String {
        val safeName = fileName.substringAfterLast('/').substringAfterLast('\\').trim().take(120)
        require(safeName.isNotEmpty() && safeName.none { it.code < 32 || it.code == 127 }) {
            "The selected document has an invalid file name."
        }
        return safeName
    }

    private fun resolveFormat(fileName: String, mimeType: String?): KnowledgeFormat {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val mime = mimeType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
        val format = when (extension) {
            "txt" -> KnowledgeFormat.TEXT
            "md", "markdown" -> KnowledgeFormat.MARKDOWN
            "json" -> KnowledgeFormat.JSON
            else -> throw IllegalArgumentException("Supported formats are TXT, Markdown, and JSON.")
        }

        val compatible = when (format) {
            KnowledgeFormat.TEXT -> mime == null || mime.startsWith("text/") || mime == "application/octet-stream"
            KnowledgeFormat.MARKDOWN -> mime == null || mime == "text/plain" ||
                mime == "text/markdown" || mime == "text/x-markdown" || mime == "application/octet-stream"
            KnowledgeFormat.JSON -> mime == null || mime == "application/json" || mime == "text/json" ||
                mime == "text/plain" || mime == "application/octet-stream" || mime.endsWith("+json")
        }
        require(compatible) { "The selected file type does not match its file extension." }
        return format
    }
}
