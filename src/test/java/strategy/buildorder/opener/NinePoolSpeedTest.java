package strategy.buildorder.opener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NinePoolSpeedTest {

    private static final int NO_USABLE_POOL = 0;

    private static final int ONE_USABLE_POOL = 1;

    private static final int NO_ZERGLINGS = 0;

    private static final int ZERGLINGS_NEEDED = 6;

    private static final int NINE_DRONES = 9;

    private static final int NO_STANDING_POOL = 0;

    private static final int ONE_STANDING_POOL = 1;

    /**
     * LUZ9502W frame 6: four living drones and four planned. The first Overlord was queued here
     * and morphed at 7 supply, ahead of the 8th and 9th drones and the pool.
     */
    @Test
    void holdsTheOverlordBelowNineDrones() {
        assertTrue(NinePoolSpeed.holdsOverlords(NINE_DRONES - 1, NO_STANDING_POOL));
    }

    @Test
    void holdsTheOverlordAtNineDronesUntilThePoolStands() {
        assertTrue(NinePoolSpeed.holdsOverlords(NINE_DRONES, NO_STANDING_POOL));
    }

    @Test
    void releasesTheOverlordOnceThePoolIsUnderConstruction() {
        assertFalse(NinePoolSpeed.holdsOverlords(NINE_DRONES, ONE_STANDING_POOL));
    }

    /** The pool's drone is gone, and its replacement has not been queued yet. */
    @Test
    void holdsTheOverlordUntilThePoolDroneIsReplaced() {
        assertTrue(NinePoolSpeed.holdsOverlords(NINE_DRONES - 1, ONE_STANDING_POOL));
    }

    /**
     * The opener must still be active, and so still holding, while the pool is only planned. A
     * terminal build order taking over then would get the first Overlord at priority 1, ahead of
     * the pool at 9 supply.
     */
    @Test
    void staysActiveWhileThePoolIsOnlyPlanned() {
        assertFalse(NinePoolSpeed.openerComplete(NO_STANDING_POOL));
        assertTrue(NinePoolSpeed.holdsOverlords(NINE_DRONES, NO_STANDING_POOL));
    }

    @Test
    void withholdsGasUntilTheSpawningPoolIsPlanned() {
        assertFalse(NinePoolSpeed.shouldPlanExtractor(0, false, true));
    }

    @Test
    void takesGasOnceTheSpawningPoolIsPlanned() {
        assertTrue(NinePoolSpeed.shouldPlanExtractor(0, true, true));
    }

    @Test
    void withholdsGasBeforeTheGasTime() {
        assertFalse(NinePoolSpeed.shouldPlanExtractor(0, true, false));
    }

    @Test
    void withholdsGasOnceAnExtractorExists() {
        assertFalse(NinePoolSpeed.shouldPlanExtractor(1, true, true));
    }

    @Test
    void handsOffOnASpawningPoolUnderConstruction() {
        assertTrue(NinePoolSpeed.openerComplete(1));
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
