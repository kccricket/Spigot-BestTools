package net.kccricket.bestesttool.listeners;

import net.kccricket.bestesttool.BestToolsTestBase;
import net.kccricket.bestesttool.tool.BestToolsCache;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.ItemMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins every event that must invalidate {@link BestToolsCache} — both the events
 * {@link BestToolsCacheListener} already covered (previously untested) and the gaps closed
 * alongside this test: hand-swap, game-mode change, respawn, and a widened pickup gate that no
 * longer misses swords or an emptied hotbar slot.
 */
class BestToolsCacheListenerTest extends BestToolsTestBase {

    /** Seeds {@code player}'s cache as valid (as if the last decision was for STONE), and returns it for assertions. */
    private BestToolsCache seedValidCache(PlayerMock player) {
        BestToolsCache cache = plugin.getPlayerSetting(player).getBtcache();
        cache.validate(Material.STONE);
        return cache;
    }

    private ItemMock groundItem(Material material) {
        return new ItemMock(server, UUID.randomUUID(), new ItemStack(material));
    }

    @Test
    void dropItem_invalidates() {
        PlayerMock player = newPlayer();
        BestToolsCache cache = seedValidCache(player);

        plugin.bestToolsCacheListener.onPlayerDropItem(new PlayerDropItemEvent(player, groundItem(Material.DIRT)));

        assertFalse(cache.valid);
    }

    @Test
    void itemHeldChange_invalidates() {
        PlayerMock player = newPlayer();
        BestToolsCache cache = seedValidCache(player);

        plugin.bestToolsCacheListener.onPlayerSwitchSlot(new PlayerItemHeldEvent(player, 0, 1));

        assertFalse(cache.valid);
    }

    @Test
    void itemBreak_invalidates() {
        PlayerMock player = newPlayer();
        BestToolsCache cache = seedValidCache(player);

        plugin.bestToolsCacheListener.onItemBreak(
                new PlayerItemBreakEvent(player, new ItemStack(Material.WOODEN_PICKAXE)));

        assertFalse(cache.valid);
    }

    /**
     * The default view returned by a fresh {@code PlayerMock.getOpenInventory()} is a
     * {@code SimpleInventoryViewMock} whose {@code convertSlot} is unimplemented (throws) — actually
     * opening the player's own inventory swaps in a {@code PlayerInventoryViewMock}, which fully
     * implements it.
     */
    private InventoryView openOwnInventoryView(PlayerMock player) {
        return player.openInventory(player.getInventory());
    }

    @Test
    void inventoryClick_invalidates() {
        PlayerMock player = newPlayer();
        InventoryView view = openOwnInventoryView(player);
        BestToolsCache cache = seedValidCache(player);

        InventoryClickEvent event = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, 0, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        plugin.bestToolsCacheListener.onInventoryClick(event);

        assertFalse(cache.valid);
    }

    @Test
    void inventoryDrag_invalidates() {
        PlayerMock player = newPlayer();
        InventoryView view = openOwnInventoryView(player);
        BestToolsCache cache = seedValidCache(player);
        Map<Integer, ItemStack> slots = Map.of(0, new ItemStack(Material.DIRT));

        InventoryDragEvent event = new InventoryDragEvent(view, null, new ItemStack(Material.DIRT), false, slots);
        plugin.bestToolsCacheListener.onInventoryDrag(event);

        assertFalse(cache.valid);
    }

    @Test
    void swapHandItems_invalidates() {
        PlayerMock player = newPlayer();
        BestToolsCache cache = seedValidCache(player);

        PlayerSwapHandItemsEvent event =
                new PlayerSwapHandItemsEvent(player, new ItemStack(Material.IRON_PICKAXE), ItemStack.empty());
        plugin.bestToolsCacheListener.onSwapHandItems(event);

        assertFalse(cache.valid);
    }

    @Test
    void gameModeChange_invalidates() {
        PlayerMock player = newPlayer();
        BestToolsCache cache = seedValidCache(player);

        player.setGameMode(GameMode.CREATIVE);

        assertFalse(cache.valid);
    }

    @Test
    void gameModeChange_noopSetDoesNotReInvalidate() {
        // Documents the no-cost claim in BestToolsCacheListener's javadoc: setting the SAME game
        // mode again must not re-fire PlayerGameModeChangeEvent (PlayerMock.setGameMode early-
        // returns on a no-op, mirroring Paper's own ServerPlayerGameMode short-circuit), so
        // re-validating the cache right after a redundant set is unaffected.
        PlayerMock player = newPlayer();
        player.setGameMode(player.getGameMode());
        BestToolsCache cache = seedValidCache(player);

        player.setGameMode(player.getGameMode());

        assertTrue(cache.valid);
    }

    @Test
    void respawn_invalidates() {
        PlayerMock player = newPlayer();
        BestToolsCache cache = seedValidCache(player);

        player.respawn();

        assertFalse(cache.valid);
    }

    @Test
    void pickupSword_invalidatesEvenThoughNotATool() {
        // isTool() alone would miss this: allTools is pickaxes/axes/hoes/shovels/shears, and
        // swords aren't in it, even though swords are live selection candidates.
        PlayerMock player = newPlayer();
        BestToolsCache cache = seedValidCache(player);

        plugin.bestToolsCacheListener.onPlayerPickupTool(
                new EntityPickupItemEvent(player, groundItem(Material.IRON_SWORD), 0));

        assertFalse(cache.valid);
    }

    @Test
    void pickupNonTool_withFullHotbar_doesNotInvalidate() {
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            inv.setItem(i, new ItemStack(Material.STONE));
        }
        BestToolsCache cache = seedValidCache(player);

        plugin.bestToolsCacheListener.onPlayerPickupTool(
                new EntityPickupItemEvent(player, groundItem(Material.DIRT), 0));

        assertTrue(cache.valid, "a full hotbar means this pickup can't change getBareHandSlot's answer");
    }

    @Test
    void pickupNonTool_withEmptyHotbarSlot_invalidates() {
        // A fresh player's hotbar is empty by default.
        PlayerMock player = newPlayer();
        BestToolsCache cache = seedValidCache(player);

        plugin.bestToolsCacheListener.onPlayerPickupTool(
                new EntityPickupItemEvent(player, groundItem(Material.DIRT), 0));

        assertFalse(cache.valid,
                "an empty hotbar slot could be consumed by this pickup, changing getBareHandSlot's answer");
    }

    @Test
    void adminReload_invalidatesOnlinePlayersCache() {
        PlayerMock player = newPlayer();
        player.setOp(true);
        BestToolsCache cache = seedValidCache(player);

        player.performCommand("bestesttool admin reload");

        assertFalse(cache.valid);
    }
}
