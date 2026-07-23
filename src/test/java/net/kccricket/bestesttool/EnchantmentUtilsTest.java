package net.kccricket.bestesttool;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EnchantmentUtilsTest extends BestToolsTestBase {

    @Test
    void baseMultiplierByMaterialPrefix() {
        assertEquals(8, EnchantmentUtils.getBaseMultiplier(new ItemStack(Material.DIAMOND_PICKAXE)));
        assertEquals(6, EnchantmentUtils.getBaseMultiplier(new ItemStack(Material.IRON_PICKAXE)));
        assertEquals(9, EnchantmentUtils.getBaseMultiplier(new ItemStack(Material.NETHERITE_PICKAXE)));
        assertEquals(4, EnchantmentUtils.getBaseMultiplier(new ItemStack(Material.STONE_PICKAXE)));
        assertEquals(2, EnchantmentUtils.getBaseMultiplier(new ItemStack(Material.WOODEN_PICKAXE)));
        assertEquals(12, EnchantmentUtils.getBaseMultiplier(new ItemStack(Material.GOLDEN_PICKAXE)));
        assertEquals(1, EnchantmentUtils.getBaseMultiplier(new ItemStack(Material.SHEARS)));
    }

    @Test
    void multiplierIncludesEfficiencyBonus() {
        ItemStack pick = new ItemStack(Material.IRON_PICKAXE);
        ItemMeta meta = pick.getItemMeta();
        meta.addEnchant(EnchantmentUtils.getEnchantment("efficiency"), 2, true);
        pick.setItemMeta(meta);

        assertEquals(6 + (2 * 2) + 1, EnchantmentUtils.getMultiplier(pick));
    }

    @Test
    void multiplierWithoutEnchantIsJustBase() {
        ItemStack pick = new ItemStack(Material.IRON_PICKAXE);
        assertEquals(6, EnchantmentUtils.getMultiplier(pick));
    }
}
