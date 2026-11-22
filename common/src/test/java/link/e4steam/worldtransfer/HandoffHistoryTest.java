package link.e4steam.worldtransfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HandoffHistoryTest {
    @TempDir Path root;
    @Test void returnConsentIsBoundToWorldOwnerAndCustodianAndSurvivesRestart() throws Exception {
        UUID world=UUID.randomUUID();
        HandoffHistory history=new HandoffHistory(root);
        assertFalse(history.permits(world,TransferEngineTest.HOST,TransferEngineTest.GUEST));
        history.delegated(world,TransferEngineTest.HOST,TransferEngineTest.GUEST);
        history=new HandoffHistory(root);
        assertTrue(history.permits(world,TransferEngineTest.HOST,TransferEngineTest.GUEST));
        assertFalse(history.permits(UUID.randomUUID(),TransferEngineTest.HOST,TransferEngineTest.GUEST));
        assertFalse(history.permits(world,TransferEngineTest.GUEST,TransferEngineTest.HOST));
        assertFalse(history.permits(world,TransferEngineTest.HOST,TransferEngineTest.GUEST+1));
    }
    @Test void sharingSettingsRoundTripWithoutReusingTheOldLanPort() throws Exception {
        var sharing=new link.e4steam.lan.LanSettings.Snapshot(false,false,64,25565,"creative",true);
        var offer=new TransferProtocol.Offer(UUID.randomUUID(),UUID.randomUUID(),"World","1.21.1","a".repeat(64),TransferEngineTest.HOST,1,sharing);
        var decoded=TransferProtocol.offer(TransferProtocol.decode(TransferProtocol.offer(offer)));
        assertEquals(1,decoded.sharing.port());assertEquals(64,decoded.sharing.maxPlayers());
        assertEquals("creative",decoded.sharing.gameMode());assertTrue(decoded.sharing.allowCheats());
        assertFalse(decoded.sharing.onlineMode());assertFalse(decoded.sharing.pvp());
    }
}
