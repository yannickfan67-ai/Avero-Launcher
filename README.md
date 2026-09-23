# Avero Launcher

Avero is an independent **Minecraft: Java Edition launcher for Android**.

> **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

Avero is maintained independently by **yannickfan67-ai**. Project contact and bug reports are handled through this repository's GitHub Issues.

It is a fresh Android project rather than a port of the old UN_Nexo desktop UI. The goal is to launch and manage Java Edition cleanly on Android with a native Kotlin + Jetpack Compose interface.

## Product direction

- Minecraft: Java Edition launch flow on Android
- Microsoft account authentication
- Minecraft profile / entitlement handling
- Java runtime management
- Version installation and instance management
- Fabric / Forge / NeoForge / Quilt support
- Modrinth integration
- JVM arguments and memory controls
- Renderer / compatibility settings
- Download progress, logs and crash diagnostics
- Native Android UI; no WebView shell for the launcher itself

## Current state

The repository now contains the native Android launcher shell, official Mojang metadata parsing, verified game-file and asset downloads, library/argument rule evaluation, native-classifier metadata handling, launcher placeholder expansion, and unit-tested CI APK builds. Java runtime installation, Microsoft authentication, an Android-native library provider, and the final Java process bridge are still under active development.

The Android source lives in [`android/`](android/).

- package: `io.yannickfan.avero`
- minSdk: 26
- compile/target SDK: 37
- JDK: 17
- Gradle: 9.6
- AGP: 9.4.0

## Planned architecture

See [`LAUNCHER_ARCHITECTURE.md`](LAUNCHER_ARCHITECTURE.md).

## Development tools

See [`DEV_TOOLS.md`](DEV_TOOLS.md). These are tools used to **develop Avero**, not features inside the launcher.

## Website prototype

The static product site lives in [`web/`](web/).

## Trademark and game files

Minecraft, Mojang and Microsoft names and trademarks belong to their respective owners. Avero does not bundle or redistribute the Minecraft game client; required game files are obtained from the official Minecraft/Mojang delivery endpoints at install time.

## License

Avero's own source code is MIT licensed. See [`LICENSE`](LICENSE). This license does not grant rights to Minecraft game files, branding or third-party dependencies.
