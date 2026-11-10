# Model Setup — Gemma 4 E2B IT

## Pinned model artifact

- Repository: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
- Revision: `7fa1d78473894f7e736a21d920c3aa80f950c0db`
- File: `gemma-4-E2B-it.litertlm`
- Size: 2,583,085,056 bytes
- SHA-256: `ab7838cdfc8f77e54d8ca45eadceb20452d9f01e4bfade03e5dce27911b27e42`
- Runtime: Google's LiteRT-LM Android Kotlin API
- Download location: app-private `filesDir/models/`
- Initial execution backend: CPU

The downloader uses a revision-pinned HTTPS URL. It checks available storage, follows redirects only when the final URL remains HTTPS, writes to a temporary `.part` file, verifies the exact byte count and SHA-256, flushes the file, and atomically moves the verified file into its final name. Existing files are re-hashed before being reused; invalid existing files and stale partial files must be removed successfully before a new transfer starts. A failed or cancelled transfer never exposes the partial file as an installed model.

The pinned revision is intentional: model repository files can change. Updating to a newer model revision requires updating the URL, byte count, and checksum together after checking the publisher's file metadata.

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

After process death or native-session eviction, the chat orchestration layer restores the persisted transcript and supplies the newest bounded user/assistant turns through `ConversationConfig.initialMessages` when it creates a new LiteRT-LM conversation. Replay is limited to 16 messages and 12,000 characters; this character budget is not tokenizer-aware. The current prompt is passed separately. The locally selected output-token limit is applied through both `ConversationConfig.maxOutputToken` and `sendMessageAsync(maxOutputToken = ...)`.

## Downloader test coverage

The API 35 Android instrumentation suite uses small deterministic byte fixtures and an injected in-memory HTTP connection to test: successful verified installation, checksum mismatch rejection, truncated transfer rejection when content length is unknown, oversized transfer rejection, replacement of a corrupt prior artifact only after the replacement passes verification, and rejection of an HTTP downgrade after redirect. These tests do not fetch the pinned 2.58 GB model, validate the live Hugging Face/CDN redirect chain, measure download speed, or prove behavior under actual Pixel 6 storage pressure. Those remain device/network verification gates.

## Pixel 6 expectations

The model file alone is about 2.58 GB. Actual inference also needs memory for the runtime, KV cache, prompt/context, and Android. The Pixel 6 has 6 GB RAM, so NEXORA should start with conservative context/output limits, close other memory-heavy apps, and gracefully handle Android low-memory termination. Performance and stability on Pixel 6 are not yet device-verified. CPU is the baseline; GPU should be enabled only after testing driver compatibility and native library requirements.

## Licensing

The Hugging Face model card currently identifies the LiteRT Community artifact as Apache-2.0; the upstream Gemma model terms and any required notices must still be reviewed separately before distribution. LiteRT-LM library licensing is also governed by its own repository.
