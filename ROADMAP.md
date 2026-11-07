# NEXORA AI Roadmap

| Area | Status | Exit criteria |
|---|---|---|
| Android Gradle/Compose foundation | PR #2 and callback lifecycle PR #3 merged; main run #68 passes. Verified Gradle 8.13 wrapper is in draft PR #5; run #78 passed wrapper validation, debug APK assembly, Android lint, and JVM unit tests | Merge the wrapper PR; keep main CI green and local Windows/Android Studio build instructions reproducible |
| LiteRT-LM adapter | CPU adapter uses pinned v0.18.1 callback API; callback completion gate and explicit tool-call disablement merged in PR #3; main run #68 passed; real inference unverified | Real Gemma output is verified on Pixel 6 |
| Model download and integrity | Revision-pinned downloader with SHA-256; not device-tested | Download, checksum, cancellation, and storage checks pass on Android |
| Chat and streaming | Compose UI and ViewModel connected to runtime API; end-to-end unverified | Compose chat receives and renders real local tokens |
| Local conversation storage | SQLite repository stores current chat session; history browsing/replay and generation settings are in draft PR #4; run #76 passed debug build, lint, and JVM tests; Android instrumentation remains pending. Original PR #1 is preserved and diverged | History list, replay, migrations, and delete behavior pass instrumentation and process-restart tests |
| Google AI Edge integration | LiteRT-LM is primary LLM path; Compiled Model API capability boundary and verification plan documented | First non-LLM model selected, licensed, compiled, parity-checked, and measured on Pixel 6 |
| Speech recognition | Not implemented; candidate LiteRT Compiled Model API task | Model input/output contract, mic permission flow, offline recognition, and Pixel 6 results verified |
| Vision | Not implemented; candidate LiteRT Compiled Model API task | Model and preprocessing verified against fixed reference inputs on Pixel 6 |
| Embeddings and retrieval | Not implemented; candidate local non-LLM model task | Embedding quality and retrieval evaluation pass on local corpus |
| Accelerator strategy | CPU is baseline; Pixel 6 GPU path unverified; Google Tensor NPU path not enabled | Runtime residency and correctness recorded on target device; no silent fallback claims |
| Agent tool permission system | Risk policy primitives exist; CI JVM test suite passes, but no tool executor is integrated | Tests pass and every side effect re-checks approval immediately before execution |
| Memory and retrieval | Pending | Indexed local recall tests |
| Knowledge ingestion | Pending | PDF/TXT/MD/JSON extraction tests |
| Agent planner | Pending | Plan, approval, and cancellation tests |
| Skills laboratory | Pending | Schema validation and rollback |
| Evolution engine | Pending | Evaluation, regression and rollback |
| Pixel 6 performance | Pending | Physical-device RAM, speed and thermal report |

## Release gates

1. Merge the verified Gradle 8.13 wrapper from PR #5; run #78 passed wrapper validation, debug build, lint, and JVM unit tests.
2. Build and lint cleanly in GitHub Actions.
3. Unit and instrumentation tests pass.
4. Real model output verified; no mock inference.
5. Offline behavior verified after model download.
6. Security review of tool execution completed.
7. Pixel 6 performance and thermal limits documented.
8. Any Compiled Model API feature must pass source parity, task-level correctness, accelerator residency, and lifecycle/memory checks before UI release.
