# API compatibility

e4steam versions four independent surfaces:

- mod version: development `0.3.0`;
- Java addon API: `1.0.0`;
- core wire protocol: `4`;
- addon channel protocols: declared independently by each channel.

`ApiVersion` follows semantic versioning. `ApiVersionRange` has an inclusive
minimum and exclusive maximum. Incompatible addons are rejected before their
entry point runs. Unknown optional capabilities may be omitted; an unavailable
required capability disables the addon with a typed sanitized error.

`api/api-surface.sha256` stores the canonical reflection surface.
`apiBinaryCompatibilityCheck` rejects accidental public changes and the API
JAR audit rejects implementation, Minecraft, loader, JNA and Steamworks types.
Compatible growth should normally add a service/type with a new `ServiceKey`,
not add an abstract member to an existing interface. Breaking changes require
a new API major version and migration notes.

The `1.0.0` surface is the first complete baseline on an unreleased branch;
there is no previously published stable API artifact. Types under
`link.e4steam.api.experimental` deliberately make no compatibility promise.
