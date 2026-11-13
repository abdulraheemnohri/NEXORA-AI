# Micro-Agent Society (MAS-X)

MAS-X is an Android-native, offline-first agent orchestration subsystem inside NEXORA SELF-X. The current implementation is a bounded, single-model society foundation, not an unrestricted autonomous operating system.

## Runtime architecture

- Kotlin and Jetpack Compose UI.
- An application-scoped `NexoraApplication` owns one `LiteRtLocalModelRuntime` and one `SharedInferenceScheduler`.
- Chat and MAS-X inference calls use the same scheduler. The default is one heavyweight inference at a time and a bounded waiting queue.
- The MAS-X data store is a separate app-private `SQLiteOpenHelper` database (`nexora_masx.db`). It intentionally does not migrate or rewrite existing chat history.
- All database work from the society ViewModel is dispatched to `Dispatchers.IO`.
- No remote inference, Firebase, Docker, or mandatory network service is used.

## Agent registry

The seed catalog provides 18 declarative agent identities across 12 departments:

Executive, Intelligence, Research, Engineering, Knowledge, Evolution, Operations, Security, Quality, Productivity, Creative, and Infrastructure.

Agents are lightweight role profiles. They do not each load a model or run as independent operating-system processes. A user-created temporary agent starts paused and requires an explicit activation action. Agent profiles have separate capability, tool, and memory-scope declarations.

## LAMP message protocol

LAMP v1 is a local JSON envelope with message ID, task/correlation IDs, sender, optional recipient, type, priority, creation/expiry times, payload, and retry attempt. The persistent message table tracks pending, delivered, dead-letter, and expired states. Delivery is acknowledged after a registered handler succeeds. Payloads are data, never executable instructions.

Current handlers record receipt in the audit trail. Rich semantic negotiation, remote transport, and cross-device federation are not implemented.

## Task orchestration

Tasks are stored before execution and use validated state transitions. The current supervisor path selects a specialist worker using deterministic task-keyword routing, assembles only permitted task/agent memory, invokes the shared local model, applies a bounded output gate, records checkpoints and metrics, and persists completion.

Interrupted tasks are reconciled on application startup. Retry attempts are bounded by the local task setting. Task dependencies are persisted and block execution until prerequisites complete.

The current planner role is registered, but full LLM-based multi-step planning, debate, independent model review, parallel worker execution, and approval-paused external actions remain future work. Do not describe this MVP as a full autonomous planner.

## Memory isolation

Supported scopes are global, department, agent, task, and user-approved. Agent retrieval filters by exact scope owner; one agent cannot retrieve another agent's private memory through the provided repository method. Global and user-approved writes require an explicit approval path. User-approved shared memory is not passed into an agent prompt unless the setting is enabled.

This is local scope filtering, not a cryptographic isolation boundary against a compromised app process.

## Skills

Skills are versioned declarative instruction bundles. The validator rejects unknown required tool IDs, common security-bypass phrases, and executable/script content. Activation requires a separate user approval action. Skills do not install code, create Android permissions, or automatically grant tools.

## Tool permissions

Tool risk metadata distinguishes read-only, local-write, external, and destructive operations. The UI can persist enablement policy and approval requirements. A permission record does not create a handler. The existing conversation-search tool remains manually invoked through the chat's existing executor. Other listed capabilities remain unregistered until a real handler, approval UX, and tests are added.

## Model management

AIMS-X model metadata and role assignments are persisted locally. The current catalog seeds the pinned Gemma 4 E2B IT LiteRT-LM artifact. Runtime readiness updates its status and assigns the active runtime to assistant and society roles. Compatibility preflight checks runtime ID, format, memory reserve, storage, thermal state and local file presence.

Dynamic multi-model loading, model import wizard, generalized catalog downloads, quantization conversion, benchmark suites, and automatic model switching are not yet implemented. The current production adapter supports the pinned LiteRT-LM path only.

## Resource governor

The governor reads Android available memory, low-memory state, battery percentage, power-save mode and thermal status where available. It blocks new society inference below a conservative 512 MiB available-memory reserve or at severe thermal status. CPU utilization, exact model native-memory consumption and battery energy attribution are not currently measured.

## Evolution

Users can submit and review evolution proposals. Approval means approved for manual implementation only. The proposal workflow never modifies source code, model weights, permissions, or security policy.

## UI

The MAS-X control center includes Overview, Agents, Tasks, Models, Memory, Skills, Tools, Security, Performance, Evolution and Settings screens. UI controls call database/runtime services. Tool permission switches are policy metadata and are labelled accordingly.

## Persistence and migrations

MAS-X owns a separate SQLite database. It currently uses `SQLiteOpenHelper`, not Room. Schema creation is explicit and additive migration policy is required for future releases. Do not migrate or delete the existing chat database as part of MAS-X work.

## Verification status

The repository contains JVM tests for catalog, skill validation, compatibility preflight and scheduler serialization, plus Android instrumentation tests for LAMP, persistence, task orchestration and governance. These are test sources, not evidence of passing CI. A successful CI run is required before claiming tests pass. Physical Pixel 6 model load, inference, offline restart, sustained RAM and thermal behavior remain unverified until run on the actual device.


## Task cancellation contract

A running task is owned by a cancellable ViewModel job. User cancellation cancels that job and waits for its completion before the UI refreshes the durable state. MasxOrchestrator propagates CancellationException after a non-cancellable database cleanup to CANCELLED; it does not mark cancellation as FAILED, schedule a retry, or report a successful result. The LiteRT-LM adapter holds its generation gate until the native callback reaches a terminal state, so user cancellation can wait for native drain. It does not claim to forcibly terminate the native request. The API 35 instrumentation test uses a suspending gateway to verify task-state and retry semantics; it does not substitute for a real-device native cancellation test.


## User-controlled memory deletion

The Memory screen exposes a per-record Delete action. The user must confirm the selected record before the ViewModel invokes the IO-bound database delete. The delete and metadata-only audit event are committed in one SQLite transaction. A stale or already-deleted ID is reported rather than silently treated as a successful deletion. This does not provide secure flash erasure or encrypted-at-rest storage.
