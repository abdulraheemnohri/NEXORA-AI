# LiteRT-LM integration notes

## Pinned API

NEXORA pins `com.google.ai.edge.litertlm:litertlm-android:0.18.1` in the version catalog and uses Google's Kotlin API directly. The implementation was checked against the upstream `v0.18.1` source, rather than inferred from a newer unpinned API:

- [LiteRT-LM v0.18.1 Config.kt](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.1/kotlin/java/com/google/ai/edge/litertlm/Config.kt)
- [LiteRT-LM v0.18.1 Conversation.kt](https://github.com/google-ai-edge/LiteRT-LM/blob/v0.18.1/kotlin/java/com/google/ai/edge/litertlm/Conversation.kt)
- [LiteRT-LM Kotlin getting started guide](https://ai.google.dev/edge/litert-lm/android)

The app uses:

- `Engine`, `EngineConfig`, and `Backend.CPU()`.
- `ConversationConfig` with `systemInstruction`, `initialMessages`, `maxOutputToken`, and `automaticToolCalling = false`.
- `Message.user(...)` and `Message.model(...)` to reconstruct stored turns.
- `Conversation.sendMessageAsync(text, maxOutputToken = ...)` for streaming generation.

The pinned API defines `maxOutputToken` as a maximum output-token limit per decode step. NEXORA does not describe it as a guaranteed whole-session token budget.

## Conversation restoration and memory bounds

- SQLite transcripts are loaded when a conversation is selected.
- When LiteRT-LM has no in-memory conversation for that ID, NEXORA replays the latest history through `ConversationConfig.initialMessages` before sending the new prompt.
- Replay is bounded to the latest 16 messages and 12,000 characters. This is a character-based guard, not a tokenizer-aware context-window guarantee. If the oldest included message must be shortened, a visible marker is inserted into the replayed text.
- At most two native conversations are cached in an access-ordered map. Older cached conversations are closed and can be reconstructed from SQLite when selected again.
- Deleting a conversation also closes its cached LiteRT-LM conversation.
- The user can select 256, 512, 1,024, or 2,048 output tokens. The choice is stored in app-private SharedPreferences and defaults to 512.

## Concurrency and lifecycle rules

- Engine initialization and teardown happen off the main thread.
- A lifecycle mutex protects engine/conversation ownership.
- A generation mutex serializes generation and prevents overlapping requests from corrupting conversation state.
- Cancellation is propagated instead of being converted into a normal model error.
- Every conversation and the engine are closed during unload.
- Automatic LiteRT-LM tool calling is explicitly disabled. No model-generated tool call can execute an app side effect through this adapter.

## Verification status

The API names above are present in the pinned upstream v0.18.1 source. This source inspection does not prove that NEXORA compiles against the resolved Android artifact or that Gemma inference succeeds on a device. CI compilation, model download, real inference, offline behavior, and Pixel 6 memory/thermal testing remain release gates.
