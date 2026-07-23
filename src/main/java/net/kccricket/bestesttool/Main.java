package net.kccricket.bestesttool;

import net.kccricket.bestesttool.placeholders.BestToolsPlaceholders;
import net.kccricket.bestesttool.update.UpdateChecker;

import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
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

    final int configVersion = 17;

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
    UpdateChecker updateChecker;

    boolean debug=false;
    boolean wtfdebug=false;
    boolean measurePerformance=false;
    PerformanceMeter meter;

    HashMap<UUID,PlayerSetting> playerSettings;
    boolean verbose = true;


    @Override
    public void onEnable() {
        load(false);

        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null){
            new BestToolsPlaceholders(this).register();
        }

    }

    public void debug(String text) {
        if(debug) getLogger().info("[Debug] "+text);
    }
    void wtfdebug(String text) {
        if(wtfdebug) getLogger().info("[D3BUG] "+text);
    }

    public PlayerSetting getPlayerSetting(Player player) {

        if(Objects.requireNonNull(playerSettings,"PlayerSettings must not be null").containsKey(player.getUniqueId())) {
            return playerSettings.get(player.getUniqueId());
        }

        debug("Creating new player setting for "+player.getName());
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

        if (getConfig().getInt("config-version", 0) != configVersion) {
            showOldConfigWarning();
            ConfigUpdater configUpdater = new ConfigUpdater(this);
            configUpdater.updateConfig();
        }

        loadDefaultValues();

        updateChecker = new UpdateChecker(this);
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
        Objects.requireNonNull(getCommand("besttools")).setExecutor(commandBestTools);
        Objects.requireNonNull(getCommand("refill")).setExecutor(commandRefill);

        if(getConfig().getBoolean("dump",false)) {
            try {
                fileUtils.dumpFile(new File(getDataFolder()+File.separator+"dump.csv"));
            } catch (IOException e) {
                getLogger().warning("Could not create dump.csv");
            }
        }

        registerMetrics();

        updateChecker.restart();

    }

    private void registerMetrics() {
        @SuppressWarnings("unused")
        Metrics metrics = new Metrics(this,32836);
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
        debug = getConfig().getBoolean("debug",false);
        wtfdebug = getConfig().getBoolean("wtf-debug", false);
        measurePerformance = getConfig().getBoolean("measure-performance",false);

        if(getConfig().getInt("favorite-slot")>8) {
            getLogger().warning(String.format("favorite-slot was set to %d, but it must not be higher than 8. Using default value 8",getConfig().getInt("favorite-slot")));
            getConfig().set("favorite-slot",8);
        }

    }

    private void showOldConfigWarning() {
        getLogger().warning("==============================================");
        getLogger().warning("You were using an old config file. BestTools");
        getLogger().warning("has updated the file to the newest version.");
        getLogger().warning("Your changes have been kept.");
        getLogger().warning("==============================================");
    }

}
