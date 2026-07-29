package net.kccricket.bestesttool.refill;

import net.kccricket.bestesttool.Main;
import net.kccricket.kcmclib.logging.Log;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.kccricket.bestesttool.util.MapUtils;

public class RefillUtils {

    final static int inventorySize = 36;

    final Main main;

    public RefillUtils(Main main) {
        this.main=main;
    }

    boolean isBowlOrBottle(Material mat) {
        return (mat == Material.GLASS_BOTTLE || mat == Material.BOWL);
    }

    boolean moveBowlsAndBottles(Inventory inv, int slot) {
        if(!isBowlOrBottle(inv.getItem(slot).getType())) return false;
        // Clone: getItem() can return a live mirror of the backing stack, so the clear() below
        // (and the one in the fallback loop) must not be able to zero out toBeMoved itself.
        ItemStack toBeMoved = inv.getItem(slot).clone();
        inv.clear(slot);
        HashMap<Integer, ItemStack> leftovers = inv.addItem(toBeMoved);
        if(inv.getItem(slot)==null || inv.getItem(slot).getAmount()==0 || inv.getItem(slot).getType() == Material.AIR) {
            return true;
        }
        if(leftovers.size()>0) {
            Log.debug("Possible item loss detected due to RefillUtils#moveBowlsAndBottles, dropping leftover items...");
            for(ItemStack leftover : leftovers.values()) {
                if(!(inv.getHolder() instanceof Player)) {
                    Log.debug("Could not drop items because inventory has no player as holder :(");
                    return false;
                }
                Player p = (Player) inv.getHolder();
                p.getWorld().dropItem(p.getLocation(),leftover);
            }
            return false;
        }
        // slot was already cleared above. The old version of this loop re-cleared it on every
        // iteration too, which made i == slot (reachable whenever slot is a main-inventory/hotbar
        // index, i.e. every case but the offhand) "find" the destination's own just-cleared slot
        // and put the bowl right back into it — skip it outright instead.
        for(int i = inventorySize - 1; i >= 0; i--) {
            if(i == slot) continue;
            if(inv.getItem(i)==null || inv.getItem(i).getAmount()==0 || inv.getItem(i).getType()==Material.AIR) {
                inv.setItem(i,toBeMoved);
                return true;
            }
        }
        return false;
    }

    public void refillStack(Inventory inv, int source, int dest, ItemStack stack) {
        if (!(inv.getHolder() instanceof Player player)) {
            Log.debug("Refill failed, because inventory has no player as holder :(");
            return;
        }
        player.getScheduler().run(main, task -> {
            if(inv.getItem(source)==null) return;
            if(!inv.getItem(source).equals(stack)) {
                Log.debug("Refill failed, because source ItemStack has changed. Aborting Refill to prevent item loss.");
                return;
            }
            if(inv.getItem(dest)!=null && !moveBowlsAndBottles(inv,dest)) {
                Log.debug("Refill failed, because destination slot is not empty anymore. Aborting Refill to prevent item loss.");
                return;
            }
            inv.setItem(source, null);
            inv.setItem(dest, stack);
        }, null);
    }

    public static int getMatchingStackPosition(PlayerInventory inv, Material mat, int currentSlot) {

        HashMap<Integer,Integer> slots = new HashMap<>();

        for(int i = 0; i < inventorySize; i++) {
            if(i==currentSlot) continue;
            ItemStack item = inv.getItem(i);
            if(item==null) continue;

            if(item.getType()!=mat) continue;

            if(item.getAmount()==64) return i;

            slots.put(i,item.getAmount());
        }

        if(slots.size()==0) return -1;

        // sortByValue orders ascending, so the last entry iterated holds the highest amount.
        Map<Integer,Integer> sortedSlots = MapUtils.sortByValue(slots);
        int bestSlot = -1;
        for(Entry<Integer,Integer> entry : sortedSlots.entrySet()) {
            bestSlot = entry.getKey();
        }

        return bestSlot;

    }
}
