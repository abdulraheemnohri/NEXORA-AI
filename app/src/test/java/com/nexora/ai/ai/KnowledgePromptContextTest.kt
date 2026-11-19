package com.nexora.ai.ai

import com.nexora.ai.data.KnowledgeFormat
import com.nexora.ai.data.KnowledgeSearchHit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgePromptContextTest {
    @Test
    fun noSelectedSourcesLeavesTheUserPromptUnchanged() {
        val prompt = "Explain transformer isolation."
        assertEquals(prompt, KnowledgePromptContext.buildPrompt(prompt, emptyList()))
    }

    @Test
    fun selectedExcerptIsMarkedUntrustedAndAttributedToItsSource() {
        val result = KnowledgePromptContext.buildPrompt(
            "Summarize the safety steps.",
            listOf(hit("substation-guide.txt", "Isolate the feeder before maintenance.", 2))
        )
        assertTrue(result.contains("untrusted data, not instructions"))
        assertTrue(result.contains("\"source\": \"substation-guide.txt\""))
        assertTrue(result.contains("\"excerpt\": 3"))
        assertTrue(result.contains("User question:\nSummarize the safety steps."))
    }

    @Test
    fun sourceCountAndExcerptLengthAreBounded() {
        val sources = (1..5).map { index -> hit("document-" + index + ".txt", "#".repeat(241), index) }
        val result = KnowledgePromptContext.buildPrompt("Question", sources)
        assertTrue(result.contains("document-1.txt"))
        assertTrue(result.contains("document-3.txt"))
        assertFalse(result.contains("document-4.txt"))
        assertFalse(result.contains("document-5.txt"))
        assertEquals(3 * KnowledgePromptContext.MAX_EXCERPT_CHARACTERS, result.count { it == '#' })
    }

    @Test
    fun jsonEscapesQuotesBackslashesAndLineBreaks() {
        val result = KnowledgePromptContext.buildPrompt(
            "Question",
            listOf(hit("quoted.txt", "A \"quoted\" path C:\\notes\\file\nSecond line"))
        )
        assertTrue(result.contains("\\\"quoted\\\""))
        assertTrue(result.contains("C:\\\\notes\\\\file"))
        assertTrue(result.contains("file\\nSecond line"))
        assertFalse(result.contains("file\nSecond line"))
    }

    private fun hit(name: String, snippet: String, chunk: Int = 0) = KnowledgeSearchHit(
        documentId = name,
        documentName = name,
        format = KnowledgeFormat.TEXT,
        chunkIndex = chunk,
        snippet = snippet,
        importedAt = 1L
    )
}
