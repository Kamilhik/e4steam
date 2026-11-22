package link.e4steam.worldtransfer;

import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;

/** Local-only address settings and retirement of a confirmed, closed source save. */
public final class HandoffStorage {
    private static final String ADDRESS="e4steam-premium-address.properties";
    static final long RETENTION_MILLIS=3L*24*60*60*1000;
    private final Path saves,configuration;
    public HandoffStorage(Path saves,Path configuration) {this.saves=saves.toAbsolutePath().normalize();this.configuration=configuration;}
    public static final class Source {
        final Path world;final String stamp;
        Source(Path world,String stamp) {this.world=world;this.stamp=stamp;}
    }
    public Source captureSource(Path world) throws IOException {
        Path source=checkedSource(world);return new Source(source,stamp(source));
    }
    public void rememberAddress(UUID world,long owner,Path source) throws IOException {
        Path address=source.resolve(ADDRESS);
        if (!Files.exists(address,LinkOption.NOFOLLOW_LINKS)) return;
        if (!Files.isRegularFile(address,LinkOption.NOFOLLOW_LINKS) || Files.size(address)>4096) throw new IOException("INVALID_ADDRESS_SETTINGS");
        atomicWrite(addressFile(world,owner),Files.readAllBytes(address));
    }
    public boolean restoreAddress(UUID world,long owner,Path imported) throws IOException {
        Path saved=addressFile(world,owner);
        if (!Files.exists(saved,LinkOption.NOFOLLOW_LINKS)) return false;
        if (!Files.isRegularFile(saved,LinkOption.NOFOLLOW_LINKS) || Files.size(saved)>4096) throw new IOException("INVALID_ADDRESS_SETTINGS");
        atomicWrite(imported.resolve(ADDRESS),Files.readAllBytes(saved));return true;
    }
    /** Called only after the receiver has acknowledged a verified committed import. */
    public Path retireConfirmed(Source source,UUID transfer) throws IOException {
        return retireConfirmed(source,transfer,System.currentTimeMillis());
    }
    Path retireConfirmed(Source source,UUID transfer,long retiredAt) throws IOException {
        Path root=checkedSource(source.world);
        try (FileChannel channel=FileChannel.open(root.resolve("session.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)) {
            FileLock held;
            try {held=channel.tryLock();} catch (OverlappingFileLockException busy) {throw new IOException("WORLD_STILL_OPEN");}
            if (held==null) throw new IOException("WORLD_STILL_OPEN");
            try {if (!source.stamp.equals(stamp(root))) throw new IOException("WORLD_CHANGED_AFTER_TRANSFER");}
            finally {held.release();}
        }
        // Close the lock handle before renaming on Windows. The handoff UI stays locked until completion.
        Path backup=checkedBackup(true);
        Path destination=backup.resolve(transfer.toString());
        Files.move(root,destination,StandardCopyOption.ATOMIC_MOVE);
        Properties record=new Properties();record.setProperty("version","1");record.setProperty("retiredAt",Long.toString(retiredAt));
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();record.store(bytes,"Managed world backup; expires 3 days after retirement");
        atomicWrite(backup.resolve(transfer+".retired"),bytes.toByteArray());
        return destination;
    }
    public int cleanupExpired(long now) throws IOException {
        Path backup=checkedBackup(false);if(backup==null)return 0;
        int removed=0;
        try (DirectoryStream<Path> records=Files.newDirectoryStream(backup,"*.retired")) {
            for(Path record:records) {
                try {
                    if(!Files.isRegularFile(record,LinkOption.NOFOLLOW_LINKS) || Files.size(record)>2048)continue;
                    String name=record.getFileName().toString();UUID id=UUID.fromString(name.substring(0,name.length()-8));
                    if(!name.equals(id+".retired"))continue;
                    Properties p=new Properties();try(InputStream in=Files.newInputStream(record)){p.load(in);}
                    long retired=Long.parseLong(p.getProperty("retiredAt","-1"));
                    if(!"1".equals(p.getProperty("version")) || retired<0 || retired>now || now-retired<RETENTION_MILLIS)continue;
                    Path target=backup.resolve(id.toString());
                    if(!Files.exists(target,LinkOption.NOFOLLOW_LINKS)){Files.delete(record);continue;}
                    if(Files.isSymbolicLink(target) || !target.toRealPath().getParent().equals(backup.toRealPath())
                            || !Files.isRegularFile(target.resolve("level.dat"),LinkOption.NOFOLLOW_LINKS))continue;
                    try(FileChannel channel=FileChannel.open(target.resolve("session.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE,LinkOption.NOFOLLOW_LINKS)) {
                        FileLock lock;
                        try {lock=channel.tryLock();}catch(OverlappingFileLockException busy){continue;}
                        if(lock==null)continue;lock.release();
                    }
                    // Only a canonical UUID directory with our matching retirement record is eligible.
                    WorldArchive.removeStaging(target);Files.delete(record);removed++;
                } catch(IllegalArgumentException | IOException unsafeOrBusy) { /* Keep this backup and retry later. */ }
            }
        }
        return removed;
    }
    private Path checkedBackup(boolean create) throws IOException {
        Path profile=saves.getParent().toRealPath(),backup=profile.resolve("e4steam-world-backups");
        if(create)Files.createDirectories(backup);
        if(!Files.exists(backup,LinkOption.NOFOLLOW_LINKS))return null;
        if(Files.isSymbolicLink(backup) || !backup.toRealPath().getParent().equals(profile))throw new IOException("UNSAFE_BACKUP_ROOT");
        return backup;
    }
    private Path checkedSource(Path world) throws IOException {
        Path root=world.toAbsolutePath().normalize();
        if (!saves.equals(root.getParent()) || Files.isSymbolicLink(root) || !root.toRealPath().getParent().equals(saves.toRealPath())
                || !Files.isRegularFile(root.resolve("level.dat"),LinkOption.NOFOLLOW_LINKS)) throw new IOException("UNSAFE_SOURCE_WORLD");
        return root;
    }
    private Path addressFile(UUID world,long owner) {return configuration.resolve("owned-addresses").resolve(world+"-"+Long.toUnsignedString(owner)+".properties");}
    private static void atomicWrite(Path destination,byte[] bytes) throws IOException {
        Files.createDirectories(destination.getParent());
        Path temp=Files.createTempFile(destination.getParent(),".address-",".tmp");
        try {Files.write(temp,bytes);Files.move(temp,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        finally {Files.deleteIfExists(temp);}
    }
    private static String stamp(Path root) throws IOException {
        MessageDigest hash;
        try {hash=MessageDigest.getInstance("SHA-256");}catch(NoSuchAlgorithmException e){throw new IOException(e);}
        List<String> files=new ArrayList<>();
        Files.walkFileTree(root,EnumSet.noneOf(FileVisitOption.class),34,new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path path,BasicFileAttributes attrs) throws IOException {
                String name=root.relativize(path).toString();
                if (name.equals("session.lock")) return FileVisitResult.CONTINUE;
                if (!attrs.isRegularFile() || attrs.isSymbolicLink() || files.size()>=WorldArchive.MAX_FILES) throw new IOException("UNSAFE_SOURCE_WORLD");
                files.add(name+"\0"+attrs.size()+"\0"+attrs.lastModifiedTime());return FileVisitResult.CONTINUE;
            }
        });
        Collections.sort(files);for(String file:files){hash.update(file.getBytes(StandardCharsets.UTF_8));hash.update((byte)0);}
        Path level=root.resolve("level.dat");if(Files.size(level)>32*1024*1024)throw new IOException("LEVEL_DATA_TOO_LARGE");
        hash.update(Files.readAllBytes(level));return Base64.getEncoder().encodeToString(hash.digest());
    }
}
