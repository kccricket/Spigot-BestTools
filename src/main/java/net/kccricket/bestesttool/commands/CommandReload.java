package net.kccricket.bestesttool.commands;

import net.kccricket.bestesttool.BestestToolPlugin;
import net.kccricket.bestesttool.security.Permissions;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.command.CommandSender;

public class CommandReload {

    static void reload(CommandSender sender, BestestToolPlugin main) {

            if (!Permissions.isAllowedTo(sender, Permissions.PERM_RELOAD)) {
                main.messages().to(sender).error().send("noPermission", Placeholder.unparsed("plugin", main.getName()));
                return;
            }
            main.reload();
            main.messages().to(sender).status().send("reloaded", Placeholder.unparsed("plugin", main.getName()));
    }

}
