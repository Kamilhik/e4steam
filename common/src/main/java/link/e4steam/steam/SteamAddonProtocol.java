package link.e4steam.steam;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.network.NetworkService.ChannelDescriptor;
import link.e4steam.api.network.NetworkService.ChannelId;
import link.e4steam.api.network.NetworkService.Delivery;
import link.e4steam.api.network.NetworkService.Direction;
import link.e4steam.api.network.NetworkService.Requirement;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict, bounded codec for addon negotiation and data fragments. */
final class SteamAddonProtocol {
    static final byte VERSION = 1;
    static final int NONCE_SIZE = 16;
    static final int MAX_CHANNELS = 32;
    static final int MAX_FRAGMENT_BYTES = SteamProtocol.DATA_CHUNK_SIZE - 512;
    static final int MAX_FRAGMENTS = (ApiLimits.MAX_CHANNEL_MESSAGE_BYTES
            + MAX_FRAGMENT_BYTES - 1) / MAX_FRAGMENT_BYTES;

    private SteamAddonProtocol() {
    }

    static byte[] encodeHello(byte[] nonce, List<ChannelDescriptor> descriptors) {
        if (nonce == null || nonce.length != NONCE_SIZE || descriptors == null
                || descriptors.size() > MAX_CHANNELS) {
            throw new IllegalArgumentException("Invalid addon hello");
        }
        List<ChannelDescriptor> ordered = new ArrayList<>(descriptors);
        ordered.sort(Comparator.comparing(value -> value.id().value()));
        Set<String> unique = new HashSet<>();
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream output = new DataOutputStream(bytes);
            output.writeByte(VERSION);
            output.write(nonce);
            output.writeShort(ordered.size());
            for (ChannelDescriptor descriptor : ordered) {
                if (!unique.add(descriptor.id().value())) {
                    throw new IllegalArgumentException("Duplicate addon channel");
                }
                writeUtf8(output, descriptor.id().value(), ApiLimits.MAX_IDENTIFIER_LENGTH * 4);
                output.writeShort(descriptor.minimumVersion());
                output.writeShort(descriptor.maximumVersion());
                output.writeByte(descriptor.requirement().ordinal());
                output.writeByte(descriptor.direction().ordinal());
                output.writeByte(descriptor.delivery().ordinal());
                output.writeInt(descriptor.maximumMessageBytes());
                output.writeInt(descriptor.bytesPerSecond());
                output.writeShort(descriptor.queueMessages());
                writeUtf8(output, descriptor.schemaId(), ApiLimits.MAX_IDENTIFIER_LENGTH * 4);
            }
            output.flush();
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > SteamProtocol.DATA_CHUNK_SIZE) {
                throw new IllegalArgumentException("Addon hello exceeds the frame limit");
            }
            return encoded;
        } catch (IOException impossible) {
            throw new IllegalStateException("In-memory addon encoding failed", impossible);
        }
    }

    static Hello decodeHello(byte[] payload) {
        if (payload == null || payload.length < 1 + NONCE_SIZE + Short.BYTES
                || payload.length > SteamProtocol.DATA_CHUNK_SIZE) return null;
        try {
            ByteBuffer input = ByteBuffer.wrap(payload);
            if (input.get() != VERSION) return null;
            byte[] nonce = new byte[NONCE_SIZE];
            input.get(nonce);
            int count = Short.toUnsignedInt(input.getShort());
            if (count > MAX_CHANNELS) return null;
            List<ChannelOffer> channels = new ArrayList<>(count);
            Set<String> unique = new HashSet<>();
            for (int index = 0; index < count; index++) {
                String id = readUtf8(input, ApiLimits.MAX_IDENTIFIER_LENGTH * 4);
                if (!unique.add(id) || input.remaining() < 2 * Short.BYTES + 3
                        + 2 * Integer.BYTES + Short.BYTES) return null;
                int minimumVersion = Short.toUnsignedInt(input.getShort());
                int maximumVersion = Short.toUnsignedInt(input.getShort());
                Requirement requirement = enumValue(Requirement.values(), input.get());
                Direction direction = enumValue(Direction.values(), input.get());
                Delivery delivery = enumValue(Delivery.values(), input.get());
                int maximumMessageBytes = input.getInt();
                int bytesPerSecond = input.getInt();
                int queueMessages = Short.toUnsignedInt(input.getShort());
                String schemaId = readUtf8(input, ApiLimits.MAX_IDENTIFIER_LENGTH * 4);
                if (requirement == null || direction == null || delivery == null) return null;
                ChannelDescriptor descriptor = new ChannelDescriptor(
                        new ChannelId(id), minimumVersion, maximumVersion,
                        requirement, direction, delivery, maximumMessageBytes,
                        bytesPerSecond, queueMessages, schemaId);
                channels.add(new ChannelOffer(descriptor));
            }
            if (input.hasRemaining()) return null;
            channels.sort(Comparator.comparing(value -> value.descriptor().id().value()));
            return new Hello(nonce, channels);
        } catch (RuntimeException failure) {
            return null;
        }
    }

    static List<byte[]> encodeData(
            byte[] bindingNonce,
            String channelId,
            int negotiatedVersion,
            long sequence,
            byte[] payload
    ) {
        if (bindingNonce == null || bindingNonce.length != NONCE_SIZE
                || channelId == null || negotiatedVersion < 1 || sequence <= 0L
                || payload == null || payload.length == 0
                || payload.length > ApiLimits.MAX_CHANNEL_MESSAGE_BYTES) {
            throw new IllegalArgumentException("Invalid addon data message");
        }
        new ChannelId(channelId);
        int fragments = (payload.length + MAX_FRAGMENT_BYTES - 1) / MAX_FRAGMENT_BYTES;
        if (fragments < 1 || fragments > MAX_FRAGMENTS) {
            throw new IllegalArgumentException("Invalid addon fragment count");
        }
        List<byte[]> result = new ArrayList<>(fragments);
        for (int index = 0; index < fragments; index++) {
            int offset = index * MAX_FRAGMENT_BYTES;
            int length = Math.min(MAX_FRAGMENT_BYTES, payload.length - offset);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(length + 160);
            try {
                DataOutputStream output = new DataOutputStream(bytes);
                output.writeByte(VERSION);
                output.write(bindingNonce);
                writeUtf8(output, channelId, ApiLimits.MAX_IDENTIFIER_LENGTH * 4);
                output.writeShort(negotiatedVersion);
                output.writeLong(sequence);
                output.writeShort(index);
                output.writeShort(fragments);
                output.writeInt(payload.length);
                output.write(payload, offset, length);
                output.flush();
            } catch (IOException impossible) {
                throw new IllegalStateException("In-memory addon encoding failed", impossible);
            }
            byte[] encoded = bytes.toByteArray();
            if (encoded.length > SteamProtocol.DATA_CHUNK_SIZE) {
                throw new IllegalArgumentException("Addon fragment exceeds the frame limit");
            }
            result.add(encoded);
        }
        return Collections.unmodifiableList(result);
    }

    static Fragment decodeData(byte[] payload) {
        if (payload == null || payload.length < 1 + NONCE_SIZE + Short.BYTES
                + Short.BYTES + Long.BYTES + 2 * Short.BYTES + Integer.BYTES + 1
                || payload.length > SteamProtocol.DATA_CHUNK_SIZE) return null;
        try {
            ByteBuffer input = ByteBuffer.wrap(payload);
            if (input.get() != VERSION) return null;
            byte[] nonce = new byte[NONCE_SIZE];
            input.get(nonce);
            String channelId = readUtf8(input, ApiLimits.MAX_IDENTIFIER_LENGTH * 4);
            if (input.remaining() < Short.BYTES + Long.BYTES + 2 * Short.BYTES
                    + Integer.BYTES + 1) return null;
            int version = Short.toUnsignedInt(input.getShort());
            long sequence = input.getLong();
            int index = Short.toUnsignedInt(input.getShort());
            int count = Short.toUnsignedInt(input.getShort());
            int totalLength = input.getInt();
            int fragmentLength = input.remaining();
            if (version < 1 || sequence <= 0L || count < 1 || count > MAX_FRAGMENTS
                    || index >= count || totalLength < 1
                    || totalLength > ApiLimits.MAX_CHANNEL_MESSAGE_BYTES
                    || fragmentLength < 1 || fragmentLength > MAX_FRAGMENT_BYTES) return null;
            int expectedCount = (totalLength + MAX_FRAGMENT_BYTES - 1) / MAX_FRAGMENT_BYTES;
            int expectedLength = index == count - 1
                    ? totalLength - index * MAX_FRAGMENT_BYTES : MAX_FRAGMENT_BYTES;
            if (count != expectedCount || fragmentLength != expectedLength) return null;
            new ChannelId(channelId);
            byte[] fragment = new byte[fragmentLength];
            input.get(fragment);
            return new Fragment(nonce, channelId, version, sequence, index,
                    count, totalLength, fragment);
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private static void writeUtf8(DataOutputStream output, String value, int maximumBytes)
            throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 1 || bytes.length > maximumBytes || bytes.length > 65_535) {
            throw new IllegalArgumentException("Invalid addon UTF-8 field");
        }
        output.writeShort(bytes.length);
        output.write(bytes);
    }

    private static String readUtf8(ByteBuffer input, int maximumBytes) {
        if (input.remaining() < Short.BYTES) throw new IllegalArgumentException("Truncated text");
        int length = Short.toUnsignedInt(input.getShort());
        if (length < 1 || length > maximumBytes || length > input.remaining()) {
            throw new IllegalArgumentException("Invalid text length");
        }
        ByteBuffer slice = input.slice();
        slice.limit(length);
        String value;
        try {
            value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(slice).toString();
        } catch (CharacterCodingException failure) {
            throw new IllegalArgumentException("Invalid UTF-8");
        }
        input.position(input.position() + length);
        return value;
    }

    private static <T> T enumValue(T[] values, byte encoded) {
        int index = Byte.toUnsignedInt(encoded);
        return index < values.length ? values[index] : null;
    }

    static final class Hello {
        private final byte[] nonce;
        private final List<ChannelOffer> channels;

        private Hello(byte[] nonce, List<ChannelOffer> channels) {
            this.nonce = nonce.clone();
            this.channels = Collections.unmodifiableList(new ArrayList<>(channels));
        }

        byte[] nonce() { return nonce.clone(); }
        List<ChannelOffer> channels() { return channels; }
        @Override public String toString() {
            return "Hello{channels=" + channels.size() + ", nonce=redacted}";
        }
    }

    static final class ChannelOffer {
        private final ChannelDescriptor descriptor;
        private ChannelOffer(ChannelDescriptor descriptor) { this.descriptor = descriptor; }
        ChannelDescriptor descriptor() { return descriptor; }
    }

    static final class Fragment {
        private final byte[] bindingNonce;
        private final String channelId;
        private final int version;
        private final long sequence;
        private final int index;
        private final int count;
        private final int totalLength;
        private final byte[] payload;

        private Fragment(byte[] bindingNonce, String channelId, int version, long sequence,
                         int index, int count, int totalLength, byte[] payload) {
            this.bindingNonce = bindingNonce.clone();
            this.channelId = channelId;
            this.version = version;
            this.sequence = sequence;
            this.index = index;
            this.count = count;
            this.totalLength = totalLength;
            this.payload = payload.clone();
        }

        byte[] bindingNonce() { return bindingNonce.clone(); }
        String channelId() { return channelId; }
        int version() { return version; }
        long sequence() { return sequence; }
        int index() { return index; }
        int count() { return count; }
        int totalLength() { return totalLength; }
        byte[] payload() { return payload.clone(); }
        @Override public String toString() {
            return "Fragment{channel=" + channelId + ", version=" + version
                    + ", index=" + index + '/' + count + ", payload=redacted}";
        }
    }
}
