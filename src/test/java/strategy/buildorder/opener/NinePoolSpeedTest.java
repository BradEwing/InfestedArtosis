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
}
