package link.e4steam.lan;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Server-thread operations. Adapters own scheduling and integrated-server lifetime. */
public final class LanServerSettingsApplier {
    private LanServerSettingsApplier() { }

    public static Object playerList(Object server) {
        return LanReflection.read(server, "getPlayerList", "getConfigurationManager",
                "method_3760", "func_71203_ab", "m_6846_");
    }

    public static boolean published(Object server) {
        return server != null && Boolean.TRUE.equals(LanReflection.read(server,
                "isPublished", "getPublic", "isPublic", "method_3860", "func_71344_c", "m_6992_"));
    }

    public static int port(Object server) {
        return ((Number) LanReflection.read(server, "getPort", "getServerPort",
                "method_3756", "func_71215_F", "m_7010_")).intValue();
    }

    public static void apply(Object server, LanSettings.Snapshot settings) {
        settings.validated();
        if (server == null) throw new IllegalStateException("No integrated server");
        Object players = playerList(server);
        // Resolve required hooks before changing state; mapping failures must be visible.
        Method online = required(server, 1, "setUsesAuthentication", "setOnlineMode",
                "method_3864", "func_71189_e", "m_129985_");
        Method pvp = LanReflection.method(server, 1, "setPvpAllowed", "setAllowPvp",
                "method_3815", "func_71188_g", "m_129997_");
        Method cheats = LanReflection.method(server, 1, "setCommandsAllowedForOtherPlayers", "setGuestCommandAccess");
        Object cheatOwner = cheats == null ? players : server;
        // Minecraft 26.3 split the old LAN "allow commands" option into the
        // world's command flag and IntegratedServer's guest permission flag.
        // The vanilla LAN screen updates both; changing only guest access leaves
        // the world permission gate closed and command actions still report
        // insufficient permissions.
        Method worldCommands = LanReflection.method(server, 1, "setWorldAllowCommands");
        if (cheats == null) cheats = required(players, 1, "setAllowCommandsForAllPlayers",
                "setAllowCheatsForAllPlayers", "setCommandsAllowedForAll", "method_14607",
                "func_72387_b", "m_11284_");
        Method mode = LanReflection.method(server, 1, "setGameTypeForOtherPlayers");
        if (mode == null) mode = required(server, 1, "setDefaultGameType", "setGameType",
                "method_3838", "func_71235_a", "m_7835_");
        Object selectedMode = LanReflection.gameMode(mode.getParameterTypes()[0], settings.gameMode());
        Field maximum = LanReflection.field(players, "maxPlayers", "field_14347", "field_72405_c", "f_11193_");
        // 26.x moved capacity to IntegratedServer.getMaxPlayers(); e4steam's mixin owns it.
        if (maximum == null && LanReflection.method(players, 0, "getMaxPlayers", "method_14592", "m_11310_") == null
                && LanReflection.method(server, 1, "setGameTypeForOtherPlayers", "setGuestCommandAccess") == null)
            throw new IllegalStateException("Missing Minecraft player capacity hook");
        Object rules = pvp == null ? gameRules(server) : null;
        Field pvpRule = rules == null ? null : LanReflection.field(rules, "PVP", "field_62434");
        Method ruleSetter = rules == null ? null : required(rules, 3, "set", "method_76186");
        if (rules != null && pvpRule == null) throw new IllegalStateException("Missing Minecraft PvP game rule");

        LanReflection.call(server, online, settings.onlineMode());
        if (pvp != null) LanReflection.call(server, pvp, settings.pvp());
        else {
            try { LanReflection.call(rules, ruleSetter, pvpRule.get(null), settings.pvp(), server); }
            catch (IllegalAccessException failure) { throw new IllegalStateException(failure); }
        }
        LanReflection.call(cheatOwner, cheats, settings.allowCheats());
        if (worldCommands != null) LanReflection.call(server, worldCommands, settings.allowCheats());
        LanReflection.call(server, mode, selectedMode);
        if (maximum != null) LanReflection.write(players, maximum, settings.maxPlayers());
        LanSettings.activate(server, settings);
        refreshCommandPermissions(players);
    }

    /** Re-send the effective permission level and command tree to already connected players. */
    private static void refreshCommandPermissions(Object players) {
        Method list = LanReflection.method(players, 0, "getPlayers", "method_14571", "func_181057_v", "m_11314_");
        Object connected;
        if (list != null) {
            connected = LanReflection.call(players, list);
        } else {
            Field field = LanReflection.field(players, "players", "playerEntityList", "field_72404_b");
            if (field == null) return; // Very old adapters have no permission-sync packet.
            try { connected = field.get(players); }
            catch (IllegalAccessException failure) { throw new IllegalStateException("Cannot read connected LAN players", failure); }
        }
        if (!(connected instanceof Iterable<?>)) throw new IllegalStateException("Invalid connected LAN player list");
        Method sync = LanReflection.method(players, 1, "sendPlayerPermissionLevel", "sendCommandTree",
                "method_14576", "func_187243_f", "m_11289_");
        // Minecraft 1.7/1.8 evaluates the flag directly and has no command tree to resend.
        if (sync == null) return;
        for (Object player : (Iterable<?>) connected) LanReflection.call(players, sync, player);
    }

    public static boolean publish(Object server, LanSettings.Snapshot settings) {
        Method publish = LanReflection.method(server, 3, "publishServer", "shareToLAN", "func_195457_a", "method_3763", "m_7386_");
        if (publish != null) {
            Class<?> first = publish.getParameterTypes()[0];
            // 26.3 replaced GameType with MultiplayerScope in the three-argument overload.
            Object selection = first.getSimpleName().equals("MultiplayerScope")
                    ? lanScope(first) : LanReflection.gameMode(first, settings.gameMode());
            return Boolean.TRUE.equals(LanReflection.call(server, publish,
                    selection, settings.allowCheats(), settings.port()));
        }
        publish = LanReflection.method(server, 4, "publishServer");
        if (publish != null) {
            Object scope = lanScope(publish.getParameterTypes()[0]);
            return Boolean.TRUE.equals(LanReflection.call(server, publish, scope,
                    LanReflection.gameMode(publish.getParameterTypes()[1], settings.gameMode()),
                    settings.allowCheats(), settings.port()));
        }
        publish = required(server, 2, "shareToLAN", "func_71206_a");
        return LanReflection.call(server, publish,
                LanReflection.gameMode(publish.getParameterTypes()[0], settings.gameMode()),
                settings.allowCheats()) != null;
    }

    private static Object gameRules(Object server) {
        Method direct = LanReflection.method(server, 0, "getGameRules");
        if (direct != null) return LanReflection.call(server, direct);
        Object world = LanReflection.read(server, "overworld", "method_30002", "m_129783_");
        return LanReflection.read(world, "getGameRules", "method_64395");
    }
    private static Object lanScope(Class<?> type) {
        if (type.isEnum()) {
            for (Object value : type.getEnumConstants())
                if (((Enum<?>) value).name().equals("LAN")) return value;
        }
        throw new IllegalStateException("Minecraft LAN scope is unavailable");
    }

    private static Method required(Object owner, int arguments, String... names) {
        Method method = LanReflection.method(owner, arguments, names);
        if (method == null) throw new IllegalStateException("Missing Minecraft hook: " + names[0]);
        return method;
    }
}
