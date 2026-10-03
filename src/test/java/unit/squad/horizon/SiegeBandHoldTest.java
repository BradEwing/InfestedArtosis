package unit.squad.horizon;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class SiegeBandHoldTest {

    private static final double THRESH = 1.44;
    private static final double SPIKE = THRESH * 3;
    private static final int LOCK = unit.squad.Squad.GROUND_RETREAT_LOCK_FRAMES;

    private static HorizonCombatSimulator retreatingSince(int frame) {
        HorizonCombatSimulator sim = new HorizonCombatSimulator();
        sim.holdVerdict("a", RETREAT, frame, 0.5, THRESH, true, true, false);
        return sim;
    }

    @Test
    void aSpikeInsideTheRetreatLockDoesNotReleaseTheHold() {
        HorizonCombatSimulator sim = retreatingSince(100);

        assertEquals(RETREAT, sim.holdVerdict("a", ENGAGE, 103, SPIKE, THRESH, true, true, true));
        assertEquals(RETREAT, sim.holdVerdict("a", ENGAGE, 107, THRESH, THRESH, true, true, false));
    }

    @Test
    void theHoldBindsAcrossTheLockAndReleasesOnceTheWindowAndMarginPass() {
        HorizonCombatSimulator sim = retreatingSince(100);
        int release = 100 + SiegeBandHysteresis.MIN_HOLD_FRAMES;

        for (int frame = 104; frame < release; frame += 4) {
            assertEquals(RETREAT, sim.holdVerdict("a", ENGAGE, frame, SPIKE, THRESH, true, true,
                    frame < 100 + LOCK));
        }
        assertEquals(ENGAGE, sim.holdVerdict("a", ENGAGE, release, SPIKE, THRESH, true, true, false));
    }

    @Test
    void aSquadNotInRetreatIsNeverHeldAndLosesTheMemory() {
        HorizonCombatSimulator sim = retreatingSince(100);

        assertEquals(ENGAGE, sim.holdVerdict("a", ENGAGE, 102, SPIKE, THRESH, true, false, false));
        assertEquals(ENGAGE, sim.holdVerdict("a", ENGAGE, 104, SPIKE, THRESH, true, true, false));
    }

    @Test
    void aGapInSimRunsDropsTheHeldRetreat() {
        HorizonCombatSimulator sim = retreatingSince(100);

        assertEquals(ENGAGE, sim.holdVerdict("a", ENGAGE, 100 + HorizonCombatSimulator.MAX_SIM_GAP_FRAMES + 1,
                SPIKE, THRESH, true, true, false));
    }

    @Test
    void theMemoryIsPerSquadAndIgnoredWhenNoTankIsInTheBand() {
        HorizonCombatSimulator sim = retreatingSince(100);

        assertEquals(ENGAGE, sim.holdVerdict("b", ENGAGE, 102, SPIKE, THRESH, true, true, false));
        assertEquals(ENGAGE, sim.holdVerdict("a", ENGAGE, 103, SPIKE, THRESH, false, true, false));
    }
}
