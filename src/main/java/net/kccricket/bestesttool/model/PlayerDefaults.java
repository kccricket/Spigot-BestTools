package net.kccricket.bestesttool.model;

/**
 * Seed values for a freshly-created {@link PlayerSetting}, read once (via
 * {@code MainConfig#playerDefaults}) from config.yml's {@code defaults.*} block. Every component
 * is overridden by the player's PDC where a leaf is already present — see
 * {@link PlayerSetting}'s constructor.
 * <p>
 * A record rather than growing {@code PlayerSetting}'s constructor to ten positional booleans/
 * int: that many same-typed adjacent parameters is a silent-transposition hazard, and this keeps
 * each seed named next to the config getter that produced it (see {@code MainConfig.playerDefaults}).
 */
public record PlayerDefaults(
        boolean bestToolsEnabled,
        boolean refillEnabled,
        boolean hotbarOnly,
        int favoriteSlot,
        boolean swordOnMobs,
        boolean useAxeAsSword,
        boolean switchDuringBattle,
        boolean considerSwordsForLeaves,
        boolean considerSwordsForCobwebs,
        boolean avoidBreakingTools) {
}
