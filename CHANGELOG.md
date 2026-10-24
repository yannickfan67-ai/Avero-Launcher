# Changelog

## 0.2.0-download-only — 2026-10-24

- Replace the account/game-start workflow with Explore, Downloads, Local and About pages.
- Move authentication/JVM/game/native source outside Android build roots; remove game and probe Activities, client-ID configuration and native build dependencies.
- Add full version search and channels, offline catalog cache, persistent named queue, interrupted-session recovery and document-picker report export.
- Retain resumable verified transfers, improve range/size validation and retry failed integrity checks.
- Validate HTTPS origins and redirects, reject unsafe metadata paths and symlinks, and atomically replace completed downloads.
- Bound asset worker count and deduplicate object downloads by hash.
- Add privacy documentation, release notes and regression coverage for queue recovery and download boundaries.
