# Local data and agent safety

## Local conversation storage

NEXORA stores conversations in the app-private SQLite database `nexora_local.db`. The database enables foreign-key constraints and indexes messages by conversation and creation time. The current schema is version 1; this history integration does not change the schema.

All repository database operations are dispatched to `Dispatchers.IO`. Conversation history is limited to the 100 most recently updated conversations in the picker. Selecting a conversation loads up to its latest 500 messages and displays them in chronological order. Messages are limited to 100,000 characters and stored roles are restricted to user, assistant, and system.

The first user prompt becomes the conversation title, trimmed to 60 characters. New title updates and message insertion remain local.

## Deletion and user control

A user must explicitly confirm deletion from the history menu. The app then deletes the conversation row; SQLite's foreign-key cascade removes dependent messages. The ViewModel also asks the LiteRT-LM runtime to close and forget the corresponding native conversation. If the active conversation is deleted, the app opens the most recently updated remaining conversation or creates a new empty one.

Deletion is permanent within the app database and is not synchronized to another service. There is no cloud history sync or remote account.

## Runtime transcript replay

SQLite is the durable source of truth. When a selected conversation has no native LiteRT-LM session (for example after process restart or LRU eviction), the next generation request recreates it with stored user/assistant turns through the pinned `ConversationConfig.initialMessages` API.

The ViewModel snapshots previous turns before it saves the current prompt. Previous turns are replayed; the current prompt is sent separately. This avoids duplicating the current user message.

To constrain native memory use, replay is limited to the newest 16 messages and 12,000 characters. If the oldest included message exceeds the remaining character budget, its prefix is replaced with an explicit truncation marker and its newest suffix is retained. This is a character limit, not an exact token or model-context guarantee. The runtime keeps at most two native conversations in an access-ordered LRU cache and closes the least-recently-used session when a third is created.

## Local generation settings

The output-token preference is saved in app-private SharedPreferences. Supported choices are 256, 512, 1,024, and 2,048; default is 512. It is validated before it is passed to LiteRT-LM. The setting does not override the model's context window or guarantee a response length.

## Agent tool permission model

Every future tool must declare a risk class:

- `READ_ONLY`: may run automatically only when enabled in settings.
- `LOCAL_WRITE`: requires per-run approval unless the user explicitly changes the local-write policy.
- `EXTERNAL`: requires per-run approval before data can leave the device.
- `DESTRUCTIVE`: requires per-run approval before deletion or overwrite.

A tool being enabled is not equivalent to user approval. The policy is a guard, not a substitute for Android runtime permissions or OS sandboxing. Tool implementations must re-check the decision immediately before executing the side effect. Never treat model-generated text as approval.

## Current safety boundary

The LiteRT-LM adapter explicitly disables automatic tool calling and has no registered app tools. The permission policy is not yet an integrated tool-execution framework. No autonomous external action or destructive operation is claimed as implemented.

The SQLite database and preferences use Android app-private storage; this integration does not add app-layer database encryption, backup export, or cross-device synchronization. Android app sandboxing is not a substitute for a separate encryption design where that is required.
