package net.kccricket.bestesttool;

import net.kccricket.bestesttool.BestToolsHandler.Tool;
import net.kccricket.kcmclib.logging.Log;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Objects;

public class BestToolsUtils {

    final Material[] weapons = {Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.NETHERITE_SWORD, Material.DIAMOND_SWORD, Material.GOLDEN_SWORD, Material.IRON_SWORD, Material.STONE_SWORD, Material.WOODEN_SWORD, Material.MACE};
    final Material[] instaBreakableByHand = {Material.COMPARATOR, Material.REPEATER, Material.REDSTONE_WIRE, Material.REDSTONE_TORCH, Material.REDSTONE_WALL_TORCH, Material.TORCH, Material.SOUL_TORCH, Material.WALL_TORCH, Material.SOUL_WALL_TORCH, Material.COPPER_TORCH, Material.COPPER_WALL_TORCH,
            Material.SCAFFOLDING, Material.SLIME_BLOCK, Material.HONEY_BLOCK, Material.TNT, Material.TRIPWIRE, Material.TRIPWIRE_HOOK, Material.SHORT_GRASS, Material.SUGAR_CANE, Material.LILY_PAD,
            Material.OAK_SAPLING, Material.SPRUCE_SAPLING, Material.BIRCH_SAPLING, Material.JUNGLE_SAPLING, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING,
            Material.BROWN_MUSHROOM, Material.RED_MUSHROOM, Material.CRIMSON_FUNGUS, Material.WARPED_FUNGUS, Material.CRIMSON_ROOTS, Material.WARPED_ROOTS, Material.WEEPING_VINES, Material.TWISTING_VINES,
            Material.DEAD_BUSH, Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.PUMPKIN_STEM, Material.MELON_STEM, Material.NETHER_WART, Material.FLOWER_POT,
            Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM, Material.AZURE_BLUET, Material.RED_TULIP, Material.ORANGE_TULIP, Material.WHITE_TULIP, Material.PINK_TULIP, Material.OXEYE_DAISY, Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY, Material.WITHER_ROSE, Material.SUNFLOWER, Material.LILAC, Material.ROSE_BUSH, Material.PEONY,
            Material.POTTED_DANDELION, Material.POTTED_POPPY, Material.POTTED_BLUE_ORCHID, Material.POTTED_ALLIUM, Material.POTTED_AZURE_BLUET, Material.POTTED_RED_TULIP, Material.POTTED_ORANGE_TULIP, Material.POTTED_WHITE_TULIP, Material.POTTED_PINK_TULIP, Material.POTTED_OXEYE_DAISY, Material.POTTED_CORNFLOWER, Material.POTTED_LILY_OF_THE_VALLEY, Material.POTTED_WITHER_ROSE,
            Material.TUBE_CORAL, Material.BRAIN_CORAL, Material.BUBBLE_CORAL, Material.FIRE_CORAL, Material.HORN_CORAL, Material.DEAD_TUBE_CORAL, Material.DEAD_BRAIN_CORAL, Material.DEAD_BUBBLE_CORAL, Material.DEAD_FIRE_CORAL, Material.DEAD_HORN_CORAL};
    final Material[] hoes = {Material.NETHERITE_HOE, Material.DIAMOND_HOE, Material.GOLDEN_HOE, Material.IRON_HOE, Material.STONE_HOE, Material.WOODEN_HOE};
    final Material[] pickaxes = {Material.NETHERITE_PICKAXE, Material.DIAMOND_PICKAXE, Material.GOLDEN_PICKAXE, Material.IRON_PICKAXE, Material.STONE_PICKAXE, Material.WOODEN_PICKAXE};
    final Material[] axes = {Material.NETHERITE_AXE, Material.DIAMOND_AXE, Material.GOLDEN_AXE, Material.IRON_AXE, Material.STONE_AXE, Material.WOODEN_AXE};
    final Material[] shovels = {Material.NETHERITE_SHOVEL, Material.DIAMOND_SHOVEL, Material.GOLDEN_SHOVEL, Material.IRON_SHOVEL, Material.STONE_SHOVEL, Material.WOODEN_SHOVEL};
    final Material[] swords = {Material.NETHERITE_SWORD, Material.DIAMOND_SWORD, Material.GOLDEN_SWORD, Material.IRON_SWORD, Material.STONE_SWORD, Material.WOODEN_SWORD};
    final Material[] defaultMats = {
            Material.DIAMOND_PICKAXE,
            Material.DIAMOND_AXE,
            Material.DIAMOND_HOE,
            Material.DIAMOND_SHOVEL,

            Material.GOLDEN_PICKAXE,
            Material.GOLDEN_AXE,
            Material.GOLDEN_HOE,
            Material.GOLDEN_SHOVEL,

            Material.IRON_PICKAXE,
            Material.IRON_AXE,
            Material.IRON_HOE,
            Material.IRON_SHOVEL,

            Material.STONE_PICKAXE,
            Material.STONE_AXE,
            Material.STONE_HOE,
            Material.STONE_SHOVEL,

            Material.WOODEN_PICKAXE,
            Material.WOODEN_AXE,
            Material.WOODEN_HOE,
            Material.WOODEN_SHOVEL,

            Material.SHEARS
    };
    final Material[] netheriteTools = {
            Material.NETHERITE_PICKAXE,
            Material.NETHERITE_AXE,
            Material.NETHERITE_HOE,
            Material.NETHERITE_SHOVEL
    };

