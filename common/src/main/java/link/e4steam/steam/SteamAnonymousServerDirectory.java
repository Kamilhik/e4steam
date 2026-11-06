package link.e4steam.steam;

import com.codedisaster.steamworks.*;
import link.e4steam.api.directory.PublicDirectoryService.DedicatedListing;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Steam-worker-owned bounded GameServer search. No raw query IP is exposed to addons. */
final class SteamAnonymousServerDirectory implements AutoCloseable {
    private final SteamMatchmakingServers browser = new SteamMatchmakingServers();
    private final Map<String,DedicatedListing> results = new LinkedHashMap<>();
    private SteamServerListRequest request;
    private SteamMatchmakingServerListResponse response;
    private CompletableFuture<List<DedicatedListing>> pending;
    private long deadline;
    private int limit;
    private boolean complete;

    CompletableFuture<List<DedicatedListing>> search(int requestedLimit) {
        if (pending != null) return pending;
        limit = Math.max(1, Math.min(100, requestedLimit));
        results.clear(); complete = false;
        pending = new CompletableFuture<>();
        CompletableFuture<List<DedicatedListing>> result = pending;
        deadline = System.currentTimeMillis() + 20_000L;
        response = new SteamMatchmakingServerListResponse() {
            @Override public void serverResponded(SteamServerListRequest handle, int index) { found(handle,index); }
            @Override public void serverFailedToRespond(SteamServerListRequest handle, int index) { found(handle,index); }
            @Override public void refreshComplete(SteamServerListRequest handle, Response status) { complete = true; }
        };
        try {
            request = browser.requestInternetServerList(480, new SteamMatchmakingKeyValuePair[]{
                    new SteamMatchmakingKeyValuePair("gamedir", "e4steam"),
                    new SteamMatchmakingKeyValuePair("gametagsand", SteamDedicatedAdvertisement.MARKER)
            }, response);
            if (request == null) throw new IllegalStateException("Steam rejected GameServer search");
        } catch (RuntimeException failure) {
            result.completeExceptionally(new IOException("Anonymous Steam directory unavailable", failure));
            cleanup(); pending = null;
        }
        return result;
    }

    private void found(SteamServerListRequest handle, int index) {
        if (pending == null || complete || results.size() >= limit) return;
        try {
            SteamMatchmakingGameServerItem item = new SteamMatchmakingGameServerItem();
            if (!browser.getServerDetails(handle,index,item) || item.getAppID() != 480) return;
            DedicatedListing listing = SteamDedicatedAdvertisement.decode(item.getGameTags(), System.currentTimeMillis());
            results.put(listing.target().value(), listing);
            if (results.size() >= limit) complete = true;
        } catch (RuntimeException invalid) { /* Foreign or stale advertisements are never trusted. */ }
    }

    void tick(long nowMillis) {
        if (pending == null || (!complete && nowMillis < deadline)) return;
        CompletableFuture<List<DedicatedListing>> done = pending;
        pending = null;
        List<DedicatedListing> snapshot = new ArrayList<>(results.values());
        // Never dispose the response while executing its native callback.
        cleanup(); results.clear(); done.complete(java.util.Collections.unmodifiableList(snapshot));
    }
    boolean keepsRuntimeAlive() { return pending != null; }
    private void cleanup() {
        if (request != null) {
            browser.cancelQuery(request); browser.releaseRequest(request); request = null;
        }
        if (response != null) { response.dispose(); response = null; }
        // SteamMatchmakingServers owns no callback; its inherited dispose() passes
        // a null callback to this bundled JNI version and causes a native crash.
    }
    @Override public void close() {
        CompletableFuture<List<DedicatedListing>> done = pending; pending = null;
        cleanup(); results.clear();
        if (done != null) done.completeExceptionally(new IOException("Steam directory closed"));
    }
}
