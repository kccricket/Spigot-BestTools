package net.kccricket.bestesttool;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies {@link SelfTestStages} parses a dedicated test-only stages fixture
 * ({@code src/test/resources/selftest/test-stages.yml}, installed as the {@code selftest.yml}
 * on-disk override before each test) into the expected {@link SelfTestSpec}, and that a malformed
 * on-disk override skips bad entries instead of throwing. The fixture started as a copy of the
 * bundled production file's structure but is independent of it — production prose/values can
 * change freely without touching this test.
 */
class SelfTestStagesTest extends BestToolsTestBase {

    /**
     * Installs the test-only stages fixture as the {@code selftest.yml} on-disk override before
     * every test, so {@link SelfTestStages#load} (which prefers an on-disk override over the
     * bundled resource) never touches the real {@code selftest/stages.yml}. Tests that write their
     * own bespoke override content (e.g. {@link #onDiskOverrideFullyReplacesTheBundledFile}) simply
     * overwrite this before calling {@code load()}.
     */
    @BeforeEach
    void installTestStagesFixture() throws IOException {
        File override = new File(plugin.getDataFolder(), "selftest.yml");
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("selftest/test-stages.yml")) {
            assertNotNull(in, "test fixture selftest/test-stages.yml must be on the test classpath");
            Files.copy(in, override.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Test
    void loadsTheFixtureStagesFile() {
        SelfTestSpec spec = SelfTestStages.load(plugin);

        assertEquals(Material.SMOOTH_STONE, spec.pedestal);
        assertEquals(4, spec.stages.size());
        assertEquals(0, spec.indexOfStage("core-mining"));
        assertEquals(1, spec.indexOfStage("fallbacks"));
        assertEquals(2, spec.indexOfStage("combat"));
        assertEquals(3, spec.indexOfStage("refill"));
        assertEquals(-1, spec.indexOfStage("no-such-stage"));
    }

    @Test
    void coreMiningStageParsesKitAndCasesIncludingEnchantedItemAndAnyOfExpectation() {
        SelfTestSpec spec = SelfTestStages.load(plugin);
        SelfTestSpec.Stage stage = spec.stage(spec.indexOfStage("core-mining"));

        assertEquals(SelfTestSpec.StageKind.BLOCKS, stage.kind);
        assertEquals(8, stage.kit.size(), "slot 8 is intentionally left out of the kit");
        assertTrue(stage.kit.stream().noneMatch(k -> k.slot == 8));

        SelfTestSpec.KitItem silkPick = stage.kit.stream()
                .filter(k -> k.slot == 6).findFirst().orElseThrow();
        assertEquals(Material.DIAMOND_PICKAXE, silkPick.material);
        assertEquals(1, silkPick.enchantments.get("silk_touch"));

        SelfTestSpec.Case leavesCase = stage.cases.stream()
                .filter(c -> c.blockSubject == Material.OAK_LEAVES).findFirst().orElseThrow();
        assertEquals(SelfTestSpec.Expectation.Kind.ANY_OF, leavesCase.expectation.kind);
        assertTrue(leavesCase.expectation.materials.contains(Material.SHEARS));
        assertTrue(leavesCase.expectation.materials.contains(Material.DIAMOND_HOE));

        SelfTestSpec.Case glassCase = stage.cases.stream()
                .filter(c -> c.blockSubject == Material.GLASS).findFirst().orElseThrow();
        assertTrue(glassCase.requireSilk);
        assertEquals(SelfTestSpec.Expectation.Kind.EXACT, glassCase.expectation.kind);

        SelfTestSpec.Case torchCase = stage.cases.stream()
                .filter(c -> c.blockSubject == Material.TORCH).findFirst().orElseThrow();
        assertEquals(SelfTestSpec.Expectation.Kind.UNCHANGED, torchCase.expectation.kind);

        SelfTestSpec.Case wheatCase = stage.cases.stream()
                .filter(c -> c.blockSubject == Material.WHEAT).findFirst().orElseThrow();
        assertEquals(SelfTestSpec.Expectation.Kind.UNCHANGED, wheatCase.expectation.kind);

        SelfTestSpec.Case cakeCase = stage.cases.stream()
                .filter(c -> c.blockSubject == Material.CAKE).findFirst().orElseThrow();
        assertEquals(SelfTestSpec.Expectation.Kind.BARE_HAND, cakeCase.expectation.kind);
    }

    @Test
    void combatStageParsesEntitySubjects() {
        SelfTestSpec spec = SelfTestStages.load(plugin);
        SelfTestSpec.Stage stage = spec.stage(spec.indexOfStage("combat"));

        assertEquals(SelfTestSpec.StageKind.COMBAT, stage.kind);
        assertTrue(stage.cases.stream().anyMatch(c -> c.entitySubject == EntityType.ZOMBIE));
        assertTrue(stage.cases.stream().anyMatch(c -> c.entitySubject == EntityType.SPIDER));
        assertTrue(stage.cases.stream().allMatch(c -> c.blockSubject == null));
    }

    @Test
    void refillStageParsesKitAmounts() {
        SelfTestSpec spec = SelfTestStages.load(plugin);
        SelfTestSpec.Stage stage = spec.stage(spec.indexOfStage("refill"));

        assertEquals(SelfTestSpec.StageKind.REFILL, stage.kind);
        SelfTestSpec.KitItem backupStack = stage.kit.stream()
                .filter(k -> k.slot == 1).findFirst().orElseThrow();
        assertEquals(64, backupStack.amount);
    }

    @Test
    void onDiskOverrideFullyReplacesTheBundledFile() throws IOException {
        File override = new File(plugin.getDataFolder(), "selftest.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("pedestal", "COBBLESTONE");
        yaml.set("stages", java.util.List.of(java.util.Map.of(
                "name", "custom",
                "kind", "blocks",
                "kit", java.util.List.of(java.util.Map.of("slot", 0, "material", "STONE_PICKAXE")),
                "cases", java.util.List.of(java.util.Map.of("block", "STONE", "expect", "STONE_PICKAXE"))
        )));
        yaml.save(override);

        SelfTestSpec spec = SelfTestStages.load(plugin);

        assertEquals(Material.COBBLESTONE, spec.pedestal);
        assertEquals(1, spec.stages.size(), "the override replaces the bundled stages wholesale, it doesn't merge");
        assertEquals(0, spec.indexOfStage("custom"));
        assertEquals(-1, spec.indexOfStage("core-mining"));
    }

    @Test
    void malformedEntriesAreSkippedNotThrown() throws IOException {
        File override = new File(plugin.getDataFolder(), "selftest.yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("stages", java.util.List.of(
                // Missing "kind" — must be skipped, not thrown.
                java.util.Map.of("name", "broken", "kit", java.util.List.of(), "cases", java.util.List.of()),
                // Valid stage with one bad case (unknown material) and one good one.
                java.util.Map.of(
                        "name", "good",
                        "kind", "blocks",
                        "kit", java.util.List.of(),
                        "cases", java.util.List.of(
                                java.util.Map.of("block", "NOT_A_REAL_MATERIAL", "expect", "BARE_HAND"),
                                java.util.Map.of("block", "STONE", "expect", "BARE_HAND")
                        ))
        ));
        yaml.save(override);

        SelfTestSpec spec = SelfTestStages.load(plugin);

        assertEquals(1, spec.stages.size(), "the stage with no 'kind' must be skipped");
        SelfTestSpec.Stage good = spec.stage(0);
        assertNotNull(good);
        assertEquals(1, good.cases.size(), "the case with an unknown material must be skipped");
        assertEquals(Material.STONE, good.cases.get(0).blockSubject);
    }
}
