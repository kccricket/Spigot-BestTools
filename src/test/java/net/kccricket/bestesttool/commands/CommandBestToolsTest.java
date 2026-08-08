package net.kccricket.bestesttool.commands;

import net.kccricket.bestesttool.BestToolsTestBase;
import net.kccricket.bestesttool.model.PlayerSetting;
import net.kccricket.kcmclib.logging.DebugLevel;
import net.kccricket.kcmclib.logging.Log;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.nio.file.Files;
import java.util.function.Function;
import java.util.stream.Stream;

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

    // --- The six preference toggles: swordsforleaves/swordsforcobwebs/switchduringbattle/
    // avoidbreaking are flat; swordonmobs/useaxeassword live under the combat-gated node. All six
    // share the same boolPref shape as hotbaronly, so one parameterized pair of tests covers all
    // of them. avoidbreaking rides the plain bestesttool.use gate, same as the three flat ones —
    // no dedicated permission node, unlike combat. ---

    private static Stream<Arguments> booleanPreferences() {
        return Stream.of(
                Arguments.of("swordsforleaves", (Function<PlayerSetting, Boolean>) PlayerSetting::isConsiderSwordsForLeaves),
                Arguments.of("swordsforcobwebs", (Function<PlayerSetting, Boolean>) PlayerSetting::isConsiderSwordsForCobwebs),
                Arguments.of("switchduringbattle", (Function<PlayerSetting, Boolean>) PlayerSetting::isSwitchDuringBattle),
                Arguments.of("avoidbreaking", (Function<PlayerSetting, Boolean>) PlayerSetting::isAvoidBreakingTools),
                Arguments.of("combat swordonmobs", (Function<PlayerSetting, Boolean>) PlayerSetting::isSwordOnMobs),
                Arguments.of("combat useaxeassword", (Function<PlayerSetting, Boolean>) PlayerSetting::isUseAxeAsSword));
    }

    @ParameterizedTest
    @MethodSource("booleanPreferences")
    void preferenceTogglesBare(String literal, Function<PlayerSetting, Boolean> getter) {
        PlayerMock player = opPlayer();
        boolean before = getter.apply(plugin.getPlayerSetting(player));

        player.performCommand("bestesttool " + literal);

        assertEquals(!before, getter.apply(plugin.getPlayerSetting(player)));
    }

    @ParameterizedTest
    @MethodSource("booleanPreferences")
    void preferenceStateArgumentSetsExplicitValue(String literal, Function<PlayerSetting, Boolean> getter) {
        PlayerMock player = opPlayer();

        player.performCommand("bestesttool " + literal + " yes");
        assertTrue(getter.apply(plugin.getPlayerSetting(player)));

        player.performCommand("bestesttool " + literal + " no");
        assertFalse(getter.apply(plugin.getPlayerSetting(player)));
    }

    // --- Combat gate: allow_combat_switching (config) AND bestesttool.combat (permission) are
    // both required; a stored preference must survive being gated off, not get wiped. ---

    @Test
    void combatSubtreeUnreachableWithoutCombatPermission() {
        PlayerMock player = opPlayer();
        grant(player, "combat", Grant.DENIED);
        boolean before = plugin.getPlayerSetting(player).isSwordOnMobs();

        assertDoesNotThrow(() -> player.performCommand("bestesttool combat swordonmobs no"));

        assertEquals(before, plugin.getPlayerSetting(player).isSwordOnMobs());
    }

    @Test
    void combatSubtreeUnreachableWhenDisabledInConfig() throws Exception {
        PlayerMock player = opPlayer();
        setAllowCombatSwitchingOnDisk(false);
        boolean before = plugin.getPlayerSetting(player).isSwordOnMobs();

        assertDoesNotThrow(() -> player.performCommand("bestesttool combat swordonmobs no"));

        assertEquals(before, plugin.getPlayerSetting(player).isSwordOnMobs());
    }

    @Test
    void combatPreferenceValueSurvivesBeingGatedOffThenReenabled() throws Exception {
        PlayerMock player = opPlayer();
        player.performCommand("bestesttool combat swordonmobs no");
        assertFalse(plugin.getPlayerSetting(player).isSwordOnMobs());

        setAllowCombatSwitchingOnDisk(false);
        // Gated off — the stored value must be preserved, not wiped, even though it's unreachable.
        assertFalse(plugin.getPlayerSetting(player).isSwordOnMobs());

        setAllowCombatSwitchingOnDisk(true);
        assertFalse(plugin.getPlayerSetting(player).isSwordOnMobs());
    }

    private void setAllowCombatSwitchingOnDisk(boolean value) throws Exception {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        String edited = Files.readString(configFile.toPath())
                .replaceAll("allow_combat_switching: (true|false)", "allow_combat_switching: " + value);
        Files.writeString(configFile.toPath(), edited);
        plugin.configManager.reloadAll();
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
        assertLogMessageContains("You must be a player to run this command.",
                () -> server.dispatchCommand(server.getConsoleSender(), "bestesttool"));
    }

    @Test
    void reloadSubcommandDelegatesToCommandReload() {
        PlayerMock player = opPlayer();

        player.performCommand("bestesttool admin reload");

        assertTrue(player.nextMessage().contains("reloaded"));
    }

    @Test
    void reloadStillRequiresReloadPermissionEvenWithOnlyUsePermission() {
        PlayerMock player = newPlayer();
        grant(player, "use", Grant.NEW);

        // The "reload" node is gated by its own .requires(bestesttool.admin.reload) — bestesttool.use
        // alone does not open it. Brigadier hides a .requires-failing node from parsing entirely,
        // so the observable effect is "no reload happened", not a noPermission chat message.
        assertDoesNotThrow(() -> player.performCommand("bestesttool admin reload"));

        String message = player.nextMessage();
        assertTrue(message == null || !message.contains("reloaded"));
    }

    @Test
    void adminSubtreeUnreachableWithNoAdminPermissions() {
        // A plain player (no admin.* grants) must not be able to reach ANY admin subcommand —
        // this is what "admin" showing up in tab completion for everyone was letting through.
        // Brigadier hides a .requires-failing node from parsing entirely, so the observable
        // effect is "no reload happened" (possibly a generic Brigadier syntax-error message from
        // MockBukkit's default handler), not a noPermission chat message — same pattern as
        // reloadStillRequiresReloadPermissionEvenWithOnlyUsePermission below.
        PlayerMock player = newPlayer();
        boolean debugBefore = Log.getDebugLevel() != DebugLevel.OFF;

        assertDoesNotThrow(() -> player.performCommand("bestesttool admin reload"));
        String reloadMessage = player.nextMessage();
        assertTrue(reloadMessage == null || !reloadMessage.contains("reloaded"), reloadMessage);

        assertDoesNotThrow(() -> player.performCommand("bestesttool admin debug"));
        assertEquals(debugBefore, Log.getDebugLevel() != DebugLevel.OFF);
    }

    @Test
    void adminSubtreeReachableWithOnlyDebugPermission() {
        // The documented intent: a sender holding exactly one admin child permission (not the
        // bestesttool.admin umbrella) must still be able to traverse "admin" to reach that child.
        PlayerMock player = newPlayer();
        grant(player, "admin.debug", Grant.NEW);
        boolean before = Log.getDebugLevel() != DebugLevel.OFF;

        player.performCommand("bestesttool admin debug");

        assertEquals(!before, Log.getDebugLevel() != DebugLevel.OFF);
    }

    @Test
    void adminParentVisibilityDoesNotBypassASiblingsOwnPermission() {
        // Holding admin.reload makes "admin" reachable, but must not leak access to debug — each
        // child still enforces its own .requires independently of why the parent was reachable.
        PlayerMock player = newPlayer();
        grant(player, "admin.reload", Grant.NEW);
        boolean before = Log.getDebugLevel() != DebugLevel.OFF;

        assertDoesNotThrow(() -> player.performCommand("bestesttool admin debug"));

        assertEquals(before, Log.getDebugLevel() != DebugLevel.OFF);
    }

    @Test
    void debugSubcommandDelegatesToCommandDebug() {
        PlayerMock player = opPlayer();
        boolean before = Log.getDebugLevel() != DebugLevel.OFF;

        player.performCommand("bestesttool admin debug");

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
