package link.e4steam.worldtransfer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/** Local sealed receipts authorize only returning an imported world to its original sender. */
public final class TransferGrants {
    private static final String FILE="e4steam-world-transfer.properties";
    private final Path key;
    public TransferGrants(Path configuration) { key=configuration.resolve("receipt-secret.key"); }

    public TransferReceipt verified(Path world,long currentSteam) throws IOException {
        world=world.toAbsolutePath().normalize();
        Path file=world.resolve(FILE);
        if (!Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>8192 || !Files.exists(key)) return null;
        Properties values=new Properties();
        try (Reader input=Files.newBufferedReader(file)) {values.load(input);}
        try {
            if (!values.getProperty("version","").equals("1") || Long.parseUnsignedLong(values.getProperty("recipient","0"))!=currentSteam
                    || !values.getProperty("folder","").equals(world.getFileName().toString())) return null;
            TransferReceipt receipt=new TransferReceipt(UUID.fromString(values.getProperty("world")),
                    Long.parseUnsignedLong(values.getProperty("origin")),Long.parseLong(values.getProperty("generation")));
            String auth=values.getProperty("auth","");
            if (receipt.generation<1 || !auth.matches("[0-9a-f]{64}")) return null;
            String expected=signature(receipt,currentSteam,values.getProperty("folder"),readKey(false));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),auth.getBytes(StandardCharsets.US_ASCII))?receipt:null;
        } catch (IllegalArgumentException | NullPointerException invalid) {return null;}
    }

    public void imported(Path staging,TransferProtocol.Offer offer,long authenticatedSender,long recipient) throws IOException {
        if (offer.origin!=authenticatedSender && offer.origin!=recipient) throw new IOException("INVALID_TRANSFER_ORIGIN");
        TransferReceipt receipt=new TransferReceipt(offer.worldId,offer.origin,offer.generation);
        String folder="e4steam-"+offer.id;
        Properties values=new Properties();
        values.setProperty("version","1");values.setProperty("world",receipt.worldId.toString());
        values.setProperty("origin",Long.toUnsignedString(receipt.origin));values.setProperty("generation",Long.toString(receipt.generation));
        values.setProperty("recipient",Long.toUnsignedString(recipient));values.setProperty("folder",folder);
        values.setProperty("auth",signature(receipt,recipient,folder,readKey(true)));
        Path temporary=Files.createTempFile(staging,".receipt-",".tmp");
        try {
            try (Writer output=Files.newBufferedWriter(temporary)) {values.store(output,"e4steam received-world receipt; no licence data");}
            Files.move(temporary,staging.resolve(FILE),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally {Files.deleteIfExists(temporary);}
    }

    private synchronized byte[] readKey(boolean create) throws IOException {
        if (!Files.exists(key,LinkOption.NOFOLLOW_LINKS) && create) {
            Files.createDirectories(key.getParent());
            if (Files.isSymbolicLink(key.getParent())) throw new IOException("INVALID_RECEIPT_STORAGE");
            byte[] value=new byte[32];new SecureRandom().nextBytes(value);
            try {Files.write(key,value,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE);}
            catch (FileAlreadyExistsException anotherProcess) { }
            try {Files.setPosixFilePermissions(key,EnumSet.of(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE));}
            catch (UnsupportedOperationException windows) { }
        }
        if (!Files.isRegularFile(key,LinkOption.NOFOLLOW_LINKS) || Files.size(key)!=32) throw new IOException("INVALID_RECEIPT_STORAGE");
        return Files.readAllBytes(key);
    }
    private static String signature(TransferReceipt receipt,long recipient,String folder,byte[] key) throws IOException {
        try {
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();
            try (DataOutputStream output=new DataOutputStream(bytes)) {
                output.writeUTF("e4steam-world-return-v1");output.writeUTF(receipt.worldId.toString());
                output.writeLong(receipt.origin);output.writeLong(receipt.generation);output.writeLong(recipient);output.writeUTF(folder);
            }
            Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(key,"HmacSHA256"));
            StringBuilder text=new StringBuilder(64);
            for(byte value:mac.doFinal(bytes.toByteArray())) text.append(String.format(Locale.ROOT,"%02x",value&255));
            return text.toString();
        } catch (java.security.GeneralSecurityException unavailable) {throw new IOException("RECEIPT_SIGNATURE_UNAVAILABLE");}
    }
}
