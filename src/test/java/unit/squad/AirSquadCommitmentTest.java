package unit.squad;

import bwapi.Position;
import bwapi.UnitType;
import org.junit.jupiter.api.Test;
import unit.squad.horizon.HorizonCombatSimulator;

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
        assertTrue(squad.engageCommitmentHolds(ARMED + AirSquad.ENGAGE_COMMITMENT_FRAMES - 1, 1100));
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
        double nearMiss = ENGAGE_THRESHOLD * 0.95;

        assertTrue(SquadManager.commitmentMayHold(SquadStatus.FIGHT, RETREAT, true, nearMiss, ENGAGE_THRESHOLD, false));
        assertFalse(SquadManager.commitmentMayHold(SquadStatus.FIGHT, RETREAT, false, nearMiss, ENGAGE_THRESHOLD,
                false));
        assertFalse(SquadManager.commitmentMayHold(SquadStatus.FIGHT, ENGAGE, true, nearMiss, ENGAGE_THRESHOLD, false));
        assertFalse(SquadManager.commitmentMayHold(SquadStatus.FIGHT, ADVANCE, true, nearMiss, ENGAGE_THRESHOLD,
                false));
        assertFalse(SquadManager.commitmentMayHold(SquadStatus.RETREAT, RETREAT, true, nearMiss, ENGAGE_THRESHOLD,
                false));
    }

    @Test
    void aRetreatReadFarBelowTheThresholdIsLetThrough() {
        double floor = ENGAGE_THRESHOLD * AirSquad.COMMITMENT_RELEASE_RATIO;

        assertTrue(SquadManager.commitmentMayHold(SquadStatus.FIGHT, RETREAT, true, floor, ENGAGE_THRESHOLD, false));
        assertFalse(SquadManager.commitmentMayHold(SquadStatus.FIGHT, RETREAT, true, floor - 0.01, ENGAGE_THRESHOLD,
                false));
        assertFalse(SquadManager.commitmentMayHold(SquadStatus.FIGHT, RETREAT, true, 1.13, ENGAGE_THRESHOLD, false));
    }

    @Test
    void aRetreatReadThatSampledStaticAntiAirIsLetThrough() {
        assertFalse(SquadManager.commitmentMayHold(SquadStatus.FIGHT, RETREAT, true, ENGAGE_THRESHOLD * 0.95,
                ENGAGE_THRESHOLD, true));
    }

    @Test
    void aRetreatReadWithNoThresholdIsLetThrough() {
        assertTrue(AirSquad.retreatReleasesCommitment(1.0, 0, false));
    }

    @Test
    void aBuildingWithAntiAirStrengthCountsAsStaticAntiAir() {
        assertTrue(SquadManager.samplesStaticAntiAir(snapshotOf(entry(UnitType.Terran_Goliath, 3.0),
                entry(UnitType.Terran_Missile_Turret, 2.0))));
        assertTrue(SquadManager.samplesStaticAntiAir(snapshotOf(entry(UnitType.Terran_Bunker, 1.5))));
    }

    @Test
    void mobileAntiAirAndUnarmedBuildingsAreNotStaticAntiAir() {
        assertFalse(SquadManager.samplesStaticAntiAir(snapshotOf(entry(UnitType.Terran_Goliath, 3.0),
                entry(UnitType.Terran_Supply_Depot, 0), entry(UnitType.Terran_Bunker, 0))));
        assertFalse(SquadManager.samplesStaticAntiAir(null));
    }

    @Test
    void anEscortingOverlordIsLeftOutOfTheFlockHitPoints() {
        assertFalse(SquadManager.countsTowardFlockHitPoints(UnitType.Zerg_Overlord));
        assertTrue(SquadManager.countsTowardFlockHitPoints(UnitType.Zerg_Mutalisk));
        assertTrue(SquadManager.countsTowardFlockHitPoints(UnitType.Zerg_Scourge));
    }

    @Test
    void aSingleStrongEngageReadDoesNotBreakTheAirRetreatLock() {
        AirSquad squad = new AirSquad();
        squad.startRetreatLock(ARMED);

        assertFalse(squad.strongEngagePersisted(true, ARMED + 1));
        assertFalse(squad.strongEngagePersisted(false, ARMED + 2));
        assertFalse(squad.strongEngagePersisted(true, ARMED + 13));
        assertFalse(squad.strongEngagePersisted(true, ARMED + 24));
        assertTrue(squad.strongEngagePersisted(true, ARMED + 25));
    }

    @Test
    void aYieldBarsTheCommitmentForTheFightItOpens() {
        AirSquad squad = new AirSquad();
        squad.setStatus(SquadStatus.RETREAT);
        squad.barEngageCommitment();
        squad.setStatus(SquadStatus.FIGHT);
        squad.armEngageCommitment(ARMED, FLOCK_HP);

        assertFalse(squad.engageCommitmentHolds(ARMED + 10, FLOCK_HP));

        squad.armEngageCommitment(ARMED + 20, FLOCK_HP);
        assertFalse(squad.engageCommitmentHolds(ARMED + 30, FLOCK_HP));
    }

    @Test
    void leavingFightLiftsTheBar() {
        AirSquad squad = new AirSquad();
        squad.setStatus(SquadStatus.FIGHT);
        squad.barEngageCommitment();
        squad.setStatus(SquadStatus.RETREAT);
        squad.setStatus(SquadStatus.FIGHT);
        squad.armEngageCommitment(ARMED, FLOCK_HP);

        assertTrue(squad.engageCommitmentHolds(ARMED + 10, FLOCK_HP));
    }

    @Test
    void aMergedFlockIsBarredWhenAnySourceWas() {
        AirSquad barred = new AirSquad();
        barred.setStatus(SquadStatus.FIGHT);
        barred.barEngageCommitment();

        AirSquad merged = new AirSquad();
        merged.inheritStateFrom(Arrays.<Squad>asList(barred, new AirSquad()));
        merged.setStatus(SquadStatus.FIGHT);
        merged.armEngageCommitment(ARMED, FLOCK_HP);

        assertFalse(merged.engageCommitmentHolds(ARMED + 10, FLOCK_HP));
    }

    private static HorizonCombatSimulator.UnitDebugEntry entry(UnitType type, double strength) {
        return new HorizonCombatSimulator.UnitDebugEntry(new Position(0, 0), type, strength, false, false);
    }

    private static HorizonCombatSimulator.DebugSnapshot snapshotOf(HorizonCombatSimulator.UnitDebugEntry... entries) {
        HorizonCombatSimulator.DebugSnapshot snapshot = new HorizonCombatSimulator.DebugSnapshot();
        snapshot.getEnemyUnits().addAll(Arrays.asList(entries));
        return snapshot;
    }
}
