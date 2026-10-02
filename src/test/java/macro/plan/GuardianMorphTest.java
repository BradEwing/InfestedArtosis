package macro.plan;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuardianMorphTest {

    @Test
    void aGuardianMorphsFromAMutaliskAndAGreaterSpireFromASpireBuildingMorph() {
        assertEquals(UnitType.Zerg_Mutalisk, PlanManager.morphProducer(UnitType.Zerg_Guardian));
        assertTrue(PlanManager.isBuildingMorph(UnitType.Zerg_Greater_Spire));
    }

    @Test
    void aLurkerStillMorphsFromAHydralisk() {
        assertEquals(UnitType.Zerg_Hydralisk, PlanManager.morphProducer(UnitType.Zerg_Lurker));
    }

    @Test
    void everyOtherUnitIsMadeFromLarva() {
        assertNull(PlanManager.morphProducer(UnitType.Zerg_Mutalisk));
        assertNull(PlanManager.morphProducer(UnitType.Zerg_Zergling));
        assertNull(PlanManager.morphProducer(UnitType.Zerg_Defiler));
    }

    @Test
    void aSpireBuiltByADroneIsNotABuildingMorph() {
        assertFalse(PlanManager.isBuildingMorph(UnitType.Zerg_Spire));
    }
}
