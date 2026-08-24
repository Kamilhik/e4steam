package link.e4steam.internal.dedicated;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DedicatedIngressRegistryTest {
    @Test
    void authenticatedHandleIsLoopbackGenerationBoundAndSingleUse() throws Exception {
        DedicatedIngressRegistry registry = new DedicatedIngressRegistry();
        AutoCloseable registration = registry.register(49123, 76561198000000001L, 4L);

        assertEquals(0L, registry.resolve(new InetSocketAddress("192.0.2.1", 49123), 4L));
        assertEquals(76561198000000001L,
                registry.resolve(new InetSocketAddress("127.0.0.1", 49123), 4L));
        assertEquals(0L,
                registry.resolve(new InetSocketAddress("127.0.0.1", 49123), 4L));
        registration.close();
    }

    @Test
    void staleGenerationConsumesAndRejectsHandle() {
        DedicatedIngressRegistry registry = new DedicatedIngressRegistry();
        registry.register(49124, 76561198000000002L, 5L);
        assertEquals(0L,
                registry.resolve(new InetSocketAddress("127.0.0.1", 49124), 6L));
        assertEquals(0L,
                registry.resolve(new InetSocketAddress("127.0.0.1", 49124), 5L));
    }
}
