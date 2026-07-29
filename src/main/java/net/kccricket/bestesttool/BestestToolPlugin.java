package net.kccricket.bestesttool;

import com.mojang.brigadier.tree.LiteralCommandNode;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import net.kccricket.bestesttool.config.ConfigManager;
import net.kccricket.bestesttool.placeholders.BestToolsPlaceholders;
import net.kccricket.bestesttool.text.MessageUtil;

import net.kccricket.kcmclib.logging.Log;
import net.kccricket.kcmclib.update.ModrinthUpdateChecker;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kccricket.bestesttool.benchmark.BenchmarkManager;
import net.kccricket.bestesttool.commands.BestToolsCommands;
import net.kccricket.bestesttool.commands.CommandBenchmark;
import net.kccricket.bestesttool.commands.CommandBestTools;
import net.kccricket.bestesttool.commands.CommandBlacklist;
import net.kccricket.bestesttool.commands.CommandRefill;
import net.kccricket.bestesttool.commands.CommandSelfTest;
import net.kccricket.bestesttool.listeners.BestToolsCacheListener;
import net.kccricket.bestesttool.listeners.BestToolsListener;
import net.kccricket.bestesttool.listeners.PlayerListener;
import net.kccricket.bestesttool.listeners.RefillListener;
import net.kccricket.bestesttool.model.PlayerSetting;
import net.kccricket.bestesttool.refill.RefillUtils;
import net.kccricket.bestesttool.selftest.SelfTestManager;
import net.kccricket.bestesttool.selftest.SelfTestSession;
import net.kccricket.bestesttool.tool.BestToolsHandler;
import net.kccricket.bestesttool.tool.BestToolsUtils;
import net.kccricket.bestesttool.util.FileUtils;

public class BestestToolPlugin extends JavaPlugin {

    private static BestestToolPlugin instance;

    public static BestestToolPlugin getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    private Metrics metrics;

    public ConfigManager configManager;
    public BestToolsHandler toolHandler;
    public BestToolsUtils toolUtils;
    public RefillListener refillListener;
    public BestToolsListener bestToolsListener;
    public PlayerListener playerListener;
    public BestToolsCacheListener bestToolsCacheListener;
    public FileUtils fileUtils;
    public RefillUtils refillUtils;
    public CommandBestTools commandBestTools;
    public CommandRefill commandRefill;
    public CommandBlacklist commandBlacklist;
    public CommandSelfTest commandSelfTest;
    public CommandBenchmark commandBenchmark;
    public ModrinthUpdateChecker updateChecker;
    public SelfTestManager selfTestManager;
    public BenchmarkManager benchmarkManager;

    // ConcurrentHashMap: mutated from per-region Folia threads (see getPlayerSetting and
    // PlayerListener.onPlayerQuit) with no external synchronization, same hazard already guarded
    // against by BestToolsHandler's silkMattersCache.
    public final Map<UUID,PlayerSetting> playerSettings = new ConcurrentHashMap<>();

    /**
     * Constructs every service and listener exactly once, mirroring ClickSorted's
     * {@code ClickSortedPlugin.onEnable}. {@code /bestesttool admin reload} ({@link #reload}) only
     * re-reads config and re-derives config-dependent state — it does not rebuild or
     * re-register anything here, so an in-progress {@code /bestesttool admin selftest} run and a
     * live {@code /bestesttool admin benchmark} task both survive a reload without special-casing.
     */
    @Override
    public void onEnable() {
        instance = this;
        Log.init(this);

        configManager = new ConfigManager(this);
        configManager.loadAll();
        MessageUtil.init(configManager);

        if (configManager.main().getEnableMetrics()) {
            metrics = new Metrics(this, 32836);
        }

        updateChecker = new ModrinthUpdateChecker(
                this,
                "bfE7PKmz",
                () -> configManager.main().getCheckForUpdatesMode().equalsIgnoreCase("true"),
                () -> configManager.main().getCheckForUpdatesIntervalHours(),
                (latestVersion, currentVersion) -> List.of(
                        "A new version of BestestTool is available: " + latestVersion
                                + " (you are running " + currentVersion + ").",
                        "Download: https://modrinth.com/plugin/bfE7PKmz | "
                                + "https://hangar.papermc.io/kccricket/BestestTool | "
                                + "https://github.com/kccricket/Spigot-BestTools/releases"));

        toolHandler = new BestToolsHandler(this);
        toolUtils = new BestToolsUtils(this);
        refillListener = new RefillListener(this);
        bestToolsListener = new BestToolsListener(this);
        playerListener = new PlayerListener(this);
        bestToolsCacheListener = new BestToolsCacheListener(this);
        commandBestTools = new CommandBestTools(this);
        commandRefill = new CommandRefill(this);
        commandBlacklist = new CommandBlacklist(this);
        commandSelfTest = new CommandSelfTest(this);
        commandBenchmark = new CommandBenchmark(this);
        refillUtils = new RefillUtils(this);
        fileUtils = new FileUtils(this);
        selfTestManager = new SelfTestManager(this);
        benchmarkManager = new BenchmarkManager(this);

        getServer().getPluginManager().registerEvents(refillListener, this);
        getServer().getPluginManager().registerEvents(bestToolsListener, this);
        getServer().getPluginManager().registerEvents(playerListener, this);
        getServer().getPluginManager().registerEvents(bestToolsCacheListener, this);
        getServer().getPluginManager().registerEvents(selfTestManager.listener(), this);

        dumpIfConfigured();
        restartUpdateChecker();

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new BestToolsPlaceholders(this).register();
        }

