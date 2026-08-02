package net.kccricket.bestesttool.util;

import net.kccricket.bestesttool.BestToolsTestBase;
import org.bukkit.GameMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link PlayerUtils#isAllowedGamemode} — the pure gate behind {@code allow_in_adventure_mode}. */
class PlayerUtilsTest extends BestToolsTestBase {

    @Test
    void survivalAlwaysAllowedRegardlessOfFlag() {
        PlayerMock player = newPlayer();
        player.setGameMode(GameMode.SURVIVAL);

        assertTrue(PlayerUtils.isAllowedGamemode(player, false));
        assertTrue(PlayerUtils.isAllowedGamemode(player, true));
    }

    @Test
    void adventureTracksTheFlag() {
        PlayerMock player = newPlayer();
        player.setGameMode(GameMode.ADVENTURE);

        assertFalse(PlayerUtils.isAllowedGamemode(player, false));
        assertTrue(PlayerUtils.isAllowedGamemode(player, true));
    }

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"CREATIVE", "SPECTATOR"})
    void creativeAndSpectatorNeverAllowedRegardlessOfFlag(GameMode mode) {
        PlayerMock player = newPlayer();
        player.setGameMode(mode);

        assertFalse(PlayerUtils.isAllowedGamemode(player, false));
        assertFalse(PlayerUtils.isAllowedGamemode(player, true),
                "allow_in_adventure_mode must not leak into " + mode);
    }
}
