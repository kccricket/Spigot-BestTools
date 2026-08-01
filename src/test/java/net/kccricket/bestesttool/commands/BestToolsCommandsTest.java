package net.kccricket.bestesttool.commands;

import net.kccricket.bestesttool.BestToolsTestBase;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.kccricket.kcmclib.commands.Suggest;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import net.kccricket.bestesttool.commands.BestToolsCommands;

/**
 * Tests for the command-suggestion helpers in {@link BestToolsCommands}, mirroring ClickSorted's
 * {@code ClickSortedCommandsTest}.
 */
class BestToolsCommandsTest extends BestToolsTestBase {

    private static List<String> suggest(String input, List<String> candidates) throws Exception {
        SuggestionsBuilder builder = new SuggestionsBuilder(input, 0);
        Suggestions suggestions = Suggest.token(builder, candidates).get();
        return suggestions.getList().stream().map(Suggestion::getText).toList();
    }

    @Test
    void suggestableMaterialNamesAreNamespacedIncludeBlocksAndExcludeItems() {
        List<String> names = Suggest.materialNames(BestToolsCommands.SUGGESTABLE_MATERIAL);
        assertTrue(names.contains("minecraft:dirt"),
                "DIRT is a block and must be suggestable for the block blacklist, namespaced");
        assertFalse(names.contains("dirt"), "bare (non-namespaced) names must not be suggested");
        assertFalse(names.contains("minecraft:wooden_sword"),
                "WOODEN_SWORD is not a block and must not be suggested");
    }

    // LEGACY_STONE is deprecated for removal; using it here is the point of the test.
    @Test
    @SuppressWarnings({"removal"})
    void suggestableMaterialExcludesLegacyMaterials() {
        assertFalse(BestToolsCommands.SUGGESTABLE_MATERIAL.test(Material.LEGACY_STONE));
    }

    @Test
    void suggestTokenFiltersByPrefixOfTheWholeInput() throws Exception {
        List<String> names = suggest("di", List.of("dirt", "diorite", "stone"));

        assertTrue(names.contains("dirt"));
        assertTrue(names.contains("diorite"));
        assertFalse(names.contains("stone"));
    }

    @Test
    void suggestTokenOnlyFiltersTheLastWhitespaceSeparatedToken() throws Exception {
        // Simulates "bl add dirt st" — "dirt" is already typed, only "st" should drive suggestions.
        List<String> names = suggest("dirt st", List.of("dirt", "stone", "diorite"));

        assertTrue(names.contains("stone"));
        assertFalse(names.contains("diorite"), "the completed first token must not resurface suggestions");
        assertFalse(names.contains("dirt"), "the completed first token itself must not be re-suggested");
    }

    @Test
    void blacklistedMaterialNamesReflectsThePlayersBlacklist() {
        PlayerMock player = newPlayer();
        plugin.getPlayerSetting(player).getBlacklist().add(Material.DIRT);

        List<String> names = BestToolsCommands.blacklistedMaterialNames(plugin, player);

        assertTrue(names.contains("minecraft:dirt"));
    }

    @Test
    void blacklistedMaterialNamesIsEmptyForNonPlayerSender() {
        List<String> names = BestToolsCommands.blacklistedMaterialNames(plugin, server.getConsoleSender());

        assertTrue(names.isEmpty());
    }
}
