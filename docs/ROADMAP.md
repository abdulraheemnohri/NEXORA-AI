# NEXORA SELF-X Roadmap

## Delivered foundations
- Android-native Compose chat.
- Pinned Gemma/LiteRT-LM CPU integration path.
- Local conversations and permissioned read-only search.
- MAS-X agent catalog, local LAMP protocol, persistent task engine, scoped memory, declarative skills, tool policy metadata, model registry, resource governor, audit and evolution review UI.

## Next implementation increments
1. Run exact-head CI; fix build, lint, JVM and API 35 instrumentation failures.
2. Validate actual model download/load/inference/offline restart on physical Pixel 6.
3. Replace deterministic task keyword routing with bounded LLM planning and independently reviewable plans.
4. Implement task DAG execution with dependency-aware scheduling, pause/resume, approval waits and cancellation propagation.
5. Connect only concrete, tested tool handlers to the existing central executor.
6. Add encrypted export and evaluate database-at-rest encryption without claiming secure flash erasure.
7. Implement richer model import/compatibility workflows and benchmark suites.
8. Add debate/simulation workflows and model evaluation with evidence provenance.
9. Improve resource telemetry and long-running thermal/battery validation.

## Non-goals unless separately approved
- Remote/cloud inference as a mandatory dependency.
- Multiple heavyweight model instances on Pixel 6.
- Arbitrary executable skill packages.
- Autonomous security-policy or permission changes.
- Foundation-model retraining on the phone.
