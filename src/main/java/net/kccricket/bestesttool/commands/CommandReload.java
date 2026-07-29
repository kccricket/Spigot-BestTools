package net.kccricket.bestesttool.commands;

import net.kccricket.bestesttool.BestestToolPlugin;
import net.kccricket.bestesttool.security.Permissions;
import net.kccricket.bestesttool.text.MessageUtil;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.command.CommandSender;

public class CommandReload {

    static void reload(CommandSender sender, BestestToolPlugin main) {

            if (!Permissions.isAllowedTo(sender, Permissions.PERM_RELOAD)) {
                MessageUtil.send(sender, "noPermission", Placeholder.unparsed("plugin", main.getName()));
                return;
            }
            main.reload();
            MessageUtil.send(sender, "reloaded", Placeholder.unparsed("plugin", main.getName()));
    }

}
