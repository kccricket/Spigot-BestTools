package net.kccricket.bestesttool;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;

/**
 * Per-player state, stored via native PDC — one {@link NamespacedKey} leaf per field (leaf names
 * match the {@code defaults.*} config-default names, matching KcMcLib/ClickSorted's leaf-key
 * convention), rather than a single {@code FILE_CONFIGURATION}-typed blob. No third-party PDC
 * library needed: every value here is a plain boolean/int/String.
 */
public class PlayerSetting {

        private static final Main main = Main.getInstance();
        private static final NamespacedKey KEY_ENABLED = new NamespacedKey(main, "enabled");
        private static final NamespacedKey KEY_REFILL_ENABLED = new NamespacedKey(main, "refill_enabled");
        private static final NamespacedKey KEY_HOTBAR_ONLY = new NamespacedKey(main, "hotbar_only");
        private static final NamespacedKey KEY_FAVORITE_SLOT = new NamespacedKey(main, "favorite_slot");
        private static final NamespacedKey KEY_SWORD_ON_MOBS = new NamespacedKey(main, "sword_on_mobs");
        private static final NamespacedKey KEY_HAS_SEEN_BESTTOOLS_MESSAGE = new NamespacedKey(main, "has_seen_besttools_message");
        private static final NamespacedKey KEY_HAS_SEEN_REFILL_MESSAGE = new NamespacedKey(main, "has_seen_refill_message");
        private static final NamespacedKey KEY_BLACKLIST = new NamespacedKey(main, "blacklist");

        private Blacklist blacklist;

        private boolean bestToolsEnabled;

        private boolean refillEnabled;

        private boolean hotbarOnly;

        private int favoriteSlot = 0;

        private boolean swordOnMobs;

        private boolean hasSeenBestToolsMessage = false;
        private boolean hasSeenRefillMessage = false;

        private final BestToolsCache btcache = new BestToolsCache();

        private final Player player;

        public Blacklist getBlacklist() {
                return blacklist;
        }

        public boolean isBestToolsEnabled() {
                return bestToolsEnabled;
        }

        public boolean isRefillEnabled() {
                return refillEnabled;
        }

        public boolean isHotbarOnly() {
                return hotbarOnly;
        }

        public boolean isSwordOnMobs() {
                return swordOnMobs;
        }

        public boolean isHasSeenBestToolsMessage() {
                return hasSeenBestToolsMessage;
        }

        public boolean isHasSeenRefillMessage() {
                return hasSeenRefillMessage;
        }

        public BestToolsCache getBtcache() {
                return btcache;
        }

        public int getFavoriteSlot() {
                if(favoriteSlot >= 0 && favoriteSlot <= 8) return favoriteSlot;
                return player.getInventory().getHeldItemSlot();
        }

        /**
         * The raw stored value, unlike {@link #getFavoriteSlot()} — {@code -1} stays {@code -1}
         * here instead of resolving to the current held slot. Used by the self-test
         * (see {@link SelfTestSession}) to snapshot/restore the tester's actual preference rather
         * than whatever slot they happened to be holding when the test started.
         */
        int getRawFavoriteSlot() {
                return favoriteSlot;
        }

        /** Reads a BYTE-encoded (1/0) boolean leaf, falling back to {@code def} if absent. */
        private static boolean getBoolean(PersistentDataContainer pdc, NamespacedKey key, boolean def) {
                return pdc.getOrDefault(key, PersistentDataType.BYTE, (byte) (def ? 1 : 0)) != 0;
        }

