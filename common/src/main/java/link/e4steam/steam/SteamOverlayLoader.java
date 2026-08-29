package link.e4steam.steam;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/** Finds Valve's installed Unix overlay renderer without loading it in-process. */
final class SteamOverlayLoader {
    private SteamOverlayLoader() {
    }

    static Optional<Path> findOverlayLibrary() {
        final NativePlatform platform;
        try {
            platform = NativePlatform.normalize(
                    System.getProperty("os.name", ""),
                    System.getProperty("os.arch", "")
            );
        } catch (IOException unsupported) {
            return Optional.empty();
        }
        String home = System.getProperty("user.home", "").trim();
        if (home.isEmpty()) return Optional.empty();
        return findOverlayLibrary(platform, Paths.get(home));
    }

    static Optional<Path> findOverlayLibrary(NativePlatform platform, Path home) {
        if (platform == null || home == null) return Optional.empty();
        String libraryName = libraryName(platform);
        if (libraryName == null) return Optional.empty();
        for (Path root : candidateSteamRoots(platform, home.toAbsolutePath().normalize())) {
            Optional<Path> candidate = usableRegularFile(root.resolve(libraryName));
            if (candidate.isPresent()) return candidate;
        }
        return Optional.empty();
    }

    static List<Path> candidateSteamRoots(NativePlatform platform, Path home) {
        if (platform == null || home == null) return Collections.emptyList();
        List<Path> roots = new ArrayList<>();
        if (platform.operatingSystem() == NativePlatform.OperatingSystem.MACOS) {
            // Current Steam installations provide a universal renderer with
            // both x86_64 and arm64 slices. The JVM architecture still has to
            // match one of those slices; NativePlatform rejects other targets.
            roots.add(home.resolve(
                    "Library/Application Support/Steam/Steam.AppBundle/Steam/Contents/MacOS"
            ));
            return Collections.unmodifiableList(roots);
        }
        if (platform.operatingSystem() != NativePlatform.OperatingSystem.LINUX
                || platform.architecture() != NativePlatform.Architecture.X86_64) {
            return Collections.emptyList();
        }

        roots.add(home.resolve(".steam/steam/ubuntu12_64"));
        roots.add(home.resolve(".steam/root/ubuntu12_64"));
        roots.add(home.resolve(".local/share/Steam/ubuntu12_64"));
        roots.add(home.resolve(
                ".var/app/com.valvesoftware.Steam/.steam/steam/ubuntu12_64"
        ));
        roots.add(home.resolve(
                ".var/app/com.valvesoftware.Steam/.local/share/Steam/ubuntu12_64"
        ));
        roots.add(home.resolve("snap/steam/common/.steam/steam/ubuntu12_64"));
        roots.add(home.resolve("snap/steam/common/.local/share/Steam/ubuntu12_64"));
        return Collections.unmodifiableList(roots);
    }

    private static String libraryName(NativePlatform platform) {
        if (platform.operatingSystem() == NativePlatform.OperatingSystem.LINUX) {
            return "gameoverlayrenderer.so";
        }
        if (platform.operatingSystem() == NativePlatform.OperatingSystem.MACOS) {
            return "gameoverlayrenderer.dylib";
        }
        return null;
    }

    private static Optional<Path> usableRegularFile(Path candidate) {
        try {
            // Steam commonly exposes its installation through symlinks. Resolve
            // those first, then reject a dangling link or non-regular target.
            Path real = candidate.toRealPath();
            if (!Files.isRegularFile(real, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isReadable(real)) {
                return Optional.empty();
            }
            return Optional.of(real);
        } catch (IOException | SecurityException unavailable) {
            return Optional.empty();
        }
    }
}
