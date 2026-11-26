package link.e4steam.steam;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.XZOutputStream;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.*;

class LinuxDedicatedSdkTest {
    @TempDir Path root;

    @Test void refusesPartialSdkHomesBeforeChangingProcessEnvironment() throws Exception {
        assertThrows(IOException.class, () -> LinuxDedicatedSdk.verifyHome(root));
        Files.createDirectories(root.resolve(".steam/sdk64"));
        assertThrows(IOException.class, () -> LinuxDedicatedSdk.verifyHome(root));
    }

    @Test void checksContentRatherThanAcceptingAnExistingLibraryName() throws Exception {
        Path library = root.resolve("steamclient.so");
        Files.write(library, new byte[]{1, 2, 3});
        assertThrows(IOException.class, () -> LinuxDedicatedSdk.verify(library, "bad-hash"));
        LinuxDedicatedSdk.verify(library, "039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81");
    }

    @Test void bundledLibrariesExtractAndKeepVerifiedFilesOnTheNextStartup() throws Exception {
        LinuxDedicatedSdk.install(root);LinuxDedicatedSdk.verifyHome(root);
        Path client=root.resolve(".steam/sdk64/steamclient.so");
        assertArrayEquals(new byte[]{0x7f,'E','L','F',2},java.util.Arrays.copyOf(Files.readAllBytes(client),5));
        Files.setLastModifiedTime(client,FileTime.fromMillis(1000));
        LinuxDedicatedSdk.install(root);
        assertEquals(1000,Files.getLastModifiedTime(client).toMillis());
    }
    @Test void missingAndCorruptCacheEntriesAreRepairedFromTheBundle() throws Exception {
        byte[] data=new byte[]{1,2,3};byte[] packed=pack(data);String hash="039058c6f2c0cb492c533b0a4d14ef77cc0f78abccced5287d84a1a2011cfb81";
        AtomicInteger opens=new AtomicInteger();
        LinuxDedicatedSdk.ResourceSource source=n->{opens.incrementAndGet();return new ByteArrayInputStream(packed);};
        LinuxDedicatedSdk.install(root,source,Collections.singletonMap("test.so",hash));
        Path file=root.resolve(".steam/sdk64/test.so");assertArrayEquals(data,Files.readAllBytes(file));
        LinuxDedicatedSdk.install(root,source,Collections.singletonMap("test.so",hash));assertEquals(1,opens.get());
        Files.write(file,new byte[]{9});
        LinuxDedicatedSdk.install(root,source,Collections.singletonMap("test.so",hash));
        assertEquals(2,opens.get());assertArrayEquals(data,Files.readAllBytes(file));
    }
    @Test void badBundleNeverReplacesTheExistingFileOrLeavesPartialFiles() throws Exception {
        Path sdk=Files.createDirectories(root.resolve(".steam/sdk64"));Path file=sdk.resolve("test.so");Files.write(file,new byte[]{9});
        byte[] packed=pack(new byte[]{1,2,3});
        assertThrows(IOException.class,()->LinuxDedicatedSdk.install(root,n->new ByteArrayInputStream(packed),Collections.singletonMap("test.so","invalid")));
        assertArrayEquals(new byte[]{9},Files.readAllBytes(file));
        try(var files=Files.list(sdk)){assertEquals(1,files.count());}
    }
    @Test void missingResourceFailsWithoutPublishingAnIncompleteLibrary() throws Exception {
        assertThrows(IOException.class,()->LinuxDedicatedSdk.install(root,n->null,Collections.singletonMap("test.so","invalid")));
        assertFalse(Files.exists(root.resolve(".steam/sdk64/test.so")));
    }
    @Test void refusesDirectoriesInPlaceOfLibraries() throws Exception {
        Files.createDirectories(root.resolve(".steam/sdk64/test.so"));
        assertThrows(IOException.class,()->LinuxDedicatedSdk.install(root,n->null,Collections.singletonMap("test.so","invalid")));
    }
    @Test void refusesSymlinkedSdkDirectories() throws Exception {
        Path outside=Files.createDirectory(root.resolve("outside"));
        try {Files.createSymbolicLink(root.resolve(".steam"),outside);}
        catch(IOException|UnsupportedOperationException denied){assumeTrue(false,"Symlinks unavailable");}
        assertThrows(IOException.class,()->LinuxDedicatedSdk.install(root,n->null,Collections.singletonMap("test.so","invalid")));
        try(var files=Files.list(outside)){assertEquals(0,files.count());}
    }
    @Test void refusesResourceNamesEscapingTheManagedSdkDirectory() {
        assertThrows(IOException.class,()->LinuxDedicatedSdk.install(root,n->null,Collections.singletonMap("../outside.so","invalid")));
    }
    private static byte[] pack(byte[] data) throws Exception {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(XZOutputStream out=new XZOutputStream(bytes,new LZMA2Options(0))){out.write(data);}
        return bytes.toByteArray();
    }
}
