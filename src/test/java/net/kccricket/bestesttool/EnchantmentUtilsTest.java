package net.kccricket.bestesttool;

import org.bukkit.enchantments.Enchantment;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EnchantmentUtilsTest extends BestToolsTestBase {

    @Test
    void getEnchantmentResolvesVanillaKey() {
        Enchantment efficiency = EnchantmentUtils.getEnchantment("efficiency");

        assertNotNull(efficiency);
        assertEquals("efficiency", efficiency.getKey().getKey());
    }
}
