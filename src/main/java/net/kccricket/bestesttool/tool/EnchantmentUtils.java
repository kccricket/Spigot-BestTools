package net.kccricket.bestesttool.tool;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;

public class EnchantmentUtils {

    public static Enchantment getEnchantment(String enchantmentKey) {
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(enchantmentKey));
    }
}
