package net.kccricket.bestesttool;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeavesUtilsTest extends BestToolsTestBase {

    @Test
    void isLeavesMatchesLeafBlocksOnly() {
        assertTrue(LeavesUtils.isLeaves(Material.OAK_LEAVES));
        assertFalse(LeavesUtils.isLeaves(Material.OAK_LOG));
    }
}
