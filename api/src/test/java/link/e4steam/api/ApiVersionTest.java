package link.e4steam.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiVersionTest {
    @Test
    void followsSemanticVersionPreReleaseOrdering() {
        ApiVersion alpha = ApiVersion.parse("1.0.0-alpha.1");
        ApiVersion beta = ApiVersion.parse("1.0.0-beta.1");
        ApiVersion stable = ApiVersion.parse("1.0.0+build.7");

        assertTrue(alpha.compareTo(beta) < 0);
        assertTrue(beta.compareTo(stable) < 0);
        assertTrue(stable.isStable());
        assertEquals("1.0.0+build.7", stable.toString());
    }

    @Test
    void rejectsInvalidSemanticVersions() {
        assertThrows(IllegalArgumentException.class, () -> ApiVersion.parse("1.0"));
        assertThrows(IllegalArgumentException.class, () -> ApiVersion.parse("01.0.0"));
        assertThrows(IllegalArgumentException.class, () -> ApiVersion.parse("1.0.0-alpha.01"));
        assertThrows(IllegalArgumentException.class, () -> ApiVersion.parse("999999999999.0.0"));
    }

    @Test
    void usesInclusiveMinimumAndExclusiveMaximum() {
        ApiVersionRange range = new ApiVersionRange(
                ApiVersion.parse("0.1.0"),
                ApiVersion.parse("1.0.0")
        );

        assertTrue(range.contains(ApiVersion.parse("0.1.0")));
        assertTrue(range.contains(ApiVersion.parse("0.9.9")));
        assertFalse(range.contains(ApiVersion.parse("1.0.0")));
    }
}
