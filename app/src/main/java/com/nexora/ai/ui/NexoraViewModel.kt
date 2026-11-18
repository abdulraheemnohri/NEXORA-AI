package com.nexora.ai.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexora.ai.agent.AgentToolExecutor
import com.nexora.ai.agent.SearchSavedConversationsTool
import com.nexora.ai.agent.ToolExecutionOutcome
import com.nexora.ai.agent.ToolInvocation
import com.nexora.ai.agent.ToolOutput
import com.nexora.ai.ai.Gemma4E2BModelDownloader
import com.nexora.ai.ai.GenerationEvent
import com.nexora.ai.ai.GenerationHistoryMessage
import com.nexora.ai.ai.GenerationRequest
import com.nexora.ai.ai.GenerationSettings
import com.nexora.ai.ai.LiteRtLocalModelRuntime
import com.nexora.ai.data.ChatRepository
import com.nexora.ai.data.ConversationSearchHit
import com.nexora.ai.data.LocalChatDatabase
import com.nexora.ai.data.KnowledgeDocumentSummary
import com.nexora.ai.data.KnowledgeImportOutcome
import com.nexora.ai.data.KnowledgeRepository
import com.nexora.ai.data.KnowledgeSearchHit
import com.nexora.ai.data.LocalKnowledgeDatabase
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChatBubble(
    val id: String,
    val role: String,
    val content: String
)

data class ConversationListItem(
    val id: String,
    val title: String,
    val updatedAt: Long
)

data class NexoraUiState(
    val messages: List<ChatBubble> = emptyList(),
    val conversations: List<ConversationListItem> = emptyList(),
    val selectedConversationId: String? = null,
    val historyMenuExpanded: Boolean = false,
    val pendingDeleteConversationId: String? = null,
    val isDeletingConversation: Boolean = false,
    val showSettings: Boolean = false,
    val showTools: Boolean = false,
    val localSearchEnabled: Boolean = false,
    val localSearchQuery: String = "",
    val localSearchResults: List<ConversationSearchHit> = emptyList(),
    val isLocalSearchRunning: Boolean = false,
    val localSearchError: String? = null,
    val showKnowledgeVault: Boolean = false,
    val knowledgeDocuments: List<KnowledgeDocumentSummary> = emptyList(),
    val knowledgeQuery: String = "",
    val knowledgeResults: List<KnowledgeSearchHit> = emptyList(),
    val isImportingKnowledge: Boolean = false,
    val isSearchingKnowledge: Boolean = false,
    val isDeletingKnowledgeDocument: Boolean = false,
    val pendingDeleteKnowledgeDocumentId: String? = null,
    val knowledgeError: String? = null,
    val knowledgeMessage: String? = null,
    val maxOutputTokens: Int = GenerationSettings.DEFAULT_MAX_OUTPUT_TOKENS,
    val input: String = "",
    val conversationReady: Boolean = false,
    val modelReady: Boolean = false,
    val modelStatus: String = "Checking local model",
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val isGenerating: Boolean = false,
    val error: String? = null
)

