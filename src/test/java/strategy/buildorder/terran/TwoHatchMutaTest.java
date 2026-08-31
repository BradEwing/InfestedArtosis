package strategy.buildorder.terran;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwoHatchMutaTest {

    @Test
    void derivesTheOverlordWhileSupplyIsTight() {
        assertTrue(TwoHatchMuta.shouldPlanOverlord(2, 3, false));
    }

    @Test
    void withholdsTheOverlordWhileSupplyIsExcess() {
        assertFalse(TwoHatchMuta.shouldPlanOverlord(2, 3, true));
    }

    @Test
    void withholdsTheOverlordOnceTheCountIsMet() {
        assertFalse(TwoHatchMuta.shouldPlanOverlord(2, 4, false));
    }

    @Test
    void withholdsTheOverlordBelowTwoSpires() {
        assertFalse(TwoHatchMuta.shouldPlanOverlord(1, 3, false));
    }
}
