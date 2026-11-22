package link.e4steam.worldtransfer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class WorldPlayerDataTest {
    @TempDir Path root;
    static final UUID PLAYER=UUID.fromString("afc4ea22-5a4d-4b12-bec3-6b4ea2593cc7");
    static void level(Path world) throws IOException {
        level(world,PLAYER,19.0f);
    }
    static void level(Path world,UUID player,float health) throws IOException {
        level(world,player,health,null);
    }
    static void level(Path world,UUID player,float health,String item) throws IOException {
        Files.createDirectories(world);
        try (DataOutputStream out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(world.resolve("level.dat"))))) {
            out.writeByte(10);out.writeUTF("");out.writeByte(10);out.writeUTF("Data");
            out.writeByte(10);out.writeUTF("Player");out.writeByte(11);out.writeUTF("UUID");out.writeInt(4);
            out.writeLong(player.getMostSignificantBits());out.writeLong(player.getLeastSignificantBits());
            out.writeByte(5);out.writeUTF("Health");out.writeFloat(health);
            out.writeByte(9);out.writeUTF("Inventory");out.writeByte(10);out.writeInt(item==null?0:1);
            if (item!=null) {
                out.writeByte(1);out.writeUTF("Slot");out.writeByte(0);
                out.writeByte(8);out.writeUTF("id");out.writeUTF(item);
                out.writeByte(3);out.writeUTF("count");out.writeInt(17);out.writeByte(0);
            }
            out.writeByte(0);
            out.writeByte(8);out.writeUTF("LevelName");out.writeUTF("Test world");
            out.writeByte(4);out.writeUTF("LastPlayed");out.writeLong(42);out.writeByte(0);out.writeByte(0);
        }
    }
    @Test void legacyUuidLongTagsPreserveBothPlayerInventories() throws Exception {
        long host=TransferEngineTest.HOST,guest=TransferEngineTest.GUEST;
        UUID guestSteam=link.e4steam.steam.SteamMinecraftIdentity.uuid(guest);
        Files.createDirectories(root.resolve("playerdata"));
        legacyPlayer(root.resolve("level.dat"),PLAYER,19F,true);
        legacyPlayer(root.resolve("playerdata").resolve(guestSteam+".dat"),guestSteam,7F,false);
        WorldPlayerData.prepareImportedWorld(root,host,guest,PLAYER);
        UUID hostSteam=link.e4steam.steam.SteamMinecraftIdentity.uuid(host);
        assertEquals(19F,legacyHealth(root.resolve("playerdata").resolve(hostSteam+".dat"),hostSteam));
        assertEquals(7F,legacyHealth(root.resolve("playerdata").resolve(PLAYER+".dat"),PLAYER));
    }
    private static void legacyPlayer(Path path,UUID id,float health,boolean level) throws IOException {
        try(DataOutputStream out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(path)))) {
            out.writeByte(10);out.writeUTF("");
            if(level){out.writeByte(10);out.writeUTF("Data");out.writeByte(10);out.writeUTF("Player");}
            out.writeByte(4);out.writeUTF("UUIDMost");out.writeLong(id.getMostSignificantBits());
            out.writeByte(5);out.writeUTF("Health");out.writeFloat(health);
            out.writeByte(4);out.writeUTF("UUIDLeast");out.writeLong(id.getLeastSignificantBits());out.writeByte(0);
            if(level){out.writeByte(0);out.writeByte(0);}
        }
    }
    private static float legacyHealth(Path path,UUID id) throws IOException {
        try(DataInputStream in=new DataInputStream(new GZIPInputStream(Files.newInputStream(path)))) {
            assertEquals(10,in.readByte());in.readUTF();assertEquals(4,in.readByte());assertEquals("UUIDMost",in.readUTF());
            assertEquals(id.getMostSignificantBits(),in.readLong());assertEquals(5,in.readByte());assertEquals("Health",in.readUTF());float health=in.readFloat();
            assertEquals(4,in.readByte());assertEquals("UUIDLeast",in.readUTF());assertEquals(id.getLeastSignificantBits(),in.readLong());return health;
        }
    }
    @Test void preservesOldHostPlayerWithoutGivingTheirInventoryToNewHost() throws Exception {
        level(root);byte[] original=Files.readAllBytes(root.resolve("level.dat"));
        WorldPlayerData.prepareImportedWorld(root);
        Path player=root.resolve("playerdata").resolve(PLAYER+".dat");
        assertTrue(Files.exists(player));
        try (DataInputStream in=new DataInputStream(new GZIPInputStream(Files.newInputStream(player)))) {
            assertEquals(10,in.readUnsignedByte());assertEquals("",in.readUTF());
            assertEquals(11,in.readUnsignedByte());assertEquals("UUID",in.readUTF());assertEquals(4,in.readInt());
            assertEquals(PLAYER.getMostSignificantBits(),in.readLong());assertEquals(PLAYER.getLeastSignificantBits(),in.readLong());
            assertEquals(5,in.readUnsignedByte());assertEquals("Health",in.readUTF());assertEquals(19.0f,in.readFloat());
        }
        byte[] once=Files.readAllBytes(root.resolve("level.dat"));
        assertFalse(java.util.Arrays.equals(original,once));
        try (DataInputStream in=new DataInputStream(new GZIPInputStream(Files.newInputStream(root.resolve("level.dat"))))) {
            in.readByte();in.readUTF();in.readByte();assertEquals("Data",in.readUTF());
            assertEquals(8,in.readUnsignedByte());assertEquals("LevelName",in.readUTF());assertEquals("Test world",in.readUTF());
        }
        WorldPlayerData.prepareImportedWorld(root);
        assertArrayEquals(once,Files.readAllBytes(root.resolve("level.dat")));
    }
    @Test void malformedLevelFailsInsteadOfDiscardingPlayerData() throws Exception {
        Files.writeString(root.resolve("level.dat"),"bad");
        assertThrows(IOException.class,()->WorldPlayerData.prepareImportedWorld(root));
    }
    @Test void hostRoleChangeKeepsBothInventoriesEvenWithTheSameLauncherUuid() throws Exception {
        level(root);
        UUID hostSteam=link.e4steam.steam.SteamMinecraftIdentity.uuid(TransferEngineTest.HOST);
        UUID guestSteam=link.e4steam.steam.SteamMinecraftIdentity.uuid(TransferEngineTest.GUEST);
        Path guest=Files.createDirectory(root.resolve("guest-fixture"));
        level(guest,guestSteam,7.0f);WorldPlayerData.prepareImportedWorld(guest);
        Files.createDirectories(root.resolve("playerdata"));
        Files.copy(guest.resolve("playerdata/"+guestSteam+".dat"),root.resolve("playerdata/"+guestSteam+".dat"));
        Files.createDirectories(root.resolve("stats"));
        Files.writeString(root.resolve("stats/"+PLAYER+".json"),"old-host");
        Files.writeString(root.resolve("stats/"+guestSteam+".json"),"new-host");
        WorldPlayerData.prepareImportedWorld(root,TransferEngineTest.HOST,TransferEngineTest.GUEST,PLAYER);
        assertEquals(19.0f,health(root.resolve("playerdata/"+hostSteam+".dat"),hostSteam));
        assertEquals(7.0f,health(root.resolve("playerdata/"+PLAYER+".dat"),PLAYER));
        assertEquals("old-host",Files.readString(root.resolve("stats/"+hostSteam+".json")));
        assertEquals("new-host",Files.readString(root.resolve("stats/"+PLAYER+".json")));
    }
    @Test void roleChangeUsesTheLiveOfflineIdentityAndRestoresItOnReturn() throws Exception {
        long host=TransferEngineTest.HOST,guestSteam=TransferEngineTest.GUEST;
        UUID account=UUID.fromString("69f3b946-c5bb-4ab9-811a-1e3553e4c161");
        UUID played=UUID.fromString("fa2e340b-6217-3b52-951b-cb635843efd6");
        UUID legacy=link.e4steam.steam.SteamMinecraftIdentity.legacyUuid(guestSteam);
        level(root,PLAYER,19F,"minecraft:diamond");
        Path fresh=Files.createDirectory(root.resolve("fresh"));level(fresh,played,7F,"minecraft:emerald");prepareImportedFixture(fresh,root,played);
        Path stale=Files.createDirectory(root.resolve("stale"));level(stale,account,3F,"minecraft:apple");prepareImportedFixture(stale,root,account);
        Path staleLegacy=Files.createDirectory(root.resolve("stale-legacy"));level(staleLegacy,legacy,2F,"minecraft:stone");prepareImportedFixture(staleLegacy,root,legacy);
        Files.setLastModifiedTime(root.resolve("playerdata/"+account+".dat"),java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis()+86_400_000));
        link.e4steam.steam.SteamPlayerDataMigration.recordIdentity(root,guestSteam,played);
        WorldPlayerData.prepareImportedWorld(root,host,guestSteam,account,played);
        assertEquals("minecraft:emerald",inventory(root.resolve("playerdata/"+account+".dat")));
        assertEquals(7F,health(root.resolve("playerdata/"+account+".dat"),account));
        assertEquals(played,link.e4steam.steam.SteamPlayerDataMigration.savedIdentity(root,guestSteam));
        // The temporary host gains new items. Return must refresh the UUID used as a guest,
        // even though the receiving process has no cached UUID mapping for that Steam user.
        level(root,account,11F,"minecraft:netherite_ingot");
        WorldPlayerData.prepareImportedWorld(root,guestSteam,host,PLAYER);
        assertEquals("minecraft:netherite_ingot",inventory(root.resolve("playerdata/"+played+".dat")));
        assertEquals(11F,health(root.resolve("playerdata/"+played+".dat"),played));
        assertEquals("minecraft:diamond",inventory(root.resolve("playerdata/"+PLAYER+".dat")));
    }
    private static void prepareImportedFixture(Path source,Path destination,UUID player) throws IOException {
        WorldPlayerData.prepareImportedWorld(source);Files.createDirectories(destination.resolve("playerdata"));
        Files.copy(source.resolve("playerdata/"+player+".dat"),destination.resolve("playerdata/"+player+".dat"));
    }
    static float health(Path file,UUID expected) throws IOException {
        try (DataInputStream in=new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
            assertEquals(10,in.readUnsignedByte());in.readUTF();assertEquals(11,in.readUnsignedByte());in.readUTF();assertEquals(4,in.readInt());
            assertEquals(expected.getMostSignificantBits(),in.readLong());assertEquals(expected.getLeastSignificantBits(),in.readLong());
            assertEquals(5,in.readUnsignedByte());assertEquals("Health",in.readUTF());return in.readFloat();
        }
    }
    @Test void realInventoryItemsStayWithTheirOwnersDuringTransferAndReturn() throws Exception {
        UUID hostSteam=link.e4steam.steam.SteamMinecraftIdentity.uuid(TransferEngineTest.HOST);
        UUID guestSteam=link.e4steam.steam.SteamMinecraftIdentity.uuid(TransferEngineTest.GUEST);
        UUID guestLocal=UUID.fromString("73baefbd-a2bc-4dd3-a974-d33daae92891");
        level(root,PLAYER,19.0f,"minecraft:diamond");
        Path guest=Files.createDirectory(root.resolve("guest-fixture"));
        level(guest,guestSteam,7.0f,"minecraft:apple");
        WorldPlayerData.prepareImportedWorld(guest);
        Files.createDirectories(root.resolve("playerdata"));
        Files.copy(guest.resolve("playerdata/"+guestSteam+".dat"),root.resolve("playerdata/"+guestSteam+".dat"));
        WorldPlayerData.prepareImportedWorld(root,TransferEngineTest.HOST,TransferEngineTest.GUEST,guestLocal);
        assertEquals("minecraft:diamond",inventory(root.resolve("playerdata/"+hostSteam+".dat")));
        assertEquals("minecraft:apple",inventory(root.resolve("playerdata/"+guestLocal+".dat")));
        // The new host saves updated local-player data before returning the world.
        level(root,guestLocal,7.0f,"minecraft:emerald");
        WorldPlayerData.prepareImportedWorld(root,TransferEngineTest.GUEST,TransferEngineTest.HOST,PLAYER);
        assertEquals("minecraft:diamond",inventory(root.resolve("playerdata/"+PLAYER+".dat")));
        assertEquals("minecraft:emerald",inventory(root.resolve("playerdata/"+guestSteam+".dat")));
    }
    static String inventory(Path file) throws IOException {
        try (DataInputStream in=new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
            assertEquals(10,in.readUnsignedByte());in.readUTF();
            assertEquals(11,in.readUnsignedByte());in.readUTF();assertEquals(4,in.readInt());in.readLong();in.readLong();
            assertEquals(5,in.readUnsignedByte());in.readUTF();in.readFloat();
            assertEquals(9,in.readUnsignedByte());assertEquals("Inventory",in.readUTF());
            assertEquals(10,in.readUnsignedByte());assertEquals(1,in.readInt());
            assertEquals(1,in.readUnsignedByte());assertEquals("Slot",in.readUTF());assertEquals(0,in.readByte());
            assertEquals(8,in.readUnsignedByte());assertEquals("id",in.readUTF());String item=in.readUTF();
            assertEquals(3,in.readUnsignedByte());assertEquals("count",in.readUTF());assertEquals(17,in.readInt());
            return item;
        }
    }
}
