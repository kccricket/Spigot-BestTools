package net.kccricket.bestesttool;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PermissionSiteTest extends BestToolsTestBase {

    @ParameterizedTest
    @EnumSource(Grant.class)
    void bestToolsListener_interactSite_gatesOnUsePermission(Grant grantType) {
        PlayerMock player = newPlayer();
        grant(player, "use", grantType);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.LEFT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), null, BlockFace.SELF, EquipmentSlot.HAND);
        plugin.bestToolsListener.onPlayerInteractWithBlock(event);

        // bestesttool.use defaults to true, so only an explicit DENIED actually withholds it.
        boolean granted = grantType != Grant.DENIED;
        assertEquals(granted, plugin.getPlayerSetting(player).isHasSeenBestToolsMessage());
    }

    @ParameterizedTest
    @EnumSource(Grant.class)
    void bestToolsListener_attackSite_gatesOnUsePermission(Grant grantType) {
        PlayerMock player = newPlayer();
        grant(player, "use", grantType);
        plugin.getPlayerSetting(player).toggleBestToolsEnabled();

        PlayerInventory inv = player.getInventory();
        inv.setItem(0, new ItemStack(Material.WOODEN_SWORD));
        inv.setItem(1, new ItemStack(Material.IRON_SWORD));
        inv.setHeldItemSlot(0);

        Zombie zombie = player.getWorld().spawn(player.getLocation(), Zombie.class);
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(player, zombie,
                EntityDamageEvent.DamageCause.ENTITY_ATTACK, 1.0);
        plugin.bestToolsListener.onPlayerAttackEntity(event);

        // bestesttool.use defaults to true, so only an explicit DENIED actually withholds it.
        boolean granted = grantType != Grant.DENIED;
        assertEquals(granted ? 1 : 0, inv.getHeldItemSlot());
    }

    @ParameterizedTest
    @EnumSource(Grant.class)
    void refillListener_gatesOnRefillPermission(Grant grantType) {
        PlayerMock player = newPlayer();
        grant(player, "refill", grantType);

        ItemStack mainHand = new ItemStack(Material.DIRT, 1);
        player.getInventory().setItemInMainHand(mainHand);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
                mainHand, null, BlockFace.SELF, EquipmentSlot.HAND);
        plugin.refillListener.onInteract(event);

        // bestesttool.refill defaults to true, so only an explicit DENIED actually withholds it.
        boolean granted = grantType != Grant.DENIED;
        assertEquals(granted, plugin.getPlayerSetting(player).isHasSeenRefillMessage());
    }

    @ParameterizedTest
    @EnumSource(Grant.class)
    void performanceMeter_broadcastGatesOnDebugPermission(Grant grantType) {
        PlayerMock player = newPlayer();
        grant(player, "debug", grantType);
        plugin.measurePerformance = true;

        for (int i = 0; i < 50; i++) {
            plugin.meter.add(System.nanoTime(), false);
        }

        // bestesttool.debug defaults to op, so only an explicit grant is allowed here.
        boolean granted = grantType == Grant.NEW;
        if (granted) {
            assertNotNull(player.nextComponentMessage());
        } else {
            assertNull(player.nextComponentMessage());
        }
    }
}
