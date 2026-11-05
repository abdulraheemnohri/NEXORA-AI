# NEXORA AI

**Think Locally. Learn Continuously. Act Intelligently.**

NEXORA AI is a native Android, local-first assistant built with Kotlin and Jetpack Compose. Its first model target is Gemma 4 E2B IT through Google's LiteRT-LM Android runtime.

## Current implementation

- Kotlin Android app foundation and dark Jetpack Compose chat UI.
- ViewModel orchestration for model installation, loading, streaming generation, and chat state.
- App-private SQLite conversation/message persistence.
- Conversation history picker, conversation switching, and confirmation-gated local deletion.
- Transcript replay into LiteRT-LM after a conversation is reopened or its native session is evicted.
- Bounded runtime history replay: latest 16 messages and at most 12,000 characters.
- Least-recently-used cache for at most two native LiteRT-LM conversations.
- On-device generation settings for 256, 512, 1,024, or 2,048 output tokens (default 512).
- Risk-based agent tool approval policy; automatic LiteRT-LM tool calling remains disabled and no app tools are registered.
- Pinned LiteRT-LM Android dependency and compatible Kotlin/AGP toolchain.
- LiteRT-LM CPU runtime adapter with streamed generation events.
- Gemma 4 E2B downloader with app-private storage, free-space checks, pinned model revision, byte-count validation, and SHA-256 verification.
- GitHub Actions workflow for debug APK assembly, lint, and unit tests.

## Honest project status

The conversation history, replay, local deletion, and generation settings are implemented in source with focused JVM unit tests for replay bounds and settings validation. The first PR CI attempt exposed an existing Kotlin 2.3.21 build-script error caused by the deprecated Android `kotlinOptions.jvmTarget` DSL; the feature branch now migrates to the typed `compilerOptions` DSL, and a rerun is pending. A successful build/test run and Android runtime validation are still required. The LiteRT-LM API surface was checked against Google's upstream v0.18.1 source, but source inspection is not a substitute for compiling against the resolved Android artifact.

Real Gemma inference, download behavior on Android, offline operation, and Pixel 6 memory/thermal performance are not claimed as verified. No mock inference path is included.

## Requirements

- JDK 17
- Android SDK 35
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Kotlin 2.3.21
- Android minSdk 26

Open the project in Android Studio. The Gradle wrapper is not yet committed. GitHub Actions provisions Gradle 8.13 directly; local command-line builds currently require an installed Gradle 8.13 distribution.

## Model

See [Model Setup](docs/MODEL_SETUP.md). The pinned model artifact is approximately 2.58 GB and is downloaded separately from the APK into app-private storage. It is not committed to Git.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Model setup](docs/MODEL_SETUP.md)
- [LiteRT-LM integration](docs/LITERT_LM_INTEGRATION.md)
- [Local data and agent safety](docs/LOCAL_DATA_AND_AGENT_SAFETY.md)
- [Security](docs/SECURITY.md)
- [Roadmap](ROADMAP.md)
- [Daily progress](docs/DAILY_PROGRESS.md)

## Licensing

Project source licensing must be finalized before public release. Gemma model terms and LiteRT-LM library licensing are separate and remain governed by their respective owners.
