package net.kccricket.bestesttool;

import net.kccricket.kcmclib.logging.DebugLevel;
import net.kccricket.kcmclib.logging.Log;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies MainConfig's comment-carrying <em>wiring</em> (saveDefaultConfig + reloadConfig +
 * copyDefaults + {@code ResourceUpdater.copyMissingKeyComments}) against the real bundled
 * config.yml. The comment-carrying mechanism itself is already covered generically, against
 * synthetic fixtures, by KcMcLib's {@code ResourceUpdaterTest} — these tests deliberately never
 * hardcode config.yml's actual prose, reading whatever comment is currently on disk instead, so
 * they verify the wiring still works without re-asserting that the resource's wording hasn't
 * changed.
 */
class MainConfigTest extends BestToolsTestBase {

    @Test
    void reloadAddsMissingKeyWithItsCommentFromTheBundledDefault() throws IOException {
        // setUpBase() already ran a full onEnable()/load(), so config.yml exists on disk with
        // every key from the bundled default, including its comments.
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        assertTrue(configFile.exists(), "config.yml must exist on disk after the first load");

        YamlConfiguration before = YamlConfiguration.loadConfiguration(configFile);
        List<String> originalComments = before.getComments("enable_benchmark");
        String firstCommentLine = originalComments.stream().filter(java.util.Objects::nonNull).findFirst()
                .orElseThrow(() -> new AssertionError(
                        "test fixture assumption: enable_benchmark must carry a comment in the bundled default"));
        boolean originalValue = before.getBoolean("enable_benchmark");

        // Simulate an old on-disk file predating the "enable_benchmark" key: truncate the file
        // right before its comment block, as if a user's file was written before this key existed.
        String content = Files.readString(configFile.toPath());
        int commentIdx = content.indexOf("# " + firstCommentLine);
        assertTrue(commentIdx >= 0, "test fixture assumption: enable_benchmark's comment must be present pre-edit");
        Files.writeString(configFile.toPath(), content.substring(0, commentIdx));

        plugin.configManager.reloadAll();

        String reloadedContent = Files.readString(configFile.toPath());
        YamlConfiguration reloaded = YamlConfiguration.loadConfiguration(configFile);

        assertEquals(originalValue, reloaded.getBoolean("enable_benchmark"),
                "Missing key's value must be restored from the bundled default on reload");
        assertEquals(originalComments, reloaded.getComments("enable_benchmark"),
                "Missing key's comment from the bundled default must be carried over on reload");
        assertTrue(reloadedContent.contains("# " + firstCommentLine),
                "The comment must actually appear in the saved file text, not just be recoverable via the API");
    }

    @Test
    void existingKeysCommentIsNeverOverwrittenOnReload() throws IOException {
        File configFile = new File(plugin.getDataFolder(), "config.yml");

        YamlConfiguration before = YamlConfiguration.loadConfiguration(configFile);
        List<String> originalComments = before.getComments("enable_benchmark");
        String lastCommentLine = originalComments.get(originalComments.size() - 1);

        String edited = Files.readString(configFile.toPath())
                .replace("# " + lastCommentLine, "# my own custom comment");
        Files.writeString(configFile.toPath(), edited);

        plugin.configManager.reloadAll();

        List<String> expected = new ArrayList<>(originalComments);
        expected.set(expected.size() - 1, "my own custom comment");
        YamlConfiguration reloaded = YamlConfiguration.loadConfiguration(configFile);
        assertEquals(expected, reloaded.getComments("enable_benchmark"),
                "A user's own comment on an existing key must survive reload unchanged");
    }

    /**
     * Regression test: {@code normalizeValues} used to clamp only {@code favoriteSlot > 8},
     * leaving a value below {@code -1} to flow straight through to
     * {@code inv.setHeldItemSlot(-5)} and throw {@code IllegalArgumentException} on every
     * mining interaction. {@code -1} itself is the documented "use the player's held slot"
     * sentinel (see {@code config.yml}'s comment and {@code PlayerSetting#getFavoriteSlot}) and
     * must survive untouched.
     */
    @Test
    void tooLowFavoriteSlotIsClampedToDefault() throws IOException {
        setFavoriteSlotOnDisk(-5);
        plugin.configManager.reloadAll();
        assertEquals(8, plugin.configManager.main().getDefaultFavoriteSlot(),
                "A favorite_slot below -1 must be clamped back to the default (8)");
    }

