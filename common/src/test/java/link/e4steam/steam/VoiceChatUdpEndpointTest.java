package link.e4steam.steam;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VoiceChatUdpEndpointTest {
    public static final class Events {
        Server server;
        public Server getServer() { return server; }
    }
    public static final class Server {
        int port;
        public int getPort() { return port; }
    }

    @Test void readsTheBoundPortAfterAsynchronousStartupAndRestart() throws Exception {
        Events events = new Events();
        assertEquals(0, VoiceChatUdpEndpoint.simpleVoiceChatPort(events));
        events.server = new Server();
        assertEquals(0, VoiceChatUdpEndpoint.simpleVoiceChatPort(events));
        events.server.port = 47831;
        assertEquals(47831, VoiceChatUdpEndpoint.simpleVoiceChatPort(events));
        events.server = new Server();
        events.server.port = 51342;
        assertEquals(51342, VoiceChatUdpEndpoint.simpleVoiceChatPort(events));
    }

    @Test void ignoresUnboundAndInvalidPorts() throws Exception {
        assertEquals(0, VoiceChatUdpEndpoint.simpleVoiceChatPort(null));
        Events events = new Events();
        events.server = new Server();
        for (int port : new int[]{-1, 0, 65536}) {
            events.server.port = port;
            assertEquals(0, VoiceChatUdpEndpoint.simpleVoiceChatPort(events));
        }
        events.server.port = 65535;
        assertEquals(65535, VoiceChatUdpEndpoint.simpleVoiceChatPort(events));
    }
}