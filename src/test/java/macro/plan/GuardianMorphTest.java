package macro.plan;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
    void theMutaliskNearestAHeldBaseMorphsFirst() {
        List<Integer> distancesFromHome = Arrays.asList(900, 120, 400);

        assertEquals(120, PlanManager.nearest(distancesFromHome, Integer::doubleValue));
    }

    @Test
    void theFirstMutaliskMorphsOnATieAndNoneWhenNoneIsFree() {
        List<String> tied = Arrays.asList("first", "second");

        assertEquals("first", PlanManager.nearest(tied, candidate -> 10));
        assertNull(PlanManager.nearest(Collections.<String>emptyList(), candidate -> 10));
    }

    @Test
    void aSpireBuiltByADroneIsNotABuildingMorph() {
        assertFalse(PlanManager.isBuildingMorph(UnitType.Zerg_Spire));
    }
}
