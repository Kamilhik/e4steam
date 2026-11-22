package link.e4steam.worldtransfer;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.zip.*;

/** Bounded, closed-world snapshots and transactional imports. Never overwrites a saved world. */
public final class WorldArchive {
    public static final long MAX_ARCHIVE = 2L * 1024 * 1024 * 1024;
    public static final long MAX_EXPANDED = 8L * 1024 * 1024 * 1024;
    public static final int MAX_FILES = 200_000;
    private static final long SPACE_RESERVE = 128L * 1024 * 1024;
    private static final Set<String> EXCLUDED = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("session.lock", "e4steam-premium-address.properties", "e4steam-world-transfer.properties")));
    private WorldArchive() { }
    @FunctionalInterface public interface Progress {void update(String stage,long done,long total);}

    public static final class Snapshot implements AutoCloseable {
        public final Path path;
        public final long size, expanded;
        public final String sha256;
        Snapshot(Path path, long size, long expanded, String sha256) {
            this.path=path; this.size=size; this.expanded=expanded; this.sha256=sha256;
        }
        @Override public void close() throws IOException { Files.deleteIfExists(path); }
    }

    /** Minecraft owns session.lock until its save has closed; holding it prevents a concurrent reopen. */
    public static Snapshot snapshot(Path world, Path cache, BooleanSupplier cancelled) throws IOException {
        return snapshot(world,cache,cancelled,(stage,done,total)->{});
    }
    public static Snapshot snapshot(Path world, Path cache, BooleanSupplier cancelled,Progress progress) throws IOException {
        Path root=world.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)
                || !Files.isRegularFile(root.resolve("level.dat"), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("WORLD_NOT_FOUND");
        if (cache.toAbsolutePath().normalize().startsWith(root)) throw new IOException("UNSAFE_CACHE");
        Files.createDirectories(cache);
        if (Files.isSymbolicLink(cache)) throw new IOException("UNSAFE_CACHE");
        Path lockPath=root.resolve("session.lock");
        if (Files.isSymbolicLink(lockPath)) throw new IOException("UNSAFE_LOCK");
        try (FileChannel channel=FileChannel.open(lockPath, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
            FileLock lock;
            try { lock=channel.tryLock(); }
            catch (OverlappingFileLockException busy) { throw new IOException("WORLD_STILL_OPEN"); }
            if (lock==null) throw new IOException("WORLD_STILL_OPEN");
            try (FileLock held=lock) { return pack(root, cache, cancelled,progress); }
        }
    }

    private static Snapshot pack(Path root, Path cache, BooleanSupplier cancelled,Progress progress) throws IOException {
        final long[] expected={0};final int[] scanned={0};
        progress.update("scanning",0,0);
        Files.walkFileTree(root,EnumSet.noneOf(FileVisitOption.class),34,new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path dir,BasicFileAttributes attrs) throws IOException {
                checkCancelled(cancelled);
                if (++scanned[0]>MAX_FILES || attrs.isSymbolicLink()) throw new IOException("WORLD_TOO_LARGE");
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs) throws IOException {
                checkCancelled(cancelled);
                String name=root.relativize(file).toString().replace(File.separatorChar,'/');
                if (EXCLUDED.contains(name)) return FileVisitResult.CONTINUE;
                if (!attrs.isRegularFile() || attrs.isSymbolicLink()) throw new IOException("WORLD_LINK_NOT_ALLOWED");
                if (++scanned[0]>MAX_FILES || attrs.size()>MAX_EXPANDED-expected[0]) throw new IOException("WORLD_TOO_LARGE");
                expected[0]+=attrs.size();progress.update("scanning",scanned[0],0);return FileVisitResult.CONTINUE;
            }
        });
        progress.update("archiving",0,expected[0]);
        Path result=Files.createTempFile(cache,"world-", ".zip.part");
        boolean success=false;
        final long[] expanded={0};
        final int[] count={0};
        final Set<String> packedNames=new HashSet<>();
        Path realRoot=root.toRealPath();
        try {
            try (OutputStream limited=new LimitedOutput(Files.newOutputStream(result), MAX_ARCHIVE);
                 ZipOutputStream zip=new ZipOutputStream(new BufferedOutputStream(limited))) {
                zip.setLevel(Deflater.BEST_SPEED);
                Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 34, new SimpleFileVisitor<Path>() {
                    @Override public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        checkCancelled(cancelled);
                        if (attrs.isSymbolicLink() || !dir.toRealPath().startsWith(realRoot)) throw new IOException("WORLD_LINK_NOT_ALLOWED");
                        if (!dir.equals(root)) {
                            String name=root.relativize(dir).toString().replace(File.separatorChar,'/');
                            safeName(name);
                            if (++count[0]>MAX_FILES || !packedNames.add(name.toLowerCase(Locale.ROOT))) throw new IOException("WORLD_TOO_LARGE");
                            zip.putNextEntry(new ZipEntry(name+"/"));zip.closeEntry();
                        }
                        return FileVisitResult.CONTINUE;
                    }
                    @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        checkCancelled(cancelled);
                        String name=root.relativize(file).toString().replace(File.separatorChar,'/');
                        if (EXCLUDED.contains(name)) return FileVisitResult.CONTINUE;
                        safeName(name);
                        if (!packedNames.add(name.toLowerCase(Locale.ROOT))) throw new IOException("UNSAFE_ARCHIVE_ENTRY");
                        if (!attrs.isRegularFile() || attrs.isSymbolicLink() || !file.toRealPath().startsWith(realRoot))
                            throw new IOException("WORLD_LINK_NOT_ALLOWED");
                        if (++count[0]>MAX_FILES || attrs.size()>MAX_EXPANDED-expanded[0]) throw new IOException("WORLD_TOO_LARGE");
                        ZipEntry entry=new ZipEntry(name);
                        entry.setLastModifiedTime(attrs.lastModifiedTime());
                        zip.putNextEntry(entry);
                        long actual=0;
                        try (InputStream input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) {
                            byte[] buffer=new byte[64*1024];
                            for (int n;(n=input.read(buffer))!=-1;) {
                                checkCancelled(cancelled);
                                if (n>MAX_EXPANDED-expanded[0]) throw new IOException("WORLD_TOO_LARGE");
                                zip.write(buffer,0,n); actual+=n; expanded[0]+=n;
                                progress.update("archiving",expanded[0],expected[0]);
                            }
                        }
                        if (actual!=attrs.size()) throw new IOException("WORLD_CHANGED_DURING_SAVE");
                        BasicFileAttributes after=Files.readAttributes(file,BasicFileAttributes.class,LinkOption.NOFOLLOW_LINKS);
                        if (!attrs.lastModifiedTime().equals(after.lastModifiedTime()) || after.size()!=actual)
                            throw new IOException("WORLD_CHANGED_DURING_SAVE");
                        zip.closeEntry();
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            progress.update("hashing",0,0);
            String hash=sha256(result,cancelled);
            success=true;
            return new Snapshot(result,Files.size(result),expanded[0],hash);
        } finally { if (!success) Files.deleteIfExists(result); }
    }

    public static Path install(Path archive, Path saves, UUID transferId, long expandedLimit,
                               String expectedHash, BooleanSupplier cancelled) throws IOException {
        return install(archive,saves,transferId,expandedLimit,expectedHash,cancelled, path -> { });
    }

    /** The finalizer may normalize level.dat only inside the newly created staging directory. */
    public static Path install(Path archive, Path saves, UUID transferId, long expandedLimit,
                               String expectedHash, BooleanSupplier cancelled, Finalizer finalizer) throws IOException {
        if (expandedLimit<1 || expandedLimit>MAX_EXPANDED || Files.size(archive)>MAX_ARCHIVE
                || !expectedHash.matches("[0-9a-f]{64}") || !MessageDigest.isEqual(
                        expectedHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                        sha256(archive,cancelled).getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
            throw new IOException("ARCHIVE_HASH_MISMATCH");
        Files.createDirectories(saves);
        Path root=saves.toRealPath();
        if (Files.isSymbolicLink(saves) || Files.getFileStore(root).getUsableSpace()<expandedLimit+SPACE_RESERVE)
            throw new IOException("NOT_ENOUGH_SPACE");
        Path target=root.resolve("e4steam-"+transferId);
        if (Files.exists(target,LinkOption.NOFOLLOW_LINKS)) throw new IOException("WORLD_ALREADY_IMPORTED");
        // This container has no level.dat. Vanilla must not see a half-imported
        // directory as a playable world while extracting its nested staging copy.
        Path container=root.resolve(".e4steam-transfers");
        Files.createDirectories(container);
        if (Files.isSymbolicLink(container) || !container.toRealPath().startsWith(root)) throw new IOException("UNSAFE_SAVE_ROOT");
        Path staging=Files.createTempDirectory(container,"incoming-");
        boolean committed=false;
        try {
            extract(archive,staging,expandedLimit,MAX_FILES,cancelled);
            if (!Files.isRegularFile(staging.resolve("level.dat"),LinkOption.NOFOLLOW_LINKS))
                throw new IOException("MISSING_LEVEL_DATA");
            checkCancelled(cancelled);
            finalizer.prepare(staging);
            checkCancelled(cancelled);
            // Same-filesystem atomic rename is required. Never expose a half-imported world.
            Files.move(staging,target,StandardCopyOption.ATOMIC_MOVE);
            committed=true;
            return target;
        } finally {
            if (!committed) removeStaging(staging);
            try { Files.deleteIfExists(container); } catch (DirectoryNotEmptyException anotherTransfer) { }
        }
    }

    static void extract(Path archive, Path staging, long limit, int fileLimit, BooleanSupplier cancelled) throws IOException {
        long total=0;
        Set<String> seen=new HashSet<>();
        Map<String,String> casing=new HashMap<>();
        validateCentralDirectory(archive);
        try (ZipFile zip=new ZipFile(archive.toFile())) {
            Enumeration<? extends ZipEntry> entries=zip.entries();
            while (entries.hasMoreElements()) {
                checkCancelled(cancelled);
                ZipEntry entry=entries.nextElement();
                String name=entry.getName();
                if (entry.isDirectory()) name=name.substring(0,name.length()-1);
                safeName(name);
                String prefix="";
                for (String component:name.split("/")) {
                    prefix=prefix.isEmpty()?component:prefix+"/"+component;
                    String old=casing.putIfAbsent(prefix.toLowerCase(Locale.ROOT),prefix);
                    if (casing.size()>MAX_FILES) throw new IOException("ARCHIVE_TOO_LARGE");
                    if (old!=null && !old.equals(prefix)) throw new IOException("UNSAFE_ARCHIVE_ENTRY");
                }
                // Also reject case collisions on Linux, because the world may later move to Windows.
                if (!seen.add(name.toLowerCase(Locale.ROOT)) || seen.size()>fileLimit || EXCLUDED.contains(name))
                    throw new IOException("UNSAFE_ARCHIVE_ENTRY");
                Path path=staging.resolve(name).normalize();
                if (!path.startsWith(staging) || entry.getSize()<0 || entry.getSize()>limit-total)
                    throw new IOException("ARCHIVE_TOO_LARGE");
                if (entry.isDirectory()) { Files.createDirectories(path); continue; }
                Files.createDirectories(path.getParent());
                long copied=0;
                CRC32 crc=new CRC32();
                try (InputStream input=zip.getInputStream(entry);
                     OutputStream output=Files.newOutputStream(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                    byte[] buffer=new byte[64*1024];
                    for (int n;(n=input.read(buffer))!=-1;) {
                        checkCancelled(cancelled);
                        if (n>limit-total) throw new IOException("ARCHIVE_TOO_LARGE");
                        output.write(buffer,0,n); crc.update(buffer,0,n); copied+=n; total+=n;
                    }
                }
                if (copied!=entry.getSize() || crc.getValue()!=entry.getCrc()) throw new IOException("ARCHIVE_CORRUPTED");
                // Identity migration uses save freshness; unpacking must not make old files look newer.
                java.nio.file.attribute.FileTime modified=entry.getLastModifiedTime();
                if(modified!=null && modified.toMillis()>=0)Files.setLastModifiedTime(path,modified);
            }
        }
    }

    /** Bound JDK ZipFile's central-directory allocation before it opens an untrusted ZIP. */
    private static void validateCentralDirectory(Path archive) throws IOException {
        try (RandomAccessFile input=new RandomAccessFile(archive.toFile(),"r")) {
            long size=input.length();
            if (size<22 || size>MAX_ARCHIVE) throw new IOException("INVALID_ARCHIVE_DIRECTORY");
            int length=(int)Math.min(size,65557);
            byte[] tail=new byte[length];input.seek(size-length);input.readFully(tail);
            java.nio.ByteBuffer bytes=java.nio.ByteBuffer.wrap(tail).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int end=-1;
            for (int i=length-22;i>=0;i--) {
                if (bytes.getInt(i)==0x06054b50 && i+22+Short.toUnsignedInt(bytes.getShort(i+20))==length) {end=i;break;}
            }
            if (end<0 || bytes.getShort(end+4)!=0 || bytes.getShort(end+6)!=0) throw new IOException("INVALID_ARCHIVE_DIRECTORY");
            long count=Short.toUnsignedInt(bytes.getShort(end+10));
            long centralSize=Integer.toUnsignedLong(bytes.getInt(end+12));
            long centralOffset=Integer.toUnsignedLong(bytes.getInt(end+16));
            long endOffset=size-length+end;
            if (count==65535 || centralSize==0xffffffffL || centralOffset==0xffffffffL) {
                if (endOffset<20) throw new IOException("INVALID_ARCHIVE_DIRECTORY");
                byte[] locator=new byte[20];input.seek(endOffset-20);input.readFully(locator);
                java.nio.ByteBuffer loc=java.nio.ByteBuffer.wrap(locator).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                if (loc.getInt()!=0x07064b50 || loc.getInt()!=0) throw new IOException("INVALID_ARCHIVE_DIRECTORY");
                long position=loc.getLong();
                if (loc.getInt()!=1 || position<0 || position>size-56) throw new IOException("INVALID_ARCHIVE_DIRECTORY");
                byte[] record=new byte[56];input.seek(position);input.readFully(record);
                java.nio.ByteBuffer zip64=java.nio.ByteBuffer.wrap(record).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                if (zip64.getInt(0)!=0x06064b50 || zip64.getLong(4)<44 || zip64.getInt(16)!=0 || zip64.getInt(20)!=0)
                    throw new IOException("INVALID_ARCHIVE_DIRECTORY");
                count=zip64.getLong(32);centralSize=zip64.getLong(40);centralOffset=zip64.getLong(48);
                if (zip64.getLong(24)!=count) throw new IOException("INVALID_ARCHIVE_DIRECTORY");
                endOffset=position;
            } else if (Short.toUnsignedInt(bytes.getShort(end+8))!=count) throw new IOException("INVALID_ARCHIVE_DIRECTORY");
            if (count<1 || count>MAX_FILES || centralSize<0 || centralSize>128L*1024*1024
                    || centralOffset<0 || centralOffset>endOffset || centralSize>endOffset-centralOffset)
                throw new IOException("INVALID_ARCHIVE_DIRECTORY");
        }
    }

    static void safeName(String name) throws IOException {
        if (name.isEmpty() || name.length()>512 || name.startsWith("/") || name.contains("\\") || name.contains(":"))
            throw new IOException("UNSAFE_ARCHIVE_ENTRY");
        String[] parts=name.split("/",-1);
        if (parts.length>32) throw new IOException("UNSAFE_ARCHIVE_ENTRY");
        for (String part:parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..") || part.length()>120 || part.endsWith(".") || part.endsWith(" ")
                    || part.chars().anyMatch(c -> c<32 || c==127 || "<>\"|?*".indexOf(c)>=0))
                throw new IOException("UNSAFE_ARCHIVE_ENTRY");
            String base=part.split("\\.",2)[0].toUpperCase(Locale.ROOT);
            if (base.matches("CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9]")) throw new IOException("UNSAFE_ARCHIVE_ENTRY");
        }
    }

    public static String sha256(Path file, BooleanSupplier cancelled) throws IOException {
        MessageDigest digest;
        try { digest=MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        try (InputStream input=Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer=new byte[64*1024];
            for (int n;(n=input.read(buffer))!=-1;) { checkCancelled(cancelled); digest.update(buffer,0,n); }
        }
        StringBuilder text=new StringBuilder(64);
        for (byte b:digest.digest()) text.append(String.format(Locale.ROOT,"%02x",b&255));
        return text.toString();
    }

    static void checkCancelled(BooleanSupplier cancelled) throws IOException {
        if (Thread.currentThread().isInterrupted() || cancelled.getAsBoolean()) throw new InterruptedIOException("TRANSFER_CANCELLED");
    }

    public static void removeStaging(Path root) throws IOException {
        if (!Files.exists(root,LinkOption.NOFOLLOW_LINKS)) return;
        Files.walkFileTree(root,new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file,BasicFileAttributes attrs) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
            @Override public FileVisitResult postVisitDirectory(Path dir,IOException failure) throws IOException {
                if (failure!=null) throw failure; Files.delete(dir); return FileVisitResult.CONTINUE;
            }
        });
    }

    @FunctionalInterface public interface Finalizer { void prepare(Path staging) throws IOException; }
    private static final class LimitedOutput extends FilterOutputStream {
        long size; final long maximum;
        LimitedOutput(OutputStream output,long maximum) { super(output); this.maximum=maximum; }
        @Override public void write(int value) throws IOException { if (++size>maximum) throw new IOException("ARCHIVE_TOO_LARGE"); out.write(value); }
        @Override public void write(byte[] bytes,int offset,int length) throws IOException {
            if (length>maximum-size) throw new IOException("ARCHIVE_TOO_LARGE"); out.write(bytes,offset,length); size+=length;
        }
    }
}
