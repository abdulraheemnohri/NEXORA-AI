# NEXORA AI Roadmap

| Area | Status | Exit criteria |
|---|---|---|
| Android Gradle/Compose foundation | Source scaffolded; CI build not yet verified | CI build and unit tests pass |
| LiteRT-LM adapter | Implemented in source; compile/runtime unverified | Build succeeds and real Gemma output is verified |
| Model download and integrity | Revision-pinned downloader implemented; not device-tested | Download, SHA-256, cancellation, and storage checks pass on Android |
| Chat and streaming | Runtime streaming API implemented; UI integration pending | Compose chat receives and renders real local tokens |
| Local conversation storage | SQLite database/repository implemented; integration tests pending | Chat UI uses repository; migrations and delete behavior tested |
| Agent tool permission system | Risk policy and unit tests added; test run pending | Tests pass and all tool side effects re-check approval |
| Memory and retrieval | Pending | Indexed local recall tests |
| Knowledge ingestion | Pending | PDF/TXT/MD/JSON extraction tests |
| Agent planner | Pending | Plan, approval, and cancellation tests |
| Skills laboratory | Pending | Schema validation and rollback |
| Evolution engine | Pending | Evaluation, regression and rollback |
| Voice and vision | Pending | Verified model/device modality support |
| Pixel 6 performance | Pending | Physical-device RAM, speed and thermal report |

## Release gates
1. Generate and commit the Gradle wrapper.
2. Build and lint cleanly in GitHub Actions.
3. Unit and instrumentation tests pass.
4. Real model output verified; no mock inference.
5. Offline behavior verified after model download.
6. Security review of tool execution completed.
7. Pixel 6 performance and thermal limits documented.
