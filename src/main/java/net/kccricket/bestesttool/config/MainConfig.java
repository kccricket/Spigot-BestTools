package net.kccricket.bestesttool.config;

import net.kccricket.bestesttool.Main;
import net.kccricket.kcmclib.config.ManagedConfig;
import net.kccricket.kcmclib.config.ResourceUpdater;
import net.kccricket.kcmclib.logging.DebugLevel;
import net.kccricket.kcmclib.logging.Log;

import java.util.List;

/**
 * Wraps {@code config.yml} via Bukkit's built-in {@code JavaPlugin} config machinery, mirroring
 * ClickSorted's {@code MainConfig}.
 * <p>
 * On every {@link #load()} / {@link #reload()} the file is guaranteed to exist on disk: defaults
 * are applied (including comments for any newly-introduced key, via
 * {@link ResourceUpdater#copyMissingKeyComments}) and {@code saveConfig()} is called before the
 * config is read.
 */
public class MainConfig implements ManagedConfig {

    private final Main plugin;

    public MainConfig(Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public String fileName() {
        return "config.yml";
    }

    @Override
    public void load() {
        applyDefaults();
        normalizeValues();
        plugin.saveConfig();
        applyToRuntime();
    }

    @Override
    public void reload() {
        // If the file was deleted mid-session, reloadConfig() loads an empty config; saveConfig()
        // (via applyDefaults()/load()) then recreates it with bundled defaults.
        plugin.reloadConfig();
        load();
    }

    private void applyDefaults() {
        plugin.getConfig().options().copyDefaults(true);
        ResourceUpdater.copyMissingKeyComments(plugin.getConfig(), plugin.getConfig().getDefaults());
    }

    /** Clamps an out-of-range {@code defaults.favorite_slot} back to the default (8), warning once. */
    private void normalizeValues() {
        int favoriteSlot = plugin.getConfig().getInt("defaults.favorite_slot");
        if (favoriteSlot > 8) {
            Log.warning(String.format(
                    "defaults.favorite_slot was set to %d, but it must not be higher than 8. Using default value 8",
                    favoriteSlot));
            plugin.getConfig().set("defaults.favorite_slot", 8);
        }
    }

    private void applyToRuntime() {
        Log.setDebugLevel(DebugLevel.parse(plugin.getConfig().getString("debug_level"), DebugLevel.OFF));
    }

    // -------------------------------------------------------------------------
    // Per-player defaults (seed values for a new PlayerSetting)
    // -------------------------------------------------------------------------

    public boolean getDefaultBestToolsEnabled() {
        return plugin.getConfig().getBoolean("defaults.enabled", false);
    }

    public boolean getDefaultRefillEnabled() {
        return plugin.getConfig().getBoolean("defaults.refill_enabled", false);
    }

    public boolean getDefaultHotbarOnly() {
        return plugin.getConfig().getBoolean("defaults.hotbar_only", true);
    }

    public int getDefaultFavoriteSlot() {
        return plugin.getConfig().getInt("defaults.favorite_slot", 8);
    }

    public boolean getDefaultSwordOnMobs() {
        return plugin.getConfig().getBoolean("defaults.sword_on_mobs", true);
    }

    // -------------------------------------------------------------------------
    // Server-wide policy toggles
    // -------------------------------------------------------------------------

    public boolean getAllowInAdventureMode() {
        return plugin.getConfig().getBoolean("allow_in_adventure_mode", false);
    }

    public boolean getDontSwitchDuringBattle() {
        return plugin.getConfig().getBoolean("dont_switch_during_battle", true);
    }

    public boolean getConsiderSwordsForLeaves() {
        return plugin.getConfig().getBoolean("consider_swords_for_leaves", false);
    }

    public boolean getConsiderSwordsForCobwebs() {
        return plugin.getConfig().getBoolean("consider_swords_for_cobwebs", false);
    }

    public boolean getUseAxeAsSword() {
        return plugin.getConfig().getBoolean("use_axe_as_sword", false);
    }

    public List<String> getGlobalBlockBlacklist() {
        return plugin.getConfig().getStringList("global_block_blacklist");
    }

    // -------------------------------------------------------------------------
    // Update checker
    // -------------------------------------------------------------------------

    /** Tri-state: {@code "true"} (immediate + recurring), {@code "on-startup"}, or anything else (off). */
    public String getCheckForUpdatesMode() {
        return plugin.getConfig().getString("check_for_updates", "true");
    }

    public int getCheckForUpdatesIntervalHours() {
        int hours = plugin.getConfig().getInt("check_for_updates_interval_hours", 4);
        return hours <= 0 ? 4 : hours;
    }

    // -------------------------------------------------------------------------
    // Technical
    // -------------------------------------------------------------------------

    public boolean getDump() {
        return plugin.getConfig().getBoolean("dump", false);
    }

    public boolean getMeasurePerformance() {
        return plugin.getConfig().getBoolean("measure_performance", false);
    }

    public boolean getPuns() {
        return plugin.getConfig().getBoolean("puns", false);
    }
}
