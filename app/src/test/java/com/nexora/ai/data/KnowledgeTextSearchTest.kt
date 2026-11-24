package com.nexora.ai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class KnowledgeTextSearchTest {
    @Test
    fun matchesLatinTextWithoutCaseSensitivity() {
        val text = "NEXORA local electrical knowledge"
        val match = KnowledgeTextSearch.findMatch(text, "nexora")
        assertNotNull(match)
        assertEquals("NEXORA", text.substring(match!!.start, match.endExclusive))
    }

    @Test
    fun normalizesArabicPresentationFormsAndMapsBackToOriginalText() {
        val originalWord = "\uFEFB\u0632\u0645"
        val normalizedQuery = "\u0644\u0627\u0632\u0645"
        val text = "Urdu reference: " + originalWord + " safety note"
        val match = KnowledgeTextSearch.findMatch(text, normalizedQuery)
        assertNotNull(match)
        assertEquals(originalWord, text.substring(match!!.start, match.endExclusive))
    }

    @Test
    fun normalizesCombiningMarksWithoutChangingSourceOffsets() {
        val originalWord = "cafe\u0301"
        val text = "The word " + originalWord + " appears here."
        val match = KnowledgeTextSearch.findMatch(text, "CAFÉ")
        assertNotNull(match)
        assertEquals(originalWord, text.substring(match!!.start, match.endExclusive))
    }

    @Test
    fun returnsNoMatchForDifferentNormalizedText() {
        assertNull(KnowledgeTextSearch.findMatch("transformer safety", "generator"))
    }
}
