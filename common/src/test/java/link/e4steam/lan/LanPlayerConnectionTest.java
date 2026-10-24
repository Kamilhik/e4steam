package link.e4steam.lan;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class LanPlayerConnectionTest {
    private static final SocketAddress ADDRESS = new InetSocketAddress("127.0.0.1", 25565);

    @Test void resolvesOlderIntermediaryGetter() {
        assertEquals(ADDRESS, LanPlayerConnection.clientRemoteAddress(new OlderListener()));
    }
    @Test void resolvesNewerInheritedGetter() {
        assertEquals(ADDRESS, LanPlayerConnection.clientRemoteAddress(new NewerListener()));
    }
    @Test void resolvesPrivateConnectionField() {
        assertEquals(ADDRESS, LanPlayerConnection.clientRemoteAddress(new FieldListener()));
    }

    private static final class Connection {
        public SocketAddress method_10755() { return ADDRESS; }
    }
    private static final class OlderListener {
        public Connection method_2872() { return new Connection(); }
    }
    private static class CommonListener {
        public Connection method_48296() { return new Connection(); }
    }
    private static final class NewerListener extends CommonListener { }
    private static final class FieldListener {
        private final Connection field_3689 = new Connection();
    }
}
