package net.kccricket.bestesttool.model;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import net.kccricket.bestesttool.selftest.SelfTestSession;
import net.kccricket.bestesttool.tool.BestToolsCache;

/**
 * Per-player state, stored via native PDC — one {@link NamespacedKey} leaf per field (leaf names
 * match the {@code defaults.*} config-default names, matching KcMcLib/ClickSorted's leaf-key
 * convention), rather than a single {@code FILE_CONFIGURATION}-typed blob. No third-party PDC
 * library needed: every value here is a plain boolean/int/String.
 */
public class PlayerSetting {

        // Namespace matches what NamespacedKey(Plugin, key) would have derived from the plugin name
        // ("BestestTool" -> "bestesttool") — spelled out as a literal instead of going through
        // BestestToolPlugin.getInstance() so this class carries no static back-reference to the
        // plugin singleton. Existing on-disk PDC data is unaffected: the namespace string is identical.
        private static final String NAMESPACE = "bestesttool";
        private static final NamespacedKey KEY_ENABLED = new NamespacedKey(NAMESPACE, "enabled");
        private static final NamespacedKey KEY_REFILL_ENABLED = new NamespacedKey(NAMESPACE, "refill_enabled");
        private static final NamespacedKey KEY_HOTBAR_ONLY = new NamespacedKey(NAMESPACE, "hotbar_only");
        private static final NamespacedKey KEY_FAVORITE_SLOT = new NamespacedKey(NAMESPACE, "favorite_slot");
        private static final NamespacedKey KEY_SWORD_ON_MOBS = new NamespacedKey(NAMESPACE, "sword_on_mobs");
        private static final NamespacedKey KEY_HAS_SEEN_BESTTOOLS_MESSAGE = new NamespacedKey(NAMESPACE, "has_seen_besttools_message");
        private static final NamespacedKey KEY_HAS_SEEN_REFILL_MESSAGE = new NamespacedKey(NAMESPACE, "has_seen_refill_message");

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
        public int getRawFavoriteSlot() {
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
        }

        public void save() {
                PersistentDataContainer pdc = player.getPersistentDataContainer();
                pdc.set(KEY_ENABLED, PersistentDataType.BYTE, (byte) (bestToolsEnabled ? 1 : 0));
                pdc.set(KEY_HAS_SEEN_BESTTOOLS_MESSAGE, PersistentDataType.BYTE, (byte) (hasSeenBestToolsMessage ? 1 : 0));
                pdc.set(KEY_HAS_SEEN_REFILL_MESSAGE, PersistentDataType.BYTE, (byte) (hasSeenRefillMessage ? 1 : 0));
                pdc.set(KEY_REFILL_ENABLED, PersistentDataType.BYTE, (byte) (refillEnabled ? 1 : 0));
                pdc.set(KEY_HOTBAR_ONLY, PersistentDataType.BYTE, (byte) (hotbarOnly ? 1 : 0));
                pdc.set(KEY_SWORD_ON_MOBS, PersistentDataType.BYTE, (byte) (swordOnMobs ? 1 : 0));
                pdc.set(KEY_FAVORITE_SLOT, PersistentDataType.INTEGER, favoriteSlot);
        }

        public PlayerSetting(Player player, boolean bestToolsEnabled, boolean refillEnabled, boolean hotbarOnly, int favoriteSlot, boolean swordOnMobs) {

                this.player = player;
                this.blacklist = new Blacklist(player);
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

        public boolean toggleBestToolsEnabled() {
                return setBestToolsEnabled(!bestToolsEnabled);
        }

        public boolean setBestToolsEnabled(boolean enabled) {
                bestToolsEnabled = enabled;
                save();
                return bestToolsEnabled;
        }

        public boolean setRefillEnabled(boolean enabled) {
                refillEnabled = enabled;
                save();
                return refillEnabled;
        }

        public boolean setHotbarOnly(boolean enabled) {
                hotbarOnly = enabled;
                save();
                return hotbarOnly;
        }

        /** Package-private setter used by {@link SelfTestSession} to force/restore this preference around a test run. */
        public boolean setSwordOnMobs(boolean enabled) {
                swordOnMobs = enabled;
                save();
                return swordOnMobs;
        }

        public void setHasSeenBestToolsMessage(boolean seen) {
                if(seen== hasSeenBestToolsMessage) return;
                hasSeenBestToolsMessage = seen;
                save();
        }

        public void setHasSeenRefillMessage(boolean seen) {
                if(seen== hasSeenRefillMessage) return;
                hasSeenRefillMessage = seen;
                save();
        }

        public void setFavoriteSlot(int favoriteSlot) {
                this.favoriteSlot = favoriteSlot;
                save();
        }

}
