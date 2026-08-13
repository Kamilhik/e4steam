package link.e4steam.api;

import link.e4steam.api.network.NetworkService.MessageReader;
import link.e4steam.api.network.NetworkService.MessageWriter;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NetworkCodecTest {
    @Test
    void roundTripsBoundedValues() {
        MessageWriter writer = new MessageWriter(128)
                .writeVarInt(Integer.MAX_VALUE)
                .writeUtf8("Привет Steam", 32)
                .writeBytes(new byte[] {1, 2, 3}, 3);

        MessageReader reader = new MessageReader(writer.toByteArray(), 128);
        assertEquals(Integer.MAX_VALUE, reader.readVarInt());
        assertEquals("Привет Steam", reader.readUtf8(32, 64));
        assertArrayEquals(new byte[] {1, 2, 3}, reader.readBytes(3));
        assertEquals(0, reader.remaining());
    }

    @Test
    void rejectsOverflowBeforeLengthAllocation() {
        assertThrows(IllegalArgumentException.class,
                () -> new MessageReader(new byte[] {(byte) 0x80, (byte) 0x80,
                        (byte) 0x80, (byte) 0x80, 0x10}, 5).readVarInt());
        assertThrows(IllegalArgumentException.class,
                () -> new MessageReader(new byte[] {(byte) 0x80, (byte) 0x80,
                        (byte) 0x80, (byte) 0x80, 0x08}, 5).readVarInt());
        assertThrows(IllegalArgumentException.class,
                () -> new MessageReader(new byte[] {(byte) 0x80, (byte) 0x80,
                        (byte) 0x80, (byte) 0x80, (byte) 0x80}, 5).readVarInt());
        assertThrows(IllegalArgumentException.class,
                () -> new MessageReader(new byte[] {(byte) 0x81, 0x00}, 2).readVarInt());
    }

    @Test
    void rejectsTruncationInvalidUtf8AndOversizedLength() {
        assertThrows(IllegalArgumentException.class,
                () -> new MessageReader(new byte[] {(byte) 0x80}, 1).readVarInt());
        assertThrows(IllegalArgumentException.class,
                () -> new MessageReader(new byte[] {2, (byte) 0xc3, 0x28}, 3)
                        .readUtf8(4, 4));
        assertThrows(IllegalArgumentException.class,
                () -> new MessageReader(new byte[] {8, 1, 2}, 3).readBytes(4));
        assertThrows(IllegalArgumentException.class,
                () -> new MessageReader("abcdef".getBytes(StandardCharsets.UTF_8), 4));
    }
}
