package unit.squad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ADVANCE;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class SquadVerdictTest {

    private static final double RETREAT_THRESHOLD = 0.8;

    @Test
    void unlockedNeverHoldsWhateverTheVerdict() {
        assertFalse(SquadManager.fightLockHolds(false, ADVANCE, true, 1.3948, RETREAT_THRESHOLD));
        assertFalse(SquadManager.fightLockHolds(false, ENGAGE, true, 1.3948, RETREAT_THRESHOLD));
        assertFalse(SquadManager.fightLockHolds(false, RETREAT, true, 1.3948, RETREAT_THRESHOLD));
    }

    @Test
    void lockedEngageAndAdvanceAlwaysHold() {
        assertTrue(SquadManager.fightLockHolds(true, ENGAGE, true, 1.3948, RETREAT_THRESHOLD));
        assertTrue(SquadManager.fightLockHolds(true, ADVANCE, false, 0, RETREAT_THRESHOLD));
    }

    @Test
    void lockedUnmeasuredRetreatHolds() {
        assertTrue(SquadManager.fightLockHolds(true, RETREAT, false, 100, RETREAT_THRESHOLD));
    }

    /**
     * Reproduces KSV3501B frame 4836: an in-band RETREAT, measured at 1.3948 against a retreat
     * threshold of 0.8, stays suppressed by the lock.
     */
    @Test
    void lockedMeasuredRetreatInBandHolds() {
        assertTrue(SquadManager.fightLockHolds(true, RETREAT, true, 1.3948, RETREAT_THRESHOLD));
    }

    @Test
    void lockedMeasuredRetreatBelowThresholdBreaksTheLock() {
        assertFalse(SquadManager.fightLockHolds(true, RETREAT, true, 0.3453, RETREAT_THRESHOLD));
    }

    @Test
    void lockedMeasuredRetreatAtThresholdHolds() {
        assertTrue(SquadManager.fightLockHolds(true, RETREAT, true, RETREAT_THRESHOLD, RETREAT_THRESHOLD));
    }

    @Test
    void fightStatusNeverHeldWhateverTheFlags() {
        assertFalse(SquadManager.blindAdvanceHeld(SquadStatus.FIGHT, false, true, true));
        assertFalse(SquadManager.blindAdvanceHeld(SquadStatus.FIGHT, false, false, false));
    }

    @Test
    void measuredAdvanceNeverHeld() {
        assertFalse(SquadManager.blindAdvanceHeld(SquadStatus.RALLY, true, true, true));
        assertFalse(SquadManager.blindAdvanceHeld(SquadStatus.RETREAT, true, true, true));
    }

    @Test
    void unmeasuredWithThreatBeyondRadiusIsHeld() {
        assertTrue(SquadManager.blindAdvanceHeld(SquadStatus.RALLY, false, true, false));
    }

    @Test
    void unmeasuredWithBaseThreatenedIsHeld() {
        assertTrue(SquadManager.blindAdvanceHeld(SquadStatus.RALLY, false, false, true));
    }

    @Test
    void unmeasuredRetreatWithThreatBeyondRadiusIsHeld() {
        assertTrue(SquadManager.blindAdvanceHeld(SquadStatus.RETREAT, false, true, false));
    }

    @Test
    void unmeasuredWithNeitherFlagIsNotHeld() {
        assertFalse(SquadManager.blindAdvanceHeld(SquadStatus.RALLY, false, false, false));
    }
}
