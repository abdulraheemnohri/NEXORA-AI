# LiteRT-LM integration notes

## Pinned runtime and verified API

NEXORA pins com.google.ai.edge.litertlm:litertlm-android:0.18.1 and uses the official Kotlin API:

- Engine, EngineConfig, and Backend.CPU().
- Engine.initialize(), Engine.createConversation(), and Engine.close().
- ConversationConfig(systemInstruction = ..., automaticToolCalling = false).
- Callback overload Conversation.sendMessageAsync(text, callback, maxOutputToken = n).
- MessageCallback.onMessage, onDone, and onError.
- Message.toString() for streamed content; the pinned Message type has no public text property.
- Conversation.close().

The model is downloaded separately to app-private storage and is never bundled into the APK. No tools are registered, and automatic tool calling is explicitly disabled.

## Why NEXORA uses the callback overload

The v0.18.1 Kotlin Flow overload is implemented with callbackFlow followed by awaitClose {}. Its cleanup block is empty. When a collector stops early, the Flow can return while the native request is still running. This behavior is tracked upstream in [LiteRT-LM issue #2718](https://github.com/google-ai-edge/LiteRT-LM/issues/2718).

NEXORA calls the callback overload directly and owns a CompletableDeferred completed only by onDone or onError. The callback bridge uses awaitClose to stop forwarding events when the collector ends, then waits in NonCancellable cleanup for the native terminal callback. Because this wait is inside the generation flow, the generation mutex remains held until native work has ended. A second generation or model unload therefore cannot overlap the still-running request.

**Cancellation semantics:** cancelling a downstream collector stops further UI delivery, but it does not immediately stop LiteRT-LM inference. The request is allowed to drain to its terminal callback before the runtime lock is released. NEXORA does not call Conversation.cancelProcess(): the pinned v0.18.1 native Conversation::CancelProcess() documentation says the session is left poisoned and subsequent sends on that conversation are unsupported. A future hard-cancel action must cancel and discard the conversation, then reconstruct it from the persisted transcript before reuse.

## Concurrency and lifecycle rules

- Engine initialization, conversation creation, callback registration, and unload run on Dispatchers.IO; inference is never started from Compose or the Android main thread.
- A lifecycle mutex protects engine/conversation ownership.
- A generation mutex serializes requests and is retained until the native callback reaches onDone or onError, including when the collector is cancelled.
- Native resources are closed only after the generation gate is released.
- Cancellation exceptions are propagated; they are not converted into ordinary model errors.
- Automatic LiteRT-LM tool calling is disabled. Any future app tool must pass the app's explicit approval policy immediately before its side effect.

## Verification boundaries

Source inspection is pinned to the Google LiteRT-LM v0.18.1 Kotlin Conversation.kt, Engine.kt, and native runtime/conversation/conversation.h. This verifies the callback signatures, output-token parameter, empty Flow awaitClose block, and native cancellation warning. It does not prove successful Gemma inference or correct callback behavior on a physical device.

Remaining release gates:

- Real Gemma 4 E2B download, load, multi-turn inference, and stream completion on Pixel 6.
- Confirm the callback-drain behavior under collector cancellation on device.
- Offline-after-download verification.
- Android instrumentation coverage for SQLite and runtime lifecycle.
- Pixel 6 peak memory, sustained CPU, thermal, and long-session measurements.
