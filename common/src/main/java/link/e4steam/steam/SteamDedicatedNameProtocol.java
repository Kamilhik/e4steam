package link.e4steam.steam;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

/** Persistent service identity, separate from Steam's changing anonymous process identity. */
public final class SteamDedicatedNameProtocol {
    public static final String MARKER="e4s2";
    // Steam master-cache age is not a liveness proof; the fresh nonce handshake below is.
    public static final long MAX_DIRECTORY_AGE_MILLIS=15*60_000L;
    public static final class Target {
        public final SteamDedicatedAddress endpoint;
        public final String minecraft,loader,core,addon,name,fingerprint;
        public final int wire,players,capacity;
        public final long updated;
        public Target(SteamDedicatedAddress endpoint,String minecraft,String loader,String core,String addon,int wire,
                int players,int capacity,long updated,String fingerprint,String name) {
            this.endpoint=Objects.requireNonNull(endpoint);this.minecraft=minecraft;this.loader=loader;this.core=core;this.addon=addon;
            this.wire=wire;this.players=players;this.capacity=capacity;this.updated=updated;
            this.fingerprint=fingerprint;this.name=SteamNamedAddress.normalize(name);
            if (!this.name.equals(name) || !fingerprint.matches("[A-Za-z0-9_-]{22}") || !minecraft.matches("[0-9.]{3,12}")
                    || !Arrays.asList("fabric","forge","neoforge","quilt").contains(loader)
                    || !core.matches("[0-9A-Za-z.+_-]{1,32}") || !addon.matches("[0-9A-Za-z.+_-]{1,32}")
                    || wire<1 || wire>255 || capacity<1 || capacity>64 || players<0 || players>capacity || updated<0)
                throw new IllegalArgumentException("Invalid named dedicated advertisement");
        }
    }
    public static String encode(Target value) {
        String loader=value.loader.equals("fabric")?"f":value.loader.equals("forge")?"g":value.loader.equals("neoforge")?"n":"q";
        String tags=MARKER+","+Long.toUnsignedString(value.endpoint.steamId(),36)+","+Long.toString(value.endpoint.generation(),36)
                +","+value.minecraft+","+loader+","+value.core+","+value.addon+","+Integer.toString(value.wire,36)
                +","+Integer.toString(value.players,36)+","+Integer.toString(value.capacity,36)+","+Long.toString(value.updated/1000,36)
                +","+value.fingerprint+","+value.name;
        if(tags.length()>127)throw new IllegalArgumentException("Named server metadata exceeds Steam's tag limit");
        return tags;
    }
    public static Target decode(String tags,long now) {
        if(tags==null || tags.length()>127)throw new IllegalArgumentException("Invalid named advertisement");
        String[] p=tags.split(",",-1);
        if(p.length!=13 || !MARKER.equals(p[0]))throw new IllegalArgumentException("Invalid named advertisement");
        String loader=p[4].equals("f")?"fabric":p[4].equals("g")?"forge":p[4].equals("n")?"neoforge":p[4].equals("q")?"quilt":"";
        long updated=Math.multiplyExact(Long.parseLong(p[10],36),1000);
        if(updated<now-MAX_DIRECTORY_AGE_MILLIS || updated>now+30_000)throw new IllegalArgumentException("Stale named advertisement");
        return new Target(new SteamDedicatedAddress(Long.parseUnsignedLong(p[1],36),Long.parseLong(p[2],36)),
                p[3],loader,p[5],p[6],Integer.parseInt(p[7],36),Integer.parseInt(p[8],36),Integer.parseInt(p[9],36),updated,p[11],p[12]);
    }
    public static String fingerprint(PublicKey key) throws GeneralSecurityException {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(key.getEncoded()),16));
    }
    private static byte[] challenge(Target target,byte[] nonce) throws IOException {
        if(nonce==null || nonce.length!=32)throw new IOException("Invalid name challenge");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)) {
            out.writeUTF("e4steam-named-dedicated-v1");out.writeUTF(target.name);
            out.writeLong(target.endpoint.steamId());out.writeLong(target.endpoint.generation());out.write(nonce);
        }
        return bytes.toByteArray();
    }
    public static byte[] proof(Target target,byte[] nonce,KeyPair identity) throws GeneralSecurityException,IOException {
        if(!target.fingerprint.equals(fingerprint(identity.getPublic())))throw new GeneralSecurityException("Wrong service key");
        Signature signer=Signature.getInstance("Ed25519");signer.initSign(identity.getPrivate());signer.update(challenge(target,nonce));
        byte[] key=identity.getPublic().getEncoded(),signed=signer.sign();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(DataOutputStream out=new DataOutputStream(bytes)){out.writeShort(key.length);out.write(key);out.write(signed);}
        return bytes.toByteArray();
    }
    public static void verify(Target target,byte[] nonce,byte[] proof) throws GeneralSecurityException,IOException {
        if(proof==null || proof.length>194)throw new GeneralSecurityException("Invalid service proof");
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(proof))) {
            int length=in.readUnsignedShort();if(length<32 || length>128 || in.available()!=length+64)throw new GeneralSecurityException("Invalid service key");
            byte[] encoded=new byte[length],signed=new byte[64];in.readFully(encoded);in.readFully(signed);
            PublicKey key=KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
            if(!target.fingerprint.equals(fingerprint(key)))throw new GeneralSecurityException("Dedicated service identity changed");
            Signature verifier=Signature.getInstance("Ed25519");verifier.initVerify(key);verifier.update(challenge(target,nonce));
            if(!verifier.verify(signed))throw new GeneralSecurityException("Unverified dedicated service");
        }
    }
}
