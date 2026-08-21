package link.e4steam.api;

import link.e4steam.api.runtime.Architecture;
import link.e4steam.api.runtime.CompatibilityFlag;
import link.e4steam.api.runtime.LifecyclePhase;
import link.e4steam.api.runtime.LoaderInfo;
import link.e4steam.api.runtime.Platform;
import link.e4steam.api.runtime.RuntimeMode;
import link.e4steam.api.runtime.RuntimeSnapshot;
import link.e4steam.api.runtime.SteamRuntimeState;
import link.e4steam.api.runtime.TransportCapability;
import org.junit.jupiter.api.Test;
import link.e4steam.api.config.ConfigService;
import link.e4steam.api.lobby.LobbyService;
import link.e4steam.api.world.WorldSettingsService;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PrivacyContractTest {
    @Test
    void resultToStringNeverSerializesSuccessfulValue() {
        String canary = "ticket-CANARY-secret";
        assertFalse(ApiResult.success(canary).toString().contains(canary));
    }

    @Test
    void runtimeSnapshotStringOmitsFailureDetailsAndPersonalData() {
        String canary = "token-CANARY-secret";
        RuntimeSnapshot snapshot = new RuntimeSnapshot(
                ApiConstants.API_VERSION,
                "0.3.0",
                ApiConstants.WIRE_PROTOCOL_VERSION,
                Platform.WINDOWS,
                Architecture.X86_64,
                RuntimeMode.CLIENT,
                new LoaderInfo("forge", "47.3.0"),
                "1.20.1",
                SteamRuntimeState.FAILED,
                LifecyclePhase.IDLE,
                Collections.singleton(TransportCapability.RELIABLE_STREAM),
                Collections.singleton(CompatibilityFlag.LOADER_ADAPTER_PRESENT),
                canary
        );

        assertFalse(snapshot.toString().contains(canary));
    }

    @Test
    void structuredStringValuesDoNotLeakThroughToString() {
        String canary = "credential-CANARY";
        assertFalse(ConfigService.ConfigValue.text(canary).toString().contains(canary));
        assertFalse(WorldSettingsService.WorldSettingValue.text(canary).toString().contains(canary));
        assertFalse(LobbyService.MetadataValue.text(canary).toString().contains(canary));
    }

    @Test
    void lobbyMetadataRejectsCredentialLikeValuesBeforePublication() {
        assertThrows(IllegalArgumentException.class,
                () -> LobbyService.MetadataValue.text("token=TOKEN-CANARY"));
        assertThrows(IllegalArgumentException.class,
                () -> LobbyService.MetadataValue.text("Bearer TOKEN-CANARY"));
    }
}
