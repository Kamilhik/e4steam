package link.e4steam.steam;

import link.e4steam.Agnos;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Java 8-compatible Unix overlay relaunch for retro artifacts. */
public final class SteamOverlayRelauncher {
    private static final Logger LOGGER = LogManager.getLogger("e4steam");
    private static final String MARKER_ENV = "E4STEAM_OVERLAY_RELAUNCHED";
    private static final String MARKER_PROPERTY = "e4steam.overlayRelaunched";
    private static final String OVERLAY_RELAUNCH_PROPERTY = "e4steam.overlayRelaunch";
    private static final String CAPTURED_STDIN_FILE_PROPERTY = "e4steam.capturedStdinFile";
    private static final String CAPTURED_STDIN_TRUNCATED_PROPERTY =
            "e4steam.capturedStdinTruncated";
    private static final long MAX_CAPTURE_BYTES = 1_048_576L;
    private static final int MAX_PUBLISHED_GAME_ARGUMENT_BYTES = 1_048_576;
    private static final int MAX_PUBLISHED_GAME_ARGUMENTS = 4_096;
    private static final char PRISM_ARGUMENT_SEPARATOR = 31;
    private static final String PRISM_MAIN_CLASS_PROPERTY =
            "org.prismlauncher.launch.mainclass";
    private static final String PRISM_GAME_ARGUMENTS_PROPERTY =
            "org.prismlauncher.launch.gameargs";
    private static final String MULTIMC_MAIN_CLASS_PROPERTY =
            "org.multimc.launch.mainclass";
    private static final String MULTIMC_GAME_ARGUMENTS_PROPERTY =
            "org.multimc.launch.gameargs";
    private static final String[] STDIN_LAUNCHER_WRAPPERS = {
            "org.prismlauncher.EntryPoint",
            "org.multimc.EntryPoint"
    };

    private SteamOverlayRelauncher() {
    }

    public static void relaunchIfNeeded() {
        if (!Agnos.isClient() || isRelaunched(
                System.getenv(MARKER_ENV), System.getProperty(MARKER_PROPERTY)
        )) return;
        NativePlatform platform = currentPlatform();
        String insertionVariable = insertLibrariesEnvName(platform);
        if (insertionVariable == null
                || !relaunchEnabled(System.getProperty(OVERLAY_RELAUNCH_PROPERTY))) {
            return;
        }

        Optional<Path> overlay = SteamOverlayLoader.findOverlayLibrary();
        if (!overlay.isPresent()) {
            LOGGER.info(
                    "Steam Overlay renderer was not found; continuing without overlay relaunch"
            );
            return;
        }

        Optional<List<String>> originalCommand = currentJvmCommandLine(platform);
        if (!originalCommand.isPresent()) {
            LOGGER.warn(
                    "Could not reconstruct the JVM launch command; continuing without overlay relaunch"
            );
            return;
        }

        List<String> command = new ArrayList<String>(originalCommand.get());
        String wrapper = detectStdinLauncherWrapper(command);
        Optional<Path> capturedStdin = capturedStdinFile();
        if (wrapper != null && !capturedStdin.isPresent()) {
            Optional<List<String>> direct = rewritePublishedLauncherCommand(
                    command,
                    wrapper,
                    publishedMainClass(wrapper),
                    publishedGameArguments(wrapper)
            );
            if (!direct.isPresent()) {
                LOGGER.warn(
                        "Detected {} but it did not publish a safe direct launch command and "
                                + "no valid e4steam stdin-agent capture is available; "
                                + "continuing without overlay relaunch",
                        wrapper
                );
                return;
            }
            command = new ArrayList<String>(direct.get());
            preservePublishedLauncherProperties(command);
            LOGGER.info(
                    "Recovered the direct Minecraft launch command from {} without a Java agent",
                    wrapper
            );
        }
        addVmOption(command, "-D" + MARKER_PROPERTY + "=true");
        addMacOsFirstThreadOption(command, platform);
        addEarlyProgressWindowFlagIfForgeModern(command);

        Process child;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            Path working = Paths.get(System.getProperty("user.dir", "."))
                    .toAbsolutePath().normalize();
            if (Files.isDirectory(working)) builder.directory(working.toFile());
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            if (!capturedStdin.isPresent()) {
                builder.redirectInput(ProcessBuilder.Redirect.INHERIT);
            }

            Map<String, String> environment = builder.environment();
            environment.put(insertionVariable, mergeInsertedLibrary(
                    environment.get(insertionVariable), overlay.get().toString()
            ));
            configureSteamAppEnvironment(environment);
            environment.put(MARKER_ENV, "1");

            LOGGER.info(
                    "Relaunching Minecraft with the Steam Overlay renderer injected"
            );
            child = builder.start();
            if (capturedStdin.isPresent()) replayCapturedStdin(child, capturedStdin.get());
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn(
                    "Could not relaunch Minecraft for the Steam Overlay; continuing normally",
                    failure
            );
            return;
        }

