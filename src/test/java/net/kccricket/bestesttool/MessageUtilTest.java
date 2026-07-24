package net.kccricket.bestesttool;

import net.kccricket.bestesttool.text.MessageUtil;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the lang system end-to-end against BestestTool's actual bundled lang/en_us.yml and a
 * real MockBukkit-loaded plugin — placeholder substitution, and that an on-disk override actually
 * overrides the bundled default (the whole point of the sparse-override lang mechanism).
 */
class MessageUtilTest extends BestToolsTestBase {

    private static String plain(net.kyori.adventure.text.Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    @Test
    void placeholderIsSubstitutedIntoMessage() {
        PlayerMock player = newPlayer();

        String rendered = plain(MessageUtil.get(player, "blacklistAdded", Placeholder.unparsed("items", "STONE, DIRT")));

        assertTrue(rendered.contains("STONE, DIRT"), "Placeholder <items> must be substituted: " + rendered);
        assertTrue(rendered.contains("Added to blacklist"), "Bundled default text must render: " + rendered);
    }

    @Test
    void onDiskOverrideWinsOverBundledDefault() throws IOException {
        PlayerMock player = newPlayer();

        Path overrideFile = plugin.getDataFolder().toPath().resolve("lang").resolve("en_us.yml");
        assertTrue(Files.exists(overrideFile), "the commented lang template must have been written on load");
        Files.writeString(overrideFile, "notAPlayer: \"Custom override text\"\n");

        plugin.configManager.reloadAll();

        String rendered = plain(MessageUtil.get(player, "notAPlayer"));
        assertEquals("Custom override text", rendered);
    }
}
