package unit.squad;

import bwapi.Race;
import org.junit.jupiter.api.Test;
import unit.squad.horizon.FightBand;
import unit.squad.horizon.HorizonCombatSimulator;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static unit.squad.CombatSimulator.CombatResult.ADVANCE;
import static unit.squad.CombatSimulator.CombatResult.ENGAGE;
import static unit.squad.CombatSimulator.CombatResult.RETREAT;

class BlindTurnBackTest {

    private static final int LEFT = 4377;
    private static final int GATE_END = LEFT + Squad.BLIND_TURN_BACK_GATE_FRAMES;
    private static final double ENGAGE_THRESH = HorizonCombatSimulator.engageThreshold(Race.Zerg);
    private static final double RETREAT_THRESH = HorizonCombatSimulator.retreatThreshold(Race.Zerg);

    private static Squad leftOnAMeasuredRetreat() {
        Squad squad = new GroundSquad();
        squad.setStatus(SquadStatus.RETREAT);
        SquadManager.noteRetreatRead(squad, true, RETREAT, true, LEFT);
        return squad;
    }

    @Test
    void aBlindTurnBackIsHeldInsideTheGateAfterAMeasuredRetreat() {
        Squad squad = leftOnAMeasuredRetreat();

        for (int frame = LEFT + Squad.GROUND_RETREAT_LOCK_FRAMES; frame < GATE_END; frame++) {
            assertTrue(SquadManager.holdsBlindTurnBack(squad, true, ADVANCE, frame), "frame " + frame);
        }
    }

    @Test
    void aBlindTurnBackIsAllowedOnceTheGateHasRunOut() {
        Squad squad = leftOnAMeasuredRetreat();

        assertFalse(SquadManager.holdsBlindTurnBack(squad, true, ADVANCE, GATE_END));
        assertTrue(Squad.BLIND_TURN_BACK_GATE_FRAMES > Squad.GROUND_RETREAT_LOCK_FRAMES,
                "the gate outlasts the retreat lock it follows");
    }

    @Test
    void aMeasuredEngageTurnsTheSquadBackInsideTheGateAndEndsIt() {
        Squad squad = leftOnAMeasuredRetreat();
        int turn = LEFT + Squad.GROUND_RETREAT_LOCK_FRAMES;

        assertFalse(SquadManager.holdsBlindTurnBack(squad, true, ENGAGE, turn));
        SquadManager.beginTurnBack(squad, true, turn);

        assertFalse(squad.isBlindTurnBackGated(turn + 1));
        assertTrue(squad.isTurnBackCommitted(turn + 1));
    }

    @Test
    void eachMeasuredRetreatReadWhileRetreatingExtendsTheGate() {
        Squad squad = leftOnAMeasuredRetreat();
        int chased = LEFT + 100;

        SquadManager.noteRetreatRead(squad, true, RETREAT, true, chased);

        assertTrue(SquadManager.holdsBlindTurnBack(squad, true, ADVANCE, GATE_END));
        assertFalse(SquadManager.holdsBlindTurnBack(squad, true, ADVANCE, chased + Squad.BLIND_TURN_BACK_GATE_FRAMES));
    }

    @Test
    void onlyAMeasuredRetreatWithTheBandOnOpensTheGate() {
        Squad unmeasured = new GroundSquad();
        unmeasured.setStatus(SquadStatus.RETREAT);
        SquadManager.noteRetreatRead(unmeasured, true, RETREAT, false, LEFT);
        Squad switchedOff = new GroundSquad();
        switchedOff.setStatus(SquadStatus.RETREAT);
        SquadManager.noteRetreatRead(switchedOff, false, RETREAT, true, LEFT);
        Squad advanced = new GroundSquad();
        advanced.setStatus(SquadStatus.RETREAT);
        SquadManager.noteRetreatRead(advanced, true, ADVANCE, false, LEFT);

        assertFalse(unmeasured.isBlindTurnBackGated(LEFT + 1));
        assertFalse(switchedOff.isBlindTurnBackGated(LEFT + 1));
        assertFalse(advanced.isBlindTurnBackGated(LEFT + 1));
    }

    @Test
    void withTheSwitchOffABlindAdvanceTurnsTheSquadBackAsOnMain() {
        Squad squad = leftOnAMeasuredRetreat();

        assertFalse(SquadManager.holdsBlindTurnBack(squad, false, ADVANCE, LEFT + Squad.GROUND_RETREAT_LOCK_FRAMES));
    }

    @Test
    void theGateOnlyHoldsARetreatingSquadOnAnAdvance() {
        Squad squad = leftOnAMeasuredRetreat();
        int frame = LEFT + 1;

        assertFalse(SquadManager.holdsBlindTurnBack(squad, true, RETREAT, frame));
        squad.setStatus(SquadStatus.FIGHT);
        assertFalse(SquadManager.holdsBlindTurnBack(squad, true, ADVANCE, frame));
        squad.setStatus(SquadStatus.CONTAIN);
        assertFalse(SquadManager.holdsBlindTurnBack(squad, true, ADVANCE, frame));
    }

