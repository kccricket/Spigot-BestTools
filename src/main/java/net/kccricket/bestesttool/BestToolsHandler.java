package net.kccricket.bestesttool;

import net.kccricket.kcmclib.logging.Log;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * This will probably be a separate plugin called BestTool or something
 */
public class BestToolsHandler {

    final Main main;
    boolean debug = false;
    boolean verbose = true;

    static final int hotbarSize = 9;
    static final int inventorySize = 36;

    // Configurable Start //
    boolean preventItemBreak = false; // Will not use Items that would break on this use
    // Configurable End //

    final HashMap<Material,Tool> toolMap = new HashMap<>();
    final HashSet<Material> globalBlacklist = new HashSet<>();
    ArrayList<Tag<Material>> usedTags = new ArrayList<>();

    // TODO: Cache valid tool materials here
    final ArrayList<Material> pickaxes = new ArrayList<>();
    final ArrayList<Material> axes = new ArrayList<>();
    final ArrayList<Material> hoes = new ArrayList<>();
    final ArrayList<Material> shovels = new ArrayList<>();
    final ArrayList<Material> swords = new ArrayList<>();

    final ArrayList<Material> allTools = new ArrayList<>();
    final ArrayList<Material> instaBreakableByHand = new ArrayList<>();

    final EnumSet<Material> leaves = EnumSet.noneOf(Material.class);


    final ArrayList<Material> weapons = new ArrayList<>();

    // Cached like BestToolsListener.useAxeAsWeapon: read once per load/reload (BestToolsHandler is
    // reconstructed fresh in Main.load()), not on every candidate check.
    boolean considerSwordsForLeaves;
    boolean considerSwordsForCobwebs;

    BestToolsHandler(Main main) {

        this.main=Objects.requireNonNull(main,"Main must not be null");

        considerSwordsForLeaves = main.configManager.main().getConsiderSwordsForLeaves();
        considerSwordsForCobwebs = main.configManager.main().getConsiderSwordsForCobwebs();

        for(String name : main.configManager.main().getGlobalBlockBlacklist()) {
            Material mat = Material.getMaterial(name.toUpperCase());
            if(mat==null) {
                main.getLogger().warning("Invalid material on global_block_blacklist: "+name);
                continue;
            }
            Log.debug("Adding to global block blacklist: " + mat.name());
            globalBlacklist.add(mat);
        }

        Arrays.stream(Material.values()).forEach(material -> {
            if(material.name().endsWith("_LEAVES")) {
                leaves.add(material);
            }
        });


    }

    boolean isWeapon(ItemStack itemInMainHand) {
        for(Material mat : weapons) {
            if(itemInMainHand.getType()==mat) return true;
        }
        return false;
    }

    boolean isTool(ItemStack i) {
        return allTools.contains(i.getType());
    }
    boolean isToolOrRoscoe(ItemStack i) {
        return allTools.contains(i.getType()) || swords.contains(i.getType());
    }

    enum Tool {
        PICKAXE,
        SHOVEL,
        SHEARS,
        AXE,
        HOE,
        SWORD,
        NONE
    }

    /**
     * Returns the durability
     * @param item
     * @return Durability left, or -1 if not damageable
     */
    // private int getDurability(@Nullable ItemStack item) {
    //     // TODO: Delete? Its unused
    //     if(item==null) return -1;
    //     if(!(item.getItemMeta() instanceof Damageable)) {
    //         return -1;
    //     }
    //     Damageable damageable = (Damageable) item.getItemMeta();
    //     return item.getType().getMaxDurability() - damageable.getDamage();
    // }

    /**
     * Gets the best tool type for a material
     * @param mat The block's material
     * @return Best tool type for that material
     */
    @NotNull
    Tool getBestToolType(@NotNull Material mat) {
        Tool bestTool = toolMap.get(mat);
        if(bestTool == null) bestTool = Tool.NONE;
        Log.debug("Best ToolType for "+mat+" is "+bestTool.name());
        return bestTool;
    }

    // TODO: Optimize all of this by caching valid Materials instead of doing String checks everytime

    @SuppressWarnings("incomplete-switch")
    boolean profitsFromSilkTouch(Material mat) {
        String name = mat.name();
        switch(mat) {
            case GLOWSTONE:
            case ENDER_CHEST:
            case QUARTZ:
            case SPAWNER:
            case SEA_LANTERN:
                return true;
        }
        if(name.equals("NETHER_GOLD_ORE")) return true; // Fortune also improves this, but according to wiki even fortune 3 on avg only gives 8.8 nuggets which is less than 1 ingot
        if(name.contains("GLASS")) return true;
        return false;
    }

    // TODO: Implement profitsFromFortune()

    /**
     * Filters mining-tool candidates independent of live mining speed — currently just the config
     * toggles that keep swords out of leaf/cobweb selection unless explicitly enabled. Everything
     * else (is this item even fast at this block) is answered by {@link BlockData#getDestroySpeed}
     * itself: an irrelevant item (e.g. a sword against stone) has no matching vanilla tool rule and
     * scores no better than a bare hand, so it never needs an explicit category filter here.
     */
    boolean isCandidate(ItemStack item, Material target) {
        if(!swords.contains(item.getType())) return true;
        if(LeavesUtils.isLeaves(target)) return considerSwordsForLeaves;
        if(target == Material.COBWEB) return considerSwordsForCobwebs;
        return true;
    }

