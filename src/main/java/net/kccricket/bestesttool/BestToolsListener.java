package net.kccricket.bestesttool;

import net.kccricket.bestesttool.events.BestToolsNotifyEvent;
import net.kccricket.bestesttool.security.Permissions;
import net.kccricket.bestesttool.text.MessageUtil;
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

public class BestToolsListener implements Listener {

    final BestToolsHandler handler;
    final Main main;
    boolean useAxeAsWeapon;

    BestToolsListener(@NotNull Main main) {
        this.main=Objects.requireNonNull(main,"Main must not be null");
        handler=Objects.requireNonNull(main.toolHandler,"ToolHandler must not be null");
        useAxeAsWeapon = main.configManager.main().getUseAxeAsSword();
    }


    @EventHandler
    public void onPlayerAttackEntity(EntityDamageByEntityEvent e) {
        long st= main.measurePerformance ? System.nanoTime() : 0;
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

        if (!
                (enemy instanceof Monster && playerSetting.isSwordOnMobs())
            // || (enemy instanceof Player && playerSetting.swordOnPlayers)
        ) return;

        Log.debug("Getting the best roscoe for "+enemy.getType().name());

        PlayerInventory inv = p.getInventory();
        ItemStack bestRoscoe = handler.getBestRoscoeFromInventory(enemy.getType(), p,playerSetting.isHotbarOnly(),inv.getItemInMainHand(),useAxeAsWeapon);

        if(bestRoscoe==null || bestRoscoe.equals(inv.getItemInMainHand())) {
            main.meter.add(st,false);
            //playerSetting.getBtcache().validate(enemy.getType());
            return;
        }
        switchToBestRoscoe(p, bestRoscoe,playerSetting.getFavoriteSlot());
        //playerSetting.getBtcache().validate(enemy.getType());
        main.meter.add(st,false);

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBreak(BlockBreakEvent event) {
        //System.out.println("BlockBreakEvent LISTENER");
        //System.out.println(event.getBlock());
        event.getPlayer().getScheduler().runDelayed(main, task -> {
            Bukkit.getPluginManager().callEvent(new BestToolsNotifyEvent(event.getPlayer(), event.getBlock()));
            //Bukkit.getPluginManager().callEvent(new PlayerInteractEvent(event.getPlayer(), Action.LEFT_CLICK_BLOCK, event.getPlayer().getInventory().getItemInMainHand(),event.getBlock(),BlockFace.SELF,EquipmentSlot.HAND));
        }, null, 1);
    }

    @EventHandler
    public void onNotify(BestToolsNotifyEvent event) {
        onPlayerInteractWithBlock(new PlayerInteractEvent(event.getPlayer(), Action.LEFT_CLICK_BLOCK, event.getPlayer().getInventory().getItemInMainHand(),event.getBlock(),BlockFace.SELF,EquipmentSlot.HAND));
    }

    @EventHandler
    public void onPlayerInteractWithBlock(PlayerInteractEvent event) {

        // DEBUG
        //for (RegisteredListener registeredListener : event.getHandlers().getRegisteredListeners()) {
        //    Log.debug(registeredListener.getPlugin().getName()+": "+registeredListener.getListener().getClass().getName() + " @ "+registeredListener.getPriority().name());
        //}
//        if(main.debug && event.getAction() == Action.LEFT_CLICK_BLOCK) {
//            main.getLogger().warning(event.getClickedBlock().getType().name());
//        }
        //

        long st= main.measurePerformance ? System.nanoTime() : 0;

        // Check the cache as soon as possible
        PlayerSetting playerSetting = main.getPlayerSetting(event.getPlayer());
        if(playerSetting.getBtcache().valid
                && event.getClickedBlock()!=null
                && event.getClickedBlock().getType() == playerSetting.getBtcache().lastMat) {
            main.meter.add(st,true);
            //main.wtfdebug("Cache valid!");
            return;
        }
        Player p = event.getPlayer();
        if(!Permissions.isAllowedTo(p, Permissions.PERM_USE)) {
            //main.meter.add(st);
            return;
        }
        if(!hasBestToolsEnabled(p, playerSetting)) {
            //main.meter.add(st);
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            //main.meter.add(st);
            return;
        }

        if(block.getType() == Material.AIR) {
            return;
        }

        if(main.toolHandler.globalBlacklist.contains(block.getType())) {
            return;
        }

        // Blacklist
        if(playerSetting.getBlacklist().contains(block.getType()))
            return;

        if(main.toolHandler.isNeverSwitch(block.getType())) {
            // No tool can break/drop this (bedrock, reinforced deepslate, ...), or the held item
            // is the player's own choice to make (decorated pots) — leave the hand alone.
            playerSetting.getBtcache().validate(block.getType());
            main.meter.add(st,false);
            return;
        }

       //main.wtfdebug("Cache invalid, doing onPlayerInteractWithBlock");
        if(!PlayerUtils.isAllowedGamemode(p,main.configManager.main().getAllowInAdventureMode())) {
            return;
        }
        PlayerInventory inv = p.getInventory();

        if(main.configManager.main().getDontSwitchDuringBattle() && handler.isWeapon(inv.getItemInMainHand())) {
            Log.debug("Return: It's a gun^^");
            return;
        }

        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getHand() != EquipmentSlot.HAND) return;

        ItemStack bestTool = handler.getBestToolFromInventory(block, p,playerSetting.isHotbarOnly());

        if(bestTool!=null) {
            if(!bestTool.equals(inv.getItemInMainHand())) {
                switchToBestTool(p, bestTool);
            }
        } else {
            switchToBareHand(p, playerSetting, block.getType());
        }
        playerSetting.getBtcache().validate(block.getType());
        main.meter.add(st,false);
    }

    private int getFavoriteSlot(Player player) {
        if(main.configManager.main().getDefaultFavoriteSlot()==-1) {
            return player.getInventory().getHeldItemSlot();
        } else {
            return main.configManager.main().getDefaultFavoriteSlot();
        }
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
                MessageUtil.send(p, "besttoolsUsage");
                playerSetting.setHasSeenBestToolsMessage(true);
            }
            return false;
        }
        return true;
    }

}
