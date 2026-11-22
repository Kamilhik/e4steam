package link.e4steam.worldtransfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class HandoffStorageTest {
    @TempDir Path root;
    Path world() throws Exception {Path p=Files.createDirectories(root.resolve("saves/world"));Files.writeString(p.resolve("level.dat"),"world-data");return p;}
    HandoffStorage storage() {return new HandoffStorage(root.resolve("saves"),root.resolve("config"));}
    @Test void confirmedSourceLeavesWorldListButKeepsARecoverableBackup() throws Exception {
        Path world=world();HandoffStorage storage=storage();var source=storage.captureSource(world);
        Path backup=storage.retireConfirmed(source,UUID.randomUUID());
        assertFalse(Files.exists(world));assertEquals("world-data",Files.readString(backup.resolve("level.dat")));
        assertFalse(backup.startsWith(root.resolve("saves")));
    }
    @Test void changedOrReopenedSourcesAreNeverMoved() throws Exception {
        Path world=world();var storage=storage();var source=storage.captureSource(world);
        Files.writeString(world.resolve("level.dat"),"updated-world");
        assertThrows(java.io.IOException.class,()->storage.retireConfirmed(source,UUID.randomUUID()));assertTrue(Files.exists(world));
        var fresh=storage.captureSource(world);
        try(var channel=FileChannel.open(world.resolve("session.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()) {
            assertThrows(java.io.IOException.class,()->storage.retireConfirmed(fresh,UUID.randomUUID()));
        }
        assertTrue(Files.exists(world));
    }
    @Test void addressRemainsLocalAndIsRestoredOnlyForTheMatchingOwnerAndWorld() throws Exception {
        Path world=world();UUID id=UUID.randomUUID();var storage=storage();
        String address="name=kamilchik\nworldId="+UUID.randomUUID()+"\n";
        Files.writeString(world.resolve("e4steam-premium-address.properties"),address);
        storage.rememberAddress(id,TransferEngineTest.HOST,world);
        Path returned=Files.createDirectories(root.resolve("returned"));
        assertFalse(storage.restoreAddress(id,TransferEngineTest.GUEST,returned));
        assertFalse(storage.restoreAddress(UUID.randomUUID(),TransferEngineTest.HOST,returned));
        assertTrue(storage.restoreAddress(id,TransferEngineTest.HOST,returned));
        assertEquals(address,Files.readString(returned.resolve("e4steam-premium-address.properties")));
    }
    @Test void cannotRetireAnArbitraryFolderOutsideSaves() throws Exception {
        Path outside=Files.createDirectories(root.resolve("other"));Files.writeString(outside.resolve("level.dat"),"keep");
        assertThrows(java.io.IOException.class,()->storage().captureSource(outside));assertTrue(Files.exists(outside));
    }
    @Test void deletesOnlyManagedBackupsAfterThreeFullDaysFromRetirement() throws Exception {
        Path world=world();var storage=storage();long retired=1_000_000;
        Path backup=storage.retireConfirmed(storage.captureSource(world),UUID.randomUUID(),retired);
        Path unmanaged=Files.createDirectories(root.resolve("e4steam-world-backups/unmanaged"));Files.writeString(unmanaged.resolve("level.dat"),"keep");
        assertEquals(0,storage.cleanupExpired(retired+HandoffStorage.RETENTION_MILLIS-1));assertTrue(Files.exists(backup));
        assertEquals(1,storage.cleanupExpired(retired+HandoffStorage.RETENTION_MILLIS));
        assertFalse(Files.exists(backup));assertTrue(Files.exists(unmanaged.resolve("level.dat")));
    }
    @Test void expiredBackupStaysWhileOpenAndIsDeletedAfterItCloses() throws Exception {
        Path world=world();var storage=storage();long retired=100;
        Path backup=storage.retireConfirmed(storage.captureSource(world),UUID.randomUUID(),retired);
        try(var channel=FileChannel.open(backup.resolve("session.lock"),StandardOpenOption.WRITE);var lock=channel.lock()) {
            assertEquals(0,storage.cleanupExpired(retired+HandoffStorage.RETENTION_MILLIS));assertTrue(Files.exists(backup));
        }
        assertEquals(1,storage.cleanupExpired(retired+HandoffStorage.RETENTION_MILLIS));
    }
}
