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
    void aPlanIsShortOnlyOfMineralsWhenTheBankCoversItsGas() {
        assertTrue(ResourceCount.isShortOnlyOfMinerals(56, 200, 200, 200));
        assertFalse(ResourceCount.isShortOnlyOfMinerals(56, 199, 200, 200));
        assertFalse(ResourceCount.isShortOnlyOfMinerals(200, 200, 200, 200));
        assertTrue(ResourceCount.isShortOnlyOfMinerals(-19, -40, 50, 0));
    }

    @Test
    void theBankCoversAPlanOnlyWhenEveryResourceItPricesIsMined() {
        assertTrue(ResourceCount.bankCovers(300, 0, 300, 0));
        assertFalse(ResourceCount.bankCovers(299, 500, 300, 0));
        assertTrue(ResourceCount.bankCovers(100, 50, 100, 50));
        assertFalse(ResourceCount.bankCovers(500, 49, 100, 50));
        assertTrue(ResourceCount.bankCovers(75, -10, 75, 0));
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

    private ResourceCount resourceCount(int mineralBank, int gasBank) {
        return new ResourceCount(null) {
            @Override
            public int availableMinerals() {
                return mineralBank - getReservedMinerals();
            }

            @Override
            public int availableGas() {
                return gasBank - getReservedGas();
            }
        };
    }

    @Test
    void aGasDebtDoesNotMakeAGasFreeUnitUnaffordable() {
        ResourceCount resourceCount = resourceCount(
                UnitType.Zerg_Lair.mineralPrice() + UnitType.Zerg_Zergling.mineralPrice(),
                UnitType.Zerg_Lair.gasPrice() - 60);
        resourceCount.reserveUnit(UnitType.Zerg_Lair);

        assertTrue(resourceCount.availableGas() < 0);
        assertFalse(resourceCount.cannotAffordUnit(UnitType.Zerg_Zergling));
    }

    @Test
    void aGasPricedUnitIsUnaffordableWithoutTheGas() {
        UnitType mutalisk = UnitType.Zerg_Mutalisk;
        ResourceCount resourceCount = resourceCount(mutalisk.mineralPrice(), mutalisk.gasPrice() - 1);

        assertTrue(resourceCount.cannotAffordUnit(mutalisk));
    }

    @Test
    void aGasPricedUnitIsUnaffordableUnderAGasDebt() {
        ResourceCount resourceCount = resourceCount(
                UnitType.Zerg_Lair.mineralPrice() + UnitType.Zerg_Mutalisk.mineralPrice(),
                0);
        resourceCount.reserveUnit(UnitType.Zerg_Lair);

        assertTrue(resourceCount.cannotAffordUnit(UnitType.Zerg_Mutalisk));
    }

    @Test
    void aUnitIsUnaffordableWithoutTheMineralsWhateverTheGas() {
        UnitType zergling = UnitType.Zerg_Zergling;
        UnitType mutalisk = UnitType.Zerg_Mutalisk;

        assertTrue(resourceCount(zergling.mineralPrice() - 1, 1000).cannotAffordUnit(zergling));
        assertTrue(resourceCount(mutalisk.mineralPrice() - 1, 1000).cannotAffordUnit(mutalisk));
        assertTrue(resourceCount(zergling.mineralPrice() - 1, -100).cannotAffordUnit(zergling));
    }

    @Test
    void aUnitThatCoversBothPricesIsAffordable() {
        UnitType mutalisk = UnitType.Zerg_Mutalisk;

        assertFalse(resourceCount(mutalisk.mineralPrice(), mutalisk.gasPrice()).cannotAffordUnit(mutalisk));
    }

    @Test
    void aResourceThePlanDoesNotPriceIsNeverShort() {
        assertFalse(ResourceCount.isShort(-76, 0));
        assertFalse(ResourceCount.isShort(0, 0));
        assertTrue(ResourceCount.isShort(-76, 1));
        assertTrue(ResourceCount.isShort(99, 100));
        assertFalse(ResourceCount.isShort(100, 100));
    }

    @Test
    void unreservingAMorphFromAnExistingUnitDoesNotFreeALarva() {
        ResourceCount resourceCount = resourceCount();
        resourceCount.reserveUnit(UnitType.Zerg_Hydralisk);
        resourceCount.unreserveUnit(UnitType.Zerg_Guardian);

        assertFalse(resourceCount.canScheduleLarva(1, 0));
    }
}
