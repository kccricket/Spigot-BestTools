package net.kccricket.bestesttool;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwordUtilsTest extends BestToolsTestBase {

    @Test
    void baseDamageByMaterial() {
        assertEquals(9, SwordUtils.getBaseDamage(Material.IRON_AXE));
        assertEquals(6, SwordUtils.getBaseDamage(Material.IRON_SWORD));
        assertEquals(5, SwordUtils.getBaseDamage(Material.STONE_SWORD));
        assertEquals(4, SwordUtils.getBaseDamage(Material.WOODEN_SWORD));
        assertEquals(8, SwordUtils.getBaseDamage(Material.NETHERITE_SWORD));
        assertEquals(10, SwordUtils.getBaseDamage(Material.NETHERITE_AXE));
        assertEquals(0, SwordUtils.getBaseDamage(Material.DIRT));
    }

    @Test
    void isUndeadTrueForZombieFalseForCreeper() {
        assertTrue(SwordUtils.isUndead(EntityType.ZOMBIE));
        assertFalse(SwordUtils.isUndead(EntityType.CREEPER));
    }

    @Test
    void isAnthropodTrueForSpiderFalseForZombie() {
        assertTrue(SwordUtils.isAnthropod(EntityType.SPIDER));
        assertFalse(SwordUtils.isAnthropod(EntityType.ZOMBIE));
    }

    @Test
    void smiteBonusAppliesOnlyToUndead() {
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = sword.getItemMeta();
        meta.addEnchant(EnchantmentUtils.getEnchantment("smite"), 2, true);
        sword.setItemMeta(meta);

        assertEquals(2.5 * 2, SwordUtils.getBonus(sword, EntityType.ZOMBIE));
        assertEquals(0, SwordUtils.getBonus(sword, EntityType.CREEPER));
    }

    @Test
    void getDamageCombinesBaseAndBonus() {
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        assertEquals(6, SwordUtils.getDamage(sword, EntityType.CREEPER));
    }
}
