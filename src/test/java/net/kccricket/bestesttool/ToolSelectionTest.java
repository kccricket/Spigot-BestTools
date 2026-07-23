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
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolSelectionTest extends BestToolsTestBase {

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

    @Test
    void getBestItemStackFromArray_silkTouchPickaxePreferredWhenPresent() {
        ItemStack plainPick = new ItemStack(Material.IRON_PICKAXE);
        ItemStack silkPick = enchanted(Material.IRON_PICKAXE, "silk_touch", 1);
        ItemStack[] items = {plainPick, silkPick};

        ItemStack best = plugin.toolHandler.getBestItemStackFromArray(Tool.PICKAXE, items, true, null, Material.GLOWSTONE);

        assertEquals(silkPick, best);
    }

    @Test
    void getBestItemStackFromArray_fallsBackToPlainPickaxeWithoutSilkTouch() {
        ItemStack plainPick = new ItemStack(Material.IRON_PICKAXE);
        ItemStack[] items = {plainPick};

        ItemStack best = plugin.toolHandler.getBestItemStackFromArray(Tool.PICKAXE, items, true, null, Material.GLOWSTONE);

        assertEquals(plainPick, best);
    }

    @Test
    void getBestItemStackFromArray_efficiencyOrdering() {
        ItemStack wooden = new ItemStack(Material.WOODEN_PICKAXE);
        ItemStack stone = new ItemStack(Material.STONE_PICKAXE);
        ItemStack[] items = {wooden, stone};

        ItemStack best = plugin.toolHandler.getBestItemStackFromArray(Tool.PICKAXE, items, false, null, Material.STONE);

        assertEquals(Material.STONE_PICKAXE, best.getType());
    }

    @Test
    void getBestItemStackFromArray_diamondOrePrefersIronPlusOverGoldPickaxe() {
        ItemStack wooden = new ItemStack(Material.WOODEN_PICKAXE);
        ItemStack golden = new ItemStack(Material.GOLDEN_PICKAXE);
        ItemStack iron = new ItemStack(Material.IRON_PICKAXE);
        ItemStack[] items = {wooden, golden, iron};

        ItemStack best = plugin.toolHandler.getBestItemStackFromArray(Tool.PICKAXE, items, false, null, Material.DIAMOND_ORE);

        assertEquals(Material.IRON_PICKAXE, best.getType());
    }

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
    void getBestToolFromInventory_leavesPrefersShearsOverHoeOverSword() {
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        inv.setItem(0, new ItemStack(Material.SHEARS));
        inv.setItem(1, new ItemStack(Material.IRON_HOE));
        inv.setItem(2, new ItemStack(Material.IRON_SWORD));

        ItemStack best = plugin.toolHandler.getBestToolFromInventory(Material.OAK_LEAVES, player, true, null);

        assertEquals(Material.SHEARS, best.getType());
    }

    @Test
    void getBestToolFromInventory_leavesFallsBackToHoeWithoutShears() {
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        inv.setItem(1, new ItemStack(Material.IRON_HOE));
        inv.setItem(2, new ItemStack(Material.IRON_SWORD));

        ItemStack best = plugin.toolHandler.getBestToolFromInventory(Material.OAK_LEAVES, player, true, null);

        assertEquals(Material.IRON_HOE, best.getType());
    }

    @Test
    void getBestToolFromInventory_leavesFallsBackToSwordWhenConfigured() {
        plugin.getConfig().set("consider-swords-for-leaves", true);
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        inv.setItem(2, new ItemStack(Material.IRON_SWORD));

        ItemStack best = plugin.toolHandler.getBestToolFromInventory(Material.OAK_LEAVES, player, true, null);

        assertEquals(Material.IRON_SWORD, best.getType());
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
        plugin.getConfig().set("global-block-blacklist", List.of("NOT_A_REAL_MATERIAL", "STONE"));

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

}
