package unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerManagerTest {

    @Test
    void gasIsCutOnlyWhenUnreservedGasClearsTheSurplus() {
        assertTrue(WorkerManager.shouldCutGasHarvesting(0, WorkerManager.GAS_SURPLUS + 1));
        assertFalse(WorkerManager.shouldCutGasHarvesting(0, WorkerManager.GAS_SURPLUS));
    }

    @Test
    void aMineralDeficitIsNotAGasSurplus() {
        assertFalse(WorkerManager.shouldCutGasHarvesting(-75, 116));
        assertFalse(WorkerManager.shouldCutGasHarvesting(-500, 100));
    }

    @Test
    void reservedMineralsDoNotRaiseTheGasBar() {
        assertTrue(WorkerManager.shouldCutGasHarvesting(-75, 400));
        assertTrue(WorkerManager.shouldCutGasHarvesting(0, 400));
    }

    @Test
    void mineralsInTheBankHoldGasWorkersOnTheGeyser() {
        assertFalse(WorkerManager.shouldCutGasHarvesting(400, 400));
        assertTrue(WorkerManager.shouldCutGasHarvesting(200, 400));
    }

    @Test
    void gasDemandSaturatesWhenNoUnreservedGasIsLeft() {
        assertTrue(WorkerManager.shouldSaturateOnGasDemand(0));
        assertTrue(WorkerManager.shouldSaturateOnGasDemand(-100));
        assertFalse(WorkerManager.shouldSaturateOnGasDemand(1));
    }

    @Test
    void geysersAreSaturatedWhenMineralsFloat() {
        assertTrue(WorkerManager.shouldSaturateOnMineralSurplus(WorkerManager.MINERAL_SURPLUS + 101, 100));
        assertFalse(WorkerManager.shouldSaturateOnMineralSurplus(WorkerManager.MINERAL_SURPLUS + 100, 100));
    }

    @Test
    void aStandingGasSurplusIsNotSaturated() {
        assertFalse(WorkerManager.shouldSaturateOnMineralSurplus(0, 400));
        assertFalse(WorkerManager.shouldSaturateOnGasDemand(400));
        assertFalse(WorkerManager.shouldSaturateOnMineralSurplus(-75, 116));
        assertFalse(WorkerManager.shouldSaturateOnGasDemand(116));
    }

    @Test
    void noDroneLeavesMineralsBelowTheGathererFloor() {
        assertEquals(0, WorkerManager.spareGeyserWorkers(
                WorkerManager.MIN_MINERAL_GATHERERS - 1, 3, WorkerManager.MIN_MINERAL_GATHERERS));
        assertEquals(0, WorkerManager.spareGeyserWorkers(0, 3, WorkerManager.MIN_MINERAL_GATHERERS));
    }

    @Test
    void theMineralSurplusPathKeepsItsUnflooredBehaviour() {
        assertEquals(3, WorkerManager.spareGeyserWorkers(3, 3, WorkerManager.NO_MINERAL_FLOOR));
        assertEquals(1, WorkerManager.spareGeyserWorkers(1, 3, WorkerManager.NO_MINERAL_FLOOR));
    }

    @Test
    void theOpenSlotsCapTheDronesMoved() {
        assertEquals(3, WorkerManager.spareGeyserWorkers(12, 3, WorkerManager.MIN_MINERAL_GATHERERS));
        assertEquals(1, WorkerManager.spareGeyserWorkers(12, 1, WorkerManager.MIN_MINERAL_GATHERERS));
        assertEquals(0, WorkerManager.spareGeyserWorkers(12, 0, WorkerManager.MIN_MINERAL_GATHERERS));
    }

    @Test
    void theGathererCountCapsTheDronesMoved() {
        assertEquals(4, WorkerManager.spareGeyserWorkers(4, 6, WorkerManager.MIN_MINERAL_GATHERERS));
    }

    @Test
    void theCutAndTheSaturationNeverBothFire() {
        for (int minerals = -600; minerals <= 600; minerals += 25) {
            for (int gas = -600; gas <= 600; gas += 25) {
                boolean cut = WorkerManager.shouldCutGasHarvesting(minerals, gas);
                assertFalse(cut && WorkerManager.shouldSaturateOnMineralSurplus(minerals, gas));
                assertFalse(cut && WorkerManager.shouldSaturateOnGasDemand(gas));
            }
        }
    }
}
