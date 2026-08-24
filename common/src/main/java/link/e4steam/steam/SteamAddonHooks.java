package link.e4steam.steam;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Optional implementation boundary between the Steam transport and the Addon API.
 * Retro runtimes compile this tiny Java 8 class without pulling the modern API
 * implementation into old Minecraft processes.
 */
public final class SteamAddonHooks {
    public interface Delegate {
        void bridgeReady(SteamConnectionBridge bridge);
        void bridgeClosed(SteamConnectionBridge bridge);
        void tick();
        void accept(SteamConnectionBridge bridge, byte frameType, byte[] payload);
    }

    private static final Delegate NOOP = new Delegate() {
        @Override public void bridgeReady(SteamConnectionBridge bridge) { }
        @Override public void bridgeClosed(SteamConnectionBridge bridge) { }
        @Override public void tick() { }
        @Override public void accept(
                SteamConnectionBridge bridge, byte frameType, byte[] payload) { }
    };
    private static final AtomicReference<Delegate> CURRENT =
            new AtomicReference<Delegate>(NOOP);

    private SteamAddonHooks() {
    }

    public static AutoCloseable install(Delegate delegate) {
        if (delegate == null) throw new NullPointerException("delegate");
        if (!CURRENT.compareAndSet(NOOP, delegate)) {
            throw new IllegalStateException("Addon network hooks are already installed");
        }
        return new AutoCloseable() {
            @Override public void close() {
                CURRENT.compareAndSet(delegate, NOOP);
            }
        };
    }

    static void bridgeReady(SteamConnectionBridge bridge) {
        CURRENT.get().bridgeReady(bridge);
    }

    static void bridgeClosed(SteamConnectionBridge bridge) {
        CURRENT.get().bridgeClosed(bridge);
    }

    static void tick() {
        CURRENT.get().tick();
    }

    static void accept(SteamConnectionBridge bridge, byte frameType, byte[] payload) {
        CURRENT.get().accept(bridge, frameType, payload);
    }
}
