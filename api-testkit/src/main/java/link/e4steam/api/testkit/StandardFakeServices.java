package link.e4steam.api.testkit;

import link.e4steam.api.ApiError;
import link.e4steam.api.ApiErrorCode;
import link.e4steam.api.ApiResult;
import link.e4steam.api.ApiServiceKeys;
import link.e4steam.api.Registration;
import link.e4steam.api.Retryability;
import link.e4steam.api.access.AccessService;
import link.e4steam.api.command.CommandService;
import link.e4steam.api.config.ConfigService;
import link.e4steam.api.dedicated.DedicatedServerService;
import link.e4steam.api.diagnostics.DiagnosticsService;
import link.e4steam.api.identity.IdentityService;
import link.e4steam.api.lobby.LobbyService;
import link.e4steam.api.localization.LocalizationService;
import link.e4steam.api.logging.SafeLogger;
import link.e4steam.api.modpack.ModpackService;
import link.e4steam.api.network.NetworkService;
import link.e4steam.api.session.SessionService;
import link.e4steam.api.skin.SkinService;
import link.e4steam.api.storage.StorageService;
import link.e4steam.api.ui.UiService;
import link.e4steam.api.udp.UdpService;
import link.e4steam.api.world.WorldSettingsService;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Complete default fake service set for compile-checked addon contract tests. */
public final class StandardFakeServices implements AutoCloseable {
    /** Fake identity service. */ public final IdentityService identities;
    /** Fake session service. */ public final FakeSessionService sessions;
    /** Fake dedicated service. */ public final DedicatedServerService dedicated;
    /** Fake access service. */ public final AccessService access;
    /** Fake lobby service. */ public final FakeLobbyService lobbies;
    /** Loopback network. */ public final NetworkLoopbackHarness network;
    /** Virtual datagram service. */ public final UdpService udp;
    /** Fake UI host. */ public final FakeUiHost ui;
    /** Fake commands. */ public final FakeCommandService commands;
    /** In-memory config. */ public final InMemoryConfigService config;
    /** In-memory storage. */ public final InMemoryStorageService storage;
    /** Fake world settings. */ public final WorldSettingsService worldSettings;
    /** Fake modpack contracts. */ public final ModpackService modpacks;
    /** Fake skin contracts. */ public final SkinService skins;
    /** Fake diagnostics. */ public final DiagnosticsService diagnostics;
    /** Fake localization. */ public final LocalizationService localization;
    /** Structured logger that validates fields without writing external logs. */ public final SafeLogger logger;

    private final TestResourceScope resources = new TestResourceScope();

    /** Creates a complete available-client fake platform. */
    public StandardFakeServices() {
        IdentityService.MinecraftIdentity localMinecraft = new IdentityService.MinecraftIdentity(
                UUID.fromString("00000000-0000-0000-0000-000000000001"), "TestPlayer", true);
        this.identities = new IdentityService() {
            @Override public ApiResult<LocalIdentity> local() { return ApiResult.success(new LocalIdentity(localMinecraft)); }
            @Override public CompletionStage<ApiResult<RemoteIdentity>> remote(PeerId peerId) { return completed(unavailable("identity.remote")); }
            @Override public CompletionStage<ApiResult<SteamProfile>> steamProfile(PeerId peerId) { return completed(denied("identity.steam_profile")); }
        };
        SessionService.SessionId sessionId = new SessionService.SessionId("testsession1", 1L);
        this.sessions = new FakeSessionService(new SessionService.SessionSnapshot(
                sessionId, SessionService.SessionRole.INTEGRATED_HOST,
                SessionService.SessionState.ACTIVE, 0, 8, Collections.<String>emptySet()));
        this.dedicated = new FakeDedicated();
        this.access = new FakeAccess();
        this.lobbies = new FakeLobbyService();
        this.network = new NetworkLoopbackHarness();
        this.udp = new FakeUdp();
        this.ui = new FakeUiHost(UiService.Availability.AVAILABLE);
        this.commands = new FakeCommandService();
        this.config = new InMemoryConfigService();
        this.storage = new InMemoryStorageService();
        this.worldSettings = new FakeWorldSettings();
        this.modpacks = new FakeModpacks();
        this.skins = new FakeSkins();
        this.diagnostics = new FakeDiagnostics();
        this.localization = new FakeLocalization();
        this.logger = new FakeSafeLogger();
    }

