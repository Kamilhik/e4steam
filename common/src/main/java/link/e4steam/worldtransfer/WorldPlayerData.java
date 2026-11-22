package link.e4steam.worldtransfer;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/** Preserve the old host's inventory without making it the new singleplayer host's inventory. */
public final class WorldPlayerData {
    private static final int MAX_NBT=32*1024*1024;
    private WorldPlayerData() { }

    public static void prepareImportedWorld(Path world) throws IOException {
        normalize(world.resolve("level.dat"),world,true);
        Path old=world.resolve("level.dat_old");
        if (Files.isRegularFile(old,LinkOption.NOFOLLOW_LINKS)) normalize(old,world,false);
    }

    public static void prepareImportedWorld(Path world,long oldHostSteam,long newHostSteam,UUID localProfile) throws IOException {
        prepareImportedWorld(world,oldHostSteam,newHostSteam,localProfile,null);
    }
    public static void prepareImportedWorld(Path world,long oldHostSteam,long newHostSteam,UUID localProfile,UUID joinedProfile) throws IOException {
        byte[] level=read(world.resolve("level.dat"));Parser original=new Parser(level);original.root();
        UUID previousHost=original.playerId;
        normalize(world.resolve("level.dat"),world,true,oldHostSteam);
        Path old=world.resolve("level.dat_old");
        if(Files.isRegularFile(old,LinkOption.NOFOLLOW_LINKS))normalize(old,world,false,oldHostSteam);
        UUID mapped=link.e4steam.steam.SteamPlayerDataMigration.savedIdentity(world,newHostSteam);
        UUID selected=joinedProfile!=null?joinedProfile:mapped;
        if(selected!=null) {
            if(selected.equals(previousHost) || !Files.isRegularFile(playerFile(world,selected),LinkOption.NOFOLLOW_LINKS))
                throw new IOException("RECIPIENT_PLAYER_DATA_MISSING");
        } else {
            UUID steamPlayer=link.e4steam.steam.SteamMinecraftIdentity.uuid(newHostSteam);
            UUID legacy=link.e4steam.steam.SteamMinecraftIdentity.legacyUuid(newHostSteam);
            for(UUID candidate:new UUID[]{steamPlayer,legacy,localProfile}) {
                if(candidate==null || candidate.equals(previousHost))continue;
                Path data=playerFile(world,candidate);
                if(Files.isRegularFile(data,LinkOption.NOFOLLOW_LINKS) && (selected==null ||
                        Files.getLastModifiedTime(data).compareTo(Files.getLastModifiedTime(playerFile(world,selected)))>0))selected=candidate;
            }
        }
        if(selected==null)return;
        link.e4steam.steam.SteamPlayerDataMigration.recordIdentity(world,newHostSteam,selected);
        if(localProfile.equals(selected))return;
        Path destination=playerFile(world,localProfile);
        if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS)) {
            Path backups=world.resolve("e4steam-player-backups");
            if(Files.isSymbolicLink(backups))throw new IOException("UNSAFE_PLAYER_DATA");
            Path backup=backups.resolve("role-"+selected+"-"+localProfile);
            if(Files.isSymbolicLink(backup))throw new IOException("UNSAFE_PLAYER_DATA");
            Files.createDirectories(backup);
            Path saved=backup.resolve(localProfile+".dat");
            if(!Files.exists(saved,LinkOption.NOFOLLOW_LINKS))Files.copy(destination,saved);
        }
        copyPlayerIdentity(playerFile(world,selected),destination,selected,localProfile);
        copyProgress(world,selected,localProfile);
    }
    private static Path playerFile(Path world,UUID id) {return world.resolve("playerdata").resolve(id+".dat");}

    private static void normalize(Path level,Path world,boolean savePlayer) throws IOException {
        normalize(level,world,savePlayer,0);
    }

    private static byte[] read(Path file) throws IOException {
        try (InputStream input=new GZIPInputStream(Files.newInputStream(file)); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];
            for (int n;(n=input.read(buffer))!=-1;) {
                if (n>MAX_NBT-output.size()) throw new IOException("LEVEL_DATA_TOO_LARGE");
                output.write(buffer,0,n);
            }
            return output.toByteArray();
        }
    }
    private static void write(Path file,byte[] bytes) throws IOException {
        try (OutputStream output=new GZIPOutputStream(Files.newOutputStream(file))) {output.write(bytes);}
    }
    /** Copy a known player's existing state, changing only its UUID tags. Source remains recoverable. */
    public static void copyPlayerIdentity(Path source,Path destination,UUID expected,UUID replacement) throws IOException {
        if(Files.isSymbolicLink(source)||Files.isSymbolicLink(destination))throw new IOException("UNSAFE_PLAYER_DATA");
        byte[] bytes=read(source);Parser parsed=new Parser(bytes);parsed.root();
        if(!expected.equals(parsed.playerId))throw new IOException("PLAYER_ID_MISMATCH");
        parsed.replaceUuid(bytes,replacement,0);
        Files.createDirectories(destination.getParent());Path tmp=Files.createTempFile(destination.getParent(),".identity-",".tmp");
        try {write(tmp,bytes);Files.move(tmp,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally {Files.deleteIfExists(tmp);}
    }
    private static void normalize(Path level,Path world,boolean savePlayer,long oldHostSteam) throws IOException {
        byte[] bytes=read(level);
        Parser parser=new Parser(bytes);
        parser.root();
        if (parser.playerStart<0) return;
        if (parser.playerId==null) throw new IOException("PLAYER_ID_MISSING");
        if (savePlayer) {
            Path players=world.resolve("playerdata");
            Files.createDirectories(players);
            try (DataOutputStream output=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(players.resolve(parser.playerId+".dat"))))) {
                output.writeByte(10); output.writeUTF("");
                output.write(bytes,parser.playerPayload,parser.playerEnd-parser.playerPayload);
            }
            if (TransferProtocol.validSteam(oldHostSteam)) {
                UUID steamId=link.e4steam.steam.SteamPlayerDataMigration.savedIdentity(world,oldHostSteam);
                if(steamId==null)steamId=link.e4steam.steam.SteamMinecraftIdentity.uuid(oldHostSteam);
                if (!steamId.equals(parser.playerId)) {
                    byte[] player=Arrays.copyOfRange(bytes,parser.playerPayload,parser.playerEnd);
                    parser.replaceUuid(player,steamId,parser.playerPayload);
                    try (DataOutputStream output=new DataOutputStream(new GZIPOutputStream(Files.newOutputStream(players.resolve(steamId+".dat"))))) {
                        output.writeByte(10);output.writeUTF("");output.write(player);
                    }
                    copyProgress(world,parser.playerId,steamId);
                }
            }
        }
        if (parser.lastPlayedOffset>=0) ByteBuffer.wrap(bytes,parser.lastPlayedOffset,8).putLong(System.currentTimeMillis());
        try (OutputStream output=new GZIPOutputStream(Files.newOutputStream(level))) {
            output.write(bytes,0,parser.playerStart);
            output.write(bytes,parser.playerEnd,bytes.length-parser.playerEnd);
        }
    }

    private static void copyProgress(Path world,UUID from,UUID to) throws IOException {
        for (String folder:new String[]{"stats","advancements"}) {
            Path source=world.resolve(folder).resolve(from+".json");
            if (Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS))
                Files.copy(source,world.resolve(folder).resolve(to+".json"),StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class Parser {
        final byte[] bytes;
        final ByteArrayInputStream source;
        final DataInputStream input;
        int playerStart=-1,playerEnd,playerPayload;
        UUID playerId;
        int uuidOffset=-1,lastPlayedOffset=-1,mostOffset=-1,leastOffset=-1;
        long most,least;
        boolean hasUuid() {return uuidOffset>=0 || mostOffset>=0 && leastOffset>=0;}
        void replaceUuid(byte[] target,UUID id,int base) throws IOException {
            if (!hasUuid()) throw new IOException("PLAYER_ID_MISSING");
            int a=(uuidOffset>=0?uuidOffset:mostOffset)-base;
            int b=(uuidOffset>=0?uuidOffset+8:leastOffset)-base;
            if(a<0||b<0||a+8>target.length||b+8>target.length)throw new IOException("INVALID_PLAYER_NBT");
            ByteBuffer.wrap(target,a,8).putLong(id.getMostSignificantBits());
            ByteBuffer.wrap(target,b,8).putLong(id.getLeastSignificantBits());
        }
        Parser(byte[] bytes) { this.bytes=bytes; source=new ByteArrayInputStream(bytes); input=new DataInputStream(source); }
        int position() { return bytes.length-source.available(); }
        void root() throws IOException {
            if (input.readUnsignedByte()!=10) throw new IOException("INVALID_LEVEL_NBT");
            input.readUTF(); compound(0,"");
            if (source.available()!=0) throw new IOException("TRAILING_LEVEL_NBT");
        }
        void compound(int depth,String path) throws IOException {
            if (depth>64) throw new IOException("NBT_TOO_DEEP");
            Set<String> names=new HashSet<>();
            for (;;) {
                int start=position();
                int type=input.readUnsignedByte();
                if (type==0) return;
                String name=input.readUTF();
                if (!names.add(name)) throw new IOException("DUPLICATE_NBT_TAG");
                String child=path.isEmpty()?name:path+"/"+name;
                int payload=position();
                if (child.equals("Data/Player") && type!=10) throw new IOException("INVALID_PLAYER_NBT");
                skip(type,depth+1,child);
                if (child.equals("Data/Player")) { playerStart=start; playerPayload=payload; playerEnd=position(); }
                if ((child.equals("Data/Player/UUID") || child.equals("UUID")) && type==11 && position()-payload==20) {
                    ByteBuffer uuid=ByteBuffer.wrap(bytes,payload+4,16);
                    uuidOffset=payload+4;
                    playerId=new UUID(uuid.getLong(),uuid.getLong());
                }
                if (type==4 && (child.equals("Data/Player/UUIDMost") || child.equals("UUIDMost"))) {
                    mostOffset=payload;most=ByteBuffer.wrap(bytes,payload,8).getLong();
                }
                if (type==4 && (child.equals("Data/Player/UUIDLeast") || child.equals("UUIDLeast"))) {
                    leastOffset=payload;least=ByteBuffer.wrap(bytes,payload,8).getLong();
                }
                if (uuidOffset<0 && mostOffset>=0 && leastOffset>=0) playerId=new UUID(most,least);
                if (child.equals("Data/LastPlayed") && type==4) lastPlayedOffset=payload;
            }
        }
        void skip(int type,int depth,String path) throws IOException {
            if (depth>64) throw new IOException("NBT_TOO_DEEP");
            switch (type) {
                case 1: consume(1); break;
                case 2: consume(2); break;
                case 3: case 5: consume(4); break;
                case 4: case 6: consume(8); break;
                case 7: consume(arrayLength(1)); break;
                case 8: input.readUTF(); break;
                case 9:
                    int element=input.readUnsignedByte(),count=input.readInt();
                    if (count<0 || count>MAX_NBT || element>12 || element==0 && count>0) throw new IOException("INVALID_NBT_LIST");
                    for (int i=0;i<count;i++) skip(element,depth+1,path+"/[]");
                    break;
                case 10: compound(depth,path); break;
                case 11: consume(arrayLength(4)); break;
                case 12: consume(arrayLength(8)); break;
                default: throw new IOException("UNKNOWN_NBT_TAG");
            }
        }
        int arrayLength(int width) throws IOException {
            int count=input.readInt();
            if (count<0 || count>source.available()/width) throw new IOException("INVALID_NBT_ARRAY");
            return count*width;
        }
        void consume(int size) throws IOException {
            if (size>source.available() || input.skipBytes(size)!=size) throw new EOFException("TRUNCATED_NBT");
        }
    }
}
