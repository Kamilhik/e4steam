package link.e4steam.steam;

import com.sun.jna.Library;
import com.sun.jna.Native;
import link.e4steam.HexCodec;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InterruptedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.tukaani.xz.XZInputStream;

/** Bundled, verified Valve SDK for Linux headless servers; never extracts during client startup. */
final class LinuxDedicatedSdk {
    static final String RESOURCE_ROOT="/e4steam/native/linux-x86_64/";
    private static final long MAX_LIBRARY_BYTES=64L*1024*1024;
    @FunctionalInterface interface ResourceSource {InputStream open(String name) throws IOException;}
    interface LibC extends Library {
        int setenv(String name, String value, int overwrite);
        String getenv(String name);
    }

    private LinuxDedicatedSdk() { }

    static void prepare() throws IOException {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux")) return;
        Path home = Paths.get(System.getProperty("user.dir", "."), ".e4steam-steam-home")
                .toAbsolutePath().normalize();
        String arch = System.getProperty("os.arch", "");
        if (!arch.equals("amd64") && !arch.equals("x86_64")) {
            throw new IOException("Dedicated Steam SDK requires Linux x86_64");
        }
        install(home);
        verifyHome(home);
        @SuppressWarnings("deprecation")
        LibC libc = (LibC) Native.loadLibrary("c", LibC.class);
        if (libc.setenv("HOME", home.toString(), 1) != 0 || !home.toString().equals(libc.getenv("HOME"))) {
            throw new IOException("Could not configure this process's private Steam SDK home");
        }
    }

    static Map<String,String> hashes() {
        Map<String,String> hashes=new LinkedHashMap<>();
        hashes.put("steamclient.so", "3e32119f473cb9c2615e1b282da8498b9986378e8b71537a4aed9f77e7b797e0");
        hashes.put("libtier0_s.so", "8d0622e0ecd9a7c853e0dba0fcc707df34d4876f5ae7a2296ced596e4269931e");
        hashes.put("libvstdlib_s.so", "642875157a7aa15dafafdf10031c5252bc70a758a90665ad72c69197c0caff47");
        return hashes;
    }

    static void install(Path home) throws IOException {
        install(home,LinuxDedicatedSdk.class::getResourceAsStream,hashes());
    }

    static void install(Path home,ResourceSource source,Map<String,String> expected) throws IOException {
        if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Steam SDK preparation canceled");
        privateDirectory(home);privateDirectory(home.resolve(".steam"));
        Path sdk=home.resolve(".steam/sdk64");privateDirectory(sdk);
        for(Map.Entry<String,String> entry:expected.entrySet()) {
            String name=entry.getKey();
            if(!name.matches("[a-z0-9_]+\\.so"))throw new IOException("Invalid SDK library name");
            Path destination=sdk.resolve(name);
            if(Files.exists(destination,LinkOption.NOFOLLOW_LINKS)) {
                if(Files.isSymbolicLink(destination)||!Files.isRegularFile(destination,LinkOption.NOFOLLOW_LINKS))
                    throw new IOException("Unsafe Steam SDK library path: "+name);
                try {verify(destination,entry.getValue());continue;}
                catch(IOException outdated) { /* Repair only this mod-managed SDK cache, never user saves/configs. */ }
            }
            Path temporary=privateTemporary(sdk,name);
            try {
                try(InputStream packed=source.open(RESOURCE_ROOT+name+".xz")) {
                    if(packed==null)throw new IOException("Bundled Linux Steam SDK resource missing: "+name);
                    try(InputStream input=new XZInputStream(packed,128*1024);
                        OutputStream output=Files.newOutputStream(temporary)) {
                        byte[] buffer=new byte[65536];long total=0;
                        for(int n;(n=input.read(buffer))!=-1;) {
                            if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Steam SDK preparation canceled");
                            total+=n;if(total>MAX_LIBRARY_BYTES)throw new IOException("Steam SDK library exceeds size limit");
                            output.write(buffer,0,n);
                        }
                    }
                }
                verify(temporary,entry.getValue());
                try {Files.move(temporary,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
                catch(AtomicMoveNotSupportedException fallback){Files.move(temporary,destination,StandardCopyOption.REPLACE_EXISTING);}
            } finally {Files.deleteIfExists(temporary);}
        }
    }

    private static void privateDirectory(Path path) throws IOException {
        try {
            try {Files.createDirectory(path,PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));}
            catch(UnsupportedOperationException noPosix){Files.createDirectory(path);}
        } catch(FileAlreadyExistsException present) { /* Validate rather than following an existing link. */ }
        if(Files.isSymbolicLink(path)||!Files.isDirectory(path,LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Unsafe Steam SDK directory");
    }

    private static Path privateTemporary(Path directory,String name) throws IOException {
        try {return Files.createTempFile(directory,name+"-",".part",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));}
        catch(UnsupportedOperationException noPosix){return Files.createTempFile(directory,name+"-",".part");}
    }

    static void verifyHome(Path home) throws IOException {
        Path steam = home.resolve(".steam");
        Path sdk = steam.resolve("sdk64");
        for (Path directory : new Path[]{home, steam, sdk}) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Private Steam SDK directory is missing or is a symbolic link");
            }
        }
        for (Map.Entry<String, String> entry : hashes().entrySet()) verify(sdk.resolve(entry.getKey()), entry.getValue());
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
