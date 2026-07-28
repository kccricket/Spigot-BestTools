package net.kccricket.bestesttool;

import net.kccricket.bestesttool.text.MessageUtil;
import net.kccricket.kcmclib.logging.Log;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates {@code /bestesttool selftest}: owns every tester's {@link SelfTestSession}, the
 * parsed {@link SelfTestSpec}, and the shared {@link SelfTestListener}.
 * <p>
 * Constructed exactly once, in {@link Main#onEnable} — not in {@link Main#load}, which re-runs on
 * every {@code /bestesttool reload} — so an in-progress test survives a reload; {@link #abortAll}
 * is called from {@code Main.load}'s reload branch just before the old listeners are torn down, so
 * the tester is put back the way they were rather than left mid-test with no observer attached.
 */
final class SelfTestManager {

    private final Main main;
    private final SelfTestListener listener;
    private final Map<UUID, SelfTestSession> sessions = new ConcurrentHashMap<>();
    private volatile SelfTestSpec spec;

    SelfTestManager(Main main) {
        this.main = main;
        this.listener = new SelfTestListener(main, this);
        reloadSpec();
    }

    /** Re-parses {@code selftest/stages.yml} (or its on-disk override). Safe to call mid-test — takes effect for the next stage/run. */
    void reloadSpec() {
        try {
            spec = SelfTestStages.load(main);
        } catch (RuntimeException e) {
            Log.warning("Failed to load self-test stages — /bestesttool selftest will report no stages configured.", e);
            spec = new SelfTestSpec(Material.SMOOTH_STONE, List.of());
        }
    }

    SelfTestListener listener() {
        return listener;
    }

    List<String> stageNames() {
        List<String> names = new ArrayList<>();
        for (SelfTestSpec.Stage stage : spec.stages) names.add(stage.name);
        return names;
    }

    SelfTestSession sessionFor(Player player) {
        return sessions.get(player.getUniqueId());
    }

    // -------------------------------------------------------------------------
    // start / next / status / stop
    // -------------------------------------------------------------------------

    void start(Player player, String stageName) {
        if (sessions.containsKey(player.getUniqueId())) {
            MessageUtil.send(player, "selfTestAlreadyRunning");
            return;
        }
        if (spec.stages.isEmpty()) {
            MessageUtil.send(player, "selfTestNoStagesConfigured");
            return;
        }

        int startIndex = 0;
        if (stageName != null) {
            startIndex = spec.indexOfStage(stageName);
            if (startIndex == -1) {
                MessageUtil.send(player, "selfTestUnknownStage", Placeholder.unparsed("stage", stageName));
                return;
            }
        }

        SelfTestSpec.Stage firstStage = spec.stage(startIndex);
        if (!SelfTestArena.hasSpace(player, firstStage)) {
            MessageUtil.send(player, "selfTestNeedsSpace",
                    Placeholder.unparsed("n", String.valueOf(requiredClearance(firstStage))));
            return;
        }

        SelfTestSession session = new SelfTestSession(main, player, spec);
        session.stageIndex = startIndex;
        session.totalCases = spec.stages.stream().mapToInt(s -> s.cases.size()).sum();
        sessions.put(player.getUniqueId(), session);
        beginStage(session);
    }

    void advance(Player player) {
        SelfTestSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            MessageUtil.send(player, "selfTestNoneRunning");
            return;
        }
        if (session.arena != null) {
            session.arena.teardown();
            session.arena = null;
        }
        session.stageIndex++;
        beginStage(session);
    }

    void status(Player player) {
        SelfTestSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            MessageUtil.send(player, "selfTestNoneRunning");
            return;
        }
        SelfTestSpec.Stage stage = session.currentStage();
        int remaining = stage == null ? 0 : stage.cases.size() - session.caseIndex;
        MessageUtil.send(player, "selfTestStatus",
                Placeholder.unparsed("stage", stage == null ? "-" : stage.name),
                Placeholder.unparsed("remaining", String.valueOf(remaining)));
    }

    /**
     * Ends {@code player}'s session, if any, restoring everything. {@code messageKey} is sent
     * afterward if non-null — pass {@code null} when a more specific message (e.g. "needs space")
     * was already sent, or to abort silently.
     */
    void stop(Player player, String messageKey) {
        SelfTestSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            if (messageKey != null) MessageUtil.send(player, "selfTestNoneRunning");
            return;
        }
        session.restoreAndClear(main);
        if (messageKey != null) MessageUtil.send(player, messageKey);
    }

    /** Ends every in-progress session — called on {@code /bestesttool reload}, {@code Main.onDisable}, and player quit. */
    void abortAll(String reason) {
        for (UUID id : List.copyOf(sessions.keySet())) {
            SelfTestSession session = sessions.remove(id);
            if (session == null) continue;
            session.restoreAndClear(main);
            Log.debug("Aborted self-test for " + session.player.getName() + " (" + reason + ")");
        }
    }

    void onQuit(Player player) {
        SelfTestSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.restoreAndClear(main);
        }
    }

    // -------------------------------------------------------------------------
    // Stage transitions, called from here and from SelfTestListener via recordResult
    // -------------------------------------------------------------------------

    private static int requiredClearance(SelfTestSpec.Stage stage) {
        return 3 * stage.cases.size();
    }

    private void beginStage(SelfTestSession session) {
        SelfTestSpec.Stage stage = session.currentStage();
        if (stage == null) {
            finish(session);
            return;
        }
        if (stage.cases.isEmpty()) {
            // Nothing to test in this stage (e.g. an override file trimmed it down) — skip it.
            session.stageIndex++;
            beginStage(session);
            return;
        }
        if (!SelfTestArena.hasSpace(session.player, stage)) {
            MessageUtil.send(session.player, "selfTestNeedsSpace",
                    Placeholder.unparsed("n", String.valueOf(requiredClearance(stage))));
            stop(session.player, null);
            return;
        }

        session.applyTestSettings(main, stage.kind == SelfTestSpec.StageKind.REFILL);
        giveKit(session.player, stage);
        session.arena = SelfTestArena.build(session.player, stage, spec.pedestal);
        session.caseIndex = 0;
        session.stagePassed = 0;

        MessageUtil.send(session.player, "selfTestStarted",
                Placeholder.unparsed("stage", stage.name),
                Placeholder.unparsed("total", String.valueOf(stage.cases.size())));
    }

    private void giveKit(Player player, SelfTestSpec.Stage stage) {
        PlayerInventory inv = player.getInventory();
        inv.setContents(new ItemStack[inv.getContents().length]);
        for (SelfTestSpec.KitItem item : stage.kit) {
            ItemStack stack = new ItemStack(item.material, item.amount);
            if (!item.enchantments.isEmpty()) {
                ItemMeta meta = stack.getItemMeta();
                for (Map.Entry<String, Integer> e : item.enchantments.entrySet()) {
                    Enchantment enchant = EnchantmentUtils.getEnchantment(e.getKey());
                    if (enchant != null) meta.addEnchant(enchant, e.getValue(), true);
                    else Log.warning("Unknown enchantment key in self-test kit: " + e.getKey());
                }
                stack.setItemMeta(meta);
            }
            inv.setItem(item.slot, stack);
        }
        inv.setHeldItemSlot(0);
    }

    /** Called by {@link SelfTestListener} once a case's outcome is known. */
    void recordResult(SelfTestSession session, boolean pass, String subjectName, String expectedDesc, String actualDesc) {
        session.arena.markResult(session.caseIndex, pass);
        if (pass) {
            session.stagePassed++;
            session.totalPassed++;
            MessageUtil.send(session.player, "selfTestCasePass",
                    Placeholder.unparsed("block", subjectName),
                    Placeholder.unparsed("expected", expectedDesc));
        } else {
            MessageUtil.send(session.player, "selfTestCaseFail",
                    Placeholder.unparsed("block", subjectName),
                    Placeholder.unparsed("expected", expectedDesc),
                    Placeholder.unparsed("actual", actualDesc));
        }

        session.caseIndex++;
        SelfTestSpec.Stage stage = session.currentStage();
        if (stage == null || session.caseIndex >= stage.cases.size()) {
            MessageUtil.send(session.player, "selfTestStageComplete",
                    Placeholder.unparsed("stage", stage == null ? "-" : stage.name),
                    Placeholder.unparsed("passed", String.valueOf(session.stagePassed)),
                    Placeholder.unparsed("total", String.valueOf(stage == null ? 0 : stage.cases.size())));
            session.arena.teardown();
            session.arena = null;
            session.stageIndex++;
            beginStage(session);
        }
    }

    private void finish(SelfTestSession session) {
        MessageUtil.send(session.player, "selfTestFinished",
                Placeholder.unparsed("passed", String.valueOf(session.totalPassed)),
                Placeholder.unparsed("total", String.valueOf(session.totalCases)));
        session.restoreAndClear(main);
        sessions.remove(session.player.getUniqueId());
    }
}
