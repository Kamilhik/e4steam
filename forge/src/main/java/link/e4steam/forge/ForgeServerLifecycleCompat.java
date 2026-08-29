package link.e4steam.forge;

import link.e4steam.E4steamDedicated;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/** Resolves the Forge server lifecycle event rename between 1.17 and 1.18. */
final class ForgeServerLifecycleCompat {
    private static final String[] STARTED_EVENTS = {
            "net.minecraftforge.event.server.ServerStartedEvent",
            "net.minecraftforge.fmlserverevents.FMLServerStartedEvent"
    };
    private static final String[] STOPPING_EVENTS = {
            "net.minecraftforge.event.server.ServerStoppingEvent",
            "net.minecraftforge.fmlserverevents.FMLServerStoppingEvent"
    };

    private ForgeServerLifecycleCompat() {
    }

    static void register(IEventBus eventBus) {
        registerFirstAvailable(eventBus, STARTED_EVENTS, E4steamDedicated::minecraftReady);
        registerFirstAvailable(eventBus, STOPPING_EVENTS, E4steamDedicated::minecraftStopped);
    }

    private static void registerFirstAvailable(
            IEventBus eventBus,
            String[] candidates,
            Runnable handler
    ) {
        ClassLoader loader = ForgeServerLifecycleCompat.class.getClassLoader();
        for (String candidate : candidates) {
            try {
                Class<? extends Event> eventType = Class.forName(candidate, false, loader)
                        .asSubclass(Event.class);
                registerTyped(eventBus, eventType, handler);
                return;
            } catch (ClassNotFoundException ignored) {
                // Try the event name used by the other supported Forge generation.
            }
        }
        throw new IllegalStateException(
                "No supported Forge server lifecycle event API is available"
        );
    }

    private static <T extends Event> void registerTyped(
            IEventBus eventBus,
            Class<T> eventType,
            Runnable handler
    ) {
        eventBus.addListener(
                EventPriority.NORMAL,
                false,
                eventType,
                ignored -> handler.run()
        );
    }
}
