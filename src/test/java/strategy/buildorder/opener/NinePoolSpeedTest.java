package strategy.buildorder.opener;

import bwapi.Race;
import bwapi.UnitType;
import info.GameState;
import macro.plan.Plan;
import macro.plan.PlanState;
import macro.plan.UnitPlan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NinePoolSpeedTest {

    private static final int NO_UNSTARTED_PLANS = 0;

    private static final int ONE_UNSTARTED_PLAN = 1;

    private static final int ZERGLING_PRIORITY = 5;

    private static final int NO_USABLE_POOL = 0;

    private static final int ONE_USABLE_POOL = 1;

    private static final int NO_ZERGLINGS = 0;

    private static final int ZERGLINGS_NEEDED = 6;

    private static final int NO_STANDING_POOL = 0;

    private static final int ONE_STANDING_POOL = 1;

    private static final int NO_EXTRACTOR = 0;

    private static final int ONE_EXTRACTOR = 1;

    private static final int EIGHT_SUPPLY = 16;

    private static final int NINE_SUPPLY = 18;

    private static final int ONE_BASE = 1;

    private static final int ONE_HATCHERY = 1;

    private static final int NO_GEYSERS = 0;

    private static final int NO_PLANNED_DRONES = 0;

    private static final int EIGHT_DRONES = 8;

    private static final int NINE_DRONES = 9;

    @Test
    void holdsTheOverlordUntilThePoolStands() {
        assertTrue(NinePoolSpeed.holdsOverlords(NO_STANDING_POOL, NO_EXTRACTOR, NO_EXTRACTOR, true));
        assertTrue(NinePoolSpeed.holdsOverlords(NO_STANDING_POOL, NO_EXTRACTOR, NO_EXTRACTOR, false));
    }

    @Test
    void holdsTheOverlordWhileThePoolStandsButTheExtractorDoesNot() {
        assertTrue(NinePoolSpeed.holdsOverlords(ONE_STANDING_POOL, NO_EXTRACTOR, NO_EXTRACTOR, true));
    }

    @Test
    void holdsTheOverlordWhileTheExtractorIsOnlyReserved() {
        assertTrue(NinePoolSpeed.holdsOverlords(ONE_STANDING_POOL, NO_EXTRACTOR, ONE_EXTRACTOR, false));
    }

    @Test
    void releasesTheOverlordOnceTheExtractorIsUnderConstruction() {
        assertFalse(NinePoolSpeed.holdsOverlords(ONE_STANDING_POOL, ONE_EXTRACTOR, ONE_EXTRACTOR, false));
    }

    /**
     * The SCV rush reaction cancels the Extractor and blocks new ones, and a stolen geyser leaves
     * none to reserve. Waiting on an Extractor that cannot come would stop all Overlord planning.
     */
    @Test
    void releasesTheOverlordOnceThePoolStandsAndNoExtractorCanCome() {
        assertFalse(NinePoolSpeed.holdsOverlords(ONE_STANDING_POOL, NO_EXTRACTOR, NO_EXTRACTOR, false));
    }

    @Test
    void takesGasAtNineSupplyOnceThePoolStands() {
        assertTrue(NinePoolSpeed.shouldPlanExtractor(0, ONE_STANDING_POOL, NINE_SUPPLY, true));
    }

    /** The pool's drone has not been replaced yet, so the Extractor waits for the 9th drone. */
    @Test
    void withholdsGasUntilThePoolDroneIsReplaced() {
        assertFalse(NinePoolSpeed.shouldPlanExtractor(0, ONE_STANDING_POOL, EIGHT_SUPPLY, true));
    }

    /**
     * The pool is queued but its drone has not morphed, so supply still reads 9. The Extractor
     * must wait for the pool to stand and its drone to be replaced.
     */
    @Test
    void withholdsGasWhileThePoolIsQueuedButNotStanding() {
        assertFalse(NinePoolSpeed.shouldPlanExtractor(0, NO_STANDING_POOL, NINE_SUPPLY, true));
    }

    @Test
    void queuesSixOpeningZerglingsOnceThePoolFinishes() {
        assertFalse(NinePoolSpeed.shouldPlanOpeningZergling(NO_USABLE_POOL, NO_ZERGLINGS));
        assertTrue(NinePoolSpeed.shouldPlanOpeningZergling(ONE_USABLE_POOL, NO_ZERGLINGS));
        assertTrue(NinePoolSpeed.shouldPlanOpeningZergling(ONE_USABLE_POOL, NinePoolSpeed.OPENING_ZERGLINGS - 2));
        assertFalse(NinePoolSpeed.shouldPlanOpeningZergling(ONE_USABLE_POOL, NinePoolSpeed.OPENING_ZERGLINGS));
    }

    @Test
    void handsOverOnlyAfterSixZerglingsAndSpeed() {
        assertFalse(NinePoolSpeed.openingDone(NinePoolSpeed.OPENING_ZERGLINGS - 2, NO_UNSTARTED_PLANS, true, false));
        assertFalse(NinePoolSpeed.openingDone(NinePoolSpeed.OPENING_ZERGLINGS, NO_UNSTARTED_PLANS, false, false));
        assertTrue(NinePoolSpeed.openingDone(NinePoolSpeed.OPENING_ZERGLINGS, NO_UNSTARTED_PLANS, true, false));
    }

    /**
     * The SCV rush reaction cancels Extractors and blocks new ones until 12 zerglings live, and a
     * stolen geyser leaves none to take. Metabolic Boost cannot come, so six zerglings hand over.
     */
    @Test
    void handsOverAfterSixZerglingsWhenNoExtractorCanCome() {
        assertTrue(NinePoolSpeed.openingDone(NinePoolSpeed.OPENING_ZERGLINGS, NO_UNSTARTED_PLANS, false, true));
        assertFalse(NinePoolSpeed.openingDone(NinePoolSpeed.OPENING_ZERGLINGS - 2, NO_UNSTARTED_PLANS, false, true));
    }

    /**
     * The pool finishes at 9/9 before the first Overlord does, so an opening zergling plan waits
     * on supply. Handing over then lets the terminal build order's natural hatchery take the
     * build-ahead slot ahead of it.
     */
    @Test
    void holdsTheHandOffWhileAnOpeningZerglingIsUnstarted() {
        assertFalse(NinePoolSpeed.openingDone(NinePoolSpeed.OPENING_ZERGLINGS, ONE_UNSTARTED_PLAN, true, false));
        assertFalse(NinePoolSpeed.openingDone(NinePoolSpeed.OPENING_ZERGLINGS, ONE_UNSTARTED_PLAN, false, true));
    }

    @Test
    void holdsTheHandOffWhileAnOpeningZerglingIsQueuedOrScheduled() {
        List<Plan> zerglings = openingZerglings(PlanState.MORPHING, PlanState.MORPHING, PlanState.PLANNED);
        assertEquals(1, NinePoolSpeed.unstartedPlans(zerglings, plan -> true));

        zerglings = openingZerglings(PlanState.MORPHING, PlanState.SCHEDULE, PlanState.PLANNED);
        assertEquals(2, NinePoolSpeed.unstartedPlans(zerglings, plan -> true));
    }

    @Test
    void handsOverOnceEveryOpeningZerglingHasItsLarvaOrIsAnEgg() {
        List<Plan> zerglings = openingZerglings(PlanState.BUILDING, PlanState.MORPHING, PlanState.COMPLETE);
        int unstarted = NinePoolSpeed.unstartedPlans(zerglings, plan -> true);

        assertEquals(NO_UNSTARTED_PLANS, unstarted);
        assertTrue(NinePoolSpeed.openingDone(NinePoolSpeed.OPENING_ZERGLINGS, unstarted, true, false));
    }

    /**
     * A cancelled plan, or one dropped from the queue and the scheduled set without a state change,
     * will never start. Waiting on it would keep the opener from handing over.
     */
    @Test
    void doesNotWaitOnOpeningZerglingsThatWillNeverStart() {
        List<Plan> zerglings = openingZerglings(PlanState.CANCELLED, PlanState.PLANNED, PlanState.MORPHING);
        Plan dropped = zerglings.get(1);

        assertEquals(NO_UNSTARTED_PLANS, NinePoolSpeed.unstartedPlans(zerglings, plan -> plan != dropped));
    }

    @Test
    void theExtractorIsDeniedOnlyWhenNoneStandsNoneIsReservedAndNoneCanBePlanned() {
        assertTrue(NinePoolSpeed.extractorDenied(NO_EXTRACTOR, NO_EXTRACTOR, false));
        assertFalse(NinePoolSpeed.extractorDenied(NO_EXTRACTOR, NO_EXTRACTOR, true));
        assertFalse(NinePoolSpeed.extractorDenied(NO_EXTRACTOR, ONE_EXTRACTOR, false));
        assertFalse(NinePoolSpeed.extractorDenied(ONE_EXTRACTOR, ONE_EXTRACTOR, false));
    }

    /**
     * Against an unknown race the opener never hands over. Drones lost after the hold released
     * must not re-arm it, or no Overlord would be planned again.
     */
    @Test
    void theHoldStaysReleasedOnceItReleases() {
        NinePoolSpeed opener = new NinePoolSpeed();

        assertTrue(opener.latchOverlordHold(true));
        assertFalse(opener.latchOverlordHold(false));
        assertFalse(opener.latchOverlordHold(true));
    }

    @Test
    void withholdsGasUntilTheSpawningPoolIsPlanned() {
        assertFalse(NinePoolSpeed.shouldPlanExtractor(0, NO_STANDING_POOL, NINE_SUPPLY, false));
        assertFalse(NinePoolSpeed.shouldPlanExtractor(0, NO_STANDING_POOL, NINE_SUPPLY, true));
    }

    @Test
    void withholdsGasOnceAnExtractorExists() {
        assertFalse(NinePoolSpeed.shouldPlanExtractor(1, ONE_STANDING_POOL, NINE_SUPPLY, true));
    }

    /**
     * The army branch waits on a finished pool. A pool that is only committed to cannot morph a
     * zergling, and a plan queued against one holds a larva and reserves its minerals for the rest
     * of the pool build.
     */
    @Test
    void withholdsZerglingsUntilTheSpawningPoolStands() {
        assertFalse(NinePoolSpeed.shouldPlanZergling(NO_USABLE_POOL, NO_ZERGLINGS, ZERGLINGS_NEEDED));
    }

    @Test
    void takesZerglingsOnceTheSpawningPoolStands() {
        assertTrue(NinePoolSpeed.shouldPlanZergling(ONE_USABLE_POOL, NO_ZERGLINGS, ZERGLINGS_NEEDED));
    }

    @Test
    void withholdsZerglingsOnceTheTargetIsPassed() {
        assertFalse(NinePoolSpeed.shouldPlanZergling(ONE_USABLE_POOL, ZERGLINGS_NEEDED + 1, ZERGLINGS_NEEDED));
    }

    /**
     * Against Zerg the shared expected-worker ceiling is 7 at one base with no gas, so the 8
     * gatherers left after the pool's drone morphs would veto its replacement and leave the
     * Extractor waiting at 8 supply for the first zergling.
     */
    @Test
    void plansThePoolsReplacementDroneAgainstZerg() {
        int expectedWorkers = GameState.expectedWorkers(Race.Zerg, ONE_BASE, NO_GEYSERS);

        assertFalse(GameState.canPlanDrone(NO_PLANNED_DRONES, ONE_HATCHERY, EIGHT_DRONES, expectedWorkers));
        assertTrue(NinePoolSpeed.shouldPlanDrone(EIGHT_DRONES,
                GameState.canPlanOpeningDrone(NO_PLANNED_DRONES, ONE_HATCHERY)));
        assertFalse(NinePoolSpeed.shouldPlanExtractor(NO_EXTRACTOR, ONE_STANDING_POOL, EIGHT_SUPPLY, true));
    }

    @Test
    void takesGasAtNineSupplyOnceThePoolsDroneIsReplacedAgainstZerg() {
        assertFalse(NinePoolSpeed.shouldPlanDrone(NINE_DRONES,
                GameState.canPlanOpeningDrone(NO_PLANNED_DRONES, ONE_HATCHERY)));
        assertTrue(NinePoolSpeed.shouldPlanExtractor(NO_EXTRACTOR, ONE_STANDING_POOL, NINE_SUPPLY, true));
    }

    @Test
    void plansNoDroneBeyondNineCountingPlannedDrones() {
        assertTrue(NinePoolSpeed.shouldPlanDrone(NINE_DRONES - 1, true));
        assertFalse(NinePoolSpeed.shouldPlanDrone(NINE_DRONES, true));
        assertFalse(NinePoolSpeed.shouldPlanDrone(NINE_DRONES + 1, true));
    }

    @Test
    void plansNoDroneBeyondThePlannedWorkerLimit() {
        assertFalse(NinePoolSpeed.shouldPlanDrone(EIGHT_DRONES, GameState.canPlanOpeningDrone(3, ONE_HATCHERY)));
        assertTrue(NinePoolSpeed.shouldPlanDrone(EIGHT_DRONES, GameState.canPlanOpeningDrone(2, ONE_HATCHERY)));
    }

    private static List<Plan> openingZerglings(PlanState... states) {
        List<Plan> zerglings = new ArrayList<>();
        for (PlanState state : states) {
            Plan zergling = new UnitPlan(UnitType.Zerg_Zergling, ZERGLING_PRIORITY);
            zergling.setState(state);
            zerglings.add(zergling);
        }
        return zerglings;
    }
}
