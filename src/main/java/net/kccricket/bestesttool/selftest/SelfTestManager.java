package net.kccricket.bestesttool.selftest;

import net.kccricket.bestesttool.BestestToolPlugin;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.kccricket.bestesttool.tool.EnchantmentUtils;

/**
 * Orchestrates {@code /bestesttool selftest}: owns every tester's {@link SelfTestSession}, the
 * parsed {@link SelfTestSpec}, and the shared {@link SelfTestListener}.
 * <p>
 * Constructed exactly once, in {@link BestestToolPlugin#onEnable} — not in {@link BestestToolPlugin#load}, which re-runs on
 * every {@code /bestesttool reload} — so an in-progress test survives a reload; {@link #abortAll}
 * is called from {@code BestestToolPlugin.load}'s reload branch just before the old listeners are torn down, so
 * the tester is put back the way they were rather than left mid-test with no observer attached.
 */
public final class SelfTestManager {

    private final BestestToolPlugin main;
    private final SelfTestListener listener;
    private final Map<UUID, SelfTestSession> sessions = new ConcurrentHashMap<>();
    private volatile SelfTestSpec spec;

    public SelfTestManager(BestestToolPlugin main) {
        this.main = main;
        this.listener = new SelfTestListener(main, this);
        reloadSpec();
    }

    /** Re-parses {@code selftest/stages.yml} (or its on-disk override). Safe to call mid-test — takes effect for the next stage/run. */
    public void reloadSpec() {
        try {
            spec = SelfTestStages.load(main);
        } catch (RuntimeException e) {
            Log.warning("Failed to load self-test stages — /bestesttool selftest will report no stages configured.", e);
            spec = new SelfTestSpec(Material.SMOOTH_STONE, List.of());
        }
    }

    public SelfTestListener listener() {
        return listener;
    }

    public List<String> stageNames() {
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

    public void start(Player player, String stageName) {
        if (sessions.containsKey(player.getUniqueId())) {
            main.messages().to(player).error().send("selfTestAlreadyRunning");
            return;
        }
        if (spec.stages.isEmpty()) {
            main.messages().to(player).status().send("selfTestNoStagesConfigured");
            return;
        }

        int startIndex = 0;
        if (stageName != null) {
            startIndex = spec.indexOfStage(stageName);
            if (startIndex == -1) {
                main.messages().to(player).error().send("selfTestUnknownStage", Placeholder.unparsed("stage", stageName));
                return;
            }
        }

        SelfTestSpec.Stage firstStage = spec.stage(startIndex);
        if (!SelfTestArena.hasSpace(player, firstStage)) {
            main.messages().to(player).error().send("selfTestNeedsSpace",
                    Placeholder.unparsed("n", String.valueOf(requiredClearance(firstStage))));
            return;
        }

        SelfTestSession session = new SelfTestSession(main, player, spec);
        session.stageIndex = startIndex;
        session.totalCases = spec.stages.stream().mapToInt(s -> s.cases.size()).sum();
        sessions.put(player.getUniqueId(), session);
        beginStage(session);
    }

    public void advance(Player player) {
        SelfTestSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            main.messages().to(player).error().send("selfTestNoneRunning");
            return;
        }
        if (session.arena != null) {
            session.arena.teardown();
            session.arena = null;
        }
        session.stageIndex++;
        beginStage(session);
    }

