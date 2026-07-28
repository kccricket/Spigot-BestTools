package net.kccricket.bestesttool;

import net.kccricket.kcmclib.logging.Log;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * One tester's in-progress {@code /bestesttool selftest} run: where they are in the stage/case
 * sequence, the arena currently built for them, and everything needed to put them back exactly how
 * they were. Constructed and torn down by {@link SelfTestManager}; the actual event evaluation
 * lives in {@link SelfTestListener}.
 */
final class SelfTestSession {

    final Player player;
    final SelfTestSpec spec;

    int stageIndex = 0;
    int caseIndex = 0;
    int stagePassed = 0;
    int totalPassed = 0;
    int totalCases = 0;

    SelfTestArena arena;

    /**
     * The main-hand stack immediately before the plugin's own switch logic runs for the current
     * case's interaction — captured at {@code LOWEST} priority, compared at {@code MONITOR}. Only
     * meaningful for an {@code UNCHANGED} expectation; {@code null} between interactions.
     */
    ItemStack pendingBeforeSnapshot;

    // --- Snapshot, restored in restoreAndClear() -------------------------------------------------

    private final ItemStack[] savedContents;
    private final ItemStack[] savedArmor;
    private final ItemStack savedOffHand;
    private final int savedHeldSlot;
    private final GameMode savedGameMode;
    private final boolean savedBestToolsEnabled;
    private final boolean savedHotbarOnly;
    private final int savedFavoriteSlot;
    private final boolean savedSwordOnMobs;
    private final boolean savedRefillEnabled;
    private final List<String> savedBlacklist;

    private final File backupFile;

    SelfTestSession(Main main, Player player, SelfTestSpec spec) {
        this.player = player;
        this.spec = spec;

        PlayerInventory inv = player.getInventory();
        this.savedContents = inv.getContents().clone();
        this.savedArmor = inv.getArmorContents().clone();
        this.savedOffHand = inv.getItemInOffHand().clone();
        this.savedHeldSlot = inv.getHeldItemSlot();
        this.savedGameMode = player.getGameMode();

        PlayerSetting settings = main.getPlayerSetting(player);
        this.savedBestToolsEnabled = settings.isBestToolsEnabled();
        this.savedHotbarOnly = settings.isHotbarOnly();
        this.savedFavoriteSlot = settings.getRawFavoriteSlot();
        this.savedSwordOnMobs = settings.isSwordOnMobs();
        this.savedRefillEnabled = settings.isRefillEnabled();
        this.savedBlacklist = new ArrayList<>(settings.getBlacklist().toStringList());

        this.backupFile = new File(main.getDataFolder(), "selftest-backup-" + player.getUniqueId() + ".yml");
        writeBackupFile();
    }

    /** Forces the plugin state a self-test run needs, regardless of what the tester had set. */
    void applyTestSettings(Main main, boolean refillEnabledForThisStage) {
        player.setGameMode(GameMode.SURVIVAL);
        PlayerSetting settings = main.getPlayerSetting(player);
        settings.setBestToolsEnabled(true);
        settings.setHotbarOnly(true);
        settings.setFavoriteSlot(-1);
        settings.setSwordOnMobs(true);
        settings.setRefillEnabled(refillEnabledForThisStage);
        settings.getBlacklist().mats.clear();
        settings.getBtcache().invalidated();
    }

