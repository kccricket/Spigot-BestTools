package net.kccricket.bestesttool;

import com.jeff_media.morepersistentdatatypes.DataType;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

public class PlayerSetting {

        private static final Main main = Main.getInstance();
        private static final NamespacedKey DATA = new NamespacedKey(main, "data");

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

        private void getPDCValues(Player player) {
                if(player.getPersistentDataContainer().has(DATA, DataType.FILE_CONFIGURATION)) {
                        FileConfiguration conf = player.getPersistentDataContainer().get(DATA, DataType.FILE_CONFIGURATION);
                        this.bestToolsEnabled = conf.getBoolean("bestToolsEnabled");
                        this.hasSeenBestToolsMessage = conf.getBoolean("hasSeenBestToolsMessage");
                        this.hasSeenRefillMessage = conf.getBoolean("hasSeenRefillMessage");
                        this.refillEnabled = conf.getBoolean("refillEnabled");
                        this.hotbarOnly = conf.getBoolean("hotbarOnly");
                        this.swordOnMobs = conf.getBoolean("swordOnMobs");
                        this.favoriteSlot = conf.getInt("favoriteSlot");
                }
        }

        private void save() {
                FileConfiguration conf = new YamlConfiguration();
                conf.set("blacklist",blacklist.toStringList());
                conf.set("bestToolsEnabled",bestToolsEnabled);
                conf.set("hasSeenBestToolsMessage",hasSeenBestToolsMessage);
                conf.set("hasSeenRefillMessage",hasSeenRefillMessage);
                conf.set("refillEnabled",refillEnabled);
                conf.set("hotbarOnly",hotbarOnly);
                conf.set("swordOnMobs",swordOnMobs);
                conf.set("favoriteSlot",favoriteSlot);
                player.getPersistentDataContainer().set(DATA,DataType.FILE_CONFIGURATION,conf);
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
                bestToolsEnabled =!bestToolsEnabled;
                save();
                return bestToolsEnabled;
        }

        boolean toggleRefillEnabled() {
                refillEnabled=!refillEnabled;
                save();
                return refillEnabled;
        }

        boolean toggleHotbarOnly() {
                hotbarOnly=!hotbarOnly;
                save();
                return hotbarOnly;
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
