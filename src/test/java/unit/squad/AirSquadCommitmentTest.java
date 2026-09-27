package unit.squad;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ADVANCE;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class AirSquadCommitmentTest {

    private static final int ARMED = 19661;
    private static final int FLOCK_HP = 1200;
    private static final double ENGAGE_THRESHOLD = 1.44;

    private static AirSquad committedFlock() {
        AirSquad squad = new AirSquad();
        squad.setStatus(SquadStatus.FIGHT);
        squad.armEngageCommitment(ARMED, FLOCK_HP);
        return squad;
    }

    @Test
    void theCommitmentHoldsThroughARetreatReadWhileTheFlockKeepsItsHitPoints() {
        AirSquad squad = committedFlock();

        assertTrue(squad.engageCommitmentHolds(ARMED + 36, FLOCK_HP));
        assertTrue(squad.engageCommitmentHolds(ARMED + AirSquad.ENGAGE_COMMITMENT_FRAMES - 1, 1000));
    }

    @Test
    void theCommitmentBreaksOnceTheFlockLosesTheThresholdOfItsHitPoints() {
        int lossAtThreshold = (int) Math.ceil(FLOCK_HP * AirSquad.COMMITMENT_HP_LOSS_THRESHOLD);

        assertTrue(committedFlock().engageCommitmentHolds(ARMED + 10, FLOCK_HP - lossAtThreshold + 1));
        assertFalse(committedFlock().engageCommitmentHolds(ARMED + 10, FLOCK_HP - lossAtThreshold));
    }

    @Test
    void theCommitmentEndsAfterItsWindow() {
        AirSquad squad = committedFlock();

        assertFalse(squad.engageCommitmentHolds(ARMED + AirSquad.ENGAGE_COMMITMENT_FRAMES, FLOCK_HP));
    }

    @Test
    void hitPointsGainedByAMergeRaiseThePeakTheLossIsMeasuredFrom() {
        AirSquad squad = committedFlock();

        assertTrue(squad.engageCommitmentHolds(ARMED + 5, 2400));
        assertFalse(squad.engageCommitmentHolds(ARMED + 6, 1900));
    }

    @Test
    void theCommitmentIsArmedOncePerFightEpisode() {
        AirSquad squad = committedFlock();
        squad.armEngageCommitment(ARMED + 60, FLOCK_HP);

        assertFalse(squad.engageCommitmentHolds(ARMED + AirSquad.ENGAGE_COMMITMENT_FRAMES, FLOCK_HP));
    }

    @Test
    void leavingFightEndsTheCommitmentAndTheNextEngageArmsAFreshOne() {
        AirSquad squad = committedFlock();
        squad.setStatus(SquadStatus.RETREAT);

        assertFalse(squad.engageCommitmentHolds(ARMED + 10, FLOCK_HP));

        squad.setStatus(SquadStatus.FIGHT);
        squad.armEngageCommitment(ARMED + 50, FLOCK_HP);
        assertTrue(squad.engageCommitmentHolds(ARMED + 50 + AirSquad.ENGAGE_COMMITMENT_FRAMES - 1, FLOCK_HP));
    }

    @Test
    void anUnarmedFlockHasNoCommitment() {
        AirSquad squad = new AirSquad();
        squad.setStatus(SquadStatus.FIGHT);

        assertFalse(squad.engageCommitmentHolds(ARMED, FLOCK_HP));
    }

    @Test
    void anAirRetreatLockYieldsToAnEngageReadOfTwiceTheThreshold() {
        double twice = ENGAGE_THRESHOLD * SquadManager.RETREAT_LOCK_ENGAGE_BREAK_MULTIPLIER;

        assertTrue(SquadManager.retreatLockYieldsToEngage(true, ENGAGE, true, twice, ENGAGE_THRESHOLD));
        assertTrue(SquadManager.retreatLockYieldsToEngage(true, ENGAGE, true, 9.56, ENGAGE_THRESHOLD));
        assertFalse(SquadManager.retreatLockYieldsToEngage(true, ENGAGE, true, twice - 0.01, ENGAGE_THRESHOLD));
    }

    @Test
    void theRetreatLockKeepsEverythingElse() {
        assertFalse(SquadManager.retreatLockYieldsToEngage(false, ENGAGE, true, 9.56, ENGAGE_THRESHOLD));
        assertFalse(SquadManager.retreatLockYieldsToEngage(true, ADVANCE, true, 9.56, ENGAGE_THRESHOLD));
        assertFalse(SquadManager.retreatLockYieldsToEngage(true, RETREAT, true, 9.56, ENGAGE_THRESHOLD));
        assertFalse(SquadManager.retreatLockYieldsToEngage(true, ENGAGE, false, 9.56, ENGAGE_THRESHOLD));
        assertFalse(SquadManager.retreatLockYieldsToEngage(true, ENGAGE, true, 0, 0));
    }

    @Test
    void releasingTheRetreatLockUnlocksTheSquad() {
        AirSquad squad = new AirSquad();
        squad.startRetreatLock(ARMED);
        squad.releaseRetreatLock();

        assertFalse(squad.isRetreatLocked(ARMED + 1));
    }
}
