package net.kccricket.bestesttool;

import net.kccricket.bestesttool.Main;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.concurrent.ConcurrentMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.kccricket.bestesttool.model.PlayerSetting;

/**
 * Regression coverage for the Folia thread-safety fix to {@code Main.playerSettings}: it must be
 * a {@link ConcurrentMap} (not a plain {@code HashMap}), a {@code /bestesttool reload} must not
 * discard an online player's already-created {@link PlayerSetting}, and quitting must still
 * remove the entry. The concurrency hazard itself (two region threads racing a first {@code put})
 * can't be reproduced in-process under single-threaded MockBukkit — this only pins the structural
 * properties that make it safe.
 */
class PlayerSettingsLifecycleTest extends BestToolsTestBase {

    private PlayerMock opPlayer() {
        PlayerMock player = newPlayer();
        player.setOp(true);
        return player;
    }

    @Test
    void playerSettingsIsConcurrent() {
        assertInstanceOf(ConcurrentMap.class, plugin.playerSettings,
                "playerSettings must be a ConcurrentMap so region threads can safely get-or-create concurrently");
    }

    @Test
    void playerSettingSurvivesReload() {
        PlayerMock player = opPlayer();
        PlayerSetting before = plugin.getPlayerSetting(player);

        player.performCommand("bestesttool admin reload");

        assertSame(before, plugin.getPlayerSetting(player),
                "reload must not discard an online player's PlayerSetting instance");
    }

    @Test
    void quitRemovesPlayerSetting() {
        PlayerMock player = newPlayer();
        plugin.getPlayerSetting(player);
        assertTrue(plugin.playerSettings.containsKey(player.getUniqueId()));

        player.disconnect();

        assertFalse(plugin.playerSettings.containsKey(player.getUniqueId()),
                "quitting must remove the player's entry from playerSettings");
    }
}
