package link.e4steam.lan;

import java.net.InetSocketAddress;
import java.net.ServerSocket;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LanPortSelectionTest {
    @Test void selectsAnAvailablePortWhileAnotherServerIsListening() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0)) {
            int port = LanPortSelection.availablePort();
            assertTrue(port > 0 && port <= 65535);
            assertNotEquals(occupied.getLocalPort(), port);
            // Selection must release its socket so Minecraft can bind it.
            try (ServerSocket minecraft = new ServerSocket()) {
                minecraft.bind(new InetSocketAddress(port));
                assertEquals(port, minecraft.getLocalPort());
            }
        }
    }
}
