package net.kccricket.bestesttool;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    final Main main;

    PlayerListener(Main main) {
        this.main=main;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        main.playerSettings.remove(event.getPlayer().getUniqueId());
    }

}
