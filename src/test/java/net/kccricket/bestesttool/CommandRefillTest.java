package net.kccricket.bestesttool;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /**
     * {@code /bestesttool refill} is canonical; {@code /refill} and {@code /rf} are root aliases
     * that share its {@link com.mojang.brigadier.Command} instance (see
     * {@link BestToolsCommands#buildRefillAlias}) — all three must toggle the same flag.
     */
    @Test
    void bestesttoolRefillAndBothRootAliasesToggleTheSameFlag() {
        PlayerMock player = newPlayer();
        player.setOp(true);
        boolean before = plugin.getPlayerSetting(player).isRefillEnabled();

        player.performCommand("bestesttool refill");
        assertEquals(!before, plugin.getPlayerSetting(player).isRefillEnabled());

        player.performCommand("rf");
        assertEquals(before, plugin.getPlayerSetting(player).isRefillEnabled());

        player.performCommand("refill");
        assertEquals(!before, plugin.getPlayerSetting(player).isRefillEnabled());
    }

    @Test
    void refillStateArgumentSetsExplicitValue() {
        PlayerMock player = newPlayer();
        player.setOp(true);

        player.performCommand("bestesttool refill no");
        assertFalse(plugin.getPlayerSetting(player).isRefillEnabled());

        player.performCommand("bestesttool refill yes");
        assertTrue(plugin.getPlayerSetting(player).isRefillEnabled());
    }

    @Test
    void nonPlayerSenderIsRejected() {
        server.dispatchCommand(server.getConsoleSender(), "refill");

        assertEquals("You must be a player to run this command.", server.getConsoleSender().nextMessage());
    }

    @Test
    void withoutRefillPermissionCommandIsRejected() {
        PlayerMock player = newPlayer();
        grant(player, "refill", Grant.DENIED);
        boolean before = plugin.getPlayerSetting(player).isRefillEnabled();

        player.performCommand("refill");

        assertEquals(before, plugin.getPlayerSetting(player).isRefillEnabled());
        assertTrue(player.nextMessage().contains("permission"));
    }

    /**
     * The {@code /refill} alias is a bare leaf node with no subcommands of its own — unlike
     * {@code /bestesttool hotbaronly}, {@code /refill hotbar} must not resolve to anything.
     */
    @Test
    void refillHotbarIsNotAValidSubcommand() {
        PlayerMock player = newPlayer();
        player.setOp(true);
        boolean before = plugin.getPlayerSetting(player).isHotbarOnly();

        assertDoesNotThrow(() -> player.performCommand("refill hotbar"));

        assertEquals(before, plugin.getPlayerSetting(player).isHotbarOnly());
    }

    /**
     * {@code /refill reload} was removed as redundant with {@code /bestesttool reload} (both only
     * ever forwarded to {@link CommandReload#reload}) — confirm it no longer parses instead of
     * silently reloading.
     */
    @Test
    void refillReloadIsNoLongerValid() {
        PlayerMock player = newPlayer();
        player.setOp(true);

        assertDoesNotThrow(() -> player.performCommand("refill reload"));

        String message = player.nextMessage();
        assertTrue(message == null || !message.contains("reloaded"));
    }
}
