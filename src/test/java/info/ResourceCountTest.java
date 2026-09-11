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

    /**
     * IA-338: the rule is a bank imbalance and nothing else. It reads unreserved totals, so a
     * reservation the gas bank cannot yet cover pushes available gas negative and widens the gap
     * rather than closing it.
     */
    @Test
    void mineralsLeadingGasByMoreThanTheBarIsAnImbalance() {
        assertFalse(ResourceCount.mineralsOutpaceGas(100, 0));
        assertTrue(ResourceCount.mineralsOutpaceGas(101, 0));
        assertFalse(ResourceCount.mineralsOutpaceGas(300, 200));
        assertTrue(ResourceCount.mineralsOutpaceGas(43, -68));
    }

    @Test
    void gasLeadingMineralsIsNotAnImbalance() {
        assertFalse(ResourceCount.mineralsOutpaceGas(0, 0));
        assertFalse(ResourceCount.mineralsOutpaceGas(0, 500));
    }

    @Test
    void unreservingAMorphFromAnExistingUnitDoesNotFreeALarva() {
        ResourceCount resourceCount = resourceCount();
        resourceCount.reserveUnit(UnitType.Zerg_Hydralisk);
        resourceCount.unreserveUnit(UnitType.Zerg_Guardian);

        assertFalse(resourceCount.canScheduleLarva(1, 0));
    }
}
