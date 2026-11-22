package link.e4steam.steam;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SteamNamePinsTest {
    @TempDir Path root;
    @Test void verifiedSameOwnerCanUpdateReturnedWorldWithoutResettingSavedNames() throws Exception {
        Path file=root.resolve("names.properties");long owner=76561199469700509L;
        var before=new SteamNamedAddress.Identity(owner,UUID.randomUUID());
        var after=new SteamNamedAddress.Identity(owner,UUID.randomUUID());
        SteamNamePins.remember(file,"xuy",before);SteamNamePins.remember(file,"xuy",after);
        assertEquals(after,SteamNamePins.get(file,"xuy"));
    }
    @Test void aDifferentSteamOwnerCannotOverwriteTheExistingPin() throws Exception {
        Path file=root.resolve("names.properties");long owner=76561199469700509L;
        var before=new SteamNamedAddress.Identity(owner,UUID.randomUUID());
        SteamNamePins.remember(file,"xuy",before);byte[] saved=Files.readAllBytes(file);
        assertThrows(java.io.IOException.class,()->SteamNamePins.remember(file,"xuy",new SteamNamedAddress.Identity(owner+1,UUID.randomUUID())));
        assertArrayEquals(saved,Files.readAllBytes(file));assertEquals(before,SteamNamePins.get(file,"xuy"));
    }
}
