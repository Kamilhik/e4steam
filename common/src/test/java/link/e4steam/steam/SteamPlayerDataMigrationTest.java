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
    @Test void verifiedOnlineLoginRestoresOwnedOfflineProgressAndKeepsBackups() throws Exception {
        UUID offline=SteamPlayerDataMigration.offlineIdentity(world,STEAM,"Steve"),online=UUID.randomUUID();
        SteamPlayerDataMigration.recordIdentity(world,STEAM,offline);
        Path source=world.resolve("playerdata/"+offline+".dat"),target=world.resolve("playerdata/"+online+".dat");
        player(target,online,20);Files.setLastModifiedTime(target,FileTime.fromMillis(1));player(source,offline,7);
        Files.createDirectories(world.resolve("stats"));Files.writeString(world.resolve("stats/"+offline+".json"),"{\"progress\":42}");
        SteamPlayerDataMigration.migrateVerifiedOfflineIdentity(world,STEAM,"Steve",online);
        assertEquals(7,health(target,online));assertEquals(7,health(source,offline));
        assertEquals(20,health(world.resolve("e4steam-player-backups/"+offline+"-"+online+"/"+online+".dat"),online));
        assertEquals("{\"progress\":42}",Files.readString(world.resolve("stats/"+online+".json")));
        SteamPlayerDataMigration.recordIdentity(world,STEAM,online);player(target,online,15);
        SteamPlayerDataMigration.migrateVerifiedOfflineIdentity(world,STEAM,"Steve",online);
        assertEquals(15,health(target,online));
    }
    @Test void anotherSteamOwnersOfflineSaveCannotBeImported() throws Exception {
        UUID offline=SteamPlayerDataMigration.offlineIdentity(world,STEAM+1,"Steve"),online=UUID.randomUUID();
        SteamPlayerDataMigration.recordIdentity(world,STEAM,offline);
        player(world.resolve("playerdata/"+offline+".dat"),offline,7);Path target=world.resolve("playerdata/"+online+".dat");player(target,online,20);
        SteamPlayerDataMigration.migrateVerifiedOfflineIdentity(world,STEAM,"Steve",online);
        assertEquals(20,health(target,online));assertFalse(Files.exists(world.resolve("e4steam-player-backups")));
    }
    @Test void switchingVerifiedMinecraftAccountsDoesNotCopyTheirInventories() throws Exception {
        UUID offline=SteamPlayerDataMigration.offlineIdentity(world,STEAM,"Steve"),online=UUID.randomUUID();
        SteamPlayerDataMigration.recordIdentity(world,STEAM,UUID.randomUUID());
        player(world.resolve("playerdata/"+offline+".dat"),offline,7);Path target=world.resolve("playerdata/"+online+".dat");player(target,online,20);
        SteamPlayerDataMigration.migrateVerifiedOfflineIdentity(world,STEAM,"Steve",online);
        assertEquals(20,health(target,online));
    }
    @Test void newerVanillaSaveWinsOverAnOwnedOfflineAlias() throws Exception {
        UUID offline=SteamPlayerDataMigration.offlineIdentity(world,STEAM,"Steve"),online=UUID.randomUUID();
        SteamPlayerDataMigration.recordIdentity(world,STEAM,offline);
        Path source=world.resolve("playerdata/"+offline+".dat"),target=world.resolve("playerdata/"+online+".dat");
        player(source,offline,7);Files.setLastModifiedTime(source,FileTime.fromMillis(1));player(target,online,20);
        SteamPlayerDataMigration.migrateVerifiedOfflineIdentity(world,STEAM,"Steve",online);
        assertEquals(20,health(target,online));
    }
    @Test void migratingAliasesPreservesTheNewestSnapshotInsteadOfTheMigrationTime() throws Exception {
        UUID offline=SteamPlayerDataMigration.offlineIdentity(world,STEAM,"Steve"),online=UUID.randomUUID(),legacy=SteamMinecraftIdentity.legacyUuid(STEAM);
        SteamPlayerDataMigration.recordIdentity(world,STEAM,offline);
        Path old=world.resolve("playerdata/"+legacy+".dat"),source=world.resolve("playerdata/"+offline+".dat"),target=world.resolve("playerdata/"+online+".dat");
        player(old,legacy,3);Files.setLastModifiedTime(old,FileTime.fromMillis(100));player(source,offline,7);Files.setLastModifiedTime(source,FileTime.fromMillis(200));
        SteamPlayerDataMigration.migrateVerifiedOfflineIdentity(world,STEAM,"Steve",online);SteamPlayerDataMigration.migrate(world,STEAM,online);
        assertEquals(7,health(target,online));assertEquals(200,Files.getLastModifiedTime(target).toMillis());
    }
}