    /** Registers every fake under the stable typed service key. */
    public TestServiceRegistry registerInto(TestServiceRegistry registry) {
        if (registry == null) throw new NullPointerException("registry");
        return registry
                .register(ApiServiceKeys.IDENTITIES, identities)
                .register(ApiServiceKeys.SESSIONS, sessions)
                .register(ApiServiceKeys.DEDICATED, dedicated)
                .register(ApiServiceKeys.ACCESS, access)
                .register(ApiServiceKeys.LOBBIES, lobbies)
                .register(ApiServiceKeys.NETWORK, network)
                .register(ApiServiceKeys.UDP, udp)
                .register(ApiServiceKeys.UI, ui)
                .register(ApiServiceKeys.COMMANDS, commands)
                .register(ApiServiceKeys.CONFIG, config)
                .register(ApiServiceKeys.STORAGE, storage)
                .register(ApiServiceKeys.WORLD_SETTINGS, worldSettings)
                .register(ApiServiceKeys.MODPACKS, modpacks)
                .register(ApiServiceKeys.SKINS, skins)
                .register(ApiServiceKeys.DIAGNOSTICS, diagnostics)
                .register(ApiServiceKeys.LOCALIZATION, localization)
                .register(ApiServiceKeys.LOGGER, logger);
    }

    @Override public void close() { network.close(); resources.close(); }

    private static final class FakeDedicated implements DedicatedServerService {
        private final ServerAuthorityRef authority = new ServerAuthorityRef("testauthority1", 1L);
        private final DedicatedServerSnapshot snapshot = new DedicatedServerSnapshot(DedicatedServerState.OFF, authority, DedicatedAccessMode.PRIVATE, true, false, 0, 8, "");
        @Override public ApiResult<DedicatedServerSnapshot> snapshot() { return ApiResult.success(snapshot); }
        @Override public ApiResult<DedicatedConfigSnapshot> config() { return ApiResult.success(new DedicatedConfigSnapshot(1, DedicatedAccessMode.PRIVATE, 8, false, "ANONYMOUS")); }
        @Override public CompletionStage<ApiResult<DedicatedServerSnapshot>> readiness() { return completed(unavailable("dedicated.readiness")); }
        @Override public CompletionStage<ApiResult<DedicatedServerSnapshot>> drain(String safeReasonCode) { return completed(ApiResult.success(snapshot)); }
        @Override public CompletionStage<ApiResult<PublicationPlan>> proposePublication(PublicationProposal proposal) { return completed(ApiResult.success(new PublicationPlan(false, "publication-disabled"))); }
    }

    private static final class FakeAccess implements AccessService {
        private final Map<AccessModeId, AccessModeProvider> providers = new LinkedHashMap<>();
        private boolean frozen;
        @Override public synchronized ApiResult<Registration> register(AccessModeProvider provider) { if (provider == null) throw new NullPointerException("provider"); if (frozen || providers.containsKey(provider.id())) return invalid("access.register"); providers.put(provider.id(), provider); return ApiResult.<Registration>success(new TestRegistration(() -> { synchronized (FakeAccess.this) { providers.remove(provider.id()); } })); }
        @Override public synchronized CompletionStage<ApiResult<AdmissionDecision>> evaluate(AccessModeId mode, AdmissionContext context) { AccessModeProvider provider = providers.get(mode); if (provider == null) return completed(unavailable("access.evaluate")); try { CompletionStage<AdmissionDecision> stage = provider.policy().evaluate(context); CompletableFuture<ApiResult<AdmissionDecision>> result = new CompletableFuture<>(); stage.whenComplete((decision, throwable) -> result.complete(throwable == null && decision != null ? ApiResult.success(decision) : ApiResult.success(AdmissionDecision.deny("addon-policy-failed")))); return result; } catch (RuntimeException exception) { return completed(ApiResult.success(AdmissionDecision.deny("addon-policy-failed"))); } }
        @Override public synchronized boolean registrationsFrozen() { return frozen; }
    }

