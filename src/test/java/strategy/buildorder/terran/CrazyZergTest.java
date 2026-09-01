package strategy.buildorder.terran;

import info.TechProgression;
import macro.AdvancedUnitEligibility;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrazyZergTest {

    private static final int GATHERER_FLOOR = AdvancedUnitEligibility.MIN_GATHERERS;

    private static TechProgression withSpire() {
        TechProgression techProgression = new TechProgression();
        techProgression.setSpire(true);
        return techProgression;
    }

    @Test
    void derivesTheMutaliskWithASpireAndTheGathererFloor() {
        assertTrue(CrazyZerg.shouldPlanMutalisk(withSpire(), true, GATHERER_FLOOR));
    }

    @Test
    void withholdsTheMutaliskWithNoGatherers() {
        assertFalse(CrazyZerg.shouldPlanMutalisk(withSpire(), true, 0));
    }

    @Test
    void withholdsTheMutaliskBelowTheGathererFloor() {
        assertFalse(CrazyZerg.shouldPlanMutalisk(withSpire(), true, GATHERER_FLOOR - 1));
    }

    @Test
    void withholdsTheMutaliskWithoutASpire() {
        assertFalse(CrazyZerg.shouldPlanMutalisk(new TechProgression(), true, GATHERER_FLOOR));
    }

    @Test
    void withholdsTheMutaliskOnceTheCapIsReached() {
        assertFalse(CrazyZerg.shouldPlanMutalisk(withSpire(), false, GATHERER_FLOOR));
    }

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
