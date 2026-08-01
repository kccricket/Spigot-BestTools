package net.kccricket.bestesttool.tool;

import net.kccricket.bestesttool.BestestToolPlugin;
import net.kccricket.kcmclib.logging.Log;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.Nullable;
import net.kccricket.bestesttool.listeners.BestToolsListener;
import net.kccricket.bestesttool.model.Blacklist;
import net.kccricket.bestesttool.model.PlayerSetting;
import net.kccricket.bestesttool.util.PlayerUtils;

/**
 * The gating + selection decision for a single block interaction, pulled out of
 * {@link BestToolsListener#onPlayerInteractWithBlock} so the "what should happen" logic can be
 * reasoned about independent of the event plumbing and the actual hotbar mutation.
 * <p>
 * Deliberately left in the listener: the {@code btcache} short-circuit, the permission check,
 * {@code hasBestToolsEnabled} (it sends a message and mutates player-visible state), and all of
 * {@code switchToBestTool}/{@code switchToBareHand} (the actual inventory mutation).
 */
public final class BestToolsSelector {

    private BestToolsSelector() {}

    public enum Outcome {
        /** Switch to {@link ToolDecision#tool()}. */
        SWITCH,
        /** Nothing in the inventory beat a bare hand for this block — try switching to an actual bare hand. */
        BARE_HAND,
        /** Leave the held item alone, but the block type is a stable answer — the cache should still be validated. */
        NO_CHANGE,
        /** Bail out entirely (blacklisted, wrong gamemode, mid-battle, wrong action/hand, ...) — do not touch the cache. */
        NOT_APPLICABLE
    }

    public record ToolDecision(Outcome outcome, @Nullable ItemStack tool) {
        public static final ToolDecision NOT_APPLICABLE_DECISION = new ToolDecision(Outcome.NOT_APPLICABLE, null);
        private static final ToolDecision NO_CHANGE_DECISION = new ToolDecision(Outcome.NO_CHANGE, null);
        private static final ToolDecision BARE_HAND_DECISION = new ToolDecision(Outcome.BARE_HAND, null);

        private static ToolDecision switchTo(ItemStack tool) {
            return new ToolDecision(Outcome.SWITCH, tool);
        }
    }

    /**
     * Decides what BestTools should do about {@code block}, given {@code p}'s inventory and
     * settings. Mirrors the gating order {@code onPlayerInteractWithBlock} used to run inline:
     * null/AIR &gt; global blacklist &gt; per-player blacklist &gt; never-switch &gt; gamemode &gt;
     * mid-battle (a per-player preference, {@link PlayerSetting#isSwitchDuringBattle()} — not a
     * combat feature, stays available regardless of {@code allow_combat_switching}) &gt;
     * action/hand &gt; actual selection. Only the never-switch branch is a
     * {@link Outcome#NO_CHANGE} (cache still validated); every other early exit is
     * {@link Outcome#NOT_APPLICABLE} (cache left untouched), exactly as before.
     */
    public static ToolDecision decide(BestestToolPlugin main, BestToolsHandler handler, Player p, PlayerSetting playerSetting,
                                @Nullable Block block, Action action, EquipmentSlot hand) {
        if (block == null) return ToolDecision.NOT_APPLICABLE_DECISION;

        Material mat = block.getType();
        if (mat == Material.AIR) return ToolDecision.NOT_APPLICABLE_DECISION;

        if (handler.isGloballyBlacklisted(mat)) return ToolDecision.NOT_APPLICABLE_DECISION;

        // Blacklist
        if (playerSetting.getBlacklist().contains(mat)) return ToolDecision.NOT_APPLICABLE_DECISION;

        if (main.toolHandler.isNeverSwitch(mat)) {
            // No tool can break/drop this (bedrock, reinforced deepslate, ...), or the held item
            // is the player's own choice to make (decorated pots) — leave the hand alone.
            return ToolDecision.NO_CHANGE_DECISION;
        }

        if (!PlayerUtils.isAllowedGamemode(p, main.configManager.main().getAllowInAdventureMode())) {
            return ToolDecision.NOT_APPLICABLE_DECISION;
        }

        PlayerInventory inv = p.getInventory();
        if (!playerSetting.isSwitchDuringBattle() && handler.isWeapon(inv.getItemInMainHand())) {
            Log.debug("Return: It's a gun^^");
            return ToolDecision.NOT_APPLICABLE_DECISION;
        }

        if (action != Action.LEFT_CLICK_BLOCK) return ToolDecision.NOT_APPLICABLE_DECISION;
        if (hand != EquipmentSlot.HAND) return ToolDecision.NOT_APPLICABLE_DECISION;

        ItemStack bestTool = handler.getBestToolFromInventory(block, p, playerSetting.isHotbarOnly(), SwordPolicy.from(playerSetting));
        return bestTool != null ? ToolDecision.switchTo(bestTool) : ToolDecision.BARE_HAND_DECISION;
    }
}
