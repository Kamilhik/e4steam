package link.e4steam.worldtransfer;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/** One explicitly consented transfer, authenticated by its Steam transport peer. */
public final class TransferProtocol {
    public static final int VERSION=2, CHUNK=24*1024, WINDOW=64;
    public static final int OFFER=1, ACCEPT=2, DECLINE=3, START=4, READY=5, DATA=6,
            ACK=7, END=8, COMPLETE=9, CANCEL=10, PING=11;
    private static final int MAGIC=0x45345754;
    private TransferProtocol() { }

    public static final class Offer {
        public final UUID id, worldId;
        public final String name, gameVersion, mods;
        public final long origin, generation;
        public final link.e4steam.lan.LanSettings.Snapshot sharing;
        public Offer(UUID id, UUID worldId,String name,String gameVersion,String mods,long origin,long generation) {
            this(id,worldId,name,gameVersion,mods,origin,generation,link.e4steam.lan.LanSettings.defaults());
        }
        public Offer(UUID id, UUID worldId,String name,String gameVersion,String mods,long origin,long generation,
                     link.e4steam.lan.LanSettings.Snapshot sharing) {
            if (id==null || worldId==null || name==null || name.trim().isEmpty() || name.length()>64 || name.chars().anyMatch(c->c<32 || c==127)
                    || gameVersion==null || !gameVersion.matches("(?:1\\.\\d+(?:\\.\\d+)?|\\d{2}\\.\\d+(?:\\.\\d+)?)")
                    || mods==null || !mods.matches("[0-9a-f]{64}") || !validSteam(origin) || generation<1 || generation>1_000_000)
                throw new IllegalArgumentException("INVALID_TRANSFER_OFFER");
            this.id=id; this.worldId=worldId; this.name=name; this.gameVersion=gameVersion; this.mods=mods;
            this.origin=origin; this.generation=generation;
            this.sharing=java.util.Objects.requireNonNull(sharing).validated().withPort(1);
        }
    }

    public static final class Packet {
        public final int type;
        public final UUID id;
        public final byte[] payload;
        Packet(int type,UUID id,byte[] payload) { this.type=type; this.id=id; this.payload=payload; }
    }

    public static boolean validSteam(long id) { return (id>>>56)==1 && ((id>>>52)&15)==1 && ((id>>>32)&0xfffff)==1 && (id&0xffffffffL)!=0; }

    public static byte[] packet(int type,UUID id,byte[] payload) throws IOException {
        if (type<OFFER || type>PING || id==null || payload==null || payload.length>CHUNK+128) throw new IOException("INVALID_TRANSFER_PACKET");
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try (DataOutputStream output=new DataOutputStream(bytes)) {
            output.writeInt(MAGIC); output.writeByte(VERSION); output.writeByte(type);
            output.writeLong(id.getMostSignificantBits()); output.writeLong(id.getLeastSignificantBits()); output.write(payload);
        }
        return bytes.toByteArray();
    }

    public static Packet decode(byte[] bytes) throws IOException {
        if (bytes==null || bytes.length<22 || bytes.length>CHUNK+150) throw new IOException("INVALID_TRANSFER_PACKET");
        try (DataInputStream input=new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (input.readInt()!=MAGIC || input.readUnsignedByte()!=VERSION) throw new IOException("TRANSFER_VERSION_MISMATCH");
            int type=input.readUnsignedByte();
            if (type<OFFER || type>PING) throw new IOException("INVALID_TRANSFER_PACKET");
            UUID id=new UUID(input.readLong(),input.readLong());
            byte[] payload = new byte[input.available()]; input.readFully(payload);
            return new Packet(type,id,payload);
        }
    }

    public static byte[] offer(Offer offer) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try (DataOutputStream output=new DataOutputStream(bytes)) {
            output.writeLong(offer.worldId.getMostSignificantBits()); output.writeLong(offer.worldId.getLeastSignificantBits());
            output.writeUTF(offer.name); output.writeUTF(offer.gameVersion); output.writeUTF(offer.mods);
            output.writeLong(offer.origin); output.writeLong(offer.generation);
            output.writeBoolean(offer.sharing.onlineMode());output.writeBoolean(offer.sharing.pvp());
            output.writeInt(offer.sharing.maxPlayers());output.writeUTF(offer.sharing.gameMode());output.writeBoolean(offer.sharing.allowCheats());
        }
        return packet(OFFER,offer.id,bytes.toByteArray());
    }

    public static Offer offer(Packet packet) throws IOException {
        if (packet.type!=OFFER || packet.payload.length>512) throw new IOException("INVALID_TRANSFER_OFFER");
        try (DataInputStream input=new DataInputStream(new ByteArrayInputStream(packet.payload))) {
            UUID world=new UUID(input.readLong(),input.readLong());
            String name=input.readUTF(),game=input.readUTF(),mods=input.readUTF();long origin=input.readLong(),generation=input.readLong();
            link.e4steam.lan.LanSettings.Snapshot sharing=new link.e4steam.lan.LanSettings.Snapshot(
                input.readBoolean(),input.readBoolean(),input.readInt(),1,input.readUTF(),input.readBoolean());
            Offer offer=new Offer(packet.id,world,name,game,mods,origin,generation,sharing);
            if (input.available()!=0) throw new IOException("INVALID_TRANSFER_OFFER");
            return offer;
        } catch (IllegalArgumentException invalid) { throw new IOException("INVALID_TRANSFER_OFFER"); }
    }

    public static byte[] offset(int type,UUID id,long offset) throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try (DataOutputStream output=new DataOutputStream(bytes)) { output.writeLong(offset); }
        return packet(type,id,bytes.toByteArray());
    }

    public static long offset(Packet packet) throws IOException {
        if (packet.payload.length!=8) throw new IOException("INVALID_TRANSFER_ACK");
        long value=new DataInputStream(new ByteArrayInputStream(packet.payload)).readLong();
        if (value<0 || value>WorldArchive.MAX_ARCHIVE) throw new IOException("INVALID_TRANSFER_ACK");
        return value;
    }

    public static byte[] data(UUID id,long offset,byte[] bytes,int count) throws IOException {
        if (offset<0 || count<1 || count>CHUNK || count>bytes.length) throw new IOException("INVALID_TRANSFER_DATA");
        ByteArrayOutputStream result=new ByteArrayOutputStream();
        try (DataOutputStream output=new DataOutputStream(result)) { output.writeLong(offset); output.write(bytes,0,count); }
        return packet(DATA,id,result.toByteArray());
    }

    public static long dataOffset(Packet packet) throws IOException {
        if (packet.type!=DATA || packet.payload.length<9 || packet.payload.length>CHUNK+8) throw new IOException("INVALID_TRANSFER_DATA");
        return new DataInputStream(new ByteArrayInputStream(packet.payload)).readLong();
    }
}
