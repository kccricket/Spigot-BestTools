package net.kccricket.bestesttool;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionUtilsTest extends BestToolsTestBase {

    @ParameterizedTest
    @ValueSource(strings = {"use", "refill", "reload", "debug"})
    void newNodeGrantsAccess(String suffix) {
        PlayerMock player = newPlayer();
        player.addAttachment(plugin).setPermission("bestesttool." + suffix, true);
        assertTrue(PermissionUtils.has(player, suffix));
    }

    @ParameterizedTest
    @ValueSource(strings = {"use", "refill", "reload", "debug"})
    void legacyNodeGrantsAccess(String suffix) {
        PlayerMock player = newPlayer();
        player.addAttachment(plugin).setPermission("besttools." + suffix, true);
        assertTrue(PermissionUtils.has(player, suffix));
    }

    @ParameterizedTest
    @ValueSource(strings = {"use", "refill", "reload", "debug"})
    void neitherNodeDeniesAccess(String suffix) {
        PlayerMock player = newPlayer();
        assertFalse(PermissionUtils.has(player, suffix));
    }
}
