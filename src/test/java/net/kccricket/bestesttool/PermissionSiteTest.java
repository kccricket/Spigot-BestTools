package net.kccricket.bestesttool;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.entity.LivingEntityMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        // Every EntityDamageByEntityEvent constructor other than the deprecated-for-removal
        // (Entity,Entity,DamageCause,double) one requires a Map<DamageModifier,...>, and
        // DamageModifier itself is deprecated — there is no way to build this event ourselves
        // without touching a deprecated symbol. MockBukkit's simulateDamage(...) builds it
        // internally instead (so the deprecated construction lives in its already-compiled code,
        // not ours) and its callEvent() call delivers it to the real registered listener, so no
        // manual dispatch to bestToolsListener is needed here.
        ((LivingEntityMock) zombie).simulateDamage(1.0, player);

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
}
