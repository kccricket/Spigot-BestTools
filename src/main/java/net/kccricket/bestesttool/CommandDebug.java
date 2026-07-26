package net.kccricket.bestesttool;

import net.kccricket.bestesttool.security.Permissions;
import net.kccricket.bestesttool.text.MessageUtil;
import net.kccricket.kcmclib.logging.DebugLevel;
import net.kccricket.kcmclib.logging.Log;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.command.CommandSender;


public class CommandDebug {

    static void debug(CommandSender sender, Main main, String arg) {
        if (!Permissions.isAllowedTo(sender, Permissions.PERM_DEBUG)) {
            MessageUtil.send(sender, "noPermission", Placeholder.unparsed("plugin", main.getName()));
            return;
        }
        if (arg.equalsIgnoreCase("debug")) {
            setDebug(sender, main, Log.getDebugLevel() == DebugLevel.OFF);
        } else if (arg.equalsIgnoreCase("performance")) {
            setPerformance(sender, main, !main.measurePerformance);
        }
    }

    static void debug(CommandSender sender, Main main, String arg, boolean enabled) {
        if (!Permissions.isAllowedTo(sender, Permissions.PERM_DEBUG)) {
            MessageUtil.send(sender, "noPermission", Placeholder.unparsed("plugin", main.getName()));
            return;
        }
        if (arg.equalsIgnoreCase("debug")) {
            setDebug(sender, main, enabled);
        } else if (arg.equalsIgnoreCase("performance")) {
            setPerformance(sender, main, enabled);
        }
    }


    private static void setDebug(CommandSender sender, Main main, boolean enabled) {
        Log.setDebugLevel(enabled ? DebugLevel.DEBUG : DebugLevel.OFF);
        MessageUtil.send(sender, enabled ? "debugEnabled" : "debugDisabled", Placeholder.unparsed("plugin", main.getName()));
    }

    private static void setPerformance(CommandSender sender, Main main, boolean enabled) {
        main.measurePerformance = enabled;
        MessageUtil.send(sender, enabled ? "performanceEnabled" : "performanceDisabled", Placeholder.unparsed("plugin", main.getName()));
    }
}
