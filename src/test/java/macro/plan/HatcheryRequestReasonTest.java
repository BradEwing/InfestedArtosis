package macro.plan;

import bwapi.TilePosition;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import strategy.buildorder.BuildOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HatcheryRequestReasonTest {

    @Test
    void floatingMineralsAloneIsTheFloatingReason() {
        assertEquals(HatcheryRequestReason.FLOATING_MINERALS, HatcheryRequestReason.forExpansion(true, false));
    }

    @Test
    void baseParityAloneIsTheBehindReason() {
        assertEquals(HatcheryRequestReason.BEHIND_ON_BASES, HatcheryRequestReason.forExpansion(false, true));
    }

    @Test
    void bothRulesAreRecordedTogether() {
        assertEquals(HatcheryRequestReason.FLOATING_AND_BEHIND, HatcheryRequestReason.forExpansion(true, true));
    }

    @Test
    void neitherRuleIsTheBuildOrdersOwnRequest() {
        assertEquals(HatcheryRequestReason.BUILD_ORDER, HatcheryRequestReason.forExpansion(false, false));
    }

    @Test
    void everyMacroHatcheryPlanCarriesTheMacroReason() {
        Plan plan = BuildOrder.macroHatcheryPlan(1, new TilePosition(10, 10));

        assertEquals(HatcheryRequestReason.MACRO, plan.getHatcheryRequestReason());
    }

    @Test
    void theReleaseReasonIsDistinctFromTheMacroReason() {
        assertNotEquals(HatcheryRequestReason.MACRO, HatcheryRequestReason.RELEASE);
    }

    @Test
    void otherPlansCarryNoReason() {
        assertNull(new BuildingPlan(UnitType.Zerg_Spire, 1).getHatcheryRequestReason());
    }
}
