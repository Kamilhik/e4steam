package link.e4steam.steam;

import org.junit.jupiter.api.Test;
import java.security.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SteamDedicatedNameProtocolTest {
    private final KeyPair key = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
    SteamDedicatedNameProtocolTest() throws Exception {}
    private SteamDedicatedNameProtocol.Target target(KeyPair pair,long generation,long updated) throws Exception {
        return new SteamDedicatedNameProtocol.Target(new SteamDedicatedAddress(76561198000000001L,generation),
                "1.21.1","fabric","0.3.7","0",1,2,64,updated,SteamDedicatedNameProtocol.fingerprint(pair.getPublic()),"my-server");
    }
    @Test void advertisementRoundTripAndFreshness() throws Exception {
        long now=System.currentTimeMillis();
        String encoded=SteamDedicatedNameProtocol.encode(target(key,4,now));
        assertTrue(encoded.length()<=127);
        var decoded=SteamDedicatedNameProtocol.decode(encoded,now);
        assertEquals("my-server",decoded.name);
        assertEquals(4,decoded.endpoint.generation());
        assertEquals(64,decoded.capacity);
        assertEquals("my-server",SteamDedicatedNameProtocol.decode(encoded,now+5*60_000).name);
        assertThrows(IllegalArgumentException.class,()->SteamDedicatedNameProtocol.decode(encoded,now+SteamDedicatedNameProtocol.MAX_DIRECTORY_AGE_MILLIS+2000));
    }
    @Test void proofCannotBeReplayedForAnotherSessionOrNonce() throws Exception {
        var server=target(key,4,System.currentTimeMillis());
        byte[] nonce=new byte[32];new SecureRandom().nextBytes(nonce);
        byte[] proof=SteamDedicatedNameProtocol.proof(server,nonce,key);
        SteamDedicatedNameProtocol.verify(server,nonce,proof);
        byte[] other=nonce.clone();other[0]^=1;
        assertThrows(GeneralSecurityException.class,()->SteamDedicatedNameProtocol.verify(server,other,proof));
        assertThrows(GeneralSecurityException.class,()->SteamDedicatedNameProtocol.verify(target(key,5,server.updated),nonce,proof));
        proof[proof.length-1]^=1;
        assertThrows(GeneralSecurityException.class,()->SteamDedicatedNameProtocol.verify(server,nonce,proof));
    }
    @Test void duplicateNamesFailButRestartedSameServiceIsSelected() throws Exception {
        long now=System.currentTimeMillis();
        var old=target(key,4,now-5000);var current=target(key,5,now);
        assertSame(current,SteamDedicatedNameDirectory.choose(Arrays.asList(old,current),null,"1.21.1"));
        var foreign=target(KeyPairGenerator.getInstance("Ed25519").generateKeyPair(),6,now);
        assertThrows(java.io.IOException.class,()->SteamDedicatedNameDirectory.choose(Arrays.asList(current,foreign),null,"1.21.1"));
        assertSame(current,SteamDedicatedNameDirectory.choose(Arrays.asList(current,foreign),current.fingerprint,"1.21.1"));
        assertNull(SteamDedicatedNameDirectory.choose(Arrays.asList(foreign),current.fingerprint,"1.21.1"));
    }
}
