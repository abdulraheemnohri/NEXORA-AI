# NEXORA AI

**Think Locally. Learn Continuously. Act Intelligently.**

NEXORA AI is a native Android local-first autonomous intelligence project. Its target inference stack is Google's Gemma 4 E2B IT model running through Google's LiteRT-LM Android runtime, with a built-in Kotlin application backend.

> Project status: foundation scaffold committed. The production LiteRT-LM adapter, model provisioning, database, agent execution and device validation are not yet complete. This repository does not pretend otherwise.

## Product goals
- On-device model inference; no mandatory cloud AI API.
- Native Kotlin backend and Jetpack Compose interface.
- Private local memory and document knowledge.
- Permission-gated agent tools and task execution.
- Validated skills, evaluation and rollback.
- Pixel 6 (6 GB RAM) as the initial device target.

## Build
Open in Android Studio with JDK 17 and Android SDK 35 installed. The Gradle wrapper still needs to be generated and committed by an Android Studio or Gradle bootstrap environment.

## Model
See docs/MODEL_SETUP.md. Model weights are not included in the APK or repository. Use the official LiteRT-LM model artifact and verify its license and integrity before installation.

## Documentation
- Architecture: docs/ARCHITECTURE.md
- Model setup: docs/MODEL_SETUP.md
- Security: docs/SECURITY.md
- Roadmap: ROADMAP.md

## License
Project source licensing will be finalized before public release. Third-party model and runtime licenses remain governed by their respective owners.
