package link.e4steam.steam;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.FileTime;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import static org.junit.jupiter.api.Assertions.*;

class SteamPlayerDataMigrationTest {
    @TempDir Path world;
    private static final long STEAM=76561198012345678L;
    private static void player(Path path,UUID id,float health) throws IOException {
        Files.createDirectories(path.getParent());
        try(DataOutputStream out=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(path)))) {
            out.writeByte(10);out.writeUTF("");
            out.writeByte(11);out.writeUTF("UUID");out.writeInt(4);
            out.writeLong(id.getMostSignificantBits());out.writeLong(id.getLeastSignificantBits());
            out.writeByte(5);out.writeUTF("Health");out.writeFloat(health);out.writeByte(0);
        }
    }
    private static float health(Path path,UUID expected) throws IOException {
        try(DataInputStream in=new DataInputStream(new GZIPInputStream(Files.newInputStream(path)))) {
            assertEquals(10,in.readByte());in.readUTF();assertEquals(11,in.readByte());assertEquals("UUID",in.readUTF());assertEquals(4,in.readInt());
            assertEquals(expected.getMostSignificantBits(),in.readLong());assertEquals(expected.getLeastSignificantBits(),in.readLong());
            assertEquals(5,in.readByte());assertEquals("Health",in.readUTF());return in.readFloat();
        }
    }
    @Test void migratedDataKeepsVanillaUuidAndBackupsAndDoesNotOverwriteLaterProgress() throws Exception {
        UUID old=SteamMinecraftIdentity.legacyUuid(STEAM),canonical=UUID.randomUUID();
        Path source=world.resolve("playerdata/"+old+".dat"),target=world.resolve("playerdata/"+canonical+".dat");
        player(target,canonical,20);Files.setLastModifiedTime(target,FileTime.fromMillis(1));
        player(source,old,7);
        for(String folder:new String[]{"stats","advancements"}) {
            Files.createDirectories(world.resolve(folder));
            Files.write(world.resolve(folder+"/"+old+".json"),"{\"progress\":42}".getBytes(StandardCharsets.UTF_8));
        }
        SteamPlayerDataMigration.migrate(world,STEAM,canonical);
        assertEquals(7,health(target,canonical));assertEquals(7,health(source,old));
        Path backup=world.resolve("e4steam-player-backups/"+old+"-"+canonical);
        assertEquals(20,health(backup.resolve(canonical+".dat"),canonical));
        assertEquals("{\"progress\":42}",Files.readString(world.resolve("stats/"+canonical+".json")));
        assertTrue(Files.exists(world.resolve("advancements/"+canonical+".json")));
        player(target,canonical,15);
        SteamPlayerDataMigration.migrate(world,STEAM,canonical);
        assertEquals(15,health(target,canonical));
    }
    @Test void newerVanillaProgressIsNotReplacedByOldModSave() throws Exception {
        UUID old=SteamMinecraftIdentity.legacyUuid(STEAM),canonical=UUID.randomUUID();
        Path source=world.resolve("playerdata/"+old+".dat"),target=world.resolve("playerdata/"+canonical+".dat");
        player(source,old,3);Files.setLastModifiedTime(source,FileTime.fromMillis(1));player(target,canonical,19);
        SteamPlayerDataMigration.migrate(world,STEAM,canonical);
        assertEquals(19,health(target,canonical));assertEquals(3,health(source,old));
    }
    @Test void offlineIdentityMatchesVanillaButCannotBeClaimedByDifferentSteamUser() throws Exception {
        UUID vanilla=UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(StandardCharsets.UTF_8));
        assertEquals(vanilla,SteamPlayerDataMigration.offlineIdentity(world,STEAM,"Steve"));
        assertEquals(vanilla,SteamPlayerDataMigration.offlineIdentity(world,STEAM,"Steve"));
        assertThrows(IOException.class,()->SteamPlayerDataMigration.offlineIdentity(world,STEAM+1,"Steve"));
    }
    @Test void mojangVerifiedNameCanKeepExistingOfflineModeSaveWithoutChangingUuid() throws Exception {
        UUID id=UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(StandardCharsets.UTF_8));
        player(world.resolve("playerdata/"+id+".dat"),id,18);
        assertEquals(id,SteamPlayerDataMigration.offlineIdentity(world,STEAM,"Steve",true));
        assertEquals(18,health(world.resolve("playerdata/"+id+".dat"),id));
        assertThrows(IOException.class,()->SteamPlayerDataMigration.offlineIdentity(world,STEAM+1,"Steve",true));
    }
    @Test void preexistingOfflineSaveRequiresOwnerApprovalBeforeBinding() throws Exception {
        UUID id=UUID.nameUUIDFromBytes("OfflinePlayer:Steve".getBytes(StandardCharsets.UTF_8));
        player(world.resolve("playerdata/"+id+".dat"),id,18);
        assertThrows(IOException.class,()->SteamPlayerDataMigration.offlineIdentity(world,STEAM,"Steve"));
        assertEquals(18,health(world.resolve("playerdata/"+id+".dat"),id));
        Files.writeString(world.resolve("e4steam-offline-identities.properties"),id+"="+STEAM+"\n");
        assertEquals(id,SteamPlayerDataMigration.offlineIdentity(world,STEAM,"Steve"));
    }
}
