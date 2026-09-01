package macro;

import bwapi.UnitType;
import info.TechProgression;
import macro.plan.PlanBlocker;
import macro.plan.PlanCancelReason;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Replays the frame order ProductionManager runs for a unit a tech building unlocks: the build
 * order asks for the unit, then the impossible-plan sweep deletes what it cannot schedule.
 *
 * <p>The producer modelled is BuildOrder.planAdvancedUnit and the canceller is the sweep behind
 * canScheduleUnit. Both read AdvancedUnitEligibility, so every rule that deletes the plan has the
 * same term in the producer's guard. The four games that logged 845 mutalisk cancels held a
 * complete Spire with three or fewer gatherers for the whole window.
 */
class AdvancedUnitQueueContractTest {

    private static final int FRAMES_PER_100_SECONDS = 2400;

    private static final int FRAMES_PER_GAME = 36000;

    private static final int GATHERER_FLOOR = AdvancedUnitEligibility.MIN_GATHERERS;

    /**
     * One game's mutalisk bookkeeping. Holds the quantities the game moves, the waiting plan the
     * plan system moves, and the lifetime and reason of each cancelled plan.
     */
    private static final class Board {
        private final TechProgression techProgression = new TechProgression();
        private final List<Integer> enqueuedAt = new ArrayList<>();
        private final List<Integer> lifetimes = new ArrayList<>();
        private final List<PlanCancelReason> cancelReasons = new ArrayList<>();
        private final Set<PlanBlocker> withheldBy = new HashSet<>();
        private int frame;
        private int gatherers;

        private PlanBlocker blocker() {
            return AdvancedUnitEligibility.blocker(UnitType.Zerg_Mutalisk, techProgression, gatherers);
        }

        private int totalEnqueues() {
            return lifetimes.size() + enqueuedAt.size();
        }

        private int shortestLifetime() {
            int shortest = Integer.MAX_VALUE;
            for (int lifetime : lifetimes) {
                shortest = Math.min(shortest, lifetime);
            }
            return shortest;
        }
    }

    private static void runFrames(Board board, int frames) {
        for (int i = 0; i < frames; i++) {
            board.frame++;
            runBuildOrder(board);
            runSweep(board);
        }
    }

    private static void runBuildOrder(Board board) {
        if (!board.enqueuedAt.isEmpty()) {
            return;
        }
        PlanBlocker blocker = board.blocker();
        if (blocker != PlanBlocker.NONE) {
            board.withheldBy.add(blocker);
            return;
        }
        board.enqueuedAt.add(board.frame);
    }

    private static void runSweep(Board board) {
        PlanBlocker blocker = board.blocker();
        if (blocker == PlanBlocker.NONE) {
            return;
        }
        for (int enqueueFrame : board.enqueuedAt) {
            board.lifetimes.add(board.frame - enqueueFrame);
            board.cancelReasons.add(blocker.cancelReason());
        }
        board.enqueuedAt.clear();
    }

    private static Board spireComplete(int gatherers) {
        Board board = new Board();
        board.techProgression.setSpire(true);
        board.gatherers = gatherers;
        return board;
    }

    /**
     * The measured loop: the Spire is complete and the gatherers sit at or below the floor. The
     * old producer read only the Spire, so it enqueued, the sweep cancelled on the gatherer term
     * the same frame, and the next frame repeated it.
     */
    @Test
    void aCompleteSpireBelowTheGathererFloorCreatesNoPlan() {
        Board board = spireComplete(GATHERER_FLOOR - 1);

        runFrames(board, FRAMES_PER_100_SECONDS);

        assertEquals(0, board.totalEnqueues());
        assertEquals(0, board.cancelReasons.size());
        assertEquals(1, board.withheldBy.size());
        assertTrue(board.withheldBy.contains(PlanBlocker.INSUFFICIENT_GATHERERS));
    }

    @Test
    void thePlanIsCreatedOnTheFrameTheGatherersReturn() {
        Board board = spireComplete(GATHERER_FLOOR - 1);

        runFrames(board, 10);
        board.gatherers = GATHERER_FLOOR;
        runFrames(board, 1);

        assertEquals(1, board.enqueuedAt.size());
        assertEquals(11, board.enqueuedAt.get(0));
        assertEquals(0, board.lifetimes.size());
    }

    /**
     * The gatherers drop after the plan is queued. The sweep cancels it once, records the
     * gatherer term rather than a missing prerequisite, and the producer stays quiet while the
     * state holds.
     */
    @Test
    void aSweptPlanIsNotRecreatedWhileTheStateHolds() {
        Board board = spireComplete(GATHERER_FLOOR);

        runFrames(board, 10);
        assertEquals(1, board.enqueuedAt.size());

        board.gatherers = GATHERER_FLOOR - 1;
        runFrames(board, FRAMES_PER_100_SECONDS);

        assertEquals(1, board.totalEnqueues());
        assertEquals(1, board.lifetimes.size());
        assertTrue(board.shortestLifetime() > 0);
        assertEquals(PlanCancelReason.INSUFFICIENT_GATHERERS, board.cancelReasons.get(0));
    }

    @Test
    void aLostSpireSweepsThePlanAsMissingTech() {
        Board board = spireComplete(GATHERER_FLOOR);

        runFrames(board, 10);
        board.techProgression.setSpire(false);
        runFrames(board, FRAMES_PER_100_SECONDS);

        assertEquals(1, board.totalEnqueues());
        assertEquals(PlanCancelReason.TECH_MISSING, board.cancelReasons.get(0));
        assertTrue(board.withheldBy.contains(PlanBlocker.TECH_MISSING));
    }

    /**
     * A full game at 24 frames per second with the gatherer count crossing the floor every 100
     * frames. Each crossing below the floor costs one cancel and each crossing above costs one
     * enqueue, so the totals follow the state changes and not the frame count.
     */
    @Test
    void cancelsFollowStateChangesNotFrames() {
        Board board = spireComplete(GATHERER_FLOOR);
        int drops = 0;
        int rises = 0;

        runFrames(board, 100);
        for (int elapsed = 100; elapsed < FRAMES_PER_GAME; elapsed += 100) {
            if (board.gatherers >= GATHERER_FLOOR) {
                board.gatherers = GATHERER_FLOOR - 1;
                drops++;
            } else {
                board.gatherers = GATHERER_FLOOR;
                rises++;
            }
            runFrames(board, 100);
        }

        assertEquals(drops, board.lifetimes.size());
        assertEquals(rises + 1, board.totalEnqueues());
        assertTrue(board.shortestLifetime() > 0);
    }

    @Test
    void noSteadyStateGameProducesACancel() {
        assertEquals(0, cancelsFor(true, GATHERER_FLOOR - 1));
        assertEquals(0, cancelsFor(true, 0));
        assertEquals(0, cancelsFor(false, GATHERER_FLOOR));
        assertEquals(0, cancelsFor(true, GATHERER_FLOOR));
    }

    private static int cancelsFor(boolean spire, int gatherers) {
        Board board = new Board();
        board.techProgression.setSpire(spire);
        board.gatherers = gatherers;

        runFrames(board, FRAMES_PER_GAME);

        return board.lifetimes.size();
    }
}
