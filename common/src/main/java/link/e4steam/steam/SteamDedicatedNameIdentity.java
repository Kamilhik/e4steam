package link.e4steam.steam;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.util.*;

/** Local service key. It is never sent to Steam, clients or a release archive. */
public final class SteamDedicatedNameIdentity {
    public final String name,fingerprint;
    private final KeyPair key;
    private SteamDedicatedNameIdentity(String name,KeyPair key) throws GeneralSecurityException {
        this.name=SteamNamedAddress.normalize(name);this.key=key;this.fingerprint=SteamDedicatedNameProtocol.fingerprint(key.getPublic());
    }
    public static SteamDedicatedNameIdentity load(Path profile) throws Exception {
        // Free builds have no provider. In Premium, the provider validates the signed licence locally.
        final Class<?> provider;
        try {provider=Class.forName("dev.k2studio.e4steam.premium.server.PremiumDedicatedName",true,SteamDedicatedNameIdentity.class.getClassLoader());}
        catch(ClassNotFoundException free){return null;}
        String name=(String)provider.getMethod("load",Path.class).invoke(null,profile);
        if(name==null || name.isEmpty())return null;
        Path file=profile.resolve("config/e4steam-premium/dedicated-name-identity.key");
        Properties data=new Properties();KeyPair pair;
        if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)) {
            if(Files.isSymbolicLink(file)||!Files.isRegularFile(file)||Files.size(file)>4096)throw new IOException("Invalid dedicated identity file");
            try(InputStream in=Files.newInputStream(file)){data.load(in);}
            KeyFactory factory=KeyFactory.getInstance("Ed25519");
            pair=new KeyPair(factory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(data.getProperty("public","")))),
                    factory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(data.getProperty("private","")))));
        } else {
            Files.createDirectories(file.getParent());pair=KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            data.setProperty("public",Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
            data.setProperty("private",Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));
            Path temporary=Files.createTempFile(file.getParent(),"name-identity-",".tmp");
            try {
                try {Files.setPosixFilePermissions(temporary,java.util.EnumSet.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));}
                catch(UnsupportedOperationException ignored){}
                try(OutputStream out=Files.newOutputStream(temporary)){data.store(out,"PRIVATE persistent dedicated-server identity; keep this file across upgrades");}
                Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE);
            } finally {Files.deleteIfExists(temporary);}
        }
        SteamDedicatedNameIdentity identity=new SteamDedicatedNameIdentity(name,pair);
        byte[] nonce=new byte[32];new SecureRandom().nextBytes(nonce);
        SteamDedicatedNameProtocol.Target self=new SteamDedicatedNameProtocol.Target(new SteamDedicatedAddress(1,1),"1.17.1","forge","0","0",1,0,1,System.currentTimeMillis(),identity.fingerprint,identity.name);
        SteamDedicatedNameProtocol.verify(self,nonce,identity.proof(self,nonce));
        return identity;
    }
    public byte[] proof(SteamDedicatedNameProtocol.Target target,byte[] nonce) throws GeneralSecurityException,IOException {
        return SteamDedicatedNameProtocol.proof(target,nonce,key);
    }
    private static Path pins(){return Paths.get(System.getProperty("user.dir","."),"config/e4steam/named-dedicated-pins");}
    public static String pinned(String name) throws IOException {
        Path file=pins().resolve(SteamNamedAddress.normalize(name)+".pin");
        if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))return null;
        if(Files.isSymbolicLink(file)||!Files.isRegularFile(file)||Files.size(file)>64)throw new IOException("Invalid saved dedicated identity");
        String value=new String(Files.readAllBytes(file),java.nio.charset.StandardCharsets.US_ASCII).trim();
        if(!value.matches("[A-Za-z0-9_-]{22}"))throw new IOException("Invalid saved dedicated identity");
        return value;
    }
    public static synchronized void remember(SteamDedicatedNameProtocol.Target target) throws IOException {
        String prior=pinned(target.name);
        if(prior!=null&&!prior.equals(target.fingerprint))throw new IOException("Dedicated address identity changed");
        if(prior!=null)return;
        Files.createDirectories(pins());
        Files.write(pins().resolve(target.name+".pin"),target.fingerprint.getBytes(java.nio.charset.StandardCharsets.US_ASCII),StandardOpenOption.CREATE_NEW);
    }
}