        registerCommands();
    }

    /**
     * Mirrors {@code ClickSortedPlugin.onDisable}: undoes {@link #onEnable} in reverse, each step
     * null-guarded. Also restores every in-progress {@code /bestesttool admin selftest} run (if
     * any) before the server goes down, so a tester doesn't lose their real inventory to an
     * interrupted test — there is no matching {@code onEnable} counterpart for this beyond what
     * {@link SelfTestSession}'s own on-disk backup file (restored on the tester's next
     * {@code PlayerJoinEvent}) already covers — and stops any in-progress
     * {@code /bestesttool admin benchmark} run, so its {@code GlobalRegionScheduler} task doesn't
     * keep firing against a disabled plugin.
     */
    @Override
    public void onDisable() {
        if (updateChecker != null) {
            updateChecker.stop();
        }
        if (selfTestManager != null) {
            selfTestManager.abortAll("disable");
        }
        if (benchmarkManager != null) {
            benchmarkManager.abortAll("disable");
        }
        if (metrics != null) {
            metrics.shutdown();
        }
        if (configManager != null) {
            configManager.saveAll();
            for (PlayerSetting setting : playerSettings.values()) {
                setting.save();
            }
        }
        MessageUtil.init(null);
        instance = null;
    }

    /**
     * Registers the {@code /bestesttool} Brigadier command tree (alias {@code bt}) and the
     * {@code /refill}/{@code /rf} alias that shares its {@code refill} child's command — see
     * {@link BestToolsCommands}. Registered exactly once, from {@link #onEnable}.
     */
    private void registerCommands() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            LiteralCommandNode<CommandSourceStack> root = BestToolsCommands.buildBestTools(this);
            event.registrar().register(root,
                    "Toggle automatically using the best tool", List.of("bt"));
            event.registrar().register(BestToolsCommands.buildRefillAlias(this, root),
                    "Toggle automatically refilling your hotbar", List.of("rf"));
        });
    }

    public PlayerSetting getPlayerSetting(Player player) {
        return playerSettings.computeIfAbsent(player.getUniqueId(), id -> {
            Log.debug("Creating new player setting for "+player.getName());
            return new PlayerSetting(player,
                    configManager.main().getDefaultBestToolsEnabled(),
                    configManager.main().getDefaultRefillEnabled(),
                    configManager.main().getDefaultHotbarOnly(),
                    configManager.main().getDefaultFavoriteSlot(),
                    configManager.main().getDefaultSwordOnMobs());
        });
    }

    /**
     * Re-reads config and re-derives config-dependent state for {@code /bestesttool admin reload}.
     * Unlike the old {@code load(true)}, this does not rebuild or re-register any service or
     * listener — see the {@link #onEnable} javadoc for why that split matters.
     */
    public void reload() {
        configManager.reloadAll();
        MessageUtil.init(configManager);
        selfTestManager.reloadSpec();
        dumpIfConfigured();
        restartUpdateChecker();
    }

    private void dumpIfConfigured() {
        if (configManager.main().getDump()) {
            try {
                fileUtils.dumpFile(new File(getDataFolder() + File.separator + "dump.csv"));
            } catch (IOException e) {
                Log.warning("Could not create dump.csv");
            }
        }
    }

    /**
     * "check_for_updates" is tri-state (true / on-startup / anything else = off), but
     * {@link ModrinthUpdateChecker}'s {@code enabled} supplier only drives one binary gate shared
     * by both the immediate check and the recurring schedule. Preserve the tri-state in this wiring
     * instead: "on-startup" always fires exactly one check and never arms a recurring task; "true"
     * gets both (via {@code restart()}); anything else fires neither ({@code restart()} ->
     * {@code reschedule()} -> {@code stop()} still cancels a previously-armed recurring task if the
     * setting was just switched off/changed). Called from both {@link #onEnable} and
     * {@link #reload} — {@code updateChecker} itself is constructed once and never rebuilt, so this
     * is the only re-evaluation its tri-state semantics need on reload.
     */
    private void restartUpdateChecker() {
        String updateCheckMode = configManager.main().getCheckForUpdatesMode();
        if (updateCheckMode.equalsIgnoreCase("on-startup")) {
            updateChecker.stop();
            updateChecker.check();
        } else {
            updateChecker.restart();
        }
    }

}
