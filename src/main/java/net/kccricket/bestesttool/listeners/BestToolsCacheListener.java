package net.kccricket.bestesttool.listeners;

import net.kccricket.bestesttool.BestestToolPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

public class BestToolsCacheListener implements @NotNull Listener {

    final BestestToolPlugin main;

    public BestToolsCacheListener(BestestToolPlugin main) {
        this.main=main;
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent e) {
      cacheInvalidated(e.getPlayer(),"DropItem");
    }

    /**
     * A player manually rearranging their own inventory (e.g. dragging a tool out of the hotbar
     * into their backpack) doesn't go through drop/pickup/held-slot-change/item-break — none of
     * which fire here — so the cache would otherwise keep pointing at a decision made against
     * inventory contents that no longer exist.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        cacheInvalidated(player, "InventoryClick");
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        cacheInvalidated(player, "InventoryDrag");
    }

    @EventHandler
    public void onPlayerSwitchSlot(PlayerItemHeldEvent e) {
        cacheInvalidated(e.getPlayer(),"ItemHeldChanged");
    }

    /**
     * The F-key hand swap changes main-hand contents (which feed the battle-weapon gate,
     * {@code shouldKeepHeldItem}, and the "already holding the right tool" check) but fires no
     * {@code InventoryClickEvent} or {@code PlayerItemHeldEvent} — it's a standalone player-action
     * packet, not a container click or a hotbar slot change.
     */
    @EventHandler
    public void onSwapHandItems(PlayerSwapHandItemsEvent e) {
        cacheInvalidated(e.getPlayer(), "SwapHandItems");
    }

    /**
     * Game mode fully gates the feature (see {@code PlayerUtils.isAllowedGamemode}), so a change
     * must invalidate even though it never touches the inventory. Paper fires this before applying
     * the change and skips it entirely on a no-op set, so there's no redundant-invalidation cost.
     */
    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent e) {
        cacheInvalidated(e.getPlayer(), "GameModeChange");
    }

    /**
     * Death clears the player's entire inventory with no inventory event on that path (the clear
     * happens after {@code PlayerDeathEvent} so plugins can see the pre-death state). Respawn is
     * the first point afterward where the cleared inventory is guaranteed visible.
     */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        cacheInvalidated(e.getPlayer(), "Respawn");
    }

    /**
     * Invalidates whenever a pickup could actually change what the next selection returns: the
     * item is itself a new candidate (tool or sword — {@code isTool} alone misses swords), or the
     * hotbar still has an empty slot the pickup could consume (which changes
     * {@code getBareHandSlot}'s answer). Fires before the item is added to the inventory, so the
     * empty-slot check below reads the correct pre-pickup state. Anything else (e.g. picking up
     * dirt with a full hotbar) can't change the decision, so the cache stays warm through ordinary
     * mining.
     */
    @EventHandler
    public void onPlayerPickupTool(EntityPickupItemEvent e) {
        if(!(e.getEntity() instanceof Player player)) return;
        ItemStack stack = e.getItem().getItemStack();
        PlayerInventory inv = player.getInventory();
        if(main.toolHandler.pickupCouldAffectSelection(stack, inv)) {
            cacheInvalidated(player,"PickupItem");
        }
    }

    @EventHandler
    public void onItemBreak(PlayerItemBreakEvent e) {
        cacheInvalidated(e.getPlayer(),"ItemBreak");
    }

    void cacheInvalidated(Player p) {
        main.getPlayerSetting(p).getBtcache().invalidated();
    }
    void cacheInvalidated(Player p,String reason) {
        main.getPlayerSetting(p).getBtcache().invalidated();
    }
}
