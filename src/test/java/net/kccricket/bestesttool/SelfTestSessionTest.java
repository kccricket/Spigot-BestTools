package net.kccricket.bestesttool;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.File;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link SelfTestSession} snapshots and exactly restores everything a self-test run
 * forces, without going through {@link SelfTestManager}/{@link SelfTestArena} — building a real
 * arena needs live {@code BlockData}/entity-spawning behavior MockBukkit doesn't provide (the same
 * gap {@code ToolSelectionTest}'s header documents for {@code BestToolsHandler} itself), so arena
 * building stays a manual, live-server verification step. This only exercises the snapshot/restore
 * contract, which has nothing to do with mining data.
 */
class SelfTestSessionTest extends BestToolsTestBase {

    private static final SelfTestSpec EMPTY_SPEC = new SelfTestSpec(Material.SMOOTH_STONE, List.of());

    @Test
    void restoreAndClearPutsInventoryGameModeAndSettingsBackExactly() {
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        inv.setItem(0, new ItemStack(Material.WOODEN_PICKAXE));
        inv.setItem(1, new ItemStack(Material.DIRT, 32));
        inv.setHeldItemSlot(1);
        player.setGameMode(GameMode.SURVIVAL);

        PlayerSetting settings = plugin.getPlayerSetting(player);
        settings.setHotbarOnly(false);
        settings.setFavoriteSlot(3);
        settings.setSwordOnMobs(false);
        settings.getBlacklist().add(Material.OBSIDIAN);

        SelfTestSession session = new SelfTestSession(plugin, player, EMPTY_SPEC);

        // Simulate the test actually running: kit replaces the inventory, settings get forced, the
        // tester ends up in creative for some reason, and their blacklist gets cleared.
        inv.setContents(new ItemStack[inv.getContents().length]);
        inv.setItem(0, new ItemStack(Material.NETHERITE_PICKAXE));
        inv.setHeldItemSlot(0);
        player.setGameMode(GameMode.CREATIVE);
        session.applyTestSettings(plugin, true);

        session.restoreAndClear(plugin);

        assertEquals(Material.WOODEN_PICKAXE, inv.getItem(0).getType());
        assertEquals(Material.DIRT, inv.getItem(1).getType());
        assertEquals(32, inv.getItem(1).getAmount());
        assertEquals(1, inv.getHeldItemSlot());
        assertEquals(GameMode.SURVIVAL, player.getGameMode());

        PlayerSetting restored = plugin.getPlayerSetting(player);
        assertFalse(restored.isHotbarOnly());
        assertEquals(3, restored.getFavoriteSlot());
        assertFalse(restored.isSwordOnMobs());
        assertTrue(restored.getBlacklist().contains(Material.OBSIDIAN));
        assertFalse(restored.isRefillEnabled(), "session started with refill_enabled off by default; must not leak the forced 'true'");
    }

    @Test
    void rawFavoriteSlotSentinelSurvivesARoundTrip() {
        PlayerMock player = newPlayer();
        PlayerSetting settings = plugin.getPlayerSetting(player);
        settings.setFavoriteSlot(-1); // "always follow whatever slot I'm holding"
        player.getInventory().setHeldItemSlot(4);

        SelfTestSession session = new SelfTestSession(plugin, player, EMPTY_SPEC);
        session.applyTestSettings(plugin, false); // forces favoriteSlot to -1 too, but from a different starting slot
        player.getInventory().setHeldItemSlot(7);
        session.restoreAndClear(plugin);

        assertEquals(-1, plugin.getPlayerSetting(player).getRawFavoriteSlot(),
                "restoring must preserve the -1 sentinel itself, not resolve it to whatever slot happened to be held at construction time");
    }

    @Test
    void constructingASessionWritesABackupFileAndRestoringDeletesIt() {
        PlayerMock player = newPlayer();
        File backup = new File(plugin.getDataFolder(), "selftest-backup-" + player.getUniqueId() + ".yml");

        SelfTestSession session = new SelfTestSession(plugin, player, EMPTY_SPEC);
        assertTrue(backup.isFile(), "constructing a session must persist a crash-safety backup");

        session.restoreAndClear(plugin);
        assertFalse(backup.isFile(), "a normal restore must clean up the backup file");
    }

    @Test
    void orphanedBackupIsRestoredAndDeletedOnNextJoin() {
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        inv.setItem(2, new ItemStack(Material.DIAMOND_SWORD));
        inv.setHeldItemSlot(2);
        plugin.getPlayerSetting(player).setHotbarOnly(false);

        // Session constructed (snapshot + backup file written) but never torn down — simulates a
        // server crash/kill mid-test, before restoreAndClear ever runs.
        new SelfTestSession(plugin, player, EMPTY_SPEC);

        // Simulate the test having mutated the inventory further, same as a real crash would leave it.
        inv.setContents(new ItemStack[inv.getContents().length]);
        inv.setItem(0, new ItemStack(Material.NETHERITE_PICKAXE));
        plugin.getPlayerSetting(player).setHotbarOnly(true);

        File backup = new File(plugin.getDataFolder(), "selftest-backup-" + player.getUniqueId() + ".yml");
        assertTrue(backup.isFile());

        boolean restored = SelfTestSession.restoreOrphanedBackup(plugin, player);

        assertTrue(restored);
        assertEquals(Material.DIAMOND_SWORD, inv.getItem(2).getType());
        assertEquals(2, inv.getHeldItemSlot());
        assertFalse(plugin.getPlayerSetting(player).isHotbarOnly());
        assertFalse(backup.isFile(), "an orphaned backup must be deleted once restored");
    }

    @Test
    void restoreOrphanedBackupIsANoOpWhenThereIsNoBackupFile() {
        PlayerMock player = newPlayer();
        assertFalse(SelfTestSession.restoreOrphanedBackup(plugin, player));
    }
}