        int exitCode;
        try {
            exitCode = child.waitFor();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            child.destroy();
            exitCode = 1;
        }

        // Forge 1.7-1.12 installs FMLSecurityManager and rejects a direct
        // System.exit() from mod code. The tiny branch-specific bridge is in
        // FML's allowlisted namespace and avoids initializing FMLCommonHandler
        // while Forge's Loader is still being constructed. Newer Forge and
        // non-Forge loaders fall through to the ordinary JVM exit below.
        if (requestLegacyForgeExit(exitCode)) {
            LOGGER.error("Legacy Forge shutdown bridge returned without terminating the JVM");
        }
        System.exit(exitCode);
    }

    static boolean requestLegacyForgeExit(int exitCode) {
        String[] earlyExitBridges = {
                "net.minecraftforge.fml.e4steam.E4steamEarlyExit",
                "cpw.mods.fml.e4steam.E4steamEarlyExit"
        };
        for (String bridgeName : earlyExitBridges) {
            try {
                Class<?> bridgeClass = Class.forName(bridgeName);
                bridgeClass.getMethod("exit", Integer.TYPE).invoke(null, exitCode);
                return true;
            } catch (ClassNotFoundException absent) {
                // The matching branch adapter is not installed.
            } catch (ReflectiveOperationException incompatible) {
                LOGGER.warn(
                        "Could not invoke legacy Forge early shutdown bridge {}",
                        bridgeName,
                        incompatible
                );
                return false;
            } catch (LinkageError incompatible) {
                LOGGER.warn(
                        "Could not link legacy Forge early shutdown bridge {}",
                        bridgeName,
                        incompatible
                );
                return false;
            }
        }

        // Retain the established fallback for environments where Forge is
        // already fully initialized. The early 1.7-1.12 artifacts always ship
        // one of the bridges above, so this path cannot initialize Loader
        // during their core-plugin construction.
        String[] handlers = {
                "net.minecraftforge.fml.common.FMLCommonHandler",
                "cpw.mods.fml.common.FMLCommonHandler"
        };
        for (String handlerName : handlers) {
            try {
                Class<?> handlerClass = Class.forName(handlerName);
                Object handler = handlerClass.getMethod("instance").invoke(null);
                handlerClass.getMethod("exitJava", Integer.TYPE, Boolean.TYPE)
                        .invoke(handler, exitCode, false);
                return true;
            } catch (ClassNotFoundException absent) {
                // Try the other legacy Forge package, then the normal exit.
            } catch (ReflectiveOperationException incompatible) {
                LOGGER.warn(
                        "Could not delegate JVM shutdown to {}",
                        handlerName,
                        incompatible
                );
                return false;
            } catch (LinkageError incompatible) {
                LOGGER.warn(
                        "Could not link legacy Forge shutdown handler {}",
                        handlerName,
                        incompatible
                );
                return false;
            }
        }
        return false;
    }

    static boolean isRelaunched(String environmentMarker, String propertyMarker) {
        return environmentMarker != null || Boolean.parseBoolean(propertyMarker);
    }

    static String insertLibrariesEnvName(NativePlatform platform) {
        if (platform == null) return null;
        if (platform.operatingSystem() == NativePlatform.OperatingSystem.LINUX
                && platform.architecture() == NativePlatform.Architecture.X86_64) {
            return "LD_PRELOAD";
        }
        if (platform.operatingSystem() == NativePlatform.OperatingSystem.MACOS) {
            return "DYLD_INSERT_LIBRARIES";
        }
        return null;
    }

    static String mergeInsertedLibrary(String existing, String overlay) {
        String checked = overlay == null ? "" : overlay.trim();
        if (checked.isEmpty()) throw new IllegalArgumentException("overlay");
        if (existing == null || existing.trim().isEmpty()) return checked;
        return existing + File.pathSeparator + checked;
    }

    static void configureSteamAppEnvironment(Map<String, String> environment) {
        if (environment == null) throw new IllegalArgumentException("environment");
        environment.put("SteamAppId", "480");
        environment.put("SteamGameId", "480");
        environment.put("SteamOverlayGameId", "480");
    }

    static String detectStdinLauncherWrapper(List<String> command) {
        if (command == null) return null;
        for (String argument : command) {
            if (argument == null) continue;
            for (String wrapper : STDIN_LAUNCHER_WRAPPERS) {
                if (wrapper.equals(argument)) return wrapper;
            }
        }
        return null;
    }

    static Optional<List<String>> rewritePublishedLauncherCommand(
            List<String> command,
            String wrapper,
            String mainClass,
            String encodedGameArguments
    ) {
        if (command == null || wrapper == null || !isSafeMainClass(mainClass)
                || encodedGameArguments == null
                || encodedGameArguments.length() > MAX_PUBLISHED_GAME_ARGUMENT_BYTES) {
            return Optional.empty();
        }
        int wrapperIndex = command.indexOf(wrapper);
        if (wrapperIndex <= 0) return Optional.empty();

        List<String> gameArguments = splitPublishedGameArguments(encodedGameArguments);
        if (gameArguments.size() > MAX_PUBLISHED_GAME_ARGUMENTS) return Optional.empty();
        for (String argument : gameArguments) {
            if (argument.indexOf(0) >= 0) return Optional.empty();
        }

        List<String> direct = new ArrayList<String>(
                command.subList(0, wrapperIndex + 1)
        );
        direct.set(wrapperIndex, mainClass);
        direct.addAll(gameArguments);
        return Optional.of(Collections.unmodifiableList(direct));
    }

    static List<String> splitPublishedGameArguments(String encoded) {
        if (encoded == null || encoded.isEmpty()) return Collections.emptyList();
        List<String> arguments = new ArrayList<String>();
        int start = 0;
        for (int index = 0; index <= encoded.length(); index++) {
            if (index != encoded.length()
                    && encoded.charAt(index) != PRISM_ARGUMENT_SEPARATOR) {
                continue;
            }
            arguments.add(encoded.substring(start, index));
            start = index + 1;
        }
        return Collections.unmodifiableList(arguments);
    }

    static boolean relaunchEnabled(String value) {
        return value != null && Boolean.parseBoolean(value);
    }

    static Optional<List<String>> parseNullSeparatedCommand(byte[] raw) {
        if (raw == null || raw.length == 0) return Optional.empty();
        List<String> result = new ArrayList<String>();
        int start = 0;
        for (int index = 0; index <= raw.length; index++) {
            if (index != raw.length && raw[index] != 0) continue;
            if (index > start) {
                result.add(new String(raw, start, index - start, StandardCharsets.UTF_8));
            }
            start = index + 1;
        }
        return result.isEmpty()
                ? Optional.<List<String>>empty()
                : Optional.of(Collections.unmodifiableList(result));
    }

    static Optional<Path> validateCapturedStdin(Path path, boolean truncated) {
        if (path == null || truncated) return Optional.empty();
        try {
            Path normalized = path.toAbsolutePath().normalize();
            if (Files.isSymbolicLink(normalized)
                    || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
                return Optional.empty();
            }
            long size = Files.size(normalized);
            if (size < 0L || size > MAX_CAPTURE_BYTES) return Optional.empty();
            return Optional.of(normalized);
        } catch (IOException | SecurityException failure) {
            return Optional.empty();
        }
    }

    static void addVmOption(List<String> command, String option) {
        if (command == null || command.isEmpty() || option == null || option.isEmpty()) return;
        for (String argument : command) {
            if (option.equals(argument)) return;
        }
        // Index one is always after the java executable and before the main
        // class/-jar boundary, so Java consumes the value as a VM option.
        command.add(1, option);
    }

    static void addMacOsFirstThreadOption(List<String> command, NativePlatform platform) {
        if (platform == null
                || platform.operatingSystem() != NativePlatform.OperatingSystem.MACOS) {
            return;
        }
        addVmOption(command, "-XstartOnFirstThread");
    }

    /** True only for the Unix path where overlay injection requires a new JVM. */
    public static boolean isUnixOverlayRelaunchRequested() {
        NativePlatform platform = currentPlatform();
        return insertLibrariesEnvName(platform) != null
                && relaunchEnabled(System.getProperty(OVERLAY_RELAUNCH_PROPERTY));
    }

    /** True when the Unix overlay relaunch path targets macOS. */
    public static boolean isMacOsUnixOverlayRelaunchRequested() {
        NativePlatform platform = currentPlatform();
        return platform != null
                && platform.operatingSystem() == NativePlatform.OperatingSystem.MACOS
                && insertLibrariesEnvName(platform) != null
                && relaunchEnabled(System.getProperty(OVERLAY_RELAUNCH_PROPERTY));
    }

    /** True only inside the replacement macOS JVM that has the overlay injected. */
    public static boolean isMacOsOverlayRelaunched() {
        NativePlatform platform = currentPlatform();
        return platform != null
                && platform.operatingSystem() == NativePlatform.OperatingSystem.MACOS
                && relaunchEnabled(System.getProperty(OVERLAY_RELAUNCH_PROPERTY))
                && isRelaunched(
                        System.getenv(MARKER_ENV),
                        System.getProperty(MARKER_PROPERTY)
                );
    }

    private static boolean isSafeMainClass(String mainClass) {
        if (mainClass == null || mainClass.isEmpty() || mainClass.length() > 512) return false;
        boolean previousDot = true;
        for (int index = 0; index < mainClass.length(); index++) {
            char value = mainClass.charAt(index);
            if (value == '.') {
                if (previousDot) return false;
                previousDot = true;
                continue;
            }
            if (previousDot) {
                if (!Character.isJavaIdentifierStart(value)) return false;
            } else if (!(Character.isJavaIdentifierPart(value) || value == '$')) {
                return false;
            }
            previousDot = false;
        }
        return !previousDot;
    }

    private static String publishedMainClass(String wrapper) {
        if ("org.prismlauncher.EntryPoint".equals(wrapper)) {
            return System.getProperty(PRISM_MAIN_CLASS_PROPERTY);
        }
        if ("org.multimc.EntryPoint".equals(wrapper)) {
            return System.getProperty(MULTIMC_MAIN_CLASS_PROPERTY);
        }
        return null;
    }

    private static String publishedGameArguments(String wrapper) {
        if ("org.prismlauncher.EntryPoint".equals(wrapper)) {
            return System.getProperty(PRISM_GAME_ARGUMENTS_PROPERTY);
        }
        if ("org.multimc.EntryPoint".equals(wrapper)) {
            return System.getProperty(MULTIMC_GAME_ARGUMENTS_PROPERTY);
        }
        return null;
    }

    private static void preservePublishedLauncherProperties(List<String> command) {
        String[] properties = {
                "minecraft.launcher.brand",
                "minecraft.launcher.version",
                "org.prismlauncher.instance.name",
                "org.prismlauncher.instance.icon.id",
                "org.prismlauncher.instance.icon.path",
                "org.prismlauncher.window.title",
                "org.prismlauncher.window.dimensions",
                "multimc.instance.title",
                "multimc.instance.icon"
        };
        for (String property : properties) {
            addVmProperty(command, property, System.getProperty(property));
        }
    }

    private static void addVmProperty(List<String> command, String name, String value) {
        if (value == null || value.indexOf(0) >= 0 || value.length() > 16_384) return;
        String prefix = "-D" + name + "=";
        for (String argument : command) {
            if (argument != null && argument.startsWith(prefix)) return;
        }
        command.add(1, prefix + value);
    }

    private static NativePlatform currentPlatform() {
        try {
            return NativePlatform.normalize(
                    System.getProperty("os.name", ""),
                    System.getProperty("os.arch", "")
            );
        } catch (IOException unsupported) {
            return null;
        }
    }

    private static Optional<List<String>> currentJvmCommandLine(NativePlatform platform) {
        Optional<List<String>> processHandle = currentJvmCommandLineViaProcessHandle();
        if (processHandle.isPresent()) return processHandle;

        if (platform != null
                && platform.operatingSystem() == NativePlatform.OperatingSystem.LINUX) {
            Path cmdline = Paths.get("/proc/self/cmdline");
            try {
                if (Files.isReadable(cmdline)) {
                    Optional<List<String>> parsed =
                            parseNullSeparatedCommand(Files.readAllBytes(cmdline));
                    if (parsed.isPresent()) return parsed;
                }
            } catch (IOException | SecurityException ignored) {
                // Fall through to the Java 8 reconstruction.
            }
        }
        return reconstructCommandLineFromRuntimeMxBean(platform);
    }

    /** Uses ProcessHandle reflectively when a retro instance runs on Java 9+. */
    private static Optional<List<String>> currentJvmCommandLineViaProcessHandle() {
        try {
            Class<?> processHandleClass = Class.forName("java.lang.ProcessHandle");
            Object current = processHandleClass.getMethod("current").invoke(null);
            Object info = processHandleClass.getMethod("info").invoke(current);
            Class<?> infoClass = Class.forName("java.lang.ProcessHandle$Info");
            Optional<?> executable = (Optional<?>) infoClass.getMethod("command").invoke(info);
            Optional<?> arguments = (Optional<?>) infoClass.getMethod("arguments").invoke(info);
            if (!executable.isPresent() || !arguments.isPresent()) return Optional.empty();

            List<String> command = new ArrayList<String>();
            command.add((String) executable.get());
            Collections.addAll(command, (String[]) arguments.get());
            return Optional.of(Collections.unmodifiableList(command));
        } catch (Throwable unavailable) {
            return Optional.empty();
        }
    }

    private static Optional<List<String>> reconstructCommandLineFromRuntimeMxBean(
            NativePlatform platform
    ) {
        String javaHome = System.getProperty("java.home", "").trim();
        String sunJavaCommand = System.getProperty("sun.java.command", "").trim();
        if (javaHome.isEmpty() || sunJavaCommand.isEmpty()) return Optional.empty();

        List<String> command = new ArrayList<String>();
        command.add(javaHome + File.separator + "bin" + File.separator + "java");
        List<String> vmArguments = ManagementFactory.getRuntimeMXBean().getInputArguments();
        command.addAll(vmArguments);

        String classpath = System.getProperty("java.class.path", "").trim();
        if (!classpath.isEmpty()) {
            command.add("-cp");
            command.add(classpath);
        }

        int firstSpace = sunJavaCommand.indexOf(' ');
        command.add(firstSpace < 0
                ? sunJavaCommand
                : sunJavaCommand.substring(0, firstSpace));
        if (firstSpace >= 0) {
            command.addAll(splitMinecraftStyleArguments(
                    sunJavaCommand.substring(firstSpace + 1)
            ));
        }
        return Optional.of(Collections.unmodifiableList(command));
    }

    static List<String> splitMinecraftStyleArguments(String arguments) {
        if (arguments == null || arguments.trim().isEmpty()) {
            return Collections.emptyList();
        }
        List<String> tokens = new ArrayList<String>();
        for (String pair : arguments.split(" (?=--)")) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) continue;
            int space = trimmed.indexOf(' ');
            if (space < 0) {
                tokens.add(trimmed);
            } else {
                tokens.add(trimmed.substring(0, space));
                tokens.add(trimmed.substring(space + 1).trim());
            }
        }
        return tokens;
    }

    private static void addEarlyProgressWindowFlagIfForgeModern(List<String> command) {
        if (!shouldDisableForgeEarlyProgressWindow(
                classAvailable("net.minecraftforge.fml.loading.FMLLoader"), command)) {
            return;
        }
        addVmOption(command, "-Dfml.earlyprogresswindow=false");
    }

    static boolean shouldDisableForgeEarlyProgressWindow(
            boolean forgeLoaderAvailable,
            List<String> command
    ) {
        // Prism/MultiMC pass the actual Minecraft arguments through stdin. By
        // the time an ordinary Forge mod is constructed, FMLLoader is the most
        // reliable discriminator even if --fml.mcVersion is absent from the
        // process command line reconstructed for the replacement JVM.
        if (forgeLoaderAvailable) return true;
        if (command == null) return false;
        for (int index = 0; index < command.size(); index++) {
            String argument = command.get(index);
            if (argument != null && argument.startsWith("--fml.mcVersion=")) {
                return isRetroModernForgeVersion(
                        argument.substring("--fml.mcVersion=".length())
                );
            }
            if ("--fml.mcVersion".equals(argument) && index + 1 < command.size()) {
                return isRetroModernForgeVersion(command.get(index + 1));
            }
        }
        return false;
    }

    private static boolean classAvailable(String className) {
        try {
            Class.forName(
                    className,
                    false,
                    SteamOverlayRelauncher.class.getClassLoader()
            );
            return true;
        } catch (ClassNotFoundException | LinkageError | SecurityException absent) {
            ClassLoader context = Thread.currentThread().getContextClassLoader();
            if (context == null || context == SteamOverlayRelauncher.class.getClassLoader()) {
                return false;
            }
            try {
                Class.forName(className, false, context);
                return true;
            } catch (ClassNotFoundException | LinkageError | SecurityException stillAbsent) {
                return false;
            }
        }
    }

    private static boolean isRetroModernForgeVersion(String version) {
        return version != null && (version.startsWith("1.13.")
                || version.startsWith("1.14.")
                || version.startsWith("1.15.")
                || version.startsWith("1.16."));
    }

    private static Optional<Path> capturedStdinFile() {
        String value = System.getProperty(CAPTURED_STDIN_FILE_PROPERTY, "").trim();
        if (value.isEmpty()) return Optional.empty();
        return validateCapturedStdin(
                Paths.get(value),
                Boolean.parseBoolean(System.getProperty(
                        CAPTURED_STDIN_TRUNCATED_PROPERTY, "false"
                ))
        );
    }

    private static void replayCapturedStdin(final Process child, final Path capturedFile) {
        Thread relay = new Thread(new Runnable() {
            @Override
            public void run() {
                try (OutputStream output = child.getOutputStream()) {
                    byte[] buffer = new byte[8_192];
                    int count;
                    try (InputStream captured = Files.newInputStream(capturedFile)) {
                        while ((count = captured.read(buffer)) >= 0) {
                            if (count > 0) output.write(buffer, 0, count);
                        }
                        output.flush();
                    }
                    while ((count = System.in.read(buffer)) >= 0) {
                        if (count > 0) {
                            output.write(buffer, 0, count);
                            output.flush();
                        }
                    }
                } catch (IOException closed) {
                    // Expected once the child or launcher closes its stdin.
                }
            }
        }, "e4steam-overlay-stdin-relay");
        relay.setDaemon(true);
        relay.start();
    }
}
