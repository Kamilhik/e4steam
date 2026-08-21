package link.e4steam.api.lobby;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiValidation;
import link.e4steam.api.identity.IdentityService.PeerId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.regex.Pattern;

/** Bounded typed Steam lobby operations; search metadata is always untrusted input. */
public interface LobbyService {
    /** Creates a lobby after capability, bounds and reserved-key checks. */
    CompletionStage<ApiResult<LobbySnapshot>> create(CreateRequest request);
    /** Joins an opaque lobby; its id alone never proves admission. */
    CompletionStage<ApiResult<LobbySnapshot>> join(LobbyId lobbyId);
    /** Leaves the current lobby idempotently. */
    CompletionStage<ApiResult<Boolean>> leave(LobbyId lobbyId);
    /** Updates only caller-owned namespaced metadata. */
    CompletionStage<ApiResult<LobbySnapshot>> updateMetadata(LobbyId lobbyId, Metadata metadata);
    /** Executes a bounded cancellable search. */
    CompletionStage<ApiResult<SearchPage>> search(SearchQuery query);

    /** Steam lobby visibility. */ enum Visibility { PRIVATE, FRIENDS_ONLY, PUBLIC }

    /** Opaque non-secret lobby id. */
    final class LobbyId {
        private static final Pattern FORMAT = Pattern.compile("^[1-9][0-9]{0,19}$");
        private final String value;
        /** Creates an opaque lobby id. */ public LobbyId(String value) { this.value = ApiValidation.identifier(value, "lobbyId", FORMAT); }
        /** Returns the opaque id. */ public String value() { return value; }
        @Override public boolean equals(Object other) { return this == other || other instanceof LobbyId && value.equals(((LobbyId) other).value); }
        @Override public int hashCode() { return value.hashCode(); }
        @Override public String toString() { return "LobbyId{opaque}"; }
    }

    /** Allowlisted primitive metadata value. */
    final class MetadataValue {
        /** Supported primitive type. */ public enum Type { STRING, INTEGER, BOOLEAN }
        private final Type type;
        private final String value;
        private MetadataValue(Type type, String value) { this.type = type; this.value = value; }
        /** Creates bounded UTF-8-compatible non-credential text. */ public static MetadataValue text(String value) {
            String checked = ApiValidation.text(value, "metadata", ApiLimits.MAX_VALUE_LENGTH);
            if (looksSensitive(checked)) throw new IllegalArgumentException("metadata value appears sensitive");
            return new MetadataValue(Type.STRING, checked);
        }
        /** Creates an integer value. */ public static MetadataValue integer(long value) { return new MetadataValue(Type.INTEGER, Long.toString(value)); }
        /** Creates a boolean value. */ public static MetadataValue bool(boolean value) { return new MetadataValue(Type.BOOLEAN, Boolean.toString(value)); }
        /** Returns primitive type. */ public Type type() { return type; }
        /** Returns canonical text representation. */ public String value() { return value; }
        @Override public String toString() {
            return type == Type.STRING ? "STRING{redacted-from-toString}" : type + ":" + value;
        }
        private static boolean looksSensitive(String value) {
            String lower = value.toLowerCase(java.util.Locale.ROOT);
            return lower.contains("bearer ") || lower.matches(
                    ".*(?:token|ticket|password|secret|cookie|authorization|joinaddress|gslt)=[^\\s]+.*");
        }
    }

    /** Immutable namespaced metadata with credential-like keys rejected. */
    final class Metadata {
        private static final Pattern KEY = Pattern.compile("^[a-z][a-z0-9_.-]{0,31}:[a-z][a-z0-9_.-]{0,63}$");
        private final Map<String, MetadataValue> values;
        /** Creates bounded metadata. */
        public Metadata(Map<String, MetadataValue> values) {
            if (values == null) throw new NullPointerException("values");
            if (values.size() > 64) throw new IllegalArgumentException("too many metadata keys");
            LinkedHashMap<String, MetadataValue> copy = new LinkedHashMap<>();
            for (Map.Entry<String, MetadataValue> entry : values.entrySet()) {
                String key = ApiValidation.identifier(entry.getKey(), "metadata key", KEY);
                ApiValidation.rejectSensitiveName(key, "metadata key");
                if (key.startsWith("e4steam:core/")) throw new IllegalArgumentException("reserved core metadata");
                copy.put(key, Objects.requireNonNull(entry.getValue(), "metadata value"));
            }
            this.values = Collections.unmodifiableMap(copy);
        }
        /** Returns immutable primitive metadata. */ public Map<String, MetadataValue> values() { return values; }
        @Override public String toString() { return "Metadata{keys=" + values.keySet() + '}'; }
    }

