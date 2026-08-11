# Diagnostics and privacy

Current `/e4steam doctor` output is local and should be reviewed before
sharing. The future addon diagnostics service is not implemented by the API
foundation.

Diagnostic exports must use an allowlist, finite time/size budget and a preview.
They exclude raw logs by default, SteamID/persona/avatar by default, passwords,
tickets, tokens, cookies, full join addresses, native handles, packet dumps and
arbitrary user files. Home/user paths are redacted. Personal identifiers may
appear only behind a separate explicit switch and label.

Addon contributions require a capability, are exception-isolated and pass
through core redaction regardless of addon behavior. Doctor never uploads a
report itself.
