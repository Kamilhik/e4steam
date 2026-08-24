package link.e4steam.api.scheduler;

/** Named execution target for addon callbacks and tasks. */
public enum ExecutionContext {
    /** Minecraft client/main thread; blocking work is forbidden. */
    MINECRAFT_CLIENT,
    /** Integrated server main thread; blocking work is forbidden. */
    INTEGRATED_SERVER,
    /** Dedicated server main thread when supported. */
    DEDICATED_SERVER,
    /** Serialized e4steam control executor, never the native callback thread. */
    E4STEAM_CONTROL,
    /** Bounded worker pool for addon-owned blocking work. */
    ADDON_WORKER,
    /** Bounded scheduled/timer executor. */
    TIMER
}
