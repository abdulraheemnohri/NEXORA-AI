# NEXORA AI Architecture

NEXORA is a native Android application. Its application backend runs in-process in Kotlin. Local LLM inference is provided only by Google's LiteRT-LM runtime.

## Layers

- Presentation: Jetpack Compose chat and local state.
- Application: ViewModel orchestration, model lifecycle, and task lifecycle.
- LLM core: LiteRT-LM runtime contract, prompt/context assembly, generation limits, and streaming events.
- On-device task runtime: future non-LLM LiteRT Compiled Model adapters, isolated from the LLM runtime.
- Agent: risk policy and approval boundary; broader planner/tool execution remains roadmap work.
- Memory and knowledge: planned local retrieval and ingestion components.
- Data: app-private SQLite conversation/message storage and local settings.
- Platform: Android permissions, app-private storage, and lifecycle management.

## Runtime integration rules

### LLM path

`LocalModelRuntime` is the text-generation boundary. The production adapter is pinned to LiteRT-LM Android v0.18.1. Do not substitute Ollama, llama.cpp, remote inference, fabricated responses, or guessed APIs.

The adapter uses CPU inference and passes the configured `maxOutputToken` limit to the LiteRT-LM Flow overload of `sendMessageAsync`. Streamed `Message` content is extracted with `Message.toString()`; the pinned upstream `Message` class exposes `role`, `contents`, and `toString()`, not a public `text` property. Actual compilation and model inference still require CI/device verification.

### Non-LLM model path

Use Google's LiteRT Compiled Model API for new non-LLM model tasks such as speech recognition, vision, or embeddings. Keep these adapters separate from `LocalModelRuntime`; a `.tflite` task model is not a substitute for the Gemma `.litertlm` model. Do not introduce the legacy TensorFlow Lite `Interpreter` API.

Each task adapter must own its model and tensor buffers, run on a confined serial background dispatcher, reuse buffers where appropriate, and close native resources deterministically. Preprocessing and postprocessing are part of the model contract and belong beside the adapter, not in Compose.

Before integrating a model, verify its license, artifact revision, input/output signatures, preprocessing contract, source-model reference outputs, and target-device behavior. Record accelerator residency logs because a successful run alone does not prove GPU execution; partial CPU fallback may occur. Benchmark enqueue plus output readback, not enqueue alone.

See [Google AI Edge integration](GOOGLE_AI_EDGE_INTEGRATION.md) for the workload matrix, Pixel 6 constraints, and verification checklist.

## Build toolchain

The app targets Java 17 bytecode. Kotlin 2.3.21 uses the typed `kotlin.compilerOptions.jvmTarget` DSL; the deprecated Android `kotlinOptions.jvmTarget` property is rejected by this Kotlin plugin. CI uses JDK 21 to accommodate the LiteRT-LM dependency's JVM test runtime while retaining Java 17 app bytecode compatibility.

## Safety

Treat model-generated tool requests as untrusted. Validate schemas, authorize each capability, constrain paths, apply timeouts, and record audit events. Destructive or externally visible actions require explicit user confirmation. Do not enable automatic tool execution by default.

## Device target

Initial target is Pixel 6 / 6 GB RAM. CPU inference is the baseline. Model loading, context size, CPU behavior, peak memory, and sustained thermal performance must be measured on physical hardware. Do not infer Google Tensor NPU support from the SoC branding; use only an accelerator/model/runtime combination explicitly verified for the target device. No Pixel 6 performance claim is made without device evidence.
