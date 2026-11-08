package com.nexora.ai.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GenerationSettingsTest {
    @Test
    fun acceptsOnlySupportedOutputLimits() {
        GenerationSettings.OUTPUT_TOKEN_LIMITS.forEach { value ->
            assertEquals(value, GenerationSettings.requireSupportedOutputLimit(value))
        }
    }

    @Test
    fun rejectsUnsupportedOutputLimit() {
        assertThrows(IllegalArgumentException::class.java) {
            GenerationSettings.requireSupportedOutputLimit(768)
        }
    }
}
