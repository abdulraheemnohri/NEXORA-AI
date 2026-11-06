# NEXORA AI Architecture

NEXORA is a native Android application. Its application backend runs in-process in Kotlin. Local LLM inference is provided only by Google's LiteRT-LM runtime.

## Layers

- Presentation: Jetpack Compose chat and local state.
- Application: ViewModel orchestration, model lifecycle, and task lifecycle.
- AI core: LiteRT-LM runtime contract, prompt/context assembly, generation limits, and streaming events.
- Agent: risk policy and approval boundary; broader planner/tool execution remains roadmap work.
- Memory and knowledge: planned local retrieval and ingestion components.
- Data: app-private SQLite conversation/message storage and local settings.
- Platform: Android permissions, app-private storage, and lifecycle management.

## Runtime integration rule

`LocalModelRuntime` is the app/runtime boundary. The production adapter is pinned to LiteRT-LM Android v0.18.1. Do not substitute Ollama, llama.cpp, remote inference, fabricated responses, or guessed APIs.

The adapter uses CPU inference and passes the configured `maxOutputToken` limit to the LiteRT-LM Flow overload of `sendMessageAsync`. Streamed `Message` content is extracted with `Message.toString()`; the pinned upstream `Message` class exposes `role`, `contents`, and `toString()`, not a public `text` property. Actual compilation and model inference still require CI/device verification.

## Build toolchain

The app targets Java 17 bytecode. Kotlin 2.3.21 uses the typed `kotlin.compilerOptions.jvmTarget` DSL; the deprecated Android `kotlinOptions.jvmTarget` property is rejected by this Kotlin plugin. CI uses JDK 21 to accommodate the LiteRT-LM dependency's JVM test runtime while retaining Java 17 app bytecode compatibility.

## Safety

Treat model-generated tool requests as untrusted. Validate schemas, authorize each capability, constrain paths, apply timeouts, and record audit events. Destructive or externally visible actions require explicit user confirmation. Do not enable automatic tool execution by default.

## Device target

Initial target is Pixel 6 / 6 GB RAM. Model loading, context size, CPU behavior, peak memory, and sustained thermal performance must be measured on physical hardware. No Pixel 6 performance claim is made without device evidence.
