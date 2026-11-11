# NEXORA AI Architecture

NEXORA is a native Android application. Its application backend runs in-process in Kotlin. Local LLM inference is provided only by Google's LiteRT-LM runtime.

## Layers

- Presentation: Jetpack Compose chat and local state.
- Application: ViewModel orchestration, model lifecycle, and task lifecycle.
- LLM core: LiteRT-LM runtime contract, prompt/context assembly, generation limits, and streaming events.
- On-device task runtime: future non-LLM LiteRT Compiled Model adapters, isolated from the LLM runtime.
- Agent: risk policy and approval boundary; broader planner/tool execution remains roadmap work.
- Memory and knowledge: planned local retrieval and ingestion components.
- Data: app-private SQLite conversation/message storage and local settings.
- Platform: Android permissions, app-private storage, and lifecycle management.

## Local conversation history

SQLite is the durable source of truth for conversation history. The ViewModel restores the most recently updated conversation at startup, loads up to 500 messages in chronological order, and exposes a local history picker. On first generation in a native session, the runtime replays the newest 16 user/assistant messages within a 12,000-character memory budget using the pinned LiteRT-LM `ConversationConfig.initialMessages` API. The current prompt is sent separately. Native conversations are cached in a two-entry access-ordered LRU and are closed on eviction or confirmed deletion. The transcript character budget is not a tokenizer-aware context guarantee. Conversation deletion requires explicit user confirmation and SQLite foreign-key cascade removes associated messages.

## Runtime integration rules

### LLM path

`LocalModelRuntime` is the text-generation boundary. The production adapter is pinned to LiteRT-LM Android v0.18.1. Do not substitute Ollama, llama.cpp, remote inference, fabricated responses, or guessed APIs.

The adapter uses CPU inference and passes the configured maxOutputToken limit to the pinned v0.18.1 callback overload of Conversation.sendMessageAsync. Streamed Message content is extracted with Message.toString(); the pinned upstream Message class exposes role, contents, and toString(), not a public text property. The adapter explicitly sets automaticToolCalling = false.

Generation and conversation creation are shifted off the main thread. A generation mutex serializes native requests. The callback bridge waits for onDone or onError even if the downstream collector is cancelled, so a cancelled collector cannot release the generation lock and allow a second request or unload while native inference is still active. This is a drain-to-completion policy, not immediate native cancellation. LiteRT-LM v0.18.1's Flow adapter uses an empty awaitClose block; the callback overload lets NEXORA own the terminal-callback gate. The pinned native Conversation::CancelProcess() documentation warns that cancellation poisons the session; NEXORA therefore does not call it or reuse a cancelled native session. A future explicit cancel feature must discard and reconstruct that conversation from the persisted transcript before reuse. CI compilation does not prove real model inference.

### Non-LLM model path

Use Google's LiteRT Compiled Model API for new non-LLM model tasks such as speech recognition, vision, or embeddings. Keep these adapters separate from `LocalModelRuntime`; a `.tflite` task model is not a substitute for the Gemma `.litertlm` model. Do not introduce the legacy TensorFlow Lite `Interpreter` API.

Each task adapter must own its model and tensor buffers, run on a confined serial background dispatcher, reuse buffers where appropriate, and close native resources deterministically. Preprocessing and postprocessing are part of the model contract and belong beside the adapter, not in Compose.

Before integrating a model, verify its license, artifact revision, input/output signatures, preprocessing contract, source-model reference outputs, and target-device behavior. Record accelerator residency logs because a successful run alone does not prove GPU execution; partial CPU fallback may occur. Benchmark enqueue plus output readback, not enqueue alone.

See [Google AI Edge integration](GOOGLE_AI_EDGE_INTEGRATION.md) for the workload matrix, Pixel 6 constraints, and verification checklist.

## Build toolchain

The checked-in Gradle Wrapper pins Gradle 8.13. `gradle/wrapper/gradle-wrapper.properties` pins the official binary distribution URL and its published SHA-256 (`20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`). The wrapper JAR is sourced from the official Gradle `v8.13.0` tag and is checked against Gradle's published wrapper-JAR checksum (`81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`) by `gradle/actions/setup-gradle@v4` in CI. Do not replace it with an unverified wrapper binary.

The app targets Java 17 bytecode. Kotlin 2.3.21 uses the typed `kotlin.compilerOptions.jvmTarget` DSL; the deprecated Android `kotlinOptions.jvmTarget` property is rejected by this Kotlin plugin. CI and local command-line builds use JDK 21 to accommodate the LiteRT-LM dependency's JVM test runtime while retaining Java 17 app bytecode compatibility. Use the wrapper (`./gradlew` or `gradlew.bat`) rather than a machine-installed Gradle version.

## Safety

Treat model-generated tool requests as untrusted. Validate schemas, authorize each capability, constrain paths, apply timeouts, and record audit events. Destructive or externally visible actions require explicit user confirmation. Do not enable automatic tool execution by default.

## Device target

Initial target is Pixel 6 / 6 GB RAM. CPU inference is the baseline. Model loading, context size, CPU behavior, peak memory, and sustained thermal performance must be measured on physical hardware. Do not infer Google Tensor NPU support from the SoC branding; use only an accelerator/model/runtime combination explicitly verified for the target device. No Pixel 6 performance claim is made without device evidence.


## Agent tool execution boundary

Tool invocation is separate from LiteRT-LM generation. Model text is never parsed into an executable tool call, and automatic LiteRT-LM tool calling remains disabled. A caller must create a user-originated `ToolInvocation`; the executor resolves only an explicitly registered tool, evaluates its risk policy immediately before the handler runs, and rejects disabled or unknown tools.

Approval grants are one-use and tied to the exact invocation object. A grant cannot be reused for another request or replayed after consumption. The current registered capability is a user-enabled, manually invoked, read-only search over user/assistant message text in the app-private SQLite conversation store; system-role messages are excluded. It runs through the repository IO boundary, uses bound SQL parameters, limits query length, scans at most the most recent 2,000 user/assistant messages and the first 12,000 characters of each, and limits result count, and does not transmit data outside the device.

No external or destructive tool is registered. The local note writer described below is the only current local-write handler. The approval primitive is not permission to execute arbitrary code. Any future side-effect tool must have a concrete handler, an explicit user-facing review step, immediate policy re-check, and focused tests before registration.


## Approved local note writes

The local note tool is registered as LOCAL_WRITE and is disabled by default. Enabling it in the Compose Tools dialog only makes the capability available; it does not approve a write. Each save first creates a user-originated invocation and receives NEEDS_APPROVAL from the executor. The Compose review dialog displays the derived title and the complete note content in a scrollable view. Only the explicit Approve & save control issues a one-use grant for that exact invocation. The executor re-checks the user-enabled descriptor and risk policy immediately before the SQLite write. Model-generated text is never converted into this invocation.

Notes are stored in an additive schema-version-2 SQLite table. The version-1 migration creates the new table without rewriting or deleting conversations/messages. The UI lists saved notes and requires a separate confirmation before deletion. Notes are not automatically inserted into Gemma prompts; retrieval/context injection is a separate future feature.
