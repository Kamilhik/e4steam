package link.e4steam.api.capability;

/** Built-in capability ids understood by the 0.x addon API foundation. */
public final class Capabilities {
    /** Observe safe session snapshots. */
    public static final CapabilityId SESSION_OBSERVE = id("session.observe");
    /** Request non-security-sensitive session control operations. */
    public static final CapabilityId SESSION_CONTROL = id("session.control");
    /** Observe dedicated runtime state when that backend exists. */
    public static final CapabilityId DEDICATED_OBSERVE = id("dedicated.observe");
    /** Perform explicitly authorized dedicated administration. */
    public static final CapabilityId DEDICATED_ADMIN = id("dedicated.admin");
    /** Propose public listing metadata without bypassing core policy. */
    public static final CapabilityId DEDICATED_PUBLICATION_PROPOSE = id("dedicated.publication.propose");
    /** Read safe Minecraft identity DTOs. */
    public static final CapabilityId IDENTITY_MINECRAFT_READ = id("identity.minecraft.read");
    /** Read consented Steam profile DTOs. */
    public static final CapabilityId IDENTITY_STEAM_PROFILE_READ = id("identity.steam.profile.read");
    /** Create an addon-owned lobby. */
    public static final CapabilityId LOBBY_CREATE = id("lobby.create");
    /** Search bounded lobby results. */
    public static final CapabilityId LOBBY_SEARCH = id("lobby.search");
    /** Write validated addon lobby metadata. */
    public static final CapabilityId LOBBY_METADATA_WRITE = id("lobby.metadata.write");
    /** Register a namespaced access mode before freeze. */
    public static final CapabilityId ACCESS_MODE_REGISTER = id("access.mode.register");
    /** Evaluate an optional access policy after mandatory core gates. */
    public static final CapabilityId ACCESS_POLICY_EVALUATE = id("access.policy.evaluate");
    /** Register a bounded versioned addon network channel. */
    public static final CapabilityId NETWORK_CHANNEL_REGISTER = id("network.channel.register");
    /** Register a bounded virtual UDP provider. */
    public static final CapabilityId UDP_PROVIDER_REGISTER = id("udp.provider.register");
    /** Contribute bounded UI actions. */
    public static final CapabilityId UI_CONTRIBUTE = id("ui.contribute");
    /** Register namespaced commands. */
    public static final CapabilityId COMMANDS_REGISTER = id("commands.register");
    /** Read addon-scoped configuration. */
    public static final CapabilityId CONFIG_READ = id("config.read");
    /** Write addon-scoped configuration. */
    public static final CapabilityId CONFIG_WRITE = id("config.write");
    /** Use private path-confined addon storage. */
    public static final CapabilityId STORAGE_PRIVATE = id("storage.private");
    /** Read allowlisted world settings. */
    public static final CapabilityId WORLD_SETTINGS_READ = id("world.settings.read");
    /** Propose allowlisted world setting changes. */
    public static final CapabilityId WORLD_SETTINGS_PROPOSE = id("world.settings.propose");
    /** Inspect bounded modpack compatibility metadata. */
    public static final CapabilityId MODPACK_INSPECT = id("modpack.inspect");
    /** Stage verified modpack files after explicit consent. */
    public static final CapabilityId MODPACK_STAGE = id("modpack.stage");
    /** Provide validated cosmetic assets. */
    public static final CapabilityId SKINS_PROVIDE = id("skins.provide");
    /** Contribute bounded sanitized diagnostics. */
    public static final CapabilityId DIAGNOSTICS_CONTRIBUTE = id("diagnostics.contribute");
    /** Read explicitly consented non-secret Steam profile data. */
    public static final CapabilityId STEAM_PROFILE_READ = id("steam.profile.read");

    private Capabilities() {
    }

    private static CapabilityId id(String value) {
        return new CapabilityId(value);
    }
}