    public void status(Player player) {
        SelfTestSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            main.messages().to(player).error().send("selfTestNoneRunning");
            return;
        }
        SelfTestSpec.Stage stage = session.currentStage();
        int remaining = stage == null ? 0 : stage.cases.size() - session.caseIndex;
        main.messages().to(player).status().send("selfTestStatus",
                Placeholder.unparsed("stage", stage == null ? "-" : stage.name),
                Placeholder.unparsed("remaining", String.valueOf(remaining)));
    }

    /**
     * Ends {@code player}'s session, if any, restoring everything. {@code messageKey} is sent
     * afterward if non-null — pass {@code null} when a more specific message (e.g. "needs space")
     * was already sent, or to abort silently.
     */
    public void stop(Player player, String messageKey) {
        SelfTestSession session = sessions.remove(player.getUniqueId());
        if (session == null) {
            if (messageKey != null) main.messages().to(player).error().send("selfTestNoneRunning");
            return;
        }
        session.restoreAndClear(main);
        if (messageKey != null) main.messages().to(player).status().send(messageKey);
    }

    /** Ends every in-progress session — called on {@code /bestesttool reload}, {@code BestestToolPlugin.onDisable}, and player quit. */
    public void abortAll(String reason) {
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
            main.messages().to(session.player).error().send("selfTestNeedsSpace",
                    Placeholder.unparsed("n", String.valueOf(requiredClearance(stage))));
            stop(session.player, null);
            return;
        }

        session.applyTestSettings(main, stage.kind == SelfTestSpec.StageKind.REFILL);
        giveKit(session.player, stage);
        session.arena = SelfTestArena.build(session.player, stage, spec.pedestal);
        session.caseIndex = 0;
        session.stagePassed = 0;

        main.messages().to(session.player).status().send("selfTestStarted",
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
        inv.setHeldItemSlot(startingHeldSlot(stage));
    }

    /**
     * Which hotbar slot to start the stage holding. REFILL cases need their placeable kit item in
     * hand, so they always start at slot 0; BLOCKS/COMBAT stages avoid a slot whose item already
     * matches case 0's expectation (e.g. the kit's netherite pickaxe for a case that expects a
     * netherite pickaxe) — otherwise a broken switch could pass silently just because the right
     * answer was already in hand before the tester interacted with anything.
     */
    private static int startingHeldSlot(SelfTestSpec.Stage stage) {
        if (stage.kind == SelfTestSpec.StageKind.REFILL) return 0;
        int slot = slotAvoiding(stage, stage.cases.get(0).expectation);
        return slot >= 0 ? slot : 0;
    }

    /**
     * A hotbar slot from {@code stage}'s kit whose item does not already satisfy {@code expectation}
     * — preferring a genuinely empty slot (bare hand) unless {@code expectation} itself is
     * {@code BARE_HAND}, in which case an empty hand would trivially satisfy it and a real item is
     * picked instead. Returns {@code -1} if the kit offers nothing safe (every item already matches).
     */
    static int slotAvoiding(SelfTestSpec.Stage stage, SelfTestSpec.Expectation expectation) {
        if (expectation.kind == SelfTestSpec.Expectation.Kind.BARE_HAND) {
            return stage.kit.isEmpty() ? -1 : stage.kit.get(0).slot;
        }
        Set<Integer> filled = stage.kit.stream().map(k -> k.slot).collect(Collectors.toSet());
        for (int slot = 0; slot <= 8; slot++) {
            if (!filled.contains(slot)) return slot;
        }
        for (SelfTestSpec.KitItem item : stage.kit) {
            if (!expectation.materials.contains(item.material)) return item.slot;
        }
        return -1;
    }

    /** Called by {@link SelfTestListener} once a case's outcome is known. */
    void recordResult(SelfTestSession session, boolean pass, String subjectName, String expectedDesc, String actualDesc) {
        session.arena.markResult(session.caseIndex, pass);
        if (pass) {
            session.stagePassed++;
            session.totalPassed++;
            main.messages().to(session.player).status().send("selfTestCasePass",
                    Placeholder.unparsed("block", subjectName),
                    Placeholder.unparsed("expected", expectedDesc));
        } else {
            main.messages().to(session.player).status().send("selfTestCaseFail",
                    Placeholder.unparsed("block", subjectName),
                    Placeholder.unparsed("expected", expectedDesc),
                    Placeholder.unparsed("actual", actualDesc));
        }

        session.caseIndex++;
        SelfTestSpec.Stage stage = session.currentStage();
        if (stage == null || session.caseIndex >= stage.cases.size()) {
            main.messages().to(session.player).status().send("selfTestStageComplete",
                    Placeholder.unparsed("stage", stage == null ? "-" : stage.name),
                    Placeholder.unparsed("passed", String.valueOf(session.stagePassed)),
                    Placeholder.unparsed("total", String.valueOf(stage == null ? 0 : stage.cases.size())));
            session.arena.teardown();
            session.arena = null;
            session.stageIndex++;
            beginStage(session);
        } else if (stage.kind != SelfTestSpec.StageKind.REFILL) {
            avoidHoldingNextAnswerAlready(session.player, stage, stage.cases.get(session.caseIndex).expectation);
        }
    }

    /**
     * If the tester is already holding (or already bare-handed for) exactly what the next case
     * expects — e.g. two consecutive cases both expecting a netherite pickaxe, or both combat cases
     * in the bundled stages both expecting a diamond sword — switch away from it before they
     * interact with the next subject. Otherwise a broken switch could pass silently because the
     * right answer simply carried over from the previous case rather than being switched to.
     */
    private void avoidHoldingNextAnswerAlready(Player player, SelfTestSpec.Stage stage, SelfTestSpec.Expectation next) {
        ItemStack held = player.getInventory().getItemInMainHand();
        boolean alreadyCorrect = switch (next.kind) {
            case EXACT, ANY_OF -> next.materials.contains(held.getType());
            case BARE_HAND -> SelfTestEvaluator.isEmpty(held) || !main.toolHandler.isDamageable(held);
            case UNCHANGED -> false;
        };
        if (!alreadyCorrect) return;

        int slot = slotAvoiding(stage, next);
        if (slot >= 0) player.getInventory().setHeldItemSlot(slot);
    }

    private void finish(SelfTestSession session) {
        main.messages().to(session.player).status().send("selfTestFinished",
                Placeholder.unparsed("passed", String.valueOf(session.totalPassed)),
                Placeholder.unparsed("total", String.valueOf(session.totalCases)));
        session.restoreAndClear(main);
        sessions.remove(session.player.getUniqueId());
    }
}
