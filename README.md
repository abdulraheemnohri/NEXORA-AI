# NEXORA AI

**Think Locally. Learn Continuously. Act Intelligently.**

NEXORA AI is a native Android, local-first assistant built with Kotlin and Jetpack Compose. Its first model target is Gemma 4 E2B IT through Google's LiteRT-LM Android runtime.

## Current implementation

- Kotlin Android app foundation and dark Jetpack Compose chat UI.
- ViewModel orchestration for model installation, loading, streaming generation, and chat state.
- Local SQLite conversation history with startup restoration, a 100-conversation history picker, chronological loading of up to 500 messages, and confirmation-gated deletion.
- Bounded multi-turn replay through LiteRT-LM v0.18.1: up to 16 recent user/assistant messages and 12,000 characters, with a two-entry native-session LRU. The character budget is not a tokenizer-aware context guarantee.
- Locally persisted generation settings for 256 / 512 / 1,024 / 2,048 maximum output tokens (default 512).
- API 35 Android emulator instrumentation for SQLite reopen persistence, ordering, recent-conversation ordering, and foreign-key cascade deletion.
- Risk-based agent tool approval policy.
- Pinned LiteRT-LM Android dependency and Kotlin/Android toolchain.
- LiteRT-LM CPU runtime adapter using the pinned callback API, streamed generation events, and per-request output-token limits.
- Native callback completion gate: cancelling a collector stops UI delivery but keeps the generation lock until LiteRT-LM reports a terminal callback, preventing premature conversation reuse or unload.
- Automatic LiteRT-LM tool calling is explicitly disabled; no model-generated app side effect is registered.
- LiteRT-LM callback lifecycle hardening is merged in [PR #3](https://github.com/abdulraheemnohri/NEXORA-AI/pull/3) as `b399c6a`; main run [#68](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42385336171) passed debug APK assembly, Android lint, and JVM unit tests. Device inference and cancellation behavior remain unverified.
- Gemma 4 E2B downloader with app-private storage, free-space checks, pinned model revision, HTTPS redirect enforcement, exact byte-count and SHA-256 verification, and atomic finalization.
- New API 35 instrumentation coverage exercises downloader success, checksum rejection, truncated/oversized responses, corrupt-file replacement, and HTTPS downgrade rejection using small deterministic fixtures; this does not substitute for a live 2.58 GB download.
- Verified Gradle 8.13 wrapper with official distribution SHA-256 pin, merged in [PR #5](https://github.com/abdulraheemnohri/NEXORA-AI/pull/5) as [`0afe596`](https://github.com/abdulraheemnohri/NEXORA-AI/commit/0afe596566fb878e32f6aba4cc57aed687ffbf3f). Latest PR-head run [#80](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42412475976) passed wrapper validation, debug APK assembly, Android lint, and JVM unit tests on the exact reviewed head.
- GitHub Actions workflow for debug APK assembly, Android lint, JVM unit tests, and API 35 emulator instrumentation. The emulator job is pinned to the stable Ubuntu 24.04 image and fails fast with explicit KVM diagnostics.

## Google AI Edge stack

NEXORA follows Google's separate runtime paths by workload:

- **LiteRT-LM:** Gemma `.litertlm` chat and text generation. This remains the primary LLM runtime.
- **LiteRT Compiled Model API:** planned for verified non-LLM tasks such as speech recognition, image processing, and embeddings when a suitable model artifact is selected.
- **LiteRT samples and agent skills:** used as the implementation and validation reference for Android runtime lifecycle, model delivery, accelerator verification, and performance measurements.

The Compiled Model API is not a replacement for LiteRT-LM. No legacy TensorFlow Lite `Interpreter` API, Ollama, llama.cpp, external Python inference server, cloud AI API, Firebase, or Docker is part of the NEXORA runtime design. Pixel 6 CPU inference remains the baseline; accelerator use must be verified on the actual device before being advertised.

See [Google AI Edge integration](docs/GOOGLE_AI_EDGE_INTEGRATION.md) for the runtime map, device policy, and implementation gates.

## Honest project status

PR #2 merged the Android CI and LiteRT-LM compatibility fixes into `main`; PR #3 added the native callback completion gate; PR #5 merged the verified Gradle 8.13 wrapper. Their historical build results are recorded in [daily progress](docs/DAILY_PROGRESS.md).

PR [#7](https://github.com/abdulraheemnohri/NEXORA-AI/pull/7) merged on 2026-11-09 as [`9729bcf`](https://github.com/abdulraheemnohri/NEXORA-AI/commit/9729bcf9b03cf4da0aa96a20accb3dad1b9440c8). It adds persistent conversation browsing/restoration, bounded LiteRT-LM multi-turn replay, local generation settings, confirmation-gated deletion, and SQLite instrumentation. Its exact-head Actions run [#90](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42522885207) passed debug APK assembly, Android lint, JVM unit tests, KVM preflight, and all three API 35 SQLite instrumentation tests.

PR [#8](https://github.com/abdulraheemnohri/NEXORA-AI/pull/8) merged on 2026-11-09 as [`651a129`](https://github.com/abdulraheemnohri/NEXORA-AI/commit/651a129ad0b0a9537e5e56decbd8fdf791ac932c). It pins Android CI to stable Ubuntu 24.04 and adds explicit diagnostics when `/dev/kvm` is absent or inaccessible. Its exact-head run [#92](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42691929272) passed APK assembly, lint, JVM tests, KVM preflight, and all three emulator instrumentation tests. The post-merge main run [#94](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42692535230) also completed successfully: debug APK assembly, lint, JVM tests, KVM preflight, emulator boot, and all three API 35 SQLite instrumentation tests passed.

Main run [#91](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42691148102) passed APK assembly, lint, and JVM tests but failed the KVM preflight twice because that hosted runner did not expose `/dev/kvm`; instrumentation was skipped. This was a runner-capability failure, not a Kotlin or SQLite test failure. The Ubuntu 24.04 pin then passed on PR #8.

Original PRs [#1](https://github.com/abdulraheemnohri/NEXORA-AI/pull/1) and [#4](https://github.com/abdulraheemnohri/NEXORA-AI/pull/4) remain open and their diverged branches are preserved. The merged PR #7 and #8 branches are also retained; no force-push or branch deletion was performed. Real Gemma download/loading/inference, offline-after-download behavior, callback-drain behavior on device, and physical Pixel 6 memory/thermal validation remain separate release gates. PR [#9](https://github.com/abdulraheemnohri/NEXORA-AI/pull/9) merged on 2026-11-10 as [2bfaae7](https://github.com/abdulraheemnohri/NEXORA-AI/commit/2bfaae7e18e70a37255cfd0e442c132252a053e8); its exact-head run [#96](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42693283140) passed build, lint, JVM tests, KVM preflight, and all three existing SQLite instrumentation tests. CI success does not prove device inference.

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
- [Daily progress](docs/DAILY_PROGRESS.md)

## Licensing

Project source licensing must be finalized before public release. Gemma model terms and LiteRT-LM library licensing are separate and remain governed by their respective owners.
