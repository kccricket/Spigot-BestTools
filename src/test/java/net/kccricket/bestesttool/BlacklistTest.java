package net.kccricket.bestesttool;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlacklistTest extends BestToolsTestBase {

    @Test
    void addRemoveContainsRoundTrip() {
        Blacklist b = new Blacklist();
        b.add(Material.STONE);
        assertTrue(b.contains(Material.STONE));

        b.remove(Material.STONE);
        assertFalse(b.contains(Material.STONE));
    }

    @Test
    void addByStringName() {
        Blacklist b = new Blacklist();
        b.add("DIRT");
        assertTrue(b.contains(Material.DIRT));
    }

    @Test
    void toStringListRoundTrip() {
        Blacklist b = new Blacklist();
        b.add(Material.STONE);
        b.add(Material.DIRT);

        Blacklist restored = new Blacklist(b.toStringList());

        assertTrue(restored.contains(Material.STONE));
        assertTrue(restored.contains(Material.DIRT));
    }

    @Test
    void invalidStringIsIgnored() {
        Blacklist b = new Blacklist(List.of("NOT_A_MATERIAL"));
        assertEquals(0, b.mats.size());
    }
}
