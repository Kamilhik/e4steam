# API compatibility

Four independent versions exist:

- e4steam mod version: development `0.3.0`;
- Java addon API version: `0.1.0`;
- core wire protocol: `4`;
- future addon-channel protocol: selected per channel.

`ApiVersion` follows Semantic Versioning and `ApiVersionRange` uses an inclusive
minimum/exclusive maximum. Incompatible addons must be rejected before their
entry point runs. Unknown optional capabilities are ignored; unknown required
capabilities cause a controlled rejection.

The first canonical public surface hash is stored in
`api/api-surface.sha256`. `apiBinaryCompatibilityCheck` rejects accidental
surface changes. Because `0.1.0` is the first baseline and has not been released
yet, there is no older published API JAR to compare. Future compatible features
should normally use a new `ServiceKey` and service interface instead of adding
an abstract method to `E4steamApi`.

Experimental contracts live under `link.e4steam.api.experimental` and make no
binary promise until promoted.
