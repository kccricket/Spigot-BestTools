package net.kccricket.bestesttool.model;

import net.kccricket.bestesttool.BestToolsTestBase;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlacklistTest extends BestToolsTestBase {

    @Test
    void addRemoveContainsRoundTrip() {
        Blacklist b = new Blacklist(newPlayer());
        b.add(Material.STONE);
        assertTrue(b.contains(Material.STONE));

        b.remove(Material.STONE);
        assertFalse(b.contains(Material.STONE));
    }

    @Test
    void addByStringName() {
        Blacklist b = new Blacklist(newPlayer());
        b.add("DIRT");
        assertTrue(b.contains(Material.DIRT));
    }

    @Test
    void mutationsPersistImmediatelyToPdcForTheSamePlayer() {
        PlayerMock player = newPlayer();
        Blacklist b = new Blacklist(player);
        b.add(Material.STONE);
        b.add(Material.DIRT);

        Blacklist reloaded = new Blacklist(player);

        assertTrue(reloaded.contains(Material.STONE));
        assertTrue(reloaded.contains(Material.DIRT));
    }

    @Test
    void invalidStringIsIgnored() {
        Blacklist b = new Blacklist(newPlayer());
        b.add("NOT_A_MATERIAL");
        assertTrue(b.toStringList().isEmpty());
    }
}
