package net.kccricket.bestesttool.tool;

import net.kccricket.bestesttool.model.PlayerSetting;

/**
 * The per-player sword-as-fallback preferences that affect *candidate filtering* in
 * {@link BestToolsHandler#isCandidate}, hoisted out of {@link PlayerSetting} once per selection
 * (in {@link BestToolsHandler#getBestToolFromInventory}) so the per-item ranking loop never has to
 * look a player up. Kept player-free so {@link BestToolsHandler#selectBestTool} — the synthetic
 * benchmark's entry point — stays usable with no {@code Player} in scope.
 */
public record SwordPolicy(boolean forLeaves, boolean forCobwebs) {

    /** Both off — matches the bundled config defaults; used by tests and the benchmark. */
    public static final SwordPolicy NONE = new SwordPolicy(false, false);

    public static SwordPolicy from(PlayerSetting settings) {
        return new SwordPolicy(settings.isConsiderSwordsForLeaves(), settings.isConsiderSwordsForCobwebs());
    }
}
