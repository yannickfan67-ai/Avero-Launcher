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
9. Stream stdout / stderr to the launcher log UI.
10. Save crash and exit diagnostics.

## Android-specific constraints

Desktop launchers cannot simply be copied to Android. Avero needs explicit handling for:

- Android filesystem / scoped storage
- ARM64 and x86_64 runtimes
- Native libraries
- Graphics translation / renderer selection
- Process lifecycle and background restrictions
- Memory pressure
- Touch-first configuration UI

## Current milestone

The native Compose launcher shell and CI build are established.

Next implementation milestone:

1. Account model and authentication interfaces
2. Mojang version-manifest client
3. Instance model
4. Download manager
5. Java runtime manager
6. First vanilla launch experiment
