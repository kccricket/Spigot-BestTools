package net.kccricket.bestesttool.commands;

import net.kccricket.bestesttool.BestestToolPlugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.kccricket.bestesttool.model.Blacklist;

/**
 * Plain action methods for {@code /bestesttool blacklist} (alias {@code bl}). Permission checks,
 * sender-type checks, and argument/material parsing all live in the Brigadier tree built by
 * {@link BestToolsCommands} — this class only performs the
 * action once a call site has already established the sender is an authorized {@link Player}.
 */
public class CommandBlacklist {

    BestestToolPlugin main;

    public CommandBlacklist(BestestToolPlugin main) {
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

    /**
     * Lists the player's blacklist as clickable {@code [X] <material>} rows that run
     * {@code /bestesttool blacklist remove <material>}. Lives here rather than on {@link Blacklist}
     * (pure data, no plugin reference) since it's the only chat-facing piece of that model.
     */
    void show(Player p) {
        main.getPlayerSetting(p).getBtcache().invalidated();
        List<String> names = main.getPlayerSetting(p).getBlacklist().toStringList();
        if (names.isEmpty()) {
            main.messages().to(p).status().send("blacklistEmpty");
            return;
        }

        main.messages().to(p).status().send("blacklistTitle");
        for (String name : names) {
            Component link = createLink("[X] ", "/bestesttool blacklist remove " + name);
            Component nameComponent = Component.text(name, NamedTextColor.GRAY);
            main.messages().to(p).raw().send(link.append(nameComponent));
        }
    }

    private Component createLink(String text, String link) {
        // TODO: Make color configurable
        return Component.text(text, NamedTextColor.DARK_RED)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(link));
    }

    void reset(Player p) {
        main.getPlayerSetting(p).getBtcache().invalidated();
        Blacklist b = main.getPlayerSetting(p).getBlacklist();
        if (b.toStringList().isEmpty()) {
            main.messages().to(p).status().send("blacklistEmpty");
            return;
        }
        b.clear();
        main.messages().to(p).status().send("blacklistCleared");
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
                main.messages().to(p).error().send("blacklistNothingSpecified");
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
            // matchMaterial accepts namespaced IDs (minecraft:dirt), bare names, and any case —
            // getMaterial (valueOf-backed) rejects namespaced input and is locale-sensitive
            // via a bare toUpperCase().
            Material m = Material.matchMaterial(s);
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
            main.messages().to(p).error().send("blacklistInvalid", Placeholder.unparsed("items", stringlist2string(errors)));
        }
        if (!successes.isEmpty()) {
            String key = add ? "blacklistAdded" : "blacklistRemoved";
            main.messages().to(p).status().send(key, Placeholder.unparsed("items", matlist2string(successes)));
        }
    }
}
