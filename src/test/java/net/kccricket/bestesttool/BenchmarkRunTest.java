package net.kccricket.bestesttool;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the pure ramp state machine behind {@code /bestesttool benchmark}. No Bukkit/
 * MockBukkit involved — {@link BenchmarkRun} takes fed-in elapsed-nanos samples, which is the
 * point: the real selection call it would otherwise drive can't run under MockBukkit at all (see
 * {@code ToolSelectionTest}'s note on {@code BlockDataMock}), so this is the only way to exercise
 * the ramp/stop logic in the automated suite.
 */
class BenchmarkRunTest {

    @Test
    void firstRealBatchIsInitialSizeAfterDiscardedWarmup() {
        BenchmarkRun run = new BenchmarkRun();

        assertEquals(BenchmarkRun.INITIAL_BATCH, run.nextBatchSize());
        run.record(1_000_000L); // warm-up — discarded, not counted as a real batch
        assertFalse(run.isDone());
        assertEquals(0, run.completedBatches());

        assertEquals(BenchmarkRun.INITIAL_BATCH, run.nextBatchSize());
    }

    @Test
    void batchSizeDoublesEachTickWhileUnderBudget() {
        BenchmarkRun run = new BenchmarkRun();
        run.record(1_000L); // discard warm-up

        int expected = BenchmarkRun.INITIAL_BATCH;
        for (int i = 0; i < 5; i++) {
            assertEquals(expected, run.nextBatchSize());
            run.record(1_000L); // comfortably under budget
            expected *= 2;
        }
        assertFalse(run.isDone());
        assertEquals(5, run.completedBatches());
    }

    @Test
    void stopsOnceABatchReachesTheTickBudget() {
        BenchmarkRun run = new BenchmarkRun();
        run.record(1_000L); // discard warm-up

        run.record(10_000_000L); // batch 1 (n=64): comfortably under 50ms
        assertFalse(run.isDone());

        run.record(BenchmarkRun.TICK_BUDGET_NANOS); // batch 2 (n=128): exactly at budget -> over
        assertTrue(run.isDone());

        BenchmarkRun.Summary summary = run.summary();
        assertEquals(BenchmarkRun.INITIAL_BATCH, summary.peakBatchSize());
    }

    @Test
    void summaryDerivesSelectionsPerTickFromThePeakBatch() {
        BenchmarkRun run = new BenchmarkRun();
        run.record(1_000L); // discard warm-up

        run.record(64L * 1000); // batch 1 (n=64): 1000 ns/selection, well under budget
        run.record(BenchmarkRun.TICK_BUDGET_NANOS + 1); // batch 2 (n=128): over budget

        BenchmarkRun.Summary summary = run.summary();
        assertEquals(64, summary.peakBatchSize());
        assertEquals(1000.0, summary.nsPerSelection(), 0.001);
        assertEquals(BenchmarkRun.TICK_BUDGET_NANOS / 1000, summary.selectionsPerTickBudget());
    }

    @Test
    void degenerateCaseWhereTheFirstRealBatchAlreadyBlowsBudgetStillSummarizes() {
        BenchmarkRun run = new BenchmarkRun();
        run.record(1_000L); // discard warm-up

        run.record(BenchmarkRun.TICK_BUDGET_NANOS * 2); // n=64 already over budget

        assertTrue(run.isDone());
        BenchmarkRun.Summary summary = run.summary();
        assertEquals(0, summary.peakBatchSize());
        assertTrue(summary.nsPerSelection() > 0);
        assertTrue(summary.selectionsPerTickBudget() >= 1);
    }

    @Test
    void maxBatchSizeCapStopsTheRampEvenWhenAlwaysUnderBudget() {
        BenchmarkRun run = new BenchmarkRun();
        run.record(1L); // discard warm-up

        int lastSize = 0;
        while (!run.isDone()) {
            lastSize = run.nextBatchSize();
            run.record(1L); // "instant" — never blows the budget on its own
        }

        assertEquals(BenchmarkRun.MAX_BATCH_SIZE, lastSize);
    }

    @Test
    void maxBatchSizeCapIsReachedWellBeforeMaxTicks() {
        // MAX_TICKS is a belt-and-suspenders backup, not the binding cap under normal doubling —
        // confirm the doubling ladder from INITIAL_BATCH reaches MAX_BATCH_SIZE in fewer than
        // MAX_TICKS steps, which is what maxBatchSizeCapStopsTheRampEvenWhenAlwaysUnderBudget
        // above actually relies on to terminate via the batch-size cap rather than the tick cap.
        int ticksToCap = 0;
        long size = BenchmarkRun.INITIAL_BATCH;
        while (size < BenchmarkRun.MAX_BATCH_SIZE) {
            size *= 2;
            ticksToCap++;
        }
        assertTrue(ticksToCap < BenchmarkRun.MAX_TICKS);
    }

    @Test
    void nextBatchSizeAndRecordThrowOnceDone() {
        BenchmarkRun run = new BenchmarkRun();
        run.record(1L); // discard warm-up
        run.record(BenchmarkRun.TICK_BUDGET_NANOS); // over budget -> done

        assertTrue(run.isDone());
        assertThrows(IllegalStateException.class, run::nextBatchSize);
        assertThrows(IllegalStateException.class, () -> run.record(1L));
    }

    @Test
    void summaryThrowsWhileStillRunning() {
        BenchmarkRun run = new BenchmarkRun();
        assertThrows(IllegalStateException.class, run::summary);
    }
}
