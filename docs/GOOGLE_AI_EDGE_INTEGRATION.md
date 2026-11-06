# Google AI Edge Integration Plan

NEXORA uses the Google AI Edge stack as an on-device inference platform, not as a reason to introduce a cloud service or a second LLM server.

## Runtime selection

| Workload | Runtime | Model artifact | NEXORA policy |
|---|---|---|---|
| Gemma 4 E2B chat, summarization, text generation | LiteRT-LM Kotlin API | `.litertlm` | Existing primary LLM path; keep CPU as the verified baseline |
| Speech recognition / audio classification | LiteRT Compiled Model API | Verified `.tflite` / LiteRT model | Candidate optional local capability; requires model, preprocessing, permission, and device verification |
| Image classification / segmentation / vision encoders | LiteRT Compiled Model API | Verified `.tflite` / LiteRT model | Candidate optional local capability; no model or UI is claimed until a compatible artifact is selected and tested |
| Text embeddings / semantic retrieval | LiteRT Compiled Model API where a compatible embedding model exists | Verified model artifact | Candidate for local memory retrieval; evaluate separately from LLM generation |
| Model conversion and quantization | LiteRT Torch / AI Edge Quantizer toolchain | Build-time artifacts | Developer workflow only; do not run conversion in the Android app |
| Web inference | LiteRT.js | Browser-compatible model | Out of scope for the native Android app |
| Google Tensor NPU | Google Tensor SDK / LiteRT NPU path | Device-specific compiled model | Not enabled for Pixel 6; official Google Tensor skill currently describes Pixel 10 / 11 targets |

## Non-negotiable distinction

LiteRT-LM is the LLM engine for `.litertlm` bundles. LiteRT's Compiled Model API is for non-LLM LiteRT models and must be used for new inference code in that category. Do not replace the Gemma LiteRT-LM engine with a raw CompiledModel or a legacy TensorFlow Lite Interpreter.

## Architecture boundaries

- Keep `LocalModelRuntime` and the LiteRT-LM adapter as the only text-generation backend.
- Add a separate task-runtime interface for future non-LLM models. Each task adapter owns its CompiledModel, tensor buffers, preprocessing, postprocessing, and a serial inference dispatcher.
- Keep inference out of Compose and the Android main thread. ViewModels expose immutable progress/result/error state.
- Reuse buffers and close every native buffer and model resource deterministically.
- Do not silently claim GPU/NPU execution. Record requested accelerator, actual runtime logs/residency, model/runtime version, device, correctness checks, and CPU fallback.
- Do not download arbitrary models from untrusted URLs. Pin model repository revision, artifact name, expected size, SHA-256, license, and task-specific input/output contract.
- Never package multi-gigabyte model weights in the APK. Stage them in app-private storage or a supported on-demand delivery mechanism.

## Pixel 6 deployment policy

Pixel 6 is the initial validation device. Use the CPU path as the baseline. GPU or other accelerator paths are opt-in experiments until a compatible artifact and runtime build are verified on that exact device. Do not infer Tensor NPU support from the presence of a Google Tensor SoC.

For every accelerator claim, follow the upstream on-device verification workflow:
1. Save fixed reference inputs and source-model outputs.
2. Run the same inputs on device CPU and the requested accelerator.
3. Check runtime residency logs for partial or silent CPU fallback.
4. Compare numeric outputs and task-level results against the reference.
5. Record device model, Android version, runtime version, accelerator residency, latency including readback, and peak memory.

## Upstream references

- [Google AI Edge organization](https://github.com/google-ai-edge)
- [LiteRT runtime](https://github.com/google-ai-edge/LiteRT)
- [LiteRT Android documentation](https://ai.google.dev/edge/litert/android)
- [LiteRT Compiled Model API](https://ai.google.dev/edge/litert/overview)
- [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM)
- [LiteRT-LM Android guide](https://ai.google.dev/edge/litert-lm/android)
- [Official LiteRT Android runtime skill](https://github.com/google-ai-edge/litert-samples/tree/main/skills/android/litert-runtime)
- [Official LiteRT-LM Android skill](https://github.com/google-ai-edge/litert-samples/tree/main/skills/android/litert-lm)
- [Official Compiled Model app scaffolding skill](https://github.com/google-ai-edge/litert-samples/tree/main/skills/compiled-model-app-scaffolding)
- [Official on-device verification skill](https://github.com/google-ai-edge/litert-samples/tree/main/skills/on-device-verification)
- [LiteRT samples](https://github.com/google-ai-edge/litert-samples/tree/main/samples/litert)

## Implementation gates

No optional ASR, vision, embedding, or NPU dependency is added just because it exists upstream. Before adding each capability, select a real model artifact and license, verify supported input/output signatures, build a minimal inference harness, compare against reference outputs on Pixel 6, measure memory/latency, and only then add the feature screen and settings.
