package strategy.buildorder.opener;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NinePoolSpeedTest {

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
}
