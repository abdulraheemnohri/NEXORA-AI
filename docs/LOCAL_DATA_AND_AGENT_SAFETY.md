# Local data and agent safety

## Local conversation storage

NEXORA stores conversations in the app-private SQLite database `nexora_local.db` through Android's `SQLiteOpenHelper`. No network transport is used by this data layer. Foreign keys are enabled and deleting a conversation cascades to its messages. The current schema is version 1.

The chat screen restores the most recently updated conversation at launch and provides a history menu for switching between the 100 most recent conversations. Each conversation loads up to 500 messages into the UI. The first user prompt becomes a title (trimmed to 60 characters).

A user can delete a conversation from the history menu. NEXORA asks for confirmation, removes the SQLite conversation row and dependent messages, and closes the corresponding cached LiteRT-LM conversation. Deleting the active conversation switches to the most recent remaining conversation or creates a new empty one. This action is local and cannot be undone.

## Runtime transcript replay

When a saved conversation is selected, its messages are shown from SQLite. If its native LiteRT-LM conversation is not currently cached, NEXORA rebuilds the runtime conversation from stored user/assistant turns using the pinned `ConversationConfig.initialMessages` API.

To constrain native memory use, replay is limited to the latest 16 messages and 12,000 characters. If the oldest included message exceeds the remaining character budget, its beginning is omitted and an explicit truncation marker is inserted. This is a character limit, not an exact token or model-context guarantee. The runtime keeps at most two native conversations cached and closes least-recently-used entries.

The maximum output-token setting (256, 512, 1,024, or 2,048; default 512) is saved in app-private SharedPreferences. No account, cloud sync, or external preference service is used.

## Agent tool permission model

Every future tool must declare a risk class:

- `READ_ONLY`: may run automatically only when enabled in settings.
- `LOCAL_WRITE`: requires per-run approval unless the user explicitly changes the local-write policy.
- `EXTERNAL`: requires per-run approval before data can leave the device.
- `DESTRUCTIVE`: requires per-run approval before deletion or overwrite.

A tool being enabled is not equivalent to user approval. The policy is a guard, not a substitute for Android runtime permissions or OS sandboxing. Tool implementations must re-check the decision immediately before executing the side effect. Never treat model-generated text as approval.

## Current safety boundary

The LiteRT-LM adapter explicitly disables automatic tool calling and has no registered app tools. The permission policy is not yet an integrated tool-execution framework. No autonomous external action or destructive operation is claimed as implemented.
