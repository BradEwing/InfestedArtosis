package strategy.buildorder.terran;

import info.TechProgression;
import macro.AdvancedUnitEligibility;
import org.junit.jupiter.api.Test;
import strategy.buildorder.GasBoundHiveTech;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrazyZergTest {

    private static final int GATHERER_FLOOR = AdvancedUnitEligibility.MIN_GATHERERS;

    private static final boolean TECH_AVAILABLE = true;

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

    /**
     * Game LMR9R0MB from frame 14823 on: the Lair finished at 7623, two Extractors were ever
     * taken and one was alive, and the unreserved bank sat at or above 500 gas for the remaining
     * 5,236 frames. The build asked for no Queen's Nest in any of them.
     */
    @Test
    void asksForTheQueensNestOnATwoGeyserBankThatHeldTheBar() {
        assertEquals(GasBoundHiveTech.Gate.TRIGGER,
                GasBoundHiveTech.evaluate(TECH_AVAILABLE, 500, GasBoundHiveTech.SUSTAINED_FRAMES));
    }

    @Test
    void asksForTheQueensNestAtTheBar() {
        assertTrue(GasBoundHiveTech.shouldPlan(TECH_AVAILABLE, GasBoundHiveTech.BRANCH_GAS,
                GasBoundHiveTech.SUSTAINED_FRAMES));
    }

    @Test
    void withholdsTheQueensNestOnABankBelowTheBar() {
        assertEquals(GasBoundHiveTech.Gate.GAS_SHORT,
                GasBoundHiveTech.evaluate(TECH_AVAILABLE, GasBoundHiveTech.BRANCH_GAS - 1,
                        GasBoundHiveTech.SUSTAINED_FRAMES));
    }

    @Test
    void withholdsTheQueensNestOnABankThatOnlyTouchedTheBar() {
        assertEquals(GasBoundHiveTech.Gate.GAS_SHORT,
                GasBoundHiveTech.evaluate(TECH_AVAILABLE, 1677, GasBoundHiveTech.SUSTAINED_FRAMES - 1));
    }

    @Test
    void withholdsTheQueensNestWhileTheLairIsUnfinished() {
        assertEquals(GasBoundHiveTech.Gate.TECH_UNAVAILABLE,
                GasBoundHiveTech.evaluate(false, 1677, GasBoundHiveTech.SUSTAINED_FRAMES));
    }

    /**
     * An unavailable structure is not a withheld one, so the gate writes no telemetry row for it.
     */
    @Test
    void theUnavailableGateIsNotARequest() {
        assertFalse(GasBoundHiveTech.Gate.TECH_UNAVAILABLE.isRequest());
        assertTrue(GasBoundHiveTech.Gate.GAS_SHORT.isRequest());
        assertTrue(GasBoundHiveTech.Gate.TRIGGER.isRequest());
    }
}
