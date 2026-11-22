package link.e4steam.worldtransfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldArchiveTest {
    @TempDir Path temp;
    private Path world() throws IOException {
        Path world=Files.createDirectory(temp.resolve("world"));
        Files.writeString(world.resolve("level.dat"),"fixture-level");
        Files.createDirectories(world.resolve("region"));
        Files.write(world.resolve("region/r.0.0.mca"),new byte[16000]);
        Files.writeString(world.resolve("e4steam-premium-address.properties"),"name=private-owner");
        return world;
    }
    @Test void roundTripRetainsSourceAndNeverOverwritesExistingWorld() throws Exception {
        Path world=world();
        UUID id=UUID.randomUUID();
        try (WorldArchive.Snapshot zip=WorldArchive.snapshot(world,temp.resolve("cache"),()->false)) {
            Path imported=WorldArchive.install(zip.path,temp.resolve("saves"),id,zip.expanded,zip.sha256,()->false);
            assertEquals("fixture-level",Files.readString(imported.resolve("level.dat")));
            assertArrayEquals(Files.readAllBytes(world.resolve("region/r.0.0.mca")),Files.readAllBytes(imported.resolve("region/r.0.0.mca")));
            assertTrue(Files.exists(world.resolve("level.dat")));
            assertFalse(Files.exists(imported.resolve("session.lock")));
            assertFalse(Files.exists(imported.resolve("e4steam-premium-address.properties")));
            assertThrows(IOException.class,()->WorldArchive.install(zip.path,temp.resolve("saves"),id,zip.expanded,zip.sha256,()->false));
        }
    }
    @Test void preservesPlayerSaveFreshnessAcrossTransfer() throws Exception {
        Path world=world();Files.createDirectories(world.resolve("playerdata"));
        Path old=world.resolve("playerdata/old.dat"),current=world.resolve("playerdata/current.dat");
        Files.writeString(old,"old");Files.writeString(current,"new");
        Files.setLastModifiedTime(old,java.nio.file.attribute.FileTime.fromMillis(1_700_000_000_000L));
        Files.setLastModifiedTime(current,java.nio.file.attribute.FileTime.fromMillis(1_700_000_010_000L));
        try(WorldArchive.Snapshot zip=WorldArchive.snapshot(world,temp.resolve("time-cache"),()->false)) {
            Path imported=WorldArchive.install(zip.path,temp.resolve("saves"),UUID.randomUUID(),zip.expanded,zip.sha256,()->false);
            assertTrue(Files.getLastModifiedTime(imported.resolve("playerdata/current.dat")).compareTo(Files.getLastModifiedTime(imported.resolve("playerdata/old.dat")))>0);
            assertTrue(Math.abs(Files.getLastModifiedTime(imported.resolve("playerdata/old.dat")).toMillis()-1_700_000_000_000L)<2000);
        }
    }
    @Test void archiveProgressReportsActualBytesAndExcludesPrivateSettings() throws Exception {
        Path world=world();
        List<long[]> progress=new ArrayList<>();List<String> stages=new ArrayList<>();
        try (WorldArchive.Snapshot zip=WorldArchive.snapshot(world,temp.resolve("progress-cache"),()->false,(stage,done,total)-> {
            stages.add(stage);if(stage.equals("archiving"))progress.add(new long[]{done,total});
        })) {
            assertTrue(stages.contains("scanning"));assertEquals("hashing",stages.get(stages.size()-1));
            assertFalse(progress.isEmpty());assertEquals(0,progress.get(0)[0]);
            long previous=0;
            for(long[] point:progress) {assertTrue(point[0]>=previous);assertTrue(point[0]<=point[1]);previous=point[0];}
            assertEquals(zip.expanded,previous);
            assertEquals(zip.expanded,progress.get(progress.size()-1)[1]);
        }
    }
    @Test void refusesAnOpenWorld() throws Exception {
        Path world=world();
        try (FileChannel channel=FileChannel.open(world.resolve("session.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
             var lock=channel.lock()) {
            assertThrows(IOException.class,()->WorldArchive.snapshot(world,temp.resolve("cache"),()->false));
        }
    }
    @Test void refusesTraversalWindowsAliasesAndReservedNames() throws Exception {
        for (String name:List.of("../escape","/absolute","C:/world","x\\y","file.","x ","CON.dat","Lpt1.txt","x//y","x/../y"))
            assertThrows(IOException.class,()->WorldArchive.safeName(name),name);
    }
    @Test void rejectsCaseCollisionsAndExpandedSizeBombs() throws Exception {
        Path archive=temp.resolve("bad.zip");
        try (ZipOutputStream output=new ZipOutputStream(Files.newOutputStream(archive))) {
            output.putNextEntry(new ZipEntry("level.dat")); output.write(new byte[1000]); output.closeEntry();
            output.putNextEntry(new ZipEntry("LEVEL.dat")); output.write(1); output.closeEntry();
        }
        Path staging=Files.createDirectory(temp.resolve("staging"));
        assertThrows(IOException.class,()->WorldArchive.extract(archive,staging,100_000,100,()->false));
        WorldArchive.removeStaging(staging); Files.createDirectory(staging);
        assertThrows(IOException.class,()->WorldArchive.extract(archive,staging,10,100,()->false));
    }
    @Test void wrongHashAndCancellationDoNotExposePartialWorld() throws Exception {
        Path world=world();
        Path saves=Files.createDirectory(temp.resolve("saves"));
        try (WorldArchive.Snapshot zip=WorldArchive.snapshot(world,temp.resolve("cache"),()->false)) {
            assertThrows(IOException.class,()->WorldArchive.install(zip.path,saves,UUID.randomUUID(),zip.expanded,"0".repeat(64),()->false));
            assertThrows(IOException.class,()->WorldArchive.install(zip.path,saves,UUID.randomUUID(),zip.expanded,zip.sha256,()->true));
            assertThrows(IOException.class,()->WorldArchive.install(zip.path,saves,UUID.randomUUID(),zip.expanded,zip.sha256,()->false,
                path->{throw new IOException("finalizer failed");}));
            try (var entries=Files.list(saves)) { assertEquals(0,entries.count()); }
        }
    }
    @Test void cancelledSnapshotRemovesItsTemporaryArchive() throws Exception {
        Path world=world(), cache=temp.resolve("cache");
        assertThrows(IOException.class,()->WorldArchive.snapshot(world,cache,()->true));
        try (var entries=Files.list(cache)) { assertEquals(0,entries.count()); }
    }
}
