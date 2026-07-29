package net.kccricket.bestesttool.selftest;

import net.kccricket.bestesttool.BestToolsTestBase;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.kccricket.bestesttool.selftest.SelfTestEvaluator;
import net.kccricket.bestesttool.selftest.SelfTestSpec;

/**
 * Exercises every {@link SelfTestSpec.Expectation.Kind} against plain {@link ItemStack}s — this is
 * the part of the self-test that MockBukkit's live-mining-data gap ({@code BlockDataMock} throwing
 * on {@code getDestroySpeed}/{@code isPreferredTool}, per {@code ToolSelectionTest}'s header) does
 * NOT block: the comparison logic itself has nothing to do with mining data.
 */
class SelfTestEvaluatorTest extends BestToolsTestBase {

    @Test
    void exactExpectationPassesOnlyForThatMaterial() {
        SelfTestSpec.Expectation exp = SelfTestSpec.Expectation.exact(Material.DIAMOND_PICKAXE);

        assertTrue(SelfTestEvaluator.matches(exp, null, new ItemStack(Material.DIAMOND_PICKAXE), true));
        assertFalse(SelfTestEvaluator.matches(exp, null, new ItemStack(Material.IRON_PICKAXE), true));
        assertFalse(SelfTestEvaluator.matches(exp, null, null, false));
    }

    @Test
    void anyOfExpectationPassesForEitherMaterial() {
        SelfTestSpec.Expectation exp = SelfTestSpec.Expectation.anyOf(List.of(Material.SHEARS, Material.DIAMOND_HOE));

        assertTrue(SelfTestEvaluator.matches(exp, null, new ItemStack(Material.SHEARS), true));
        assertTrue(SelfTestEvaluator.matches(exp, null, new ItemStack(Material.DIAMOND_HOE), true));
        assertFalse(SelfTestEvaluator.matches(exp, null, new ItemStack(Material.DIAMOND_SWORD), true));
    }

    @Test
    void bareHandExpectationPassesForEmptyHandOrNonDamageableItem() {
        SelfTestSpec.Expectation exp = SelfTestSpec.Expectation.bareHand();

        assertTrue(SelfTestEvaluator.matches(exp, null, null, false), "a null (empty) hand is a bare hand");
        assertTrue(SelfTestEvaluator.matches(exp, null, new ItemStack(Material.AIR), false), "AIR is a bare hand");
        assertTrue(SelfTestEvaluator.matches(exp, null, new ItemStack(Material.DIRT), false),
                "a non-damageable item (afterIsDamageable=false) is a legal bare-hand stand-in");
        assertFalse(SelfTestEvaluator.matches(exp, null, new ItemStack(Material.IRON_PICKAXE), true),
                "a real (damageable) tool is not a bare hand");
    }

    @Test
    void unchangedExpectationComparesBeforeAndAfter() {
        SelfTestSpec.Expectation exp = SelfTestSpec.Expectation.unchanged();
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);

        assertTrue(SelfTestEvaluator.matches(exp, pickaxe, pickaxe.clone(), true), "identical stacks pass");
        assertTrue(SelfTestEvaluator.matches(exp, null, null, false), "both empty passes");
        assertTrue(SelfTestEvaluator.matches(exp, new ItemStack(Material.AIR), null, false),
                "AIR before and null after are both 'empty' — must count as unchanged");
        assertFalse(SelfTestEvaluator.matches(exp, pickaxe, new ItemStack(Material.IRON_PICKAXE), true),
                "a switch away from the original item fails UNCHANGED");
        assertFalse(SelfTestEvaluator.matches(exp, pickaxe, null, false),
                "switching away to an empty hand also fails UNCHANGED");
    }

    @Test
    void describeNamesTheMaterialOrCallsOutAnEmptyHand() {
        assertTrue(SelfTestEvaluator.describe(null).contains("empty"));
        assertTrue(SelfTestEvaluator.describe(new ItemStack(Material.AIR)).contains("empty"));
        assertTrue(SelfTestEvaluator.describe(new ItemStack(Material.STONE)).contains("STONE"));
    }
}
