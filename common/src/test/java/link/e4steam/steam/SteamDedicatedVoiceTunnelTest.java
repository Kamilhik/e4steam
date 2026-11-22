package link.e4steam.steam;

import org.junit.jupiter.api.Test;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class SteamDedicatedVoiceTunnelTest {
    @Test void voiceCrossesBothSteamDirectionsWithoutExposingTheServerUdpPort() throws Exception {
        WireRuntime hostWire=new WireRuntime(),guestWire=new WireRuntime();
        SteamConnectionBridge hostOwner=new SteamConnectionBridge(hostWire,77,51,new Socket(),true,null);
        SteamConnectionBridge guestOwner=new SteamConnectionBridge(guestWire,88,51,new Socket(),false,null);
        try(DatagramSocket voiceServer=new DatagramSocket(new InetSocketAddress("127.0.0.1",0));
            DatagramSocket voiceClient=new DatagramSocket(new InetSocketAddress("127.0.0.1",0))) {
            voiceServer.setSoTimeout(3000);voiceClient.setSoTimeout(3000);
            SteamUdpBridge host=SteamUdpBridge.host(hostWire,hostOwner,voiceServer.getLocalPort());
            SteamUdpBridge guest=SteamUdpBridge.client(guestWire,guestOwner,0,true);
            hostWire.destination=guest;guestWire.destination=host;
            try {
                host.start();guest.start();
                byte[] request=new byte[900];for(int i=0;i<request.length;i++)request[i]=(byte)i;
                voiceClient.send(new DatagramPacket(request,request.length,new InetSocketAddress("127.0.0.1",guest.localPort())));
                DatagramPacket received=new DatagramPacket(new byte[1500],1500);voiceServer.receive(received);
                assertArrayEquals(request,Arrays.copyOf(received.getData(),received.getLength()));
                assertEquals(host.localPort(),received.getPort());
                byte[] reply="voice-response".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                voiceServer.send(new DatagramPacket(reply,reply.length,received.getSocketAddress()));
                DatagramPacket actual=new DatagramPacket(new byte[1500],1500);voiceClient.receive(actual);
                assertArrayEquals(reply,Arrays.copyOf(actual.getData(),actual.getLength()));
                assertEquals(guest.localPort(),actual.getPort());
            } finally {guest.close();host.close();}
        } finally {hostOwner.close(false);guestOwner.close(false);}
    }
    @Test void voiceEndpointIsBoundedAndDoesNotChangeTheOriginalDedicatedAcknowledgement() {
        byte[] encoded=SteamProtocol.encodeUdpEndpoint(51,24454);
        var decoded=SteamProtocol.decode(ByteBuffer.wrap(encoded));
        assertNotNull(decoded);assertEquals(SteamProtocol.UDP_ENDPOINT,decoded.type());
        assertArrayEquals(new byte[]{1,0x5f,(byte)0x86},decoded.payload());
        assertEquals(8,SteamProtocol.decode(ByteBuffer.wrap(SteamProtocol.encodeDedicatedOpenAck(51,2))).payload().length);
        encoded[SteamProtocol.HEADER_SIZE]=2;
        assertNull(SteamProtocol.decode(ByteBuffer.wrap(encoded)));
        assertThrows(IllegalArgumentException.class,()->SteamProtocol.encodeUdpEndpoint(51,0));
        assertThrows(IllegalArgumentException.class,()->SteamProtocol.encodeUdpEndpoint(51,65536));
    }
    private static final class WireRuntime implements SteamBridgeRuntime {
        SteamUdpBridge destination;
        public void sendDatagram(SteamUdpBridge sender,byte[] payload) {
            var packet=SteamProtocol.decode(ByteBuffer.wrap(SteamProtocol.encodeDatagram(sender.owner().connectionId(),payload)));
            assertNotNull(packet);destination.acceptSteamDatagram(packet.payload());
        }
        public boolean sendData(SteamConnectionBridge owner,byte[] bytes){return false;}
        public boolean sendFin(SteamConnectionBridge owner){return true;}
        public boolean sendReset(SteamConnectionBridge owner){return true;}
        public void closeUdpBridge(SteamConnectionBridge owner){}
        public void unregister(SteamConnectionBridge owner){}
    }
}
