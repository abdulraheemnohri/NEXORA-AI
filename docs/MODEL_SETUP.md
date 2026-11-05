# Model Setup — Gemma 4 E2B IT

## Pinned model artifact

- Repository: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
- File: `gemma-4-E2B-it.litertlm`
- Published size: 2,583,085,056 bytes (about 2.58 GB)
- Runtime: Google's LiteRT-LM Android Kotlin API
- Download location: app-private `filesDir/models/`
- Initial execution backend: CPU

The app downloads the model separately from the APK. It checks available storage, follows HTTPS redirects, writes to a temporary file, checks the final byte count, flushes it, and only then renames it to the model filename. A failed or cancelled download is not treated as a valid model.

## Android dependency and compiler compatibility

The app pins `com.google.ai.edge.litertlm:litertlm-android:0.18.1`. LiteRT-LM 0.17.x and 0.18.x publish Kotlin 2.4.0 metadata, so the consumer compiler must be Kotlin 2.3 or newer. This project uses Kotlin 2.3.21, AGP 8.13.2, Gradle 8.13, and JDK 17. Keep these versions aligned when upgrading.

Official Kotlin API guide:
https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md

## Runtime lifecycle

`LiteRtLocalModelRuntime` currently:
1. Validates that the model path points to a readable, non-empty file.
2. Creates the LiteRT-LM engine on Dispatchers.IO.
3. Starts with the CPU backend for conservative compatibility.
4. Creates and caches one in-memory runtime conversation per app conversation ID.
5. Streams response text as `GenerationEvent.Token` events.
6. Closes conversations and engine on unload.

The persisted app transcript is not automatically injected into LiteRT-LM after process death yet. The chat orchestration layer must restore the transcript into a new runtime conversation in a later milestone. `GenerationRequest.maxOutputTokens` remains a contract field and is not yet mapped to a LiteRT-LM sampler setting.

## Pixel 6 expectations

The model file alone is about 2.58 GB. Actual inference also needs memory for the runtime, KV cache, prompt/context, and Android. The Pixel 6 has 6 GB RAM, so NEXORA should start with conservative context/output limits, close other memory-heavy apps, and gracefully handle Android low-memory termination. Performance and stability on Pixel 6 are not yet device-verified. CPU is the baseline; GPU should be enabled only after testing driver compatibility and native library requirements.

## Integrity and licensing

The downloader validates the published byte size, not a cryptographic checksum. Add a verified SHA-256 from the model publisher before calling this a cryptographically verified download. Gemma model terms apply independently of this app; review the model license and terms before distribution.
