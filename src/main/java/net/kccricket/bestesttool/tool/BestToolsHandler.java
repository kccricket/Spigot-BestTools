package net.kccricket.bestesttool.tool;

import net.kccricket.bestesttool.BestestToolPlugin;
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
import java.util.function.BooleanSupplier;
import net.kccricket.bestesttool.benchmark.BenchmarkManager;
import net.kccricket.bestesttool.benchmark.BenchmarkWorkload;
import net.kccricket.bestesttool.listeners.BestToolsListener;

/**
 * This will probably be a separate plugin called BestTool or something
 */
public class BestToolsHandler {

    final BestestToolPlugin main;
    boolean debug = false;
    boolean verbose = true;

    public static final int hotbarSize = 9;
    public static final int inventorySize = 36;

    final HashMap<Material,Tool> toolMap = new HashMap<>();
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
    // per-region Folia threads, and discarded whenever BestestToolPlugin.load() rebuilds this handler.
    private final Map<Material, Boolean> silkMattersCache = new ConcurrentHashMap<>();
    // Built once here, not as a static field: EnchantmentUtils.getEnchantment reads
    // Registry.ENCHANTMENT, which isn't populated until the server is up, and this constructor
    // already only ever runs after that point (BestestToolPlugin.load() constructs BestToolsHandler on enable
    // /reload).
    private final ItemStack silkProbe;

    final ArrayList<Material> weapons = new ArrayList<>();

