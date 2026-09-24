package strategy.buildorder.opener;

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
}
