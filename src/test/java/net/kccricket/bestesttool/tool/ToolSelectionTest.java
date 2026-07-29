package net.kccricket.bestesttool.tool;

import net.kccricket.bestesttool.BestToolsTestBase;
import net.kccricket.bestesttool.tool.BestToolsHandler.Tool;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockbukkit.mockbukkit.block.data.BlockDataMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.kccricket.bestesttool.listeners.BestToolsListener;
import net.kccricket.bestesttool.model.PlayerSetting;
import net.kccricket.bestesttool.tool.BestToolsHandler;
import net.kccricket.bestesttool.tool.EnchantmentUtils;
import net.kccricket.bestesttool.tool.SwordUtils;

class ToolSelectionTest extends BestToolsTestBase {

    // Note on what's NOT tested here: BestToolsHandler.getBestToolFromInventory(Block, ...) itself
    // isn't unit-tested. It calls block.getBlockData(), and MockBukkit's BlockDataMock throws
    // UnimplementedOperationException from getDestroySpeed/isPreferredTool/requiresCorrectToolForDrops
    // (confirmed by reading its source — there is no working mock block data in this harness). The
    // ranking core (getBestItemStackFromArray) is unit-tested below against a hand-rolled fake that
    // implements those three methods for real; the block-mining integration is covered by manual
    // verification against a live Paper server instead (see the plan's Verification section).

    private static Stream<Arguments> toolTypeMatrix() {
        return Stream.of(
                Arguments.of(Material.STONE, Tool.PICKAXE),
                Arguments.of(Material.IRON_ORE, Tool.PICKAXE),
                Arguments.of(Material.DIAMOND_ORE, Tool.PICKAXE),
                Arguments.of(Material.OBSIDIAN, Tool.PICKAXE),

                Arguments.of(Material.OAK_LOG, Tool.AXE),
                Arguments.of(Material.OAK_PLANKS, Tool.AXE),
                Arguments.of(Material.CRAFTING_TABLE, Tool.AXE),

                Arguments.of(Material.DIRT, Tool.SHOVEL),
                Arguments.of(Material.SAND, Tool.SHOVEL),
                Arguments.of(Material.GRAVEL, Tool.SHOVEL),
                Arguments.of(Material.SNOW, Tool.SHOVEL),

                Arguments.of(Material.HAY_BLOCK, Tool.HOE),
                Arguments.of(Material.MOSS_BLOCK, Tool.HOE),
                Arguments.of(Material.MOSS_CARPET, Tool.HOE),

                Arguments.of(Material.COBWEB, Tool.SHEARS),
                Arguments.of(Material.SEAGRASS, Tool.SHEARS),

                Arguments.of(Material.WHEAT, Tool.NONE),
                Arguments.of(Material.TORCH, Tool.NONE),
                Arguments.of(Material.GLASS, Tool.NONE),
                Arguments.of(Material.WHITE_CARPET, Tool.NONE)
        );
    }

