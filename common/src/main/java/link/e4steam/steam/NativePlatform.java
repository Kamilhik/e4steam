package link.e4steam.steam;

import java.io.IOException;
import java.util.Locale;

/** Strict normalized Steam native target independent from JVM alias spelling. */
final class NativePlatform {
    enum OperatingSystem { WINDOWS, LINUX, MACOS }
    enum Architecture { X86_64, ARM64 }

    private final OperatingSystem operatingSystem;
    private final Architecture architecture;

    private NativePlatform(OperatingSystem operatingSystem, Architecture architecture) {
        this.operatingSystem = operatingSystem;
        this.architecture = architecture;
    }

    static NativePlatform normalize(String osName, String architecture) throws IOException {
        String os = normalizeAlias(osName);
        String arch = normalizeAlias(architecture);
        OperatingSystem operatingSystem;
        if (isFamily(os, "windows")) operatingSystem = OperatingSystem.WINDOWS;
        else if (isFamily(os, "linux")) operatingSystem = OperatingSystem.LINUX;
        else if (isFamily(os, "mac os") || isFamily(os, "macos")
                || isFamily(os, "os x") || isFamily(os, "darwin")) {
            operatingSystem = OperatingSystem.MACOS;
        }
        else throw new IOException("Unsupported operating system family");

        Architecture normalizedArchitecture;
        if (arch.equals("amd64") || arch.equals("x86_64") || arch.equals("x64")) {
            normalizedArchitecture = Architecture.X86_64;
        } else if (arch.equals("arm64") || arch.equals("aarch64")) {
            normalizedArchitecture = Architecture.ARM64;
        } else {
            throw new IOException("Unsupported 64-bit Steam native architecture");
        }
        if (operatingSystem != OperatingSystem.MACOS
                && normalizedArchitecture != Architecture.X86_64) {
            throw new IOException("This operating system currently requires an x86-64 Java runtime");
        }
        return new NativePlatform(operatingSystem, normalizedArchitecture);
    }

    OperatingSystem operatingSystem() { return operatingSystem; }
    Architecture architecture() { return architecture; }

    String directoryName() {
        String os = operatingSystem == OperatingSystem.WINDOWS ? "windows"
                : operatingSystem == OperatingSystem.LINUX ? "linux" : "macos";
        return os + '-' + (architecture == Architecture.X86_64 ? "x64" : "arm64");
    }

    private static String normalizeAlias(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace('-', '_').replaceAll("\\s+", " ");
    }

    private static boolean isFamily(String normalized, String family) {
        return normalized.equals(family) || normalized.startsWith(family + " ");
    }
}
