package link.e4steam;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DoctorTest {
    @Test
    void shortMessageUsesTheRootCauseWithoutChatControlCharacters() {
        var failure = new IOException(
                "Steam initialization failed",
                new IOException("Steam is not running\r\nor the current user is not signed in")
        );

        String message = Doctor.shortMessage(failure);

        assertEquals("Steam is not running or the current user is not signed in", message);
        assertFalse(message.contains("\r"));
        assertFalse(message.contains("\n"));
        assertFalse(message.contains("at link.e4steam"));
    }

    @Test
    void shortMessageLimitsUntrustedExceptionTextForChat() {
        String message = Doctor.shortMessage(new IOException("x".repeat(500)));

        assertEquals(240, message.length());
        assertTrue(message.endsWith("..."));
    }

    @Test
    void diagnosticsRedactJoinAddressesSteamIdsSecretsAndHomePaths() {
        String home = System.getProperty("user.home", "");
        String value = "peer 76561198000000001 at s-abc-1234567890.steam "
                + "token=super-secret " + home + "\\logs";

        String redacted = Doctor.redactDiagnostic(value);

        assertFalse(redacted.contains("76561198000000001"));
        assertFalse(redacted.contains("s-abc-1234567890.steam"));
        assertFalse(redacted.contains("super-secret"));
        if (!home.isEmpty()) assertFalse(redacted.contains(home));
        assertTrue(redacted.contains("<redacted-join-address>"));
        assertTrue(redacted.contains("<redacted-steam-id>"));
    }

    @Test
    void shortMessageAlsoRedactsCredentialBearingAddress() {
        String message = Doctor.shortMessage(new IOException(
                "failed s-abc-1234567890.steam ticket=raw-ticket"));

        assertFalse(message.contains("1234567890.steam"));
        assertFalse(message.contains("raw-ticket"));
    }
}
