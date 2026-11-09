# NEXORA AI Roadmap

| Area | Status | Exit criteria |
|---|---|---|
| Android Gradle/Compose foundation | PR #2 and callback lifecycle PR #3 merged; main run #68 passed. Verified Gradle 8.13 wrapper merged in PR #5 as `0afe596`; exact PR-head run #80 passed wrapper validation, debug APK assembly, Android lint, and JVM unit tests. Post-merge main status has not yet been independently verified. | Keep main CI green and Windows/Android Studio wrapper builds reproducible |
| LiteRT-LM adapter | CPU adapter uses pinned v0.18.1 callback API; callback completion gate and explicit tool-call disablement merged in PR #3; main run #68 passed; real inference unverified | Real Gemma output is verified on Pixel 6 |
| Model download and integrity | Revision-pinned downloader with SHA-256; not device-tested | Download, checksum, cancellation, and storage checks pass on Android |
| Chat and streaming | Compose UI and ViewModel connected to runtime API; end-to-end unverified | Compose chat receives and renders real local tokens |
| Local conversation storage | History/replay and generation settings are ported in draft PR [#7](https://github.com/abdulraheemnohri/NEXORA-AI/pull/7). Exact-head run [#88](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42477644347) passed APK assembly, lint, and JVM tests, but instrumentation never started because the Ubuntu emulator had no `/dev/kvm` access and timed out. The workflow now applies KVM udev permissions and requires hardware acceleration; exact-head rerun is pending. PR #1 and #4 branches remain preserved. | API 35 emulator instrumentation passes; native multi-turn replay verified on Pixel 6 |
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

1. Keep the merged Gradle 8.13 wrapper on main and verify the post-merge main workflow.
2. Verify the KVM-enabled exact-head CI rerun for draft PR #7; confirm the API 35 emulator boots and executes the SQLite instrumentation suite.
3. Review and merge the history/replay port only after required CI passes and the branch diff is conflict-safe; preserve original PR #1 and PR #4 history.
4. Separately verify native conversation lifecycle and bounded multi-turn replay on a physical device.
5. Verify real Gemma download, load, and multi-turn inference on Pixel 6; no mock inference.
6. Verify offline operation after model download.
7. Complete security review of tool execution before registering app tools.
8. Measure Pixel 6 memory, latency, and thermal behavior.
9. Any Compiled Model API feature must pass source parity, task-level correctness, accelerator residency, and lifecycle/memory checks before UI release.
