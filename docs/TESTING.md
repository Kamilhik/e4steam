# Testing e4steam

e4steam supports many Minecraft versions and loaders. A successful Gradle
build proves only that code compiled and the artifact passed its audits. It
does not prove that two Steam accounts can join the same world.

This guide separates each level of evidence so test results stay honest and
useful.

## Test levels

| Level | What it proves | What it does not prove |
| --- | --- | --- |
| Unit test | One isolated rule behaves as expected | Minecraft can launch |
| Build | Source compiles for a target | The JAR loads at runtime |
| JAR audit | Metadata, bytecode, natives and entrypoints look correct | Steam can connect |
| Client launch | A chosen Minecraft/loader reaches the expected screen | Hosting or joining works |
| Host/guest test | Two real clients complete a named scenario | Every other OS/version works |
| Dedicated test | A physical server and guest complete a named scenario | Integrated-world hosting works |

`COMPATIBILITY.md` records these results separately. Never turn a green build
badge or main-menu screenshot into a multiplayer claim.

## Automated checks

Use JDK 21 to run the root build. The Gradle projects select the required
bytecode level for each Minecraft target.

Windows:

```powershell
.\gradlew.bat --no-daemon clean apiChecks test headlessEntrypointAudit releaseJars
.\gradlew.bat --no-daemon -p retro clean auditRetroArtifacts
git diff --check
```

Linux or macOS:

```bash
./gradlew --no-daemon clean apiChecks test headlessEntrypointAudit releaseJars
./gradlew --no-daemon -p retro clean auditRetroArtifacts
git diff --check
```

Important groups:

- `test` checks address parsing, protocol state, lifecycle, authentication,
  retries, queues and other isolated behavior;
- `apiChecks` checks the stable Addon API surface, testkit and example addon;
- `headlessEntrypointAudit` rejects client-only references from server entry
  paths;
- `auditRetroArtifacts` builds and inspects all retro branch JARs;
- `releaseJars` assembles only publishable runtime artifacts.

Read the first real failure. A final `InvocationTargetException` usually wraps
the useful `Caused by` message above it.

## Inspecting release JARs

For every candidate, verify:

- filename contains the loader, Minecraft range and release version;
- mod metadata declares the intended loader and environment;
- classfile version matches the target Java generation;
- expected Steam client and GameServer natives are present exactly once;
- no development, sources, private key or credential file is packaged;
- `LICENSE`, `NOTICE` and third-party notices are present;
- client classes are not reachable from a headless entrypoint;
- SHA-256 is recorded before copying the file to test machines.

There should be six modern and thirteen retro runtime JARs for a complete 0.3.1
candidate set. `RELEASING.md` contains the exact filenames.

## Clean client launch test

Use a new launcher instance when possible. Install only:

- the chosen Minecraft version;
- the matching loader;
- one matching e4steam candidate;
- Fabric API for Fabric or Quilt where required;
- UniMixins 0.1.20+ for Forge 1.7.x.

Check:

1. the client reaches the main menu;
2. the Mods screen identifies the correct e4steam version;
3. a new singleplayer world loads;
4. **Open to LAN** shows the expected e4steam controls or chat actions;
5. Spacewar starts when the Steam runtime becomes active;
6. the address appears only after the local listener is ready;
7. closing the world stops sharing without closing Steam itself.

Then repeat with the real modpack. If the clean instance works but the pack
fails, add the other mods back in groups to locate the conflict.

## Two-client integrated-world test

Use two different Steam accounts on two machines or fully independent user
sessions. Both clients need compatible Minecraft, loader, modpack and e4steam
builds.

Record:

- exact JAR SHA-256 on host and guest;
- Minecraft patch, loader build, Java and OS/architecture;
- access mode: friends or invitation;
- join method: address or Steam invitation;
- direct P2P or Valve relay, if known;
- timestamps for first attempt and successful readiness.

Run this sequence:

1. host opens a fresh world and starts e4steam sharing;
2. guest joins on the first attempt;
3. guest loads chunks, moves, changes inventory and disconnects;
4. guest reconnects and confirms the same player progress;
5. host closes sharing and confirms the old address no longer works;
6. host starts sharing again and sends the new address;
7. repeat with several guests if the change touches capacity or queues;
8. interrupt Steam once and confirm recovery or a clear bounded failure.

For voice-chat compatibility, test TCP Minecraft traffic and the configured UDP
path separately. A successful Minecraft join does not prove UDP forwarding.

## Dedicated-server test

Bind Minecraft to loopback, enable `e4steam-dedicated.toml` and start the normal
loader server. The server must print the `d-...steam` address automatically
only after Steam transport and Minecraft ingress are ready.

Verify:

1. direct TCP access from another machine is unavailable;
2. an allowed Steam guest joins through the descriptor on the first attempt;
3. a blocked or unlisted guest is rejected before Minecraft login;
4. restart preserves the intended stable player identity;
5. stale and replayed descriptors fail;
6. disconnect/reconnect and graceful drain work;
7. the player limit is enforced;
8. the process starts without loading Minecraft client classes.

See the [deployment guide](DEDICATED_DEPLOYMENT.md) and
[security model](DEDICATED_SECURITY.md).

## Recording a result

A useful row contains:

- date;
- e4steam version and candidate SHA-256;
- Minecraft, loader, Java, OS and architecture;
- host and guest roles;
- exact scenario performed;
- pass, fail or partial;
- a short sanitized note and issue link when needed.

Do not record a SteamID, live address, ticket, invite secret, GSLT, private IP,
launcher token or unreviewed full log. Failed and skipped tests are evidence too;
keep them visible until they are resolved or deliberately removed from support.

## Before publishing

Automated checks, exact candidate inspection and the main manual scenarios must
all use the same JAR hashes. Update `CHANGELOG.md` and `COMPATIBILITY.md` before
the release tag. The complete order is in [`RELEASING.md`](../RELEASING.md).
