# Building NEXORA AI

## Requirements

- JDK 21 for Gradle and the current LiteRT-LM test toolchain.
- Android Studio with Android SDK Platform 35 installed.
- Windows 10/11, Linux, or macOS.
- A network connection on the first wrapper invocation to download Gradle 8.13. Android SDK packages may also be downloaded by Android Studio/Gradle as needed.

The app compiles to Java 17 bytecode and has Android minSdk 26. The wrapper does not install the Android SDK or JDK.

## Windows (PowerShell or Command Prompt)

Open a terminal in the repository root:

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:lintDebug
.\gradlew.bat :app:testDebugUnitTest
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

If Android Studio is used, open the repository root as a project and set **Gradle JDK** to JDK 21 in the IDE's Gradle settings. Android Studio will use the checked-in wrapper.

## Linux and macOS

```sh
java -version
./gradlew --version
./gradlew :app:assembleDebug
./gradlew :app:lintDebug
./gradlew :app:testDebugUnitTest
```

## Wrapper integrity

- Gradle version: 8.13.
- Distribution: `https://services.gradle.org/distributions/gradle-8.13-bin.zip`.
- Official distribution SHA-256: `20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78`.
- Official Gradle 8.13 wrapper JAR SHA-256: `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f`.

The wrapper JAR is taken from the official Gradle `v8.13.0` source tag. The GitHub Actions `gradle/actions/setup-gradle@v4` step performs wrapper-JAR validation. The wrapper itself verifies the downloaded Gradle distribution against `distributionSha256Sum`. Do not disable either check or substitute a wrapper JAR from an untrusted mirror.

## Build scope

These commands build and test the Android project. They do not download the Gemma model, run local inference, or validate Pixel 6 runtime performance. Those are separate application/device verification gates. NEXORA does not require Docker, Firebase, Ollama, llama.cpp, or a cloud AI inference service.


## Local instrumentation tests

With an API 35 `google_apis` x86_64 Android emulator booted and visible to ADB, run:

```sh
./gradlew :app:connectedDebugAndroidTest
```

The instrumentation suite exercises SQLite persistence, ordering, and cascade deletion, plus deterministic downloader fixtures for byte-count validation, checksum rejection, partial-file cleanup, corrupt-file replacement, and HTTPS redirect enforcement. Downloader tests use tiny in-memory responses; they do not download or execute the real Gemma model.

## GitHub Actions instrumentation environment

The CI workflow runs on the stable `ubuntu-24.04` image and executes SQLite instrumentation on an API 35 `google_apis` x86_64 Android emulator after the debug build, lint, and JVM unit tests. Before launching the emulator, it installs a udev rule that grants the runner read/write access to `/dev/kvm`, reloads the rule, and verifies that `/dev/kvm` is a character device with read/write access. The emulator action is configured not to fall back to software CPU emulation and has a 600-second boot limit. If the runner cannot expose KVM, the job fails at the explicit preflight instead of spending the boot window on an unusably slow emulator. These instrumentation tests exercise Android SQLite persistence and deletion; they do not download or execute Gemma.
