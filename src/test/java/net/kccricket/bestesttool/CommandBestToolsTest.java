package net.kccricket.bestesttool;

import net.kccricket.kcmclib.logging.DebugLevel;
import net.kccricket.kcmclib.logging.Log;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandBestToolsTest extends BestToolsTestBase {

    private PlayerMock opPlayer() {
        PlayerMock p = newPlayer();
        p.setOp(true);
        return p;
    }

    @Test
    void defaultTogglesBestToolsEnabled() {
        PlayerMock player = opPlayer();
        boolean before = plugin.getPlayerSetting(player).isBestToolsEnabled();

        player.performCommand("besttools");

        assertEquals(!before, plugin.getPlayerSetting(player).isBestToolsEnabled());
    }

    @Test
    void hotbarTogglesHotbarOnly() {
        PlayerMock player = opPlayer();
        boolean before = plugin.getPlayerSetting(player).isHotbarOnly();

        player.performCommand("besttools hotbar");

        assertEquals(!before, plugin.getPlayerSetting(player).isHotbarOnly());
    }

    @Test
    void blacklistSubcommandDelegates() {
        PlayerMock player = opPlayer();
        player.getInventory().setItemInMainHand(new ItemStack(Material.STONE));

        player.performCommand("besttools bl add");

        assertTrue(plugin.getPlayerSetting(player).getBlacklist().contains(Material.STONE));
    }

    @Test
    void settingsOpensGuiHolder() {
        PlayerMock player = opPlayer();

        player.performCommand("besttools settings");
        server.getScheduler().performOneTick();

        assertTrue(player.getOpenInventory().getTopInventory().getHolder() instanceof GUIHolder);
    }

    @Test
    void nonPlayerSenderIsRejected() {
        server.dispatchCommand(server.getConsoleSender(), "besttools");

        assertEquals("You must be a player to run this command.", server.getConsoleSender().nextMessage());
    }

    @Test
    void reloadSubcommandDelegatesToCommandReload() {
        PlayerMock player = opPlayer();

        player.performCommand("besttools reload");

        assertTrue(player.nextMessage().contains("reloaded"));
    }

    @Test
    void debugSubcommandDelegatesToCommandDebug() {
        PlayerMock player = opPlayer();
        boolean before = Log.getDebugLevel() != DebugLevel.OFF;

        player.performCommand("besttools debug");

        assertEquals(!before, Log.getDebugLevel() != DebugLevel.OFF);
    }

    @Test
    void withoutUsePermissionCommandIsRejected() {
        PlayerMock player = newPlayer();
        boolean before = plugin.getPlayerSetting(player).isBestToolsEnabled();

        player.performCommand("besttools");

        assertEquals(before, plugin.getPlayerSetting(player).isBestToolsEnabled());
    }
}
