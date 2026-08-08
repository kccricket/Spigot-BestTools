package net.kccricket.bestesttool.config;

import net.kccricket.bestesttool.BestestToolPlugin;
import net.kccricket.bestesttool.model.PlayerDefaults;
import net.kccricket.kcmclib.config.ManagedConfig;
import net.kccricket.kcmclib.config.ResourceUpdater;
import net.kccricket.kcmclib.logging.DebugLevel;
import net.kccricket.kcmclib.logging.Log;
import net.kccricket.kcmclib.update.UpdateCheckMode;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import org.bukkit.Material;

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

    private final BestestToolPlugin plugin;
    // Reassigned on reload; read on Folia region threads (e.g. every block break/attack, now that
    // BestToolsHandler/BestToolsListener are constructed once in onEnable rather than rebuilt per
    // reload — see BestestToolPlugin.reload()), so published via volatile, mirroring ClickSorted's
    // MainConfig. defaultLocale is additionally read on Folia-async-scheduler threads (e.g.
    // ModrinthUpdateChecker's notice lines).
    private volatile Locale defaultLocale = Locale.forLanguageTag("en-US");
    // allow_combat_switching is read on every EntityDamageByEntityEvent (region thread) and inside
    // a Brigadier .requires predicate (main thread, on command-tree sync) — same hot-path shape as
    // defaultLocale/globalBlockBlacklist below, so it gets the same volatile-cache treatment. The
    // four sword/battle toggles that used to live here moved to PlayerSetting (read once per
    // player, not per event) when they became per-player preferences.
    private volatile boolean allowCombatSwitching = true;
    // Read on the same two hot paths as allowCombatSwitching above (every block-interact decision
    // and every EntityDamageByEntityEvent), so it gets the same volatile-cache treatment rather
    // than a live per-call read like getAllowInAdventureMode.
    private volatile boolean allowAvoidBreakingTools = true;
    private volatile Set<Material> globalBlockBlacklist = Set.of();

    public MainConfig(BestestToolPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String fileName() {
        return "config.yml";
    }

    @Override
    public void load() {
        applyDefaults();
        warnUnknownKeys();
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
     * Warns (once, in a single line) about any on-disk {@code config.yml} key not present in the
     * bundled default — e.g. a leftover key from an old config scheme, or a plain typo. Neither
     * {@code copyDefaults(true)} nor {@code copyMissingKeyComments} ever removes or flags an
     * unrecognized key, so without this an admin gets no signal that a key they set is being
     * silently ignored.
     */
    private void warnUnknownKeys() {
        Set<String> onDisk = new TreeSet<>(plugin.getConfig().getKeys(true));
        onDisk.removeAll(plugin.getConfig().getDefaults().getKeys(true));
        if (!onDisk.isEmpty()) {
            Log.warning("Unknown key(s) in config.yml — these are ignored: " + String.join(", ", onDisk));
        }
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
        allowCombatSwitching = plugin.getConfig().getBoolean("allow_combat_switching", true);
        allowAvoidBreakingTools = plugin.getConfig().getBoolean("allow_avoid_breaking_tools", true);
        globalBlockBlacklist = parseGlobalBlockBlacklist();
    }

    /** Unknown material name -> warn and skip, mirroring ClickSorted's blacklist-parsing policy. */
    private Set<Material> parseGlobalBlockBlacklist() {
        Set<Material> mats = new HashSet<>();
        for (String name : plugin.getConfig().getStringList("global_block_blacklist")) {
            Material mat = Material.getMaterial(name.toUpperCase(Locale.ROOT));
            if (mat == null) {
                Log.warning("Unknown material on global_block_blacklist: '" + name + "' — skipping");
                continue;
            }
            mats.add(mat);
        }
        return mats;
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

    public boolean getDefaultUseAxeAsSword() {
        return plugin.getConfig().getBoolean("defaults.use_axe_as_sword", false);
    }

    /**
     * Bundled default for {@code defaults.switch_during_battle} — a pure rename+invert of the old
     * {@code dont_switch_during_battle: true}, so {@code false} here reproduces prior behavior
     * exactly. Named so the default is a single one-line change; kept in sync with the bundled
     * {@code config.yml} by {@code MainConfigTest#bundledSwitchDuringBattleMatchesConstant}.
     */
    public static final boolean DEFAULT_SWITCH_DURING_BATTLE = false;

    public boolean getDefaultSwitchDuringBattle() {
        return plugin.getConfig().getBoolean("defaults.switch_during_battle", DEFAULT_SWITCH_DURING_BATTLE);
    }

    public boolean getDefaultConsiderSwordsForLeaves() {
        return plugin.getConfig().getBoolean("defaults.consider_swords_for_leaves", false);
    }

    public boolean getDefaultConsiderSwordsForCobwebs() {
        return plugin.getConfig().getBoolean("defaults.consider_swords_for_cobwebs", false);
    }

    public boolean getDefaultAvoidBreakingTools() {
        return plugin.getConfig().getBoolean("defaults.avoid_breaking_tools", true);
    }

    /** Assembles every per-player preference default in one read, for {@link net.kccricket.bestesttool.model.PlayerSetting}'s constructor. */
    public PlayerDefaults playerDefaults() {
        return new PlayerDefaults(
                getDefaultBestToolsEnabled(), getDefaultRefillEnabled(), getDefaultHotbarOnly(),
                getDefaultFavoriteSlot(), getDefaultSwordOnMobs(), getDefaultUseAxeAsSword(),
                getDefaultSwitchDuringBattle(), getDefaultConsiderSwordsForLeaves(),
                getDefaultConsiderSwordsForCobwebs(), getDefaultAvoidBreakingTools());
    }

    // -------------------------------------------------------------------------
    // Server-wide policy toggles
    // -------------------------------------------------------------------------

    public boolean getAllowInAdventureMode() {
        return plugin.getConfig().getBoolean("allow_in_adventure_mode", false);
    }

    /**
     * Master switch for BestTools' combat features (weapon-switching on attack). Read on every
     * {@code EntityDamageByEntityEvent} and inside the {@code /bestesttool combat} command node's
     * {@code .requires}, so it's cached like {@link #getDefaultLocale()}/{@link #getGlobalBlockBlacklist()}
     * rather than read live like the one-shot per-player defaults above.
     */
    public boolean getAllowCombatSwitching() {
        return allowCombatSwitching;
    }

    /**
     * Server-wide kill switch for {@code avoid_breaking_tools} (mining and combat selection
     * alike) — no permission pairing, unlike {@link #getAllowCombatSwitching()}: it's a pure
     * on/off, not a feature gate. Read on the same hot paths as combat switching, so it's cached
     * the same way.
     */
    public boolean getAllowAvoidBreakingTools() {
        return allowAvoidBreakingTools;
    }

    /** Parsed once per load/reload; an unrecognized material name is warned about and skipped. */
    public Set<Material> getGlobalBlockBlacklist() {
        return globalBlockBlacklist;
    }

    // -------------------------------------------------------------------------
    // Update checker
    // -------------------------------------------------------------------------

    /** Tri-state: {@code "true"} (immediate + recurring), {@code "on-startup"}, or anything else (off). */
    public UpdateCheckMode getCheckForUpdatesMode() {
        return UpdateCheckMode.parse(plugin.getConfig().getString("check_for_updates", "true"));
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

    public boolean getEnableMetrics() {
        return plugin.getConfig().getBoolean("enable_metrics", true);
    }

    /**
     * Minimum milliseconds between a player's successive actions ({@code action_cooldown_ms}). A
     * value &le; 0 disables the {@link net.kccricket.bestesttool.security.ActionThrottle}.
     */
    public int getActionCooldownMs() {
        return plugin.getConfig().getInt("action_cooldown_ms", 150);
    }

    /**
     * Master switch for {@code /bestesttool admin selftest} — off by default even for an op, since
     * the self-test forces the tester into survival, wipes their held/inventory state, and rebuilds
     * terrain around them. An admin has to opt in on top of holding {@code bestesttool.admin.selftest}.
     */
    public boolean getEnableSelfTest() {
        return plugin.getConfig().getBoolean("enable_selftest", false);
    }

    /**
     * Master switch for {@code /bestesttool admin benchmark} — off by default even for an op, since
     * a run deliberately ramps synthetic work until a tick blows its 50 ms budget. An admin has to
     * opt in on top of holding {@code bestesttool.admin.benchmark}.
     */
    public boolean getEnableBenchmark() {
        return plugin.getConfig().getBoolean("enable_benchmark", false);
    }

    /**
     * The fallback locale ({@code default_locale}) used for console output and for any player
     * locale with no matching lang file. Parsed and cached on load/reload.
     */
    public Locale getDefaultLocale() {
        return defaultLocale;
    }
}
