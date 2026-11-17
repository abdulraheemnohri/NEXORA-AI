# MAS-X Security and Permission Model

## Trust boundaries

- User input, retrieved memory, model output, LAMP payloads, skill instructions and imported model metadata are untrusted data.
- No LAMP payload is evaluated as code.
- No skill is executable; skills are instruction bundles only.
- Model output is not approval and is never automatically converted into a tool invocation.
- Agent roles do not grant Android OS permissions.

## Tool policy

Tool risk classes are READ_ONLY, LOCAL_WRITE, EXTERNAL and DESTRUCTIVE. A tool must have a registered handler before it can execute. A persisted enabled flag is not a handler and is not sufficient authorization.

Any future side-effect handler must use the central executor, validate input, apply path/network constraints, enforce timeouts, re-check policy immediately before execution, and require an invocation-bound one-use approval where applicable.

## Memory access

- GLOBAL: read-only to agents through the filtered retrieval path.
- DEPARTMENT: only matching department owner.
- AGENT: only matching agent ID.
- TASK: only matching task ID.
- USER_APPROVED: included only when the user enables shared-memory access.

Agent writes cannot create global or user-approved memory without the explicit approval flag.

## Skills and agent creation

Temporary agents are created paused with no tools. Activation is an explicit user action. Skill drafts undergo static validation and require user approval before activation. Neither process can grant new tool permissions.

## Evolution

An approved proposal is a record for manual implementation. It cannot directly change application code, model weights, Android permissions, tool authorization policy, or audit settings.

## Audit

Task state transitions, message receipt, tool-policy changes, agent activation, skill activation, memory writes and evolution review are recorded in the local audit table. Audit data is app-private and is not tamper-proof against a compromised/rooted device.

## Limitations

The app does not claim a separate OS sandbox per agent, encrypted SQLite storage, tamper-proof audit logs, cryptographic memory compartments, secure flash erasure, or arbitrary-code sandboxing. Android app-private storage and in-process policy are not substitutes for those controls.


## Task cancellation

The Tasks UI exposes cancellation while a task is running. Cancellation is propagated through the Kotlin job and persisted as CANCELLED in a non-cancellable cleanup block; it is not treated as a retryable failure. The LiteRT-LM callback adapter may continue native work until its terminal callback, and the scheduler/session gate remains held during that drain. The UI and documentation must not describe this as an immediate native kill. A cancelled task is terminal and cannot be resumed; users may create a new task if they want to retry.


## User-controlled memory deletion

Every displayed MAS-X memory record has an explicit Delete action and a confirmation dialog. The database removes only the selected record by bound ID inside a transaction. A local audit event records the action and record identifier, but never copies the memory key or value into audit details. This is app-level deletion, not a claim of secure flash erasure; SQLite storage is not encrypted in this build.


## Resource admission

MAS-X re-samples Android memory and thermal state after it acquires the shared inference slot, then rejects the request before calling LiteRT-LM when the OS low-memory flag is set, available memory is below 512 MiB, or an available thermal status is `PowerManager.THERMAL_STATUS_SEVERE` (3) or higher. This closes the stale-preflight window while a task waits in the queue. The check is best-effort and does not guarantee that memory or temperature will remain safe for the full generation; it is not an OS sandbox or a hard memory limit. The resource snapshot is read locally on `Dispatchers.IO` and is not sent to a service.


## Ready-queue execution boundary

The ready queue is started by an explicit user action and a Compose confirmation dialog. A single invocation is limited to 10 sequential tasks and uses the existing shared local inference scheduler; it does not run in the background, create model-generated tool calls, or invoke external/destructive handlers. The queue checks current dependency completion in SQLite before each task. Each task ID is attempted at most once in one batch, so a retryable failure cannot cause an immediate retry loop; a later user action is required to retry it.

Queue cancellation propagates through the active Kotlin task and waits for the existing LiteRT-LM callback cleanup path. Dependency edits are transactional and allowed only while the dependent task is CREATED. The orchestrator checks prerequisites before planning and again after entering planning, before assignment, so an edit that wins the initial race cannot be bypassed. The SQLite cycle check and insert share the same write transaction to avoid a check-then-write race.


## LLM-generated task proposals

Gemma output is untrusted data, not authorization. The planner accepts only a bounded declarative JSON schema (1–8 tasks, fixed fields, bounded strings, known priorities, and dependencies on earlier steps). Unknown fields such as tool calls are rejected. Proposals are persisted as pending review; the UI shows the complete goal, summary, instructions, priorities, and dependency list before approval. Approval atomically creates local tasks in CREATED state and dependency rows only. It does not execute tasks or call tools. Task execution requires the separate ready-queue confirmation and the existing resource gate. Malformed model output is rejected; there is no deterministic, remote, or executable fallback.


The task-plan repository rechecks the MAS-X master enablement setting inside the SQLite write transaction for both proposal persistence and approval. A disabled society cannot persist a new plan or convert a pending plan into tasks. Pending proposals are capped at 20 to bound local storage. These checks are enforced at the storage boundary, not only by Compose button state.
