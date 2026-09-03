package macro;

import bwapi.UnitType;
import macro.plan.BuildingPlan;
import macro.plan.Plan;
import macro.plan.UnitPlan;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SupplyCapacityTest {

    private Plan unit(UnitType unitType) {
        return new UnitPlan(unitType, 1000);
    }

    @Test
    void withholdsOverlordsWhileFreeSupplyExceedsTheCancelThreshold() {
        assertTrue(SupplyCapacity.isExcess(60, 20));
    }

    @Test
    void derivesOverlordsAtTheCancelThreshold() {
        assertFalse(SupplyCapacity.isExcess(37, 20));
        assertTrue(SupplyCapacity.isExcess(38, 20));
    }

    @Test
    void neverWithholdsOverlordsWhileSupplyIsTight() {
        for (int freeSupply = 0; freeSupply <= 17; freeSupply++) {
            assertFalse(SupplyCapacity.isExcess(200 + freeSupply, 200),
                    "free supply of " + freeSupply + " must still derive overlords");
        }
    }

    @Test
    void neverWithholdsTheEmergencyOverlordWhileSupplyBlocked() {
        for (int supply = 0; supply <= 400; supply++) {
            assertFalse(SupplyCapacity.isExcess(supply, supply),
                    "supply block at " + supply + " must still reach the emergency overlord");
        }
    }

    @Test
    void aZerglingIsChargedForBothHalvesOfItsEgg() {
        assertEquals(2, SupplyCapacity.morphSupplyCost(UnitType.Zerg_Zergling));
        assertEquals(2, SupplyCapacity.morphSupplyCost(UnitType.Zerg_Scourge));
    }

    @Test
    void aSingleUnitPerEggIsChargedOnce() {
        assertEquals(2, SupplyCapacity.morphSupplyCost(UnitType.Zerg_Hydralisk));
        assertEquals(4, SupplyCapacity.morphSupplyCost(UnitType.Zerg_Mutalisk));
    }

    @Test
    void anOverlordCostsNoSupplyAndIsNeverTheCheapestWaitingUnit() {
        assertEquals(0, SupplyCapacity.morphSupplyCost(UnitType.Zerg_Overlord));
        assertEquals(
                SupplyCapacity.NO_QUEUED_UNIT,
                SupplyCapacity.cheapestUnitSupply(Collections.singletonList(unit(UnitType.Zerg_Overlord))));
    }

    @Test
    void buildingPlansDoNotCountTowardsTheCheapestWaitingUnit() {
        List<Plan> plans = Arrays.asList(
                new BuildingPlan(UnitType.Zerg_Hatchery, 1),
                unit(UnitType.Zerg_Mutalisk));

        assertEquals(4, SupplyCapacity.cheapestUnitSupply(plans));
    }

    @Test
    void anEmptyQueueIsNeverSupplyBlocked() {
        int cheapest = SupplyCapacity.cheapestUnitSupply(Collections.<Plan>emptyList());

        assertFalse(SupplyCapacity.isBlocked(54, 54, cheapest));
    }

    @Test
    void oneFreeRawSupplyBlocksAQueueOfTwoSupplyUnits() {
        List<Plan> plans = Arrays.asList(unit(UnitType.Zerg_Zergling), unit(UnitType.Zerg_Hydralisk));
        int cheapest = SupplyCapacity.cheapestUnitSupply(plans);

        assertEquals(2, cheapest);
        assertTrue(SupplyCapacity.isBlocked(54, 53, cheapest));
        assertFalse(SupplyCapacity.isBlocked(54, 52, cheapest));
    }

    @Test
    void aQueueOfMutalisksIsBlockedByThreeFreeRawSupply() {
        int cheapest = SupplyCapacity.cheapestUnitSupply(Collections.singletonList(unit(UnitType.Zerg_Mutalisk)));

        assertTrue(SupplyCapacity.isBlocked(54, 51, cheapest));
        assertFalse(SupplyCapacity.isBlocked(54, 50, cheapest));
    }
}
