package net.kccricket.bestesttool;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.*;
import org.jetbrains.annotations.NotNull;

public class BestToolsCacheListener implements @NotNull Listener {

    final Main main;

    BestToolsCacheListener(Main main) {
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

    @EventHandler
    public void onPlayerPickupTool(EntityPickupItemEvent e) {
        if(!(e.getEntity() instanceof Player)) return;
        if(main.toolHandler.isTool(e.getItem().getItemStack())) {
            cacheInvalidated((Player) e.getEntity(),"PickupItem");
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
