package link.e4steam.steam;

import link.e4steam.Agnos;
import link.e4steam.Config;
import link.e4steam.E4steamClient;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

/** Pre-LWJGL JVM relaunch used to inject Steam's Unix overlay renderer. */
final class SteamOverlayRelauncher {
    private static final String MARKER_ENV = "E4STEAM_OVERLAY_RELAUNCHED";
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

    static void relaunchIfNeeded() {
        if (!Agnos.isClient() || System.getenv(MARKER_ENV) != null) return;
        NativePlatform platform = currentPlatform();
        if (platform == null || insertLibrariesEnvName(platform) == null) return;
        if (!Config.INSTANCE.overlayRelaunch.value()) return;

        Optional<Path> overlay = SteamOverlayLoader.findOverlayLibrary();
        if (!overlay.isPresent()) {
            E4steamClient.LOGGER.info(
                    "Steam Overlay renderer was not found; continuing without overlay relaunch"
            );
            return;
        }
        Optional<List<String>> originalCommand = currentJvmCommandLine(platform);
        if (!originalCommand.isPresent()) {
            E4steamClient.LOGGER.warn(
                    "Could not reconstruct the JVM launch command; continuing without overlay relaunch"
            );
            return;
        }

        List<String> command = new ArrayList<>(originalCommand.get());
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
                E4steamClient.LOGGER.warn(
                        "Detected {} but it did not publish a safe direct launch command and "
                                + "no valid e4steam stdin-agent capture is available; "
                                + "continuing without overlay relaunch",
                        wrapper
                );
                return;
            }
            command = new ArrayList<>(direct.get());
            preservePublishedLauncherProperties(command);
            E4steamClient.LOGGER.info(
                    "Recovered the direct Minecraft launch command from {} without a Java agent",
                    wrapper
            );
        }

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
            String variable = insertLibrariesEnvName(platform);
            environment.put(variable, mergeInsertedLibrary(
                    environment.get(variable), overlay.get().toString()
            ));
            configureSteamAppEnvironment(environment);
            environment.put(MARKER_ENV, "1");

            E4steamClient.LOGGER.info(
                    "Relaunching Minecraft with the Steam Overlay renderer injected"
            );
            child = builder.start();
            if (capturedStdin.isPresent()) replayCapturedStdin(child, capturedStdin.get());
        } catch (IOException | RuntimeException failure) {
            E4steamClient.LOGGER.warn(
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
        System.exit(exitCode);
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

        List<String> direct = new ArrayList<>(
                command.subList(0, wrapperIndex + 1)
        );
        direct.set(wrapperIndex, mainClass);
        direct.addAll(gameArguments);
        return Optional.of(Collections.unmodifiableList(direct));
    }

    static List<String> splitPublishedGameArguments(String encoded) {
        if (encoded == null || encoded.isEmpty()) return Collections.emptyList();
        List<String> arguments = new ArrayList<>();
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

    static Optional<List<String>> parseNullSeparatedCommand(byte[] raw) {
        if (raw == null || raw.length == 0) return Optional.empty();
        List<String> result = new ArrayList<>();
        int start = 0;
        for (int index = 0; index <= raw.length; index++) {
            if (index != raw.length && raw[index] != 0) continue;
            if (index > start) {
                result.add(new String(raw, start, index - start, StandardCharsets.UTF_8));
            }
            start = index + 1;
        }
        return result.isEmpty()
                ? Optional.empty()
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
        Optional<String> executable = ProcessHandle.current().info().command();
        Optional<String[]> arguments = ProcessHandle.current().info().arguments();
        if (executable.isPresent() && arguments.isPresent()) {
            List<String> command = new ArrayList<>();
            command.add(executable.get());
            Collections.addAll(command, arguments.get());
            return Optional.of(Collections.unmodifiableList(command));
        }
        if (platform.operatingSystem() != NativePlatform.OperatingSystem.LINUX) {
            return Optional.empty();
        }
        Path cmdline = Paths.get("/proc/self/cmdline");
        try {
            if (!Files.isReadable(cmdline)) return Optional.empty();
            return parseNullSeparatedCommand(Files.readAllBytes(cmdline));
        } catch (IOException | SecurityException failure) {
            return Optional.empty();
        }
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

    private static void replayCapturedStdin(Process child, Path capturedFile) {
        Thread relay = new Thread(() -> {
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
                // Expected after the child or launcher closes its stdin.
            }
        }, "e4steam-overlay-stdin-relay");
        relay.setDaemon(true);
        relay.start();
    }
}
