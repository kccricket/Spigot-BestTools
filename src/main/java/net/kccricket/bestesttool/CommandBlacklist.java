package net.kccricket.bestesttool;

import net.kccricket.bestesttool.text.MessageUtil;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Plain action methods for {@code /bestesttool blacklist} (alias {@code bl}). Permission checks,
 * sender-type checks, and argument/material parsing all live in the Brigadier tree built by
 * {@link BestToolsCommands} — this class only performs the
 * action once a call site has already established the sender is an authorized {@link Player}.
 */
public class CommandBlacklist {

    Main main;

    CommandBlacklist(Main main) {
        this.main = main;
    }

    private ArrayList<String> inv2stringlist(Inventory inv, int startSlot, int endSlot) {
        ArrayList<String> list = new ArrayList<>();
        for (int i = startSlot; i <= endSlot; i++) {
            if (inv.getItem(i) == null) continue;
            if (!list.contains(inv.getItem(i).getType().name())) {
                list.add(inv.getItem(i).getType().name());
            }
        }
        return list;
    }

    private String matlist2string(List<Material> list) {
        return list.stream()
                .map(Material::name)
                .collect(Collectors.joining(", "));
    }

    private String stringlist2string(List<String> list) {
        return String.join(", ", list);
    }

    void show(Player p) {
        main.getPlayerSetting(p).getBtcache().invalidated();
        main.getPlayerSetting(p).getBlacklist().print(p);
    }

    void reset(Player p) {
        main.getPlayerSetting(p).getBtcache().invalidated();
        Blacklist b = main.getPlayerSetting(p).getBlacklist();
        p.sendMessage(MessageUtil.get(p, "blacklistRemoved", Placeholder.unparsed("items", matlist2string(b.mats))));
        b.mats.clear();
    }

    /**
     * Adds/removes materials named on the command line. When {@code rawMaterials} is empty, falls
     * back to the player's currently held item (mirrors the pre-Brigadier bare {@code bl add}/
     * {@code bl remove} behavior).
     */
    void addOrRemove(Player p, boolean add, List<String> rawMaterials) {
        main.getPlayerSetting(p).getBtcache().invalidated();
        Blacklist b = main.getPlayerSetting(p).getBlacklist();

        List<String> materialNames = rawMaterials;
        if (materialNames.isEmpty()) {
            ItemStack currentItem = p.getInventory().getItemInMainHand();
            if (currentItem.getType() == Material.AIR) {
                MessageUtil.send(p, "blacklistNothingSpecified");
                return;
            }
            materialNames = List.of(currentItem.getType().name());
        }

        applyToMaterials(p, b, add, materialNames);
    }

    void addOrRemoveFromInventory(Player p, boolean add, boolean hotbarOnly) {
        main.getPlayerSetting(p).getBtcache().invalidated();
        Blacklist b = main.getPlayerSetting(p).getBlacklist();
        List<String> materialNames = inv2stringlist(p.getInventory(), hotbarOnly ? 0 : 9, hotbarOnly ? 8 : 35);
        applyToMaterials(p, b, add, materialNames);
    }

    private void applyToMaterials(Player p, Blacklist b, boolean add, List<String> materialNames) {
        ArrayList<Material> successes = new ArrayList<>();
        ArrayList<String> errors = new ArrayList<>();

        for (String s : materialNames) {
            Material m = Material.getMaterial(s.toUpperCase());
            if (m == Material.AIR) m = null;
            if (m == null) {
                errors.add(s);
                continue;
            }
            successes.add(m);
            if (add) {
                b.add(m);
            } else {
                b.remove(m);
            }
        }

        if (!errors.isEmpty()) {
            p.sendMessage(MessageUtil.get(p, "blacklistInvalid", Placeholder.unparsed("items", stringlist2string(errors))));
        }
        if (!successes.isEmpty()) {
            String key = add ? "blacklistAdded" : "blacklistRemoved";
            p.sendMessage(MessageUtil.get(p, key, Placeholder.unparsed("items", matlist2string(successes))));
        }
    }
}
