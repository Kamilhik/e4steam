package link.e4steam.steam;

import com.sun.jna.Library;
import com.sun.jna.Native;
import link.e4steam.HexCodec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Optional verified Valve SDK home for headless hosting containers with inaccessible HOME. */
final class LinuxDedicatedSdk {
    interface LibC extends Library {
        int setenv(String name, String value, int overwrite);
        String getenv(String name);
    }

    private LinuxDedicatedSdk() { }

    static void prepare() throws IOException {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) return;
        Path home = Paths.get(System.getProperty("user.dir", "."), ".e4steam-steam-home")
                .toAbsolutePath().normalize();
        // Without a private SDK installation, retain the normal system SDK lookup.
        if (!Files.exists(home, LinkOption.NOFOLLOW_LINKS)) return;
        String arch = System.getProperty("os.arch", "");
        if (!arch.equals("amd64") && !arch.equals("x86_64")) {
            throw new IOException("Dedicated Steam SDK requires Linux x86_64");
        }
        verifyHome(home);
        @SuppressWarnings("deprecation")
        LibC libc = (LibC) Native.loadLibrary("c", LibC.class);
        if (libc.setenv("HOME", home.toString(), 1) != 0 || !home.toString().equals(libc.getenv("HOME"))) {
            throw new IOException("Could not configure this process's private Steam SDK home");
        }
    }

    static void verifyHome(Path home) throws IOException {
        Path steam = home.resolve(".steam");
        Path sdk = steam.resolve("sdk64");
        for (Path directory : new Path[]{home, steam, sdk}) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Private Steam SDK directory is missing or is a symbolic link");
            }
        }
        Map<String, String> hashes = new LinkedHashMap<>();
        hashes.put("steamclient.so", "3e32119f473cb9c2615e1b282da8498b9986378e8b71537a4aed9f77e7b797e0");
        hashes.put("libtier0_s.so", "8d0622e0ecd9a7c853e0dba0fcc707df34d4876f5ae7a2296ced596e4269931e");
        hashes.put("libvstdlib_s.so", "642875157a7aa15dafafdf10031c5252bc70a758a90665ad72c69197c0caff47");
        for (Map.Entry<String, String> entry : hashes.entrySet()) verify(sdk.resolve(entry.getKey()), entry.getValue());
    }

    static void verify(Path file, String expected) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 64L * 1024 * 1024) {
            throw new IOException("Missing or unsafe Steam SDK library: " + file.getFileName());
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[65536];
                long total = 0;
                for (int count; (count = input.read(buffer)) != -1;) {
                    total += count;
                    if (total > 64L * 1024 * 1024) throw new IOException("Steam SDK library exceeds size limit");
                    digest.update(buffer, 0, count);
                }
            }
            if (!expected.equals(HexCodec.encode(digest.digest()))) {
                throw new IOException("Steam SDK checksum mismatch: " + file.getFileName());
            }
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
