package unit.squad;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

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
    void aMergedFlockKeepsTheEarliestCommitmentOfItsSources() {
        AirSquad early = committedFlock();
        AirSquad late = new AirSquad();
        late.setStatus(SquadStatus.FIGHT);
        late.armEngageCommitment(ARMED + 40, FLOCK_HP);

        AirSquad merged = new AirSquad();
        merged.inheritStateFrom(Arrays.<Squad>asList(late, early));

        assertTrue(merged.engageCommitmentHolds(ARMED + AirSquad.ENGAGE_COMMITMENT_FRAMES - 1, 2 * FLOCK_HP));
        assertFalse(merged.engageCommitmentHolds(ARMED + AirSquad.ENGAGE_COMMITMENT_FRAMES, 2 * FLOCK_HP));
    }

    @Test
    void aMergedFlockMeasuresItsLossFromTheMergedHitPoints() {
        AirSquad merged = new AirSquad();
        merged.inheritStateFrom(Arrays.<Squad>asList(committedFlock(), committedFlock()));

        assertTrue(merged.engageCommitmentHolds(ARMED + 10, 2 * FLOCK_HP));
        assertFalse(merged.engageCommitmentHolds(ARMED + 11, 2 * FLOCK_HP - 480));
    }

    @Test
    void aMergeOutOfFightCarriesNoCommitment() {
        AirSquad runby = new AirSquad();
        runby.setStatus(SquadStatus.RUNBY);

        AirSquad merged = new AirSquad();
        merged.inheritStateFrom(Arrays.<Squad>asList(committedFlock(), runby));

        assertFalse(merged.engageCommitmentHolds(ARMED + 10, FLOCK_HP));
    }

    @Test
    void aSplitChildInheritsTheCommitmentAndThePeakRestartsFromItsOwnHitPoints() {
        AirSquad parent = committedFlock();
        AirSquad child = new AirSquad();
        child.inheritStateFrom(parent);

        assertTrue(child.engageCommitmentHolds(ARMED + 10, 240));
        assertFalse(child.engageCommitmentHolds(ARMED + AirSquad.ENGAGE_COMMITMENT_FRAMES, 240));
    }

    @Test
    void aSplitParentDoesNotCountTheMembersItGaveAwayAsLost() {
        AirSquad parent = committedFlock();
        parent.rebaseEngageCommitment();

        assertTrue(parent.engageCommitmentHolds(ARMED + 10, 960));
        assertFalse(parent.engageCommitmentHolds(ARMED + 11, 768));
    }

    @Test
    void theCommitmentIsConsultedOnlyForAnAirSquadInFightGivenARetreat() {
        assertTrue(SquadManager.commitmentMayHold(SquadStatus.FIGHT, RETREAT, true));
        assertFalse(SquadManager.commitmentMayHold(SquadStatus.FIGHT, RETREAT, false));
        assertFalse(SquadManager.commitmentMayHold(SquadStatus.FIGHT, ENGAGE, true));
        assertFalse(SquadManager.commitmentMayHold(SquadStatus.FIGHT, ADVANCE, true));
        assertFalse(SquadManager.commitmentMayHold(SquadStatus.RETREAT, RETREAT, true));
    }

    @Test
    void releasingTheRetreatLockUnlocksTheSquad() {
        AirSquad squad = new AirSquad();
        squad.startRetreatLock(ARMED);
        squad.releaseRetreatLock();

        assertFalse(squad.isRetreatLocked(ARMED + 1));
    }
}
