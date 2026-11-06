# Daily Development Progress

## 2026-11-05 — Build-readiness and persistence audit

### Repository checkpoint
- Audited the latest main commit: 08f2ff440cb6cb6340cd8b12978819cc2a2925a7.
- Confirmed source wiring for the Compose chat screen, Android ViewModel, SQLite repository, Gemma model downloader, and LiteRT-LM CPU adapter.
- Confirmed the Android CI workflow exists and targets Gradle 8.13 with JDK 17.
- Queried the latest commit's combined status; GitHub returned no status entries. This is not evidence of a successful build.

### Added
- Unit tests for local stored-message field preservation, accepted role values, unsupported-role rejection, and the 100,000-character storage boundary.

### Still unverified
- Android compilation and unit-test execution.
- Gradle wrapper generation and repository inclusion.
- LiteRT-LM API compatibility at compile time.
- Gemma download, checksum verification, and actual inference on Pixel 6.
- Runtime sampling configuration: GenerationRequest.maxOutputTokens is currently not applied by the LiteRT-LM adapter.
- Chat history restoration and conversation-context replay after process restart.

### Next engineering gate
Run the Android CI workflow and resolve compiler/test failures before expanding the autonomous agent surface. Do not label the app production-ready until the build, model inference, offline behavior, and Pixel 6 memory/thermal behavior are verified.

## 2026-11-06 — Conversation history and generation controls

### Repository checkpoint
- Reviewed `main`, recent commits, open issues, branches, and available commit checks before starting this increment.
- At the checkpoint, the repository had no open issues and no open pull requests; `main` was at `6681696`.
- One helper file was briefly written to the default branch without the requested feature-branch argument. It was immediately removed in a follow-up commit before the feature PR was opened; no other default-branch files were changed by that mistake.
- Opened draft PR [#1](https://github.com/abdulraheemnohri/NEXORA-AI/pull/1) from `feature/chat-history-replay`.

### Delivered in source
- Restore the most recently updated local conversation at app startup.
- Add a history picker for switching conversations and confirmation-gated local deletion.
- Update the first conversation title from the user's first prompt.
- Replay saved user/assistant turns through the pinned LiteRT-LM v0.18.1 `ConversationConfig.initialMessages` API.
- Bound replay to 16 messages / 12,000 characters and retain at most two native conversations with LRU eviction.
- Close a cached runtime conversation when the corresponding local transcript is deleted.
- Add device-local output-token choices (256, 512, 1,024, 2,048; default 512) and pass the selected value to LiteRT-LM.
- Add JVM tests for history ordering/truncation, history-role mapping, request limits, and supported generation settings.
- Add an Android lint step to the CI workflow and update README, roadmap, architecture, local-data, and runtime integration documentation.

### Files changed
- `app/build.gradle.kts`
- `.github/workflows/android-ci.yml`
- `README.md`, `ROADMAP.md`
- `app/src/main/java/com/nexora/ai/ai/ConversationHistoryPolicy.kt`
- `app/src/main/java/com/nexora/ai/ai/GenerationSettings.kt`
- `app/src/main/java/com/nexora/ai/ai/LocalModelRuntime.kt`
- `app/src/main/java/com/nexora/ai/ai/LiteRtLocalModelRuntime.kt`
- `app/src/main/java/com/nexora/ai/data/ChatRepository.kt`
- `app/src/main/java/com/nexora/ai/data/LocalChatDatabase.kt`
- `app/src/main/java/com/nexora/ai/ui/NexoraHome.kt`
- `app/src/main/java/com/nexora/ai/ui/NexoraViewModel.kt`
- `app/src/test/java/com/nexora/ai/ai/ConversationHistoryPolicyTest.kt`
- `app/src/test/java/com/nexora/ai/ai/GenerationRequestTest.kt`
- `app/src/test/java/com/nexora/ai/ai/GenerationSettingsTest.kt`
- `docs/ARCHITECTURE.md`, `docs/DAILY_PROGRESS.md`, `docs/LITERT_LM_INTEGRATION.md`, `docs/LOCAL_DATA_AND_AGENT_SAFETY.md`

### Verification actually performed
- Inspected the pinned upstream Google LiteRT-LM `v0.18.1` Kotlin source for `ConversationConfig.initialMessages`, `maxOutputToken`, `automaticToolCalling`, `Message.user/model`, and the `sendMessageAsync` Flow overload.
- Corrected the adapter to use `Message.toString()` for streamed chunks; the pinned `Message` class does not define a `text` property.
- Checked the local execution environment: JDK 21 is present, but Gradle is not installed and `ANDROID_HOME` / `ANDROID_SDK_ROOT` are unset. The repository also has no Gradle wrapper.
- No Gradle build, lint, or JVM test command could be run locally. Pixel 6, download, offline, and real Gemma inference tests were not run.

### CI history and corrective actions
- Run [#37](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42108664411) failed during Gradle configuration because Kotlin Gradle Plugin 2.3.21 rejects the deprecated `android.kotlinOptions.jvmTarget` DSL. Migrated to typed `kotlin.compilerOptions` with JVM target 17.
- Run [#41](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42108913542) reached Kotlin compilation and exposed a SQLite bind-array type inference error and an inaccessible Compose `weight` import. Both were corrected. Coroutine cancellation is also now propagated rather than swallowed.
- Run [#46](https://github.com/abdulraheemnohri/NEXORA-AI/actions/runs/42109522385) then completed **debug APK assembly successfully** and **Android lint successfully**, but the JVM test task failed: 15 tests completed, one failed. The failing test is `ConversationHistoryPolicyTest.mapsPersistedRolesToLiteRtLmMessageRoles`, with `UnsupportedClassVersionError` at the LiteRT-LM role-mapping assertion. Build and lint are verified for this PR merge ref; the unit-test suite is not green.
- The workflow's test runtime was configured as JDK 17. The current investigation indicates the failing test loads the pinned LiteRT-LM dependency class at JVM test runtime. CI is moved to JDK 21 (while Android bytecode target remains Java 17) to test whether the dependency's class-file version is the cause. This is a targeted hypothesis, not yet confirmed.
- The CI JDK change is committed as [`9ca88f1`](https://github.com/abdulraheemnohri/NEXORA-AI/commit/9ca88f17768b520d9d411cfe9b6b4ce945441b2e). A new workflow result is pending.
- No Gradle wrapper or Android instrumentation tests have been added in this increment. No physical Pixel 6 or real Gemma inference test was performed.

### Next priority
- Confirm whether JDK 21 resolves the LiteRT-LM test runtime class-version failure. If not, isolate the mapping policy from dependency runtime loading or add a proper Android instrumentation test.
- Add SQLite instrumentation tests after the unit-test baseline is green.
- Generate and verify the official Gradle wrapper; do not handcraft or commit an unverified wrapper JAR.
- Then verify Gemma inference, offline behavior, and Pixel 6 memory/thermal performance.
