package net.kccricket.bestesttool;

import net.kccricket.kcmclib.logging.DebugLevel;
import net.kccricket.kcmclib.logging.Log;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies MainConfig's comment-carrying behavior against BestestTool's actual bundled
 * config.yml (not a synthetic fixture) — proving the real deliverable, not just KcMcLib's
 * isolated ResourceUpdater unit test.
 */
class MainConfigTest extends BestToolsTestBase {

    @Test
    void reloadAddsMissingKeyWithItsCommentFromTheBundledDefault() throws IOException {
        // setUpBase() already ran a full onEnable()/load(), so config.yml exists on disk with
        // every key from the bundled default, including its comments.
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        assertTrue(configFile.exists(), "config.yml must exist on disk after the first load");

        // Simulate an old on-disk file predating the "puns" key: remove it (and any trailing
        // blank/comment lines) and reload, as if a user manually edited an old version.
        String content = Files.readString(configFile.toPath());
        int punsCommentIdx = content.indexOf("# Do you like bad puns?");
        assertTrue(punsCommentIdx >= 0, "test fixture assumption: puns' comment must be present pre-edit");
        String edited = content.substring(0, punsCommentIdx);
        Files.writeString(configFile.toPath(), edited);

        plugin.configManager.reloadAll();

        String reloadedContent = Files.readString(configFile.toPath());
        YamlConfiguration reloaded = YamlConfiguration.loadConfiguration(configFile);

        assertEquals(false, reloaded.getBoolean("puns"),
                "Missing key's value must be restored from the bundled default on reload");
        assertEquals(Arrays.asList(null, "Do you like bad puns?"), reloaded.getComments("puns"),
                "Missing key's comment from the bundled default must be carried over on reload");
        assertTrue(reloadedContent.contains("# Do you like bad puns?"),
                "The comment must actually appear in the saved file text, not just be recoverable via the API");
    }

    @Test
    void existingKeysCommentIsNeverOverwrittenOnReload() throws IOException {
        File configFile = new File(plugin.getDataFolder(), "config.yml");

        String edited = Files.readString(configFile.toPath())
                .replace("# Do you like bad puns?", "# my own custom comment");
        Files.writeString(configFile.toPath(), edited);

        plugin.configManager.reloadAll();

        YamlConfiguration reloaded = YamlConfiguration.loadConfiguration(configFile);
        assertEquals(Arrays.asList(null, "my own custom comment"), reloaded.getComments("puns"),
                "A user's own comment on an existing key must survive reload unchanged");
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
}
