package net.kccricket.bestesttool.tool;

import net.kccricket.bestesttool.BestToolsTestBase;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.kccricket.bestesttool.tool.LeavesUtils;

class LeavesUtilsTest extends BestToolsTestBase {

    @Test
    void isLeavesMatchesLeafBlocksOnly() {
        assertTrue(LeavesUtils.isLeaves(Material.OAK_LEAVES));
        assertFalse(LeavesUtils.isLeaves(Material.OAK_LOG));
    }
}
