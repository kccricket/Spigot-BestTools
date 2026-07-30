package net.kccricket.bestesttool.commands;

import net.kccricket.bestesttool.BestestToolPlugin;

import org.bukkit.entity.Player;
import net.kccricket.bestesttool.model.PlayerSetting;

/**
 * Plain action method for {@code /bestesttool refill} (and its {@code /refill}/{@code /rf}
 * root aliases). Permission checks and sender-type checks live in the Brigadier tree built by
 * {@link BestToolsCommands} — this class only performs the
 * action once a call site has already established the sender is an authorized {@link Player}.
 */
public class CommandRefill {

    final BestestToolPlugin main;

    public CommandRefill(BestestToolPlugin main) {
        this.main = main;
    }

    void toggleRefill(Player p) {
        setRefill(p, !main.getPlayerSetting(p).isRefillEnabled());
    }

    void setRefill(Player p, boolean enabled) {
        PlayerSetting playerSetting = main.getPlayerSetting(p);
        playerSetting.setHasSeenRefillMessage(true);
        playerSetting.setRefillEnabled(enabled);
        main.messages().to(p).status().send(enabled ? "refillEnabled" : "refillDisabled");
    }
}
