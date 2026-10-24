# Privacy — Avero 0.2.0-download-only

Maintainer: Yannick Fan / yannickfan67-ai. Contact: https://github.com/yannickfan67-ai/Avero-Launcher/issues .

This application only downloads and manages files. It does not collect Microsoft credentials, authentication tokens or player account data. It does not use the historical authentication implementation in legacy/.

Network requests are made to the official Minecraft/Mojang delivery endpoints for the version catalog, metadata, client, libraries and assets. Those services receive the requesting IP address, file URLs and Avero User-Agent. There is no developer-operated analytics, telemetry, advertising or credential-collection service.

The catalog cache, download queue, completion records and game files are stored in application-private storage. Android backup is disabled. Existing game data is reused when it passes verification. Uninstalling the application can remove these local files under Android's normal application-storage behavior.

Reports are exported only when the user chooses a document destination. The report contains product version, game version IDs, task states and timestamps. It excludes tokens, account information, local file paths, personal task names and raw error text. Sharing an exported report is the user's choice.

Minecraft/Mojang services and any other app used to view an exported report have their own policies. Minecraft content and marks belong to their respective owners.
