package net.kccricket.bestesttool.selftest;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds and tears down one stage's arena: a line of labelled case blocks (BLOCKS/REFILL stages)
 * or docile mobs (COMBAT stages), spaced out along the tester's cardinal facing. Every block this
 * touches is snapshotted before being overwritten and restored verbatim in {@link #teardown()}.
 */
public final class SelfTestArena {

    /** Blocks between adjacent case positions along the facing axis, so hitboxes/swing range don't overlap. */
    private static final int SPACING = 3;
    private static final double LABEL_HEIGHT = 1.6;

    private final SelfTestSpec.Stage stage;
    private final List<Location> positions = new ArrayList<>();
    private final List<BlockState> savedStates = new ArrayList<>();
    private final List<TextDisplay> labels = new ArrayList<>();
    private final List<Entity> mobs = new ArrayList<>();

    private SelfTestArena(SelfTestSpec.Stage stage) {
        this.stage = stage;
    }

    private static List<Location> computePositions(Player player, SelfTestSpec.Stage stage) {
        Location origin = player.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
        BlockFace facing = player.getFacing();
        int count = stage.cases.size();

        List<Location> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int distance = SPACING * (i + 1);
            positions.add(origin.clone().add(facing.getModX() * distance, 0, facing.getModZ() * distance));
        }
        return positions;
    }

    /** Whether every case position ahead of {@code player} is currently clear air. */
    static boolean hasSpace(Player player, SelfTestSpec.Stage stage) {
        for (Location loc : computePositions(player, stage)) {
            if (loc.getBlock().getType() != Material.AIR) return false;
        }
        return true;
    }

    /**
     * @return the built arena, or {@code null} if there isn't a clear line of air ahead of
     * {@code player} long enough to hold every case — the caller should ask the tester to move.
     */
    static SelfTestArena build(Player player, SelfTestSpec.Stage stage, Material pedestal) {
        World world = player.getWorld();
        List<Location> positions = computePositions(player, stage);
        int count = stage.cases.size();

        for (Location loc : positions) {
            if (loc.getBlock().getType() != Material.AIR) return null;
        }

        SelfTestArena arena = new SelfTestArena(stage);
        arena.positions.addAll(positions);

        for (int i = 0; i < count; i++) {
            SelfTestSpec.Case c = stage.cases.get(i);
            Location loc = positions.get(i);
            switch (stage.kind) {
                case BLOCKS -> arena.placeBlockCase(loc, pedestal, c);
                case REFILL -> arena.placeMarker(loc, pedestal, c);
                case COMBAT -> arena.spawnMob(world, loc, pedestal, c);
            }
        }
        return arena;
    }

    private void placeBlockCase(Location loc, Material pedestal, SelfTestSpec.Case c) {
        Block pedestalBlock = loc.clone().add(0, -1, 0).getBlock();
        savedStates.add(pedestalBlock.getState());
        pedestalBlock.setType(pedestal, false);

        Block subjectBlock = loc.getBlock();
        savedStates.add(subjectBlock.getState());
        subjectBlock.setType(c.blockSubject, false);

        labels.add(spawnLabel(loc, c.subjectName() + " — expect " + c.expectation.describe()));
    }

    private void placeMarker(Location loc, Material pedestal, SelfTestSpec.Case c) {
        Block pedestalBlock = loc.clone().add(0, -1, 0).getBlock();
        savedStates.add(pedestalBlock.getState());
        pedestalBlock.setType(pedestal, false);

        labels.add(spawnLabel(loc, "Place " + c.subjectName() + " here"));
    }

    private void spawnMob(World world, Location loc, Material pedestal, SelfTestSpec.Case c) {
        Block pedestalBlock = loc.clone().add(0, -1, 0).getBlock();
        savedStates.add(pedestalBlock.getState());
        pedestalBlock.setType(pedestal, false);

        Entity entity = world.spawnEntity(loc.clone().add(0, 0.1, 0), c.entitySubject);
        if (entity instanceof LivingEntity living) {
            living.setAI(false);
            living.setCollidable(false);
            living.setSilent(true);
            living.setPersistent(false);
            living.setRemoveWhenFarAway(true);
        }
        mobs.add(entity);
        labels.add(spawnLabel(loc, c.subjectName() + " — expect " + c.expectation.describe()));
    }

    private TextDisplay spawnLabel(Location loc, String text) {
        return loc.getWorld().spawn(loc.clone().add(0, LABEL_HEIGHT, 0), TextDisplay.class, td -> {
            td.text(Component.text(text, NamedTextColor.WHITE));
            td.setBillboard(Display.Billboard.CENTER);
            td.setPersistent(false);
            td.setSeeThrough(true);
        });
    }

    /** Index of the case whose position matches {@code loc}'s block, or -1. */
    int caseIndexAt(Location loc) {
        for (int i = 0; i < positions.size(); i++) {
            Location p = positions.get(i);
            if (p.getBlockX() == loc.getBlockX() && p.getBlockY() == loc.getBlockY() && p.getBlockZ() == loc.getBlockZ()) {
                return i;
            }
        }
        return -1;
    }

    /** Index of the case whose spawned mob is {@code entity}, or -1. */
    int caseIndexOf(Entity entity) {
        return mobs.indexOf(entity);
    }

    /** Appends a pass/fail mark to a case's label. No-op if the label entity is gone. */
    void markResult(int index, boolean pass) {
        if (index < 0 || index >= labels.size()) return;
        TextDisplay td = labels.get(index);
        if (td == null || !td.isValid()) return;
        Component mark = Component.text(pass ? " ✔" : " ✘", pass ? NamedTextColor.GREEN : NamedTextColor.RED);
        td.text(td.text().append(mark));
    }

    void teardown() {
        for (BlockState state : savedStates) {
            state.update(true, false);
        }
        // A REFILL case's marker position starts as air, but the tester places a real block there
        // as part of the test — put it back to air explicitly, since no BlockState was captured for
        // a spot that was already air to begin with.
        if (stage.kind == SelfTestSpec.StageKind.REFILL) {
            for (Location loc : positions) {
                loc.getBlock().setType(Material.AIR, false);
            }
        }
        for (TextDisplay td : labels) {
            if (td != null && td.isValid()) td.remove();
        }
        for (Entity e : mobs) {
            if (e != null && e.isValid()) e.remove();
        }
    }
}
