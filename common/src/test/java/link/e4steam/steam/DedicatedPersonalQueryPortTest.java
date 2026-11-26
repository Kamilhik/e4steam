package link.e4steam.steam;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DedicatedPersonalQueryPortTest {
    @Test void personalAddressAutomaticallyUsesTheMinecraftUdpPort() {
        assertEquals(25565,new SteamRuntimeBackend.Config(480,25565,0,8,"test",null).personalQueryPort(true));
    }
    @Test void explicitHostingPortAndUnnamedServerBehaviorArePreserved() {
        assertEquals(27016,new SteamRuntimeBackend.Config(480,25565,27016,8,"test",null).personalQueryPort(true));
        assertEquals(0,new SteamRuntimeBackend.Config(480,25565,0,8,"test",null).personalQueryPort(false));
    }
}
