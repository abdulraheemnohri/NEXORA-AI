# Model Setup

Primary target: Gemma 4 E2B IT in Google's LiteRT-LM .litertlm format.

Model repository: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm

1. Verify the current LiteRT-LM Android SDK version and Kotlin API against Google's official documentation.
2. Verify the exact model artifact, license, checksum and supported modalities.
3. Download the model separately from the APK into app-private storage.
4. Verify checksum and file size before loading.
5. Never package multi-gigabyte model weights inside the APK.
6. Test cold start, warm start, cancellation, unload and low-memory recovery.

Model availability, download URL, artifact filename and checksums must be read from the official repository at implementation time; do not hardcode unverified values.
