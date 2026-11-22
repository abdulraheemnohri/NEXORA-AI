# Local knowledge vault

## Supported import formats

The initial implementation imports UTF-8 .txt, .md, .markdown, and .json files using Android's Storage Access Framework. The picker is user initiated. NEXORA reads the granted URI once and does not call takePersistableUriPermission; the original document is never modified. HTTP URIs are rejected. Cloud-backed Android providers may fetch a source before exposing its content URI, so choose an already downloaded file for offline import. JSON is indexed as raw text, not parsed into structured records.

PDF, DOCX, HTML, OCR, and binary formats are not supported by this increment.

## Resource and storage bounds

- Maximum source file: 512 KiB.
- Maximum decoded text: 450,000 characters.
- Chunk size: 2,000 characters.
- Chunk overlap: 200 characters.
- Maximum chunks per document: 256.
- Maximum stored documents: 50.
- Maximum total source bytes: 8 MiB.
- Search query: 2–128 characters.
- Search results: at most 20 excerpts, each at most 240 characters.

UTF-8 decoding is strict. Malformed byte sequences and unsupported control characters are rejected. The importer computes SHA-256 over the original bytes; byte-identical documents are not imported twice, even if their file names differ.

## Storage and deletion

Metadata and chunk rows live in the separate app-private SQLite database nexora_knowledge.db. Document and chunk insertion is transactional. Foreign-key cascade removes chunks when a document is deleted. The UI requires explicit confirmation before deleting one document or clearing the entire vault. Bulk clear deletes all document rows in one SQLite transaction; foreign-key cascade removes every indexed chunk. The UI also clears search results and pending one-turn source selections after a successful clear.

Deletion removes NEXORA's local indexed copy only. It does not delete the original source file and does not claim secure flash erasure. App-private database encryption is not implemented.

## Retrieval boundary

Search is a manually invoked, parameterized SQLite keyword query. It is bounded by the vault's 8 MiB source quota and returns at most 20 snippets. SQLite's built-in lower() provides ASCII case folding; this is not semantic search or cross-language case folding.

Imported content is never automatically placed in a Gemma prompt and is not registered as a model tool. A user may explicitly select up to three search excerpts for the next reply. The composer displays the selected source names and allows clearing them before send. The selected excerpts are added only to that one generation request as bounded JSON-escaped reference data with an explicit untrusted-content instruction; the selection is cleared when sending and is not persisted in durable conversation history. After the grounded generation completes or fails, the runtime drops its cached native conversation session so the next turn replays the durable transcript without the raw excerpts. This is session disposal, not a guarantee of secure RAM zeroization. This is prompt grounding by explicit user selection, not automatic RAG. Embeddings, semantic retrieval, PDF parsing, and OCR remain future work.

## Chat interaction gating

The chat composer uses the same pure Kotlin availability policy as the ViewModel send guard. Text entry and Send are disabled while Gemma is generating, a conversation is being deleted, or a knowledge import, search, per-document deletion, or bulk clear is active. The ViewModel rechecks the policy before accepting a send; UI state is not treated as an authorization boundary. Focused JVM tests cover every busy state and the model-not-ready case.

## Verification

The prompt-grounding source head `d37a6aa7a718a585c6bb5395af14553157c5fd99` passed GitHub Actions run [#159](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/44608802099) on attempt 4. The bulk-clear source head `56ab34f14f32c3a417b4f78b6b2c08f83a70c412` passed exact-head run [#160](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/44813044902), including debug APK assembly, Android lint, JVM tests, KVM preflight, emulator boot, and API 35 instrumentation. A subsequent shared chat-operation policy follow-up is awaiting its own exact-head workflow; the parent run does not validate that change. No local Gradle build was run. No real Gemma inference, offline-after-download verification, or physical Pixel 6 result is claimed.


## Official Android API references

- [ActivityResultContracts.OpenDocument](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.OpenDocument) — user-selected document URI contract and MIME filters.
- [OpenableColumns](https://developer.android.com/reference/android/provider/OpenableColumns) — standard DISPLAY_NAME metadata for openable URIs.
- [ContentResolver.openInputStream](https://developer.android.com/reference/android/content/ContentResolver#openInputStream(android.net.Uri)) — supports content, file, and android.resource schemes; the repository rejects HTTP and other unsupported schemes.


## Unicode search normalization

Search is a manually invoked local keyword scan over the bounded SQLite chunk rows. It normalizes each Unicode grapheme cluster with NFKC and locale-independent lowercase, then maps the match back to offsets in the original source so displayed excerpts preserve stored text. The scan is bounded by the 8 MiB source quota and returns at most 20 snippets. This is compatibility-normalized substring matching, not linguistic stemming, transliteration, cross-language tokenization, or semantic search.
