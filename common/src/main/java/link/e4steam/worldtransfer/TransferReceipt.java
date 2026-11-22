package link.e4steam.worldtransfer;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;
import java.util.UUID;

/** Saved world lineage; this metadata alone is not a Premium entitlement. */
public final class TransferReceipt {
    public final UUID worldId;
    public final long origin, generation;
    private static final String FILE="e4steam-world-transfer.properties";
    public TransferReceipt(UUID worldId,long origin,long generation) {
        if (worldId==null || !TransferProtocol.validSteam(origin) || generation<0 || generation>1_000_000)
            throw new IllegalArgumentException("INVALID_TRANSFER_RECEIPT");
        this.worldId=worldId; this.origin=origin; this.generation=generation;
    }
    public static TransferReceipt load(Path world) throws IOException {
        Path file=world.resolve(FILE);
        if (!Files.exists(file,LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>4096) throw new IOException("INVALID_TRANSFER_RECEIPT");
        Properties properties=new Properties();
        try (Reader input=Files.newBufferedReader(file)) { properties.load(input); }
        try {
            return new TransferReceipt(UUID.fromString(properties.getProperty("world")),
                Long.parseUnsignedLong(properties.getProperty("origin")),Long.parseLong(properties.getProperty("generation")));
        } catch (RuntimeException invalid) { throw new IOException("INVALID_TRANSFER_RECEIPT"); }
    }
    public void save(Path world) throws IOException {
        Properties values=new Properties();
        values.setProperty("world",worldId.toString()); values.setProperty("origin",Long.toUnsignedString(origin));
        values.setProperty("generation",Long.toString(generation));
        Path temporary=Files.createTempFile(world,".transfer-receipt-",".tmp");
        try {
            try (Writer output=Files.newBufferedWriter(temporary)) { values.store(output,"e4steam world transfer lineage; no credentials"); }
            Files.move(temporary,world.resolve(FILE),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    public boolean canReturnTo(long steamId) { return steamId==origin; }
}
