package strategy.buildorder;

import bwapi.Race;
import info.GameState;
import macro.plan.UnitPlan;
import org.junit.jupiter.api.Test;
import util.Time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the Zergling flood hold rules.
 */
class LingFloodHoldTest {

    private static final Time EARLY = new Time(4, 0);

    private static final Time AFTER_DEADLINE = new Time(10, 1);

    @Test
    void startsOnTheTwoHatchLingDetection() {
        assertTrue(LingFloodHold.TRIGGER_STRATEGIES.contains("2HatchLing"));
    }

    @Test
    void startsOnTheNinePoolMainHatchDetection() {
        assertTrue(LingFloodHold.TRIGGER_STRATEGIES.contains("9PoolMainHatch"));
    }

    @Test
    void standsOnceTheFloodIsDetected() {
        assertTrue(LingFloodHold.isActive(true, false, EARLY));
    }

    @Test
    void doesNotStandWithoutADetection() {
        assertFalse(LingFloodHold.isActive(false, false, EARLY));
    }

    @Test
    void liftsOnceEnemyLairTechIsSeen() {
        assertFalse(LingFloodHold.isActive(true, true, EARLY));
    }

    @Test
    void releaseCutoffIsSixMinutes() {
        assertEquals(new Time(6, 0), LingFloodHold.LAIR_RELEASE_CUTOFF);
    }

    @Test
    void doesNotStandOnLairTechAlone() {
        assertFalse(LingFloodHold.isActive(false, true, EARLY));
    }

    @Test
    void standsThroughTheDeadline() {
        assertTrue(LingFloodHold.isActive(true, false, LingFloodHold.DEADLINE));
    }

    @Test
    void liftsAfterTheDeadline() {
        assertFalse(LingFloodHold.isActive(true, false, AFTER_DEADLINE));
    }

    @Test
    void raisesTheSunkenTargetWhileHolding() {
        assertEquals(LingFloodHold.SUNKENS, LingFloodHold.sunkenTarget(0, true));
        assertEquals(LingFloodHold.SUNKENS, LingFloodHold.sunkenTarget(1, true));
    }

    @Test
    void keepsAHigherSunkenTargetWhileHolding() {
        assertEquals(LingFloodHold.SUNKENS + 1, LingFloodHold.sunkenTarget(LingFloodHold.SUNKENS + 1, true));
    }

    @Test
    void leavesTheSunkenTargetAloneWithoutTheHold() {
        assertEquals(1, LingFloodHold.sunkenTarget(1, false));
    }

    /**
     * ZergBase asks for ten zerglings plus every enemy zergling seen, up to forty. Against a flood
     * that target is never met, and a build that plans drones only once it is met never drones.
     */
    @Test
    void capsTheZerglingTargetWhileHolding() {
        assertEquals(LingFloodHold.ZERGLINGS, LingFloodHold.zerglingTarget(40, true));
    }

    @Test
    void keepsALowerZerglingTargetWhileHolding() {
        assertEquals(0, LingFloodHold.zerglingTarget(0, true));
        assertEquals(LingFloodHold.ZERGLINGS - 2, LingFloodHold.zerglingTarget(LingFloodHold.ZERGLINGS - 2, true));
    }

    @Test
    void leavesTheZerglingTargetAloneWithoutTheHold() {
        assertEquals(40, LingFloodHold.zerglingTarget(40, false));
    }

    @Test
    void plansDronesBelowTheFloor() {
        assertTrue(LingFloodHold.wantsDrone(true, LingFloodHold.DRONE_FLOOR - 1, true));
    }

    @Test
    void stopsAtTheFloor() {
        assertFalse(LingFloodHold.wantsDrone(true, LingFloodHold.DRONE_FLOOR, true));
    }

    @Test
    void respectsThePlannedWorkerLimit() {
        assertFalse(LingFloodHold.wantsDrone(true, 8, false));
    }

    @Test
    void plansNoDroneWithoutTheHold() {
        assertFalse(LingFloodHold.wantsDrone(false, 8, true));
    }

    /**
     * The floor has to be reachable on one base against Zerg, where the expected-worker ceiling
     * of seven per base and three per geyser stops GameState.canPlanDrone() at ten.
     */
    @Test
    void floorExceedsTheOneBaseZergWorkerCeiling() {
        assertTrue(LingFloodHold.DRONE_FLOOR > GameState.expectedWorkers(Race.Zerg, 1, 1));
    }

    @Test
    void floorDronesQueueBehindTheEmergencyBandAndAheadOfFramePriorities() {
        assertTrue(LingFloodHold.DRONE_PRIORITY > BuildOrder.EMERGENCY_DEFENSE_PRIORITY);
        assertTrue(LingFloodHold.DRONE_PRIORITY < UnitPlan.DRONE_ROUND_PRIORITY);
    }
}
