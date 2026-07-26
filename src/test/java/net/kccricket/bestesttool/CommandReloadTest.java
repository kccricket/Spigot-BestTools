package net.kccricket.bestesttool;

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

        CommandReload.reload(player, plugin);

        String message = player.nextMessage();
        if (grantType == Grant.NEW) {
            assertTrue(message.contains("reloaded"));
        } else {
            assertTrue(message.contains("permission"));
        }
    }
}