    static int getEmptyHotbarSlot(PlayerInventory inv) {
        for(int i = 0; i<hotbarSize;i++) {
            if(inv.getItem(i)==null) return i;
        }
        return -1;
    }

    @Nullable
    ItemStack getNonToolItemFromArray(@NotNull ItemStack[] items,ItemStack currentItem, Material target) {

        // Note: InstaBreaks dont cause damage except on hoes
        // TODO: Take this into account: https://minecraft.gamepedia.com/Item_durability
        // TODO: itemMeta instanceof Damageable may also mean the tool is unused!
        if(instaBreakableByHand.contains(target) && !hoes.contains(currentItem.getType()) ||
            !isToolOrRoscoe(currentItem))
            return currentItem;

        for(ItemStack item: items) {
            if(item==null || !isDamageable(item)) {
                return item;
            }
        }
        return null;

    }

    boolean hasSilktouch(ItemStack item) {
        if(item==null) return false;
        if(!item.hasItemMeta()) return false;
        return item.getItemMeta().hasEnchant(EnchantmentUtils.getEnchantment("silk_touch"));
    }

    /**
     * Ranks inventory items against a block's live mining data and returns the best one, or
     * {@code null} if nothing beats a bare hand (speed {@code 1.0}).
     * <p>
     * Ranked by {@code (isPreferredTool desc, getDestroySpeed desc)} — drops beat speed. {@link
     * BlockData#getDestroySpeed} alone is not enough: an Efficiency V iron pickaxe outscores a
     * plain diamond pickaxe on obsidian, but iron doesn't drop obsidian. {@link
     * BlockData#isPreferredTool} is only consulted when {@link BlockData#requiresCorrectToolForDrops()}
     * is true, and only for a candidate that's already the fastest seen so far — for the common
     * case (dirt, wood, leaves, wool) that's zero calls; for ores, typically one to three.
     */
    @Nullable
    ItemStack getBestItemStackFromArray(@NotNull BlockData data, @NotNull ItemStack[] items, boolean trySilktouch, @NotNull Material target) {

        boolean needsCorrect = data.requiresCorrectToolForDrops();

        ItemStack bestAny = null;
        float bestAnySpeed = 1.0f; // 1.0 == bare hand; a candidate must beat it to be worth switching to
        ItemStack bestCorrect = null;
        float bestCorrectSpeed = 1.0f;

        for(ItemStack item : items) {
            if(item==null) continue; // IntelliJ says this is always false
            // TODO: Check if durability is 1

            if(trySilktouch && !hasSilktouch(item)) continue;
            if(!isCandidate(item,target)) continue;

            float speed = data.getDestroySpeed(item,true);
            if(speed > bestAnySpeed) {
                bestAny = item;
                bestAnySpeed = speed;
            }
            if(needsCorrect && speed > bestCorrectSpeed && data.isPreferredTool(item)) {
                bestCorrect = item;
                bestCorrectSpeed = speed;
            }
        }

        if(bestAny == null) {
            if(trySilktouch) {
                return getBestItemStackFromArray(data,items,false,target);
            } else {
                return null;
            }
        }
        return needsCorrect && bestCorrect != null ? bestCorrect : bestAny;
    }

    @Nullable
    ItemStack getBestRoscoeFromArray(@NotNull ItemStack[] items, ItemStack currentItem, EntityType enemy, boolean useAxe) {

        ArrayList<ItemStack> list = new ArrayList<>();
        for(ItemStack item : items) {
            if(item==null) continue; // IntelliJ says this is always false
            // TODO: Check if durability is 1

            if(isRoscoe(item,useAxe)) {
                list.add(item);
            }
        }
        if(list.size()==0) {
            return null;
        }
        list.sort((o1, o2) -> SwordUtils.getDamage(o1,enemy) < SwordUtils.getDamage(o2,enemy) ? 1 : -1);
        return list.get(0);
    }

    // Roscoes are only axes and swords, weapons are roscoes + bow, crossbow, etc
    private boolean isRoscoe(ItemStack item, boolean useAxe) {
         return useAxe ?
                 swords.contains(item.getType())
                         || axes.contains(item.getType())
                 : swords.contains(item.getType());
    }


