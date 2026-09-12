package strategy.buildorder.opener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NinePoolSpeedTest {

    private static final int NO_USABLE_POOL = 0;

    private static final int ONE_USABLE_POOL = 1;

    private static final int NO_ZERGLINGS = 0;

    private static final int ZERGLINGS_NEEDED = 6;

    @Test
    void derivesTheOverlordWhileSupplyIsTight() {
        assertTrue(NinePoolSpeed.shouldPlanOverlord(9, 1, false));
    }

    @Test
    void withholdsTheOverlordWhileSupplyIsExcess() {
        assertFalse(NinePoolSpeed.shouldPlanOverlord(9, 1, true));
    }

    @Test
    void withholdsTheOverlordOnceTheCountIsMet() {
        assertFalse(NinePoolSpeed.shouldPlanOverlord(9, 2, false));
    }

    @Test
    void withholdsTheOverlordBelowNineDrones() {
        assertFalse(NinePoolSpeed.shouldPlanOverlord(8, 1, false));
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

    @Test
    void holdsWhileNoSpawningPoolIsCommittedTo() {
        assertFalse(NinePoolSpeed.openerComplete(0));
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
