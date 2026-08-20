# e4steam retro builds

Every directory is an isolated, exact loader/Minecraft artifact. There is no
generic retro JAR. The current matrix contains Forge 1.7.x-1.16.x and regular
Fabric 1.14.x-1.16.x. Minecraft 1.7.10-1.13.2 would require separate Legacy
Fabric or Ornithe ports, with Rift also possible on 1.13.2; those targets are
not built yet. There is no retro Quilt target.

The loader setup and Minecraft lifecycle seams were independently rewritten
from the Apache-2.0 reference repository `xhyrom/e4mc-retro`; no tunnel,
Cloudflare, Quiclime, broker, endpoint, token or e4mc runtime branding is used.
Exact reference revisions are recorded in `docs/RETRO_PORTING.md`.

Current status is **build-only** until each artifact completes a manual
host/join/reconnect smoke test with Steam App ID 480.
