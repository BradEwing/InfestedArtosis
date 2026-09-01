package macro;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays the frame order ProductionManager runs: the reactions, then the build order, then the
 * excess sweep.
 *
 * <p>The producers modelled are the non-Zerg ones, which ask for hatcheries off base counts, base
 * parity and floating minerals. Zerg parity is covered by HatcheryCapacityTest. The games that
 * carried 148,000 enqueue events were played against Terran, Protoss and Unknown opponents, so
 * this class holds the frames that let the loop through the first time.
 */
class HatcheryQueueContractTest {

    private static final int FRAMES_PER_100_SECONDS = 2400;

    private static final int FRAMES_PER_GAME = 36000;

    private static final int ENQUEUE_LIMIT_PER_GAME = 50;

    private static final int SATURATED_HATCHERIES = HatcheryCapacity.EXCESS_HATCHERIES;

    private static final int IDLE_LARVA = HatcheryCapacity.EXCESS_LARVA;

    /**
     * One game's hatchery bookkeeping. Holds the quantities the game moves, the waiting plans the
     * plan system moves, and the lifetime each cancelled plan achieved.
     */
    private static final class Board {
        private final List<Integer> expansionEnqueuedAt = new ArrayList<>();
        private final List<Integer> macroEnqueuedAt = new ArrayList<>();
        private final List<Integer> lifetimes = new ArrayList<>();
        private int frame;
        private int completedHatcheries;
        private int larva;
        private int minerals;
        private boolean earlyRushed;
        private boolean scvRushed;

        private boolean floatingMinerals() {
            return HatcheryCapacity.isFloatingMinerals(minerals, completedHatcheries, true);
        }

        private boolean excess() {
            return HatcheryCapacity.isExcess(completedHatcheries, larva, floatingMinerals());
        }

        private int waitingPlans() {
            return expansionEnqueuedAt.size() + macroEnqueuedAt.size();
        }

        private boolean mayQueueExpansion() {
            return waitingPlans() == 0
                    && HatcheryCapacity.isQueueable(excess(), earlyRushed || scvRushed);
        }

        private boolean mayQueueMacroHatchery() {
            return waitingPlans() == 0 && HatcheryCapacity.isQueueable(excess(), scvRushed);
        }

        private int totalEnqueues() {
            return lifetimes.size() + waitingPlans();
        }

        private int shortestLifetime() {
            int shortest = Integer.MAX_VALUE;
            for (int lifetime : lifetimes) {
                shortest = Math.min(shortest, lifetime);
            }
            return shortest;
        }

        private void cancelQueued(List<Integer> enqueuedAt) {
            for (int enqueueFrame : enqueuedAt) {
                lifetimes.add(frame - enqueueFrame);
            }
            enqueuedAt.clear();
        }
    }

    private static void runFrames(Board board, boolean wantsExpansion, boolean wantsMacro, int frames) {
        for (int i = 0; i < frames; i++) {
            board.frame++;
            runReactions(board);
            runBuildOrder(board, wantsExpansion, wantsMacro);
            runExcessSweep(board);
        }
    }

    private static void runReactions(Board board) {
        if (board.scvRushed) {
            board.cancelQueued(board.macroEnqueuedAt);
            board.cancelQueued(board.expansionEnqueuedAt);
        } else if (board.earlyRushed) {
            board.cancelQueued(board.expansionEnqueuedAt);
        }
    }

    private static void runBuildOrder(Board board, boolean wantsExpansion, boolean wantsMacro) {
        if (wantsExpansion && board.mayQueueExpansion()) {
            board.expansionEnqueuedAt.add(board.frame);
            return;
        }

        if (wantsMacro && board.mayQueueMacroHatchery()) {
            board.macroEnqueuedAt.add(board.frame);
        }
    }

    private static void runExcessSweep(Board board) {
        if (board.excess()) {
            board.cancelQueued(board.macroEnqueuedAt);
            board.cancelQueued(board.expansionEnqueuedAt);
        }
    }

    /**
     * The measured loop: three saturated hatcheries, idle larva and a mineral pile between one
     * and two hatchery prices. The old floating signal read the waiting plan count, so the
     * enqueue raised the bar, the sweep cancelled in the same frame, the cancel lowered the bar
     * and the next frame repeated it.
     */
    @Test
    void terranAndProtossProducersStayQuietWhileHatcheriesAreExcess() {
        Board board = new Board();
        board.completedHatcheries = SATURATED_HATCHERIES;
        board.larva = IDLE_LARVA;
        board.minerals = 500;

        runFrames(board, true, true, FRAMES_PER_100_SECONDS);

        assertEquals(0, board.totalEnqueues());
    }

