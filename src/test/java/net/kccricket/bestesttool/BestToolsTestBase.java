package net.kccricket.bestesttool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

abstract class BestToolsTestBase {

    ServerMock server;
    Main plugin;

    @BeforeEach
    void setUpBase() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(Main.class);
    }

    @AfterEach
    void tearDownBase() {
        MockBukkit.unmock();
    }

    PlayerMock newPlayer() {
        return server.addPlayer();
    }

    enum Grant { NEW, NONE }

    void grant(PlayerMock player, String suffix, Grant grant) {
        switch (grant) {
            case NEW -> player.addAttachment(plugin).setPermission("bestesttool." + suffix, true);
            case NONE -> { /* no permission granted */ }
        }
    }
}
