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

## Archive libraries

Avero uses these libraries to extract runtime `.tar.xz` packages:

- Apache Commons Compress — Apache License 2.0
- XZ for Java — 0BSD

See each dependency's published package metadata for its complete license text.

## Minecraft

Minecraft game files, assets and trademarks are not covered by Avero's MIT license. Minecraft game files are obtained from Minecraft/Mojang service endpoints as required by the selected version.

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**
