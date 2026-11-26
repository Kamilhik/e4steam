package link.e4steam.steam;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/** Share a pending name lookup without letting a canceled caller cancel another connection. */
final class SteamLookupSubscriptions {
    private SteamLookupSubscriptions() { }
    static <T> CompletableFuture<T> existing(String requested,String current,CompletableFuture<T> pending) throws IOException {
        if(pending==null||pending.isDone())return null;
        if(!requested.equals(current))throw new IOException("Поиск другого адреса ещё выполняется. Повторите подключение.");
        return detached(pending);
    }
    static <T> CompletableFuture<T> detached(CompletableFuture<T> pending) {
        return pending.thenApply(value->value);
    }
}
