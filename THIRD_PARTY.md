# Third-party components

Avero's own source code is licensed under the MIT License. Third-party components keep their own licenses.

## Android OpenJDK runtime packages

Avero can download Android OpenJDK runtime packages built by:

- Project: `AngelAuraMC/angelauramc-openjdk-build`
- Upstream: https://github.com/AngelAuraMC/angelauramc-openjdk-build
- Purpose: Java runtime environment used to run Minecraft: Java Edition on Android
- Runtime versions currently catalogued by Avero: Java 8, 17, 21 and 25 where an architecture build is available
- Upstream runtime license: GNU GPL v2 (OpenJDK; refer to the upstream runtime/source package for the complete applicable notices and source)

Avero does **not** relicense these runtime binaries as MIT. They are downloaded separately from upstream GitHub Releases and verified against the SHA-256 digests published with those release assets.

Avero's runtime catalog stores upstream URLs, expected byte sizes and SHA-256 hashes only.

## Android LWJGL native provider

Avero can download Android LWJGL bridge AARs from:

- Project: `AngelAuraMC/Amethyst-Android`
- Upstream source: https://github.com/AngelAuraMC/Amethyst-Android
- Pinned upstream commit: `330c6eae3164df64bdc4828e946a9e62cc5169e4`
- Catalogued provider versions: LWJGL 3.3.3 and 3.4.1
- Upstream project license: GNU LGPL v3

The provider is **not** part of Avero's MIT-licensed source. Avero downloads the pinned patched LWJGL component JARs and the matching native AAR separately when requested. Every artifact is validated against its exact upstream Git blob SHA-1 and byte size; only native libraries for the current Android ABI are extracted from the AAR.

The provider remains replaceable on disk rather than being relicensed or copied into Avero's source tree. Users should refer to the pinned upstream source and license for the complete corresponding source and license terms.

## Archive libraries

Avero uses these libraries to extract runtime `.tar.xz` packages:

- Apache Commons Compress — Apache License 2.0
- XZ for Java — 0BSD

See each dependency's published package metadata for its complete license text.

## Minecraft

Minecraft game files, assets and trademarks are not covered by Avero's MIT license. Minecraft game files are obtained from Minecraft/Mojang service endpoints as required by the selected version.

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**
