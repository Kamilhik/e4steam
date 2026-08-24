package link.e4steam.retro;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

public final class RetroCommandTest {
    @Test
    public void exposesCompleteCoreCommandTree() {
        assertEquals(Arrays.asList(
                        "start", "stop", "restart", "invite", "doctor", "addon", "help"),
                RetroBootstrap.clientCommandNames());
    }
}
