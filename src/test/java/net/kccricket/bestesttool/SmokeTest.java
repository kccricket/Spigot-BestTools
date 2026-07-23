package net.kccricket.bestesttool;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SmokeTest extends BestToolsTestBase {

    @Test
    void pluginLoadsAndBuildsToolMap() {
        assertNotNull(plugin.toolHandler);
        assertEquals(BestToolsHandler.Tool.PICKAXE, plugin.toolHandler.getBestToolType(Material.STONE));
    }
}
