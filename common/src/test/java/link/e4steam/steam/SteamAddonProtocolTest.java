package link.e4steam.steam;

import link.e4steam.api.ApiLimits;
import link.e4steam.api.network.NetworkService.ChannelDescriptor;
import link.e4steam.api.network.NetworkService.ChannelId;
import link.e4steam.api.network.NetworkService.Delivery;
import link.e4steam.api.network.NetworkService.Direction;
import link.e4steam.api.network.NetworkService.Requirement;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SteamAddonProtocolTest {
    @Test
    void helloIsDeterministicBoundedAndRedacted() {
        byte[] nonce = nonce();
        ChannelDescriptor beta = descriptor("owner:beta", 1, 3, Requirement.OPTIONAL);
        ChannelDescriptor alpha = descriptor("owner:alpha", 2, 4, Requirement.REQUIRED);

        byte[] first = SteamAddonProtocol.encodeHello(nonce, Arrays.asList(beta, alpha));
        byte[] second = SteamAddonProtocol.encodeHello(nonce, Arrays.asList(alpha, beta));
        assertArrayEquals(first, second);

        SteamAddonProtocol.Hello decoded = SteamAddonProtocol.decodeHello(first);
        assertNotNull(decoded);
        assertArrayEquals(nonce, decoded.nonce());
        assertEquals("owner:alpha", decoded.channels().get(0).descriptor().id().value());
        assertEquals(Requirement.REQUIRED,
                decoded.channels().get(0).descriptor().requirement());
        assertEquals("Hello{channels=2, nonce=redacted}", decoded.toString());
        assertFalse(decoded.toString().contains(Arrays.toString(nonce)));
    }

    @Test
    void rejectsDuplicateTruncatedTrailingAndMalformedUtf8Hello() {
        ChannelDescriptor channel = descriptor("owner:alpha", 1, 1, Requirement.OPTIONAL);
        assertThrows(IllegalArgumentException.class, () -> SteamAddonProtocol.encodeHello(
                nonce(), Arrays.asList(channel, channel)));

        byte[] valid = SteamAddonProtocol.encodeHello(nonce(), Collections.singletonList(channel));
        assertNull(SteamAddonProtocol.decodeHello(Arrays.copyOf(valid, valid.length - 1)));
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        assertNull(SteamAddonProtocol.decodeHello(trailing));

        byte[] malformed = valid.clone();
        int firstTextByte = 1 + SteamAddonProtocol.NONCE_SIZE + Short.BYTES + Short.BYTES;
        malformed[firstTextByte] = (byte) 0xC3;
        malformed[firstTextByte + 1] = 0x28;
        assertNull(SteamAddonProtocol.decodeHello(malformed));
    }

    @Test
    void fragmentsMaximumMessageWithoutChangingBytes() {
        byte[] payload = new byte[ApiLimits.MAX_CHANNEL_MESSAGE_BYTES];
        new Random(44L).nextBytes(payload);
        byte[] nonce = nonce();

        List<byte[]> encoded = SteamAddonProtocol.encodeData(
                nonce, "owner:bulk", 9, 41L, payload);
        assertTrue(encoded.size() > 1);
        byte[] restored = new byte[payload.length];
        int offset = 0;
        for (int index = 0; index < encoded.size(); index++) {
            assertTrue(encoded.get(index).length <= SteamProtocol.DATA_CHUNK_SIZE);
            SteamAddonProtocol.Fragment fragment = SteamAddonProtocol.decodeData(encoded.get(index));
            assertNotNull(fragment);
            assertArrayEquals(nonce, fragment.bindingNonce());
            assertEquals(index, fragment.index());
            assertEquals(encoded.size(), fragment.count());
            assertEquals(payload.length, fragment.totalLength());
            byte[] part = fragment.payload();
            System.arraycopy(part, 0, restored, offset, part.length);
            offset += part.length;
            assertTrue(fragment.toString().contains("payload=redacted"));
        }
        assertEquals(payload.length, offset);
        assertArrayEquals(payload, restored);
    }

    @Test
    void malformedAndRandomFramesAreRejectedWithoutThrowing() {
        List<byte[]> valid = SteamAddonProtocol.encodeData(
                nonce(), "owner:test", 1, 1L, new byte[]{1, 2, 3});
        byte[] corrupted = valid.get(0).clone();
        corrupted[corrupted.length - Integer.BYTES - 1] ^= 0x7f;
        assertNull(SteamAddonProtocol.decodeData(corrupted));
        assertNull(SteamAddonProtocol.decodeData(new byte[0]));

        Random random = new Random(5_009L);
        for (int index = 0; index < 2_000; index++) {
            byte[] candidate = new byte[random.nextInt(768)];
            random.nextBytes(candidate);
            assertDoesNotThrow(() -> SteamAddonProtocol.decodeHello(candidate));
            assertDoesNotThrow(() -> SteamAddonProtocol.decodeData(candidate));
        }
    }

    private static ChannelDescriptor descriptor(
            String id, int minimum, int maximum, Requirement requirement) {
        return new ChannelDescriptor(new ChannelId(id), minimum, maximum, requirement,
                Direction.BIDIRECTIONAL, Delivery.RELIABLE_ORDERED,
                64 * 1024, 256 * 1024, 8, "schema-v1");
    }

    private static byte[] nonce() {
        byte[] nonce = new byte[SteamAddonProtocol.NONCE_SIZE];
        for (int index = 0; index < nonce.length; index++) nonce[index] = (byte) (index + 1);
        return nonce;
    }
}
