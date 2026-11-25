# Android CI

NEXORA's Android CI gates debug APK assembly, lint, JVM unit tests, and API 35 instrumentation.

## Emulator acceleration policy

The emulator job runs on Ubuntu 24.04 and attempts to enable access to `/dev/kvm`. Hosted-runner access can vary even when the device node exists and its visible mode looks permissive.

- KVM setup is best-effort and diagnostic; it no longer stops the build/lint/JVM-test job solely because KVM access is unavailable.
- `reactivecircus/android-emulator-runner@v2` uses `disable-linux-hw-accel: auto`. Its current upstream implementation checks read/write access to `/dev/kvm` and disables Linux hardware acceleration when the check fails.
- When KVM is accessible, the emulator may use hardware acceleration. When it is not, the action attempts software acceleration. Software emulation can be substantially slower and may still fail or exceed boot/test time limits; this fallback is not a guarantee.
- The emulator boot timeout is 900 seconds for the slower fallback case.

## CI gates

1. `./gradlew :app:assembleDebug`
2. `./gradlew :app:lintDebug`
3. `./gradlew :app:testDebugUnitTest`
4. KVM diagnostics and emulator startup using the action's auto acceleration policy.
5. `./gradlew :app:connectedDebugAndroidTest` on API 35.

Instrumentation is still a required validation gate. A KVM warning does not count as a passing test; only a successful exact-head Actions run with the instrumentation job completed counts.

## Troubleshooting

- If the emulator fails to boot under software acceleration, inspect the emulator runner logs and timeout. Do not claim instrumentation passed.
- A missing or inaccessible `/dev/kvm` is a runner capability limitation, not proof of an app source failure.
- CI/emulator success does not replace live Gemma inference, offline-after-download, or physical Pixel 6 validation.
- The CI workflow does not download or install Gemma model weights and does not use any external inference server.
