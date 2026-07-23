package net.kccricket.bestesttool;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeavesUtilsTest extends BestToolsTestBase {

    @Test
    void hasShearsDetectsShearsInHotbar() {
        ItemStack[] inv = new ItemStack[36];
        inv[3] = new ItemStack(Material.SHEARS);
        assertTrue(LeavesUtils.hasShears(true, inv));
    }

    @Test
    void hasShearsRespectsHotbarOnlyBoundary() {
        ItemStack[] inv = new ItemStack[36];
        inv[10] = new ItemStack(Material.SHEARS);
        assertFalse(LeavesUtils.hasShears(true, inv));
        assertTrue(LeavesUtils.hasShears(false, inv));
    }

    @Test
    void hasHoeDetectsAnyHoe() {
        ItemStack[] inv = new ItemStack[9];
        inv[0] = new ItemStack(Material.IRON_HOE);
        assertTrue(LeavesUtils.hasHoe(true, inv));
    }

    @Test
    void hasSwordDetectsAnySword() {
        ItemStack[] inv = new ItemStack[9];
        inv[0] = new ItemStack(Material.IRON_SWORD);
        assertTrue(LeavesUtils.hasSword(true, inv));
    }

    @Test
    void isLeavesMatchesLeafBlocksOnly() {
        assertTrue(LeavesUtils.isLeaves(Material.OAK_LEAVES));
        assertFalse(LeavesUtils.isLeaves(Material.OAK_LOG));
    }
}
