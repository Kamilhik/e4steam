package link.e4steam.worldtransfer;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Local consent to receive a return from the exact custodian of a previously sent world. */
public final class HandoffHistory {
    private final Path directory;
    public HandoffHistory(Path directory) { this.directory=directory; }
    public void delegated(UUID world,long owner,long custodian) throws IOException {
        if (!TransferProtocol.validSteam(owner) || !TransferProtocol.validSteam(custodian) || owner==custodian)
            throw new IOException("INVALID_TRANSFER_PEER");
        Files.createDirectories(directory);
        Properties p=new Properties();p.setProperty("owner",Long.toUnsignedString(owner));
        p.setProperty("custodian",Long.toUnsignedString(custodian));
        Path tmp=Files.createTempFile(directory,".delegated-",".tmp");
        try {
            try (Writer output=Files.newBufferedWriter(tmp)) {p.store(output,"Return consent for a world sent by this installation");}
            Files.move(tmp,directory.resolve(world+".properties"),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(tmp);}
    }
    public boolean permits(UUID world,long owner,long custodian) {
        Path file=directory.resolve(world+".properties");
        try {
            if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>2048) return false;
            Properties p=new Properties();try (Reader input=Files.newBufferedReader(file)) {p.load(input);}
            return Long.toUnsignedString(owner).equals(p.getProperty("owner"))
                    && Long.toUnsignedString(custodian).equals(p.getProperty("custodian"));
        } catch (IOException | IllegalArgumentException failure) {return false;}
    }
}
