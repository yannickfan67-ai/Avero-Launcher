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

The repository currently contains the native Android launcher shell, official Mojang metadata parsing, verified game-file downloading, asset installation and CI APK builds. Java runtime installation, Microsoft authentication, native rendering support and the final launch process are still under active development.

The Android source lives in [`android/`](android/).

- package: `io.yannickfan.avero`
- minSdk: 26
- compile/target SDK: 37
- JDK: 17
- Gradle: 9.6
- AGP: 9.4.0

## Microsoft account authentication

Avero uses Microsoft's **public-client device-code flow**. No Microsoft client secret is embedded in the APK.

Create or use an Avero Microsoft app registration configured for personal Microsoft accounts and public client/device-code authentication, then build with:

```bash
AVERO_MS_CLIENT_ID="<application-client-id>" gradle assembleDebug
```

GitHub Actions can provide the same non-secret client ID through the `AVERO_MS_CLIENT_ID` environment variable. Builds without it still compile, but the Accounts page will show that Microsoft login is not configured.

## Android Java runtime

Avero can install Android OpenJDK runtime packages for Java 8, 17, 21 and 25 (depending on device ABI and upstream availability). Runtime archives are downloaded separately, verified with published SHA-256 digests, and extracted into Avero's private runtime directory.

The runtime binaries are **not MIT-licensed Avero code**. See [`THIRD_PARTY.md`](THIRD_PARTY.md) for upstream and license information.

The runtime installer does not by itself make Minecraft render on Android: the remaining launch milestone requires the Android JVM/native launch bridge and the LWJGL/renderer compatibility layer.

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
