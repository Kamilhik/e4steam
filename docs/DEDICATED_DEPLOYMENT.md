# Dedicated deployment status

There is currently no dedicated e4steam JAR, container image, server config or
deployment command. Do not launch the client JAR headlessly or publish a GSLT
in command-line arguments.

Future deployment documentation must cover supported Windows/Linux/macOS
backend matrices separately, secret-file/environment-provider permissions,
private bind/firewall settings, readiness/health checks, graceful draining,
world backup and exact GameServer smoke evidence. Unsupported combinations
must remain fail-closed.
