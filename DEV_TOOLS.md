# Development tools for Avero

This file describes software used to **develop Avero Launcher**. These are not launcher features.

## Primary desktop toolchain

- Android Studio
- JDK 17
- Android SDK / platform tools
- Gradle 9.6
- Android Gradle Plugin 9.4.0
- Kotlin + Jetpack Compose
- adb / logcat for device debugging

## Command-line Android build tools

Useful for diagnostics, CI and lower-level APK work:

- aapt2
- d8 / R8
- zipalign
- apksigner
- sdkmanager / Android command-line tools

## On-device development tools

### Termux

Useful for Git, shell tools, scripting, adb-related work and experimenting with Java/native toolchains directly on Android.

### CodeAssist

An open-source Android/Java IDE that can be studied as a reference for running build tooling on-device. It is GPL-3.0-or-later, so its implementation code must not simply be copied into this MIT repository.

### AndroidIDE

A formerly full-featured Gradle-based Android IDE on Android. The original project was archived in 2024. It is useful as a historical/reference implementation rather than a core Avero dependency.

## Avero development rule

Avero remains an independent Minecraft launcher implementation. External launcher/IDE projects may be studied for interoperability and workflow ideas, but code reuse must follow their licenses.
