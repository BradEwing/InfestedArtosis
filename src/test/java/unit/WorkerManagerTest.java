package unit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkerManagerTest {

    private static final int NO_QUEUED_GAS = 0;
    private static final int MUTALISK_GAS = 100;

    @Test
    void gasIsCutOnlyWhenUnreservedGasClearsTheSurplus() {
        assertTrue(WorkerManager.shouldCutGasHarvesting(0, WorkerManager.GAS_SURPLUS + 1, NO_QUEUED_GAS));
        assertFalse(WorkerManager.shouldCutGasHarvesting(0, WorkerManager.GAS_SURPLUS, NO_QUEUED_GAS));
    }

    @Test
    void aMineralDeficitIsNotAGasSurplus() {
        assertFalse(WorkerManager.shouldCutGasHarvesting(-75, 116, NO_QUEUED_GAS));
        assertFalse(WorkerManager.shouldCutGasHarvesting(-500, 100, NO_QUEUED_GAS));
    }

    @Test
    void reservedMineralsDoNotRaiseTheGasBar() {
        assertTrue(WorkerManager.shouldCutGasHarvesting(-75, 400, NO_QUEUED_GAS));
        assertTrue(WorkerManager.shouldCutGasHarvesting(0, 400, NO_QUEUED_GAS));
    }

    @Test
    void mineralsInTheBankHoldGasWorkersOnTheGeyser() {
        assertFalse(WorkerManager.shouldCutGasHarvesting(400, 400, NO_QUEUED_GAS));
        assertTrue(WorkerManager.shouldCutGasHarvesting(200, 400, NO_QUEUED_GAS));
    }

    @Test
    void gasDemandSaturatesWhenNoUnreservedGasIsLeft() {
        assertTrue(WorkerManager.shouldSaturateOnGasDemand(0, NO_QUEUED_GAS));
        assertTrue(WorkerManager.shouldSaturateOnGasDemand(-100, NO_QUEUED_GAS));
        assertFalse(WorkerManager.shouldSaturateOnGasDemand(1, NO_QUEUED_GAS));
    }

    @Test
    void geysersAreSaturatedWhenMineralsFloat() {
        assertTrue(WorkerManager.shouldSaturateOnMineralSurplus(WorkerManager.MINERAL_SURPLUS + 101, 100));
        assertFalse(WorkerManager.shouldSaturateOnMineralSurplus(WorkerManager.MINERAL_SURPLUS + 100, 100));
    }

    @Test
    void aStandingGasSurplusIsNotSaturated() {
        assertFalse(WorkerManager.shouldSaturateOnMineralSurplus(0, 400));
        assertFalse(WorkerManager.shouldSaturateOnGasDemand(400, NO_QUEUED_GAS));
        assertFalse(WorkerManager.shouldSaturateOnMineralSurplus(-75, 116));
        assertFalse(WorkerManager.shouldSaturateOnGasDemand(116, NO_QUEUED_GAS));
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
        for (int queuedGas = 0; queuedGas <= 1200; queuedGas += 100) {
            for (int minerals = -600; minerals <= 600; minerals += 25) {
                for (int gas = -600; gas <= 600; gas += 25) {
                    boolean cut = WorkerManager.shouldCutGasHarvesting(minerals, gas, queuedGas);
                    assertFalse(cut && WorkerManager.shouldSaturateOnMineralSurplus(minerals, gas));
                    assertFalse(cut && WorkerManager.shouldSaturateOnGasDemand(gas, queuedGas));
                }
            }
        }
    }

    @Test
    void queuedGasDemandAboveTheBankHoldsDronesOnGas() {
        assertFalse(WorkerManager.shouldCutGasHarvesting(51, 262, 3 * MUTALISK_GAS));
        assertFalse(WorkerManager.shouldCutGasHarvesting(0, 400, 11 * MUTALISK_GAS));
    }

    @Test
    void theSameBankWithNothingQueuedIsStillCut() {
        assertTrue(WorkerManager.shouldCutGasHarvesting(51, 262, NO_QUEUED_GAS));
        assertTrue(WorkerManager.shouldCutGasHarvesting(0, 400, NO_QUEUED_GAS));
    }

    @Test
    void queuedGasBelowTheSurplusLeavesTheCutInPlace() {
        assertTrue(WorkerManager.shouldCutGasHarvesting(0, 400, MUTALISK_GAS));
        assertFalse(WorkerManager.shouldCutGasHarvesting(0, 400, 3 * MUTALISK_GAS));
    }

    @Test
    void queuedGasDemandAboveTheBankRestoresGasFromAllOff() {
        assertFalse(WorkerManager.shouldSaturateOnMineralSurplus(140, 230));
        assertFalse(WorkerManager.shouldSaturateOnGasDemand(230, NO_QUEUED_GAS));
        assertTrue(WorkerManager.shouldSaturateOnGasDemand(230, 11 * MUTALISK_GAS));
        assertTrue(WorkerManager.shouldSaturateOnGasDemand(230, 230));
        assertFalse(WorkerManager.shouldCutGasHarvesting(140, 230, 11 * MUTALISK_GAS));
    }

    @Test
    void aBankSittingOnTheCutThresholdDoesNotToggleGas() {
        int queuedGas = 2 * MUTALISK_GAS;
        int cutEdge = WorkerManager.GAS_SURPLUS + queuedGas;
        assertEquals(1, gasTransitions(true, 0, cutEdge, queuedGas));
    }

    @Test
    void aBankSittingOnTheRestoreThresholdDoesNotToggleGas() {
        int queuedGas = 2 * MUTALISK_GAS;
        assertEquals(1, gasTransitions(false, 0, queuedGas, queuedGas));
    }

    private static int gasTransitions(boolean gasOn, int minerals, int edgeGas, int queuedGas) {
        int transitions = 0;
        for (int frame = 0; frame < 200; frame++) {
            int gas = edgeGas + frame % 2;
            boolean next = gasOn;
            if (WorkerManager.shouldCutGasHarvesting(minerals, gas, queuedGas)) {
                next = false;
            } else if (WorkerManager.shouldSaturateOnMineralSurplus(minerals, gas)
                    || WorkerManager.shouldSaturateOnGasDemand(gas, queuedGas)) {
                next = true;
            }
            if (next != gasOn) {
                transitions++;
            }
            gasOn = next;
        }
        return transitions;
    }
}