    @ParameterizedTest
    @MethodSource("toolTypeMatrix")
    void getBestToolType_matchesExpectedTool(Material mat, Tool expected) {
        assertEquals(expected, plugin.toolHandler.getBestToolType(mat));
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"GLOWSTONE", "ENDER_CHEST", "QUARTZ", "SPAWNER", "SEA_LANTERN",
            "NETHER_GOLD_ORE", "GLASS", "TINTED_GLASS", "GLASS_PANE", "BEEHIVE", "BEE_NEST",
            "AMETHYST_CLUSTER", "LARGE_AMETHYST_BUD"})
    void profitsFromSilkTouch_trueForSpecialBlocks(Material mat) {
        assertTrue(plugin.toolHandler.profitsFromSilkTouch(mat));
    }

    @ParameterizedTest
    @EnumSource(value = Material.class, names = {"STONE", "DIRT"})
    void profitsFromSilkTouch_falseForPlainBlocks(Material mat) {
        assertFalse(plugin.toolHandler.profitsFromSilkTouch(mat));
    }

    @Test
    void noMineableTagOverrides() {
        assertEquals(Tool.PICKAXE, plugin.toolHandler.getBestToolType(Material.GLOWSTONE));
        assertEquals(Tool.PICKAXE, plugin.toolHandler.getBestToolType(Material.CALCITE));
        assertEquals(Tool.SHEARS, plugin.toolHandler.getBestToolType(Material.SEAGRASS));
        assertEquals(Tool.HOE, plugin.toolHandler.getBestToolType(Material.MOSS_BLOCK));
    }

    @Test
    void getBestRoscoeFromInventory_picksHighestDamageSword() {
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        inv.setItem(0, new ItemStack(Material.WOODEN_SWORD));
        inv.setItem(1, new ItemStack(Material.IRON_SWORD));

        ItemStack best = plugin.toolHandler.getBestRoscoeFromInventory(EntityType.ZOMBIE, player, true, null, false);

        assertEquals(Material.IRON_SWORD, best.getType());
    }

    // --- Copper tools: regression coverage for the gap where copper tiers were absent from every
    // hand-typed array, so the plugin never recognized them at all (mining, combat, or the
    // Silk-Touch pass) --------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = Material.class,
            names = {"COPPER_PICKAXE", "COPPER_AXE", "COPPER_HOE", "COPPER_SHOVEL"})
    void isTool_trueForCopperTools(Material mat) {
        assertTrue(plugin.toolHandler.isTool(new ItemStack(mat)));
    }

    @Test
    void isToolOrRoscoe_trueForCopperSword() {
        assertTrue(plugin.toolHandler.isToolOrRoscoe(new ItemStack(Material.COPPER_SWORD)));
    }

    @Test
    void weapons_containsCopperSword() {
        // The dont_switch_during_battle guard (BestToolsListener#isWeapon) reads this list
        // directly, so this is what actually protects a held copper sword during combat.
        assertTrue(plugin.toolHandler.weapons.contains(Material.COPPER_SWORD));
    }

    @Test
    void swordUtilsGetBaseDamage_knowsCopperTools() {
        assertEquals(5, SwordUtils.getBaseDamage(Material.COPPER_SWORD),
                "COPPER's ToolMaterial.attackDamageBonus (1.0) matches STONE's, so COPPER_SWORD "
                        + "must score the same base damage as STONE_SWORD");
        assertEquals(9, SwordUtils.getBaseDamage(Material.COPPER_AXE),
                "COPPER_AXE must score the same base damage as STONE_AXE/IRON_AXE/DIAMOND_AXE");
    }

    @Test
    void getBestRoscoeFromInventory_choosesCopperSwordOverWoodenSword() {
        // Regression guard for the gap SwordUtils#getBaseDamage would otherwise leave: a copper
        // sword being classified as a roscoe is meaningless if it still scores 0 damage and can
        // never actually be chosen.
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        inv.setItem(0, new ItemStack(Material.WOODEN_SWORD));
        inv.setItem(1, new ItemStack(Material.COPPER_SWORD));

        ItemStack best = plugin.toolHandler.getBestRoscoeFromInventory(EntityType.ZOMBIE, player, true, null, false);

        assertEquals(Material.COPPER_SWORD, best.getType());
    }

    // --- getBestItemStackFromArray: the live-speed ranking core ------------------------------

    @Test
    void getBestItemStackFromArray_picksFasterOfTwoCandidates() {
        ItemStack wooden = new ItemStack(Material.WOODEN_PICKAXE);
        ItemStack stone = new ItemStack(Material.STONE_PICKAXE);
        ItemStack[] items = {wooden, stone};

        FakeBlockData data = new FakeBlockData(Material.STONE, false,
                Map.of(Material.WOODEN_PICKAXE, 2f, Material.STONE_PICKAXE, 4f), Set.of());

        ItemStack best = plugin.toolHandler.getBestItemStackFromArray(data, items, false, Material.STONE, 1.0f);

        assertEquals(Material.STONE_PICKAXE, best.getType());
    }

    @Test
    void getBestItemStackFromArray_preferredToolBeatsFasterButIncorrectTool() {
        // The headline scenario this whole feature exists for: raw getDestroySpeed alone would
        // pick the Efficiency V iron pickaxe (fast, but iron doesn't drop obsidian) over a plain
        // diamond pickaxe. isPreferredTool is what makes drops win.
        ItemStack fastWrongTool = new ItemStack(Material.IRON_PICKAXE);
        ItemStack slowCorrectTool = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemStack[] items = {fastWrongTool, slowCorrectTool};

        FakeBlockData obsidian = new FakeBlockData(Material.OBSIDIAN, true,
                Map.of(Material.IRON_PICKAXE, 32f, Material.DIAMOND_PICKAXE, 8f),
                Set.of(Material.DIAMOND_PICKAXE));

        ItemStack best = plugin.toolHandler.getBestItemStackFromArray(obsidian, items, false, Material.OBSIDIAN, 1.0f);

        assertEquals(Material.DIAMOND_PICKAXE, best.getType());
    }

    @Test
    void getBestItemStackFromArray_fallsBackToFastestWhenNothingIsPreferred() {
        ItemStack wooden = new ItemStack(Material.WOODEN_PICKAXE);
        ItemStack iron = new ItemStack(Material.IRON_PICKAXE);
        ItemStack[] items = {wooden, iron};

        FakeBlockData diamondOre = new FakeBlockData(Material.DIAMOND_ORE, true,
                Map.of(Material.WOODEN_PICKAXE, 2f, Material.IRON_PICKAXE, 6f), Set.of());

        ItemStack best = plugin.toolHandler.getBestItemStackFromArray(diamondOre, items, false, Material.DIAMOND_ORE, 1.0f);

        assertEquals(Material.IRON_PICKAXE, best.getType());
    }

    @Test
    void getBestItemStackFromArray_skipsPreferredToolCheckWhenDropsDontRequireIt() {
        ItemStack shovel = new ItemStack(Material.IRON_SHOVEL);
        ItemStack[] items = {shovel};

        FakeBlockData dirt = new FakeBlockData(Material.DIRT, false, Map.of(Material.IRON_SHOVEL, 6f), Set.of());

        ItemStack best = plugin.toolHandler.getBestItemStackFromArray(dirt, items, false, Material.DIRT, 1.0f);

        assertEquals(Material.IRON_SHOVEL, best.getType());
        assertTrue(dirt.preferredToolChecks.isEmpty());
    }

    @Test
    void getBestItemStackFromArray_tiesKeepLowestSlotIndex() {
        ItemStack first = new ItemStack(Material.IRON_PICKAXE);
        ItemStack second = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemStack[] items = {first, second};

        FakeBlockData data = new FakeBlockData(Material.STONE, false,
                Map.of(Material.IRON_PICKAXE, 6f, Material.DIAMOND_PICKAXE, 6f), Set.of());

        ItemStack best = plugin.toolHandler.getBestItemStackFromArray(data, items, false, Material.STONE, 1.0f);

        assertEquals(first, best);
    }

    @Test
    void getBestItemStackFromArray_returnsNullWhenNothingBeatsBareHand() {
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemStack[] items = {sword};

        FakeBlockData data = new FakeBlockData(Material.STONE, false, Map.of(), Set.of());

        assertNull(plugin.toolHandler.getBestItemStackFromArray(data, items, false, Material.STONE, 1.0f));
    }

    @Test
    void getBestItemStackFromArray_silkTouchPickaxePreferredWhenPresent() {
        ItemStack plainPick = new ItemStack(Material.IRON_PICKAXE);
        ItemStack silkPick = enchanted(Material.IRON_PICKAXE, "silk_touch", 1);
        ItemStack[] items = {plainPick, silkPick};

        FakeBlockData glowstone = new FakeBlockData(Material.GLOWSTONE, false, Map.of(Material.IRON_PICKAXE, 6f), Set.of());

        ItemStack best = plugin.toolHandler.getBestItemStackFromArray(glowstone, items, true, Material.GLOWSTONE, 1.0f);

        assertEquals(silkPick, best);
    }

    @Test
    void getBestItemStackFromArray_fallsBackToPlainPickaxeWithoutSilkTouch() {
        ItemStack plainPick = new ItemStack(Material.IRON_PICKAXE);
        ItemStack[] items = {plainPick};

        FakeBlockData glowstone = new FakeBlockData(Material.GLOWSTONE, false, Map.of(Material.IRON_PICKAXE, 6f), Set.of());

        ItemStack best = plugin.toolHandler.getBestItemStackFromArray(glowstone, items, true, Material.GLOWSTONE, 1.0f);

        assertEquals(plainPick, best);
    }

    @Test
    void getBestItemStackFromArray_floorZeroLetsSilkTouchWinAtBareHandSpeed() {
        // The scenario silkChangesDrops()/getBestToolFromInventory rely on: a block with no
        // matching #mineable/* tool (empty speed map, so every item is at bare-hand speed 1.0f).
        // A Silk Touch tool has to be selectable there even though it's no faster than a hand.
        ItemStack silkPick = enchanted(Material.IRON_PICKAXE, "silk_touch", 1);
        ItemStack[] items = {silkPick};

        FakeBlockData glass = new FakeBlockData(Material.GLASS, false, Map.of(), Set.of());

        assertEquals(silkPick, plugin.toolHandler.getBestItemStackFromArray(glass, items, true, Material.GLASS, 0.0f));
        assertNull(plugin.toolHandler.getBestItemStackFromArray(glass, items, true, Material.GLASS, 1.0f));
    }

    @Test
    void getBestItemStackFromArray_silkPassSkipsNonToolsEvenWithTheEnchant() {
        ItemStack silkHelmet = enchanted(Material.DIAMOND_HELMET, "silk_touch", 1);
        ItemStack[] items = {silkHelmet};

        FakeBlockData glass = new FakeBlockData(Material.GLASS, false, Map.of(), Set.of());

        assertNull(plugin.toolHandler.getBestItemStackFromArray(glass, items, true, Material.GLASS, 0.0f));
    }

    // --- isCandidate: the only remaining category filter (sword-for-leaves/cobweb toggles) -------

    @Test
    void isCandidate_excludesSwordForLeavesWhenToggleOff() {
        assertFalse(plugin.toolHandler.isCandidate(new ItemStack(Material.IRON_SWORD), Material.OAK_LEAVES));
    }

    @Test
    void isCandidate_includesSwordForLeavesWhenToggleOn() {
        plugin.getConfig().set("consider_swords_for_leaves", true);
        BestToolsHandler handler = new BestToolsHandler(plugin);

        assertTrue(handler.isCandidate(new ItemStack(Material.IRON_SWORD), Material.OAK_LEAVES));
    }

    @Test
    void isCandidate_excludesSwordForCobwebWhenToggleOff() {
        assertFalse(plugin.toolHandler.isCandidate(new ItemStack(Material.IRON_SWORD), Material.COBWEB));
    }

    @Test
    void isCandidate_includesSwordForCobwebWhenToggleOn() {
        plugin.getConfig().set("consider_swords_for_cobwebs", true);
        BestToolsHandler handler = new BestToolsHandler(plugin);

        assertTrue(handler.isCandidate(new ItemStack(Material.IRON_SWORD), Material.COBWEB));
    }

    @Test
    void isCandidate_nonSwordAlwaysCandidate() {
        assertTrue(plugin.toolHandler.isCandidate(new ItemStack(Material.SHEARS), Material.OAK_LEAVES));
        assertTrue(plugin.toolHandler.isCandidate(new ItemStack(Material.IRON_HOE), Material.COBWEB));
    }

    @Test
    void isCandidate_swordUnrestrictedAwayFromLeavesAndCobwebs() {
        assertTrue(plugin.toolHandler.isCandidate(new ItemStack(Material.IRON_SWORD), Material.STONE));
    }

    // --- isDamageable: the regression that let a pristine tool masquerade as a bare hand --------

    @Test
    void isDamageable_trueForPristineTool() {
        // The bug this guards against: a pristine (unenchanted, undamaged) tool has no ItemMeta
        // component patch, so the old hasItemMeta()-first check misclassified it as NOT
        // damageable — which is exactly what let getNonToolItemFromArray hand back a diamond
        // pickaxe as a "bare hand" substitute for bedrock/glass/decorated pots.
        assertTrue(plugin.toolHandler.isDamageable(new ItemStack(Material.DIAMOND_PICKAXE)));
        assertTrue(plugin.toolHandler.isDamageable(new ItemStack(Material.SHEARS)));
    }

    @Test
    void isDamageable_falseForMaterialsWithNoDurability() {
        assertFalse(plugin.toolHandler.isDamageable(new ItemStack(Material.DIRT)));
        assertFalse(plugin.toolHandler.isDamageable(new ItemStack(Material.STONE)));
    }

    @Test
    void isDamageable_falseForNull() {
        assertFalse(plugin.toolHandler.isDamageable(null));
    }

    // --- neverSwitch/isNeverSwitch: bedrock-class blocks and decorated pots -------------------

    @Test
    void isNeverSwitch_trueForUnbreakableBlocksAndDecoratedPot() {
        assertTrue(plugin.toolHandler.isNeverSwitch(Material.BEDROCK));
        assertTrue(plugin.toolHandler.isNeverSwitch(Material.BARRIER));
        assertTrue(plugin.toolHandler.isNeverSwitch(Material.MOVING_PISTON));
        assertTrue(plugin.toolHandler.isNeverSwitch(Material.REINFORCED_DEEPSLATE));
        assertTrue(plugin.toolHandler.isNeverSwitch(Material.DECORATED_POT));
    }

    @Test
    void isNeverSwitch_falseForOrdinaryAndAnyToolBlocks() {
        assertFalse(plugin.toolHandler.isNeverSwitch(Material.STONE));
        assertFalse(plugin.toolHandler.isNeverSwitch(Material.GLASS));
        assertFalse(plugin.toolHandler.isNeverSwitch(Material.SEA_LANTERN));
    }

    // --- shouldKeepHeldItem / getBareHandSlot: the bare-hand fallback --------------------------

    @Test
    void shouldKeepHeldItem_trueWhenCurrentItemIsAlreadyNotATool() {
        assertTrue(plugin.toolHandler.shouldKeepHeldItem(new ItemStack(Material.DIRT), Material.GLASS));
    }

    @Test
    void shouldKeepHeldItem_trueForInstaBreakBlockWithoutHoeInHand() {
        assertTrue(plugin.toolHandler.shouldKeepHeldItem(new ItemStack(Material.IRON_PICKAXE), Material.WHEAT));
    }

    @Test
    void shouldKeepHeldItem_falseForInstaBreakBlockWithHoeInHand() {
        assertFalse(plugin.toolHandler.shouldKeepHeldItem(new ItemStack(Material.IRON_HOE), Material.WHEAT));
    }

    @Test
    void shouldKeepHeldItem_falseWhenHoldingToolAtNonInstaBreakBlock() {
        assertFalse(plugin.toolHandler.shouldKeepHeldItem(new ItemStack(Material.IRON_PICKAXE), Material.GLASS));
    }

    @Test
    void getBareHandSlot_prefersEmptyHotbarSlotOverNonDamageableItem() {
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        inv.setItem(0, new ItemStack(Material.DIRT)); // a non-damageable candidate, but not the best one
        // slot 1 is left empty on purpose
        ItemStack[] items = plugin.toolHandler.inventoryToArray(player, true);

        assertEquals(1, plugin.toolHandler.getBareHandSlot(inv, items));
    }

    @Test
    void getBareHandSlot_fallsBackToNonDamageableItemWhenHotbarIsFull() {
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, i == 3 ? new ItemStack(Material.DIRT) : new ItemStack(Material.IRON_PICKAXE));
        }
        ItemStack[] items = plugin.toolHandler.inventoryToArray(player, true);

        assertEquals(3, plugin.toolHandler.getBareHandSlot(inv, items));
    }

    @Test
    void getBareHandSlot_returnsMinusOneWhenEverythingIsDamageable() {
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, new ItemStack(Material.IRON_PICKAXE));
        }
        ItemStack[] items = plugin.toolHandler.inventoryToArray(player, true);

        assertEquals(-1, plugin.toolHandler.getBareHandSlot(inv, items));
    }

    // --- dropMaterials: the getDrops() comparison silkChangesDrops relies on ------------------

    @Test
    void dropMaterials_equalSetsRegardlessOfStackSize() {
        assertEquals(
                BestToolsHandler.dropMaterials(List.of(new ItemStack(Material.GLASS, 1))),
                BestToolsHandler.dropMaterials(List.of(new ItemStack(Material.GLASS, 64))));
    }

    @Test
    void dropMaterials_differsWhenMaterialsDiffer() {
        assertNotEquals(
                BestToolsHandler.dropMaterials(List.of(new ItemStack(Material.GLASS))),
                BestToolsHandler.dropMaterials(List.of()));
    }

    @Test
    void moveToolToSlot_swapsIntoInventorySlot() {
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
        ItemStack occupant = new ItemStack(Material.DIRT);
        inv.setItem(20, pickaxe);
        inv.setItem(0, occupant);

        plugin.toolHandler.moveToolToSlot(20, 0, inv);

        assertEquals(0, inv.getHeldItemSlot());
        assertEquals(Material.IRON_PICKAXE, inv.getItem(0).getType());
        assertEquals(Material.DIRT, inv.getItem(20).getType());
    }

    @Test
    void moveToolToSlot_alreadyInHotbarJustSwitchesHeldSlot() {
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
        inv.setItem(3, pickaxe);

        plugin.toolHandler.moveToolToSlot(3, 0, inv);

        assertEquals(3, inv.getHeldItemSlot());
        assertEquals(Material.IRON_PICKAXE, inv.getItem(3).getType());
    }

    @Test
    void freeSlot_movesDamageableOccupantAndFreesSlot() {
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        inv.setItem(0, new ItemStack(Material.IRON_PICKAXE));
        inv.setHeldItemSlot(0);
        ItemStack occupant = new ItemStack(Material.DIAMOND_PICKAXE);
        inv.setItem(5, occupant);

        plugin.toolHandler.freeSlot(5, inv);

        assertEquals(5, inv.getHeldItemSlot());
        assertNull(inv.getItem(5));
    }

    @Test
    void getPositionInInventory_findsSlot() {
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);
        inv.setItem(5, pickaxe);

        assertEquals(5, plugin.toolHandler.getPositionInInventory(pickaxe, inv));
    }

    @Test
    void getPositionInInventory_returnsMinusOneWhenNotFound() {
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        ItemStack pickaxe = new ItemStack(Material.IRON_PICKAXE);

        assertEquals(-1, plugin.toolHandler.getPositionInInventory(pickaxe, inv));
    }

    @Test
    void getEmptyHotbarSlot_findsFirstEmptySlot() {
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (i != 4) inv.setItem(i, new ItemStack(Material.DIRT));
        }

        assertEquals(4, BestToolsHandler.getEmptyHotbarSlot(inv));
    }

    @Test
    void getEmptyHotbarSlot_returnsMinusOneWhenFull() {
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, new ItemStack(Material.DIRT));
        }

        assertEquals(-1, BestToolsHandler.getEmptyHotbarSlot(inv));
    }

    @Test
    void invalidGlobalBlockBlacklistEntryIsSkippedNotThrown() throws java.io.IOException {
        java.io.File configFile = new java.io.File(plugin.getDataFolder(), "config.yml");
        String edited = java.nio.file.Files.readString(configFile.toPath())
                .replace("global_block_blacklist: []", "global_block_blacklist: [NOT_A_REAL_MATERIAL, STONE]");
        java.nio.file.Files.writeString(configFile.toPath(), edited);

        assertDoesNotThrow(() -> plugin.configManager.reloadAll());

        assertTrue(plugin.toolHandler.isGloballyBlacklisted(Material.STONE));
        assertFalse(plugin.toolHandler.isGloballyBlacklisted(Material.DIRT));
    }

    @Test
    void swordOnMobsAndFavoriteSlotPersistAcrossPdcReload() {
        PlayerMock player = newPlayer();

        new PlayerSetting(player, true, true, true, 3, true);
        PlayerSetting reloaded = new PlayerSetting(player, false, false, false, 0, false);

        assertTrue(reloaded.isSwordOnMobs());
        assertEquals(3, reloaded.getFavoriteSlot());
    }

    private ItemStack enchanted(Material mat, String enchantKey, int level) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.addEnchant(EnchantmentUtils.getEnchantment(enchantKey), level, true);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Minimal live-mining-data double for {@link BestToolsHandler#getBestItemStackFromArray}.
     * MockBukkit's own {@code BlockDataMock} throws {@code UnimplementedOperationException} for
     * getDestroySpeed/isPreferredTool/requiresCorrectToolForDrops, so this fake overrides just
     * those three real Paper API methods with canned per-Material answers. Extending BlockDataMock
     * rather than implementing the (large) BlockData interface from scratch keeps everything else
     * inherited — those extra methods are never called by the code under test.
     */
    private static final class FakeBlockData extends BlockDataMock {
        private final boolean requiresCorrect;
        private final Map<Material, Float> speeds;
        private final Set<Material> preferred;
        final List<ItemStack> preferredToolChecks = new ArrayList<>();

        FakeBlockData(Material material, boolean requiresCorrect, Map<Material, Float> speeds, Set<Material> preferred) {
            super(material);
            this.requiresCorrect = requiresCorrect;
            this.speeds = speeds;
            this.preferred = preferred;
        }

        @Override
        public boolean requiresCorrectToolForDrops() {
            return requiresCorrect;
        }

        @Override
        public float getDestroySpeed(ItemStack itemStack, boolean considerEnchants) {
            return speeds.getOrDefault(itemStack.getType(), 1.0f);
        }

        @Override
        public boolean isPreferredTool(ItemStack tool) {
            preferredToolChecks.add(tool);
            return preferred.contains(tool.getType());
        }
    }

}
