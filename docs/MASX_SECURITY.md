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
