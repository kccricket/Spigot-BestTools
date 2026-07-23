package net.kccricket.bestesttool;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandRefillTest extends BestToolsTestBase {

    @Test
    void defaultTogglesRefillEnabled() {
        PlayerMock player = newPlayer();
        player.setOp(true);
        boolean before = plugin.getPlayerSetting(player).isRefillEnabled();

        player.performCommand("refill");

        assertEquals(!before, plugin.getPlayerSetting(player).isRefillEnabled());
    }

    @Test
    void reloadSubcommandDelegatesWithBothPermissions() {
        PlayerMock player = newPlayer();
        player.setOp(true);

        player.performCommand("refill reload");

        assertTrue(player.nextMessage().contains("reloaded"));
    }

    @Test
    void nonPlayerSenderIsRejected() {
        server.dispatchCommand(server.getConsoleSender(), "refill");

        assertEquals("You must be a player to run this command.", server.getConsoleSender().nextMessage());
    }

    @Test
    void withoutRefillPermissionCommandIsRejected() {
        PlayerMock player = newPlayer();
        boolean before = plugin.getPlayerSetting(player).isRefillEnabled();

        player.performCommand("refill");

        assertEquals(before, plugin.getPlayerSetting(player).isRefillEnabled());
        assertTrue(player.nextMessage().contains("permission"));
    }

    @Test
    void reloadStillRequiresReloadPermissionEvenWithOnlyRefillPermission() {
        PlayerMock player = newPlayer();
        player.addAttachment(plugin).setPermission("bestesttool.refill", true);

        player.performCommand("refill reload");

        assertTrue(player.nextMessage().contains("permission"));
    }
}
