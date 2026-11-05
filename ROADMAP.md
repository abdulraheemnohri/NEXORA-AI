# NEXORA AI Roadmap

| Area | Status | Exit criteria |
|---|---|---|
| Android Gradle/Compose foundation | Source scaffolded; CI build not yet verified | CI build and lint pass |
| LiteRT-LM adapter | CPU adapter, bounded transcript replay, and per-request output limit implemented in source; compile/runtime unverified | Build succeeds and real Gemma output is verified |
| Model download and integrity | Revision-pinned downloader with SHA-256; not device-tested | Download, checksum, cancellation, and storage checks pass on Android |
| Chat and streaming | Compose chat and ViewModel connected to runtime API; end-to-end unverified | Compose chat receives and renders real local tokens |
| Local conversation storage | History picker, switching, title updates, confirmation-gated deletion, and runtime transcript replay implemented in source; JVM policy tests added | CI passes; SQLite restore/delete lifecycle verified with Android tests |
| Generation settings | Local output-token choices persisted in app-private SharedPreferences; validation tests added | Settings survive process restart on Android and are covered by UI tests |
| Agent tool permission system | Risk policy primitives exist; not integrated with a tool executor | Tests pass and all tool side effects re-check approval |
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