    @Test
    void floatingMineralsQueueOnceAndSurvive() {
        Board board = new Board();
        board.completedHatcheries = SATURATED_HATCHERIES;
        board.larva = IDLE_LARVA;
        board.minerals = 2000;

        runFrames(board, true, true, FRAMES_PER_100_SECONDS);

        assertEquals(1, board.totalEnqueues());
        assertEquals(1, board.expansionEnqueuedAt.size());
        assertEquals(0, board.lifetimes.size());
    }

    /**
     * The Dave Churchill case. An early rush holds for the whole window while the build order
     * keeps asking for the natural and the third.
     */
    @Test
    void anEarlyRushStopsEveryExpansionEnqueue() {
        Board board = new Board();
        board.completedHatcheries = 2;
        board.larva = 0;
        board.minerals = 1200;
        board.earlyRushed = true;

        runFrames(board, true, true, FRAMES_PER_100_SECONDS);

        assertEquals(0, board.expansionEnqueuedAt.size());
        assertEquals(0, board.lifetimes.size());
    }

    /**
     * The early rush reaction deletes expansion hatcheries only, so the macro hatchery it asks
     * for still goes through.
     */
    @Test
    void anEarlyRushStillQueuesTheMacroHatcheryItAsksFor() {
        Board board = new Board();
        board.completedHatcheries = 2;
        board.larva = 0;
        board.minerals = 1200;
        board.earlyRushed = true;

        runFrames(board, false, true, FRAMES_PER_100_SECONDS);

        assertEquals(1, board.macroEnqueuedAt.size());
        assertEquals(0, board.lifetimes.size());
    }

    /**
     * The Ecgberht case. The SCV rush reaction deletes every hatchery plan, macro hatcheries
     * included, so both gates close.
     */
    @Test
    void anScvRushStopsEveryHatcheryEnqueue() {
        Board board = new Board();
        board.completedHatcheries = 2;
        board.larva = 0;
        board.minerals = 1200;
        board.scvRushed = true;

        runFrames(board, true, true, FRAMES_PER_100_SECONDS);

        assertEquals(0, board.totalEnqueues());
    }

    /**
     * The reaction clears, the producer queues, the state then turns excess and the sweep
     * cancels. The cancelled plan lives more than zero frames and does not come back while that
     * state holds.
     */
    @Test
    void aCancelledPlanIsNotRecreatedOnTheFollowingFrame() {
        Board board = new Board();
        board.completedHatcheries = SATURATED_HATCHERIES;
        board.larva = 0;
        board.minerals = 2000;
        board.earlyRushed = true;

        runFrames(board, true, false, 10);
        board.earlyRushed = false;
        runFrames(board, true, false, 10);
        assertEquals(1, board.expansionEnqueuedAt.size());

        board.minerals = 500;
        board.larva = IDLE_LARVA;
        runFrames(board, true, false, FRAMES_PER_100_SECONDS);

        assertEquals(1, board.totalEnqueues());
        assertTrue(board.shortestLifetime() > 0);
    }

    /**
     * A full game at 24 frames per second across every non-Zerg canceller, each holding its
     * worst case for the whole game.
     */
    @Test
    void noNonZergGameReachesTheEnqueueLimit() {
        int worstGame = enqueuesFor(SATURATED_HATCHERIES, IDLE_LARVA, 500, false, false);

        worstGame = Math.max(worstGame, enqueuesFor(SATURATED_HATCHERIES, IDLE_LARVA, 2000, false, false));
        worstGame = Math.max(worstGame, enqueuesFor(2, 0, 1200, true, false));
        worstGame = Math.max(worstGame, enqueuesFor(2, 0, 1200, false, true));

        assertTrue(worstGame <= ENQUEUE_LIMIT_PER_GAME);
    }

    private static int enqueuesFor(int hatcheries, int larva, int minerals, boolean earlyRushed, boolean scvRushed) {
        Board board = new Board();
        board.completedHatcheries = hatcheries;
        board.larva = larva;
        board.minerals = minerals;
        board.earlyRushed = earlyRushed;
        board.scvRushed = scvRushed;

        runFrames(board, true, true, FRAMES_PER_GAME);

        return board.totalEnqueues();
    }
}
