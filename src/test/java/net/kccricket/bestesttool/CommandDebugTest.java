package net.kccricket.bestesttool;

import org.bukkit.command.Command;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommandDebugTest extends BestToolsTestBase {

    @ParameterizedTest
    @EnumSource(Grant.class)
    void debugGatesOnDebugPermission(Grant grantType) {
        PlayerMock player = newPlayer();
        grant(player, "debug", grantType);
        boolean before = plugin.debug;
        Command command = server.getPluginCommand("besttools");

        CommandDebug.debug(player, command, plugin, "debug");

        assertEquals(grantType != Grant.NONE ? !before : before, plugin.debug);
    }

    @Test
    void performanceArgumentIsCaseInsensitive() {
        PlayerMock player = newPlayer();
        player.setOp(true);
        boolean before = plugin.measurePerformance;
        Command command = server.getPluginCommand("besttools");

        CommandDebug.debug(player, command, plugin, "Performance");

        assertEquals(!before, plugin.measurePerformance);
    }
}
