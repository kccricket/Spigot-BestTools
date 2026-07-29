package net.kccricket.bestesttool.commands;

import net.kccricket.bestesttool.BestToolsTestBase;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.kccricket.bestesttool.commands.BestToolsCommands;
import net.kccricket.bestesttool.commands.CommandReload;

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
        assertLogMessageContains("You must be a player to run this command.",
                () -> server.dispatchCommand(server.getConsoleSender(), "refill"));
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
     * The {@code /refill} alias has no {@code hotbar} subcommand of its own — unlike
     * {@code /bestesttool hotbaronly}. {@code hotbar} still parses, though: it's consumed by the
     * alias's own {@code [<state>]} child (see {@link BestToolsCommands#buildRefillAlias}) as an
     * unrecognized state word, so it's rejected with the {@code invalidValue} message rather than
     * failing to resolve at all.
     */
    @Test
    void refillHotbarIsNotAValidSubcommand() {
        PlayerMock player = newPlayer();
        player.setOp(true);
        boolean before = plugin.getPlayerSetting(player).isHotbarOnly();

        assertDoesNotThrow(() -> player.performCommand("refill hotbar"));

        assertEquals(before, plugin.getPlayerSetting(player).isHotbarOnly());
        assertTrue(player.nextMessage().contains("Invalid value"));
    }

    /**
     * {@code /refill} and {@code /rf} are root aliases (see
     * {@link BestToolsCommands#buildRefillAlias}) that must accept the same {@code [<state>]}
     * argument as the canonical {@code /bestesttool refill} — regression coverage for the alias
     * previously being built as a bare leaf node with no argument child at all.
     */
    @Test
    void refillStateArgumentOnBothRootAliases() {
        PlayerMock player = newPlayer();
        player.setOp(true);

        player.performCommand("refill no");
        assertFalse(plugin.getPlayerSetting(player).isRefillEnabled());

        player.performCommand("rf yes");
        assertTrue(plugin.getPlayerSetting(player).isRefillEnabled());
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
