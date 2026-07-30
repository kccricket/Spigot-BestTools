package net.kccricket.bestesttool.listeners;

import net.kccricket.bestesttool.BestestToolPlugin;
import net.kccricket.bestesttool.events.BestToolsNotifyEvent;
import net.kccricket.bestesttool.security.Permissions;
import net.kccricket.kcmclib.logging.Log;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import net.kccricket.bestesttool.model.PlayerSetting;
import net.kccricket.bestesttool.tool.BestToolsHandler;
import net.kccricket.bestesttool.tool.BestToolsSelector;
import net.kccricket.bestesttool.util.PlayerUtils;

public class BestToolsListener implements Listener {

    final BestToolsHandler handler;
    final BestestToolPlugin main;

    public BestToolsListener(@NotNull BestestToolPlugin main) {
        this.main=Objects.requireNonNull(main,"BestestToolPlugin must not be null");
        handler=Objects.requireNonNull(main.toolHandler,"ToolHandler must not be null");
    }


    @EventHandler
    public void onPlayerAttackEntity(EntityDamageByEntityEvent e) {
        Log.debug("EntityDamageByEntity 1");

        if (!(e.getDamager() instanceof Player)) return;
        Log.debug("EntityDamageByEntity 2");
        Player p = (Player) e.getDamager();
        if(!Permissions.isAllowedTo(p, Permissions.PERM_USE)) return;
        Log.debug("EntityDamageByEntity 3");
        PlayerSetting playerSetting = main.getPlayerSetting(p);
        if(!playerSetting.isBestToolsEnabled()) return;
        Log.debug("EntityDamageByEntity 4");
        Entity enemy = e.getEntity();

        if(!PlayerUtils.isAllowedGamemode(p,main.configManager.main().getAllowInAdventureMode())) {
            return;
        }

        if (!(enemy instanceof Monster && playerSetting.isSwordOnMobs())) return;

        Log.debug("Getting the best roscoe for "+enemy.getType().name());

        PlayerInventory inv = p.getInventory();
        ItemStack bestRoscoe = handler.getBestRoscoeFromInventory(enemy.getType(), p,playerSetting.isHotbarOnly(),inv.getItemInMainHand(),main.configManager.main().getUseAxeAsSword());

        if(bestRoscoe==null || bestRoscoe.equals(inv.getItemInMainHand())) {
            return;
        }
        switchToBestRoscoe(p, bestRoscoe,playerSetting.getFavoriteSlot());

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        event.getPlayer().getScheduler().runDelayed(main, task -> {
            Bukkit.getPluginManager().callEvent(new BestToolsNotifyEvent(event.getPlayer(), event.getBlock()));
        }, null, 1);
    }

    @EventHandler
    public void onNotify(BestToolsNotifyEvent event) {
        onPlayerInteractWithBlock(new PlayerInteractEvent(event.getPlayer(), Action.LEFT_CLICK_BLOCK, event.getPlayer().getInventory().getItemInMainHand(),event.getBlock(),BlockFace.SELF,EquipmentSlot.HAND));
    }

    @EventHandler
    public void onPlayerInteractWithBlock(PlayerInteractEvent event) {

        // Check the cache as soon as possible
        PlayerSetting playerSetting = main.getPlayerSetting(event.getPlayer());
        if(playerSetting.getBtcache().valid
                && event.getClickedBlock()!=null
                && event.getClickedBlock().getType() == playerSetting.getBtcache().lastMat) {
            return;
        }
        Player p = event.getPlayer();
        if(!Permissions.isAllowedTo(p, Permissions.PERM_USE)) {
            return;
        }
        if(!hasBestToolsEnabled(p, playerSetting)) {
            return;
        }
        Block block = event.getClickedBlock();

        BestToolsSelector.ToolDecision decision = BestToolsSelector.decide(
                main, handler, p, playerSetting, block, event.getAction(), event.getHand());

        switch (decision.outcome()) {
            case SWITCH -> {
                PlayerInventory inv = p.getInventory();
                if(!decision.tool().equals(inv.getItemInMainHand())) {
                    switchToBestTool(p, decision.tool());
                }
                playerSetting.getBtcache().validate(block.getType());
            }
            case BARE_HAND -> {
                switchToBareHand(p, playerSetting, block.getType());
                playerSetting.getBtcache().validate(block.getType());
            }
            case NO_CHANGE -> playerSetting.getBtcache().validate(block.getType());
            case NOT_APPLICABLE -> { /* nothing to do */ }
        }
    }

    private int getFavoriteSlot(Player player) {
        return main.getPlayerSetting(player).getFavoriteSlot();
    }

    private void switchToBestTool(Player p, @NotNull ItemStack bestTool) {

        PlayerInventory inv = p.getInventory();
        int positionInInventory = handler.getPositionInInventory(bestTool,inv) ;
        if(positionInInventory != -1) {
            handler.moveToolToSlot(positionInInventory,getFavoriteSlot(p),inv);
            Log.debug("Found tool");
        } else {
            handler.freeSlot(getFavoriteSlot(p),inv);
            Log.debug("Use no tool");
        }

    }

    /**
     * Nothing in the inventory beat a bare hand for {@code target} — switch to an actual bare
     * hand (an empty hotbar slot) if one exists and it's worth the swap, otherwise free up the
     * favorite slot as today.
     */
    private void switchToBareHand(Player p, PlayerSetting playerSetting, Material target) {

        PlayerInventory inv = p.getInventory();
        ItemStack currentItem = inv.getItemInMainHand();

        if(handler.shouldKeepHeldItem(currentItem,target)) return;

        ItemStack[] items = handler.inventoryToArray(p,playerSetting.isHotbarOnly());
        int bareHandSlot = handler.getBareHandSlot(inv,items);
        if(bareHandSlot != -1) {
            handler.moveToolToSlot(bareHandSlot,getFavoriteSlot(p),inv);
            Log.debug("Found bare-hand stand-in");
        } else {
            handler.freeSlot(getFavoriteSlot(p),inv);
            Log.debug("Could not find any bare-hand stand-in");
        }
    }

    private void switchToBestRoscoe(Player p, @NotNull ItemStack bestRoscoe, int favoriteSlot) {

        PlayerInventory inv = p.getInventory();
        int positionInInventory = handler.getPositionInInventory(bestRoscoe,inv) ;
        if(positionInInventory != -1) {
            handler.moveToolToSlot(positionInInventory,favoriteSlot,inv);
            Log.debug("Found tool");
        } else {
            handler.freeSlot(favoriteSlot,inv);
            Log.debug("Use no tool");
        }

    }

    private boolean hasBestToolsEnabled(Player p, PlayerSetting playerSetting) {
        if(!playerSetting.isBestToolsEnabled()) {
            if (!playerSetting.isHasSeenBestToolsMessage()) {
                main.messages().to(p).status().send("besttoolsUsage");
                playerSetting.setHasSeenBestToolsMessage(true);
            }
            return false;
        }
        return true;
    }

}
