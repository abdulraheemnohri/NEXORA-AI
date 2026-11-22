package com.nexora.ai.data

import java.text.BreakIterator
import java.text.Normalizer
import java.util.Locale

/**
 * Unicode-normalized substring matching for the bounded local knowledge corpus.
 *
 * NFKC and locale-independent lowercase are applied per character boundary. The
 * offset map keeps excerpts anchored to the original, unchanged source text.
 * This is not linguistic stemming, transliteration, or semantic retrieval.
 */
internal object KnowledgeTextSearch {
    data class MatchRange(val start: Int, val endExclusive: Int)

    fun findMatch(text: String, query: String): MatchRange? {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return null

        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
        iterator.setText(text)
        val normalizedText = StringBuilder(text.length)
        val originalStarts = ArrayList<Int>(text.length)
        val originalEnds = ArrayList<Int>(text.length)

        var clusterStart = iterator.first()
        var clusterEnd = iterator.next()
        while (clusterEnd != BreakIterator.DONE) {
            val normalizedCluster = normalize(text.substring(clusterStart, clusterEnd))
            normalizedCluster.forEach { character ->
                normalizedText.append(character)
                originalStarts.add(clusterStart)
                originalEnds.add(clusterEnd)
            }
            clusterStart = clusterEnd
            clusterEnd = iterator.next()
        }

        val normalizedStart = normalizedText.indexOf(normalizedQuery)
        if (normalizedStart < 0) return null
        val normalizedLast = normalizedStart + normalizedQuery.length - 1
        if (normalizedLast !in originalStarts.indices) return null
        return MatchRange(originalStarts[normalizedStart], originalEnds[normalizedLast])
    }

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
}