    private static final class FakeUdp implements UdpService {
        private final Map<EndpointId, EndpointHandle> endpoints = new LinkedHashMap<>();
        @Override public synchronized ApiResult<EndpointHandle> register(EndpointDescriptor descriptor, DatagramHandler handler) { if (descriptor == null || handler == null) throw new NullPointerException("endpoint"); if (endpoints.containsKey(descriptor.id())) return invalid("udp.register"); EndpointHandle handle = new EndpointHandle() { private boolean closed; @Override public EndpointDescriptor descriptor() { return descriptor; } @Override public boolean ready() { return !closed; } @Override public CompletionStage<ApiResult<Boolean>> send(Datagram datagram) { if (closed) return completed(unavailable("udp.send")); if (datagram.payload().length > descriptor.maximumDatagramBytes()) return completed(invalid("udp.send")); return handler.onDatagram(datagram); } @Override public boolean isClosed() { return closed; } @Override public void close() { closed = true; synchronized (FakeUdp.this) { endpoints.remove(descriptor.id()); } } }; endpoints.put(descriptor.id(), handle); return ApiResult.success(handle); }
    }

    private static final class FakeWorldSettings implements WorldSettingsService {
        private final WorldSettingsSchema schema = new WorldSettingsSchema(1, Collections.<WorldSettingRule>emptyList());
        private final WorldSettingsSnapshot snapshot = new WorldSettingsSnapshot(1, Collections.<WorldSettingKey, WorldSettingValue>emptyMap());
        @Override public ApiResult<WorldSettingsSchema> schema() { return ApiResult.success(schema); }
        @Override public ApiResult<WorldSettingsSnapshot> snapshot() { return ApiResult.success(snapshot); }
        @Override public CompletionStage<ApiResult<WorldSettingsPlan>> plan(WorldSettingsProposal proposal) { return completed(ApiResult.success(new WorldSettingsPlan("test-plan", proposal.changes(), ApplyTiming.NEXT_WORLD_START, false))); }
        @Override public CompletionStage<ApiResult<WorldSettingsSnapshot>> apply(WorldSettingsPlan plan, boolean confirmed) { return completed(ApiResult.success(new WorldSettingsSnapshot(1, plan.changes()))); }
    }

    private static final class FakeModpacks implements ModpackService {
        private final Map<String, ModpackProvider> providers = new LinkedHashMap<>();
        @Override public synchronized ApiResult<Registration> registerProvider(ModpackProvider provider) { if (provider == null) throw new NullPointerException("provider"); if (providers.containsKey(provider.id())) return invalid("modpack.register"); providers.put(provider.id(), provider); return ApiResult.<Registration>success(new TestRegistration(() -> { synchronized (FakeModpacks.this) { providers.remove(provider.id()); } })); }
        @Override public CompletionStage<ApiResult<CompatibilityReport>> inspect(ModpackManifest manifest, Environment environment) { boolean same = manifest.requiredEnvironment().minecraftVersion().equals(environment.minecraftVersion()) && manifest.requiredEnvironment().loaderId().equals(environment.loaderId()); return completed(ApiResult.success(new CompatibilityReport(same ? CompatibilityStatus.COMPATIBLE : CompatibilityStatus.INCOMPATIBLE, Collections.<String>emptyList()))); }
        @Override public CompletionStage<ApiResult<InstallPlan>> plan(ModpackManifest manifest, CompatibilityReport report) { return completed(ApiResult.success(new InstallPlan("test-modpack-plan", Collections.<PlannedEntry>emptyList(), false, true))); }
    }

