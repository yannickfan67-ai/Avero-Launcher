# Avero Launcher

Independent native Android file downloader for Minecraft: Java Edition, written in Kotlin and Jetpack Compose.

> **NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

## Current release: 0.2.0-download-only

This version **only downloads, checks and manages game files**. It does not log into Microsoft, store accounts or tokens, launch Minecraft, run a JVM, or connect to game servers. Downloading files does not grant a license to play Minecraft. Gameplay requires legitimate Java Edition ownership and a launcher that authenticates and verifies that ownership.

### Features

- Native Android Explore, Downloads, Local and About pages with Traditional Chinese labels.
- Full official version catalog with search and release/snapshot/historical filters.
- Offline catalog cache for browsing previously fetched metadata.
- Persistent download queue with named tasks, manual pause/resume and per-task retry. Interrupted tasks recover paused on restart.
- SHA-1 and size verification of version metadata, the game client, compatible libraries, logging configuration, asset indexes and asset objects.
- HTTP Range resume of partial files with integrity digests, bounded retry/backoff, content-range checks and atomic file publication.
- Reuse verified files, and selectively repair missing or damaged content.
- Fixed-size asset workers and deduplication by content hash, avoiding concurrent writes for duplicate objects.
- Official HTTPS delivery host allowlist, validation of every redirect, metadata/file size limits and managed-path validation.
- JSON download-report export through Android's document picker. Reports contain version IDs, state and timestamps; no account data, credentials, local paths or arbitrary error messages.
- Application-private storage and disabled Android backups.

The app downloads Java Edition file content; it does not claim those desktop files are a working Android game runtime. The Android-compatible renderer and earlier JVM work are not part of this release.

## Build

Source: [`android/`](android/). Package: `io.yannickfan.avero`. Android 8.0+ (minSdk 26), compile/target API 37, JDK 17, Gradle 9.6.0, AGP 9.4.0.

```sh
cd android
gradle testDebugUnitTest assembleDebug lintDebug
```

CI runs tests, lint, distribution checks and APK creation. Debug APKs are for testing and are not represented as store-signed production builds. Native C++/NDK, Java runtime archives, client IDs and client secrets are not required to build the current application.

## Distribution boundary and history

Earlier authentication, game Activity, JVM bridge, native renderer code and their tests were moved to [`legacy/`](legacy/), outside Gradle source roots. No authentication client ID is generated into BuildConfig. The manifest declares only MainActivity. There is no review-specific toggle or remote mechanism to restore gameplay.

The old code remains historical, uncompiled development material. A future gameplay release must complete AppID review as applicable and enforce Microsoft authentication plus Minecraft entitlement/profile verification before play or server access. The Android application currently has no configured AppID; approval of the separate PC application's ID must not be described as Android approval.

The PC companion is [UN_Nexo](https://github.com/yannickfan67-ai/UN_Nexo).

## Maintainer, privacy and license

Maintained independently by Yannick Fan / `yannickfan67-ai`. Contact: [GitHub Issues](https://github.com/yannickfan67-ai/Avero-Launcher/issues).

See [PRIVACY.md](PRIVACY.md), [CHANGELOG.md](CHANGELOG.md), [LICENSE](LICENSE), and [THIRD_PARTY.md](THIRD_PARTY.md). The MIT license covers original Avero code, not Minecraft files or third-party dependencies. Game files are requested from official delivery endpoints; they are not bundled in the APK.
