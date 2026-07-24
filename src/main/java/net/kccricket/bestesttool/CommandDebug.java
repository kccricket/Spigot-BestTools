package net.kccricket.bestesttool;

import net.kccricket.bestesttool.security.Permissions;
import net.kccricket.bestesttool.text.MessageUtil;
import net.kccricket.kcmclib.logging.DebugLevel;
import net.kccricket.kcmclib.logging.Log;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;


public class CommandDebug {

    static void debug(CommandSender sender, Command command, Main main, String arg) {

        TagResolver pluginName = Placeholder.unparsed("plugin", main.getName());

        if (!Permissions.isAllowedTo(sender, Permissions.PERM_DEBUG)) {
            MessageUtil.send(sender, "noPermission", pluginName);
            return;
        }
        if(arg.equalsIgnoreCase("debug")) {
            boolean nowEnabled = Log.getDebugLevel() == DebugLevel.OFF;
            Log.setDebugLevel(nowEnabled ? DebugLevel.DEBUG : DebugLevel.OFF);
            if(nowEnabled) {
                MessageUtil.send(sender, "debugEnabled", pluginName);
            } else {
                MessageUtil.send(sender, "debugDisabled", pluginName);
            }
        }
        else if(arg.equalsIgnoreCase("performance")) {
            main.measurePerformance=!main.measurePerformance;
            if(main.measurePerformance) {
                MessageUtil.send(sender, "performanceEnabled", pluginName);
            } else {
                MessageUtil.send(sender, "performanceDisabled", pluginName);
            }
        }
    }
}
