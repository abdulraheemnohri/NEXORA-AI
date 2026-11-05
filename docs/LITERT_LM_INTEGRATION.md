# LiteRT-LM integration notes

## Verified API surface

NEXORA targets the official LiteRT-LM Kotlin API:
- `Engine` and `EngineConfig`
- `Backend.CPU()`
- `Engine.initialize()` and `Engine.close()`
- `Engine.createConversation()`
- `Conversation.sendMessageAsync(prompt): Flow<Message>`
- `Message.text`
- `Conversation.close()`

The Android Maven coordinate is pinned in the version catalog. The model is downloaded separately and is never bundled into the APK.

## Concurrency and lifecycle rules

- Engine initialization and teardown happen off the main thread.
- A lifecycle mutex protects engine/conversation ownership.
- A generation mutex serializes generation and prevents overlapping requests from corrupting conversation state.
- Cancellation is propagated instead of being converted into a normal model error.
- Every conversation and the engine are closed during unload.
- Tool calling is not enabled in this adapter. The app-level permission policy remains the authority for future tool execution.

## Not yet delivered

- Process-death transcript replay into the runtime.
- User-configurable sampling/context settings.
- Model SHA-256 verification against a publisher-provided checksum.
- GPU backend selection and device-specific profiling.
- Pixel 6 thermal, memory-pressure, and long-session testing.
- Instrumented Android tests with an actual model file.
