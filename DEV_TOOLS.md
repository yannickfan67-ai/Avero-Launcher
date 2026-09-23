# Android development tools

Avero is intended to use a normal Android toolchain rather than hand-built APK packaging.

## Primary desktop toolchain

- Android Studio
- JDK 17
- Android SDK / platform tools
- Gradle 9.6
- Android Gradle Plugin 9.4.0
- Kotlin + Jetpack Compose
- adb / logcat for device debugging

## Command-line build tools

The low-level tools are useful for diagnostics, custom build experiments and CI:

- aapt2
- d8 / R8
- zipalign
- apksigner
- sdkmanager / command-line tools

## On-device development

### Termux

Useful for Git, shell tools, scripting and custom compiler/toolchain work on Android.

### CodeAssist

A current open-source on-device Android/Java IDE. It builds directly with the Android toolchain and avoids a full Gradle daemon. Its license is GPL-3.0-or-later, so do not copy GPL implementation code into Avero's MIT codebase unless licensing is deliberately changed or the code is kept legally separate.

### AndroidIDE

Historically one of the most complete Gradle-based Android IDEs on Android (JDK 17, Git, terminal, Java/Kotlin/XML language services). The original AndroidIDEOfficial project was archived in 2024 and is no longer maintained. Treat it as a design/reference project, not the primary maintained dependency.

## Avero rule

Avero should remain an independent implementation. External IDE projects can be studied for product ideas, interoperability and workflow design, but code reuse must respect their licenses.
