package unit.squad.horizon;

import org.junit.jupiter.api.Test;
import unit.squad.HeldRetreat;
import unit.squad.Squad;
import unit.squad.SquadStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static unit.squad.CombatSimulator.CombatResult.ADVANCE;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class SiegeBandHoldTest {

    private static final double THRESH = 1.44;
    private static final double SPIKE = THRESH * 3;
    private static final double BELOW_BAR = THRESH * (1 + SiegeBandHysteresis.MARGIN) - 0.1;
    private static final int LOCK = Squad.GROUND_RETREAT_LOCK_FRAMES;
    private static final int UNLOCKED = 0;

    private final HorizonCombatSimulator sim = new HorizonCombatSimulator();

    private HeldRetreat retreatingSince(int frame) {
        HeldRetreat memory = new HeldRetreat();
        sim.holdVerdict(memory, RETREAT, frame, 0.5, THRESH, true, true, frame + LOCK);
        return memory;
    }

    @Test
    void aSpikeInsideTheRetreatLockDoesNotReleaseTheHold() {
        HeldRetreat memory = retreatingSince(100);

        assertEquals(RETREAT, sim.holdVerdict(memory, ENGAGE, 103, SPIKE, THRESH, true, true, 100 + LOCK));
        assertEquals(RETREAT, sim.holdVerdict(memory, ENGAGE, 107, THRESH, THRESH, true, true, UNLOCKED));
    }

    @Test
    void theHoldBindsAcrossTheLockAndReleasesOnceTheWindowAndMarginPass() {
        HeldRetreat memory = retreatingSince(100);
        int release = 100 + SiegeBandHysteresis.MIN_HOLD_FRAMES;

        for (int frame = 104; frame < release; frame += 4) {
            assertEquals(RETREAT, sim.holdVerdict(memory, ENGAGE, frame, SPIKE, THRESH, true, true, 100 + LOCK));
        }
        assertEquals(ENGAGE, sim.holdVerdict(memory, ENGAGE, release, SPIKE, THRESH, true, true, UNLOCKED));
    }

    @Test
    void aSquadNotInRetreatIsNeverHeldAndLosesTheMemory() {
        HeldRetreat memory = retreatingSince(100);

        assertEquals(ENGAGE, sim.holdVerdict(memory, ENGAGE, 102, SPIKE, THRESH, true, false, UNLOCKED));
        assertEquals(ENGAGE, sim.holdVerdict(memory, ENGAGE, 104, SPIKE, THRESH, true, true, UNLOCKED));
    }

    @Test
    void aGapInSimRunsDropsTheHeldRetreat() {
        HeldRetreat memory = retreatingSince(100);

        assertEquals(ENGAGE, sim.holdVerdict(memory, ENGAGE, 100 + HorizonCombatSimulator.MAX_SIM_GAP_FRAMES + 1,
                SPIKE, THRESH, true, true, UNLOCKED));
    }

    @Test
    void theMemoryIsPerSquadAndIgnoredWhenNoTankIsInTheBand() {
        HeldRetreat memory = retreatingSince(100);

        assertEquals(ENGAGE, sim.holdVerdict(new HeldRetreat(), ENGAGE, 102, SPIKE, THRESH, true, true, UNLOCKED));
        assertEquals(ENGAGE, sim.holdVerdict(memory, ENGAGE, 103, SPIKE, THRESH, false, true, UNLOCKED));
    }

    private void stepEngage(HeldRetreat memory, int from, int to, boolean tankInBand, int lockEnd) {
        for (int frame = from; frame < to; frame += 4) {
            sim.holdVerdict(memory, ENGAGE, frame, SPIKE, THRESH, tankInBand, true, lockEnd);
        }
    }

    @Test
    void anOutOfBandEngageDoesNotWipeTheHeldRetreat() {
        HeldRetreat memory = retreatingSince(100);
        int lockEnd = 100 + LOCK;

        assertEquals(ENGAGE, sim.holdVerdict(memory, ENGAGE, 104, SPIKE, THRESH, false, true, lockEnd));
        stepEngage(memory, 108, lockEnd, false, lockEnd);
        assertEquals(RETREAT, sim.holdVerdict(memory, ENGAGE, lockEnd, BELOW_BAR, THRESH, true, true, lockEnd));
        assertEquals(RETREAT, sim.holdVerdict(memory, ENGAGE, lockEnd + 4, BELOW_BAR, THRESH, true, true, lockEnd));
    }

    @Test
    void anOutOfBandEngageKeepsTheOriginalWindowSoAnInBandReleaseStillWaitsForIt() {
        HeldRetreat memory = retreatingSince(100);
        int release = 100 + SiegeBandHysteresis.MIN_HOLD_FRAMES;

        stepEngage(memory, 104, release - 3, false, UNLOCKED);
        assertEquals(RETREAT, sim.holdVerdict(memory, ENGAGE, release - 1, SPIKE, THRESH, true, true, UNLOCKED));
        assertEquals(ENGAGE, sim.holdVerdict(memory, ENGAGE, release, SPIKE, THRESH, true, true, UNLOCKED));
    }

    @Test
    void anInBandReleaseReplacesTheHeldRetreat() {
        HeldRetreat memory = retreatingSince(100);
        int release = 100 + SiegeBandHysteresis.MIN_HOLD_FRAMES;

        assertEquals(ENGAGE, sim.holdVerdict(memory, ENGAGE, release, SPIKE, THRESH, true, true, UNLOCKED));
        assertEquals(ENGAGE, sim.holdVerdict(memory, ENGAGE, release + 4, THRESH, THRESH, true, true, UNLOCKED));
    }

    @Test
    void aSquadBornInRetreatUnderAnInheritedLockIsHeldWithoutAnyMemory() {
        HeldRetreat memory = new HeldRetreat();
        int lockEnd = 23486;

        assertEquals(RETREAT, sim.holdVerdict(memory, ENGAGE, lockEnd - 86, SPIKE, THRESH, true, true, lockEnd));
        for (int frame = lockEnd - 82; frame < lockEnd; frame += 4) {
            assertEquals(RETREAT, sim.holdVerdict(memory, ENGAGE, frame, SPIKE, THRESH, true, true, lockEnd));
        }
        assertEquals(RETREAT, sim.holdVerdict(memory, ENGAGE, lockEnd, BELOW_BAR, THRESH, true, true, lockEnd));
        assertEquals(RETREAT, sim.holdVerdict(memory, ENGAGE, lockEnd + 4, SPIKE, THRESH, true, true, lockEnd));
    }

    @Test
    void theSeedDatesTheHoldFromTheStartOfTheLockNotFromTheFirstSimRun() {
        HeldRetreat memory = new HeldRetreat();
        int lockEnd = 1000;
        int release = lockEnd - LOCK + SiegeBandHysteresis.MIN_HOLD_FRAMES;

        for (int frame = lockEnd - 86; frame < release; frame += 4) {
            assertEquals(RETREAT, sim.holdVerdict(memory, ENGAGE, frame, SPIKE, THRESH, true, true, lockEnd));
        }
        assertEquals(RETREAT, sim.holdVerdict(memory, ENGAGE, release - 1, SPIKE, THRESH, true, true, lockEnd));
        assertEquals(ENGAGE, sim.holdVerdict(memory, ENGAGE, release, SPIKE, THRESH, true, true, lockEnd));
    }

    @Test
    void anAdvanceDoesNotWipeTheHeldRetreat() {
        HeldRetreat memory = retreatingSince(100);
        int lockEnd = 100 + LOCK;

        stepEngage(memory, 104, lockEnd - 4, true, lockEnd);
        assertEquals(ADVANCE, sim.holdVerdict(memory, ADVANCE, lockEnd - 1, SPIKE, THRESH, true, true, lockEnd));
        assertEquals(RETREAT, sim.holdVerdict(memory, ENGAGE, lockEnd, BELOW_BAR, THRESH, true, true, lockEnd));
    }

    @Test
    void anAttritionLockPassedAsZeroLeavesTheRawVerdict() {
        HeldRetreat memory = new HeldRetreat();

        assertEquals(ENGAGE, sim.holdVerdict(memory, ENGAGE, 500, SPIKE, THRESH, true, true, UNLOCKED));
    }

    @Test
    void anInheritedMemoryCarriesTheOriginalSinceFrameThroughASplit() {
        Squad parent = new Squad();
        parent.setStatus(SquadStatus.RETREAT);
        parent.getHeldRetreat().hold(100, 140);
        Squad child = new Squad();
        child.inheritStateFrom(parent);
        HeldRetreat memory = child.getHeldRetreat();

        assertEquals(100, memory.getSinceFrame());
        assertEquals(RETREAT, sim.holdVerdict(memory, ENGAGE, 142, SPIKE, THRESH, true, true, 100 + LOCK));
        assertEquals(100, memory.getSinceFrame());
        assertEquals(ENGAGE, sim.holdVerdict(memory, ENGAGE, 100 + SiegeBandHysteresis.MIN_HOLD_FRAMES, SPIKE,
                THRESH, true, true, UNLOCKED));
    }
}
