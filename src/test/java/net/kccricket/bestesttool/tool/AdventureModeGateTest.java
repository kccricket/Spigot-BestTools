package net.kccricket.bestesttool.tool;

import net.kccricket.bestesttool.BestToolsTestBase;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.LivingEntityMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Coverage for {@code allow_in_adventure_mode} at all three call sites of
 * {@link net.kccricket.bestesttool.util.PlayerUtils#isAllowedGamemode} —
 * {@link net.kccricket.bestesttool.listeners.BestToolsListener#onPlayerAttackEntity},
 * {@link net.kccricket.bestesttool.listeners.RefillListener}, and {@link BestToolsSelector#decide}.
 * {@link net.kccricket.bestesttool.util.PlayerUtilsTest} already pins the pure gate itself; this
 * proves each call site actually reads it and is otherwise inert/active as expected.
 */
class AdventureModeGateTest extends BestToolsTestBase {

    // --- Attack site -----------------------------------------------------------------------

    @Test
    void attackSite_inertInAdventureModeByDefault() throws Exception {
        PlayerMock player = newPlayer();
        plugin.getPlayerSetting(player).toggleBestToolsEnabled();
        player.setGameMode(GameMode.ADVENTURE);

        PlayerInventory inv = player.getInventory();
        inv.setItem(0, new ItemStack(Material.WOODEN_SWORD));
        inv.setItem(1, new ItemStack(Material.IRON_SWORD));
        inv.setHeldItemSlot(0);

        Zombie zombie = player.getWorld().spawn(player.getLocation(), Zombie.class);
        ((LivingEntityMock) zombie).simulateDamage(1.0, player);

        assertEquals(0, inv.getHeldItemSlot(), "allow_in_adventure_mode defaults to false");
    }

    @Test
    void attackSite_activeInAdventureModeWhenAllowed() throws Exception {
        PlayerMock player = newPlayer();
        plugin.getPlayerSetting(player).toggleBestToolsEnabled();
        player.setGameMode(GameMode.ADVENTURE);
        rewriteConfig("allow_in_adventure_mode: false", "allow_in_adventure_mode: true");

        PlayerInventory inv = player.getInventory();
        inv.setItem(0, new ItemStack(Material.WOODEN_SWORD));
        inv.setItem(1, new ItemStack(Material.IRON_SWORD));
        inv.setHeldItemSlot(0);

        Zombie zombie = player.getWorld().spawn(player.getLocation(), Zombie.class);
        ((LivingEntityMock) zombie).simulateDamage(1.0, player);

        assertEquals(1, inv.getHeldItemSlot());
    }

    @Test
    void attackSite_inertInCreativeEvenWhenAdventureAllowed() throws Exception {
        // The flag must not leak into other non-survival gamemodes.
        PlayerMock player = newPlayer();
        plugin.getPlayerSetting(player).toggleBestToolsEnabled();
        player.setGameMode(GameMode.CREATIVE);
        rewriteConfig("allow_in_adventure_mode: false", "allow_in_adventure_mode: true");

        PlayerInventory inv = player.getInventory();
        inv.setItem(0, new ItemStack(Material.WOODEN_SWORD));
        inv.setItem(1, new ItemStack(Material.IRON_SWORD));
        inv.setHeldItemSlot(0);

        Zombie zombie = player.getWorld().spawn(player.getLocation(), Zombie.class);
        ((LivingEntityMock) zombie).simulateDamage(1.0, player);

        assertEquals(0, inv.getHeldItemSlot());
    }

    // --- Refill site -------------------------------------------------------------------------
    // Bottles/bowls are the one material RefillUtils#refillStack can actually complete through a
    // plain PlayerInteractEvent without the item having already been consumed to empty first (see
    // RefillUtils#moveBowlsAndBottles) — mirrors RefillUtilsTest's own fixture shape.

    private PlayerMock refillCandidatePlayer() {
        PlayerMock player = newPlayer();
        plugin.getPlayerSetting(player).setRefillEnabled(true);
        PlayerInventory inv = player.getInventory();
        inv.setItem(0, new ItemStack(Material.GLASS_BOTTLE, 1)); // the "just emptied" bottle in hand
        inv.setItem(5, new ItemStack(Material.GLASS_BOTTLE, 3)); // the stack to refill from
        inv.setHeldItemSlot(0);
        return player;
    }

    private void triggerRefill(PlayerMock player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
                mainHand, null, BlockFace.SELF, EquipmentSlot.HAND);
        plugin.refillListener.onInteract(event);
        server.getScheduler().performOneTick(); // refillStack defers its body one tick
    }

    @Test
    void refillSite_inertInAdventureModeByDefault() {
        PlayerMock player = refillCandidatePlayer();
        player.setGameMode(GameMode.ADVENTURE);

        triggerRefill(player);

        assertEquals(1, player.getInventory().getItemInMainHand().getAmount(),
                "allow_in_adventure_mode defaults to false");
    }

    @Test
    void refillSite_activeInAdventureModeWhenAllowed() throws Exception {
        PlayerMock player = refillCandidatePlayer();
        player.setGameMode(GameMode.ADVENTURE);
        rewriteConfig("allow_in_adventure_mode: false", "allow_in_adventure_mode: true");

        triggerRefill(player);

        assertEquals(3, player.getInventory().getItemInMainHand().getAmount());
    }

    // --- Selector (block-mining) site ---------------------------------------------------------

    private Block fakeBlock(PlayerMock player, Material material, Map<Material, Float> speeds) {
        Block block = player.getLocation().getBlock();
        block.setBlockData(new FakeBlockData(material, false, speeds, Set.of()));
        return block;
    }

    @Test
    void decide_notApplicableInAdventureModeByDefault() {
        PlayerMock player = newPlayer();
        player.setGameMode(GameMode.ADVENTURE);
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_PICKAXE));
        Block block = fakeBlock(player, Material.STONE, Map.of(Material.DIAMOND_PICKAXE, 8f));

        BestToolsSelector.ToolDecision decision = BestToolsSelector.decide(plugin, plugin.toolHandler, player,
                plugin.getPlayerSetting(player), block, Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND);

        assertEquals(BestToolsSelector.Outcome.NOT_APPLICABLE, decision.outcome());
    }

    @Test
    void decide_switchesInAdventureModeWhenAllowed() throws Exception {
        PlayerMock player = newPlayer();
        player.setGameMode(GameMode.ADVENTURE);
        rewriteConfig("allow_in_adventure_mode: false", "allow_in_adventure_mode: true");
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND_PICKAXE));
        Block block = fakeBlock(player, Material.STONE, Map.of(Material.DIAMOND_PICKAXE, 8f));

        BestToolsSelector.ToolDecision decision = BestToolsSelector.decide(plugin, plugin.toolHandler, player,
                plugin.getPlayerSetting(player), block, Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND);

        assertEquals(BestToolsSelector.Outcome.SWITCH, decision.outcome());
        assertEquals(Material.DIAMOND_PICKAXE, decision.tool().getType());
    }
}
