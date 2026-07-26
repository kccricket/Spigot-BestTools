package net.kccricket.bestesttool;

import net.kccricket.bestesttool.text.MessageUtil;

import org.bukkit.entity.Player;

/**
 * Plain action methods for {@code /bestesttool}'s player-facing subcommands. Permission checks,
 * sender-type checks, and argument parsing all live in the Brigadier tree built by
 * {@link BestToolsCommands} — this class only performs the
 * action once a call site has already established the sender is an authorized {@link Player}.
 */
public class CommandBestTools {

    final Main main;

    CommandBestTools(Main main) {
        this.main = main;
    }

    void toggleBestTools(Player p) {
        boolean enabled = !main.getPlayerSetting(p).isBestToolsEnabled();
        PlayerSetting setting = main.getPlayerSetting(p);
        setting.getBtcache().invalidated();
        setting.setHasSeenBestToolsMessage(true);
        setting.setBestToolsEnabled(enabled);
        MessageUtil.send(p, enabled ? "besttoolsEnabled" : "besttoolsDisabled");
    }

    void toggleHotbarOnly(Player p) {
        setHotbarOnly(p, !main.getPlayerSetting(p).isHotbarOnly());
    }

    void setHotbarOnly(Player p, boolean enabled) {
        PlayerSetting setting = main.getPlayerSetting(p);
        setting.getBtcache().invalidated();
        setting.setHasSeenBestToolsMessage(true);
        setting.setHotbarOnly(enabled);
        MessageUtil.send(p, enabled ? "hotbarOnlyEnabled" : "hotbarOnlyDisabled");
    }

    void openGui(Player p) {
        PlayerSetting setting = main.getPlayerSetting(p);
        setting.getBtcache().invalidated();
        setting.setHasSeenBestToolsMessage(true);
        main.guiHandler.open(p);
    }
}
