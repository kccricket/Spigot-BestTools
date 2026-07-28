package net.kccricket.bestesttool;

import org.bukkit.entity.Player;

/**
 * Plain action methods for {@code /bestesttool selftest}. Permission and {@code enable_selftest}
 * config gating live in the Brigadier tree built by {@link BestToolsCommands} — this class only
 * performs the action once a call site has already established the sender is an authorized
 * {@link Player}. The actual test logic lives in {@link SelfTestManager}, which (unlike this
 * class) is constructed once in {@link Main#onEnable}, not per {@code /bestesttool reload}, so an
 * in-progress test survives a reload.
 */
public class CommandSelfTest {

    private final Main main;

    CommandSelfTest(Main main) {
        this.main = main;
    }

    void start(Player player, String stageName) {
        main.selfTestManager.start(player, stageName);
    }

    void next(Player player) {
        main.selfTestManager.advance(player);
    }

    void status(Player player) {
        main.selfTestManager.status(player);
    }

    void stop(Player player) {
        main.selfTestManager.stop(player, "selfTestStopped");
    }
}