    @Test
    void aMergedSquadKeepsTheLatestGateAndContactOfItsSources() {
        Squad early = leftOnAMeasuredRetreat();
        early.noteContact(LEFT - 10);
        Squad late = new GroundSquad();
        late.setStatus(SquadStatus.RETREAT);
        SquadManager.noteRetreatRead(late, true, RETREAT, true, LEFT + 30);
        Squad merged = new GroundSquad();
        merged.inheritStateFrom(Arrays.asList(early, late));

        assertEquals(LEFT + 30 + Squad.BLIND_TURN_BACK_GATE_FRAMES, merged.getBlindTurnBackGateUntilFrame());
        assertTrue(merged.isInContact(LEFT - 10 + Squad.CONTACT_MEMORY_FRAMES));
    }

    @Test
    void everyExitFromRetreatEndsTheGate() {
        Squad cornered = leftOnAMeasuredRetreat();
        SquadManager.turnCorneredSquadToFight(cornered, ENGAGE, LEFT + 1);
        Squad contained = leftOnAMeasuredRetreat();
        contained.setStatus(SquadStatus.CONTAIN);
        Squad rallied = leftOnAMeasuredRetreat();
        rallied.setStatus(SquadStatus.RALLY);
        Squad stillRetreating = leftOnAMeasuredRetreat();
        stillRetreating.setStatus(SquadStatus.RETREAT);

        assertFalse(cornered.isBlindTurnBackGated(LEFT + 2), "a cornered turn to FIGHT");
        assertFalse(contained.isBlindTurnBackGated(LEFT + 2), "a contain join");
        assertFalse(rallied.isBlindTurnBackGated(LEFT + 2));
        assertTrue(stillRetreating.isBlindTurnBackGated(LEFT + 2), "staying in RETREAT keeps the gate");
    }

    @Test
    void aLaterUnmeasuredRetreatIsNotHeldByAnEarlierGate() {
        Squad squad = leftOnAMeasuredRetreat();
        squad.setStatus(SquadStatus.FIGHT);
        squad.setStatus(SquadStatus.RETREAT);

        assertFalse(SquadManager.holdsBlindTurnBack(squad, true, ADVANCE, LEFT + Squad.GROUND_RETREAT_LOCK_FRAMES));
    }

    @Test
    void aMergedSquadOutOfRetreatCarriesNoGate() {
        Squad retreating = leftOnAMeasuredRetreat();
        Squad fighting = new GroundSquad();
        fighting.setStatus(SquadStatus.FIGHT);
        Squad merged = new GroundSquad();
        merged.inheritStateFrom(Arrays.asList(retreating, fighting));

        assertEquals(SquadStatus.FIGHT, merged.getStatus());
        assertFalse(merged.isBlindTurnBackGated(LEFT + 1));
    }

    @Test
    void theTurnBackCommitStartsOnlyForARetreatingSquadWithTheBandOn() {
        Squad retreating = new GroundSquad();
        retreating.setStatus(SquadStatus.RETREAT);
        SquadManager.beginTurnBack(retreating, true, LEFT);
        Squad fighting = new GroundSquad();
        fighting.setStatus(SquadStatus.FIGHT);
        SquadManager.beginTurnBack(fighting, true, LEFT);
        Squad switchedOff = new GroundSquad();
        switchedOff.setStatus(SquadStatus.RETREAT);
        SquadManager.beginTurnBack(switchedOff, false, LEFT);

        assertEquals(LEFT + Squad.TURN_BACK_COMMIT_FRAMES, retreating.getTurnBackCommitUntilFrame());
        assertFalse(fighting.isTurnBackCommitted(LEFT + 1));
        assertFalse(switchedOff.isTurnBackCommitted(LEFT + 1));
    }

    @Test
    void contactLastsForTheContactMemory() {
        Squad squad = new GroundSquad();

        assertFalse(squad.isInContact(LEFT), "a squad that never fought is out of contact");
        squad.noteContact(LEFT);
        assertTrue(squad.isInContact(LEFT + Squad.CONTACT_MEMORY_FRAMES));
        assertFalse(squad.isInContact(LEFT + Squad.CONTACT_MEMORY_FRAMES + 1));
    }

    private static boolean lockHolds(boolean inContact, double ratio) {
        double hold = FightBand.holdThreshold(inContact, RETREAT_THRESH, ENGAGE_THRESH);
        return SquadManager.fightLockHolds(true, RETREAT, true, ratio,
                FightBand.fightLockReleaseThreshold(true, ENGAGE_THRESH, hold));
    }

    @Test
    void theFightLockHoldsALosingReadOnlyInContact() {
        assertTrue(lockHolds(true, 0.9));
        assertFalse(lockHolds(false, 0.9));
        assertTrue(lockHolds(false, FightBand.EVEN_RATIO));
        assertFalse(lockHolds(true, 0.69));
        assertFalse(lockHolds(false, 0.69));
    }
}
