package net.kccricket.bestesttool;

import net.kccricket.kcmclib.logging.DebugLevel;
import net.kccricket.kcmclib.logging.Log;

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
        boolean before = Log.getDebugLevel() != DebugLevel.OFF;
        Command command = server.getPluginCommand("besttools");

        CommandDebug.debug(player, command, plugin, "debug");

        boolean after = Log.getDebugLevel() != DebugLevel.OFF;
        assertEquals(grantType != Grant.NONE ? !before : before, after);
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
