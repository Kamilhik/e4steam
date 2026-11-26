package link.e4steam.steam;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import link.e4steam.worldtransfer.WorldPlayerData;

/** World-owned identity bindings. Unknown offline users cannot claim another player's existing UUID. */
public final class SteamPlayerDataMigration {
    private SteamPlayerDataMigration(){}
    public static synchronized UUID offlineIdentity(Path world,long steam,String name) throws IOException {
        return offlineIdentity(world,steam,name,false);
    }
    /** verifiedName requires completed Mojang authentication, never a client-supplied hint. */
    public static synchronized UUID offlineIdentity(Path world,long steam,String name,boolean verifiedName) throws IOException {
        if(name==null||name.isEmpty()||name.length()>16||name.chars().anyMatch(c->c<32||c==127))throw new IOException("Invalid Minecraft name");
        UUID id=UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));
        Path bindings=world.resolve("e4steam-offline-identities.properties");Properties values=read(bindings);
        String owner=values.getProperty(id.toString());
        if(owner!=null&&!owner.equals(Long.toUnsignedString(steam)))throw new IOException("This Minecraft name belongs to another Steam account");
        if(owner==null) {
            if(!verifiedName && Files.exists(world.resolve("playerdata").resolve(id+".dat"),LinkOption.NOFOLLOW_LINKS))
                throw new IOException("Existing offline player data needs an owner-approved binding in e4steam-offline-identities.properties: "+id+"="+Long.toUnsignedString(steam));
            values.setProperty(id.toString(),Long.toUnsignedString(steam));write(bindings,values);
        }
        return id;
    }
    public static synchronized void migrate(Path world,long steam,UUID canonical) throws IOException {
        migrateFrom(world,steam,SteamMinecraftIdentity.legacyUuid(steam),canonical,Long.toUnsignedString(steam)+"."+canonical);
    }
    /** Only completed Mojang verification may restore this owned offline-name save. */
    public static synchronized void migrateVerifiedOfflineIdentity(Path world,long steam,String name,UUID canonical) throws IOException {
        if(name==null||name.isEmpty()||name.length()>16)throw new IOException("Invalid Minecraft name");
        UUID offline=UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));
        if(offline.equals(canonical)||!offline.equals(savedIdentity(world,steam)))return;
        Properties owners=read(world.resolve("e4steam-offline-identities.properties"));
        if(!Long.toUnsignedString(steam).equals(owners.getProperty(offline.toString())))return;
        migrateFrom(world,steam,offline,canonical,Long.toUnsignedString(steam)+"."+offline+"."+canonical);
    }
    private static void migrateFrom(Path world,long steam,UUID legacy,UUID canonical,String key) throws IOException {
        if(legacy.equals(canonical))return;
        Path marker=world.resolve("e4steam-identity-migrations.properties");Properties values=read(marker);
        if("1".equals(values.getProperty(key)))return;
        Path players=world.resolve("playerdata"),source=players.resolve(legacy+".dat"),target=players.resolve(canonical+".dat");
        if(!Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))return;
        if(Files.isSymbolicLink(players)||Files.isSymbolicLink(source)||Files.isSymbolicLink(target))throw new IOException("Unsafe player data path");
        boolean useLegacy="pending".equals(values.getProperty(key))||!Files.exists(target)||Files.getLastModifiedTime(source).compareTo(Files.getLastModifiedTime(target))>=0;
        if(useLegacy) {
            Path backup=world.resolve("e4steam-player-backups").resolve(legacy+"-"+canonical);Files.createDirectories(backup);
            if(Files.isSymbolicLink(backup)||Files.isSymbolicLink(backup.getParent()))throw new IOException("Unsafe player backup path");
            values.setProperty(key,"pending");write(marker,values);
            if(Files.exists(target)&&!Files.exists(backup.resolve(canonical+".dat")))Files.copy(target,backup.resolve(canonical+".dat"));
            if(!Files.exists(backup.resolve(legacy+".dat")))Files.copy(source,backup.resolve(legacy+".dat"));
            WorldPlayerData.copyPlayerIdentity(source,target,legacy,canonical);
            // A migration is not new gameplay. Preserve snapshot chronology so
            // another compatible old identity cannot overwrite newer progress.
            Files.setLastModifiedTime(target,Files.getLastModifiedTime(source));
            for(String folder:new String[]{"stats","advancements"}) {
                Path from=world.resolve(folder).resolve(legacy+".json"),to=world.resolve(folder).resolve(canonical+".json");
                if(!Files.isRegularFile(from,LinkOption.NOFOLLOW_LINKS))continue;
                if(Files.isSymbolicLink(from)||Files.isSymbolicLink(to)||Files.isSymbolicLink(from.getParent()))throw new IOException("Unsafe player progress path");
                if(Files.exists(to)&&!Files.exists(backup.resolve(folder+"-"+canonical+".json")))Files.copy(to,backup.resolve(folder+"-"+canonical+".json"));
                Path tmp=Files.createTempFile(to.getParent(),".identity-",".tmp");
                try {Files.copy(from,tmp,StandardCopyOption.REPLACE_EXISTING);Files.move(tmp,to,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                finally{Files.deleteIfExists(tmp);}
            }
        }
        values.setProperty(key,"1");write(marker,values);
    }
    public static synchronized UUID savedIdentity(Path world,long steam) throws IOException {
        String value=read(world.resolve("e4steam-player-identities.properties")).getProperty(Long.toUnsignedString(steam));
        if(value==null)return null;
        try{return UUID.fromString(value);}catch(IllegalArgumentException invalid){throw new IOException("Invalid saved player identity",invalid);}
    }
    public static synchronized void recordIdentity(Path world,long steam,UUID id) throws IOException {
        if(steam==0 || id==null)throw new IOException("Missing authenticated player identity");
        Path file=world.resolve("e4steam-player-identities.properties");Properties values=read(file);
        String key=Long.toUnsignedString(steam);if(id.toString().equals(values.getProperty(key)))return;
        values.setProperty(key,id.toString());write(file,values);
    }
    private static Properties read(Path file) throws IOException {
        Properties values=new Properties();if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return values;
        if(Files.isSymbolicLink(file)||!Files.isRegularFile(file)||Files.size(file)>1024*1024)throw new IOException("Invalid player identity file");
        try(Reader input=Files.newBufferedReader(file,StandardCharsets.UTF_8)){values.load(input);}return values;
    }
    private static void write(Path file,Properties values) throws IOException {
        Path tmp=Files.createTempFile(file.getParent(),".identities-",".tmp");
        try {try(Writer output=Files.newBufferedWriter(tmp,StandardCharsets.UTF_8)){values.store(output,"Server-owned Minecraft/Steam identity compatibility");}
            Files.move(tmp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally {Files.deleteIfExists(tmp);}
    }
}
