package link.e4steam.worldtransfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;

class TransferEngineTest {
    @TempDir Path root;
    static final long HOST=76561199469700509L,GUEST=76561199194856222L;
    static final String MODS="a".repeat(64);
    static void await(BooleanSupplier condition) throws Exception {
        long end=System.nanoTime()+Duration.ofSeconds(15).toNanos();
        while (!condition.getAsBoolean()) { if (System.nanoTime()>end) fail("Transfer did not reach expected state");Thread.sleep(10); }
    }
    final AtomicReference<TransferEngine> left=new AtomicReference<>(),right=new AtomicReference<>();
    final AtomicInteger snapshots=new AtomicInteger(),receivedData=new AtomicInteger();
    final AtomicBoolean congested=new AtomicBoolean(),corrupt=new AtomicBoolean();
    TransferEngine.Transport transport(AtomicReference<TransferEngine> remote,long local) {
        return new TransferEngine.Transport() {
            public CompletionStage<Boolean> send(long peer,byte[] bytes) {
                try {
                    TransferProtocol.Packet p=TransferProtocol.decode(bytes);
                    if (p.type==TransferProtocol.DATA) {
                        if (congested.get()) return CompletableFuture.completedFuture(false);
                        receivedData.incrementAndGet();
                        if (corrupt.get()) { bytes=bytes.clone();bytes[bytes.length-1]^=1; }
                    }
                    remote.get().packet(local,bytes);
                    return CompletableFuture.completedFuture(true);
                } catch (IOException e) { return CompletableFuture.failedFuture(e); }
            }
            public void release(long peer) { }
        };
    }
    TransferEngine sender() { TransferEngine engine=new TransferEngine(transport(right,HOST),root.resolve("saves-a"),root.resolve("cache-a"),()->HOST,"1.21.1",MODS);left.set(engine);return engine; }
    TransferEngine receiver(String mods) { TransferEngine engine=new TransferEngine(transport(left,GUEST),root.resolve("saves-b"),root.resolve("cache-b"),()->GUEST,"1.21.1",mods);right.set(engine);return engine; }
    TransferProtocol.Offer offer() { return new TransferProtocol.Offer(UUID.randomUUID(),UUID.randomUUID(),"My world","1.21.1",MODS,HOST,1); }
    TransferEngine.Source source() throws Exception {
        Path world=root.resolve("source");WorldPlayerDataTest.level(world);
        byte[] bytes=new byte[600_000];new Random(42).nextBytes(bytes);Files.write(world.resolve("region-fixture.mca"),bytes);
        return () -> CompletableFuture.supplyAsync(() -> {
            snapshots.incrementAndGet();
            try { return WorldArchive.snapshot(world,root.resolve("snapshots"),()->false); }
            catch (IOException e) { throw new CompletionException(e); }
        });
    }
    @Test void bulkSendingDoesNotWaitForTheMaintenanceTimerBetweenChunks() throws Exception {
        TransferEngine.PlayerImporter importer=(world,from,to)->WorldPlayerData.prepareImportedWorld(world);
        TransferEngine.ReceiptWriter receipt=(world,offer,from,to)->new TransferReceipt(offer.worldId,offer.origin,offer.generation).save(world);
        left.set(new TransferEngine(transport(right,HOST),root.resolve("host-saves"),root.resolve("host-cache"),()->HOST,"1.21.1",MODS,importer,receipt,1000));
        right.set(new TransferEngine(transport(left,GUEST),root.resolve("guest-saves"),root.resolve("guest-cache"),()->GUEST,"1.21.1",MODS,importer,receipt,1000));
        try {
            Path world=root.resolve("bulk-source");WorldPlayerDataTest.level(world);byte[] data=new byte[2*1024*1024];new Random(17).nextBytes(data);Files.write(world.resolve("bulk.dat"),data);
            right.get().listen(HOST);await(()->right.get().view().state==TransferEngine.State.LISTENING);
            left.get().offer(GUEST,offer(),()->CompletableFuture.completedFuture(bulkSnapshot(world)));
            await(()->right.get().view().state==TransferEngine.State.OFFERED);right.get().accept();
            await(()->right.get().view().state==TransferEngine.State.COMPLETE);
            await(()->left.get().view().state==TransferEngine.State.COMPLETE);
            assertArrayEquals(data,Files.readAllBytes(right.get().view().installed.resolve("bulk.dat")));
            assertTrue(receivedData.get()>64,"Fixture must exercise more than one transfer window");
        } finally {left.get().close();right.get().close();}
    }
    private WorldArchive.Snapshot bulkSnapshot(Path world) {
        try{return WorldArchive.snapshot(world,root.resolve("bulk-snapshot"),()->false);}
        catch(IOException failure){throw new CompletionException(failure);}
    }
    @Test void freeRecipientMustConsentThenGetsCompleteVerifiedWorld() throws Exception {
        try (TransferEngine a=sender();TransferEngine b=receiver(MODS)) {
            TransferEngine.Source source=source();
            b.listen(HOST);await(()->b.view().state==TransferEngine.State.LISTENING);
            a.offer(GUEST,offer(),source);await(()->b.view().state==TransferEngine.State.OFFERED);
            assertEquals(0,snapshots.get());assertEquals(0,receivedData.get());assertFalse(Files.exists(root.resolve("cache-b")));
            b.accept();await(()->a.view().state==TransferEngine.State.COMPLETE && b.view().state==TransferEngine.State.COMPLETE);
            assertTrue(Files.isRegularFile(b.view().installed.resolve("level.dat")));
            assertTrue(Files.isRegularFile(b.view().installed.resolve("playerdata/"+WorldPlayerDataTest.PLAYER+".dat")));
            assertArrayEquals(Files.readAllBytes(root.resolve("source/region-fixture.mca")),Files.readAllBytes(b.view().installed.resolve("region-fixture.mca")));
            assertTrue(Files.exists(root.resolve("source/level.dat")));
            assertEquals(HOST,TransferReceipt.load(b.view().installed).origin);
        }
    }
    @Test void incompatibleModpackIsRejectedBeforeSavingOrDownloading() throws Exception {
        try (TransferEngine a=sender();TransferEngine b=receiver("b".repeat(64))) {
            TransferEngine.Source source=source();
            b.listen(HOST);await(()->b.view().state==TransferEngine.State.LISTENING);
            a.offer(GUEST,offer(),source);await(()->b.view().state==TransferEngine.State.FAILED);
            assertEquals("MODPACK_MISMATCH",b.view().error);assertEquals(0,snapshots.get());assertEquals(0,receivedData.get());
        }
    }
    @Test void tamperedArchiveIsNeverInstalled() throws Exception {
        corrupt.set(true);
        try (TransferEngine a=sender();TransferEngine b=receiver(MODS)) {
            TransferEngine.Source source=source();
            b.listen(HOST);await(()->b.view().state==TransferEngine.State.LISTENING);
            a.offer(GUEST,offer(),source);await(()->b.view().state==TransferEngine.State.OFFERED);b.accept();
            await(()->b.view().state==TransferEngine.State.FAILED);
            assertNull(b.view().installed);
            try (var files=Files.list(root.resolve("saves-b"))) { assertEquals(0,files.count()); }
            assertTrue(Files.exists(root.resolve("source/level.dat")));
        }
    }
    @Test void cancellationWithNativeBackpressureRetainsOriginalAndRemovesPartialCopy() throws Exception {
        congested.set(true);
        try (TransferEngine a=sender();TransferEngine b=receiver(MODS)) {
            TransferEngine.Source source=source();
            b.listen(HOST);await(()->b.view().state==TransferEngine.State.LISTENING);
            a.offer(GUEST,offer(),source);await(()->b.view().state==TransferEngine.State.OFFERED);b.accept();
            await(()->b.view().state==TransferEngine.State.RECEIVING);b.cancel();
            await(()->b.view().state==TransferEngine.State.CANCELLED);
            assertTrue(Files.exists(root.resolve("source/level.dat")));
            try (var files=Files.list(root.resolve("cache-b"))) { assertEquals(0,files.count()); }
        }
    }
    @Test void unsolicitedPeerCannotOfferWorld() throws Exception {
        try (TransferEngine a=sender();TransferEngine b=receiver(MODS)) {
            b.packet(HOST,TransferProtocol.offer(offer()));Thread.sleep(60);
            assertEquals(TransferEngine.State.IDLE,b.view().state);assertFalse(Files.exists(root.resolve("saves-b")));
            b.listen(HOST);await(()->b.view().state==TransferEngine.State.LISTENING);
            b.packet(GUEST,TransferProtocol.offer(offer()));Thread.sleep(60);
            assertEquals(TransferEngine.State.LISTENING,b.view().state);
        }
    }
    @Test void automaticallyPreparedRecipientCanStartListeningAfterTheFirstOffer() throws Exception {
        try (TransferEngine a=sender();TransferEngine b=receiver(MODS)) {
            a.offer(GUEST,offer(),source());await(()->a.view().state==TransferEngine.State.OFFERING);
            Thread.sleep(200); // Initial offer is ignored until the receiver has authenticated its current host.
            assertEquals(TransferEngine.State.IDLE,b.view().state);
            b.listen(HOST);await(()->b.view().state==TransferEngine.State.OFFERED);
            assertEquals(0,snapshots.get());
            b.accept();await(()->a.view().state==TransferEngine.State.COMPLETE && b.view().state==TransferEngine.State.COMPLETE);
        }
    }
}
