# Avero Launcher

Avero is a fresh, Android-first developer workspace and launcher. It is independent from the old `UN_Nexo` desktop codebase and intentionally starts with a native mobile architecture.

## Current direction

- Native Android app written in Kotlin + Jetpack Compose
- Touch-first project/workspace UI
- Projects and repository workflows
- Future coding assistant integration
- Terminal/developer-tool integration
- No WebView shell
- No Minecraft-specific AI integration

## Android project

The Android source lives in [`android/`](android/).

- package: `io.yannickfan.avero`
- minSdk: 26
- compile/target SDK: 37
- JDK: 17
- Gradle: 9.6
- AGP: 9.4.0

## Website prototype

The static landing-page prototype can evolve independently from the Android application.

## Development tools

See [`DEV_TOOLS.md`](DEV_TOOLS.md) for the desktop, command-line and on-device Android development environments being considered.

## License

MIT. See [`LICENSE`](LICENSE).
