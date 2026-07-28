package net.kccricket.bestesttool;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Pure comparison logic behind a self-test case's verdict — split out of {@link SelfTestListener}
 * so it can be unit-tested against plain {@link ItemStack}s without a live event/world.
 */
final class SelfTestEvaluator {

    private SelfTestEvaluator() {}

    /**
     * @param before the main-hand stack just before the plugin's switch logic ran (only consulted
     *               for {@link SelfTestSpec.Expectation.Kind#UNCHANGED})
     * @param after the main-hand stack after the plugin has had a chance to act
     * @param afterIsDamageable whether {@code after} counts as a "real tool" for
     *                          {@link SelfTestSpec.Expectation.Kind#BARE_HAND} purposes — callers
     *                          pass {@code BestToolsHandler.isDamageable(after)}
     */
    static boolean matches(SelfTestSpec.Expectation expectation, ItemStack before, ItemStack after, boolean afterIsDamageable) {
        return switch (expectation.kind) {
            case EXACT, ANY_OF -> expectation.materials.contains(after == null ? Material.AIR : after.getType());
            case BARE_HAND -> isEmpty(after) || !afterIsDamageable;
            case UNCHANGED -> itemsEqual(before, after);
        };
    }

    static boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
    }

    static boolean itemsEqual(ItemStack a, ItemStack b) {
        boolean aEmpty = isEmpty(a);
        boolean bEmpty = isEmpty(b);
        if (aEmpty || bEmpty) return aEmpty == bEmpty;
        return a.isSimilar(b);
    }

    static String describe(ItemStack item) {
        return isEmpty(item) ? "an empty hand" : item.getType().name();
    }
}
