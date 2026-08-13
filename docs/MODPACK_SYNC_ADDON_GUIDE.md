# Modpack Sync addon boundary

Modpack Sync is **not** included in e4steam core. Core does not discover,
download, install, hot-load or execute a remote JAR.

API 1.0 supplies neutral bounded manifest, compatibility, artifact, staging,
progress and proposal contracts. A separate addon owns sources and UI. A safe
implementation must use explicit user confirmation, bounded manifests and
artifact counts/sizes, normalized path confinement, HTTPS sources, declared
hashes/signatures, staging outside the live mods directory, integrity checks,
preview, backup, restart and rollback.

The contract rejects traversal, absolute/device paths, duplicate normalized
entries, oversized metadata and unsafe executable transitions. Staging is not
permission to move files into `mods` or launch code. Steam credentials and
arbitrary filesystem access are never supplied by e4steam. The API remains a
contract boundary, not a sandbox for the installed addon.