    final Main main;
    //final Map<Material,Tool> uToolMap;


    // This is called AFTER BestToolsHandler, so the Utils can affect the Handler
    public BestToolsUtils(@NotNull Main main) {

        this.main = Objects.requireNonNull(main, "Main must not be null");
        Objects.requireNonNull(main.toolHandler, "BestToolsHandler must be instantiated before BestToolUtils!");

        // Register valid weapons
        main.toolHandler.weapons.addAll(Arrays.asList(weapons));

        // Register all InstaBreaksByHand
        main.toolHandler.instaBreakableByHand.addAll(Arrays.asList(instaBreakableByHand));

        // Hoes
        main.toolHandler.hoes.addAll(Arrays.asList(hoes));

        // Pickaxes
        main.toolHandler.pickaxes.addAll(Arrays.asList(pickaxes));

        // Axes
        main.toolHandler.axes.addAll(Arrays.asList(axes));

        // Shovels
        main.toolHandler.shovels.addAll(Arrays.asList(shovels));

        // Swords
        main.toolHandler.swords.addAll(Arrays.asList(swords));

        main.toolHandler.allTools.addAll(Arrays.asList(defaultMats));
        main.toolHandler.allTools.addAll(Arrays.asList(netheriteTools));

        this.initMap();

        //uToolMap = Map.copyOf(main.toolHandler.toolMap); // Java 10+ only
    }

    private void tagToMap(@NotNull Tag<Material> tag, @NotNull Tool tool) {
        tagToMap(Objects.requireNonNull(tag, "Tag must not be null"),
                Objects.requireNonNull(tool, "Tool must not be null"),
                null);
    }

    private void tagToMap(@NotNull Tag<Material> tag, @NotNull Tool tool, @Nullable String match) {
        Objects.requireNonNull(tag, "Tag must not be null");
        Objects.requireNonNull(tool, "Tool must not be null");
        for (Material mat : tag.getValues()) {
            if (match == null) {
                addToMap(mat, tool);
            } else {
                if (mat.name().contains(match)) {
                    addToMap(mat, tool);
                }
            }
        }
        //usedTags.add(tag);
    }

    // private void printMap(HashMap<Material, Tool> toolMap) {
    //     toolMap.forEach((mat, tool) -> System.out.println(String.format("%0$30s -> %s", mat.name(), tool.name())));
    // }

    private void addToMap(@NotNull Material mat, @NotNull Tool tool) {
        Objects.requireNonNull(Objects.requireNonNull(main.toolHandler, "ToolHandler must not be null").
                toolMap, "ToolMap must not be null")
                .put(Objects.requireNonNull(mat, "Material must not be null"),
                        Objects.requireNonNull(tool, "Tool must not be null"));
    }

