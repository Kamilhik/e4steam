package link.e4steam.steam;

import java.nio.ByteBuffer;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

final class SteamProtocol {
    static final int MAGIC = 0x45345354; // E4ST
    static final byte VERSION = 4;
    static final byte OPEN = 1;
    static final byte DATA = 2;
    static final byte FIN = 3;
    static final byte RESET = 4;
    static final byte DATAGRAM = 5;
    static final byte OPEN_ACK = 6;
    /**
     * Confirms that the client received OPEN_ACK and that the return path is
     * usable before a Forge host releases its large login-registry burst.
     * Older clients safely ignore this optional frame.
     */
    static final byte BRIDGE_READY = 7;
    private static final short FLAG_COMPRESSED = 1;
    private static final short KNOWN_FLAGS = FLAG_COMPRESSED;
    private static final int COMPRESSED_LENGTH_SIZE = Integer.BYTES;
    static final int OPEN_ACK_PAYLOAD_SIZE = Byte.BYTES + Short.BYTES;

    static final int DATA_CHUNK_SIZE = 32 * 1024;
    static final int HEADER_SIZE = Integer.BYTES + Byte.BYTES + Byte.BYTES + Short.BYTES + Integer.BYTES;
    // Keep voice datagrams within a conservative single-packet payload,
    // including this protocol's header.
    static final int MAX_DATAGRAM_SIZE = 1_200 - HEADER_SIZE;
    static final int MAX_PACKET_SIZE = HEADER_SIZE + Math.max(DATA_CHUNK_SIZE, MAX_DATAGRAM_SIZE);
    static final int MAX_ACCEPTED_STEAM_PACKET_SIZE = 1024 * 1024;

    private SteamProtocol() {
    }

    static byte[] encodeOpen(int connectionId, byte[] token) {
        if (token.length != SteamAddress.TOKEN_LENGTH) {
            throw new IllegalArgumentException("Invalid invite token length");
        }
        ByteBuffer buffer = header(OPEN, connectionId, SteamAddress.TOKEN_LENGTH);
        buffer.put(token);
        return buffer.array();
    }

    static byte[] encodeData(int connectionId, byte[] payload) {
        if (payload.length == 0 || payload.length > DATA_CHUNK_SIZE) {
            throw new IllegalArgumentException("Invalid Steam payload length: " + payload.length);
        }
        // Keep the Minecraft byte stream byte-for-byte identical to the
        // working beta transport. Steam already compresses/encrypts its own
        // packets; a second framing compression caused some reverse streams
        // to be discarded before Forge/Fabric could read them.
        ByteBuffer buffer = header(DATA, (short) 0, connectionId, payload.length);
        buffer.put(payload);
        return buffer.array();
    }

    static byte[] encodeOpenAck(int connectionId, VoiceChatUdpEndpoint endpoint) {
        ByteBuffer buffer = header(OPEN_ACK, connectionId, OPEN_ACK_PAYLOAD_SIZE);
        buffer.put(endpoint.clientPortMode());
        buffer.putShort((short) endpoint.hostPort());
        return buffer.array();
    }

    static byte[] encodeBridgeReady(int connectionId) {
        return header(BRIDGE_READY, connectionId, 0).array();
    }

    static byte[] encodeFin(int connectionId) {
        return header(FIN, connectionId, 0).array();
    }

    static byte[] encodeReset(int connectionId) {
        return header(RESET, connectionId, 0).array();
    }

    static byte[] encodeDatagram(int connectionId, byte[] payload) {
        if (payload.length == 0 || payload.length > MAX_DATAGRAM_SIZE) {
            throw new IllegalArgumentException("Invalid UDP payload length: " + payload.length);
        }
        ByteBuffer buffer = header(DATAGRAM, connectionId, payload.length);
        buffer.put(payload);
        return buffer.array();
    }

    static Frame decode(ByteBuffer source) {
        if (source.remaining() < HEADER_SIZE) {
            return null;
        }
        if (source.getInt() != MAGIC || source.get() != VERSION) {
            return null;
        }

        byte type = source.get();
        short flags = source.getShort();
        if ((flags & ~KNOWN_FLAGS) != 0 || (flags != 0 && type != DATA)) {
            return null;
        }
        int connectionId = source.getInt();
        int payloadLength = source.remaining();

        if (connectionId == 0) {
            return null;
        }
        if (type == OPEN && payloadLength != SteamAddress.TOKEN_LENGTH) {
            return null;
        }
        if (type == OPEN_ACK && payloadLength != OPEN_ACK_PAYLOAD_SIZE) {
            return null;
        }
        if (type == DATA && (payloadLength == 0 || payloadLength > DATA_CHUNK_SIZE)) {
            return null;
        }
        if (type == DATAGRAM && (payloadLength == 0 || payloadLength > MAX_DATAGRAM_SIZE)) {
            return null;
        }
        if ((type == FIN || type == RESET || type == BRIDGE_READY) && payloadLength != 0) {
            return null;
        }
        if (type != OPEN
                && type != OPEN_ACK
                && type != BRIDGE_READY
                && type != DATA
                && type != FIN
                && type != RESET
                && type != DATAGRAM) {
            return null;
        }

        byte[] payload;
        if (type == DATA && (flags & FLAG_COMPRESSED) != 0) {
            payload = decompress(source);
            if (payload == null) {
                return null;
            }
        } else {
            payload = new byte[payloadLength];
            source.get(payload);
        }
        return new Frame(type, connectionId, payload);
    }

    private static ByteBuffer header(byte type, int connectionId, int payloadLength) {
        return header(type, (short) 0, connectionId, payloadLength);
    }

    private static ByteBuffer header(byte type, short flags, int connectionId, int payloadLength) {
        return ByteBuffer.allocate(HEADER_SIZE + payloadLength)
                .putInt(MAGIC)
                .put(VERSION)
                .put(type)
                .putShort(flags)
                .putInt(connectionId);
    }

    private static byte[] decompress(ByteBuffer source) {
        if (source.remaining() <= COMPRESSED_LENGTH_SIZE) {
            return null;
        }
        int uncompressedLength = source.getInt();
        if (uncompressedLength <= 0 || uncompressedLength > DATA_CHUNK_SIZE) {
            return null;
        }
        byte[] compressed = new byte[source.remaining()];
        source.get(compressed);
        byte[] result = new byte[uncompressedLength];
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            int offset = 0;
            while (!inflater.finished() && offset < result.length) {
                int read = inflater.inflate(result, offset, result.length - offset);
                if (read == 0) {
                    return null;
                }
                offset += read;
            }
            if (offset != result.length || !inflater.finished() || inflater.getRemaining() != 0) {
                return null;
            }
            return result;
        } catch (DataFormatException exception) {
            return null;
        } finally {
            inflater.end();
        }
    }

    static final class Frame {
        private final byte type;
        private final int connectionId;
        private final byte[] payload;

        Frame(byte type, int connectionId, byte[] payload) {
            this.type = type;
            this.connectionId = connectionId;
            this.payload = payload;
        }

        byte type() { return type; }
        int connectionId() { return connectionId; }
        byte[] payload() { return payload; }
    }
}
