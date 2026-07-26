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
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Main extends JavaPlugin {

    {
        instance = this;
    }

    private static Main instance;

    public static Main getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    ConfigManager configManager;
    BestToolsHandler toolHandler;
    BestToolsUtils toolUtils;
    RefillListener refillListener;
    BestToolsListener bestToolsListener;
    PlayerListener playerListener;
    BestToolsCacheListener bestToolsCacheListener;
    FileUtils fileUtils;
    RefillUtils refillUtils;
    CommandBestTools commandBestTools;
    CommandRefill commandRefill;
    CommandBlacklist commandBlacklist;
    GUIHandler guiHandler;
    ModrinthUpdateChecker updateChecker;

    boolean measurePerformance=false;
    PerformanceMeter meter;

    HashMap<UUID,PlayerSetting> playerSettings;


    @Override
    public void onEnable() {
        Log.init(this);

        load(false);

        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null){
            new BestToolsPlaceholders(this).register();
        }

        registerCommands();
    }

    /**
     * Registers the {@code /bestesttool} Brigadier command tree (alias {@code bt}) and the
     * {@code /refill}/{@code /rf} alias that shares its {@code refill} child's command — see
     * {@link BestToolsCommands}. Registered exactly once here, not in {@link #load}: unlike the
     * old {@code DelegatingCommand}-based registration (which had to re-run on every
     * {@code /bestesttool reload} because {@code paper-plugin.yml} can't declare a
     * {@code commands:} block), the Brigadier tree's {@code executes} bodies dereference
     * {@code main.command*} fields lazily, so it keeps working across reloads without being
     * rebuilt.
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

        if(Objects.requireNonNull(playerSettings,"PlayerSettings must not be null").containsKey(player.getUniqueId())) {
            return playerSettings.get(player.getUniqueId());
        }

        Log.debug("Creating new player setting for "+player.getName());
        PlayerSetting setting = new PlayerSetting(player,
                configManager.main().getDefaultBestToolsEnabled(),
                configManager.main().getDefaultRefillEnabled(),
                configManager.main().getDefaultHotbarOnly(),
                configManager.main().getDefaultFavoriteSlot(),
                configManager.main().getDefaultSwordOnMobs());
        playerSettings.put(player.getUniqueId(),setting);
        return setting;
    }

    void load(boolean reload) {

        if(reload) {
            updateChecker.stop();
            HandlerList.unregisterAll(this);
            configManager.reloadAll();
        } else {
            configManager = new ConfigManager(this);
            configManager.loadAll();
        }
        MessageUtil.init(configManager);

        measurePerformance = configManager.main().getMeasurePerformance();

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
        bestToolsCacheListener = new BestToolsCacheListener((this));
        commandBestTools = new CommandBestTools(this);
        commandRefill = new CommandRefill(this);
        commandBlacklist = new CommandBlacklist(this);
        refillUtils = new RefillUtils((this));
        fileUtils = new FileUtils(this);
        playerSettings = new HashMap<>();
        guiHandler = new GUIHandler(this);

        meter = new PerformanceMeter(this);

        getServer().getPluginManager().registerEvents(refillListener,this);
        getServer().getPluginManager().registerEvents(bestToolsListener,this);
        getServer().getPluginManager().registerEvents(playerListener, this);
        getServer().getPluginManager().registerEvents(bestToolsCacheListener,this);
        getServer().getPluginManager().registerEvents(guiHandler,this);

        if(configManager.main().getDump()) {
            try {
                fileUtils.dumpFile(new File(getDataFolder()+File.separator+"dump.csv"));
            } catch (IOException e) {
                getLogger().warning("Could not create dump.csv");
            }
        }

        registerMetrics();

        // "check_for_updates" is tri-state (true / on-startup / anything else = off), but
        // ModrinthUpdateChecker's `enabled` supplier only drives one binary gate shared by both the
        // immediate check and the recurring schedule. Preserve the tri-state in this wiring instead:
        // "on-startup" always fires exactly one check and never arms a recurring task; "true" gets
        // both (via restart()); anything else fires neither (restart() -> reschedule() -> stop() still
        // cancels a previously-armed recurring task if the setting was just switched off/changed).
        String updateCheckMode = configManager.main().getCheckForUpdatesMode();
        if (updateCheckMode.equalsIgnoreCase("on-startup")) {
            updateChecker.stop();
            updateChecker.check();
        } else {
            updateChecker.restart();
        }

    }

    private void registerMetrics() {
        @SuppressWarnings("unused")
        Metrics metrics = new Metrics(this,32836);
    }

}
