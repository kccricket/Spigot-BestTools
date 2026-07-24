package net.kccricket.bestesttool;

import net.kccricket.bestesttool.security.Permissions;
import net.kccricket.bestesttool.text.MessageUtil;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class CommandRefill implements CommandExecutor, TabCompleter {

    final Main main;

    CommandRefill(Main main) {
        this.main=main;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {

        Player p;

        if (!Permissions.isAllowedTo(sender, Permissions.PERM_REFILL)) {
            MessageUtil.send(sender, "noPermission", Placeholder.unparsed("plugin", main.getName()));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            CommandReload.reload(sender,command,main);
            return true;
        }

        if(!(sender instanceof Player)) {
            MessageUtil.send(sender, "notAPlayer");
            return true;
        }

        p = (Player) sender;
        PlayerSetting playerSetting = main.getPlayerSetting(p);

        playerSetting.setHasSeenRefillMessage(true);

        // Toggle AutoRefill //
        if(playerSetting.toggleRefillEnabled()) {
            MessageUtil.send(p, "refillEnabled");
        } else {
            MessageUtil.send(p, "refillDisabled");
        }

        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender commandSender, @NotNull Command command, @NotNull String s,  @NotNull String[] str) {
        // final String[] args = {"toggle","on","off","preventItemBreak","hotbar","hotbarOnly"};
        // final String[] trueFalse = {"true","false"};


        return null;
    }
}
