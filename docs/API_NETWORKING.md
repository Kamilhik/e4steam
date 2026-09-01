# Addon networking

Addon API 1.0 provides namespaced, negotiated channels over an already
authenticated e4steam session. Addons do not open Steam sessions themselves and
never receive raw Steam/JNA handles, tickets, native buffers or core protocol
frames.

Use `NetworkService` for reliable or ordered messages. Use `UdpService` only
for session-scoped virtual datagrams such as a voice or realtime addon
protocol. Neither service exposes an arbitrary operating-system socket.

## Security boundary

An addon handler can run only after:

1. Steam transport validation;
2. authenticated peer identity;
3. e4steam core protocol and generation binding;
4. replay, rate, capacity and access checks;
5. addon manifest exchange and channel negotiation.

Accepting a transport session is not enough. Early, malformed, replayed or
stale-generation addon frames are rejected before the handler.

## Registering a channel

Channels must be registered during addon initialization, before registrations
freeze. The addon needs the `network.channel.register` capability.

```java
NetworkService.ChannelDescriptor descriptor =
        new NetworkService.ChannelDescriptor(
                new NetworkService.ChannelId("myaddon:sync"),
                1,                                      // minimum version
                2,                                      // maximum version
                NetworkService.Requirement.OPTIONAL,
                NetworkService.Direction.BIDIRECTIONAL,
                NetworkService.Delivery.RELIABLE_ORDERED,
                8 * 1024,                               // bytes per message
                64 * 1024,                              // bytes per second
                32,                                     // queued messages
                "myaddon:sync-v2"
        );

ApiResult<NetworkService.ChannelHandle> result =
        context.api().network().register(descriptor, (message, payload) -> {
            NetworkService.MessageReader reader =
                    new NetworkService.MessageReader(payload, 8 * 1024);
            String value = reader.readUtf8(256, 1024);
            if (reader.remaining() != 0) {
                throw new IllegalArgumentException("trailing payload");
            }
            return CompletableFuture.completedFuture(ApiResult.success(Boolean.TRUE));
        });

if (!result.isSuccess()) {
    throw new IllegalStateException("channel registration rejected");
}

NetworkService.ChannelHandle channel = result.value().get();
context.resources().own(channel);
```

Channel and endpoint IDs use a namespace such as `myaddon:sync`. Keep the
namespace stable after release. The schema ID identifies the payload schema; it
must not contain a token, password or other secret.

## Descriptor fields

| Field | Meaning |
| --- | --- |
| minimum/maximum version | Addon protocol versions this build can understand |
| `REQUIRED` | An incompatible or missing peer channel rejects addon activation for that session |
| `OPTIONAL` | Base Minecraft connection remains usable when the channel is unavailable |
| direction | `CLIENT_TO_HOST`, `HOST_TO_CLIENT` or `BIDIRECTIONAL` |
| delivery | Reliable ordered, reliable unordered or unreliable behavior |
| maximum message bytes | Hard decoded payload limit, at most 1 MiB |
| bytes per second | Per-channel traffic budget |
| queued messages | Backpressure limit, at most 1024 |
| schema ID | Stable non-secret name for the serialized format |

Use a required channel only when the addon cannot safely function without the
same protocol on both peers. Cosmetic, optional UI and observation features
should normally use optional channels.

## Negotiation

After core authentication, peers exchange bounded addon manifests. For a
matching channel, e4steam selects a supported protocol version and creates a
handle for the current session generation.

Channel states are:

- `REGISTERED`: local declaration exists;
- `NEGOTIATING`: peer manifest is being checked;
- `AVAILABLE`: sending and receiving are allowed;
- `UNAVAILABLE`: an optional channel could not be agreed;
- `CLOSED`: registration or session ended.

An old handle never becomes valid for a new connection. Look for
`STALE_SESSION`, `STALE_HANDLE`, `UNAVAILABLE` or `CLOSED` and obtain current
session state instead of retrying forever with the same handle.

## Encoding messages

`MessageWriter` and `MessageReader` provide bounded varints, UTF-8 strings and
byte arrays. They validate lengths before allocation and reject malformed UTF-8
and non-canonical or overflowing varints.

```java
byte[] payload = new NetworkService.MessageWriter(8 * 1024)
        .writeVarInt(2)
        .writeUtf8("hello", 256)
        .toByteArray();
```

On decode, always use limits no larger than the descriptor and verify that no
unexpected bytes remain. Never deserialize arbitrary Java objects from a peer.

## Sending

Sending requires the current `SessionId` and opaque authenticated `PeerId`:

```java
channel.send(sessionId, peerId, payload).thenAccept(sendResult -> {
    if (!sendResult.isSuccess()) {
        // Inspect the typed ApiError and stop or retry according to its category.
        return;
    }

    switch (sendResult.value().get()) {
        case ACCEPTED:
            break;
        case QUEUE_FULL:
        case RATE_LIMITED:
            // Apply backoff; do not spin.
            break;
        case UNAVAILABLE:
        case STALE_SESSION:
        case CLOSED:
            // Drop data that belongs to the old/unavailable channel.
            break;
    }
});
```

`ACCEPTED` means the bounded e4steam queue accepted the message. It is not an
application-level acknowledgement from the remote addon. Add your own bounded
acknowledgement only when the feature needs it.

## Fragmentation and fairness

Core wire protocol 4 carries addon manifests, agreements, fragments and
messages. Oversized allowed messages may be fragmented internally, but total
message size, fragment count, reassembly memory and lifetime remain bounded.

Per-peer, per-addon and per-channel budgets limit queued bytes, rate,
reassembly and callback work. Queue scheduling reserves capacity for core and
Minecraft traffic, so one addon cannot intentionally consume every transport
slot. Addons must still react to backpressure and avoid immediate retry loops.

## Virtual UDP

`UdpService` carries authenticated virtual datagrams inside the current e4steam
session. It does not bind an OS UDP port.

```java
UdpService.EndpointDescriptor endpoint = new UdpService.EndpointDescriptor(
        new UdpService.EndpointId("myaddon:realtime"),
        1200,
        50,
        "myaddon-realtime-v1"
);

ApiResult<UdpService.EndpointHandle> result = context.api().udp().register(
        endpoint,
        datagram -> CompletableFuture.completedFuture(ApiResult.success(Boolean.TRUE))
);
```

The endpoint needs `udp.provider.register`. Its datagrams remain bound to a
current `SessionId`, authenticated `PeerId`, endpoint ID and generation. Copy
payloads are defensive. A closed session makes the endpoint unavailable.

Do not use the virtual datagram API to expose an arbitrary local service. Core
Minecraft TCP forwarding and configured voice-chat UDP bridging are core
features, separate from addon endpoint registration.

## Handler rules

- Treat every payload as untrusted and validate it completely.
- Return a finite `CompletionStage`; do not block the callback thread.
- Keep expensive work on `ADDON_WORKER` and game mutations on the appropriate
  Minecraft execution context.
- Do not log payloads that may contain player data.
- Close registrations through the addon's parent `ResourceScope`.
- Do not retain a `SessionId`, `PeerId` or handle after the owning session
  closes.

## Testing

`api-testkit` includes `NetworkLoopbackHarness` for deterministic channel tests.
Cover:

- matching and incompatible protocol ranges;
- required versus optional behavior;
- malformed, truncated and oversized input;
- stale sessions and replay attempts;
- fragmentation and reassembly limits;
- queue pressure, rate limits and fairness;
- callback exceptions and cleanup.

After unit tests, run a real two-client Steam test and record it separately in
`COMPATIBILITY.md`. Loopback tests do not prove Valve P2P or relay behavior.
