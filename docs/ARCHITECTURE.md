# NEXORA AI Architecture

NEXORA is a native Android application. Its application backend runs in-process in Kotlin. Local LLM inference is provided only by Google's LiteRT-LM runtime.

## Layers
- Presentation: Jetpack Compose, navigation and screen state.
- Application: use cases, orchestration and task lifecycle.
- AI core: runtime contract, prompt/context assembly and generation events.
- Agent: plans, task steps, tool registry, approvals and audit.
- Memory: working, episodic, semantic and procedural records.
- Knowledge: document extraction, chunking, indexing and retrieval.
- Data: Room repositories and DataStore preferences.
- Platform: Android permissions, Keystore, WorkManager and storage.

## Runtime integration rule
LocalModelRuntime is the app/runtime boundary. Implement its production adapter against the exact LiteRT-LM Android artifact and Kotlin API documented by Google. Do not substitute Ollama, llama.cpp, remote inference, fabricated responses or guessed APIs.

## Safety
Treat model-generated tool requests as untrusted. Validate schemas, authorize each capability, constrain paths, apply timeouts and record audit events. Destructive or externally visible actions require explicit user confirmation.

## Device target
Initial target is Pixel 6 / 6 GB RAM. Model loading, context size, CPU/GPU behavior, peak memory and sustained thermal performance must be measured on physical hardware.
