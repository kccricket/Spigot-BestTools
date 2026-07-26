package net.kccricket.bestesttool;

import net.kccricket.kcmclib.logging.DebugLevel;
import net.kccricket.kcmclib.logging.Log;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandDebugTest extends BestToolsTestBase {

    @ParameterizedTest
    @EnumSource(Grant.class)
    void debugGatesOnDebugPermission(Grant grantType) {
        PlayerMock player = newPlayer();
        grant(player, "debug", grantType);
        boolean before = Log.getDebugLevel() != DebugLevel.OFF;

        CommandDebug.debug(player, plugin, "debug");

        boolean after = Log.getDebugLevel() != DebugLevel.OFF;
        assertEquals(grantType == Grant.NEW ? !before : before, after);
    }

    @Test
    void performanceArgumentIsCaseInsensitive() {
        PlayerMock player = newPlayer();
        player.setOp(true);
        boolean before = plugin.measurePerformance;

        CommandDebug.debug(player, plugin, "Performance");

        assertEquals(!before, plugin.measurePerformance);
    }

    @Test
    void explicitStateSetsDebugRegardlessOfCurrentValue() {
        PlayerMock player = newPlayer();
        player.setOp(true);
        Log.setDebugLevel(DebugLevel.OFF);

        CommandDebug.debug(player, plugin, "debug", true);
        assertTrue(Log.getDebugLevel() != DebugLevel.OFF);

        CommandDebug.debug(player, plugin, "debug", true);
        assertTrue(Log.getDebugLevel() != DebugLevel.OFF);

        CommandDebug.debug(player, plugin, "debug", false);
        assertEquals(DebugLevel.OFF, Log.getDebugLevel());
    }

    @Test
    void explicitStateSetsPerformanceRegardlessOfCurrentValue() {
        PlayerMock player = newPlayer();
        player.setOp(true);
        plugin.measurePerformance = false;

        CommandDebug.debug(player, plugin, "performance", true);
        assertTrue(plugin.measurePerformance);

        CommandDebug.debug(player, plugin, "performance", true);
        assertTrue(plugin.measurePerformance);

        CommandDebug.debug(player, plugin, "performance", false);
        assertFalse(plugin.measurePerformance);
    }

    @Test
    void debugStateArgumentViaCommandPath() {
        PlayerMock player = newPlayer();
        player.setOp(true);
        Log.setDebugLevel(DebugLevel.OFF);

        player.performCommand("bestesttool debug yes");
        assertTrue(Log.getDebugLevel() != DebugLevel.OFF);

        player.performCommand("bestesttool debug no");
        assertEquals(DebugLevel.OFF, Log.getDebugLevel());
    }
}
