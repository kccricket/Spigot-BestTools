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
import java.util.concurrent.ConcurrentHashMap;

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

    // Blocks BestTools must never switch tools/hands for, full stop: bedrock-class blocks that no
    // tool can break or drop (hardness < 0, plus REINFORCED_DEEPSLATE which doesn't fit that rule
    // but still drops nothing regardless of tool), and DECORATED_POT, where the held item is the
    // player's own choice between an intact pot and 4 sherds. See BestToolsListener.onPlayerInteractWithBlock.
    final EnumSet<Material> neverSwitch = EnumSet.noneOf(Material.class);

    // Per-Material memo of silkChangesDrops(); see that method. Not an EnumMap: mutated from
    // per-region Folia threads, and discarded whenever Main.load() rebuilds this handler.
    private final Map<Material, Boolean> silkMattersCache = new ConcurrentHashMap<>();
    // Built once here, not as a static field: EnchantmentUtils.getEnchantment reads
    // Registry.ENCHANTMENT, which isn't populated until the server is up, and this constructor
    // already only ever runs after that point (Main.load() constructs BestToolsHandler on enable
    // /reload).
    private final ItemStack silkProbe;

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

        Arrays.stream(Material.values()).forEach(material -> {
            if(material.isLegacy() || !material.isBlock()) return;
            if(material.getHardness() < 0) {
                neverSwitch.add(material);
            }
        });
        neverSwitch.add(Material.REINFORCED_DEEPSLATE); // hardness 55, but drops nothing regardless of tool
        neverSwitch.add(Material.DECORATED_POT); // held item decides intact pot vs. sherds; respect the player's choice

        silkProbe = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta silkMeta = silkProbe.getItemMeta();
        silkMeta.addEnchant(EnchantmentUtils.getEnchantment("silk_touch"), 1, true);
        silkProbe.setItemMeta(silkMeta);
    }

    boolean isNeverSwitch(Material mat) {
        return neverSwitch.contains(mat);
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
            case BEEHIVE: // Silk Touch keeps the bees and honey level; a plain axe releases angry bees
            case BEE_NEST:
            case AMETHYST_CLUSTER: // buds/clusters drop nothing at all without Silk Touch
            case SMALL_AMETHYST_BUD:
            case MEDIUM_AMETHYST_BUD:
            case LARGE_AMETHYST_BUD:
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

    /**
     * Whether it's even worth switching off the currently-held item when nothing beats a bare
     * hand. True if the current item is already effectively a bare hand (not a tool/roscoe), or if
     * {@code target} insta-breaks and the held item isn't a hoe (insta-breaks don't cost durability
     * except on hoes, so churning the hotbar for a torch or a flower gains nothing).
     */
    boolean shouldKeepHeldItem(@NotNull ItemStack currentItem, Material target) {
        if(!isToolOrRoscoe(currentItem)) return true;
        return instaBreakableByHand.contains(target) && !hoes.contains(currentItem.getType());
    }

    /**
     * Slot holding the best stand-in for a bare hand: a genuinely empty hotbar slot if there is
     * one, otherwise the first non-damageable item (won't take wear standing in for a hand).
     * Returns -1 if neither exists. {@code items} must come from {@link #inventoryToArray}, whose
     * array index maps 1:1 to inventory slot.
     */
    int getBareHandSlot(@NotNull PlayerInventory inv, @NotNull ItemStack[] items) {
        int empty = getEmptyHotbarSlot(inv);
        if(empty != -1) return empty;
        for(int i = 0; i < items.length; i++) {
            if(items[i] != null && !isDamageable(items[i])) return i;
        }
        return -1;
    }

    boolean hasSilktouch(ItemStack item) {
        if(item==null) return false;
        if(!item.hasItemMeta()) return false;
        return item.getItemMeta().hasEnchant(EnchantmentUtils.getEnchantment("silk_touch"));
    }

    /**
     * Ranks inventory items against a block's live mining data and returns the best one, or
     * {@code null} if nothing beats {@code floor}.
     * <p>
     * Ranked by {@code (isPreferredTool desc, getDestroySpeed desc)} — drops beat speed. {@link
     * BlockData#getDestroySpeed} alone is not enough: an Efficiency V iron pickaxe outscores a
     * plain diamond pickaxe on obsidian, but iron doesn't drop obsidian. {@link
     * BlockData#isPreferredTool} is only consulted when {@link BlockData#requiresCorrectToolForDrops()}
     * is true, and only for a candidate that's already the fastest seen so far — for the common
     * case (dirt, wood, leaves, wool) that's zero calls; for ores, typically one to three.
     * @param floor A candidate must score strictly above this to be worth switching to. Normally
     *              {@code 1.0} (bare-hand speed): a switch has to actually be faster to be worth
     *              it. The Silk Touch pass ({@code trySilktouch}) is called with {@code 0.0}
     *              instead, since on a block where Silk Touch is the only way to get a drop at all
     *              (see {@link #silkChangesDrops}) the enchant is the point, not the speed.
     */
    @Nullable
    ItemStack getBestItemStackFromArray(@NotNull BlockData data, @NotNull ItemStack[] items, boolean trySilktouch, @NotNull Material target, float floor) {

        boolean needsCorrect = data.requiresCorrectToolForDrops();

        ItemStack bestAny = null;
        float bestAnySpeed = floor;
        ItemStack bestCorrect = null;
        float bestCorrectSpeed = floor;

        for(ItemStack item : items) {
            if(item==null) continue; // IntelliJ says this is always false
            // TODO: Check if durability is 1

            if(trySilktouch && (!isToolOrRoscoe(item) || !hasSilktouch(item))) continue;
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
                return getBestItemStackFromArray(data,items,false,target,1.0f);
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
     * Whether Silk Touch is the difference between getting a drop from {@code block} at all and
     * getting nothing (e.g. glass, sea lantern, coral, turtle eggs) — answered from the block's
     * live loot table rather than a hardcoded list, so it covers every such block including
     * datapack-defined ones. Only meaningful (and only called) on the fallback path in {@link
     * #getBestToolFromInventory}, once nothing has already beaten a bare hand: silk also changes
     * the drop for ordinary stone/ore/grass blocks, but those always have a real tool that beats a
     * bare hand and so never reach this check — a Fortune pickaxe still wins on ores as before.
     * <p>
     * {@link Block#getDrops(ItemStack)} rolls the loot table — its javadoc warns results aren't
     * stable across calls — so the comparison is by material set, not exact stacks, and the
     * verdict is memoized per {@link Material} to pin down an answer and avoid re-rolling the loot
     * table on every interaction. Caveats worth knowing: the memo ignores block state (e.g. a
     * cracked decorated pot — moot, since {@code DECORATED_POT} is in {@link #neverSwitch}
     * anyway), and for a block whose non-silk drop is itself random (ferns, candles, sea pickles,
     * sweet berry bushes) the first roll decides the cached verdict for the session; the worst
     * case there is switching to a Silk Touch tool instead of a bare hand, costing a point of
     * durability.
     */
    boolean silkChangesDrops(@NotNull Block block) {
        return silkMattersCache.computeIfAbsent(block.getType(), mat ->
                !dropMaterials(block.getDrops(null)).equals(dropMaterials(block.getDrops(silkProbe))));
    }

    static Set<Material> dropMaterials(@NotNull Collection<ItemStack> drops) {
        Set<Material> materials = EnumSet.noneOf(Material.class);
        for(ItemStack drop : drops) {
            materials.add(drop.getType());
        }
        return materials;
    }

    /**
     * Tries to get the ItemStack that is the best for this block, ranked by live mining data
     * ({@link BlockData#getDestroySpeed}/{@link BlockData#isPreferredTool}) rather than the static
     * {@code toolMap} — see {@link #getBestItemStackFromArray}. This also covers leaves and cobweb
     * natively (shears/hoe/sword are simply whichever candidate scores highest), replacing what
     * used to be a separate {@code LeavesUtils}-driven branch here.
     * <p>
     * Returns {@code null} if nothing in the inventory is worth switching to — the caller should
     * fall back to a bare hand (see {@link #shouldKeepHeldItem}/{@link #getBareHandSlot}).
     * @param block The block being mined
     * @param p Player
     * @return
     */
    @Nullable
    ItemStack getBestToolFromInventory(@NotNull Block block, Player p, boolean hotbarOnly) {
        ItemStack[] items = inventoryToArray(p,hotbarOnly);
        Material mat = block.getType();
        BlockData data = block.getBlockData();

        ItemStack bestStack = getBestItemStackFromArray(data,items,profitsFromSilkTouch(mat),mat,1.0f);
        if(bestStack!=null) {
            Log.debug("bestStack is "+bestStack.toString());
            return bestStack;
        }
        Log.debug("bestStack is null");
        if(silkChangesDrops(block)) {
            ItemStack silkStack = getBestItemStackFromArray(data,items,true,mat,0.0f);
            if(silkStack!=null) {
                Log.debug("silkStack is "+silkStack.toString());
                return silkStack;
            }
        }
        return null;
    }

    /**
     * Tries to get the roscoe that is the best for this block
     * @param p Player
     * @return
     */
    @Nullable
    ItemStack getBestRoscoeFromInventory(@NotNull EntityType enemy, Player p, boolean hotbarOnly, ItemStack currentItem, boolean useAxe) {
        ItemStack[] items = inventoryToArray(p,hotbarOnly);

        return getBestRoscoeFromArray(items,currentItem,enemy,useAxe);

    }

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
        // A pristine tool (unenchanted, undamaged) carries no component patch, so hasItemMeta()
        // is false for it — checking that first (as this used to) misclassifies every unused tool
        // as non-damageable. getMaxDurability() reflects the material itself, not the item's
        // current meta, so it's correct regardless of whether the stack has been touched yet.
        if(item.getType().getMaxDurability() > 0) {
            Log.debug(item.getType().name() + " is damageable");
            return true;
        }
        // 1.20.5+ lets a datapack/plugin attach a max_damage component to an otherwise
        // non-damageable material; catch that case too.
        if(item.hasItemMeta() && item.getItemMeta() instanceof Damageable damageable && damageable.hasMaxDamage()) {
            Log.debug(item.getType().name() + " is damageable (custom max_damage component)");
            return true;
        }
        Log.debug(item.getType().name() + " is NOT damageable");
        return false;
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
