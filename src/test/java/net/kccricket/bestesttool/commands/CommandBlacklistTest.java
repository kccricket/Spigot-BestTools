package net.kccricket.bestesttool.commands;

import net.kccricket.bestesttool.BestToolsTestBase;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandBlacklistTest extends BestToolsTestBase {

    private PlayerMock opPlayer() {
        PlayerMock p = newPlayer();
        p.setOp(true);
        return p;
    }

    @Test
    void addHeldItem() {
        PlayerMock player = opPlayer();
        player.getInventory().setItemInMainHand(new ItemStack(Material.STONE));

        player.performCommand("bestesttool blacklist add");

        assertTrue(plugin.getPlayerSetting(player).getBlacklist().contains(Material.STONE));
    }

    @Test
    void addExplicitMaterial() {
        PlayerMock player = opPlayer();

        player.performCommand("bestesttool blacklist add DIRT");

        assertTrue(plugin.getPlayerSetting(player).getBlacklist().contains(Material.DIRT));
    }

    @Test
    void removeExplicitMaterial() {
        PlayerMock player = opPlayer();
        plugin.getPlayerSetting(player).getBlacklist().add(Material.DIRT);

        player.performCommand("bestesttool blacklist remove DIRT");

        assertFalse(plugin.getPlayerSetting(player).getBlacklist().contains(Material.DIRT));
    }

    @Test
    void resetClearsBlacklist() {
        PlayerMock player = opPlayer();
        plugin.getPlayerSetting(player).getBlacklist().add(Material.DIRT);

        player.performCommand("bestesttool blacklist reset");

        assertFalse(plugin.getPlayerSetting(player).getBlacklist().contains(Material.DIRT));
    }

    @Test
    void showDoesNotThrow() {
        PlayerMock player = opPlayer();

        assertDoesNotThrow(() -> player.performCommand("bestesttool blacklist show"));
    }

    @Test
    void addInventoryAddsAllInventoryItems() {
        PlayerMock player = opPlayer();
        player.getInventory().setItem(9, new ItemStack(Material.DIRT));
        player.getInventory().setItem(10, new ItemStack(Material.STONE));

        player.performCommand("bestesttool blacklist add inventory");

        assertTrue(plugin.getPlayerSetting(player).getBlacklist().contains(Material.DIRT));
        assertTrue(plugin.getPlayerSetting(player).getBlacklist().contains(Material.STONE));
    }

    @Test
    void addHotbarAddsOnlyHotbarItems() {
        PlayerMock player = opPlayer();
        player.getInventory().setItem(0, new ItemStack(Material.DIRT));
        player.getInventory().setItem(9, new ItemStack(Material.STONE));

        player.performCommand("bestesttool blacklist add hotbar");

        assertTrue(plugin.getPlayerSetting(player).getBlacklist().contains(Material.DIRT));
        assertFalse(plugin.getPlayerSetting(player).getBlacklist().contains(Material.STONE));
    }

    @Test
    void blIsNoLongerAValidAlias() {
        PlayerMock player = opPlayer();

        assertDoesNotThrow(() -> player.performCommand("bestesttool bl add DIRT"));

        assertFalse(plugin.getPlayerSetting(player).getBlacklist().contains(Material.DIRT));
    }
}
