# e4steam retro builds

Player-friendly guides: [English](../docs/RETRO_PORTING.md) ·
[Русский](../docs/RETRO_PORTING_RU.md).

Each retro directory builds one loader and Minecraft branch. There is no
generic all-retro JAR.

The 0.3.1 release contains:

- Forge `1.7.x` through `1.16.x`;
- regular Fabric `1.14.x` through `1.16.x`.

Minecraft `1.7.10`-`1.13.2` would need separate Legacy Fabric or Ornithe
ports, with Rift also possible on `1.13.2`. Those targets are not built. There
is no retro Quilt target.

Forge `1.7.x` requires a separate UniMixins `0.1.20` or newer JAR. e4steam
does not embed UniMixins component mods, so existing packs can keep one shared
copy without duplicate mod IDs.

Loader setup and Minecraft lifecycle seams were adapted from the Apache-2.0
reference repository `xhyrom/e4mc-retro`; the Steam transport and runtime were
rewritten for e4steam. No Cloudflare tunnel, broker, endpoint, token or e4mc
runtime branding remains. Exact source revisions are listed in
`docs/RETRO_PORTING.md`.

Retro JARs are supported release artifacts. Build and JAR audits run for every
branch, while exact manual launch and multiplayer results are tracked
separately in `COMPATIBILITY.md`.

Every retro JAR also has a physical dedicated-server entrypoint. With
`config/e4steam-dedicated.toml` enabled and Minecraft bound to loopback, it
starts an anonymous Steam GameServer backend, authenticates guests before
Minecraft login and prints a `d-...steam` address. Forge 1.12.2 has a recorded
Windows x64 authenticated join; other branch results remain in the compatibility
matrix.

The optional Unix overlay relaunch is included in the retro Java 8 artifacts
but remains disabled by default. Steam networking, copied addresses and the
standalone Steam friends window work without overlay injection. See
`docs/UNIX_OVERLAY.md` before enabling it.
