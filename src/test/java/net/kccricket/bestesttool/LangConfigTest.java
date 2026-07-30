package net.kccricket.bestesttool;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the lang system end-to-end via a real MockBukkit-loaded plugin: placeholder
 * substitution, and that an on-disk override actually overrides the bundled default (the whole
 * point of the sparse-override lang mechanism). Uses a dedicated test-only lang fixture
 * ({@code src/test/resources/lang/test-lang.yml}, installed as the {@code lang/en_us.yml} on-disk
 * override before each test) rather than the real bundled {@code lang/en_us.yml} prose, so these
 * tests don't depend on production wording.
 *
 * <p>Renamed from {@code MessageUtilTest} when {@code text/MessageUtil} was deleted in favor of
 * KcMcLib's {@link net.kccricket.kcmclib.text.Messenger} — these assertions were always about
 * {@code ConfigManager.lang(...)}'s resolution, not {@code MessageUtil} itself, so they now go
 * straight through {@link net.kccricket.kcmclib.text.lang.Localized#render} rather than a deleted
 * plugin-local wrapper.
 */
class LangConfigTest extends BestToolsTestBase {

    /**
     * Installs the test-only lang fixture as the {@code lang/en_us.yml} on-disk override before
     * every test, then reloads so {@code LangConfig} picks it up. The directory/file already exist
     * after {@code setUpBase()}'s initial load (see {@link #onDiskOverrideWinsOverBundledDefault}),
     * so this just overwrites it. {@link #onDiskOverrideWinsOverBundledDefault} overwrites it again
     * itself mid-test, same as before this fixture existed.
     */
    @BeforeEach
    void installTestLangFixture() throws IOException {
        Path overrideFile = plugin.getDataFolder().toPath().resolve("lang").resolve("en_us.yml");
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("lang/test-lang.yml")) {
            assertNotNull(in, "test fixture lang/test-lang.yml must be on the test classpath");
            Files.copy(in, overrideFile, StandardCopyOption.REPLACE_EXISTING);
        }
        plugin.configManager.reloadAll();
    }

    private static String plain(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void placeholderIsSubstitutedIntoMessage() {
        PlayerMock player = newPlayer();

        String rendered = plain(plugin.getConfigManager().lang(player.locale())
                .render("blacklistAdded", Placeholder.unparsed("items", "STONE, DIRT")));

        assertTrue(rendered.contains("STONE, DIRT"), "Placeholder <items> must be substituted: " + rendered);
        assertTrue(rendered.contains("TEST-BLACKLIST-ADDED"), "Fixture override text must render: " + rendered);
    }

    @Test
    void onDiskOverrideWinsOverBundledDefault() throws IOException {
        PlayerMock player = newPlayer();

        Path overrideFile = plugin.getDataFolder().toPath().resolve("lang").resolve("en_us.yml");
        assertTrue(Files.exists(overrideFile), "the commented lang template must have been written on load");
        Files.writeString(overrideFile, "notAPlayer: \"Custom override text\"\n");

        plugin.configManager.reloadAll();

        String rendered = plain(plugin.getConfigManager().lang(player.locale()).render("notAPlayer"));
        assertEquals("Custom override text", rendered);
    }
}
