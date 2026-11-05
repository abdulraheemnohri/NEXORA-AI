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

## Remaining integration
The current change adds storage and policy primitives only. The Compose chat screen, Room/SQLite migration strategy, real LiteRT-LM adapter, model downloads, encrypted export, and device validation remain separate work items. No claim of production-ready autonomous execution is made.
