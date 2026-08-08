package net.kccricket.bestesttool.selftest;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Parsed, immutable model of {@code selftest/stages.yml} (see {@link SelfTestStages}) — what the
 * self-test builds and what it expects the player to end up holding after each case.
 */
public final class SelfTestSpec {

    final Material pedestal;
    final List<Stage> stages;

    SelfTestSpec(Material pedestal, List<Stage> stages) {
        this.pedestal = pedestal;
        this.stages = List.copyOf(stages);
    }

    Stage stage(int index) {
        return (index >= 0 && index < stages.size()) ? stages.get(index) : null;
    }

    int indexOfStage(String name) {
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).name.equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    enum StageKind { BLOCKS, COMBAT, REFILL }

    static final class Stage {
        final String name;
        final StageKind kind;
        final List<KitItem> kit;
        final List<Case> cases;
        /**
         * Whether {@code avoidBreakingTools} should be forced <b>on</b> for this stage (instead of
         * the usual forced-off — see {@link SelfTestSession#applyTestSettings}). Only meaningful for
         * a stage whose kit includes a {@link KitItem#nearBreaking} item, since a pristine kit
         * behaves identically either way.
         */
        final boolean avoidBreaking;

        Stage(String name, StageKind kind, List<KitItem> kit, List<Case> cases, boolean avoidBreaking) {
            this.name = name;
            this.kind = kind;
            this.kit = List.copyOf(kit);
            this.cases = List.copyOf(cases);
            this.avoidBreaking = avoidBreaking;
        }
    }

    /** One hotbar slot (0-8) of the kit handed to the tester at the start of a stage. */
    static final class KitItem {
        final int slot;
        final Material material;
        final int amount;
        /** Enchantment key (as registered in {@code Registry.ENCHANTMENT}, e.g. {@code "silk_touch"}) -> level. */
        final Map<String, Integer> enchantments;
        /**
         * When true, this item is handed out already at one point of durability from breaking (see
         * {@code BestToolsHandler.isAboutToBreak}) instead of pristine — used to exercise
         * {@code avoidBreakingTools} (paired with the stage's own {@link Stage#avoidBreaking} flag).
         */
        final boolean nearBreaking;

        KitItem(int slot, Material material, int amount, Map<String, Integer> enchantments, boolean nearBreaking) {
            this.slot = slot;
            this.material = material;
            this.amount = amount;
            this.enchantments = Map.copyOf(enchantments);
            this.nearBreaking = nearBreaking;
        }
    }

    /**
     * One thing the tester interacts with during a stage: a block to hit ({@link #blockSubject})
     * for a {@link StageKind#BLOCKS}/{@link StageKind#REFILL} stage, or a mob to attack
     * ({@link #entitySubject}) for a {@link StageKind#COMBAT} stage. Exactly one of the two is set.
     */
    static final class Case {
        final Material blockSubject;
        final EntityType entitySubject;
        final Expectation expectation;
        /** BLOCKS-only: whether {@code blockSubject} is expected to only be worth Silk Touch for. */
        final boolean requireSilk;

        private Case(Material blockSubject, EntityType entitySubject, Expectation expectation, boolean requireSilk) {
            this.blockSubject = blockSubject;
            this.entitySubject = entitySubject;
            this.expectation = expectation;
            this.requireSilk = requireSilk;
        }

        static Case forBlock(Material block, Expectation expectation, boolean requireSilk) {
            return new Case(block, null, expectation, requireSilk);
        }

        static Case forEntity(EntityType entity, Expectation expectation) {
            return new Case(null, entity, expectation, false);
        }

        String subjectName() {
            return blockSubject != null ? blockSubject.name() : entitySubject.name();
        }
    }

    /** What the tester's main hand must look like after interacting with a case's subject. */
    static final class Expectation {
        enum Kind { EXACT, ANY_OF, BARE_HAND, UNCHANGED }

        final Kind kind;
        final List<Material> materials; // empty for BARE_HAND/UNCHANGED

        private Expectation(Kind kind, List<Material> materials) {
            this.kind = kind;
            this.materials = materials;
        }

        static Expectation exact(Material m) {
            return new Expectation(Kind.EXACT, List.of(m));
        }

        static Expectation anyOf(List<Material> m) {
            return new Expectation(Kind.ANY_OF, List.copyOf(m));
        }

        static Expectation bareHand() {
            return new Expectation(Kind.BARE_HAND, List.of());
        }

        static Expectation unchanged() {
            return new Expectation(Kind.UNCHANGED, List.of());
        }

        String describe() {
            return switch (kind) {
                case EXACT, ANY_OF -> materials.stream().map(Material::name).collect(Collectors.joining(" or "));
                case BARE_HAND -> "a bare hand";
                case UNCHANGED -> "whatever you started with";
            };
        }
    }
}
