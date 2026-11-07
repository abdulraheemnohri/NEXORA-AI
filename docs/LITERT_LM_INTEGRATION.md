# LiteRT-LM integration notes

## Pinned runtime and verified API

NEXORA pins `com.google.ai.edge.litertlm:litertlm-android:0.18.1` and uses Google's Kotlin API:

- `Engine`, `EngineConfig`, and `Backend.CPU()`.
- `Engine.initialize()`, `Engine.createConversation()`, and `Engine.close()`.
- `ConversationConfig.systemInstruction`, `initialMessages`, `maxOutputToken`, and `automaticToolCalling`.
- Callback overload `Conversation.sendMessageAsync(text, callback, maxOutputToken = n)`.
- `MessageCallback.onMessage`, `onDone`, and `onError`.
- `Message.user(text)` and `Message.model(text)` for replayed history roles.
- `Message.toString()` for streamed content; the pinned `Message` type has no public `text` property.
- `Conversation.close()`.

Google's Kotlin API guide documents conversation initialization with `initialMessages` and the asynchronous callback interface: [LiteRT-LM Kotlin API guide](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.1/docs/api/kotlin/getting_started.md). The source-level configuration shape is visible in [Google's LiteRT-LM Config.kt](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.1/kotlin/java/com/google/ai/edge/litertlm/Config.kt). NEXORA's dependency remains pinned to v0.18.1; checking current upstream documentation does not change the pinned artifact.

The model is downloaded separately to app-private storage and is never bundled into the APK. No tools are registered, and `automaticToolCalling = false` is set for every native conversation.

## Conversation replay and bounded memory

SQLite is the durable transcript store. The ViewModel snapshots previous user/assistant turns before persisting the current prompt, then supplies that snapshot in `GenerationRequest.history`. The current prompt is sent separately through `sendMessageAsync`; it is not duplicated in `initialMessages`.

When a native conversation is first created, NEXORA maps persisted roles to `Message.user` and `Message.model`, applies `ConversationHistoryPolicy`, and passes the result through `ConversationConfig.initialMessages`. The policy retains at most 16 recent messages and 12,000 characters. The character limit is a memory guard, not a tokenizer-aware context guarantee.

The runtime uses an access-ordered LRU cache capped at two native conversations. A third conversation evicts and closes the least-recently-used entry. Confirmed user deletion calls `forgetConversation`, which closes the cached native session after the generation lock is acquired.

The output-token choice is stored locally and passed to both conversation configuration and each generation request. Current choices are 256, 512, 1,024, and 2,048; model and context constraints still apply.

## Why NEXORA uses the callback overload

The pinned v0.18.1 Kotlin Flow overload is implemented with `callbackFlow` followed by an empty `awaitClose { }`. If a collector stops early, the Flow can return while native inference is still running. This behavior is tracked in [LiteRT-LM issue #2718](https://github.com/google-ai-edge/LiteRT-LM/issues/2718).

NEXORA calls the callback overload directly and owns a `CompletableDeferred` completed only by `onDone` or `onError`. The callback bridge stops forwarding events when the collector ends, then waits in `NonCancellable` cleanup for the native terminal callback. Because this wait remains inside the generation flow, the generation mutex stays held until native work has ended.

**Cancellation semantics:** cancelling a downstream collector stops further UI delivery, but does not immediately stop LiteRT-LM inference. The request is allowed to drain to its terminal callback before the runtime lock is released. NEXORA does not call `Conversation.cancelProcess()`: the pinned native API documents that cancellation poisons the session. A future hard-cancel feature must discard the native conversation and reconstruct it from SQLite before reuse.

If LiteRT-LM never delivers a terminal callback, the gate intentionally remains held rather than closing a native handle that may still be active. This can delay shutdown indefinitely and remains a device-validation risk; no Pixel 6 cancellation behavior is claimed as verified.

## Concurrency and lifecycle rules

- Engine initialization, conversation creation, callback registration, generation, and unload run off the Android main thread.
- A lifecycle mutex protects engine/conversation ownership.
- A generation mutex serializes requests and remains held until the native callback reaches `onDone` or `onError`, including when the collector is cancelled.
- Native resources are closed only after the generation gate is released.
- Cancellation exceptions are propagated; they are not converted into ordinary model errors.
- Automatic LiteRT-LM tool calling is disabled. Any future app tool must pass the app's explicit approval policy immediately before its side effect.

## Verification boundaries

The integration branch uses the pinned v0.18.1 API shape and preserves the callback completion gate already merged into main. API/source inspection does not prove real Gemma inference or callback behavior on physical hardware.

Remaining release gates:

- Branch CI for conversation history and generation settings.
- Android instrumentation for SQLite ordering, foreign-key deletion, and persistence across process recreation.
- Real Gemma 4 E2B download, load, multi-turn inference, and stream completion on Pixel 6.
- Offline-after-download verification.
- Callback-drain behavior under collector cancellation on device.
- Pixel 6 peak memory, sustained CPU, thermal, and long-session measurements.
