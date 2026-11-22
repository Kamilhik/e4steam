# Release verification — 2026-11-22

Scope: Minecraft 1.17–26.3, six JARs per edition. No retro files in this release.

- 332 common tests, zero failures/errors, 10 skipped; API and headless audits passed.
- Twelve loader/Java-target/entrypoint/edition/private-file checks passed before final packaging.
- Real two-player Fabric 1.21.1 transfer and return were confirmed by the user. The recipient inventory regression is covered automatically.
- Personal-name singleplayer and dedicated joins received targeted in-game confirmation; this is not a full two-account matrix for every Minecraft patch and loader.
- Bulk profile v2 tests cover transient RTT spikes, sustained congestion cooldown, recovery despite old quality estimates, unlimited upper send rate and restoration of prior settings. New remote throughput is not yet measured.
- A controlled local native transfer completed both 2 MiB rounds; the harness client later hit a Windows access violation during SteamAPI.shutdown. The server exited normally. Do not describe this as a fully clean native harness run.
- Third-party mod ownership/data and all real-world voice combinations remain broader compatibility areas. Keep backups of important worlds.
