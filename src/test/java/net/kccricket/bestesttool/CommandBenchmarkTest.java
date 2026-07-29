package net.kccricket.bestesttool;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Command-tree gating and lifecycle tests for {@code /bestesttool benchmark}. Doesn't advance any
 * ticks (no {@code performTicks}/similar) — the scheduled batch itself calls
 * {@link BestToolsHandler#selectBestTool}, which reads live {@code BlockData.getDestroySpeed}, and
 * MockBukkit's {@code BlockDataMock} throws {@code UnimplementedOperationException} from that (see
 * {@code ToolSelectionTest}'s note); a real run can only be exercised on a live server. These tests
 * only cover what happens before the first tick fires: gating, start/stop/status messaging.
 */
class CommandBenchmarkTest extends BestToolsTestBase {

    private PlayerMock opPlayer() {
        PlayerMock p = newPlayer();
        p.setOp(true);
        return p;
    }

    @Test
    void benchmarkIsHiddenWhenEnableBenchmarkIsFalse() {
        PlayerMock player = opPlayer();
        // bestesttool.admin.benchmark defaults to op, so the player already holds it — the config flag
        // (default false) is the only thing gating the node here. Brigadier hides a
        // .requires-failing node from parsing entirely, so the observable effect is "nothing
        // benchmark-shaped happened" (possibly a generic Brigadier syntax-error message), not a
        // noPermission chat message — same pattern as CommandBestToolsTest's reload gating test.
        assertDoesNotThrow(() -> player.performCommand("bestesttool admin benchmark status"));

        String message = player.nextMessage();
        assertTrue(message == null || !message.contains("benchmark"), message);
        assertFalse(plugin.benchmarkManager.isRunning());
    }

    @Test
    void benchmarkRequiresBenchmarkPermissionEvenWhenEnabled() {
        PlayerMock player = newPlayer();
        grant(player, "admin.benchmark", Grant.DENIED);
        plugin.getConfig().set("enable_benchmark", true);

        assertDoesNotThrow(() -> player.performCommand("bestesttool admin benchmark status"));

        String message = player.nextMessage();
        assertTrue(message == null || !message.contains("benchmark"), message);
    }

    @Test
    void startSendsStartedMessageAndGatesASecondStart() {
        PlayerMock player = opPlayer();
        plugin.getConfig().set("enable_benchmark", true);

        player.performCommand("bestesttool admin benchmark start");
        String started = player.nextMessage();
        assertNotNull(started);
        assertTrue(started.contains("full"), "default kit is 'full': " + started);
        assertTrue(plugin.benchmarkManager.isRunning());

        player.performCommand("bestesttool admin benchmark start");
        String again = player.nextMessage();
        assertNotNull(again);
        assertTrue(again.contains("already running"), again);

        plugin.benchmarkManager.abortAll("test cleanup");
    }

    @Test
    void startAcceptsAnExplicitHotbarKit() {
        PlayerMock player = opPlayer();
        plugin.getConfig().set("enable_benchmark", true);

        player.performCommand("bestesttool admin benchmark start hotbar");
        String started = player.nextMessage();
        assertNotNull(started);
        assertTrue(started.contains("hotbar"), started);

        plugin.benchmarkManager.abortAll("test cleanup");
    }

    @Test
    void stopEndsARunningBenchmarkAndReportsNoneAfterwards() {
        PlayerMock player = opPlayer();
        plugin.getConfig().set("enable_benchmark", true);

        player.performCommand("bestesttool admin benchmark start");
        player.nextMessage(); // started

        player.performCommand("bestesttool admin benchmark stop");
        assertNotNull(player.nextMessage());
        assertFalse(plugin.benchmarkManager.isRunning());

        player.performCommand("bestesttool admin benchmark stop");
        String noneRunning = player.nextMessage();
        assertNotNull(noneRunning);
        assertTrue(noneRunning.contains("No benchmark"), noneRunning);
    }

    @Test
    void statusReportsNoneRunningWhenIdle() {
        PlayerMock player = opPlayer();
        plugin.getConfig().set("enable_benchmark", true);

        player.performCommand("bestesttool admin benchmark status");

        String status = player.nextMessage();
        assertNotNull(status);
        assertTrue(status.contains("No benchmark"), status);
    }
}