    /** Bounded lobby creation request. */
    final class CreateRequest {
        private final Visibility visibility;
        private final int memberLimit;
        private final Metadata metadata;
        /** Creates a request. */
        public CreateRequest(Visibility visibility, int memberLimit, Metadata metadata) {
            this.visibility = Objects.requireNonNull(visibility, "visibility");
            if (memberLimit < 2 || memberLimit > 256) throw new IllegalArgumentException("invalid memberLimit");
            this.memberLimit = memberLimit;
            this.metadata = Objects.requireNonNull(metadata, "metadata");
        }
        /** Returns visibility. */ public Visibility visibility() { return visibility; }
        /** Returns member limit. */ public int memberLimit() { return memberLimit; }
        /** Returns safe metadata. */ public Metadata metadata() { return metadata; }
    }

    /** Minimal immutable lobby member. */
    final class MemberSnapshot {
        private final PeerId peerId;
        private final boolean owner;
        /** Creates a member projection. */ public MemberSnapshot(PeerId peerId, boolean owner) { this.peerId = Objects.requireNonNull(peerId, "peerId"); this.owner = owner; }
        /** Returns opaque peer id. */ public PeerId peerId() { return peerId; }
        /** Returns whether the peer owns this user lobby. */ public boolean owner() { return owner; }
    }

    /** Immutable lobby snapshot. */
    final class LobbySnapshot {
        private final LobbyId id;
        private final Visibility visibility;
        private final int memberLimit;
        private final List<MemberSnapshot> members;
        private final Metadata metadata;
        /** Creates a snapshot. */
        public LobbySnapshot(LobbyId id, Visibility visibility, int memberLimit,
                             List<MemberSnapshot> members, Metadata metadata) {
            this.id = Objects.requireNonNull(id, "id"); this.visibility = Objects.requireNonNull(visibility, "visibility");
            if (memberLimit < 2 || memberLimit > 256) throw new IllegalArgumentException("invalid memberLimit");
            this.memberLimit = memberLimit;
            this.members = ApiValidation.immutableList(members, 256, "members");
            if (this.members.size() > memberLimit) throw new IllegalArgumentException("members exceed limit");
            this.metadata = Objects.requireNonNull(metadata, "metadata");
        }
        /** Returns lobby id. */ public LobbyId id() { return id; }
        /** Returns visibility. */ public Visibility visibility() { return visibility; }
        /** Returns capacity. */ public int memberLimit() { return memberLimit; }
        /** Returns immutable members. */ public List<MemberSnapshot> members() { return members; }
        /** Returns untrusted-but-validated metadata. */ public Metadata metadata() { return metadata; }
    }

    /** Bounded query; all results still require the core handshake. */
    final class SearchQuery {
        private final Metadata filters;
        private final int limit;
        private final String cursor;
        /** Creates a query. */
        public SearchQuery(Metadata filters, int limit, String cursor) {
            this.filters = Objects.requireNonNull(filters, "filters");
            if (limit < 1 || limit > ApiLimits.MAX_PAGE_SIZE) throw new IllegalArgumentException("invalid limit");
            this.limit = limit;
            this.cursor = ApiValidation.optionalText(cursor, "cursor", 256);
        }
        /** Returns bounded filters. */ public Metadata filters() { return filters; }
        /** Returns result limit. */ public int limit() { return limit; }
        /** Returns opaque cursor. */ public String cursor() { return cursor; }
    }

    /** Bounded search page. */
    final class SearchPage {
        private final List<LobbySnapshot> results;
        private final String nextCursor;
        /** Creates a page. */
        public SearchPage(List<LobbySnapshot> results, String nextCursor) {
            this.results = ApiValidation.immutableList(results, ApiLimits.MAX_PAGE_SIZE, "results");
            this.nextCursor = ApiValidation.optionalText(nextCursor, "nextCursor", 256);
        }
        /** Returns immutable untrusted results. */ public List<LobbySnapshot> results() { return results; }
        /** Returns opaque next cursor. */ public String nextCursor() { return nextCursor; }
    }
}