    /**
     * Best-effort crash-safety net: persists the snapshot to disk so a server that dies mid-test
     * doesn't just lose the tester's real inventory. Deleted the moment the session ends normally
     * (see {@link SelfTestManager#stop}); restored and deleted on the tester's next join if it's
     * still there (see {@code SelfTestListener#onJoin}).
     */
    private void writeBackupFile() {
        try {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("heldSlot", savedHeldSlot);
            yaml.set("gameMode", savedGameMode.name());
            yaml.set("bestToolsEnabled", savedBestToolsEnabled);
            yaml.set("hotbarOnly", savedHotbarOnly);
            yaml.set("favoriteSlot", savedFavoriteSlot);
            yaml.set("swordOnMobs", savedSwordOnMobs);
            yaml.set("refillEnabled", savedRefillEnabled);
            yaml.set("blacklist", savedBlacklist);

            List<ItemStack> all = new ArrayList<>(savedContents.length + savedArmor.length + 1);
            for (ItemStack i : savedContents) all.add(i);
            for (ItemStack i : savedArmor) all.add(i);
            all.add(savedOffHand);
            yaml.set("items", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(all)));

            yaml.save(backupFile);
        } catch (IOException e) {
            Log.warning("Could not write self-test backup file for " + player.getName()
                    + " — if the server crashes before the test ends, restore their inventory manually.", e);
        }
    }

    /** Restores everything this session forced/replaced, then deletes the on-disk backup. */
    void restoreAndClear(Main main) {
        if (arena != null) {
            arena.teardown();
            arena = null;
        }

        PlayerInventory inv = player.getInventory();
        inv.setContents(savedContents);
        inv.setArmorContents(savedArmor);
        inv.setItemInOffHand(savedOffHand);
        inv.setHeldItemSlot(savedHeldSlot);
        player.setGameMode(savedGameMode);

        PlayerSetting settings = main.getPlayerSetting(player);
        settings.setBestToolsEnabled(savedBestToolsEnabled);
        settings.setHotbarOnly(savedHotbarOnly);
        settings.setFavoriteSlot(savedFavoriteSlot);
        settings.setSwordOnMobs(savedSwordOnMobs);
        settings.setRefillEnabled(savedRefillEnabled);
        settings.getBlacklist().mats.clear();
        for (String s : savedBlacklist) settings.getBlacklist().add(s);
        settings.getBtcache().invalidated();

        //noinspection ResultOfMethodCallIgnored
        backupFile.delete();
    }

    /**
     * Restores a backup file left behind by a session that never got to call
     * {@link #restoreAndClear} — e.g. the server crashed, or was stopped mid-test. Called from
     * {@code SelfTestListener#onJoin}. Returns {@code true} if a backup was found and restored.
     */
    static boolean restoreOrphanedBackup(Main main, Player player) {
        File file = new File(main.getDataFolder(), "selftest-backup-" + player.getUniqueId() + ".yml");
        if (!file.isFile()) return false;

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            byte[] bytes = Base64.getDecoder().decode(yaml.getString("items", ""));
            ItemStack[] all = ItemStack.deserializeItemsFromBytes(bytes);

            PlayerInventory inv = player.getInventory();
            int mainSize = inv.getContents().length;
            int armorSize = inv.getArmorContents().length;

            ItemStack[] contents = new ItemStack[mainSize];
            ItemStack[] armor = new ItemStack[armorSize];
            System.arraycopy(all, 0, contents, 0, Math.min(mainSize, all.length));
            System.arraycopy(all, mainSize, armor, 0, Math.min(armorSize, Math.max(0, all.length - mainSize)));
            ItemStack offHand = all.length > mainSize + armorSize ? all[mainSize + armorSize] : null;

            inv.setContents(contents);
            inv.setArmorContents(armor);
            inv.setItemInOffHand(offHand);
            inv.setHeldItemSlot(Math.max(0, Math.min(8, yaml.getInt("heldSlot", 0))));

            GameMode gameMode = parseGameMode(yaml.getString("gameMode"));
            if (gameMode != null) player.setGameMode(gameMode);

            PlayerSetting settings = main.getPlayerSetting(player);
            settings.setBestToolsEnabled(yaml.getBoolean("bestToolsEnabled", settings.isBestToolsEnabled()));
            settings.setHotbarOnly(yaml.getBoolean("hotbarOnly", settings.isHotbarOnly()));
            settings.setFavoriteSlot(yaml.getInt("favoriteSlot", settings.getFavoriteSlot()));
            settings.setSwordOnMobs(yaml.getBoolean("swordOnMobs", settings.isSwordOnMobs()));
            settings.setRefillEnabled(yaml.getBoolean("refillEnabled", settings.isRefillEnabled()));
            settings.getBlacklist().mats.clear();
            for (String s : yaml.getStringList("blacklist")) settings.getBlacklist().add(s);
            settings.getBtcache().invalidated();

            Log.warning("Restored " + player.getName() + "'s pre-self-test inventory from an interrupted run.");
            return true;
        } catch (Exception e) {
            Log.warning("Found a self-test backup for " + player.getName() + " but failed to restore it: "
                    + file + " — check it by hand.", e);
            return false;
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    private static GameMode parseGameMode(String name) {
        if (name == null) return null;
        try {
            return GameMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    SelfTestSpec.Stage currentStage() {
        return spec.stage(stageIndex);
    }

    SelfTestSpec.Case currentCase() {
        SelfTestSpec.Stage stage = currentStage();
        return (stage != null && caseIndex >= 0 && caseIndex < stage.cases.size()) ? stage.cases.get(caseIndex) : null;
    }
}
