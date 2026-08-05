package net.kccricket.bestesttool.selftest;

import net.kccricket.bestesttool.BestestToolPlugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import net.kccricket.bestesttool.listeners.BestToolsListener;
import net.kccricket.bestesttool.listeners.RefillListener;
import net.kccricket.bestesttool.model.PlayerSetting;

/**
 * The self-test's event side: an observer that records verdicts (never itself deciding what the
 * plugin should do), plus arena protection so a test run doesn't consume the blocks/mobs it built.
 * <p>
 * Registered fresh on every {@link BestestToolPlugin#load}, same as {@code BestToolsListener} — but the
 * {@link SelfTestManager} it reports to (and thus every in-progress {@link SelfTestSession}) is
 * owned by {@code BestestToolPlugin} directly and survives a reload; only this listener object is rebuilt.
 * <p>
 * Priorities matter here: the {@code LOWEST}/{@code MONITOR} pair around
 * {@link org.bukkit.event.player.PlayerInteractEvent} brackets {@code BestToolsListener}'s own
 * {@code NORMAL}-priority switch, so this always reads the tool the plugin actually picked rather
 * than racing it. The {@code HIGHEST} block-break/damage handlers cancel the outcome (protecting
 * the arena) without touching {@code BestToolsListener}'s own switching logic, which runs at
 * {@code NORMAL} on the interact/attack event itself, not on break/damage.
 */
public final class SelfTestListener implements Listener {

    private final BestestToolPlugin main;
    private final SelfTestManager manager;

    SelfTestListener(BestestToolPlugin main, SelfTestManager manager) {
        this.main = main;
        this.manager = manager;
    }

    // -------------------------------------------------------------------------
    // Mining (BLOCKS stage kind) — brackets BestToolsListener's own switch
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractBefore(PlayerInteractEvent event) {
        SelfTestSession session = matchingBlockCase(event);
        if (session == null) return;

        // Defeat PlayerSetting's per-block-type cache so BestToolsListener always re-evaluates —
        // otherwise a repeated block type from a previous case (or a previous run) could short-
        // circuit the switch entirely. See BestToolsListener.onPlayerInteractWithBlock.
        main.getPlayerSetting(event.getPlayer()).getBtcache().invalidated();
        session.pendingBeforeSnapshot = event.getPlayer().getInventory().getItemInMainHand().clone();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteractAfter(PlayerInteractEvent event) {
        SelfTestSession session = matchingBlockCase(event);
        if (session == null || session.pendingBeforeSnapshot == null) return;

        SelfTestSpec.Case c = session.currentCase();
        ItemStack before = session.pendingBeforeSnapshot;
        ItemStack after = event.getPlayer().getInventory().getItemInMainHand();
        session.pendingBeforeSnapshot = null;

        boolean pass = SelfTestEvaluator.matches(c.expectation, before, after, main.toolHandler.isDamageable(after))
                && (!c.requireSilk || main.toolHandler.hasSilktouch(after));

        String expectedDesc = c.expectation.describe() + (c.requireSilk ? " (with Silk Touch)" : "");
        manager.recordResult(session, pass, c.subjectName(), expectedDesc, SelfTestEvaluator.describe(after));
    }

    /** The tester's session, only if this event is a left-click on the current BLOCKS case's block. */
    private SelfTestSession matchingBlockCase(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK) return null;
        if (event.getHand() != EquipmentSlot.HAND) return null;
        if (event.getClickedBlock() == null) return null;

        SelfTestSession session = manager.sessionFor(event.getPlayer());
        if (session == null || session.arena == null) return null;
        SelfTestSpec.Stage stage = session.currentStage();
        if (stage == null || stage.kind != SelfTestSpec.StageKind.BLOCKS) return null;

        int idx = session.arena.caseIndexAt(event.getClickedBlock().getLocation());
        return idx == session.caseIndex ? session : null;
    }

    /** Keeps the arena block from actually being consumed once BestToolsListener has already switched. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onBreakProtectArena(BlockBreakEvent event) {
        SelfTestSession session = manager.sessionFor(event.getPlayer());
        if (session == null || session.arena == null) return;
        if (session.arena.caseIndexAt(event.getBlock().getLocation()) != -1) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Combat (COMBAT stage kind)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        SelfTestSession session = manager.sessionFor(player);
        if (session == null || session.arena == null) return;
        SelfTestSpec.Stage stage = session.currentStage();
        if (stage == null || stage.kind != SelfTestSpec.StageKind.COMBAT) return;

        if (session.arena.caseIndexOf(event.getEntity()) != session.caseIndex) return;

        SelfTestSpec.Case c = session.currentCase();
        ItemStack after = player.getInventory().getItemInMainHand();
        boolean pass = SelfTestEvaluator.matches(c.expectation, null, after, main.toolHandler.isDamageable(after));
        manager.recordResult(session, pass, c.subjectName(), c.expectation.describe(), SelfTestEvaluator.describe(after));
    }

    /** Keeps the self-test's mobs alive across repeated hits. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onAttackProtectArena(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        SelfTestSession session = manager.sessionFor(player);
        if (session == null || session.arena == null) return;
        if (session.arena.caseIndexOf(event.getEntity()) != -1) {
            event.setCancelled(true);
        }
    }

    // -------------------------------------------------------------------------
    // Refill (REFILL stage kind)
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        SelfTestSession session = manager.sessionFor(player);
        if (session == null || session.arena == null) return;
        SelfTestSpec.Stage stage = session.currentStage();
        if (stage == null || stage.kind != SelfTestSpec.StageKind.REFILL) return;

        Location loc = event.getBlockPlaced().getLocation();
        if (session.arena.caseIndexAt(loc) != session.caseIndex) return;

        SelfTestSpec.Case c = session.currentCase();
        if (event.getBlockPlaced().getType() != c.blockSubject) return;

        // RefillListener's own refill runs via player.getScheduler().run(...) off the back of this
        // same event — give it a couple of ticks to land before reading the outcome.
        SelfTestArena expectedArena = session.arena;
        int expectedCaseIndex = session.caseIndex;
        player.getScheduler().runDelayed(main, task -> {
            if (session.arena != expectedArena || session.caseIndex != expectedCaseIndex) return;
            ItemStack after = player.getInventory().getItemInMainHand();
            boolean pass = SelfTestEvaluator.matches(c.expectation, null, after, main.toolHandler.isDamageable(after));
            manager.recordResult(session, pass, c.subjectName(), c.expectation.describe(), SelfTestEvaluator.describe(after));
            // Keep the marker spot clean for a retry / the next case, rather than waiting for stage teardown.
            loc.getBlock().setType(Material.AIR, false);
        }, null, 2L);
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.onQuit(event.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (SelfTestSession.restoreOrphanedBackup(main, event.getPlayer())) {
            main.messages().to(event.getPlayer()).status().send("selfTestBackupRestored");
        }
    }
}
