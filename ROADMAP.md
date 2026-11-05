# NEXORA AI Roadmap

| Area | Status | Exit criteria |
|---|---|---|
| Android Gradle/Compose foundation | Scaffolded; build not verified | CI build and unit tests pass |
| LiteRT-LM adapter | Pending | Real Gemma inference on Android |
| Model download and integrity | Pending | Verified install, resume and cleanup |
| Chat and streaming | Pending | Real tokens from local model |
| Room persistence | Pending | Migration and repository tests |
| Memory and retrieval | Pending | Indexed local recall tests |
| Knowledge ingestion | Pending | PDF/TXT/MD/JSON extraction tests |
| Agent planner | Pending | Plan and cancellation tests |
| Tool permission system | Pending | Deny-by-default security tests |
| Skills laboratory | Pending | Schema validation and rollback |
| Evolution engine | Pending | Evaluation, regression and rollback |
| Voice and vision | Pending | Verified model/device modality support |
| Pixel 6 performance | Pending | Physical-device RAM, speed and thermal report |

## Release gates
1. Build and lint cleanly.
2. Unit and instrumentation tests pass.
3. Real model output verified; no mock inference.
4. Offline behavior verified after model download.
5. Security review of tool execution completed.
6. Pixel 6 performance and thermal limits documented.
