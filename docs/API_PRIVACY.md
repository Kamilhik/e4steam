# Addon API privacy

e4steam exposes the minimum data needed for an addon feature. It does not give
addons passwords, auth tickets, GSLT, invite tokens, raw native callbacks or a
credential-bearing join address.

Capabilities limit what e4steam itself returns. They are not a Java sandbox: an
installed addon is still ordinary mod code in the same JVM and may use normal
Java APIs. Users must trust the addons they install.

## Data categories

| Data | Default availability | Required capability or rule |
| --- | --- | --- |
| Opaque `PeerId` | Available inside an authenticated session | Session/channel context |
| Stable Minecraft UUID and safe name | Restricted | `identity.minecraft.read` |
| SteamID64, persona, avatar, presence, friendship | Restricted personal data | `identity.steam.profile.read` or compatible profile-read capability |
| Runtime version/platform/health | Safe bounded snapshots | Appropriate observe service |
| Steam password, auth ticket, GSLT, invite secret | Never exposed | No capability can grant it |
| Raw packets/native handles | Never exposed | No public hook exists |

An opaque `PeerId` is generation-scoped. It is the preferred key for temporary
session state because it does not reveal a Steam account number.

## Identity access

`IdentityService.local()` returns the safe local Minecraft projection without a
Steam profile by default. `remote(peerId)` resolves an authenticated remote peer
to a stable Minecraft identity when the addon has permission.

`steamProfile(peerId)` is more sensitive. It can contain SteamID64, persona
name, avatar reference, presence and friend relationship. Request this
capability only when the visible feature genuinely needs it, such as a friends
screen. Do not request it for a protocol, counter or generic status icon.

```java
if (!context.api().capabilities().has(
        Capabilities.IDENTITY_STEAM_PROFILE_READ)) {
    // Keep the feature on opaque PeerId/Minecraft identity only.
    return;
}

context.api().identities().steamProfile(peerId).thenAccept(result -> {
    if (!result.isSuccess()) return;
    IdentityService.SteamProfile profile = result.value().get();
    renderTransientProfile(profile);
});
```

Steam profile DTOs are immutable, and their `toString()` hides personal data.
That does not make the getter values anonymous. Keep them only for as long as
the feature needs them and do not copy them into logs by default.

## Data that must never enter public API output

Public DTOs, events, errors, logs and diagnostics must not contain:

- passwords, Steam auth tickets, GSLT, bearer tokens, cookies or API keys;
- complete `s-...steam` or `d-...steam` addresses while their bearer secret is
  active;
- private launcher arguments or stdin captures;
- native handles, raw callbacks, packet dumps or mutable native memory;
- arbitrary files selected from the user's computer;
- unredacted home paths or raw provider exception text.

Do not put a secret in an ID, schema name, storage key, log field name or error
operation string. Validators reject common sensitive names, but validation is
not a reason to handle credentials in the first place.

## Diagnostics

`DiagnosticsService` builds local previews. It never uploads a report.

An addon with `diagnostics.contribute` can register one bounded structured
section:

```java
DiagnosticsService.DiagnosticsContributor contributor =
        new DiagnosticsService.DiagnosticsContributor() {
            @Override
            public String id() {
                return "myaddon:health";
            }

            @Override
            public CompletionStage<ApiResult<DiagnosticsService.DiagnosticsSection>> contribute() {
                Map<String, String> fields = new LinkedHashMap<>();
                fields.put("state", "ready");
                fields.put("queued_items", "3");
                return CompletableFuture.completedFuture(ApiResult.success(
                        new DiagnosticsService.DiagnosticsSection("myaddon:health", fields)
                ));
            }
        };
```

Section IDs, field counts, keys and values are bounded. Credential-like values
are rejected, and the current home path is replaced with `<user-home>`. A
contributor exception or timeout is isolated.

`doctorPreview(new PrivacyOptions(false, false))` excludes Steam and lobby IDs.
Even when the user explicitly asks to include those identifiers, secrets remain
impossible to include through this API. Always let the user inspect the preview
before manual export.

## Logging

Use `SafeLogger` rather than dumping arbitrary strings or exceptions:

```java
Map<String, SafeLogger.SafeValue> fields = new LinkedHashMap<>();
fields.put("state", SafeLogger.SafeValue.text("ready"));
fields.put("peers", SafeLogger.SafeValue.integer(2));

context.api().logger().log(
        SafeLogger.Level.INFO,
        "myaddon.runtime.ready",
        SafeLogger.fields(fields)
);
```

Message codes and field names should be stable, non-secret identifiers. Log a
sanitized failure category, not a raw throwable whose message may contain paths
or provider data. Avoid logging persona names, Steam IDs and full message
payloads.

## Storage

`StorageService` is pathless and private to the addon. Logical keys cannot use
path traversal, and values have format, schema version and quota limits.

Private storage is suitable for addon settings or cached non-sensitive state.
It is not a password vault. Do not store Steam tickets, launcher credentials,
API tokens or a live join address. Prefer an opaque `PeerId` for short-lived
state and delete stale entries when the session or world closes.

## Network payloads

Addon channel contents may travel through Valve relays but are still visible to
the two installed addons and their JVMs. Send only data required by the feature.
Validate size and structure, and never serialize arbitrary files, access tokens
or raw account objects.

If an addon needs its own external web service, that traffic is outside the
e4steam Addon API privacy boundary. The addon must document the destination,
data, retention and consent itself.

## Practical checklist

Before release, verify that the addon:

- requests only capabilities it actually uses;
- works with opaque peer IDs wherever possible;
- does not log profiles, payloads or full addresses;
- closes subscriptions and deletes unnecessary cached personal data;
- shows the user what a manual diagnostic export contains;
- contains no telemetry or upload path that is missing from its documentation;
- passes secret-canary tests from `api-testkit`.

The testkit's `PrivacyAssertions` can detect common secret leakage in API-facing
objects. It supplements code review; it cannot prove that arbitrary installed
Java code behaves honestly.
