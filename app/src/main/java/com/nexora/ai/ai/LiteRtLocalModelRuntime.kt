package com.nexora.ai.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
import java.util.LinkedHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** LiteRT-LM CPU adapter. Stored transcripts are replayed when a conversation is recreated. */
class LiteRtLocalModelRuntime(private val cacheDirectory: File) : LocalModelRuntime {
    private val mutableState = MutableStateFlow<RuntimeState>(RuntimeState.NotConfigured)
    override val state = mutableState.asStateFlow()
    private val lifecycleMutex = Mutex()
    private val generationMutex = Mutex()
    private var engine: Engine? = null
    private val conversations = LinkedHashMap<String, Conversation>(4, 0.75f, true)

    override suspend fun load(modelPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            generationMutex.withLock {
                lifecycleMutex.withLock {
                    val model = File(modelPath)
                    require(model.isFile && model.canRead() && model.length() > 0L) {
                        "Model file is missing, unreadable, or empty: $modelPath"
                    }
                    mutableState.value = RuntimeState.Loading
                    closeResources()
                    check(cacheDirectory.exists() || cacheDirectory.mkdirs()) {
                        "Could not create LiteRT-LM cache directory."
                    }
                    val candidate = Engine(
                        EngineConfig(
                            modelPath = model.absolutePath,
                            backend = Backend.CPU(),
                            cacheDir = cacheDirectory.absolutePath
                        )
                    )
                    try {
                        candidate.initialize()
                        engine = candidate
                        mutableState.value = RuntimeState.Ready
                        Result.success(Unit)
                    } catch (error: Throwable) {
                        runCatching { candidate.close() }
                        throw error
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            mutableState.value = RuntimeState.NotConfigured
            throw cancelled
        } catch (error: Exception) {
            mutableState.value = RuntimeState.Error(error.message ?: "LiteRT-LM failed to load.")
            Result.failure(error)
        }
    }

    override fun generate(request: GenerationRequest): Flow<GenerationEvent> = flow {
        generationMutex.withLock {
            val conversation = lifecycleMutex.withLock {
                val activeEngine = checkNotNull(engine) { "Load a model before generating." }
                conversations[request.conversationId] ?: run {
                    val instruction = request.systemInstruction?.takeIf { it.isNotBlank() }
                    val replayMessages = ConversationHistoryPolicy.select(request.history)
                        .map(GenerationHistoryMessage::toLiteRtMessage)
                    val created = activeEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = instruction?.let { Contents.of(it) },
                            initialMessages = replayMessages,
                            maxOutputToken = request.maxOutputTokens,
                            automaticToolCalling = false
                        )
                    )
                    conversations[request.conversationId] = created
                    evictLeastRecentlyUsedConversations()
                    created
                }
            }

            conversation.sendMessageAsync(
                request.prompt,
                maxOutputToken = request.maxOutputTokens
            ).collect { message ->
                val chunk = message.toString()
                if (chunk.isNotEmpty()) emit(GenerationEvent.Token(chunk))
            }
            emit(GenerationEvent.Completed)
        }
    }.catch { error ->
        if (error is CancellationException) throw error
        emit(GenerationEvent.Failed(error.message ?: "Local generation failed."))
    }

    override suspend fun forgetConversation(conversationId: String): Unit = withContext(Dispatchers.IO) {
        generationMutex.withLock {
            lifecycleMutex.withLock {
                conversations.remove(conversationId)?.let { conversation ->
                    runCatching { conversation.close() }
                }
            }
        }
        Unit
    }

    override suspend fun unload() = withContext(Dispatchers.IO) {
        generationMutex.withLock {
            lifecycleMutex.withLock {
                closeResources()
                mutableState.value = RuntimeState.NotConfigured
            }
        }
    }

    private fun evictLeastRecentlyUsedConversations() {
        while (conversations.size > MAX_CACHED_CONVERSATIONS) {
            val oldestId = conversations.entries.iterator().next().key
            conversations.remove(oldestId)?.let { conversation ->
                runCatching { conversation.close() }
            }
        }
    }

    private fun closeResources() {
        conversations.values.forEach { runCatching { it.close() } }
        conversations.clear()
        engine?.let { runCatching { it.close() } }
        engine = null
    }

    private companion object {
        const val MAX_CACHED_CONVERSATIONS = 2
    }
}
