package unit.squad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ADVANCE;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class SquadVerdictTest {

    private static final double ENGAGE_THRESHOLD = 1.4;
    private static final double RETREAT_THRESHOLD = 0.8;

    @Test
    void unlockedNeverHoldsWhateverTheVerdict() {
        assertFalse(SquadManager.fightLockHolds(false, ADVANCE, true, 1.3948, ENGAGE_THRESHOLD));
        assertFalse(SquadManager.fightLockHolds(false, ENGAGE, true, 1.3948, ENGAGE_THRESHOLD));
        assertFalse(SquadManager.fightLockHolds(false, RETREAT, true, 1.3948, ENGAGE_THRESHOLD));
    }

    @Test
    void lockedWinnableFightHolds() {
        assertTrue(SquadManager.fightLockHolds(true, ENGAGE, true, ENGAGE_THRESHOLD, ENGAGE_THRESHOLD));
        assertTrue(SquadManager.fightLockHolds(true, ENGAGE, true, 2.5, ENGAGE_THRESHOLD));
    }

    @Test
    void lockedRetreatAtTheEngageThresholdHolds() {
        assertTrue(SquadManager.fightLockHolds(true, RETREAT, true, ENGAGE_THRESHOLD, ENGAGE_THRESHOLD));
    }

    @Test
    void lockedUnmeasuredVerdictsHold() {
        assertTrue(SquadManager.fightLockHolds(true, RETREAT, false, 100, ENGAGE_THRESHOLD));
        assertTrue(SquadManager.fightLockHolds(true, RETREAT, false, 0, ENGAGE_THRESHOLD));
        assertTrue(SquadManager.fightLockHolds(true, ADVANCE, false, 0, ENGAGE_THRESHOLD));
    }

    @Test
    void lockedRetreatWithoutASnapshotHolds() {
        assertTrue(SquadManager.fightLockHolds(true, RETREAT, true, 0, 0));
    }

    /**
     * L9NW30UL frames 4650 and 4656 (ratios 1.39996 and 1.3312) and KSV3501B frame 4836 (1.3948): a RETREAT
     * measured between the ZvP retreat threshold of 0.8 and the engage threshold of 1.4 releases the lock.
     */
    @Test
    void lockedMeasuredRetreatBetweenTheThresholdsIsReleased() {
        assertFalse(SquadManager.fightLockHolds(true, RETREAT, true, 1.39996, ENGAGE_THRESHOLD));
        assertFalse(SquadManager.fightLockHolds(true, RETREAT, true, 1.3312, ENGAGE_THRESHOLD));
        assertFalse(SquadManager.fightLockHolds(true, RETREAT, true, 1.3948, ENGAGE_THRESHOLD));
        assertFalse(SquadManager.fightLockHolds(true, RETREAT, true, 1.1, ENGAGE_THRESHOLD));
        assertFalse(SquadManager.fightLockHolds(true, RETREAT, true, RETREAT_THRESHOLD, ENGAGE_THRESHOLD));
    }

    @Test
    void lockedMeasuredRetreatBelowTheRetreatThresholdIsReleased() {
        assertFalse(SquadManager.fightLockHolds(true, RETREAT, true, 0.3453, ENGAGE_THRESHOLD));
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
