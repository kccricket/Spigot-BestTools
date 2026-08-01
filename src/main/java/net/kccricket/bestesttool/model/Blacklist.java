package net.kccricket.bestesttool.model;

import net.kccricket.kcmclib.pdc.PdcStringSet;

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
 * Pure data — no plugin reference and no messaging; the chat-facing listing lives in
 * {@code CommandBlacklist.show}.
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
        // matchMaterial (unlike getMaterial) accepts namespaced IDs (minecraft:dirt) and any
        // case, matching what the command-line blacklist add/remove suggestions now offer.
        Material mat = Material.matchMaterial(string);
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

    /**
     * Namespaced material IDs (e.g. {@code minecraft:dirt}), alphabetical for deterministic
     * display order — used for the chat listing/clickable links in {@code CommandBlacklist.show}
     * and for {@code remove}'s tab-completion. Distinct from {@link #STORE}'s own on-disk PDC
     * encoding (bare {@link Material#name()}, unchanged, so existing player data round-trips
     * exactly as before) — this is purely a display/command-line format.
     */
    public List<String> toStringList() {
        return STORE.get(player).stream()
                .map(m -> m.getKey().toString())
                .sorted()
                .toList();
    }

}
