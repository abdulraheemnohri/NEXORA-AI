package com.nexora.ai.ui

/**
 * Shared availability policy for the chat composer and ViewModel.
 * UI disabling is a usability boundary; the ViewModel rechecks this policy
 * immediately before accepting a send request.
 */
data class ChatInteractionState(
    val modelReady: Boolean,
    val isGenerating: Boolean,
    val isDeletingConversation: Boolean,
    val isImportingKnowledge: Boolean,
    val isDeletingKnowledgeDocument: Boolean,
    val isSearchingKnowledge: Boolean,
    val isClearingKnowledgeVault: Boolean
)

object ChatInteractionPolicy {
    fun canSubmitMessage(state: ChatInteractionState): Boolean =
        state.modelReady &&
            !state.isGenerating &&
            !state.isDeletingConversation &&
            !state.isImportingKnowledge &&
            !state.isDeletingKnowledgeDocument &&
            !state.isSearchingKnowledge &&
            !state.isClearingKnowledgeVault
}
