# NEXORA AI

**Think Locally. Learn Continuously. Act Intelligently.**

NEXORA AI is a native Android, local-first assistant built with Kotlin and Jetpack Compose. Its first model target is Gemma 4 E2B IT through Google's LiteRT-LM Android runtime.

## Current implementation

- Kotlin Android app foundation and dark Jetpack Compose chat UI.
- ViewModel orchestration for model installation, loading, streaming generation, and chat state.
- Local SQLite conversation/message persistence.
- Risk-based agent tool approval policy.
- Pinned LiteRT-LM Android dependency and Kotlin/Android toolchain.
- LiteRT-LM CPU runtime adapter with streamed generation events and per-request output-token limits.
- Gemma 4 E2B downloader with app-private storage, free-space checks, pinned model revision, byte-count validation, and SHA-256 verification.
- GitHub Actions workflow for debug APK assembly, Android lint, and JVM unit tests.

## Honest project status

The current main branch has a known CI failure: Kotlin 2.3.21 rejects the deprecated `android.kotlinOptions.jvmTarget` DSL. The focused fix branch migrates to typed `kotlin.compilerOptions`, uses JDK 21 for the LiteRT-LM test runtime, and reads streamed LiteRT-LM message content through the documented `Message.toString()` API. These changes are awaiting CI verification.

The chat UI, downloader, runtime adapter, and SQLite repository are present in source. Conversation history browsing/replay, full instrumentation coverage, real-device model inference, offline behavior, and Pixel 6 memory/thermal validation remain release gates. CI/build success and real-device inference are not claimed until verified.

## Requirements

- JDK 21 for the current CI/test toolchain; app bytecode target remains Java 17.
- Android SDK 35
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Kotlin 2.3.21
- Android minSdk 26

Open the project in Android Studio. The Gradle wrapper still needs to be generated and committed; until then, use an installed Gradle 8.13 distribution.

## Model

See [Model Setup](docs/MODEL_SETUP.md). The pinned model artifact is approximately 2.58 GB and is downloaded separately from the APK into app-private storage. It is not committed to Git.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Model setup](docs/MODEL_SETUP.md)
- [LiteRT-LM integration](docs/LITERT_LM_INTEGRATION.md)
- [Local data and agent safety](docs/LOCAL_DATA_AND_AGENT_SAFETY.md)
- [Security](docs/SECURITY.md)
- [Roadmap](ROADMAP.md)

## Licensing

Project source licensing must be finalized before public release. Gemma model terms and LiteRT-LM library licensing are separate and remain governed by their respective owners.