        private void getPDCValues(Player player) {
                PersistentDataContainer pdc = player.getPersistentDataContainer();
                this.bestToolsEnabled = getBoolean(pdc, KEY_ENABLED, bestToolsEnabled);
                this.hasSeenBestToolsMessage = getBoolean(pdc, KEY_HAS_SEEN_BESTTOOLS_MESSAGE, hasSeenBestToolsMessage);
                this.hasSeenRefillMessage = getBoolean(pdc, KEY_HAS_SEEN_REFILL_MESSAGE, hasSeenRefillMessage);
                this.refillEnabled = getBoolean(pdc, KEY_REFILL_ENABLED, refillEnabled);
                this.hotbarOnly = getBoolean(pdc, KEY_HOTBAR_ONLY, hotbarOnly);
                this.swordOnMobs = getBoolean(pdc, KEY_SWORD_ON_MOBS, swordOnMobs);
                this.favoriteSlot = pdc.getOrDefault(KEY_FAVORITE_SLOT, PersistentDataType.INTEGER, favoriteSlot);

                String blacklistStr = pdc.get(KEY_BLACKLIST, PersistentDataType.STRING);
                this.blacklist = (blacklistStr == null || blacklistStr.isEmpty())
                        ? new Blacklist()
                        : new Blacklist(List.of(blacklistStr.split(",")));
        }

        private void save() {
                PersistentDataContainer pdc = player.getPersistentDataContainer();
                pdc.set(KEY_ENABLED, PersistentDataType.BYTE, (byte) (bestToolsEnabled ? 1 : 0));
                pdc.set(KEY_HAS_SEEN_BESTTOOLS_MESSAGE, PersistentDataType.BYTE, (byte) (hasSeenBestToolsMessage ? 1 : 0));
                pdc.set(KEY_HAS_SEEN_REFILL_MESSAGE, PersistentDataType.BYTE, (byte) (hasSeenRefillMessage ? 1 : 0));
                pdc.set(KEY_REFILL_ENABLED, PersistentDataType.BYTE, (byte) (refillEnabled ? 1 : 0));
                pdc.set(KEY_HOTBAR_ONLY, PersistentDataType.BYTE, (byte) (hotbarOnly ? 1 : 0));
                pdc.set(KEY_SWORD_ON_MOBS, PersistentDataType.BYTE, (byte) (swordOnMobs ? 1 : 0));
                pdc.set(KEY_FAVORITE_SLOT, PersistentDataType.INTEGER, favoriteSlot);
                pdc.set(KEY_BLACKLIST, PersistentDataType.STRING, String.join(",", blacklist.toStringList()));
        }

        PlayerSetting(Player player, boolean bestToolsEnabled, boolean refillEnabled, boolean hotbarOnly, int favoriteSlot, boolean swordOnMobs) {

                this.player = player;
                this.blacklist = new Blacklist();
                this.bestToolsEnabled = bestToolsEnabled;
                this.refillEnabled = refillEnabled;
                this.hasSeenBestToolsMessage = false;
                this.hasSeenRefillMessage = false;
                this.hotbarOnly = hotbarOnly;
                this.swordOnMobs= swordOnMobs;
                this.favoriteSlot = favoriteSlot;
                getPDCValues(player);
                this.save();
        }

        boolean toggleBestToolsEnabled() {
                return setBestToolsEnabled(!bestToolsEnabled);
        }

        boolean setBestToolsEnabled(boolean enabled) {
                bestToolsEnabled = enabled;
                save();
                return bestToolsEnabled;
        }

        boolean setRefillEnabled(boolean enabled) {
                refillEnabled = enabled;
                save();
                return refillEnabled;
        }

        boolean setHotbarOnly(boolean enabled) {
                hotbarOnly = enabled;
                save();
                return hotbarOnly;
        }

        /** Package-private setter used by {@link SelfTestSession} to force/restore this preference around a test run. */
        boolean setSwordOnMobs(boolean enabled) {
                swordOnMobs = enabled;
                save();
                return swordOnMobs;
        }

        void setHasSeenBestToolsMessage(boolean seen) {
                if(seen== hasSeenBestToolsMessage) return;
                hasSeenBestToolsMessage = seen;
                save();
        }

        void setHasSeenRefillMessage(boolean seen) {
                if(seen== hasSeenRefillMessage) return;
                hasSeenRefillMessage = seen;
                save();
        }

        void setFavoriteSlot(int favoriteSlot) {
                this.favoriteSlot = favoriteSlot;
                save();
        }

}
