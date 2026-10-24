package hamigo.version;

import org.junit.Test;
import static org.junit.Assert.*;

public class HamigoVersionTest {
    private HamigoVersion version(int ordinal, int build, String channel, int n) {
        return HamigoVersion.create("1.0", ordinal, build, channel, n, "a1b2c3d4", false);
    }
    @Test public void installedLegacyAppsCanUpgradeAndTracksHaveOneSequence() {
        var alpha = version(57, 5, "alpha", 5);
        var beta = version(57, 8, "beta", 2);
        var release = version(57, 9, "release", 1);
        assertTrue(alpha.code() > 56);
        assertTrue(alpha.code() < beta.code());
        assertTrue(beta.code() < release.code());
        assertEquals("1.0-alpha5", alpha.name());
        assertEquals("1.0-beta2", beta.name());
        assertEquals("1.0", release.name());
    }
    @Test public void nextProductVersionAlwaysFollowsPreviousRange() {
        assertTrue(version(57, 9999, "release", 1).code() < version(58, 1, "alpha", 1).code());
        assertTrue(version(209999, 9999, "release", 1).code() <= 2100000000);
    }
    @Test public void rebuildKeepsIdentityAndDevKeepsTraceability() {
        assertEquals(version(57, 9, "release", 1), version(57, 9, "release", 1));
        assertEquals("1.0-dev8+a1b2c3d4-dirty",
            HamigoVersion.create("1.0",57,8,"dev",1,"a1b2c3d4",true).name());
    }
    @Test public void invalidReservationsFailInsteadOfClampingOrWrapping() {
        for (int[] pair : new int[][] {{0,1},{210000,1},{57,0},{57,10000}})
            assertThrows(IllegalArgumentException.class, () -> version(pair[0], pair[1], "release", 1));
        assertThrows(IllegalArgumentException.class, () -> version(57,1,"production",1));
    }
}