    ItemStack[] inventoryToArray(Player p,boolean hotbarOnly) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] items = new ItemStack[(hotbarOnly ? hotbarSize : inventorySize)];
        for(int i = 0; i < (hotbarOnly ? hotbarSize : inventorySize); i++) {
            items[i] = inv.getItem(i);
        }
        return items;
    }

    /**
     * Tries to get the ItemStack that is the best for this block, ranked by live mining data
     * ({@link BlockData#getDestroySpeed}/{@link BlockData#isPreferredTool}) rather than the static
     * {@code toolMap} — see {@link #getBestItemStackFromArray}. This also covers leaves and cobweb
     * natively (shears/hoe/sword are simply whichever candidate scores highest), replacing what
     * used to be a separate {@code LeavesUtils}-driven branch here.
     * @param block The block being mined
     * @param p Player
     * @return
     */
    @Nullable
    ItemStack getBestToolFromInventory(@NotNull Block block, Player p, boolean hotbarOnly,ItemStack currentItem) {
        ItemStack[] items = inventoryToArray(p,hotbarOnly);
        Material mat = block.getType();

        ItemStack bestStack = getBestItemStackFromArray(block.getBlockData(),items,profitsFromSilkTouch(mat),mat);
        if(bestStack==null) {
            Log.debug("bestStack is null");
            return getNonToolItemFromArray(items,currentItem,mat);
        }
        Log.debug("bestStack is "+bestStack.toString());
        return bestStack;

    }

    /**
     * Tries to get the roscoe that is the best for this block
     * @param p Player
     * @return
     */
    @Nullable
    ItemStack getBestRoscoeFromInventory(@NotNull EntityType enemy, Player p, boolean hotbarOnly, ItemStack currentItem, boolean useAxe) {
        ItemStack[] items = inventoryToArray(p,hotbarOnly);

        ItemStack bestRoscoe = getBestRoscoeFromArray(items,currentItem,enemy,useAxe);
        //if(bestRoscoe==null) {
        //    bestRoscoe = getNonToolItemFromArray(items,currentItem,mat);
        //}
        return bestRoscoe;

    }



    /*@Nullable
    ItemStack getBestToomFromInventory(Entity e, Player p) {
        PlayerInventory inv = p.getPositionInInventory();
        ItemStack[] items = inventoryToArray(p);
    }*/


    /**
     * Gets the slot number of a given ItemStack
     * @param item ItemStack that we need the slot number of
     * @param inv Player's inventory
     * @return slot number or -1 if not found
     */
    int getPositionInInventory(@NotNull ItemStack item, @NotNull PlayerInventory inv) {
        for(int i = 0; i < Objects.requireNonNull(inv,"Inventory must not be null").getSize(); i++) {
            ItemStack currentItem = inv.getItem(i);
            if(currentItem==null) continue;
            if(currentItem.equals(Objects.requireNonNull(item,"Item must not be null"))) {
                Log.debug(String.format("Found perfect tool %s at slot %d",currentItem.getType().name(),i));
                return i;
            }
        }
        return -1;
    }

    /**
     * Moves a tool to the given slot
     * @param source Slot where the tool is
     * @param dest Slot where the tool should be
     * @param inv Player's inventory
     */
    void moveToolToSlot(int source, int dest, @NotNull PlayerInventory inv) {
        Log.debug(String.format("Moving item from slot %d to %d",source,dest));
        inv.setHeldItemSlot(dest);
        if(source==dest) return;
        ItemStack sourceItem = inv.getItem(source);
        ItemStack destItem = inv.getItem(dest);
        if(source < hotbarSize) {
            inv.setHeldItemSlot(source);
            return;
        }
        if(destItem == null) {
            inv.setItem(dest,sourceItem);
            inv.setItem(source,null);
        } else {
            inv.setItem(source, destItem);
            inv.setItem(dest, sourceItem);
        }
    }

    boolean isDamageable(ItemStack item) {
        if(item==null) return false;
        if(!item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if( meta instanceof Damageable) {
            Log.debug(item.getType().name() + " is damageable");
            return true;
        } else {
            Log.debug(item.getType().name() + " is NOT damageable");
            return false;
        }
    }

    /**
     * Tries to free the slot if it is occupied with a damageable item
     * @param source Slot to free
     * @param inv Player's inventory
     */
    void freeSlot(int source, @NotNull PlayerInventory inv) {

        if(inv.getItemInMainHand()==null) return; // IntelliJ says this is always false

        if(!isDamageable(inv.getItemInMainHand())) return;

        ItemStack item =inv.getItem(source);

        // If current slot is empty, we don't have to change it
        if(item == null) return;

        // If the item is not damageable, we don't have to move it
        if(!isDamageable(item)) return;

        Log.debug(String.format("Trying to free slot %d",source));

        // Try to combine the item with existing stacks
        inv.setItem(source, null);
        inv.addItem(item);

        // If the item was moved to the same slot, we have to move it somewhere else
        if(inv.getItem(source)==null) {
            Log.debug("Freed slot");
            inv.setHeldItemSlot(source);
            return;
        }
        Log.debug("Could not free slot yet...");
        for(int i = source; i < inventorySize; i++) {
            if(inv.getItem(i)==null) {
                inv.setItem(i,item);
                inv.setItem(source,null);
                inv.setHeldItemSlot(source);
                Log.debug("Freed slot on second try");
                return;
            }
        }

        Log.debug("WARNING: COULD NOT FREE SLOT AT ALL");

        for(int i = 0; i < hotbarSize ; i++) {
            if(inv.getItem(i) == null || !isDamageable(inv.getItem(i))) {
                Log.debug("Found not damageable item at slot "+i);
                inv.setHeldItemSlot(i);
            }
        }
    }

}
