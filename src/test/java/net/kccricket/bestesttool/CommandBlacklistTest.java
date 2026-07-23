package net.kccricket.bestesttool;

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

        player.performCommand("besttools bl add");

        assertTrue(plugin.getPlayerSetting(player).getBlacklist().contains(Material.STONE));
    }

    @Test
    void addExplicitMaterial() {
        PlayerMock player = opPlayer();

        player.performCommand("besttools bl add DIRT");

        assertTrue(plugin.getPlayerSetting(player).getBlacklist().contains(Material.DIRT));
    }

    @Test
    void removeExplicitMaterial() {
        PlayerMock player = opPlayer();
        plugin.getPlayerSetting(player).getBlacklist().add(Material.DIRT);

        player.performCommand("besttools bl remove DIRT");

        assertFalse(plugin.getPlayerSetting(player).getBlacklist().contains(Material.DIRT));
    }

    @Test
    void resetClearsBlacklist() {
        PlayerMock player = opPlayer();
        plugin.getPlayerSetting(player).getBlacklist().add(Material.DIRT);

        player.performCommand("besttools bl reset");

        assertFalse(plugin.getPlayerSetting(player).getBlacklist().contains(Material.DIRT));
    }

    @Test
    void showDoesNotThrow() {
        PlayerMock player = opPlayer();

        assertDoesNotThrow(() -> player.performCommand("besttools bl show"));
    }
}
