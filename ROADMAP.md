# NEXORA AI Roadmap

| Area | Status | Exit criteria |
|---|---|---|
| Android Gradle/Compose foundation | Main contains Kotlin 2.3.21 typed compiler options, JDK 21 CI, debug APK assembly, and Android lint. PR #3 head run #67 passed all configured CI steps. Gradle wrapper is still absent. | Wrapper committed; latest main and feature-head CI pass; reproducible local build documented |
| LiteRT-LM adapter | Main uses pinned v0.18.1 CPU callback API, serialized native requests, terminal-callback completion gate, and explicit automaticToolCalling=false. | Real Gemma multi-turn inference and callback lifecycle verified on Pixel 6 |
| Model download and integrity | Revision-pinned downloader with app-private destination, free-space checks, byte-count validation, and SHA-256; device behavior unverified. | Download, checksum, cancellation, low-storage, and retry tests pass on Android |
| Chat and streaming | Compose UI and ViewModel are wired to the local runtime API; real model output is not device-verified. | Pixel 6 receives and renders real Gemma stream output |
| Local conversation storage | Main stores chats in SQLite. Draft [PR #4](https://github.com/abdulraheemnohri/NEXORA-AI/pull/4) on `integration/local-conversation-history` adds startup restore, history switching, title updates, confirmation-gated deletion, and native cache cleanup. CI run #69 found a Kotlin return-type mismatch in `forgetConversation`; commit `7e2a64f` fixes it. Final-head CI remains a merge gate. | Branch CI passes; SQLite lifecycle instrumentation, process-restart replay, and delete cascade verified |
| Conversation replay | Draft PR #4 replays up to 16 previous user/assistant messages and 12,000 characters via LiteRT-LM ConversationConfig.initialMessages; callback bridge is preserved. | Compile/test pass and real multi-turn replay verified after process restart on Pixel 6 |
| Generation settings | Draft PR #4 adds locally persisted 256/512/1,024/2,048 output-token options and passes the chosen limit to LiteRT-LM. | Branch CI passes; settings persistence and Compose interaction verified on Android |
| Google AI Edge integration | LiteRT-LM is the LLM path; LiteRT Compiled Model API is reserved for non-LLM tasks. | First non-LLM model selected, licensed, compiled, parity-checked, and measured on Pixel 6 |
| Speech recognition | Not implemented; candidate non-LLM LiteRT task. | Model contract, permission flow, offline recognition, and Pixel 6 results verified |
| Vision | Not implemented; candidate non-LLM LiteRT task. | Model/preprocessing verified against fixed reference inputs on Pixel 6 |
| Embeddings and retrieval | Not implemented. | Embedding quality and local-corpus retrieval evaluation pass |
| Accelerator strategy | CPU is the baseline; Pixel 6 GPU path is unverified; no Tensor NPU support is claimed. | Runtime residency and correctness recorded on target hardware |
| Agent tool permission system | Risk-policy primitives exist; no integrated tool executor. LiteRT-LM automatic tool calling remains disabled. | Every tool re-checks explicit approval immediately before side effects; audit and rollback tests pass |
| Memory and retrieval | Pending. | Indexed local recall tests |
| Knowledge ingestion | Pending. | PDF/TXT/MD/JSON extraction and size-limit tests |
| Agent planner | Pending. | Plan, approval, cancellation, and bounded execution tests |
| Skills laboratory | Pending. | Schema validation, permission checks, and rollback |
| Evolution engine | Pending. | Evaluation, regression detection, and rollback |
| Pixel 6 performance | Pending. | Physical-device peak RAM, token speed, sustained CPU, thermal, and long-session report |

## Release gates

1. Commit a reproducible Gradle wrapper.
2. Require debug APK assembly, Android lint, and JVM unit tests to pass on the exact reviewed commit.
3. Add and run Android instrumentation for SQLite restore, message ordering, foreign-key deletion, and runtime lifecycle.
4. Verify real Gemma download, load, multi-turn inference, and streamed completion.
5. Verify offline operation after a successful model download.
6. Review tool execution, approval, path constraints, and audit logging before enabling any app tools.
7. Measure memory, CPU, thermal behavior, and long-session stability on a physical Pixel 6.
8. Do not claim GPU/NPU acceleration unless runtime residency and task correctness are measured on the target device.

## Branch hygiene

- Draft PR #1 remains open and diverged (42 commits ahead / 11 behind main at the 2026-11-08 checkpoint). Its branch is not force-updated or merged.
- `integration/local-conversation-history` is a fresh branch from current main and is the conflict-safe integration candidate.
- Merge only after the exact branch head passes CI and the diff is reviewed.
