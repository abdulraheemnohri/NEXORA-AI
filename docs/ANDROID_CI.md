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


## Android Emulator 37.3+ software-acceleration opt-in

The emulator runner's `disable-linux-hw-accel: auto` checks whether the runner process can read and write `/dev/kvm`. When unavailable, it selects `-accel off`. A recent report from another Android CI consumer identifies that Android Emulator 37.3.10.0 rejects `-accel off` unless `ANDROID_I_WANT_MY_TCG=yes` is present. NEXORA therefore runs `.github/scripts/set-emulator-tcg-opt-in.sh` immediately before the emulator action; it exports that variable only when the KVM character device is missing or inaccessible.

The helper is covered by `.github/scripts/test-emulator-tcg-opt-in.sh`, which tests both an accessible character-device decision and a missing-device decision. This is a shell policy test, not proof that the emulator successfully boots under TCG. The latest exact-head run #174 used Android Emulator 37.3.10.0 with KVM available, so it validates the hardware-accelerated path only. A no-KVM TCG emulator boot remains a separate gate.

References:
- [ReactiveCircus emulator runner implementation](https://github.com/ReactiveCircus/android-emulator-runner/blob/main/src/main.ts) — `auto` checks read/write access to `/dev/kvm`.
- [ExecuTorch CI change for Emulator 37.3.10.0](https://github.com/pytorch/executorch/commit/a73bcb5ada28292a72e29517aeb83bd0b2396296) — documents the TCG opt-in required when `-accel off` is selected.
