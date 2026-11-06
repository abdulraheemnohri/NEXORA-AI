# NEXORA AI Roadmap

| Area | Status | Exit criteria |
|---|---|---|
| Android Gradle/Compose foundation | Source scaffolded; main CI Kotlin DSL failure has a focused fix awaiting CI | CI debug build, lint, and unit tests pass |
| LiteRT-LM adapter | CPU adapter and per-request output-token budget wired; compile/runtime verification pending | Build succeeds and real Gemma output is verified |
| Model download and integrity | Revision-pinned downloader with SHA-256; not device-tested | Download, checksum, cancellation, and storage checks pass on Android |
| Chat and streaming | Compose UI and ViewModel connected to runtime API; end-to-end unverified | Compose chat receives and renders real local tokens |
| Local conversation storage | SQLite repository stores current chat session; history browsing/replay is proposed in draft PR #1 | History list, replay, migrations, and delete behavior tested |
| Agent tool permission system | Risk policy and unit tests added; full test run pending | Tests pass and all tool side effects re-check approval |
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
