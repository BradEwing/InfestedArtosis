package strategy.buildorder.opener;

import bwapi.UnitType;
import info.TechProgression;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.PlanComparator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwelveHatchTest {

    private static final int HATCHERY_SUPPLY = 24;

    private static final int DRONE_TARGET = 12;

    private static final int ONE_HATCHERY = 1;

    private static final int TWO_HATCHERIES = 2;

    private static final int HATCHERY_FRAME = 2067;

    private static TechProgression withPlannedPool() {
        TechProgression techProgression = new TechProgression();
        techProgression.setPlannedSpawningPool(true);
        return techProgression;
    }

    /**
     * IA-345 criterion 1: the pool is requested on the frame the opener reaches its hatchery step,
     * alongside the expansion rather than behind a transition.
     */
    @Test
    void queuesThePoolOnTheFrameItReachesItsHatcheryStep() {
        assertTrue(TwelveHatch.shouldPlanHatchery(HATCHERY_SUPPLY, ONE_HATCHERY));
        assertTrue(TwelveHatch.shouldPlanPool(HATCHERY_SUPPLY, true));
    }

    @Test
    void keepsQueueingThePoolOnceTheExpansionIsQueued() {
        assertFalse(TwelveHatch.shouldPlanHatchery(HATCHERY_SUPPLY, TWO_HATCHERIES));
        assertTrue(TwelveHatch.shouldPlanPool(HATCHERY_SUPPLY, true));
    }

    /**
     * IA-345 criterion 2: with the expansion suppressed the hatchery count never reaches two, so
     * the opener can never transition. The pool gate is open in exactly that state, so the frame
     * produces a Spawning Pool plan rather than an empty list.
     */
    @Test
    void queuesThePoolWhileTheExpansionIsSuppressed() {
        assertFalse(TwelveHatch.shouldTransition(ONE_HATCHERY, DRONE_TARGET));
        assertTrue(TwelveHatch.shouldPlanPool(HATCHERY_SUPPLY, true));
    }

    @Test
    void waitsForTheHatcherySupplyBeforeQueueingThePool() {
        assertFalse(TwelveHatch.shouldPlanPool(HATCHERY_SUPPLY - 1, true));
    }

    /**
     * IA-345 criterion 4: the pool gate reads the same authority every transition target reads,
     * so whichever of them commits to the pool first, the other declines to duplicate it.
     */
    @Test
    void leavesNoPoolForATransitionTargetToDuplicate() {
        TechProgression techProgression = new TechProgression();

        assertTrue(TwelveHatch.shouldPlanPool(HATCHERY_SUPPLY, techProgression.canPlanPool()));

        techProgression.setPlannedSpawningPool(true);
        assertFalse(techProgression.canPlanPool());
        assertFalse(TwelveHatch.shouldPlanPool(HATCHERY_SUPPLY, withPlannedPool().canPlanPool()));
    }

    @Test
    void transitionsOnceTheExpansionIsQueuedAndTheDroneTargetIsMet() {
        assertTrue(TwelveHatch.shouldTransition(TWO_HATCHERIES, DRONE_TARGET));
        assertFalse(TwelveHatch.shouldTransition(TWO_HATCHERIES, DRONE_TARGET - 1));
    }

    /**
     * Both plans are created on the same frame, and the queue is a priority queue that breaks no
     * ties, so the pool carries the frame after the hatchery's and the expansion is served first.
     */
    @Test
    void sortsThePoolBehindTheHatcheryQueuedOnTheSameFrame() {
        TwelveHatch opener = new TwelveHatch();
        Plan hatchery = new BuildingPlan(UnitType.Zerg_Hatchery, HATCHERY_FRAME);
        Plan pool = new BuildingPlan(UnitType.Zerg_Spawning_Pool, opener.poolPriority(HATCHERY_FRAME));

        assertTrue(new PlanComparator().compare(hatchery, pool) < 0);
    }
}
