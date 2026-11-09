# NEXORA AI Roadmap

| Area | Status | Exit criteria |
|---|---|---|
| Android Gradle/Compose foundation | PR #7 conversation history and PR #8 stable Ubuntu 24.04/KVM CI fixes merged. Exact-head runs #90 and #92 passed; post-merge main run #94 was in progress at this record. | Keep main CI green and Windows/Android Studio wrapper builds reproducible |
| LiteRT-LM adapter | CPU adapter uses pinned v0.18.1 callback API; callback completion gate and explicit tool-call disablement merged in PR #3; main run #68 passed; real inference unverified | Real Gemma output is verified on Pixel 6 |
| Model download and integrity | Revision-pinned downloader with SHA-256; not device-tested | Download, checksum, cancellation, and storage checks pass on Android |
| Chat and streaming | Compose UI and ViewModel connected to runtime API; end-to-end unverified | Compose chat receives and renders real local tokens |
| Local conversation storage | Implemented and merged in PR #7: 100-conversation history picker, up to 500 persisted messages, bounded 16-message / 12,000-character replay, two-session LRU, local generation settings, and confirmation-gated deletion. Three SQLite instrumentation tests passed on API 35 emulator in runs #90 and #92. | Verify native multi-turn replay and deletion/session cleanup on physical Pixel 6 |
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

1. Verify real Gemma 4 E2B download, checksum, load, and multi-turn inference on a physical Pixel 6; no mock inference.
3. Verify offline operation after the model is downloaded.
4. Verify native callback drain, collector cancellation, session cleanup, and transcript replay on the physical device.
5. Measure Pixel 6 peak memory, sustained CPU, latency, and thermal behavior over long sessions.
6. Complete security review and tests before registering any agent tool; every destructive or external side effect must require explicit user approval.
7. Any Compiled Model API feature must pass source parity, task-level correctness, accelerator residency, and lifecycle/memory checks before UI release.
