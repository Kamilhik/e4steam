# Future world-settings addon boundary

Core does not include a general server-properties editor. A separate future
addon may read an allowlisted immutable settings snapshot and submit a bounded
proposal that the host previews and confirms.

No addon may disable Steam authentication, the ingress guard, mandatory access
checks, secret validation or capacity bounds. Filesystem paths and arbitrary
property keys are not public contracts. Unsupported settings are rejected with
a typed error rather than written reflectively.
