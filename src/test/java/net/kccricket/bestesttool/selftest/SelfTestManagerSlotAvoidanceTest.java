package net.kccricket.bestesttool.selftest;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link SelfTestManager#slotAvoiding} is pure (no live {@code Player}/{@code World} needed), so it
 * is tested directly here rather than by driving a full {@code SelfTestManager} run — building a
 * real arena needs live entity/BlockData behavior MockBukkit doesn't provide (see
 * {@link SelfTestSessionTest}'s header), so the actual case-by-case tool switching this logic feeds
 * stays a manual, live-server verification step.
 */
class SelfTestManagerSlotAvoidanceTest {

    private static SelfTestSpec.KitItem kitItem(int slot, Material material) {
        return new SelfTestSpec.KitItem(slot, material, 1, Map.of());
    }

    private static SelfTestSpec.Stage stage(SelfTestSpec.KitItem... items) {
        return new SelfTestSpec.Stage("test", SelfTestSpec.StageKind.BLOCKS, List.of(items), List.of());
    }

    @Test
    void prefersAGenuinelyEmptySlotOverAnyKitItem() {
        SelfTestSpec.Stage stage = stage(
                kitItem(0, Material.NETHERITE_PICKAXE),
                kitItem(1, Material.DIAMOND_AXE));

        int slot = SelfTestManager.slotAvoiding(stage, SelfTestSpec.Expectation.exact(Material.NETHERITE_PICKAXE));

        assertEquals(2, slot, "slot 2 is the first hotbar slot the kit leaves empty");
    }

    @Test
    void fallsBackToANonMatchingKitItemWhenNoSlotIsEmpty() {
        SelfTestSpec.KitItem[] items = new SelfTestSpec.KitItem[9];
        items[0] = kitItem(0, Material.NETHERITE_PICKAXE);
        for (int i = 1; i < 9; i++) items[i] = kitItem(i, Material.DIRT);
        SelfTestSpec.Stage stage = stage(items);

        int slot = SelfTestManager.slotAvoiding(stage, SelfTestSpec.Expectation.exact(Material.NETHERITE_PICKAXE));

        assertEquals(1, slot, "every slot is filled, so the first non-matching item (slot 1, dirt) must be picked");
    }

    @Test
    void returnsMinusOneWhenEveryFilledSlotAlreadyMatches() {
        SelfTestSpec.KitItem[] items = new SelfTestSpec.KitItem[9];
        for (int i = 0; i < 9; i++) items[i] = kitItem(i, Material.NETHERITE_PICKAXE);
        SelfTestSpec.Stage stage = stage(items);

        int slot = SelfTestManager.slotAvoiding(stage, SelfTestSpec.Expectation.exact(Material.NETHERITE_PICKAXE));

        assertEquals(-1, slot, "nothing in the kit is safe to hold, so there is no slot to switch to");
    }

    @Test
    void bareHandExpectationPicksARealItemInsteadOfAnEmptySlot() {
        SelfTestSpec.Stage stage = stage(
                kitItem(0, Material.DIAMOND_SWORD),
                kitItem(1, Material.DIAMOND_AXE));

        int slot = SelfTestManager.slotAvoiding(stage, SelfTestSpec.Expectation.bareHand());

        assertEquals(0, slot, "an empty hand would trivially satisfy BARE_HAND, so a real item must be picked instead");
    }

    @Test
    void bareHandExpectationWithEmptyKitReturnsMinusOne() {
        SelfTestSpec.Stage stage = stage();

        int slot = SelfTestManager.slotAvoiding(stage, SelfTestSpec.Expectation.bareHand());

        assertEquals(-1, slot, "an empty kit has no real item to hold for a BARE_HAND case");
    }
}
