package net.kccricket.bestesttool.listeners;

import net.kccricket.bestesttool.BestToolsTestBase;
import org.bukkit.Material;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.LivingEntityMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * End-to-end coverage for {@code use_axe_as_sword}, driven through the real attack event —
 * {@link net.kccricket.bestesttool.tool.ToolSelectionTest}'s {@code getBestRoscoeFromInventory_*}
 * tests already pin the pure ranking; this proves {@link BestToolsListener#onPlayerAttackEntity}
 * actually reads {@link net.kccricket.bestesttool.model.PlayerSetting#isUseAxeAsSword()}, not a
 * stale config getter. Mirrors {@link net.kccricket.bestesttool.PermissionSiteTest}'s
 * {@code LivingEntityMock#simulateDamage(1.0, player)} pattern — see its comment for why the event
 * is built that way.
 */
class CombatSwitchTest extends BestToolsTestBase {

    @Test
    void attackSite_switchesToAxeWhenUseAxeAsSwordEnabled() {
        PlayerMock player = newPlayer();
        plugin.getPlayerSetting(player).toggleBestToolsEnabled();
        plugin.getPlayerSetting(player).setUseAxeAsSword(true);

        PlayerInventory inv = player.getInventory();
        inv.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
        inv.setItem(1, new ItemStack(Material.NETHERITE_AXE));
        inv.setHeldItemSlot(0);

        Zombie zombie = player.getWorld().spawn(player.getLocation(), Zombie.class);
        ((LivingEntityMock) zombie).simulateDamage(1.0, player);

        assertEquals(1, inv.getHeldItemSlot(), "the higher-damage axe must be switched to");
    }

    @Test
    void attackSite_leavesAxeAloneByDefault() {
        // use_axe_as_sword defaults to false.
        PlayerMock player = newPlayer();
        plugin.getPlayerSetting(player).toggleBestToolsEnabled();

        PlayerInventory inv = player.getInventory();
        inv.setItem(0, new ItemStack(Material.DIAMOND_SWORD));
        inv.setItem(1, new ItemStack(Material.NETHERITE_AXE));
        inv.setHeldItemSlot(0);

        Zombie zombie = player.getWorld().spawn(player.getLocation(), Zombie.class);
        ((LivingEntityMock) zombie).simulateDamage(1.0, player);

        assertEquals(0, inv.getHeldItemSlot(), "the sword is already the best roscoe when axes aren't considered");
    }
}
