package link.e4steam.api.identity;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiValidation;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/** Privacy-scoped immutable identity projections; authentication material is never exposed. */
public interface IdentityService {
    /** Returns the current local identity without a Steam profile by default. */
    ApiResult<LocalIdentity> local();

    /** Resolves one authenticated remote peer to its safe Minecraft identity. */
    CompletionStage<ApiResult<RemoteIdentity>> remote(PeerId peerId);

    /** Resolves optional Steam profile data when the caller has profile-read capability. */
    CompletionStage<ApiResult<SteamProfile>> steamProfile(PeerId peerId);

    /** Opaque generation-scoped peer identifier. */
    final class PeerId {
        private static final Pattern FORMAT = Pattern.compile("^[A-Za-z0-9_-]{8,96}$");
        private final String value;

        /** Creates an opaque peer identifier that contains no credential. */
        public PeerId(String value) {
            this.value = ApiValidation.identifier(value, "peerId", FORMAT);
        }

        /** Returns the opaque value. */
        public String value() { return value; }

        @Override public boolean equals(Object other) {
            return this == other || other instanceof PeerId && value.equals(((PeerId) other).value);
        }

        @Override public int hashCode() { return value.hashCode(); }

        @Override public String toString() { return "PeerId{" + value + '}'; }
    }

    /** Stable Minecraft identity derived only after successful core authentication. */
    final class MinecraftIdentity {
        private final UUID uuid;
        private final String displayName;
        private final boolean local;

        /** Creates a validated immutable Minecraft identity. */
        public MinecraftIdentity(UUID uuid, String displayName, boolean local) {
            this.uuid = Objects.requireNonNull(uuid, "uuid");
            this.displayName = ApiValidation.text(
                    displayName, "displayName", ApiLimits.MAX_DISPLAY_NAME_LENGTH);
            this.local = local;
        }

        /** Returns the stable versioned UUID. */
        public UUID uuid() { return uuid; }
        /** Returns the bounded display name. */
        public String displayName() { return displayName; }
        /** Returns whether the identity belongs to this process. */
        public boolean local() { return local; }

        @Override public String toString() {
            return "MinecraftIdentity{uuid=" + uuid + ", local=" + local + '}';
        }
    }

    /** Opaque peer plus its authenticated Minecraft projection. */
    final class PeerIdentity {
        private final PeerId peerId;
        private final MinecraftIdentity minecraft;

        /** Creates an immutable peer identity. */
        public PeerIdentity(PeerId peerId, MinecraftIdentity minecraft) {
            this.peerId = Objects.requireNonNull(peerId, "peerId");
            this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        }

        /** Returns the opaque peer id. */
        public PeerId peerId() { return peerId; }
        /** Returns the stable Minecraft identity. */
        public MinecraftIdentity minecraft() { return minecraft; }
    }

    /** Minimal local identity projection. */
    final class LocalIdentity {
        private final MinecraftIdentity minecraft;

        /** Creates a local projection. */
        public LocalIdentity(MinecraftIdentity minecraft) {
            this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
            if (!minecraft.local()) throw new IllegalArgumentException("local identity must be local");
        }

        /** Returns the local Minecraft identity. */
        public MinecraftIdentity minecraft() { return minecraft; }

        @Override public String toString() { return "LocalIdentity{" + minecraft + '}'; }
    }

    /** Minimal authenticated remote identity projection. */
    final class RemoteIdentity {
        private final PeerIdentity peer;

        /** Creates a remote projection. */
        public RemoteIdentity(PeerIdentity peer) {
            this.peer = Objects.requireNonNull(peer, "peer");
            if (peer.minecraft().local()) throw new IllegalArgumentException("remote identity cannot be local");
        }

        /** Returns the remote peer identity. */
        public PeerIdentity peer() { return peer; }

        @Override public String toString() { return "RemoteIdentity{peer=" + peer.peerId() + '}'; }
    }

    /** Presence state exposed only with explicit Steam profile capability. */
    enum PresenceState { OFFLINE, ONLINE, BUSY, AWAY, SNOOZE, UNKNOWN }

    /** Friend relationship exposed only when required by an admitted feature. */
    enum FriendRelationship { FRIEND, BLOCKED, NONE, UNKNOWN }

    /** Read-only personal Steam profile projection with no tickets or native handles. */
    final class SteamProfile {
        private static final Pattern STEAM_ID = Pattern.compile("^[1-9][0-9]{0,19}$");
        private final String steamId64;
        private final String personaName;
        private final String avatarAsset;
        private final PresenceState presence;
        private final FriendRelationship relationship;

        /** Creates a bounded personal-data projection. */
        public SteamProfile(String steamId64, String personaName, String avatarAsset,
                            PresenceState presence, FriendRelationship relationship) {
            this.steamId64 = ApiValidation.identifier(steamId64, "steamId64", STEAM_ID);
            this.personaName = ApiValidation.text(
                    personaName, "personaName", ApiLimits.MAX_DISPLAY_NAME_LENGTH);
            this.avatarAsset = ApiValidation.optionalText(avatarAsset, "avatarAsset", 512);
            this.presence = Objects.requireNonNull(presence, "presence");
            this.relationship = Objects.requireNonNull(relationship, "relationship");
        }

        /** Returns SteamID64 personal data. */
        public String steamId64() { return steamId64; }
        /** Returns the current display name. */
        public String personaName() { return personaName; }
        /** Returns an optional safe asset reference, never raw image memory. */
        public String avatarAsset() { return avatarAsset; }
        /** Returns presence. */
        public PresenceState presence() { return presence; }
        /** Returns the relationship needed by access/UI. */
        public FriendRelationship relationship() { return relationship; }

        @Override public String toString() {
            return "SteamProfile{personalData=redacted, presence=" + presence + '}';
        }
    }
}
