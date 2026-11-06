package com.nexora.ai.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import java.io.File
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

/** LiteRT-LM CPU adapter. Conversations live in memory and must be replayed after process death. */
class LiteRtLocalModelRuntime(private val cacheDirectory: File) : LocalModelRuntime {
    private val mutableState = MutableStateFlow<RuntimeState>(RuntimeState.NotConfigured)
    override val state = mutableState.asStateFlow()
    private val lifecycleMutex = Mutex()
    private val generationMutex = Mutex()
    private var engine: Engine? = null
    private val conversations = mutableMapOf<String, Conversation>()

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
        require(request.prompt.isNotBlank()) { "Prompt cannot be empty." }
        generationMutex.withLock {
            val conversation = lifecycleMutex.withLock {
                val activeEngine = checkNotNull(engine) { "Load a model before generating." }
                conversations.getOrPut(request.conversationId) {
                    val instruction = request.systemInstruction?.takeIf { it.isNotBlank() }
                    if (instruction == null) activeEngine.createConversation()
                    else activeEngine.createConversation(
                        ConversationConfig(systemInstruction = Contents.of(instruction))
                    )
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

    override suspend fun unload() = withContext(Dispatchers.IO) {
        generationMutex.withLock {
            lifecycleMutex.withLock {
                closeResources()
                mutableState.value = RuntimeState.NotConfigured
            }
        }
    }

    private fun closeResources() {
        conversations.values.forEach { runCatching { it.close() } }
        conversations.clear()
        engine?.let { runCatching { it.close() } }
        engine = null
    }
}
