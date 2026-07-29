package net.kccricket.bestesttool.security;

import org.bukkit.command.CommandSender;

/**
 * BestestTool's permission-node constants. The check ({@link #isAllowedTo}) delegates to the
 * store-neutral, plugin-agnostic helper in {@link net.kccricket.kcmclib.security.Permissions}.
 *
 * <p>No legacy {@code besttools.*} alias — BestestTool is a fresh re-release with no backward
 * compatibility to preserve, so only the canonical {@code bestesttool.*} nodes are checked.
 */
public final class Permissions {

    private Permissions() {}

    public static final String PERM_USE = "bestesttool.use";
    public static final String PERM_REFILL = "bestesttool.refill";
    public static final String PERM_RELOAD = "bestesttool.admin.reload";
    public static final String PERM_DEBUG = "bestesttool.admin.debug";
    public static final String PERM_SELFTEST = "bestesttool.admin.selftest";
    public static final String PERM_BENCHMARK = "bestesttool.admin.benchmark";

    /**
     * Check if the sender has the specified permission node.
     *
     * @param sender Command sender (player or console) to check
     * @param node   Node to check for
     * @return true if the sender has the permission node, false otherwise
     */
    public static boolean isAllowedTo(CommandSender sender, String node) {
        return net.kccricket.kcmclib.security.Permissions.isAllowedTo(sender, node);
    }
}
