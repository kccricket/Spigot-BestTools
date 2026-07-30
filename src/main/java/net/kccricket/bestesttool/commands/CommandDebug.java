package net.kccricket.bestesttool.commands;

import net.kccricket.bestesttool.BestestToolPlugin;
import net.kccricket.bestesttool.security.Permissions;
import net.kccricket.kcmclib.logging.DebugLevel;
import net.kccricket.kcmclib.logging.Log;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.command.CommandSender;


public class CommandDebug {

    static void debug(CommandSender sender, BestestToolPlugin main, String arg) {
        if (!Permissions.isAllowedTo(sender, Permissions.PERM_DEBUG)) {
            main.messages().to(sender).error().send("noPermission", Placeholder.unparsed("plugin", main.getName()));
            return;
        }
        if (arg.equalsIgnoreCase("debug")) {
            setDebug(sender, main, Log.getDebugLevel() == DebugLevel.OFF);
        }
    }

    static void debug(CommandSender sender, BestestToolPlugin main, String arg, boolean enabled) {
        if (!Permissions.isAllowedTo(sender, Permissions.PERM_DEBUG)) {
            main.messages().to(sender).error().send("noPermission", Placeholder.unparsed("plugin", main.getName()));
            return;
        }
        if (arg.equalsIgnoreCase("debug")) {
            setDebug(sender, main, enabled);
        }
    }


    private static void setDebug(CommandSender sender, BestestToolPlugin main, boolean enabled) {
        Log.setDebugLevel(enabled ? DebugLevel.DEBUG : DebugLevel.OFF);
        main.messages().to(sender).status().send(enabled ? "debugEnabled" : "debugDisabled", Placeholder.unparsed("plugin", main.getName()));
    }
}
