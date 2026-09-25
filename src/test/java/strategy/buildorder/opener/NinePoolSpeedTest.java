package strategy.buildorder.opener;

import bwapi.Race;
import info.GameState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NinePoolSpeedTest {

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
        assertFalse(NinePoolSpeed.openingDone(NinePoolSpeed.OPENING_ZERGLINGS - 2, true));
        assertFalse(NinePoolSpeed.openingDone(NinePoolSpeed.OPENING_ZERGLINGS, false));
        assertTrue(NinePoolSpeed.openingDone(NinePoolSpeed.OPENING_ZERGLINGS, true));
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
}
