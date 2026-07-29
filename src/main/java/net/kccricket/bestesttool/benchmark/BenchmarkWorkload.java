package net.kccricket.bestesttool.benchmark;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.function.BooleanSupplier;
import net.kccricket.bestesttool.tool.BestToolsHandler;
import net.kccricket.bestesttool.tool.EnchantmentUtils;

/**
 * The fixed, hardcoded workload driven by {@code /bestesttool benchmark}. Deliberately not
 * config-driven (unlike {@code selftest/stages.yml}) — hardcoding it is what makes a run
 * reproducible and comparable across versions and machines, rather than an artifact of whatever a
 * particular server's admin happened to be carrying or standing near.
 */
public final class BenchmarkWorkload {

    private BenchmarkWorkload() {}

    /** Inventory size selectable via {@code /bestesttool benchmark start [full|hotbar]}. */
    public enum KitSize {
        FULL(BestToolsHandler.inventorySize),
        HOTBAR(BestToolsHandler.hotbarSize);

        final int slots;

        KitSize(int slots) {
            this.slots = slots;
        }
    }

    /**
     * Materials spanning the interesting shapes of {@link BestToolsHandler#selectBestTool}: plain
     * stone, ores that set {@code requiresCorrectToolForDrops} (exercising {@code
     * isPreferredTool}), logs, leaves, dirt/sand, glass (a Silk-Touch-relevant material),
     * cobweb/seagrass (shears), and an insta-break block (torch, wheat).
     */
    static final List<Material> MATERIALS = List.of(
            Material.STONE, Material.DEEPSLATE, Material.COBBLESTONE,
            Material.IRON_ORE, Material.DIAMOND_ORE, Material.ANCIENT_DEBRIS, Material.OBSIDIAN,
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.OAK_PLANKS, Material.CRAFTING_TABLE,
            Material.OAK_LEAVES, Material.SPRUCE_LEAVES,
            Material.DIRT, Material.SAND, Material.GRAVEL, Material.SNOW,
            Material.HAY_BLOCK, Material.MOSS_BLOCK,
            Material.COBWEB, Material.SEAGRASS,
            Material.GLASS, Material.SEA_LANTERN,
            Material.WHEAT, Material.TORCH
    );

    /**
     * {@link BlockData} built once per {@link #MATERIALS} entry (same order), reused for every
     * selection call in a run rather than re-created per iteration.
     */
    static BlockData[] blockData() {
        return MATERIALS.stream().map(Material::createBlockData).toArray(BlockData[]::new);
    }

    /**
     * Always {@code false} — the benchmark's kit always has a real tool candidate for every
     * material in {@link #MATERIALS}, so the Silk Touch fallback path in
     * {@link BestToolsHandler#selectBestTool} is never reached. Just needs to be a stable, cheap
     * supplier; see {@code selectBestTool}'s javadoc for why it must stay lazy at all.
     */
    static final BooleanSupplier NEVER_SILK = () -> false;

    /**
     * A synthetic tool kit: one of each tier, an Efficiency-enchanted pickaxe, a Silk Touch
     * pickaxe, and non-tool filler padding out the rest of {@code size}'s slots — a reasonable
     * stand-in for "some player's actual inventory" without reading a real one.
     */
    static ItemStack[] buildKit(KitSize size) {
        ItemStack[] items = new ItemStack[size.slots];
        int i = 0;
        items[i++] = new ItemStack(Material.WOODEN_PICKAXE);
        items[i++] = new ItemStack(Material.STONE_AXE);
        items[i++] = new ItemStack(Material.IRON_SHOVEL);
        items[i++] = new ItemStack(Material.GOLDEN_HOE);
        items[i++] = new ItemStack(Material.DIAMOND_SWORD);
        items[i++] = new ItemStack(Material.SHEARS);
        items[i++] = enchant(new ItemStack(Material.NETHERITE_PICKAXE), "efficiency", 5);
        items[i++] = enchant(new ItemStack(Material.DIAMOND_PICKAXE), "silk_touch", 1);

        // Filler: non-tool items, the way most of a real inventory is blocks/food/junk rather
        // than tools.
        Material[] filler = {Material.COBBLESTONE, Material.DIRT, Material.BREAD, Material.TORCH};
        for (; i < items.length; i++) {
            items[i] = new ItemStack(filler[i % filler.length]);
        }
        return items;
    }

    private static ItemStack enchant(ItemStack item, String enchantKey, int level) {
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(EnchantmentUtils.getEnchantment(enchantKey), level, true);
        item.setItemMeta(meta);
        return item;
    }
}
