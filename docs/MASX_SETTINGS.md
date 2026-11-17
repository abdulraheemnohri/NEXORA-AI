# MAS-X Settings Reference

All settings are stored in the MAS-X app-private database.

| Key | Default | Purpose |
|---|---|---|
| `society.enabled` | `true` | Society master enable flag |
| `society.mode` | `SUPERVISOR` | Selected operating mode |
| `society.max_agents` | `24` | Maximum registered agent profiles |
| `inference.max_concurrent` | `1` | Documented shared-runtime concurrency target |
| `tasks.max_attempts` | `2` | Bounded task retry allowance |
| `security.require_approval_for_writes` | `true` | Local-write approval policy preference |
| `memory.allow_user_approved` | `false` | Allow explicitly approved shared memory in agent context |

The actual shared scheduler is currently hard-limited to one active inference. The concurrency setting is descriptive and does not override the scheduler.

Additional UI controls:
- Agent activation/pause.
- Temporary-agent proposal and activation.
- Model role assignment.
- Tool permission enablement.
- Skill draft and approval.
- Task retry limit.
- Society mode selection.
- User-approved memory opt-in.
- Evolution proposal review.

A visible policy toggle does not imply that a corresponding Android capability or tool handler exists.
