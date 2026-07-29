package net.kccricket.bestesttool.commands;

import net.kccricket.bestesttool.BestestToolPlugin;
import net.kccricket.bestesttool.text.MessageUtil;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.entity.Player;
import net.kccricket.bestesttool.model.PlayerSetting;

/**
 * Plain action methods for {@code /bestesttool}'s player-facing subcommands. Permission checks,
 * sender-type checks, and argument parsing all live in the Brigadier tree built by
 * {@link BestToolsCommands} — this class only performs the
 * action once a call site has already established the sender is an authorized {@link Player}.
 */
public class CommandBestTools {

    final BestestToolPlugin main;

    public CommandBestTools(BestestToolPlugin main) {
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

    /** Reports the effective favorite slot (already resolved to the held slot if unset/out-of-range). */
    void reportFavoriteSlot(Player p) {
        int slot = main.getPlayerSetting(p).getFavoriteSlot();
        MessageUtil.send(p, "favoriteSlotSet", Placeholder.unparsed("slot", Integer.toString(slot)));
    }

    /** {@code slot} of {@code -1} means "use whatever slot I'm currently holding" — see {@link PlayerSetting#getFavoriteSlot()}. */
    void setFavoriteSlot(Player p, int slot) {
        PlayerSetting setting = main.getPlayerSetting(p);
        setting.getBtcache().invalidated();
        setting.setHasSeenBestToolsMessage(true);
        setting.setFavoriteSlot(slot);
        if (slot < 0 || slot > 8) {
            MessageUtil.send(p, "favoriteSlotHeld");
        } else {
            MessageUtil.send(p, "favoriteSlotSet", Placeholder.unparsed("slot", Integer.toString(slot)));
        }
    }
}
