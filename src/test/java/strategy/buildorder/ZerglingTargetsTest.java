package strategy.buildorder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZerglingTargetsTest {

    private static final int MATCHUP_TARGET = 24;

    private static final int HYDRALISKS_WANTED = 11;

    private static final int FRAMES = 30;

    private static final int UNIT_GAS_PRICE = bwapi.UnitType.Zerg_Hydralisk.gasPrice();

    @Test
    void aGasFocusedBuildStillKeepsAScreeningForceWhileItSavesLarva() {
        int target = ZerglingTargets.gasUnitFocus(MATCHUP_TARGET, true, 0, HYDRALISKS_WANTED, true);

        assertEquals(ZerglingTargets.GAS_UNIT_FOCUS_FLOOR, target);
        assertTrue(target > 0);
    }

    @Test
    void theHoldLiftsWhenTheGasThatWouldPayForTheUnitIsOutOfReach() {
        assertEquals(MATCHUP_TARGET, ZerglingTargets.gasUnitFocus(MATCHUP_TARGET, true, 0, HYDRALISKS_WANTED, false));
    }

    @Test
    void theHoldLiftsOnceTheGasUnitsAreFielded() {
        assertEquals(MATCHUP_TARGET,
                ZerglingTargets.gasUnitFocus(MATCHUP_TARGET, true, HYDRALISKS_WANTED, HYDRALISKS_WANTED, true));
    }

    @Test
    void theHoldNeverAppliesBeforeTheTechBuildingFinishes() {
        assertEquals(MATCHUP_TARGET, ZerglingTargets.gasUnitFocus(MATCHUP_TARGET, false, 0, HYDRALISKS_WANTED, true));
    }

    @Test
    void anUnreachableGasUnitCannotPinTheTargetAtZero() {
        int targetOverTime = 0;
        for (int frame = 0; frame < FRAMES; frame++) {
            targetOverTime += ZerglingTargets.gasUnitFocus(MATCHUP_TARGET, true, 0, HYDRALISKS_WANTED, false);
        }

        assertEquals(FRAMES * MATCHUP_TARGET, targetOverTime);
        assertTrue(ZerglingTargets.gasUnitFocus(MATCHUP_TARGET, true, 0, HYDRALISKS_WANTED, true) > 0);
    }

    @Test
    void aSatisfiedMatchupTargetIsNotRaisedByTheFloor() {
        assertEquals(0, ZerglingTargets.gasUnitFocus(0, true, 0, HYDRALISKS_WANTED, true));
    }

    @Test
    void aMatchupTargetBelowTheFloorIsNotRaisedEither() {
        int belowFloor = ZerglingTargets.GAS_UNIT_FOCUS_FLOOR - 2;

        assertEquals(belowFloor, ZerglingTargets.gasUnitFocus(belowFloor, true, 0, HYDRALISKS_WANTED, true));
    }

    @Test
    void aGasWorkerGapDoesNotPutTheUnitOutOfReachWhileTheBankCoversIt() {
        assertTrue(ZerglingTargets.gasUnitReachable(0, UNIT_GAS_PRICE, UNIT_GAS_PRICE));
        assertEquals(ZerglingTargets.GAS_UNIT_FOCUS_FLOOR,
                ZerglingTargets.gasUnitFocus(MATCHUP_TARGET, true, 0, HYDRALISKS_WANTED,
                        ZerglingTargets.gasUnitReachable(0, UNIT_GAS_PRICE, UNIT_GAS_PRICE)));
    }

    @Test
    void miningGasKeepsTheUnitInReachOnAnEmptyBank() {
        assertTrue(ZerglingTargets.gasUnitReachable(1, 0, UNIT_GAS_PRICE));
    }

    @Test
    void neitherGasWorkersNorABankPutsTheUnitOutOfReach() {
        assertFalse(ZerglingTargets.gasUnitReachable(0, UNIT_GAS_PRICE - 1, UNIT_GAS_PRICE));
        assertEquals(MATCHUP_TARGET,
                ZerglingTargets.gasUnitFocus(MATCHUP_TARGET, true, 0, HYDRALISKS_WANTED,
                        ZerglingTargets.gasUnitReachable(0, 0, UNIT_GAS_PRICE)));
    }
}