    @Test
    void tooHighFavoriteSlotIsClampedToDefault() throws IOException {
        setFavoriteSlotOnDisk(9);
        plugin.configManager.reloadAll();
        assertEquals(8, plugin.configManager.main().getDefaultFavoriteSlot(),
                "A favorite_slot above 8 must be clamped back to the default (8)");
    }

    @Test
    void negativeOneFavoriteSlotSentinelIsPreserved() throws IOException {
        setFavoriteSlotOnDisk(-1);
        plugin.configManager.reloadAll();
        assertEquals(-1, plugin.configManager.main().getDefaultFavoriteSlot(),
                "-1 is a valid sentinel (\"use the player's held slot\") and must not be clamped");
    }

    /** Rewrites the on-disk {@code defaults.favorite_slot} value, as if an admin had edited it. */
    private void setFavoriteSlotOnDisk(int value) throws IOException {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        String edited = Files.readString(configFile.toPath())
                .replace("favorite_slot: 8", "favorite_slot: " + value);
        Files.writeString(configFile.toPath(), edited);
    }

    /**
     * Regression test: YAML 1.1 parses the bundled default's bare {@code debug_level: OFF} as the
     * boolean {@code false}, which {@code getString("debug_level")} then auto-converts to the
     * string {@code "false"} — not a valid {@link DebugLevel} name. Caught by manually reading a
     * runServer console log (MockBukkit tests don't fail on a logged warning), fixed in
     * MainConfig#normalizeValues. A fresh load must resolve to OFF with no warning, and the on-disk
     * value must be corrected to a real string so it round-trips cleanly from then on.
     */
    @Test
    void bareOffDebugLevelParsesCorrectlyNotAsBooleanFalse() throws IOException {
        assertEquals(DebugLevel.OFF, Log.getDebugLevel(),
                "debug_level: OFF (bundled default) must resolve to DebugLevel.OFF, not fall back from a parse failure");

        File configFile = new File(plugin.getDataFolder(), "config.yml");
        YamlConfiguration onDisk = YamlConfiguration.loadConfiguration(configFile);
        assertTrue(onDisk.isString("debug_level"),
                "debug_level must be normalized to a real string on disk, not left as the YAML-1.1-parsed boolean false");
        assertEquals("OFF", onDisk.getString("debug_level"));
    }

    /**
     * Regression test: an on-disk key not present in the bundled default (a leftover from an old
     * config scheme, or a plain typo) used to be silently ignored — {@code copyDefaults(true)}
     * never removes or flags it. {@code MainConfig#warnUnknownKeys} now names it in a console
     * warning on every load/reload.
     */
    @Test
    void reloadWarnsAboutAnUnrecognizedKey() throws IOException {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        String edited = Files.readString(configFile.toPath()) + "\nmessage-blacklist-add: some stale text\n";
        Files.writeString(configFile.toPath(), edited);

        List<String> warnings = captureWarnings(() -> plugin.configManager.reloadAll());

        assertTrue(warnings.stream().anyMatch(w -> w.contains("message-blacklist-add")),
                "An unrecognized key must be named in a warning");
    }

    @Test
    void reloadDoesNotWarnWhenConfigHasNoUnrecognizedKeys() {
        List<String> warnings = captureWarnings(() -> plugin.configManager.reloadAll());

        assertTrue(warnings.isEmpty(), "A clean config.yml must not produce an unknown-key warning: " + warnings);
    }

    /** Runs {@code action}, returning every message logged at WARNING or above by the plugin logger. */
    private List<String> captureWarnings(Runnable action) {
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        plugin.getLogger().addHandler(handler);
        try {
            action.run();
        } finally {
            plugin.getLogger().removeHandler(handler);
        }
        return messages;
    }
}
