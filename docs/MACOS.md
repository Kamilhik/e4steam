# macOS status

macOS is unsupported by the current code and artifacts. No `.dylib` is bundled,
the release audit intentionally rejects one, and no Intel or Apple Silicon
Steam host/join smoke test has been performed.

Support requires official redistributable provenance, separate x86_64 and
arm64 selection, Mach-O slice/dependency/hash verification, hardened cache
tests on APFS, Gatekeeper/quarantine-safe diagnostics, Gradle/CI resource
audits and real Steam host/join/reconnect tests on both JVM architectures.
Rosetta may only be documented as a fallback, never as native arm64 support.

e4steam will not disable Gatekeeper, remove quarantine automatically, request
`sudo` or load a library from PATH/working directory.
