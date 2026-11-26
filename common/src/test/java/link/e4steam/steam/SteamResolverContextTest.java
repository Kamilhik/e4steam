package link.e4steam.steam;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class SteamResolverContextTest {
    @Test void nestedStatusProbesRestoreTheOriginalResolverContext() {
        assertFalse(SteamResolverContext.isStatusProbe());
        assertEquals("resolved",SteamResolverContext.statusProbe(()->{
            assertTrue(SteamResolverContext.isStatusProbe());
            SteamResolverContext.statusProbe(()->{assertTrue(SteamResolverContext.isStatusProbe());return null;});
            assertTrue(SteamResolverContext.isStatusProbe());return "resolved";
        }));
        assertFalse(SteamResolverContext.isStatusProbe());
    }
    @Test void failedDnsProbeCannotMarkTheNextConnectionAsAStatusProbe() {
        assertThrows(IllegalStateException.class,()->SteamResolverContext.statusProbe(()->{throw new IllegalStateException("DNS failed");}));
        assertFalse(SteamResolverContext.isStatusProbe());
    }
    @Test void backgroundStatusCannotChangeTheConnectingThreadsContext() throws Exception {
        ExecutorService worker=Executors.newSingleThreadExecutor();
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        try {
            Future<Boolean> ping=worker.submit(()->SteamResolverContext.statusProbe(()->{
                entered.countDown();
                try {if(!release.await(5,TimeUnit.SECONDS))throw new AssertionError("probe not released");}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}
                return SteamResolverContext.isStatusProbe();
            }));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            assertFalse(SteamResolverContext.isStatusProbe());release.countDown();
            assertTrue(ping.get(5,TimeUnit.SECONDS));
            assertFalse(worker.submit(SteamResolverContext::isStatusProbe).get(5,TimeUnit.SECONDS));
        } finally {release.countDown();worker.shutdownNow();}
    }
}
