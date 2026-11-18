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

Metadata and chunk rows live in the separate app-private SQLite database nexora_knowledge.db. Document and chunk insertion is transactional. Foreign-key cascade removes chunks when a document is deleted. The UI requires explicit confirmation before deletion.

Deletion removes NEXORA's local indexed copy only. It does not delete the original source file and does not claim secure flash erasure. App-private database encryption is not implemented.

## Retrieval boundary

Search is a manually invoked, parameterized SQLite keyword query. It is bounded by the vault's 8 MiB source quota and returns at most 20 snippets. SQLite's built-in lower() provides ASCII case folding; this is not semantic search or cross-language case folding.

Imported content is not automatically inserted into Gemma prompts and is not registered as a model tool. User-selected prompt grounding, embeddings, semantic retrieval, PDF parsing, and OCR remain future work.

## Verification

The branch adds seven JVM importer tests and four API 35 SQLite instrumentation tests. These are test cases added to source; they are not considered passing until the exact-head GitHub Actions run completes. No physical Pixel 6 or real Gemma inference result is claimed.


## Official Android API references

- [ActivityResultContracts.OpenDocument](https://developer.android.com/reference/androidx/activity/result/contract/ActivityResultContracts.OpenDocument) — user-selected document URI contract and MIME filters.
- [OpenableColumns](https://developer.android.com/reference/android/provider/OpenableColumns) — standard DISPLAY_NAME metadata for openable URIs.
- [ContentResolver.openInputStream](https://developer.android.com/reference/android/content/ContentResolver#openInputStream(android.net.Uri)) — supports content, file, and android.resource schemes; the repository rejects HTTP and other unsupported schemes.
