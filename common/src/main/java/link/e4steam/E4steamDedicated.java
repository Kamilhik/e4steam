package link.e4steam;

import link.e4steam.internal.addon.AddonCandidate;
import link.e4steam.internal.api.CoreApiPlatform;
import link.e4steam.internal.api.RuntimeEnvironment;
import link.e4steam.internal.dedicated.DedicatedRuntimeConfig;
import link.e4steam.internal.dedicated.DedicatedServerController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetAddress;
import java.net.SocketAddress;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.InputStream;
import java.util.Properties;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Loader-neutral bootstrap for an opt-in headless dedicated server process. */
public final class E4steamDedicated {
    private static final Logger LOGGER = LogManager.getLogger("e4steam");
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static volatile DedicatedServerController controller;

    private E4steamDedicated() {
    }

    public static void init(RuntimeEnvironment environment, List<AddonCandidate> candidates) {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        DedicatedRuntimeConfig config;
        try {
            config = DedicatedRuntimeConfig.load(
                    Paths.get(System.getProperty("user.dir", "."),
                            "config", "e4steam-dedicated.toml"),
                    System.getenv(),
                    System.getProperties()
            );
        } catch (IOException | RuntimeException failure) {
            throw new IllegalStateException("Invalid e4steam dedicated configuration", failure);
        }
        if (config.enabled()) {
            validateServerProperties();
        }
        controller = DedicatedServerController.install(config);
        CoreApiPlatform.start(environment, candidates);
        if (config.enabled()) {
            LOGGER.info("e4steam dedicated mode enabled: {}", config);
        } else {
            LOGGER.info("e4steam dedicated backend is installed but disabled");
        }
    }

    public static void minecraftListening(InetAddress bindAddress, int port) {
        DedicatedServerController active = controller;
        if (active != null) active.minecraftListening(bindAddress, port);
    }

    /** Called by the loader only after the dedicated world and tick loop are ready. */
    public static void minecraftReady() {
        DedicatedServerController active = controller;
        if (active != null) active.minecraftReady();
    }

    public static void validateMinecraftBind(InetAddress bindAddress) {
        DedicatedServerController active = controller;
        if (active != null) active.validateMinecraftBind(bindAddress);
    }

    public static void minecraftStopped() {
        DedicatedServerController active = controller;
        if (active != null) active.minecraftStopped();
    }

    public static long authenticatedMinecraftPeer(SocketAddress remoteAddress) {
        DedicatedServerController active = controller;
        return active == null ? 0L : active.authenticatedMinecraftPeer(remoteAddress);
    }

    public static boolean requiresAuthenticatedIngress() {
        DedicatedServerController active = controller;
        return active != null && active.requiresAuthenticatedIngress();
    }

    /** Registers console/op-only headless commands without loading client UI classes. */
    public static void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("e4steam-dedicated")
                .requires(source -> source.hasPermission(4))
                .then(Commands.literal("status").executes(context -> {
                    DedicatedServerController active = controller;
                    String status = active == null ? "unavailable" : active.safeStatus();
                    DedicatedCommandCompat.success(context.getSource(), status);
                    return 1;
                }))
                .then(Commands.literal("descriptor").executes(context -> {
                    DedicatedServerController active = controller;
                    String descriptor = active == null ? "" : active.descriptor();
                    if (descriptor.isEmpty()) {
                        DedicatedCommandCompat.failure(context.getSource(),
                                "e4steam dedicated is not accepting connections");
                        return 0;
                    }
                    DedicatedCommandCompat.success(context.getSource(),
                            "e4steam descriptor: " + descriptor);
                    return 1;
                }))
                .then(Commands.literal("stop").executes(context -> {
                    DedicatedServerController active = controller;
                    if (active != null) active.minecraftStopped();
                    DedicatedCommandCompat.success(context.getSource(),
                            "e4steam dedicated is draining");
                    return 1;
                }))
                .then(identityCommand("allow", true, false))
                .then(identityCommand("unallow", false, false))
                .then(identityCommand("ban", true, true))
                .then(identityCommand("unban", false, true)));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack>
    identityCommand(String literal, boolean present, boolean ban) {
        return Commands.literal(literal).then(Commands.argument("identity", StringArgumentType.word())
                .executes(context -> {
                    DedicatedServerController active = controller;
                    if (active == null) {
                        DedicatedCommandCompat.failure(context.getSource(),
                                "e4steam dedicated is unavailable");
                        return 0;
                    }
                    String identity = StringArgumentType.getString(context, "identity");
                    try {
                        boolean changed = ban ? active.updateBan(identity, present)
                                : active.updateWhitelist(identity, present);
                        String normalized = active.normalizedIdentity(identity);
                        DedicatedCommandCompat.success(context.getSource(),
                                "e4steam access " + (changed ? "updated: " : "unchanged: ")
                                        + normalized);
                        return changed ? 1 : 0;
                    } catch (RuntimeException failure) {
                        DedicatedCommandCompat.failure(context.getSource(),
                                "Expected a SteamID64 or e4steam-derived UUID");
                        return 0;
                    }
                }));
    }

    private static void validateServerProperties() {
        Path path = Paths.get(System.getProperty("user.dir", "."), "server.properties")
                .toAbsolutePath().normalize();
        Properties properties = new Properties();
        try {
            if (Files.isSymbolicLink(path)
                    || !Files.isRegularFile(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException(
                        "e4steam dedicated requires server.properties with server-ip=127.0.0.1");
            }
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Could not validate dedicated server.properties", failure);
        }
        link.e4steam.internal.dedicated.DedicatedServerPropertiesValidator.Validation validation =
                link.e4steam.internal.dedicated.DedicatedServerPropertiesValidator.validate(properties);
        if (!validation.allowed()) {
            throw new IllegalStateException("Unsafe e4steam dedicated server.properties: "
                    + validation.category());
        }
    }
}
