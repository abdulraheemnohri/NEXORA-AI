# NEXORA AI

**Think Locally. Learn Continuously. Act Intelligently.**

NEXORA AI is a native Android, local-first assistant built with Kotlin and Jetpack Compose. Its first model target is Gemma 4 E2B IT through Google's LiteRT-LM Android runtime.

## Current implementation

- Kotlin Android app foundation and Compose UI.
- Local SQLite conversation/message persistence.
- Risk-based agent tool approval policy.
- Pinned LiteRT-LM Android dependency and compatible Kotlin/AGP toolchain.
- LiteRT-LM CPU runtime adapter with streamed generation events.
- Gemma 4 E2B model downloader with private storage, free-space checks, temporary-file handling, and final-size validation.
- GitHub Actions workflow for debug APK assembly and unit tests.

## Honest project status

The runtime adapter and downloader are now present in source, but the complete app flow is not finished. The Compose UI is not yet connected to the database/runtime, conversation replay after process death is pending, and the model download/inference flow has not yet been built or tested on a Pixel 6. CI/build success and real-device inference are not claimed until verified.

## Requirements

- JDK 17
- Android SDK 35
- Android Gradle Plugin 8.13.2
- Gradle 8.13
- Kotlin 2.3.21
- Android minSdk 26

Open the project in Android Studio. The Gradle wrapper still needs to be generated and committed; until then, use an installed Gradle 8.13 distribution.

## Model

See [Model Setup](docs/MODEL_SETUP.md). The model is approximately 2.58 GB and is downloaded separately from the APK into app-private storage. It is not committed to Git.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Model setup](docs/MODEL_SETUP.md)
- [LiteRT-LM integration](docs/LITERT_LM_INTEGRATION.md)
- [Local data and agent safety](docs/LOCAL_DATA_AND_AGENT_SAFETY.md)
- [Security](docs/SECURITY.md)
- [Roadmap](ROADMAP.md)

## Licensing

Project source licensing must be finalized before public release. Gemma model terms and LiteRT-LM library licensing are separate and remain governed by their respective owners.
