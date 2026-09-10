package macro;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays the frame order ProductionManager runs: the reactions, then the build order, then the
 * excess sweep, then the schedule pass.
 *
 * <p>The board models what the plan system does with a hatchery plan, not what the production
 * queue holds. A plan is created into the queue and moves into the scheduled, building or
 * morphing set on the same frame, and stays outstanding there until it completes or a canceller
 * takes it. The two stages are modelled separately because the rush reactions delete hatchery
 * plans out of the queue only, so a plan that has already left the queue survives them.
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

    /** Far longer than the cooldown, so only the outstanding count can hold the request. */
    private static final int A_LONG_HOLD = 1800;

    private static final int SATURATED_HATCHERIES = HatcheryCapacity.EXCESS_HATCHERIES;

    private static final int IDLE_LARVA = HatcheryCapacity.EXCESS_LARVA;

    /**
     * The plans of one hatchery kind. Queued plans are what a rush reaction can delete; active
     * plans have left the queue for the scheduled, building or morphing set.
     */
    private static final class Kind {
        private final List<Integer> queued = new ArrayList<>();
        private final List<Integer> active = new ArrayList<>();

        private int outstanding() {
            return queued.size() + active.size();
        }
    }

    /**
     * One game's hatchery bookkeeping. Holds the quantities the game moves, the plans the plan
     * system moves, and the lifetime each cancelled plan achieved.
     */
    private static final class Board {
        private final Kind expansion = new Kind();
        private final Kind macro = new Kind();
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

        private boolean floatingMinerals() {
            return HatcheryCapacity.isFloatingMinerals(minerals, completedHatcheries, true);
        }

        private boolean excess() {
            return HatcheryCapacity.isExcess(completedHatcheries, larva);
        }

        private boolean rearmed(Kind kind) {
            return HatcheryCapacity.isEnqueueRearmed(kind.outstanding(), frame - lastEnqueueFrame);
        }

        private boolean mayQueueExpansion() {
            return rearmed(expansion)
                    && HatcheryCapacity.isQueueable(excess(), earlyRushed || scvRushed);
        }

        private boolean mayQueueMacroHatchery() {
            return rearmed(macro) && HatcheryCapacity.isQueueable(excess(), scvRushed);
        }

        private void enqueue(Kind kind) {
            kind.queued.add(frame);
            enqueueFrames.add(frame);
            lastEnqueueFrame = frame;
        }

        private void schedule() {
            expansion.active.addAll(expansion.queued);
            expansion.queued.clear();
            macro.active.addAll(macro.queued);
            macro.queued.clear();
        }

        private void cancel(List<Integer> plans) {
            for (int enqueueFrame : plans) {
                lifetimes.add(frame - enqueueFrame);
            }
            plans.clear();
        }

        private void cancelEverything(Kind kind) {
            cancel(kind.queued);
            cancel(kind.active);
        }

        private void completeOldestExpansion() {
            if (expansion.active.isEmpty()) {
                return;
            }
            expansion.active.remove(0);
            completedHatcheries++;
            completionFrames.add(frame);
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
            board.schedule();
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
            board.schedule();
        }
    }

    /**
     * Both rush reactions call removeWhere on the production queue, so they reach queued plans
     * only. A plan a drone is already walking to survives.
     */
    private static void runReactions(Board board) {
        if (board.scvRushed) {
            board.cancel(board.macro.queued);
            board.cancel(board.expansion.queued);
        } else if (board.earlyRushed) {
            board.cancel(board.expansion.queued);
        }
    }

    private static void runBuildOrder(Board board, boolean wantsExpansion, boolean wantsMacro) {
        if (wantsExpansion && board.mayQueueExpansion()) {
            board.enqueue(board.expansion);
            return;
        }

        if (wantsMacro && board.mayQueueMacroHatchery()) {
            board.enqueue(board.macro);
        }
    }

    private static void runExcessSweep(Board board) {
        if (board.excess()) {
            board.cancelEverything(board.macro);
            board.cancelEverything(board.expansion);
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

        runFrames(board, true, false, FRAMES_PER_100_SECONDS);

        assertEquals(1, board.totalEnqueues());
        assertEquals(1, board.expansion.outstanding());
        assertEquals(0, board.lifetimes.size());
    }

    /**
     * The outstanding count is per kind, so a build order holding both requests true commits to
     * one of each rather than one per frame. The shared cooldown is what keeps them apart.
     */
    @Test
    void bothRequestsHeldTrueCommitOncePerKind() {
        Board board = new Board();
        board.completedHatcheries = SATURATED_HATCHERIES;
        board.larva = 0;
        board.minerals = 2000;

        runFrames(board, true, true, FRAMES_PER_100_SECONDS);

        assertEquals(2, board.totalEnqueues());
        assertEquals(1, board.expansion.outstanding());
        assertEquals(1, board.macro.outstanding());
        assertTrue(board.shortestGapWithoutACompletion() >= HatcheryCapacity.ENQUEUE_COOLDOWN_FRAMES);
    }

    /**
     * Game L4KVD0CN. One hatchery, 706 minerals against a 700 bar and no larva to spend, held
     * while the expansion it asked for was built. It queued three hatcheries in three frames;
     * the outstanding count now holds the request to one.
     */
    @Test
    void theFloatingRequestThatQueuedThreeHatcheriesInThreeFramesQueuesOne() {
        Board board = new Board();
        board.completedHatcheries = 1;
        board.larva = 0;
        board.minerals = 706;

        runFloatingFrames(board, A_LONG_HOLD);

        assertEquals(1, board.totalEnqueues());
        assertEquals(1, board.expansion.outstanding());
    }

    /**
     * The hatchery the request asked for completes, which raises the floating bar past the
     * mineral pile and ends the request.
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
            board.cancelEverything(board.expansion);
        }

        assertTrue(board.totalEnqueues() > 1);
        assertTrue(board.shortestGapWithoutACompletion() >= HatcheryCapacity.ENQUEUE_COOLDOWN_FRAMES);
    }

    /**
     * A rush reaction reaches queued plans only, so an expansion a drone is already walking to
     * survives it. The macro hatchery the reaction asks for must not wait on that expansion;
     * only the shared cooldown may delay it.
     */
    @Test
    void aSurvivingExpansionDoesNotStarveTheMacroHatcheryARushAsksFor() {
        Board board = new Board();
        board.completedHatcheries = 1;
        board.larva = 0;
        board.minerals = 1200;

        runFrames(board, true, false, 1);
        assertEquals(1, board.expansion.outstanding());

        board.earlyRushed = true;
        runFrames(board, false, true, FRAMES_PER_100_SECONDS);

        assertEquals(1, board.macro.outstanding());
        assertEquals(1, board.expansion.outstanding());
        assertTrue(board.enqueueFrames.get(1) - board.enqueueFrames.get(0)
                <= HatcheryCapacity.ENQUEUE_COOLDOWN_FRAMES);
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

        assertEquals(0, board.expansion.outstanding());
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

        assertEquals(1, board.macro.outstanding());
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
        assertEquals(1, board.expansion.outstanding());

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