    public BestToolsHandler(BestestToolPlugin main) {

        this.main=Objects.requireNonNull(main,"BestestToolPlugin must not be null");

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

    public boolean isTool(ItemStack i) {
        return allTools.contains(i.getType());
    }
    boolean isToolOrRoscoe(ItemStack i) {
        return allTools.contains(i.getType()) || swords.contains(i.getType());
    }

    public enum Tool {
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
    public Tool getBestToolType(@NotNull Material mat) {
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
     * Filters mining-tool candidates independent of live mining speed — currently just the
     * per-player preferences (see {@link SwordPolicy}) that keep swords out of leaf/cobweb
     * selection unless explicitly enabled. Everything else (is this item even fast at this block)
     * is answered by {@link BlockData#getDestroySpeed} itself: an irrelevant item (e.g. a sword
     * against stone) has no matching vanilla tool rule and scores no better than a bare hand, so
     * it never needs an explicit category filter here.
     */
    boolean isCandidate(ItemStack item, Material target, SwordPolicy policy) {
        if(!swords.contains(item.getType())) return true;
        if(LeavesUtils.isLeaves(target)) return policy.forLeaves();
        if(target == Material.COBWEB) return policy.forCobwebs();
        return true;
    }

    /** Live read of {@code global_block_blacklist}, parsed and cached once per load/reload in {@link net.kccricket.bestesttool.config.MainConfig}. */
    public boolean isGloballyBlacklisted(Material mat) {
        return main.configManager.main().getGlobalBlockBlacklist().contains(mat);
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
    public boolean shouldKeepHeldItem(@NotNull ItemStack currentItem, Material target) {
        if(!isToolOrRoscoe(currentItem)) return true;
        return instaBreakableByHand.contains(target) && !hoes.contains(currentItem.getType());
    }

    /**
     * Slot holding the best stand-in for a bare hand: a genuinely empty hotbar slot if there is
     * one, otherwise the first non-damageable item (won't take wear standing in for a hand).
     * Returns -1 if neither exists. {@code items} must come from {@link #inventoryToArray}, whose
     * array index maps 1:1 to inventory slot.
     */
    public int getBareHandSlot(@NotNull PlayerInventory inv, @NotNull ItemStack[] items) {
        int empty = getEmptyHotbarSlot(inv);
        if(empty != -1) return empty;
        for(int i = 0; i < items.length; i++) {
            if(items[i] != null && !isDamageable(items[i])) return i;
        }
        return -1;
    }

    /**
     * Whether picking up {@code stack} could change what {@link #getBestToolFromInventory} or
     * {@link #getBareHandSlot} would answer — used by {@code BestToolsCacheListener} to decide
     * whether a pickup must invalidate the per-player cache. True if the stack is itself a new
     * selection candidate (tool or sword), or if the hotbar still has an empty slot the pickup
     * could consume, since {@code getBareHandSlot} prefers a genuinely empty hotbar slot over any
     * stand-in item. Called before the item is added to the inventory (see
     * {@code EntityPickupItemEvent}), so the empty-slot check reads the correct pre-pickup state.
     */
    public boolean pickupCouldAffectSelection(@NotNull ItemStack stack, @NotNull PlayerInventory inv) {
        return isToolOrRoscoe(stack) || getEmptyHotbarSlot(inv) != -1;
    }

    public boolean hasSilktouch(ItemStack item) {
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
     * @param avoidBreaking When true, an item one hit from breaking (see {@link #isAboutToBreak})
     *              is skipped entirely. Unlike {@code trySilktouch}, there is no fallback retry
     *              here for mining: if excluding near-broken items leaves nothing, {@link
     *              #selectBestTool} returns {@code null} and the caller falls through to its
     *              existing bare-hand path ({@link BestToolsSelector.Outcome#BARE_HAND}) instead
     *              of handing the player the near-broken item. Combat's equivalent,
     *              {@link #getBestRoscoeFromArray}, deliberately keeps the old retry-and-use-it-
     *              anyway behavior instead, since there's no bare-hand fallback in combat and
     *              going unarmed mid-fight is usually worse than one more hit with a nearly-spent
     *              weapon.
     */
    @Nullable
    ItemStack getBestItemStackFromArray(@NotNull BlockData data, @NotNull ItemStack[] items, boolean trySilktouch,
                                         @NotNull Material target, float floor, @NotNull SwordPolicy policy,
                                         boolean avoidBreaking) {

        boolean needsCorrect = data.requiresCorrectToolForDrops();

        ItemStack bestAny = null;
        float bestAnySpeed = floor;
        ItemStack bestCorrect = null;
        float bestCorrectSpeed = floor;

        for(ItemStack item : items) {
            if(item==null) continue; // IntelliJ says this is always false
            if(avoidBreaking && isAboutToBreak(item)) continue;

            if(trySilktouch && (!isToolOrRoscoe(item) || !hasSilktouch(item))) continue;
            if(!isCandidate(item,target,policy)) continue;

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
                return getBestItemStackFromArray(data,items,false,target,1.0f,policy,avoidBreaking);
            } else {
                return null;
            }
        }
        return needsCorrect && bestCorrect != null ? bestCorrect : bestAny;
    }

    /**
     * Whether {@code item} would break the next time it's used — damageable, and one point of
     * durability away from its current max. Used by {@code avoidBreakingTools} to keep BestTools
     * from handing a player a tool/weapon that's about to snap when a healthier alternative
     * exists. A pristine item's meta still implements {@link Damageable} for a damageable
     * material (the interface comes from the {@link Material}, not from whether a component patch
     * is persisted) and {@code getDamage()} defaults to {@code 0}, so this correctly answers
     * {@code false} for anything not actually near its limit.
     */
    boolean isAboutToBreak(@NotNull ItemStack item) {
        if (!(item.getItemMeta() instanceof Damageable damageable)) return false;
        int maxDurability = damageable.hasMaxDamage() ? damageable.getMaxDamage() : item.getType().getMaxDurability();
        return maxDurability > 0 && maxDurability - damageable.getDamage() <= 1;
    }

    @Nullable
    ItemStack getBestRoscoeFromArray(@NotNull ItemStack[] items, ItemStack currentItem, EntityType enemy, boolean useAxe, boolean avoidBreaking) {
        ItemStack best = getBestRoscoeFromArrayPass(items, currentItem, enemy, useAxe, avoidBreaking);
        if (best == null && avoidBreaking) {
            // Excluding near-broken items left nothing at all — retry once allowing them, so the
            // player still gets a weapon instead of being left with whatever's already in hand
            // just because every candidate is almost spent.
            best = getBestRoscoeFromArrayPass(items, currentItem, enemy, useAxe, false);
        }
        return best;
    }

    private ItemStack getBestRoscoeFromArrayPass(ItemStack[] items, ItemStack currentItem, EntityType enemy, boolean useAxe, boolean avoidBreaking) {

        ArrayList<ItemStack> list = new ArrayList<>();
        for(ItemStack item : items) {
            if(item==null) continue; // IntelliJ says this is always false
            if(avoidBreaking && isAboutToBreak(item)) continue;

            if(isRoscoe(item,useAxe)) {
                list.add(item);
            }
        }
        if(list.size()==0) {
            return null;
        }
        list.sort((o1, o2) -> Double.compare(SwordUtils.getDamage(o2,enemy), SwordUtils.getDamage(o1,enemy)));
        return list.get(0);
    }

    // Roscoes are only axes and swords, weapons are roscoes + bow, crossbow, etc
    private boolean isRoscoe(ItemStack item, boolean useAxe) {
         return useAxe ?
                 swords.contains(item.getType())
                         || axes.contains(item.getType())
                 : swords.contains(item.getType());
    }


    public ItemStack[] inventoryToArray(Player p,boolean hotbarOnly) {
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
    ItemStack getBestToolFromInventory(@NotNull Block block, Player p, boolean hotbarOnly, @NotNull SwordPolicy policy, boolean avoidBreaking) {
        ItemStack[] items = inventoryToArray(p,hotbarOnly);
        return selectBestTool(block.getBlockData(), block.getType(), items, () -> silkChangesDrops(block), policy, avoidBreaking);
    }

    /**
     * The pure ranking core of {@link #getBestToolFromInventory}: no {@link Player}, no
     * {@link Block}, no world access — just {@code data}/{@code mat} (both readable off a
     * {@link Block} up front) and an inventory snapshot. Split out so it can be driven directly by
     * a synthetic workload (see {@code BenchmarkWorkload}/{@code BenchmarkManager}), which has no
     * real block or inventory to read from.
     * <p>
     * {@code silkChangesDrops} stays lazy (a supplier, not a precomputed {@code boolean}) because
     * it is only ever evaluated on the fallback path, once nothing has already beaten a bare hand —
     * evaluating it eagerly would call {@link Block#getDrops(ItemStack)} on every block interaction.
     * In production this is {@link #silkChangesDrops(Block)}, already memoized per {@link Material}
     * by {@link #silkMattersCache}; the benchmark instead passes a constant.
     * <p>
     * When {@code avoidBreaking} excludes every candidate (including on the Silk Touch pass) this
     * returns {@code null} rather than retrying with it off — see
     * {@link #getBestItemStackFromArray}'s {@code avoidBreaking} doc for why mining deliberately
     * does not fall back to a near-broken item the way {@link #getBestRoscoeFromArray} does.
     */
    @Nullable
    public ItemStack selectBestTool(@NotNull BlockData data, @NotNull Material mat, @NotNull ItemStack[] items,
                              @NotNull BooleanSupplier silkChangesDrops, @NotNull SwordPolicy policy, boolean avoidBreaking) {
        ItemStack bestStack = getBestItemStackFromArray(data,items,profitsFromSilkTouch(mat),mat,1.0f,policy,avoidBreaking);
        if(bestStack!=null) {
            Log.debug("bestStack is "+bestStack.toString());
            return bestStack;
        }
        Log.debug("bestStack is null");
        if(silkChangesDrops.getAsBoolean()) {
            ItemStack silkStack = getBestItemStackFromArray(data,items,true,mat,0.0f,policy,avoidBreaking);
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
    public ItemStack getBestRoscoeFromInventory(@NotNull EntityType enemy, Player p, boolean hotbarOnly, ItemStack currentItem, boolean useAxe, boolean avoidBreaking) {
        ItemStack[] items = inventoryToArray(p,hotbarOnly);

        return getBestRoscoeFromArray(items,currentItem,enemy,useAxe,avoidBreaking);

    }

    /**
     * Gets the slot number of a given ItemStack
     * @param item ItemStack that we need the slot number of
     * @param inv Player's inventory
     * @return slot number or -1 if not found
     */
    public int getPositionInInventory(@NotNull ItemStack item, @NotNull PlayerInventory inv) {
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
    public void moveToolToSlot(int source, int dest, @NotNull PlayerInventory inv) {
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

    public boolean isDamageable(ItemStack item) {
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
    public void freeSlot(int source, @NotNull PlayerInventory inv) {

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
