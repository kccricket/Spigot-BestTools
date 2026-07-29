package net.kccricket.bestesttool.config;

import net.kccricket.bestesttool.BestestToolPlugin;
import net.kccricket.kcmclib.text.lang.Localized;

import java.util.Locale;

/**
 * Owns BestestTool's configuration files and provides a single unified lifecycle:
 * {@link #loadAll()} and {@link #reloadAll()}, mirroring ClickSorted's {@code ConfigManager}.
 */
public class ConfigManager {

    private final MainConfig main;
    private final LangConfig lang;

    public ConfigManager(BestestToolPlugin plugin) {
        this.main = new MainConfig(plugin);
        this.lang = new LangConfig(plugin);
    }

    /**
     * Load all config files. Called once from {@code onEnable}. {@code main} loads first — {@code
     * lang}'s load() reads {@code main.getDefaultLocale()}.
     */
    public void loadAll() {
        main.load();
        lang.load();
    }

    /** Reload all config files. Called by {@code /bestesttool reload}. */
    public void reloadAll() {
        main.reload();
        lang.reload();
    }

    /** Persist any in-memory mutations. Called from {@code onDisable}. */
    public void saveAll() {
        main.save();
        lang.save();
    }

    public MainConfig main() {
        return main;
    }

    /** The default-locale-bound message handle — console output and any non-player sender. */
    public Localized lang() {
        return lang.defaultLocale();
    }

    /** The message handle bound to {@code locale} — used for player-facing call sites. */
    public Localized lang(Locale locale) {
        return lang.forLocale(locale);
    }
}
