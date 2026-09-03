package info;

import bwapi.UnitType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceCountTest {

    private ResourceCount resourceCount() {
        return new ResourceCount(null);
    }

    @Test
    void aReservationWithoutALarvaBlocksTheLastLarva() {
        ResourceCount resourceCount = resourceCount();
        resourceCount.reserveUnit(UnitType.Zerg_Hydralisk);

        assertFalse(resourceCount.canScheduleLarva(1, 0));
        assertTrue(resourceCount.canScheduleLarva(2, 0));
    }

    @Test
    void aLarvaHandedToAPlanStopsCountingAgainstThePlansBehindIt() {
        ResourceCount resourceCount = resourceCount();
        resourceCount.reserveUnit(UnitType.Zerg_Hydralisk);

        assertTrue(resourceCount.canScheduleLarva(1, 1));
    }

    @Test
    void fourStuckPlansStillLeaveTheFreeLarvaSchedulable() {
        ResourceCount resourceCount = resourceCount();
        for (int i = 0; i < 4; i++) {
            resourceCount.reserveUnit(UnitType.Zerg_Hydralisk);
        }

        assertTrue(resourceCount.canScheduleLarva(1, 4));
        assertFalse(resourceCount.canScheduleLarva(0, 4));
    }

    @Test
    void morphsFromAnExistingUnitDoNotReserveALarva() {
        ResourceCount resourceCount = resourceCount();
        resourceCount.reserveUnit(UnitType.Zerg_Lurker);
        resourceCount.reserveUnit(UnitType.Zerg_Guardian);
        resourceCount.reserveUnit(UnitType.Zerg_Devourer);

        assertTrue(resourceCount.canScheduleLarva(1, 0));
    }

    @Test
    void unreservingAMorphFromAnExistingUnitDoesNotFreeALarva() {
        ResourceCount resourceCount = resourceCount();
        resourceCount.reserveUnit(UnitType.Zerg_Hydralisk);
        resourceCount.unreserveUnit(UnitType.Zerg_Guardian);

        assertFalse(resourceCount.canScheduleLarva(1, 0));
    }
}
