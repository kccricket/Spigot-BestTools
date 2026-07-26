package net.kccricket.bestesttool;

import net.kccricket.bestesttool.BestToolsHandler.Tool;
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
            "NETHER_GOLD_ORE", "GLASS", "TINTED_GLASS", "GLASS_PANE"})
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

    // --- getBestItemStackFromArray: the live-speed ranking core ------------------------------

    @Test
    void getBestItemStackFromArray_picksFasterOfTwoCandidates() {
        ItemStack wooden = new ItemStack(Material.WOODEN_PICKAXE);
        ItemStack stone = new ItemStack(Material.STONE_PICKAXE);
        ItemStack[] items = {wooden, stone};

        FakeBlockData data = new FakeBlockData(Material.STONE, false,
                Map.of(Material.WOODEN_PICKAXE, 2f, Material.STONE_PICKAXE, 4f), Set.of());

        ItemStack best = plugin.toolHandler.getBestItemStackFromArray(data, items, false, Material.STONE);

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

        ItemStack best = plugin.toolHandler.getBestItemStackFromArray(obsidian, items, false, Material.OBSIDIAN);

        assertEquals(Material.DIAMOND_PICKAXE, best.getType());
    }

    @Test
    void getBestItemStackFromArray_fallsBackToFastestWhenNothingIsPreferred() {
        ItemStack wooden = new ItemStack(Material.WOODEN_PICKAXE);
        ItemStack iron = new ItemStack(Material.IRON_PICKAXE);
        ItemStack[] items = {wooden, iron};

        FakeBlockData diamondOre = new FakeBlockData(Material.DIAMOND_ORE, true,
                Map.of(Material.WOODEN_PICKAXE, 2f, Material.IRON_PICKAXE, 6f), Set.of());

        ItemStack best = plugin.toolHandler.getBestItemStackFromArray(diamondOre, items, false, Material.DIAMOND_ORE);

        assertEquals(Material.IRON_PICKAXE, best.getType());
    }

    @Test
    void getBestItemStackFromArray_skipsPreferredToolCheckWhenDropsDontRequireIt() {
        ItemStack shovel = new ItemStack(Material.IRON_SHOVEL);
        ItemStack[] items = {shovel};

        FakeBlockData dirt = new FakeBlockData(Material.DIRT, false, Map.of(Material.IRON_SHOVEL, 6f), Set.of());

        ItemStack best = plugin.toolHandler.getBestItemStackFromArray(dirt, items, false, Material.DIRT);

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

        ItemStack best = plugin.toolHandler.getBestItemStackFromArray(data, items, false, Material.STONE);

        assertEquals(first, best);
    }

    @Test
    void getBestItemStackFromArray_returnsNullWhenNothingBeatsBareHand() {
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemStack[] items = {sword};

        FakeBlockData data = new FakeBlockData(Material.STONE, false, Map.of(), Set.of());

        assertNull(plugin.toolHandler.getBestItemStackFromArray(data, items, false, Material.STONE));
    }

    @Test
    void getBestItemStackFromArray_silkTouchPickaxePreferredWhenPresent() {
        ItemStack plainPick = new ItemStack(Material.IRON_PICKAXE);
        ItemStack silkPick = enchanted(Material.IRON_PICKAXE, "silk_touch", 1);
        ItemStack[] items = {plainPick, silkPick};

        FakeBlockData glowstone = new FakeBlockData(Material.GLOWSTONE, false, Map.of(Material.IRON_PICKAXE, 6f), Set.of());

        ItemStack best = plugin.toolHandler.getBestItemStackFromArray(glowstone, items, true, Material.GLOWSTONE);

        assertEquals(silkPick, best);
    }

    @Test
    void getBestItemStackFromArray_fallsBackToPlainPickaxeWithoutSilkTouch() {
        ItemStack plainPick = new ItemStack(Material.IRON_PICKAXE);
        ItemStack[] items = {plainPick};

        FakeBlockData glowstone = new FakeBlockData(Material.GLOWSTONE, false, Map.of(Material.IRON_PICKAXE, 6f), Set.of());

        ItemStack best = plugin.toolHandler.getBestItemStackFromArray(glowstone, items, true, Material.GLOWSTONE);

        assertEquals(plainPick, best);
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

    // --- getNonToolItemFromArray: unaffected by the live-speed switch ----------------------------

    @Test
    void getNonToolItemFromArray_keepsCurrentItemForInstaBreakWithoutHoe() {
        ItemStack current = new ItemStack(Material.IRON_PICKAXE);
        ItemStack[] items = {new ItemStack(Material.DIRT)};

        ItemStack result = plugin.toolHandler.getNonToolItemFromArray(items, current, Material.WHEAT);

        assertEquals(current, result);
    }

    @Test
    void getNonToolItemFromArray_looksForAlternativeWithHoeInHand() {
        // MockBukkit's ItemMeta mock reports every material as Damageable, so an empty (null)
        // slot is the only way to exercise "non-damageable item found" fidelity here; what this
        // proves is that a hoe in hand does NOT short-circuit to the current item like the
        // instaBreak-without-hoe case does.
        ItemStack current = new ItemStack(Material.IRON_HOE);
        ItemStack[] items = {new ItemStack(Material.DIAMOND_PICKAXE), null};

        ItemStack result = plugin.toolHandler.getNonToolItemFromArray(items, current, Material.WHEAT);

        assertNotEquals(current, result);
        assertNull(result);
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
    void invalidGlobalBlockBlacklistEntryIsSkippedNotThrown() {
        plugin.getConfig().set("global_block_blacklist", List.of("NOT_A_REAL_MATERIAL", "STONE"));

        BestToolsHandler handler = assertDoesNotThrow(() -> new BestToolsHandler(plugin));

        assertTrue(handler.globalBlacklist.contains(Material.STONE));
        assertFalse(handler.globalBlacklist.contains(Material.DIRT));
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
