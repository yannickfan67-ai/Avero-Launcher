# Avero Launcher architecture

Avero is a Minecraft: Java Edition launcher for Android.

## Core layers

### 1. Account
Responsibilities:

- Microsoft OAuth sign-in
- Xbox Live authentication chain
- Minecraft services token
- Minecraft profile lookup
- Entitlement / ownership checks
- Local account session storage and refresh

Tokens must remain in app-private/session state and must never be copied into Compose text, persistent launch-request diagnostics, or GitHub Actions artifacts.

### 2. Version metadata
Responsibilities:

- Mojang version manifest
- Per-version metadata
- Client jar
- Libraries
- Assets and indexes
- Logging configuration
- Rule evaluation by OS / architecture

### 3. Java runtime
Responsibilities:

- Select a compatible Java major version for each game version
- Download or import managed runtimes
- Verify architecture and executable state
- Build JVM arguments
- Memory configuration

### 4. Loader support
Initial targets:

- Vanilla
- Fabric
- Forge
- NeoForge
- Quilt

Loader installers should produce normalized instance metadata so the launch pipeline does not need loader-specific UI logic.

### 5. Resource integration
Planned:

- Modrinth search and install
- Mods
- Modpacks
- Resource packs
- Shader packs

CurseForge is not required for the initial open integration path.

### 6. Launch pipeline
The launch service should:

1. Resolve account and selected instance.
2. Validate required game files.
3. Validate Java runtime.
4. Resolve libraries and native files.
5. Build classpath.
6. Build JVM and game arguments.
7. Apply renderer / compatibility configuration.
8. Start the Java process.
9. Stream safe stdout / stderr state to the launcher log UI.
10. Save crash and exit diagnostics without credentials.

## Android-specific constraints

Desktop launchers cannot simply be copied to Android. Avero needs explicit handling for:

- Android filesystem / scoped storage
- ARM64 and x86_64 runtimes
- Native libraries, including a replaceable Android LWJGL provider
- Graphics translation / renderer selection
- Process lifecycle and background restrictions
- Memory pressure
- Touch-first configuration UI

## Current milestone

The native Compose shell, Microsoft authentication pipeline, official Mojang metadata/download pipeline, managed Android OpenJDK installer, Android LWJGL provider installation, native JLI/JVM bridge, isolated `:game` process wiring, Surface hand-off, library path normalization, asset integrity checks, Android-native filtering, and launch-readiness validation are implemented and covered by JVM/Android CI tests.

The remaining work for the first complete vanilla launch is end-to-end Android validation of the patched LWJGL/GLFW provider reaching the Minecraft main class on a supported device. Until that is demonstrated, the launcher keeps Play gated behind readiness checks and reports safe failure diagnostics instead of claiming launch success.

The next launch milestone is:

1. Consume a one-time authenticated launch request in `:game` and delete it after read / expiry.
2. Combine the installed game, runtime and Android LWJGL provider into a launch-ready instance.
3. Run Minecraft in the isolated `:game` Android process.
4. Attach a Surface and renderer bridge for GLFW/LWJGL.
5. Stream launch/crash diagnostics back to the launcher UI without tokens.
6. Validate the full path on a supported Android device before enabling unrestricted Play.
