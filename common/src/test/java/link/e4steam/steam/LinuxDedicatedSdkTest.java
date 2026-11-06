package link.e4steam.steam;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
