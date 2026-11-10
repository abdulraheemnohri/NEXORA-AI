# Local data and agent safety

## Local conversation storage
NEXORA stores conversations in the app-private SQLite database `nexora_local.db`. No network transport is used by this data layer. The database enables foreign keys and indexes messages by conversation and creation time. The current schema is version 1.

The repository is an IO boundary: call it from a ViewModel or coroutine and keep database work off the main thread. Messages are limited to 100,000 characters and roles are restricted to user, assistant, and system.

## Agent tool permission model
Every tool declares a risk class:
- `READ_ONLY`: may run automatically only when enabled in settings.
- `LOCAL_WRITE`: requires per-run approval unless the user explicitly changes the local-write policy.
- `EXTERNAL`: requires per-run approval before data can leave the device.
- `DESTRUCTIVE`: requires per-run approval before deletion or overwrite.

A tool being enabled is not equivalent to user approval. The policy is a guard, not a substitute for Android runtime permissions or OS sandboxing. Tool implementations must re-check the decision immediately before executing the side effect. Never treat model-generated text as approval.

## Current integration status
The native Compose chat screen, Kotlin ViewModel, SQLite repository, LiteRT-LM CPU adapter, and revision-pinned Gemma model downloader are implemented. The current conversation database uses Android's `SQLiteOpenHelper` with schema version 1 and additive-only migration policy; Room is not the active persistence implementation. Encrypted export and database encryption are not implemented. Agent risk-policy primitives exist, but no general tool executor is integrated; LiteRT-LM automatic tool calling remains disabled. No claim of production-ready autonomous execution is made.

## Verification evidence
API 35 emulator instrumentation in Actions runs [#90](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42522885207) and [#92](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42691929272) passed three tests covering database reopen persistence, chronological message order, recent conversation order, and foreign-key cascade deletion. These tests do not prove secure flash erasure, encrypted storage, native Gemma inference, or physical Pixel 6 behavior.


## Conversation history controls

The history picker lists at most 100 recently updated conversations and loads up to 500 stored messages for the selected conversation. A conversation title is derived from the first prompt and limited to 60 characters. Deletion is always confirmation-gated in the UI. While deletion is running, chat switching, new chat creation, and sending are disabled; the repository deletes the conversation row and SQLite foreign-key cascade deletes dependent messages. The ViewModel then asks LiteRT-LM to close the corresponding cached native session. The runtime serializes this cleanup with generation and lifecycle operations. This does not imply secure erasure from flash storage or backups; no app-layer database encryption is currently implemented.
