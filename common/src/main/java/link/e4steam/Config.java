package link.e4steam;

import folk.sisby.kaleido.api.ReflectiveConfig;
import folk.sisby.kaleido.lib.quiltconfig.api.annotations.Comment;
import folk.sisby.kaleido.lib.quiltconfig.api.values.TrackedValue;

public class Config extends ReflectiveConfig {
    public static final Config INSTANCE = Config.createToml(Agnos.configDir(), "e4steam", "e4steam", Config.class);

    @Comment("Whether to hide the domain on chat and only allow copying")
    public final TrackedValue<Boolean> hideDomainInChat = this.value(false);

    @Comment("Allows use of dedicated server commands such as /ban in a shared LAN world")
    public final TrackedValue<Boolean> restoreDedicatedCommands = this.value(true);

    @Comment("Whether to share opened LAN worlds through Steam P2P and Valve relays")
    public final TrackedValue<Boolean> hostEnabled = this.value(true);

    @Comment("Fallback UDP port for voice mods and other UDP services; Simple Voice Chat and Plasmo Voice are detected automatically; use 0 to disable the fallback")
    public final TrackedValue<Integer> voiceChatPort = this.value(24454);

    @Comment("Experimental: on Linux and macOS, relaunch Minecraft before LWJGL starts so Steam can inject its overlay; set true to enable")
    public final TrackedValue<Boolean> overlayRelaunch = this.value(false);

    @Comment("Authenticate ordinary LAN/TCP players with Minecraft services; Steam players always use Steam authentication")
    public final TrackedValue<Boolean> lanOnlineMode = this.value(true);

    @Comment("Whether PvP is enabled in an opened integrated LAN world")
    public final TrackedValue<Boolean> lanPvp = this.value(true);

    @Comment("Total integrated-server player limit, including the host")
    public final TrackedValue<Integer> lanMaxPlayers = this.value(8);

    @Comment("Local Minecraft listener port used when opening a world to LAN")
    public final TrackedValue<Integer> lanPort = this.value(25565);
    public final TrackedValue<String> publicServerName = this.value("");

    @Comment("Default game mode applied when opening or editing a shared LAN world")
    public final TrackedValue<String> lanGameMode = this.value("survival");

    @Comment("Whether commands/cheats are enabled for players in the integrated LAN world")
    public final TrackedValue<Boolean> lanAllowCheats = this.value(false);
}
