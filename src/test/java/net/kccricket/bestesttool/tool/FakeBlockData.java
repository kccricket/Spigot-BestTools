package net.kccricket.bestesttool.tool;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.mockbukkit.mockbukkit.block.data.BlockDataMock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal live-mining-data double for {@link BestToolsHandler#getBestItemStackFromArray}/
 * {@link BestToolsHandler#selectBestTool}. MockBukkit's own {@code BlockDataMock} throws
 * {@code UnimplementedOperationException} for getDestroySpeed/isPreferredTool/
 * requiresCorrectToolForDrops, so this fake overrides just those three real Paper API methods
 * with canned per-Material answers. Extending BlockDataMock rather than implementing the (large)
 * BlockData interface from scratch keeps everything else inherited — those extra methods are
 * never called by the code under test.
 */
public final class FakeBlockData extends BlockDataMock {
    private final boolean requiresCorrect;
    private final Map<Material, Float> speeds;
    private final Set<Material> preferred;
    final List<ItemStack> preferredToolChecks = new ArrayList<>();

    public FakeBlockData(Material material, boolean requiresCorrect, Map<Material, Float> speeds, Set<Material> preferred) {
        super(material);
        this.requiresCorrect = requiresCorrect;
        this.speeds = speeds;
        this.preferred = preferred;
    }

    @Override
    public boolean requiresCorrectToolForDrops() {
        return requiresCorrect;
    }

    @Override
    public float getDestroySpeed(ItemStack itemStack, boolean considerEnchants) {
        return speeds.getOrDefault(itemStack.getType(), 1.0f);
    }

    @Override
    public boolean isPreferredTool(ItemStack tool) {
        preferredToolChecks.add(tool);
        return preferred.contains(tool.getType());
    }

    /**
     * BlockDataMock#clone() returns a plain new BlockDataMock, which would silently discard these
     * three overrides — and BlockMock#setBlockData stores blockData.clone(), so a fake handed to a
     * block would come back out of getBlockData() throwing UnimplementedOperationException again.
     * This fake is immutable, so returning itself is safe and is what makes it survive a round-trip
     * through a BlockMock.
     */
    @Override
    public FakeBlockData clone() {
        return this;
    }
}
