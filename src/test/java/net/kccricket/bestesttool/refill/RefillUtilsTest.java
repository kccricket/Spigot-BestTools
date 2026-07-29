package net.kccricket.bestesttool.refill;

import net.kccricket.bestesttool.BestToolsTestBase;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.kccricket.bestesttool.refill.RefillUtils;

/**
 * Regression coverage for {@link RefillUtils#moveBowlsAndBottles} (RefillUtils.java:29), which
 * had zero test coverage before a full-codebase review surfaced the bug it fixes: the fallback
 * relocation loop re-cleared the destination slot on every iteration, so once the loop reached
 * {@code i == slot} it "found" the slot it had just cleared, put the bowl/bottle back into it,
 * and reported success — after which {@link RefillUtils#refillStack} immediately overwrote that
 * same slot, silently destroying the item. The fix makes the loop skip {@code i == slot} outright
 * and clones the item before the first {@code clear()} (RefillUtils.java:31-33), rather than
 * holding a live reference into the slot it's about to clear.
 */
class RefillUtilsTest extends BestToolsTestBase {

    /** Fills every main-inventory slot (0..35) with a full, unstackable-by-fullness stack, except {@code except}. */
    private void fillExcept(PlayerInventory inv, int except) {
        for (int i = 0; i < RefillUtils.inventorySize; i++) {
            if (i == except) continue;
            inv.setItem(i, new ItemStack(Material.STONE, 64));
        }
    }

    private int countBottles(PlayerInventory inv) {
        int total = 0;
        for (int i = 0; i < RefillUtils.inventorySize; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() == Material.GLASS_BOTTLE) total += item.getAmount();
        }
        return total;
    }

    @Test
    void refusesInsteadOfSilentlyDestroyingTheBottleWhenNoOtherSlotIsFree() {
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        int slot = 4;
        fillExcept(inv, slot);
        inv.setItem(slot, new ItemStack(Material.GLASS_BOTTLE, 1));

        boolean moved = plugin.refillUtils.moveBowlsAndBottles(inv, slot);

        // The pre-fix loop cleared `slot` on every iteration, so reaching i == slot always found
        // an "empty" slot and falsely reported success — the caller then overwrote it, deleting
        // the bottle. With nowhere else to put it, this must now refuse instead.
        assertFalse(moved, "must refuse to report success when there is nowhere else to put the item");
        assertEquals(Material.GLASS_BOTTLE, inv.getItem(slot).getType(),
                "the bottle must still be sitting right where it started, not destroyed");
    }

    @Test
    void relocatesToAFreeSlotWhenOneGenuinelyExists() {
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();
        int slot = 20;
        int freeSlot = 3;
        fillExcept(inv, slot);
        inv.setItem(freeSlot, null); // carve out a second, lower-index free slot
        inv.setItem(slot, new ItemStack(Material.BOWL, 1));

        boolean moved = plugin.refillUtils.moveBowlsAndBottles(inv, slot);

        assertTrue(moved, "must succeed when a free slot genuinely exists");
        assertEquals(Material.BOWL, inv.getItem(freeSlot).getType(),
                "the bowl must land in the free slot rather than staying aliased to the original");
        assertTrue(inv.getItem(slot) == null || inv.getItem(slot).getType() == Material.AIR,
                "the original slot must end up empty, not holding a duplicate");
    }

    @Test
    void refillStackRelocatesTheOldBottleWithoutLosingAnyItems() {
        PlayerMock player = newPlayer();
        PlayerInventory inv = player.getInventory();

        int source = 10;
        int dest = 4;
        ItemStack freshBottles = new ItemStack(Material.GLASS_BOTTLE, 3);
        inv.setItem(source, freshBottles);
        inv.setItem(dest, new ItemStack(Material.GLASS_BOTTLE, 1)); // the empty bottle being refilled away

        assertEquals(4, countBottles(inv), "test fixture assumption: 1 (dest) + 3 (source) bottles to start");

        plugin.refillUtils.refillStack(inv, source, dest, freshBottles);
        server.getScheduler().performOneTick(); // refillStack defers its body one tick

        assertEquals(Material.GLASS_BOTTLE, inv.getItem(dest).getType());
        assertEquals(3, inv.getItem(dest).getAmount(), "the fresh stack must land in dest");
        assertTrue(inv.getItem(source) == null || inv.getItem(source).getType() == Material.AIR,
                "source slot must be cleared after the refill");
        assertEquals(4, countBottles(inv),
                "no bottles may vanish across the refill — the old one must be relocated, not destroyed");
    }
}
