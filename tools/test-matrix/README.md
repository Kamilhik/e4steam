# e4steam 0.3.0 test matrix

This directory contains the local two-PC smoke-test kit for all 20 candidate
runtime JARs. It does not publish a website or send test results anywhere.

- `index.html` is the offline checklist. Progress and notes stay in the
  browser's `localStorage`; JSON export/import is available for backups.
- `profiles.json` maps every release JAR to one representative Minecraft and
  loader version. A representative baseline does not prove every patch in a
  retro `.x` branch.
- `prepare-prism-profiles.ps1` creates clean, managed Prism instances. Existing
  non-managed instances are never replaced. `-Replace` first moves an older
  managed test set into Prism's `e4steam-test-profile-backups` directory.
- `verify-prism-profiles.ps1` checks JAR counts, SHA hashes, Fabric API and Prism
  component metadata.
- `install-prism-profile-archive.ps1` safely installs a previously prepared
  profile archive on another Windows PC after checking its SHA-256.

From the repository root on Windows:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/test-matrix/prepare-prism-profiles.ps1 -Replace
powershell -NoProfile -ExecutionPolicy Bypass -File tools/test-matrix/verify-prism-profiles.ps1 -ReleaseRoot release/0.3.0
Start-Process tools/test-matrix/index.html
```

Fabric and Quilt profiles include the exact Fabric API file pinned in
`profiles.json`. Forge and NeoForge profiles contain only their matching
e4steam JAR.
