package link.e4steam.steam;

import java.util.function.Supplier;

/** Thread-scoped status probes cannot join/stop a real Steam connection on another thread. */
public final class SteamResolverContext {
    private static final ThreadLocal<Boolean> STATUS=new ThreadLocal<>();
    private SteamResolverContext() { }
    public static boolean isStatusProbe(){return Boolean.TRUE.equals(STATUS.get());}
    public static <T> T statusProbe(Supplier<T> action) {
        Boolean previous=STATUS.get();STATUS.set(true);
        try{return action.get();}
        finally {if(previous==null)STATUS.remove();else STATUS.set(previous);}
    }
}
