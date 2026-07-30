package net.kccricket.bestesttool.security;

import net.kccricket.bestesttool.BestestToolPlugin;

import org.bukkit.entity.Player;

/**
 * Thin BestestTool wrapper around the store-neutral {@link net.kccricket.kcmclib.security.ActionThrottle}:
 * supplies the bypass node and a live cooldown reader from {@code action_cooldown_ms}, and adds the
 * rate-limited {@code actionTooFast} chat notice ({@link #throttled(Player)}) that the library
 * throttle deliberately has no messaging/lang dependency to send itself. Mirrors ClickSorted's
 * {@code security.ActionThrottle}.
 */
public class ActionThrottle {

    /** Permission that exempts a player from throttling. */
    public static final String BYPASS_NODE = "bestesttool.throttle.bypass";

    private final BestestToolPlugin plugin;
    private final net.kccricket.kcmclib.security.ActionThrottle delegate;

    public ActionThrottle(BestestToolPlugin plugin) {
        this.plugin = plugin;
        this.delegate = new net.kccricket.kcmclib.security.ActionThrottle(
                BYPASS_NODE, () -> plugin.getConfigManager().main().getActionCooldownMs());
    }

    /**
     * Record an action attempt for {@code player} and decide whether it may proceed, using the
     * configured cooldown.
     *
     * @return {@code true} if the action is allowed; {@code false} if it falls within the cooldown
     *         window and should be dropped.
     */
    public boolean allow(Player player) {
        return delegate.allow(player);
    }

    /**
     * Shared throttle gate: like {@link #allow(Player)} but, on denial, also sends the player the
     * rate-limited {@code actionTooFast} notice. Lets every call site collapse to a single check.
     *
     * @return {@code true} if the action should be dropped (and the notice was sent);
     *         {@code false} if it may proceed.
     */
    public boolean throttled(Player player) {
        if (allow(player)) {
            return false;
        }
        plugin.messages().to(player).error().throttle("throttle", 3).send("actionTooFast");
        return true;
    }
}
