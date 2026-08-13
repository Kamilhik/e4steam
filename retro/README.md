# e4steam retro builds

Every directory is an isolated, exact loader/Minecraft artifact. There is no
generic retro JAR and no Legacy Fabric, Ornithe, Rift or Quilt target.

The loader setup and Minecraft lifecycle seams were independently rewritten
from the Apache-2.0 reference repository `xhyrom/e4mc-retro`; no tunnel,
Cloudflare, Quiclime, broker, endpoint, token or e4mc runtime branding is used.
Exact reference revisions are recorded in `docs/RETRO_PORTING.md`.

Current status is **build-only** until each artifact completes a manual
host/join/reconnect smoke test with Steam App ID 480.
