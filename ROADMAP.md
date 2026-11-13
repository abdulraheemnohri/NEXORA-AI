# NEXORA AI Roadmap

| Area | Status | Exit criteria |
|---|---|---|
| Android Gradle/Compose foundation | PR #7 conversation history, PR #8 stable Ubuntu 24.04/KVM CI, and PR #9 documentation reconciliation merged. Exact-head runs #90, #92, and #96 passed; post-merge main run #94 passed all configured jobs. | Keep main CI green and Windows/Android Studio wrapper builds reproducible |
| LiteRT-LM adapter | CPU adapter uses pinned v0.18.1 callback API; callback completion gate and explicit tool-call disablement merged in PR #3; main run #68 passed; real inference unverified | Real Gemma output is verified on Pixel 6 |
| Model download and integrity | Revision-pinned app-private downloader enforces HTTPS after redirects, exact byte count, SHA-256, and atomic finalization. Six deterministic downloader instrumentation tests passed on API 35 in exact PR-head run #97. The fixtures do not test the live CDN transfer; Pixel 6 storage behavior remains unverified. | Live 2.58 GB download, cancellation, checksum, storage, and retry behavior pass on Pixel 6 |
| Chat and streaming | Compose UI and ViewModel connected to runtime API; end-to-end unverified | Compose chat receives and renders real local tokens |
| Local conversation storage | Implemented and merged in PR #7: 100-conversation history picker, up to 500 persisted messages, bounded 16-message / 12,000-character replay, two-session LRU, local generation settings, and confirmation-gated deletion. Three SQLite instrumentation tests passed on API 35 emulator in runs #90 and #92. | Verify native multi-turn replay and deletion/session cleanup on physical Pixel 6 |
| Google AI Edge integration | LiteRT-LM is primary LLM path; Compiled Model API capability boundary and verification plan documented | First non-LLM model selected, licensed, compiled, parity-checked, and measured on Pixel 6 |
| Speech recognition | Not implemented; candidate LiteRT Compiled Model API task | Model input/output contract, mic permission flow, offline recognition, and Pixel 6 results verified |
| Vision | Not implemented; candidate LiteRT Compiled Model API task | Model and preprocessing verified against fixed reference inputs on Pixel 6 |
| Embeddings and retrieval | Not implemented; candidate local non-LLM model task | Embedding quality and retrieval evaluation pass on local corpus |
| Accelerator strategy | CPU is baseline; Pixel 6 GPU path unverified; Google Tensor NPU path not enabled | Runtime residency and correctness recorded on target device; no silent fallback claims |
| Agent tool permission system | Partial: central executor, one-shot invocation-bound approval grants, and a real user-enabled local read-only conversation search are implemented. Search is manually invoked; no model-generated tool calls or external/destructive handlers are registered. | Add a review/approval UI for a concrete side-effect tool and verify that approval is bound to its exact arguments and cannot be replayed |
| Memory and retrieval | Pending; keyword search is not semantic memory or embeddings | Select a licensed local embedding model and evaluate retrieval quality on a private local corpus |
| Knowledge ingestion | Pending | PDF/TXT/MD/JSON extraction tests |
| MAS-X society foundation | Draft implementation in PR #13: separate SQLite store, declarative agents, durable LAMP, bounded single-model orchestration, scoped memory, explicit per-record memory deletion, settings and Compose control center. Exact-head run #133 passed APK assembly, lint, JVM tests, KVM preflight and Android instrumentation before the memory-deletion follow-up. | Exact-head build/lint/JVM/emulator instrumentation pass for the memory-deletion head, then review and merge; physical Pixel 6 inference remains a separate gate |
| Agent planner | Partial: deterministic keyword routing to one specialist worker; full LLM planning/debate and parallel worker execution are not implemented. | Plan, approval, cancellation, recovery and dependency-DAG tests |
| Skills laboratory | Pending | Schema validation and rollback |
| Evolution engine | Pending | Evaluation, regression and rollback |
| Pixel 6 performance | Pending | Physical-device RAM, speed and thermal report |

## Release gates

1. Verify the real 2.58 GB Gemma 4 E2B download, final HTTPS redirect, checksum, and storage behavior on a physical Pixel 6; no mock inference.
2. Verify real LiteRT-LM model load and multi-turn inference on the physical Pixel 6.
3. Verify offline operation after the model is downloaded.
4. Verify native callback drain, collector cancellation, session cleanup, and transcript replay on the physical device.
5. Measure Pixel 6 peak memory, sustained CPU, latency, and thermal behavior over long sessions.
6. Complete security review and tests before registering any agent tool; every destructive or external side effect must require explicit user approval.
7. Any Compiled Model API feature must pass source parity, task-level correctness, accelerator residency, and lifecycle/memory checks before UI release.
