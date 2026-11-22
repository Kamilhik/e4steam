package link.e4steam.steam;
import org.junit.jupiter.api.Test;
import java.nio.ByteBuffer;
import static org.junit.jupiter.api.Assertions.*;

class SteamMinecraftAuthModeRegistryTest {
    @Test void sessionHintIsBoundToAuthenticatedPeerAndReleasedOnDisconnect() {
        int port=49011;long peer=76561198012345679L;
        try {
            SteamMinecraftAuthModeRegistry.record(port,peer,true);
            assertTrue(SteamMinecraftAuthModeRegistry.online(port,peer));
            assertFalse(SteamMinecraftAuthModeRegistry.online(port,peer+1));
            assertFalse(SteamMinecraftAuthModeRegistry.online(port,0));
            SteamMinecraftAuthModeRegistry.forget(port,peer+1);
            assertTrue(SteamMinecraftAuthModeRegistry.online(port,peer));
            SteamMinecraftAuthModeRegistry.forget(port,peer);
            assertFalse(SteamMinecraftAuthModeRegistry.online(port,peer));
        } finally {SteamMinecraftAuthModeRegistry.forget(port,peer);}
    }
    @Test void authModePacketAcceptsOnlyBooleanValues() {
        byte[] packet=SteamProtocol.encodeMinecraftAuthMode(72,true);
        assertEquals(1,SteamProtocol.decode(ByteBuffer.wrap(packet)).payload()[0]);
        packet[packet.length-1]=2;
        assertNull(SteamProtocol.decode(ByteBuffer.wrap(packet)));
        assertEquals(0,SteamProtocol.decode(ByteBuffer.wrap(SteamProtocol.encodeMinecraftAuthMode(72,false))).payload()[0]);
    }
}
