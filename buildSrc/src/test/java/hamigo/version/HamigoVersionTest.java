package hamigo.version;

import org.junit.Test;
import static org.junit.Assert.*;

public class HamigoVersionTest {
    private HamigoVersion version(int build, String channel, int n) {
        return HamigoVersion.create("1.0", build, channel, n, "a1b2c3d4", false);
    }
    @Test public void installedLegacyAppsCanUpgradeAndTracksHaveOneSequence() {
        var alpha = version(57, "alpha", 5);
        var beta = version(58, "beta", 2);
        var release = version(59, "release", 1);
        assertTrue(alpha.code() > 56);
        assertTrue(alpha.code() < beta.code());
        assertTrue(beta.code() < release.code());
        assertEquals("1.0-alpha5", alpha.name());
        assertEquals("1.0-beta2", beta.name());
        assertEquals("1.0", release.name());
    }
    @Test public void codeIsIndependentOfProductVersionAndGitHistory() {
        assertEquals(60,HamigoVersion.create("1.1",60,"alpha",1,"a1b2c3d4",false).code());
        assertEquals(2100000000,version(2100000000,"release",1).code());
    }
    @Test public void rebuildKeepsIdentityAndDevKeepsTraceability() {
        assertEquals(version(59, "release", 1), version(59, "release", 1));
        assertEquals(570001,version(57,"dev",1).code());
        assertEquals("1.0-dev57+a1b2c3d4-dirty",
            HamigoVersion.create("1.0",57,"dev",1,"a1b2c3d4",true).name());
    }
    @Test public void invalidReservationsFailInsteadOfClampingOrWrapping() {
        for (int build : new int[] {0,-1,2100000001})
            assertThrows(IllegalArgumentException.class, () -> version(build, "release", 1));
        assertThrows(IllegalArgumentException.class, () -> version(57,"production",1));
    }
}
