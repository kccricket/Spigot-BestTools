package net.kccricket.bestesttool.config;

import net.kccricket.bestesttool.Main;

/**
 * Owns BestestTool's configuration file(s) and provides a single unified lifecycle:
 * {@link #loadAll()} and {@link #reloadAll()}, mirroring ClickSorted's {@code ConfigManager}.
 */
public class ConfigManager {

    private final MainConfig main;

    public ConfigManager(Main plugin) {
        this.main = new MainConfig(plugin);
    }

    /** Load the config file. Called once from {@code onEnable}. */
    public void loadAll() {
        main.load();
    }

    /** Reload the config file. Called by {@code /besttools reload}. */
    public void reloadAll() {
        main.reload();
    }

    public MainConfig main() {
        return main;
    }
}
