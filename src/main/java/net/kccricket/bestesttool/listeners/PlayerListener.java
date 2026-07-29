package net.kccricket.bestesttool.listeners;

import net.kccricket.bestesttool.BestestToolPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {

    final BestestToolPlugin main;

    public PlayerListener(BestestToolPlugin main) {
        this.main=main;
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        main.playerSettings.remove(event.getPlayer().getUniqueId());
    }

}
