# Pixel 6 MAS-X Validation Checklist

Do not mark any item hardware-verified until it has been run on a physical Pixel 6 and the device, Android build, model revision, runtime revision, configuration and measurements are recorded.

## Required validation

- [ ] Install a signed/debug build on a physical Pixel 6.
- [ ] Confirm app starts with network disabled after the model has been installed.
- [ ] Verify the pinned Gemma artifact file size and SHA-256.
- [ ] Load Gemma through the LiteRT-LM CPU adapter.
- [ ] Run a short chat request.
- [ ] Run a MAS-X task from the Society screen.
- [ ] Verify chat and MAS-X cannot run native inference concurrently.
- [ ] Verify the queue rejects work above its configured bound.
- [ ] Force-stop during a task and verify startup recovery.
- [ ] Test low-memory and severe-thermal gating.
- [ ] Record model load time, time-to-first-token and output duration.
- [ ] Record available RAM before, during and after inference.
- [ ] Record Android thermal status before and after a sustained test.
- [ ] Verify task, message, memory and audit records survive process restart.
- [ ] Verify one agent cannot retrieve another agent's private memory.
- [ ] Verify skill activation and temporary-agent activation require user actions.

## Measurements to record

Device model, Android build fingerprint, app commit, model artifact SHA-256, LiteRT-LM version, available RAM, storage headroom, battery percentage, thermal status, inference prompt size, output length, elapsed time and observed failures.

## Not assumed

Pixel 6 GPU/NPU acceleration, unrestricted background work, multi-heavyweight-model concurrency, foundation-model training, or continuous autonomous execution are not assumed.
