package com.nexora.ai.ai

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
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
                    activeEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = instruction?.let(Contents::of),
                            automaticToolCalling = false
                        )
                    )
                }
            }
            conversation.stream(request).collect { message ->
                val chunk = message.toString()
                if (chunk.isNotEmpty()) emit(GenerationEvent.Token(chunk))
            }
            emit(GenerationEvent.Completed)
        }
    }.flowOn(Dispatchers.IO).catch { error ->
        if (error is CancellationException) throw error
        emit(GenerationEvent.Failed(error.message ?: "Local generation failed."))
    }

    /**
     * Uses LiteRT-LM's callback overload rather than its Flow overload.
     *
     * In v0.18.1 the built-in Flow adapter has an empty awaitClose block. If the collector is
     * cancelled, native inference can continue after the Flow returns. We therefore keep the
     * generation mutex until the native callback reports onDone/onError. This stops downstream
     * delivery immediately but deliberately lets the native request drain before the conversation
     * can be reused or unloaded.
     *
     * cancelProcess() is intentionally not called: the pinned native API documents that cancelling
     * a Conversation poisons its session, so it must not be reused after cancellation.
     */
    private fun Conversation.stream(request: GenerationRequest): Flow<Message> = callbackFlow {
        val nativeCompletion = CompletableDeferred<Unit>()
        val callback = object : MessageCallback {
            override fun onMessage(message: Message) {
                trySend(message)
            }

            override fun onDone() {
                nativeCompletion.complete(Unit)
                close()
            }

            override fun onError(throwable: Throwable) {
                nativeCompletion.complete(Unit)
                close(throwable)
            }
        }

        try {
            sendMessageAsync(
                request.prompt,
                callback,
                maxOutputToken = request.maxOutputTokens
            )
        } catch (error: Exception) {
            nativeCompletion.complete(Unit)
            close(error)
        }

        try {
            awaitClose { }
        } finally {
            awaitNativeCompletion(nativeCompletion)
        }
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

/**
 * Do not release the runtime's generation/lifecycle lock while a native callback request is active.
 * This is non-cancellable so collector cancellation cannot bypass native completion cleanup.
 */
internal suspend fun awaitNativeCompletion(completion: Deferred<Unit>) {
    withContext(NonCancellable) {
        completion.await()
    }
}