    void initMap() {
        long startTime = System.nanoTime();

        tagToMap(Tag.ANVIL, Tool.PICKAXE);

        tagToMap(Tag.ICE, Tool.PICKAXE);
        tagToMap(Tag.LEAVES, Tool.SHEARS);
        tagToMap(Tag.LOGS, Tool.AXE);
        tagToMap(Tag.PLANKS, Tool.AXE);
        tagToMap(Tag.RAILS, Tool.PICKAXE);
        tagToMap(Tag.WOOL, Tool.SHEARS);

        // WATCH OUT FOR ORDER - START //
        tagToMap(Tag.BUTTONS, Tool.AXE);
        tagToMap(Tag.BUTTONS, Tool.PICKAXE, "STONE");

        tagToMap(Tag.DOORS, Tool.AXE);
        tagToMap(Tag.DOORS, Tool.PICKAXE, "IRON");

        tagToMap(Tag.TRAPDOORS, Tool.AXE);
        tagToMap(Tag.TRAPDOORS, Tool.PICKAXE, "IRON");

        tagToMap(Tag.SLABS, Tool.PICKAXE);
        tagToMap(Tag.WOODEN_SLABS, Tool.AXE);

        tagToMap(Tag.STAIRS, Tool.PICKAXE);
        tagToMap(Tag.WOODEN_STAIRS, Tool.PICKAXE);

        // WATCH OUT FOR ORDER - END //

        tagToMap(Tag.SAND, Tool.SHOVEL);
        tagToMap(Tag.STONE_BRICKS, Tool.PICKAXE);

        addToMap(Material.SEAGRASS, Tool.SHEARS);
        addToMap(Material.TALL_SEAGRASS, Tool.SHEARS);

        tagToMap(Tag.BAMBOO_PLANTABLE_ON, Tool.SHOVEL);
        tagToMap(Tag.SIGNS, Tool.AXE);
        tagToMap(Tag.WALLS, Tool.PICKAXE);

        // Order important START
        tagToMap(Tag.FENCES, Tool.AXE);
        tagToMap(Tag.FENCES, Tool.PICKAXE, "NETHER");
        tagToMap(Tag.FENCES, Tool.PICKAXE, "BRICK");
        // Order important END

        tagToMap(Tag.BEEHIVES, Tool.AXE);
        tagToMap(Tag.SHULKER_BOXES, Tool.PICKAXE);

        // The following kind of unneccessary anyway
        tagToMap(Tag.CROPS, Tool.NONE);
        tagToMap(Tag.FLOWERS, Tool.NONE);

        tagToMap(Tag.CRIMSON_STEMS, Tool.AXE);
        tagToMap(Tag.FENCE_GATES, Tool.AXE);
        tagToMap(Tag.NYLIUM, Tool.PICKAXE);
        // Important order START //
        tagToMap(Tag.PRESSURE_PLATES, Tool.PICKAXE);
        tagToMap(Tag.WOODEN_PRESSURE_PLATES, Tool.AXE);
        // Important order STOP //

        addToMap(Material.ACACIA_LEAVES, Tool.SHEARS);
        addToMap(Material.BAMBOO_SAPLING, Tool.AXE);
        addToMap(Material.BIRCH_LEAVES, Tool.SHEARS);
        addToMap(Material.COBWEB, Tool.SHEARS);
        addToMap(Material.DARK_OAK_LEAVES, Tool.SHEARS);
        addToMap(Material.GLOWSTONE, Tool.PICKAXE); // TODO: Prefer SilkTouch
        addToMap(Material.JUNGLE_LEAVES, Tool.SHEARS);
        addToMap(Material.MOVING_PISTON, Tool.PICKAXE);
        addToMap(Material.OAK_LEAVES, Tool.SHEARS);
        addToMap(Material.SPRUCE_LEAVES, Tool.SHEARS);
        addToMap(Material.VINE, Tool.SHEARS);

        for (Material mat : Material.values()) {
            if(mat.isLegacy() || !mat.isBlock()) continue;

            String matName = mat.name();

            if(matName.contains("GLASS")) {
                addToMap(mat, Tool.NONE);
            }
            if(matName.contains("CARPET")) {
                addToMap(mat, Tool.NONE);
            }
            if (matName.contains("AMETHYST")) {
                addToMap(mat, Tool.PICKAXE);
            }
            if(matName.endsWith("_ORE")) {
                addToMap(mat, Tool.PICKAXE);
            }
            if(matName.contains("BASALT")) {
                addToMap(mat, Tool.PICKAXE);
            }
            if(matName.contains("DEEPSLATE")) {
                addToMap(mat, Tool.PICKAXE);
            }
        }

        addToMap(Material.GLOW_LICHEN, Tool.SHEARS);
        addToMap(Material.CALCITE, Tool.PICKAXE);
        addToMap(Material.MOSS_BLOCK, Tool.HOE);
        addToMap(Material.MOSS_CARPET, Tool.HOE);

        tagToMap(Tag.CANDLES, Tool.NONE);

        tagToMap(Tag.MINEABLE_AXE, Tool.AXE);
        tagToMap(Tag.MINEABLE_HOE, Tool.HOE);
        tagToMap(Tag.MINEABLE_PICKAXE, Tool.PICKAXE);
        tagToMap(Tag.MINEABLE_SHOVEL, Tool.SHOVEL);

        long endTime = System.nanoTime();

        Log.debug(String.format("Building the <Block,Tool> map took %d ms", (endTime - startTime) / 1000000));
    }

}
