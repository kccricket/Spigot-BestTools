package net.kccricket.bestesttool;

import net.kccricket.bestesttool.placeholders.BestToolsPlaceholders;
import net.kccricket.bestesttool.security.Permissions;

import net.kccricket.kcmclib.logging.DebugLevel;
import net.kccricket.kcmclib.logging.Log;
import net.kccricket.kcmclib.update.ModrinthUpdateChecker;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
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
    Messages messages;
    GUIHandler guiHandler;
    ModrinthUpdateChecker updateChecker;

    boolean measurePerformance=false;
    PerformanceMeter meter;

    HashMap<UUID,PlayerSetting> playerSettings;
    boolean verbose = true;


    @Override
    public void onEnable() {
        Log.init(this);

        load(false);

        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null){
            new BestToolsPlaceholders(this).register();
        }

    }

    public PlayerSetting getPlayerSetting(Player player) {

        if(Objects.requireNonNull(playerSettings,"PlayerSettings must not be null").containsKey(player.getUniqueId())) {
            return playerSettings.get(player.getUniqueId());
        }

        Log.debug("Creating new player setting for "+player.getName());
        PlayerSetting setting = new PlayerSetting(player,
                getConfig().getBoolean("besttools-enabled-by-default"),
                getConfig().getBoolean("refill-enabled-by-default"),
                getConfig().getBoolean("hotbar-only"),
                getConfig().getInt("favorite-slot"),
                getConfig().getBoolean("use-sword-on-hostile-mobs"));
        playerSettings.put(player.getUniqueId(),setting);
        return setting;
    }

    void load(boolean reload) {

        getDataFolder().mkdir();
        saveDefaultConfig();

        if(reload) {
            updateChecker.stop();
            HandlerList.unregisterAll(this);
            reloadConfig();

        }

        loadDefaultValues();

        updateChecker = new ModrinthUpdateChecker(
                this,
                "bfE7PKmz",
                () -> getConfig().getString("check-for-updates", "true").equalsIgnoreCase("true"),
                () -> {
                    int hours = getConfig().getInt("check-interval", 4);
                    return hours <= 0 ? 4 : hours;
                },
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
        messages = new Messages(this);
        fileUtils = new FileUtils(this);
        playerSettings = new HashMap<>();
        guiHandler = new GUIHandler(this);

        meter = new PerformanceMeter(this);

        getServer().getPluginManager().registerEvents(refillListener,this);
        getServer().getPluginManager().registerEvents(bestToolsListener,this);
        getServer().getPluginManager().registerEvents(playerListener, this);
        getServer().getPluginManager().registerEvents(bestToolsCacheListener,this);
        getServer().getPluginManager().registerEvents(guiHandler,this);
        registerPermissions();
        registerCommands();

        if(getConfig().getBoolean("dump",false)) {
            try {
                fileUtils.dumpFile(new File(getDataFolder()+File.separator+"dump.csv"));
            } catch (IOException e) {
                getLogger().warning("Could not create dump.csv");
            }
        }

        registerMetrics();

        // "check-for-updates" is tri-state (true / on-startup / anything else = off), but
        // ModrinthUpdateChecker's `enabled` supplier only drives one binary gate shared by both the
        // immediate check and the recurring schedule. Preserve the tri-state in this wiring instead:
        // "on-startup" always fires exactly one check and never arms a recurring task; "true" gets
        // both (via restart()); anything else fires neither (restart() -> reschedule() -> stop() still
        // cancels a previously-armed recurring task if the setting was just switched off/changed).
        String updateCheckMode = getConfig().getString("check-for-updates", "true");
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

    /**
     * Registers the {@code /besttools} and {@code /refill} commands directly against the
     * server's command map. {@code paper-plugin.yml} cannot declare a {@code commands:} block
     * (unlike the legacy {@code plugin.yml}), so this replaces the old
     * {@code getCommand(name).setExecutor(...)} wiring. Safe to call repeatedly (e.g. on
     * {@code /besttools reload}) — re-registering just overwrites the previous mapping.
     */
    private void registerCommands() {
        CommandMap commandMap = getServer().getCommandMap();

        String besttoolsUsage = """
                /<command> -- Toggle automatically using the best tool
                /<command> hotbar -- Toggle whether to only use tools from your hotbar
                /<command> reload -- Reloads the config file
                /<command> debug -- Toggle debug mode
                /<command> performance -- Toggle performance test

                /<command> bl -- Show your blacklist
                /<command> bl add -- Adds your currently held item to your blacklist
                /<command> bl add inventory -- Adds all items from your inventory to your blacklist
                /<command> bl add hotbar -- Adds all items from your hotbar to your blacklist
                /<command> bl add <items...> -- Add specified items to your blacklist
                /<command> bl remove -- Removes your currently held item from your blacklist
                /<command> bl remove inventory -- Removes all items from your inventory from your blacklist
                /<command> bl remove hotbar -- Removes all items from your hotbar from your blacklist
                /<command> bl remove <items...> -- Remove items from your blacklist
                /<command> bl reset -- Removes all items from your blacklist""";

        commandMap.register(getName().toLowerCase(), new DelegatingCommand(
                "besttools", commandBestTools, "Toggle BestTools", besttoolsUsage,
                List.of("bt", "besttool")));

        commandMap.register(getName().toLowerCase(), new DelegatingCommand(
                "refill", commandRefill, "Toggle Refill", "/<command> -- Toggle automatically refilling your hotbar",
                List.of("rf")));
    }

    /**
     * Registers the {@code bestesttool.*} permissions, mirroring what the old {@code plugin.yml}
     * {@code permissions:} block declared. {@code paper-plugin.yml} cannot declare permissions, so
     * this is done in code instead. Guarded against re-registration so {@code /besttools reload}
     * (which re-runs {@link #load}) doesn't throw. No {@code besttools.*} legacy alias — BestestTool
     * is a fresh re-release with no backward compatibility to preserve.
     */
    private void registerPermissions() {
        registerPermission(Permissions.PERM_USE, "Allows using /besttools");
        registerPermission(Permissions.PERM_REFILL, "Allows using /refill");
        registerPermission(Permissions.PERM_RELOAD, "Allows to reload the config via /besttools reload");
        registerPermission(Permissions.PERM_DEBUG, "Allows to enable the debug mode via /besttools debug and the performance test via /besttools performance");
    }

    private void registerPermission(String name, String description) {
        if (getServer().getPluginManager().getPermission(name) != null) return;
        getServer().getPluginManager().addPermission(new Permission(name, description, PermissionDefault.OP));
    }

    private void loadDefaultValues() {
        getConfig().addDefault("besttools-enabled-by-default",false);
        getConfig().addDefault("refill-enabled-by-default",false);
        getConfig().addDefault("hotbar-only", true);
        getConfig().addDefault("favorite-slot",8);
        getConfig().addDefault("check-interval",4);
        getConfig().addDefault("check-for-updates","true");
        getConfig().addDefault("allow-in-adventure-mode",false);
        getConfig().addDefault("dont-switch-during-battle",true);
        getConfig().addDefault("puns",false);
        getConfig().addDefault("use-sword-on-hostile-mobs",true);
        getConfig().addDefault("use-axe-as-sword",false);

        verbose = getConfig().getBoolean("verbose",true);
        boolean debugFlag = getConfig().getBoolean("debug",false);
        boolean wtfDebugFlag = getConfig().getBoolean("wtf-debug", false);
        Log.setDebugLevel(wtfDebugFlag ? DebugLevel.TRACE : (debugFlag ? DebugLevel.DEBUG : DebugLevel.OFF));
        measurePerformance = getConfig().getBoolean("measure-performance",false);

        if(getConfig().getInt("favorite-slot")>8) {
            getLogger().warning(String.format("favorite-slot was set to %d, but it must not be higher than 8. Using default value 8",getConfig().getInt("favorite-slot")));
            getConfig().set("favorite-slot",8);
        }

    }

}
