# Avero Launcher

Avero is an independent **Minecraft: Java Edition launcher for Android**.

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

The repository currently contains the Android UI foundation and CI build. The actual Minecraft runtime / authentication / version-launch pipeline is the next major implementation stage.

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

## License

MIT. See [`LICENSE`](LICENSE).
