package link.e4steam.internal.api;

import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiValidation;
import link.e4steam.api.Registration;
import link.e4steam.api.capability.Capabilities;
import link.e4steam.api.dedicated.DedicatedServerService;
import link.e4steam.api.identity.IdentityService;
import link.e4steam.api.lobby.LobbyService;
import link.e4steam.api.localization.LocalizationService;
import link.e4steam.api.session.SessionService;
import link.e4steam.api.world.WorldSettingsService;
import link.e4steam.internal.dedicated.DedicatedServerController;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Safe unavailable/adapter-neutral state services used before their runtime context exists. */
final class CoreStateServices {
    private CoreStateServices() { }

    static IdentityService identities(CoreCapabilityService capabilities,
                                      CoreSessionRegistry sessions) {
        return new IdentityService() {
            @Override public ApiResult<LocalIdentity> local() {
                if (!capabilities.has(Capabilities.IDENTITY_MINECRAFT_READ)) return denied("identity.local");
                LocalIdentity identity = sessions.localIdentity();
                return identity == null ? unavailable("identity.local") : ApiResult.success(identity);
            }
            @Override public CompletionStage<ApiResult<RemoteIdentity>> remote(PeerId peerId) {
                if (!capabilities.has(Capabilities.IDENTITY_MINECRAFT_READ)) {
                    return completed(denied("identity.remote"));
                }
                if (peerId == null) return completed(SafeApiErrors.failure(
                        ApiErrorCode.INVALID_ARGUMENT, "identity.remote", "Validation"));
                RemoteIdentity identity = sessions.remoteIdentity(peerId);
                return completed(identity == null ? unavailable("identity.remote")
                        : ApiResult.success(identity));
            }
            @Override public CompletionStage<ApiResult<SteamProfile>> steamProfile(PeerId peerId) {
                boolean allowed = capabilities.has(Capabilities.IDENTITY_STEAM_PROFILE_READ)
                        || capabilities.has(Capabilities.STEAM_PROFILE_READ);
                return completed(allowed ? unavailable("identity.steam-profile") : denied("identity.steam-profile"));
            }
        };
    }

    static SessionService sessions(CoreCapabilityService capabilities,
                                   CoreSessionRegistry sessions) {
        return new SessionService() {
            @Override public ApiResult<SessionSnapshot> snapshot() {
                return capabilities.has(Capabilities.SESSION_OBSERVE)
                        ? sessions.snapshot() : denied("session.snapshot");
            }
            @Override public CompletionStage<ApiResult<PeerPage>> peers(SessionId id, String cursor, int limit) {
                return capabilities.has(Capabilities.SESSION_OBSERVE)
                        ? sessions.peers(id, cursor, limit) : completed(denied("session.peers"));
            }
            @Override public CompletionStage<ApiResult<SessionSnapshot>> disconnect(SessionId id, String reason) {
                if (!capabilities.has(Capabilities.SESSION_CONTROL)) {
                    return completed(denied("session.disconnect"));
                }
                try {
                    String checked = ApiValidation.text(reason, "reason", 96);
                    ApiValidation.rejectSensitiveName(checked, "reason");
                } catch (RuntimeException failure) {
                    return completed(SafeApiErrors.failure(ApiErrorCode.INVALID_ARGUMENT,
                            "session.disconnect", "Reason"));
                }
                return sessions.disconnect(id);
            }
            @Override public CompletionStage<ApiResult<SessionSnapshot>> readiness() {
                return capabilities.has(Capabilities.SESSION_OBSERVE)
                        ? sessions.readiness() : completed(denied("session.readiness"));
            }
            @Override public ApiResult<Registration> registerResource(SessionId id, Registration resource) {
                return capabilities.has(Capabilities.SESSION_OBSERVE)
                        ? sessions.register(id, resource) : denied("session.resource");
            }
        };
    }

    static DedicatedServerService dedicated(CoreCapabilityService capabilities) {
        DedicatedServerController controller = DedicatedServerController.current();
        DedicatedServerService delegate = controller == null ? null : controller.service();
        return new DedicatedServerService() {
            @Override public ApiResult<DedicatedServerSnapshot> snapshot() {
                if (!capabilities.has(Capabilities.DEDICATED_OBSERVE)) {
                    return denied("dedicated.snapshot");
                }
                return delegate == null ? unsupported("dedicated.snapshot") : delegate.snapshot();
            }
            @Override public ApiResult<DedicatedConfigSnapshot> config() {
                if (!capabilities.has(Capabilities.DEDICATED_OBSERVE)) {
                    return denied("dedicated.config");
                }
                return delegate == null ? unsupported("dedicated.config") : delegate.config();
            }
            @Override public CompletionStage<ApiResult<DedicatedServerSnapshot>> readiness() {
                if (!capabilities.has(Capabilities.DEDICATED_OBSERVE)) {
                    return completed(denied("dedicated.readiness"));
                }
                return delegate == null ? completed(unsupported("dedicated.readiness"))
                        : delegate.readiness();
            }
            @Override public CompletionStage<ApiResult<DedicatedServerSnapshot>> drain(String reason) {
                if (!capabilities.has(Capabilities.DEDICATED_ADMIN)) {
                    return completed(denied("dedicated.drain"));
                }
                return delegate == null ? completed(unsupported("dedicated.drain"))
                        : delegate.drain(reason);
            }
            @Override public CompletionStage<ApiResult<PublicationPlan>> proposePublication(PublicationProposal proposal) {
                if (!capabilities.has(Capabilities.DEDICATED_PUBLICATION_PROPOSE)) {
                    return completed(denied("dedicated.publication"));
                }
                return delegate == null
                        ? completed(ApiResult.success(new PublicationPlan(
                        false, "public-provider-not-active")))
                        : delegate.proposePublication(proposal);
            }
        };
    }

