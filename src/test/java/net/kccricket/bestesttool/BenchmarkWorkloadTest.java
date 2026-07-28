package net.kccricket.bestesttool;

import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Extends {@link BestToolsTestBase} (not a plain unit test) because {@link BenchmarkWorkload}
 * builds real {@link ItemStack}s with enchantments, which needs a live {@code Registry.ENCHANTMENT}
 * — only available once MockBukkit has bootstrapped a server (see {@code EnchantmentUtils}).
 */
class BenchmarkWorkloadTest extends BestToolsTestBase {

    @Test
    void fullKitHasOneSlotPerInventorySlot() {
        ItemStack[] kit = BenchmarkWorkload.buildKit(BenchmarkWorkload.KitSize.FULL);
        assertEquals(BestToolsHandler.inventorySize, kit.length);
    }

    @Test
    void hotbarKitHasOneSlotPerHotbarSlot() {
        ItemStack[] kit = BenchmarkWorkload.buildKit(BenchmarkWorkload.KitSize.HOTBAR);
        assertEquals(BestToolsHandler.hotbarSize, kit.length);
    }

    @Test
    void everySlotInTheKitIsFilled() {
        for (ItemStack item : BenchmarkWorkload.buildKit(BenchmarkWorkload.KitSize.FULL)) {
            assertNotNull(item);
        }
    }

    @Test
    void blockDataIsBuiltOncePerMaterialInTheSameOrder() {
        var blockData = BenchmarkWorkload.blockData();
        assertEquals(BenchmarkWorkload.MATERIALS.size(), blockData.length);
        for (int i = 0; i < blockData.length; i++) {
            assertEquals(BenchmarkWorkload.MATERIALS.get(i), blockData[i].getMaterial());
        }
    }
}
