package net.kccricket.bestesttool;

import net.kccricket.kcmclib.logging.DebugLevel;
import net.kccricket.kcmclib.logging.Log;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandBestToolsTest extends BestToolsTestBase {

    private PlayerMock opPlayer() {
        PlayerMock p = newPlayer();
        p.setOp(true);
        return p;
    }

    @Test
    void defaultTogglesBestToolsEnabled() {
        PlayerMock player = opPlayer();
        boolean before = plugin.getPlayerSetting(player).isBestToolsEnabled();

        player.performCommand("bestesttool");

        assertEquals(!before, plugin.getPlayerSetting(player).isBestToolsEnabled());
    }

    /**
     * Unlike {@code hotbaronly}/{@code refill}/{@code debug}, the bare root
     * toggle does not take a {@code [<state>]} argument — it's the entry point to the whole
     * subcommand tree, so a trailing word is rejected rather than interpreted as a boolean.
     */
    @Test
    void bestesttoolDoesNotAcceptAStateArgument() {
        PlayerMock player = opPlayer();
        boolean before = plugin.getPlayerSetting(player).isBestToolsEnabled();

        assertDoesNotThrow(() -> player.performCommand("bestesttool yes"));

        assertEquals(before, plugin.getPlayerSetting(player).isBestToolsEnabled());
    }

    @Test
    void hotbaronlyTogglesHotbarOnly() {
        PlayerMock player = opPlayer();
        boolean before = plugin.getPlayerSetting(player).isHotbarOnly();

        player.performCommand("bestesttool hotbaronly");

        assertEquals(!before, plugin.getPlayerSetting(player).isHotbarOnly());
    }

    @Test
    void hotbaronlyStateArgumentSetsExplicitValue() {
        PlayerMock player = opPlayer();

        player.performCommand("bestesttool hotbaronly yes");
        assertTrue(plugin.getPlayerSetting(player).isHotbarOnly());

        player.performCommand("bestesttool hotbaronly no");
        assertFalse(plugin.getPlayerSetting(player).isHotbarOnly());

        player.performCommand("bestesttool hotbaronly true");
        assertTrue(plugin.getPlayerSetting(player).isHotbarOnly());
    }

    @Test
    void refillSubcommandTogglesRefillEnabled() {
        PlayerMock player = opPlayer();
        boolean before = plugin.getPlayerSetting(player).isRefillEnabled();

        player.performCommand("bestesttool refill");

        assertEquals(!before, plugin.getPlayerSetting(player).isRefillEnabled());
    }

    @Test
    void blacklistSubcommandDelegates() {
        PlayerMock player = opPlayer();
        player.getInventory().setItemInMainHand(new ItemStack(Material.STONE));

        player.performCommand("bestesttool blacklist add");

        assertTrue(plugin.getPlayerSetting(player).getBlacklist().contains(Material.STONE));
    }

    @Test
    void favoriteslotReportsAndSetsExplicitValue() {
        PlayerMock player = opPlayer();

        player.performCommand("bestesttool favoriteslot 3");
        assertEquals(3, plugin.getPlayerSetting(player).getFavoriteSlot());

        player.performCommand("bestesttool favoriteslot -1");
        assertEquals(player.getInventory().getHeldItemSlot(), plugin.getPlayerSetting(player).getFavoriteSlot());
    }

    @Test
    void nonPlayerSenderIsRejected() {
        server.dispatchCommand(server.getConsoleSender(), "bestesttool");

        assertEquals("You must be a player to run this command.", server.getConsoleSender().nextMessage());
    }

    @Test
    void reloadSubcommandDelegatesToCommandReload() {
        PlayerMock player = opPlayer();

        player.performCommand("bestesttool reload");

        assertTrue(player.nextMessage().contains("reloaded"));
    }

    @Test
    void reloadStillRequiresReloadPermissionEvenWithOnlyUsePermission() {
        PlayerMock player = newPlayer();
        grant(player, "use", Grant.NEW);

        // The "reload" node is gated by its own .requires(bestesttool.reload) — bestesttool.use
        // alone does not open it. Brigadier hides a .requires-failing node from parsing entirely,
        // so the observable effect is "no reload happened", not a noPermission chat message.
        assertDoesNotThrow(() -> player.performCommand("bestesttool reload"));

        String message = player.nextMessage();
        assertTrue(message == null || !message.contains("reloaded"));
    }

    @Test
    void debugSubcommandDelegatesToCommandDebug() {
        PlayerMock player = opPlayer();
        boolean before = Log.getDebugLevel() != DebugLevel.OFF;

        player.performCommand("bestesttool debug");

        assertEquals(!before, Log.getDebugLevel() != DebugLevel.OFF);
    }

    @Test
    void withoutUsePermissionCommandIsRejected() {
        PlayerMock player = newPlayer();
        grant(player, "use", Grant.DENIED);
        boolean before = plugin.getPlayerSetting(player).isBestToolsEnabled();

        player.performCommand("bestesttool");

        assertEquals(before, plugin.getPlayerSetting(player).isBestToolsEnabled());
    }
}
