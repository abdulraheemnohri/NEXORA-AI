package com.nexora.ai.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatInteractionPolicyTest {
    private val readyAndIdle = ChatInteractionState(
        modelReady = true,
        isGenerating = false,
        isDeletingConversation = false,
        isImportingKnowledge = false,
        isDeletingKnowledgeDocument = false,
        isSearchingKnowledge = false,
        isClearingKnowledgeVault = false
    )

    @Test
    fun allowsChatOnlyWhenModelIsReadyAndAllLocalOperationsAreIdle() {
        assertTrue(ChatInteractionPolicy.canSubmitMessage(readyAndIdle))
        assertFalse(ChatInteractionPolicy.canSubmitMessage(readyAndIdle.copy(modelReady = false)))
    }

    @Test
    fun blocksChatForEveryConflictingOperation() {
        val busyStates = listOf(
            readyAndIdle.copy(isGenerating = true),
            readyAndIdle.copy(isDeletingConversation = true),
            readyAndIdle.copy(isImportingKnowledge = true),
            readyAndIdle.copy(isDeletingKnowledgeDocument = true),
            readyAndIdle.copy(isSearchingKnowledge = true),
            readyAndIdle.copy(isClearingKnowledgeVault = true)
        )

        busyStates.forEach { state ->
            assertFalse(ChatInteractionPolicy.canSubmitMessage(state))
        }
    }
}
