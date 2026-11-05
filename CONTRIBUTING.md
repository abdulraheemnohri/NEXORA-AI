# Contributing

1. Work in a feature branch.
2. Keep model/runtime changes aligned with official Google LiteRT-LM APIs.
3. Do not add mock inference or claim unsupported features.
4. Add unit tests for business logic and integration tests for runtime behavior.
5. Document permissions, data flows and failure modes.
6. Never commit model weights, signing keys, secrets or local SDK paths.
7. Mark physical-device checks separately from CI checks.
