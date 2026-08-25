package link.e4steam.internal.api;

import link.e4steam.api.ApiConstants;
import link.e4steam.api.runtime.Architecture;
import link.e4steam.api.runtime.CompatibilityFlag;
import link.e4steam.api.runtime.LifecyclePhase;
import link.e4steam.api.runtime.Platform;
import link.e4steam.api.runtime.RuntimeService;
import link.e4steam.api.runtime.RuntimeSnapshot;
import link.e4steam.api.runtime.SteamRuntimeState;
import link.e4steam.api.runtime.TransportCapability;
import link.e4steam.steam.SteamClientApiBridge;
import link.e4steam.internal.dedicated.DedicatedServerController;
import link.e4steam.api.dedicated.DedicatedServerService.DedicatedServerState;
import link.e4steam.api.runtime.RuntimeMode;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import link.e4steam.api.ApiResult;
import link.e4steam.api.dedicated.DedicatedServerService.DedicatedServerSnapshot;

final class CoreRuntimeService implements RuntimeService {
    private final RuntimeEnvironment environment;
    private final CompletableFuture<RuntimeSnapshot> readiness = new CompletableFuture<>();
    private volatile LifecyclePhase phase = LifecyclePhase.BOOTSTRAP;

    CoreRuntimeService(RuntimeEnvironment environment) { this.environment = environment; }

    @Override public RuntimeSnapshot snapshot() {
        SteamRuntimeState steamState;
        String failureCategory;
        if (environment.mode() == RuntimeMode.DEDICATED_SERVER) {
            DedicatedServerController controller = DedicatedServerController.current();
            ApiResult<DedicatedServerSnapshot> result = controller == null
                    ? null : controller.service().snapshot();
            DedicatedServerSnapshot dedicated = result != null && result.value().isPresent()
                    ? result.value().get() : null;
            DedicatedServerState dedicatedState = dedicated == null
                    ? DedicatedServerState.OFF : dedicated.state();
            steamState = dedicatedSteamState(dedicatedState);
            failureCategory = dedicated == null ? "DEDICATED_BACKEND_UNAVAILABLE"
                    : dedicated.failureCategory();
        } else {
            String status = SteamClientApiBridge.statusCode();
            try {
                steamState = SteamRuntimeState.valueOf(
                        status.equals("RUNNING") ? "READY" : status
                );
            } catch (IllegalArgumentException failure) {
                steamState = SteamRuntimeState.FAILED;
            }
            failureCategory = SteamClientApiBridge.failureCategory();
        }
        Set<TransportCapability> transports;
        if (steamState != SteamRuntimeState.READY) {
            transports = Collections.emptySet();
        } else if (environment.mode() == RuntimeMode.DEDICATED_SERVER) {
            transports = Collections.unmodifiableSet(
                    EnumSet.of(TransportCapability.RELIABLE_STREAM));
        } else {
            transports = Collections.unmodifiableSet(EnumSet.of(
                    TransportCapability.RELIABLE_STREAM,
                    TransportCapability.DATAGRAM,
                    TransportCapability.LOBBY_INVITES));
        }
        EnumSet<CompatibilityFlag> flags = EnumSet.of(CompatibilityFlag.LOADER_ADAPTER_PRESENT);
        if (environment.mode() != RuntimeMode.DEDICATED_SERVER
                && steamClientBackendAvailable(platform(), architecture())) {
            flags.add(CompatibilityFlag.STEAM_CLIENT_BACKEND_AVAILABLE);
        }
        if (environment.mode() == RuntimeMode.DEDICATED_SERVER
                && steamClientBackendAvailable(platform(), architecture())) {
            flags.add(CompatibilityFlag.DEDICATED_BACKEND_AVAILABLE);
        }
        if (environment.experimental()) flags.add(CompatibilityFlag.EXPERIMENTAL_COMBINATION);
        return new RuntimeSnapshot(ApiConstants.API_VERSION, "0.3.0", ApiConstants.WIRE_PROTOCOL_VERSION,
                platform(), architecture(), environment.mode(), environment.loader(),
                environment.minecraftVersion(), steamState, phase, transports, flags,
                failureCategory);
    }

    @Override public CompletionStage<RuntimeSnapshot> readiness() {
        return readiness.thenApply(snapshot -> snapshot);
    }

    void phase(LifecyclePhase next) {
        phase = java.util.Objects.requireNonNull(next, "next");
        RuntimeSnapshot snapshot = snapshot();
        if (snapshot.steamState() == SteamRuntimeState.READY && !readiness.isDone()) readiness.complete(snapshot);
    }

    void refreshReadiness() {
        RuntimeSnapshot snapshot = snapshot();
        if (snapshot.steamState() == SteamRuntimeState.READY && !readiness.isDone()) readiness.complete(snapshot);
    }

    private static Platform platform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("windows")) return Platform.WINDOWS;
        if (os.contains("linux")) return Platform.LINUX;
        if (os.contains("mac") || os.contains("darwin")) return Platform.MACOS;
        return Platform.UNKNOWN;
    }

    private static Architecture architecture() {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x64")) return Architecture.X86_64;
        if (arch.equals("aarch64") || arch.equals("arm64")) return Architecture.ARM64;
        return Architecture.UNKNOWN;
    }

    private static boolean steamClientBackendAvailable(Platform platform, Architecture architecture) {
        if (platform == Platform.WINDOWS || platform == Platform.LINUX) {
            return architecture == Architecture.X86_64;
        }
        return platform == Platform.MACOS
                && (architecture == Architecture.X86_64 || architecture == Architecture.ARM64);
    }

    private static SteamRuntimeState dedicatedSteamState(DedicatedServerState state) {
        switch (state) {
            case OFF:
                return SteamRuntimeState.NEW;
            case CONFIG_VALIDATED:
            case NATIVES_READY:
            case STEAM_INITIALIZING:
            case STEAM_LOGGING_ON:
                return SteamRuntimeState.STARTING;
            case TRANSPORT_READY:
            case MINECRAFT_READY:
            case ACCEPTING:
                return SteamRuntimeState.READY;
            case DRAINING:
                return SteamRuntimeState.STOPPING;
            case STOPPED:
                return SteamRuntimeState.STOPPED;
            case FAILED:
            default:
                return SteamRuntimeState.FAILED;
        }
    }
}
