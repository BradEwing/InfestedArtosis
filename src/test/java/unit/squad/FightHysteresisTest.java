package unit.squad;

import bwapi.Race;
import org.junit.jupiter.api.Test;
import telemetry.SquadLock;
import unit.squad.horizon.FightBand;
import unit.squad.horizon.HorizonCombatSimulator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ADVANCE;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class FightHysteresisTest {

    private static final double ENGAGE_THRESH = HorizonCombatSimulator.engageThreshold(Race.Zerg);
    private static final double RETREAT_THRESH = HorizonCombatSimulator.retreatThreshold(Race.Zerg);
    private static final double IN_BAND = 1.0;
    private static final double BELOW_RETREAT = 0.69;
    private static final int TURN_BACK = 5274;

    private static boolean bandLockHolds(boolean band, boolean fightLocked, CombatSimulator.CombatResult result,
                                         boolean enemyMeasured, double ratio) {
        return SquadManager.fightLockHolds(fightLocked, result, enemyMeasured, ratio,
                FightBand.fightLockReleaseThreshold(band, ENGAGE_THRESH, RETREAT_THRESH));
    }

    @Test
    void theFightLockHoldsARetreatReadInsideTheBand() {
        assertTrue(bandLockHolds(true, true, RETREAT, true, IN_BAND));
        assertTrue(bandLockHolds(true, true, RETREAT, true, RETREAT_THRESH));
    }

    @Test
    void theFightLockReleasesARetreatReadBelowTheRetreatThreshold() {
        assertFalse(bandLockHolds(true, true, RETREAT, true, BELOW_RETREAT));
        assertFalse(bandLockHolds(true, true, RETREAT, true, 0.0));
    }

    @Test
    void theFightLockStillHoldsEngageAdvanceAndUnmeasuredReadsAndNeverHoldsUnlocked() {
        assertTrue(bandLockHolds(true, true, ENGAGE, true, ENGAGE_THRESH));
        assertTrue(bandLockHolds(true, true, ADVANCE, false, 0));
        assertTrue(bandLockHolds(true, true, RETREAT, false, 0));
        assertFalse(bandLockHolds(true, false, RETREAT, true, IN_BAND));
        assertFalse(bandLockHolds(true, false, ENGAGE, true, ENGAGE_THRESH));
    }

    @Test
    void withTheSwitchOffTheFightLockReleasesARetreatReadInsideTheBandAsOnMain() {
        boolean onMain = SquadManager.fightLockHolds(true, RETREAT, true, IN_BAND, ENGAGE_THRESH);

        assertFalse(onMain);
        assertEquals(onMain, bandLockHolds(false, true, RETREAT, true, IN_BAND));
        assertEquals(SquadManager.fightLockHolds(true, RETREAT, true, BELOW_RETREAT, ENGAGE_THRESH),
                bandLockHolds(false, true, RETREAT, true, BELOW_RETREAT));
    }

    private static Squad turnedBack() {
        Squad squad = new GroundSquad();
        squad.setStatus(SquadStatus.FIGHT);
        squad.startTurnBackCommit(TURN_BACK);
        return squad;
    }

    private static boolean commitHolds(Squad squad, int frame, double ratio) {
        return SquadManager.turnBackCommitHolds(squad.isTurnBackCommitted(frame), RETREAT, true, ratio,
                RETREAT_THRESH);
    }

    @Test
    void aTurnedBackSquadCannotReturnToRetreatInsideTheCommitWindowAboveTheRetreatThreshold() {
        Squad squad = turnedBack();
        int end = TURN_BACK + Squad.TURN_BACK_COMMIT_FRAMES;

        for (int frame = TURN_BACK + 1; frame < end; frame++) {
            assertTrue(SquadManager.fightHeld(squad, frame, commitHolds(squad, frame, IN_BAND)), "frame " + frame);
        }
        assertFalse(SquadManager.fightHeld(squad, end, commitHolds(squad, end, IN_BAND)), "the window has ended");
    }

    @Test
    void aTurnedBackSquadBelowTheRetreatThresholdRetreatsInsideTheCommitWindow() {
        Squad squad = turnedBack();

        assertFalse(SquadManager.fightHeld(squad, TURN_BACK + 1, commitHolds(squad, TURN_BACK + 1, BELOW_RETREAT)));
    }

    @Test
    void theCommitWindowLastsAsLongAsTheGroundRetreatLock() {
        assertEquals(Squad.GROUND_RETREAT_LOCK_FRAMES, Squad.TURN_BACK_COMMIT_FRAMES);
        assertFalse(new GroundSquad().isTurnBackCommitted(TURN_BACK), "a squad that never turned back has none");
    }

    @Test
    void theCommitWindowPassesEngageAndAdvanceReadsThrough() {
        assertFalse(SquadManager.turnBackCommitHolds(true, ENGAGE, true, ENGAGE_THRESH, RETREAT_THRESH));
        assertFalse(SquadManager.turnBackCommitHolds(true, ADVANCE, false, 0, RETREAT_THRESH));
        assertFalse(SquadManager.turnBackCommitHolds(false, RETREAT, true, IN_BAND, RETREAT_THRESH));
    }

    @Test
    void aMergedSquadKeepsTheLatestCommitWindowOfItsSources() {
        Squad early = new GroundSquad();
        early.setStatus(SquadStatus.FIGHT);
        early.startTurnBackCommit(TURN_BACK);
        Squad late = new GroundSquad();
        late.setStatus(SquadStatus.FIGHT);
        late.startTurnBackCommit(TURN_BACK + 50);
        Squad merged = new GroundSquad();
        merged.inheritStateFrom(java.util.Arrays.asList(early, late));

        assertEquals(TURN_BACK + 50 + Squad.TURN_BACK_COMMIT_FRAMES, merged.getTurnBackCommitUntilFrame());
    }

    private static Squad lockedAfterLosses(int frame) {
        Squad squad = new GroundSquad();
        squad.setStatus(SquadStatus.FIGHT);
        squad.setCachedSupply(12);
        SquadManager.updateFightLock(squad, ENGAGE, false, frame);
        squad.setCachedSupply(8);
        return squad;
    }

    @Test
    void aReadAboveTheRetreatThresholdRenewsTheFightLockDespiteLostSupply() {
        Squad squad = lockedAfterLosses(4582);
        int expiry = squad.getFightLockedUntilFrame();

        SquadManager.updateFightLock(squad, ENGAGE, false,
                FightBand.holdsAboveRetreat(true, true, IN_BAND, RETREAT_THRESH), expiry);

        assertEquals(expiry + squad.getFightHysteresis().getFrames(), squad.getFightLockedUntilFrame());
    }

    @Test
    void withTheSwitchOffLostSupplyStillStopsTheRenewalAsOnMain() {
        Squad squad = lockedAfterLosses(4582);
        int expiry = squad.getFightLockedUntilFrame();

        SquadManager.updateFightLock(squad, ENGAGE, false,
                FightBand.holdsAboveRetreat(false, true, IN_BAND, RETREAT_THRESH), expiry);

        assertEquals(expiry, squad.getFightLockedUntilFrame());
        assertFalse(squad.canRenewFightLock(expiry));
    }

    @Test
    void theHoldIsNamedByWhatWasDecisive() {
        assertEquals(SquadLock.FIGHT, SquadManager.fightHoldLock(true, true));
        assertEquals(SquadLock.FIGHT, SquadManager.fightHoldLock(true, false));
        assertEquals(SquadLock.FIGHT_LOCK_BAND, SquadManager.fightHoldLock(false, true));
        assertEquals(SquadLock.COMMIT_WINDOW, SquadManager.fightHoldLock(false, false));
    }
}