    static LobbyService lobbies(CoreCapabilityService capabilities) {
        return new LobbyService() {
            @Override public CompletionStage<ApiResult<LobbySnapshot>> create(CreateRequest request) {
                return completed(capabilities.has(Capabilities.LOBBY_CREATE)
                        ? unavailable("lobby.create") : denied("lobby.create"));
            }
            @Override public CompletionStage<ApiResult<LobbySnapshot>> join(LobbyId id) {
                return completed(unavailable("lobby.join"));
            }
            @Override public CompletionStage<ApiResult<Boolean>> leave(LobbyId id) {
                return completed(unavailable("lobby.leave"));
            }
            @Override public CompletionStage<ApiResult<LobbySnapshot>> updateMetadata(LobbyId id, Metadata metadata) {
                return completed(capabilities.has(Capabilities.LOBBY_METADATA_WRITE)
                        ? unavailable("lobby.metadata") : denied("lobby.metadata"));
            }
            @Override public CompletionStage<ApiResult<SearchPage>> search(SearchQuery query) {
                return completed(capabilities.has(Capabilities.LOBBY_SEARCH)
                        ? unavailable("lobby.search") : denied("lobby.search"));
            }
        };
    }

    static WorldSettingsService world(CoreCapabilityService capabilities) {
        return new WorldSettingsService() {
            private final WorldSettingsSchema schema = new WorldSettingsSchema(1, Collections.emptyList());
            private final WorldSettingsSnapshot snapshot = new WorldSettingsSnapshot(1, Collections.emptyMap());
            @Override public ApiResult<WorldSettingsSchema> schema() {
                return capabilities.has(Capabilities.WORLD_SETTINGS_READ) ? ApiResult.success(schema) : denied("world.schema");
            }
            @Override public ApiResult<WorldSettingsSnapshot> snapshot() {
                return capabilities.has(Capabilities.WORLD_SETTINGS_READ) ? ApiResult.success(snapshot) : denied("world.snapshot");
            }
            @Override public CompletionStage<ApiResult<WorldSettingsPlan>> plan(WorldSettingsProposal proposal) {
                if (!capabilities.has(Capabilities.WORLD_SETTINGS_PROPOSE)) return completed(denied("world.plan"));
                return completed(ApiResult.success(new WorldSettingsPlan("plan-disabled-adapter",
                        proposal == null ? Collections.emptyMap() : proposal.changes(),
                        ApplyTiming.NEXT_WORLD_START, true)));
            }
            @Override public CompletionStage<ApiResult<WorldSettingsSnapshot>> apply(WorldSettingsPlan plan, boolean confirmed) {
                return completed(unsupported("world.apply"));
            }
        };
    }

    static LocalizationService localization() {
        return new LocalizationService() {
            @Override public LocaleSnapshot locale() {
                String current = Locale.getDefault().toLanguageTag();
                if (current == null || current.isEmpty() || "und".equals(current)) current = "en-US";
                return new LocaleSnapshot(current, "en-US");
            }
            @Override public ApiResult<String> resolve(LocalizedMessage message) {
                if (message == null) return SafeApiErrors.failure(ApiErrorCode.INVALID_ARGUMENT,
                        "localization.resolve", "Validation");
                String value = message.fallback();
                for (MessageArgument argument : message.arguments()) {
                    value = value.replace("{" + argument.name() + "}", argument.value());
                }
                return ApiResult.success(value);
            }
        };
    }

    private static <T> ApiResult<T> denied(String operation) { return SafeApiErrors.failure(
            ApiErrorCode.CAPABILITY_DENIED, operation, "PolicyDenied"); }
    private static <T> ApiResult<T> unavailable(String operation) { return SafeApiErrors.failure(
            ApiErrorCode.UNAVAILABLE, operation, "NoActiveContext"); }
    private static <T> ApiResult<T> unsupported(String operation) { return SafeApiErrors.failure(
            ApiErrorCode.UNSUPPORTED, operation, "BackendUnavailable"); }
    private static <T> CompletionStage<T> completed(T value) { return CompletableFuture.completedFuture(value); }
}
