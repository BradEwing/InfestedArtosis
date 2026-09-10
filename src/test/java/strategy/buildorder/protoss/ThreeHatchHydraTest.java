package strategy.buildorder.protoss;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreeHatchHydraTest {

    @Test
    void withholdsSpeedWhileTheZerglingsAreStillPlanned() {
        assertFalse(ThreeHatchHydra.shouldPlanMetabolicBoost(true, 0));
    }

    @Test
    void withholdsSpeedOnTheZerglingCountAlone() {
        assertFalse(ThreeHatchHydra.shouldPlanMetabolicBoost(true, 12));
    }

    @Test
    void takesSpeedOnceTheZerglingsAreFielded() {
        assertTrue(ThreeHatchHydra.shouldPlanMetabolicBoost(true, 13));
    }

    @Test
    void withholdsSpeedWhileTheUpgradeIsUnavailable() {
        assertFalse(ThreeHatchHydra.shouldPlanMetabolicBoost(false, 20));
    }
}
