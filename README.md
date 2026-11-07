# NEXORA AI

**Think Locally. Learn Continuously. Act Intelligently.**

NEXORA AI is a native Android assistant built with Kotlin and Jetpack Compose. Its first local language model is Gemma 4 E2B IT, executed through Google's LiteRT-LM Android runtime. The application backend runs in-process; prompts and chat history are stored locally.

## Runtime constraints

- **LLM:** Google's LiteRT-LM Android runtime only.
- **Model:** Gemma 4 E2B IT in the `.litertlm` format.
- **Baseline backend:** LiteRT-LM CPU.
- **Initial device:** Google Pixel 6, 6 GB RAM.
- **Data:** app-private SQLite and SharedPreferences.
- **Network:** required only to download the model; chat inference is designed to run locally after installation.
- **Not part of this project:** Ollama, llama.cpp, external Python inference servers, mandatory cloud AI APIs, Firebase, and Docker.

## Implemented on main

- Native Kotlin Android app foundation and Jetpack Compose chat interface.
- ViewModel orchestration for model download, loading, and streamed generation.
- App-private SQLite conversation/message persistence.
- Gemma 4 E2B downloader with pinned revision, free-space checks, byte-count validation, and SHA-256 verification.
- LiteRT-LM v0.18.1 CPU adapter with streamed callback delivery and per-request output-token limits.
- Native callback completion gate: downstream cancellation stops UI delivery but does not release the generation lock until LiteRT-LM reports a terminal callback.
- Automatic LiteRT-LM tool calling is disabled; no model-generated app side effect is registered.
- GitHub Actions checks for debug APK assembly, Android lint, and JVM unit tests.

## Conversation history integration branch

The focused branch `integration/local-conversation-history` ports the still-open history work onto the current main baseline while preserving the callback-safe LiteRT-LM adapter:

- Restore the most recently updated conversation at startup.
- Browse and switch between the 100 most recently updated local conversations.
- Load up to 500 messages per conversation in chronological order.
- Update a new conversation's title from its first prompt, capped at 60 characters.
- Require explicit confirmation before deleting a conversation and its messages.
- Close the deleted conversation's cached native LiteRT-LM session.
- Replay the previous user/assistant turns after process restart or native-session eviction.
- Bound native replay to the newest 16 messages and 12,000 characters; this is a character memory guard, not a tokenizer-aware context guarantee.
- Keep at most two native conversations in an access-ordered LRU cache.
- Persist the 256, 512, 1,024, or 2,048 output-token preference in app-private SharedPreferences.

The existing draft [PR #1](https://github.com/abdulraheemnohri/NEXORA-AI/pull/1) and its diverged branch are left untouched. The new integration branch is based on current main and is not merged until its own CI and review gates pass.

## Google AI Edge workload split

- **LiteRT-LM:** Gemma `.litertlm` language-model inference.
- **LiteRT Compiled Model API:** a separate future path for non-LLM tasks such as speech, vision, or embeddings, only after a licensed artifact and device-level parity tests are available.

The Compiled Model API is not a replacement for LiteRT-LM. Pixel 6 CPU remains the baseline; no GPU or NPU capability is claimed without device evidence.

## Verification status

- PR [#2](https://github.com/abdulraheemnohri/NEXORA-AI/pull/2) merged the Kotlin/Android CI compatibility fixes.
- PR [#3](https://github.com/abdulraheemnohri/NEXORA-AI/pull/3) merged the LiteRT-LM callback lifecycle fix as [`b399c6a`](https://github.com/abdulraheemnohri/NEXORA-AI/commit/b399c6a9c370a8dea98f2475f9ee021164c14d19).
- GitHub Actions run [#67](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42291721158) on PR #3's reviewed head passed debug APK assembly, Android lint, and JVM unit tests.
- Draft [PR #4](https://github.com/abdulraheemnohri/NEXORA-AI/pull/4) now carries the integration. Its initial Actions run [#69](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42410815028) was queued for the reviewed PR head; the final updated head must complete its own CI before merge.
- The latest main commit has no PR-triggered workflow run returned by the current Actions lookup; no latest-main build result is inferred from PR-head CI.
- No real Gemma download/load/inference, offline-after-download test, Android instrumentation run, or physical Pixel 6 memory/thermal measurement is claimed.

## Build requirements

- JDK 21 for the current CI/test toolchain.
- Java 17 app bytecode.
- Android SDK 35.
- Android Gradle Plugin 8.13.2.
- Gradle 8.13.
- Kotlin 2.3.21.
- Android minSdk 26.

The Gradle wrapper is not committed yet. GitHub Actions provisions Gradle 8.13 directly; local command-line builds require an installed Gradle 8.13 distribution until the wrapper is added.

## Model setup

See [Model Setup](docs/MODEL_SETUP.md). The pinned model artifact is approximately 2.58 GB and is downloaded separately from the APK into app-private storage. Model weights are not committed to Git.

## Documentation

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
