package net.kccricket.bestesttool;

import org.bukkit.command.Command;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandReloadTest extends BestToolsTestBase {

    @ParameterizedTest
    @EnumSource(Grant.class)
    void reloadGatesOnReloadPermission(Grant grantType) {
        PlayerMock player = newPlayer();
        grant(player, "reload", grantType);
        Command command = server.getPluginCommand("besttools");

        CommandReload.reload(player, command, plugin);

        String message = player.nextMessage();
        if (grantType != Grant.NONE) {
            assertTrue(message.contains("reloaded"));
        } else {
            assertTrue(message.contains("permission"));
        }
    }
}
