package net.kccricket.bestesttool;

import org.bukkit.command.CommandSender;

final class PermissionUtils {

    private PermissionUtils() {}

    static boolean has(CommandSender sender, String suffix) {
        return sender.hasPermission("bestesttool." + suffix)
            || sender.hasPermission("besttools." + suffix);
    }

}
