# NEXORA AI Architecture

NEXORA is a native Android application. Its application backend runs in-process in Kotlin. Local LLM inference is provided only by Google's LiteRT-LM runtime.

## Layers

- Presentation: Jetpack Compose chat, conversation history, and generation settings.
- Application: ViewModel orchestration, model lifecycle, and task lifecycle.
- LLM core: LiteRT-LM runtime contract, bounded transcript assembly, generation limits, and streaming events.
- On-device task runtime: future non-LLM LiteRT Compiled Model adapters, isolated from the LLM runtime.
- Agent: risk policy and approval boundary; broader planner/tool execution remains roadmap work.
- Memory and knowledge: planned local retrieval and ingestion components.
- Data: app-private SQLite conversation/message storage and SharedPreferences for local generation settings.
- Platform: Android permissions, app-private storage, and lifecycle management.

## Runtime integration rules

### LLM path

`LocalModelRuntime` is the text-generation boundary. The production adapter is pinned to LiteRT-LM Android v0.18.1. Do not substitute Ollama, llama.cpp, remote inference, fabricated responses, or guessed APIs.

The adapter uses CPU inference and passes the configured `maxOutputToken` limit to the pinned v0.18.1 callback overload of `Conversation.sendMessageAsync`. Streamed `Message` content is extracted with `Message.toString()`; the pinned `Message` type has no public `text` property. Every conversation sets `automaticToolCalling = false`.

Generation and conversation creation run on `Dispatchers.IO`. A lifecycle mutex protects native engine/session ownership. A generation mutex serializes native requests and remains held until the callback reports `onDone` or `onError`, even if the downstream collector is cancelled. This is a drain-to-completion policy, not immediate native cancellation. The pinned Flow adapter has an empty `awaitClose` cleanup block, and the native `CancelProcess()` API documents that the session is poisoned after cancellation. NEXORA therefore does not call native cancellation or reuse a poisoned session. If a terminal callback never arrives, the gate can remain held; this fail-safe behavior still requires physical-device validation.

### Conversation restoration and replay

SQLite is the durable source of truth. The ViewModel restores the most recently updated conversation at startup, exposes the 100 most recently updated conversations, and loads up to 500 messages per selected conversation in chronological order.

Before a new user prompt is persisted, the ViewModel snapshots the currently displayed user/assistant turns. Those prior turns are passed as `GenerationRequest.history`; the current prompt is sent separately to LiteRT-LM. This prevents replaying the just-submitted prompt twice. On first creation of a native conversation, the runtime maps stored roles to `Message.user` and `Message.model` and supplies them through `ConversationConfig.initialMessages`.

Replay is bounded to the latest 16 messages and 12,000 characters before the transcript is copied into native state. If the oldest selected message exceeds the remaining budget, its prefix is replaced by an explicit truncation marker and the newest suffix is retained. The character budget is a memory guard, not a tokenizer-aware context guarantee.

The runtime holds at most two native conversations in an access-ordered LRU cache. When an entry is evicted or a user confirms deletion, the native conversation is closed. Deleting a conversation removes its SQLite row and dependent messages through the foreign-key cascade; the active chat then switches to the most recently updated remaining conversation or creates a new empty one.

### Generation settings

The output-token setting is stored in app-private SharedPreferences. Supported choices are 256, 512, 1,024, and 2,048; default is 512. The selected value is validated and passed to LiteRT-LM as the per-decode output-token limit. It does not override model context limits or guarantee a particular response length.

### Non-LLM model path

Use Google's LiteRT Compiled Model API for new non-LLM model tasks such as speech recognition, vision, or embeddings. Keep these adapters separate from `LocalModelRuntime`; a task model is not a substitute for the Gemma `.litertlm` model. Do not introduce the legacy TensorFlow Lite `Interpreter` API.

Each task adapter must own its model and tensor buffers, run on a confined serial background dispatcher, reuse buffers where appropriate, and close native resources deterministically. Preprocessing and postprocessing are part of the model contract and belong beside the adapter, not in Compose.

Before integrating a model, verify its license, artifact revision, input/output signatures, preprocessing contract, source-model reference outputs, and target-device behavior. Record accelerator residency logs because a successful run alone does not prove GPU execution; partial CPU fallback may occur. Benchmark enqueue plus output readback, not enqueue alone.

See [Google AI Edge integration](GOOGLE_AI_EDGE_INTEGRATION.md) for the workload matrix, Pixel 6 constraints, and verification checklist.

## Build toolchain

The app targets Java 17 bytecode. Kotlin 2.3.21 uses the typed `kotlin.compilerOptions.jvmTarget` DSL. CI uses JDK 21 for the pinned LiteRT-LM dependency's test runtime. GitHub Actions provisions Gradle 8.13; a Gradle wrapper is still a release gate.

## Safety

Treat model-generated tool requests as untrusted. Validate schemas, authorize each capability, constrain paths, apply timeouts, and record audit events. Destructive or externally visible actions require explicit user confirmation. Do not enable automatic tool execution by default.

## Device target

Initial target is Pixel 6 / 6 GB RAM. CPU inference is the baseline. Model loading, context size, CPU behavior, peak memory, and sustained thermal performance must be measured on physical hardware. Do not infer Google Tensor NPU support from SoC branding. No Pixel 6 performance claim is made without device evidence.
