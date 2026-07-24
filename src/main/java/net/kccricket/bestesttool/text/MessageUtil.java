package net.kccricket.bestesttool.text;

import net.kccricket.bestesttool.config.ConfigManager;
import net.kccricket.kcmclib.text.lang.Localized;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Local MiniMessage helper on top of the KcMcLib lang system — no KcMcLib equivalent (that's
 * plugin-agnostic; this resolves BestestTool's lang keys and knows how to reach a
 * {@link CommandSender}). Mirrors ClickSorted's {@code MessageUtil}, trimmed to what BestestTool
 * needs.
 */
public class MessageUtil {

    private static ConfigManager configManager;

    public static void init(ConfigManager cm) {
        configManager = cm;
    }

    private static Localized localized(CommandSender sender) {
        return sender instanceof Player p ? configManager.lang(p.locale()) : configManager.lang();
    }

    /** Resolves {@code key} to a colored {@link Component} for {@code sender}'s locale. */
    public static Component get(CommandSender sender, String key, TagResolver... resolvers) {
        return localized(sender).getColoredMessage(key, resolvers);
    }

    /** Resolves {@code key} and sends it to {@code sender}. */
    public static void send(CommandSender sender, String key, TagResolver... resolvers) {
        sender.sendMessage(get(sender, key, resolvers));
    }

    /**
     * Renders {@code key} through the lang system down to a single legacy string (embedded
     * {@code \n}s preserved), for the GUI's {@code ItemMeta.setDisplayName(String)}/lore call
     * sites, which predate Adventure Components and already split lore on {@code \n} themselves
     * ({@code GUIHandler.createGUIItem}/{@code addItem}). Keeps GUI item text translatable
     * without a full GUI-to-Component rewrite.
     */
    public static String legacy(Player player, String key) {
        return LegacyComponentSerializer.legacySection().serialize(get(player, key));
    }
}