    private static final class FakeSkins implements SkinService {
        private final Map<String, SkinProvider> providers = new LinkedHashMap<>();
        @Override public synchronized ApiResult<Registration> registerProvider(SkinProvider provider) { if (provider == null) throw new NullPointerException("provider"); if (providers.containsKey(provider.id())) return invalid("skin.register"); providers.put(provider.id(), provider); return ApiResult.<Registration>success(new TestRegistration(() -> { synchronized (FakeSkins.this) { providers.remove(provider.id()); } })); }
        @Override public synchronized CompletionStage<ApiResult<SkinResult>> resolve(SkinRequest request) { if (providers.isEmpty()) return completed(ApiResult.success(SkinResult.fallback(RejectionReason.NOT_FOUND))); return providers.values().iterator().next().resolve(request); }
    }

    private static final class FakeDiagnostics implements DiagnosticsService {
        private final Map<String, DiagnosticsContributor> contributors = new LinkedHashMap<>();
        @Override public ApiResult<HealthSnapshot> health() { return ApiResult.success(new HealthSnapshot(Collections.singletonList(new ComponentHealth("testkit", Health.HEALTHY, "ready")))); }
        @Override public synchronized ApiResult<Registration> registerContributor(DiagnosticsContributor contributor) { if (contributor == null) throw new NullPointerException("contributor"); if (contributors.containsKey(contributor.id())) return invalid("diagnostics.register"); contributors.put(contributor.id(), contributor); return ApiResult.<Registration>success(new TestRegistration(() -> { synchronized (FakeDiagnostics.this) { contributors.remove(contributor.id()); } })); }
        @Override public CompletionStage<ApiResult<DoctorPreview>> doctorPreview(PrivacyOptions options) { return completed(ApiResult.success(new DoctorPreview(Collections.<DiagnosticsSection>emptyList(), Collections.singletonList("secrets-always-redacted"), 0))); }
    }

    private static final class FakeLocalization implements LocalizationService {
        private final LocaleSnapshot locale = new LocaleSnapshot("en-US", "en-US");
        @Override public LocaleSnapshot locale() { return locale; }
        @Override public ApiResult<String> resolve(LocalizedMessage message) { return ApiResult.success(message.fallback()); }
    }

    private static final class FakeSafeLogger implements SafeLogger {
        @Override public ApiResult<Boolean> log(Level level, String messageCode,
                                                Map<String, SafeValue> fields) {
            if (level == null || messageCode == null || !messageCode.matches("[a-z][a-z0-9_.-]{0,95}")) {
                return invalid("logger.log");
            }
            try {
                SafeLogger.fields(fields);
                return ApiResult.success(Boolean.TRUE);
            } catch (IllegalArgumentException failure) {
                return ApiResult.failure(new ApiError(ApiErrorCode.SECURITY_REJECTION,
                        "e4steam:security_rejection", Retryability.PERMANENT,
                        "logger.log", "", "testkit"));
            }
        }
    }

    private static <T> CompletionStage<T> completed(T value) { return CompletableFuture.completedFuture(value); }
    private static <T> ApiResult<T> unavailable(String operation) { return ApiResult.failure(new ApiError(ApiErrorCode.UNAVAILABLE, "e4steam:unavailable", Retryability.AFTER_STATE_CHANGE, operation, "", "testkit")); }
    private static <T> ApiResult<T> denied(String operation) { return ApiResult.failure(new ApiError(ApiErrorCode.CAPABILITY_DENIED, "e4steam:capability_denied", Retryability.PERMANENT, operation, "", "testkit")); }
    private static <T> ApiResult<T> invalid(String operation) { return ApiResult.failure(new ApiError(ApiErrorCode.INVALID_ARGUMENT, "e4steam:invalid_argument", Retryability.PERMANENT, operation, "", "testkit")); }
}
