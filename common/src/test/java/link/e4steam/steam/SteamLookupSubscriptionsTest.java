package link.e4steam.steam;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import static org.junit.jupiter.api.Assertions.*;

class SteamLookupSubscriptionsTest {
    @Test void sameAddressCanHaveIndependentPingAndConnectorSubscribers() throws Exception {
        CompletableFuture<String> nativeSearch=new CompletableFuture<>();
        CompletableFuture<String> ping=SteamLookupSubscriptions.existing("kamil","kamil",nativeSearch);
        CompletableFuture<String> connect=SteamLookupSubscriptions.existing("kamil","kamil",nativeSearch);
        ping.cancel(false);
        assertFalse(nativeSearch.isCancelled());assertFalse(connect.isDone());
        nativeSearch.complete("target");assertEquals("target",connect.join());
    }
    @Test void searchFailuresReachBothSubscribersAndPermitANewSearch() throws Exception {
        CompletableFuture<String> nativeSearch=new CompletableFuture<>();
        CompletableFuture<String> a=SteamLookupSubscriptions.existing("kamil","kamil",nativeSearch);
        CompletableFuture<String> b=SteamLookupSubscriptions.existing("kamil","kamil",nativeSearch);
        nativeSearch.completeExceptionally(new IOException("timeout"));
        assertThrows(CompletionException.class,a::join);assertThrows(CompletionException.class,b::join);
        assertNull(SteamLookupSubscriptions.existing("kamil","kamil",nativeSearch));
    }
    @Test void differentAddressesCannotReceiveEachOthersNativeResult() {
        CompletableFuture<String> nativeSearch=new CompletableFuture<>();
        assertThrows(IOException.class,()->SteamLookupSubscriptions.existing("other","kamil",nativeSearch));
        assertFalse(nativeSearch.isDone());
    }
}
