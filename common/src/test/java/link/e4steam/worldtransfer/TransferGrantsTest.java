package link.e4steam.worldtransfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class TransferGrantsTest {
    @TempDir Path root;
    @Test void freeReturnGrantSurvivesRestartButIsBoundToSteamAndThisWorldFolder() throws Exception {
        TransferProtocol.Offer offer=new TransferProtocol.Offer(UUID.randomUUID(),UUID.randomUUID(),"World","1.21.1",
            "a".repeat(64),TransferEngineTest.HOST,1);
        Path world=Files.createDirectory(root.resolve("e4steam-"+offer.id));
        TransferGrants grants=new TransferGrants(root.resolve("config"));
        grants.imported(world,offer,TransferEngineTest.HOST,TransferEngineTest.GUEST);
        TransferReceipt receipt=new TransferGrants(root.resolve("config")).verified(world,TransferEngineTest.GUEST);
        assertNotNull(receipt);assertEquals(TransferEngineTest.HOST,receipt.origin);
        // IntegratedServer.getWorldPath(ROOT) may append "/." to the save directory.
        assertNotNull(grants.verified(world.resolve("."),TransferEngineTest.GUEST));
        assertNull(grants.verified(world,TransferEngineTest.HOST));
        Path another=Files.createDirectory(root.resolve("another-world"));
        Files.copy(world.resolve("e4steam-world-transfer.properties"),another.resolve("e4steam-world-transfer.properties"));
        assertNull(grants.verified(another,TransferEngineTest.GUEST));
        String saved=Files.readString(world.resolve("e4steam-world-transfer.properties"));
        Files.writeString(world.resolve("e4steam-world-transfer.properties"),saved.replace(Long.toString(TransferEngineTest.HOST),Long.toString(TransferEngineTest.GUEST)));
        assertNull(grants.verified(world,TransferEngineTest.GUEST));
    }
    @Test void plainOrForeignReceiptsDoNotEnableFreeSending() throws Exception {
        Path world=Files.createDirectory(root.resolve("world"));
        new TransferReceipt(UUID.randomUUID(),TransferEngineTest.HOST,1).save(world);
        TransferGrants grants=new TransferGrants(root.resolve("config"));
        assertNull(grants.verified(world,TransferEngineTest.GUEST));
        TransferProtocol.Offer offer=new TransferProtocol.Offer(UUID.randomUUID(),UUID.randomUUID(),"World","1.21.1",
            "a".repeat(64),TransferEngineTest.HOST,1);
        assertThrows(java.io.IOException.class,()->grants.imported(world,offer,TransferEngineTest.GUEST,TransferEngineTest.GUEST));
        assertFalse(Files.exists(root.resolve("config/receipt-secret.key")));
    }
}
