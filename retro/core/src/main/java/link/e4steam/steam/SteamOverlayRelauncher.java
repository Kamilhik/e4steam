package link.e4steam.steam;

import link.e4steam.Agnos;
import link.e4steam.E4steamClient;

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

/** Java 8-compatible opt-in Unix overlay relaunch for retro artifacts. */
final class SteamOverlayRelauncher {
    private static final String MARKER_ENV = "E4STEAM_OVERLAY_RELAUNCHED";
    private static final String OVERLAY_RELAUNCH_PROPERTY = "e4steam.overlayRelaunch";
    private static final String CAPTURED_STDIN_FILE_PROPERTY = "e4steam.capturedStdinFile";
    private static final String CAPTURED_STDIN_TRUNCATED_PROPERTY =
            "e4steam.capturedStdinTruncated";
    private static final long MAX_CAPTURE_BYTES = 1_048_576L;
    private static final String[] STDIN_LAUNCHER_WRAPPERS = {
            "org.prismlauncher.EntryPoint",
            "org.multimc.EntryPoint"
    };

    private SteamOverlayRelauncher() {
    }

    static void relaunchIfNeeded() {
        if (!Agnos.isClient() || System.getenv(MARKER_ENV) != null) return;
        NativePlatform platform = currentPlatform();
        String insertionVariable = insertLibrariesEnvName(platform);
        if (insertionVariable == null || !Boolean.getBoolean(OVERLAY_RELAUNCH_PROPERTY)) return;

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

        List<String> command = new ArrayList<String>(originalCommand.get());
        addEarlyProgressWindowFlagIfForgeModern(command);
        String wrapper = detectStdinLauncherWrapper(command);
        Optional<Path> capturedStdin = capturedStdinFile();
        if (wrapper != null && !capturedStdin.isPresent()) {
            E4steamClient.LOGGER.warn(
                    "Detected {} but no valid e4steam stdin-agent capture is available; "
                            + "continuing without overlay relaunch",
                    wrapper
            );
            return;
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
            environment.put(insertionVariable, mergeInsertedLibrary(
                    environment.get(insertionVariable), overlay.get().toString()
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
        if (platform != null
                && platform.operatingSystem() == NativePlatform.OperatingSystem.MACOS) {
            addVmOption(command, "-XstartOnFirstThread");
        }

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
        if (!isModernForgeLaunch(command)) return;
        addVmOption(command, "-Dfml.earlyprogresswindow=false");
    }

    private static boolean isModernForgeLaunch(List<String> command) {
        try {
            Class.forName(
                    "net.minecraftforge.fml.loading.FMLLoader",
                    false,
                    SteamOverlayRelauncher.class.getClassLoader()
            );
        } catch (ClassNotFoundException absent) {
            return false;
        }
        for (int index = 0; index < command.size(); index++) {
            String argument = command.get(index);
            if ("fmlclient".equals(argument)) return true;
            if (argument != null && argument.startsWith("--fml.mcVersion=")) {
                return isRetroModernForgeVersion(
                        argument.substring("--fml.mcVersion=".length())
                );
            }
            if ("--fml.mcVersion".equals(argument) && index + 1 < command.size()
                    && isRetroModernForgeVersion(command.get(index + 1))) {
                return true;
            }
        }
        return false;
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
