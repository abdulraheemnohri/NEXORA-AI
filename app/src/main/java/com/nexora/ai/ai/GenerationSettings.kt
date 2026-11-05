package com.nexora.ai.ai

/** Locally persisted generation limits exposed by the chat settings screen. */
object GenerationSettings {
    const val DEFAULT_MAX_OUTPUT_TOKENS = 512
    val OUTPUT_TOKEN_LIMITS = listOf(256, 512, 1_024, 2_048)

    fun requireSupportedOutputLimit(value: Int): Int {
        require(value in OUTPUT_TOKEN_LIMITS) { "Unsupported output token limit: $value" }
        return value
    }
}
