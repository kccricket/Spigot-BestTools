package net.kccricket.bestesttool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static org.junit.jupiter.api.Assertions.assertTrue;

public abstract class BestToolsTestBase {

    protected ServerMock server;
    protected BestestToolPlugin plugin;

    @BeforeEach
    void setUpBase() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(BestestToolPlugin.class);
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

    /**
     * Runs {@code action}, returning every message logged by the plugin logger — used to assert on
     * console-sender feedback, since {@link net.kccricket.kcmclib.text.Send} routes a
     * {@code ConsoleCommandSender} through {@link net.kccricket.kcmclib.logging.Log} rather than
     * {@code sendMessage}, so it never reaches {@code ConsoleCommandSender#nextMessage()}.
     */
    protected List<String> captureLogMessages(Runnable action) {
        List<String> messages = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                messages.add(record.getMessage());
            }

            @Override
            public void flush() {}

            @Override
            public void close() {}
        };
        plugin.getLogger().addHandler(handler);
        try {
            action.run();
        } finally {
            plugin.getLogger().removeHandler(handler);
        }
        return messages;
    }

    /** Asserts {@code action} logs a message containing {@code expectedSubstring}. */
    protected void assertLogMessageContains(String expectedSubstring, Runnable action) {
        List<String> messages = captureLogMessages(action);
        assertTrue(messages.stream().anyMatch(m -> m.contains(expectedSubstring)),
                "Expected a logged message containing \"" + expectedSubstring + "\"; got: " + messages);
    }
}
