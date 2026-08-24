package link.e4steam.retro;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class RetroVersionTest {
    @Test
    public void branchAcceptsOnlyItsMinorLine() {
        assertTrue(RetroVersion.belongsToBranch("1.16", "1.16.x"));
        assertTrue(RetroVersion.belongsToBranch("1.16.1", "1.16.x"));
        assertTrue(RetroVersion.belongsToBranch("1.16.5", "1.16.x"));
        assertFalse(RetroVersion.belongsToBranch("1.15.2", "1.16.x"));
        assertFalse(RetroVersion.belongsToBranch("1.17", "1.16.x"));
    }

    @Test
    public void exactArtifactRejectsOtherPatches() {
        assertTrue(RetroVersion.belongsToBranch("1.6.4", "1.6.4"));
        assertFalse(RetroVersion.belongsToBranch("1.6.2", "1.6.4"));
    }

    @Test
    public void embeddedMetadataKeepsBaselineAndPublicBranchSeparate() {
        assertEquals("1.16.5", RetroVersion.baseline());
        assertEquals("1.16.x", RetroVersion.branch());
        assertEquals("1.16.5", RetroVersion.minecraft());
    }
}
