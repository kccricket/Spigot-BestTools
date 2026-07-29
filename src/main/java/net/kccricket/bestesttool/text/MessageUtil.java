package net.kccricket.bestesttool.text;

import net.kccricket.bestesttool.config.ConfigManager;
import net.kccricket.kcmclib.logging.Log;
import net.kccricket.kcmclib.text.lang.Localized;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

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

    /**
     * Resolves {@code key} to a colored {@link Component} for {@code sender}'s locale — unprefixed,
     * for call sites that need the raw resolved message (e.g. to embed elsewhere). Use {@link #send}
     * to dispatch a message to {@code sender} with the {@code prefix} lang key prepended.
     */
    public static Component get(CommandSender sender, String key, TagResolver... resolvers) {
        return localized(sender).getColoredMessage(key, resolvers);
    }

    /**
     * Resolves {@code key}, prepends the locale-appropriate {@code prefix} lang key, and dispatches
     * it to {@code sender} — as a chat message for a player, or as a plain-text log line (via
     * {@link Log}) for the console, matching ClickSorted's {@code MessageUtil}.
     */
    public static void send(CommandSender sender, String key, TagResolver... resolvers) {
        Component message = withPrefix(sender, get(sender, key, resolvers));
        if (sender instanceof ConsoleCommandSender) {
            Log.log(Level.INFO, PlainTextComponentSerializer.plainText().serialize(message));
        } else {
            sender.sendMessage(message);
        }
    }

    /**
     * Prepends {@code sender}'s locale-appropriate {@code prefix} lang key to an already-resolved
     * {@link Component}. Exposed for call sites (e.g. a {@code CooldownMessenger}) that need the
     * fully-prefixed message but must dispatch it themselves rather than through {@link #send}.
     */
    public static Component withPrefix(CommandSender sender, Component component) {
        return localized(sender).getColoredMessage("prefix").append(component);
    }
}
