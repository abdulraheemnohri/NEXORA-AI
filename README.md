# NEXORA AI

**Think Locally. Learn Continuously. Act Intelligently.**

NEXORA AI is a native Android, local-first assistant built with Kotlin and Jetpack Compose. Its first model target is Gemma 4 E2B IT through Google's LiteRT-LM Android runtime.

## Current implementation

- Kotlin Android app foundation and dark Jetpack Compose chat UI.
- ViewModel orchestration for model installation, loading, streaming generation, and chat state.
- Local SQLite conversation/message persistence.
- Risk-based agent tool approval policy.
- Pinned LiteRT-LM Android dependency and Kotlin/Android toolchain.
- LiteRT-LM CPU runtime adapter using the pinned callback API, streamed generation events, and per-request output-token limits.
- Native callback completion gate: cancelling a collector stops UI delivery but keeps the generation lock until LiteRT-LM reports a terminal callback, preventing premature conversation reuse or unload.
- Automatic LiteRT-LM tool calling is explicitly disabled; no model-generated app side effect is registered.
- LiteRT-LM callback lifecycle hardening is merged in [PR #3](https://github.com/abdulraheemnohri/NEXORA-AI/pull/3) as `b399c6a`; main run [#68](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42385336171) passed debug APK assembly, Android lint, and JVM unit tests. Device inference and cancellation behavior remain unverified.
- Gemma 4 E2B downloader with app-private storage, free-space checks, pinned model revision, byte-count validation, and SHA-256 verification.
- Verified Gradle 8.13 wrapper with official distribution SHA-256 pin, merged in [PR #5](https://github.com/abdulraheemnohri/NEXORA-AI/pull/5) as [`0afe596`](https://github.com/abdulraheemnohri/NEXORA-AI/commit/0afe596566fb878e32f6aba4cc57aed687ffbf3f). Latest PR-head run [#80](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42412475976) passed wrapper validation, debug APK assembly, Android lint, and JVM unit tests on the exact reviewed head.
- GitHub Actions workflow for debug APK assembly, Android lint, and JVM unit tests.

## Google AI Edge stack

NEXORA follows Google's separate runtime paths by workload:

- **LiteRT-LM:** Gemma `.litertlm` chat and text generation. This remains the primary LLM runtime.
- **LiteRT Compiled Model API:** planned for verified non-LLM tasks such as speech recognition, image processing, and embeddings when a suitable model artifact is selected.
- **LiteRT samples and agent skills:** used as the implementation and validation reference for Android runtime lifecycle, model delivery, accelerator verification, and performance measurements.

The Compiled Model API is not a replacement for LiteRT-LM. No legacy TensorFlow Lite `Interpreter` API, Ollama, llama.cpp, external Python inference server, cloud AI API, Firebase, or Docker is part of the NEXORA runtime design. Pixel 6 CPU inference remains the baseline; accelerator use must be verified on the actual device before being advertised.

See [Google AI Edge integration](docs/GOOGLE_AI_EDGE_INTEGRATION.md) for the runtime map, device policy, and implementation gates.

## Honest project status

PR #2 merged the Android CI and LiteRT-LM compatibility fixes into `main` as commit [`143a8a6`](https://github.com/abdulraheemnohri/NEXORA-AI/commit/143a8a6586c44ecfe4a1da7907a34c07e9264d3b). GitHub Actions run [#62](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42179552385) passed debug APK assembly, Android lint, and JVM unit tests on the reviewed PR head. Post-merge main workflow [#64](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42287012972) passed on commit `3e95c9d`; main baseline [#68](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42385336171) passed all configured checks on `b399c6a`. PR #5 subsequently merged the reproducible Gradle 8.13 wrapper as `0afe596`; its exact final PR head passed run #80. A separate post-merge main status result was not returned by the current status lookup, so no post-merge CI pass is claimed.

Conversation history browsing/replay and local generation settings have been ported onto the wrapper-enabled `main` in `integration/chat-history-wrapper-main`. Draft PR [#7](https://github.com/abdulraheemnohri/NEXORA-AI/pull/7) adds bounded LiteRT-LM transcript replay, local output-token settings, and Android SQLite instrumentation tests. Its exact-head Actions run [#88](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42477644347) passed debug APK assembly, Android lint, and JVM unit tests, but the instrumentation suite did not start: the API 35 emulator remained ADB-offline because the Ubuntu runner lacked read/write access to `/dev/kvm`, then hit the 600-second boot timeout. The PR branch now installs the documented KVM udev permissions and explicitly requires hardware acceleration; a fresh exact-head CI run must verify that mitigation before the instrumentation tests can be called passing. Earlier PR [#4](https://github.com/abdulraheemnohri/NEXORA-AI/pull/4) and original PR [#1](https://github.com/abdulraheemnohri/NEXORA-AI/pull/1) remain open and preserved. No force-push or branch deletion was performed. Real Gemma download/loading/inference, offline-after-download behavior, and physical Pixel 6 memory/thermal validation remain separate release gates. CI success does not prove device inference.

## Requirements

- JDK 21 for the current CI/test toolchain; app bytecode target remains Java 17.
- Android SDK 35
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Kotlin 2.3.21
- Android minSdk 26

Open the project in Android Studio. The checked-in Gradle Wrapper pins Gradle 8.13 and verifies the official distribution checksum before use. See [Building NEXORA](docs/BUILDING.md) for Windows, Linux, and Android Studio setup.

## Model

See [Model Setup](docs/MODEL_SETUP.md). The pinned model artifact is approximately 2.58 GB and is downloaded separately from the APK into app-private storage. It is not committed to Git.

## Documentation

- [Building NEXORA](docs/BUILDING.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Google AI Edge integration](docs/GOOGLE_AI_EDGE_INTEGRATION.md)
- [Model setup](docs/MODEL_SETUP.md)
- [LiteRT-LM integration](docs/LITERT_LM_INTEGRATION.md)
- [Local data and agent safety](docs/LOCAL_DATA_AND_AGENT_SAFETY.md)
- [Security](docs/SECURITY.md)
- [Roadmap](ROADMAP.md)

## Licensing

Project source licensing must be finalized before public release. Gemma model terms and LiteRT-LM library licensing are separate and remain governed by their respective owners.
