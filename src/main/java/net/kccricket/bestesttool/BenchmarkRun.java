package net.kccricket.bestesttool;

import java.util.ArrayList;
import java.util.List;

/**
 * The pure ramp state machine behind {@code /bestesttool benchmark}: no Bukkit types, no
 * scheduler — just "how big should the next batch be" and "here's how long that batch took, what
 * next". Kept free of any live selection call (or even a Bukkit dependency) so it's unit-testable
 * by feeding in synthetic elapsed-nanos samples; MockBukkit's {@code BlockDataMock} can't actually
 * run {@link BestToolsHandler#selectBestTool} (see {@code ToolSelectionTest}'s note on why), so the
 * real call can only ever be exercised on a live server, but the ramp/stop logic doesn't need one.
 * <p>
 * One caller-driven cycle per tick: {@link #nextBatchSize()} says how many selections to run,
 * {@link #record(long)} reports how long that took. The very first batch is a warm-up — its timing
 * is discarded (JIT compilation and any lazy one-time state, e.g. the Silk Touch memo cache, would
 * otherwise pollute the first real measurement) — then the batch size doubles each tick until one
 * takes at least the 50ms tick budget, or a safety cap is hit.
 */
final class BenchmarkRun {

    static final long TICK_BUDGET_NANOS = 50_000_000L;
    static final int INITIAL_BATCH = 64;
    static final int MAX_BATCH_SIZE = 50_000_000;
    static final int MAX_TICKS = 40;

    record BatchResult(int n, long elapsedNanos) {
        double elapsedMillis() {
            return elapsedNanos / 1_000_000.0;
        }

        double nsPerSelection() {
            return elapsedNanos / (double) n;
        }
    }

    /**
     * @param peakBatchSize          the largest batch that stayed under the tick budget (0 if even
     *                               the first real batch didn't)
     * @param nsPerSelection         cost per selection, taken from the batch closest to the budget
     *                               (least proportional timer noise) that stayed under it — or,
     *                               failing that, from the first batch that went over
     * @param selectionsPerTickBudget {@code TICK_BUDGET_NANOS / nsPerSelection} — the headline
     *                               number, more precise than the doubling ladder's last rung
     * @param allBatches             every recorded (non-warm-up) batch, in order, for a full table
     */
    record Summary(int peakBatchSize, double nsPerSelection, long selectionsPerTickBudget,
                    List<BatchResult> allBatches) {}

    private boolean warmupDone = false;
    private int currentBatch = INITIAL_BATCH;
    private int ticksRun = 0;
    private boolean done = false;

    private final List<BatchResult> results = new ArrayList<>();
    private BatchResult peak;
    private BatchResult overBudget;

    /** How many selections the caller should run this tick. */
    int nextBatchSize() {
        if (done) throw new IllegalStateException("Benchmark already finished");
        return warmupDone ? currentBatch : INITIAL_BATCH;
    }

    /** Reports how long the batch {@link #nextBatchSize()} just returned actually took. */
    void record(long elapsedNanos) {
        if (done) throw new IllegalStateException("Benchmark already finished");

        if (!warmupDone) {
            warmupDone = true;
            return;
        }

        BatchResult result = new BatchResult(currentBatch, elapsedNanos);
        results.add(result);
        ticksRun++;

        if (elapsedNanos < TICK_BUDGET_NANOS) {
            peak = result;
            if (currentBatch >= MAX_BATCH_SIZE || ticksRun >= MAX_TICKS) {
                done = true;
            } else {
                currentBatch = (int) Math.min((long) currentBatch * 2, MAX_BATCH_SIZE);
            }
        } else {
            overBudget = result;
            done = true;
        }
    }

    boolean isDone() {
        return done;
    }

    /** How many (non-warm-up) batches have completed so far — useful for a mid-run status line. */
    int completedBatches() {
        return results.size();
    }

    BatchResult lastBatch() {
        return results.isEmpty() ? null : results.get(results.size() - 1);
    }

    Summary summary() {
        if (!done) throw new IllegalStateException("Benchmark still running");

        // Normally `peak` is set — the run only stops without one if even the first real batch
        // (INITIAL_BATCH) blew the tick budget, in which case `overBudget` is the only data point
        // there is; treat its per-selection cost as the best available estimate.
        BatchResult basis = peak != null ? peak : overBudget;
        double nsPerSelection = basis.nsPerSelection();
        long perTickBudget = Math.max(1L, (long) (TICK_BUDGET_NANOS / nsPerSelection));

        return new Summary(peak != null ? peak.n() : 0, nsPerSelection, perTickBudget, List.copyOf(results));
    }
}
