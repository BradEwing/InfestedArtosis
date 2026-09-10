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
 * <p>The board models what the plan system does with a hatchery plan, not what the production
 * queue holds: a plan moves out of the queue and into the scheduled, building or morphing set on
 * the frame it is created, and stays in flight there until it completes or a canceller takes it.
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
     * One game's hatchery bookkeeping. Holds the quantities the game moves, the in-flight plans
     * the plan system moves, and the lifetime each cancelled plan achieved.
     */
    private static final class Board {
        private final List<Integer> expansionInFlight = new ArrayList<>();
        private final List<Integer> macroInFlight = new ArrayList<>();
        private final List<Integer> enqueueFrames = new ArrayList<>();
        private final List<Integer> completionFrames = new ArrayList<>();
        private final List<Integer> lifetimes = new ArrayList<>();
        private int frame;
        private int completedHatcheries;
        private int larva;
        private int minerals;
        private boolean earlyRushed;
        private boolean scvRushed;
        private int lastEnqueueFrame = -HatcheryCapacity.ENQUEUE_COOLDOWN_FRAMES;
        private int hatcheriesAtLastEnqueue;

        private boolean floatingMinerals() {
            return HatcheryCapacity.isFloatingMinerals(minerals, completedHatcheries, true);
        }

        private boolean excess() {
            return HatcheryCapacity.isExcess(completedHatcheries, larva);
        }

        private int inFlightPlans() {
            return expansionInFlight.size() + macroInFlight.size();
        }

        private boolean rearmed() {
            return HatcheryCapacity.isEnqueueRearmed(
                    inFlightPlans(),
                    frame - lastEnqueueFrame,
                    completedHatcheries - hatcheriesAtLastEnqueue);
        }

        private boolean mayQueueExpansion() {
            return rearmed() && HatcheryCapacity.isQueueable(excess(), earlyRushed || scvRushed);
        }

        private boolean mayQueueMacroHatchery() {
            return rearmed() && HatcheryCapacity.isQueueable(excess(), scvRushed);
        }

        private void enqueue(List<Integer> inFlight) {
            inFlight.add(frame);
            enqueueFrames.add(frame);
            lastEnqueueFrame = frame;
            hatcheriesAtLastEnqueue = completedHatcheries;
        }

        private int totalEnqueues() {
            return enqueueFrames.size();
        }

        private int shortestLifetime() {
            int shortest = Integer.MAX_VALUE;
            for (int lifetime : lifetimes) {
                shortest = Math.min(shortest, lifetime);
            }
            return shortest;
        }

        private void cancelInFlight(List<Integer> inFlight) {
            for (int enqueueFrame : inFlight) {
                lifetimes.add(frame - enqueueFrame);
            }
            inFlight.clear();
        }

        private void completeOldestExpansion() {
            if (expansionInFlight.isEmpty()) {
                return;
            }
            expansionInFlight.remove(0);
            completedHatcheries++;
            completionFrames.add(frame);
        }

        private int shortestGapWithoutACompletion() {
            int shortest = Integer.MAX_VALUE;
            for (int i = 1; i < enqueueFrames.size(); i++) {
                int previous = enqueueFrames.get(i - 1);
                int current = enqueueFrames.get(i);
                if (!completedBetween(previous, current)) {
                    shortest = Math.min(shortest, current - previous);
                }
            }
            return shortest;
        }

        private boolean completedBetween(int from, int to) {
            for (int completion : completionFrames) {
                if (completion > from && completion < to) {
                    return true;
                }
            }
            return false;
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

    /**
     * The same frame order, with the expansion request read off the mineral pile rather than
     * held true by the caller.
     */
    private static void runFloatingFrames(Board board, int frames) {
        for (int i = 0; i < frames; i++) {
            board.frame++;
            runReactions(board);
            runBuildOrder(board, HatcheryCapacity.isFloatingExpansion(board.floatingMinerals(), board.earlyRushed), false);
            runExcessSweep(board);
        }
    }

    private static void runReactions(Board board) {
        if (board.scvRushed) {
            board.cancelInFlight(board.macroInFlight);
            board.cancelInFlight(board.expansionInFlight);
        } else if (board.earlyRushed) {
            board.cancelInFlight(board.expansionInFlight);
        }
    }

    private static void runBuildOrder(Board board, boolean wantsExpansion, boolean wantsMacro) {
        if (wantsExpansion && board.mayQueueExpansion()) {
            board.enqueue(board.expansionInFlight);
            return;
        }

        if (wantsMacro && board.mayQueueMacroHatchery()) {
            board.enqueue(board.macroInFlight);
        }
    }

    private static void runExcessSweep(Board board) {
        if (board.excess()) {
            board.cancelInFlight(board.macroInFlight);
            board.cancelInFlight(board.expansionInFlight);
        }
    }

    /**
     * The measured loop: three saturated hatcheries, idle larva and a mineral pile between one
     * and two hatchery prices.
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

    /**
     * Idle larva are what the excess rule reads, so a mineral pile no longer buys an expansion
     * the sweep would cancel on the same frame.
     */
    @Test
    void floatingMineralsDoNotQueueWhileLarvaAreIdle() {
        Board board = new Board();
        board.completedHatcheries = SATURATED_HATCHERIES;
        board.larva = IDLE_LARVA;
        board.minerals = 2000;

        runFrames(board, true, true, FRAMES_PER_100_SECONDS);

        assertEquals(0, board.totalEnqueues());
    }

    @Test
    void floatingMineralsQueueOnceAndSurvive() {
        Board board = new Board();
        board.completedHatcheries = SATURATED_HATCHERIES;
        board.larva = 0;
        board.minerals = 2000;

        runFrames(board, true, true, FRAMES_PER_100_SECONDS);

        assertEquals(1, board.totalEnqueues());
        assertEquals(1, board.expansionInFlight.size());
        assertEquals(0, board.lifetimes.size());
    }

    /**
     * Game L4KVD0CN. One hatchery, 706 minerals against a 700 bar and no larva to spend, held
     * for the whole window the expansion took to finish. It queued three hatcheries in three
     * frames; the in-flight count now holds the request to one.
     */
    @Test
    void theFloatingRequestThatQueuedThreeHatcheriesInThreeFramesQueuesOne() {
        Board board = new Board();
        board.completedHatcheries = 1;
        board.larva = 0;
        board.minerals = 706;

        runFloatingFrames(board, 1800);

        assertEquals(1, board.totalEnqueues());
        assertEquals(1, board.expansionInFlight.size());
    }

    /**
     * The hatchery the request asked for completes, which raises the floating bar past the
     * mineral pile and ends the request. Nothing waits out a cooldown to notice.
     */
    @Test
    void aCompletedHatcheryEndsTheRequestThatAskedForIt() {
        Board board = new Board();
        board.completedHatcheries = 1;
        board.larva = 0;
        board.minerals = 706;

        runFloatingFrames(board, 100);
        assertEquals(1, board.totalEnqueues());

        board.completeOldestExpansion();
        runFloatingFrames(board, FRAMES_PER_100_SECONDS);

        assertEquals(1, board.totalEnqueues());
    }

    /**
     * A canceller taking the plan does not re-arm the request. However often the plan is taken,
     * two enqueues with no hatchery completing between them stay a cooldown apart.
     */
    @Test
    void aRequestCancelledEveryFrameStillWaitsOutTheCooldown() {
        Board board = new Board();
        board.completedHatcheries = 2;
        board.larva = 0;
        board.minerals = 1200;

        for (int i = 0; i < FRAMES_PER_GAME; i++) {
            board.frame++;
            runBuildOrder(board, true, false);
            board.cancelInFlight(board.expansionInFlight);
        }

        assertTrue(board.totalEnqueues() > 1);
        assertTrue(board.shortestGapWithoutACompletion() >= HatcheryCapacity.ENQUEUE_COOLDOWN_FRAMES);
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

        assertEquals(0, board.expansionInFlight.size());
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

        assertEquals(1, board.macroInFlight.size());
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
        assertEquals(1, board.expansionInFlight.size());

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
        worstGame = Math.max(worstGame, enqueuesFor(SATURATED_HATCHERIES, 0, 2000, false, false));
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
