package net.kccricket.bestesttool.config;

import net.kccricket.bestesttool.Main;
import net.kccricket.kcmclib.config.ManagedConfig;
import net.kccricket.kcmclib.config.ResourceUpdater;
import net.kccricket.kcmclib.logging.DebugLevel;
import net.kccricket.kcmclib.logging.Log;

import java.util.List;
import java.util.Locale;

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
    // Reassigned on reload; read on Folia-async-scheduler threads (e.g. ModrinthUpdateChecker's
    // notice lines), so publish via volatile — mirrors ClickSorted's MainConfig.
    private volatile Locale defaultLocale = Locale.forLanguageTag("en-US");

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
        load();
    }

    /**
     * {@code saveDefaultConfig()} raw-copies the bundled {@code config.yml} to disk byte-for-byte
     * on a fresh install (or after a mid-session delete) — this is what actually preserves prose
     * that isn't attached to any single key (section banners, the Commands/Permissions/
     * Placeholders documentation blocks): those have no key path for
     * {@link ResourceUpdater#copyMissingKeyComments} to hang a comment on, so relying on
     * {@code copyDefaults(true)} alone to synthesize the whole file from scratch on first install
     * silently drops them. The explicit {@code reloadConfig()} after it ensures {@code getConfig()}
     * reflects whatever was just (maybe) written — needed because a prior {@code reloadConfig()}
     * call (e.g. this method being invoked from an admin reload) can otherwise leave {@code
     * getConfig()} pointing at a stale empty in-memory config from before the raw copy happened.
     * {@code copyDefaults(true)} + {@code copyMissingKeyComments} then only ever have real work to
     * do on an upgrade — an existing on-disk file missing a key a newer version added — which is
     * exactly the synthetic-reconstruction case they're designed and tested for.
     */
    private void applyDefaults() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        plugin.getConfig().options().copyDefaults(true);
        ResourceUpdater.copyMissingKeyComments(plugin.getConfig(), plugin.getConfig().getDefaults());
    }

    /**
     * Clamps an out-of-range {@code defaults.favorite_slot} back to the default (8), warning once,
     * and corrects {@code debug_level} if YAML 1.1 parsed the bundled default's bare {@code OFF} as
     * the boolean {@code false} (YAML 1.1 treats {@code OFF}/{@code ON}/{@code YES}/{@code NO} as
     * boolean literals) — otherwise {@code getString("debug_level")} would auto-convert that boolean
     * to the string {@code "false"}, which {@code DebugLevel.parse} rejects. Mirrors ClickSorted's
     * MainConfig fix for the identical bundled-default gotcha.
     */
    private void normalizeValues() {
        int favoriteSlot = plugin.getConfig().getInt("defaults.favorite_slot");
        if (favoriteSlot > 8 || favoriteSlot < -1) {
            Log.warning(String.format(
                    "defaults.favorite_slot was set to %d, but it must be -1, or between 0 and 8. Using default value 8",
                    favoriteSlot));
            plugin.getConfig().set("defaults.favorite_slot", 8);
        }

        if (plugin.getConfig().isBoolean("debug_level") && !plugin.getConfig().getBoolean("debug_level")) {
            plugin.getConfig().set("debug_level", "OFF");
        }
    }

    private void applyToRuntime() {
        Log.setDebugLevel(DebugLevel.parse(plugin.getConfig().getString("debug_level"), DebugLevel.OFF));
        defaultLocale = parseLocaleToken(plugin.getConfig().getString("default_locale", "en_us"));
    }

    /** Parses a lowercase Minecraft-style locale token ({@code lang} or {@code lang_country}), falling back to {@code en_us} on garbage. */
    private static Locale parseLocaleToken(String token) {
        if (token != null) {
            String[] parts = token.trim().toLowerCase(Locale.ROOT).split("_", 2);
            if (parts[0].matches("[a-z]{2,3}")) {
                return parts.length == 2 && !parts[1].isEmpty()
                        ? Locale.of(parts[0], parts[1].toUpperCase(Locale.ROOT))
                        : Locale.of(parts[0]);
            }
        }
        Log.warning("Invalid default_locale '" + token + "' — falling back to en_us");
        return Locale.of("en", "US");
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

    /**
     * The fallback locale ({@code default_locale}) used for console output and for any player
     * locale with no matching lang file. Parsed and cached on load/reload.
     */
    public Locale getDefaultLocale() {
        return defaultLocale;
    }
}