/** Application state holder; no network AI service or mock response path exists. */
class NexoraViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(LocalChatDatabase(application))
    private val knowledgeRepository = KnowledgeRepository(LocalKnowledgeDatabase(application))
    private val downloader = Gemma4E2BModelDownloader(application)
    private val runtime = LiteRtLocalModelRuntime(File(application.cacheDir, "litertlm"))
    private var conversationId: String? = null
    private val settingsPreferences = application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val initialMaxOutputTokens = runCatching {
        GenerationSettings.requireSupportedOutputLimit(
            settingsPreferences.getInt(KEY_MAX_OUTPUT_TOKENS, GenerationSettings.DEFAULT_MAX_OUTPUT_TOKENS)
        )
    }.getOrDefault(GenerationSettings.DEFAULT_MAX_OUTPUT_TOKENS)

    private val initialLocalSearchEnabled = settingsPreferences.getBoolean(KEY_LOCAL_SEARCH_ENABLED, false)

    private val mutableUiState = MutableStateFlow(
        NexoraUiState(
            maxOutputTokens = initialMaxOutputTokens,
            localSearchEnabled = initialLocalSearchEnabled
        )
    )
    val uiState = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = repository.conversations()
            if (existing.isEmpty()) {
                createAndActivateConversation()
            } else {
                activateConversation(existing.first().id)
            }
            loadInstalledModel()
        }
    }

    private suspend fun loadInstalledModel() {
        val installedModel = File(
            getApplication<Application>().filesDir,
            "models/${Gemma4E2BModelDownloader.MODEL_FILE_NAME}"
        )
        if (installedModel.isFile && installedModel.length() > 0L) {
            mutableUiState.update { it.copy(modelStatus = "Loading local Gemma model…") }
            val result = runtime.load(installedModel.absolutePath)
            mutableUiState.update {
                it.copy(
                    modelReady = result.isSuccess,
                    modelStatus = if (result.isSuccess) "Gemma 4 E2B · CPU ready" else "Model load failed",
                    error = result.exceptionOrNull()?.message
                )
            }
        } else {
            mutableUiState.update { it.copy(modelStatus = "Gemma model not installed") }
        }
    }

    private suspend fun refreshConversationList() {
        val items = repository.conversations().map {
            ConversationListItem(it.id, it.title, it.updatedAt)
        }
        mutableUiState.update { it.copy(conversations = items) }
    }

    private suspend fun activateConversation(id: String) {
        val storedMessages = repository.messages(id)
        conversationId = id
        mutableUiState.update { state ->
            state.copy(
                messages = storedMessages
                    .filter { it.role == "user" || it.role == "assistant" }
                    .map { ChatBubble(it.id, it.role, it.content) },
                selectedConversationId = id,
                conversationReady = true,
                historyMenuExpanded = false,
                pendingDeleteConversationId = null,
                error = null
            )
        }
        refreshConversationList()
    }

    private suspend fun createAndActivateConversation() {
        val id = repository.createConversation("New conversation")
        refreshConversationList()
        activateConversation(id)
    }

    fun updateInput(value: String) {
        mutableUiState.update { it.copy(input = value, error = null) }
    }

    fun openSettings() {
        mutableUiState.update { it.copy(showSettings = true) }
    }

    fun dismissSettings() {
        mutableUiState.update { it.copy(showSettings = false) }
    }

    fun openTools() {
        mutableUiState.update { it.copy(showTools = true, localSearchError = null) }
    }

    fun dismissTools() {
        mutableUiState.update { it.copy(showTools = false) }
    }


    fun openKnowledgeVault() {
        val current = mutableUiState.value
        if (current.isImportingKnowledge || current.isDeletingKnowledgeDocument) return
        mutableUiState.update {
            it.copy(showTools = false, showKnowledgeVault = true, knowledgeError = null, knowledgeMessage = null)
        }
        viewModelScope.launch { refreshKnowledgeDocuments() }
    }

    fun dismissKnowledgeVault() {
        if (mutableUiState.value.isDeletingKnowledgeDocument) return
        mutableUiState.update {
            it.copy(showKnowledgeVault = false, pendingDeleteKnowledgeDocumentId = null)
        }
    }

    private suspend fun refreshKnowledgeDocuments() {
        val documents = knowledgeRepository.documents()
        mutableUiState.update { it.copy(knowledgeDocuments = documents) }
    }

    fun importKnowledgeDocument(uri: Uri) {
        val current = mutableUiState.value
        if (current.isImportingKnowledge || current.isDeletingKnowledgeDocument) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(isImportingKnowledge = true, knowledgeError = null, knowledgeMessage = null)
            }
            try {
                val outcome = knowledgeRepository.importFromUri(
                    getApplication<Application>().contentResolver,
                    uri
                )
                when (outcome) {
                    is KnowledgeImportOutcome.Imported -> mutableUiState.update {
                        it.copy(
                            knowledgeQuery = "",
                            knowledgeResults = emptyList(),
                            knowledgeMessage = "Imported " + outcome.document.displayName +
                                " (" + outcome.document.chunkCount + " searchable chunks)."
                        )
                    }
                    KnowledgeImportOutcome.Duplicate -> mutableUiState.update {
                        it.copy(knowledgeMessage = "This exact document is already in the local vault.")
                    }
                }
                refreshKnowledgeDocuments()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(knowledgeError = error.message ?: "Unable to import the selected document.")
                }
            } finally {
                mutableUiState.update { it.copy(isImportingKnowledge = false) }
            }
        }
    }

    fun updateKnowledgeQuery(value: String) {
        mutableUiState.update {
            it.copy(knowledgeQuery = value.take(128), knowledgeError = null, knowledgeMessage = null)
        }
    }

    fun searchKnowledge() {
        val current = mutableUiState.value
        if (current.isSearchingKnowledge || current.isImportingKnowledge) return
        val query = current.knowledgeQuery.trim()
        if (query.length !in 2..128) {
            mutableUiState.update {
                it.copy(knowledgeError = "Enter 2–128 characters to search the local vault.")
            }
            return
        }
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(isSearchingKnowledge = true, knowledgeError = null, knowledgeMessage = null, knowledgeResults = emptyList())
            }
            try {
                val results = knowledgeRepository.search(query, limit = 20)
                mutableUiState.update { it.copy(knowledgeResults = results) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(knowledgeError = error.message ?: "Local knowledge search failed.")
                }
            } finally {
                mutableUiState.update { it.copy(isSearchingKnowledge = false) }
            }
        }
    }

    fun requestDeleteKnowledgeDocument(documentId: String) {
        val current = mutableUiState.value
        if (current.isImportingKnowledge || current.isDeletingKnowledgeDocument) return
        mutableUiState.update {
            it.copy(pendingDeleteKnowledgeDocumentId = documentId, knowledgeError = null)
        }
    }

    fun cancelDeleteKnowledgeDocument() {
        if (mutableUiState.value.isDeletingKnowledgeDocument) return
        mutableUiState.update { it.copy(pendingDeleteKnowledgeDocumentId = null) }
    }

    fun confirmDeleteKnowledgeDocument() {
        val documentId = mutableUiState.value.pendingDeleteKnowledgeDocumentId ?: return
        if (mutableUiState.value.isDeletingKnowledgeDocument || mutableUiState.value.isImportingKnowledge) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(isDeletingKnowledgeDocument = true, knowledgeError = null)
            }
            try {
                check(knowledgeRepository.deleteDocument(documentId)) {
                    "This document is no longer in the local vault."
                }
                refreshKnowledgeDocuments()
                mutableUiState.update {
                    it.copy(
                        pendingDeleteKnowledgeDocumentId = null,
                        knowledgeResults = emptyList(),
                        knowledgeMessage = "Document and its indexed chunks were deleted from this device."
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(
                        pendingDeleteKnowledgeDocumentId = null,
                        knowledgeError = error.message ?: "Unable to delete this document."
                    )
                }
            } finally {
                mutableUiState.update { it.copy(isDeletingKnowledgeDocument = false) }
            }
        }
    }

    fun setLocalSearchEnabled(enabled: Boolean) {
        settingsPreferences.edit().putBoolean(KEY_LOCAL_SEARCH_ENABLED, enabled).apply()
        mutableUiState.update {
            it.copy(
                localSearchEnabled = enabled,
                localSearchQuery = if (enabled) it.localSearchQuery else "",
                localSearchResults = if (enabled) it.localSearchResults else emptyList(),
                localSearchError = null
            )
        }
    }

    fun updateLocalSearchQuery(value: String) {
        mutableUiState.update {
            it.copy(localSearchQuery = value.take(128), localSearchError = null)
        }
    }

    fun searchLocalConversations() {
        val snapshot = mutableUiState.value
        if (!snapshot.localSearchEnabled || snapshot.isLocalSearchRunning) return
        val query = snapshot.localSearchQuery.trim()
        if (query.length !in 2..128) {
            mutableUiState.update { it.copy(localSearchError = "Enter 2–128 characters to search.") }
            return
        }
        val tool = SearchSavedConversationsTool(repository, enabledByUser = snapshot.localSearchEnabled)
        val invocation = ToolInvocation.userInitiated(SearchSavedConversationsTool.TOOL_ID, query)
        val executor = AgentToolExecutor(listOf(tool))
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(isLocalSearchRunning = true, localSearchError = null, localSearchResults = emptyList())
            }
            try {
                when (val outcome = executor.execute(invocation)) {
                    is ToolExecutionOutcome.Completed -> {
                        val matches = outcome.output as? ToolOutput.ConversationMatches
                            ?: error("Local search returned an unsupported result.")
                        mutableUiState.update { it.copy(localSearchResults = matches.matches) }
                    }
                    is ToolExecutionOutcome.NeedsApproval -> {
                        mutableUiState.update { it.copy(localSearchError = outcome.reason) }
                    }
                    is ToolExecutionOutcome.Denied -> {
                        mutableUiState.update { it.copy(localSearchError = outcome.reason) }
                    }
                    is ToolExecutionOutcome.Failed -> {
                        mutableUiState.update { it.copy(localSearchError = outcome.reason) }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(localSearchError = error.message ?: "Local search failed.")
                }
            } finally {
                mutableUiState.update { it.copy(isLocalSearchRunning = false) }
            }
        }
    }

    fun openSearchResult(conversationId: String) {
        val state = mutableUiState.value
        if (state.isGenerating || state.isDeletingConversation || state.isLocalSearchRunning) return
        dismissTools()
        openConversation(conversationId)
    }

    fun setMaxOutputTokens(value: Int) {
        val supported = runCatching {
            GenerationSettings.requireSupportedOutputLimit(value)
        }.getOrNull() ?: return
        settingsPreferences.edit().putInt(KEY_MAX_OUTPUT_TOKENS, supported).apply()
        mutableUiState.update { it.copy(maxOutputTokens = supported) }
    }

    fun toggleHistoryMenu() {
        if (mutableUiState.value.isGenerating || mutableUiState.value.isDeletingConversation) return
        mutableUiState.update { it.copy(historyMenuExpanded = !it.historyMenuExpanded) }
    }

    fun dismissHistoryMenu() {
        mutableUiState.update { it.copy(historyMenuExpanded = false) }
    }

    fun openConversation(id: String) {
        if (mutableUiState.value.isGenerating || mutableUiState.value.isDeletingConversation) return
        viewModelScope.launch { activateConversation(id) }
    }

    fun requestDeleteConversation(id: String) {
        if (mutableUiState.value.isGenerating || mutableUiState.value.isDeletingConversation) return
        mutableUiState.update {
            it.copy(historyMenuExpanded = false, pendingDeleteConversationId = id)
        }
    }

    fun cancelDeleteConversation() {
        if (mutableUiState.value.isDeletingConversation) return
        mutableUiState.update { it.copy(pendingDeleteConversationId = null) }
    }

    fun confirmDeleteConversation() {
        val targetId = mutableUiState.value.pendingDeleteConversationId ?: return
        if (mutableUiState.value.isGenerating || mutableUiState.value.isDeletingConversation) return
        mutableUiState.update { it.copy(isDeletingConversation = true) }
        viewModelScope.launch {
            try {
                val deleted = repository.deleteConversation(targetId)
                check(deleted) { "This conversation is no longer available." }
                runtime.forgetConversation(targetId)
                refreshConversationList()
                mutableUiState.update { it.copy(pendingDeleteConversationId = null) }

                if (conversationId == targetId) {
                    val nextConversation = mutableUiState.value.conversations.firstOrNull()
                    if (nextConversation == null) createAndActivateConversation()
                    else activateConversation(nextConversation.id)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(
                        pendingDeleteConversationId = null,
                        error = error.message ?: "Unable to delete this conversation."
                    )
                }
            } finally {
                mutableUiState.update { it.copy(isDeletingConversation = false) }
            }
        }
    }

    fun installModel() {
        if (mutableUiState.value.isDownloading || mutableUiState.value.modelReady) return
        viewModelScope.launch {
            mutableUiState.update {
                it.copy(isDownloading = true, downloadProgress = 0f, error = null, modelStatus = "Downloading Gemma 4 E2B…")
            }
            try {
                val modelFile = downloader.download { progress ->
                    mutableUiState.update { it.copy(downloadProgress = progress.fraction) }
                }
                mutableUiState.update { it.copy(modelStatus = "Verifying and loading model…") }
                val result = runtime.load(modelFile.absolutePath)
                result.getOrThrow()
                mutableUiState.update {
                    it.copy(
                        modelReady = true,
                        isDownloading = false,
                        downloadProgress = 1f,
                        modelStatus = "Gemma 4 E2B · CPU ready",
                        error = null
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableUiState.update {
                    it.copy(
                        isDownloading = false,
                        modelReady = false,
                        modelStatus = "Model installation failed",
                        error = error.message ?: "Unable to install the local model."
                    )
                }
            }
        }
    }

    fun sendMessage() {
        val current = mutableUiState.value
        val prompt = current.input.trim()
        val activeConversation = conversationId
        if (prompt.isEmpty() || !current.modelReady || current.isGenerating ||
            current.isDeletingConversation || activeConversation == null
        ) return

        val history = current.messages
            .filter { it.role == "user" || it.role == "assistant" }
            .map { GenerationHistoryMessage(it.role, it.content) }

        viewModelScope.launch {
            mutableUiState.update {
                it.copy(
                    input = "",
                    isGenerating = true,
                    error = null,
                    messages = it.messages + ChatBubble(UUID.randomUUID().toString(), "user", prompt)
                )
            }
            try {
                if (current.messages.isEmpty()) {
                    repository.updateConversationTitle(activeConversation, prompt)
                }
                repository.saveMessage(activeConversation, "user", prompt)
                refreshConversationList()

                val assistantId = UUID.randomUUID().toString()
                mutableUiState.update {
                    it.copy(messages = it.messages + ChatBubble(assistantId, "assistant", ""))
                }
                val assistantText = StringBuilder()
                runtime.generate(
                    GenerationRequest(
                        conversationId = activeConversation,
                        prompt = prompt,
                        systemInstruction = "You are NEXORA AI, a helpful private on-device assistant. Be accurate, concise, and honest about uncertainty.",
                        history = history,
                        maxOutputTokens = current.maxOutputTokens
                    )
                ).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> {
                            assistantText.append(event.text)
                            val partial = assistantText.toString()
                            mutableUiState.update { state ->
                                state.copy(messages = state.messages.map { bubble ->
                                    if (bubble.id == assistantId) bubble.copy(content = partial) else bubble
                                })
                            }
                        }
                        GenerationEvent.Completed -> Unit
                        is GenerationEvent.Failed -> error(event.reason)
                    }
                }
                if (assistantText.isNotBlank()) {
                    repository.saveMessage(activeConversation, "assistant", assistantText.toString())
                }
                refreshConversationList()
                mutableUiState.update { state ->
                    state.copy(
                        isGenerating = false,
                        messages = if (assistantText.isBlank()) state.messages.filterNot { it.id == assistantId }
                        else state.messages
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                mutableUiState.update { state ->
                    state.copy(
                        isGenerating = false,
                        messages = state.messages.filterNot { it.role == "assistant" && it.content.isBlank() },
                        error = error.message ?: "Local generation failed."
                    )
                }
            }
        }
    }

    fun newConversation() {
        if (mutableUiState.value.isGenerating || mutableUiState.value.isDeletingConversation) return
        viewModelScope.launch { createAndActivateConversation() }
    }

    fun shutdown() {
        viewModelScope.launch { runtime.unload() }
    }

    override fun onCleared() {
        knowledgeRepository.close()
        super.onCleared()
    }

    private companion object {
        const val PREFERENCES_NAME = "nexora_generation_settings"
        const val KEY_MAX_OUTPUT_TOKENS = "max_output_tokens"
        const val KEY_LOCAL_SEARCH_ENABLED = "local_conversation_search_enabled"
    }
}
