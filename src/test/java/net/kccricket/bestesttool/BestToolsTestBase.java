package net.kccricket.bestesttool;

import net.kccricket.bestesttool.Main;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

public abstract class BestToolsTestBase {

    protected ServerMock server;
    protected Main plugin;

    @BeforeEach
    void setUpBase() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Main.class);
    }

    @AfterEach
    void tearDownBase() {
        MockBukkit.unmock();
    }

    protected PlayerMock newPlayer() {
        return server.addPlayer();
    }

    /**
     * {@code NEW} explicitly grants the node; {@code NONE} leaves it at its {@code paper-plugin.yml}
     * default (which is {@code true} for {@code bestesttool.use}/{@code bestesttool.refill}, so
     * {@code NONE} alone no longer means "denied" for those two — use {@code DENIED} to test an
     * actual denial); {@code DENIED} explicitly revokes the node regardless of its default.
     */
    public enum Grant { NEW, NONE, DENIED }

    protected void grant(PlayerMock player, String suffix, Grant grant) {
        switch (grant) {
            case NEW -> player.addAttachment(plugin).setPermission("bestesttool." + suffix, true);
            case NONE -> { /* no permission granted; falls back to the paper-plugin.yml default */ }
            case DENIED -> player.addAttachment(plugin).setPermission("bestesttool." + suffix, false);
        }
    }
}
