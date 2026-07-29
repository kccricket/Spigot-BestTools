package net.kccricket.bestesttool.model;

import net.kccricket.bestesttool.text.MessageUtil;

import net.kccricket.kcmclib.pdc.PdcStringSet;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.List;

/**
 * A per-player set of materials to never auto-switch for, backed directly by PDC via
 * {@link PdcStringSet} — every mutation ({@link #add}/{@link #remove}) persists immediately,
 * rather than riding along on the next unrelated {@link PlayerSetting#save()} call. The
 * {@code bestesttool:blacklist} key matches what {@code PlayerSetting} used to manage itself
 * (namespace/key/comma-delimiter all identical), so existing on-disk player data is unaffected.
 */
public class Blacklist {

    private static final PdcStringSet<Material> STORE = new PdcStringSet<>(
            new NamespacedKey("bestesttool", "blacklist"), ",",
            Material::getMaterial, Material::name, HashSet::new);

    private final Player player;

    Blacklist(Player player) {
        this.player = player;
    }

    public void add(String string) {
        Material mat = Material.getMaterial(string);
        if (mat != null) add(mat);
    }

    public void add(Material mat) {
        STORE.add(player, mat);
    }

    public boolean contains(Material mat) {
        return STORE.get(player).contains(mat);
    }

    public void remove(Material mat) {
        STORE.remove(player, mat);
    }

    public void clear() {
        STORE.clear(player);
    }

    /** Alphabetical by material name, for deterministic display/serialization order. */
    public List<String> toStringList() {
        return STORE.get(player).stream()
                .map(Material::name)
                .sorted()
                .toList();
    }

    public void print(Player p) {
        List<String> names = toStringList();
        if (names.isEmpty()) {
            MessageUtil.send(p, "blacklistEmpty");
            return;
        }

        p.sendMessage(MessageUtil.get(p, "blacklistTitle"));

        for (String name : names) {
            Component link = createLink("[X] ", "/bestesttool blacklist remove " + name);
            Component nameComponent = Component.text(name, NamedTextColor.GRAY);
            p.sendMessage(link.append(nameComponent));
        }
    }

    private Component createLink(String text, String link) {
        // TODO: Make color configurable
        return Component.text(text, NamedTextColor.DARK_RED)
                .decorate(TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand(link));
    }

}
