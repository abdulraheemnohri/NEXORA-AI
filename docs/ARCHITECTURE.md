# NEXORA AI Architecture

NEXORA is a native Android application. Its application backend runs in-process in Kotlin. Local LLM inference is provided only by Google's LiteRT-LM runtime.

## Layers

- Presentation: Jetpack Compose chat, conversation history menu, and generation settings dialog.
- Application: ViewModel orchestration, conversation selection, transcript restoration, and task lifecycle.
- AI core: LiteRT-LM runtime contract, bounded history replay, generation limits, and streaming events.
- Agent: planned plans, task steps, tool registry, approvals, and audit.
- Memory: planned working, episodic, semantic, and procedural records.
- Knowledge: planned document extraction, chunking, indexing, and retrieval.
- Data: app-private SQLite via `SQLiteOpenHelper`; SharedPreferences for the small local generation-settings record.
- Platform: Android permissions, app-private storage, and lifecycle management.

## Runtime integration rule

`LocalModelRuntime` is the app/runtime boundary. The production adapter is implemented against the pinned LiteRT-LM Android artifact and the upstream v0.18.1 Kotlin API. Do not substitute Ollama, llama.cpp, remote inference, fabricated responses, or guessed APIs.

The adapter uses CPU inference, disables automatic tool calling, replays stored user/assistant messages through `ConversationConfig.initialMessages`, and passes the configured `maxOutputToken` limit. Replay is capped at 16 messages and 12,000 characters. At most two native conversations remain cached; older ones are closed and reconstructed from SQLite on demand.

## Data lifecycle

The database stores conversations and messages locally. The UI restores the most recently updated conversation, allows switching, and requires confirmation before deleting a conversation. Deletion cascades to messages and closes the matching in-memory runtime conversation. No account or cloud synchronization is included.

## Safety

Treat model-generated tool requests as untrusted. Validate schemas, authorize each capability, constrain paths, apply timeouts, and record audit events. Destructive or externally visible actions require explicit user confirmation. The current LiteRT-LM adapter has automatic tool calling disabled and no registered app tools.

## Device target

Initial target is Pixel 6 / 6 GB RAM. Model loading, context size, CPU behavior, peak memory, and sustained thermal performance must be measured on physical hardware. No Pixel 6 performance claim is made without device evidence.
