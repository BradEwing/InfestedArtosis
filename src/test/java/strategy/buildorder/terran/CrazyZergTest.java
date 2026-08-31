package strategy.buildorder.terran;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrazyZergTest {

    @Test
    void derivesTheOverlordWhileSupplyIsTight() {
        assertTrue(CrazyZerg.shouldPlanOverlord(1, 3, false));
    }

    @Test
    void withholdsTheOverlordWhileSupplyIsExcess() {
        assertFalse(CrazyZerg.shouldPlanOverlord(1, 3, true));
    }

    @Test
    void withholdsTheOverlordOnceTheCountIsMet() {
        assertFalse(CrazyZerg.shouldPlanOverlord(1, 4, false));
    }

    @Test
    void withholdsTheOverlordWithoutASpire() {
        assertFalse(CrazyZerg.shouldPlanOverlord(0, 3, false));
    }
}
