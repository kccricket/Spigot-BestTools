package net.kccricket.bestesttool;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import net.kccricket.bestesttool.text.MessageUtil;
import net.kccricket.kcmclib.logging.Log;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orchestrates {@code /bestesttool benchmark}: owns the single server-wide run (if any) and its
 * {@code GlobalRegionScheduler} task. {@code GlobalRegionScheduler} (not
 * {@code entity.getScheduler()}/{@code AsyncScheduler}) is the right Folia-safe choice here — the
 * workload is fully synthetic (see {@link BenchmarkWorkload}), tied to no entity or region, and
 * the ramp needs to be tick-aligned so a batch's wall time is directly comparable to the 50ms tick
 * budget; {@code Bukkit.getScheduler()} is never used, per this project's Folia rule.
 * <p>
 * Constructed exactly once, in {@link Main#onEnable} — not in {@link Main#load}, which re-runs on
 * every {@code /bestesttool reload} — mirroring {@link SelfTestManager}. Unlike a self-test run, a
 * benchmark does not survive a reload: {@link #abortAll} is called from {@code Main.load}'s reload
 * branch and from {@code Main.onDisable}, both of which just stop the scheduled task — there's no
 * player state to restore, since the workload never touches a real player or the world.
 */
final class BenchmarkManager {

    private final Main main;
    private final AtomicReference<Session> active = new AtomicReference<>();

    BenchmarkManager(Main main) {
        this.main = main;
    }

    /** One in-flight run: its ramp state, workload, scheduled task, and who to report to. */
    private static final class Session {
        final CommandSender sender;
        final BenchmarkWorkload.KitSize kitSize;
        final BenchmarkRun run = new BenchmarkRun();
        final Material[] materials;
        final BlockData[] blockData;
        final ItemStack[] kit;
        ScheduledTask task;

        // Consumes the selection result so the JIT can't prove the call is dead code and elide it.
        @SuppressWarnings("unused")
        int sink;

        Session(CommandSender sender, BenchmarkWorkload.KitSize kitSize) {
            this.sender = sender;
            this.kitSize = kitSize;
            this.materials = BenchmarkWorkload.MATERIALS.toArray(new Material[0]);
            this.blockData = BenchmarkWorkload.blockData();
            this.kit = BenchmarkWorkload.buildKit(kitSize);
        }
    }

    /** Whether a run is currently active server-wide. */
    boolean isRunning() {
        return active.get() != null;
    }

    void start(CommandSender sender, BenchmarkWorkload.KitSize kitSize) {
        Session session = new Session(sender, kitSize);
        if (!active.compareAndSet(null, session)) {
            MessageUtil.send(sender, "benchmarkAlreadyRunning");
            return;
        }

        MessageUtil.send(sender, "benchmarkStarted",
                Placeholder.unparsed("kit", kitSize.name().toLowerCase(Locale.ROOT)));

        session.task = main.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(main, ignored -> tick(session), 1L, 1L);
    }

    void stop(CommandSender sender) {
        Session session = active.getAndSet(null);
        if (session == null) {
            MessageUtil.send(sender, "benchmarkNoneRunning");
            return;
        }
        cancel(session);
        MessageUtil.send(sender, "benchmarkStopped");
    }

    void status(CommandSender sender) {
        Session session = active.get();
        if (session == null) {
            MessageUtil.send(sender, "benchmarkNoneRunning");
            return;
        }
        BenchmarkRun.BatchResult last = session.run.lastBatch();
        MessageUtil.send(sender, "benchmarkStatus",
                Placeholder.unparsed("n", last == null ? "-" : String.valueOf(last.n())),
                Placeholder.unparsed("ms", last == null ? "-" : String.format(Locale.US, "%.2f", last.elapsedMillis())));
    }

    /** Ends the run, if any, with no message to the original sender — called on reload/disable. */
    void abortAll(String reason) {
        Session session = active.getAndSet(null);
        if (session == null) return;
        cancel(session);
        Log.debug("Aborted benchmark (" + reason + ")");
    }

    private void cancel(Session session) {
        if (session.task != null) session.task.cancel();
    }

    private void tick(Session session) {
        int n = session.run.nextBatchSize();

        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            int idx = i % session.materials.length;
            ItemStack result = main.toolHandler.selectBestTool(
                    session.blockData[idx], session.materials[idx], session.kit, BenchmarkWorkload.NEVER_SILK);
            if (result != null) session.sink += result.hashCode();
        }
        long elapsed = System.nanoTime() - start;

        session.run.record(elapsed);

        BenchmarkRun.BatchResult justRan = session.run.lastBatch();
        if (justRan != null) {
            main.getLogger().info(String.format(Locale.US,
                    "[benchmark] batch %,d selections in %.2f ms (%.1f ns/selection)",
                    justRan.n(), justRan.elapsedMillis(), justRan.nsPerSelection()));
        }

        if (session.run.isDone()) {
            active.compareAndSet(session, null);
            cancel(session);

            BenchmarkRun.Summary summary = session.run.summary();
            MessageUtil.send(session.sender, "benchmarkFinished",
                    Placeholder.unparsed("kit", session.kitSize.name().toLowerCase(Locale.ROOT)),
                    Placeholder.unparsed("peak", String.valueOf(summary.peakBatchSize())),
                    Placeholder.unparsed("nspersel", String.format(Locale.US, "%.1f", summary.nsPerSelection())),
                    Placeholder.unparsed("pertick", String.valueOf(summary.selectionsPerTickBudget())));
        }
    }
}
